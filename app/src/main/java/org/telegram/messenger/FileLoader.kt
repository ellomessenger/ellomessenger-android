/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.messenger

import android.text.TextUtils
import android.util.SparseArray
import org.telegram.messenger.ChatObject.isChannelAndNotMegaGroup
import org.telegram.messenger.FileLoadOperation.FileLoadOperationDelegate
import org.telegram.messenger.FilePathDatabase.PathData
import org.telegram.messenger.FileUploadOperation.FileUploadOperationDelegate
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.*
import org.telegram.tgnet.TLRPC.ChatPhoto
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.InputEncryptedFile
import org.telegram.tgnet.TLRPC.InputFile
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.TLChatPhoto
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlin.math.max
import kotlin.math.min

class FileLoader(instance: Int) : BaseController(instance) {
	private var priorityIncreasePointer = 0
	private val largeFilesQueue = FileLoaderPriorityQueue("large files queue", 2)
	private val filesQueue = FileLoaderPriorityQueue("files queue", 3)
	private val imagesQueue = FileLoaderPriorityQueue("imagesQueue queue", 6)
	private val audioQueue = FileLoaderPriorityQueue("audioQueue queue", 3)
	private val fileDatabase = FilePathDatabase(instance)
	private val uploadOperationQueue = LinkedList<FileUploadOperation>()
	private val uploadSmallOperationQueue = LinkedList<FileUploadOperation>()
	private val uploadOperationPaths = ConcurrentHashMap<String, FileUploadOperation>()
	private val uploadOperationPathsEnc = ConcurrentHashMap<String, FileUploadOperation>()
	private var currentUploadOperationsCount = 0
	private var currentUploadSmallOperationsCount = 0
	private val loadOperationPaths = ConcurrentHashMap<String?, FileLoadOperation?>()
	private val loadOperationPathsUI = ConcurrentHashMap<String?, LoadOperationUIObject>(10, 1f, 2)
	private val uploadSizes = HashMap<String, Long>()
	private val loadingVideos = HashMap<String, Boolean?>()
	private var forceLoadingFile: String? = null
	private var delegate: FileLoaderDelegate? = null
	private var lastReferenceId = 0
	private val parentObjectReferences = ConcurrentHashMap<Int, Any>()

	private fun getPriorityValue(priorityType: Int): Int {
		return when (priorityType) {
			PRIORITY_STREAM -> {
				Int.MAX_VALUE
			}

			PRIORITY_HIGH -> {
				priorityIncreasePointer++
				(1 shl 20) + priorityIncreasePointer
			}

			PRIORITY_NORMAL_UP -> {
				priorityIncreasePointer++
				(1 shl 16) + priorityIncreasePointer
			}

			PRIORITY_NORMAL -> {
				1 shl 16
			}

			else -> {
				0
			}
		}
	}

	fun getFileReference(parentObject: Any): Int {
		val reference = lastReferenceId++
		parentObjectReferences[reference] = parentObject
		return reference
	}

	fun getParentObject(reference: Int): Any? {
		return parentObjectReferences[reference]
	}

	private fun setLoadingVideoInternal(document: TLRPC.Document?, player: Boolean) {
		val key = getAttachFileName(document)
		val dKey = key + if (player) "p" else ""
		loadingVideos[dKey] = true
		notificationCenter.postNotificationName(NotificationCenter.videoLoadingStateChanged, key)
	}

	fun setLoadingVideo(document: TLRPC.Document?, player: Boolean, schedule: Boolean) {
		if (document == null) {
			return
		}

		if (schedule) {
			AndroidUtilities.runOnUIThread {
				setLoadingVideoInternal(document, player)
			}
		}
		else {
			setLoadingVideoInternal(document, player)
		}
	}

	fun setLoadingVideoForPlayer(document: TLRPC.Document?, player: Boolean) {
		if (document == null) {
			return
		}

		val key = getAttachFileName(document)

		if (loadingVideos.containsKey(key + if (player) "" else "p")) {
			loadingVideos[key + (if (player) "p" else "")] = true
		}
	}

	private fun removeLoadingVideoInternal(document: TLRPC.Document, player: Boolean) {
		val key = getAttachFileName(document)
		val dKey = key + if (player) "p" else ""
		if (loadingVideos.remove(dKey) != null) {
			notificationCenter.postNotificationName(NotificationCenter.videoLoadingStateChanged, key)
		}
	}

	fun removeLoadingVideo(document: TLRPC.Document?, player: Boolean, schedule: Boolean) {
		if (document == null) {
			return
		}

		if (schedule) {
			AndroidUtilities.runOnUIThread {
				removeLoadingVideoInternal(document, player)
			}
		}
		else {
			removeLoadingVideoInternal(document, player)
		}
	}

	fun isLoadingVideo(document: TLRPC.Document?, player: Boolean): Boolean {
		return document != null && loadingVideos.containsKey(getAttachFileName(document) + if (player) "p" else "")
	}

	fun isLoadingVideoAny(document: TLRPC.Document?): Boolean {
		return isLoadingVideo(document, false) || isLoadingVideo(document, true)
	}

	fun cancelFileUpload(location: String, enc: Boolean) {
		fileLoaderQueue.postRunnable {
			val operation = if (!enc) {
				uploadOperationPaths[location]
			}
			else {
				uploadOperationPathsEnc[location]
			}

			uploadSizes.remove(location)

			if (operation != null) {
				uploadOperationPathsEnc.remove(location)
				uploadOperationQueue.remove(operation)
				uploadSmallOperationQueue.remove(operation)

				operation.cancel()
			}
		}
	}

	fun checkUploadNewDataAvailable(location: String, encrypted: Boolean, newAvailableSize: Long, finalSize: Long) {
		fileLoaderQueue.postRunnable {
			val operation = if (encrypted) {
				uploadOperationPathsEnc[location]
			}
			else {
				uploadOperationPaths[location]
			}

			if (operation != null) {
				operation.checkNewDataAvailable(newAvailableSize, finalSize)
			}
			else if (finalSize != 0L) {
				uploadSizes[location] = finalSize
			}
		}
	}

	fun onNetworkChanged(slow: Boolean) {
		fileLoaderQueue.postRunnable {
			for ((_, value) in uploadOperationPaths) {
				value.onNetworkChanged(slow)
			}

			for ((_, value) in uploadOperationPathsEnc) {
				value.onNetworkChanged(slow)
			}
		}
	}

	fun uploadFile(location: String?, encrypted: Boolean, small: Boolean, type: Int) {
		uploadFile(location, encrypted, small, 0, type, false)
	}

	fun uploadFile(location: String?, encrypted: Boolean, small: Boolean, estimatedSize: Long, type: Int, forceSmallFile: Boolean) {
		if (location == null) {
			return
		}

		fileLoaderQueue.postRunnable {
			if (encrypted) {
				if (uploadOperationPathsEnc.containsKey(location)) {
					return@postRunnable
				}
			}
			else {
				if (uploadOperationPaths.containsKey(location)) {
					return@postRunnable
				}
			}

			var estimated = estimatedSize

			if (estimated != 0L) {
				val finalSize = uploadSizes[location]

				if (finalSize != null) {
					estimated = 0

					uploadSizes.remove(location)
				}
			}

			val operation = FileUploadOperation(currentAccount, location, encrypted, estimated, type)

			if (estimatedSize != 0L) {
				delegate?.fileUploadProgressChanged(operation, location, 0, estimatedSize, encrypted)
			}

			if (encrypted) {
				uploadOperationPathsEnc[location] = operation
			}
			else {
				uploadOperationPaths[location] = operation
			}

			if (forceSmallFile) {
				operation.setForceSmallFile()
			}

			operation.setDelegate(object : FileUploadOperationDelegate {
				override fun didFinishUploadingFile(operation: FileUploadOperation, inputFile: InputFile?, inputEncryptedFile: InputEncryptedFile?, key: ByteArray?, iv: ByteArray?) {
					fileLoaderQueue.postRunnable {
						if (encrypted) {
							uploadOperationPathsEnc.remove(location)
						}
						else {
							uploadOperationPaths.remove(location)
						}

						if (small) {
							currentUploadSmallOperationsCount--

							if (currentUploadSmallOperationsCount < 1) {
								val operation12 = uploadSmallOperationQueue.poll()

								if (operation12 != null) {
									currentUploadSmallOperationsCount++
									operation12.start()
								}
							}
						}
						else {
							currentUploadOperationsCount--

							if (currentUploadOperationsCount < 1) {
								val operation12 = uploadOperationQueue.poll()

								if (operation12 != null) {
									currentUploadOperationsCount++
									operation12.start()
								}
							}
						}

						delegate?.fileDidUpload(location, inputFile, inputEncryptedFile, key, iv, operation.totalFileSize)
					}
				}

				override fun didFailedUploadingFile(operation: FileUploadOperation) {
					fileLoaderQueue.postRunnable {
						if (encrypted) {
							uploadOperationPathsEnc.remove(location)
						}
						else {
							uploadOperationPaths.remove(location)
						}

						delegate?.fileDidFailToUpload(location, encrypted)

						if (small) {
							currentUploadSmallOperationsCount--

							if (currentUploadSmallOperationsCount < 1) {
								val operation1 = uploadSmallOperationQueue.poll()

								if (operation1 != null) {
									currentUploadSmallOperationsCount++
									operation1.start()
								}
							}
						}
						else {
							currentUploadOperationsCount--

							if (currentUploadOperationsCount < 1) {
								val operation1 = uploadOperationQueue.poll()

								if (operation1 != null) {
									currentUploadOperationsCount++
									operation1.start()
								}
							}
						}
					}
				}

				override fun didChangedUploadProgress(operation: FileUploadOperation, uploadedSize: Long, totalSize: Long) {
					delegate?.fileUploadProgressChanged(operation, location, uploadedSize, totalSize, encrypted)
				}
			})

			if (small) {
				if (currentUploadSmallOperationsCount < 1) {
					currentUploadSmallOperationsCount++
					operation.start()
				}
				else {
					uploadSmallOperationQueue.add(operation)
				}
			}
			else {
				if (currentUploadOperationsCount < 1) {
					currentUploadOperationsCount++
					operation.start()
				}
				else {
					uploadOperationQueue.add(operation)
				}
			}
		}
	}

	fun setForceStreamLoadingFile(location: FileLocation?, ext: String?) {
		if (location == null) {
			return
		}

		fileLoaderQueue.postRunnable {
			forceLoadingFile = getAttachFileName(location, ext)

			val operation = loadOperationPaths[forceLoadingFile]

			if (operation != null) {
				if (operation.isPreloadVideoOperation()) {
					operation.setIsPreloadVideoOperation(false)
				}

				operation.isForceRequest = true
				operation.priority = getPriorityValue(PRIORITY_STREAM)

				operation.queue?.add(operation)
				operation.queue?.checkLoadingOperations()
			}
		}
	}

	fun cancelLoadFile(document: TLRPC.Document?) {
		cancelLoadFile(document, null, null, null, null, null)
	}

	fun cancelLoadFile(document: SecureDocument?) {
		cancelLoadFile(null, document, null, null, null, null)
	}

	fun cancelLoadFile(document: WebFile?) {
		cancelLoadFile(null, null, document, null, null, null)
	}

	fun cancelLoadFile(photo: PhotoSize?) {
		cancelLoadFile(null, null, null, photo?.location, null, null)
	}

	fun cancelLoadFile(location: FileLocation?, ext: String?) {
		cancelLoadFile(null, null, null, location, ext, null)
	}

	fun cancelLoadFile(fileName: String?) {
		cancelLoadFile(null, null, null, null, null, fileName)
	}

	fun cancelLoadFiles(fileNames: ArrayList<String>) {
		for (name in fileNames) {
			cancelLoadFile(null, null, null, null, null, name)
		}
	}

	private fun cancelLoadFile(document: TLRPC.Document?, secureDocument: SecureDocument?, webDocument: WebFile?, location: FileLocation?, locationExt: String?, name: String?) {
		if (location == null && document == null && webDocument == null && secureDocument == null && name.isNullOrEmpty()) {
			return
		}

		val fileName = if (location != null) {
			getAttachFileName(location, locationExt)
		}
		else if (document != null) {
			getAttachFileName(document)
		}
		else if (secureDocument != null) {
			getAttachFileName(secureDocument)
		}
		else if (webDocument != null) {
			getAttachFileName(webDocument)
		}
		else {
			name
		}

		val uiObject = loadOperationPathsUI.remove(fileName)
		val runnable = uiObject?.loadInternalRunnable
		val removed = uiObject != null

		if (runnable != null) {
			fileLoaderQueue.cancelRunnable(runnable)
		}

		fileLoaderQueue.postRunnable {
			val operation = loadOperationPaths.remove(fileName)

			if (operation != null) {
				val queue = operation.queue
				queue?.cancel(operation)
			}
		}

		if (removed && document != null) {
			AndroidUtilities.runOnUIThread {
				notificationCenter.postNotificationName(NotificationCenter.onDownloadingFilesChanged)
			}
		}
	}

	fun isLoadingFile(fileName: String?): Boolean {
		return fileName != null && loadOperationPathsUI.containsKey(fileName)
	}

	fun getBufferedProgressFromPosition(position: Float, fileName: String?): Float {
		if (fileName.isNullOrEmpty()) {
			return 0f
		}

		val loadOperation = loadOperationPaths[fileName]

		return loadOperation?.getDownloadedLengthFromOffset(position) ?: 0.0f
	}

	fun loadFile(imageLocation: ImageLocation?, parentObject: Any?, ext: String?, priority: Int, cacheType: Int) {
		@Suppress("NAME_SHADOWING") var cacheType = cacheType

		if (imageLocation == null) {
			return
		}

		if (cacheType == 0 && (imageLocation.isEncrypted || imageLocation.photoSize != null && imageLocation.size == 0L)) {
			cacheType = 1
		}

		loadFile(imageLocation.document, imageLocation.secureDocument, imageLocation.webFile, imageLocation.location, imageLocation, parentObject, ext, imageLocation.size, priority, cacheType)
	}

	fun loadFile(secureDocument: SecureDocument?, priority: Int) {
		if (secureDocument == null) {
			return
		}

		loadFile(null, secureDocument, null, null, null, null, null, 0, priority, 1)
	}

	fun loadFile(document: TLRPC.Document?, parentObject: Any?, priority: Int, cacheType: Int) {
		if (document == null) {
			return
		}

		loadFile(document, null, null, null, null, parentObject, null, 0, priority, cacheType)
	}

	fun loadFile(document: WebFile?, priority: Int, cacheType: Int) {
		loadFile(null, null, document, null, null, null, null, 0, priority, cacheType)
	}

	private fun loadFileInternal(document: TLRPC.Document?, secureDocument: SecureDocument?, webDocument: WebFile?, location: FileLocation?, imageLocation: ImageLocation?, parentObject: Any?, locationExt: String?, locationSize: Long, priority: Int, stream: FileLoadOperationStream?, streamOffset: Long, streamPriority: Boolean, cacheType: Int): FileLoadOperation? {
		@Suppress("NAME_SHADOWING") var priority = priority

		val fileName = if (location != null) {
			getAttachFileName(location, locationExt)
		}
		else if (secureDocument != null) {
			getAttachFileName(secureDocument)
		}
		else if (document != null) {
			getAttachFileName(document)
		}
		else if (webDocument != null) {
			getAttachFileName(webDocument)
		}
		else {
			null
		}

		if (fileName == null || fileName.contains("" + Int.MIN_VALUE)) {
			return null
		}

		if (cacheType != 10 && !TextUtils.isEmpty(fileName) && !fileName.contains("" + Int.MIN_VALUE)) {
			loadOperationPathsUI[fileName] = LoadOperationUIObject()
		}

		if (document != null && parentObject is MessageObject && parentObject.putInDownloadsStore && !parentObject.isAnyKindOfSticker) {
			downloadController.startDownloadFile(parentObject as MessageObject?)
		}

		var operation = loadOperationPaths[fileName]

		if (BuildConfig.DEBUG) {
			FileLog.d("checkFile operation fileName=" + fileName + " documentName=" + getDocumentFileName(document) + " operation=" + operation)
		}

		if (stream != null) {
			priority = PRIORITY_STREAM
		}

		priority = getPriorityValue(priority)

		if (operation != null) {
			if (cacheType != 10 && operation.isPreloadVideoOperation()) {
				operation.setIsPreloadVideoOperation(false)
			}

			operation.isForceRequest = priority > 0
			operation.priority = priority
			operation.setStream(stream, streamPriority, streamOffset)
			operation.queue?.add(operation)
			operation.updateProgress()
			operation.queue?.checkLoadingOperations()

			return operation
		}

		val tempDir = getDirectory(MEDIA_DIR_CACHE)
		var storeDir = tempDir
		var type = MEDIA_DIR_CACHE
		var documentId: Long = 0
		var dcId = 0

		if (secureDocument != null) {
			operation = FileLoadOperation(secureDocument)
			type = MEDIA_DIR_DOCUMENT
		}
		else if (location != null) {
			documentId = location.volumeId
			dcId = (location as? TLRPC.TLFileLocation)?.dcId ?: 0
			operation = FileLoadOperation(imageLocation!!, parentObject, locationExt, locationSize)
			type = MEDIA_DIR_IMAGE
		}
		else if (document != null && document is TLRPC.TLDocument) {
			operation = FileLoadOperation(document, parentObject)

			if (MessageObject.isVoiceDocument(document)) {
				type = MEDIA_DIR_AUDIO
			}
			else if (MessageObject.isVideoDocument(document)) {
				type = MEDIA_DIR_VIDEO
				documentId = document.id
				dcId = document.dcId
			}
			else {
				type = MEDIA_DIR_DOCUMENT
				documentId = document.id
				dcId = document.dcId
			}

			if (MessageObject.isRoundVideoDocument(document)) {
				documentId = 0
				dcId = 0
			}
		}
		else if (webDocument != null) {
			operation = FileLoadOperation(currentAccount, webDocument)

			type = if (webDocument.location != null) {
				MEDIA_DIR_CACHE
			}
			else if (MessageObject.isVoiceWebDocument(webDocument)) {
				MEDIA_DIR_AUDIO
			}
			else if (MessageObject.isVideoWebDocument(webDocument)) {
				MEDIA_DIR_VIDEO
			}
			else if (MessageObject.isImageWebDocument(webDocument)) {
				MEDIA_DIR_IMAGE
			}
			else {
				MEDIA_DIR_DOCUMENT
			}
		}

		if (operation == null) {
			return null
		}

		val loaderQueue = if (type == MEDIA_DIR_AUDIO) {
			audioQueue
		}
		else if (secureDocument != null || location != null && (imageLocation == null || imageLocation.imageType != IMAGE_TYPE_ANIMATION) || MessageObject.isImageWebDocument(webDocument) || MessageObject.isStickerDocument(document) || MessageObject.isAnimatedStickerDocument(document, true) || MessageObject.isVideoStickerDocument(document)) {
			imagesQueue
		}
		else {
			if (document == null || document.size > 20 * 1024 * 1024) {
				largeFilesQueue
			}
			else {
				filesQueue
			}
		}

		var storeFileName = fileName

		if (cacheType == 0 || cacheType == 10) {
			if (documentId != 0L) {
				val path = fileDatabase.getPath(documentId, dcId, type, true)
				var customPath = false

				if (path != null) {
					val file = File(path)

					if (file.exists()) {
						customPath = true
						storeFileName = file.getName()
						storeDir = file.getParentFile()
					}
				}

				if (!customPath) {
					storeFileName = fileName
					storeDir = getDirectory(type)

					var saveCustomPath = false

					if ((type == MEDIA_DIR_IMAGE || type == MEDIA_DIR_VIDEO) && canSaveToPublicStorage(parentObject)) {
						val newDir = if (type == MEDIA_DIR_IMAGE) {
							getDirectory(MEDIA_DIR_IMAGE_PUBLIC)
						}
						else {
							getDirectory(MEDIA_DIR_VIDEO_PUBLIC)
						}

						if (newDir != null) {
							storeDir = newDir
							saveCustomPath = true
						}
					}
					else if (!getDocumentFileName(document).isNullOrEmpty() && canSaveAsFile(parentObject)) {
						storeFileName = getDocumentFileName(document)

						val newDir = getDirectory(MEDIA_DIR_FILES)

						if (newDir != null) {
							storeDir = newDir
							saveCustomPath = true
						}
					}

					if (saveCustomPath) {
						operation.pathSaveData = PathData(documentId, dcId, type)
					}
				}
			}
			else {
				storeDir = getDirectory(type)
			}
		}
		else if (cacheType == 2) {
			operation.setEncryptFile(true)
		}

		operation.setPaths(currentAccount, fileName, loaderQueue, storeDir, tempDir, storeFileName)

		if (cacheType == 10) {
			operation.setIsPreloadVideoOperation(true)
		}

		val fileLoadOperationDelegate = object : FileLoadOperationDelegate {
			override fun didFinishLoadingFile(operation: FileLoadOperation, finalFile: File?) {
				if (!operation.isPreloadVideoOperation() && operation.isPreloadFinished) {
					return
				}

				if (document != null && parentObject is MessageObject && parentObject.putInDownloadsStore) {
					downloadController.onDownloadComplete(parentObject as MessageObject?)
				}

				if (!operation.isPreloadVideoOperation()) {
					loadOperationPathsUI.remove(fileName)
					delegate?.fileDidLoad(fileName, finalFile, parentObject, type)
				}

				operation.queue?.let {
					checkDownloadQueue(it, fileName)
				}
			}

			override fun didFailedLoadingFile(operation: FileLoadOperation, state: Int) {
				loadOperationPathsUI.remove(fileName)

				operation.queue?.let {
					checkDownloadQueue(it, fileName)
				}

				delegate?.fileDidFailToLoad(fileName, state)

				if (document != null && parentObject is MessageObject && state == 0) {
					downloadController.onDownloadFail(parentObject, state)
				}
			}

			override fun didChangedLoadProgress(operation: FileLoadOperation, uploadedSize: Long, totalSize: Long) {
				delegate?.fileLoadProgressChanged(operation, fileName, uploadedSize, totalSize)
			}

			override fun saveFilePath(pathSaveData: PathData, cacheFileFinal: File?) {
				fileDatabase.putPath(pathSaveData.id, pathSaveData.dc, pathSaveData.type, cacheFileFinal?.toString())
			}

			override fun hasAnotherRefOnFile(path: String): Boolean {
				return fileDatabase.hasAnotherRefOnFile(path)
			}
		}

		operation.setDelegate(fileLoadOperationDelegate)

		loadOperationPaths[fileName] = operation

		operation.priority = priority
		operation.setStream(stream, streamPriority, streamOffset)

		if (BuildConfig.DEBUG) {
			FileLog.d("loadFileInternal fileName=" + fileName + " documentName=" + getDocumentFileName(document))
		}

		loaderQueue.add(operation)
		loaderQueue.checkLoadingOperations()

		return operation
	}

	private fun canSaveAsFile(parentObject: Any?): Boolean {
		if (parentObject is MessageObject) {
			return parentObject.isDocument()
		}

		return false
	}

	private fun canSaveToPublicStorage(parentObject: Any?): Boolean {
		if (SharedConfig.saveToGalleryFlags == 0 || BuildVars.NO_SCOPED_STORAGE) {
			return false
		}

		if (parentObject is MessageObject) {
			val flag: Int
			val dialogId = parentObject.dialogId

			if (parentObject.isRoundVideo || parentObject.isVoice || parentObject.isAnyKindOfSticker || messagesController.isChatNoForwards(messagesController.getChat(-dialogId)) || (parentObject.messageOwner as? TLRPC.TLMessage)?.noforwards == true || DialogObject.isEncryptedDialog(dialogId)) {
				return false
			}

			flag = if (dialogId >= 0) {
				SharedConfig.SAVE_TO_GALLERY_FLAG_PEER
			}
			else {
				if (isChannelAndNotMegaGroup(messagesController.getChat(-dialogId))) {
					SharedConfig.SAVE_TO_GALLERY_FLAG_CHANNELS
				}
				else {
					SharedConfig.SAVE_TO_GALLERY_FLAG_GROUP
				}
			}

			return SharedConfig.saveToGalleryFlags and flag != 0
		}

		return false
	}

//	private fun addOperationToQueue(operation: FileLoadOperation, queue: LinkedList<FileLoadOperation>) {
//		val priority = operation.priority
//
//		if (priority > 0) {
//			var index = queue.size
//			var a = 0
//			val size = queue.size
//
//			while (a < size) {
//				val queuedOperation = queue[a]
//
//				if (queuedOperation.priority < priority) {
//					index = a
//					break
//				}
//
//				a++
//			}
//
//			queue.add(index, operation)
//		}
//		else {
//			queue.add(operation)
//		}
//	}

	private fun loadFile(document: TLRPC.Document?, secureDocument: SecureDocument?, webDocument: WebFile?, location: FileLocation?, imageLocation: ImageLocation?, parentObject: Any?, locationExt: String?, locationSize: Long, priority: Int, cacheType: Int) {
		val fileName = if (location != null) {
			getAttachFileName(location, locationExt)
		}
		else if (document != null) {
			getAttachFileName(document)
		}
		else if (webDocument != null) {
			getAttachFileName(webDocument)
		}
		else {
			null
		}

		val runnable = Runnable {
			loadFileInternal(document, secureDocument, webDocument, location, imageLocation, parentObject, locationExt, locationSize, priority, null, 0, false, cacheType)
		}

		if (cacheType != 10 && !fileName.isNullOrEmpty() && !fileName.contains("" + Int.MIN_VALUE)) {
			val uiObject = LoadOperationUIObject()
			uiObject.loadInternalRunnable = runnable

			loadOperationPathsUI[fileName] = uiObject
		}

		fileLoaderQueue.postRunnable(runnable)
	}

	fun loadStreamFile(stream: FileLoadOperationStream?, document: TLRPC.Document?, location: ImageLocation?, parentObject: Any?, offset: Long, priority: Boolean): FileLoadOperation? {
		val semaphore = CountDownLatch(1)
		var result: FileLoadOperation? = null

		fileLoaderQueue.postRunnable {
			result = loadFileInternal(document, null, null, if (document == null && location != null) location.location else null, location, parentObject, if (document == null && location != null) "mp4" else null, if (document == null && location != null) location.currentSize else 0, 1, stream, offset, priority, if (document == null) 1 else 0)
			semaphore.countDown()
		}

		try {
			semaphore.await()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		return result
	}

	private fun checkDownloadQueue(queue: FileLoaderPriorityQueue, fileName: String) {
		fileLoaderQueue.postRunnable {
			val operation = loadOperationPaths.remove(fileName)
			queue.remove(operation)
			queue.checkLoadingOperations()
		}
	}

	fun setDelegate(fileLoaderDelegate: FileLoaderDelegate?) {
		delegate = fileLoaderDelegate
	}

	fun getPathToMessage(message: TLRPC.Message?): File {
		return getPathToMessage(message, true)
	}

	fun getPathToMessage(message: TLRPC.Message?, useFileDatabaseQueue: Boolean): File {
		if (message == null) {
			return File("")
		}

		if (message is TLRPC.TLMessageService) {
			val photo = (message.action as? TLRPC.TLMessageActionChatEditPhoto)?.photo as? TLRPC.TLPhoto

			if (photo != null) {
				val sizes = photo.sizes

				if (sizes.size > 0) {
					val sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize())

					if (sizeFull != null) {
						return getPathToAttach(sizeFull, null, false, useFileDatabaseQueue)
					}
				}
			}
		}
		else {
			when (val media = MessageObject.getMedia(message)) {
				is TLRPC.TLMessageMediaDocument -> {
					return getPathToAttach(media.document, null, media.ttlSeconds != 0, useFileDatabaseQueue)
				}

				is TLRPC.TLMessageMediaPhoto -> {
					val sizes = (media.photo as? TLRPC.TLPhoto)?.sizes

					if (sizes != null && sizes.size > 0) {
						val sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize(), false, null, true)

						if (sizeFull != null) {
							return getPathToAttach(sizeFull, null, media.ttlSeconds != 0, useFileDatabaseQueue)
						}
					}
				}

				is TLRPC.TLMessageMediaWebPage -> {
					val webpage = media.webpage as? TLRPC.TLWebPage

					if (webpage?.document != null) {
						return getPathToAttach(webpage.document, null, false, useFileDatabaseQueue)
					}
					else if (webpage?.photo != null) {
						val sizes = (webpage.photo as? TLRPC.TLPhoto)?.sizes

						if (sizes != null && sizes.size > 0) {
							val sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize())

							if (sizeFull != null) {
								return getPathToAttach(sizeFull, null, false, useFileDatabaseQueue)
							}
						}
					}
				}

				is TLRPC.TLMessageMediaInvoice -> {
					return getPathToAttach(media.photo, null, true, useFileDatabaseQueue)
				}
			}
		}

		return File("")
	}

	fun getPathToAttach(attach: TLObject?): File {
		return getPathToAttach(attach, null, false)
	}

	fun getPathToAttach(attach: TLObject?, forceCache: Boolean): File {
		return getPathToAttach(attach, null, forceCache)
	}

	fun getPathToAttach(attach: TLObject?, ext: String?, forceCache: Boolean): File {
		return getPathToAttach(attach, null, ext, forceCache, true)
	}

	fun getPathToAttach(attach: TLObject?, ext: String?, forceCache: Boolean, useFileDatabaseQueue: Boolean): File {
		return getPathToAttach(attach, null, ext, forceCache, useFileDatabaseQueue)
	}

	/**
	 * Return real file name. Used before file.exist()
	 */
	fun getPathToAttach(attach: TLObject?, size: String?, ext: String?, forceCache: Boolean, useFileDatabaseQueue: Boolean): File {
		@Suppress("NAME_SHADOWING") var size = size
		var dir: File? = null
		var documentId = 0L
		var dcId = 0
		var type = 0

		if (forceCache) {
			dir = getDirectory(MEDIA_DIR_CACHE)
		}
		else {
			if (attach is TLRPC.TLDocument) {
				type = if (MessageObject.isVoiceDocument(attach)) {
					MEDIA_DIR_AUDIO
				}
				else if (MessageObject.isVideoDocument(attach)) {
					MEDIA_DIR_VIDEO
				}
				else {
					MEDIA_DIR_DOCUMENT
				}

				documentId = attach.id
				dcId = attach.dcId
				dir = getDirectory(type)
			}
			else if (attach is TLRPC.TLPhoto) {
				val photoSize = getClosestPhotoSizeWithSize(attach.sizes, AndroidUtilities.getPhotoSize())
				return getPathToAttach(photoSize, ext, false, useFileDatabaseQueue)
			}
			else if (attach is PhotoSize) {
				if (attach is TLRPC.TLPhotoStrippedSize || attach is TLRPC.TLPhotoPathSize) {
					dir = null
				}
				else if (attach.location == null || /*attach.location.key != null || */ attach.location!!.volumeId == Int.MIN_VALUE.toLong() && attach.location!!.localId < 0 || attach.size < 0) {
					dir = getDirectory(MEDIA_DIR_CACHE.also { type = it })
				}
				else {
					dir = getDirectory(MEDIA_DIR_IMAGE.also { type = it })
				}

				documentId = attach.location?.volumeId ?: 0
				dcId = attach.location?.dcId ?: 0

			}
			else if (attach is TLRPC.VideoSize) {
				if (attach.size < 0) {
					dir = getDirectory(MEDIA_DIR_CACHE.also { type = it })
				}
				else {
					dir = getDirectory(MEDIA_DIR_IMAGE.also { type = it })
				}

				documentId = attach.location?.volumeId ?: 0L
				dcId = attach.location?.dcId ?: 0
			}
			else if (attach is TLRPC.TLFileLocation) {
				if (attach.volumeId == Int.MIN_VALUE.toLong() && attach.localId < 0) {
					dir = getDirectory(MEDIA_DIR_CACHE)
				}
				else {
					documentId = attach.volumeId
					dcId = attach.dcId
					dir = getDirectory(MEDIA_DIR_IMAGE.also { type = it })
				}
			}
			else if (attach is TLRPC.UserProfilePhoto || attach is ChatPhoto) {
				if (size == null) {
					size = "s"
				}

				dir = if ("s" == size) {
					getDirectory(MEDIA_DIR_CACHE)
				}
				else {
					getDirectory(MEDIA_DIR_IMAGE)
				}
			}
			else if (attach is WebFile) {
				dir = if (attach.mimeType?.startsWith("image/") == true) {
					getDirectory(MEDIA_DIR_IMAGE)
				}
				else if (attach.mimeType?.startsWith("audio/") == true) {
					getDirectory(MEDIA_DIR_AUDIO)
				}
				else if (attach.mimeType?.startsWith("video/") == true) {
					getDirectory(MEDIA_DIR_VIDEO)
				}
				else {
					getDirectory(MEDIA_DIR_DOCUMENT)
				}
			}
			else if (attach is TLRPC.TLSecureFile || attach is SecureDocument) {
				dir = getDirectory(MEDIA_DIR_CACHE)
			}
		}

		if (dir == null) {
			return File("")
		}

		if (documentId != 0L) {
			val path = getInstance(UserConfig.selectedAccount).fileDatabase.getPath(documentId, dcId, type, useFileDatabaseQueue)

			if (path != null) {
				return File(path)
			}
		}

		return File(dir, getAttachFileName(attach, ext))
	}

	fun deleteFiles(files: ArrayList<File>?, type: Int) {
		if (files.isNullOrEmpty()) {
			return
		}

		fileLoaderQueue.postRunnable {
			for (a in files.indices) {
				val file = files[a]
				val encrypted = File(file.absolutePath + ".enc")

				if (encrypted.exists()) {
					try {
						if (!encrypted.delete()) {
							encrypted.deleteOnExit()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
					try {
						val key = File(internalCacheDir, file.getName() + ".enc.key")

						if (!key.delete()) {
							key.deleteOnExit()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				else if (file.exists()) {
					try {
						if (!file.delete()) {
							file.deleteOnExit()
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}
				try {
					val qFile = File(file.getParentFile(), "q_" + file.getName())

					if (qFile.exists()) {
						if (!qFile.delete()) {
							qFile.deleteOnExit()
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			if (type == 2) {
				ImageLoader.getInstance().clearMemory()
			}
		}
	}

	fun getCurrentLoadingFiles(currentLoadingFiles: ArrayList<MessageObject>) {
		currentLoadingFiles.clear()
		currentLoadingFiles.addAll(downloadController.downloadingFiles)

		for (file in currentLoadingFiles) {
			file.isDownloadingFile = true
		}
	}

	fun getRecentLoadingFiles(recentLoadingFiles: ArrayList<MessageObject>) {
		recentLoadingFiles.clear()
		recentLoadingFiles.addAll(downloadController.recentDownloadingFiles)

		for (file in recentLoadingFiles) {
			file.isDownloadingFile = true
		}
	}

	fun checkCurrentDownloadsFiles() {
		val messagesToRemove = ArrayList<MessageObject>()
		val messageObjects = ArrayList(downloadController.recentDownloadingFiles)

		for (messageObject in messageObjects) {
			messageObject.checkMediaExistence()

			if (messageObject.mediaExists) {
				messagesToRemove.add(messageObject)
			}
		}

		if (messagesToRemove.isNotEmpty()) {
			AndroidUtilities.runOnUIThread {
				downloadController.recentDownloadingFiles.removeAll(messagesToRemove.toSet())
				notificationCenter.postNotificationName(NotificationCenter.onDownloadingFilesChanged)
			}
		}
	}

	/**
	 * Optimized for bulk messages
	 */
	fun checkMediaExistence(messageObjects: List<MessageObject>?) {
		fileDatabase.checkMediaExistence(messageObjects)
	}

	interface FileResolver {
		val file: File?
	}

//	fun clearRecentDownloadedFiles() {
//		downloadController.clearRecentDownloadedFiles()
//	}

	fun clearFilePaths() {
		fileDatabase.clear()
	}

	private class LoadOperationUIObject {
		var loadInternalRunnable: Runnable? = null
	}

	interface FileLoaderDelegate {
		fun fileUploadProgressChanged(operation: FileUploadOperation?, location: String?, uploadedSize: Long, totalSize: Long, isEncrypted: Boolean)
		fun fileDidUpload(location: String?, inputFile: InputFile?, inputEncryptedFile: InputEncryptedFile?, key: ByteArray?, iv: ByteArray?, totalFileSize: Long)
		fun fileDidFailToUpload(location: String?, isEncrypted: Boolean)
		fun fileDidLoad(location: String?, finalFile: File?, parentObject: Any?, type: Int)
		fun fileDidFailToLoad(location: String?, state: Int)
		fun fileLoadProgressChanged(operation: FileLoadOperation?, location: String?, uploadedSize: Long, totalSize: Long)
	}

	companion object {
		private const val PRIORITY_STREAM = 4
		const val PRIORITY_HIGH = 3
		const val PRIORITY_NORMAL_UP = 2
		const val PRIORITY_NORMAL = 1
		const val PRIORITY_LOW = 0
		const val MEDIA_DIR_IMAGE = 0
		const val MEDIA_DIR_AUDIO = 1
		const val MEDIA_DIR_VIDEO = 2
		const val MEDIA_DIR_DOCUMENT = 3
		const val MEDIA_DIR_CACHE = 4
		const val MEDIA_DIR_FILES = 5
		const val MEDIA_DIR_IMAGE_PUBLIC = 100
		const val MEDIA_DIR_VIDEO_PUBLIC = 101
		const val IMAGE_TYPE_LOTTIE = 1
		const val IMAGE_TYPE_ANIMATION = 2
		const val IMAGE_TYPE_SVG = 3
		const val IMAGE_TYPE_SVG_WHITE = 4
		const val IMAGE_TYPE_THEME_PREVIEW = 5
		const val DEFAULT_MAX_FILE_SIZE = 1024L * 1024L * 1024L

		//public final static long DEFAULT_MAX_FILE_SIZE_PREMIUM = DEFAULT_MAX_FILE_SIZE * 2L;
		const val DEFAULT_MAX_FILE_SIZE_PREMIUM = DEFAULT_MAX_FILE_SIZE
		const val PRELOAD_CACHE_TYPE = 11
		private val fileLoaderQueue = DispatchQueue("fileUploadQueue")
		private var mediaDirs: SparseArray<File>? = null
		private val Instance = arrayOfNulls<FileLoader>(UserConfig.MAX_ACCOUNT_COUNT)

		@JvmStatic
		fun getInstance(num: Int): FileLoader {
			var localInstance = Instance[num]

			if (localInstance == null) {
				synchronized(FileLoader::class.java) {
					localInstance = Instance[num]

					if (localInstance == null) {
						localInstance = FileLoader(num)
						Instance[num] = localInstance
					}
				}
			}

			return localInstance!!
		}

		fun setMediaDirs(dirs: SparseArray<File>?) {
			mediaDirs = dirs
		}

		@JvmStatic
		fun checkDirectory(type: Int): File? {
			return mediaDirs?.get(type)
		}

		@JvmStatic
		fun getDirectory(type: Int): File? {
			var dir = checkDirectory(type)

			if (dir == null && type != MEDIA_DIR_CACHE) {
				dir = mediaDirs?.get(MEDIA_DIR_CACHE)
			}

			try {
				if (dir != null && !dir.isDirectory()) {
					dir.mkdirs()
				}
			}
			catch (e: Exception) {
				// don't prompt
			}

			return dir
		}

		@JvmStatic
		fun getMessageFileName(message: TLRPC.Message?): String {
			if (message == null) {
				return ""
			}

			if (message is TLRPC.TLMessageService) {
				val photo = (message.action as? TLRPC.TLMessageActionChatEditPhoto)?.photo

				if (photo != null) {
					val sizes = (photo as? TLRPC.TLPhoto)?.sizes

					if (sizes != null && sizes.size > 0) {
						val sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize())

						if (sizeFull != null) {
							return getAttachFileName(sizeFull)
						}
					}
				}
			}
			else {
				when (val media = MessageObject.getMedia(message)) {
					is TLRPC.TLMessageMediaDocument -> {
						return getAttachFileName(media.document)
					}

					is TLRPC.TLMessageMediaPhoto -> {
						val sizes = (media.photo as? TLRPC.TLPhoto)?.sizes

						if (sizes != null && sizes.size > 0) {
							val sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize(), false, null, true)

							if (sizeFull != null) {
								return getAttachFileName(sizeFull)
							}
						}
					}

					is TLRPC.TLMessageMediaWebPage -> {
						val webpage = media.webpage as? TLRPC.TLWebPage

						if (webpage?.document != null) {
							return getAttachFileName(webpage.document)
						}
						else if (webpage?.photo != null) {
							val sizes = (webpage.photo as? TLRPC.TLPhoto)?.sizes

							if (sizes != null && sizes.size > 0) {
								val sizeFull = getClosestPhotoSizeWithSize(sizes, AndroidUtilities.getPhotoSize())

								if (sizeFull != null) {
									return getAttachFileName(sizeFull)
								}
							}
						}
					}

					is TLRPC.TLMessageMediaInvoice -> {
						val document = media.photo

						if (document != null) {
							return Utilities.MD5(document.url) + "." + ImageLoader.getHttpUrlExtension(document.url, getMimeTypePart(document.mimeType))
						}
					}
				}
			}

			return ""
		}

		@JvmStatic
		fun getClosestPhotoSizeWithSize(sizes: List<PhotoSize?>?, side: Int): PhotoSize? {
			return getClosestPhotoSizeWithSize(sizes, side, false)
		}

		@JvmStatic
		fun getClosestPhotoSizeWithSize(sizes: List<PhotoSize?>?, side: Int, byMinSide: Boolean): PhotoSize? {
			return getClosestPhotoSizeWithSize(sizes, side, byMinSide, null, false)
		}

		@JvmStatic
		fun getClosestPhotoSizeWithSize(sizes: List<PhotoSize?>?, side: Int, byMinSide: Boolean, toIgnore: PhotoSize?, ignoreStripped: Boolean): PhotoSize? {
			if (sizes.isNullOrEmpty()) {
				return null
			}

			var lastSide = 0
			var closestObject: PhotoSize? = null

			for (a in sizes.indices) {
				val obj = sizes[a]

				if (obj == null || obj === toIgnore || obj is TLRPC.TLPhotoSizeEmpty || obj is TLRPC.TLPhotoPathSize || ignoreStripped && obj is TLRPC.TLPhotoStrippedSize) {
					continue
				}

				if (byMinSide) {
					val currentSide = min(obj.h.toDouble(), obj.w.toDouble()).toInt()

					if (closestObject == null || side > 100 && closestObject.location != null && (closestObject.location as? TLRPC.TLFileLocation)?.dcId == Int.MIN_VALUE || obj is TLRPC.TLPhotoCachedSize || side > lastSide && lastSide < currentSide) {
						closestObject = obj
						lastSide = currentSide
					}
				}
				else {
					val currentSide = max(obj.w.toDouble(), obj.h.toDouble()).toInt()

					if (closestObject == null || side > 100 && closestObject.location != null && (closestObject.location as? TLRPC.TLFileLocation)?.dcId == Int.MIN_VALUE || obj is TLRPC.TLPhotoCachedSize || currentSide in (lastSide + 1)..side) {
						closestObject = obj
						lastSide = currentSide
					}
				}
			}

			return closestObject
		}

//		fun getPathPhotoSize(sizes: ArrayList<PhotoSize?>?): TL_photoPathSize? {
//			if (sizes.isNullOrEmpty()) {
//				return null
//			}
//
//			for (a in sizes.indices) {
//				val obj = sizes[a]
//
//				if (obj is TL_photoPathSize) {
//					continue
//				}
//
//				return obj as TL_photoPathSize?
//			}
//
//			return null
//		}

		@JvmStatic
		fun getFileExtension(file: File): String {
			val name = file.getName()

			return try {
				name.substring(name.lastIndexOf('.') + 1)
			}
			catch (e: Exception) {
				""
			}
		}

		@JvmStatic
		fun fixFileName(fileName: String?): String? {
			@Suppress("NAME_SHADOWING") var fileName = fileName

			if (fileName != null) {
				fileName = fileName.replace("[\u0001-\u001f<>\u202E:\"/\\\\|?*\u007f]+".toRegex(), "").trim()
			}

			return fileName
		}

		@JvmStatic
		fun getDocumentFileName(document: TLRPC.Document?): String? {
			if (document == null || document !is TLRPC.TLDocument) {
				return null
			}

			if (document.fileNameFixed != null) {
				return document.fileNameFixed
			}

			var fileName: String? = null

			for (documentAttribute in document.attributes) {
				if (documentAttribute is TLRPC.TLDocumentAttributeFilename) {
					fileName = documentAttribute.fileName
					break
				}
			}

			fileName = fixFileName(fileName)

			return fileName ?: ""
		}

		@JvmStatic
		fun getMimeTypePart(mime: String?): String {
			if (mime.isNullOrEmpty()) {
				return ""
			}

			var index: Int

			return if (mime.lastIndexOf('/').also { index = it } != -1) {
				mime.substring(index + 1)
			}
			else {
				""
			}
		}

		fun getExtensionByMimeType(mime: String?): String {
			return when (mime) {
				"video/mp4" -> ".mp4"
				"video/x-matroska" -> ".mkv"
				"audio/ogg" -> ".ogg"
				else -> ""
			}
		}

		@JvmStatic
		val internalCacheDir: File
			get() = ApplicationLoader.applicationContext.cacheDir

		@JvmStatic
		fun getDocumentExtension(document: TLRPC.Document): String {
			val fileName = getDocumentFileName(document)
			val idx = fileName!!.lastIndexOf('.')
			var ext: String? = null

			if (idx != -1) {
				ext = fileName.substring(idx + 1)
			}

			if (ext.isNullOrEmpty()) {
				ext = document.mimeType
			}

			if (ext == null) {
				ext = ""
			}

			ext = ext.uppercase()

			return ext
		}

		@JvmStatic
		fun getAttachFileName(attach: TLObject?): String {
			return getAttachFileName(attach, null)
		}

		fun getAttachFileName(attach: TLObject?, ext: String?): String {
			return getAttachFileName(attach, null, ext)
		}

		/**
		 * file hash. contains docId, dcId, ext.
		 */
		fun getAttachFileName(attach: TLObject?, size: String?, ext: String?): String {
			@Suppress("NAME_SHADOWING") var size = size

			when (attach) {
				is TLRPC.TLDocument -> {
					var docExt: String?
					docExt = getDocumentFileName(attach)

					var idx = -1

					if (docExt?.lastIndexOf('.')?.also { idx = it } == -1) {
						docExt = ""
					}
					else {
						docExt = docExt?.substring(idx)
					}

					if ((docExt?.length ?: 0) <= 1) {
						docExt = getExtensionByMimeType(attach.mimeType)
					}

					return if ((docExt?.length ?: 0) > 1) {
						attach.dcId.toString() + "_" + attach.id + docExt
					}
					else {
						attach.dcId.toString() + "_" + attach.id
					}
				}

				is SecureDocument -> {
					return attach.secureFile.dcId.toString() + "_" + attach.secureFile.id + ".jpg"
				}

				is TLRPC.TLSecureFile -> {
					return attach.dcId.toString() + "_" + attach.id + ".jpg"
				}

				is WebFile -> {
					return Utilities.MD5(attach.url) + "." + ImageLoader.getHttpUrlExtension(attach.url, getMimeTypePart(attach.mimeType ?: ""))
				}

				is PhotoSize -> {
					return if (attach.location == null || attach.location is TLRPC.TLFileLocationUnavailable) {
						""
					}
					else {
						attach.location?.volumeId?.toString() + "_" + attach.location?.localId + "." + (ext ?: "jpg")
					}
				}

				is TLRPC.VideoSize -> {
					return ""
				}

				is FileLocation -> {
					if (attach is TLRPC.TLFileLocationUnavailable) {
						return ""
					}

					return attach.volumeId.toString() + "_" + attach.localId + "." + (ext ?: "jpg")
				}

				is TLRPC.UserProfilePhoto -> {
					if (size == null) {
						size = "s"
					}

					return attach.photoId.toString() + "_" + size + "." + (ext ?: "jpg")
				}

				is TLChatPhoto -> {
					return attach.photoId.toString() + "_" + size + "." + (ext ?: "jpg")
				}

				else -> {
					return ""
				}
			}
		}

		fun isVideoMimeType(mime: String?): Boolean {
			return "video/mp4" == mime || SharedConfig.streamMkv && "video/x-matroska" == mime
		}

		@JvmOverloads
		@Throws(IOException::class)
		fun copyFile(sourceFile: InputStream, destFile: File?, maxSize: Int = -1): Boolean {
			FileOutputStream(destFile).use { fos ->
				val buf = ByteArray(4096)
				var len: Int
				var totalLen = 0

				while (sourceFile.read(buf).also { len = it } > 0) {
					Thread.yield()

					fos.write(buf, 0, len)

					totalLen += len

					if (maxSize in 1..totalLen) {
						break
					}
				}

				fos.getFD().sync()
			}

			return true
		}

//		fun isSamePhoto(photo1: TLObject?, photo2: TLObject?): Boolean {
//			if ((photo1 == null && photo2 != null) || (photo1 != null && photo2 == null)) {
//				return false
//			}
//
//			if (photo1 == null && photo2 == null) {
//				return true
//			}
//
//			if (photo1?.javaClass != photo2?.javaClass) {
//				return false
//			}
//
//			if (photo1 is UserProfilePhoto) {
//				val p2 = photo2 as? UserProfilePhoto
//				return photo1.photo_id == p2?.photo_id
//			}
//			else if (photo1 is ChatPhoto) {
//				val p2 = photo2 as? ChatPhoto
//				return photo1.photo_id == p2?.photo_id
//			}
//
//			return false
//		}

		fun isSamePhoto(location: FileLocation?, photo: TLRPC.Photo?): Boolean {
			if (location == null || photo !is TLRPC.TLPhoto) {
				return false
			}

			for (size in photo.sizes) {
				if (size.location != null && size.location?.localId == location.localId && size.location?.volumeId == location.volumeId) {
					return true
				}
			}

			return -location.volumeId == photo.id
		}

//		fun getPhotoId(`object`: TLObject?): Long {
//			return when (`object`) {
//				is TLRPC.Photo -> `object`.id
//				is ChatPhoto -> `object`.photo_id
//				is UserProfilePhoto -> `object`.photo_id
//				else -> 0
//			}
//		}

		fun checkUploadFileSize(currentAccount: Int, length: Long): Boolean {
			val premium = AccountInstance.getInstance(currentAccount).userConfig.isPremium
			return (length < DEFAULT_MAX_FILE_SIZE_PREMIUM && premium) || (length < DEFAULT_MAX_FILE_SIZE)
		}
	}
}
