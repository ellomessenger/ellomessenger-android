package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import java.util.Random
import kotlin.math.abs
import kotlin.math.min

open class FlickerLoadingView(context: Context) : View(context) {
	private val headerPaint = Paint()
	private val matrix: Matrix = Matrix()
	private val rectF = RectF()
	private var backgroundPaint: Paint? = null
	private var color0 = 0
	private var color1 = 0
	private var gradient: LinearGradient? = null
	private var gradientWidth = 0
	private var ignoreHeightCheck = false
	private var isSingleCell = false
	private var itemsCount = 1
	private var lastUpdateTime: Long = 0
	private var paddingLeft = 0
	private var paddingTop = 0
	private var parentHeight = 0
	private var parentWidth = 0
	private var parentXOffset = 0f
	private var randomParams = FloatArray(2)
	private var showDate = true
	private var skipDrawItemsCount = 0
	private var totalTranslation = 0
	private var useHeaderOffset = false
	private var viewType = 0
	private var globalGradientView: FlickerLoadingView? = null

	@JvmField
	val paint = Paint()

	@ColorInt
	private var colorKey1 = ResourcesCompat.getColor(getContext().resources, R.color.background, null)

	@ColorInt
	private var colorKey2 = ResourcesCompat.getColor(getContext().resources, R.color.light_background, null)

	@ColorInt
	private var colorKey3 = 0

	fun setViewType(type: Int) {
		viewType = type

		if (viewType == BOTS_MENU_TYPE) {
			val random = Random()

			randomParams = FloatArray(2)

			for (i in 0..1) {
				randomParams[i] = abs(random.nextInt() % 1000) / 1000f
			}
		}

		invalidate()
	}

	fun setIsSingleCell(b: Boolean) {
		isSingleCell = b
	}

	open fun getViewType(): Int {
		return viewType
	}

	open val columnsCount: Int
		get() = 2

	fun setColors(@ColorInt key1: Int, @ColorInt key2: Int, @ColorInt key3: Int) {
		colorKey1 = key1
		colorKey2 = key2
		colorKey3 = key3
		invalidate()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		if (isSingleCell) {
			if (itemsCount > 1 && ignoreHeightCheck) {
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getCellHeight(MeasureSpec.getSize(widthMeasureSpec)) * itemsCount + additionalHeight, MeasureSpec.EXACTLY))
			}
			else if (itemsCount > 1 && MeasureSpec.getSize(heightMeasureSpec) > 0) {
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(min(MeasureSpec.getSize(heightMeasureSpec), getCellHeight(MeasureSpec.getSize(widthMeasureSpec)) * itemsCount) + additionalHeight, MeasureSpec.EXACTLY))
			}
			else {
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(getCellHeight(MeasureSpec.getSize(widthMeasureSpec)) + additionalHeight, MeasureSpec.EXACTLY))
			}
		}
		else {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
	}

	open val additionalHeight: Int
		get() = 0

	override fun onDraw(canvas: Canvas) {
		var paint = paint

		globalGradientView?.let {
			(parent as? View)?.let { parent ->
				it.setParentSize(parent.measuredWidth, parent.measuredHeight, -x)
			}

			paint = it.paint
		}

		updateColors()
		updateGradient()
		var h = paddingTop

		if (useHeaderOffset) {
			h += AndroidUtilities.dp(32f)

			if (colorKey3 != 0) {
				headerPaint.color = colorKey3
			}

			canvas.drawRect(0f, 0f, measuredWidth.toFloat(), AndroidUtilities.dp(32f).toFloat(), (if (colorKey3 != 0) headerPaint else paint))
		}

		when (getViewType()) {
			DIALOG_CELL_TYPE -> {
				var k = 0

				while (h <= measuredHeight) {
					val childH = getCellHeight(measuredWidth)
					val r = AndroidUtilities.dp(28f)

					canvas.drawCircle(checkRtl((AndroidUtilities.dp(10f) + r).toFloat()), (h + (childH shr 1)).toFloat(), r.toFloat(), paint)
					rectF.set(AndroidUtilities.dp(76f).toFloat(), (h + AndroidUtilities.dp(16f)).toFloat(), AndroidUtilities.dp(148f).toFloat(), (h + AndroidUtilities.dp(24f)).toFloat())

					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF.set(AndroidUtilities.dp(76f).toFloat(), (h + AndroidUtilities.dp(38f)).toFloat(), AndroidUtilities.dp(268f).toFloat(), (h + AndroidUtilities.dp(46f)).toFloat())
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					if (SharedConfig.useThreeLinesLayout) {
						rectF[AndroidUtilities.dp(76f).toFloat(), (h + AndroidUtilities.dp((46 + 8).toFloat())).toFloat(), AndroidUtilities.dp(220f).toFloat()] = (h + AndroidUtilities.dp((46 + 8 + 8).toFloat())).toFloat()
						checkRtl(rectF)
						canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					}
					if (showDate) {
						rectF[(measuredWidth - AndroidUtilities.dp(50f)).toFloat(), (h + AndroidUtilities.dp(16f)).toFloat(), (measuredWidth - AndroidUtilities.dp(12f)).toFloat()] = (h + AndroidUtilities.dp(24f)).toFloat()
						checkRtl(rectF)
						canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					}
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			CONTACT_TYPE -> {
				var k = 0
				while (h <= measuredHeight) {
					val r = AndroidUtilities.dp(25f)
					canvas.drawCircle(checkRtl((paddingLeft + AndroidUtilities.dp(9f) + r).toFloat()), (h + AndroidUtilities.dp(32f)).toFloat(), r.toFloat(), paint)
					val textStart = 76
					val firstNameWidth = if (k % 2 == 0) 52 else 72
					rectF[AndroidUtilities.dp(textStart.toFloat()).toFloat(), (h + AndroidUtilities.dp(20f)).toFloat(), AndroidUtilities.dp((textStart + firstNameWidth).toFloat()).toFloat()] = (h + AndroidUtilities.dp(28f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp((textStart + firstNameWidth + 8).toFloat()).toFloat(), (h + AndroidUtilities.dp(20f)).toFloat(), AndroidUtilities.dp((textStart + firstNameWidth + 8 + 84).toFloat()).toFloat()] = (h + AndroidUtilities.dp(28f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(textStart.toFloat()).toFloat(), (h + AndroidUtilities.dp(42f)).toFloat(), AndroidUtilities.dp((textStart + 64).toFloat()).toFloat()] = (h + AndroidUtilities.dp(50f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					canvas.drawLine(AndroidUtilities.dp(textStart.toFloat()).toFloat(), (h + getCellHeight(measuredWidth)).toFloat(), measuredWidth.toFloat(), (h + getCellHeight(measuredWidth)).toFloat(), paint)
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			STICKERS_TYPE -> {
				var k = 0
				while (h <= measuredHeight) {
					val r = AndroidUtilities.dp(20f)
					canvas.drawCircle(checkRtl((paddingLeft + AndroidUtilities.dp(9f) + r).toFloat()), (h + AndroidUtilities.dp(29f)).toFloat(), r.toFloat(), paint)
					val textStart = 76
					val titleWidth = if (k % 2 == 0) 92 else 128
					rectF[AndroidUtilities.dp(textStart.toFloat()).toFloat(), (h + AndroidUtilities.dp(16f)).toFloat(), AndroidUtilities.dp((textStart + titleWidth).toFloat()).toFloat()] = (h + AndroidUtilities.dp(24f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(textStart.toFloat()).toFloat(), (h + AndroidUtilities.dp(38f)).toFloat(), AndroidUtilities.dp((textStart + 164).toFloat()).toFloat()] = (h + AndroidUtilities.dp(46f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					canvas.drawLine(AndroidUtilities.dp(textStart.toFloat()).toFloat(), (h + getCellHeight(measuredWidth)).toFloat(), measuredWidth.toFloat(), (h + getCellHeight(measuredWidth)).toFloat(), paint)
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			DIALOG_TYPE -> {
				var k = 0
				while (h <= measuredHeight) {
					val r = AndroidUtilities.dp(25f)
					canvas.drawCircle(checkRtl((AndroidUtilities.dp(9f) + r).toFloat()), (h + (AndroidUtilities.dp(78f) shr 1)).toFloat(), r.toFloat(), paint)
					rectF[AndroidUtilities.dp(68f).toFloat(), (h + AndroidUtilities.dp(20f)).toFloat(), AndroidUtilities.dp(140f).toFloat()] = (h + AndroidUtilities.dp(28f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(68f).toFloat(), (h + AndroidUtilities.dp(42f)).toFloat(), AndroidUtilities.dp(260f).toFloat()] = (h + AndroidUtilities.dp(50f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					if (showDate) {
						rectF[(measuredWidth - AndroidUtilities.dp(50f)).toFloat(), (h + AndroidUtilities.dp(20f)).toFloat(), (measuredWidth - AndroidUtilities.dp(12f)).toFloat()] = (h + AndroidUtilities.dp(28f)).toFloat()
						checkRtl(rectF)
						canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					}
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			PHOTOS_TYPE -> {
				val photoWidth = (measuredWidth - AndroidUtilities.dp(2f) * (columnsCount - 1)) / columnsCount
				var k = 0
				while (h < measuredHeight || isSingleCell) {
					for (i in 0 until columnsCount) {
						if (k == 0 && i < skipDrawItemsCount) {
							continue
						}
						val x = i * (photoWidth + AndroidUtilities.dp(2f))
						canvas.drawRect(x.toFloat(), h.toFloat(), (x + photoWidth).toFloat(), (h + photoWidth).toFloat(), paint)
					}
					h += photoWidth + AndroidUtilities.dp(2f)
					k++
					if (isSingleCell && k >= 2) {
						break
					}
				}
			}

			FILES_TYPE -> {
				var k = 0
				while (h <= measuredHeight) {
					rectF[AndroidUtilities.dp(12f).toFloat(), (h + AndroidUtilities.dp(8f)).toFloat(), AndroidUtilities.dp(52f).toFloat()] = (h + AndroidUtilities.dp(48f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(68f).toFloat(), (h + AndroidUtilities.dp(12f)).toFloat(), AndroidUtilities.dp(140f).toFloat()] = (h + AndroidUtilities.dp(20f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(68f).toFloat(), (h + AndroidUtilities.dp(34f)).toFloat(), AndroidUtilities.dp(260f).toFloat()] = (h + AndroidUtilities.dp(42f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					if (showDate) {
						rectF[(measuredWidth - AndroidUtilities.dp(50f)).toFloat(), (h + AndroidUtilities.dp(12f)).toFloat(), (measuredWidth - AndroidUtilities.dp(12f)).toFloat()] = (h + AndroidUtilities.dp(20f)).toFloat()
						checkRtl(rectF)
						canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					}
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			AUDIO_TYPE -> {
				var k = 0
				while (h <= measuredHeight) {
					val radius = AndroidUtilities.dp(44f) shr 1
					canvas.drawCircle(checkRtl((AndroidUtilities.dp(12f) + radius).toFloat()), (h + AndroidUtilities.dp(6f) + radius).toFloat(), radius.toFloat(), paint)
					rectF[AndroidUtilities.dp(68f).toFloat(), (h + AndroidUtilities.dp(12f)).toFloat(), AndroidUtilities.dp(140f).toFloat()] = (h + AndroidUtilities.dp(20f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(68f).toFloat(), (h + AndroidUtilities.dp(34f)).toFloat(), AndroidUtilities.dp(260f).toFloat()] = (h + AndroidUtilities.dp(42f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					if (showDate) {
						rectF[(measuredWidth - AndroidUtilities.dp(50f)).toFloat(), (h + AndroidUtilities.dp(12f)).toFloat(), (measuredWidth - AndroidUtilities.dp(12f)).toFloat()] = (h + AndroidUtilities.dp(20f)).toFloat()
						checkRtl(rectF)
						canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					}
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			LINKS_TYPE -> {
				var k = 0
				while (h <= measuredHeight) {
					rectF[AndroidUtilities.dp(10f).toFloat(), (h + AndroidUtilities.dp(11f)).toFloat(), AndroidUtilities.dp(62f).toFloat()] = (h + AndroidUtilities.dp((11 + 52).toFloat())).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(68f).toFloat(), (h + AndroidUtilities.dp(12f)).toFloat(), AndroidUtilities.dp(140f).toFloat()] = (h + AndroidUtilities.dp(20f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(68f).toFloat(), (h + AndroidUtilities.dp(34f)).toFloat(), AndroidUtilities.dp(268f).toFloat()] = (h + AndroidUtilities.dp(42f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(68f).toFloat(), (h + AndroidUtilities.dp((34 + 20).toFloat())).toFloat(), AndroidUtilities.dp((120 + 68).toFloat()).toFloat()] = (h + AndroidUtilities.dp((42 + 20).toFloat())).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					if (showDate) {
						rectF[(measuredWidth - AndroidUtilities.dp(50f)).toFloat(), (h + AndroidUtilities.dp(12f)).toFloat(), (measuredWidth - AndroidUtilities.dp(12f)).toFloat()] = (h + AndroidUtilities.dp(20f)).toFloat()
						checkRtl(rectF)
						canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					}
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			USERS_TYPE, USERS2_TYPE -> {
				var k = 0
				while (h <= measuredHeight) {
					val r = AndroidUtilities.dp(23f)
					canvas.drawCircle(checkRtl((paddingLeft + AndroidUtilities.dp(9f) + r).toFloat()), (h + (AndroidUtilities.dp(64f) shr 1)).toFloat(), r.toFloat(), paint)
					rectF[(paddingLeft + AndroidUtilities.dp(68f)).toFloat(), (h + AndroidUtilities.dp(17f)).toFloat(), (paddingLeft + AndroidUtilities.dp(260f)).toFloat()] = (h + AndroidUtilities.dp(25f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[(paddingLeft + AndroidUtilities.dp(68f)).toFloat(), (h + AndroidUtilities.dp(39f)).toFloat(), (paddingLeft + AndroidUtilities.dp(140f)).toFloat()] = (h + AndroidUtilities.dp(47f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					if (showDate) {
						rectF[(measuredWidth - AndroidUtilities.dp(50f)).toFloat(), (h + AndroidUtilities.dp(20f)).toFloat(), (measuredWidth - AndroidUtilities.dp(12f)).toFloat()] = (h + AndroidUtilities.dp(28f)).toFloat()
						checkRtl(rectF)
						canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					}
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			CALL_LOG_TYPE -> {
				var k = 0
				while (h <= measuredHeight) {
					val r = AndroidUtilities.dp(23f)
					canvas.drawCircle(checkRtl((paddingLeft + AndroidUtilities.dp(11f) + r).toFloat()), (h + (AndroidUtilities.dp(64f) shr 1)).toFloat(), r.toFloat(), paint)
					rectF[(paddingLeft + AndroidUtilities.dp(68f)).toFloat(), (h + AndroidUtilities.dp(17f)).toFloat(), (paddingLeft + AndroidUtilities.dp(140f)).toFloat()] = (h + AndroidUtilities.dp(25f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[(paddingLeft + AndroidUtilities.dp(68f)).toFloat(), (h + AndroidUtilities.dp(39f)).toFloat(), (paddingLeft + AndroidUtilities.dp(260f)).toFloat()] = (h + AndroidUtilities.dp(47f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					if (showDate) {
						rectF[(measuredWidth - AndroidUtilities.dp(50f)).toFloat(), (h + AndroidUtilities.dp(20f)).toFloat(), (measuredWidth - AndroidUtilities.dp(12f)).toFloat()] = (h + AndroidUtilities.dp(28f)).toFloat()
						checkRtl(rectF)
						canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					}
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			INVITE_LINKS_TYPE -> {
				var k = 0
				while (h <= measuredHeight) {
					val childH = getCellHeight(measuredWidth)
					val r = AndroidUtilities.dp(32f) / 2
					canvas.drawCircle(checkRtl(AndroidUtilities.dp(35f).toFloat()), (h + (childH shr 1)).toFloat(), r.toFloat(), paint)
					rectF[AndroidUtilities.dp(72f).toFloat(), (h + AndroidUtilities.dp(16f)).toFloat(), AndroidUtilities.dp(268f).toFloat()] = (h + AndroidUtilities.dp(24f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(72f).toFloat(), (h + AndroidUtilities.dp(38f)).toFloat(), AndroidUtilities.dp(140f).toFloat()] = (h + AndroidUtilities.dp(46f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					if (showDate) {
						rectF[(measuredWidth - AndroidUtilities.dp(50f)).toFloat(), (h + AndroidUtilities.dp(16f)).toFloat(), (measuredWidth - AndroidUtilities.dp(12f)).toFloat()] = (h + AndroidUtilities.dp(24f)).toFloat()
						checkRtl(rectF)
						canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					}
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			BOTS_MENU_TYPE -> {
				var k = 0
				while (h <= measuredHeight) {
					rectF[AndroidUtilities.dp(18f).toFloat(), AndroidUtilities.dp((36 - 8) / 2f).toFloat(), measuredWidth * 0.5f + AndroidUtilities.dp(40 * randomParams[0])] = (AndroidUtilities.dp((36 - 8) / 2f) + AndroidUtilities.dp(8f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[(measuredWidth - AndroidUtilities.dp(18f)).toFloat(), AndroidUtilities.dp((36 - 8) / 2f).toFloat(), measuredWidth - measuredWidth * 0.2f - AndroidUtilities.dp(20 * randomParams[0])] = (AndroidUtilities.dp((36 - 8) / 2f) + AndroidUtilities.dp(8f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			SHARE_ALERT_TYPE -> {
				h += AndroidUtilities.dp(14f)
				while (h <= measuredHeight) {
					val part = measuredWidth / 4
					for (i in 0..3) {
						val cx = part * i + part / 2f
						val cy = h + AndroidUtilities.dp(7f) + AndroidUtilities.dp(56f) / 2f
						canvas.drawCircle(cx, cy, AndroidUtilities.dp(56 / 2f).toFloat(), paint)
						val y = (h + AndroidUtilities.dp(7f) + AndroidUtilities.dp(56f) + AndroidUtilities.dp(16f)).toFloat()
						AndroidUtilities.rectTmp[cx - AndroidUtilities.dp(24f), y - AndroidUtilities.dp(4f), cx + AndroidUtilities.dp(24f)] = y + AndroidUtilities.dp(4f)
						canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					}
					h += getCellHeight(measuredWidth)
					if (isSingleCell) {
						break
					}
				}
			}

			MESSAGE_SEEN_TYPE -> {
				val cy = measuredHeight / 2f
				AndroidUtilities.rectTmp[AndroidUtilities.dp(40f).toFloat(), cy - AndroidUtilities.dp(4f), (measuredWidth - AndroidUtilities.dp(120f)).toFloat()] = cy + AndroidUtilities.dp(4f)
				canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
				if (backgroundPaint == null) {
					backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
					backgroundPaint?.color = ResourcesCompat.getColor(context.resources, R.color.light_background, null)
				}
				for (i in 0..2) {
					canvas.drawCircle((measuredWidth - AndroidUtilities.dp((8 + 24 + 12 + 12).toFloat()) + AndroidUtilities.dp(13f) + AndroidUtilities.dp(12f) * i).toFloat(), cy, AndroidUtilities.dp(13f).toFloat(), backgroundPaint!!)
					canvas.drawCircle((measuredWidth - AndroidUtilities.dp((8 + 24 + 12 + 12).toFloat()) + AndroidUtilities.dp(13f) + AndroidUtilities.dp(12f) * i).toFloat(), cy, AndroidUtilities.dp(12f).toFloat(), paint)
				}
			}

			CHAT_THEMES_TYPE, QR_TYPE -> {
				var x = AndroidUtilities.dp(12f)
				val itemWidth = AndroidUtilities.dp(77f)
				val innerRectSpace = AndroidUtilities.dp(4f)
				val bubbleHeight = AndroidUtilities.dp(21f).toFloat()
				val bubbleWidth = AndroidUtilities.dp(41f).toFloat()
				while (x < measuredWidth) {
					if (backgroundPaint == null) {
						backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
						backgroundPaint?.color = ResourcesCompat.getColor(context.resources, R.color.background, null)
					}
					AndroidUtilities.rectTmp[(x + AndroidUtilities.dp(4f)).toFloat(), AndroidUtilities.dp(4f).toFloat(), (x + itemWidth - AndroidUtilities.dp(4f)).toFloat()] = (measuredHeight - AndroidUtilities.dp(4f)).toFloat()
					canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(6f).toFloat(), paint)
					if (getViewType() == CHAT_THEMES_TYPE) {
						var bubbleTop = (innerRectSpace + AndroidUtilities.dp(8f)).toFloat()
						var bubbleLeft = (innerRectSpace + AndroidUtilities.dp(22f)).toFloat()
						rectF[x + bubbleLeft, bubbleTop, x + bubbleLeft + bubbleWidth] = bubbleTop + bubbleHeight
						canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, backgroundPaint!!)
						bubbleLeft = (innerRectSpace + AndroidUtilities.dp(5f)).toFloat()
						bubbleTop += bubbleHeight + AndroidUtilities.dp(4f)
						rectF[x + bubbleLeft, bubbleTop, x + bubbleLeft + bubbleWidth] = bubbleTop + bubbleHeight
						canvas.drawRoundRect(rectF, rectF.height() * 0.5f, rectF.height() * 0.5f, backgroundPaint!!)
					}
					else if (getViewType() == QR_TYPE) {
						val radius = AndroidUtilities.dp(5f).toFloat()
						val squareSize = AndroidUtilities.dp(32f).toFloat()
						val left = x + (itemWidth - squareSize) / 2
						val top = AndroidUtilities.dp(21f)
						AndroidUtilities.rectTmp[left, top.toFloat(), left + squareSize] = (top + AndroidUtilities.dp(32f)).toFloat()
						canvas.drawRoundRect(AndroidUtilities.rectTmp, radius, radius, backgroundPaint!!)
					}
					canvas.drawCircle(x + itemWidth / 2f, (measuredHeight - AndroidUtilities.dp(20f)).toFloat(), AndroidUtilities.dp(8f).toFloat(), backgroundPaint!!)
					x += itemWidth
				}
			}

			MEMBER_REQUESTS_TYPE -> {
				var count = 0
				val radius = AndroidUtilities.dp(23f)
				val rectRadius = AndroidUtilities.dp(4f)
				while (h <= measuredHeight) {
					canvas.drawCircle(checkRtl((paddingLeft + AndroidUtilities.dp(12f)).toFloat()) + radius, (h + AndroidUtilities.dp(8f) + radius).toFloat(), radius.toFloat(), paint)
					rectF[(paddingLeft + AndroidUtilities.dp(74f)).toFloat(), (h + AndroidUtilities.dp(12f)).toFloat(), (paddingLeft + AndroidUtilities.dp(260f)).toFloat()] = (h + AndroidUtilities.dp(20f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, rectRadius.toFloat(), rectRadius.toFloat(), paint)
					rectF[(paddingLeft + AndroidUtilities.dp(74f)).toFloat(), (h + AndroidUtilities.dp(36f)).toFloat(), (paddingLeft + AndroidUtilities.dp(140f)).toFloat()] = (h + AndroidUtilities.dp(42f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, rectRadius.toFloat(), rectRadius.toFloat(), paint)
					h += getCellHeight(measuredWidth)
					count++
					if (isSingleCell && count >= itemsCount) {
						break
					}
				}
			}

			REACTED_TYPE, REACTED_TYPE_WITH_EMOJI_HINT -> {
				var k = 0
				while (h <= measuredHeight) {
					var r = AndroidUtilities.dp(18f)
					canvas.drawCircle(checkRtl((paddingLeft + AndroidUtilities.dp(8f) + r).toFloat()), (h + AndroidUtilities.dp(24f)).toFloat(), r.toFloat(), paint)
					rectF[(paddingLeft + AndroidUtilities.dp(58f)).toFloat(), (h + AndroidUtilities.dp(20f)).toFloat(), (width - AndroidUtilities.dp(53f)).toFloat()] = (h + AndroidUtilities.dp(28f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(8f).toFloat(), AndroidUtilities.dp(8f).toFloat(), paint)
					if (k < 4) {
						r = AndroidUtilities.dp(12f)
						canvas.drawCircle(checkRtl((width - AndroidUtilities.dp(12f) - r).toFloat()), (h + AndroidUtilities.dp(24f)).toFloat(), r.toFloat(), paint)
					}
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
				rectF[(paddingLeft + AndroidUtilities.dp(8f)).toFloat(), (h + AndroidUtilities.dp(20f)).toFloat(), (width - AndroidUtilities.dp(8f)).toFloat()] = (h + AndroidUtilities.dp(28f)).toFloat()
				checkRtl(rectF)
				canvas.drawRoundRect(rectF, AndroidUtilities.dp(8f).toFloat(), AndroidUtilities.dp(8f).toFloat(), paint)
				rectF[(paddingLeft + AndroidUtilities.dp(8f)).toFloat(), (h + AndroidUtilities.dp(36f)).toFloat(), (width - AndroidUtilities.dp(53f)).toFloat()] = (h + AndroidUtilities.dp(44f)).toFloat()
				checkRtl(rectF)
				canvas.drawRoundRect(rectF, AndroidUtilities.dp(8f).toFloat(), AndroidUtilities.dp(8f).toFloat(), paint)
			}

			LIMIT_REACHED_GROUPS -> {
				var k = 0
				while (h <= measuredHeight) {
					val r = AndroidUtilities.dp(46f) shr 1
					canvas.drawCircle(checkRtl((AndroidUtilities.dp(20f) + r).toFloat()), (h + (AndroidUtilities.dp(58f) shr 1)).toFloat(), r.toFloat(), paint)
					rectF[AndroidUtilities.dp(74f).toFloat(), (h + AndroidUtilities.dp(16f)).toFloat(), AndroidUtilities.dp(140f).toFloat()] = (h + AndroidUtilities.dp(24f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(74f).toFloat(), (h + AndroidUtilities.dp(38f)).toFloat(), AndroidUtilities.dp(260f).toFloat()] = (h + AndroidUtilities.dp(46f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}

			LIMIT_REACHED_LINKS -> {
				var k = 0
				while (h <= measuredHeight) {
					val r = AndroidUtilities.dp(48f) shr 1
					canvas.drawCircle(checkRtl((AndroidUtilities.dp(20f) + r).toFloat()), (h + AndroidUtilities.dp(6f) + r).toFloat(), r.toFloat(), paint)
					rectF[AndroidUtilities.dp(76f).toFloat(), (h + AndroidUtilities.dp(16f)).toFloat(), AndroidUtilities.dp(140f).toFloat()] = (h + AndroidUtilities.dp(24f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					rectF[AndroidUtilities.dp(76f).toFloat(), (h + AndroidUtilities.dp(38f)).toFloat(), AndroidUtilities.dp(260f).toFloat()] = (h + AndroidUtilities.dp(46f)).toFloat()
					checkRtl(rectF)
					canvas.drawRoundRect(rectF, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), paint)
					h += getCellHeight(measuredWidth)
					k++
					if (isSingleCell && k >= itemsCount) {
						break
					}
				}
			}
		}

		invalidate()
	}

	fun updateGradient() {
		if (globalGradientView != null) {
			globalGradientView!!.updateGradient()
			return
		}
		val newUpdateTime = SystemClock.elapsedRealtime()
		var dt = abs(lastUpdateTime - newUpdateTime)
		if (dt > 17) {
			dt = 16
		}
		if (dt < 4) {
			dt = 0
		}
		var width = parentWidth
		if (width == 0) {
			width = measuredWidth
		}
		var height = parentHeight
		if (height == 0) {
			height = measuredHeight
		}
		lastUpdateTime = newUpdateTime
		if (isSingleCell || viewType == MESSAGE_SEEN_TYPE || getViewType() == CHAT_THEMES_TYPE || getViewType() == QR_TYPE) {
			totalTranslation += (dt * width / 400.0f).toInt()
			if (totalTranslation >= width * 2) {
				totalTranslation = -gradientWidth * 2
			}
			matrix.setTranslate(totalTranslation + parentXOffset, 0f)
		}
		else {
			totalTranslation += (dt * height / 400.0f).toInt()
			if (totalTranslation >= height * 2) {
				totalTranslation = -gradientWidth * 2
			}
			matrix.setTranslate(parentXOffset, totalTranslation.toFloat())
		}
		if (gradient != null) {
			gradient!!.setLocalMatrix(matrix)
		}
	}

	fun updateColors() {
		if (globalGradientView != null) {
			globalGradientView!!.updateColors()
			return
		}
		val color0 = colorKey1
		val color1 = colorKey2
		if (this.color1 != color1 || this.color0 != color0) {
			this.color0 = color0
			this.color1 = color1
			if (isSingleCell || viewType == MESSAGE_SEEN_TYPE || viewType == CHAT_THEMES_TYPE || viewType == QR_TYPE) {
				gradient = LinearGradient(0f, 0f, AndroidUtilities.dp(200f).also { gradientWidth = it }.toFloat(), 0f, intArrayOf(color1, color0, color0, color1), floatArrayOf(0.0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP)
			}
			else {
				gradient = LinearGradient(0f, 0f, 0f, AndroidUtilities.dp(600f).also { gradientWidth = it }.toFloat(), intArrayOf(color1, color0, color0, color1), floatArrayOf(0.0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP)
			}
			paint.shader = gradient
		}
	}

	private fun checkRtl(x: Float): Float {
		return if (LocaleController.isRTL) {
			measuredWidth - x
		}
		else x
	}

	private fun checkRtl(rectF: RectF) {
		if (LocaleController.isRTL) {
			rectF.left = measuredWidth - rectF.left
			rectF.right = measuredWidth - rectF.right
		}
	}

	private fun getCellHeight(width: Int): Int {
		when (getViewType()) {
			DIALOG_CELL_TYPE -> return AndroidUtilities.dp(((if (SharedConfig.useThreeLinesLayout) 78 else 72) + 1).toFloat())
			DIALOG_TYPE -> return AndroidUtilities.dp(78f) + 1
			PHOTOS_TYPE -> {
				val photoWidth = (width - AndroidUtilities.dp(2f) * (columnsCount - 1)) / columnsCount
				return photoWidth + AndroidUtilities.dp(2f)
			}

			FILES_TYPE, AUDIO_TYPE -> return AndroidUtilities.dp(56f)
			LINKS_TYPE -> return AndroidUtilities.dp(80f)
			STICKERS_TYPE, USERS2_TYPE, LIMIT_REACHED_GROUPS -> return AndroidUtilities.dp(58f)
			USERS_TYPE, CONTACT_TYPE -> return AndroidUtilities.dp(64f)
			INVITE_LINKS_TYPE -> return AndroidUtilities.dp(66f)
			CALL_LOG_TYPE -> return AndroidUtilities.dp(61f)
			BOTS_MENU_TYPE -> return AndroidUtilities.dp(36f)
			SHARE_ALERT_TYPE -> return AndroidUtilities.dp(103f)
			MEMBER_REQUESTS_TYPE -> return AndroidUtilities.dp(107f)
			REACTED_TYPE_WITH_EMOJI_HINT, REACTED_TYPE -> return AndroidUtilities.dp(48f)
			LIMIT_REACHED_LINKS -> return AndroidUtilities.dp(60f)
		}
		return 0
	}

	fun showDate(showDate: Boolean) {
		this.showDate = showDate
	}

	fun setUseHeaderOffset(useHeaderOffset: Boolean) {
		this.useHeaderOffset = useHeaderOffset
	}

	fun skipDrawItemsCount(i: Int) {
		skipDrawItemsCount = i
	}

	fun setPaddingTop(t: Int) {
		paddingTop = t
		invalidate()
	}

	fun setPaddingLeft(paddingLeft: Int) {
		this.paddingLeft = paddingLeft
		invalidate()
	}

	fun setItemsCount(i: Int) {
		itemsCount = i
	}

	fun setGlobalGradientView(globalGradientView: FlickerLoadingView?) {
		this.globalGradientView = globalGradientView
	}

	fun setParentSize(parentWidth: Int, parentHeight: Int, parentXOffset: Float) {
		this.parentWidth = parentWidth
		this.parentHeight = parentHeight
		this.parentXOffset = parentXOffset
	}

	fun setIgnoreHeightCheck(ignore: Boolean) {
		ignoreHeightCheck = ignore
	}

	companion object {
		const val DIALOG_TYPE = 1
		const val PHOTOS_TYPE = 2
		const val FILES_TYPE = 3
		const val AUDIO_TYPE = 4
		const val LINKS_TYPE = 5
		const val USERS_TYPE = 6
		const val DIALOG_CELL_TYPE = 7
		const val CALL_LOG_TYPE = 8
		const val INVITE_LINKS_TYPE = 9
		const val USERS2_TYPE = 10
		const val BOTS_MENU_TYPE = 11
		const val SHARE_ALERT_TYPE = 12
		const val MESSAGE_SEEN_TYPE = 13
		const val CHAT_THEMES_TYPE = 14
		const val MEMBER_REQUESTS_TYPE = 15
		const val REACTED_TYPE = 16
		const val QR_TYPE = 17
		const val CONTACT_TYPE = 18
		const val STICKERS_TYPE = 19
		const val LIMIT_REACHED_GROUPS = 21
		const val LIMIT_REACHED_LINKS = 22
		const val REACTED_TYPE_WITH_EMOJI_HINT = 23
	}
}