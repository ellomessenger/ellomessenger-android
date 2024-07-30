/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import org.telegram.messenger.FileLog
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaController.SavedFilterState
import org.telegram.messenger.MediaController.VideoConvertorListener
import org.telegram.messenger.Utilities
import org.telegram.messenger.VideoEditedInfo.MediaEntity
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MediaCodecVideoConvertor {
	private var mediaMuxer: MP4Builder? = null
	private var extractor: MediaExtractor? = null
	private var callback: VideoConvertorListener? = null

	var lastFrameTimestamp: Long = 0
		private set

	fun convertVideo(videoPath: String, cacheFile: File, rotationValue: Int, isSecret: Boolean, originalWidth: Int, originalHeight: Int, resultWidth: Int, resultHeight: Int, framerate: Int, bitrate: Int, originalBitrate: Int, startTime: Long, endTime: Long, avatarStartTime: Long, needCompress: Boolean, duration: Long, savedFilterState: SavedFilterState?, paintPath: String?, mediaEntities: ArrayList<MediaEntity>?, isPhoto: Boolean, cropState: MediaController.CropState?, isRound: Boolean, callback: VideoConvertorListener?): Boolean {
		this.callback = callback
		return convertVideoInternal(videoPath, cacheFile, rotationValue, isSecret, originalWidth, originalHeight, resultWidth, resultHeight, framerate, bitrate, originalBitrate, startTime, endTime, avatarStartTime, duration, needCompress, false, savedFilterState, paintPath, mediaEntities, isPhoto, cropState, isRound)
	}

	private fun convertVideoInternal(videoPath: String, cacheFile: File, rotationValue: Int, isSecret: Boolean, originalWidth: Int, originalHeight: Int, resultWidth: Int, resultHeight: Int, framerate: Int, bitrate: Int, originalBitrate: Int, startTime: Long, endTime: Long, avatarStartTime: Long, duration: Long, needCompress: Boolean, increaseTimeout: Boolean, savedFilterState: SavedFilterState?, paintPath: String?, mediaEntities: ArrayList<MediaEntity>?, isPhoto: Boolean, cropState: MediaController.CropState?, isRound: Boolean): Boolean {
		@Suppress("NAME_SHADOWING") var resultWidth = resultWidth
		@Suppress("NAME_SHADOWING") var resultHeight = resultHeight
		@Suppress("NAME_SHADOWING") var bitrate = bitrate
		@Suppress("NAME_SHADOWING") var endTime = endTime
		@Suppress("NAME_SHADOWING") var avatarStartTime = avatarStartTime
		val time = System.currentTimeMillis()
		var error = false
		var repeatWithIncreasedTimeout = false
		var videoTrackIndex = -5

		try {
			val info = MediaCodec.BufferInfo()

			val movie = Mp4Movie()
			movie.cacheFile = cacheFile
			movie.setRotation(0)
			movie.setSize(resultWidth, resultHeight)

			val mediaMuxer = MP4Builder().createMovie(movie, isSecret).also {
				this.mediaMuxer = it
			}

			var currentPts: Long = 0
			val durationS = duration / 1000f
			var encoder: MediaCodec? = null
			var inputSurface: InputSurface? = null
			var outputSurface: OutputSurface? = null
			var prependHeaderSize = 0

			lastFrameTimestamp = duration * 1000

			checkConversionCanceled()

			if (isPhoto) {
				try {
					var outputDone = false
					var decoderDone = false
					var framesCount = 0

					if (avatarStartTime >= 0) {
						bitrate = if (durationS <= 2000) {
							2600000
						}
						else if (durationS <= 5000) {
							2200000
						}
						else {
							1560000
						}
					}
					else if (bitrate <= 0) {
						bitrate = 921600
					}

					if (resultWidth % 16 != 0) {
						FileLog.d("changing width from " + resultWidth + " to " + Math.round(resultWidth / 16.0f) * 16)
						resultWidth = Math.round(resultWidth / 16.0f) * 16
					}

					if (resultHeight % 16 != 0) {
						FileLog.d("changing height from " + resultHeight + " to " + Math.round(resultHeight / 16.0f) * 16)
						resultHeight = Math.round(resultHeight / 16.0f) * 16
					}

					FileLog.d("create photo encoder $resultWidth $resultHeight duration = $duration")

					val outputFormat = MediaFormat.createVideoFormat(MediaController.VIDEO_MIME_TYPE, resultWidth, resultHeight)
					outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
					outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
					outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
					outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

					encoder = MediaCodec.createEncoderByType(MediaController.VIDEO_MIME_TYPE)
					encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

					inputSurface = InputSurface(encoder.createInputSurface())
					inputSurface.makeCurrent()

					encoder.start()

					outputSurface = OutputSurface(savedFilterState, videoPath, paintPath, mediaEntities, null, resultWidth, resultHeight, originalWidth, originalHeight, rotationValue, framerate.toFloat(), true)

					var firstEncode = true

					checkConversionCanceled()

					while (!outputDone) {
						checkConversionCanceled()

						var decoderOutputAvailable = !decoderDone
						var encoderOutputAvailable = true

						while (decoderOutputAvailable || encoderOutputAvailable) {
							checkConversionCanceled()

							val encoderStatus = encoder.dequeueOutputBuffer(info, (if (increaseTimeout) MEDIACODEC_TIMEOUT_INCREASED else MEDIACODEC_TIMEOUT_DEFAULT).toLong())

							if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
								encoderOutputAvailable = false
							}
							else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
								// unused
							}
							else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
								val newFormat = encoder.outputFormat

								FileLog.d("photo encoder new format $newFormat")

								if (videoTrackIndex == -5) {
									videoTrackIndex = mediaMuxer.addTrack(newFormat, false)

									if (newFormat.containsKey(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) && newFormat.getInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) == 1) {
										val spsBuff = newFormat.getByteBuffer("csd-0")
										val ppsBuff = newFormat.getByteBuffer("csd-1")

										prependHeaderSize = (spsBuff?.limit() ?: 0) + (ppsBuff?.limit() ?: 0)
									}
								}
							}
							else if (encoderStatus < 0) {
								throw RuntimeException("unexpected result from encoder.dequeueOutputBuffer: $encoderStatus")
							}
							else {
								val encodedData = encoder.getOutputBuffer(encoderStatus) ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")

								if (info.size > 1) {
									if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
										if (prependHeaderSize != 0 && info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
											info.offset += prependHeaderSize
											info.size -= prependHeaderSize
										}

										if (firstEncode && info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
											if (info.size > 100) {
												encodedData.position(info.offset)

												val temp = ByteArray(100)

												encodedData.get(temp)

												var nalCount = 0

												for (a in 0 until temp.size - 4) {
													if (temp[a].toInt() == 0 && temp[a + 1].toInt() == 0 && temp[a + 2].toInt() == 0 && temp[a + 3].toInt() == 1) {
														nalCount++

														if (nalCount > 1) {
															info.offset += a
															info.size -= a
															break
														}
													}
												}
											}

											firstEncode = false
										}

										val availableSize = mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info, true)

										if (availableSize != 0L) {
											callback?.didWriteData(availableSize, currentPts / 1000f / durationS)
										}
									}
									else if (videoTrackIndex == -5) {
										val csd = ByteArray(info.size)

										encodedData.limit(info.offset + info.size)
										encodedData.position(info.offset)
										encodedData.get(csd)

										var sps: ByteBuffer? = null
										var pps: ByteBuffer? = null

										for (a in info.size - 1 downTo 0) {
											if (a > 3) {
												if (csd[a].toInt() == 1 && csd[a - 1].toInt() == 0 && csd[a - 2].toInt() == 0 && csd[a - 3].toInt() == 0) {
													sps = ByteBuffer.allocate(a - 3)
													pps = ByteBuffer.allocate(info.size - (a - 3))
													sps.put(csd, 0, a - 3).position(0)
													pps.put(csd, a - 3, info.size - (a - 3)).position(0)
													break
												}
											}
											else {
												break
											}
										}

										val newFormat = MediaFormat.createVideoFormat(MediaController.VIDEO_MIME_TYPE, resultWidth, resultHeight)

										if (sps != null && pps != null) {
											newFormat.setByteBuffer("csd-0", sps)
											newFormat.setByteBuffer("csd-1", pps)
										}

										videoTrackIndex = mediaMuxer.addTrack(newFormat, false)
									}
								}

								outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

								encoder.releaseOutputBuffer(encoderStatus, false)
							}

							if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
								continue
							}

							if (!decoderDone) {
								outputSurface.drawImage()

								val presentationTime = (framesCount / 30.0f * 1000L * 1000L * 1000L).toLong()
								inputSurface.setPresentationTime(presentationTime)
								inputSurface.swapBuffers()

								framesCount++

								if (framesCount >= duration / 1000.0f * 30) {
									decoderDone = true
									decoderOutputAvailable = false

									encoder.signalEndOfInputStream()
								}
							}
						}
					}
				}
				catch (e: Exception) {
					// in some case encoder.dequeueOutputBuffer return IllegalStateException
					// stable reproduced on xiaomi
					// fix it by increasing timeout
					if (e is IllegalStateException && !increaseTimeout) {
						repeatWithIncreasedTimeout = true
					}

					FileLog.e("bitrate: " + bitrate + " framerate: " + framerate + " size: " + resultHeight + "x" + resultWidth)
					FileLog.e(e)

					error = true
				}

				outputSurface?.release()
				inputSurface?.release()

				if (encoder != null) {
					encoder.stop()
					encoder.release()
				}

				checkConversionCanceled()
			}
			else {
				val extractor = MediaExtractor().also {
					this.extractor = it
				}

				extractor.setDataSource(videoPath)

				val videoIndex = MediaController.findTrack(extractor, false)
				var audioIndex = if (bitrate != -1) MediaController.findTrack(extractor, true) else -1
				var needConvertVideo = false

				if (videoIndex >= 0 && extractor.getTrackFormat(videoIndex).getString(MediaFormat.KEY_MIME) != MediaController.VIDEO_MIME_TYPE) {
					needConvertVideo = true
				}

				if (needCompress || needConvertVideo) {
					var audioRecoder: AudioRecoder? = null
					var audioBuffer: ByteBuffer? = null
					var copyAudioBuffer = true
					var lastFramePts: Long = -1

					if (videoIndex >= 0) {
						var decoder: MediaCodec? = null

						try {
							var videoTime: Long = -1
							var outputDone = false
							var inputDone = false
							var decoderDone = false
							var audioTrackIndex = -5
							var additionalPresentationTime: Long = 0
							var minPresentationTime = Int.MIN_VALUE.toLong()
							val frameDelta = (1000 / framerate * 1000).toLong()

							val frameDeltaFroSkipFrames = if (framerate < 30) {
								(1000 / (framerate + 5) * 1000).toLong()
							}
							else {
								(1000 / (framerate + 1) * 1000).toLong()
							}

							extractor.selectTrack(videoIndex)

							val videoFormat = extractor.getTrackFormat(videoIndex)

							if (avatarStartTime >= 0) {
								bitrate = if (durationS <= 2000) {
									2600000
								}
								else if (durationS <= 5000) {
									2200000
								}
								else {
									1560000
								}

								avatarStartTime = 0
							}
							else if (bitrate <= 0) {
								bitrate = 921600
							}

							if (originalBitrate > 0) {
								bitrate = min(originalBitrate.toDouble(), bitrate.toDouble()).toInt()
							}

							var trueStartTime: Long // = startTime < 0 ? 0 : startTime;

							if (avatarStartTime >= 0 /* && trueStartTime == avatarStartTime*/) {
								avatarStartTime = -1
							}

							if (avatarStartTime >= 0) {
								extractor.seekTo(avatarStartTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
							}
							else if (startTime > 0) {
								extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
							}
							else {
								extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
							}

							val w: Int
							val h: Int

							if (cropState != null) {
								if (rotationValue == 90 || rotationValue == 270) {
									w = cropState.transformHeight
									h = cropState.transformWidth
								}
								else {
									w = cropState.transformWidth
									h = cropState.transformHeight
								}
							}
							else {
								w = resultWidth
								h = resultHeight
							}

							FileLog.d("create encoder with w = $w h = $h")

							val outputFormat = MediaFormat.createVideoFormat(MediaController.VIDEO_MIME_TYPE, w, h)
							outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
							outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
							outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate)
							outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)

							encoder = MediaCodec.createEncoderByType(MediaController.VIDEO_MIME_TYPE)
							encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

							inputSurface = InputSurface(encoder.createInputSurface())
							inputSurface.makeCurrent()

							encoder.start()

							decoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME)!!)

							outputSurface = OutputSurface(savedFilterState, null, paintPath, mediaEntities, cropState, resultWidth, resultHeight, originalWidth, originalHeight, rotationValue, framerate.toFloat(), false)

							if (!isRound && (max(resultHeight.toDouble(), resultHeight.toDouble()) / max(originalHeight.toDouble(), originalWidth.toDouble())).toFloat() < 0.9f) {
								outputSurface.changeFragmentShader(createFragmentShader(originalWidth, originalHeight, resultWidth, resultHeight, true), createFragmentShader(originalWidth, originalHeight, resultWidth, resultHeight, false))
							}

							decoder.configure(videoFormat, outputSurface.surface, null, 0)
							decoder.start()

							var maxBufferSize = 0

							if (audioIndex >= 0) {
								val audioFormat = extractor.getTrackFormat(audioIndex)

								copyAudioBuffer = audioFormat.getString(MediaFormat.KEY_MIME) == MediaController.AUIDO_MIME_TYPE || audioFormat.getString(MediaFormat.KEY_MIME) == "audio/mpeg"

								if (audioFormat.getString(MediaFormat.KEY_MIME) == "audio/unknown") {
									audioIndex = -1
								}

								if (audioIndex >= 0) {
									if (copyAudioBuffer) {
										audioTrackIndex = mediaMuxer.addTrack(audioFormat, true)

										extractor.selectTrack(audioIndex)

										try {
											maxBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
										}
										catch (e: Exception) {
											FileLog.e(e) //s20 ultra exception
										}

										if (maxBufferSize <= 0) {
											maxBufferSize = 64 * 1024
										}

										audioBuffer = ByteBuffer.allocateDirect(maxBufferSize)

										if (startTime > 0) {
											extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
										}
										else {
											extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
										}
									}
									else {
										val audioExtractor = MediaExtractor()
										audioExtractor.setDataSource(videoPath)
										audioExtractor.selectTrack(audioIndex)

										if (startTime > 0) {
											audioExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
										}
										else {
											audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
										}

										audioRecoder = AudioRecoder(audioFormat, audioExtractor, audioIndex)
										audioRecoder.startTime = startTime
										audioRecoder.endTime = endTime

										audioTrackIndex = mediaMuxer.addTrack(audioRecoder.format, true)
									}
								}
							}

							var audioEncoderDone = audioIndex < 0
							var firstEncode = true

							checkConversionCanceled()

							while (!outputDone || !copyAudioBuffer && !audioEncoderDone) {
								checkConversionCanceled()

								if (!copyAudioBuffer && audioRecoder != null) {
									audioEncoderDone = audioRecoder.step(mediaMuxer, audioTrackIndex)
								}

								if (!inputDone) {
									var eof = false
									val index = extractor.sampleTrackIndex

									if (index == videoIndex) {
										val inputBufIndex = decoder.dequeueInputBuffer(MEDIACODEC_TIMEOUT_DEFAULT.toLong())

										if (inputBufIndex >= 0) {
											val inputBuf = decoder.getInputBuffer(inputBufIndex)

											val chunkSize = if (inputBuf != null) {
												extractor.readSampleData(inputBuf, 0)
											}
											else {
												-1
											}

											if (chunkSize < 0) {
												decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
												inputDone = true
											}
											else {
												decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.sampleTime, 0)
												extractor.advance()
											}
										}
									}
									else if (copyAudioBuffer && audioIndex != -1 && index == audioIndex) {
										if (Build.VERSION.SDK_INT >= 28) {
											val size = extractor.sampleSize

											if (size > maxBufferSize) {
												maxBufferSize = (size + 1024).toInt()
												audioBuffer = ByteBuffer.allocateDirect(maxBufferSize)
											}
										}

										info.size = if (audioBuffer != null) {
											extractor.readSampleData(audioBuffer, 0)
										}
										else {
											-1
										}

										if (info.size >= 0) {
											info.presentationTimeUs = extractor.sampleTime
											extractor.advance()
										}
										else {
											info.size = 0
											inputDone = true
										}

										if (info.size > 0 && (endTime < 0 || info.presentationTimeUs < endTime)) {
											info.offset = 0
											info.flags = extractor.sampleFlags

											val availableSize = mediaMuxer.writeSampleData(audioTrackIndex, audioBuffer, info, false)

											if (availableSize != 0L) {
												if (callback != null) {
													if (info.presentationTimeUs - startTime > currentPts) {
														currentPts = info.presentationTimeUs - startTime
													}

													callback?.didWriteData(availableSize, currentPts / 1000f / durationS)
												}
											}
										}
									}
									else if (index == -1) {
										eof = true
									}

									if (eof) {
										val inputBufIndex = decoder.dequeueInputBuffer(MEDIACODEC_TIMEOUT_DEFAULT.toLong())

										if (inputBufIndex >= 0) {
											decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
											inputDone = true
										}
									}
								}

								var decoderOutputAvailable = !decoderDone
								var encoderOutputAvailable = true

								while (decoderOutputAvailable || encoderOutputAvailable) {
									checkConversionCanceled()

									val encoderStatus = encoder.dequeueOutputBuffer(info, (if (increaseTimeout) MEDIACODEC_TIMEOUT_INCREASED else MEDIACODEC_TIMEOUT_DEFAULT).toLong())

									if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
										encoderOutputAvailable = false
									}
									else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
										// unused
									}
									else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
										val newFormat = encoder.outputFormat

										if (videoTrackIndex == -5) {
											videoTrackIndex = mediaMuxer.addTrack(newFormat, false)

											if (newFormat.containsKey(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) && newFormat.getInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) == 1) {
												val spsBuff = newFormat.getByteBuffer("csd-0")
												val ppsBuff = newFormat.getByteBuffer("csd-1")


												prependHeaderSize = (spsBuff?.limit() ?: 0) + (ppsBuff?.limit() ?: 0)
											}
										}
									}
									else if (encoderStatus < 0) {
										throw RuntimeException("unexpected result from encoder.dequeueOutputBuffer: $encoderStatus")
									}
									else {
										val encodedData = encoder.getOutputBuffer(encoderStatus) ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")

										if (info.size > 1) {
											if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
												if (prependHeaderSize != 0 && info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
													info.offset += prependHeaderSize
													info.size -= prependHeaderSize
												}

												if (firstEncode && info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
													if (info.size > 100) {
														encodedData.position(info.offset)

														val temp = ByteArray(100)

														encodedData.get(temp)

														var nalCount = 0

														for (a in 0 until temp.size - 4) {
															if (temp[a].toInt() == 0 && temp[a + 1].toInt() == 0 && temp[a + 2].toInt() == 0 && temp[a + 3].toInt() == 1) {
																nalCount++

																if (nalCount > 1) {
																	info.offset += a
																	info.size -= a
																	break
																}
															}
														}
													}

													firstEncode = false
												}

												val availableSize = mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info, true)

												if (availableSize != 0L) {
													if (callback != null) {
														if (info.presentationTimeUs - startTime > currentPts) {
															currentPts = info.presentationTimeUs - startTime
														}

														callback?.didWriteData(availableSize, currentPts / 1000f / durationS)
													}
												}
											}
											else if (videoTrackIndex == -5) {
												val csd = ByteArray(info.size)

												encodedData.limit(info.offset + info.size)
												encodedData.position(info.offset)
												encodedData.get(csd)

												var sps: ByteBuffer? = null
												var pps: ByteBuffer? = null

												for (a in info.size - 1 downTo 0) {
													if (a > 3) {
														if (csd[a].toInt() == 1 && csd[a - 1].toInt() == 0 && csd[a - 2].toInt() == 0 && csd[a - 3].toInt() == 0) {
															sps = ByteBuffer.allocate(a - 3)
															pps = ByteBuffer.allocate(info.size - (a - 3))
															sps.put(csd, 0, a - 3).position(0)
															pps.put(csd, a - 3, info.size - (a - 3)).position(0)
															break
														}
													}
													else {
														break
													}
												}

												val newFormat = MediaFormat.createVideoFormat(MediaController.VIDEO_MIME_TYPE, w, h)

												if (sps != null && pps != null) {
													newFormat.setByteBuffer("csd-0", sps)
													newFormat.setByteBuffer("csd-1", pps)
												}

												videoTrackIndex = mediaMuxer.addTrack(newFormat, false)
											}
										}

										outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

										encoder.releaseOutputBuffer(encoderStatus, false)
									}

									if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
										continue
									}

									if (!decoderDone) {
										val decoderStatus = decoder.dequeueOutputBuffer(info, MEDIACODEC_TIMEOUT_DEFAULT.toLong())

										if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
											decoderOutputAvailable = false
										}
										else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
											val newFormat = decoder.outputFormat
											FileLog.d("newFormat = $newFormat")
										}
										else if (decoderStatus < 0) {
											throw RuntimeException("unexpected result from decoder.dequeueOutputBuffer: $decoderStatus")
										}
										else {
											var doRender = info.size != 0
											val originalPresentationTime = info.presentationTimeUs

											if (endTime in 1..originalPresentationTime) {
												inputDone = true
												decoderDone = true
												doRender = false
												info.flags = info.flags or MediaCodec.BUFFER_FLAG_END_OF_STREAM
											}

											var flushed = false

											if (avatarStartTime >= 0 && info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 && abs((avatarStartTime - startTime).toDouble()) > 1000000 / framerate) {
												if (startTime > 0) {
													extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
												}
												else {
													extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
												}

												additionalPresentationTime = minPresentationTime + frameDelta
												endTime = avatarStartTime
												avatarStartTime = -1
												inputDone = false
												decoderDone = false
												doRender = false
												info.flags = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()

												decoder.flush()

												flushed = true
											}

											if (lastFramePts > 0 && info.presentationTimeUs - lastFramePts < frameDeltaFroSkipFrames && info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM == 0) {
												doRender = false
											}

											trueStartTime = if (avatarStartTime >= 0) avatarStartTime else startTime

											if (trueStartTime > 0 && videoTime == -1L) {
												if (originalPresentationTime < trueStartTime) {
													doRender = false
													FileLog.d("drop frame startTime = " + trueStartTime + " present time = " + info.presentationTimeUs)
												}
												else {
													videoTime = info.presentationTimeUs

													if (minPresentationTime != Int.MIN_VALUE.toLong()) {
														additionalPresentationTime -= videoTime
													}
												}
											}

											if (flushed) {
												videoTime = -1
											}
											else {
												if (avatarStartTime == -1L && additionalPresentationTime != 0L) {
													info.presentationTimeUs += additionalPresentationTime
												}

												decoder.releaseOutputBuffer(decoderStatus, doRender)
											}

											if (doRender) {
												lastFramePts = info.presentationTimeUs

												if (avatarStartTime >= 0) {
													minPresentationTime = max(minPresentationTime.toDouble(), info.presentationTimeUs.toDouble()).toLong()
												}

												var errorWait = false

												try {
													outputSurface.awaitNewImage()
												}
												catch (e: Exception) {
													errorWait = true
													FileLog.e(e)
												}

												if (!errorWait) {
													outputSurface.drawImage()
													inputSurface.setPresentationTime(info.presentationTimeUs * 1000)
													inputSurface.swapBuffers()
												}
											}

											if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
												decoderOutputAvailable = false
												FileLog.d("decoder stream end")
												encoder.signalEndOfInputStream()
											}
										}
									}
								}
							}
						}
						catch (e: Exception) {
							// in some case encoder.dequeueOutputBuffer return IllegalStateException
							// stable reproduced on xiaomi
							// fix it by increasing timeout
							if (e is IllegalStateException && !increaseTimeout) {
								repeatWithIncreasedTimeout = true
							}

							FileLog.e("bitrate: " + bitrate + " framerate: " + framerate + " size: " + resultHeight + "x" + resultWidth)
							FileLog.e(e)

							error = true
						}

						extractor.unselectTrack(videoIndex)

						if (decoder != null) {
							decoder.stop()
							decoder.release()
						}
					}

					outputSurface?.release()
					inputSurface?.release()

					if (encoder != null) {
						encoder.stop()
						encoder.release()
					}

					audioRecoder?.release()

					checkConversionCanceled()
				}
				else {
					readAndWriteTracks(extractor, mediaMuxer, info, startTime, endTime, duration, bitrate != -1)
				}
			}
		}
		catch (e: Throwable) {
			error = true
			FileLog.e("bitrate: " + bitrate + " framerate: " + framerate + " size: " + resultHeight + "x" + resultWidth)
			FileLog.e(e)
		}
		finally {
			extractor?.release()

			mediaMuxer?.let {
				try {
					it.finishMovie()
					lastFrameTimestamp = it.getLastFrameTimestamp(videoTrackIndex)
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}
		}

		if (repeatWithIncreasedTimeout) {
			return convertVideoInternal(videoPath, cacheFile, rotationValue, isSecret, originalWidth, originalHeight, resultWidth, resultHeight, framerate, bitrate, originalBitrate, startTime, endTime, avatarStartTime, duration, needCompress, true, savedFilterState, paintPath, mediaEntities, isPhoto, cropState, isRound)
		}

		val timeLeft = System.currentTimeMillis() - time

		FileLog.d("compression completed time=$timeLeft needCompress=$needCompress w=$resultWidth h=$resultHeight bitrate=$bitrate")

		return error
	}

	@Throws(Exception::class)
	private fun readAndWriteTracks(extractor: MediaExtractor, mediaMuxer: MP4Builder, info: MediaCodec.BufferInfo, start: Long, end: Long, duration: Long, needAudio: Boolean): Long {
		val videoTrackIndex = MediaController.findTrack(extractor, false)
		var audioTrackIndex = if (needAudio) MediaController.findTrack(extractor, true) else -1
		var muxerVideoTrackIndex = -1
		var muxerAudioTrackIndex = -1
		var inputDone = false
		var currentPts: Long = 0
		val durationS = duration / 1000f
		var maxBufferSize = 0

		if (videoTrackIndex >= 0) {
			extractor.selectTrack(videoTrackIndex)

			val trackFormat = extractor.getTrackFormat(videoTrackIndex)

			muxerVideoTrackIndex = mediaMuxer.addTrack(trackFormat, false)

			try {
				maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
			}
			catch (e: Exception) {
				FileLog.e(e) //s20 ultra exception
			}

			if (start > 0) {
				extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
			}
			else {
				extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
			}
		}

		if (audioTrackIndex >= 0) {
			extractor.selectTrack(audioTrackIndex)

			val trackFormat = extractor.getTrackFormat(audioTrackIndex)

			if (trackFormat.getString(MediaFormat.KEY_MIME) == "audio/unknown") {
				audioTrackIndex = -1
			}
			else {
				muxerAudioTrackIndex = mediaMuxer.addTrack(trackFormat, true)

				try {
					maxBufferSize = max(trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).toDouble(), maxBufferSize.toDouble()).toInt()
				}
				catch (e: Exception) {
					FileLog.e(e) //s20 ultra exception
				}

				if (start > 0) {
					extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
				}
				else {
					extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
				}
			}
		}

		if (maxBufferSize <= 0) {
			maxBufferSize = 64 * 1024
		}

		var buffer = ByteBuffer.allocateDirect(maxBufferSize)

		if (audioTrackIndex >= 0 || videoTrackIndex >= 0) {
			var startTime: Long = -1

			checkConversionCanceled()

			while (!inputDone) {
				checkConversionCanceled()

				var eof = false
				var muxerTrackIndex: Int

				if (Build.VERSION.SDK_INT >= 28) {
					val size = extractor.sampleSize

					if (size > maxBufferSize) {
						maxBufferSize = (size + 1024).toInt()
						buffer = ByteBuffer.allocateDirect(maxBufferSize)
					}
				}

				info.size = extractor.readSampleData(buffer, 0)

				val index = extractor.sampleTrackIndex

				muxerTrackIndex = when (index) {
					videoTrackIndex -> muxerVideoTrackIndex
					audioTrackIndex -> muxerAudioTrackIndex
					else -> -1
				}

				if (muxerTrackIndex != -1) {
					if (index != audioTrackIndex) {
						val array = buffer.array()
						val offset = buffer.arrayOffset()
						val len = offset + buffer.limit()
						var writeStart = -1

						for (a in offset..len - 4) {
							if (array[a].toInt() == 0 && array[a + 1].toInt() == 0 && array[a + 2].toInt() == 0 && array[a + 3].toInt() == 1 || a == len - 4) {
								if (writeStart != -1) {
									val l = a - writeStart - if (a != len - 4) 4 else 0

									array[writeStart] = (l shr 24).toByte()
									array[writeStart + 1] = (l shr 16).toByte()
									array[writeStart + 2] = (l shr 8).toByte()
									array[writeStart + 3] = l.toByte()

									writeStart = a
								}
								else {
									writeStart = a
								}
							}
						}
					}

					if (info.size >= 0) {
						info.presentationTimeUs = extractor.sampleTime
					}
					else {
						info.size = 0
						eof = true
					}

					if (info.size > 0 && !eof) {
						if (index == videoTrackIndex && start > 0 && startTime == -1L) {
							startTime = info.presentationTimeUs
						}

						if (end < 0 || info.presentationTimeUs < end) {
							info.offset = 0
							info.flags = extractor.sampleFlags

							val availableSize = mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info, false)

							if (availableSize != 0L) {
								if (callback != null) {
									if (info.presentationTimeUs - startTime > currentPts) {
										currentPts = info.presentationTimeUs - startTime
									}

									callback?.didWriteData(availableSize, currentPts / 1000f / durationS)
								}
							}
						}
						else {
							eof = true
						}
					}

					if (!eof) {
						extractor.advance()
					}
				}
				else if (index == -1) {
					eof = true
				}
				else {
					extractor.advance()
				}

				if (eof) {
					inputDone = true
				}
			}

			if (videoTrackIndex >= 0) {
				extractor.unselectTrack(videoTrackIndex)
			}
			if (audioTrackIndex >= 0) {
				extractor.unselectTrack(audioTrackIndex)
			}

			return startTime
		}

		return -1
	}

	private fun checkConversionCanceled() {
		if (callback?.checkConversionCanceled() == true) {
			throw ConversionCanceledException()
		}
	}

	class ConversionCanceledException : RuntimeException("canceled conversion")

	companion object {
		// private const val PROCESSOR_TYPE_OTHER = 0
		// private const val PROCESSOR_TYPE_QCOM = 1
		// private const val PROCESSOR_TYPE_INTEL = 2
		// private const val PROCESSOR_TYPE_MTK = 3
		// private const val PROCESSOR_TYPE_SEC = 4
		// private const val PROCESSOR_TYPE_TI = 5
		private const val MEDIACODEC_TIMEOUT_DEFAULT = 2500
		private const val MEDIACODEC_TIMEOUT_INCREASED = 22000

		private fun createFragmentShader(srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int, external: Boolean): String {
			val kernelSize = Utilities.clamp((max(srcWidth.toDouble(), srcHeight.toDouble()) / max(dstHeight.toDouble(), dstWidth.toDouble()).toFloat()).toFloat() * 0.8f, 2f, 1f)
			val kernelRadius = kernelSize.toInt()

			FileLog.d("source size " + srcWidth + "x" + srcHeight + "    dest size " + dstWidth + dstHeight + "   kernelRadius " + kernelRadius)

			return if (external) {
				"#extension GL_OES_EGL_image_external : require\nprecision mediump float;\nvarying vec2 vTextureCoord;\nconst float kernel = $kernelRadius.0;\nconst float pixelSizeX = 1.0 / $srcWidth.0;\nconst float pixelSizeY = 1.0 / $srcHeight.0;\nuniform samplerExternalOES sTexture;\nvoid main() {\nvec3 accumulation = vec3(0);\nvec3 weightsum = vec3(0);\nfor (float x = -kernel; x <= kernel; x++){\n   for (float y = -kernel; y <= kernel; y++){\n       accumulation += texture2D(sTexture, vTextureCoord + vec2(x * pixelSizeX, y * pixelSizeY)).xyz;\n       weightsum += 1.0;\n   }\n}\ngl_FragColor = vec4(accumulation / weightsum, 1.0);\n}\n"
			}
			else {
				"precision mediump float;\nvarying vec2 vTextureCoord;\nconst float kernel = $kernelRadius.0;\nconst float pixelSizeX = 1.0 / $srcHeight.0;\nconst float pixelSizeY = 1.0 / $srcWidth.0;\nuniform sampler2D sTexture;\nvoid main() {\nvec3 accumulation = vec3(0);\nvec3 weightsum = vec3(0);\nfor (float x = -kernel; x <= kernel; x++){\n   for (float y = -kernel; y <= kernel; y++){\n       accumulation += texture2D(sTexture, vTextureCoord + vec2(x * pixelSizeX, y * pixelSizeY)).xyz;\n       weightsum += 1.0;\n   }\n}\ngl_FragColor = vec4(accumulation / weightsum, 1.0);\n}\n"
			}
		}
	}
}
