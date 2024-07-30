/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.net.Uri
import android.util.SparseArray
import android.util.SparseIntArray
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.NativeByteBuffer
import org.telegram.tgnet.TLRPC.InputEncryptedFile
import org.telegram.tgnet.TLRPC.InputFile
import org.telegram.tgnet.TLRPC.TL_boolTrue
import org.telegram.tgnet.TLRPC.TL_inputEncryptedFileBigUploaded
import org.telegram.tgnet.TLRPC.TL_inputEncryptedFileUploaded
import org.telegram.tgnet.TLRPC.TL_inputFile
import org.telegram.tgnet.TLRPC.TL_inputFileBig
import org.telegram.tgnet.TLRPC.TL_upload_saveBigFilePart
import org.telegram.tgnet.TLRPC.TL_upload_saveFilePart
import org.telegram.tgnet.tlrpc.TLObject
import java.io.File
import java.io.FileDescriptor
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.max

class FileUploadOperation(private val currentAccount: Int, private val uploadingFilePath: String, private val isEncrypted: Boolean, private var estimatedSize: Long, private val currentType: Int) {
	private val requestTokens = SparseIntArray()
	private val cachedResults = SparseArray<UploadCachedResult>()
	private var isLastPart = false
	private var nextPartFirst = false
	private var operationGuid = 0
	private var maxRequestsCount = 0
	private var uploadChunkSize = 64 * 1024
	private var slowNetwork = false
	private var freeRequestIvs: ArrayList<ByteArray>? = null
	private var requestNum = 0
	private var state = 0
	private var readBuffer: ByteArray? = null
	private var delegate: FileUploadOperationDelegate? = null
	private var currentPartNum = 0
	private var currentFileId: Long = 0
	private var totalPartsCount = 0
	private var readBytesCount: Long = 0
	private var uploadedBytesCount: Long = 0
	private var saveInfoTimes = 0
	private var key: ByteArray? = null
	private var iv: ByteArray? = null
	private var ivChange: ByteArray? = null
	private var fingerprint = 0
	private var isBigFile = false
	private var forceSmallFile = false
	private var fileKey: String? = null
	private var uploadStartTime = 0
	private var stream: RandomAccessFile? = null
	private var started = false
	private var currentUploadRequestsCount = 0
	private var preferences: SharedPreferences? = null
	private var lastSavedPartNum = 0
	private var availableSize: Long = 0
	private var uploadFirstPartLater: Boolean

	@JvmField
	var lastProgressUpdateTime: Long = 0

	var totalFileSize: Long = 0
		private set

	init {
		uploadFirstPartLater = estimatedSize != 0L && !isEncrypted
	}

	fun setDelegate(fileUploadOperationDelegate: FileUploadOperationDelegate?) {
		delegate = fileUploadOperationDelegate
	}

	fun start() {
		if (state != 0) {
			return
		}

		state = 1

		Utilities.stageQueue.postRunnable {
			preferences = ApplicationLoader.applicationContext.getSharedPreferences("uploadinfo", Activity.MODE_PRIVATE)
			slowNetwork = ApplicationLoader.isConnectionSlow

			if (BuildConfig.DEBUG) {
				FileLog.d("start upload on slow network = $slowNetwork")
			}

			var a = 0
			val count = (if (slowNetwork) INITIAL_REQUESTS_SLOW_NETWORK_COUNT else INITIAL_REQUESTS_COUNT)

			while (a < count) {
				startUploadRequest()
				a++
			}
		}
	}

	fun onNetworkChanged(slow: Boolean) {
		if (state != 1) {
			return
		}

		Utilities.stageQueue.postRunnable {
			if (slowNetwork != slow) {
				slowNetwork = slow

				if (BuildConfig.DEBUG) {
					FileLog.d("network changed to slow = $slowNetwork")
				}

				for (a in 0 until requestTokens.size()) {
					ConnectionsManager.getInstance(currentAccount).cancelRequest(requestTokens.valueAt(a), true)
				}

				requestTokens.clear()

				cleanup()

				isLastPart = false
				nextPartFirst = false
				requestNum = 0
				currentPartNum = 0
				readBytesCount = 0
				uploadedBytesCount = 0
				saveInfoTimes = 0
				key = null
				iv = null
				ivChange = null
				currentUploadRequestsCount = 0
				lastSavedPartNum = 0
				uploadFirstPartLater = false
				cachedResults.clear()

				operationGuid++

				var a = 0
				val count = (if (slowNetwork) INITIAL_REQUESTS_SLOW_NETWORK_COUNT else INITIAL_REQUESTS_COUNT)

				while (a < count) {
					startUploadRequest()
					a++
				}
			}
		}
	}

	fun cancel() {
		if (state == 3) {
			return
		}

		state = 2

		Utilities.stageQueue.postRunnable {
			for (a in 0 until requestTokens.size()) {
				ConnectionsManager.getInstance(currentAccount).cancelRequest(requestTokens.valueAt(a), true)
			}
		}

		delegate?.didFailedUploadingFile(this)

		cleanup()
	}

	private fun cleanup() {
		if (preferences == null) {
			preferences = ApplicationLoader.applicationContext.getSharedPreferences("uploadinfo", Activity.MODE_PRIVATE)
		}

		preferences?.edit()?.remove(fileKey + "_time")?.remove(fileKey + "_size")?.remove(fileKey + "_uploaded")?.remove(fileKey + "_id")?.remove(fileKey + "_iv")?.remove(fileKey + "_key")?.remove(fileKey + "_ivc")?.commit()

		try {
			stream?.close()
			stream = null
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	fun checkNewDataAvailable(newAvailableSize: Long, finalSize: Long) {
		Utilities.stageQueue.postRunnable {
			if (estimatedSize != 0L && finalSize != 0L) {
				estimatedSize = 0
				totalFileSize = finalSize

				calcTotalPartsCount()

				if (!uploadFirstPartLater && started) {
					storeFileUploadInfo()
				}
			}

			availableSize = if (finalSize > 0) finalSize else newAvailableSize

			if (currentUploadRequestsCount < maxRequestsCount) {
				startUploadRequest()
			}
		}
	}

	private fun storeFileUploadInfo() {
		val editor = preferences?.edit() ?: return
		editor.putInt(fileKey + "_time", uploadStartTime)
		editor.putLong(fileKey + "_size", totalFileSize)
		editor.putLong(fileKey + "_id", currentFileId)
		editor.remove(fileKey + "_uploaded")

		if (isEncrypted) {
			editor.putString(fileKey + "_iv", Utilities.bytesToHex(iv))
			editor.putString(fileKey + "_ivc", Utilities.bytesToHex(ivChange))
			editor.putString(fileKey + "_key", Utilities.bytesToHex(key))
		}

		editor.commit()
	}

	private fun calcTotalPartsCount() {
		totalPartsCount = if (uploadFirstPartLater) {
			if (isBigFile) {
				1 + (((totalFileSize - uploadChunkSize) + uploadChunkSize - 1) / uploadChunkSize).toInt()
			}
			else {
				1 + (((totalFileSize - 1024) + uploadChunkSize - 1) / uploadChunkSize).toInt()
			}
		}
		else {
			((totalFileSize + uploadChunkSize - 1) / uploadChunkSize).toInt()
		}
	}

	fun setForceSmallFile() {
		forceSmallFile = true
	}

	private fun startUploadRequest() {
		if (state != 1) {
			return
		}

		val finalRequest: TLObject
		val currentRequestPartNum: Int
		val currentRequestBytes: Int
		val currentRequestIv: ByteArray?

		try {
			started = true

			if (stream == null) {
				val cacheFile = File(uploadingFilePath)

				if (AndroidUtilities.isInternalUri(Uri.fromFile(cacheFile))) {
					throw Exception("trying to upload internal file")
				}

				stream = RandomAccessFile(cacheFile, "r")

				var isInternalFile = false

				try {
					@SuppressLint("DiscouragedPrivateApi") val getInt = FileDescriptor::class.java.getDeclaredMethod("getInt$")
					val fdint = getInt.invoke(stream!!.fd) as Int

					if (AndroidUtilities.isInternalUri(fdint)) {
						isInternalFile = true
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}

				if (isInternalFile) {
					throw Exception("trying to upload internal file")
				}

				totalFileSize = if (estimatedSize != 0L) {
					estimatedSize
				}
				else {
					cacheFile.length()
				}

				if (!forceSmallFile && totalFileSize > 10 * 1024 * 1024) {
					isBigFile = true
				}

				var maxUploadParts = MessagesController.getInstance(currentAccount).uploadMaxFileParts.toLong()

				if (AccountInstance.getInstance(currentAccount).userConfig.isPremium && totalFileSize > FileLoader.DEFAULT_MAX_FILE_SIZE) {
					maxUploadParts = MessagesController.getInstance(currentAccount).uploadMaxFilePartsPremium.toLong()
				}

				uploadChunkSize = max((if (slowNetwork) MIN_UPLOAD_CHUNK_SLOW_NETWORK_SIZE else MIN_UPLOAD_CHUNK_SIZE).toDouble(), ((totalFileSize + 1024L * maxUploadParts - 1) / (1024L * maxUploadParts)).toDouble()).toInt()

				if (1024 % uploadChunkSize != 0) {
					var chunkSize = 64

					while (uploadChunkSize > chunkSize) {
						chunkSize *= 2
					}

					uploadChunkSize = chunkSize
				}

				maxRequestsCount = max(1.0, ((if (slowNetwork) MAX_UPLOADING_SLOW_NETWORK_KBYTES else MAX_UPLOADING_KBYTES) / uploadChunkSize).toDouble()).toInt()

				if (isEncrypted) {
					freeRequestIvs = ArrayList(maxRequestsCount)

					for (a in 0 until maxRequestsCount) {
						freeRequestIvs?.add(ByteArray(32))
					}
				}

				uploadChunkSize *= 1024

				calcTotalPartsCount()

				readBuffer = ByteArray(uploadChunkSize)

				fileKey = Utilities.MD5(uploadingFilePath + (if (isEncrypted) "enc" else ""))

				val fileSize = preferences?.getLong(fileKey + "_size", 0) ?: 0L

				uploadStartTime = (System.currentTimeMillis() / 1000).toInt()

				var rewrite = false

				if (!uploadFirstPartLater && !nextPartFirst && estimatedSize == 0L && fileSize == totalFileSize) {
					currentFileId = preferences?.getLong(fileKey + "_id", 0) ?: 0L

					var date = preferences?.getInt(fileKey + "_time", 0) ?: 0
					val uploadedSize = preferences?.getLong(fileKey + "_uploaded", 0) ?: 0L

					if (isEncrypted) {
						val ivString = preferences?.getString(fileKey + "_iv", null)
						val keyString = preferences?.getString(fileKey + "_key", null)

						if (ivString != null && keyString != null) {
							key = Utilities.hexToBytes(keyString)
							iv = Utilities.hexToBytes(ivString)

							if (key?.size == 32 && iv?.size == 32) {
								ivChange = ByteArray(32)
								System.arraycopy(iv!!, 0, ivChange!!, 0, 32)
							}
							else {
								rewrite = true
							}
						}
						else {
							rewrite = true
						}
					}

					if (!rewrite && date != 0) {
						if (isBigFile && date < uploadStartTime - 60 * 60 * 24) {
							date = 0
						}
						else if (!isBigFile && date < uploadStartTime - 60 * 60 * 1.5f) {
							date = 0
						}

						if (date != 0) {
							if (uploadedSize > 0) {
								readBytesCount = uploadedSize
								currentPartNum = (uploadedSize / uploadChunkSize).toInt()

								if (!isBigFile) {
									for (b in 0 until readBytesCount / uploadChunkSize) {
										val bytesRead = stream!!.read(readBuffer)
										var toAdd = 0

										if (isEncrypted && bytesRead % 16 != 0) {
											toAdd += 16 - bytesRead % 16
										}

										val sendBuffer = NativeByteBuffer(bytesRead + toAdd)

										if (bytesRead != uploadChunkSize || totalPartsCount == currentPartNum + 1) {
											isLastPart = true
										}

										sendBuffer.writeBytes(readBuffer, 0, bytesRead)

										if (isEncrypted) {
											for (a in 0 until toAdd) {
												sendBuffer.writeByte(0)
											}

											Utilities.aesIgeEncryption(sendBuffer.buffer, key, ivChange, true, true, 0, bytesRead + toAdd)
										}

										sendBuffer.reuse()
									}
								}
								else {
									stream?.seek(uploadedSize)

									if (isEncrypted) {
										val ivcString = preferences?.getString(fileKey + "_ivc", null)

										if (ivcString != null) {
											ivChange = Utilities.hexToBytes(ivcString)

											if (ivChange?.size != 32) {
												rewrite = true
												readBytesCount = 0
												currentPartNum = 0
											}
										}
										else {
											rewrite = true
											readBytesCount = 0
											currentPartNum = 0
										}
									}
								}
							}
							else {
								rewrite = true
							}
						}
					}
					else {
						rewrite = true
					}
				}
				else {
					rewrite = true
				}

				if (rewrite) {
					if (isEncrypted) {
						iv = ByteArray(32)
						key = ByteArray(32)
						ivChange = ByteArray(32)

						Utilities.random.nextBytes(iv)
						Utilities.random.nextBytes(key)

						System.arraycopy(iv!!, 0, ivChange!!, 0, 32)
					}

					currentFileId = Utilities.random.nextLong()

					if (!nextPartFirst && !uploadFirstPartLater && estimatedSize == 0L) {
						storeFileUploadInfo()
					}
				}

				if (isEncrypted) {
					try {
						val md = MessageDigest.getInstance("MD5")
						val arr = ByteArray(64)

						System.arraycopy(key!!, 0, arr, 0, 32)
						System.arraycopy(iv!!, 0, arr, 32, 32)

						val digest = md.digest(arr)

						for (a in 0..3) {
							fingerprint = fingerprint or (((digest[a].toInt() xor digest[a + 4].toInt()) and 0xFF) shl (a * 8))
						}
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}

				uploadedBytesCount = readBytesCount
				lastSavedPartNum = currentPartNum

				if (uploadFirstPartLater) {
					if (isBigFile) {
						stream?.seek(uploadChunkSize.toLong())
						readBytesCount = uploadChunkSize.toLong()
					}
					else {
						stream?.seek(1024)
						readBytesCount = 1024
					}

					currentPartNum = 1
				}
			}

			if (estimatedSize != 0L) {
				if (readBytesCount + uploadChunkSize > availableSize) {
					return
				}
			}

			if (nextPartFirst) {
				stream?.seek(0)

				currentRequestBytes = if (isBigFile) {
					stream?.read(readBuffer)
				}
				else {
					stream?.read(readBuffer, 0, 1024)
				} ?: -1

				currentPartNum = 0
			}
			else {
				currentRequestBytes = stream?.read(readBuffer) ?: -1
			}

			if (currentRequestBytes == -1) {
				return
			}

			var toAdd = 0

			if (isEncrypted && currentRequestBytes % 16 != 0) {
				toAdd += 16 - currentRequestBytes % 16
			}

			val sendBuffer = NativeByteBuffer(currentRequestBytes + toAdd)

			if (nextPartFirst || currentRequestBytes != uploadChunkSize || estimatedSize == 0L && totalPartsCount == currentPartNum + 1) {
				if (uploadFirstPartLater) {
					nextPartFirst = true
					uploadFirstPartLater = false
				}
				else {
					isLastPart = true
				}
			}

			sendBuffer.writeBytes(readBuffer, 0, currentRequestBytes)

			if (isEncrypted) {
				for (a in 0 until toAdd) {
					sendBuffer.writeByte(0)
				}

				Utilities.aesIgeEncryption(sendBuffer.buffer, key, ivChange, true, true, 0, currentRequestBytes + toAdd)

				currentRequestIv = freeRequestIvs!![0]

				System.arraycopy(ivChange!!, 0, currentRequestIv, 0, 32)

				freeRequestIvs?.removeAt(0)
			}
			else {
				currentRequestIv = null
			}

			if (isBigFile) {
				val req = TL_upload_saveBigFilePart()

				currentRequestPartNum = currentPartNum

				req.file_part = currentRequestPartNum
				req.file_id = currentFileId

				if (estimatedSize != 0L) {
					req.file_total_parts = -1
				}
				else {
					req.file_total_parts = totalPartsCount
				}

				req.bytes = sendBuffer

				finalRequest = req
			}
			else {
				val req = TL_upload_saveFilePart()

				currentRequestPartNum = currentPartNum

				req.file_part = currentRequestPartNum
				req.file_id = currentFileId
				req.bytes = sendBuffer

				finalRequest = req
			}

			if (isLastPart && nextPartFirst) {
				nextPartFirst = false
				currentPartNum = totalPartsCount - 1

				stream?.seek(totalFileSize)
			}

			readBytesCount += currentRequestBytes.toLong()
		}
		catch (e: Exception) {
			FileLog.e(e)
			state = 4
			delegate?.didFailedUploadingFile(this)
			cleanup()
			return
		}

		currentPartNum++
		currentUploadRequestsCount++

		val requestNumFinal = requestNum++
		val currentRequestBytesOffset = (currentRequestPartNum + currentRequestBytes).toLong()
		val requestSize = finalRequest.objectSize + 4
		val currentOperationGuid = operationGuid

		val connectionType = if (slowNetwork) {
			ConnectionsManager.ConnectionTypeUpload
		}
		else {
			ConnectionsManager.ConnectionTypeUpload or ((requestNumFinal % 4) shl 16)
		}

		val requestToken = ConnectionsManager.getInstance(currentAccount).sendRequest(finalRequest, { response, _ ->
			if (currentOperationGuid != operationGuid) {
				return@sendRequest
			}

			val networkType = response?.networkType ?: ApplicationLoader.currentNetworkType

			when (currentType) {
				ConnectionsManager.FileTypeAudio -> {
					StatsController.getInstance(currentAccount).incrementSentBytesCount(networkType, StatsController.TYPE_AUDIOS, requestSize.toLong())
				}

				ConnectionsManager.FileTypeVideo -> {
					StatsController.getInstance(currentAccount).incrementSentBytesCount(networkType, StatsController.TYPE_VIDEOS, requestSize.toLong())
				}

				ConnectionsManager.FileTypePhoto -> {
					StatsController.getInstance(currentAccount).incrementSentBytesCount(networkType, StatsController.TYPE_PHOTOS, requestSize.toLong())
				}

				ConnectionsManager.FileTypeFile -> {
					StatsController.getInstance(currentAccount).incrementSentBytesCount(networkType, StatsController.TYPE_FILES, requestSize.toLong())
				}
			}

			if (currentRequestIv != null) {
				freeRequestIvs?.add(currentRequestIv)
			}

			requestTokens.delete(requestNumFinal)

			if (response is TL_boolTrue) {
				if (state != 1) {
					return@sendRequest
				}

				uploadedBytesCount += currentRequestBytes.toLong()

				val size = if (estimatedSize != 0L) {
					max(availableSize.toDouble(), estimatedSize.toDouble()).toLong()
				}
				else {
					totalFileSize
				}

				delegate?.didChangedUploadProgress(this@FileUploadOperation, uploadedBytesCount, size)

				currentUploadRequestsCount--

				if (isLastPart && currentUploadRequestsCount == 0 && state == 1) {
					state = 3

					if (key == null) {
						val result: InputFile

						if (isBigFile) {
							result = TL_inputFileBig()
						}
						else {
							result = TL_inputFile()
							result.md5_checksum = ""
						}

						result.parts = currentPartNum
						result.id = currentFileId
						result.name = uploadingFilePath.substring(uploadingFilePath.lastIndexOf("/") + 1)

						delegate?.didFinishUploadingFile(this@FileUploadOperation, result, null, null, null)

						cleanup()
					}
					else {
						val result: InputEncryptedFile

						if (isBigFile) {
							result = TL_inputEncryptedFileBigUploaded()
						}
						else {
							result = TL_inputEncryptedFileUploaded()
							result.md5_checksum = ""
						}

						result.parts = currentPartNum
						result.id = currentFileId
						result.key_fingerprint = fingerprint

						delegate?.didFinishUploadingFile(this@FileUploadOperation, null, result, key, iv)

						cleanup()
					}

					when (currentType) {
						ConnectionsManager.FileTypeAudio -> {
							StatsController.getInstance(currentAccount).incrementSentItemsCount(ApplicationLoader.currentNetworkType, StatsController.TYPE_AUDIOS, 1)
						}

						ConnectionsManager.FileTypeVideo -> {
							StatsController.getInstance(currentAccount).incrementSentItemsCount(ApplicationLoader.currentNetworkType, StatsController.TYPE_VIDEOS, 1)
						}

						ConnectionsManager.FileTypePhoto -> {
							StatsController.getInstance(currentAccount).incrementSentItemsCount(ApplicationLoader.currentNetworkType, StatsController.TYPE_PHOTOS, 1)
						}

						ConnectionsManager.FileTypeFile -> {
							StatsController.getInstance(currentAccount).incrementSentItemsCount(ApplicationLoader.currentNetworkType, StatsController.TYPE_FILES, 1)
						}
					}
				}
				else if (currentUploadRequestsCount < maxRequestsCount) {
					if (estimatedSize == 0L && !uploadFirstPartLater && !nextPartFirst) {
						if (saveInfoTimes >= 4) {
							saveInfoTimes = 0
						}

						if (currentRequestPartNum == lastSavedPartNum) {
							lastSavedPartNum++

							var offsetToSave = currentRequestBytesOffset
							var ivToSave = currentRequestIv
							var result: UploadCachedResult?

							while ((cachedResults[lastSavedPartNum].also { result = it }) != null) {
								offsetToSave = result!!.bytesOffset
								ivToSave = result!!.iv
								cachedResults.remove(lastSavedPartNum)
								lastSavedPartNum++
							}

							if (isBigFile && offsetToSave % (1024 * 1024) == 0L || !isBigFile && saveInfoTimes == 0) {
								val editor = preferences?.edit()
								editor?.putLong(fileKey + "_uploaded", offsetToSave)

								if (isEncrypted) {
									editor?.putString(fileKey + "_ivc", Utilities.bytesToHex(ivToSave))
								}

								editor?.commit()
							}
						}
						else {
							val result = UploadCachedResult()
							result.bytesOffset = currentRequestBytesOffset

							if (currentRequestIv != null) {
								result.iv = ByteArray(32)
								System.arraycopy(currentRequestIv, 0, result.iv!!, 0, 32)
							}

							cachedResults.put(currentRequestPartNum, result)
						}

						saveInfoTimes++
					}

					startUploadRequest()
				}
			}
			else {
				state = 4
				delegate?.didFailedUploadingFile(this@FileUploadOperation)
				cleanup()
			}
		}, null, {
			Utilities.stageQueue.postRunnable {
				if (currentUploadRequestsCount < maxRequestsCount) {
					startUploadRequest()
				}
			}
		}, if (forceSmallFile) ConnectionsManager.RequestFlagCanCompress else 0, ConnectionsManager.DEFAULT_DATACENTER_ID, connectionType, true)

		requestTokens.put(requestNumFinal, requestToken)
	}

	interface FileUploadOperationDelegate {
		fun didFinishUploadingFile(operation: FileUploadOperation, inputFile: InputFile?, inputEncryptedFile: InputEncryptedFile?, key: ByteArray?, iv: ByteArray?)
		fun didFailedUploadingFile(operation: FileUploadOperation)
		fun didChangedUploadProgress(operation: FileUploadOperation, uploadedSize: Long, totalSize: Long)
	}

	private class UploadCachedResult {
		var bytesOffset = 0L
		var iv: ByteArray? = null
	}

	companion object {
		private const val MIN_UPLOAD_CHUNK_SIZE = 128
		private const val MIN_UPLOAD_CHUNK_SLOW_NETWORK_SIZE = 32
		private const val INITIAL_REQUESTS_COUNT = 8
		private const val INITIAL_REQUESTS_SLOW_NETWORK_COUNT = 1
		private const val MAX_UPLOADING_KBYTES = 1024 * 2
		private const val MAX_UPLOADING_SLOW_NETWORK_KBYTES = 32
	}
}
