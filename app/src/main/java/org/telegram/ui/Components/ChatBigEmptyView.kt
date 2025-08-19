/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper.createLinear

class ChatBigEmptyView(context: Context, parent: View?, type: Int) : LinearLayout(context) {
	private var statusTextView: TextView? = null
	private val textViews = ArrayList<TextView>()
	private val imageViews = ArrayList<ImageView>()

	init {
		val paint = Paint()
		paint.color = ResourcesCompat.getColor(context.resources, R.color.service_message_background, null)

		background = Theme.createServiceDrawable(AndroidUtilities.dp(14f), this, parent, paint)

		setPadding(AndroidUtilities.dp(16f), AndroidUtilities.dp(12f), AndroidUtilities.dp(16f), AndroidUtilities.dp(12f))

		orientation = VERTICAL

		when (type) {
			EMPTY_VIEW_TYPE_SECRET -> {
				statusTextView = TextView(context).also {
					it.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
					it.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
					it.gravity = Gravity.CENTER_HORIZONTAL
					it.maxWidth = AndroidUtilities.dp(210f)
					it.typeface = Theme.TYPEFACE_BOLD

					textViews.add(it)

					addView(it, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.TOP))
				}
			}
			EMPTY_VIEW_TYPE_GROUP -> {
				statusTextView = TextView(context).also {
					it.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
					it.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
					it.gravity = Gravity.CENTER_HORIZONTAL
					it.maxWidth = AndroidUtilities.dp(210f)
					it.typeface = Theme.TYPEFACE_BOLD

					textViews.add(it)

					addView(it, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.TOP))
				}
			}
			else -> {
				val imageView = RLottieImageView(context)
				imageView.setAutoRepeat(true)
				imageView.setAnimation(R.raw.panda_share_progress, 120, 120)
				imageView.playAnimation()
				addView(imageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, 2, 0, 0))
			}
		}

		var textView = TextView(context)

		when (type) {
			EMPTY_VIEW_TYPE_SECRET -> {
				textView.text = context.getString(R.string.EncryptedDescriptionTitle)
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			}
			EMPTY_VIEW_TYPE_GROUP -> {
				textView.text = context.getString(R.string.GroupEmptyTitle2)
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			}
			else -> {
				textView.text = context.getString(R.string.ChatYourSelfTitle)
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
				textView.typeface = Theme.TYPEFACE_BOLD
				textView.gravity = Gravity.CENTER_HORIZONTAL
			}
		}

		textView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))

		textViews.add(textView)

		textView.maxWidth = AndroidUtilities.dp(260f)

		addView(textView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (if (type != EMPTY_VIEW_TYPE_SAVED) (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) else Gravity.CENTER_HORIZONTAL) or Gravity.TOP, 0, 8, 0, if (type != EMPTY_VIEW_TYPE_SAVED) 0 else 8))

		for (a in 0..3) {
			val linearLayout = LinearLayout(context)
			linearLayout.orientation = HORIZONTAL

			addView(linearLayout, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, 0, 8, 0, 0))

			val imageView = ImageView(context)
			imageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.text, null), PorterDuff.Mode.SRC_IN)

			when (type) {
				EMPTY_VIEW_TYPE_SECRET -> {
					imageView.setImageResource(R.drawable.ic_lock_white)
				}
				EMPTY_VIEW_TYPE_SAVED -> {
					imageView.setImageResource(R.drawable.list_circle)
				}
				else -> {
					imageView.setImageResource(R.drawable.list_circle)
					// imageView.setImageResource(R.drawable.groups_overview_check)
				}
			}

			imageViews.add(imageView)

			textView = TextView(context)
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			textView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))

			textViews.add(textView)

			textView.gravity = Gravity.CENTER_VERTICAL or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT
			textView.maxWidth = AndroidUtilities.dp(260f)

			when (a) {
				0 -> when (type) {
					EMPTY_VIEW_TYPE_SECRET -> {
						textView.text = context.getString(R.string.EncryptedDescription1)
					}
					EMPTY_VIEW_TYPE_SAVED -> {
						textView.text = context.getString(R.string.ChatYourSelfDescription1)
					}
					else -> {
						textView.text = context.getString(R.string.GroupDescription1)
					}
				}
				1 -> when (type) {
					EMPTY_VIEW_TYPE_SECRET -> {
						textView.text = context.getString(R.string.EncryptedDescription2)
					}
					EMPTY_VIEW_TYPE_SAVED -> {
						textView.text = context.getString(R.string.ChatYourSelfDescription2)
					}
					else -> {
						textView.text = context.getString(R.string.GroupDescription2)
					}
				}
				2 -> when (type) {
					EMPTY_VIEW_TYPE_SECRET -> {
						textView.text = context.getString(R.string.EncryptedDescription3)
					}
					EMPTY_VIEW_TYPE_SAVED -> {
						textView.text = context.getString(R.string.ChatYourSelfDescription3)
					}
					else -> {
						textView.text = context.getString(R.string.GroupDescription3)
					}
				}
				3 -> when (type) {
					EMPTY_VIEW_TYPE_SECRET -> {
						textView.text = context.getString(R.string.EncryptedDescription4)
					}
					EMPTY_VIEW_TYPE_SAVED -> {
						textView.text = context.getString(R.string.ChatYourSelfDescription4)
					}
					else -> {
						textView.text = context.getString(R.string.GroupDescription4)
					}
				}
			}

			if (LocaleController.isRTL) {
				linearLayout.addView(textView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

				when (type) {
					EMPTY_VIEW_TYPE_SECRET -> {
						linearLayout.addView(imageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8f, 3f, 0f, 0f))
					}
					EMPTY_VIEW_TYPE_SAVED -> {
						linearLayout.addView(imageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8f, 7f, 0f, 0f))
					}
					else -> {
						linearLayout.addView(imageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8f, 7f, 0f, 0f))
						// linearLayout.addView(imageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8f, 3f, 0f, 0f))
					}
				}
			}
			else {
				when (type) {
					EMPTY_VIEW_TYPE_SECRET -> {
						linearLayout.addView(imageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 4f, 8f, 0f))
					}
					EMPTY_VIEW_TYPE_SAVED -> {
						linearLayout.addView(imageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 8f, 8f, 0f))
					}
					else -> {
						linearLayout.addView(imageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 8f, 8f, 0f))
						// linearLayout.addView(imageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0f, 4f, 8f, 0f))
					}
				}

				linearLayout.addView(textView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))
			}
		}
	}

	fun setTextColor(color: Int) {
		textViews.forEach {
			it.setTextColor(color)
		}

		imageViews.forEach {
			it.setColorFilter(color, PorterDuff.Mode.SRC_IN)
		}
	}

	fun setStatusText(text: CharSequence?) {
		statusTextView?.text = text
	}

	companion object {
		const val EMPTY_VIEW_TYPE_SECRET = 0
		const val EMPTY_VIEW_TYPE_GROUP = 1
		const val EMPTY_VIEW_TYPE_SAVED = 2
	}
}
