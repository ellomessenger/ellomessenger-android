/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger.messageobject

import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.tgnet.TLRPC.TL_messageMediaGame
import org.telegram.tgnet.TLRPC.TL_messageMediaInvoice
import org.telegram.tgnet.TLRPC.TL_peerUser
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class GroupedMessages {
	@JvmField
	val transitionParams = TransitionParams()

	@JvmField
	var groupId: Long = 0

	@JvmField
	var hasSibling = false

	@JvmField
	var hasCaption = false

	@JvmField
	var messages = ArrayList<MessageObject>()

	@JvmField
	var posArray = ArrayList<GroupedMessagePosition>()

	@JvmField
	var positions = HashMap<MessageObject, GroupedMessagePosition>()

	@JvmField
	var isDocuments = false

	private var maxSizeWidth = DEFAULT_MAX_WIDTH

	private fun multiHeight(array: FloatArray, start: Int, end: Int): Float {
		var sum = 0f

		for (a in start until end) {
			sum += array[a]
		}

		return maxSizeWidth / sum
	}

	fun calculate() {
		posArray.clear()
		positions.clear()
		maxSizeWidth = DEFAULT_MAX_WIDTH

		val count = messages.size

		if (count <= 1) {
			return
		}

		if (messages.any { it.isMediaSale }) {
			messages.sortByDescending { it.messageOwner?.media != null }
		}

		var firstSpanAdditionalSize = 200
		val maxSizeHeight = 814.0f
		val proportions = StringBuilder()
		var averageAspectRatio = 1.0f
		var isOut = false
		var maxX = 0
		var forceCalc = false
		var needShare = false

		hasSibling = false
		hasCaption = false

		for (a in 0 until count) {
			val messageObject = messages[a]

			if (a == 0) {
				isOut = messageObject.isOutOwner
				needShare = !isOut && (messageObject.messageOwner?.fwd_from != null && messageObject.messageOwner?.fwd_from?.saved_from_peer != null || messageObject.messageOwner?.from_id is TL_peerUser && (messageObject.messageOwner?.peer_id?.channel_id != 0L || messageObject.messageOwner?.peer_id?.chat_id != 0L || MessageObject.getMedia(messageObject.messageOwner) is TL_messageMediaGame || MessageObject.getMedia(messageObject.messageOwner) is TL_messageMediaInvoice))

				if (messageObject.isMusic || messageObject.isDocument()) {
					isDocuments = true
				}
			}

			val photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, AndroidUtilities.getPhotoSize())

			val position = GroupedMessagePosition()
			position.last = (a == count - 1)
			position.aspectRatio = if (photoSize == null) 1.0f else photoSize.w / photoSize.h.toFloat()

			if (position.aspectRatio > 1.2f) {
				proportions.append("w")
			}
			else if (position.aspectRatio < 0.8f) {
				proportions.append("n")
			}
			else {
				proportions.append("q")
			}

			averageAspectRatio += position.aspectRatio

			if (position.aspectRatio > 2.0f) {
				forceCalc = true
			}

			positions[messageObject] = position
			posArray.add(position)

			if (messageObject.caption != null) {
				hasCaption = true
			}
		}

		if (isDocuments) {
			for (a in 0 until count) {
				val pos = posArray[a]
				pos.flags = pos.flags or (MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT)

				if (a == 0) {
					pos.flags = pos.flags or MessageObject.POSITION_FLAG_TOP
				}
				else if (a == count - 1) {
					pos.flags = pos.flags or MessageObject.POSITION_FLAG_BOTTOM
					pos.last = true
				}

				pos.edge = true
				pos.aspectRatio = 1.0f
				pos.minX = 0
				pos.maxX = 0
				pos.minY = a.toByte()
				pos.maxY = a.toByte()
				pos.spanSize = 1000
				pos.pw = maxSizeWidth
				pos.ph = 100f
			}

			return
		}

		if (needShare) {
			maxSizeWidth -= 50
			firstSpanAdditionalSize += 50
		}

		val minHeight = AndroidUtilities.dp(120f)
		val minWidth = (AndroidUtilities.dp(120f) / (min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / maxSizeWidth.toFloat())).toInt()
		val paddingsWidth = (AndroidUtilities.dp(40f) / (min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / maxSizeWidth.toFloat())).toInt()
		val maxAspectRatio = maxSizeWidth / maxSizeHeight

		averageAspectRatio /= count

		val minH = AndroidUtilities.dp(100f) / maxSizeHeight

		if (!forceCalc && (count == 2 || count == 3 || count == 4)) {
			if (count == 2) {
				val position1 = posArray[0]
				val position2 = posArray[1]
				val pString = proportions.toString()

				if (pString == "ww" && averageAspectRatio > 1.4 * maxAspectRatio && position1.aspectRatio - position2.aspectRatio < 0.2) {
					val height = min(maxSizeWidth / position1.aspectRatio, min(maxSizeWidth / position2.aspectRatio, maxSizeHeight / 2.0f)).roundToInt() / maxSizeHeight

					position1.set(0, 0, 0, 0, maxSizeWidth, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)
					position2.set(0, 0, 1, 1, maxSizeWidth, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)
				}
				else if (pString == "ww" || pString == "qq") {
					val width = maxSizeWidth / 2
					val height = min(width / position1.aspectRatio, min(width / position2.aspectRatio, maxSizeHeight)).roundToInt() / maxSizeHeight

					position1.set(0, 0, 0, 0, width, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
					position2.set(1, 1, 0, 0, width, height, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)

					maxX = 1
				}
				else {
					var secondWidth = max(0.4f * maxSizeWidth, (maxSizeWidth / position1.aspectRatio / (1.0f / position1.aspectRatio + 1.0f / position2.aspectRatio)).roundToInt().toFloat()).toInt()
					var firstWidth = maxSizeWidth - secondWidth

					if (firstWidth < minWidth) {
						val diff = minWidth - firstWidth
						firstWidth = minWidth
						secondWidth -= diff
					}

					val height = min(maxSizeHeight, min(firstWidth / position1.aspectRatio, secondWidth / position2.aspectRatio).roundToInt().toFloat()) / maxSizeHeight

					position1.set(0, 0, 0, 0, firstWidth, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
					position2.set(1, 1, 0, 0, secondWidth, height, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)

					maxX = 1
				}
			}
			else if (count == 3) {
				val position1 = posArray[0]
				val position2 = posArray[1]
				val position3 = posArray[2]

				if (proportions[0] == 'n') {
					val thirdHeight = min(maxSizeHeight * 0.5f, (position2.aspectRatio * maxSizeWidth / (position3.aspectRatio + position2.aspectRatio)).roundToInt().toFloat())
					val secondHeight = maxSizeHeight - thirdHeight
					val rightWidth = max(minWidth.toFloat(), min(maxSizeWidth * 0.5f, min(thirdHeight * position3.aspectRatio, secondHeight * position2.aspectRatio).roundToInt().toFloat())).toInt()
					val leftWidth = min(maxSizeHeight * position1.aspectRatio + paddingsWidth, (maxSizeWidth - rightWidth).toFloat()).roundToInt()

					position1.set(0, 0, 0, 1, leftWidth, 1.0f, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
					position2.set(1, 1, 0, 0, rightWidth, secondHeight / maxSizeHeight, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)
					position3.set(0, 1, 1, 1, rightWidth, thirdHeight / maxSizeHeight, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)

					position3.spanSize = maxSizeWidth
					position1.siblingHeights = floatArrayOf(thirdHeight / maxSizeHeight, secondHeight / maxSizeHeight)

					if (isOut) {
						position1.spanSize = maxSizeWidth - rightWidth
					}
					else {
						position2.spanSize = maxSizeWidth - leftWidth
						position3.leftSpanOffset = leftWidth
					}

					hasSibling = true

					maxX = 1
				}
				else {
					val firstHeight = min(maxSizeWidth / position1.aspectRatio, maxSizeHeight * 0.66f).roundToInt() / maxSizeHeight

					position1.set(0, 1, 0, 0, maxSizeWidth, firstHeight, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)

					val width = maxSizeWidth / 2
					var secondHeight = min(maxSizeHeight - firstHeight, min(width / position2.aspectRatio, width / position3.aspectRatio).roundToInt().toFloat()) / maxSizeHeight

					if (secondHeight < minH) {
						secondHeight = minH
					}

					position2.set(0, 0, 1, 1, width, secondHeight, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM)
					position3.set(1, 1, 1, 1, width, secondHeight, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)

					maxX = 1
				}
			}
			else {
				val position1 = posArray[0]
				val position2 = posArray[1]
				val position3 = posArray[2]
				val position4 = posArray[3]

				if (proportions[0] == 'w') {
					val h0 = min(maxSizeWidth / position1.aspectRatio, maxSizeHeight * 0.66f).roundToInt() / maxSizeHeight

					position1.set(0, 2, 0, 0, maxSizeWidth, h0, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)

					var h = (maxSizeWidth / (position2.aspectRatio + position3.aspectRatio + position4.aspectRatio)).roundToInt().toFloat()
					var w0 = max(minWidth.toFloat(), min(maxSizeWidth * 0.4f, h * position2.aspectRatio)).toInt()
					var w2 = max(max(minWidth.toFloat(), maxSizeWidth * 0.33f), h * position4.aspectRatio).toInt()
					var w1 = maxSizeWidth - w0 - w2

					if (w1 < AndroidUtilities.dp(58f)) {
						val diff = AndroidUtilities.dp(58f) - w1
						w1 = AndroidUtilities.dp(58f)
						w0 -= diff / 2
						w2 -= diff - diff / 2
					}

					h = min(maxSizeHeight - h0, h)
					h /= maxSizeHeight

					if (h < minH) {
						h = minH
					}

					position2.set(0, 0, 1, 1, w0, h, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM)
					position3.set(1, 1, 1, 1, w1, h, MessageObject.POSITION_FLAG_BOTTOM)
					position4.set(2, 2, 1, 1, w2, h, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)

					maxX = 2
				}
				else {
					val w = max(minWidth, (maxSizeHeight / (1.0f / position2.aspectRatio + 1.0f / position3.aspectRatio + 1.0f / position4.aspectRatio)).roundToInt())
					val h0 = min(0.33f, max(minHeight.toFloat(), w / position2.aspectRatio) / maxSizeHeight)
					val h1 = min(0.33f, max(minHeight.toFloat(), w / position3.aspectRatio) / maxSizeHeight)
					val h2 = 1.0f - h0 - h1
					val w0 = min(maxSizeHeight * position1.aspectRatio + paddingsWidth, (maxSizeWidth - w).toFloat()).roundToInt()

					position1.set(0, 0, 0, 2, w0, h0 + h1 + h2, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_TOP or MessageObject.POSITION_FLAG_BOTTOM)
					position2.set(1, 1, 0, 0, w, h0, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)
					position3.set(0, 1, 1, 1, w, h1, MessageObject.POSITION_FLAG_RIGHT)
					position3.spanSize = maxSizeWidth
					position4.set(0, 1, 2, 2, w, h2, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)
					position4.spanSize = maxSizeWidth

					if (isOut) {
						position1.spanSize = maxSizeWidth - w
					}
					else {
						position2.spanSize = maxSizeWidth - w0
						position3.leftSpanOffset = w0
						position4.leftSpanOffset = w0
					}

					position1.siblingHeights = floatArrayOf(h0, h1, h2)

					hasSibling = true

					maxX = 1
				}
			}
		}
		else {
			val croppedRatios = FloatArray(posArray.size)

			for (a in 0 until count) {
				if (averageAspectRatio > 1.1f) {
					croppedRatios[a] = max(1.0f, posArray[a].aspectRatio)
				}
				else {
					croppedRatios[a] = min(1.0f, posArray[a].aspectRatio)
				}

				croppedRatios[a] = max(0.66667f, min(1.7f, croppedRatios[a]))
			}

			var secondLine: Int
			var thirdLine: Int
			var fourthLine: Int
			val attempts = mutableListOf<MessageGroupedLayoutAttempt>()
			var firstLine = 1

			while (firstLine < croppedRatios.size) {
				secondLine = croppedRatios.size - firstLine

				if (firstLine > 3 || secondLine > 3) {
					firstLine++
					continue
				}

				attempts.add(MessageGroupedLayoutAttempt(firstLine, secondLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, croppedRatios.size)))

				firstLine++
			}

			firstLine = 1

			while (firstLine < croppedRatios.size - 1) {
				secondLine = 1

				while (secondLine < croppedRatios.size - firstLine) {
					thirdLine = croppedRatios.size - firstLine - secondLine

					if (firstLine > 3 || secondLine > (if (averageAspectRatio < 0.85f) 4 else 3) || thirdLine > 3) {
						secondLine++
						continue
					}

					attempts.add(MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, croppedRatios.size)))
					secondLine++
				}

				firstLine++
			}

			firstLine = 1

			while (firstLine < croppedRatios.size - 2) {
				secondLine = 1

				while (secondLine < croppedRatios.size - firstLine) {
					thirdLine = 1

					while (thirdLine < croppedRatios.size - firstLine - secondLine) {
						fourthLine = croppedRatios.size - firstLine - secondLine - thirdLine

						if (firstLine > 3 || secondLine > 3 || thirdLine > 3 || fourthLine > 3) {
							thirdLine++
							continue
						}

						attempts.add(MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, fourthLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, firstLine + secondLine + thirdLine), multiHeight(croppedRatios, firstLine + secondLine + thirdLine, croppedRatios.size)))
						thirdLine++
					}

					secondLine++
				}

				firstLine++
			}

			var optimal: MessageGroupedLayoutAttempt? = null
			var optimalDiff = 0.0f
			val maxHeight = (maxSizeWidth / 3 * 4).toFloat()

			for (a in attempts.indices) {
				val attempt = attempts[a]
				var height = 0f
				var minLineHeight = Float.MAX_VALUE

				for (b in attempt.heights.indices) {
					height += attempt.heights[b]

					if (attempt.heights[b] < minLineHeight) {
						minLineHeight = attempt.heights[b]
					}
				}

				var diff = abs(height - maxHeight)

				if (attempt.lineCounts.size > 1) {
					if (attempt.lineCounts[0] > attempt.lineCounts[1] || attempt.lineCounts.size > 2 && attempt.lineCounts[1] > attempt.lineCounts[2] || attempt.lineCounts.size > 3 && attempt.lineCounts[2] > attempt.lineCounts[3]) {
						diff *= 1.2f
					}
				}

				if (minLineHeight < minWidth) {
					diff *= 1.5f
				}

				if (optimal == null || diff < optimalDiff) {
					optimal = attempt
					optimalDiff = diff
				}
			}

			if (optimal == null) {
				return
			}

			var index = 0

			for (i in optimal.lineCounts.indices) {
				val c = optimal.lineCounts[i]
				val lineHeight = optimal.heights[i]
				var spanLeft = maxSizeWidth
				var posToFix: GroupedMessagePosition? = null

				maxX = max(maxX, c - 1)

				for (k in 0 until c) {
					val ratio = croppedRatios[index]
					val width = (ratio * lineHeight).toInt()

					spanLeft -= width

					val pos = posArray[index]
					var flags = 0

					if (i == 0) {
						flags = flags or MessageObject.POSITION_FLAG_TOP
					}

					if (i == optimal.lineCounts.size - 1) {
						flags = flags or MessageObject.POSITION_FLAG_BOTTOM
					}

					if (k == 0) {
						flags = flags or MessageObject.POSITION_FLAG_LEFT

						if (isOut) {
							posToFix = pos
						}
					}

					if (k == c - 1) {
						flags = flags or MessageObject.POSITION_FLAG_RIGHT

						if (!isOut) {
							posToFix = pos
						}
					}

					pos.set(k, k, i, i, width, max(minH, lineHeight / maxSizeHeight), flags)

					index++
				}

				if (posToFix != null) {
					posToFix.pw += spanLeft
					posToFix.spanSize += spanLeft
				}
			}
		}

		val avatarOffset = 108

		for (a in 0 until count) {
			val pos = posArray[a]

			if (isOut) {
				if (pos.minX.toInt() == 0) {
					pos.spanSize += firstSpanAdditionalSize
				}

				if (pos.flags and MessageObject.POSITION_FLAG_RIGHT != 0) {
					pos.edge = true
				}
			}
			else {
				if (pos.maxX.toInt() == maxX || pos.flags and MessageObject.POSITION_FLAG_RIGHT != 0) {
					pos.spanSize += firstSpanAdditionalSize
				}

				if (pos.flags and MessageObject.POSITION_FLAG_LEFT != 0) {
					pos.edge = true
				}
			}

			val messageObject = messages[a]

			if (!isOut && messageObject.needDrawAvatarInternal()) {
				if (pos.edge) {
					if (pos.spanSize != 1000) {
						pos.spanSize += avatarOffset
					}

					pos.pw += avatarOffset
				}
				else if (pos.flags and MessageObject.POSITION_FLAG_RIGHT != 0) {
					if (pos.spanSize != 1000) {
						pos.spanSize -= avatarOffset
					}
					else if (pos.leftSpanOffset != 0) {
						pos.leftSpanOffset += avatarOffset
					}
				}
			}
		}
	}

	fun findPrimaryMessageObject(): MessageObject? {
		return findMessageWithFlags(MessageObject.POSITION_FLAG_TOP or MessageObject.POSITION_FLAG_LEFT)
	}

	fun findMessageWithFlags(flags: Int): MessageObject? {
		if (messages.isNotEmpty() && positions.isEmpty()) {
			calculate()
		}

		return messages.find {
			val position = positions[it]
			position != null && position.flags and flags == flags
		}
	}

	private class MessageGroupedLayoutAttempt {
		val lineCounts: IntArray
		val heights: FloatArray

		constructor(i1: Int, i2: Int, f1: Float, f2: Float) {
			lineCounts = intArrayOf(i1, i2)
			heights = floatArrayOf(f1, f2)
		}

		constructor(i1: Int, i2: Int, i3: Int, f1: Float, f2: Float, f3: Float) {
			lineCounts = intArrayOf(i1, i2, i3)
			heights = floatArrayOf(f1, f2, f3)
		}

		constructor(i1: Int, i2: Int, i3: Int, i4: Int, f1: Float, f2: Float, f3: Float, f4: Float) {
			lineCounts = intArrayOf(i1, i2, i3, i4)
			heights = floatArrayOf(f1, f2, f3, f4)
		}
	}

	companion object {
		private const val DEFAULT_MAX_WIDTH = 800
	}
}
