/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MediaController.AlbumEntry
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.LayoutHelper

class PhotoPickerAlbumsCell(context: Context) : FrameLayout(context) {
	fun interface PhotoPickerAlbumsCellDelegate {
		fun didSelectAlbum(albumEntry: AlbumEntry?)
	}

	private val albumViews: Array<AlbumView>
	private val albumEntries: Array<AlbumEntry?> = arrayOfNulls(4)
	private var albumsCount = 0
	private var delegate: PhotoPickerAlbumsCellDelegate? = null
	private val backgroundPaint = Paint()

	private inner class AlbumView(context: Context) : FrameLayout(context) {
		val imageView = BackupImageView(context)
		val nameTextView = TextView(context)
		val countTextView = TextView(context)
		private val selector = View(context)

		init {
			addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			val linearLayout = LinearLayout(context)

			linearLayout.orientation = LinearLayout.HORIZONTAL
			linearLayout.setBackgroundResource(R.drawable.album_shadow)

			addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.LEFT or Gravity.BOTTOM))

			nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
			nameTextView.setTextColor(-0x1)
			nameTextView.isSingleLine = true
			nameTextView.ellipsize = TextUtils.TruncateAt.END
			nameTextView.maxLines = 1
			nameTextView.gravity = Gravity.BOTTOM

			linearLayout.addView(nameTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 8, 0, 0, 5))

			countTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
			countTextView.setTextColor(-0x1)
			countTextView.isSingleLine = true
			countTextView.ellipsize = TextUtils.TruncateAt.END
			countTextView.maxLines = 1
			countTextView.gravity = Gravity.BOTTOM

			linearLayout.addView(countTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 4f, 0f, 7f, 5f))

			selector.background = Theme.getSelectorDrawable(false)

			addView(selector, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		}

		@SuppressLint("ClickableViewAccessibility")
		override fun onTouchEvent(event: MotionEvent): Boolean {
			selector.drawableHotspotChanged(event.x, event.y)
			return super.onTouchEvent(event)
		}

		override fun onDraw(canvas: Canvas) {
			if (!imageView.imageReceiver.hasNotThumb() || imageView.imageReceiver.currentAlpha != 1.0f) {
				backgroundPaint.color = ResourcesCompat.getColor(resources, R.color.dark_background, null)
				canvas.drawRect(0f, 0f, imageView.measuredWidth.toFloat(), imageView.measuredHeight.toFloat(), backgroundPaint)
			}
		}
	}

	init {

		albumViews = (0..3).map {
			val albumView = AlbumView(context)
			albumView.visibility = INVISIBLE
			albumView.tag = it

			albumView.setOnClickListener { view ->
				delegate?.didSelectAlbum(albumEntries[(view.tag as Int)])
			}

			addView(albumView)

			albumView
		}.toTypedArray()
	}

	fun setAlbumsCount(count: Int) {
		for (a in albumViews.indices) {
			albumViews[a].visibility = if (a < count) VISIBLE else INVISIBLE
		}

		albumsCount = count
	}

	fun setDelegate(delegate: PhotoPickerAlbumsCellDelegate?) {
		this.delegate = delegate
	}

	fun setAlbum(a: Int, albumEntry: AlbumEntry?) {
		albumEntries[a] = albumEntry

		if (albumEntry != null) {
			val albumView = albumViews[a]
			albumView.imageView.setOrientation(0, true)

			if (albumEntry.coverPhoto != null && albumEntry.coverPhoto.path != null) {
				albumView.imageView.setOrientation(albumEntry.coverPhoto.orientation, true)

				if (albumEntry.coverPhoto.isVideo) {
					albumView.imageView.setImage("vthumb://" + albumEntry.coverPhoto.imageId + ":" + albumEntry.coverPhoto.path, null, Theme.chat_attachEmptyDrawable)
				}
				else {
					albumView.imageView.setImage("thumb://" + albumEntry.coverPhoto.imageId + ":" + albumEntry.coverPhoto.path, null, Theme.chat_attachEmptyDrawable)
				}
			}
			else {
				albumView.imageView.setImageDrawable(Theme.chat_attachEmptyDrawable)
			}

			albumView.nameTextView.text = albumEntry.bucketName
			albumView.countTextView.text = String.format("%d", albumEntry.photos.size)
		}
		else {
			albumViews[a].visibility = INVISIBLE
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val itemWidth = if (AndroidUtilities.isTablet()) {
			AndroidUtilities.dp(490f) - AndroidUtilities.dp(12f) - (albumsCount - 1) * AndroidUtilities.dp(4f) / albumsCount
		}
		else {
			AndroidUtilities.displaySize.x - AndroidUtilities.dp(12f) - (albumsCount - 1) * AndroidUtilities.dp(4f) / albumsCount
		}

		for (a in 0 until albumsCount) {
			val layoutParams = albumViews[a].layoutParams as LayoutParams
			layoutParams.topMargin = AndroidUtilities.dp(4f)
			layoutParams.leftMargin = (itemWidth + AndroidUtilities.dp(4f)) * a
			layoutParams.width = itemWidth
			layoutParams.height = itemWidth
			layoutParams.gravity = Gravity.LEFT or Gravity.TOP

			albumViews[a].layoutParams = layoutParams
		}
		super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(4f) + itemWidth, MeasureSpec.EXACTLY))
	}
}
