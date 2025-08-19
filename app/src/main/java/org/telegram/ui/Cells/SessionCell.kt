/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Cells

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.buildSpannedString
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserObject
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedFloat
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CombinedDrawable
import org.telegram.ui.Components.DotDividerSpan
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.SessionsActivity

class SessionCell @JvmOverloads constructor(context: Context, type: Int = SessionsActivity.ALL_SESSIONS) : FrameLayout(context) {
	private val currentAccount = UserConfig.selectedAccount
	private val detailExTextView = TextView(context)
	private val detailTextView = TextView(context)
	private val linearLayout = LinearLayout(context)
	private val nameTextView = TextView(context)
	private val onlineTextView = TextView(context)
	private val showStubValue = AnimatedFloat(this)
	private var avatarDrawable: AvatarDrawable? = null
	private var globalGradient: FlickerLoadingView? = null
	private var imageView: BackupImageView? = null
	private var isStub = false
	private var needDivider = false
	private var placeholderImageView: BackupImageView? = null

	init {
		linearLayout.orientation = LinearLayout.HORIZONTAL
		linearLayout.weightSum = 1f

		if (type == SessionsActivity.WEB_SESSIONS) {
			addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 15 else 49).toFloat(), 11f, (if (LocaleController.isRTL) 49 else 15).toFloat(), 0f))

			avatarDrawable = AvatarDrawable()
			avatarDrawable?.setTextSize(AndroidUtilities.dp(10f))

			imageView = BackupImageView(context)
			imageView?.setRoundRadius(AndroidUtilities.dp(10f))

			addView(imageView, LayoutHelper.createFrame(20, 20f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 21).toFloat(), 13f, (if (LocaleController.isRTL) 21 else 0).toFloat(), 0f))
		}
		else {
			placeholderImageView = BackupImageView(context)
			placeholderImageView?.setRoundRadius(AndroidUtilities.dp(10f))

			addView(placeholderImageView, LayoutHelper.createFrame(42, 42f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 16).toFloat(), 13f, (if (LocaleController.isRTL) 16 else 0).toFloat(), 0f))

			imageView = BackupImageView(context)
			imageView?.setRoundRadius(AndroidUtilities.dp(10f))

			addView(imageView, LayoutHelper.createFrame(42, 42f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 16).toFloat(), 13f, (if (LocaleController.isRTL) 16 else 0).toFloat(), 0f))
			addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 15 else 72).toFloat(), 11f, (if (LocaleController.isRTL) 72 else 15).toFloat(), 0f))
		}

		nameTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		nameTextView.setLines(1)
		nameTextView.typeface = Theme.TYPEFACE_BOLD
		nameTextView.maxLines = 1
		nameTextView.isSingleLine = true
		nameTextView.ellipsize = TextUtils.TruncateAt.END
		nameTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP

		onlineTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		onlineTextView.gravity = (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.TOP

		if (LocaleController.isRTL) {
			linearLayout.addView(onlineTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT or Gravity.TOP, 0, 2, 0, 0))
			linearLayout.addView(nameTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, Gravity.RIGHT or Gravity.TOP, 10, 0, 0, 0))
		}
		else {
			linearLayout.addView(nameTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, Gravity.LEFT or Gravity.TOP, 0, 0, 10, 0))
			linearLayout.addView(onlineTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT or Gravity.TOP, 0, 2, 0, 0))
		}

		val leftMargin: Int
		val rightMargin: Int

		if (LocaleController.isRTL) {
			rightMargin = if (type == SessionsActivity.ALL_SESSIONS) 72 else 21
			leftMargin = 21
		}
		else {
			leftMargin = if (type == SessionsActivity.ALL_SESSIONS) 72 else 21
			rightMargin = 21
		}

		detailTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		detailTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		detailTextView.setLines(1)
		detailTextView.maxLines = 1
		detailTextView.isSingleLine = true
		detailTextView.ellipsize = TextUtils.TruncateAt.END
		detailTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP

		addView(detailTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, leftMargin.toFloat(), 36f, rightMargin.toFloat(), 0f))

		detailExTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		detailExTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		detailExTextView.setLines(1)
		detailExTextView.maxLines = 1
		detailExTextView.isSingleLine = true
		detailExTextView.ellipsize = TextUtils.TruncateAt.END
		detailExTextView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP

		addView(detailExTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, leftMargin.toFloat(), 59f, rightMargin.toFloat(), 0f))
	}

	private fun setContentAlpha(alpha: Float) {
		detailExTextView.alpha = alpha
		detailTextView.alpha = alpha
		nameTextView.alpha = alpha
		onlineTextView.alpha = alpha
		imageView?.alpha = alpha
		placeholderImageView?.alpha = 1f - alpha
		linearLayout.alpha = alpha
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(90f) + if (needDivider) 1 else 0, MeasureSpec.EXACTLY))
	}

	fun setSession(`object`: TLObject?, divider: Boolean) {
		needDivider = divider

		if (`object` is TLRPC.TLAuthorization) {
			imageView?.setImageDrawable(createDrawable(`object`))

			nameTextView.text = buildString {
				if (!`object`.deviceModel.isNullOrEmpty()) {
					append(`object`.deviceModel)
				}

				if (isEmpty()) {
					if (!`object`.platform.isNullOrEmpty()) {
						append(`object`.platform)
					}

					if (!`object`.systemVersion.isNullOrEmpty()) {
						if (!`object`.platform.isNullOrEmpty()) {
							append(" ")
						}

						append(`object`.systemVersion)
					}
				}
			}

			val timeText = if (`object`.flags and 1 != 0) {
				context.getString(R.string.Online)
			}
			else {
				LocaleController.stringForMessageListDate(`object`.dateActive.toLong())
			}

			detailExTextView.text = buildSpannedString {
				val country = `object`.country

				if (!country.isNullOrEmpty() && !country.contains("unknown", ignoreCase = true)) {
					append(country)
				}

				if (isNotEmpty()) {
					val dotDividerSpan = DotDividerSpan()
					dotDividerSpan.setTopPadding(AndroidUtilities.dp(1.5f))
					append(" . ").setSpan(dotDividerSpan, length - 2, length - 1, 0)
				}

				append(timeText)
			}

			detailTextView.text = buildString {
				append(`object`.appName)
				append(" ")
				append(`object`.appVersion)
			}
		}
		else if (`object` is TLRPC.TLWebAuthorization) {
			val user = MessagesController.getInstance(currentAccount).getUser(`object`.botId)

			nameTextView.text = `object`.domain

			val name = if (user != null) {
				avatarDrawable?.setInfo(user)
				imageView?.setForUserOrChat(user, avatarDrawable)
				UserObject.getFirstName(user)
			}
			else {
				""
			}

			onlineTextView.text = LocaleController.stringForMessageListDate(`object`.dateActive.toLong())
			onlineTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))

			detailExTextView.text = buildString {
				if (!`object`.ip.isNullOrEmpty()) {
					append(`object`.ip)
				}

				if (!`object`.region.isNullOrEmpty()) {
					if (isNotEmpty()) {
						append(" ")
					}

					append("— ")
					append(`object`.region)
				}
			}

			detailTextView.text = buildString {
				if (name.isNotEmpty()) {
					append(name)
				}

				if (!`object`.browser.isNullOrEmpty()) {
					if (isNotEmpty()) {
						append(", ")
					}

					append(`object`.browser)
				}

				if (!`object`.platform.isNullOrEmpty()) {
					if (isNotEmpty()) {
						append(", ")
					}

					append(`object`.platform)
				}
			}
		}

		if (isStub) {
			isStub = false
			invalidate()
		}
	}

	override fun onDraw(canvas: Canvas) {
		val stubAlpha = showStubValue.set(if (isStub) 1f else 0f)

		setContentAlpha(1f - stubAlpha)

		val globalGradient = globalGradient

		if (stubAlpha > 0 && globalGradient != null) {
			if (stubAlpha < 1f) {
				AndroidUtilities.rectTmp[0f, 0f, width.toFloat()] = height.toFloat()
				canvas.saveLayerAlpha(AndroidUtilities.rectTmp, (255 * stubAlpha).toInt())
			}

			globalGradient.updateColors()
			globalGradient.updateGradient()

			(parent as? View)?.let {
				globalGradient.setParentSize(it.measuredWidth, it.measuredHeight, -x)
			}

			var y = (linearLayout.top + nameTextView.top + AndroidUtilities.dp(12f)).toFloat()
			var x = linearLayout.x

			AndroidUtilities.rectTmp[x, y - AndroidUtilities.dp(4f), x + measuredWidth * 0.2f] = y + AndroidUtilities.dp(4f)

			canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), globalGradient.paint)

			y = (linearLayout.top + detailTextView.top - AndroidUtilities.dp(1f)).toFloat()
			x = linearLayout.x

			AndroidUtilities.rectTmp.set(x, y - AndroidUtilities.dp(4f), x + measuredWidth * 0.4f, y + AndroidUtilities.dp(4f))

			canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), globalGradient.paint)

			y = (linearLayout.top + detailExTextView.top - AndroidUtilities.dp(1f)).toFloat()
			x = linearLayout.x

			AndroidUtilities.rectTmp.set(x, y - AndroidUtilities.dp(4f), x + measuredWidth * 0.3f, y + AndroidUtilities.dp(4f))

			canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(4f).toFloat(), AndroidUtilities.dp(4f).toFloat(), globalGradient.paint)

			invalidate()

			if (stubAlpha < 1f) {
				canvas.restore()
			}
		}

		if (needDivider) {
			canvas.drawLine((if (LocaleController.isRTL) 0 else AndroidUtilities.dp(20f)).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(20f) else 0).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
		}
	}

	fun showStub(globalGradient: FlickerLoadingView?) {
		this.globalGradient = globalGradient

		isStub = true

		val iconDrawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, if (AndroidUtilities.isTablet()) R.drawable.device_tablet_android else R.drawable.device_phone_android)?.mutate()
		iconDrawable?.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

		val combinedDrawable = CombinedDrawable(Theme.createCircleDrawable(AndroidUtilities.dp(42f), ResourcesCompat.getColor(context.resources, R.color.online, null)), iconDrawable)

		placeholderImageView?.setImageDrawable(combinedDrawable) ?: imageView?.setImageDrawable(combinedDrawable)

		invalidate()
	}

	companion object {
		fun createDrawable(session: TLRPC.TLAuthorization): Drawable {
			var platform = session.platform?.lowercase() ?: ""

			if (platform.isEmpty()) {
				platform = session.systemVersion?.lowercase() ?: ""
			}

			val deviceModel = session.deviceModel?.lowercase() ?: ""
			val iconId: Int
			val colorKey: String

			if (deviceModel.contains("safari")) {
				iconId = R.drawable.device_web_safari
				colorKey = Theme.key_avatar_backgroundPink
			}
			else if (deviceModel.contains("edge")) {
				iconId = R.drawable.device_web_edge
				colorKey = Theme.key_avatar_backgroundPink
			}
			else if (deviceModel.contains("chrome")) {
				iconId = R.drawable.device_web_chrome
				colorKey = Theme.key_avatar_backgroundPink
			}
			else if (deviceModel.contains("opera")) {
				iconId = R.drawable.device_web_opera
				colorKey = Theme.key_avatar_backgroundPink
			}
			else if (deviceModel.contains("firefox")) {
				iconId = R.drawable.device_web_firefox
				colorKey = Theme.key_avatar_backgroundPink
			}
			else if (deviceModel.contains("vivaldi")) {
				iconId = R.drawable.device_web_other
				colorKey = Theme.key_avatar_backgroundPink
			}
			else if (platform.contains("ios")) {
				iconId = if (deviceModel.contains("ipad")) R.drawable.device_tablet_ios else R.drawable.device_phone_ios
				colorKey = Theme.key_avatar_backgroundBlue
			}
			else if (platform.contains("windows")) {
				iconId = R.drawable.device_desktop_win
				colorKey = Theme.key_avatar_backgroundCyan
			}
			else if (platform.contains("macos")) {
				iconId = R.drawable.device_desktop_osx
				colorKey = Theme.key_avatar_backgroundCyan
			}
			else if (platform.contains("android")) {
				iconId = if (deviceModel.contains("tab")) R.drawable.device_tablet_android else R.drawable.device_phone_android
				colorKey = Theme.key_avatar_backgroundGreen
			}
			else {
				if (session.appName?.lowercase()?.contains("desktop") == true) {
					iconId = R.drawable.device_desktop_other
					colorKey = Theme.key_avatar_backgroundCyan
				}
				else {
					iconId = R.drawable.device_web_other
					colorKey = Theme.key_avatar_backgroundPink
				}
			}

			val iconDrawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, iconId)?.mutate()
			iconDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(ApplicationLoader.applicationContext.resources, R.color.white, null), PorterDuff.Mode.SRC_IN)

			return CombinedDrawable(Theme.createCircleDrawable(AndroidUtilities.dp(42f), Theme.getColor(colorKey)), iconDrawable)
		}
	}
}