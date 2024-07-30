/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Components

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.math.MathUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MediaController
import org.telegram.messenger.messageobject.MessageObject
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class SeekBarWaveform {
	private val appearFloat = AnimatedFloat(125, 600, CubicBezierInterpolator.EASE_OUT_QUINT)
	private val loadingFloat = AnimatedFloat(150, CubicBezierInterpolator.DEFAULT)
	private var thumbX = 0
	private var thumbDX = 0
	private var progress = 0f
	private var startX = 0f

	var isStartDraging = false
		private set
	var isDragging = false
		private set

	private var width = 0
	private var height = 0
	private var fromWidth = 0
	private var toWidth = 0
	private var delegate: SeekBar.SeekBarDelegate? = null
	private var waveformBytes: ByteArray? = null
	private var messageObject: MessageObject? = null
	private var parentView: View? = null
	private var selected = false
	private var innerColor = 0
	private var outerColor = 0
	private var selectedColor = 0
	private var clearProgress = 1f
	private var isUnread = false
	private var waveScaling = 1f
	private var path: Path? = null
	private var alphaPath: Path? = null
	private var loading = false
	private var loadingStart: Long = 0
	private var loadingPaint: Paint? = null
	private var loadingPaintWidth = 0f
	private var loadingPaintColor1 = 0
	private var loadingPaintColor2 = 0
	private var heights: FloatArray? = null
	private var fromHeights: FloatArray? = null
	private var toHeights: FloatArray? = null

	init {
		if (paintInner == null) {
			paintInner = Paint(Paint.ANTI_ALIAS_FLAG)
			paintInner?.style = Paint.Style.FILL

			paintOuter = Paint(Paint.ANTI_ALIAS_FLAG)
			paintOuter?.style = Paint.Style.FILL
		}
	}

	fun setDelegate(seekBarDelegate: SeekBar.SeekBarDelegate?) {
		delegate = seekBarDelegate
	}

	fun setColors(inner: Int, outer: Int, selected: Int) {
		innerColor = inner
		outerColor = outer
		selectedColor = selected
	}

	fun setWaveform(waveform: ByteArray?) {
		waveformBytes = waveform
		heights = calculateHeights((width / AndroidUtilities.dpf2(3f)).toInt())
	}

	fun setSelected(value: Boolean) {
		selected = value
	}

	fun setMessageObject(`object`: MessageObject?) {
		messageObject = `object`
	}

	fun setParentView(view: View?) {
		parentView = view
		loadingFloat.setParent(view)
		appearFloat.setParent(view)
	}

	fun onTouch(action: Int, x: Float, y: Float): Boolean {
		if (action == MotionEvent.ACTION_DOWN) {
			if (0 <= x && x <= width && y >= 0 && y <= height) {
				startX = x
				isDragging = true
				thumbDX = (x - thumbX).toInt()
				isStartDraging = false
				return true
			}
		}
		else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
			if (isDragging) {
				if (action == MotionEvent.ACTION_UP && delegate != null) {
					delegate?.onSeekBarDrag(thumbX.toFloat() / width.toFloat())
				}

				isDragging = false

				return true
			}
		}
		else if (action == MotionEvent.ACTION_MOVE) {
			if (isDragging) {
				if (isStartDraging) {
					thumbX = (x - thumbDX).toInt()

					if (thumbX < 0) {
						thumbX = 0
					}
					else if (thumbX > width) {
						thumbX = width
					}

					progress = thumbX / width.toFloat()
				}
				if (startX != -1f && abs(x - startX) > AndroidUtilities.getPixelsInCM(0.2f, true)) {
					parentView?.parent?.requestDisallowInterceptTouchEvent(true)
					isStartDraging = true
					startX = -1f
				}

				return true
			}
		}

		return false
	}

	fun getProgress(): Float {
		return thumbX / width.toFloat()
	}

	fun setProgress(progress: Float) {
		setProgress(progress, false)
	}

	fun setProgress(progress: Float, animated: Boolean) {
		this.progress = if (isUnread) 1f else progress

		val currentThumbX = if (isUnread) width else thumbX

		if (animated && currentThumbX != 0 && progress == 0f) {
			clearProgress = 0f
		}
		else if (!animated) {
			clearProgress = 1f
		}

		thumbX = ceil((width * progress).toDouble()).toInt()

		if (thumbX < 0) {
			thumbX = 0
		}
		else if (thumbX > width) {
			thumbX = width
		}
	}

	fun setSize(w: Int, h: Int) {
		setSize(w, h, w, w)
	}

	fun setSize(w: Int, h: Int, fromW: Int, toW: Int) {
		width = w
		height = h

		if (heights == null || heights?.size != (width / AndroidUtilities.dpf2(3f)).toInt()) {
			heights = calculateHeights((width / AndroidUtilities.dpf2(3f)).toInt())
		}

		if (fromW != toW && (fromWidth != fromW || toWidth != toW)) {
			fromWidth = fromW
			toWidth = toW
			fromHeights = calculateHeights((fromWidth / AndroidUtilities.dpf2(3f)).toInt())
			toHeights = calculateHeights((toWidth / AndroidUtilities.dpf2(3f)).toInt())
		}
		else if (fromW == toW) {
			toHeights = null
			fromHeights = null
		}
	}

	fun setSent() {
		appearFloat[0f] = true
		parentView?.invalidate()
	}

	private fun calculateHeights(count: Int): FloatArray? {
		if (waveformBytes == null || count <= 0) {
			return null
		}

		val heights = FloatArray(count)
		var value: Byte
		val samplesCount = waveformBytes!!.size * 8 / 5
		val samplesPerBar = samplesCount / count.toFloat()
		var barCounter = 0f
		var nextBarNum = 0
		var barNum = 0
		var lastBarNum: Int
		var drawBarCount: Int

		for (a in 0 until samplesCount) {
			if (a != nextBarNum) {
				continue
			}

			drawBarCount = 0
			lastBarNum = nextBarNum

			while (lastBarNum == nextBarNum) {
				barCounter += samplesPerBar
				nextBarNum = barCounter.toInt()
				drawBarCount++
			}

			val bitPointer = a * 5
			val byteNum = bitPointer / 8
			val byteBitOffset = bitPointer - byteNum * 8
			val currentByteCount = 8 - byteBitOffset
			val nextByteRest = 5 - currentByteCount

			value = ((waveformBytes!![byteNum].toInt() shr byteBitOffset) and ((2 shl min(5, currentByteCount) - 1) - 1)).toByte()

			if (nextByteRest > 0 && byteNum + 1 < waveformBytes!!.size) {
				value = (value.toInt() shl nextByteRest).toByte()
				value = value or (waveformBytes!![byteNum + 1] and ((2 shl nextByteRest - 1) - 1).toByte())
			}

			for (b in 0 until drawBarCount) {
				if (barNum >= heights.size) {
					return heights
				}

				heights[barNum++] = max(0f, 7 * value / 31.0f)
			}
		}

		return heights
	}

	fun draw(canvas: Canvas, parentView: View) {
		if (waveformBytes == null || width == 0) {
			return
		}

		val totalBarsCount = width / AndroidUtilities.dpf2(3f)

		if (totalBarsCount <= 0.1f) {
			return
		}
		if (clearProgress != 1f) {
			clearProgress += 16 / 150f

			if (clearProgress > 1f) {
				clearProgress = 1f
			}
			else {
				parentView.invalidate()
			}
		}

		val appearProgress = appearFloat.set(1f)
		if (path == null) {
			path = Path()
		}
		else {
			path?.reset()
		}

		var alpha = 0f

		if (alphaPath == null) {
			alphaPath = Path()
		}
		else {
			alphaPath?.reset()
		}

		if (fromHeights != null && toHeights != null) {
			var t = (width - fromWidth) / (toWidth - fromWidth).toFloat()

			val maxlen = max(fromHeights!!.size, toHeights!!.size)
			val minlen = min(fromHeights!!.size, toHeights!!.size)
			val minarr = if (fromHeights!!.size < toHeights!!.size) fromHeights!! else toHeights!!
			val maxarr = if (fromHeights!!.size < toHeights!!.size) toHeights!! else fromHeights!!

			//            t = CubicBezierInterpolator.EASE_OUT.getInterpolation(t);
			t = if (fromHeights!!.size < toHeights!!.size) t else 1f - t
			var k = -1

			for (barNum in 0 until maxlen) {
				val l = MathUtils.clamp(floor((barNum / maxlen.toFloat() * minlen).toDouble()).toInt(), 0, minlen - 1)
				val x = AndroidUtilities.lerp(l.toFloat(), barNum.toFloat(), t) * AndroidUtilities.dpf2(3f)
				val h = AndroidUtilities.dpf2(AndroidUtilities.lerp(minarr[l], maxarr[barNum], t))

				if (k < l) {
					addBar(path!!, x, h)
					k = l
				}
				else {
					addBar(alphaPath!!, x, h)
					alpha = t
				}
			}
		}
		else if (heights != null) {
			var barNum = 0

			while (barNum < totalBarsCount) {
				if (barNum >= heights!!.size) {
					break
				}

				val x = barNum * AndroidUtilities.dpf2(3f)
				val bart = MathUtils.clamp(appearProgress * totalBarsCount - barNum, 0f, 1f)
				var h = AndroidUtilities.dpf2(heights!![barNum]) * bart

				h -= AndroidUtilities.dpf2(1f) * (1f - bart)
				addBar(path!!, x, h)
				barNum++
			}
		}

		if (alpha > 0) {
			canvas.save()
			canvas.clipPath(alphaPath!!)
			drawFill(canvas, alpha)
			canvas.restore()
		}

		canvas.save()
		canvas.clipPath(path!!)
		drawFill(canvas, 1f)
		canvas.restore()
	}

	private fun drawFill(canvas: Canvas, alpha: Float) {
		val strokeWidth = AndroidUtilities.dpf2(2f)

		isUnread = messageObject != null && messageObject!!.isContentUnread && !messageObject!!.isOut && progress <= 0
		paintInner?.color = if (isUnread) outerColor else if (selected) selectedColor else innerColor
		paintOuter?.color = outerColor

		loadingFloat.setParent(parentView)

		val isPlaying = MediaController.getInstance().isPlayingMessage(messageObject)
		val loadingT = loadingFloat.set(if (loading && !isPlaying) 1f else 0f)

		paintInner?.color = ColorUtils.blendARGB(paintInner!!.color, innerColor, loadingT)
		paintOuter?.alpha = (paintOuter!!.alpha * (1f - loadingT) * alpha).toInt()
		paintInner?.alpha = (paintInner!!.alpha * alpha).toInt()

		canvas.drawRect(0f, 0f, width + strokeWidth, height.toFloat(), paintInner!!)

		if (loadingT < 1f) {
			canvas.drawRect(0f, 0f, progress * (width + strokeWidth) * (1f - loadingT), height.toFloat(), paintOuter!!)
		}

		if (loadingT > 0f) {
			if (loadingPaint == null || abs(loadingPaintWidth - width) > AndroidUtilities.dp(8f) || loadingPaintColor1 != innerColor || loadingPaintColor2 != outerColor) {
				if (loadingPaint == null) {
					loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
				}

				loadingPaintColor1 = innerColor
				loadingPaintColor2 = outerColor

				loadingPaint?.shader = LinearGradient(0f, 0f, width.also { loadingPaintWidth = it.toFloat() }.toFloat(), 0f, intArrayOf(loadingPaintColor1, loadingPaintColor2, loadingPaintColor1), floatArrayOf(0f, 0.2f, 0.4f), Shader.TileMode.CLAMP)
			}

			loadingPaint?.alpha = (255 * loadingT * alpha).toInt()

			canvas.save()

			var t = (SystemClock.elapsedRealtime() - loadingStart) / 270f
			t = t.toDouble().pow(0.75).toFloat()

			val dx = (t % 1.6f - .6f) * loadingPaintWidth

			canvas.translate(dx, 0f)
			canvas.drawRect(-dx, 0f, width + 5 - dx, height.toFloat(), loadingPaint!!)
			canvas.restore()

			parentView?.invalidate()
		}
	}

	private fun addBar(path: Path, x: Float, h: Float) {
		@Suppress("NAME_SHADOWING") var h = h
		val strokeWidth = AndroidUtilities.dpf2(3f)
		val y = (height - AndroidUtilities.dp(14f)) / 2
		h *= waveScaling
		AndroidUtilities.rectTmp[x + AndroidUtilities.dpf2(2f) - strokeWidth / 2f, y + AndroidUtilities.dp(7f) + (-h - strokeWidth / 2f), x + AndroidUtilities.dpf2(1f) + strokeWidth / 2f] = y + AndroidUtilities.dp(7f) + (h + strokeWidth / 2f)
		path.addRoundRect(AndroidUtilities.rectTmp, strokeWidth, strokeWidth, Path.Direction.CW)
	}

	fun setWaveScaling(waveScaling: Float) {
		this.waveScaling = waveScaling
	}

	fun setLoading(loading: Boolean) {
		if (!this.loading && loading && loadingFloat.get() <= 0) {
			loadingStart = SystemClock.elapsedRealtime()
		}

		this.loading = loading

		parentView?.invalidate()
	}

	companion object {
		private var paintInner: Paint? = null
		private var paintOuter: Paint? = null
	}
}
