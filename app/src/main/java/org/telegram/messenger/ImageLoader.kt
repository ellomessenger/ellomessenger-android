/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.messenger

import android.annotation.TargetApi
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.text.TextUtils
import android.util.SparseArray
import androidx.exifinterface.media.ExifInterface
import org.json.JSONObject
import org.telegram.DispatchQueuePriority
import org.telegram.messenger.ChatObject.isChannelAndNotMegaGroup
import org.telegram.messenger.DocumentObject.ThemeDocument
import org.telegram.messenger.FileLoader.FileLoaderDelegate
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.messageobject.MessageObject.Companion.canPreviewDocument
import org.telegram.messenger.messageobject.MessageObject.Companion.isAnimatedStickerDocument
import org.telegram.messenger.messageobject.MessageObject.Companion.isGifDocument
import org.telegram.messenger.messageobject.MessageObject.Companion.isRoundVideoDocument
import org.telegram.messenger.messageobject.MessageObject.Companion.isVideoDocument
import org.telegram.messenger.messageobject.MessageObject.Companion.isVideoSticker
import org.telegram.messenger.messageobject.MessageObject.Companion.isWebM
import org.telegram.messenger.messageobject.MessageObject.Companion.shouldEncryptPhotoOrVideo
import org.telegram.messenger.secretmedia.EncryptedFileInputStream
import org.telegram.messenger.utils.BitmapsCache
import org.telegram.messenger.utils.BitmapsCache.CacheOptions
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.InputEncryptedFile
import org.telegram.tgnet.TLRPC.InputFile
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.TL_document
import org.telegram.tgnet.TLRPC.TL_documentAttributeVideo
import org.telegram.tgnet.TLRPC.TL_documentEncrypted
import org.telegram.tgnet.TLRPC.TL_fileLocationToBeDeprecated
import org.telegram.tgnet.TLRPC.TL_fileLocationUnavailable
import org.telegram.tgnet.TLRPC.TL_messageExtendedMediaPreview
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument
import org.telegram.tgnet.TLRPC.TL_messageMediaInvoice
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto
import org.telegram.tgnet.TLRPC.TL_messageMediaWebPage
import org.telegram.tgnet.TLRPC.TL_photoCachedSize
import org.telegram.tgnet.TLRPC.TL_photoPathSize
import org.telegram.tgnet.TLRPC.TL_photoSize
import org.telegram.tgnet.TLRPC.TL_photoSize_layer127
import org.telegram.tgnet.TLRPC.TL_photoStrippedSize
import org.telegram.tgnet.TLRPC.TL_upload_getWebFile
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.ui.Cells.ChatMessageCell
import org.telegram.ui.Components.AnimatedFileDrawable
import org.telegram.ui.Components.RLottieDrawable
import org.telegram.ui.Components.SlotsDrawable
import org.telegram.ui.Components.ThemePreviewDrawable
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLConnection
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.util.LinkedList
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

/**
 * image filter types
 * suffixes:
 * f - image is wallpaper
 * isc - ignore cache for small images
 * b - need blur image
 * g - autoplay
 * lastframe - return firstframe for Lottie animation
 * firstframe - return firstframe for Lottie animation
 */
class ImageLoader {
	private val bitmapUseCounts = mutableMapOf<String, Int>()
	private val smallImagesMemCache: LruCache<BitmapDrawable>
	private val memCache: LruCache<BitmapDrawable>
	private val wallpaperMemCache: LruCache<BitmapDrawable>
	val lottieMemCache: LruCache<BitmapDrawable>
	private val imageLoadingByUrl = mutableMapOf<String, CacheImage>()
	private val imageLoadingByKeys = mutableMapOf<String, CacheImage>()
	private val imageLoadingByTag = SparseArray<CacheImage>()
	private val waitingForQualityThumb = mutableMapOf<String, ThumbGenerateInfo>()
	private val waitingForQualityThumbByTag = SparseArray<String>()
	private val httpTasks = LinkedList<HttpImageTask>()
	private val artworkTasks = LinkedList<ArtworkLoadTask>()
	private val cacheThumbOutQueue = DispatchQueue("cacheThumbOutQueue")
	private val thumbGeneratingQueue = DispatchQueue("thumbGeneratingQueue")
	private val imageLoadQueue = DispatchQueue("imageLoadQueue")
	private val replacedBitmaps = mutableMapOf<String, String>()
	private val fileProgresses = ConcurrentHashMap<String, LongArray>()
	private val thumbGenerateTasks = mutableMapOf<String, ThumbGenerateTask>()
	private val forceLoadingImages = mutableMapOf<String, Int>()
	private var canForce8888 = false
	private val testWebFile = ConcurrentHashMap<String, WebFile>()
	private val httpFileLoadTasks = LinkedList<HttpFileTask>()
	private val httpFileLoadTasksByKeys = mutableMapOf<String, HttpFileTask>()
	private val retryHttpsTasks = mutableMapOf<String, Runnable>()
	private val cachedAnimatedFileDrawables = mutableListOf<AnimatedFileDrawable>()
	private var currentHttpTasksCount = 0
	private var currentArtworkTasksCount = 0
	private var currentHttpFileLoadTasksCount = 0
	private var ignoreRemoval: String? = null
	private var lastImageNum = 0
	private var telegramPath: File? = null

	@JvmField
	val cacheOutQueue: DispatchQueuePriority = DispatchQueuePriority("cacheOutQueue")

	@Volatile
	private var lastCacheOutTime: Long = 0

	init {
		thumbGeneratingQueue.priority = Thread.MIN_PRIORITY

		val memoryClass = (ApplicationLoader.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
		val maxSize: Int

		if ((memoryClass >= 192).also { canForce8888 = it }) {
			maxSize = 30
		}
		else {
			maxSize = 15
		}

		val cacheSize = (min(maxSize.toDouble(), (memoryClass / 7).toDouble()) * 1024 * 1024).toInt()
		val commonCacheSize = (cacheSize * 0.8f).toInt()
		val smallImagesCacheSize = (cacheSize * 0.2f).toInt()

		memCache = object : LruCache<BitmapDrawable>(commonCacheSize) {
			override fun sizeOf(key: String, value: BitmapDrawable): Int {
				return value.bitmap.byteCount
			}

			override fun entryRemoved(evicted: Boolean, key: String, oldValue: BitmapDrawable?, newValue: BitmapDrawable?) {
				if (ignoreRemoval != null && ignoreRemoval == key) {
					return
				}

				val count = bitmapUseCounts[key]

				if (count == null || count == 0) {
					val b = oldValue?.bitmap

					if (b?.isRecycled == false) {
						AndroidUtilities.recycleBitmaps(listOf(b))
					}
				}
			}
		}

		smallImagesMemCache = object : LruCache<BitmapDrawable>(smallImagesCacheSize) {
			override fun sizeOf(key: String, value: BitmapDrawable): Int {
				return value.bitmap.byteCount
			}

			override fun entryRemoved(evicted: Boolean, key: String, oldValue: BitmapDrawable?, newValue: BitmapDrawable?) {
				if (ignoreRemoval != null && ignoreRemoval == key) {
					return
				}

				val count = bitmapUseCounts[key]

				if (count == null || count == 0) {
					val b = oldValue?.bitmap

					if (b?.isRecycled == false) {
						AndroidUtilities.recycleBitmaps(listOf(b))
					}
				}
			}
		}

		wallpaperMemCache = object : LruCache<BitmapDrawable>(cacheSize / 4) {
			override fun sizeOf(key: String, value: BitmapDrawable): Int {
				return value.bitmap.byteCount
			}
		}

		lottieMemCache = object : LruCache<BitmapDrawable>(512 * 512 * 2 * 4 * 5) {
			override fun sizeOf(key: String, value: BitmapDrawable): Int {
				return value.intrinsicWidth * value.intrinsicHeight * 4 * 2
			}

			override fun put(key: String, value: BitmapDrawable): BitmapDrawable? {
				if (value is AnimatedFileDrawable) {
					cachedAnimatedFileDrawables.add(value)
				}

				return super.put(key, value)
			}

			override fun entryRemoved(evicted: Boolean, key: String, oldValue: BitmapDrawable?, newValue: BitmapDrawable?) {
				val count = bitmapUseCounts[key]

				if (oldValue is AnimatedFileDrawable) {
					cachedAnimatedFileDrawables.remove(oldValue)
				}

				if (count == null || count == 0) {
					if (oldValue is AnimatedFileDrawable) {
						oldValue.recycle()
					}

					if (oldValue is RLottieDrawable) {
						oldValue.recycle()
					}
				}
			}
		}

		val mediaDirs = SparseArray<File>()
		val cachePath = AndroidUtilities.getCacheDir()

		if (!cachePath.isDirectory) {
			try {
				cachePath.mkdirs()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		AndroidUtilities.createEmptyFile(File(cachePath, ".nomedia"))

		mediaDirs.put(FileLoader.MEDIA_DIR_CACHE, cachePath)

		for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
			FileLoader.getInstance(a).setDelegate(object : FileLoaderDelegate {
				override fun fileUploadProgressChanged(operation: FileUploadOperation?, location: String?, uploadedSize: Long, totalSize: Long, isEncrypted: Boolean) {
					if (location == null) {
						return
					}

					if (operation == null) {
						return
					}

					fileProgresses[location] = longArrayOf(uploadedSize, totalSize)

					val currentTime = SystemClock.elapsedRealtime()

					if (operation.lastProgressUpdateTime == 0L || operation.lastProgressUpdateTime < currentTime - 100 || uploadedSize == totalSize) {
						operation.lastProgressUpdateTime = currentTime

						AndroidUtilities.runOnUIThread {
							NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.fileUploadProgressChanged, location, uploadedSize, totalSize, isEncrypted)
						}
					}
				}

				override fun fileDidUploaded(location: String?, inputFile: InputFile?, inputEncryptedFile: InputEncryptedFile?, key: ByteArray?, iv: ByteArray?, totalFileSize: Long) {
					Utilities.stageQueue.postRunnable {
						AndroidUtilities.runOnUIThread {
							NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.fileUploaded, location, inputFile, inputEncryptedFile, key, iv, totalFileSize)
						}

						fileProgresses.remove(location)
					}
				}

				override fun fileDidFailedUpload(location: String?, isEncrypted: Boolean) {
					Utilities.stageQueue.postRunnable {
						AndroidUtilities.runOnUIThread {
							NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.fileUploadFailed, location, isEncrypted)
						}

						fileProgresses.remove(location)
					}
				}

				override fun fileDidLoaded(location: String?, finalFile: File?, parentObject: Any?, type: Int) {
					fileProgresses.remove(location)

					AndroidUtilities.runOnUIThread {
						if (SharedConfig.saveToGalleryFlags != 0 && finalFile != null && (location?.endsWith(".mp4") == true || location?.endsWith(".jpg") == true)) {
							if (parentObject is MessageObject) {
								val dialogId = parentObject.dialogId

								val flag = if (dialogId >= 0) {
									SharedConfig.SAVE_TO_GALLERY_FLAG_PEER
								}
								else {
									if (isChannelAndNotMegaGroup(MessagesController.getInstance(a).getChat(-dialogId))) {
										SharedConfig.SAVE_TO_GALLERY_FLAG_CHANNELS
									}
									else {
										SharedConfig.SAVE_TO_GALLERY_FLAG_GROUP
									}
								}

								if ((SharedConfig.saveToGalleryFlags and flag) != 0) {
									AndroidUtilities.addMediaToGallery(finalFile.toString())
								}
							}
						}

						NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.fileLoaded, location, finalFile)

						this@ImageLoader.fileDidLoaded(location, finalFile, type)
					}
				}

				override fun fileDidFailedLoad(location: String?, state: Int) {
					fileProgresses.remove(location)

					AndroidUtilities.runOnUIThread {
						this@ImageLoader.fileDidFailedLoad(location, state)
						NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.fileLoadFailed, location, state)
					}
				}

				override fun fileLoadProgressChanged(operation: FileLoadOperation?, location: String?, uploadedSize: Long, totalSize: Long) {
					if (location == null) {
						return
					}

					if (operation == null) {
						return
					}

					fileProgresses[location] = longArrayOf(uploadedSize, totalSize)

					val currentTime = SystemClock.elapsedRealtime()

					if (operation.lastProgressUpdateTime == 0L || operation.lastProgressUpdateTime < currentTime - 500 || uploadedSize == 0L) {
						operation.lastProgressUpdateTime = currentTime

						AndroidUtilities.runOnUIThread {
							NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.fileLoadProgressChanged, location, uploadedSize, totalSize)
						}
					}
				}
			})
		}

		FileLoader.setMediaDirs(mediaDirs)

		val receiver = object : BroadcastReceiver() {
			override fun onReceive(arg0: Context, intent: Intent) {
				val r = Runnable { checkMediaPaths() }

				if (Intent.ACTION_MEDIA_UNMOUNTED == intent.action) {
					AndroidUtilities.runOnUIThread(r, 1000)
				}
				else {
					r.run()
				}
			}
		}

		val filter = IntentFilter()
		filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
		filter.addAction(Intent.ACTION_MEDIA_CHECKING)
		filter.addAction(Intent.ACTION_MEDIA_EJECT)
		filter.addAction(Intent.ACTION_MEDIA_MOUNTED)
		filter.addAction(Intent.ACTION_MEDIA_NOFS)
		filter.addAction(Intent.ACTION_MEDIA_REMOVED)
		filter.addAction(Intent.ACTION_MEDIA_SHARED)
		filter.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE)
		filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED)

		filter.addDataScheme("file")

		runCatching {
			ApplicationLoader.applicationContext.registerReceiver(receiver, filter)
		}

		checkMediaPaths()
	}

	fun moveToFront(key: String?) {
		if (key == null) {
			return
		}

		var drawable = lottieMemCache[key]

		if (drawable != null) {
			lottieMemCache.moveToFront(key)
		}

		drawable = memCache[key]

		if (drawable != null) {
			memCache.moveToFront(key)
		}

		drawable = smallImagesMemCache[key]

		if (drawable != null) {
			smallImagesMemCache.moveToFront(key)
		}
	}

	fun putThumbsToCache(updateMessageThumbs: List<MessageThumb>) {
		for (thumb in updateMessageThumbs) {
			putImageToCache(thumb.drawable, thumb.key, true)
		}
	}

	private fun isAnimatedAvatar(filter: String?): Boolean {
		return filter != null && filter.endsWith("avatar")
	}

	private fun getFromMemCache(key: String?): BitmapDrawable? {
		var drawable = memCache[key]

		if (drawable == null) {
			drawable = smallImagesMemCache[key]
		}

		if (drawable == null) {
			drawable = wallpaperMemCache[key]
		}

		if (drawable == null) {
			drawable = getFromLottieCache(key)
		}

		return drawable
	}

	fun checkMediaPaths() {
		cacheOutQueue.postRunnable {
			val paths = createMediaPaths()

			AndroidUtilities.runOnUIThread {
				FileLoader.setMediaDirs(paths)
			}
		}
	}

	fun addTestWebFile(url: String?, webFile: WebFile?) {
		if (url == null || webFile == null) {
			return
		}

		testWebFile[url] = webFile
	}

	fun removeTestWebFile(url: String?) {
		if (url == null) {
			return
		}

		testWebFile.remove(url)
	}

	fun createMediaPaths(): SparseArray<File> {
		val mediaDirs = SparseArray<File>()
		val cachePath = AndroidUtilities.getCacheDir()

		if (!cachePath.isDirectory) {
			try {
				cachePath.mkdirs()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		AndroidUtilities.createEmptyFile(File(cachePath, ".nomedia"))

		mediaDirs.put(FileLoader.MEDIA_DIR_CACHE, cachePath)

		if (BuildConfig.DEBUG) {
			FileLog.d("cache path = $cachePath")
		}

		try {
			if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
				var path = Environment.getExternalStorageDirectory()

				if (!SharedConfig.storageCacheDir.isNullOrEmpty()) {
					val dirs = AndroidUtilities.getRootDirs()

					if (dirs != null) {
						for (dir in dirs) {
							if (dir.absolutePath.startsWith(SharedConfig.storageCacheDir)) {
								path = dir
								break
							}
						}
					}
				}

				var publicMediaDir: File? = null

				if (Build.VERSION.SDK_INT >= 30) {
					try {
						val firstExternalMediaDir = ApplicationLoader.applicationContext.externalMediaDirs.firstOrNull()

						if (firstExternalMediaDir != null) {
							publicMediaDir = File(firstExternalMediaDir, "Ello")
							publicMediaDir.mkdirs()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					val newPath = ApplicationLoader.applicationContext.getExternalFilesDir(null)

					telegramPath = File(newPath, "Ello")
				}
				else {
					telegramPath = File(path, "Ello")
				}

				telegramPath?.mkdirs()

				if (telegramPath?.isDirectory != true) {
					val dirs = AndroidUtilities.getDataDirs()

					for (dir in dirs) {
						if (dir.absolutePath.startsWith(SharedConfig.storageCacheDir)) {
							path = dir

							telegramPath = File(path, "Ello")
							telegramPath?.mkdirs()

							break
						}
					}
				}

				if (telegramPath?.isDirectory == true) {
					try {
						val imagePath = File(telegramPath, "Ello Images")
						imagePath.mkdir()

						if (imagePath.isDirectory && canMoveFiles(cachePath, imagePath, FileLoader.MEDIA_DIR_IMAGE)) {
							mediaDirs.put(FileLoader.MEDIA_DIR_IMAGE, imagePath)

							if (BuildConfig.DEBUG) {
								FileLog.d("image path = $imagePath")
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					try {
						val videoPath = File(telegramPath, "Ello Video")
						videoPath.mkdir()

						if (videoPath.isDirectory && canMoveFiles(cachePath, videoPath, FileLoader.MEDIA_DIR_VIDEO)) {
							mediaDirs.put(FileLoader.MEDIA_DIR_VIDEO, videoPath)

							if (BuildConfig.DEBUG) {
								FileLog.d("video path = $videoPath")
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					try {
						val audioPath = File(telegramPath, "Ello Audio")
						audioPath.mkdir()

						if (audioPath.isDirectory && canMoveFiles(cachePath, audioPath, FileLoader.MEDIA_DIR_AUDIO)) {
							AndroidUtilities.createEmptyFile(File(audioPath, ".nomedia"))

							mediaDirs.put(FileLoader.MEDIA_DIR_AUDIO, audioPath)

							if (BuildConfig.DEBUG) {
								FileLog.d("audio path = $audioPath")
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					try {
						val documentPath = File(telegramPath, "Ello Documents")
						documentPath.mkdir()

						if (documentPath.isDirectory && canMoveFiles(cachePath, documentPath, FileLoader.MEDIA_DIR_DOCUMENT)) {
							AndroidUtilities.createEmptyFile(File(documentPath, ".nomedia"))

							mediaDirs.put(FileLoader.MEDIA_DIR_DOCUMENT, documentPath)

							if (BuildConfig.DEBUG) {
								FileLog.d("documents path = $documentPath")
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					try {
						val normalNamesPath = File(telegramPath, "Ello Files")
						normalNamesPath.mkdir()

						if (normalNamesPath.isDirectory && canMoveFiles(cachePath, normalNamesPath, FileLoader.MEDIA_DIR_FILES)) {
							AndroidUtilities.createEmptyFile(File(normalNamesPath, ".nomedia"))

							mediaDirs.put(FileLoader.MEDIA_DIR_FILES, normalNamesPath)

							if (BuildConfig.DEBUG) {
								FileLog.d("files path = $normalNamesPath")
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				if (publicMediaDir != null && publicMediaDir.isDirectory) {
					try {
						val imagePath = File(publicMediaDir, "Ello Images")
						imagePath.mkdir()

						if (imagePath.isDirectory && canMoveFiles(cachePath, imagePath, FileLoader.MEDIA_DIR_IMAGE)) {
							mediaDirs.put(FileLoader.MEDIA_DIR_IMAGE_PUBLIC, imagePath)

							if (BuildConfig.DEBUG) {
								FileLog.d("image path = $imagePath")
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					try {
						val videoPath = File(publicMediaDir, "Ello Video")
						videoPath.mkdir()

						if (videoPath.isDirectory && canMoveFiles(cachePath, videoPath, FileLoader.MEDIA_DIR_VIDEO)) {
							mediaDirs.put(FileLoader.MEDIA_DIR_VIDEO_PUBLIC, videoPath)

							if (BuildConfig.DEBUG) {
								FileLog.d("video path = $videoPath")
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}
			else {
				if (BuildConfig.DEBUG) {
					FileLog.d("this Android can't rename files")
				}
			}

			SharedConfig.checkSaveToGalleryFiles()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		return mediaDirs
	}

	private fun canMoveFiles(from: File, to: File, type: Int): Boolean {
		return runCatching {
			var srcFile: File? = null
			var dstFile: File? = null

			when (type) {
				FileLoader.MEDIA_DIR_IMAGE -> {
					srcFile = File(from, "000000000_999999_temp.f")
					dstFile = File(to, "000000000_999999.f")
				}

				FileLoader.MEDIA_DIR_DOCUMENT, FileLoader.MEDIA_DIR_FILES -> {
					srcFile = File(from, "000000000_999999_temp.f")
					dstFile = File(to, "000000000_999999.f")
				}

				FileLoader.MEDIA_DIR_AUDIO -> {
					srcFile = File(from, "000000000_999999_temp.f")
					dstFile = File(to, "000000000_999999.f")
				}

				FileLoader.MEDIA_DIR_VIDEO -> {
					srcFile = File(from, "000000000_999999_temp.f")
					dstFile = File(to, "000000000_999999.f")
				}
			}

			if (srcFile == null || dstFile == null) {
				return@runCatching false
			}

			val buffer = ByteArray(1024)

			srcFile.createNewFile()

			RandomAccessFile(srcFile, "rws").use {
				it.write(buffer)
			}

			val canRename = srcFile.renameTo(dstFile)

			srcFile.delete()
			dstFile.delete()

			return@runCatching canRename
		}.getOrNull() ?: return false
	}

	fun getFileProgress(location: String?): Float? {
		if (location == null) {
			return null
		}

		val progress = fileProgresses[location] ?: return null

		if (progress[1] == 0L) {
			return 0.0f
		}

		return min(1.0f, (progress[0] / progress[1].toFloat()))
	}

	fun getFileProgressSizes(location: String?): LongArray? {
		if (location == null) {
			return null
		}

		return fileProgresses[location]
	}

	fun getReplacedKey(oldKey: String?): String? {
		if (oldKey == null) {
			return null
		}

		return replacedBitmaps[oldKey]
	}

	private fun performReplace(oldKey: String, newKey: String) {
		var currentCache = memCache
		var b = currentCache[oldKey]

		if (b == null) {
			currentCache = smallImagesMemCache
			b = currentCache[oldKey]
		}

		replacedBitmaps[oldKey] = newKey

		if (b != null) {
			val oldBitmap = currentCache[newKey]
			var doNotChange = false

			if (oldBitmap != null && oldBitmap.bitmap != null && b.bitmap != null) {
				val oldBitmapObject = oldBitmap.bitmap
				val newBitmapObject = b.bitmap

				if (oldBitmapObject.width > newBitmapObject.width || oldBitmapObject.height > newBitmapObject.height) {
					doNotChange = true
				}
			}

			if (!doNotChange) {
				ignoreRemoval = oldKey
				currentCache.remove(oldKey)
				currentCache.put(newKey, b)
				ignoreRemoval = null
			}
			else {
				currentCache.remove(oldKey)
			}
		}

		val `val` = bitmapUseCounts[oldKey]

		if (`val` != null) {
			bitmapUseCounts[newKey] = `val`
			bitmapUseCounts.remove(oldKey)
		}
	}

	fun incrementUseCount(key: String) {
		val count = bitmapUseCounts[key]

		if (count == null) {
			bitmapUseCounts[key] = 1
		}
		else {
			bitmapUseCounts[key] = count + 1
		}
	}

	fun decrementUseCount(key: String): Boolean {
		val count = bitmapUseCounts[key] ?: return true

		if (count == 1) {
			bitmapUseCounts.remove(key)
			return true
		}
		else {
			bitmapUseCounts[key] = count - 1
		}

		return false
	}

	fun removeImage(key: String?) {
		bitmapUseCounts.remove(key)
		memCache.remove(key)
		smallImagesMemCache.remove(key)
	}

	fun isInMemCache(key: String?, animated: Boolean): Boolean {
		return if (animated) {
			getFromLottieCache(key) != null
		}
		else {
			getFromMemCache(key) != null
		}
	}

	fun clearMemory() {
		smallImagesMemCache.evictAll()
		memCache.evictAll()
		lottieMemCache.evictAll()
	}

	private fun removeFromWaitingForThumb(tag: Int, imageReceiver: ImageReceiver) {
		val location = waitingForQualityThumbByTag[tag]

		if (location != null) {
			val info = waitingForQualityThumb[location]

			if (info != null) {
				val index = info.imageReceiverArray.indexOf(imageReceiver)

				if (index >= 0) {
					info.imageReceiverArray.removeAt(index)
					info.imageReceiverGuidsArray.removeAt(index)
				}

				if (info.imageReceiverArray.isEmpty()) {
					waitingForQualityThumb.remove(location)
				}
			}

			waitingForQualityThumbByTag.remove(tag)
		}
	}

	fun cancelLoadingForImageReceiver(imageReceiver: ImageReceiver?, cancelAll: Boolean) {
		if (imageReceiver == null) {
			return
		}

		val runnables = imageReceiver.loadingOperations

		for (runnable in runnables) {
			imageLoadQueue.cancelRunnable(runnable)
		}

		runnables.clear()

		imageReceiver.addLoadingImageRunnable(null)

		imageLoadQueue.postRunnable({
			for (a in 0..2) {
				if (a > 0 && !cancelAll) {
					return@postRunnable
				}

				val type = when (a) {
					0 -> ImageReceiver.TYPE_THUMB
					1 -> ImageReceiver.TYPE_IMAGE
					else -> ImageReceiver.TYPE_MEDIA
				}

				val tag = imageReceiver.getTag(type)

				if (tag != 0) {
					if (a == 0) {
						removeFromWaitingForThumb(tag, imageReceiver)
					}

					val ei = imageLoadingByTag[tag]
					ei?.removeImageReceiver(imageReceiver)
				}
			}
		}, if (imageReceiver.fileLoadingPriority == FileLoader.PRIORITY_LOW) 0L else 1L)
	}

	fun getImageFromMemory(fileLocation: TLObject?, httpUrl: String?, filter: String?): BitmapDrawable? {
		if (fileLocation == null && httpUrl == null) {
			return null
		}

		var key: String? = null

		if (httpUrl != null) {
			key = Utilities.MD5(httpUrl)
		}
		else {
			when (fileLocation) {
				is FileLocation -> key = fileLocation.volume_id.toString() + "_" + fileLocation.local_id
				is TLRPC.Document -> key = fileLocation.dc_id.toString() + "_" + fileLocation.id
				is SecureDocument -> key = fileLocation.secureFile.dc_id.toString() + "_" + fileLocation.secureFile.id
				is WebFile -> key = Utilities.MD5(fileLocation.url)
			}
		}

		if (filter != null) {
			key += "@$filter"
		}

		return getFromMemCache(key)
	}

	private fun replaceImageInCacheInternal(oldKey: String, newKey: String, newLocation: ImageLocation) {
		for (i in 0..1) {
			val arr = if (i == 0) {
				memCache.getFilterKeys(oldKey)
			}
			else {
				smallImagesMemCache.getFilterKeys(oldKey)
			}

			if (arr != null) {
				for (a in arr.indices) {
					val filter = arr[a]
					val oldK = "$oldKey@$filter"
					val newK = "$newKey@$filter"

					performReplace(oldK, newK)

					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.didReplacedPhotoInMemCache, oldK, newK, newLocation)
				}
			}
			else {
				performReplace(oldKey, newKey)

				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.didReplacedPhotoInMemCache, oldKey, newKey, newLocation)
			}
		}
	}

	fun replaceImageInCache(oldKey: String, newKey: String, newLocation: ImageLocation, post: Boolean) {
		if (post) {
			AndroidUtilities.runOnUIThread {
				replaceImageInCacheInternal(oldKey, newKey, newLocation)
			}
		}
		else {
			replaceImageInCacheInternal(oldKey, newKey, newLocation)
		}
	}

	fun putImageToCache(bitmap: BitmapDrawable, key: String?, smallImage: Boolean) {
		if (smallImage) {
			smallImagesMemCache.put(key, bitmap)
		}
		else {
			memCache.put(key, bitmap)
		}
	}

	private fun generateThumb(mediaType: Int, originalPath: File?, info: ThumbGenerateInfo?) {
		if (mediaType != FileLoader.MEDIA_DIR_IMAGE && mediaType != FileLoader.MEDIA_DIR_VIDEO && mediaType != FileLoader.MEDIA_DIR_DOCUMENT || originalPath == null || info == null) {
			return
		}

		val name = FileLoader.getAttachFileName(info.parentDocument)
		var task = thumbGenerateTasks[name]

		if (task == null) {
			task = ThumbGenerateTask(mediaType, originalPath, info)

			thumbGeneratingQueue.postRunnable(task)
		}
	}

	fun cancelForceLoadingForImageReceiver(imageReceiver: ImageReceiver?) {
		val key = imageReceiver?.imageKey ?: return

		imageLoadQueue.postRunnable {
			forceLoadingImages.remove(key)
		}
	}

	private fun createLoadOperationForImageReceiver(imageReceiver: ImageReceiver?, key: String?, url: String?, ext: String, imageLocation: ImageLocation?, filter: String?, size: Long, cacheType: Int, type: Int, thumb: Int, guid: Int) {
		// TODO: here is the problem with loading static map images
		if (imageReceiver == null) {
			return
		}

		@Suppress("NAME_SHADOWING") val imageLocation = imageLocation ?: imageReceiver.imageLocation
		@Suppress("NAME_SHADOWING") val key = key ?: imageReceiver.imageKey
		@Suppress("NAME_SHADOWING") val url = url ?: imageLocation?.webFile?.url

		if (url.isNullOrEmpty() || key.isNullOrEmpty() || imageLocation == null) {
			return
		}

		var tag = imageReceiver.getTag(type)

		if (tag == 0) {
			imageReceiver.setTag(lastImageNum.also { tag = it }, type)

			lastImageNum++

			if (lastImageNum == Int.MAX_VALUE) {
				lastImageNum = 0
			}
		}

		val finalTag = tag
		val finalIsNeedsQualityThumb = imageReceiver.isNeedsQualityThumb
		val parentObject = imageReceiver.parentObject
		val qualityDocument = imageReceiver.qualityThumbDocument
		val shouldGenerateQualityThumb = imageReceiver.isShouldGenerateQualityThumb
		val currentAccount = imageReceiver.currentAccount
		val currentKeyQuality = type == ImageReceiver.TYPE_IMAGE && imageReceiver.isCurrentKeyQuality

		val loadOperationRunnable = Runnable {
			var added = false

			if (thumb != 2) {
				val alreadyLoadingUrl = imageLoadingByUrl[url]
				val alreadyLoadingCache = imageLoadingByKeys[key]
				val alreadyLoadingImage = imageLoadingByTag[finalTag]

				if (alreadyLoadingImage != null) {
					if (alreadyLoadingImage === alreadyLoadingCache) {
						alreadyLoadingImage.setImageReceiverGuid(imageReceiver, guid)
						added = true
					}
					else if (alreadyLoadingImage === alreadyLoadingUrl) {
						if (alreadyLoadingCache == null) {
							alreadyLoadingImage.replaceImageReceiver(imageReceiver, key, filter, type, guid)
						}

						added = true
					}
					else {
						alreadyLoadingImage.removeImageReceiver(imageReceiver)
					}
				}

				if (!added && alreadyLoadingCache != null) {
					alreadyLoadingCache.addImageReceiver(imageReceiver, key, filter, type, guid)
					added = true
				}

				if (!added && alreadyLoadingUrl != null) {
					alreadyLoadingUrl.addImageReceiver(imageReceiver, key, filter, type, guid)
					added = true
				}
			}

			if (!added) {
				var onlyCache = false
				var cacheFile: File? = null
				var cacheFileExists = false
				val imageLocationPath = imageLocation.path

				if (imageLocationPath != null) {
					if (!imageLocationPath.startsWith("http") && !imageLocationPath.startsWith("athumb")) {
						onlyCache = true

						if (imageLocationPath.startsWith("thumb://")) {
							val idx = imageLocationPath.indexOf(":", 8)

							if (idx >= 0) {
								cacheFile = File(imageLocationPath.substring(idx + 1))
							}
						}
						else if (imageLocationPath.startsWith("vthumb://")) {
							val idx = imageLocationPath.indexOf(":", 9)

							if (idx >= 0) {
								cacheFile = File(imageLocationPath.substring(idx + 1))
							}
						}
						else {
							cacheFile = File(imageLocationPath)
						}
					}
				}
				else if (thumb == 0 && currentKeyQuality) {
					onlyCache = true

					val parentDocument: TLRPC.Document?
					val localPath: String?
					val cachePath: File?
					val mediaType: Int
					val bigThumb: Boolean

					if (parentObject is MessageObject) {
						parentDocument = parentObject.document
						localPath = parentObject.messageOwner!!.attachPath
						cachePath = FileLoader.getInstance(currentAccount).getPathToMessage(parentObject.messageOwner)
						mediaType = parentObject.mediaType
						bigThumb = false
					}
					else if (qualityDocument != null) {
						parentDocument = qualityDocument
						cachePath = FileLoader.getInstance(currentAccount).getPathToAttach(parentDocument, true)

						mediaType = if (isVideoDocument(parentDocument)) {
							FileLoader.MEDIA_DIR_VIDEO
						}
						else {
							FileLoader.MEDIA_DIR_DOCUMENT
						}

						localPath = null
						bigThumb = true
					}
					else {
						parentDocument = null
						localPath = null
						cachePath = null
						mediaType = 0
						bigThumb = false
					}

					if (parentDocument != null) {
						if (finalIsNeedsQualityThumb) {
							cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "q_" + parentDocument.dc_id + "_" + parentDocument.id + ".jpg")

							if (!cacheFile.exists()) {
								cacheFile = null
							}
							else {
								cacheFileExists = true
							}
						}

						var attachPath: File? = null

						if (!localPath.isNullOrEmpty()) {
							attachPath = File(localPath)

							if (!attachPath.exists()) {
								attachPath = null
							}
						}

						if (attachPath == null) {
							attachPath = cachePath
						}

						if (cacheFile == null) {
							val location = FileLoader.getAttachFileName(parentDocument)

							var info = waitingForQualityThumb[location]
							if (info == null) {
								info = ThumbGenerateInfo()
								info.parentDocument = parentDocument
								info.filter = filter
								info.big = bigThumb

								waitingForQualityThumb[location] = info
							}

							if (!info.imageReceiverArray.contains(imageReceiver)) {
								info.imageReceiverArray.add(imageReceiver)
								info.imageReceiverGuidsArray.add(guid)
							}

							waitingForQualityThumbByTag.put(finalTag, location)

							if (attachPath?.exists() == true && shouldGenerateQualityThumb) {
								generateThumb(mediaType, attachPath, info)
							}

							return@Runnable
						}
					}
				}

				if (thumb != 2) {
					val isEncrypted = imageLocation.isEncrypted

					val img = CacheImage()
					img.priority = if (imageReceiver.fileLoadingPriority == FileLoader.PRIORITY_LOW) 0 else 1

					if (!currentKeyQuality) {
						if (imageLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION || isGifDocument(imageLocation.webFile) || isGifDocument(imageLocation.document) || isRoundVideoDocument(imageLocation.document) || isVideoSticker(imageLocation.document)) {
							img.imageType = FileLoader.IMAGE_TYPE_ANIMATION
						}
						else if (imageLocation.path != null) {
							val location = imageLocation.path!!

							if (!location.startsWith("vthumb") && !location.startsWith("thumb")) {
								val trueExt = getHttpUrlExtension(location, "jpg")

								if (trueExt == "webm" || trueExt == "mp4" || trueExt == "gif") {
									img.imageType = FileLoader.IMAGE_TYPE_ANIMATION
								}
								else if ("tgs" == ext) {
									img.imageType = FileLoader.IMAGE_TYPE_LOTTIE
								}
							}
						}
					}

					if (cacheFile == null) {
						var fileSize: Long = 0

						if (imageLocation.photoSize is TL_photoStrippedSize || imageLocation.photoSize is TL_photoPathSize) {
							onlyCache = true
						}
						else if (imageLocation.secureDocument != null) {
							img.secureDocument = imageLocation.secureDocument
							onlyCache = img.secureDocument?.secureFile?.dc_id == Int.MIN_VALUE
							cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url)
						}
						else if (!(AUTOPLAY_FILTER == filter || isAnimatedAvatar(filter)) && (cacheType != 0 || size <= 0 || imageLocation.path != null || isEncrypted)) {
							cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url)

							if (cacheFile.exists()) {
								cacheFileExists = true
							}
							else if (cacheType == 2) {
								cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$url.enc")
							}

							if (imageLocation.document != null) {
								if (imageLocation.document is ThemeDocument) {
									val themeDocument = imageLocation.document as ThemeDocument

									if (themeDocument.wallpaper == null) {
										onlyCache = true
									}

									img.imageType = FileLoader.IMAGE_TYPE_THEME_PREVIEW
								}
								else if ("application/x-tgsdice" == imageLocation.document?.mime_type) {
									img.imageType = FileLoader.IMAGE_TYPE_LOTTIE
									onlyCache = true
								}
								else if ("application/x-tgsticker" == imageLocation.document?.mime_type) {
									img.imageType = FileLoader.IMAGE_TYPE_LOTTIE
								}
								else if ("application/x-tgwallpattern" == imageLocation.document?.mime_type) {
									img.imageType = FileLoader.IMAGE_TYPE_SVG
								}
								else {
									val name = FileLoader.getDocumentFileName(imageLocation.document)

									if (name?.endsWith(".svg") == true) {
										img.imageType = FileLoader.IMAGE_TYPE_SVG
									}
								}
							}
						}
						else if (imageLocation.document != null) {
							val document = imageLocation.document

							cacheFile = if (document is TL_documentEncrypted) {
								File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url)
							}
							else if (isVideoDocument(document)) {
								File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_VIDEO), url)
							}
							else {
								File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), url)
							}

							if ((isAnimatedAvatar(filter) || AUTOPLAY_FILTER == filter) && !cacheFile.exists()) {
								cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), document?.dc_id?.toString() + "_" + document?.id + ".temp")
							}

							if (document is ThemeDocument) {
								if (document.wallpaper == null) {
									onlyCache = true
								}

								img.imageType = FileLoader.IMAGE_TYPE_THEME_PREVIEW
							}
							else if ("application/x-tgsdice" == imageLocation.document?.mime_type) {
								img.imageType = FileLoader.IMAGE_TYPE_LOTTIE
								onlyCache = true
							}
							else if ("application/x-tgsticker" == document?.mime_type) {
								img.imageType = FileLoader.IMAGE_TYPE_LOTTIE
							}
							else if ("application/x-tgwallpattern" == document?.mime_type) {
								img.imageType = FileLoader.IMAGE_TYPE_SVG
							}
							else {
								val name = FileLoader.getDocumentFileName(imageLocation.document)

								if (name?.endsWith(".svg") == true) {
									img.imageType = FileLoader.IMAGE_TYPE_SVG
								}
							}

							fileSize = document?.size ?: 0
						}
						else if (imageLocation.webFile != null) {
							cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), url)
						}
						else {
							cacheFile = if (cacheType == 1) {
								File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url)
							}
							else {
								File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE), url)
							}

							if (isAnimatedAvatar(filter) || AUTOPLAY_FILTER == filter && imageLocation.location != null && !cacheFile.exists()) {
								cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), imageLocation.location?.volume_id?.toString() + "_" + imageLocation.location?.local_id + ".temp")
							}
						}

						if (hasAutoplayFilter(filter) || isAnimatedAvatar(filter)) {
							img.imageType = FileLoader.IMAGE_TYPE_ANIMATION
							img.size = fileSize

							if (AUTOPLAY_FILTER == filter || isAnimatedAvatar(filter)) {
								onlyCache = true
							}
						}
					}

					img.type = type
					img.key = key
					img.filter = filter
					img.imageLocation = imageLocation
					img.ext = ext
					img.currentAccount = currentAccount
					img.parentObject = parentObject

					if (imageLocation.imageType != 0) {
						img.imageType = imageLocation.imageType
					}
					if (cacheType == 2) {
						img.encryptionKeyPath = File(FileLoader.internalCacheDir, "$url.enc.key")
					}

					img.addImageReceiver(imageReceiver, key, filter, type, guid)

					if (onlyCache || cacheFileExists || cacheFile?.exists() == true) {
						img.finalFilePath = cacheFile
						img.imageLocation = imageLocation
						img.cacheTask = CacheOutTask(img)

						imageLoadingByKeys[key] = img

						if (thumb != 0) {
							cacheThumbOutQueue.postRunnable(img.cacheTask)
						}
						else {
							img.runningTask = cacheOutQueue.postRunnable(img.cacheTask, img.priority)
						}
					}
					else {
						img.url = url

						imageLoadingByUrl[url] = img

						if (imageLocation.path != null) {
							val file = Utilities.MD5(imageLocation.path)
							val cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)

							img.tempFilePath = File(cacheDir, file + "_temp.jpg")
							img.finalFilePath = cacheFile

							if (imageLocation.path?.startsWith("athumb") == true) {
								img.artworkTask = ArtworkLoadTask(img).also {
									artworkTasks.add(it)
								}

								runArtworkTasks(false)
							}
							else {
								img.httpTask = HttpImageTask(img, size).also {
									httpTasks.add(it)
								}

								runHttpTasks(false)
							}
						}
						else {
							val loadingPriority = if (thumb != 0) FileLoader.PRIORITY_HIGH else imageReceiver.fileLoadingPriority

							if (imageLocation.location != null) {
								var localCacheType = cacheType

								if (localCacheType == 0 && (size <= 0 || imageLocation.key != null)) {
									localCacheType = 1
								}

								FileLoader.getInstance(currentAccount).loadFile(imageLocation, parentObject, ext, loadingPriority, localCacheType)
							}
							else if (imageLocation.document != null) {
								FileLoader.getInstance(currentAccount).loadFile(imageLocation.document, parentObject, loadingPriority, cacheType)
							}
							else if (imageLocation.secureDocument != null) {
								FileLoader.getInstance(currentAccount).loadFile(imageLocation.secureDocument, loadingPriority)
							}
							else if (imageLocation.webFile != null) {
								FileLoader.getInstance(currentAccount).loadFile(imageLocation.webFile, loadingPriority, cacheType)
							}

							if (imageReceiver.isForceLoading) {
								img.key?.let {
									forceLoadingImages[it] = 0
								}
							}
						}
					}
				}
			}
		}

		imageLoadQueue.postRunnable(loadOperationRunnable, (if (imageReceiver.fileLoadingPriority == FileLoader.PRIORITY_LOW) 0 else 1).toLong())

		imageReceiver.addLoadingImageRunnable(loadOperationRunnable)
	}

	fun preloadArtwork(athumbUrl: String?) {
		imageLoadQueue.postRunnable {
			val ext = getHttpUrlExtension(athumbUrl, "jpg")
			val url = Utilities.MD5(athumbUrl) + "." + ext
			val cacheFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), url)

			if (cacheFile.exists()) {
				return@postRunnable
			}

			val imageLocation = ImageLocation.getForPath(athumbUrl)

			val img = CacheImage()
			img.type = ImageReceiver.TYPE_THUMB
			img.key = Utilities.MD5(athumbUrl)
			img.filter = null
			img.imageLocation = imageLocation
			img.ext = ext
			img.parentObject = null

			if (imageLocation?.imageType != 0) {
				img.imageType = imageLocation?.imageType ?: 0
			}

			img.url = url

			imageLoadingByUrl[url] = img

			val file = Utilities.MD5(imageLocation?.path)
			val cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)

			img.tempFilePath = File(cacheDir, file + "_temp.jpg")
			img.finalFilePath = cacheFile

			img.artworkTask = ArtworkLoadTask(img).also {
				artworkTasks.add(it)
			}

			runArtworkTasks(false)
		}
	}

	fun loadImageForImageReceiver(imageReceiver: ImageReceiver?) {
		if (imageReceiver == null) {
			return
		}

		var imageSet = false
		var mediaSet = false
		var mediaKey = imageReceiver.mediaKey
		val guid = imageReceiver.newGuid

		if (mediaKey != null) {
			val mediaLocation = imageReceiver.mediaLocation
			var drawable: Drawable?

			if (useLottieMemCache(mediaLocation, mediaKey)) {
				drawable = getFromLottieCache(mediaKey)
			}
			else {
				drawable = memCache[mediaKey]

				if (drawable != null) {
					memCache.moveToFront(mediaKey)
				}

				if (drawable == null) {
					drawable = smallImagesMemCache[mediaKey]

					if (drawable != null) {
						smallImagesMemCache.moveToFront(mediaKey)
					}
				}

				if (drawable == null) {
					drawable = wallpaperMemCache[mediaKey]

					if (drawable != null) {
						wallpaperMemCache.moveToFront(mediaKey)
					}
				}
			}

			var hasBitmap = true

			if (drawable is RLottieDrawable) {
				hasBitmap = drawable.hasBitmap()
			}
			else if (drawable is AnimatedFileDrawable) {
				hasBitmap = drawable.hasBitmap()
			}

			if (hasBitmap && drawable != null) {
				cancelLoadingForImageReceiver(imageReceiver, true)

				imageReceiver.setImageBitmapByKey(drawable, mediaKey, ImageReceiver.TYPE_MEDIA, true, guid)

				imageSet = true

				if (!imageReceiver.isForcePreview) {
					return
				}
			}
			else if (drawable != null) {
				mediaSet = true
				imageReceiver.setImageBitmapByKey(drawable, mediaKey, ImageReceiver.TYPE_MEDIA, true, guid)
			}
		}

		var imageKey = imageReceiver.imageKey

		if (!imageSet && imageKey != null) {
			val imageLocation = imageReceiver.imageLocation
			var drawable: Drawable? = null

			if (useLottieMemCache(imageLocation, imageKey)) {
				drawable = getFromLottieCache(imageKey)
			}

			if (drawable == null) {
				drawable = memCache[imageKey]

				if (drawable != null) {
					memCache.moveToFront(imageKey)
				}

				if (drawable == null) {
					drawable = smallImagesMemCache[imageKey]

					if (drawable != null) {
						smallImagesMemCache.moveToFront(imageKey)
					}
				}

				if (drawable == null) {
					drawable = wallpaperMemCache[imageKey]

					if (drawable != null) {
						wallpaperMemCache.moveToFront(imageKey)
					}
				}
			}

			if (drawable != null) {
				cancelLoadingForImageReceiver(imageReceiver, true)

				imageReceiver.setImageBitmapByKey(drawable, imageKey, ImageReceiver.TYPE_IMAGE, true, guid)

				imageSet = true

				if (!imageReceiver.isForcePreview && (mediaKey == null || mediaSet)) {
					return
				}
			}
		}

		var thumbSet = false
		var thumbKey = imageReceiver.thumbKey

		if (thumbKey != null) {
			val thumbLocation = imageReceiver.thumbLocation
			var drawable: Drawable?

			if (useLottieMemCache(thumbLocation, thumbKey)) {
				drawable = getFromLottieCache(thumbKey)
			}
			else {
				drawable = memCache[thumbKey]

				if (drawable != null) {
					memCache.moveToFront(thumbKey)
				}

				if (drawable == null) {
					drawable = smallImagesMemCache[thumbKey]

					if (drawable != null) {
						smallImagesMemCache.moveToFront(thumbKey)
					}
				}

				if (drawable == null) {
					drawable = wallpaperMemCache[thumbKey]

					if (drawable != null) {
						wallpaperMemCache.moveToFront(thumbKey)
					}
				}
			}

			if (drawable != null) {
				imageReceiver.setImageBitmapByKey(drawable, thumbKey, ImageReceiver.TYPE_THUMB, true, guid)

				cancelLoadingForImageReceiver(imageReceiver, false)

				if (imageSet && imageReceiver.isForcePreview) {
					return
				}

				thumbSet = true
			}
		}

		var qualityThumb = false
		val parentObject = imageReceiver.parentObject
		val qualityDocument = imageReceiver.qualityThumbDocument
		val thumbLocation = imageReceiver.thumbLocation
		val thumbFilter = imageReceiver.thumbFilter
		var mediaLocation = imageReceiver.mediaLocation
		val mediaFilter = imageReceiver.mediaFilter
		val originalImageLocation = imageReceiver.imageLocation
		val imageFilter = imageReceiver.imageFilter
		var imageLocation = originalImageLocation

		if (imageLocation == null && imageReceiver.isNeedsQualityThumb && imageReceiver.isCurrentKeyQuality) {
			if (parentObject is MessageObject) {
				imageLocation = ImageLocation.getForDocument(parentObject.document)
				qualityThumb = true
			}
			else if (qualityDocument != null) {
				imageLocation = ImageLocation.getForDocument(qualityDocument)
				qualityThumb = true
			}
		}

		var saveImageToCache = false
		var imageUrl: String? = null
		var thumbUrl: String? = null
		var mediaUrl: String? = null

		imageKey = null
		thumbKey = null
		mediaKey = null

		var imageExt = if (imageLocation != null && imageLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
			"mp4"
		}
		else {
			null
		}

		var mediaExt = if (mediaLocation != null && mediaLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
			"mp4"
		}
		else {
			null
		}

		var thumbExt = imageReceiver.ext

		if (thumbExt == null) {
			thumbExt = "jpg"
		}

		if (imageExt == null) {
			imageExt = thumbExt
		}

		if (mediaExt == null) {
			mediaExt = thumbExt
		}

		for (a in 0..1) {
			var `object`: ImageLocation?
			var ext: String

			if (a == 0) {
				`object` = imageLocation
				ext = imageExt
			}
			else {
				`object` = mediaLocation
				ext = mediaExt
			}

			if (`object` == null) {
				continue
			}

			var key = `object`.getKey(parentObject, mediaLocation ?: imageLocation, false)

			if (key == null) {
				continue
			}

			var url = `object`.getKey(parentObject, mediaLocation ?: imageLocation, true)

			if (`object`.path != null) {
				url = url + "." + getHttpUrlExtension(`object`.path, "jpg")
			}
			else if (`object`.photoSize is TL_photoStrippedSize || `object`.photoSize is TL_photoPathSize) {
				url = "$url.$ext"
			}
			else if (`object`.location != null) {
				url = "$url.$ext"

				if (imageReceiver.ext != null || `object`.location?.key != null || `object`.location?.volume_id == Int.MIN_VALUE.toLong() && (`object`.location?.local_id ?: 0) < 0) {
					saveImageToCache = true
				}
			}
			else if (`object`.webFile != null) {
				val defaultExt = FileLoader.getMimeTypePart(`object`.webFile?.mimeType ?: "")
				url = url + "." + getHttpUrlExtension(`object`.webFile?.url, defaultExt)
			}
			else if (`object`.secureDocument != null) {
				url = "$url.$ext"
			}
			else if (`object`.document != null) {
				if (a == 0 && qualityThumb) {
					key = "q_$key"
				}

				var docExt = FileLoader.getDocumentFileName(`object`.document)
				var idx: Int

				if ((docExt?.lastIndexOf('.').also { idx = it ?: -1 }) == -1) {
					docExt = ""
				}
				else {
					docExt = docExt?.substring(idx) ?: ""
				}

				if (docExt.length <= 1) {
					docExt = when (`object`.document?.mime_type) {
						"video/mp4" -> ".mp4"
						"video/x-matroska" -> ".mkv"
						else -> ""
					}
				}

				url += docExt
				saveImageToCache = !isVideoDocument(`object`.document) && !isGifDocument(`object`.document) && !isRoundVideoDocument(`object`.document) && !canPreviewDocument(`object`.document)
			}

			if (a == 0) {
				imageKey = key
				imageUrl = url
			}
			else {
				mediaKey = key
				mediaUrl = url
			}

			if (`object` === thumbLocation) {
				if (a == 0) {
					imageLocation = null
					imageKey = null
					imageUrl = null
				}
				else {
					mediaLocation = null
					mediaKey = null
					mediaUrl = null
				}
			}
		}

		if (thumbLocation != null) {
			var strippedLoc = imageReceiver.strippedLocation

			if (strippedLoc == null) {
				strippedLoc = mediaLocation ?: originalImageLocation
			}

			thumbKey = thumbLocation.getKey(parentObject, strippedLoc, false)
			thumbUrl = thumbLocation.getKey(parentObject, strippedLoc, true)

			if (thumbLocation.path != null) {
				thumbUrl = thumbUrl + "." + getHttpUrlExtension(thumbLocation.path, "jpg")
			}
			else if (thumbLocation.photoSize is TL_photoStrippedSize || thumbLocation.photoSize is TL_photoPathSize) {
				thumbUrl = "$thumbUrl.$thumbExt"
			}
			else if (thumbLocation.location != null) {
				thumbUrl = "$thumbUrl.$thumbExt"
			}
		}

		if (mediaKey != null && mediaFilter != null) {
			mediaKey += "@$mediaFilter"
		}

		if (imageKey != null && imageFilter != null) {
			imageKey += "@$imageFilter"
		}

		if (thumbKey != null && thumbFilter != null) {
			thumbKey += "@$thumbFilter"
		}

		if (imageReceiver.uniqueKeyPrefix != null && imageKey != null) {
			imageKey = imageReceiver.uniqueKeyPrefix + imageKey
		}

		if (imageReceiver.uniqueKeyPrefix != null && mediaKey != null) {
			mediaKey = imageReceiver.uniqueKeyPrefix + mediaKey
		}

		if (imageLocation?.path != null) {
			createLoadOperationForImageReceiver(imageReceiver, thumbKey, thumbUrl, thumbExt, thumbLocation, thumbFilter, 0, 1, ImageReceiver.TYPE_THUMB, if (thumbSet) 2 else 1, guid)
			createLoadOperationForImageReceiver(imageReceiver, imageKey, imageUrl, imageExt, imageLocation, imageFilter, imageReceiver.size, 1, ImageReceiver.TYPE_IMAGE, 0, guid)
		}
		else if (mediaLocation != null) {
			var mediaCacheType = imageReceiver.cacheType
			val imageCacheType = 1

			if (mediaCacheType == 0 && saveImageToCache) {
				mediaCacheType = 1
			}

			val thumbCacheType = if (mediaCacheType == 0) 1 else mediaCacheType

			if (!thumbSet) {
				createLoadOperationForImageReceiver(imageReceiver, thumbKey, thumbUrl, thumbExt, thumbLocation, thumbFilter, 0, thumbCacheType, ImageReceiver.TYPE_THUMB, 1, guid)
			}

			if (!imageSet) {
				createLoadOperationForImageReceiver(imageReceiver, imageKey, imageUrl, imageExt, imageLocation, imageFilter, 0, imageCacheType, ImageReceiver.TYPE_IMAGE, 0, guid)
			}

			if (!mediaSet) {
				createLoadOperationForImageReceiver(imageReceiver, mediaKey, mediaUrl, mediaExt, mediaLocation, mediaFilter, imageReceiver.size, mediaCacheType, ImageReceiver.TYPE_MEDIA, 0, guid)
			}
		}
		else {
			var imageCacheType = imageReceiver.cacheType

			if (imageCacheType == 0 && saveImageToCache) {
				imageCacheType = 1
			}

			val thumbCacheType = if (imageCacheType == 0) 1 else imageCacheType

			createLoadOperationForImageReceiver(imageReceiver, thumbKey, thumbUrl, thumbExt, thumbLocation, thumbFilter, 0, thumbCacheType, ImageReceiver.TYPE_THUMB, if (thumbSet) 2 else 1, guid)
			createLoadOperationForImageReceiver(imageReceiver, imageKey, imageUrl, imageExt, imageLocation, imageFilter, imageReceiver.size, imageCacheType, ImageReceiver.TYPE_IMAGE, 0, guid)
		}
	}

	private fun getFromLottieCache(imageKey: String?): BitmapDrawable? {
		var drawable = lottieMemCache[imageKey]

		if (drawable is AnimatedFileDrawable) {
			if (drawable.isRecycled) {
				lottieMemCache.remove(imageKey)
				drawable = null
			}
		}

		return drawable
	}

	private fun useLottieMemCache(imageLocation: ImageLocation?, key: String): Boolean {
		return imageLocation != null && (isAnimatedStickerDocument(imageLocation.document, true) || imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE || isVideoSticker(imageLocation.document)) || isAnimatedAvatar(key)
	}

	fun hasLottieMemCache(key: String?): Boolean {
		return lottieMemCache.contains(key)
	}

	private fun httpFileLoadError(location: String?) {
		imageLoadQueue.postRunnable {
			val img = imageLoadingByUrl[location] ?: return@postRunnable
			val oldTask = img.httpTask

			if (oldTask != null) {
				img.httpTask = HttpImageTask(oldTask.cacheImage, oldTask.imageSize).also {
					httpTasks.add(it)
				}
			}

			runHttpTasks(false)
		}
	}

	private fun artworkLoadError(location: String?) {
		imageLoadQueue.postRunnable {
			val img = imageLoadingByUrl[location] ?: return@postRunnable
			val oldTask = img.artworkTask

			if (oldTask != null) {
				img.artworkTask = ArtworkLoadTask(oldTask.cacheImage).also {
					artworkTasks.add(it)
				}
			}

			runArtworkTasks(false)
		}
	}

	private fun fileDidLoaded(location: String?, finalFile: File?, mediaType: Int) {
		imageLoadQueue.postRunnable {
			val info = waitingForQualityThumb[location]

			if (info?.parentDocument != null) {
				generateThumb(mediaType, finalFile, info)
				waitingForQualityThumb.remove(location)
			}

			val img = imageLoadingByUrl[location] ?: return@postRunnable

			imageLoadingByUrl.remove(location)

			val tasks = mutableListOf<CacheOutTask>()

			for (a in img.imageReceiverArray.indices) {
				val key = img.keys[a]
				val filter = img.filters[a]
				val type = img.types[a]
				val imageReceiver = img.imageReceiverArray[a]
				val guid = img.imageReceiverGuidsArray[a]
				var cacheImage = imageLoadingByKeys[key]

				if (cacheImage == null) {
					cacheImage = CacheImage()
					cacheImage.priority = img.priority
					cacheImage.secureDocument = img.secureDocument
					cacheImage.currentAccount = img.currentAccount
					cacheImage.finalFilePath = finalFile
					cacheImage.parentObject = img.parentObject
					cacheImage.key = key
					cacheImage.imageLocation = img.imageLocation
					cacheImage.type = type
					cacheImage.ext = img.ext
					cacheImage.encryptionKeyPath = img.encryptionKeyPath

					cacheImage.cacheTask = CacheOutTask(cacheImage).also {
						tasks.add(it)
					}

					cacheImage.filter = filter
					cacheImage.imageType = img.imageType

					imageLoadingByKeys[key] = cacheImage
				}

				cacheImage.addImageReceiver(imageReceiver, key, filter, type, guid)
			}

			for (task in tasks) {
				if (task.cacheImage.type == ImageReceiver.TYPE_THUMB) {
					cacheThumbOutQueue.postRunnable(task)
				}
				else {
					cacheOutQueue.postRunnable(task, task.cacheImage.priority)
				}
			}
		}
	}

	private fun fileDidFailedLoad(location: String?, canceled: Int) {
		if (canceled == 1) {
			return
		}

		imageLoadQueue.postRunnable {
			val img = imageLoadingByUrl[location]
			img?.setImageAndClear(null, null)
		}
	}

	private fun runHttpTasks(complete: Boolean) {
		if (complete) {
			currentHttpTasksCount--
		}

		while (currentHttpTasksCount < 4 && !httpTasks.isEmpty()) {
			val task = httpTasks.poll()

			if (task != null) {
				task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null)
				currentHttpTasksCount += 1
			}
		}
	}

	private fun runArtworkTasks(complete: Boolean) {
		if (complete) {
			currentArtworkTasksCount--
		}

		while (currentArtworkTasksCount < 4 && !artworkTasks.isEmpty()) {
			try {
				val task = artworkTasks.poll()
				task?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null)

				currentArtworkTasksCount += 1
			}
			catch (ignore: Throwable) {
				runArtworkTasks(false)
			}
		}
	}

	fun isLoadingHttpFile(url: String): Boolean {
		return httpFileLoadTasksByKeys.containsKey(url)
	}

	fun loadHttpFile(url: String?, defaultExt: String?, currentAccount: Int) {
		if (url.isNullOrEmpty() || httpFileLoadTasksByKeys.containsKey(url)) {
			return
		}

		val ext = getHttpUrlExtension(url, defaultExt)

		val file = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(url) + "_temp." + ext)
		file.delete()

		val task = HttpFileTask(url, file, ext, currentAccount)

		httpFileLoadTasks.add(task)
		httpFileLoadTasksByKeys[url] = task

		runHttpFileLoadTasks(null, 0)
	}

	fun cancelLoadHttpFile(url: String) {
		val task = httpFileLoadTasksByKeys[url]

		if (task != null) {
			task.cancel(true)
			httpFileLoadTasksByKeys.remove(url)
			httpFileLoadTasks.remove(task)
		}

		val runnable = retryHttpsTasks[url]

		if (runnable != null) {
			AndroidUtilities.cancelRunOnUIThread(runnable)
		}

		runHttpFileLoadTasks(null, 0)
	}

	private fun runHttpFileLoadTasks(oldTask: HttpFileTask?, reason: Int) {
		AndroidUtilities.runOnUIThread {
			if (oldTask != null) {
				currentHttpFileLoadTasksCount--
			}

			if (oldTask != null) {
				if (reason == 1) {
					if (oldTask.canRetry) {
						val newTask = HttpFileTask(oldTask.url, oldTask.tempFile, oldTask.ext, oldTask.currentAccount)

						val runnable = Runnable {
							httpFileLoadTasks.add(newTask)
							runHttpFileLoadTasks(null, 0)
						}

						retryHttpsTasks[oldTask.url] = runnable

						AndroidUtilities.runOnUIThread(runnable, 1000)
					}
					else {
						httpFileLoadTasksByKeys.remove(oldTask.url)

						NotificationCenter.getInstance(oldTask.currentAccount).postNotificationName(NotificationCenter.httpFileDidFailedLoad, oldTask.url, 0)
					}
				}
				else if (reason == 2) {
					httpFileLoadTasksByKeys.remove(oldTask.url)

					val file = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(oldTask.url) + "." + oldTask.ext)
					val result = if (oldTask.tempFile.renameTo(file)) file.toString() else oldTask.tempFile.toString()

					NotificationCenter.getInstance(oldTask.currentAccount).postNotificationName(NotificationCenter.httpFileDidLoad, oldTask.url, result)
				}
			}

			while (currentHttpFileLoadTasksCount < 2 && !httpFileLoadTasks.isEmpty()) {
				val task = httpFileLoadTasks.poll()
				task?.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null, null, null)

				currentHttpFileLoadTasksCount += 1
			}
		}
	}

	fun onFragmentStackChanged() {
		for (drawable in cachedAnimatedFileDrawables) {
			drawable.repeatCount = 0
		}
	}

	private class ThumbGenerateInfo {
		val imageReceiverArray = mutableListOf<ImageReceiver>()
		val imageReceiverGuidsArray = mutableListOf<Int>()
		var parentDocument: TLRPC.Document? = null
		var filter: String? = null
		var big: Boolean = false
	}

	class MessageThumb(var key: String, var drawable: BitmapDrawable)

	private inner class HttpFileTask(val url: String, val tempFile: File, val ext: String?, val currentAccount: Int) : AsyncTask<Void, Void, Boolean>() {
		private var fileSize = 0
		private var fileOutputStream: RandomAccessFile? = null
		var canRetry: Boolean = true
		private var lastProgressTime: Long = 0

		private fun reportProgress(uploadedSize: Long, totalSize: Long) {
			val currentTime = SystemClock.elapsedRealtime()

			if (uploadedSize == totalSize || lastProgressTime == 0L || lastProgressTime < currentTime - 100) {
				lastProgressTime = currentTime

				Utilities.stageQueue.postRunnable {
					fileProgresses[url] = longArrayOf(uploadedSize, totalSize)

					AndroidUtilities.runOnUIThread {
						NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.fileLoadProgressChanged, url, uploadedSize, totalSize)
					}
				}
			}
		}

		@Deprecated("Deprecated in Java")
		override fun doInBackground(vararg voids: Void): Boolean {
			var httpConnectionStream: InputStream? = null
			var done = false
			var httpConnection: URLConnection? = null

			try {
				var downloadUrl = URL(url)

				httpConnection = downloadUrl.openConnection()
				httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1")
				httpConnection.connectTimeout = 5000
				httpConnection.readTimeout = 5000

				if (httpConnection is HttpURLConnection) {
					val httpURLConnection = httpConnection
					httpURLConnection.instanceFollowRedirects = true

					val status = httpURLConnection.responseCode

					if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
						val newUrl = httpURLConnection.getHeaderField("Location")
						val cookies = httpURLConnection.getHeaderField("Set-Cookie")

						downloadUrl = URL(newUrl)

						httpConnection = downloadUrl.openConnection()
						httpConnection.setRequestProperty("Cookie", cookies)
						httpConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1")
					}
				}

				httpConnection.connect()

				httpConnectionStream = httpConnection.getInputStream()

				fileOutputStream = RandomAccessFile(tempFile, "rws")
			}
			catch (e: Throwable) {
				if (e is SocketTimeoutException) {
					if (ApplicationLoader.isNetworkOnline) {
						canRetry = false
					}
				}
				else if (e is UnknownHostException) {
					canRetry = false
				}
				else if (e is SocketException) {
					if (e.message != null && e.message!!.contains("ECONNRESET")) {
						canRetry = false
					}
				}
				else if (e is FileNotFoundException) {
					canRetry = false
				}

				FileLog.e(e)
			}

			if (canRetry) {
				try {
					if (httpConnection is HttpURLConnection) {
						val code = httpConnection.responseCode

						if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_ACCEPTED && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
							canRetry = false
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				if (httpConnection != null) {
					try {
						val headerFields = httpConnection.headerFields

						if (headerFields != null) {
							val values = headerFields["content-Length"]
							val length = values?.firstOrNull()

							if (length != null) {
								fileSize = Utilities.parseInt(length)
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				if (httpConnectionStream != null) {
					try {
						val data = ByteArray(1024 * 32)
						var totalLoaded = 0

						while (true) {
							if (isCancelled) {
								break
							}

							try {
								val read = httpConnectionStream.read(data)

								if (read > 0) {
									fileOutputStream?.write(data, 0, read)

									totalLoaded += read

									if (fileSize > 0) {
										reportProgress(totalLoaded.toLong(), fileSize.toLong())
									}
								}
								else if (read == -1) {
									done = true

									if (fileSize != 0) {
										reportProgress(fileSize.toLong(), fileSize.toLong())
									}

									break
								}
								else {
									break
								}
							}
							catch (e: Exception) {
								FileLog.e(e)
								break
							}
						}
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}

				try {
					fileOutputStream?.close()
					fileOutputStream = null
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}

				try {
					httpConnectionStream?.close()
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}

			return done
		}

		@Deprecated("Deprecated in Java")
		override fun onPostExecute(result: Boolean) {
			runHttpFileLoadTasks(this, if (result) 2 else 1)
		}

		@Deprecated("Deprecated in Java")
		override fun onCancelled() {
			runHttpFileLoadTasks(this, 2)
		}
	}

	private inner class ArtworkLoadTask(val cacheImage: CacheImage) : AsyncTask<Void, Void, String?>() {
		private val small: Boolean
		private var canRetry = true
		private var httpConnection: HttpURLConnection? = null

		init {
			val uri = Uri.parse(cacheImage.imageLocation?.path ?: "")
			small = uri.getQueryParameter("s") != null
		}

		@Deprecated("Deprecated in Java")
		override fun doInBackground(vararg voids: Void): String? {
			var outbuf: ByteArrayOutputStream? = null
			var httpConnectionStream: InputStream? = null

			try {
				val location = cacheImage.imageLocation?.path ?: ""
				val downloadUrl = URL(location.replace("athumb://", "https://"))

				httpConnection = downloadUrl.openConnection() as HttpURLConnection
				httpConnection?.connectTimeout = 5000
				httpConnection?.readTimeout = 5000
				httpConnection?.connect()

				try {
					if (httpConnection != null) {
						val code = httpConnection?.responseCode ?: 0

						if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_ACCEPTED && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
							canRetry = false
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				httpConnectionStream = httpConnection?.inputStream

				outbuf = ByteArrayOutputStream()

				val data = ByteArray(1024 * 32)

				while (true) {
					if (isCancelled) {
						break
					}

					val read = httpConnectionStream?.read(data) ?: 0

					if (read > 0) {
						outbuf.write(data, 0, read)
					}
					else if (read == -1) {
						break
					}
					else {
						break
					}
				}

				canRetry = false

				val `object` = JSONObject(String(outbuf.toByteArray()))
				val array = `object`.getJSONArray("results")

				if (array.length() > 0) {
					val media = array.getJSONObject(0)
					val artworkUrl100 = media.getString("artworkUrl100")

					return if (small) {
						artworkUrl100
					}
					else {
						artworkUrl100.replace("100x100", "600x600")
					}
				}
			}
			catch (e: Throwable) {
				if (e is SocketTimeoutException) {
					if (ApplicationLoader.isNetworkOnline) {
						canRetry = false
					}
				}
				else if (e is UnknownHostException) {
					canRetry = false
				}
				else if (e is SocketException) {
					if (e.message?.contains("ECONNRESET") == true) {
						canRetry = false
					}
				}
				else if (e is FileNotFoundException) {
					canRetry = false
				}

				FileLog.e(e)
			}
			finally {
				runCatching {
					httpConnection?.disconnect()
				}

				try {
					httpConnectionStream?.close()
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}

				runCatching {
					outbuf?.close()
				}
			}

			return null
		}

		@Deprecated("Deprecated in Java")
		override fun onPostExecute(result: String?) {
			if (result != null) {
				imageLoadQueue.postRunnable {
					cacheImage.httpTask = HttpImageTask(cacheImage, 0, result).also {
						httpTasks.add(it)
					}

					runHttpTasks(false)
				}
			}
			else if (canRetry) {
				artworkLoadError(cacheImage.url)
			}

			imageLoadQueue.postRunnable {
				runArtworkTasks(true)
			}
		}

		@Deprecated("Deprecated in Java")
		override fun onCancelled() {
			imageLoadQueue.postRunnable {
				runArtworkTasks(true)
			}
		}
	}

	private inner class HttpImageTask : AsyncTask<Void, Void, Boolean> {
		val cacheImage: CacheImage
		private var fileOutputStream: RandomAccessFile? = null
		var imageSize: Long
		private var lastProgressTime: Long = 0
		private var canRetry = true
		private var overrideUrl: String? = null
		private var httpConnection: HttpURLConnection? = null

		constructor(cacheImage: CacheImage, size: Long) {
			this.cacheImage = cacheImage
			imageSize = size
		}

		constructor(cacheImage: CacheImage, size: Int, url: String?) {
			this.cacheImage = cacheImage
			imageSize = size.toLong()
			overrideUrl = url
		}

		private fun reportProgress(uploadedSize: Long, totalSize: Long) {
			val currentTime = SystemClock.elapsedRealtime()

			if (uploadedSize == totalSize || lastProgressTime == 0L || lastProgressTime < currentTime - 100) {
				lastProgressTime = currentTime

				Utilities.stageQueue.postRunnable {
					cacheImage.url?.let {
						fileProgresses[it] = longArrayOf(uploadedSize, totalSize)

						AndroidUtilities.runOnUIThread {
							NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.fileLoadProgressChanged, it, uploadedSize, totalSize)
						}
					}
				}
			}
		}

		@Deprecated("Deprecated in Java")
		override fun doInBackground(vararg voids: Void): Boolean {
			var httpConnectionStream: InputStream? = null
			var done = false

			if (!isCancelled) {
				try {
					val location = cacheImage.imageLocation?.path

					if (!location.isNullOrEmpty()) {
						if (location.startsWith("https://static-maps") || location.startsWith("https://maps.googleapis")) {
							val provider = MessagesController.getInstance(cacheImage.currentAccount).mapProvider

							if (provider == MessagesController.MAP_PROVIDER_YANDEX_WITH_ARGS || provider == MessagesController.MAP_PROVIDER_GOOGLE) {
								val webFile = testWebFile[location]

								if (webFile != null) {
									val req = TL_upload_getWebFile()
									req.location = webFile.location
									req.offset = 0
									req.limit = 0

									ConnectionsManager.getInstance(cacheImage.currentAccount).sendRequest(req)
								}
							}
						}

						val downloadUrl = URL(overrideUrl ?: location)

						httpConnection = downloadUrl.openConnection() as HttpURLConnection
						httpConnection?.addRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0 like Mac OS X) AppleWebKit/602.1.38 (KHTML, like Gecko) Version/10.0 Mobile/14A5297c Safari/602.1")
						httpConnection?.connectTimeout = 5000
						httpConnection?.readTimeout = 5000
						httpConnection?.instanceFollowRedirects = true

						if (!isCancelled) {
							httpConnection?.connect()
							httpConnectionStream = httpConnection?.inputStream
							fileOutputStream = RandomAccessFile(cacheImage.tempFilePath, "rws")
						}
					}
				}
				catch (e: Throwable) {
					when (e) {
						is SocketTimeoutException -> {
							if (ApplicationLoader.isNetworkOnline) {
								canRetry = false
							}
						}

						is UnknownHostException -> {
							canRetry = false
						}

						is SocketException -> {
							if (e.message?.contains("ECONNRESET") == true) {
								canRetry = false
							}
						}

						is FileNotFoundException -> {
							canRetry = false
						}

						is InterruptedIOException -> {
							// unused
						}
					}

					FileLog.e(e)
				}
			}

			if (!isCancelled) {
				try {
					if (httpConnection != null) {
						val code = httpConnection?.responseCode ?: 0

						if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_ACCEPTED && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
							canRetry = false
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				if (imageSize == 0L && httpConnection != null) {
					try {
						val headerFields = httpConnection?.headerFields

						if (headerFields != null) {
							val values = headerFields["content-Length"]
							val length = values?.firstOrNull()

							if (length != null) {
								imageSize = Utilities.parseInt(length).toLong()
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				if (httpConnectionStream != null) {
					try {
						val data = ByteArray(1024 * 8)
						var totalLoaded = 0

						while (true) {
							if (isCancelled) {
								break
							}

							try {
								val read = httpConnectionStream.read(data)

								if (read > 0) {
									totalLoaded += read

									fileOutputStream?.write(data, 0, read)

									if (imageSize != 0L) {
										reportProgress(totalLoaded.toLong(), imageSize)
									}
								}
								else if (read == -1) {
									done = true

									if (imageSize != 0L) {
										reportProgress(imageSize, imageSize)
									}

									break
								}
								else {
									break
								}
							}
							catch (e: Exception) {
								FileLog.e(e)
								break
							}
						}
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}
			}

			try {
				fileOutputStream?.close()
				fileOutputStream = null
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}

			runCatching {
				httpConnection?.disconnect()
			}

			try {
				httpConnectionStream?.close()
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}

			if (done) {
				cacheImage.tempFilePath?.let {
					if (!it.renameTo(cacheImage.finalFilePath)) {
						cacheImage.finalFilePath = it
					}
				}
			}

			return done
		}

		@Deprecated("Deprecated in Java")
		override fun onPostExecute(result: Boolean) {
			if (result || !canRetry) {
				fileDidLoaded(cacheImage.url, cacheImage.finalFilePath, FileLoader.MEDIA_DIR_IMAGE)
			}
			else {
				httpFileLoadError(cacheImage.url)
			}

			Utilities.stageQueue.postRunnable {
				fileProgresses.remove(cacheImage.url)

				AndroidUtilities.runOnUIThread {
					if (result) {
						NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.fileLoaded, cacheImage.url, cacheImage.finalFilePath)
					}
					else {
						NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.fileLoadFailed, cacheImage.url, 2)
					}
				}
			}

			imageLoadQueue.postRunnable({ runHttpTasks(true) }, cacheImage.priority.toLong())
		}

		@Deprecated("Deprecated in Java")
		override fun onCancelled() {
			imageLoadQueue.postRunnable({ runHttpTasks(true) }, cacheImage.priority.toLong())

			Utilities.stageQueue.postRunnable {
				fileProgresses.remove(cacheImage.url)

				AndroidUtilities.runOnUIThread {
					NotificationCenter.getInstance(cacheImage.currentAccount).postNotificationName(NotificationCenter.fileLoadFailed, cacheImage.url, 1)
				}
			}
		}
	}

	private inner class ThumbGenerateTask(private val mediaType: Int, private val originalPath: File, private val info: ThumbGenerateInfo?) : Runnable {
		private fun removeTask() {
			if (info == null) {
				return
			}

			val name = FileLoader.getAttachFileName(info.parentDocument)

			imageLoadQueue.postRunnable {
				thumbGenerateTasks.remove(name)
			}
		}

		override fun run() {
			try {
				if (info == null) {
					removeTask()
					return
				}

				val key = "q_" + info.parentDocument?.dc_id + "_" + info.parentDocument?.id
				val thumbFile = File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "$key.jpg")

				if (thumbFile.exists() || !originalPath.exists()) {
					removeTask()
					return
				}

				val size = if (info.big) max(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()).toInt() else min(180.0, (min(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) / 4)).toInt()
				var originalBitmap: Bitmap? = null

				if (mediaType == FileLoader.MEDIA_DIR_IMAGE) {
					originalBitmap = loadBitmap(originalPath.toString(), null, size.toFloat(), size.toFloat(), false)
				}
				else if (mediaType == FileLoader.MEDIA_DIR_VIDEO) {
					originalBitmap = SendMessagesHelper.createVideoThumbnail(originalPath.toString(), if (info.big) MediaStore.Video.Thumbnails.FULL_SCREEN_KIND else MediaStore.Video.Thumbnails.MINI_KIND)
				}
				else if (mediaType == FileLoader.MEDIA_DIR_DOCUMENT) {
					val path = originalPath.toString().lowercase(Locale.getDefault())

					if (path.endsWith("mp4")) {
						originalBitmap = SendMessagesHelper.createVideoThumbnail(originalPath.toString(), if (info.big) MediaStore.Video.Thumbnails.FULL_SCREEN_KIND else MediaStore.Video.Thumbnails.MINI_KIND)
					}
					else if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".gif")) {
						originalBitmap = loadBitmap(path, null, size.toFloat(), size.toFloat(), false)
					}
				}

				if (originalBitmap == null) {
					removeTask()
					return
				}

				val w = originalBitmap.width
				val h = originalBitmap.height

				if (w == 0 || h == 0) {
					removeTask()
					return
				}

				val scaleFactor = min((w.toFloat() / size.toFloat()).toDouble(), (h.toFloat() / size.toFloat()).toDouble()).toFloat()

				if (scaleFactor > 1) {
					val scaledBitmap = Bitmaps.createScaledBitmap(originalBitmap, (w / scaleFactor).toInt(), (h / scaleFactor).toInt(), true)

					if (scaledBitmap != originalBitmap) {
						originalBitmap.recycle()
						originalBitmap = scaledBitmap
					}
				}

				val stream = FileOutputStream(thumbFile)

				originalBitmap?.compress(CompressFormat.JPEG, if (info.big) 83 else 60, stream)

				try {
					stream.close()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				val bitmapDrawable = BitmapDrawable(ApplicationLoader.applicationContext.resources, originalBitmap)
				val finalImageReceiverArray = info.imageReceiverArray.toList()
				val finalImageReceiverGuidsArray = info.imageReceiverGuidsArray.toList()

				AndroidUtilities.runOnUIThread {
					removeTask()

					var kf = key

					if (info.filter != null) {
						kf += "@" + info.filter
					}

					for (a in finalImageReceiverArray.indices) {
						val imgView = finalImageReceiverArray[a]
						imgView.setImageBitmapByKey(bitmapDrawable, kf, ImageReceiver.TYPE_IMAGE, false, finalImageReceiverGuidsArray[a])
					}

					memCache.put(kf, bitmapDrawable)
				}
			}
			catch (e: Throwable) {
				FileLog.e(e)
				removeTask()
			}
		}
	}

	private inner class CacheOutTask(val cacheImage: CacheImage) : Runnable {
		private val sync = Any()
		private var runningThread: Thread? = null
		private var isCancelled = false

		override fun run() {
			synchronized(sync) {
				runningThread = Thread.currentThread()

				Thread.interrupted()

				if (isCancelled) {
					return
				}
			}

			if (cacheImage.imageLocation?.photoSize is TL_photoStrippedSize) {
				val photoSize = cacheImage.imageLocation?.photoSize as TL_photoStrippedSize
				val bitmap = getStrippedPhotoBitmap(photoSize.bytes, "b")

				onPostExecute(if (bitmap != null) BitmapDrawable(ApplicationLoader.applicationContext.resources, bitmap) else null)
			}
			else if (cacheImage.imageType == FileLoader.IMAGE_TYPE_THEME_PREVIEW) {
				var bitmapDrawable: BitmapDrawable? = null

				try {
					(cacheImage.imageLocation?.document as? ThemeDocument)?.let {
						bitmapDrawable = ThemePreviewDrawable(cacheImage.finalFilePath, it)
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}

				onPostExecute(bitmapDrawable)
			}
			else if (cacheImage.imageType == FileLoader.IMAGE_TYPE_SVG || cacheImage.imageType == FileLoader.IMAGE_TYPE_SVG_WHITE) {
				var w = AndroidUtilities.displaySize.x
				var h = AndroidUtilities.displaySize.y

				if (cacheImage.filter != null) {
					val args = cacheImage.filter!!.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

					if (args.size >= 2) {
						val wFilter = args[0].toFloat()
						val hFilter = args[1].toFloat()

						w = (wFilter * AndroidUtilities.density).toInt()
						h = (hFilter * AndroidUtilities.density).toInt()
					}
				}

				var bitmap: Bitmap? = null

				try {
					bitmap = SvgHelper.getBitmap(cacheImage.finalFilePath, w, h, cacheImage.imageType == FileLoader.IMAGE_TYPE_SVG_WHITE)
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}

				onPostExecute(if (bitmap != null) BitmapDrawable(ApplicationLoader.applicationContext.resources, bitmap) else null)
			}
			else if (cacheImage.imageType == FileLoader.IMAGE_TYPE_LOTTIE) {
				var w = min(512.0, AndroidUtilities.dp(170.6f).toDouble()).toInt()
				var h = min(512.0, AndroidUtilities.dp(170.6f).toDouble()).toInt()
				var precache = false
				var limitFps = false
				var lastFrameBitmap = false
				var firstFrameBitmap = false
				var autoRepeat = 1
				var diceEmoji: String? = null
				var fitzModifier = 0

				val cacheImageFilter = cacheImage.filter

				if (cacheImageFilter != null) {
					val args = cacheImageFilter.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

					if (args.size >= 2) {
						val wFilter = args[0].toFloat()
						val hFilter = args[1].toFloat()

						w = min(512.0, (wFilter * AndroidUtilities.density).toInt().toDouble()).toInt()
						h = min(512.0, (hFilter * AndroidUtilities.density).toInt().toDouble()).toInt()

						if (wFilter <= 90 && hFilter <= 90 && !cacheImageFilter.contains("nolimit")) {
							w = min(w.toDouble(), 160.0).toInt()
							h = min(h.toDouble(), 160.0).toInt()

							limitFps = true
						}

						precache = if (args.size >= 3 && "pcache" == args[2]) {
							true
						}
						else {
							cacheImageFilter.contains("pcache") || !cacheImageFilter.contains("nolimit") && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_HIGH
						}

						if (cacheImageFilter.contains("lastframe")) {
							lastFrameBitmap = true
						}

						if (cacheImageFilter.contains("firstframe")) {
							firstFrameBitmap = true
						}
					}

					if (args.size >= 3) {
						if ("nr" == args[2]) {
							autoRepeat = 2
						}
						else if ("nrs" == args[2]) {
							autoRepeat = 3
						}
						else if ("dice" == args[2]) {
							diceEmoji = args[3]
							autoRepeat = 2
						}
					}

					if (args.size >= 5) {
						if ("c1" == args[4]) {
							fitzModifier = 12
						}
						else if ("c2" == args[4]) {
							fitzModifier = 3
						}
						else if ("c3" == args[4]) {
							fitzModifier = 4
						}
						else if ("c4" == args[4]) {
							fitzModifier = 5
						}
						else if ("c5" == args[4]) {
							fitzModifier = 6
						}
					}
				}

				val lottieDrawable: RLottieDrawable

				if (diceEmoji != null) {
					lottieDrawable = if ("\uD83C\uDFB0" == diceEmoji) {
						SlotsDrawable(diceEmoji, w, h)
					}
					else {
						RLottieDrawable(diceEmoji, w, h)
					}
				}
				else {
					var compressed = false

					try {
						RandomAccessFile(cacheImage.finalFilePath, "r").use {
							val bytes = if (cacheImage.type == ImageReceiver.TYPE_THUMB) {
								headerThumb
							}
							else {
								header
							}

							it.readFully(bytes, 0, 2)

							if (bytes[0].toInt() == 0x1f && bytes[1] == 0x8b.toByte()) {
								compressed = true
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					if (lastFrameBitmap || firstFrameBitmap) {
						precache = false
					}

					var cacheOptions: CacheOptions? = null

					if (precache) {
						cacheOptions = CacheOptions()

						if (cacheImage.filter?.contains("compress") == true) {
							cacheOptions.compressQuality = BitmapsCache.COMPRESS_QUALITY_DEFAULT
						}

						if (cacheImage.filter?.contains("flbk") == true) {
							cacheOptions.fallback = true
						}
					}

					lottieDrawable = if (compressed) {
						RLottieDrawable(cacheImage.finalFilePath, decompressGzip(cacheImage.finalFilePath), w, h, cacheOptions, limitFps, null, fitzModifier)
					}
					else {
						RLottieDrawable(cacheImage.finalFilePath, w, h, cacheOptions, limitFps, null, fitzModifier)
					}
				}

				if (lastFrameBitmap || firstFrameBitmap) {
					loadLastFrame(lottieDrawable, h, w, lastFrameBitmap)
				}
				else {
					lottieDrawable.setAutoRepeat(autoRepeat)
					onPostExecute(lottieDrawable)
				}
			}
			else if (cacheImage.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
				val fileDrawable: AnimatedFileDrawable

				val seekTo = cacheImage.imageLocation?.videoSeekTo ?: 0
				var limitFps = false
				var precache = false

				if (cacheImage.filter != null) {
					val args = cacheImage.filter!!.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

					if (args.size >= 2) {
						val wFilter = args[0].toFloat()
						val hFilter = args[1].toFloat()

						if (wFilter <= 90 && hFilter <= 90 && !cacheImage.filter!!.contains("nolimit")) {
							limitFps = true
						}
					}

					for (i in args.indices) {
						if ("pcache" == args[i]) {
							precache = true
						}
					}
				}

				if ((isAnimatedAvatar(cacheImage.filter) || AUTOPLAY_FILTER == cacheImage.filter) && cacheImage.imageLocation!!.document !is TL_documentEncrypted && !precache) {
					val document = if (cacheImage.imageLocation!!.document is TLRPC.Document) cacheImage.imageLocation!!.document else null
					val size = if (document != null) cacheImage.size else cacheImage.imageLocation!!.currentSize
					var cacheOptions: CacheOptions? = null

					if (precache) {
						cacheOptions = CacheOptions()

						if (cacheImage.filter?.contains("compress") == true) {
							cacheOptions.compressQuality = BitmapsCache.COMPRESS_QUALITY_DEFAULT
						}
					}

					fileDrawable = AnimatedFileDrawable(cacheImage.finalFilePath, false, size, document, if (document == null) cacheImage.imageLocation else null, cacheImage.parentObject, seekTo, cacheImage.currentAccount, false, cacheOptions)
					fileDrawable.setIsWebmSticker(isWebM(document) || isVideoSticker(document) || isAnimatedAvatar(cacheImage.filter))
				}
				else {
					var w = 0
					var h = 0

					if (cacheImage.filter != null) {
						val args = cacheImage.filter!!.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

						if (args.size >= 2) {
							val wFilter = args[0].toFloat()
							val hFilter = args[1].toFloat()

							w = (wFilter * AndroidUtilities.density).toInt()
							h = (hFilter * AndroidUtilities.density).toInt()
						}
					}

					var cacheOptions: CacheOptions? = null

					if (precache) {
						cacheOptions = CacheOptions()

						if (cacheImage.filter?.contains("compress") == true) {
							cacheOptions.compressQuality = BitmapsCache.COMPRESS_QUALITY_DEFAULT
						}
					}

					fileDrawable = AnimatedFileDrawable(cacheImage.finalFilePath, "d" == cacheImage.filter, 0, cacheImage.imageLocation!!.document, null, null, seekTo, cacheImage.currentAccount, false, w, h, cacheOptions)
					fileDrawable.setIsWebmSticker(isWebM(cacheImage.imageLocation!!.document) || isVideoSticker(cacheImage.imageLocation!!.document) || isAnimatedAvatar(cacheImage.filter))
				}

				fileDrawable.setLimitFps(limitFps)

				Thread.interrupted()

				onPostExecute(fileDrawable)
			}
			else {
				var mediaId: Long? = null
				var mediaIsVideo = false
				var image: Bitmap? = null
				var needInvert = false
				var orientation = 0
				val cacheFileFinal = cacheImage.finalFilePath
				val inEncryptedFile = cacheImage.secureDocument != null || cacheImage.encryptionKeyPath != null && cacheFileFinal != null && cacheFileFinal.absolutePath.endsWith(".enc")
				val secureDocumentKey: SecureDocumentKey?
				val secureDocumentHash: ByteArray?

				if (cacheImage.secureDocument != null) {
					secureDocumentKey = cacheImage.secureDocument!!.secureDocumentKey

					secureDocumentHash = cacheImage.secureDocument?.secureFile?.file_hash ?: cacheImage.secureDocument?.fileHash
				}
				else {
					secureDocumentKey = null
					secureDocumentHash = null
				}

				var canDeleteFile = true
				val useNativeWebpLoader = false
				var mediaThumbPath: String? = null

				if (cacheImage.imageLocation?.path != null) {
					val location = cacheImage.imageLocation?.path ?: ""

					if (location.startsWith("thumb://")) {
						val idx = location.indexOf(":", 8)

						if (idx >= 0) {
							mediaId = location.substring(8, idx).toLong()
							mediaIsVideo = false
							mediaThumbPath = location.substring(idx + 1)
						}

						canDeleteFile = false
					}
					else if (location.startsWith("vthumb://")) {
						val idx = location.indexOf(":", 9)

						if (idx >= 0) {
							mediaId = location.substring(9, idx).toLong()
							mediaIsVideo = true
						}

						canDeleteFile = false
					}
					else if (!location.startsWith("http")) {
						canDeleteFile = false
					}
				}

				val opts = BitmapFactory.Options()
				opts.inSampleSize = 1

				var wFilter = 0f
				var hFilter = 0f
				var blurType = 0
				var checkInversion = false
				var force8888 = canForce8888

				try {
					if (cacheImage.filter != null) {
						val args = cacheImage.filter!!.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

						if (args.size >= 2) {
							wFilter = args[0].toFloat() * AndroidUtilities.density
							hFilter = args[1].toFloat() * AndroidUtilities.density
						}

						if (cacheImage.filter!!.contains("b2")) {
							blurType = 3
						}
						else if (cacheImage.filter!!.contains("b1")) {
							blurType = 2
						}
						else if (cacheImage.filter!!.contains("b")) {
							blurType = 1
						}

						if (cacheImage.filter!!.contains("i")) {
							checkInversion = true
						}

						if (cacheImage.filter!!.contains("f")) {
							force8888 = true
						}

						if (!useNativeWebpLoader && wFilter != 0f && hFilter != 0f) {
							opts.inJustDecodeBounds = true

							if (mediaId != null && mediaThumbPath == null) {
								if (mediaIsVideo) {
									MediaStore.Video.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.contentResolver, mediaId, MediaStore.Video.Thumbnails.MINI_KIND, opts)
								}
								else {
									MediaStore.Images.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.contentResolver, mediaId, MediaStore.Images.Thumbnails.MINI_KIND, opts)
								}
							}
							else {
								if (secureDocumentKey != null) {
									val f = RandomAccessFile(cacheFileFinal, "r")
									var len = f.length().toInt()
									var bytes = bytesLocal.get()
									var data = if (bytes != null && bytes.size >= len) bytes else null

									if (data == null) {
										data = ByteArray(len)
										bytes = data
										bytesLocal.set(bytes)
									}

									f.readFully(data, 0, len)
									f.close()

									EncryptedFileInputStream.decryptBytesWithKeyFile(data, 0, len, secureDocumentKey)

									val hash = Utilities.computeSHA256(data, 0, len.toLong())
									var error = false

									if (secureDocumentHash == null || !hash.contentEquals(secureDocumentHash)) {
										error = true
									}

									val offset = (data[0].toInt() and 0xff)

									len -= offset

									if (!error) {
										BitmapFactory.decodeByteArray(data, offset, len, opts)
									}
								}
								else {
									if (inEncryptedFile) {
										EncryptedFileInputStream(cacheFileFinal, cacheImage.encryptionKeyPath)
									}
									else {
										FileInputStream(cacheFileFinal)
									}.use {
										BitmapFactory.decodeStream(it, null, opts)
									}
								}
							}

							val photoW = opts.outWidth.toFloat()
							val photoH = opts.outHeight.toFloat()

							var scaleFactor = if (wFilter >= hFilter && photoW > photoH) {
								max((photoW / wFilter).toDouble(), (photoH / hFilter).toDouble()).toFloat()
							}
							else {
								min((photoW / wFilter).toDouble(), (photoH / hFilter).toDouble()).toFloat()
							}

							if (scaleFactor < 1.2f) {
								scaleFactor = 1f
							}

							opts.inJustDecodeBounds = false

							if (scaleFactor > 1.0f && (photoW > wFilter || photoH > hFilter)) {
								var sample = 1

								do {
									sample *= 2
								} while (sample * 2 < scaleFactor)

								opts.inSampleSize = sample
							}
							else {
								opts.inSampleSize = scaleFactor.toInt()
							}
						}
					}
					else if (mediaThumbPath != null) {
						opts.inJustDecodeBounds = true
						opts.inPreferredConfig = if (force8888) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565

						image = FileInputStream(cacheFileFinal).use {
							BitmapFactory.decodeStream(it, null, opts)
						}

						val photoW2 = opts.outWidth
						val photoH2 = opts.outHeight

						opts.inJustDecodeBounds = false

						val screenSize = max(66.0, min(AndroidUtilities.getRealScreenSize().x.toDouble(), AndroidUtilities.getRealScreenSize().y.toDouble())).toInt()
						var scaleFactor = (min(photoH2.toDouble(), photoW2.toDouble()) / screenSize.toFloat() * 6f).toFloat()

						if (scaleFactor < 1) {
							scaleFactor = 1f
						}

						if (scaleFactor > 1.0f) {
							var sample = 1

							do {
								sample *= 2
							} while (sample * 2 <= scaleFactor)

							opts.inSampleSize = sample
						}
						else {
							opts.inSampleSize = scaleFactor.toInt()
						}
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}

				if (cacheImage.type == ImageReceiver.TYPE_THUMB) {
					try {
						lastCacheOutTime = SystemClock.elapsedRealtime()

						synchronized(sync) {
							if (isCancelled) {
								return
							}
						}

						if (useNativeWebpLoader) {
							image = RandomAccessFile(cacheFileFinal, "r").use {
								val buffer: ByteBuffer = it.channel.map(FileChannel.MapMode.READ_ONLY, 0, cacheFileFinal!!.length())

								val bmOptions = BitmapFactory.Options()
								bmOptions.inJustDecodeBounds = true
								Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true)

								val img = Bitmaps.createBitmap(bmOptions.outWidth, bmOptions.outHeight, Bitmap.Config.ARGB_8888)

								Utilities.loadWebpImage(img, buffer, buffer.limit(), null, !opts.inPurgeable)

								img
							}
						}
						else {
							if (opts.inPurgeable || secureDocumentKey != null) {
								val f = RandomAccessFile(cacheFileFinal, "r")
								var len = f.length().toInt()
								var offset = 0
								var bytesThumb = bytesThumbLocal.get()
								var data = if (bytesThumb != null && bytesThumb.size >= len) bytesThumb else null

								if (data == null) {
									data = ByteArray(len)
									bytesThumb = data
									bytesThumbLocal.set(bytesThumb)
								}

								f.readFully(data, 0, len)
								f.close()

								var error = false

								if (secureDocumentKey != null) {
									EncryptedFileInputStream.decryptBytesWithKeyFile(data, 0, len, secureDocumentKey)

									val hash = Utilities.computeSHA256(data, 0, len.toLong())

									if (secureDocumentHash == null || !hash.contentEquals(secureDocumentHash)) {
										error = true
									}

									offset = (data[0].toInt() and 0xff)
									len -= offset
								}
								else if (inEncryptedFile) {
									EncryptedFileInputStream.decryptBytesWithKeyFile(data, 0, len, cacheImage.encryptionKeyPath)
								}

								if (!error) {
									image = BitmapFactory.decodeByteArray(data, offset, len, opts)
								}
							}
							else {
								if (inEncryptedFile) {
									EncryptedFileInputStream(cacheFileFinal, cacheImage.encryptionKeyPath)
								}
								else {
									FileInputStream(cacheFileFinal)
								}.use {
									BitmapFactory.decodeStream(it, null, opts)
								}
							}
						}

						if (image == null) {
							if (cacheFileFinal?.length() == 0L || cacheImage.filter == null) {
								cacheFileFinal?.delete()
							}
						}
						else {
							if (cacheImage.filter != null) {
								val bitmapW = image.width.toFloat()
								val bitmapH = image.height.toFloat()

								if (!opts.inPurgeable && wFilter != 0f && bitmapW != wFilter && bitmapW > wFilter + 20) {
									val scaleFactor = bitmapW / wFilter
									val scaledBitmap = Bitmaps.createScaledBitmap(image, wFilter.toInt(), (bitmapH / scaleFactor).toInt(), true)

									if (image != scaledBitmap) {
										image.recycle()
										image = scaledBitmap
									}
								}
							}

							if (checkInversion) {
								needInvert = Utilities.needInvert(image, if (opts.inPurgeable) 0 else 1, image!!.width, image.height, image.rowBytes) != 0
							}

							if (blurType == 1) {
								if (image?.config == Bitmap.Config.ARGB_8888) {
									Utilities.blurBitmap(image, 3, if (opts.inPurgeable) 0 else 1, image.width, image.height, image.rowBytes)
								}
							}
							else if (blurType == 2) {
								if (image?.config == Bitmap.Config.ARGB_8888) {
									Utilities.blurBitmap(image, 1, if (opts.inPurgeable) 0 else 1, image.width, image.height, image.rowBytes)
								}
							}
							else if (blurType == 3) {
								if (image?.config == Bitmap.Config.ARGB_8888) {
									Utilities.blurBitmap(image, 7, if (opts.inPurgeable) 0 else 1, image.width, image.height, image.rowBytes)
									Utilities.blurBitmap(image, 7, if (opts.inPurgeable) 0 else 1, image.width, image.height, image.rowBytes)
									Utilities.blurBitmap(image, 7, if (opts.inPurgeable) 0 else 1, image.width, image.height, image.rowBytes)
								}
							}
							else if (blurType == 0 && opts.inPurgeable) {
								Utilities.pinBitmap(image)
							}
						}
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}
				else {
					try {
						lastCacheOutTime = SystemClock.elapsedRealtime()

						synchronized(sync) {
							if (isCancelled) {
								return
							}
						}

						if (force8888 || cacheImage.filter == null || blurType != 0 || cacheImage.imageLocation?.path != null) {
							opts.inPreferredConfig = Bitmap.Config.ARGB_8888
						}
						else {
							opts.inPreferredConfig = Bitmap.Config.RGB_565
						}

						opts.inDither = false

						if (mediaId != null && mediaThumbPath == null) {
							image = if (mediaIsVideo) {
								MediaStore.Video.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.contentResolver, mediaId, MediaStore.Video.Thumbnails.MINI_KIND, opts)
							}
							else {
								MediaStore.Images.Thumbnails.getThumbnail(ApplicationLoader.applicationContext.contentResolver, mediaId, MediaStore.Images.Thumbnails.MINI_KIND, opts)
							}
						}

						if (image == null) {
							if (useNativeWebpLoader) {
								image = RandomAccessFile(cacheFileFinal, "r").use {
									val buffer: ByteBuffer = it.channel.map(FileChannel.MapMode.READ_ONLY, 0, cacheFileFinal!!.length())
									val bmOptions = BitmapFactory.Options()
									bmOptions.inJustDecodeBounds = true
									Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true)
									val img = Bitmaps.createBitmap(bmOptions.outWidth, bmOptions.outHeight, Bitmap.Config.ARGB_8888)
									Utilities.loadWebpImage(img, buffer, buffer.limit(), null, !opts.inPurgeable)
									img
								}
							}
							else {
								if (opts.inPurgeable || secureDocumentKey != null || Build.VERSION.SDK_INT <= 29) {
									val f = RandomAccessFile(cacheFileFinal, "r")
									var len = f.length().toInt()
									var offset = 0
									var bytes = bytesLocal.get()
									var data = if (bytes != null && bytes.size >= len) bytes else null

									if (data == null) {
										data = ByteArray(len)
										bytes = data
										bytesLocal.set(bytes)
									}

									f.readFully(data, 0, len)
									f.close()

									var error = false

									if (secureDocumentKey != null) {
										EncryptedFileInputStream.decryptBytesWithKeyFile(data, 0, len, secureDocumentKey)

										val hash = Utilities.computeSHA256(data, 0, len.toLong())

										if (secureDocumentHash == null || !hash.contentEquals(secureDocumentHash)) {
											error = true
										}

										offset = (data[0].toInt() and 0xff)
										len -= offset
									}
									else if (inEncryptedFile) {
										EncryptedFileInputStream.decryptBytesWithKeyFile(data, 0, len, cacheImage.encryptionKeyPath)
									}

									if (!error) {
										image = BitmapFactory.decodeByteArray(data, offset, len, opts)
									}
								}
								else {
									image = if (inEncryptedFile) {
										EncryptedFileInputStream(cacheFileFinal, cacheImage.encryptionKeyPath)
									}
									else {
										FileInputStream(cacheFileFinal)
									}.use {
										if (cacheImage.imageLocation?.document is TL_document) {
											runCatching {
												val exif = ExifInterface(it)
												val attribute = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)

												when (attribute) {
													ExifInterface.ORIENTATION_ROTATE_90 -> orientation = 90
													ExifInterface.ORIENTATION_ROTATE_180 -> orientation = 180
													ExifInterface.ORIENTATION_ROTATE_270 -> orientation = 270
												}
											}

											it.channel.position(0)
										}

										BitmapFactory.decodeStream(it, null, opts)
									}
								}
							}
						}

						if (image == null) {
							if (canDeleteFile && (cacheFileFinal?.length() == 0L || cacheImage.filter == null)) {
								cacheFileFinal?.delete()
							}
						}
						else {
							var blured = false

							if (cacheImage.filter != null) {
								var bitmapW = image.width.toFloat()
								var bitmapH = image.height.toFloat()

								if (!opts.inPurgeable && wFilter != 0f && bitmapW != wFilter && bitmapW > wFilter + 20) {
									val scaledBitmap: Bitmap

									if (bitmapW > bitmapH && wFilter > hFilter) {
										val scaleFactor = bitmapW / wFilter

										scaledBitmap = if (scaleFactor > 1) {
											Bitmaps.createScaledBitmap(image, wFilter.toInt(), (bitmapH / scaleFactor).toInt(), true)
										}
										else {
											image
										}
									}
									else {
										val scaleFactor = bitmapH / hFilter

										scaledBitmap = if (scaleFactor > 1) {
											Bitmaps.createScaledBitmap(image, (bitmapW / scaleFactor).toInt(), hFilter.toInt(), true)
										}
										else {
											image
										}
									}

									if (image != scaledBitmap) {
										image.recycle()
										image = scaledBitmap
									}
								}

								if (checkInversion) {
									var b: Bitmap = image
									val w = image.width
									val h = image.height

									if (w * h > 150 * 150) {
										b = Bitmaps.createScaledBitmap(image, 100, 100, false)
									}

									needInvert = Utilities.needInvert(b, if (opts.inPurgeable) 0 else 1, b.width, b.height, b.rowBytes) != 0

									if (b != image) {
										b.recycle()
									}
								}

								if (blurType != 0 && (bitmapH > 100 || bitmapW > 100)) {
									image = Bitmaps.createScaledBitmap(image, 80, 80, false)
									bitmapH = 80f
									bitmapW = 80f
								}

								if (blurType != 0 && bitmapH < 100 && bitmapW < 100) {
									if (image?.config == Bitmap.Config.ARGB_8888) {
										Utilities.blurBitmap(image, 3, if (opts.inPurgeable) 0 else 1, image.width, image.height, image.rowBytes)
									}

									blured = true
								}
							}

							if (!blured && opts.inPurgeable) {
								Utilities.pinBitmap(image)
							}
						}
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}

				Thread.interrupted()

				if (BuildConfig.DEBUG && inEncryptedFile) {
					FileLog.e("Image Loader image is empty = " + (image == null) + " " + cacheFileFinal)
				}

				if (needInvert || orientation != 0) {
					onPostExecute(if (image != null) ExtendedBitmapDrawable(image, needInvert, orientation) else null)
				}
				else {
					onPostExecute(if (image != null) BitmapDrawable(ApplicationLoader.applicationContext.resources, image) else null)
				}
			}
		}

		private fun loadLastFrame(lottieDrawable: RLottieDrawable, w: Int, h: Int, lastFrame: Boolean) {
			val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
			val canvas = Canvas(bitmap)

			if (lastFrame) {
				canvas.scale(2f, 2f, w / 2f, h / 2f)
			}

			AndroidUtilities.runOnUIThread {
				lottieDrawable.setOnFrameReadyRunnable {
					lottieDrawable.setOnFrameReadyRunnable(null)

					var bitmapDrawable: BitmapDrawable? = null

					if (lottieDrawable.backgroundBitmap != null || lottieDrawable.renderingBitmap != null) {
						val currentBitmap = if (lottieDrawable.backgroundBitmap != null) lottieDrawable.backgroundBitmap else lottieDrawable.renderingBitmap

						canvas.save()

						if (!lastFrame) {
							canvas.scale((currentBitmap.width / w).toFloat(), (currentBitmap.height / h).toFloat(), w / 2f, h / 2f)
						}

						val paint = Paint(Paint.ANTI_ALIAS_FLAG)
						paint.isFilterBitmap = true

						canvas.drawBitmap(currentBitmap, 0f, 0f, paint)

						bitmapDrawable = BitmapDrawable(ApplicationLoader.applicationContext.resources, bitmap)
					}

					onPostExecute(bitmapDrawable)

					lottieDrawable.recycle()
				}

				lottieDrawable.setCurrentFrame(if (lastFrame) lottieDrawable.framesCount - 1 else 0, true, true)
			}
		}

		private fun onPostExecute(drawable: Drawable?) {
			AndroidUtilities.runOnUIThread {
				val cacheImageKey = cacheImage.key ?: return@runOnUIThread
				var toSet: Drawable? = null
				var decrementKey: String? = null

				if (drawable is RLottieDrawable) {
					toSet = lottieMemCache[cacheImageKey]

					if (toSet == null) {
						lottieMemCache.put(cacheImageKey, drawable)
						toSet = drawable
					}
					else {
						drawable.recycle()
					}

					incrementUseCount(cacheImageKey)

					decrementKey = cacheImageKey
				}
				else if (drawable is AnimatedFileDrawable) {
					if (drawable.isWebmSticker) {
						toSet = getFromLottieCache(cacheImageKey)

						if (toSet == null) {
							lottieMemCache.put(cacheImageKey, drawable)
							toSet = drawable
						}
						else {
							drawable.recycle()
						}

						incrementUseCount(cacheImageKey)

						decrementKey = cacheImageKey
					}
					else {
						toSet = drawable
					}
				}
				else if (drawable is BitmapDrawable) {
					toSet = getFromMemCache(cacheImageKey)

					var incrementUseCount = true

					if (toSet == null) {
						if (cacheImageKey.endsWith("_f")) {
							wallpaperMemCache.put(cacheImageKey, drawable)
							incrementUseCount = false
						}
						else if (!cacheImageKey.endsWith("_isc") && drawable.bitmap.width <= 80 * AndroidUtilities.density && drawable.bitmap.height <= 80 * AndroidUtilities.density) {
							smallImagesMemCache.put(cacheImageKey, drawable)
						}
						else {
							memCache.put(cacheImageKey, drawable)
						}

						toSet = drawable
					}
					else {
						val image = drawable.bitmap
						image.recycle()
					}

					if (incrementUseCount) {
						incrementUseCount(cacheImageKey)
						decrementKey = cacheImageKey
					}
				}

				val toSetFinal = toSet
				val decrementKetFinal = decrementKey

				imageLoadQueue.postRunnable({
					cacheImage.setImageAndClear(toSetFinal, decrementKetFinal)
				}, cacheImage.priority.toLong())
			}
		}

		fun cancel() {
			synchronized(sync) {
				try {
					isCancelled = true
					runningThread?.interrupt()
				}
				catch (e: Exception) {
					// don't prompt
				}
			}
		}
	}

	private inner class CacheImage {
		var priority: Int = 1
		var runningTask: Runnable? = null
		var key: String? = null
		var url: String? = null
		var filter: String? = null
		var ext: String? = null
		var secureDocument: SecureDocument? = null
		var imageLocation: ImageLocation? = null
		var parentObject: Any? = null
		var size: Long = 0
		var imageType: Int = 0
		var type: Int = 0
		var currentAccount: Int = 0
		var finalFilePath: File? = null
		var tempFilePath: File? = null
		var encryptionKeyPath: File? = null
		var artworkTask: ArtworkLoadTask? = null
		var httpTask: HttpImageTask? = null
		var cacheTask: CacheOutTask? = null
		val imageReceiverArray = mutableListOf<ImageReceiver>()
		val imageReceiverGuidsArray = mutableListOf<Int>()
		val keys = mutableListOf<String>()
		val filters = mutableListOf<String?>()
		val types = mutableListOf<Int>()

		fun addImageReceiver(imageReceiver: ImageReceiver, key: String, filter: String?, type: Int, guid: Int) {
			val index = imageReceiverArray.indexOf(imageReceiver)

			if (index >= 0) {
				imageReceiverGuidsArray[index] = guid
				return
			}

			imageReceiverArray.add(imageReceiver)
			imageReceiverGuidsArray.add(guid)
			keys.add(key)
			filters.add(filter)
			types.add(type)
			imageLoadingByTag.put(imageReceiver.getTag(type), this)
		}

		fun replaceImageReceiver(imageReceiver: ImageReceiver, key: String, filter: String?, type: Int, guid: Int) {
			var index = imageReceiverArray.indexOf(imageReceiver)

			if (index == -1) {
				return
			}

			if (types[index] != type) {
				index = imageReceiverArray.subList(index + 1, imageReceiverArray.size).indexOf(imageReceiver)
				if (index == -1) {
					return
				}
			}

			imageReceiverGuidsArray[index] = guid
			keys[index] = key
			filters[index] = filter
		}

		fun setImageReceiverGuid(imageReceiver: ImageReceiver, guid: Int) {
			val index = imageReceiverArray.indexOf(imageReceiver)

			if (index == -1) {
				return
			}

			imageReceiverGuidsArray[index] = guid
		}

		fun removeImageReceiver(imageReceiver: ImageReceiver) {
			var currentMediaType = type
			var a = 0

			while (a < imageReceiverArray.size) {
				val obj = imageReceiverArray[a]

				if (obj === imageReceiver) {
					imageReceiverArray.removeAt(a)
					imageReceiverGuidsArray.removeAt(a)
					keys.removeAt(a)
					filters.removeAt(a)
					currentMediaType = types.removeAt(a)
					imageLoadingByTag.remove(obj.getTag(currentMediaType))
					a--
				}

				a++
			}

			if (imageReceiverArray.isEmpty()) {
				if (imageLocation != null) {
					if (!forceLoadingImages.containsKey(key)) {
						if (imageLocation?.location != null) {
							FileLoader.getInstance(currentAccount).cancelLoadFile(imageLocation?.location, ext)
						}
						else if (imageLocation?.document != null) {
							FileLoader.getInstance(currentAccount).cancelLoadFile(imageLocation?.document)
						}
						else if (imageLocation?.secureDocument != null) {
							FileLoader.getInstance(currentAccount).cancelLoadFile(imageLocation?.secureDocument)
						}
						else if (imageLocation?.webFile != null) {
							FileLoader.getInstance(currentAccount).cancelLoadFile(imageLocation?.webFile)
						}
					}
				}

				if (cacheTask != null) {
					if (currentMediaType == ImageReceiver.TYPE_THUMB) {
						cacheThumbOutQueue.cancelRunnable(cacheTask)
					}
					else {
						cacheOutQueue.cancelRunnable(cacheTask)
						cacheOutQueue.cancelRunnable(runningTask)
					}

					cacheTask?.cancel()
					cacheTask = null
				}

				if (httpTask != null) {
					httpTasks.remove(httpTask)

					httpTask?.cancel(true)
					httpTask = null
				}
				if (artworkTask != null) {
					artworkTasks.remove(artworkTask)

					artworkTask?.cancel(true)
					artworkTask = null
				}

				if (url != null) {
					imageLoadingByUrl.remove(url)
				}

				if (key != null) {
					imageLoadingByKeys.remove(key)
				}
			}
		}

		fun setImageAndClear(image: Drawable?, decrementKey: String?) {
			if (image != null) {
				val finalImageReceiverArray = imageReceiverArray.toList()
				val finalImageReceiverGuidsArray = imageReceiverGuidsArray.toList()

				AndroidUtilities.runOnUIThread {
					if (image is AnimatedFileDrawable && !image.isWebmSticker) {
						var imageSet = false

						for (a in finalImageReceiverArray.indices) {
							val imgView = finalImageReceiverArray[a]
							val toSet = (if (a == 0) image else image.makeCopy())

							if (imgView.setImageBitmapByKey(toSet, key, type, false, finalImageReceiverGuidsArray[a])) {
								if (toSet === image) {
									imageSet = true
								}
							}
							else {
								if (toSet !== image) {
									toSet.recycle()
								}
							}
						}

						if (!imageSet) {
							image.recycle()
						}
					}
					else {
						for (a in finalImageReceiverArray.indices) {
							val imgView = finalImageReceiverArray[a]
							imgView.setImageBitmapByKey(image, key, types[a], false, finalImageReceiverGuidsArray[a])
						}
					}

					if (decrementKey != null) {
						decrementUseCount(decrementKey)
					}
				}
			}

			for (a in imageReceiverArray.indices) {
				val imageReceiver = imageReceiverArray[a]
				imageLoadingByTag.remove(imageReceiver.getTag(type))
			}

			imageReceiverArray.clear()
			imageReceiverGuidsArray.clear()

			if (url != null) {
				imageLoadingByUrl.remove(url)
			}

			if (key != null) {
				imageLoadingByKeys.remove(key)
			}
		}
	}

	companion object {
		const val AUTOPLAY_FILTER: String = "g"
		private val bytesLocal = ThreadLocal<ByteArray>()
		private val bytesThumbLocal = ThreadLocal<ByteArray>()
		private val header = ByteArray(12)
		private val headerThumb = ByteArray(12)

		@JvmStatic
		val instance = ImageLoader()

		fun hasAutoplayFilter(s: String?): Boolean {
			if (s == null) {
				return false
			}

			val words = s.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

			for (word in words) {
				if (AUTOPLAY_FILTER == word) {
					return true
				}
			}

			return false
		}

		fun decompressGzip(file: File?): String {
			val outStr = StringBuilder()

			if (file == null) {
				return ""
			}

			try {
				GZIPInputStream(FileInputStream(file)).use { gis ->
					BufferedReader(InputStreamReader(gis, "UTF-8")).use { bufferedReader ->
						var line: String?

						while ((bufferedReader.readLine().also { line = it }) != null) {
							outStr.append(line)
						}

						return outStr.toString()
					}
				}
			}
			catch (ignore: Exception) {
				return ""
			}
		}

		@JvmStatic
		fun getStrippedPhotoBitmap(photoBytes: ByteArray, filter: String?): Bitmap? {
			val len = photoBytes.size - 3 + Bitmaps.header.size + Bitmaps.footer.size
			var bytes = bytesLocal.get()
			var data = if (bytes != null && bytes.size >= len) bytes else null

			if (data == null) {
				data = ByteArray(len)
				bytes = data

				bytesLocal.set(bytes)
			}

			System.arraycopy(Bitmaps.header, 0, data, 0, Bitmaps.header.size)
			System.arraycopy(photoBytes, 3, data, Bitmaps.header.size, photoBytes.size - 3)
			System.arraycopy(Bitmaps.footer, 0, data, Bitmaps.header.size + photoBytes.size - 3, Bitmaps.footer.size)

			data[164] = photoBytes[1]
			data[166] = photoBytes[2]

			val bitmap = BitmapFactory.decodeByteArray(data, 0, len)

			if (bitmap != null && !TextUtils.isEmpty(filter) && filter!!.contains("b")) {
				Utilities.blurBitmap(bitmap, 3, 1, bitmap.width, bitmap.height, bitmap.rowBytes)

			}
			return bitmap
		}

		@TargetApi(26)
		private fun moveDirectory(source: File, target: File) {
			if (!source.exists() || (!target.exists() && !target.mkdir())) {
				return
			}
			try {
				Files.list(source.toPath()).use { files ->
					files.forEach { path ->
						val dest = File(target, path.fileName.toString())

						if (Files.isDirectory(path)) {
							moveDirectory(path.toFile(), dest)
						}
						else {
							try {
								Files.move(path, dest.toPath())
							}
							catch (e: Exception) {
								FileLog.e(e)
							}
						}
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		@JvmStatic
		fun getHttpFileName(url: String?): String {
			return Utilities.MD5(url)
		}

		@JvmStatic
		fun getHttpFilePath(url: String?, defaultExt: String?): File {
			val ext = getHttpUrlExtension(url, defaultExt)
			return File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), Utilities.MD5(url) + "." + ext)
		}

		fun shouldSendImageAsDocument(path: String?, uri: Uri?): Boolean {
			@Suppress("NAME_SHADOWING") var path = path

			val bmOptions = BitmapFactory.Options()
			bmOptions.inJustDecodeBounds = true

			if (path == null && uri != null && uri.scheme != null) {
				if (uri.scheme?.contains("file") == true) {
					path = uri.path
				}
				else {
					try {
						path = AndroidUtilities.getPath(uri)
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}
			}

			if (path != null) {
				BitmapFactory.decodeFile(path, bmOptions)
			}
			else if (uri != null) {
				try {
					ApplicationLoader.applicationContext.contentResolver?.openInputStream(uri)?.use {
						BitmapFactory.decodeStream(it, null, bmOptions)
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
					return false
				}
			}

			val photoW = bmOptions.outWidth.toFloat()
			val photoH = bmOptions.outHeight.toFloat()

			return photoW / photoH > 10.0f || photoH / photoW > 10.0f
		}

		@JvmStatic
		fun loadBitmap(path: String?, uri: Uri?, maxWidth: Float, maxHeight: Float, useMaxScale: Boolean): Bitmap? {
			@Suppress("NAME_SHADOWING") var path = path

			val bmOptions = BitmapFactory.Options()
			bmOptions.inJustDecodeBounds = true

			var inputStream: InputStream? = null

			if (path == null && uri != null && uri.scheme != null) {
				if (uri.scheme?.contains("file") == true) {
					path = uri.path
				}
				else if (Build.VERSION.SDK_INT < 30 || "content" != uri.scheme) {
					try {
						path = AndroidUtilities.getPath(uri)
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}
			}

			if (path != null) {
				BitmapFactory.decodeFile(path, bmOptions)
			}
			else if (uri != null) {
				try {
					inputStream = ApplicationLoader.applicationContext.contentResolver?.openInputStream(uri)

					BitmapFactory.decodeStream(inputStream, null, bmOptions)

					inputStream?.close()

					inputStream = ApplicationLoader.applicationContext.contentResolver?.openInputStream(uri)
				}
				catch (e: Throwable) {
					try {
						inputStream?.close()
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}

					FileLog.e(e)

					return null
				}
			}

			val photoW = bmOptions.outWidth.toFloat()
			val photoH = bmOptions.outHeight.toFloat()

			var scaleFactor = if (useMaxScale) max((photoW / maxWidth).toDouble(), (photoH / maxHeight).toDouble()).toFloat() else min((photoW / maxWidth).toDouble(), (photoH / maxHeight).toDouble()).toFloat()

			if (scaleFactor < 1) {
				scaleFactor = 1f
			}

			bmOptions.inJustDecodeBounds = false
			bmOptions.inSampleSize = scaleFactor.toInt()

			if (bmOptions.inSampleSize % 2 != 0) {
				var sample = 1

				while (sample * 2 < bmOptions.inSampleSize) {
					sample *= 2
				}

				bmOptions.inSampleSize = sample
			}

			var matrix: Matrix? = null

			try {
				var orientation = 0

				if (path != null) {
					val exif = ExifInterface(path)

					orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
				}
				else if (uri != null) {
					runCatching {
						ApplicationLoader.applicationContext.contentResolver?.openInputStream(uri)?.use { stream ->
							val exif = ExifInterface(stream)

							orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
						}
					}
				}

				when (orientation) {
					ExifInterface.ORIENTATION_ROTATE_90 -> {
						matrix = Matrix()
						matrix.postRotate(90f)
					}

					ExifInterface.ORIENTATION_ROTATE_180 -> {
						matrix = Matrix()
						matrix.postRotate(180f)
					}

					ExifInterface.ORIENTATION_ROTATE_270 -> {
						matrix = Matrix()
						matrix.postRotate(270f)
					}
				}
			}
			catch (e: Throwable) {
				// ignored
			}

			scaleFactor /= bmOptions.inSampleSize.toFloat()

			if (scaleFactor > 1) {
				if (matrix == null) {
					matrix = Matrix()
				}

				matrix.postScale(1.0f / scaleFactor, 1.0f / scaleFactor)
			}

			var b: Bitmap? = null

			if (path != null) {
				try {
					b = BitmapFactory.decodeFile(path, bmOptions)

					if (b != null) {
						val newBitmap = Bitmaps.createBitmap(b, 0, 0, b.width, b.height, matrix, true)

						if (newBitmap != b) {
							b.recycle()
							b = newBitmap
						}
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)

					instance.clearMemory()

					try {
						if (b == null) {
							b = BitmapFactory.decodeFile(path, bmOptions)

							if (b != null && bmOptions.inPurgeable) {
								Utilities.pinBitmap(b)
							}
						}

						if (b != null) {
							val newBitmap = Bitmaps.createBitmap(b, 0, 0, b.width, b.height, matrix, true)

							if (newBitmap != b) {
								b.recycle()
								b = newBitmap
							}
						}
					}
					catch (e2: Throwable) {
						FileLog.e(e2)
					}
				}
			}
			else if (uri != null) {
				try {
					b = BitmapFactory.decodeStream(inputStream, null, bmOptions)

					if (b != null) {
						val newBitmap = Bitmaps.createBitmap(b, 0, 0, b.width, b.height, matrix, true)

						if (newBitmap != b) {
							b.recycle()
							b = newBitmap
						}
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
				finally {
					try {
						inputStream?.close()
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}
			}

			return b
		}

		fun fillPhotoSizeWithBytes(photoSize: PhotoSize?) {
			if (photoSize == null || (photoSize.bytes != null && photoSize.bytes.isNotEmpty())) {
				return
			}

			val file = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(photoSize, true)

			try {
				RandomAccessFile(file, "r").use {
					val len = it.length().toInt()

					if (len < 20000) {
						photoSize.bytes = ByteArray(it.length().toInt())

						it.readFully(photoSize.bytes, 0, photoSize.bytes.size)
					}
				}
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}

		@Throws(Exception::class)
		private fun scaleAndSaveImageInternal(photoSize: PhotoSize?, bitmap: Bitmap, compressFormat: CompressFormat, w: Int, h: Int, scaleFactor: Float, quality: Int, cache: Boolean, scaleAnyway: Boolean, forceCacheDir: Boolean): PhotoSize {
			@Suppress("NAME_SHADOWING") var photoSize = photoSize

			val scaledBitmap = if (scaleFactor > 1 || scaleAnyway) {
				Bitmaps.createScaledBitmap(bitmap, w, h, true)
			}
			else {
				bitmap
			}

			val location: TL_fileLocationToBeDeprecated

			if (photoSize == null || photoSize.location !is TL_fileLocationToBeDeprecated) {
				location = TL_fileLocationToBeDeprecated()
				location.volume_id = Int.MIN_VALUE.toLong()
				location.dc_id = Int.MIN_VALUE
				location.local_id = SharedConfig.getLastLocalId()
				location.file_reference = ByteArray(0)

				photoSize = TL_photoSize_layer127()
				photoSize.location = location
				photoSize.w = scaledBitmap.width
				photoSize.h = scaledBitmap.height

				if (photoSize.w <= 100 && photoSize.h <= 100) {
					photoSize.type = "s"
				}
				else if (photoSize.w <= 320 && photoSize.h <= 320) {
					photoSize.type = "m"
				}
				else if (photoSize.w <= 800 && photoSize.h <= 800) {
					photoSize.type = "x"
				}
				else if (photoSize.w <= 1280 && photoSize.h <= 1280) {
					photoSize.type = "y"
				}
				else {
					photoSize.type = "w"
				}
			}
			else {
				location = photoSize.location as TL_fileLocationToBeDeprecated
			}

			val fileName = location.volume_id.toString() + "_" + location.local_id + ".jpg"

			val fileDir = if (forceCacheDir) {
				FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)
			}
			else {
				if (location.volume_id != Int.MIN_VALUE.toLong()) FileLoader.getDirectory(FileLoader.MEDIA_DIR_IMAGE) else FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE)
			}

			val cacheFile = File(fileDir, fileName)

			//TODO was crash in DEBUG_PRIVATE
//        if (compressFormat == Bitmap.CompressFormat.JPEG && progressive && BuildConfig.DEBUG) {
//            photoSize.size = Utilities.saveProgressiveJpeg(scaledBitmap, scaledBitmap.getWidth(), scaledBitmap.getHeight(), scaledBitmap.getRowBytes(), quality, cacheFile.getAbsolutePath());
//        } else {

			val stream = FileOutputStream(cacheFile)

			scaledBitmap.compress(compressFormat, quality, stream)

			if (!cache) {
				photoSize.size = stream.channel.size().toInt()
			}

			stream.close()
			// }

			if (cache) {
				val stream2 = ByteArrayOutputStream()
				scaledBitmap.compress(compressFormat, quality, stream2)

				photoSize.bytes = stream2.toByteArray()
				photoSize.size = photoSize.bytes.size

				stream2.close()
			}

			if (scaledBitmap != bitmap) {
				scaledBitmap.recycle()
			}

			return photoSize
		}

		fun scaleAndSaveImage(bitmap: Bitmap?, maxWidth: Float, maxHeight: Float, quality: Int, cache: Boolean): PhotoSize? {
			return scaleAndSaveImage(null, bitmap, CompressFormat.JPEG, false, maxWidth, maxHeight, quality, cache, 0, 0, false)
		}

		fun scaleAndSaveImage(photoSize: PhotoSize?, bitmap: Bitmap?, maxWidth: Float, maxHeight: Float, quality: Int, cache: Boolean, forceCacheDir: Boolean): PhotoSize? {
			return scaleAndSaveImage(photoSize, bitmap, CompressFormat.JPEG, false, maxWidth, maxHeight, quality, cache, 0, 0, forceCacheDir)
		}

		@JvmStatic
		fun scaleAndSaveImage(bitmap: Bitmap?, maxWidth: Float, maxHeight: Float, quality: Int, cache: Boolean, minWidth: Int, minHeight: Int): PhotoSize? {
			return scaleAndSaveImage(null, bitmap, CompressFormat.JPEG, false, maxWidth, maxHeight, quality, cache, minWidth, minHeight, false)
		}

		fun scaleAndSaveImage(bitmap: Bitmap?, maxWidth: Float, maxHeight: Float, progressive: Boolean, quality: Int, cache: Boolean, minWidth: Int, minHeight: Int): PhotoSize? {
			return scaleAndSaveImage(null, bitmap, CompressFormat.JPEG, progressive, maxWidth, maxHeight, quality, cache, minWidth, minHeight, false)
		}

		@JvmStatic
		fun scaleAndSaveImage(bitmap: Bitmap?, compressFormat: CompressFormat, maxWidth: Float, maxHeight: Float, quality: Int, cache: Boolean, minWidth: Int, minHeight: Int): PhotoSize? {
			return scaleAndSaveImage(null, bitmap, compressFormat, false, maxWidth, maxHeight, quality, cache, minWidth, minHeight, false)
		}

		fun scaleAndSaveImage(photoSize: PhotoSize?, bitmap: Bitmap?, compressFormat: CompressFormat, progressive: Boolean, maxWidth: Float, maxHeight: Float, quality: Int, cache: Boolean, minWidth: Int, minHeight: Int, forceCacheDir: Boolean): PhotoSize? {
			if (bitmap == null) {
				return null
			}

			val photoW = bitmap.width.toFloat()
			val photoH = bitmap.height.toFloat()

			if (photoW == 0f || photoH == 0f) {
				return null
			}

			var scaleAnyway = false
			var scaleFactor = max((photoW / maxWidth).toDouble(), (photoH / maxHeight).toDouble()).toFloat()

			if (minWidth != 0 && minHeight != 0 && (photoW < minWidth || photoH < minHeight)) {
				scaleFactor = if (photoW < minWidth && photoH > minHeight) {
					photoW / minWidth.toFloat()
				}
				else if (photoW > minWidth && photoH < minHeight) {
					photoH / minHeight.toFloat()
				}
				else {
					max((photoW / minWidth.toFloat()).toDouble(), (photoH / minHeight.toFloat()).toDouble()).toFloat()
				}

				scaleAnyway = true
			}

			val w = (photoW / scaleFactor).toInt()
			val h = (photoH / scaleFactor).toInt()

			if (h == 0 || w == 0) {
				return null
			}

			try {
				return scaleAndSaveImageInternal(photoSize, bitmap, compressFormat, w, h, scaleFactor, quality, cache, scaleAnyway, forceCacheDir)
			}
			catch (e: Throwable) {
				FileLog.e(e)

				instance.clearMemory()

				System.gc()

				try {
					return scaleAndSaveImageInternal(photoSize, bitmap, compressFormat, w, h, scaleFactor, quality, cache, scaleAnyway, forceCacheDir)
				}
				catch (e2: Throwable) {
					FileLog.e(e2)
					return null
				}
			}
		}

		@JvmStatic
		fun getHttpUrlExtension(url: String?, defaultExt: String?): String? {
			@Suppress("NAME_SHADOWING") var url = url
			var ext: String? = null
			val last = Uri.parse(url).lastPathSegment

			if (!last.isNullOrEmpty() && last.length > 1) {
				url = last
			}

			val idx = url?.lastIndexOf('.') ?: -1

			if (idx != -1) {
				ext = url?.substring(idx + 1)
			}

			if (ext.isNullOrEmpty() || ext.length > 4) {
				ext = defaultExt
			}

			return ext
		}

		fun saveMessageThumbs(message: Message) {
			if (message.media == null) {
				return
			}

			val photoSize = findPhotoCachedSize(message)

			if (photoSize?.bytes != null && photoSize.bytes.isNotEmpty()) {
				if (photoSize.location == null || photoSize.location is TL_fileLocationUnavailable) {
					photoSize.location = TL_fileLocationToBeDeprecated()
					photoSize.location.volume_id = Int.MIN_VALUE.toLong()
					photoSize.location.local_id = SharedConfig.getLastLocalId()
				}

				var file = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(photoSize, true)
				var isEncrypted = false

				if (shouldEncryptPhotoOrVideo(message)) {
					file = File(file.absolutePath + ".enc")
					isEncrypted = true
				}

				if (!file.exists()) {
					try {
						if (isEncrypted) {
							val keyPath = File(FileLoader.internalCacheDir, file.name + ".key")

							RandomAccessFile(keyPath, "rws").use {
								val len = it.length()
								val encryptKey = ByteArray(32)
								val encryptIv = ByteArray(16)

								if (len > 0 && len % 48 == 0L) {
									it.read(encryptKey, 0, 32)
									it.read(encryptIv, 0, 16)
								}
								else {
									Utilities.random.nextBytes(encryptKey)
									Utilities.random.nextBytes(encryptIv)

									it.write(encryptKey)
									it.write(encryptIv)
								}

								Utilities.aesCtrDecryptionByteArray(photoSize.bytes, encryptKey, encryptIv, 0, photoSize.bytes.size.toLong(), 0)
							}
						}

						RandomAccessFile(file, "rws").use {
							it.write(photoSize.bytes)
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				val newPhotoSize: TL_photoSize = TL_photoSize_layer127()
				newPhotoSize.w = photoSize.w
				newPhotoSize.h = photoSize.h
				newPhotoSize.location = photoSize.location
				newPhotoSize.size = photoSize.size
				newPhotoSize.type = photoSize.type

				if (message.media is TL_messageMediaPhoto) {
					var a = 0
					val count = message.media?.photo?.sizes?.size ?: 0

					while (a < count) {
						val size = message.media?.photo?.sizes?.get(a)

						if (size is TL_photoCachedSize) {
							message.media?.photo?.sizes?.set(a, newPhotoSize)
							break
						}

						a++
					}
				}
				else if (message.media is TL_messageMediaDocument) {
					var a = 0
					val count = message.media?.document?.thumbs?.size ?: 0

					while (a < count) {
						val size = message.media?.document?.thumbs?.get(a)

						if (size is TL_photoCachedSize) {
							message.media?.document?.thumbs?.set(a, newPhotoSize)
							break
						}

						a++
					}
				}
				else if (message.media is TL_messageMediaWebPage) {
					var a = 0
					val count = message.media?.webpage?.photo?.sizes?.size ?: 0

					while (a < count) {
						val size = message.media?.webpage?.photo?.sizes?.get(a)

						if (size is TL_photoCachedSize) {
							message.media?.webpage?.photo?.sizes?.set(a, newPhotoSize)
							break
						}

						a++
					}
				}
			}
		}

		private fun findPhotoCachedSize(message: Message): PhotoSize? {
			var photoSize: PhotoSize? = null

			if (message.media is TL_messageMediaPhoto) {
				photoSize = message.media?.photo?.sizes?.find { it is TL_photoCachedSize }
			}
			else if (message.media is TL_messageMediaDocument) {
				photoSize = message.media?.document?.thumbs?.find { it is TL_photoCachedSize }
			}
			else if (message.media is TL_messageMediaWebPage) {
				photoSize = message.media?.webpage?.photo?.sizes?.find { it is TL_photoCachedSize }
			}
			else if (message.media is TL_messageMediaInvoice && message.media?.extended_media is TL_messageExtendedMediaPreview) {
				photoSize = (message.media?.extended_media as? TL_messageExtendedMediaPreview)?.thumb
			}

			return photoSize
		}

		fun saveMessagesThumbs(messages: List<Message>?) {
			if (messages.isNullOrEmpty()) {
				return
			}

			for (message in messages) {
				saveMessageThumbs(message)
			}
		}

		fun generateMessageThumb(message: Message): MessageThumb? {
			val photoSize = findPhotoCachedSize(message)

			if (photoSize?.bytes != null && photoSize.bytes.isNotEmpty()) {
				val file = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(photoSize, true)

				val newPhotoSize: TL_photoSize = TL_photoSize_layer127()
				newPhotoSize.w = photoSize.w
				newPhotoSize.h = photoSize.h
				newPhotoSize.location = photoSize.location
				newPhotoSize.size = photoSize.size
				newPhotoSize.type = photoSize.type

				if (file.exists() && message.realGroupId == 0L) {
					val h = photoSize.h
					val w = photoSize.w
					val point = ChatMessageCell.getMessageSize(w, h)
					val key = String.format(Locale.US, "%d_%d@%d_%d_b", photoSize.location.volume_id, photoSize.location.local_id, (point.x / AndroidUtilities.density).toInt(), (point.y / AndroidUtilities.density).toInt())

					if (!instance.isInMemCache(key, false)) {
						var bitmap = loadBitmap(file.path, null, (point.x / AndroidUtilities.density).toInt().toFloat(), (point.y / AndroidUtilities.density).toInt().toFloat(), false)

						if (bitmap != null) {
							Utilities.blurBitmap(bitmap, 3, 1, bitmap.width, bitmap.height, bitmap.rowBytes)

							val scaledBitmap = Bitmaps.createScaledBitmap(bitmap, (point.x / AndroidUtilities.density).toInt(), (point.y / AndroidUtilities.density).toInt(), true)

							if (scaledBitmap != bitmap) {
								bitmap.recycle()
								bitmap = scaledBitmap
							}

							return MessageThumb(key, BitmapDrawable(ApplicationLoader.applicationContext.resources, bitmap))
						}
					}
				}
			}
			else if (message.media is TL_messageMediaDocument) {
				var a = 0
				val count = message.media?.document?.thumbs?.size ?: 0

				while (a < count) {
					val size = message.media?.document?.thumbs?.get(a)

					if (size is TL_photoStrippedSize) {
						val thumbSize = FileLoader.getClosestPhotoSizeWithSize(message.media?.document?.thumbs, 320)
						var h = 0
						var w = 0

						if (thumbSize != null) {
							h = thumbSize.h
							w = thumbSize.w
						}
						else {
							val attrs = message.media?.document?.attributes

							if (attrs != null) {
								for (attr in attrs) {
									if (attr is TL_documentAttributeVideo) {
										h = attr.h
										w = attr.w
										break
									}
								}
							}
						}

						val point = ChatMessageCell.getMessageSize(w, h)
						val key = String.format(Locale.US, "%s_false@%d_%d_b", ImageLocation.getStrippedKey(message, message, size), (point.x / AndroidUtilities.density).toInt(), (point.y / AndroidUtilities.density).toInt())

						if (!instance.isInMemCache(key, false)) {
							var b = getStrippedPhotoBitmap(size.bytes, null)

							if (b != null) {
								Utilities.blurBitmap(b, 3, 1, b.width, b.height, b.rowBytes)

								val scaledBitmap = Bitmaps.createScaledBitmap(b, (point.x / AndroidUtilities.density).toInt(), (point.y / AndroidUtilities.density).toInt(), true)

								if (scaledBitmap != b) {
									b.recycle()
									b = scaledBitmap
								}

								return MessageThumb(key, BitmapDrawable(ApplicationLoader.applicationContext.resources, b))
							}
						}
					}
					a++
				}
			}

			return null
		}
	}
}
