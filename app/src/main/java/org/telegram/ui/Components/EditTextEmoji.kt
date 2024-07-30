/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.InputFilter
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.utils.gone
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.EmojiView.EmojiViewDelegate
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet
import org.telegram.ui.Components.SizeNotifierFrameLayout.SizeNotifierFrameLayoutDelegate
import org.telegram.ui.PremiumPreviewFragment

open class EditTextEmoji @JvmOverloads constructor(context: Context, private var sizeNotifierLayout: SizeNotifierFrameLayout?, private val parentFragment: BaseFragment?, private val currentStyle: Int, private val allowAnimatedEmoji: Boolean, withLineColors: Boolean = true) : FrameLayout(context), NotificationCenterDelegate, SizeNotifierFrameLayoutDelegate {
	private val emojiButton = ImageView(context)
	private var emojiIconDrawable: ReplaceableIconDrawable? = null
	private var keyboardHeight = 0
	private var keyboardHeightLand = 0
	private var destroyed = false
	private var isPaused = true
	private var showKeyboardOnResume = false
	private var lastSizeChangeValue1 = 0
	private var lastSizeChangeValue2 = false
	private var innerTextChange = 0
	private var delegate: EditTextEmojiDelegate? = null
	var adjustPanLayoutHelper: AdjustPanLayoutHelper? = null

	val editText = object : EditTextCaption(context) {
		@SuppressLint("ClickableViewAccessibility")
		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (isPopupShowing && event.action == MotionEvent.ACTION_DOWN) {
				showPopup(if (AndroidUtilities.usingHardwareInput) 0 else 2)
				openKeyboardInternal()
			}
			if (event.action == MotionEvent.ACTION_DOWN) {
				requestFocus()
				if (!AndroidUtilities.showKeyboard(this)) {
					clearFocus()
					requestFocus()
				}
			}
			try {
				return super.onTouchEvent(event)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
			return false
		}

		override fun onLineCountChanged(oldLineCount: Int, newLineCount: Int) {
			this@EditTextEmoji.onLineCountChanged(oldLineCount, newLineCount)
		}
	}

	var isWaitingForKeyboardOpen = false
		private set

	var isAnimatePopupClosing = false
		private set

	var isKeyboardVisible = false
		private set
	var emojiPadding = 0
		private set

	var emojiView: EmojiView? = null
		private set
	var isPopupShowing = false
		private set

	private val openKeyboardRunnable by lazy {
		object : Runnable {
			override fun run() {
				if (!destroyed && isWaitingForKeyboardOpen && !isKeyboardVisible && !AndroidUtilities.usingHardwareInput && !AndroidUtilities.isInMultiwindow && AndroidUtilities.isTablet()) {
					editText.requestFocus()
					AndroidUtilities.showKeyboard(editText)
					AndroidUtilities.cancelRunOnUIThread(this)
					AndroidUtilities.runOnUIThread(this, 100)
				}
			}
		}
	}

	val isPopupVisible: Boolean
		get() = emojiView?.visibility == VISIBLE

	fun interface EditTextEmojiDelegate {
		fun onWindowSizeChanged(size: Int)
	}

	init {
		NotificationCenter.globalInstance.addObserver(this, NotificationCenter.emojiLoaded)

		sizeNotifierLayout?.setDelegate(this)

		editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		editText.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
		editText.inputType = editText.inputType or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
		editText.maxLines = 4
		editText.isFocusable = editText.isEnabled
		editText.setCursorSize(AndroidUtilities.dp(20f))
		editText.setCursorWidth(1.5f)
		editText.setCursorColor(ResourcesCompat.getColor(context.resources, R.color.text, null))

		if (currentStyle == STYLE_FRAGMENT) {
			editText.gravity = Gravity.CENTER_VERTICAL or if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

			editText.background = null
			if (withLineColors) {
				editText.setLineColors(ResourcesCompat.getColor(context.resources, R.color.hint, null), ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))
			}

			editText.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
			editText.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			editText.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(40f) else 0, 0, if (LocaleController.isRTL) 0 else AndroidUtilities.dp(40f), AndroidUtilities.dp(8f))

			addView(editText, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.CENTER_VERTICAL, if (LocaleController.isRTL) 11f else 0f, 1f, if (LocaleController.isRTL) 0f else 11f, 0f))
		}
		else {
			editText.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
			editText.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
			editText.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			editText.setPadding(0, AndroidUtilities.dp(11f), AndroidUtilities.dp(40f) , AndroidUtilities.dp(12f))

			editText.background = null
			addView(editText, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.LEFT or Gravity.CENTER_VERTICAL, 48f, 0f, 0f, 0f))
		}

		emojiButton.scaleType = ImageView.ScaleType.CENTER_INSIDE

		emojiButton.setImageDrawable(ReplaceableIconDrawable(context).also {
			emojiIconDrawable = it
		})

		emojiIconDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.hint, null), PorterDuff.Mode.SRC_IN)
		emojiIconDrawable?.setIcon(R.drawable.smile, false)

		val bottomMargin = if (withLineColors) 7f else 5f

		addView(emojiButton, createFrame(48, 48f, Gravity.CENTER_VERTICAL or if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT, 0f, 0f, 0f, bottomMargin))

		emojiButton.background = Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector))

		emojiButton.setOnClickListener {
			if (!emojiButton.isEnabled || adjustPanLayoutHelper != null && adjustPanLayoutHelper!!.animationInProgress()) {
				return@setOnClickListener
			}

			if (!isPopupShowing) {
				showPopup(1)
				emojiView?.onOpen(editText.length() > 0)
				editText.requestFocus()
			}
			else {
				openKeyboardInternal()
			}
		}

		emojiButton.contentDescription = context.getString(R.string.Emoji)
	}

	protected open fun onLineCountChanged(oldLineCount: Int, newLineCount: Int) {
		// stub
	}

	fun setSizeNotifierLayout(layout: SizeNotifierFrameLayout?) {
		sizeNotifierLayout = layout?.also {
			it.setDelegate(this)
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.emojiLoaded) {
			emojiView?.invalidateViews()

			val color = editText.currentTextColor
			editText.setTextColor(-0x1)
			editText.setTextColor(color)
		}
	}

	override fun setEnabled(enabled: Boolean) {
		editText.isEnabled = enabled

		emojiButton.visibility = if (enabled) VISIBLE else GONE

		if (enabled) {
			editText.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(40f) else 0, 0, if (LocaleController.isRTL) 0 else AndroidUtilities.dp(40f), AndroidUtilities.dp(8f))
		}
		else {
			editText.setPadding(0, 0, 0, AndroidUtilities.dp(8f))
		}
	}

	override fun setFocusable(focusable: Boolean) {
		editText.isFocusable = focusable
	}

	fun hideEmojiView() {
		if (!isPopupShowing && emojiView?.visibility != GONE) {
			emojiView?.visibility = GONE
		}

		emojiPadding = 0
	}

	fun setDelegate(editTextEmojiDelegate: EditTextEmojiDelegate?) {
		delegate = editTextEmojiDelegate
	}

	fun onPause() {
		isPaused = true
		closeKeyboard()
	}

	fun onResume() {
		isPaused = false

		if (showKeyboardOnResume) {
			showKeyboardOnResume = false

			editText.requestFocus()

			AndroidUtilities.showKeyboard(editText)

			if (!AndroidUtilities.usingHardwareInput && !isKeyboardVisible && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
				isWaitingForKeyboardOpen = true
				AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
				AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100)
			}
		}
	}

	fun onDestroy() {
		destroyed = true
		NotificationCenter.globalInstance.removeObserver(this, NotificationCenter.emojiLoaded)
		emojiView?.onDestroy()
		sizeNotifierLayout?.setDelegate(null)
	}

	fun updateColors() {
		if (currentStyle == STYLE_FRAGMENT) {
			editText.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
			editText.setCursorColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			editText.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		}
		else {
			editText.setHintTextColor(ResourcesCompat.getColor(context.resources, R.color.hint, null))
			editText.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		}

		emojiIconDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.hint, null), PorterDuff.Mode.SRC_IN)

		emojiView?.updateColors()
	}

	fun setMaxLines(value: Int) {
		editText.maxLines = value
	}

	fun length(): Int {
		return editText.length()
	}

	fun setFilters(filters: Array<InputFilter>?) {
		editText.filters = filters
	}

	var text: CharSequence?
		get() = editText.text
		set(text) {
			editText.setText(text)
		}

	fun setHint(hint: CharSequence?) {
		editText.hint = hint
	}

	fun setSelection(selection: Int) {
		editText.setSelection(selection)
	}

	open fun hidePopup(byBackButton: Boolean) {
		if (isPopupShowing) {
			showPopup(0)
		}

		if (byBackButton) {
			if (SharedConfig.smoothKeyboard && emojiView?.visibility == VISIBLE && !isWaitingForKeyboardOpen) {
				val height = emojiView!!.measuredHeight
				val animator = ValueAnimator.ofFloat(0f, height.toFloat())

				animator.addUpdateListener {
					val v = it.animatedValue as Float
					emojiView?.translationY = v
					bottomPanelTranslationY(v - height)
				}

				isAnimatePopupClosing = true

				animator.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						isAnimatePopupClosing = false
						emojiView?.translationY = 0f
						bottomPanelTranslationY(0f)
						hideEmojiView()
					}
				})

				animator.duration = AdjustPanLayoutHelper.keyboardDuration
				animator.interpolator = AdjustPanLayoutHelper.keyboardInterpolator
				animator.start()
			}
			else {
				hideEmojiView()
			}
		}
	}

	protected open fun bottomPanelTranslationY(translation: Float) {
		// stub
	}

	fun openKeyboard() {
		AndroidUtilities.showKeyboard(editText)
	}

	fun closeKeyboard() {
		AndroidUtilities.hideKeyboard(editText)
	}

	protected fun openKeyboardInternal() {
		showPopup(if (AndroidUtilities.usingHardwareInput || isPaused) 0 else 2)

		editText.requestFocus()

		AndroidUtilities.showKeyboard(editText)

		if (isPaused) {
			showKeyboardOnResume = true
		}
		else if (!AndroidUtilities.usingHardwareInput && !isKeyboardVisible && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
			isWaitingForKeyboardOpen = true
			AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
			AndroidUtilities.runOnUIThread(openKeyboardRunnable, 100)
		}
	}

	protected open fun showPopup(show: Int) {
		if (show == 1) {
			val emojiWasVisible = emojiView != null && emojiView!!.visibility == VISIBLE

			createEmojiView()

			emojiView?.visibility = VISIBLE
			isPopupShowing = true

			val currentView = emojiView

			if (keyboardHeight <= 0) {
				keyboardHeight = if (AndroidUtilities.isTablet()) {
					AndroidUtilities.dp(150f)
				}
				else {
					MessagesController.getGlobalEmojiSettings().getInt("kbd_height", AndroidUtilities.dp(200f))
				}
			}
			if (keyboardHeightLand <= 0) {
				keyboardHeightLand = if (AndroidUtilities.isTablet()) {
					AndroidUtilities.dp(150f)
				}
				else {
					MessagesController.getGlobalEmojiSettings().getInt("kbd_height_land3", AndroidUtilities.dp(200f))
				}
			}

			val currentHeight = if (AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) keyboardHeightLand else keyboardHeight

			currentView?.updateLayoutParams<LayoutParams> {
				height = currentHeight
			}

			if (!AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
				AndroidUtilities.hideKeyboard(editText)
			}

			if (sizeNotifierLayout != null) {
				emojiPadding = currentHeight
				sizeNotifierLayout?.requestLayout()
				emojiIconDrawable?.setIcon(R.drawable.keyboard_open, true)
				onWindowSizeChanged()
			}

			if (!isKeyboardVisible && !emojiWasVisible) {
				if (SharedConfig.smoothKeyboard) {
					val animator = ValueAnimator.ofFloat(emojiPadding.toFloat(), 0f)

					animator.addUpdateListener {
						val v = it.animatedValue as Float
						emojiView?.translationY = v
						bottomPanelTranslationY(v)
					}

					animator.addListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							emojiView?.translationY = 0f
							bottomPanelTranslationY(0f)
						}
					})

					animator.duration = AdjustPanLayoutHelper.keyboardDuration
					animator.interpolator = AdjustPanLayoutHelper.keyboardInterpolator
					animator.start()
				}
			}
		}
		else {
			emojiIconDrawable?.setIcon(R.drawable.smile, true)

			if (emojiView != null) {
				isPopupShowing = false

				if (AndroidUtilities.usingHardwareInput || AndroidUtilities.isInMultiwindow) {
					emojiView?.gone()
				}
			}

			if (sizeNotifierLayout != null) {
				if (show == 0) {
					emojiPadding = 0
				}

				sizeNotifierLayout?.requestLayout()

				onWindowSizeChanged()
			}
		}
	}

	private fun onWindowSizeChanged() {
		var size = sizeNotifierLayout?.height ?: return

		if (!isKeyboardVisible) {
			size -= emojiPadding
		}

		delegate?.onWindowSizeChanged(size)
	}

	protected open fun closeParent() {
		// stub
	}

	private fun createEmojiView() {
		if (emojiView != null && emojiView?.currentAccount != UserConfig.selectedAccount) {
			sizeNotifierLayout?.removeView(emojiView)
			emojiView = null
		}

		if (emojiView != null) {
			return
		}

		emojiView = EmojiView(parentFragment, allowAnimatedEmoji, false, false, context, false, null, null)
		emojiView?.gone()

		if (AndroidUtilities.isTablet()) {
			emojiView?.setForseMultiwindowLayout(true)
		}

		emojiView?.setDelegate(object : EmojiViewDelegate {
			override fun onBackspace(): Boolean {
				if (editText.length() == 0) {
					return false
				}


				editText.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
				return true
			}

			override fun onAnimatedEmojiUnlockClick() {
				var fragment = parentFragment

				if (fragment == null) {
					fragment = object : BaseFragment() {
						override fun getCurrentAccount(): Int {
							return currentAccount
						}

						override fun getContext(): Context {
							return this@EditTextEmoji.context
						}

						override fun getParentActivity(): Activity? {
							var context: Context? = context

							while (context is ContextWrapper) {
								if (context is Activity) {
									return context
								}

								context = context.baseContext
							}

							return null
						}

						override fun getVisibleDialog(): Dialog {
							return object : Dialog(this@EditTextEmoji.context) {
								override fun dismiss() {
									hidePopup(false)
									closeParent()
								}
							}
						}
					}

					PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false).show()
				}
				else {
					fragment.showDialog(PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false))
				}
			}

			override fun onEmojiSelected(symbol: String) {
				var i = editText.selectionEnd

				if (i < 0) {
					i = 0
				}

				try {
					innerTextChange = 2

					val localCharSequence = Emoji.replaceEmoji(symbol, editText.paint.fontMetricsInt, false) ?: symbol
					editText.text = editText.text.insert(i, localCharSequence)

					val j = i + localCharSequence.length
					editText.setSelection(j, j)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
				finally {
					innerTextChange = 0
				}
			}

			override fun onCustomEmojiSelected(documentId: Long, document: TLRPC.Document?, emoticon: String?, isRecent: Boolean) {
				var i = editText.selectionEnd

				if (i < 0) {
					i = 0
				}

				try {
					innerTextChange = 2

					val spannable = SpannableString(emoticon)

					val span = if (document != null) {
						AnimatedEmojiSpan(document, editText.paint.fontMetricsInt)
					}
					else {
						AnimatedEmojiSpan(documentId, editText.paint.fontMetricsInt)
					}

					spannable.setSpan(span, 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

					editText.text = editText.text.insert(i, spannable)

					val j = i + spannable.length
					editText.setSelection(j, j)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
				finally {
					innerTextChange = 0
				}
			}

			override fun onClearEmojiRecent() {
				val builder = AlertDialog.Builder(context)
				builder.setTitle(context.getString(R.string.ClearRecentEmojiTitle))
				builder.setMessage(context.getString(R.string.ClearRecentEmojiText))

				builder.setPositiveButton(context.getString(R.string.ClearButton).uppercase()) { _, _ ->
					emojiView?.clearRecentEmoji()
				}

				builder.setNegativeButton(context.getString(R.string.Cancel), null)

				if (parentFragment != null) {
					parentFragment.showDialog(builder.create())
				}
				else {
					builder.show()
				}
			}
		})

		sizeNotifierLayout?.addView(emojiView)
	}

	fun isPopupView(view: View): Boolean {
		return view === emojiView
	}

	override fun onSizeChanged(height: Int, isWidthGreater: Boolean) {
		if (height > AndroidUtilities.dp(50f) && isKeyboardVisible && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
			if (isWidthGreater) {
				keyboardHeightLand = height
				MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height_land3", keyboardHeightLand).commit()
			}
			else {
				keyboardHeight = height
				MessagesController.getGlobalEmojiSettings().edit().putInt("kbd_height", keyboardHeight).commit()
			}
		}

		if (isPopupShowing) {
			val newHeight = if (isWidthGreater) keyboardHeightLand else keyboardHeight

			(emojiView?.layoutParams as? LayoutParams)?.let { layoutParams ->
				if (layoutParams.width != AndroidUtilities.displaySize.x || layoutParams.height != newHeight) {
					layoutParams.width = AndroidUtilities.displaySize.x
					layoutParams.height = newHeight

					emojiView?.layoutParams = layoutParams

					sizeNotifierLayout?.let {
						emojiPadding = layoutParams.height
						it.requestLayout()
						onWindowSizeChanged()
					}
				}
			}
		}

		if (lastSizeChangeValue1 == height && lastSizeChangeValue2 == isWidthGreater) {
			onWindowSizeChanged()
			return
		}

		lastSizeChangeValue1 = height
		lastSizeChangeValue2 = isWidthGreater

		val oldValue = isKeyboardVisible

		isKeyboardVisible = editText.isFocused && height > 0

		if (isKeyboardVisible && isPopupShowing) {
			showPopup(0)
		}

		if (emojiPadding != 0 && !isKeyboardVisible && isKeyboardVisible != oldValue && !isPopupShowing) {
			emojiPadding = 0
			sizeNotifierLayout!!.requestLayout()
		}

		if (isKeyboardVisible && isWaitingForKeyboardOpen) {
			isWaitingForKeyboardOpen = false
			AndroidUtilities.cancelRunOnUIThread(openKeyboardRunnable)
		}

		onWindowSizeChanged()
	}

	private fun getThemedColor(key: String): Int {
		return Theme.getColor(key)
	}

	companion object {
		const val STYLE_FRAGMENT = 0
		const val STYLE_DIALOG = 1
	}
}
