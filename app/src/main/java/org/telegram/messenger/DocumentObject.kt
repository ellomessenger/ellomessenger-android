/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.messenger

import android.graphics.Paint
import android.graphics.Path
import org.telegram.messenger.SvgHelper.SvgDrawable
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.TL_document
import org.telegram.tgnet.TLRPC.TL_documentAttributeImageSize
import org.telegram.tgnet.TLRPC.TL_photoPathSize
import org.telegram.tgnet.TLRPC.TL_wallPaper
import org.telegram.tgnet.TLRPC.ThemeSettings
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ActionBar.Theme.ThemeAccent
import org.telegram.ui.ActionBar.Theme.ThemeInfo

object DocumentObject {
	@JvmStatic
	fun getSvgThumb(sizes: ArrayList<PhotoSize>?, color: Int, alpha: Float): SvgDrawable? {
		if (sizes.isNullOrEmpty()) {
			return null
		}

		var w = 0
		var h = 0
		var photoPathSize: TL_photoPathSize? = null

		for (photoSize in sizes) {
			if (photoSize is TL_photoPathSize) {
				photoPathSize = photoSize
			}
			else {
				w = photoSize.w
				h = photoSize.h
			}

			if (photoPathSize != null && w != 0 && h != 0) {
				val pathThumb = SvgHelper.getDrawableByPath(SvgHelper.decompress(photoPathSize.bytes), w, h)
				pathThumb?.setupGradient(color, alpha, false)
				return pathThumb
			}
		}

		return null
	}

	@JvmStatic
	fun getCircleThumb(radius: Float, color: Int, alpha: Float): SvgDrawable? {
		return try {
			val drawable = SvgDrawable()
			val circle = SvgHelper.Circle(256f, 256f, radius * 512)
			drawable.commands.add(circle)
			drawable.paints[circle] = Paint(Paint.ANTI_ALIAS_FLAG)
			drawable.width = 512
			drawable.height = 512
			drawable.setupGradient(color, alpha, false)
			drawable
		}
		catch (e: Exception) {
			FileLog.e(e)
			null
		}
	}

	@JvmStatic
	fun getSvgThumb(document: TLRPC.Document?, color: Int, alpha: Float): SvgDrawable? {
		return getSvgThumb(document, color, alpha, 1.0f)
	}

	@JvmStatic
	fun getSvgRectThumb(color: Int, alpha: Float): SvgDrawable {
		val path = Path()
		path.addRect(0f, 0f, 512f, 512f, Path.Direction.CW)
		path.close()

		val drawable = SvgDrawable()
		drawable.commands.add(path)
		drawable.paints[path] = Paint(Paint.ANTI_ALIAS_FLAG)
		drawable.width = 512
		drawable.height = 512
		drawable.setupGradient(color, alpha, false)

		return drawable
	}

	@JvmStatic
	fun getSvgThumb(document: TLRPC.Document?, color: Int, alpha: Float, zoom: Float): SvgDrawable? {
		if (document == null) {
			return null
		}

		var pathThumb: SvgDrawable? = null

		for (size in document.thumbs) {
			if (size is TL_photoPathSize) {
				var w = 512
				var h = 512

				for (attribute in document.attributes) {
					if (attribute is TL_documentAttributeImageSize) {
						w = attribute.w
						h = attribute.h
						break
					}
				}

				if (w != 0 && h != 0) {
					pathThumb = SvgHelper.getDrawableByPath(SvgHelper.decompress(size.bytes), (w * zoom).toInt(), (h * zoom).toInt())
					pathThumb?.setupGradient(color, alpha, false)
				}

				break
			}
		}

		return pathThumb
	}

	class ThemeDocument(@JvmField var themeSettings: ThemeSettings) : TL_document() {
		@JvmField
		var wallpaper: TLRPC.Document? = null

		@JvmField
		var baseTheme: ThemeInfo = Theme.getTheme(Theme.getBaseThemeKey(themeSettings))

		@JvmField
		var accent: ThemeAccent = baseTheme.createNewAccent(themeSettings)

		init {
			if (themeSettings.wallpaper is TL_wallPaper) {
				val `object` = themeSettings.wallpaper as TL_wallPaper

				val wallpaper = `object`.document
				this.wallpaper = wallpaper

				id = wallpaper.id
				access_hash = wallpaper.access_hash
				file_reference = wallpaper.file_reference
				user_id = wallpaper.user_id
				date = wallpaper.date
				file_name = wallpaper.file_name
				mime_type = wallpaper.mime_type
				size = wallpaper.size
				thumbs = wallpaper.thumbs
				version = wallpaper.version
				dc_id = wallpaper.dc_id
				key = wallpaper.key
				iv = wallpaper.iv
				attributes = wallpaper.attributes
			}
			else {
				id = Int.MIN_VALUE.toLong()
				dc_id = Int.MIN_VALUE
			}
		}
	}
}
