/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2023-205.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.provider.ContactsContract
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.net.toUri
import androidx.core.widget.NestedScrollView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.AndroidUtilities.VcardItem
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ContactsController.Companion.formatName
import org.telegram.messenger.ContactsController.Contact
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.expires
import org.telegram.tgnet.restrictionReason
import org.telegram.tgnet.status
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.Bulletin.SimpleLayout
import org.telegram.ui.Components.ChatAttachAlertContactsLayout.PhonebookShareAlertDelegate
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.LayoutHelper.createScroll
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class PhonebookShareAlert @JvmOverloads constructor(private val parentFragment: BaseFragment, contact: Contact?, user: User?, uri: Uri?, file: File?, firstName: String?, lastName: String?, resourcesProvider: Theme.ResourcesProvider? = null) : BottomSheet(parentFragment.parentActivity, false) {
	private val listAdapter = ListAdapter()
	private val linearLayout = LinearLayout(context)

	private val scrollView by lazy {
		object : NestedScrollView(context) {
			private var focusingView: View? = null
			override fun requestChildFocus(child: View, focused: View) {
				focusingView = focused
				super.requestChildFocus(child, focused)
			}

			override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
				val focusingView = focusingView ?: return 0

				if (linearLayout.top != paddingTop) {
					return 0
				}

				var delta = super.computeScrollDeltaToGetChildRectOnScreen(rect)
				val currentViewY = focusingView.top - scrollY + rect.top + delta
				val diff = ActionBar.getCurrentActionBarHeight() - currentViewY

				if (diff > 0) {
					delta -= diff + AndroidUtilities.dp(10f)
				}

				return delta
			}
		}
	}

	private val actionBar by lazy {
		object : ActionBar(context) {
			override fun setAlpha(alpha: Float) {
				super.setAlpha(alpha)
				containerView?.invalidate()
			}
		}
	}

	private val actionBarShadow = View(context)
	private val shadow = View(context)
	private val buttonTextView = TextView(context)
	private var inLayout = false
	private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private var scrollOffsetY = 0
	private var actionBarAnimation: AnimatorSet? = null
	private var shadowAnimation: AnimatorSet? = null
	private var rowCount = 0
	private var userRow = 0
	private var vcardStartRow = 0
	private var vcardEndRow = 0
	private var isImport = false
	private val other = ArrayList<VcardItem>()
	private val currentUser: User
	private var mDelegate: PhonebookShareAlertDelegate? = null

	fun setDelegate(phonebookShareAlertDelegate: PhonebookShareAlertDelegate?) {
		mDelegate = phonebookShareAlertDelegate
	}

	inner class UserCell(context: Context) : LinearLayout(context) {
		init {
			orientation = VERTICAL

			val needPadding = true

			val status = if (currentUser.status != null && currentUser.status?.expires != 0) {
				LocaleController.formatUserStatus(currentAccount, currentUser)
			}
			else {
				null
			}

			val avatarDrawable = AvatarDrawable()
			avatarDrawable.setTextSize(AndroidUtilities.dp(30f))
			avatarDrawable.setInfo(currentUser)

			val avatarImageView = BackupImageView(context)
			avatarImageView.setRoundRadius(AndroidUtilities.dp(40f))
			avatarImageView.setForUserOrChat(currentUser, avatarDrawable)

			addView(avatarImageView, createLinear(80, 80, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 32, 0, 0))

			var textView = TextView(context)
			textView.typeface = Theme.TYPEFACE_BOLD
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
			textView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
			textView.isSingleLine = true
			textView.ellipsize = TextUtils.TruncateAt.END
			textView.text = formatName(currentUser.firstName, currentUser.lastName)

			addView(textView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 10, 10, 10, if (status != null) 0 else 27))

			if (status != null) {
				textView = TextView(context)
				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
				textView.setTextColor(ResourcesCompat.getColor(resources, R.color.dark_gray, null))
				textView.isSingleLine = true
				textView.ellipsize = TextUtils.TruncateAt.END
				textView.text = status

				addView(textView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL, 10, 3, 10, if (needPadding) 27 else 11))
			}
		}
	}

	inner class TextCheckBoxCell(context: Context) : FrameLayout(context) {
		private val textView = TextView(context)
		private val valueTextView: TextView
		private val imageView: ImageView
		private var checkBox: Switch? = null
		private var needDivider = false

		init {
			textView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			textView.isSingleLine = false
			textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP
			textView.ellipsize = TextUtils.TruncateAt.END

			addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) (if (isImport) 17 else 64) else 72).toFloat(), 10f, (if (LocaleController.isRTL) 72 else if (isImport) 17 else 64).toFloat(), 0f))

			valueTextView = TextView(context)
			valueTextView.setTextColor(ResourcesCompat.getColor(resources, R.color.dark_gray, null))
			valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
			valueTextView.setLines(1)
			valueTextView.maxLines = 1
			valueTextView.isSingleLine = true
			valueTextView.gravity = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

			addView(valueTextView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT, (if (LocaleController.isRTL) (if (isImport) 17 else 64) else 72).toFloat(), 35f, (if (LocaleController.isRTL) 72 else if (isImport) 17 else 64).toFloat(), 0f))

			imageView = ImageView(context)
			imageView.scaleType = ImageView.ScaleType.CENTER
			imageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)

			addView(imageView, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 20).toFloat(), 20f, (if (LocaleController.isRTL) 20 else 0).toFloat(), 0f))

			if (!isImport) {
				checkBox = Switch(context)
				// checkBox.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
				addView(checkBox, createFrame(37, 40f, (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL, 22f, 0f, 22f, 0f))
			}
		}

		override fun invalidate() {
			super.invalidate()
			checkBox?.invalidate()
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			measureChildWithMargins(textView, widthMeasureSpec, 0, heightMeasureSpec, 0)
			measureChildWithMargins(valueTextView, widthMeasureSpec, 0, heightMeasureSpec, 0)
			measureChildWithMargins(imageView, widthMeasureSpec, 0, heightMeasureSpec, 0)

			if (checkBox != null) {
				measureChildWithMargins(checkBox, widthMeasureSpec, 0, heightMeasureSpec, 0)
			}

			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), max(AndroidUtilities.dp(64f), textView.measuredHeight + valueTextView.measuredHeight + AndroidUtilities.dp((10 + 10).toFloat())) + if (needDivider) 1 else 0)
		}

		override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
			super.onLayout(changed, left, top, right, bottom)
			val y = textView.measuredHeight + AndroidUtilities.dp((10 + 3).toFloat())
			valueTextView.layout(valueTextView.left, y, valueTextView.right, y + valueTextView.measuredHeight)
		}

		fun setVCardItem(item: VcardItem, icon: Int, divider: Boolean) {
			textView.text = item.getValue(true)
			valueTextView.text = item.getType()
			checkBox?.setChecked(item.checked, false)

			if (icon != 0) {
				imageView.setImageResource(icon)
			}
			else {
				imageView.setImageDrawable(null)
			}

			needDivider = divider

			setWillNotDraw(!needDivider)
		}

		var isChecked: Boolean
			get() = checkBox?.isChecked == true
			set(value) {
				checkBox?.setChecked(value, true)
			}

		override fun onDraw(canvas: Canvas) {
			if (needDivider) {
				canvas.drawLine((if (LocaleController.isRTL) 0 else AndroidUtilities.dp(70f)).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(70f) else 0).toFloat(), (measuredHeight - 1).toFloat(), Theme.dividerPaint)
			}
		}
	}

	init {
		@Suppress("NAME_SHADOWING") var user = user
		@Suppress("NAME_SHADOWING") var uri = uri
		@Suppress("NAME_SHADOWING") var firstName = firstName
		@Suppress("NAME_SHADOWING") var lastName = lastName

		val name = formatName(firstName, lastName)
		var result: List<User>? = null
		val items = mutableListOf<VcardItem>()
		var vcard: List<TLRPC.TLRestrictionReason>? = null

		if (uri != null) {
			result = AndroidUtilities.loadVCardFromStream(uri, currentAccount, false, items, name)
		}
		else if (file != null) {
			result = AndroidUtilities.loadVCardFromStream(Uri.fromFile(file), currentAccount, false, items, name)
			file.delete()
			isImport = true
		}
		else if (contact?.key != null) {
			uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, contact.key)
			result = AndroidUtilities.loadVCardFromStream(uri, currentAccount, true, items, name)
		}

		if (user == null && contact != null) {
			user = contact.user
		}

		if (result != null) {
			for (a in items.indices) {
				val item = items[a]

				if (item.type == 0) {
					// unused
				}
				else {
					other.add(item)
				}
			}

			if (result.isNotEmpty()) {
				val u = result[0]
				vcard = u.restrictionReason

				if (firstName.isNullOrEmpty()) {
					firstName = u.firstName
					lastName = u.lastName
				}
			}
		}

		currentUser = TLRPC.TLUser()

		if (user is TLRPC.TLUser) {
			currentUser.id = user.id
			currentUser.accessHash = user.accessHash
			currentUser.photo = user.photo
			currentUser.status = user.status
			currentUser.firstName = user.firstName
			currentUser.lastName = user.lastName
			currentUser.username = user.username

			if (vcard != null) {
				currentUser.restrictionReason.clear()
				currentUser.restrictionReason.addAll(vcard)
			}
		}
		else {
			currentUser.firstName = firstName
			currentUser.lastName = lastName
		}

		val context = parentFragment.parentActivity!!

		updateRows()

		val frameLayout: FrameLayout = object : FrameLayout(context) {
			private val rect = RectF()
			private var ignoreLayout = false

			override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
				if (ev.action == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.y < scrollOffsetY && actionBar.alpha == 0.0f) {
					dismiss()
					return true
				}

				return super.onInterceptTouchEvent(ev)
			}

			@SuppressLint("ClickableViewAccessibility")
			override fun onTouchEvent(event: MotionEvent): Boolean {
				return !isDismissed && super.onTouchEvent(event)
			}

			override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
				val totalHeight = MeasureSpec.getSize(heightMeasureSpec)

				ignoreLayout = true

				setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0)

				ignoreLayout = false

				val availableHeight = totalHeight - paddingTop
				// int availableWidth = MeasureSpec.getSize(widthMeasureSpec) - backgroundPaddingLeft * 2;

				val layoutParams = actionBarShadow.layoutParams as LayoutParams
				layoutParams.topMargin = ActionBar.getCurrentActionBarHeight()

				ignoreLayout = true

				val padding: Int
				var contentSize = AndroidUtilities.dp(80f)
				val count: Int = listAdapter.getItemCount()

				for (a in 0 until count) {
					val view = listAdapter.createView(context, a)
					view.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
					contentSize += view.measuredHeight
				}

				padding = if (contentSize < availableHeight) {
					availableHeight - contentSize
				}
				else {
					availableHeight / 5
				}

				if (scrollView.paddingTop != padding) {
					// int diff = scrollView.getPaddingTop() - padding;
					scrollView.setPadding(0, padding, 0, 0)
				}

				ignoreLayout = false

				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY))
			}

			override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
				inLayout = true
				super.onLayout(changed, l, t, r, b)
				inLayout = false
				updateLayout(false)
			}

			override fun requestLayout() {
				if (ignoreLayout) {
					return
				}

				super.requestLayout()
			}

			override fun onDraw(canvas: Canvas) {
				var top = scrollOffsetY - backgroundPaddingTop
				var height = measuredHeight + AndroidUtilities.dp(30f) + backgroundPaddingTop
				var rad = 1.0f
				val r = AndroidUtilities.dp(12f).toFloat()

				if (top + backgroundPaddingTop < r) {
					rad = 1.0f - min(1.0f, (r - top - backgroundPaddingTop) / r)
				}

				top += AndroidUtilities.statusBarHeight
				height -= AndroidUtilities.statusBarHeight

				shadowDrawable.setBounds(0, top, measuredWidth, height)
				shadowDrawable.draw(canvas)

				if (rad != 1.0f) {
					backgroundPaint.color = ResourcesCompat.getColor(resources, R.color.background, null)
					rect.set(backgroundPaddingLeft.toFloat(), (backgroundPaddingTop + top).toFloat(), (measuredWidth - backgroundPaddingLeft).toFloat(), (backgroundPaddingTop + top + AndroidUtilities.dp(24f)).toFloat())
					canvas.drawRoundRect(rect, r * rad, r * rad, backgroundPaint)
				}

				val color1 = ResourcesCompat.getColor(resources, R.color.background, null)
				val finalColor = Color.argb((255 * actionBar.alpha).toInt(), (Color.red(color1) * 0.8f).toInt(), (Color.green(color1) * 0.8f).toInt(), (Color.blue(color1) * 0.8f).toInt())

				backgroundPaint.color = finalColor

				canvas.drawRect(backgroundPaddingLeft.toFloat(), 0f, (measuredWidth - backgroundPaddingLeft).toFloat(), AndroidUtilities.statusBarHeight.toFloat(), backgroundPaint)
			}
		}

		frameLayout.setWillNotDraw(false)

		containerView = frameLayout

		setApplyTopPadding(false)
		setApplyBottomPadding(false)

		scrollView.clipToPadding = false
		scrollView.isVerticalScrollBarEnabled = false

		frameLayout.addView(scrollView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 77f))

		linearLayout.orientation = LinearLayout.VERTICAL

		scrollView.addView(linearLayout, createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT or Gravity.TOP))

		scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
			updateLayout(!inLayout)
		}

		var a = 0
		val n = listAdapter.getItemCount()

		while (a < n) {
			val view = listAdapter.createView(context, a)
			val position = a

			linearLayout.addView(view, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

			if (position in vcardStartRow until vcardEndRow) {
				view.background = Theme.getSelectorDrawable(false)

				view.setOnClickListener {
					val item = when (position) {
						in vcardStartRow until vcardEndRow -> {
							other[position - vcardStartRow]
						}

						else -> {
							null
						}
					} ?: return@setOnClickListener

					if (isImport) {
						when (item.type) {
							0 -> {
								try {
									val intent = Intent(Intent.ACTION_DIAL, ("tel:" + item.getValue(false)).toUri())
									intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
									parentFragment.parentActivity!!.startActivityForResult(intent, 500)
								}
								catch (e: Exception) {
									FileLog.e(e)
								}
							}

							1 -> {
								Browser.openUrl(parentFragment.parentActivity, "mailto:" + item.getValue(false))
							}

							3 -> {
								var url = item.getValue(false)

								if (!url.startsWith("http")) {
									url = "http://$url"
								}

								Browser.openUrl(parentFragment.parentActivity, url)
							}

							else -> {
								val builder = AlertDialog.Builder(parentFragment.parentActivity!!)

								builder.setItems(arrayOf(context.getString(R.string.Copy))) { _, i ->
									if (i == 0) {
										try {
											val clipboard = ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
											val clip = ClipData.newPlainText("label", item.getValue(false))

											clipboard.setPrimaryClip(clip)

											if (AndroidUtilities.shouldShowClipboardToast()) {
												Toast.makeText(parentFragment.parentActivity, context.getString(R.string.TextCopied), Toast.LENGTH_SHORT).show()
											}
										}
										catch (e: Exception) {
											FileLog.e(e)
										}
									}
								}

								builder.show()
							}
						}
					}
					else {
						item.checked = !item.checked

						val cell = view as TextCheckBoxCell
						cell.isChecked = item.checked
					}
				}

				view.setOnLongClickListener {
					val item = when (position) {
						in vcardStartRow until vcardEndRow -> {
							other[position - vcardStartRow]
						}

						else -> {
							null
						}
					} ?: return@setOnLongClickListener false

					val clipboard = ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
					val clip = ClipData.newPlainText("label", item.getValue(false))

					clipboard.setPrimaryClip(clip)

					if (BulletinFactory.canShowBulletin(parentFragment)) {
						if (item.type == 3) {
							BulletinFactory.of(containerView as FrameLayout).createCopyLinkBulletin().show()
						}
						else {
							val layout = SimpleLayout(context)

							when (item.type) {
								0 -> {
									layout.textView.text = context.getString(R.string.PhoneCopied)
									layout.imageView.setImageResource(R.drawable.msg_calls)
								}

								1 -> {
									layout.textView.text = context.getString(R.string.EmailCopied)
									layout.imageView.setImageResource(R.drawable.msg_mention)
								}

								else -> {
									layout.textView.text = context.getString(R.string.TextCopied)
									layout.imageView.setImageResource(R.drawable.msg_info)
								}
							}

							if (AndroidUtilities.shouldShowClipboardToast()) {
								Bulletin.make((containerView as FrameLayout), layout, Bulletin.DURATION_SHORT).show()
							}
						}
					}

					true
				}
			}

			a++
		}

		actionBar.setBackgroundColor(context.getColor(R.color.background))
		actionBar.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar.setItemsColor(context.getColor(R.color.text), false)
		actionBar.setItemsBackgroundColor(context.getColor(R.color.brand), false)
		actionBar.setTitleColor(context.getColor(R.color.text))
		actionBar.occupyStatusBar = false
		actionBar.alpha = 0.0f

		if (isImport) {
			actionBar.setTitle(context.getString(R.string.AddContactPhonebookTitle))
		}
		else {
			actionBar.setTitle(context.getString(R.string.ShareContactTitle))
		}

		containerView.addView(actionBar, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

		actionBar.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					dismiss()
				}
			}
		})

		actionBarShadow.alpha = 0.0f
		actionBarShadow.setBackgroundColor(context.getColor(R.color.shadow))

		containerView.addView(actionBarShadow, createFrame(LayoutHelper.MATCH_PARENT, 1f))

		shadow.setBackgroundColor(context.getColor(R.color.shadow))
		shadow.alpha = 0.0f

		containerView.addView(shadow, createFrame(LayoutHelper.MATCH_PARENT, 1f, Gravity.BOTTOM or Gravity.LEFT, 0f, 0f, 0f, 77f))

		buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
		buttonTextView.gravity = Gravity.CENTER
		buttonTextView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.white, null))
		buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)

		if (isImport) {
			buttonTextView.text = context.getString(R.string.AddContactPhonebookTitle)
		}
		else {
			buttonTextView.text = context.getString(R.string.ShareContactTitle)
		}

		val privateUserWarningText = TextView(context)
		privateUserWarningText.gravity = Gravity.CENTER
		privateUserWarningText.setTextColor(ResourcesCompat.getColor(context.resources, R.color.dark, null))
		privateUserWarningText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		privateUserWarningText.setTypeface(privateUserWarningText.typeface, Typeface.BOLD)
		privateUserWarningText.text = context.getString(R.string.private_user_warning_text)

		if ((user as? TLRPC.TLUser)?.isPublic == true) {
			buttonTextView.visible()
			privateUserWarningText.gone()
		}
		else {
			buttonTextView.gone()
			privateUserWarningText.visible()
		}

		buttonTextView.typeface = Theme.TYPEFACE_BOLD
		buttonTextView.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(15f), ResourcesCompat.getColor(context.resources, R.color.brand, null), ResourcesCompat.getColor(context.resources, R.color.darker_brand, null))

		frameLayout.addView(buttonTextView, createFrame(LayoutHelper.MATCH_PARENT, 42f, Gravity.LEFT or Gravity.BOTTOM, 16f, 16f, 16f, 16f))
		frameLayout.addView(privateUserWarningText, createFrame(LayoutHelper.MATCH_PARENT, 42f, Gravity.LEFT or Gravity.BOTTOM, 16f, 16f, 16f, 16f))

		buttonTextView.setOnClickListener {
			if (isImport) {
				val builder = AlertDialog.Builder(getContext())
				builder.setTitle(context.getString(R.string.AddContactTitle))
				builder.setNegativeButton(context.getString(R.string.Cancel), null)

				builder.setItems(arrayOf(context.getString(R.string.CreateNewContact), context.getString(R.string.AddToExistingContact)), object : DialogInterface.OnClickListener {
					private fun fillRowWithType(type: String, row: ContentValues) {
						if (type.startsWith("X-")) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_CUSTOM)
							row.put(ContactsContract.CommonDataKinds.Phone.LABEL, type.substring(2))
						}
						else if ("PREF".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MAIN)
						}
						else if ("HOME".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_HOME)
						}
						else if ("MOBILE".equals(type, ignoreCase = true) || "CELL".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
						}
						else if ("OTHER".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_OTHER)
						}
						else if ("WORK".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
						}
						else if ("RADIO".equals(type, ignoreCase = true) || "VOICE".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_RADIO)
						}
						else if ("PAGER".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_PAGER)
						}
						else if ("CALLBACK".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_CALLBACK)
						}
						else if ("CAR".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_CAR)
						}
						else if ("ASSISTANT".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT)
						}
						else if ("MMS".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MMS)
						}
						else if (type.startsWith("FAX")) {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK)
						}
						else {
							row.put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_CUSTOM)
							row.put(ContactsContract.CommonDataKinds.Phone.LABEL, type)
						}
					}

					private fun fillUrlRowWithType(type: String, row: ContentValues) {
						if (type.startsWith("X-")) {
							row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_CUSTOM)
							row.put(ContactsContract.CommonDataKinds.Website.LABEL, type.substring(2))
						}
						else if ("HOMEPAGE".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE)
						}
						else if ("BLOG".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_BLOG)
						}
						else if ("PROFILE".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_PROFILE)
						}
						else if ("HOME".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_HOME)
						}
						else if ("WORK".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_WORK)
						}
						else if ("FTP".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_FTP)
						}
						else if ("OTHER".equals(type, ignoreCase = true)) {
							row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_OTHER)
						}
						else {
							row.put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_CUSTOM)
							row.put(ContactsContract.CommonDataKinds.Website.LABEL, type)
						}
					}

					override fun onClick(dialog: DialogInterface, which: Int) {
						val intent: Intent?

						when (which) {
							0 -> {
								intent = Intent(ContactsContract.Intents.Insert.ACTION)
								intent.type = ContactsContract.RawContacts.CONTENT_TYPE
							}

							1 -> {
								intent = Intent(Intent.ACTION_INSERT_OR_EDIT)
								intent.type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
							}

							else -> {
								return
							}
						}

						intent.putExtra(ContactsContract.Intents.Insert.NAME, formatName(currentUser.firstName, currentUser.lastName))

						val data = ArrayList<ContentValues>()

						var orgAdded = false

						other.forEach { item ->
							if (item.type == 1) {
								val row = ContentValues()
								row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
								row.put(ContactsContract.CommonDataKinds.Email.ADDRESS, item.getValue(false))

								val type = item.getRawType(false)

								fillRowWithType(type, row)

								data.add(row)
							}
							else if (item.type == 3) {
								val row = ContentValues()
								row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
								row.put(ContactsContract.CommonDataKinds.Website.URL, item.getValue(false))

								val type = item.getRawType(false)

								fillUrlRowWithType(type, row)

								data.add(row)
							}
							else if (item.type == 4) {
								val row = ContentValues()
								row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
								row.put(ContactsContract.CommonDataKinds.Note.NOTE, item.getValue(false))

								data.add(row)
							}
							else if (item.type == 5) {
								val row = ContentValues()
								row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)
								row.put(ContactsContract.CommonDataKinds.Event.START_DATE, item.getValue(false))
								row.put(ContactsContract.CommonDataKinds.Event.TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY)

								data.add(row)
							}
							else if (item.type == 2) {
								val row = ContentValues()
								row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)

								val args = item.rawValue

								if (args.isNotEmpty()) {
									row.put(ContactsContract.CommonDataKinds.StructuredPostal.POBOX, args[0])
								}

								if (args.size > 1) {
									row.put(ContactsContract.CommonDataKinds.StructuredPostal.NEIGHBORHOOD, args[1])
								}

								if (args.size > 2) {
									row.put(ContactsContract.CommonDataKinds.StructuredPostal.STREET, args[2])
								}

								if (args.size > 3) {
									row.put(ContactsContract.CommonDataKinds.StructuredPostal.CITY, args[3])
								}

								if (args.size > 4) {
									row.put(ContactsContract.CommonDataKinds.StructuredPostal.REGION, args[4])
								}

								if (args.size > 5) {
									row.put(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE, args[5])
								}

								if (args.size > 6) {
									row.put(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY, args[6])
								}

								val type = item.getRawType(false)

								if ("HOME".equals(type, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME)
								}
								else if ("WORK".equals(type, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK)
								}
								else if ("OTHER".equals(type, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER)
								}

								data.add(row)
							}
							else if (item.type == 20) {
								val row = ContentValues()
								row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)

								val imType = item.getRawType(true)
								val type = item.getRawType(false)

								row.put(ContactsContract.CommonDataKinds.Im.DATA, item.getValue(false))

								if ("AIM".equals(imType, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_AIM)
								}
								else if ("MSN".equals(imType, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_MSN)
								}
								else if ("YAHOO".equals(imType, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_YAHOO)
								}
								else if ("SKYPE".equals(imType, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_SKYPE)
								}
								else if ("QQ".equals(imType, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_QQ)
								}
								else if ("GOOGLE-TALK".equals(imType, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK)
								}
								else if ("ICQ".equals(imType, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_ICQ)
								}
								else if ("JABBER".equals(imType, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER)
								}
								else if ("NETMEETING".equals(imType, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_NETMEETING)
								}
								else {
									row.put(ContactsContract.CommonDataKinds.Im.PROTOCOL, ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM)
									row.put(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL, item.getRawType(true))
								}

								if ("HOME".equals(type, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.TYPE, ContactsContract.CommonDataKinds.Im.TYPE_HOME)
								}
								else if ("WORK".equals(type, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.TYPE, ContactsContract.CommonDataKinds.Im.TYPE_WORK)
								}
								else if ("OTHER".equals(type, ignoreCase = true)) {
									row.put(ContactsContract.CommonDataKinds.Im.TYPE, ContactsContract.CommonDataKinds.Im.TYPE_OTHER)
								}

								data.add(row)
							}
							else if (item.type == 6) {
								if (orgAdded) {
									return@forEach
								}

								orgAdded = true

								val row = ContentValues()
								row.put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)

								for (b in a until other.size) {
									val orgItem = other[b]

									if (orgItem.type != 6) {
										continue
									}

									val type = orgItem.getRawType(true)

									if ("ORG".equals(type, ignoreCase = true)) {
										val value = orgItem.rawValue

										if (value.isEmpty()) {
											continue
										}

										row.put(ContactsContract.CommonDataKinds.Organization.COMPANY, value[0])

										if (value.size >= 2) {
											row.put(ContactsContract.CommonDataKinds.Organization.DEPARTMENT, value[1])
										}
									}
									else if ("TITLE".equals(type, ignoreCase = true)) {
										row.put(ContactsContract.CommonDataKinds.Organization.TITLE, orgItem.getValue(false))
									}
									else if ("ROLE".equals(type, ignoreCase = true)) {
										row.put(ContactsContract.CommonDataKinds.Organization.TITLE, orgItem.getValue(false))
									}

									val orgType = orgItem.getRawType(true)

									if ("WORK".equals(orgType, ignoreCase = true)) {
										row.put(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
									}
									else if ("OTHER".equals(orgType, ignoreCase = true)) {
										row.put(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_OTHER)
									}
								}
								data.add(row)
							}
						}

						intent.putExtra("finishActivityOnSaveCompleted", true)
						intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, data)

						try {
							parentFragment.parentActivity!!.startActivity(intent)
							dismiss()
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				})

				builder.show()
			}
			else {
				val builder = if (currentUser.restrictionReason.isNotEmpty()) {
					StringBuilder(currentUser.restrictionReason[0].text ?: "")
				}
				else {
					StringBuilder(String.format(Locale.US, "BEGIN:VCARD\nVERSION:3.0\nFN:%1\$s\nEND:VCARD", formatName(currentUser.firstName, currentUser.lastName)))
				}

				val idx = builder.lastIndexOf("END:VCARD")

				if (idx >= 0) {
					other.reversed().forEach { item ->
						if (!item.checked) {
							return@forEach
						}
						for (b in item.vcardData.indices.reversed()) {
							builder.insert(idx, """
     ${item.vcardData[b]}
     
     """.trimIndent())
						}
					}

					currentUser.restrictionReason.clear()

					val reason = TLRPC.TLRestrictionReason()
					reason.text = builder.toString()
					reason.reason = ""
					reason.platform = ""

					currentUser.restrictionReason.add(reason)
				}

				if (parentFragment is ChatActivity && parentFragment.isInScheduleMode) {
					AlertsCreator.createScheduleDatePickerDialog(getContext(), parentFragment.dialogId, { notify, scheduleDate ->
						mDelegate?.didSelectContact(currentUser, notify, scheduleDate)
						dismiss()
					}, resourcesProvider)
				}
				else {
					mDelegate?.didSelectContact(currentUser, true, 0)
					dismiss()
				}
			}
		}
	}

	override fun onStart() {
		super.onStart()

		Bulletin.addDelegate((containerView as FrameLayout), object : Bulletin.Delegate {
			override fun getBottomOffset(tag: Int): Int {
				return AndroidUtilities.dp(74f)
			}
		})
	}

	override fun onStop() {
		super.onStop()
		Bulletin.removeDelegate((containerView as FrameLayout))
	}

	private fun updateLayout(animated: Boolean) {
		val child = scrollView.getChildAt(0)
		val top = child.top - scrollView.scrollY
		val newOffset = max(top, 0)
		var show = newOffset == 0

		if (show && actionBar.tag == null || !show && actionBar.tag != null) {
			actionBar.tag = if (show) 1 else null

			actionBarAnimation?.cancel()
			actionBarAnimation = null

			if (animated) {
				actionBarAnimation = AnimatorSet()
				actionBarAnimation?.duration = 180
				actionBarAnimation?.playTogether(ObjectAnimator.ofFloat(actionBar, View.ALPHA, if (show) 1.0f else 0.0f), ObjectAnimator.ofFloat(actionBarShadow, View.ALPHA, if (show) 1.0f else 0.0f))

				actionBarAnimation?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						actionBarAnimation = null
					}
				})

				actionBarAnimation?.start()
			}
			else {
				actionBar.alpha = if (show) 1.0f else 0.0f
				actionBarShadow.alpha = if (show) 1.0f else 0.0f
			}
		}

		if (scrollOffsetY != newOffset) {
			scrollOffsetY = newOffset
			containerView.invalidate()
		}

		show = child.bottom - scrollView.scrollY > scrollView.measuredHeight

		if (show && shadow.tag == null || !show && shadow.tag != null) {
			shadow.tag = if (show) 1 else null

			shadowAnimation?.cancel()
			shadowAnimation = null

			if (animated) {
				shadowAnimation = AnimatorSet()
				shadowAnimation?.duration = 180
				shadowAnimation?.playTogether(ObjectAnimator.ofFloat(shadow, View.ALPHA, if (show) 1.0f else 0.0f))

				shadowAnimation?.addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(animation: Animator) {
						shadowAnimation = null
					}
				})

				shadowAnimation?.start()
			}
			else {
				shadow.alpha = if (show) 1.0f else 0.0f
			}
		}
	}

	override fun canDismissWithSwipe(): Boolean {
		return false
	}

	private fun updateRows() {
		rowCount = 0
		userRow = rowCount++

		if (other.isEmpty()) {
			vcardStartRow = -1
			vcardEndRow = -1
		}
		else {
			vcardStartRow = rowCount
			rowCount += other.size
			vcardEndRow = rowCount
		}
	}

	private inner class ListAdapter {
		fun getItemCount(): Int {
			return rowCount
		}

		fun onBindViewHolder(itemView: View, position: Int, type: Int) {
			if (type == 1) {
				val cell = itemView as TextCheckBoxCell
				val item = other[position - vcardStartRow]

				val icon = if (item.type == 1) {
					R.drawable.msg_mention
				}
				else if (item.type == 2) {
					R.drawable.msg_location
				}
				else if (item.type == 3) {
					R.drawable.msg_link
				}
				else if (item.type == 4) {
					R.drawable.msg_info
				}
				else if (item.type == 5) {
					R.drawable.msg_calendar2
				}
				else if (item.type == 6) {
					if ("ORG".equals(item.getRawType(true), ignoreCase = true)) {
						R.drawable.msg_work
					}
					else {
						R.drawable.msg_jobtitle
					}
				}
				else if (item.type == 20) {
					R.drawable.msg_info
				}
				else {
					R.drawable.msg_info
				}

				cell.setVCardItem(item, icon, position != getItemCount() - 1)
			}
		}

		fun createView(context: Context, position: Int): View {
			val viewType = getItemViewType(position)

			val view = when (viewType) {
				0 -> UserCell(context)
				1 -> TextCheckBoxCell(context)
				else -> TextCheckBoxCell(context)
			}

			onBindViewHolder(view, position, viewType)

			return view
		}

		fun getItemViewType(position: Int): Int {
			return if (position == userRow) {
				0
			}
			else {
				1
			}
		}
	}
}
