/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewPropertyAnimator
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.Interpolator
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.MediaController.PhotoEntry
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.VideoEditedInfo
import org.telegram.messenger.messageobject.GroupedMessagePosition
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.MessageDrawable
import org.telegram.ui.ActionBar.MessageDrawable.PathDrawParams
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.ChatActionCell
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.ChatAttachAlert.AttachAlertLayout
import org.telegram.ui.Components.ChatAttachAlertPhotoLayoutPreview.PreviewGroupsView.PreviewGroupCell.MediaCell
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.PhotoViewer
import org.telegram.ui.PhotoViewer.EmptyPhotoViewerProvider
import org.telegram.ui.PhotoViewer.PlaceProviderObject
import org.telegram.ui.sales.CreateMediaSaleFragment
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class ChatAttachAlertPhotoLayoutPreview(alert: ChatAttachAlert, context: Context) : AttachAlertLayout(alert, context) {
	private val durationMultiplier: Long = 1

	// preview is 80% of real message size
	val previewScale: Float
		get() = if (AndroidUtilities.displaySize.y > AndroidUtilities.displaySize.x) .8f else .45f

	var listView: RecyclerListView
	private val layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
	private val groupsView = PreviewGroupsView(context)
	private val undoView = UndoView(context, false)
	var header: TextView
	private var draggingCellTouchX = 0f
	private var draggingCellTouchY = 0f
	private var draggingCellTop = 0f
	private var draggingCellLeft = 0f
	private var draggingCellFromWidth = 0f
	private var draggingCellFromHeight = 0f
	private var draggingCell: MediaCell? = null
	private var draggingCellHiding = false
	private var draggingAnimator: ValueAnimator? = null
	private var draggingCellGroupY = 0f
	private val videoPlayImage: Drawable
	private var headerAnimator: ViewPropertyAnimator? = null
	private var photoLayout: ChatAttachAlertPhotoLayout?
	private var shown = false

	override fun onShow(previousLayout: AttachAlertLayout?) {
		shown = true

		if (previousLayout is ChatAttachAlertPhotoLayout) {
			photoLayout = previousLayout

			groupsView.deletedPhotos.clear()
			groupsView.fromPhotoLayout(photoLayout)
			groupsView.requestLayout()

			layoutManager.scrollToPositionWithOffset(0, 0)

			val setScrollY = Runnable {
				val currentItemTop = previousLayout.currentItemTop
				val paddingTop = previousLayout.listTopPadding
				listView.scrollBy(0, if (currentItemTop > AndroidUtilities.dp(7f)) paddingTop - currentItemTop else paddingTop)
			}

			listView.post(setScrollY)

			postDelayed({
				if (shown) {
					parentAlert.selectedMenuItem.hideSubItem(ChatAttachAlertPhotoLayout.preview)
				}
			}, 250)

			groupsView.toPhotoLayout(photoLayout, false)
		}
		else {
			scrollToTop()
		}

		headerAnimator?.cancel()

		headerAnimator = header.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT)
		headerAnimator?.start()
	}

	override fun onHide() {
		shown = false

		headerAnimator?.cancel()

		headerAnimator = header.animate().alpha(0f).setDuration(150).setInterpolator(CubicBezierInterpolator.EASE_BOTH)
		headerAnimator?.start()

		if (selectedItemsCount > 1) {
			parentAlert.selectedMenuItem.showSubItem(ChatAttachAlertPhotoLayout.preview)
		}

		groupsView.toPhotoLayout(photoLayout, true)
	}

	override val selectedItemsCount: Int
		get() = groupsView.photosCount

	override fun onHidden() {
		draggingCell = null
		undoView.hide(false, 0)
	}

	override val firstOffset: Int
		get() = listTopPadding + AndroidUtilities.dp(56f)

	override fun shouldHideBottomButtons(): Boolean {
		return true
	}

	override fun applyCaption(text: CharSequence?) {
		photoLayout?.applyCaption(text)
	}

	private inner class GroupCalculator(var photos: ArrayList<PhotoEntry>?) {
		var posArray = ArrayList<GroupedMessagePosition>()
		var positions = HashMap<PhotoEntry, GroupedMessagePosition>()

		private inner class MessageGroupedLayoutAttempt {
			var lineCounts: IntArray
			var heights: FloatArray

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

		private fun multiHeight(array: FloatArray, start: Int, end: Int): Float {
			var sum = 0f

			for (a in start until end) {
				sum += array[a]
			}

			return maxSizeWidth.toFloat() / sum
		}

		@JvmField
		var width = 0

		var maxX = 0
		var maxY = 0

		@JvmField
		var height = 0f

		fun calculate(photos: ArrayList<PhotoEntry>?) {
			this.photos = photos
			calculate()
		}

		private val maxSizeWidth = 1000 // was 800, made 1000 for preview

		init {
			calculate()
		}

		fun calculate() {
			// TODO: copied from GroupedMessages, would be better to merge
			val firstSpanAdditionalSize = 200
			val count = photos?.size ?: 0
			posArray.clear()
			positions.clear()

			if (count == 0) {
				width = 0
				height = 0f
				maxX = 0
				maxY = 0
				return
			}

			posArray.ensureCapacity(count)

			val maxSizeHeight = 814.0f
			val proportionsArray = CharArray(count)
			var averageAspectRatio = 1.0f
			var forceCalc = false

			for (a in 0 until count) {
				val photo = photos?.get(a) ?: continue

				val position = GroupedMessagePosition()
				position.last = a == count - 1

				var w = if (photo.cropState != null) photo.cropState.width else photo.width
				var h = if (photo.cropState != null) photo.cropState.height else photo.height

				var rotate: Boolean

				if (photoRotate.containsKey(photo)) {
					rotate = photoRotate[photo] ?: false
				}
				else {
					rotate = false

					runCatching {
						if (photo.isVideo) {
							val m = MediaMetadataRetriever()
							m.setDataSource(photo.path)
							val rotation = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
							rotate = rotation == "90" || rotation == "270"
						}
						else {
							val exif = ExifInterface(photo.path)

							when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
								ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270 -> rotate = true
								ExifInterface.ORIENTATION_ROTATE_180 -> {}
								else -> {}
							}
						}
					}

					photoRotate[photo] = rotate
				}

				if (rotate) {
					val wasW = w
					w = h
					h = wasW
				}

				position.aspectRatio = w / h.toFloat()
				proportionsArray[a] = if (position.aspectRatio > 1.2f) 'w' else if (position.aspectRatio < .8f) 'n' else 'q'
				averageAspectRatio += position.aspectRatio

				if (position.aspectRatio > 2.0f) {
					forceCalc = true
				}

				positions[photo] = position
				posArray.add(position)
			}

			val proportions = String(proportionsArray)
			val minHeight = AndroidUtilities.dp(120f)
			val minWidth = (AndroidUtilities.dp(120f) / (min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / maxSizeWidth.toFloat())).toInt()
			val paddingsWidth = (AndroidUtilities.dp(40f) / (min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / maxSizeWidth.toFloat())).toInt()
			val maxAspectRatio = maxSizeWidth / maxSizeHeight

			averageAspectRatio /= count

			val minH = AndroidUtilities.dp(100f) / maxSizeHeight

			if (count == 1) {
				val position1 = posArray[0]
				val widthPx = AndroidUtilities.displaySize.x - parentAlert.backgroundPaddingLeft * 2
				val maxHeight = max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * .5f
				position1.set(0, 0, 0, 0, 800, widthPx * .8f / position1.aspectRatio / maxHeight, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP or MessageObject.POSITION_FLAG_BOTTOM)
			}
			else if (!forceCalc && (count == 2 || count == 3 || count == 4)) {
				if (count == 2) {
					val position1 = posArray[0]
					val position2 = posArray[1]

					if (proportions == "ww" && averageAspectRatio > 1.4 * maxAspectRatio && position1.aspectRatio - position2.aspectRatio < 0.2) {
						val height = min(maxSizeWidth / position1.aspectRatio, min(maxSizeWidth / position2.aspectRatio, maxSizeHeight / 2.0f)).roundToLong() / maxSizeHeight
						position1.set(0, 0, 0, 0, maxSizeWidth, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)
						position2.set(0, 0, 1, 1, maxSizeWidth, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)
					}
					else if (proportions == "ww" || proportions == "qq") {
						val width = maxSizeWidth / 2
						val height = min(width / position1.aspectRatio, min(width / position2.aspectRatio, maxSizeHeight)).roundToLong() / maxSizeHeight
						position1.set(0, 0, 0, 0, width, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
						position2.set(1, 1, 0, 0, width, height, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
					}
					else {
						var secondWidth = max(0.4f * maxSizeWidth, (maxSizeWidth / position1.aspectRatio / (1.0f / position1.aspectRatio + 1.0f / position2.aspectRatio)).roundToLong().toFloat()).toInt()
						var firstWidth = maxSizeWidth - secondWidth

						if (firstWidth < minWidth) {
							val diff = minWidth - firstWidth
							firstWidth = minWidth
							secondWidth -= diff
						}

						val height = min(maxSizeHeight, min(firstWidth / position1.aspectRatio, secondWidth / position2.aspectRatio).roundToLong().toFloat()) / maxSizeHeight
						position1.set(0, 0, 0, 0, firstWidth, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
						position2.set(1, 1, 0, 0, secondWidth, height, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
					}
				}
				else if (count == 3) {
					val position1 = posArray[0]
					val position2 = posArray[1]
					val position3 = posArray[2]

					if (proportions[0] == 'n') {
						val thirdHeight = min(maxSizeHeight * 0.5f, (position2.aspectRatio * maxSizeWidth / (position3.aspectRatio + position2.aspectRatio)).roundToLong().toFloat())
						val secondHeight = maxSizeHeight - thirdHeight
						val rightWidth = max(minWidth.toFloat(), min(maxSizeWidth * 0.5f, min(thirdHeight * position3.aspectRatio, secondHeight * position2.aspectRatio).roundToLong().toFloat())).toInt()
						val leftWidth = min(maxSizeHeight * position1.aspectRatio + paddingsWidth, (maxSizeWidth - rightWidth).toFloat()).roundToInt()

						position1.set(0, 0, 0, 1, leftWidth, 1.0f, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
						position2.set(1, 1, 0, 0, rightWidth, secondHeight / maxSizeHeight, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)
						position3.set(1, 1, 1, 1, rightWidth, thirdHeight / maxSizeHeight, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)

						position3.spanSize = maxSizeWidth
						position1.siblingHeights = floatArrayOf(thirdHeight / maxSizeHeight, secondHeight / maxSizeHeight)
						position1.spanSize = maxSizeWidth - rightWidth
					}
					else {
						val firstHeight = min(maxSizeWidth / position1.aspectRatio, maxSizeHeight * 0.66f).roundToLong() / maxSizeHeight

						position1.set(0, 1, 0, 0, maxSizeWidth, firstHeight, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)

						val width = maxSizeWidth / 2

						var secondHeight = min(maxSizeHeight - firstHeight, min(width / position2.aspectRatio, width / position3.aspectRatio).roundToLong().toFloat()) / maxSizeHeight

						if (secondHeight < minH) {
							secondHeight = minH
						}

						position2.set(0, 0, 1, 1, width, secondHeight, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM)
						position3.set(1, 1, 1, 1, width, secondHeight, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)
					}
				}
				else {
					val position1 = posArray[0]
					val position2 = posArray[1]
					val position3 = posArray[2]
					val position4 = posArray[3]

					if (proportions[0] == 'w') {
						val h0 = min(maxSizeWidth / position1.aspectRatio, maxSizeHeight * 0.66f).roundToLong() / maxSizeHeight

						position1.set(0, 2, 0, 0, maxSizeWidth, h0, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)

						var h = (maxSizeWidth / (position2.aspectRatio + position3.aspectRatio + position4.aspectRatio)).roundToLong().toFloat()
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
					}
					else {
						val w = max(minWidth, (maxSizeHeight / (1.0f / position2.aspectRatio + 1.0f / position3.aspectRatio + 1.0f / position4.aspectRatio)).roundToInt())
						val h0 = min(0.33f, max(minHeight.toFloat(), w / position2.aspectRatio) / maxSizeHeight)
						val h1 = min(0.33f, max(minHeight.toFloat(), w / position3.aspectRatio) / maxSizeHeight)
						val h2 = 1.0f - h0 - h1
						val w0 = min(maxSizeHeight * position1.aspectRatio + paddingsWidth, (maxSizeWidth - w).toFloat()).roundToInt()

						position1.set(0, 0, 0, 2, w0, h0 + h1 + h2, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_TOP or MessageObject.POSITION_FLAG_BOTTOM)
						position2.set(1, 1, 0, 0, w, h0, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)
						position3.set(1, 1, 1, 1, w, h1, MessageObject.POSITION_FLAG_RIGHT)

						position3.spanSize = maxSizeWidth

						position4.set(1, 1, 2, 2, w, h2, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)

						position4.spanSize = maxSizeWidth

						position1.spanSize = maxSizeWidth - w
						position1.siblingHeights = floatArrayOf(h0, h1, h2)
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
				val attempts = ArrayList<MessageGroupedLayoutAttempt>()
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
							posToFix = pos
						}

						if (k == c - 1) {
							flags = flags or MessageObject.POSITION_FLAG_RIGHT
							posToFix = pos
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

			for (a in 0 until count) {
				val pos = posArray[a]

				if (pos.minX.toInt() == 0) {
					pos.spanSize += firstSpanAdditionalSize
				}

				if (pos.flags and MessageObject.POSITION_FLAG_RIGHT != 0) {
					pos.edge = true
				}

				maxX = max(maxX, pos.maxX.toInt())
				maxY = max(maxY, pos.maxY.toInt())
				pos.left = getLeft(pos, pos.minY.toInt(), pos.maxY.toInt(), pos.minX.toInt())
			}

			for (a in 0 until count) {
				val pos = posArray[a]
				pos.top = getTop(pos, pos.minY.toInt())
			}

			width = getWidth()
			height = getHeight()
		}

		fun getWidth(): Int {
			val lineWidths = IntArray(10)

			Arrays.fill(lineWidths, 0)

			val count = posArray.size

			for (i in 0 until count) {
				val pos = posArray[i]
				val width = pos.pw

				for (y in pos.minY..pos.maxY) {
					lineWidths[y] += width
				}
			}

			var width = lineWidths[0]

			for (y in 1 until lineWidths.size) {
				if (width < lineWidths[y]) {
					width = lineWidths[y]
				}
			}

			return width
		}

		fun getHeight(): Float {
			val lineHeights = FloatArray(10)

			Arrays.fill(lineHeights, 0f)

			val count = posArray.size

			for (i in 0 until count) {
				val pos = posArray[i]
				val height = pos.ph

				for (x in pos.minX..pos.maxX) {
					lineHeights[x] += height
				}
			}

			var height = lineHeights[0]

			for (y in 1 until lineHeights.size) {
				if (height < lineHeights[y]) {
					height = lineHeights[y]
				}
			}

			return height
		}

		private fun getLeft(except: GroupedMessagePosition, minY: Int, maxY: Int, minX: Int): Float {
			val sums = FloatArray(maxY - minY + 1)

			Arrays.fill(sums, 0f)

			val count = posArray.size

			for (i in 0 until count) {
				val pos = posArray[i]

				if (pos !== except && pos.maxX < minX) {
					val end = min(pos.maxY.toInt(), maxY) - minY

					for (y in max(pos.minY - minY, 0)..end) {
						sums[y] += pos.pw.toFloat()
					}
				}
			}

			var max = 0f

			for (i in sums.indices) {
				if (max < sums[i]) {
					max = sums[i]
				}
			}

			return max
		}

		private fun getTop(except: GroupedMessagePosition, minY: Int): Float {
			val sums = FloatArray(maxX + 1)

			Arrays.fill(sums, 0f)

			val count = posArray.size

			for (i in 0 until count) {
				val pos = posArray[i]

				if (pos !== except && pos.maxY < minY) {
					for (x in pos.minX..pos.maxX) {
						sums[x] += pos.ph
					}
				}
			}

			var max = 0f

			for (i in sums.indices) {
				if (max < sums[i]) {
					max = sums[i]
				}
			}

			return max
		}
	}

	override val listTopPadding: Int
		get() = listView.paddingTop

	override var currentItemTop: Int
		get() {
			if (listView.childCount <= 0) {
				listView.topGlowOffset = listView.paddingTop
				return Int.MAX_VALUE
			}

			val child = listView.getChildAt(0)
			val holder = listView.findContainingViewHolder(child) as RecyclerListView.Holder?
			val top = child.top
			var newOffset = AndroidUtilities.dp(8f)

			if (top >= AndroidUtilities.dp(8f) && holder != null && holder.adapterPosition == 0) {
				newOffset = top
			}

			listView.topGlowOffset = newOffset

			return newOffset
		}
		set(value) {
			super.currentItemTop = value
		}

	private var paddingTop = 0

	override fun onPreMeasure(availableWidth: Int, availableHeight: Int) {
		ignoreLayout = true

		val layoutParams = layoutParams as LayoutParams
		layoutParams.topMargin = ActionBar.getCurrentActionBarHeight()

		paddingTop = if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
			(availableHeight / 3.5f).toInt()
		}
		else {
			availableHeight / 5 * 2
		}

		paddingTop -= AndroidUtilities.dp(52f)

		if (paddingTop < 0) {
			paddingTop = 0
		}

		if (listView.paddingTop != paddingTop) {
			listView.setPadding(listView.paddingLeft, paddingTop, listView.paddingRight, listView.paddingBottom)
			invalidate()
		}

		header.textSize = (if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) 18 else 20).toFloat()

		ignoreLayout = false
	}

	override fun scrollToTop() {
		listView.smoothScrollToPosition(0)
	}

	override fun needsActionBar(): Int {
		return 1
	}

	override fun onBackPressed(): Boolean {
		parentAlert.updatePhotoPreview(false)
		return true
	}

	private var ignoreLayout = false

	override fun requestLayout() {
		if (ignoreLayout) {
			return
		}

		super.requestLayout()
	}

	override fun onMenuItemClick(id: Int) {
		runCatching {
			parentAlert.photoLayout.onMenuItemClick(id)
		}
	}

	private var isPortrait = AndroidUtilities.displaySize.y > AndroidUtilities.displaySize.x

	init {
		setWillNotDraw(false)

		val menu = parentAlert.actionBar.createMenu()

		header = TextView(context)

		val dropDownContainer = object : ActionBarMenuItem(context, menu, 0, 0) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)
				info.text = header.text
			}
		}

		parentAlert.actionBar.addView(dropDownContainer, 0, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, (if (AndroidUtilities.isTablet()) 64 else 56).toFloat(), 0f, 40f, 0f))

		header.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		header.gravity = Gravity.LEFT
		header.isSingleLine = true
		header.setLines(1)
		header.maxLines = 1
		header.ellipsize = TextUtils.TruncateAt.END
		header.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
		header.text = context.getString(R.string.AttachMediaPreview)
		header.typeface = Theme.TYPEFACE_BOLD
		header.compoundDrawablePadding = AndroidUtilities.dp(4f)
		header.setPadding(0, 0, AndroidUtilities.dp(10f), 0)
		header.alpha = 0f

		dropDownContainer.addView(header, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.CENTER_VERTICAL, 16f, 0f, 0f, 0f))

		listView = object : RecyclerListView(context) {
			override fun onScrolled(dx: Int, dy: Int) {
				this@ChatAttachAlertPhotoLayoutPreview.invalidate()
				parentAlert.updateLayout(this@ChatAttachAlertPhotoLayoutPreview, true, dy)
				groupsView.onScroll()
				super.onScrolled(dx, dy)
			}

			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(e: MotionEvent): Boolean {
				return if (draggingCell != null) {
					false
				}
				else {
					super.onTouchEvent(e)
				}
			}

			override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
				return if (draggingCell != null) {
					false
				}
				else {
					super.onInterceptTouchEvent(e)
				}
			}
		}

		listView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
				return RecyclerListView.Holder(groupsView)
			}

			override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
				// unused
			}

			override fun getItemCount(): Int {
				return 1
			}
		}

		listView.layoutManager = layoutManager
		listView.clipChildren = false
		listView.clipToPadding = false
		listView.overScrollMode = OVER_SCROLL_NEVER
		listView.isVerticalScrollBarEnabled = false
		listView.setPadding(0, 0, 0, AndroidUtilities.dp(46f))

		groupsView.clipToPadding = true
		groupsView.clipChildren = true

		addView(listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		photoLayout = parentAlert.photoLayout

		groupsView.deletedPhotos.clear()
		groupsView.fromPhotoLayout(photoLayout)

		undoView.setEnterOffsetMargin(AndroidUtilities.dp((8 + 24).toFloat()))

		addView(undoView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM or Gravity.LEFT, 8f, 0f, 8f, 52f))

		videoPlayImage = ResourcesCompat.getDrawable(resources, R.drawable.play_mini_video, null)!!
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)

		val isPortrait = AndroidUtilities.displaySize.y > AndroidUtilities.displaySize.x

		if (this.isPortrait != isPortrait) {
			this.isPortrait = isPortrait

			val groupCellsCount = groupsView.groupCells.size

			for (i in 0 until groupCellsCount) {
				val groupCell = groupsView.groupCells[i]

				if (groupCell.group?.photos?.size == 1) {
					groupCell.setGroup(groupCell.group, true)
				}
			}
		}
	}

	override fun onSelectedItemsCountChanged(count: Int) {
		if (count > 1) {
			parentAlert.selectedMenuItem.showSubItem(ChatAttachAlertPhotoLayout.group)
		}
		else {
			parentAlert.selectedMenuItem.hideSubItem(ChatAttachAlertPhotoLayout.group)
		}
	}

	private inner class PreviewGroupsView(context: Context) : ViewGroup(context) {
		private val hintView: ChatActionCell

		override fun onLayout(b: Boolean, i: Int, i1: Int, i2: Int, i3: Int) {
			hintView.layout(0, 0, hintView.measuredWidth, hintView.measuredHeight)
		}

		override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
			return false
		}

		val groupCells = ArrayList<PreviewGroupCell>()
		val deletedPhotos = HashMap<Any, Any>()

		fun saveDeletedImageId(photo: PhotoEntry?) {
			if (photoLayout == null) {
				return
			}

			val photosMap = photoLayout?.selectedPhotos
			val entries: List<Map.Entry<Any, Any>> = ArrayList<Map.Entry<Any, Any>>(photosMap?.entries ?: emptyList())
			val entriesCount = entries.size

			for (i in 0 until entriesCount) {
				if (entries[i].value === photo) {
					deletedPhotos[photo] = entries[i].key
					break
				}
			}
		}

		fun fromPhotoLayout(photoLayout: ChatAttachAlertPhotoLayout?) {
			photosOrder = photoLayout?.selectedPhotosOrder
			photosMap = photoLayout?.selectedPhotos
			fromPhotoArrays()
		}

		fun fromPhotoArrays() {
			groupCells.clear()

			var photos = ArrayList<PhotoEntry>()
			val photosOrderSize = photosOrder?.size ?: 0
			val photosOrderLast = photosOrderSize - 1

			for (i in 0 until photosOrderSize) {
				val imageId = photosOrder?.get(i) as? Int ?: continue
				val photoEntry = photosMap?.get(imageId) as? PhotoEntry ?: continue

				photos.add(photoEntry)

				if (i % 10 == 9 || i == photosOrderLast) {
					val groupCell = PreviewGroupCell()
					groupCell.setGroup(GroupCalculator(photos), false)
					groupCells.add(groupCell)
					photos = ArrayList()
				}
			}
		}

		var photosMap: HashMap<Any, Any>? = null
		var photosMapKeys: List<Map.Entry<Any, Any>>? = null
		var selectedPhotos: HashMap<Any, Any>? = null
		var photosOrder: ArrayList<Any>? = null

		fun calcPhotoArrays() {
			photosMap = photoLayout?.selectedPhotos
			photosMapKeys = ArrayList<Map.Entry<Any, Any>>(photosMap?.entries ?: emptyList())
			selectedPhotos = HashMap()
			photosOrder = ArrayList()

			val groupCellsCount = groupCells.size

			for (i in 0 until groupCellsCount) {
				val groupCell = groupCells[i]
				val group = groupCell.group

				if (group?.photos.isNullOrEmpty()) {
					continue
				}

				val photosCount = group?.photos?.size ?: 0

				for (j in 0 until photosCount) {
					val photoEntry = group?.photos?.get(j) ?: continue

					if (deletedPhotos.containsKey(photoEntry)) {
						val imageId = deletedPhotos[photoEntry] ?: continue
						selectedPhotos?.set(imageId, photoEntry)
						photosOrder?.add(imageId)
					}
					else {
						var found = false

						for (k in photosMapKeys!!.indices) {
							val (key, value) = photosMapKeys!![k]

							if (value === photoEntry) {
								selectedPhotos!![key] = value
								photosOrder!!.add(key)
								found = true
								break
							}
						}

						if (!found) {
							for (k in photosMapKeys!!.indices) {
								val (key, value) = photosMapKeys!![k]

								if (value is PhotoEntry && value.path != null && value.path == photoEntry.path) {
									selectedPhotos!![key] = value
									photosOrder!!.add(key)
									break
								}
							}
						}
					}
				}
			}
		}

		fun toPhotoLayout(photoLayout: ChatAttachAlertPhotoLayout?, updateLayout: Boolean) {
			val previousCount = photoLayout?.selectedPhotosOrder?.size ?: 0

			calcPhotoArrays()

			photoLayout?.updateSelected(selectedPhotos, photosOrder, updateLayout)

			if (previousCount != photosOrder?.size) {
				parentAlert.updateCountButton(1)
			}
		}

		val photosCount: Int
			get() {
				var count = 0
				val groupCellsCount = groupCells.size

				for (i in 0 until groupCellsCount) {
					val groupCell = groupCells[i]

					groupCell.group?.photos?.let {
						count += it.size
					}
				}

				return count
			}

		val photos: ArrayList<PhotoEntry>
			get() {
				val photos = ArrayList<PhotoEntry>()
				val groupCellsCount = groupCells.size

				for (i in 0 until groupCellsCount) {
					val groupCell = groupCells[i]

					groupCell.group?.photos?.let {
						photos.addAll(it)
					}
				}

				return photos
			}

		private val paddingTop = AndroidUtilities.dp((8 + 8).toFloat())
		private val paddingBottom = AndroidUtilities.dp((32 + 32).toFloat())
		private var lastMeasuredHeight = 0

		private fun measurePureHeight(): Int {
			var height = paddingTop + paddingBottom
			val groupCellsCount = groupCells.size

			for (i in 0 until groupCellsCount) {
				height += groupCells[i].measure().toInt()
			}

			if (hintView.measuredHeight <= 0) {
				hintView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(9999, MeasureSpec.AT_MOST))
			}

			height += hintView.measuredHeight

			return height
		}

		private fun measureHeight(): Int {
			return max(measurePureHeight(), AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.dp((8 + 46 - 9).toFloat()))
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			hintView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(9999, MeasureSpec.AT_MOST))

			if (lastMeasuredHeight <= 0) {
				lastMeasuredHeight = measureHeight()
			}

			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max(MeasureSpec.getSize(heightMeasureSpec), lastMeasuredHeight), MeasureSpec.EXACTLY))
		}

		override fun invalidate() {
			val measuredHeight = measureHeight()

			if (lastMeasuredHeight != measuredHeight) {
				lastMeasuredHeight = measuredHeight
				requestLayout()
			}

			super.invalidate()
		}

		var viewTop = 0f
		var viewBottom = 0f
		var lastGroupSeen: BooleanArray? = null

		private fun groupSeen(): BooleanArray {
			val seen = BooleanArray(groupCells.size)
			var y = paddingTop.toFloat()
			val scrollY = listView.computeVerticalScrollOffset()

			viewTop = max(0, scrollY - listTopPadding).toFloat()
			viewBottom = (listView.measuredHeight - listTopPadding + scrollY).toFloat()

			val groupCellsSize = groupCells.size

			for (i in 0 until groupCellsSize) {
				val groupCell = groupCells[i]
				val height = groupCell.measure()
				seen[i] = isSeen(y, y + height)
				y += height
			}

			return seen
		}

		fun isSeen(fromY: Float, toY: Float): Boolean {
			return fromY in viewTop..viewBottom || toY in viewTop..viewBottom || fromY <= viewTop && toY >= viewBottom
		}

		fun onScroll() {
			var newGroupSeen = lastGroupSeen == null

			if (!newGroupSeen) {
				val seen = groupSeen()

				if (seen.size != lastGroupSeen!!.size) {
					newGroupSeen = true
				}
				else {
					for (i in seen.indices) {
						if (seen[i] != lastGroupSeen!![i]) {
							newGroupSeen = true
							break
						}
					}
				}
			}
			else {
				lastGroupSeen = groupSeen()
			}

			if (newGroupSeen) {
				invalidate()
			}
		}

		fun remeasure() {
			var y = paddingTop.toFloat()
			var i = 0
			val groupCellsCount = groupCells.size

			for (j in 0 until groupCellsCount) {
				val groupCell = groupCells[j]
				val height = groupCell.measure()
				groupCell.y = y
				groupCell.indexStart = i
				y += height
				i += groupCell.group!!.photos!!.size
			}
		}

		public override fun onDraw(canvas: Canvas) {
			var y = paddingTop.toFloat()
			var i = 0
			val scrollY = listView.computeVerticalScrollOffset()

			viewTop = max(0, scrollY - listTopPadding).toFloat()
			viewBottom = (listView.measuredHeight - listTopPadding + scrollY).toFloat()

			canvas.withTranslation(0f, paddingTop.toFloat()) {
				val groupCellsCount = groupCells.size

				for (j in 0 until groupCellsCount) {
					val groupCell = groupCells[j]
					val height = groupCell.measure()

					groupCell.y = y
					groupCell.indexStart = i

					val groupIsSeen = y in viewTop..viewBottom || y + height in viewTop..viewBottom || y <= viewTop && y + height >= viewBottom

					if (groupIsSeen && groupCell.draw(this)) {
						invalidate()
					}

					translate(0f, height)

					y += height
					i += groupCell.group!!.photos!!.size
				}

				hintView.setVisiblePart(y, hintView.measuredHeight)

				if (hintView.hasGradientService()) {
					hintView.drawBackground(this, true)
				}

				hintView.draw(this)
			}

			if (draggingCell != null) {
				canvas.withSave {
					val point = dragTranslate()

					translate(point.x, point.y)

					if (draggingCell?.draw(this, true) == true) {
						invalidate()
					}
				}
			}

			super.onDraw(canvas)
		}

		var tapTime: Long = 0
		var tapGroupCell: PreviewGroupCell? = null
		var tapMediaCell: MediaCell? = null
		private var draggingT = 0f
		private var savedDragFromX = 0f
		private var savedDragFromY = 0f
		private var savedDraggingT = 0f
		private val tmpPoint = Point()

		fun dragTranslate(): Point {
			val draggingCell = draggingCell

			if (draggingCell == null) {
				tmpPoint.x = 0f
				tmpPoint.y = 0f
				return tmpPoint
			}

			if (!draggingCellHiding) {
				val drawingRect = draggingCell.rect()
				val finalDrawingRect = draggingCell.rect(1f)
				tmpPoint.x = AndroidUtilities.lerp(finalDrawingRect.left + drawingRect.width() / 2f, draggingCellTouchX - (draggingCellLeft - .5f) * draggingCellFromWidth, draggingT)
				tmpPoint.y = AndroidUtilities.lerp(draggingCell.groupCell.y + finalDrawingRect.top + drawingRect.height() / 2f, draggingCellTouchY - (draggingCellTop - .5f) * draggingCellFromHeight + draggingCellGroupY, draggingT)
			}
			else {
				val drawingRect = draggingCell.rect()
				val finalDrawingRect = draggingCell.rect(1f)
				tmpPoint.x = AndroidUtilities.lerp(finalDrawingRect.left + drawingRect.width() / 2f, savedDragFromX, draggingT / savedDraggingT)
				tmpPoint.y = AndroidUtilities.lerp(draggingCell.groupCell.y + finalDrawingRect.top + drawingRect.height() / 2f, savedDragFromY, draggingT / savedDraggingT)
			}

			return tmpPoint
		}

		fun stopDragging() {
			draggingAnimator?.cancel()

			val dragTranslate = dragTranslate()

			savedDraggingT = draggingT
			savedDragFromX = dragTranslate.x
			savedDragFromY = dragTranslate.y
			draggingCellHiding = true

			draggingAnimator = ValueAnimator.ofFloat(savedDraggingT, 0f)

			draggingAnimator?.addUpdateListener {
				draggingT = it.animatedValue as Float
				invalidate()
			}

			draggingAnimator?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					draggingCell = null
					draggingCellHiding = false
					invalidate()
				}
			})

			draggingAnimator?.duration = (200 * durationMultiplier)
			draggingAnimator?.start()

			invalidate()
		}

		fun startDragging(cell: MediaCell?) {
			draggingCell = cell
			draggingCellGroupY = draggingCell!!.groupCell.y
			draggingCellHiding = false
			draggingT = 0f
			invalidate()

			if (draggingAnimator != null) {
				draggingAnimator?.cancel()
			}

			draggingAnimator = ValueAnimator.ofFloat(0f, 1f)

			draggingAnimator?.addUpdateListener {
				draggingT = it.animatedValue as Float
				invalidate()
			}

			draggingAnimator?.duration = (200 * durationMultiplier)
			draggingAnimator?.start()
		}

		private var scrollerStarted = false

		private val scroller = object : Runnable {
			override fun run() {
				if (draggingCell == null || draggingCellHiding) {
					return
				}

				val scrollY = listView.computeVerticalScrollOffset()
				val atBottom = scrollY + listView.computeVerticalScrollExtent() >= measurePureHeight() - paddingBottom + paddingTop
				val top = max(0f, draggingCellTouchY - max(0, scrollY - listTopPadding) - AndroidUtilities.dp(52f))
				val bottom = max(0f, listView.measuredHeight - (draggingCellTouchY - scrollY) - listTopPadding - AndroidUtilities.dp((52 + 32).toFloat()))
				val r = AndroidUtilities.dp(32f).toFloat()
				var dy = 0f

				if (top < r && scrollY > listTopPadding) {
					dy = -(1f - top / r) * AndroidUtilities.dp(6f).toFloat()
				}
				else if (bottom < r) {
					dy = (1f - bottom / r) * AndroidUtilities.dp(6f).toFloat()
				}

				if (abs(dy.toInt()) > 0 && listView.canScrollVertically(dy.toInt()) && !(dy > 0 && atBottom)) {
					draggingCellTouchY += dy
					listView.scrollBy(0, dy.toInt())
					invalidate()
				}
				scrollerStarted = true
				postDelayed(this, 15)
			}
		}

		var photoViewerProvider = GroupingPhotoViewerProvider()

		inner class GroupingPhotoViewerProvider : EmptyPhotoViewerProvider() {
			private var photos = ArrayList<PhotoEntry>()

			fun init(photos: ArrayList<PhotoEntry>) {
				this.photos = photos
			}

			override fun onClose() {
				fromPhotoArrays()
				toPhotoLayout(photoLayout, false)
			}

			override fun isPhotoChecked(index: Int): Boolean {
				return if (index < 0 || index >= photos.size) {
					false
				}
				else {
					photosOrder?.contains(photos[index].imageId) == true
				}
			}

			override fun setPhotoChecked(index: Int, videoEditedInfo: VideoEditedInfo?): Int {
				if (index < 0 || index >= photos.size) {
					return -1
				}

				val photosOrder = photosOrder ?: return -1
				val imageId = photos[index].imageId as? Int ?: return -1
				val orderIndex = photosOrder.indexOf(imageId)

				return if (orderIndex >= 0) {
					if (photosOrder.size <= 1) {
						return -1
					}

					photosOrder.removeAt(orderIndex)
					fromPhotoArrays()
					orderIndex
				}
				else {
					photosOrder.add(imageId)
					fromPhotoArrays()
					photosOrder.size - 1
				}
			}

			override fun setPhotoUnchecked(entry: Any): Int {
				val photosOrder = photosOrder ?: return -1
				val photoEntry = entry as PhotoEntry
				val imageId: Any = photoEntry.imageId

				if (photosOrder.size <= 1) {
					return -1
				}

				val index = photosOrder.indexOf(imageId as Int)

				if (index >= 0) {
					photosOrder.removeAt(index)
					fromPhotoArrays()
					return index
				}

				return -1
			}

			override fun getSelectedCount(): Int {
				return photosOrder?.size ?: 0
			}

			override fun getSelectedPhotosOrder(): ArrayList<Any> {
				return photosOrder ?: ArrayList()
			}

			override fun getSelectedPhotos(): HashMap<Any, Any> {
				return photosMap ?: HashMap()
			}

			override fun getPhotoIndex(index: Int): Int {
				if (index < 0 || index >= photos.size) {
					return -1
				}

				val photoEntry = photos[index]
				return photosOrder?.indexOf(photoEntry.imageId) ?: -1
			}

			override fun getPlaceForPhoto(messageObject: MessageObject?, fileLocation: FileLocation?, index: Int, needPreview: Boolean): PlaceProviderObject? {
				if (index < 0 || index >= photos.size || !isPhotoChecked(index)) {
					return null
				}

				val photoEntry = photos[index]

				var group: PreviewGroupCell? = null
				var mediaCell: MediaCell? = null
				val groupCellsCount = groupCells.size

				for (i in 0 until groupCellsCount) {
					group = groupCells[i]

					if (group.media != null) {
						val count = group.media?.size ?: 0

						for (j in 0 until count) {
							val cell = group.media?.get(j)

							if (cell != null && cell.photoEntry === photoEntry && cell.scale > .5) {
								mediaCell = group.media!![j]
								break
							}
						}

						if (mediaCell != null) {
							break
						}
					}
				}

				if (group != null && mediaCell != null) {
					val `object` = PlaceProviderObject()
					val coordinates = IntArray(2)

					getLocationInWindow(coordinates)

					if (Build.VERSION.SDK_INT < 26) {
						coordinates[0] -= parentAlert.leftInset
					}

					`object`.viewX = coordinates[0]
					`object`.viewY = coordinates[1] + group.y.toInt()
					`object`.scale = 1f
					`object`.parentView = this@PreviewGroupsView
					`object`.imageReceiver = mediaCell.image
					`object`.thumb = `object`.imageReceiver.bitmapSafe
					`object`.radius = IntArray(4)
					`object`.radius[0] = mediaCell.roundRadii.left.toInt()
					`object`.radius[1] = mediaCell.roundRadii.top.toInt()
					`object`.radius[2] = mediaCell.roundRadii.right.toInt()
					`object`.radius[3] = mediaCell.roundRadii.bottom.toInt()
					`object`.clipTopAddition = (-this@PreviewGroupsView.y).toInt()
					`object`.clipBottomAddition = this@PreviewGroupsView.height - (-this@PreviewGroupsView.y + listView.height - parentAlert.clipLayoutBottom).toInt()

					return `object`
				}

				return null
			}

			override fun cancelButtonPressed(): Boolean {
				return false
			}

			override fun updatePhotoAtIndex(index: Int) {
				if (index < 0 || index >= photos.size) {
					return
				}

				val photoEntry = photos[index]
				val imageId = photoEntry.imageId

				invalidate()

				for (i in groupCells.indices) {
					val groupCell = groupCells[i]

					if (groupCell.media != null) {
						for (j in groupCell.media!!.indices) {
							val mediaCell = groupCell.media?.get(j)

							if (mediaCell != null && mediaCell.photoEntry?.imageId == imageId) {
								mediaCell.setImage(photoEntry)
							}
						}

						var hadUpdates = false

						groupCell.group?.photos?.let {
							for (j in it.indices) {
								if (it[j].imageId == imageId) {
									it[j] = photoEntry
									hadUpdates = true
								}
							}
						}

						if (hadUpdates) {
							groupCell.setGroup(groupCell.group, true)
						}
					}
				}

				remeasure()
				invalidate()
			}
		}

		private var undoViewId = 0

		@SuppressLint("ClickableViewAccessibility")
		override fun onTouchEvent(event: MotionEvent): Boolean {
			var result = false
			val touchX = event.x
			val touchY = event.y
			var touchGroupCell: PreviewGroupCell? = null
			var touchMediaCell: MediaCell? = null
			var groupY = 0f
			val groupCellsCount = groupCells.size

			for (j in 0 until groupCellsCount) {
				val groupCell = groupCells[j]
				val height = groupCell.measure()

				if (touchY >= groupY && touchY <= groupY + height) {
					touchGroupCell = groupCell
					break
				}

				groupY += height
			}

			if (touchGroupCell != null) {
				val mediaCount = touchGroupCell.media!!.size

				for (i in 0 until mediaCount) {
					val mediaCell = touchGroupCell.media?.get(i)

					if (mediaCell != null && mediaCell.drawingRect().contains(touchX, touchY - groupY)) {
						touchMediaCell = mediaCell
						break
					}
				}
			}

			var draggingOverGroupCell: PreviewGroupCell? = null
			var draggingOverMediaCell: MediaCell? = null

			if (draggingCell != null) {
				groupY = 0f

				val drawingRect = draggingCell!!.rect()
				val dragPoint = dragTranslate()
				val draggingCellXY = RectF()
				val cx = dragPoint.x
				val cy = dragPoint.y

				draggingCellXY[cx - drawingRect.width() / 2, cy - drawingRect.height() / 2, cx + drawingRect.width() / 2] = cy + drawingRect.height() / 2

				var maxLength = 0f

				for (j in 0 until groupCellsCount) {
					val groupCell = groupCells[j]
					val height = groupCell.measure()
					val top = groupY
					val bottom = groupY + height

					if (bottom >= draggingCellXY.top && draggingCellXY.bottom >= top) {
						val length = min(bottom, draggingCellXY.bottom) - max(top, draggingCellXY.top)

						if (length > maxLength) {
							draggingOverGroupCell = groupCell
							maxLength = length
						}
					}

					groupY += height
				}

				if (draggingOverGroupCell != null) {
					var maxArea = 0f
					val mediaCount = draggingOverGroupCell.media!!.size

					for (i in 0 until mediaCount) {
						val mediaCell = draggingOverGroupCell.media?.get(i)

						if (mediaCell != null && mediaCell !== draggingCell && draggingOverGroupCell.group!!.photos!!.contains(mediaCell.photoEntry)) {
							val mediaCellRect = mediaCell.drawingRect()

							if (mediaCell.positionFlags and MessageObject.POSITION_FLAG_TOP > 0) {
								mediaCellRect.top = 0f
							}

							if (mediaCell.positionFlags and MessageObject.POSITION_FLAG_LEFT > 0) {
								mediaCellRect.left = 0f
							}

							if (mediaCell.positionFlags and MessageObject.POSITION_FLAG_RIGHT > 0) {
								mediaCellRect.right = width.toFloat()
							}

							if (mediaCell.positionFlags and MessageObject.POSITION_FLAG_BOTTOM > 0) {
								mediaCellRect.bottom = draggingOverGroupCell.height
							}

							if (RectF.intersects(draggingCellXY, mediaCellRect)) {
								val area = (min(mediaCellRect.right, draggingCellXY.right) - max(mediaCellRect.left, draggingCellXY.left)) * (min(mediaCellRect.bottom, draggingCellXY.bottom) - max(mediaCellRect.top, draggingCellXY.top)) / (draggingCellXY.width() * draggingCellXY.height())

								if (area > 0.15f && area > maxArea) {
									draggingOverMediaCell = mediaCell
									maxArea = area
								}
							}
						}
					}
				}
			}

			val action = event.action

			if (action == MotionEvent.ACTION_DOWN && draggingCell == null && !listView.scrollingByUser && (draggingAnimator == null || !draggingAnimator!!.isRunning) && touchGroupCell != null && touchMediaCell != null && touchGroupCell.group != null && touchGroupCell.group!!.photos!!.contains(touchMediaCell.photoEntry)) {
				tapGroupCell = touchGroupCell
				tapMediaCell = touchMediaCell
				draggingCellTouchX = touchX
				draggingCellTouchY = touchY
				draggingCell = null
				tapTime = SystemClock.elapsedRealtime()

				val wasTapTime = tapTime
				val wasTapMediaCell = tapMediaCell!!

				AndroidUtilities.runOnUIThread({
					if (listView.scrollingByUser || tapTime != wasTapTime || tapMediaCell !== wasTapMediaCell) {
						return@runOnUIThread
					}

					startDragging(tapMediaCell)

					val draggingCellRect = draggingCell!!.rect()
					val draggingCellDrawingRect = draggingCell!!.drawingRect()

					draggingCellLeft = (.5f + (draggingCellTouchX - draggingCellRect.left) / draggingCellRect.width()) / 2
					draggingCellTop = (draggingCellTouchY - draggingCellRect.top) / draggingCellRect.height()
					draggingCellFromWidth = draggingCellDrawingRect.width()
					draggingCellFromHeight = draggingCellDrawingRect.height()

					runCatching {
						this@ChatAttachAlertPhotoLayoutPreview.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
					}
				}, ViewConfiguration.getLongPressTimeout().toLong())

				invalidate()

				result = true
			}
			else if (action == MotionEvent.ACTION_MOVE && draggingCell != null && !draggingCellHiding) {
				draggingCellTouchX = touchX
				draggingCellTouchY = touchY

				if (!scrollerStarted) {
					scrollerStarted = true
					postDelayed(scroller, 16)
				}

				invalidate()
				result = true
			}
			else if (action == MotionEvent.ACTION_UP && draggingCell != null) {
				var replaceGroupCell: PreviewGroupCell? = null
				var replaceMediaCell: MediaCell? = null

				if (touchGroupCell != null && touchMediaCell != null && touchMediaCell !== draggingCell) {
					replaceGroupCell = touchGroupCell
					replaceMediaCell = touchMediaCell
				}
				else if (draggingOverGroupCell != null && draggingOverMediaCell != null && draggingOverMediaCell !== draggingCell && draggingOverMediaCell.photoEntry !== draggingCell!!.photoEntry) {
					replaceGroupCell = draggingOverGroupCell
					replaceMediaCell = draggingOverMediaCell
				}

				if (replaceGroupCell != null && replaceMediaCell != null && replaceMediaCell !== draggingCell) {
					val draggingIndex = draggingCell!!.groupCell.group!!.photos!!.indexOf(draggingCell!!.photoEntry)
					var tapIndex = replaceGroupCell.group!!.photos!!.indexOf(replaceMediaCell.photoEntry)

					if (draggingIndex >= 0) {
						draggingCell!!.groupCell.group!!.photos!!.removeAt(draggingIndex)
						draggingCell!!.groupCell.setGroup(draggingCell!!.groupCell.group, true)
					}

					if (tapIndex >= 0) {
						if (groupCells.indexOf(replaceGroupCell) > groupCells.indexOf(draggingCell!!.groupCell)) {
							tapIndex++
						}

						pushToGroup(replaceGroupCell, draggingCell!!.photoEntry!!, tapIndex)

						if (draggingCell!!.groupCell !== replaceGroupCell) {
							var newDraggingCell: MediaCell? = null
							val mediaCount = replaceGroupCell.media!!.size

							for (i in 0 until mediaCount) {
								val mediaCell = replaceGroupCell.media!![i]
								if (mediaCell.photoEntry === draggingCell!!.photoEntry) {
									newDraggingCell = mediaCell
									break
								}
							}

							if (newDraggingCell != null) {
								remeasure()
								newDraggingCell.layoutFrom(draggingCell!!)
								draggingCell = newDraggingCell
								newDraggingCell.groupCell = replaceGroupCell
								draggingCell!!.fromScale = 1f
								newDraggingCell.scale = draggingCell!!.fromScale
								remeasure()
							}
						}
					}

					runCatching {
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
							this@ChatAttachAlertPhotoLayoutPreview.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
						}
					}

					updateGroups()

					toPhotoLayout(photoLayout, false)
				}

				stopDragging()
				result = true
			}
			else if (action == MotionEvent.ACTION_UP && draggingCell == null && tapMediaCell != null && tapGroupCell != null) {
				val cellRect = tapMediaCell!!.drawingRect()

				AndroidUtilities.rectTmp.set(cellRect.right - AndroidUtilities.dp(36.4f), tapGroupCell!!.top + cellRect.top, cellRect.right, tapGroupCell!!.top + cellRect.top + AndroidUtilities.dp(36.4f))

				val tappedAtIndex = AndroidUtilities.rectTmp.contains(touchX, touchY - tapMediaCell!!.groupCell.y)

				if (tappedAtIndex) {
					if (selectedItemsCount > 1) {
						// short tap -> remove photo
						val photo = tapMediaCell!!.photoEntry
						val index = tapGroupCell!!.group!!.photos!!.indexOf(photo)

						if (index >= 0) {
							saveDeletedImageId(photo)

							val groupCell: PreviewGroupCell = tapGroupCell!!
							groupCell.group!!.photos!!.removeAt(index)
							groupCell.setGroup(groupCell.group, true)

							updateGroups()

							toPhotoLayout(photoLayout, false)

							val currentUndoViewId = ++undoViewId

							undoView.showWithAction(0, UndoView.ACTION_PREVIEW_MEDIA_DESELECTED, photo, null) {
								draggingAnimator?.cancel()

								draggingCell = null
								draggingT = 0f

								pushToGroup(groupCell, photo!!, index)
								updateGroups()
								toPhotoLayout(photoLayout, false)
							}

							postDelayed({
								if (currentUndoViewId == undoViewId && undoView.isShown) {
									undoView.hide(true, 1)
								}
							}, (1000 * 4).toLong())
						}

						draggingAnimator?.cancel()
					}
				}
				else {
					calcPhotoArrays()
					val arrayList: ArrayList<PhotoEntry> = photos
					val position = arrayList.indexOf(tapMediaCell!!.photoEntry)
					val chatActivity: ChatActivity?

					val type: Int

					if (parentAlert.avatarPicker != 0) {
						chatActivity = null
						type = PhotoViewer.SELECT_TYPE_AVATAR
					}
					else if (parentAlert.baseFragment is ChatActivity) {
						chatActivity = parentAlert.baseFragment
						type = 0
					}
					else {
						chatActivity = null
						type = 4
					}

					if (parentAlert.chatAttachViewDelegate?.needEnterComment() != true) {
						AndroidUtilities.hideKeyboard(parentAlert.baseFragment.fragmentView?.findFocus())
						AndroidUtilities.hideKeyboard(parentAlert.container.findFocus())
					}

					PhotoViewer.getInstance().setParentActivity(parentAlert.baseFragment)
					PhotoViewer.getInstance().setParentAlert(parentAlert)
					PhotoViewer.getInstance().setMaxSelectedPhotos(parentAlert.maxSelectedPhotos, parentAlert.allowOrder)

					photoViewerProvider.init(arrayList)

					val objectArrayList = ArrayList<Any>(arrayList)

					PhotoViewer.getInstance().openPhotoForSelect(objectArrayList, position, type, false, photoViewerProvider, chatActivity, parentAlert.baseFragment is CreateMediaSaleFragment)

					if (photoLayout?.captionForAllMedia() == true) {
						PhotoViewer.getInstance().setCaption(parentAlert.commentTextView.text)
					}
				}

				tapMediaCell = null
				tapTime = 0
				draggingCell = null
				draggingT = 0f
				result = true
			}

			if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
				tapTime = 0

				removeCallbacks(scroller)

				scrollerStarted = false

				if (!result) {
					stopDragging()
					result = true
				}
			}

			return result
		}

		private fun pushToGroup(groupCell: PreviewGroupCell, photoEntry: PhotoEntry, index: Int) {
			groupCell.group?.photos?.add(min(groupCell.group!!.photos!!.size, index), photoEntry)

			if (groupCell.group?.photos?.size == 11) {
				val jumpPhoto = groupCell.group!!.photos!![10]
				groupCell.group!!.photos!!.removeAt(10)

				val groupIndex = groupCells.indexOf(groupCell)

				if (groupIndex >= 0) {
					var nextGroupCell = if (groupIndex + 1 == groupCells.size) null else groupCells[groupIndex + 1]

					if (nextGroupCell == null) {
						nextGroupCell = PreviewGroupCell()
						val newPhotos = ArrayList<PhotoEntry>()
						newPhotos.add(jumpPhoto)
						nextGroupCell.setGroup(GroupCalculator(newPhotos), true)
						invalidate()
					}
					else {
						pushToGroup(nextGroupCell, jumpPhoto, 0)
					}
				}
			}

			groupCell.setGroup(groupCell.group, true)
		}

		private fun updateGroups() {
			val groupCellsCount = groupCells.size

			for (i in 0 until groupCellsCount) {
				val groupCell = groupCells[i]

				if (groupCell.group!!.photos!!.size < 10 && i < groupCells.size - 1) {
					var photosToTake = 10 - groupCell.group!!.photos!!.size
					val nextGroup = groupCells[i + 1]
					val takenPhotos = ArrayList<PhotoEntry>()

					photosToTake = min(photosToTake, nextGroup.group!!.photos!!.size)

					for (j in 0 until photosToTake) {
						takenPhotos.add(nextGroup.group!!.photos!!.removeAt(0))
					}

					groupCell.group!!.photos!!.addAll(takenPhotos)
					groupCell.setGroup(groupCell.group, true)

					nextGroup.setGroup(nextGroup.group, true)
				}
			}
		}

		// private val images = HashMap<PhotoEntry, ImageReceiver>()

		init {
			setWillNotDraw(false)
			hintView = ChatActionCell(context, true)
			hintView.setCustomText(context.getString(R.string.AttachMediaDragHint))
			addView(hintView)
		}

		inner class PreviewGroupCell {
			var y = 0f
			var indexStart = 0
			private val updateDuration = 200 * durationMultiplier
			private var lastMediaUpdate: Long = 0
			private var groupWidth = 0f
			private var groupHeight = 0f
			private var previousGroupWidth = 0f
			private var previousGroupHeight = 0f
			var media: ArrayList<MediaCell>? = ArrayList()

			inner class MediaCell {
				var groupCell = this@PreviewGroupCell
				var photoEntry: PhotoEntry? = null
				var image: ImageReceiver? = null
				private var fromRect: RectF? = null
				var rect: RectF? = RectF()
				var lastUpdate: Long = 0
				val updateDuration = 200 * durationMultiplier
				var positionFlags = 0
				var fromScale = 1f
				var scale = 0f
				var fromRoundRadii: RectF? = null
				var roundRadii = RectF()
				private var videoDurationText: String? = null

				fun setImage(photoEntry: PhotoEntry?) {
					this.photoEntry = photoEntry

					videoDurationText = if (photoEntry != null && photoEntry.isVideo) {
						AndroidUtilities.formatShortDuration(photoEntry.duration)
					}
					else {
						null
					}

					if (image == null) {
						image = ImageReceiver(this@PreviewGroupsView)
					}

					if (photoEntry != null) {
						if (photoEntry.thumbPath != null) {
							image!!.setImage(ImageLocation.getForPath(photoEntry.thumbPath), null, null, null, Theme.chat_attachEmptyDrawable, 0, null, null, 0)
						}
						else if (photoEntry.path != null) {
							if (photoEntry.isVideo) {
								image!!.setImage(ImageLocation.getForPath("vthumb://" + photoEntry.imageId + ":" + photoEntry.path), null, null, null, Theme.chat_attachEmptyDrawable, 0, null, null, 0)
								image!!.allowStartAnimation = true
							}
							else {
								image!!.setOrientation(photoEntry.orientation, true)
								image!!.setImage(ImageLocation.getForPath("thumb://" + photoEntry.imageId + ":" + photoEntry.path), null, null, null, Theme.chat_attachEmptyDrawable, 0, null, null, 0)
							}
						}
						else {
							image!!.setImageBitmap(Theme.chat_attachEmptyDrawable)
						}
					}
				}

				fun layoutFrom(fromCell: MediaCell) {
					fromScale = AndroidUtilities.lerp(fromCell.fromScale, fromCell.scale, fromCell.t)

					if (fromRect == null) {
						fromRect = RectF()
					}

					val myRect = RectF()

					if (fromRect == null) {
						myRect.set(rect!!)
					}
					else {
						AndroidUtilities.lerp(fromRect, rect, t, myRect)
					}

					if (fromCell.fromRect != null) {
						AndroidUtilities.lerp(fromCell.fromRect, fromCell.rect, fromCell.t, fromRect)
						fromRect!![myRect.centerX() - fromRect!!.width() / 2 * fromCell.groupCell.width / width, myRect.centerY() - fromRect!!.height() / 2 * fromCell.groupCell.height / height, myRect.centerX() + fromRect!!.width() / 2 * fromCell.groupCell.width / width] = myRect.centerY() + fromRect!!.height() / 2 * fromCell.groupCell.height / height
					}
					else {
						fromRect!![myRect.centerX() - fromCell.rect!!.width() / 2 * fromCell.groupCell.width / width, myRect.centerY() - fromCell.rect!!.height() / 2 * fromCell.groupCell.height / height, myRect.centerX() + fromCell.rect!!.width() / 2 * fromCell.groupCell.width / width] = myRect.centerY() + fromCell.rect!!.height() / 2 * fromCell.groupCell.height / height
					}

					fromScale = AndroidUtilities.lerp(fromScale, scale, t)
					lastUpdate = SystemClock.elapsedRealtime()
				}

				fun layout(group: GroupCalculator?, pos: GroupedMessagePosition?, animated: Boolean) {
					if (group == null || pos == null) {
						if (animated) {
							val now = SystemClock.elapsedRealtime()

							fromScale = AndroidUtilities.lerp(fromScale, scale, t)

							if (fromRect != null) {
								AndroidUtilities.lerp(fromRect, rect, t, fromRect)
							}

							scale = 0f
							lastUpdate = now
						}
						else {
							fromScale = 0f
							scale = fromScale
						}

						return
					}

					positionFlags = pos.flags

					if (animated) {
						val t: Float = t

						if (fromRect != null) {
							AndroidUtilities.lerp(fromRect, rect, t, fromRect)
						}

						if (fromRoundRadii != null) {
							AndroidUtilities.lerp(fromRoundRadii, roundRadii, t, fromRoundRadii)
						}

						fromScale = AndroidUtilities.lerp(fromScale, scale, t)
						lastUpdate = SystemClock.elapsedRealtime()
					}

					val x = pos.left / group.width
					val y = pos.top / group.height
					val w = pos.pw / group.width.toFloat()
					val h = pos.ph / group.height

					scale = 1f

					rect?.set(x, y, x + w, y + h)

					val r = AndroidUtilities.dp(2f).toFloat()
					val R = AndroidUtilities.dp((SharedConfig.bubbleRadius - 1).toFloat()).toFloat()

					roundRadii[if (positionFlags and (MessageObject.POSITION_FLAG_TOP or MessageObject.POSITION_FLAG_LEFT) == MessageObject.POSITION_FLAG_TOP or MessageObject.POSITION_FLAG_LEFT) R else r, if (positionFlags and (MessageObject.POSITION_FLAG_TOP or MessageObject.POSITION_FLAG_RIGHT) == MessageObject.POSITION_FLAG_TOP or MessageObject.POSITION_FLAG_RIGHT) R else r, if (positionFlags and (MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_RIGHT) == MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_RIGHT) R else r] = if (positionFlags and (MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_LEFT) == MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_LEFT) R else r

					if (fromRect == null) {
						fromRect = RectF()
						fromRect?.set(rect!!)
					}
					if (fromRoundRadii == null) {
						fromRoundRadii = RectF()
						fromRoundRadii!!.set(roundRadii)
					}
				}

				val t: Float
					get() = interpolator.getInterpolation(min(1f, (SystemClock.elapsedRealtime() - lastUpdate) / updateDuration.toFloat()))

				fun clone(): MediaCell {
					val newMediaCell = MediaCell()
					newMediaCell.rect!!.set(rect!!)
					newMediaCell.image = image
					newMediaCell.photoEntry = photoEntry
					return newMediaCell
				}

				private val tempRect = RectF()

				@JvmOverloads
				fun rect(t: Float = this.t): RectF {
					if (rect == null || image == null) {
						tempRect[0f, 0f, 0f] = 0f
						return tempRect
					}

					var x = left + rect!!.left * width
					var y = top + rect!!.top * height
					var w = rect!!.width() * width
					var h = rect!!.height() * height

					if (t < 1f && fromRect != null) {
						x = AndroidUtilities.lerp(left + fromRect!!.left * width, x, t)
						y = AndroidUtilities.lerp(top + fromRect!!.top * height, y, t)
						w = AndroidUtilities.lerp(fromRect!!.width() * width, w, t)
						h = AndroidUtilities.lerp(fromRect!!.height() * height, h, t)
					}

					if (positionFlags and MessageObject.POSITION_FLAG_TOP == 0) {
						y += halfGap.toFloat()
						h -= halfGap.toFloat()
					}

					if (positionFlags and MessageObject.POSITION_FLAG_BOTTOM == 0) {
						h -= halfGap.toFloat()
					}

					if (positionFlags and MessageObject.POSITION_FLAG_LEFT == 0) {
						x += halfGap.toFloat()
						w -= halfGap.toFloat()
					}

					if (positionFlags and MessageObject.POSITION_FLAG_RIGHT == 0) {
						w -= halfGap.toFloat()
					}

					tempRect.set(x, y, x + w, y + h)

					return tempRect
				}

				fun drawingRect(): RectF {
					if (rect == null || image == null) {
						tempRect.set(0f, 0f, 0f, 0f)
						return tempRect
					}

					val dragging: Float = if (draggingCell != null && draggingCell!!.photoEntry === photoEntry) draggingT else 0f
					val scale = AndroidUtilities.lerp(fromScale, scale, t) * (.8f + .2f * (1f - dragging))
					val myRect = rect()
					myRect.set(myRect.left + myRect.width() * (1f - scale) / 2f, myRect.top + myRect.height() * (1f - scale) / 2f, myRect.left + myRect.width() * (1f + scale) / 2f, myRect.top + myRect.height() * (1f + scale) / 2f)
					return myRect
				}

				private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
				private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
				private var textPaint: TextPaint? = null
				private var videoDurationTextPaint: TextPaint? = null
				private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
				private var indexBitmap: Bitmap? = null
				private var indexBitmapText: String? = null
				private var videoDurationBitmap: Bitmap? = null
				private var videoDurationBitmapText: String? = null
				private val indexIn = Rect()
				private val indexOut = Rect()
				private val durationIn = Rect()
				private val durationOut = Rect()

				private fun drawPhotoIndex(canvas: Canvas, top: Float, right: Float, indexText: String?, scale: Float, alpha: Float) {
					val radius = AndroidUtilities.dp(12f)
					val strokeWidth = AndroidUtilities.dp(1.2f)
					val sz = (radius + strokeWidth) * 2
					val pad = strokeWidth * 4

					if (indexText != null && (indexBitmap == null || indexBitmapText == null || indexBitmapText != indexText)) {
						if (indexBitmap == null) {
							indexBitmap = createBitmap(sz, sz)
						}

						val bitmapCanvas = Canvas(indexBitmap!!)
						bitmapCanvas.drawColor(0x00000000)

						if (textPaint == null) {
							textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
							textPaint!!.typeface = Theme.TYPEFACE_BOLD
						}
						textPaint!!.color = ResourcesCompat.getColor(resources, R.color.brand, null) // TODO: check if this color is correct - maybe use white

						val textSize = when (indexText.length) {
							0, 1, 2 -> 14f
							3 -> 10f
							else -> 8f
						}

						textPaint!!.textSize = AndroidUtilities.dp(textSize).toFloat()
						val cx = sz / 2f
						val cy = sz / 2f

						paint.color = ResourcesCompat.getColor(resources, R.color.background, null) // TODO: check if this color is correct - maybe use white

						bitmapCanvas.drawCircle(cx.toInt().toFloat(), cy.toInt().toFloat(), radius.toFloat(), paint)
						strokePaint.color = AndroidUtilities.getOffsetColor(-0x1, ResourcesCompat.getColor(resources, R.color.brand, null), 1f, 1f) // TODO: check if this color is correct - maybe use white
						strokePaint.style = Paint.Style.STROKE
						strokePaint.strokeWidth = strokeWidth.toFloat()
						bitmapCanvas.drawCircle(cx.toInt().toFloat(), cy.toInt().toFloat(), radius.toFloat(), strokePaint)
						bitmapCanvas.drawText(indexText, cx - textPaint!!.measureText(indexText) / 2f, cy + AndroidUtilities.dp(1f) + AndroidUtilities.dp(textSize / 4f), textPaint!!)
						indexIn.set(0, 0, sz, sz)
						indexBitmapText = indexText
					}

					if (indexBitmap != null) {
						indexOut.set((right - sz * scale + pad).toInt(), (top - pad).toInt(), (right + pad).toInt(), (top - pad + sz * scale).toInt())
						bitmapPaint.alpha = (255 * alpha).toInt()
						canvas.drawBitmap(indexBitmap!!, indexIn, indexOut, bitmapPaint)
					}
				}

				private fun drawDuration(canvas: Canvas, left: Float, bottom: Float, durationText: String?, scale: Float, alpha: Float) {
					if (durationText != null) {
						if (videoDurationBitmap == null || videoDurationBitmapText == null || videoDurationBitmapText != durationText) {
							if (videoDurationTextPaint == null) {
								videoDurationTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
								videoDurationTextPaint?.typeface = Theme.TYPEFACE_BOLD
								videoDurationTextPaint?.color = -0x1
							}

							val textSize = AndroidUtilities.dp(12f).toFloat()

							videoDurationTextPaint!!.textSize = textSize

							val textWidth = videoDurationTextPaint!!.measureText(durationText)
							val width = videoPlayImage.intrinsicWidth + textWidth + AndroidUtilities.dp(15f)
							val height = max(textSize, (videoPlayImage.intrinsicHeight + AndroidUtilities.dp(4f)).toFloat())
							val w = ceil(width.toDouble()).toInt()
							val h = ceil(height.toDouble()).toInt()

							if (videoDurationBitmap == null || videoDurationBitmap!!.width != w || videoDurationBitmap!!.height != h) {
								videoDurationBitmap?.recycle()
								videoDurationBitmap = createBitmap(w, h)
							}

							val bitmapCanvas = Canvas(videoDurationBitmap!!)

							AndroidUtilities.rectTmp.set(0f, 0f, width, height)

							bitmapCanvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), Theme.chat_timeBackgroundPaint)

							val imageLeft = AndroidUtilities.dp(5f)
							val imageTop = ((height - videoPlayImage.intrinsicHeight) / 2).toInt()

							videoPlayImage.setBounds(imageLeft, imageTop, imageLeft + videoPlayImage.intrinsicWidth, imageTop + videoPlayImage.intrinsicHeight)
							videoPlayImage.draw(bitmapCanvas)

							bitmapCanvas.drawText(durationText, AndroidUtilities.dp(18f).toFloat(), textSize + AndroidUtilities.dp(-0.7f), videoDurationTextPaint!!)

							durationIn.set(0, 0, w, h)

							videoDurationBitmapText = durationText
						}

						val w = videoDurationBitmap!!.width
						val h = videoDurationBitmap!!.height
						durationOut[left.toInt(), (bottom - h * scale).toInt(), (left + w * scale).toInt()] = bottom.toInt()
						bitmapPaint.alpha = (255 * alpha).toInt()
						canvas.drawBitmap(videoDurationBitmap!!, durationIn, durationOut, bitmapPaint)
					}
				}

				private var visibleT = 1f
				private var lastVisibleTUpdate: Long = 0

				@JvmOverloads
				fun draw(canvas: Canvas, ignoreBounds: Boolean = false): Boolean {
					return draw(canvas, t, ignoreBounds)
				}

				fun draw(canvas: Canvas, t: Float, ignoreBounds: Boolean): Boolean {
					if (rect == null || image == null) {
						return false
					}

					val dragging: Float = if (draggingCell === this) draggingT else 0f
					val scale = AndroidUtilities.lerp(fromScale, scale, t)

					if (scale <= 0f) {
						return false
					}

					val drawingRect = drawingRect()
					val R = AndroidUtilities.dp((SharedConfig.bubbleRadius - 1).toFloat()).toFloat()
					var tl = roundRadii.left
					var tr = roundRadii.top
					var br = roundRadii.right
					var bl = roundRadii.bottom

					if (t < 1f && fromRoundRadii != null) {
						tl = AndroidUtilities.lerp(fromRoundRadii!!.left, tl, t)
						tr = AndroidUtilities.lerp(fromRoundRadii!!.top, tr, t)
						br = AndroidUtilities.lerp(fromRoundRadii!!.right, br, t)
						bl = AndroidUtilities.lerp(fromRoundRadii!!.bottom, bl, t)
					}

					tl = AndroidUtilities.lerp(tl, R, dragging)
					tr = AndroidUtilities.lerp(tr, R, dragging)
					br = AndroidUtilities.lerp(br, R, dragging)
					bl = AndroidUtilities.lerp(bl, R, dragging)

					if (ignoreBounds) {
						canvas.save()
						canvas.translate(-drawingRect.centerX(), -drawingRect.centerY())
					}

					image!!.setRoundRadius(tl.toInt(), tr.toInt(), br.toInt(), bl.toInt())
					image!!.setImageCoordinates(drawingRect.left, drawingRect.top, drawingRect.width(), drawingRect.height())
					image!!.alpha = scale
					image!!.draw(canvas)

					val index = indexStart + group!!.photos!!.indexOf(photoEntry)
					val indexText = if (index >= 0) (index + 1).toString() + "" else null
					val shouldVisibleT = (if (image!!.visible) 1 else 0).toFloat()
					var needVisibleTUpdate: Boolean

					if ((abs(visibleT - shouldVisibleT) > 0.01f).also { needVisibleTUpdate = it }) {
						val tx = min(17, SystemClock.elapsedRealtime() - lastVisibleTUpdate)

						lastVisibleTUpdate = SystemClock.elapsedRealtime()

						val upd = tx / 100f

						visibleT = if (shouldVisibleT < visibleT) {
							max(0f, visibleT - upd)
						}
						else {
							min(1f, visibleT + upd)
						}
					}

					drawPhotoIndex(canvas, drawingRect.top + AndroidUtilities.dp(10f), drawingRect.right - AndroidUtilities.dp(10f), indexText, scale, scale * visibleT)
					drawDuration(canvas, drawingRect.left + AndroidUtilities.dp(4f), drawingRect.bottom - AndroidUtilities.dp(4f), videoDurationText, scale, scale * visibleT)

					if (ignoreBounds) {
						canvas.restore()
					}

					return t < 1f || needVisibleTUpdate
				}
			}

			private val interpolator: Interpolator = CubicBezierInterpolator.EASE_BOTH
			var group: GroupCalculator? = null

			fun setGroup(group: GroupCalculator?, animated: Boolean) {
				this.group = group

				if (group == null) {
					return
				}

				group.calculate()

				val now = SystemClock.elapsedRealtime()

				if (now - lastMediaUpdate < updateDuration) {
					val t = (now - lastMediaUpdate) / updateDuration.toFloat()
					previousGroupHeight = AndroidUtilities.lerp(previousGroupHeight, groupHeight, t)
					previousGroupWidth = AndroidUtilities.lerp(previousGroupWidth, groupWidth, t)
				}
				else {
					previousGroupHeight = groupHeight
					previousGroupWidth = groupWidth
				}

				groupWidth = group.width / 1000f
				groupHeight = group.height
				lastMediaUpdate = if (animated) now else 0

				val photoEntries: List<PhotoEntry> = ArrayList(group.positions.keys)
				val photoEntriesCount = photoEntries.size

				for (j in 0 until photoEntriesCount) {
					val photoEntry = photoEntries[j]
					val pos = group.positions[photoEntry]
					var properCell: MediaCell? = null
					val mediaCount = media!!.size

					for (i in 0 until mediaCount) {
						val cell = media!![i]

						if (cell.photoEntry === photoEntry) {
							properCell = cell
							break
						}
					}

					if (properCell == null) {
						// new cell
						properCell = MediaCell()
						properCell.setImage(photoEntry)
						properCell.layout(group, pos, animated)
						media?.add(properCell)
					}
					else {
						properCell.layout(group, pos, animated)
					}
				}

				var mediaCount = media!!.size
				var i = 0

				while (i < mediaCount) {
					val cell = media!![i]

					if (!group.positions.containsKey(cell.photoEntry)) {
						// old cell, remove it
						if (cell.scale <= 0 && cell.lastUpdate + cell.updateDuration <= now) {
							media!!.removeAt(i)
							i--
							mediaCount--
						}
						else {
							cell.layout(null, null, animated)
						}
					}

					++i
				}

				this@PreviewGroupsView.invalidate()
			}

			val padding = AndroidUtilities.dp(4f)
			val gap = AndroidUtilities.dp(2f)
			val halfGap = gap / 2
			private var left = 0f
			private var right = 0f
			var top = 0f
			private var bottom = 0f
			private var width = 0f
			var height = 0f

			val t: Float
				get() = interpolator.getInterpolation(min(1f, (SystemClock.elapsedRealtime() - lastMediaUpdate) / updateDuration.toFloat()))

			fun measure(): Float {
				val maxHeight = max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f
				return AndroidUtilities.lerp(previousGroupHeight, groupHeight, t) * maxHeight * previewScale // height
			}

			fun maxHeight(): Float {
				val maxHeight = max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f
				return if (t >= 0.95f) groupHeight * maxHeight * previewScale else measure()
			}

			var needToUpdate = false

			fun invalidate() {
				needToUpdate = true
			}

			private val messageBackground: MessageDrawable? = getThemedDrawable(Theme.key_drawable_msgOutMedia) as? MessageDrawable
			private val backgroundCacheParams = PathDrawParams()

			fun draw(canvas: Canvas): Boolean {
				var update = false
				val t = interpolator.getInterpolation(min(1f, (SystemClock.elapsedRealtime() - lastMediaUpdate) / updateDuration.toFloat()))

				if (t < 1f) {
					update = true
				}

				val maxHeight = max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.5f
				val groupWidth: Float = AndroidUtilities.lerp(previousGroupWidth, groupWidth, t) * getWidth() * previewScale
				val groupHeight: Float = AndroidUtilities.lerp(previousGroupHeight, groupHeight, t) * maxHeight * previewScale

				if (messageBackground != null) {
					top = 0f
					left = (getWidth() - max(padding.toFloat(), groupWidth)) / 2f
					right = (getWidth() + max(padding.toFloat(), groupWidth)) / 2f
					bottom = max((padding * 2).toFloat(), groupHeight)

					messageBackground.setTop(0, groupWidth.toInt(), groupHeight.toInt(), 0, 0, topNear = false, bottomNear = false)
					messageBackground.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

					var alpha = 1f

					if (this.groupWidth <= 0) {
						alpha = 1f - t
					}
					else if (previousGroupWidth <= 0) {
						alpha = t
					}

					messageBackground.alpha = (255 * alpha).toInt()
					messageBackground.drawCached(canvas, backgroundCacheParams)

					top += padding.toFloat()
					left += padding.toFloat()
					bottom -= padding.toFloat()
					right -= padding.toFloat()
				}

				width = right - left
				height = bottom - top

				val count = media!!.size

				for (i in 0 until count) {
					val cell = media?.get(i) ?: continue

					if (draggingCell != null && draggingCell!!.photoEntry === cell.photoEntry) {
						continue
					}

					if (cell.draw(canvas)) {
						update = true
					}
				}
				return update
			}
		}
	}

	fun getThemedDrawable(drawableKey: String?): Drawable {
		return Theme.getThemeDrawable(drawableKey)
	}

	companion object {
		private val photoRotate = HashMap<PhotoEntry, Boolean>()
	}
}
