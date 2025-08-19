/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.TypedValue
import android.view.Gravity
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.TLAccountChangeAuthorizationSettings
import org.telegram.tgnet.TLRPC.TLAuthorization
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.Components.Switch

class SessionBottomSheet(private val parentFragment: BaseFragment, session: TLAuthorization, isCurrentSession: Boolean, callback: Callback) : BottomSheet(parentFragment.parentActivity, false) {
	var session: TLAuthorization
	var imageView: RLottieImageView

	init {
		setOpenNoDelay(true)

		val context = parentFragment.parentActivity!!

		this.session = session

		fixNavigationBar()

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = LinearLayout.VERTICAL

		imageView = RLottieImageView(context)
		imageView.setOnClickListener {
			if (!imageView.isPlaying() && imageView.animatedDrawable != null) {
				imageView.animatedDrawable?.currentFrame = 40
				imageView.playAnimation()
			}
		}

		imageView.scaleType = ImageView.ScaleType.CENTER

		linearLayout.addView(imageView, LayoutHelper.createLinear(70, 70, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0))

		val nameView = TextView(context)
		nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
		nameView.typeface = Theme.TYPEFACE_BOLD
		nameView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		nameView.gravity = Gravity.CENTER

		linearLayout.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 12, 21, 0))

		val timeView = TextView(context)
		timeView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))
		timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
		timeView.gravity = Gravity.CENTER

		linearLayout.addView(timeView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 21, 4, 21, 21))

		val timeText = if (session.flags and 1 != 0) {
			context.getString(R.string.Online)
		}
		else {
			LocaleController.formatDateTime(session.dateActive.toLong())
		}

		timeView.text = timeText

		nameView.text = buildString {
			if (!session.deviceModel.isNullOrEmpty()) {
				append(session.deviceModel)
			}

			if (isEmpty()) {
				if (!session.platform.isNullOrEmpty()) {
					append(session.platform)
				}

				if (!session.systemVersion.isNullOrEmpty()) {
					if (!session.platform.isNullOrEmpty()) {
						append(" ")
					}

					append(session.systemVersion)
				}
			}
		}

		setAnimation(session, imageView)
		val applicationItemView = ItemView(context, false)

		applicationItemView.valueText.text = buildString {
			append(session.appName)
			append(" ")
			append(session.appVersion)
		}

		var drawable = ContextCompat.getDrawable(context, R.drawable.menu_devices)?.mutate()
		drawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)

		applicationItemView.iconView.setImageDrawable(drawable)
		applicationItemView.descriptionText.text = context.getString(R.string.Application)

		linearLayout.addView(applicationItemView)

		var prevItem: ItemView? = applicationItemView

		val country = session.country

		if (!country.isNullOrEmpty() && !country.contains("unknown", ignoreCase = true)) {
			val locationItemView = ItemView(context, false)
			locationItemView.valueText.text = country

			drawable = ContextCompat.getDrawable(context, R.drawable.msg_location)?.mutate()
			drawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)

			locationItemView.iconView.setImageDrawable(drawable)
			locationItemView.descriptionText.text = context.getString(R.string.Location)

			locationItemView.setOnClickListener {
				copyText(country)
			}

			locationItemView.setOnLongClickListener {
				copyText(country)
				true
			}

			locationItemView.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.light_background, null), 2)

			linearLayout.addView(locationItemView)

			prevItem?.needDivider = true

			prevItem = locationItemView
		}

		val ip = session.ip

		if (!ip.isNullOrEmpty() && !ip.contains("unknown", ignoreCase = true)) {
			val locationItemView = ItemView(context, false)
			locationItemView.valueText.text = ip

			drawable = ContextCompat.getDrawable(context, R.drawable.msg_language)?.mutate()
			drawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)

			locationItemView.iconView.setImageDrawable(drawable)
			locationItemView.descriptionText.text = context.getString(R.string.IpAddress)

			locationItemView.setOnClickListener {
				copyText(ip)
			}

			locationItemView.setOnLongClickListener {
				copyText(ip)
				true
			}

			locationItemView.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.light_background, null), 2)

			linearLayout.addView(locationItemView)

			prevItem?.needDivider = true

			prevItem = locationItemView
		}

//		if (secretChatsEnabled(session)) {
//			val acceptSecretChats = ItemView(context, true)
//			acceptSecretChats.valueText.text = context.getString(R.string.AcceptSecretChats)
//
//			drawable = ContextCompat.getDrawable(context, R.drawable.msg_secret)?.mutate()
//			drawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)
//
//			acceptSecretChats.iconView.setImageDrawable(drawable)
//			acceptSecretChats.switchView?.setChecked(!session.encryptedRequestsDisabled, false)
//			acceptSecretChats.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.light_background, null), 7)
//
//			acceptSecretChats.setOnClickListener {
//				acceptSecretChats.switchView?.setChecked(acceptSecretChats.switchView?.isChecked?.not() ?: false, true)
//				session.encryptedRequestsDisabled = acceptSecretChats.switchView?.isChecked?.not() ?: false
//				uploadSessionSettings()
//			}
//
//			prevItem?.needDivider = true
//
//			acceptSecretChats.descriptionText.text = context.getString(R.string.AcceptSecretChatsDescription)
//
//			linearLayout.addView(acceptSecretChats)
//
//			prevItem = acceptSecretChats
//		}

//		val acceptCalls = ItemView(context, true)
//		acceptCalls.valueText.text = context.getString(R.string.AcceptCalls)
//
//		drawable = ContextCompat.getDrawable(context, R.drawable.msg_calls)?.mutate()
//		drawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)
//
//		acceptCalls.iconView.setImageDrawable(drawable)
//		acceptCalls.switchView?.setChecked(!session.call_requests_disabled, false)
//		acceptCalls.background = Theme.createSelectorDrawable(ResourcesCompat.getColor(context.resources, R.color.light_background, null), 7)
//
//		acceptCalls.setOnClickListener {
//			acceptCalls.switchView?.setChecked(acceptCalls.switchView?.isChecked?.not() ?: false, true)
//			session.call_requests_disabled = acceptCalls.switchView?.isChecked?.not() ?: false
//			uploadSessionSettings()
//		}
//
//		prevItem?.needDivider = true
//
//		acceptCalls.descriptionText.text = context.getString(R.string.AcceptCallsChatsDescription)
//
//		linearLayout.addView(acceptCalls)

		if (!isCurrentSession) {
			val buttonTextView = TextView(context)
			buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
			buttonTextView.gravity = Gravity.CENTER
			buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			buttonTextView.typeface = Theme.TYPEFACE_BOLD
			buttonTextView.text = context.getString(R.string.TerminateSession)
			buttonTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.white, null))
			buttonTextView.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6f), ResourcesCompat.getColor(context.resources, R.color.purple, null), ColorUtils.setAlphaComponent(ResourcesCompat.getColor(context.resources, R.color.background, null), 120))
			linearLayout.addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, 0, 16f, 15f, 16f, 16f))

			buttonTextView.setOnClickListener {
				val builder = AlertDialog.Builder(it.context)
				builder.setMessage(it.context.getString(R.string.TerminateSessionText))
				builder.setTitle(it.context.getString(R.string.AreYouSureSessionTitle))

				builder.setPositiveButton(it.context.getString(R.string.Terminate)) { _, _ ->
					callback.onSessionTerminated(session)
					dismiss()
				}

				builder.setNegativeButton(context.getString(R.string.Cancel), null)

				val alertDialog = builder.create()

				parentFragment.showDialog(alertDialog)

				val button = alertDialog.getButton(BUTTON_POSITIVE) as? TextView
				button?.setTextColor(ResourcesCompat.getColor(it.context.resources, R.color.purple, null))
			}
		}

		val scrollView = ScrollView(context)
		scrollView.addView(linearLayout)
		setCustomView(scrollView)
	}

//	private fun secretChatsEnabled(session: TLAuthorization): Boolean {
//		return !(session.api_id == 2040 || session.api_id == 2496)
//	}

	private fun uploadSessionSettings() {
		val req = TLAccountChangeAuthorizationSettings()
		req.encryptedRequestsDisabled = session.encryptedRequestsDisabled
		req.callRequestsDisabled = session.callRequestsDisabled
		req.flags = 1 or 2
		req.hash = session.hash

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { _, _ -> }
	}

	private fun copyText(text: String) {
		val builder = AlertDialog.Builder(context)

		builder.setItems(arrayOf(context.getString(R.string.Copy))) { _, _ ->
			val clipboard = ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
			val clip = ClipData.newPlainText("label", text)
			clipboard.setPrimaryClip(clip)
			BulletinFactory.of(getContainer()).createCopyBulletin(context.getString(R.string.TextCopied)).show()
		}

		builder.show()
	}

	@SuppressLint("ResourceType")
	private fun setAnimation(session: TLAuthorization, imageView: RLottieImageView) {
		var platform = session.platform?.lowercase() ?: ""

		if (platform.isEmpty()) {
			platform = session.systemVersion?.lowercase() ?: ""
		}

		val deviceModel = session.deviceModel?.lowercase() ?: ""

		val iconId: Int
		val colorKey: String
		var animation = true

		if (deviceModel.contains("safari")) {
			iconId = R.raw.safari_30
			colorKey = Theme.key_avatar_backgroundPink
		}
		else if (deviceModel.contains("edge")) {
			iconId = R.raw.edge_30
			colorKey = Theme.key_avatar_backgroundPink
		}
		else if (deviceModel.contains("chrome")) {
			iconId = R.raw.chrome_30
			colorKey = Theme.key_avatar_backgroundPink
		}
		else if (deviceModel.contains("opera") || deviceModel.contains("firefox") || deviceModel.contains("vivaldi")) {
			animation = false

			iconId = if (deviceModel.contains("opera")) {
				R.drawable.device_web_opera
			}
			else if (deviceModel.contains("firefox")) {
				R.drawable.device_web_firefox
			}
			else {
				R.drawable.device_web_other
			}

			colorKey = Theme.key_avatar_backgroundPink
		}
		else if (platform.contains("ubuntu")) {
			iconId = R.raw.ubuntu_30
			colorKey = Theme.key_avatar_backgroundBlue
		}
		else if (platform.contains("ios")) {
			iconId = if (deviceModel.contains("ipad")) R.raw.ipad_30 else R.raw.iphone_30
			colorKey = Theme.key_avatar_backgroundBlue
		}
		else if (platform.contains("windows")) {
			iconId = R.raw.windows_30
			colorKey = Theme.key_avatar_backgroundCyan
		}
		else if (platform.contains("macos")) {
			iconId = R.raw.mac_30
			colorKey = Theme.key_avatar_backgroundCyan
		}
		else if (platform.contains("android")) {
			iconId = R.raw.android_30
			colorKey = Theme.key_avatar_backgroundGreen
		}
		else {
			if (session.appName?.lowercase()?.contains("desktop") == true) {
				iconId = R.raw.windows_30
				colorKey = Theme.key_avatar_backgroundCyan
			}
			else {
				iconId = R.raw.chrome_30
				colorKey = Theme.key_avatar_backgroundPink
			}
		}

		imageView.background = Theme.createCircleDrawable(AndroidUtilities.dp(42f), Theme.getColor(colorKey))

		if (animation) {
			val colors = intArrayOf(0x000000, Theme.getColor(colorKey))
			imageView.setAnimation(iconId, 50, 50, colors)
		}
		else {
			imageView.setImageDrawable(ContextCompat.getDrawable(context, iconId))
		}
	}

	private class ItemView(context: Context, needSwitch: Boolean) : FrameLayout(context) {
		val iconView = ImageView(context)
		val valueText: TextView
		val descriptionText: TextView
		var switchView: Switch? = null
		var needDivider = false

		init {

			addView(iconView, LayoutHelper.createFrame(28, 28f, 0, 16f, 8f, 0f, 0f))

			val linearLayout = LinearLayout(context)
			linearLayout.orientation = LinearLayout.VERTICAL

			addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), 0, 64f, 4f, 0f, 4f))

			valueText = TextView(context)
			valueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
			valueText.gravity = Gravity.LEFT
			valueText.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))

			linearLayout.addView(valueText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, if (needSwitch) 46 else 0, 0))

			descriptionText = TextView(context)
			descriptionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
			descriptionText.gravity = Gravity.LEFT
			descriptionText.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null))

			linearLayout.addView(descriptionText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 4, if (needSwitch) 46 else 0, 0))

			setPadding(0, AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f))

			if (needSwitch) {
				switchView = Switch(context)
				switchView?.setDrawIconType(1)

				addView(switchView, LayoutHelper.createFrame(37, 40f, Gravity.RIGHT or Gravity.CENTER_VERTICAL, 21f, 0f, 21f, 0f))
			}
		}

		override fun dispatchDraw(canvas: Canvas) {
			super.dispatchDraw(canvas)

			if (needDivider) {
				canvas.drawRect(AndroidUtilities.dp(64f).toFloat(), (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), measuredHeight.toFloat(), Theme.dividerPaint)
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)

			switchView?.let {
				info.className = "android.widget.Switch"
				info.isCheckable = true
				info.isChecked = it.isChecked
				info.text = valueText.text.toString() + "\n" + descriptionText.text.toString() + "\n" + (if (it.isChecked) context.getString(R.string.NotificationsOn) else context.getString(R.string.NotificationsOff))
			}
		}
	}

	fun interface Callback {
		fun onSessionTerminated(session: TLAuthorization)
	}

	override fun show() {
		super.show()
		imageView.playAnimation()
	}
}
