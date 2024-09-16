/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger

import org.telegram.messenger.ApplicationLoader.Companion.currentNetworkType
import org.telegram.messenger.ApplicationLoader.Companion.filesDirFixed
import org.telegram.messenger.FilePathDatabase.PathData
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.NativeByteBuffer
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.InputFileLocation
import org.telegram.tgnet.TLRPC.InputWebFileLocation
import org.telegram.tgnet.TLRPC.TL_document
import org.telegram.tgnet.TLRPC.TL_documentAttributeVideo
import org.telegram.tgnet.TLRPC.TL_documentEncrypted
import org.telegram.tgnet.TLRPC.TL_error
import org.telegram.tgnet.TLRPC.TL_fileHash
import org.telegram.tgnet.TLRPC.TL_inputDocumentFileLocation
import org.telegram.tgnet.TLRPC.TL_inputEncryptedFileLocation
import org.telegram.tgnet.TLRPC.TL_inputFileLocation
import org.telegram.tgnet.TLRPC.TL_inputPeerPhotoFileLocation
import org.telegram.tgnet.TLRPC.TL_inputPhotoFileLocation
import org.telegram.tgnet.TLRPC.TL_inputSecureFileLocation
import org.telegram.tgnet.TLRPC.TL_inputStickerSetThumb
import org.telegram.tgnet.TLRPC.TL_theme
import org.telegram.tgnet.TLRPC.TL_upload_cdnFile
import org.telegram.tgnet.TLRPC.TL_upload_cdnFileReuploadNeeded
import org.telegram.tgnet.TLRPC.TL_upload_file
import org.telegram.tgnet.TLRPC.TL_upload_fileCdnRedirect
import org.telegram.tgnet.TLRPC.TL_upload_getCdnFile
import org.telegram.tgnet.TLRPC.TL_upload_getCdnFileHashes
import org.telegram.tgnet.TLRPC.TL_upload_getFile
import org.telegram.tgnet.TLRPC.TL_upload_getWebFile
import org.telegram.tgnet.TLRPC.TL_upload_reuploadCdnFile
import org.telegram.tgnet.TLRPC.TL_upload_webFile
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.Vector
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.Scanner
import java.util.concurrent.CountDownLatch
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

class FileLoadOperation {
	private var stream: FileLoadOperationStream? = null
	private var streamPriority = false
	private var streamOffset = 0L
	private var streamListeners: ArrayList<FileLoadOperationStream>? = null
	private val downloadChunkSize = 1024 * 512 // was 32 kB
	private var downloadChunkSizeBig = 1024 * 1024 // was 128 kB
	private val cdnChunkCheckSize = 1024 * 1024 // was 128 kB
	private var maxDownloadRequests = 4
	private var maxDownloadRequestsBig = 4
	private val bigFileSizeFrom = 10 * 1024 * 1024
	private var maxCdnParts = (FileLoader.DEFAULT_MAX_FILE_SIZE / downloadChunkSizeBig).toInt()
	private val maxDownloadRequestsAnimation = 4
	private var storeFileName: String? = null
	private var preloadedBytesRanges: HashMap<Long, PreloadRange?>? = null
	private var requestedPreloadedBytesRanges: HashMap<Long, Int>? = null
	private var preloadStream: RandomAccessFile? = null
	private var preloadStreamFileOffset = 0
	private var totalPreloadedBytes = 0
	private var isPreloadVideoOperation = false
	private var cacheFilePreload: File? = null
	private var supportsPreloading = false
	private var nextPreloadDownloadOffset: Long = 0
	private var nextAtomOffset: Long = 0
	private var foundMoovSize: Long = 0
	private var preloadNotRequestedBytesCount: Long = 0
	private var moovFound = 0
	private val preloadTempBuffer = ByteArray(24)
	private var preloadTempBufferCount = 0
	private var nextPartWasPreloaded = false
	private var notLoadedBytesRanges: ArrayList<Range>? = null
	private var notRequestedBytesRanges: ArrayList<Range>? = null
	private var notCheckedCdnRanges: ArrayList<Range>? = null
	private var requestedBytesCount: Long = 0
	private var currentAccount = 0
	private var started = false
	private var datacenterId = 0
	private var initialDatacenterId = 0
	private var webLocation: InputWebFileLocation? = null
	private var webFile: WebFile? = null
	private var downloadedBytes: Long = 0
	private var totalBytesCount: Long = 0
	private var bytesCountPadding: Long = 0
	private var streamStartOffset: Long = 0
	private var streamPriorityStartOffset: Long = 0
	private var priorityRequestInfo: RequestInfo? = null
	private var delegate: FileLoadOperationDelegate? = null
	private var key: ByteArray? = null
	private var iv: ByteArray? = null
	private var currentDownloadChunkSize = 0
	private var currentMaxDownloadRequests = 0
	private var requestsCount = 0
	private var renameRetryCount = 0
	private var encryptFile = false
	private var allowDisordererFileSave = false
	var parentObject: Any? = null
	private var cdnHashes: HashMap<Long, TL_fileHash>? = null
	private var isStream = false
	private var encryptKey: ByteArray? = null
	private var encryptIv: ByteArray? = null
	private var isCdn = false
	private var cdnIv: ByteArray? = null
	private var cdnKey: ByteArray? = null
	private var cdnToken: ByteArray? = null
	private var cdnDatacenterId = 0
	private var reuploadingCdn = false
	private var fileReadStream: RandomAccessFile? = null
	private var cdnCheckBytes: ByteArray? = null
	private var requestingCdnOffsets = false
	private var requestInfos = ArrayList<RequestInfo>()
	private var delayedRequestInfos = ArrayList<RequestInfo>()
	private var cacheFileTemp: File? = null
	private var cacheFileGzipTemp: File? = null
	private var cacheIvTemp: File? = null
	private var cacheFileParts: File? = null
	private var ext: String? = null
	private var fileOutputStream: RandomAccessFile? = null
	private var fiv: RandomAccessFile? = null
	private var filePartsStream: RandomAccessFile? = null
	private var storePath: File? = null
	private var tempPath: File? = null
	private var ungzip = false
	private var startTime: Long = 0

	//load small parts for stream
	private val downloadChunkSizeAnimation = 1024 * 128

	var isPreloadFinished = false
		private set

	@JvmField
	var lastProgressUpdateTime: Long = 0

	@Volatile
	private var notLoadedBytesRangesCopy: ArrayList<Range>? = null

	@JvmField
	var location: InputFileLocation? = null

	@Volatile
	private var state = STATE_IDLE

	@Volatile
	var isPaused = false
		private set

	@JvmField
	var requestingReference = false

	var cacheFileFinal: File? = null
		private set

	@JvmField
	var isForceRequest = false

	@JvmField
	var priority = 0

	var currentType = 0
		private set

	@JvmField
	var pathSaveData: PathData? = null

	var queue: FileLoaderPriorityQueue? = null
		private set

	var fileName: String? = null
		private set

	class RequestInfo {
		var requestToken = 0
		var offset: Long = 0
		var response: TL_upload_file? = null
		var responseWeb: TL_upload_webFile? = null
		var responseCdn: TL_upload_cdnFile? = null
	}

	data class Range(var start: Long, var end: Long)
	private data class PreloadRange(val fileOffset: Long, val length: Long)

	interface FileLoadOperationDelegate {
		fun didFinishLoadingFile(operation: FileLoadOperation, finalFile: File?)
		fun didFailedLoadingFile(operation: FileLoadOperation, state: Int)
		fun didChangedLoadProgress(operation: FileLoadOperation, uploadedSize: Long, totalSize: Long)
		fun saveFilePath(pathSaveData: PathData, cacheFileFinal: File?)
		fun hasAnotherRefOnFile(path: String): Boolean
	}

	fun setStream(stream: FileLoadOperationStream?, streamPriority: Boolean, streamOffset: Long) {
		this.stream = stream
		this.streamOffset = streamOffset
		this.streamPriority = streamPriority
	}

	private fun updateParams() {
		if (MessagesController.getInstance(currentAccount).getfileExperimentalParams) {
			downloadChunkSizeBig = 1024 * 512
			maxDownloadRequests = 8
			maxDownloadRequestsBig = 8
		}
		else {
			downloadChunkSizeBig = 1024 * 128
			maxDownloadRequests = 4
			maxDownloadRequestsBig = 4
		}

		maxCdnParts = (FileLoader.DEFAULT_MAX_FILE_SIZE / downloadChunkSizeBig).toInt()
	}

	constructor(imageLocation: ImageLocation, parent: Any?, extension: String?, size: Long) {
		updateParams()

		parentObject = parent
		isStream = imageLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION

		if (imageLocation.isEncrypted) {
			location = TL_inputEncryptedFileLocation()
			location?.id = imageLocation.location?.volume_id ?: 0L
			location?.volume_id = imageLocation.location?.volume_id ?: 0L
			location?.local_id = imageLocation.location?.local_id ?: 0
			location?.access_hash = imageLocation.accessHash

			iv = ByteArray(32)

			System.arraycopy(imageLocation.iv, 0, iv!!, 0, iv!!.size)

			key = imageLocation.key
		}
		else if (imageLocation.photoPeer != null) {
			val inputPeerPhotoFileLocation = TL_inputPeerPhotoFileLocation()
			inputPeerPhotoFileLocation.id = imageLocation.location?.volume_id ?: 0L
			inputPeerPhotoFileLocation.volume_id = imageLocation.location?.volume_id ?: 0L
			inputPeerPhotoFileLocation.local_id = imageLocation.location?.local_id ?: 0
			inputPeerPhotoFileLocation.photo_id = imageLocation.photoId
			inputPeerPhotoFileLocation.big = imageLocation.photoPeerType == ImageLocation.TYPE_BIG
			inputPeerPhotoFileLocation.peer = imageLocation.photoPeer

			location = inputPeerPhotoFileLocation
		}
		else if (imageLocation.stickerSet != null) {
			val inputStickerSetThumb = TL_inputStickerSetThumb()
			inputStickerSetThumb.id = imageLocation.location?.volume_id ?: 0L
			inputStickerSetThumb.volume_id = imageLocation.location?.volume_id ?: 0L
			inputStickerSetThumb.local_id = imageLocation.location?.local_id ?: 0
			inputStickerSetThumb.thumb_version = imageLocation.thumbVersion
			inputStickerSetThumb.stickerset = imageLocation.stickerSet

			location = inputStickerSetThumb
		}
		else if (imageLocation.thumbSize != null) {
			if (imageLocation.photoId != 0L) {
				location = TL_inputPhotoFileLocation()
				location?.id = imageLocation.photoId
				location?.volume_id = imageLocation.location?.volume_id ?: 0L
				location?.local_id = imageLocation.location?.local_id ?: 0
				location?.access_hash = imageLocation.accessHash
				location?.file_reference = imageLocation.fileReference
				location?.thumb_size = imageLocation.thumbSize

				if (imageLocation.imageType == FileLoader.IMAGE_TYPE_ANIMATION) {
					allowDisordererFileSave = true
				}
			}
			else {
				location = TL_inputDocumentFileLocation()
				location?.id = imageLocation.documentId
				location?.volume_id = imageLocation.location?.volume_id ?: 0L
				location?.local_id = imageLocation.location?.local_id ?: 0
				location?.access_hash = imageLocation.accessHash
				location?.file_reference = imageLocation.fileReference
				location?.thumb_size = imageLocation.thumbSize
			}

			if (location?.file_reference == null) {
				location?.file_reference = ByteArray(0)
			}
		}
		else {
			location = TL_inputFileLocation()
			location?.volume_id = imageLocation.location?.volume_id ?: 0L
			location?.local_id = imageLocation.location?.local_id ?: 0
			location?.secret = imageLocation.accessHash
			location?.file_reference = imageLocation.fileReference

			if (location?.file_reference == null) {
				location?.file_reference = ByteArray(0)
			}

			allowDisordererFileSave = true
		}

		ungzip = imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE || imageLocation.imageType == FileLoader.IMAGE_TYPE_SVG
		datacenterId = imageLocation.dcId
		initialDatacenterId = datacenterId
		currentType = ConnectionsManager.FileTypePhoto
		totalBytesCount = size
		ext = extension ?: "jpg"
	}

	constructor(secureDocument: SecureDocument) {
		updateParams()

		location = TL_inputSecureFileLocation()
		location?.id = secureDocument.secureFile.id
		location?.access_hash = secureDocument.secureFile.access_hash

		datacenterId = secureDocument.secureFile.dc_id
		totalBytesCount = secureDocument.secureFile.size
		allowDisordererFileSave = true
		currentType = ConnectionsManager.FileTypeFile
		ext = ".jpg"
	}

	constructor(instance: Int, webDocument: WebFile) {
		updateParams()

		currentAccount = instance
		webFile = webDocument
		webLocation = webDocument.location
		totalBytesCount = webDocument.size.toLong()
		datacenterId = MessagesController.getInstance(currentAccount).webFileDatacenterId
		initialDatacenterId = datacenterId

		val defaultExt = FileLoader.getMimeTypePart(webDocument.mimeType ?: "")

		currentType = if (webDocument.mimeType?.startsWith("image/") == true) {
			ConnectionsManager.FileTypePhoto
		}
		else if (webDocument.mimeType == "audio/ogg") {
			ConnectionsManager.FileTypeAudio
		}
		else if (webDocument.mimeType?.startsWith("video/") == true) {
			ConnectionsManager.FileTypeVideo
		}
		else {
			ConnectionsManager.FileTypeFile
		}

		allowDisordererFileSave = true

		ext = ImageLoader.getHttpUrlExtension(webDocument.url, defaultExt)
	}

	constructor(documentLocation: TLRPC.Document, parent: Any?) {
		updateParams()

		try {
			parentObject = parent

			if (documentLocation is TL_documentEncrypted) {
				location = TL_inputEncryptedFileLocation()
				location?.id = documentLocation.id
				location?.access_hash = documentLocation.access_hash

				datacenterId = documentLocation.dc_id
				initialDatacenterId = datacenterId
				iv = ByteArray(32)

				System.arraycopy(documentLocation.iv, 0, iv!!, 0, iv!!.size)

				key = documentLocation.key
			}
			else if (documentLocation is TL_document) {
				location = TL_inputDocumentFileLocation()
				location?.id = documentLocation.id
				location?.access_hash = documentLocation.access_hash
				location?.file_reference = documentLocation.file_reference
				location?.thumb_size = ""

				if (location?.file_reference == null) {
					location?.file_reference = ByteArray(0)
				}

				datacenterId = documentLocation.dc_id
				initialDatacenterId = datacenterId
				allowDisordererFileSave = true

				for (attributes in documentLocation.attributes) {
					if (attributes is TL_documentAttributeVideo) {
						supportsPreloading = true
						break
					}
				}
			}

			ungzip = "application/x-tgsticker" == documentLocation.mime_type || "application/x-tgwallpattern" == documentLocation.mime_type
			totalBytesCount = documentLocation.size

			if (key != null) {
				if (totalBytesCount % 16 != 0L) {
					bytesCountPadding = 16 - totalBytesCount % 16
					totalBytesCount += bytesCountPadding
				}
			}

			ext = FileLoader.getDocumentFileName(documentLocation)
			var idx = 0

			if (ext == null || ext?.lastIndexOf('.')?.also { idx = it } == -1) {
				ext = ""
			}
			else {
				ext = ext?.substring(idx)
			}

			currentType = if ("audio/ogg" == documentLocation.mime_type) {
				ConnectionsManager.FileTypeAudio
			}
			else if (FileLoader.isVideoMimeType(documentLocation.mime_type)) {
				ConnectionsManager.FileTypeVideo
			}
			else {
				ConnectionsManager.FileTypeFile
			}

			if ((ext?.length ?: 0) <= 1) {
				ext = FileLoader.getExtensionByMimeType(documentLocation.mime_type)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
			onFail(true, 0)
		}
	}

	fun setEncryptFile(value: Boolean) {
		encryptFile = value

		if (encryptFile) {
			allowDisordererFileSave = false
		}
	}

	fun setPaths(instance: Int, name: String?, priorityQueue: FileLoaderPriorityQueue?, store: File?, temp: File?, finalName: String?) {
		storePath = store
		tempPath = temp
		currentAccount = instance
		fileName = name
		storeFileName = finalName
		queue = priorityQueue
	}

	fun wasStarted(): Boolean {
		return started && !isPaused
	}

	private fun removePart(ranges: ArrayList<Range>?, start: Long, end: Long) {
		if (ranges == null || end < start) {
			return
		}

		val count = ranges.size
		var range: Range
		var modified = false

		for (a in 0 until count) {
			range = ranges[a]

			if (start == range.end) {
				range.end = end
				modified = true
				break
			}
			else if (end == range.start) {
				range.start = start
				modified = true
				break
			}
		}

		ranges.sortWith { o1, o2 ->
			if (o1.start > o2.start) {
				return@sortWith 1
			}
			else if (o1.start < o2.start) {
				return@sortWith -1
			}
			else {
				return@sortWith 0
			}
		}

		var a = 0

		while (a < ranges.size - 1) {
			val r1 = ranges[a]
			val r2 = ranges[a + 1]

			if (r1.end == r2.start) {
				r1.end = r2.end
				ranges.removeAt(a + 1)
				a--
			}

			a++
		}

		if (!modified) {
			ranges.add(Range(start, end))
		}
	}

	private fun addPart(ranges: ArrayList<Range>?, start: Long, end: Long, save: Boolean) {
		if (ranges == null || end < start) {
			return
		}

		var modified = false
		val count = ranges.size
		var range: Range

		for (a in 0 until count) {
			range = ranges[a]

			if (start <= range.start) {
				if (end >= range.end) {
					ranges.removeAt(a)
					modified = true
					break
				}
				else if (end > range.start) {
					range.start = end
					modified = true
					break
				}
			}
			else {
				if (end < range.end) {
					val newRange = Range(range.start, start)
					ranges.add(0, newRange)
					modified = true
					range.start = end
					break
				}
				else if (start < range.end) {
					range.end = start
					modified = true
					break
				}
			}
		}

		if (save) {
			if (modified) {
				val rangesFinal = ArrayList(ranges)

				filesQueue.postRunnable {
					try {
						synchronized(this@FileLoadOperation) {
							val filePartsStream = filePartsStream ?: return@postRunnable

							filePartsStream.seek(0)

							val countFinal = rangesFinal.size

							filePartsStream.writeInt(countFinal)

							for (a in 0 until countFinal) {
								val rangeFinal = rangesFinal[a]

								filePartsStream.writeLong(rangeFinal.start)
								filePartsStream.writeLong(rangeFinal.end)
							}
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				notifyStreamListeners()
			}
			else {
				if (BuildConfig.DEBUG) {
					FileLog.e(cacheFileFinal.toString() + " downloaded duplicate file part " + start + " - " + end)
				}
			}
		}
	}

	private fun notifyStreamListeners() {
		streamListeners?.forEach {
			it.newDataAvailable()
		}
	}

	val currentFile: File?
		get() {
			val countDownLatch = CountDownLatch(1)
			var result: File? = null

			Utilities.stageQueue.postRunnable {
				result = if (state == STATE_FINISHED) {
					cacheFileFinal
				}
				else {
					cacheFileTemp
				}

				countDownLatch.countDown()
			}

			try {
				countDownLatch.await()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			return result
		}

	private fun getDownloadedLengthFromOffsetInternal(ranges: ArrayList<Range>?, offset: Long, length: Long): Long {
		return if (ranges == null || state == STATE_FINISHED || ranges.isEmpty()) {
			if (state == STATE_FINISHED) {
				return length
			}

			if (downloadedBytes == 0L) {
				0
			}
			else {
				min(length.toDouble(), max((downloadedBytes - offset).toDouble(), 0.0)).toLong()
			}
		}
		else {
			val count = ranges.size
			var range: Range
			var minRange: Range? = null
			var availableLength = length

			for (a in 0 until count) {
				range = ranges[a]

				if (offset <= range.start && (minRange == null || range.start < minRange.start)) {
					minRange = range
				}

				if (range.start <= offset && range.end > offset) {
					availableLength = 0
					break
				}
			}

			if (availableLength == 0L) {
				0
			}
			else if (minRange != null) {
				min(length.toDouble(), (minRange.start - offset).toDouble()).toLong()
			}
			else {
				min(length.toDouble(), max((totalBytesCount - offset).toDouble(), 0.0)).toLong()
			}
		}
	}

	fun getDownloadedLengthFromOffset(progress: Float): Float {
		val ranges = notLoadedBytesRangesCopy

		return if (totalBytesCount == 0L || ranges == null) {
			0f
		}
		else {
			progress + getDownloadedLengthFromOffsetInternal(ranges, (totalBytesCount * progress).toInt().toLong(), totalBytesCount) / totalBytesCount.toFloat()
		}
	}

	fun getDownloadedLengthFromOffset(offset: Long, length: Long): LongArray {
		val countDownLatch = CountDownLatch(1)
		val result = LongArray(2)

		Utilities.stageQueue.postRunnable {
			result[0] = getDownloadedLengthFromOffsetInternal(notLoadedBytesRanges, offset, length)

			if (state == STATE_FINISHED) {
				result[1] = 1
			}
			countDownLatch.countDown()
		}

		runCatching {
			countDownLatch.await()
		}

		return result
	}

	fun removeStreamListener(operation: FileLoadOperationStream) {
		Utilities.stageQueue.postRunnable {
			streamListeners?.remove(operation)
		}
	}

	private fun copyNotLoadedRanges() {
		val notLoadedBytesRanges = notLoadedBytesRanges ?: return

		notLoadedBytesRangesCopy = ArrayList(notLoadedBytesRanges)
	}

	fun pause() {
		if (state != STATE_DOWNLOADING) {
			return
		}

		isPaused = true
	}

	@JvmOverloads
	fun start(stream: FileLoadOperationStream? = this.stream, streamOffset: Long = this.streamOffset, steamPriority: Boolean = streamPriority): Boolean {
		startTime = System.currentTimeMillis()

		updateParams()

		if (currentDownloadChunkSize == 0) {
			if (isStream) {
				currentDownloadChunkSize = downloadChunkSizeAnimation
				currentMaxDownloadRequests = maxDownloadRequestsAnimation
			}

			currentDownloadChunkSize = if (totalBytesCount >= bigFileSizeFrom || isStream) downloadChunkSizeBig else downloadChunkSize
			currentMaxDownloadRequests = if (totalBytesCount >= bigFileSizeFrom || isStream) maxDownloadRequestsBig else maxDownloadRequests
		}

		val alreadyStarted = state != STATE_IDLE
		val wasPaused = isPaused

		isPaused = false

		if (stream != null) {
			Utilities.stageQueue.postRunnable {
				if (streamListeners == null) {
					streamListeners = ArrayList()
				}

				if (steamPriority) {
					val offset = streamOffset / currentDownloadChunkSize.toLong() * currentDownloadChunkSize.toLong()

					if (priorityRequestInfo != null && priorityRequestInfo?.offset != offset) {
						requestInfos.remove(priorityRequestInfo!!)
						requestedBytesCount -= currentDownloadChunkSize.toLong()

						removePart(notRequestedBytesRanges, priorityRequestInfo!!.offset, priorityRequestInfo!!.offset + currentDownloadChunkSize)

						if (priorityRequestInfo?.requestToken != 0) {
							ConnectionsManager.getInstance(currentAccount).cancelRequest(priorityRequestInfo!!.requestToken, true)
							requestsCount--
						}

						if (BuildConfig.DEBUG) {
							FileLog.d("frame get cancel request at offset " + priorityRequestInfo!!.offset)
						}

						priorityRequestInfo = null
					}

					if (priorityRequestInfo == null) {
						streamPriorityStartOffset = offset
					}
				}
				else {
					streamStartOffset = streamOffset / currentDownloadChunkSize * currentDownloadChunkSize
				}

				streamListeners?.add(stream)

				if (alreadyStarted) {
					if (preloadedBytesRanges != null && getDownloadedLengthFromOffsetInternal(notLoadedBytesRanges, streamStartOffset, 1) == 0L) {
						if (preloadedBytesRanges!![streamStartOffset] != null) {
							nextPartWasPreloaded = true
						}
					}

					startDownloadRequest()

					nextPartWasPreloaded = false
				}
			}
		}
		else if (wasPaused && alreadyStarted) {
			Utilities.stageQueue.postRunnable {
				startDownloadRequest()
			}
		}

		if (alreadyStarted) {
			return wasPaused
		}

		val location = location
		val webLocation = webLocation

		if (location == null && webLocation == null) {
			onFail(true, 0)
			return false
		}

		streamStartOffset = streamOffset / currentDownloadChunkSize * currentDownloadChunkSize

		if (allowDisordererFileSave && totalBytesCount > 0 && totalBytesCount > currentDownloadChunkSize) {
			notLoadedBytesRanges = ArrayList()
			notRequestedBytesRanges = ArrayList()
		}

		var fileNameFinal: String? = null
		var fileNameTemp: String? = null
		var fileNameParts: String? = null
		var fileNamePreload: String? = null
		var fileNameIv: String? = null

		if (webLocation != null) {
			val md5 = Utilities.MD5(webFile?.url)

			if (encryptFile) {
				fileNameTemp = "$md5.temp.enc"
				fileNameFinal = "$md5.$ext.enc"

				if (key != null) {
					fileNameIv = md5 + "_64.iv.enc"
				}
			}
			else {
				fileNameTemp = "$md5.temp"
				fileNameFinal = "$md5.$ext"

				if (key != null) {
					fileNameIv = md5 + "_64.iv"
				}
			}
		}
		else if (location != null) {
			if (location.volume_id != 0L && location.local_id != 0) {
				if (datacenterId == Int.MIN_VALUE || location.volume_id == Int.MIN_VALUE.toLong() || datacenterId == 0) {
					onFail(true, 0)
					return false
				}

				if (encryptFile) {
					fileNameTemp = location.volume_id.toString() + "_" + location.local_id + ".temp.enc"
					fileNameFinal = location.volume_id.toString() + "_" + location.local_id + "." + ext + ".enc"

					if (key != null) {
						fileNameIv = location.volume_id.toString() + "_" + location.local_id + "_64.iv.enc"
					}
				}
				else {
					fileNameTemp = location.volume_id.toString() + "_" + location.local_id + ".temp"
					fileNameFinal = location.volume_id.toString() + "_" + location.local_id + "." + ext
					if (key != null) {
						fileNameIv = location.volume_id.toString() + "_" + location.local_id + "_64.iv"
					}
					if (notLoadedBytesRanges != null) {
						fileNameParts = location.volume_id.toString() + "_" + location.local_id + "_64.pt"
					}
					fileNamePreload = location.volume_id.toString() + "_" + location.local_id + "_64.preload"
				}
			}
			else {
				if (datacenterId == 0 || location.id == 0L) {
					onFail(true, 0)
					return false
				}

				if (encryptFile) {
					fileNameTemp = datacenterId.toString() + "_" + location.id + ".temp.enc"
					fileNameFinal = datacenterId.toString() + "_" + location.id + ext + ".enc"

					if (key != null) {
						fileNameIv = datacenterId.toString() + "_" + location.id + "_64.iv.enc"
					}
				}
				else {
					fileNameTemp = datacenterId.toString() + "_" + location.id + ".temp"
					fileNameFinal = datacenterId.toString() + "_" + location.id + ext

					if (key != null) {
						fileNameIv = datacenterId.toString() + "_" + location.id + "_64.iv"
					}

					if (notLoadedBytesRanges != null) {
						fileNameParts = datacenterId.toString() + "_" + location.id + "_64.pt"
					}

					fileNamePreload = datacenterId.toString() + "_" + location.id + "_64.preload"
				}
			}
		}

		requestInfos = ArrayList(currentMaxDownloadRequests)
		delayedRequestInfos = ArrayList(currentMaxDownloadRequests - 1)
		state = STATE_DOWNLOADING

		cacheFileFinal = if (parentObject is TL_theme) {
			val theme = parentObject as TL_theme
			File(filesDirFixed, "remote" + theme.id + ".attheme")
		}
		else {
			if (!encryptFile) {
				File(storePath, storeFileName)
			}
			else {
				File(storePath, fileNameFinal)
			}
		}

		var finalFileExist = cacheFileFinal?.exists() == true

		if (finalFileExist && (parentObject is TL_theme || totalBytesCount != 0L && totalBytesCount != cacheFileFinal?.length())) {
			if (delegate?.hasAnotherRefOnFile(cacheFileFinal.toString()) != true) {
				cacheFileFinal?.delete()
			}

			finalFileExist = false
		}

		if (!finalFileExist) {
			cacheFileTemp = File(tempPath, fileNameTemp)

			if (ungzip) {
				cacheFileGzipTemp = File(tempPath, "$fileNameTemp.gz")
			}

			var newKeyGenerated = false

			if (encryptFile) {
				val keyFile = File(FileLoader.internalCacheDir, "$fileNameFinal.key")

				try {
					RandomAccessFile(keyFile, "rws").use { file ->
						val len = keyFile.length()

						encryptKey = ByteArray(32)
						encryptIv = ByteArray(16)

						if (len > 0 && len % 48 == 0L) {
							file.read(encryptKey, 0, 32)
							file.read(encryptIv, 0, 16)
						}
						else {
							Utilities.random.nextBytes(encryptKey)
							Utilities.random.nextBytes(encryptIv)

							file.write(encryptKey)
							file.write(encryptIv)

							newKeyGenerated = true
						}

						try {
							file.channel.close()
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			val preloaded = booleanArrayOf(false)

			if (supportsPreloading && fileNamePreload != null) {
				cacheFilePreload = File(tempPath, fileNamePreload)

				try {
					preloadStream = RandomAccessFile(cacheFilePreload, "rws")

					val len = preloadStream?.length() ?: 0L
					var readOffset = 0L

					preloadStreamFileOffset = 1

					if (len - readOffset > 1) {
						preloaded[0] = preloadStream?.readByte()?.toInt() != 0

						readOffset += 1

						while (readOffset < len) {
							if (len - readOffset < 8) {
								break
							}

							val offset = preloadStream?.readLong() ?: 0L

							readOffset += 8

							if (len - readOffset < 8 || offset < 0 || offset > totalBytesCount) {
								break
							}

							val size = preloadStream?.readLong() ?: 0L

							readOffset += 8

							if (len - readOffset < size || size > currentDownloadChunkSize) {
								break
							}

							val range = PreloadRange(readOffset, size)

							readOffset += size

							preloadStream?.seek(readOffset)

							if (len - readOffset < 24) {
								break
							}

							foundMoovSize = preloadStream?.readLong() ?: 0L

							if (foundMoovSize != 0L) {
								moovFound = if (nextPreloadDownloadOffset > totalBytesCount / 2) 2 else 1
								preloadNotRequestedBytesCount = foundMoovSize
							}

							nextPreloadDownloadOffset = preloadStream?.readLong() ?: 0L
							nextAtomOffset = preloadStream?.readLong() ?: 0L

							readOffset += 24

							if (preloadedBytesRanges == null) {
								preloadedBytesRanges = HashMap()
							}

							if (requestedPreloadedBytesRanges == null) {
								requestedPreloadedBytesRanges = HashMap()
							}

							preloadedBytesRanges!![offset] = range
							requestedPreloadedBytesRanges!![offset] = 1
							totalPreloadedBytes += size.toInt()
							preloadStreamFileOffset += (36 + size).toInt()
						}
					}

					preloadStream?.seek(preloadStreamFileOffset.toLong())
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				if (!isPreloadVideoOperation && preloadedBytesRanges == null) {
					cacheFilePreload = null

					try {
						if (preloadStream != null) {
							try {
								preloadStream?.channel?.close()
							}
							catch (e: Exception) {
								FileLog.e(e)
							}

							preloadStream?.close()
							preloadStream = null
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}

			if (fileNameParts != null) {
				cacheFileParts = File(tempPath, fileNameParts)

				try {
					filePartsStream = RandomAccessFile(cacheFileParts, "rws")

					var len = filePartsStream?.length() ?: 0L

					if (len % 8 == 4L) {
						len -= 4
						val count = filePartsStream?.readInt() ?: 0

						if (count <= len / 2) {
							for (a in 0 until count) {
								val start = filePartsStream?.readLong() ?: 0L
								val end = filePartsStream?.readLong() ?: 0L

								notLoadedBytesRanges?.add(Range(start, end))
								notRequestedBytesRanges?.add(Range(start, end))
							}
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			val cacheFileTemp = cacheFileTemp
			val notLoadedBytesRanges = notLoadedBytesRanges

			if (cacheFileTemp?.exists() == true) {
				if (newKeyGenerated) {
					cacheFileTemp.delete()
				}
				else {
					val totalDownloadedLen = cacheFileTemp.length()

					if (fileNameIv != null && totalDownloadedLen % currentDownloadChunkSize != 0L) {
						requestedBytesCount = 0
					}
					else {
						downloadedBytes = cacheFileTemp.length() / currentDownloadChunkSize.toLong() * currentDownloadChunkSize
						requestedBytesCount = downloadedBytes
					}

					if (notLoadedBytesRanges != null && notLoadedBytesRanges.isEmpty()) {
						notLoadedBytesRanges.add(Range(downloadedBytes, totalBytesCount))
						notRequestedBytesRanges?.add(Range(downloadedBytes, totalBytesCount))
					}
				}
			}
			else if (notLoadedBytesRanges != null && notLoadedBytesRanges.isEmpty()) {
				notLoadedBytesRanges.add(Range(0, totalBytesCount))
				notRequestedBytesRanges?.add(Range(0, totalBytesCount))
			}

			if (notLoadedBytesRanges != null) {
				downloadedBytes = totalBytesCount

				val size = notLoadedBytesRanges.size
				var range: Range

				for (a in 0 until size) {
					range = notLoadedBytesRanges[a]
					downloadedBytes -= range.end - range.start
				}

				requestedBytesCount = downloadedBytes
			}

			if (BuildConfig.DEBUG) {
				if (isPreloadVideoOperation) {
					FileLog.d("start preloading file to temp = $cacheFileTemp")
				}
				else {
					FileLog.d("start loading file to temp = $cacheFileTemp final = $cacheFileFinal")
				}
			}

			if (fileNameIv != null) {
				cacheIvTemp = File(tempPath, fileNameIv)

				try {
					fiv = RandomAccessFile(cacheIvTemp, "rws")

					if (downloadedBytes != 0L && !newKeyGenerated) {
						val len = cacheIvTemp?.length() ?: 0L

						if (len > 0 && len % 64 == 0L) {
							fiv?.read(iv, 0, 64)
						}
						else {
							downloadedBytes = 0
							requestedBytesCount = 0
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
					downloadedBytes = 0
					requestedBytesCount = 0
				}
			}

			if (!isPreloadVideoOperation && downloadedBytes != 0L && totalBytesCount > 0) {
				copyNotLoadedRanges()
			}

			updateProgress()

			try {
				fileOutputStream = RandomAccessFile(cacheFileTemp, "rws")

				if (downloadedBytes != 0L) {
					fileOutputStream?.seek(downloadedBytes)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			if (fileOutputStream == null) {
				onFail(true, 0)
				return false
			}

			started = true

			Utilities.stageQueue.postRunnable {
				if (totalBytesCount != 0L && (isPreloadVideoOperation && preloaded[0] || downloadedBytes == totalBytesCount)) {
					try {
						onFinishLoadingFile(false)
					}
					catch (e: Exception) {
						onFail(true, 0)
					}
				}
				else {
					startDownloadRequest()
				}
			}
		}
		else {
			started = true

			try {
				onFinishLoadingFile(false)

				pathSaveData?.let {
					delegate?.saveFilePath(it, null)
				}
			}
			catch (e: Exception) {
				onFail(true, 0)
			}
		}

		return true
	}

	fun updateProgress() {
		if (downloadedBytes != totalBytesCount && totalBytesCount > 0) {
			delegate?.didChangedLoadProgress(this@FileLoadOperation, downloadedBytes, totalBytesCount)
		}
	}

	fun setIsPreloadVideoOperation(value: Boolean) {
		if ((isPreloadVideoOperation == value) || (value && totalBytesCount <= PRELOAD_MAX_BYTES)) {
			return
		}

		if (!value && isPreloadVideoOperation) {
			when (state) {
				STATE_FINISHED -> {
					isPreloadVideoOperation = value
					state = STATE_IDLE
					isPreloadFinished = false
					start()
				}

				STATE_DOWNLOADING -> {
					Utilities.stageQueue.postRunnable {
						requestedBytesCount = 0
						clearOperaion(null, true)
						isPreloadVideoOperation = value
						startDownloadRequest()
					}
				}

				else -> {
					isPreloadVideoOperation = value
				}
			}
		}
		else {
			isPreloadVideoOperation = value
		}
	}

	fun isPreloadVideoOperation(): Boolean {
		return isPreloadVideoOperation
	}

	@JvmOverloads
	fun cancel(deleteFiles: Boolean = false) {
		Utilities.stageQueue.postRunnable {
			if (state != STATE_FINISHED && state != STATE_FAILED) {
				for (requestInfo in requestInfos) {
					if (requestInfo.requestToken != 0) {
						ConnectionsManager.getInstance(currentAccount).cancelRequest(requestInfo.requestToken, true)
					}
				}

				onFail(false, 1)
			}

			if (deleteFiles) {
				cacheFileFinal?.let {
					try {
						if (!it.delete()) {
							it.deleteOnExit()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				cacheFileTemp?.let {
					try {
						if (!it.delete()) {
							it.deleteOnExit()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				cacheFileParts?.let {
					try {
						if (!it.delete()) {
							it.deleteOnExit()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				cacheIvTemp?.let {
					try {
						if (!it.delete()) {
							it.deleteOnExit()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				cacheFilePreload?.let {
					try {
						if (!it.delete()) {
							it.deleteOnExit()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
			}
		}
	}

	private fun cleanup() {
		try {
			try {
				fileOutputStream?.channel?.close()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			fileOutputStream?.close()
			fileOutputStream = null
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		try {
			try {
				preloadStream?.channel?.close()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			preloadStream?.close()
			preloadStream = null
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		try {
			try {
				fileReadStream?.channel?.close()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			fileReadStream?.close()
			fileReadStream = null
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
		try {
			synchronized(this@FileLoadOperation) {
				try {
					filePartsStream?.channel?.close()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				filePartsStream?.close()
				filePartsStream = null
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		try {
			fiv?.close()
			fiv = null
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		for (a in delayedRequestInfos.indices) {
			val requestInfo = delayedRequestInfos[a]

			if (requestInfo.response != null) {
				requestInfo.response?.disableFree = false
				requestInfo.response?.freeResources()
			}
			else if (requestInfo.responseWeb != null) {
				requestInfo.responseWeb?.disableFree = false
				requestInfo.responseWeb?.freeResources()
			}
			else if (requestInfo.responseCdn != null) {
				requestInfo.responseCdn?.disableFree = false
				requestInfo.responseCdn?.freeResources()
			}
		}

		delayedRequestInfos.clear()
	}

	private fun onFinishLoadingFile(increment: Boolean) {
		if (state != STATE_DOWNLOADING) {
			return
		}

		state = STATE_FINISHED

		notifyStreamListeners()
		cleanup()

		if (isPreloadVideoOperation) {
			isPreloadFinished = true

			if (BuildConfig.DEBUG) {
				FileLog.d("finished preloading file to $cacheFileTemp loaded $totalPreloadedBytes of $totalBytesCount")
			}
		}
		else {
			cacheIvTemp?.delete()
			cacheIvTemp = null

			cacheFileParts?.delete()
			cacheFileParts = null

			cacheFilePreload?.delete()
			cacheFilePreload = null

			if (cacheFileTemp != null) {
				if (ungzip) {
					try {
						GZIPInputStream(FileInputStream(cacheFileTemp)).use {
							FileLoader.copyFile(it, cacheFileGzipTemp, 1024 * 1024 * 2)
						}

						cacheFileTemp?.delete()
						cacheFileTemp = cacheFileGzipTemp

						ungzip = false
					}
					catch (zipException: ZipException) {
						ungzip = false
					}
					catch (e: Throwable) {
						FileLog.e(e)

						if (BuildConfig.DEBUG) {
							FileLog.e("unable to ungzip temp = $cacheFileTemp to final = $cacheFileFinal")
						}
					}
				}

				if (!ungzip) {
					var renameResult: Boolean

					if (parentObject is TL_theme) {
						try {
							renameResult = AndroidUtilities.copyFile(cacheFileTemp, cacheFileFinal)
						}
						catch (e: Exception) {
							renameResult = false
							FileLog.e(e)
						}
					}
					else {
						try {
							if (pathSaveData != null) {
								synchronized(lockObject) {
									cacheFileFinal = File(storePath, storeFileName)

									var count = 1

									while (cacheFileFinal?.exists() == true) {
										val lastDotIndex = storeFileName?.lastIndexOf('.') ?: -1

										val newFileName = if (lastDotIndex > 0) {
											storeFileName?.substring(0, lastDotIndex) + " (" + count + ")" + storeFileName?.substring(lastDotIndex)
										}
										else {
											"$storeFileName ($count)"
										}

										cacheFileFinal = File(storePath, newFileName)

										count++
									}
								}
							}

							renameResult = cacheFileTemp?.renameTo(cacheFileFinal) ?: false
						}
						catch (e: Exception) {
							renameResult = false
							FileLog.e(e)
						}
					}
					if (!renameResult) {
						if (BuildConfig.DEBUG) {
							FileLog.e("unable to rename temp = $cacheFileTemp to final = $cacheFileFinal retry = $renameRetryCount")
						}

						renameRetryCount++

						if (renameRetryCount < 3) {
							state = STATE_DOWNLOADING

							Utilities.stageQueue.postRunnable({
								try {
									onFinishLoadingFile(increment)
								}
								catch (e: Exception) {
									onFail(false, 0)
								}
							}, 200)

							return
						}

						cacheFileFinal = cacheFileTemp
					}
					else {
						if (pathSaveData != null && cacheFileFinal?.exists() == true) {
							delegate?.saveFilePath(pathSaveData!!, cacheFileFinal)
						}
					}
				}
				else {
					onFail(false, 0)
					return
				}
			}

			if (BuildConfig.DEBUG) {
				FileLog.d("finished downloading file to " + cacheFileFinal + " time = " + (System.currentTimeMillis() - startTime))
			}

			if (increment) {
				when (currentType) {
					ConnectionsManager.FileTypeAudio -> {
						StatsController.getInstance(currentAccount).incrementReceivedItemsCount(currentNetworkType, StatsController.TYPE_AUDIOS, 1)
					}

					ConnectionsManager.FileTypeVideo -> {
						StatsController.getInstance(currentAccount).incrementReceivedItemsCount(currentNetworkType, StatsController.TYPE_VIDEOS, 1)
					}

					ConnectionsManager.FileTypePhoto -> {
						StatsController.getInstance(currentAccount).incrementReceivedItemsCount(currentNetworkType, StatsController.TYPE_PHOTOS, 1)
					}

					ConnectionsManager.FileTypeFile -> {
						StatsController.getInstance(currentAccount).incrementReceivedItemsCount(currentNetworkType, StatsController.TYPE_FILES, 1)
					}
				}
			}
		}

		delegate?.didFinishLoadingFile(this@FileLoadOperation, cacheFileFinal)
	}

	private fun delayRequestInfo(requestInfo: RequestInfo) {
		delayedRequestInfos.add(requestInfo)

		if (requestInfo.response != null) {
			requestInfo.response?.disableFree = true
		}
		else if (requestInfo.responseWeb != null) {
			requestInfo.responseWeb?.disableFree = true
		}
		else if (requestInfo.responseCdn != null) {
			requestInfo.responseCdn?.disableFree = true
		}
	}

	private fun findNextPreloadDownloadOffset(atomOffset: Long, partOffset: Long, partBuffer: NativeByteBuffer): Long {
		@Suppress("NAME_SHADOWING") var atomOffset = atomOffset
		val partSize = partBuffer.limit()

		while (true) {
			if (atomOffset < partOffset - (if (preloadTempBuffer != null) 16 else 0) || atomOffset >= partOffset + partSize) {
				return 0
			}

			if (atomOffset >= partOffset + partSize - 16) {
				val count = partOffset + partSize - atomOffset

				if (count > Int.MAX_VALUE) {
					throw RuntimeException("!!!")
				}

				preloadTempBufferCount = count.toInt()

				val position = (partBuffer.limit() - preloadTempBufferCount).toLong()

				partBuffer.position(position.toInt())
				partBuffer.readBytes(preloadTempBuffer, 0, preloadTempBufferCount, false)

				return partOffset + partSize
			}

			if (preloadTempBufferCount != 0) {
				partBuffer.position(0)
				partBuffer.readBytes(preloadTempBuffer, preloadTempBufferCount, 16 - preloadTempBufferCount, false)

				preloadTempBufferCount = 0
			}
			else {
				val count = atomOffset - partOffset

				if (count > Int.MAX_VALUE) {
					throw RuntimeException("!!!")
				}

				partBuffer.position(count.toInt())
				partBuffer.readBytes(preloadTempBuffer, 0, 16, false)
			}

			var atomSize = (preloadTempBuffer[0].toInt() and 0xFF shl 24) + (preloadTempBuffer[1].toInt() and 0xFF shl 16) + (preloadTempBuffer[2].toInt() and 0xFF shl 8) + (preloadTempBuffer[3].toInt() and 0xFF)

			if (atomSize == 0) {
				return 0
			}
			else if (atomSize == 1) {
				atomSize = (preloadTempBuffer[12].toInt() and 0xFF shl 24) + (preloadTempBuffer[13].toInt() and 0xFF shl 16) + (preloadTempBuffer[14].toInt() and 0xFF shl 8) + (preloadTempBuffer[15].toInt() and 0xFF)
			}

			if (preloadTempBuffer[4] == 'm'.code.toByte() && preloadTempBuffer[5] == 'o'.code.toByte() && preloadTempBuffer[6] == 'o'.code.toByte() && preloadTempBuffer[7] == 'v'.code.toByte()) {
				return (-atomSize).toLong()
			}

			if (atomSize + atomOffset >= partOffset + partSize) {
				return atomSize + atomOffset
			}

			atomOffset += atomSize.toLong()
		}
	}

	private fun requestFileOffsets(offset: Long) {
		if (requestingCdnOffsets) {
			return
		}

		requestingCdnOffsets = true

		val req = TL_upload_getCdnFileHashes()
		req.file_token = cdnToken
		req.offset = offset

		ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response, _ ->
			if (response is Vector) {
				requestingCdnOffsets = false

				if (response.objects.isNotEmpty()) {
					if (cdnHashes == null) {
						cdnHashes = HashMap()
					}

					response.objects.forEach {
						val hash = it as TL_fileHash
						cdnHashes?.put(hash.offset, hash)
					}
				}

				for (a in delayedRequestInfos.indices) {
					val delayedRequestInfo = delayedRequestInfos[a]

					if (notLoadedBytesRanges != null || downloadedBytes == delayedRequestInfo.offset) {
						delayedRequestInfos.removeAt(a)

						if (!processRequestResult(delayedRequestInfo, null)) {
							if (delayedRequestInfo.response != null) {
								delayedRequestInfo.response?.disableFree = false
								delayedRequestInfo.response?.freeResources()
							}
							else if (delayedRequestInfo.responseWeb != null) {
								delayedRequestInfo.responseWeb?.disableFree = false
								delayedRequestInfo.responseWeb?.freeResources()
							}
							else if (delayedRequestInfo.responseCdn != null) {
								delayedRequestInfo.responseCdn?.disableFree = false
								delayedRequestInfo.responseCdn?.freeResources()
							}
						}

						break
					}
				}
			}
			else {
				onFail(false, 0)
			}
		}, null, null, 0, datacenterId, ConnectionsManager.ConnectionTypeGeneric, true)
	}

	fun processRequestResult(requestInfo: RequestInfo, error: TL_error?): Boolean {
		if (state != STATE_DOWNLOADING) {
			if (BuildConfig.DEBUG && state == STATE_FINISHED) {
				FileLog.e(Exception("trying to write to finished file " + fileName + " offset " + requestInfo.offset + " " + totalBytesCount))
			}

			return false
		}

		requestInfos.remove(requestInfo)

		if (error == null) {
			try {
				if (notLoadedBytesRanges == null && downloadedBytes != requestInfo.offset) {
					delayRequestInfo(requestInfo)
					return false
				}

				val bytes = if (requestInfo.response != null) {
					requestInfo.response?.bytes
				}
				else if (requestInfo.responseWeb != null) {
					requestInfo.responseWeb?.bytes
				}
				else if (requestInfo.responseCdn != null) {
					requestInfo.responseCdn?.bytes
				}
				else {
					null
				}

				if (bytes == null || bytes.limit() == 0) {
					onFinishLoadingFile(true)
					return false
				}

				val currentBytesSize = bytes.limit()

				if (isCdn) {
					val cdnCheckPart = requestInfo.offset / cdnChunkCheckSize
					val fileOffset = cdnCheckPart * cdnChunkCheckSize
					val hash = cdnHashes?.get(fileOffset)

					if (hash == null) {
						delayRequestInfo(requestInfo)
						requestFileOffsets(fileOffset)
						return true
					}
				}

				if (requestInfo.responseCdn != null) {
					val offset = requestInfo.offset / 16

					cdnIv!![15] = (offset and 0xffL).toByte()
					cdnIv!![14] = (offset shr 8 and 0xffL).toByte()
					cdnIv!![13] = (offset shr 16 and 0xffL).toByte()
					cdnIv!![12] = (offset shr 24 and 0xffL).toByte()

					Utilities.aesCtrDecryption(bytes.buffer, cdnKey, cdnIv, 0, bytes.limit())
				}

				val finishedDownloading: Boolean

				if (isPreloadVideoOperation) {
					preloadStream?.writeLong(requestInfo.offset)
					preloadStream?.writeLong(currentBytesSize.toLong())

					preloadStreamFileOffset += 16

					val channel = preloadStream!!.channel
					channel.write(bytes.buffer)

					if (BuildConfig.DEBUG) {
						FileLog.d("save preload file part " + cacheFilePreload + " offset " + requestInfo.offset + " size " + currentBytesSize)
					}

					if (preloadedBytesRanges == null) {
						preloadedBytesRanges = HashMap()
					}

					preloadedBytesRanges?.put(requestInfo.offset, PreloadRange(preloadStreamFileOffset.toLong(), currentBytesSize.toLong()))
					totalPreloadedBytes += currentBytesSize
					preloadStreamFileOffset += currentBytesSize

					if (moovFound == 0) {
						var offset = findNextPreloadDownloadOffset(nextAtomOffset, requestInfo.offset, bytes)

						if (offset < 0) {
							offset *= -1

							nextPreloadDownloadOffset += currentDownloadChunkSize.toLong()

							if (nextPreloadDownloadOffset < totalBytesCount / 2) {
								foundMoovSize = PRELOAD_MAX_BYTES / 2 + offset
								preloadNotRequestedBytesCount = foundMoovSize
								moovFound = 1
							}
							else {
								foundMoovSize = PRELOAD_MAX_BYTES.toLong()
								preloadNotRequestedBytesCount = foundMoovSize
								moovFound = 2
							}

							nextPreloadDownloadOffset = -1
						}
						else {
							nextPreloadDownloadOffset += currentDownloadChunkSize.toLong()
						}

						nextAtomOffset = offset
					}

					preloadStream?.writeLong(foundMoovSize)
					preloadStream?.writeLong(nextPreloadDownloadOffset)
					preloadStream?.writeLong(nextAtomOffset)

					preloadStreamFileOffset += 24

					finishedDownloading = nextPreloadDownloadOffset == 0L || moovFound != 0 && foundMoovSize < 0 || totalPreloadedBytes > PRELOAD_MAX_BYTES || nextPreloadDownloadOffset >= totalBytesCount

					if (finishedDownloading) {
						preloadStream?.seek(0)
						preloadStream?.write(1.toByte().toInt())
					}
					else if (moovFound != 0) {
						foundMoovSize -= currentDownloadChunkSize.toLong()
					}
				}
				else {
					downloadedBytes += currentBytesSize.toLong()

					finishedDownloading = if (totalBytesCount > 0) {
						downloadedBytes >= totalBytesCount
					}
					else {
						currentBytesSize != currentDownloadChunkSize || (totalBytesCount == downloadedBytes || downloadedBytes % currentDownloadChunkSize != 0L) && (totalBytesCount <= 0 || totalBytesCount <= downloadedBytes)
					}

					if (key != null) {
						Utilities.aesIgeEncryption(bytes.buffer, key, iv, false, true, 0, bytes.limit())

						if (finishedDownloading && bytesCountPadding != 0L) {
							val limit = bytes.limit() - bytesCountPadding

							if (BuildConfig.DEBUG && limit > Int.MAX_VALUE) {
								throw RuntimeException("Out of limit$limit")
							}

							bytes.limit(limit.toInt())
						}
					}

					if (encryptFile) {
						val offset = requestInfo.offset / 16

						encryptIv!![15] = (offset and 0xffL).toByte()
						encryptIv!![14] = (offset shr 8 and 0xffL).toByte()
						encryptIv!![13] = (offset shr 16 and 0xffL).toByte()
						encryptIv!![12] = (offset shr 24 and 0xffL).toByte()

						Utilities.aesCtrDecryption(bytes.buffer, encryptKey, encryptIv, 0, bytes.limit())
					}

					if (notLoadedBytesRanges != null) {
						fileOutputStream?.seek(requestInfo.offset)

						if (BuildConfig.DEBUG) {
							FileLog.d("save file part " + fileName + " offset=" + requestInfo.offset + " chunk_size=" + currentDownloadChunkSize + " isCdn=" + isCdn)
						}
					}

					val channel = fileOutputStream?.channel
					channel?.write(bytes.buffer)

					addPart(notLoadedBytesRanges, requestInfo.offset, requestInfo.offset + currentBytesSize, true)

					if (isCdn) {
						val cdnCheckPart = requestInfo.offset / cdnChunkCheckSize
						val size = notCheckedCdnRanges?.size ?: 0
						var range: Range
						var checked = true

						for (a in 0 until size) {
							range = notCheckedCdnRanges!![a]

							if (range.start <= cdnCheckPart && cdnCheckPart <= range.end) {
								checked = false
								break
							}
						}

						if (!checked) {
							val fileOffset = cdnCheckPart * cdnChunkCheckSize
							val availableSize = getDownloadedLengthFromOffsetInternal(notLoadedBytesRanges, fileOffset, cdnChunkCheckSize.toLong())

							if (availableSize != 0L && (availableSize == cdnChunkCheckSize.toLong() || totalBytesCount > 0 && availableSize == totalBytesCount - fileOffset || totalBytesCount <= 0 && finishedDownloading)) {
								val hash = cdnHashes!![fileOffset]

								if (fileReadStream == null) {
									cdnCheckBytes = ByteArray(cdnChunkCheckSize)
									fileReadStream = RandomAccessFile(cacheFileTemp, "r")
								}

								fileReadStream?.seek(fileOffset)

								if (BuildConfig.DEBUG && availableSize > Int.MAX_VALUE) {
									throw RuntimeException("!!!")
								}

								fileReadStream?.readFully(cdnCheckBytes, 0, availableSize.toInt())

								if (encryptFile) {
									val offset = fileOffset / 16

									encryptIv!![15] = (offset and 0xffL).toByte()
									encryptIv!![14] = (offset shr 8 and 0xffL).toByte()
									encryptIv!![13] = (offset shr 16 and 0xffL).toByte()
									encryptIv!![12] = (offset shr 24 and 0xffL).toByte()

									Utilities.aesCtrDecryptionByteArray(cdnCheckBytes, encryptKey, encryptIv, 0, availableSize, 0)
								}

								val sha256 = Utilities.computeSHA256(cdnCheckBytes, 0, availableSize)

								if (!sha256.contentEquals(hash!!.hash)) {
									if (BuildConfig.DEBUG) {
										if (location != null) {
											FileLog.e("invalid cdn hash " + location + " id = " + location!!.id + " local_id = " + location!!.local_id + " access_hash = " + location!!.access_hash + " volume_id = " + location!!.volume_id + " secret = " + location!!.secret)
										}
										else if (webLocation != null) {
											FileLog.e("invalid cdn hash  $webLocation id = $fileName")
										}
									}

									onFail(false, 0)

									cacheFileTemp?.delete()

									return false
								}

								cdnHashes?.remove(fileOffset)

								addPart(notCheckedCdnRanges, cdnCheckPart, cdnCheckPart + 1, false)
							}
						}
					}

					fiv?.seek(0)
					fiv?.write(iv)

					if (totalBytesCount > 0 && state == STATE_DOWNLOADING) {
						copyNotLoadedRanges()

						delegate?.didChangedLoadProgress(this@FileLoadOperation, downloadedBytes, totalBytesCount)
					}
				}

				for (a in delayedRequestInfos.indices) {
					val delayedRequestInfo = delayedRequestInfos[a]

					if (notLoadedBytesRanges != null || downloadedBytes == delayedRequestInfo.offset) {
						delayedRequestInfos.removeAt(a)

						if (!processRequestResult(delayedRequestInfo, null)) {
							if (delayedRequestInfo.response != null) {
								delayedRequestInfo.response?.disableFree = false
								delayedRequestInfo.response?.freeResources()
							}
							else if (delayedRequestInfo.responseWeb != null) {
								delayedRequestInfo.responseWeb?.disableFree = false
								delayedRequestInfo.responseWeb?.freeResources()
							}
							else if (delayedRequestInfo.responseCdn != null) {
								delayedRequestInfo.responseCdn?.disableFree = false
								delayedRequestInfo.responseCdn?.freeResources()
							}
						}

						break
					}
				}

				if (finishedDownloading) {
					onFinishLoadingFile(true)
				}
				else if (state != STATE_CANCELLED) {
					startDownloadRequest()
				}
			}
			catch (e: Exception) {
				onFail(false, 0)
				FileLog.e(e)
			}
		}
		else {
			if (error.text.contains("FILE_MIGRATE_")) {
				val errorMsg = error.text.replace("FILE_MIGRATE_", "")

				val scanner = Scanner(errorMsg)
				scanner.useDelimiter("")

				val `val` = try {
					scanner.nextInt()
				}
				catch (e: Exception) {
					null
				}

				if (`val` == null) {
					onFail(false, 0)
				}
				else {
					datacenterId = `val`
					downloadedBytes = 0
					requestedBytesCount = 0

					startDownloadRequest()
				}
			}
			else if (error.text.contains("OFFSET_INVALID")) {
				if (downloadedBytes % currentDownloadChunkSize == 0L) {
					try {
						onFinishLoadingFile(true)
					}
					catch (e: Exception) {
						FileLog.e(e)
						onFail(false, 0)
					}
				}
				else {
					onFail(false, 0)
				}
			}
			else if (error.text.contains("RETRY_LIMIT")) {
				onFail(false, 2)
			}
			else {
				if (BuildConfig.DEBUG) {
					if (location != null) {
						FileLog.e(error.text + " " + location + " id = " + location!!.id + " local_id = " + location!!.local_id + " access_hash = " + location!!.access_hash + " volume_id = " + location!!.volume_id + " secret = " + location!!.secret)
					}
					else if (webLocation != null) {
						FileLog.e(error.text + " " + webLocation + " id = " + fileName)
					}
				}

				onFail(false, 0)
			}
		}

		return false
	}

	fun onFail(thread: Boolean, reason: Int) {
		cleanup()

		state = if (reason == 1) STATE_CANCELLED else STATE_FAILED

		if (delegate != null) {
			if (thread) {
				Utilities.stageQueue.postRunnable {
					delegate?.didFailedLoadingFile(this@FileLoadOperation, reason)
				}
			}
			else {
				delegate?.didFailedLoadingFile(this@FileLoadOperation, reason)
			}
		}
	}

	private fun clearOperaion(currentInfo: RequestInfo?, preloadChanged: Boolean) {
		var minOffset = Long.MAX_VALUE

		for (a in requestInfos.indices) {
			val info = requestInfos[a]

			minOffset = min(info.offset.toDouble(), minOffset.toDouble()).toLong()

			if (isPreloadVideoOperation) {
				requestedPreloadedBytesRanges!!.remove(info.offset)
			}
			else {
				removePart(notRequestedBytesRanges, info.offset, info.offset + currentDownloadChunkSize)
			}

			if (currentInfo === info) {
				continue
			}

			if (info.requestToken != 0) {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(info.requestToken, true)
			}
		}

		requestInfos.clear()

		for (a in delayedRequestInfos.indices) {
			val info = delayedRequestInfos[a]

			if (isPreloadVideoOperation) {
				requestedPreloadedBytesRanges?.remove(info.offset)
			}
			else {
				removePart(notRequestedBytesRanges, info.offset, info.offset + currentDownloadChunkSize)
			}

			if (info.response != null) {
				info.response?.disableFree = false
				info.response?.freeResources()
			}
			else if (info.responseWeb != null) {
				info.responseWeb?.disableFree = false
				info.responseWeb?.freeResources()
			}
			else if (info.responseCdn != null) {
				info.responseCdn?.disableFree = false
				info.responseCdn?.freeResources()
			}

			minOffset = min(info.offset.toDouble(), minOffset.toDouble()).toLong()
		}

		delayedRequestInfos.clear()

		requestsCount = 0

		if (!preloadChanged && isPreloadVideoOperation) {
			requestedBytesCount = totalPreloadedBytes.toLong()
		}
		else if (notLoadedBytesRanges == null) {
			downloadedBytes = minOffset
			requestedBytesCount = downloadedBytes
		}
	}

	private fun requestReference(requestInfo: RequestInfo) {
		if (requestingReference) {
			return
		}

		clearOperaion(requestInfo, false)

		requestingReference = true

		if (parentObject is MessageObject) {
			val messageObject = parentObject as MessageObject

			if (messageObject.id < 0 && messageObject.messageOwner?.media?.webpage != null) {
				parentObject = messageObject.messageOwner?.media?.webpage
			}
		}

		FileRefController.getInstance(currentAccount).requestReference(parentObject, location, this, requestInfo)
	}

	fun startDownloadRequest() {
		if (isPaused || reuploadingCdn || state != STATE_DOWNLOADING || streamPriorityStartOffset == 0L && (!nextPartWasPreloaded && requestInfos.size + delayedRequestInfos.size >= currentMaxDownloadRequests || isPreloadVideoOperation && (requestedBytesCount > PRELOAD_MAX_BYTES || moovFound != 0 && requestInfos.size > 0))) {
			return
		}

		var count = 1

		if (streamPriorityStartOffset == 0L && !nextPartWasPreloaded && (!isPreloadVideoOperation || moovFound != 0) && totalBytesCount > 0) {
			count = max(0.0, (currentMaxDownloadRequests - requestInfos.size).toDouble()).toInt()
		}

		for (a in 0 until count) {
			var downloadOffset: Long

			if (isPreloadVideoOperation) {
				if (moovFound != 0 && preloadNotRequestedBytesCount <= 0) {
					return
				}

				if (nextPreloadDownloadOffset == -1L) {
					downloadOffset = 0

					var found = false
					var tries = PRELOAD_MAX_BYTES / currentDownloadChunkSize + 2

					while (tries != 0) {
						if (!requestedPreloadedBytesRanges!!.containsKey(downloadOffset)) {
							found = true
							break
						}

						downloadOffset += currentDownloadChunkSize.toLong()

						if (downloadOffset > totalBytesCount) {
							break
						}

						if (moovFound == 2 && downloadOffset == (currentDownloadChunkSize * 8).toLong()) {
							downloadOffset = (totalBytesCount - PRELOAD_MAX_BYTES / 2) / currentDownloadChunkSize * currentDownloadChunkSize
						}

						tries--
					}

					if (!found && requestInfos.isEmpty()) {
						onFinishLoadingFile(false)
					}
				}
				else {
					downloadOffset = nextPreloadDownloadOffset
				}

				if (requestedPreloadedBytesRanges == null) {
					requestedPreloadedBytesRanges = HashMap()
				}

				requestedPreloadedBytesRanges!![downloadOffset] = 1

				if (BuildConfig.DEBUG) {
					FileLog.d("start next preload from $downloadOffset size $totalBytesCount for $cacheFilePreload")
				}

				preloadNotRequestedBytesCount -= currentDownloadChunkSize.toLong()
			}
			else {
				if (notRequestedBytesRanges != null) {
					val streamOffset = if (streamPriorityStartOffset != 0L) streamPriorityStartOffset else streamStartOffset
					val size = notRequestedBytesRanges!!.size
					var minStart = Long.MAX_VALUE
					var minStreamStart = Long.MAX_VALUE

					for (b in 0 until size) {
						val range = notRequestedBytesRanges!![b]

						if (streamOffset != 0L) {
							if (range.start <= streamOffset && range.end > streamOffset) {
								minStreamStart = streamOffset
								minStart = Long.MAX_VALUE
								break
							}

							if (range.start in (streamOffset + 1)..<minStreamStart) {
								minStreamStart = range.start
							}
						}

						minStart = min(minStart.toDouble(), range.start.toDouble()).toLong()
					}

					downloadOffset = if (minStreamStart != Long.MAX_VALUE) {
						minStreamStart
					}
					else if (minStart != Long.MAX_VALUE) {
						minStart
					}
					else {
						break
					}
				}
				else {
					downloadOffset = requestedBytesCount
				}
			}

			if (!isPreloadVideoOperation && notRequestedBytesRanges != null) {
				addPart(notRequestedBytesRanges, downloadOffset, downloadOffset + currentDownloadChunkSize, false)
			}

			if (totalBytesCount in 1..downloadOffset) {
				break
			}

			val isLast = totalBytesCount <= 0 || a == count - 1 || downloadOffset + currentDownloadChunkSize >= totalBytesCount
			val request: TLObject
			val connectionType = if (requestsCount % 2 == 0) ConnectionsManager.ConnectionTypeDownload else ConnectionsManager.ConnectionTypeDownload2
			var flags = if (isForceRequest) ConnectionsManager.RequestFlagForceDownload else ConnectionsManager.RequestFlagFailOnServerErrors

			if (isCdn) {
				val req = TL_upload_getCdnFile()
				req.file_token = cdnToken
				req.offset = downloadOffset
				req.limit = currentDownloadChunkSize

				request = req

				flags = flags or ConnectionsManager.RequestFlagEnableUnauthorized
			}
			else {
				if (webLocation != null) {
					val req = TL_upload_getWebFile()
					req.location = webLocation
					req.offset = downloadOffset.toInt()
					req.limit = currentDownloadChunkSize

					request = req
				}
				else {
					val req = TL_upload_getFile()
					req.location = location
					req.offset = downloadOffset
					req.limit = currentDownloadChunkSize
					req.cdn_supported = true

					request = req
				}
			}

			requestedBytesCount += currentDownloadChunkSize.toLong()

			val requestInfo = RequestInfo()

			requestInfos.add(requestInfo)

			requestInfo.offset = downloadOffset

			if (!isPreloadVideoOperation && supportsPreloading && preloadStream != null && preloadedBytesRanges != null) {
				val range = preloadedBytesRanges!![requestInfo.offset]

				if (range != null) {
					requestInfo.response = TL_upload_file()

					try {
						if (BuildConfig.DEBUG && range.length > Int.MAX_VALUE) {
							throw RuntimeException("cast long to integer")
						}

						val buffer = NativeByteBuffer(range.length.toInt())

						preloadStream?.seek(range.fileOffset)
						preloadStream?.channel?.read(buffer.buffer)

						buffer.buffer.position(0)

						requestInfo.response?.bytes = buffer

						Utilities.stageQueue.postRunnable {
							processRequestResult(requestInfo, null)
							requestInfo.response?.freeResources()
						}

						continue
					}
					catch (e: Exception) {
						// ignored
					}
				}
			}

			if (streamPriorityStartOffset != 0L) {
				if (BuildConfig.DEBUG) {
					FileLog.d("frame get offset = $streamPriorityStartOffset")
				}

				streamPriorityStartOffset = 0
				priorityRequestInfo = requestInfo
			}

			if (location is TL_inputPeerPhotoFileLocation) {
				val inputPeerPhotoFileLocation = location as TL_inputPeerPhotoFileLocation

				if (inputPeerPhotoFileLocation.photo_id == 0L) {
					requestReference(requestInfo)
					continue
				}
			}

			requestInfo.requestToken = ConnectionsManager.getInstance(currentAccount).sendRequest(request, { response, error ->
				@Suppress("NAME_SHADOWING") var error = error

				if (!requestInfos.contains(requestInfo)) {
					return@sendRequest
				}

				if (requestInfo === priorityRequestInfo) {
					if (BuildConfig.DEBUG) {
						FileLog.d("frame get request completed " + priorityRequestInfo!!.offset)
					}

					priorityRequestInfo = null
				}

				if (error != null) {
					if (FileRefController.isFileRefError(error.text)) {
						requestReference(requestInfo)
						return@sendRequest
					}
					else if (request is TL_upload_getCdnFile) {
						if (error.text == "FILE_TOKEN_INVALID") {
							isCdn = false

							clearOperaion(requestInfo, false)
							startDownloadRequest()

							return@sendRequest
						}
					}
				}

				if (response is TL_upload_fileCdnRedirect) {
					if (response.file_hashes.isNotEmpty()) {
						if (cdnHashes == null) {
							cdnHashes = HashMap()
						}

						for (a1 in response.file_hashes.indices) {
							val hash = response.file_hashes[a1]
							cdnHashes!![hash.offset] = hash
						}
					}

					if (response.encryption_iv == null || response.encryption_key == null || response.encryption_iv.size != 16 || response.encryption_key.size != 32) {
						error = TL_error()
						error.text = "bad redirect response"
						error.code = 400

						processRequestResult(requestInfo, error)
					}
					else {
						isCdn = true

						if (notCheckedCdnRanges == null) {
							notCheckedCdnRanges = ArrayList()
							notCheckedCdnRanges?.add(Range(0, maxCdnParts.toLong()))
						}

						cdnDatacenterId = response.dc_id
						cdnIv = response.encryption_iv
						cdnKey = response.encryption_key
						cdnToken = response.file_token

						clearOperaion(requestInfo, false)
						startDownloadRequest()
					}
				}
				else if (response is TL_upload_cdnFileReuploadNeeded) {
					if (!reuploadingCdn) {
						clearOperaion(requestInfo, false)

						reuploadingCdn = true

						val req = TL_upload_reuploadCdnFile()
						req.file_token = cdnToken
						req.request_token = response.request_token

						ConnectionsManager.getInstance(currentAccount).sendRequest(req, { response1, error1 ->
							reuploadingCdn = false

							if (error1 == null) {
								val vector = response1 as Vector

								if (vector.objects.isNotEmpty()) {
									if (cdnHashes == null) {
										cdnHashes = HashMap()
									}

									for (a1 in vector.objects.indices) {
										val hash = vector.objects[a1] as TL_fileHash
										cdnHashes!![hash.offset] = hash
									}
								}

								startDownloadRequest()
							}
							else {
								if (error1.text == "FILE_TOKEN_INVALID" || error1.text == "REQUEST_TOKEN_INVALID") {
									isCdn = false
									clearOperaion(requestInfo, false)
									startDownloadRequest()
								}
								else {
									onFail(false, 0)
								}
							}
						}, null, null, 0, datacenterId, ConnectionsManager.ConnectionTypeGeneric, true)
					}
				}
				else {
					if (response is TL_upload_file) {
						requestInfo.response = response
					}
					else if (response is TL_upload_webFile) {
						requestInfo.responseWeb = response

						if (totalBytesCount == 0L && requestInfo.responseWeb?.size != 0) {
							totalBytesCount = requestInfo.responseWeb?.size?.toLong() ?: 0L
						}
					}
					else {
						requestInfo.responseCdn = response as? TL_upload_cdnFile
					}

					if (response != null) {
						when (currentType) {
							ConnectionsManager.FileTypeAudio -> {
								StatsController.getInstance(currentAccount).incrementReceivedBytesCount(response.networkType, StatsController.TYPE_AUDIOS, (response.objectSize + 4).toLong())
							}

							ConnectionsManager.FileTypeVideo -> {
								StatsController.getInstance(currentAccount).incrementReceivedBytesCount(response.networkType, StatsController.TYPE_VIDEOS, (response.objectSize + 4).toLong())
							}

							ConnectionsManager.FileTypePhoto -> {
								StatsController.getInstance(currentAccount).incrementReceivedBytesCount(response.networkType, StatsController.TYPE_PHOTOS, (response.objectSize + 4).toLong())
							}

							ConnectionsManager.FileTypeFile -> {
								StatsController.getInstance(currentAccount).incrementReceivedBytesCount(response.networkType, StatsController.TYPE_FILES, (response.objectSize + 4).toLong())
							}
						}
					}

					processRequestResult(requestInfo, error)
				}
			}, null, null, flags, if (isCdn) cdnDatacenterId else /*ConnectionsManager.DEFAULT_DATACENTER_ID*/ datacenterId, connectionType, isLast)

			requestsCount++
		}
	}

	fun setDelegate(delegate: FileLoadOperationDelegate?) {
		this.delegate = delegate
	}

	companion object {
		private val filesQueue = DispatchQueue("writeFileQueue")
		private val lockObject = Any()
		private const val STATE_IDLE = 0
		private const val STATE_DOWNLOADING = 1
		private const val STATE_FAILED = 2
		private const val STATE_FINISHED = 3
		private const val STATE_CANCELLED = 4
		private const val PRELOAD_MAX_BYTES = 2 * 1024 * 1024
	}
}
