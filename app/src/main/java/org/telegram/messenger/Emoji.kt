/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.messenger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Paint.FontMetricsInt
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import androidx.core.content.edit
import org.telegram.messenger.MessagesController.Companion.getGlobalEmojiSettings
import org.telegram.ui.Components.AnimatedEmojiSpan
import java.util.Locale
import kotlin.math.abs

object Emoji {
	private val rects = mutableMapOf<CharSequence, DrawableInfo>()
	private val drawImgSize = AndroidUtilities.dp(20f)
	private val bigImgSize = AndroidUtilities.dp((if (AndroidUtilities.isTablet()) 40 else 34).toFloat())
	private val placeholderPaint: Paint
	private val emojiCounts = intArrayOf(EmojiData.data[0].size, EmojiData.data[1].size, EmojiData.data[2].size, EmojiData.data[3].size, EmojiData.data[4].size, EmojiData.data[5].size, EmojiData.data[6].size, EmojiData.data[7].size)
	private val emojiBmp = arrayOfNulls<Array<Bitmap?>>(8)
	private val loadingEmoji = arrayOfNulls<BooleanArray>(8)
	private var recentEmojiLoaded = false
	private val invalidateUiRunnable = Runnable { NotificationCenter.globalInstance.postNotificationName(NotificationCenter.emojiLoaded) }
	private const val MAX_RECENT_EMOJI_COUNT = 48
	private var emojiUseHistory = mutableMapOf<String, Int>()
	var emojiDrawingUseAlpha = true

	@JvmField
	val recentEmoji = mutableListOf<String>()

	@JvmField
	val emojiColor = mutableMapOf<String, String>()

	@JvmField
	var emojiDrawingYOffset = 0f

	init {
		for (a in emojiBmp.indices) {
			emojiBmp[a] = arrayOfNulls(emojiCounts[a])
			loadingEmoji[a] = BooleanArray(emojiCounts[a])
		}

		for (j in EmojiData.data.indices) {
			for (i in EmojiData.data[j].indices) {
				rects[EmojiData.data[j][i]] = DrawableInfo(j.toByte(), i.toShort(), i)
			}
		}

		placeholderPaint = Paint()
		placeholderPaint.setColor(0x00000000)
	}

	@JvmStatic
	fun preloadEmoji(code: CharSequence?) {
		val info = getDrawableInfo(code)

		if (info != null) {
			loadEmoji(info.page, info.page2)
		}
	}

	private fun loadEmoji(page: Byte, page2: Short) {
		if (emojiBmp[page.toInt()]?.get(page2.toInt()) == null) {
			if (loadingEmoji[page.toInt()]!![page2.toInt()]) {
				return
			}

			loadingEmoji[page.toInt()]!![page2.toInt()] = true

			Utilities.globalQueue.postRunnable {
				loadEmojiInternal(page, page2)
				loadingEmoji[page.toInt()]!![page2.toInt()] = false
			}
		}
	}

	private fun loadEmojiInternal(page: Byte, page2: Short) {
		try {
			val imageResize = if (AndroidUtilities.density <= 1.0f) {
				2
			}
			else {
				1
			}

			var bitmap: Bitmap? = null

			try {
				ApplicationLoader.applicationContext.assets.open("emoji/" + String.format(Locale.US, "%d_%d.png", page, page2)).use {
					val opts = BitmapFactory.Options()
					opts.inJustDecodeBounds = false
					opts.inSampleSize = imageResize

					bitmap = BitmapFactory.decodeStream(it, null, opts)
				}
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}

			emojiBmp[page.toInt()]?.set(page2.toInt(), bitmap)

			AndroidUtilities.cancelRunOnUIThread(invalidateUiRunnable)
			AndroidUtilities.runOnUIThread(invalidateUiRunnable)
		}
		catch (x: Throwable) {
			if (BuildConfig.DEBUG) {
				FileLog.e("Error loading emoji", x)
			}
		}
	}

	@JvmStatic
	fun fixEmoji(emoji: String): String {
		@Suppress("NAME_SHADOWING") var emoji = emoji
		var ch: Char
		var length = emoji.length
		var a = 0

		while (a < length) {
			ch = emoji[a]

			if (ch.code in 0xD83C..0xD83E) {
				if (ch.code == 0xD83C && a < length - 1) {
					ch = emoji[a + 1]

					if (ch.code == 0xDE2F || ch.code == 0xDC04 || ch.code == 0xDE1A || ch.code == 0xDD7F) {
						emoji = emoji.substring(0, a + 2) + "\uFE0F" + emoji.substring(a + 2)
						length++
						a += 2
					}
					else {
						a++
					}
				}
				else {
					a++
				}
			}
			else if (ch.code == 0x20E3) {
				return emoji
			}
			else if (ch.code in 0x203C..0x3299) {
				if (EmojiData.emojiToFE0FMap.containsKey(ch)) {
					emoji = emoji.substring(0, a + 1) + "\uFE0F" + emoji.substring(a + 1)
					length++
					a++
				}
			}

			a++
		}

		return emoji
	}

	fun getEmojiDrawableForEmojicon(emojicon: CharSequence?): EmojiDrawable? {
		val parsedEmoji = parseEmojis(emojicon, null)

		return getEmojiDrawable(parsedEmoji.firstOrNull()?.code)?.also {
			it.preload()
		}
	}

	@JvmStatic
	fun getEmojiDrawable(code: CharSequence?): EmojiDrawable? {
		if (code == null) {
			return null
		}

		val info = getDrawableInfo(code) ?: return null

		val ed = EmojiDrawable(info)
		ed.setBounds(0, 0, drawImgSize, drawImgSize)

		return ed
	}

	private fun getDrawableInfo(code: CharSequence?): DrawableInfo? {
		if (code.isNullOrEmpty()) {
			return null
		}

		var info = rects[code]

		if (info == null) {
			val newCode = EmojiData.emojiAliasMap[code]

			if (newCode != null) {
				info = rects[newCode]
			}
		}

		return info
	}

	@JvmStatic
	fun isValidEmoji(code: CharSequence?): Boolean {
		if (code.isNullOrEmpty()) {
			return false
		}

		var info = rects[code]

		if (info == null) {
			val newCode = EmojiData.emojiAliasMap[code]

			if (newCode != null) {
				info = rects[newCode]
			}
		}

		return info != null
	}

	@JvmStatic
	fun getEmojiBigDrawable(code: String?): Drawable? {
		if (code.isNullOrEmpty()) {
			return null
		}

		var ed = getEmojiDrawable(code)

		if (ed == null) {
			val newCode = EmojiData.emojiAliasMap[code]

			if (newCode != null) {
				ed = getEmojiDrawable(newCode)
			}
		}

		if (ed == null) {
			return null
		}

		ed.setBounds(0, 0, bigImgSize, bigImgSize)
		ed.fullSize = true

		return ed
	}

	@JvmStatic
	fun fullyConsistsOfEmojis(cs: CharSequence?): Boolean {
		val emojiOnly = IntArray(1)
		parseEmojis(cs, emojiOnly)
		return emojiOnly[0] > 0
	}

	fun parseEmojis(cs: CharSequence?, emojiOnly: IntArray?): List<EmojiSpanRange> {
		@Suppress("NAME_SHADOWING") var emojiOnly = emojiOnly

		val emojis = mutableListOf<EmojiSpanRange>()

		if (cs.isNullOrEmpty()) {
			return emojis
		}

		var buf: Long = 0
		var c: Char
		var startIndex = -1
		var startLength = 0
		var previousGoodIndex = 0
		val emojiCode = StringBuilder(16)
		val length = cs.length
		var doneEmoji = false
		var notOnlyEmoji: Boolean

		try {
			var i = 0

			while (i < length) {
				c = cs[i]

				notOnlyEmoji = false

				if (c.code in 0xD83C..0xD83E || buf != 0L && buf and -0x100000000L == 0L && buf and 0xFFFFL == 0xD83CL && c.code >= 0xDDE6 && c.code <= 0xDDFF) {
					if (startIndex == -1) {
						startIndex = i
					}

					emojiCode.append(c)
					startLength++
					buf = buf shl 16
					buf = buf or c.code.toLong()
				}
				else if (emojiCode.isNotEmpty() && (c.code == 0x2640 || c.code == 0x2642 || c.code == 0x2695)) {
					emojiCode.append(c)
					startLength++
					buf = 0
					doneEmoji = true
				}
				else if (buf > 0 && c.code and 0xF000 == 0xD000) {
					emojiCode.append(c)
					startLength++
					buf = 0
					doneEmoji = true
				}
				else if (c.code == 0x20E3) {
					if (i > 0) {
						val c2 = cs[previousGoodIndex]

						if (c2 in '0'..'9' || c2 == '#' || c2 == '*') {
							startIndex = previousGoodIndex
							startLength = i - previousGoodIndex + 1
							emojiCode.append(c2)
							emojiCode.append(c)
							doneEmoji = true
						}
					}
				}
				else if ((c.code == 0x00A9 || c.code == 0x00AE || c.code in 0x203C..0x3299) && EmojiData.dataCharsMap.containsKey(c)) {
					if (startIndex == -1) {
						startIndex = i
					}

					startLength++
					emojiCode.append(c)
					doneEmoji = true
				}
				else if (startIndex != -1) {
					emojiCode.setLength(0)
					startIndex = -1
					startLength = 0
				}
				else if (c.code != 0xfe0f && c != '\n' && c != ' ' && c != '\t') {
					notOnlyEmoji = true
				}
				if (doneEmoji && i + 2 < length) {
					var next = cs[i + 1]

					if (next.code == 0xD83C) {
						next = cs[i + 2]

						if (next.code in 0xDFFB..0xDFFF) {
							emojiCode.append(cs.subSequence(i + 1, i + 3))
							startLength += 2
							i += 2
						}
					}
					else if (emojiCode.length >= 2 && emojiCode[0].code == 0xD83C && emojiCode[1].code == 0xDFF4 && next.code == 0xDB40) {
						i++

						while (true) {
							emojiCode.append(cs[i]).append(cs[i + 1])
							startLength += 2
							i += 2

							if (i >= cs.length || cs[i].code != 0xDB40) {
								i--
								break
							}
						}
					}
				}

				previousGoodIndex = i

				val prevCh = c

				for (a in 0..2) {
					if (i + 1 < length) {
						c = cs[i + 1]

						if (a == 1) {
							if (c.code == 0x200D && emojiCode.isNotEmpty()) {
								notOnlyEmoji = false
								emojiCode.append(c)
								i++
								startLength++
								doneEmoji = false
							}
						}
						else if (startIndex != -1 || prevCh == '*' || prevCh == '#' || prevCh in '0'..'9') {
							if (c.code in 0xFE00..0xFE0F) {
								i++
								startLength++

								if (!doneEmoji) {
									doneEmoji = i + 1 >= length
								}
							}
						}
					}
				}

				if (notOnlyEmoji && emojiOnly != null) {
					emojiOnly[0] = 0
					emojiOnly = null
				}

				if (doneEmoji && i + 2 < length && cs[i + 1].code == 0xD83C) {
					val next = cs[i + 2]

					if (next.code in 0xDFFB..0xDFFF) {
						emojiCode.append(cs.subSequence(i + 1, i + 3))
						startLength += 2
						i += 2
					}
				}

				if (doneEmoji) {
					if (emojiOnly != null) {
						emojiOnly[0]++
					}

					emojis.add(EmojiSpanRange(startIndex, startIndex + startLength, emojiCode.subSequence(0, emojiCode.length)))
					startLength = 0
					startIndex = -1
					emojiCode.setLength(0)
					doneEmoji = false
				}

				i++
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		if (emojiOnly != null && emojiCode.isNotEmpty()) {
			emojiOnly[0] = 0
		}

		return emojis
	}

	@JvmStatic
	@JvmOverloads
	fun replaceEmoji(cs: CharSequence?, fontMetrics: FontMetricsInt?, createNew: Boolean, emojiOnly: IntArray? = null): CharSequence? {
		if (SharedConfig.useSystemEmoji || cs.isNullOrEmpty()) {
			return cs
		}

		val s = if (!createNew && cs is Spannable) {
			cs
		}
		else {
			Spannable.Factory.getInstance().newSpannable(cs.toString())
		}

		val emojis = parseEmojis(s, emojiOnly)
		val animatedEmojiSpans = s.getSpans(0, s.length, AnimatedEmojiSpan::class.java)
		var span: EmojiSpan
		var drawable: Drawable?

		for (i in emojis.indices) {
			try {
				val emojiRange = emojis[i]

				if (animatedEmojiSpans != null) {
					var hasAnimated = false

					for (animatedSpan in animatedEmojiSpans) {
						if (animatedSpan != null && s.getSpanStart(animatedSpan) == emojiRange.start && s.getSpanEnd(animatedSpan) == emojiRange.end) {
							hasAnimated = true
							break
						}
					}

					if (hasAnimated) {
						continue
					}
				}

				drawable = getEmojiDrawable(emojiRange.code)

				if (drawable != null) {
					span = EmojiSpan(drawable, DynamicDrawableSpan.ALIGN_BOTTOM, fontMetrics)
					span.emoji = emojiRange.code?.toString()

					s.setSpan(span, emojiRange.start, emojiRange.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			val limitCount = if (SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH) 100 else 50

			if (Build.VERSION.SDK_INT >= 29 && i + 1 >= limitCount) {
				break
			}
		}

		return s
	}

	@JvmStatic
	fun addRecentEmoji(code: String) {
		var count = emojiUseHistory[code]

		if (count == null) {
			count = 0
		}

		if (count == 0 && emojiUseHistory.size >= MAX_RECENT_EMOJI_COUNT) {
			val emoji = recentEmoji[recentEmoji.size - 1]
			emojiUseHistory.remove(emoji)
			recentEmoji[recentEmoji.size - 1] = code
		}

		emojiUseHistory[code] = ++count
	}

	@JvmStatic
	fun sortEmoji() {
		recentEmoji.clear()

		for ((key) in emojiUseHistory) {
			recentEmoji.add(key)
		}

		recentEmoji.sortWith { lhs, rhs ->
			var count1 = emojiUseHistory[lhs]
			var count2 = emojiUseHistory[rhs]
			if (count1 == null) {
				count1 = 0
			}
			if (count2 == null) {
				count2 = 0
			}
			if (count1 > count2) {
				return@sortWith -1
			}
			else if (count1 < count2) {
				return@sortWith 1
			}

			0
		}

		while (recentEmoji.size > MAX_RECENT_EMOJI_COUNT) {
			recentEmoji.removeAt(recentEmoji.size - 1)
		}
	}

	@JvmStatic
	fun saveRecentEmoji() {
		val preferences = getGlobalEmojiSettings()
		val stringBuilder = StringBuilder()

		for ((key, value) in emojiUseHistory) {
			if (stringBuilder.isNotEmpty()) {
				stringBuilder.append(",")
			}

			stringBuilder.append(key)
			stringBuilder.append("=")
			stringBuilder.append(value)
		}

		preferences.edit { putString("emojis2", stringBuilder.toString()) }
	}

	@JvmStatic
	fun clearRecentEmoji() {
		val preferences = getGlobalEmojiSettings()
		preferences.edit { putBoolean("filled_default", true) }

		emojiUseHistory.clear()
		recentEmoji.clear()

		saveRecentEmoji()
	}

	@JvmStatic
	fun loadRecentEmoji() {
		if (recentEmojiLoaded) {
			return
		}

		recentEmojiLoaded = true

		val preferences = getGlobalEmojiSettings()
		var str: String?

		try {
			emojiUseHistory.clear()

			if (preferences.contains("emojis")) {
				str = preferences.getString("emojis", "")

				if (!str.isNullOrEmpty()) {
					val args = str.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

					for (arg in args) {
						val args2 = arg.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
						var value = Utilities.parseLong(args2[0])
						val string = StringBuilder()

						for (a in 0..3) {
							val ch = Char(value.toUShort())
							string.insert(0, ch)
							value = value shr 16

							if (value == 0L) {
								break
							}
						}

						if (string.isNotEmpty()) {
							emojiUseHistory[string.toString()] = Utilities.parseInt(args2[1])
						}
					}
				}

				preferences.edit { remove("emojis") }

				saveRecentEmoji()
			}
			else {
				str = preferences.getString("emojis2", "")
				if (!str.isNullOrEmpty()) {
					val args = str.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

					for (arg in args) {
						val args2 = arg.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
						emojiUseHistory[args2[0]] = Utilities.parseInt(args2[1])
					}
				}
			}

			if (emojiUseHistory.isEmpty()) {
				if (!preferences.getBoolean("filled_default", false)) {
					val newRecent = arrayOf("\uD83D\uDE02", "\uD83D\uDE18", "\u2764", "\uD83D\uDE0D", "\uD83D\uDE0A", "\uD83D\uDE01", "\uD83D\uDC4D", "\u263A", "\uD83D\uDE14", "\uD83D\uDE04", "\uD83D\uDE2D", "\uD83D\uDC8B", "\uD83D\uDE12", "\uD83D\uDE33", "\uD83D\uDE1C", "\uD83D\uDE48", "\uD83D\uDE09", "\uD83D\uDE03", "\uD83D\uDE22", "\uD83D\uDE1D", "\uD83D\uDE31", "\uD83D\uDE21", "\uD83D\uDE0F", "\uD83D\uDE1E", "\uD83D\uDE05", "\uD83D\uDE1A", "\uD83D\uDE4A", "\uD83D\uDE0C", "\uD83D\uDE00", "\uD83D\uDE0B", "\uD83D\uDE06", "\uD83D\uDC4C", "\uD83D\uDE10", "\uD83D\uDE15")

					for (i in newRecent.indices) {
						emojiUseHistory[newRecent[i]] = newRecent.size - i
					}

					preferences.edit { putBoolean("filled_default", true) }

					saveRecentEmoji()
				}
			}
			sortEmoji()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		try {
			str = preferences.getString("color", "")

			if (!str.isNullOrEmpty()) {
				val args = str.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

				for (arg in args) {
					val args2 = arg.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
					emojiColor[args2[0]] = args2[1]
				}
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	@JvmStatic
	fun saveEmojiColors() {
		val preferences = getGlobalEmojiSettings()
		val stringBuilder = StringBuilder()

		for ((key, value) in emojiColor) {
			if (stringBuilder.isNotEmpty()) {
				stringBuilder.append(",")
			}

			stringBuilder.append(key)
			stringBuilder.append("=")
			stringBuilder.append(value)
		}

		preferences.edit { putString("color", stringBuilder.toString()) }
	}

	class EmojiDrawable(private val info: DrawableInfo) : Drawable() {
		var fullSize = false
		var placeholderColor = 0x10000000

		val drawRect: Rect
			get() {
				val original = getBounds()
				val cX = original.centerX()
				val cY = original.centerY()
				rect.left = cX - (if (fullSize) bigImgSize else drawImgSize) / 2
				rect.right = cX + (if (fullSize) bigImgSize else drawImgSize) / 2
				rect.top = cY - (if (fullSize) bigImgSize else drawImgSize) / 2
				rect.bottom = cY + (if (fullSize) bigImgSize else drawImgSize) / 2
				return rect
			}

		override fun draw(canvas: Canvas) {
			if (!isLoaded) {
				loadEmoji(info.page, info.page2)
				placeholderPaint.setColor(placeholderColor)

				val bounds = getBounds()

				canvas.drawCircle(bounds.centerX().toFloat(), bounds.centerY().toFloat(), bounds.width() * .4f, placeholderPaint)

				return
			}

			val bounds = if (fullSize) {
				drawRect
			}
			else {
				getBounds()
			}

			val reject = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				val rectF = RectF(bounds)
				canvas.quickReject(rectF)
			}
			else {
				@Suppress("DEPRECATION") canvas.quickReject(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), Canvas.EdgeType.AA)
			}

			if (!reject) {
				canvas.drawBitmap(emojiBmp[info.page.toInt()]?.get(info.page2.toInt())!!, null, bounds, paint)
			}
		}

		@Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSPARENT", "android.graphics.PixelFormat"))
		override fun getOpacity(): Int {
			return PixelFormat.TRANSPARENT
		}

		override fun setAlpha(alpha: Int) {
			// unused
			// MARK: this was causing emoji-only messages become invisible when there are multiple messages with same emoji
			// paint.setAlpha(alpha)
		}

		override fun setColorFilter(cf: ColorFilter?) {
			// unused
		}

		val isLoaded: Boolean
			get() = emojiBmp[info.page.toInt()]?.get(info.page2.toInt()) != null

		fun preload() {
			if (!isLoaded) {
				loadEmoji(info.page, info.page2)
			}
		}

		companion object {
			private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
			private val rect = Rect()
		}
	}

	data class DrawableInfo(val page: Byte, val page2: Short, val emojiIndex: Int)
	data class EmojiSpanRange(val start: Int, val end: Int, val code: CharSequence?)

	class EmojiSpan(d: Drawable?, verticalAlignment: Int, @JvmField var fontMetrics: FontMetricsInt?) : ImageSpan(d!!, verticalAlignment) {
		var size = AndroidUtilities.dp(20f)

		@JvmField
		var emoji: String? = null

		fun replaceFontMetrics(newMetrics: FontMetricsInt?, newSize: Int) {
			fontMetrics = newMetrics
			size = newSize
		}

		override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: FontMetricsInt?): Int {
			@Suppress("NAME_SHADOWING") val fm = fm ?: FontMetricsInt()
			val fontMetrics = fontMetrics

			return if (fontMetrics == null) {
				val sz = super.getSize(paint, text, start, end, fm)
				val offset = AndroidUtilities.dp(8f)
				val w = AndroidUtilities.dp(10f)

				fm.top = -w - offset
				fm.bottom = w - offset
				fm.ascent = -w - offset
				fm.leading = 0
				fm.descent = w - offset

				sz
			}
			else {
				fm.ascent = fontMetrics.ascent
				fm.descent = fontMetrics.descent
				fm.top = fontMetrics.top
				fm.bottom = fontMetrics.bottom

				getDrawable()?.setBounds(0, 0, size, size)

				size
			}
		}

		@JvmField
		var drawn = false

		@JvmField
		var lastDrawX = 0f

		@JvmField
		var lastDrawY = 0f

		init {
			val fontMetrics = fontMetrics

			if (fontMetrics != null) {
				size = (abs(fontMetrics.descent.toDouble()) + abs(fontMetrics.ascent.toDouble())).toInt()

				if (size == 0) {
					size = AndroidUtilities.dp(20f)
				}
			}
		}

		override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
			lastDrawX = x + size / 2f
			lastDrawY = top + (bottom - top) / 2f
			drawn = true

			var restoreAlpha = false

			if (paint.alpha != 255 && emojiDrawingUseAlpha) {
				restoreAlpha = true
				getDrawable().alpha = paint.alpha
			}

			var needRestore = false

			if (emojiDrawingYOffset != 0f) {
				needRestore = true

				canvas.save()
				canvas.translate(0f, emojiDrawingYOffset)
			}

			super.draw(canvas, text, start, end, x, top, y, bottom, paint)

			if (needRestore) {
				canvas.restore()
			}

			if (restoreAlpha) {
				getDrawable().alpha = 255
			}
		}

		override fun updateDrawState(ds: TextPaint) {
			(getDrawable() as? EmojiDrawable)?.placeholderColor = 0x10ffffff and ds.color
			super.updateDrawState(ds)
		}
	}
}
