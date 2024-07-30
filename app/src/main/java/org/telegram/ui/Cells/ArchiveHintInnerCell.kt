/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.RLottieImageView

class ArchiveHintInnerCell(context: Context, num: Int) : LinearLayout(context) {
	private val imageView: RLottieImageView
	private val headerTextView: TextView
	private val messageTextView: TextView

	init {
		orientation = VERTICAL
		gravity = Gravity.CENTER_HORIZONTAL

		headerTextView = TextView(context)
		headerTextView.setTextColor(context.getColor(R.color.text))
		headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		headerTextView.typeface = Theme.TYPEFACE_BOLD
		headerTextView.gravity = Gravity.CENTER

		messageTextView = TextView(context)
		messageTextView.setTextColor(context.getColor(R.color.medium_gray))
		messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		messageTextView.gravity = Gravity.CENTER

		when (num) {
			1 -> {
				imageView = RLottieImageView(context).apply {
					setAnimation(R.raw.chats_archive_muted, 160, 160)
				}

				headerTextView.text = context.getString(R.string.ArchiveHintHeader2)
				messageTextView.text = context.getString(R.string.ArchiveHintText2)
			}

			0 -> {
				imageView = RLottieImageView(context).apply {
					setAnimation(R.raw.chats_archive_pinned, 160, 160)
				}

				headerTextView.text = context.getString(R.string.ArchiveHintHeader3)
				messageTextView.text = context.getString(R.string.ArchiveHintText3)
			}

			else -> {
				imageView = RLottieImageView(context)
			}
		}

		addView(imageView, createLinear(160, 160, 0f, 23f, 0f, 0f))
		addView(headerTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 20f, 24f, 20f, 0f))
		addView(messageTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 20f, 4f, 20f, 0f))

		imageView.playAnimation()
		imageView.setAutoRepeat(true)
	}
}
