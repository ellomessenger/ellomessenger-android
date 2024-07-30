/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 * Copyright Mykhailo Mykytyn, Ello 2023.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBarMenuSubItem
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import kotlin.math.min

class LinkActionView(context: Context, private val fragment: BaseFragment, bottomSheet: BottomSheet?, var permanent: Boolean) : LinearLayout(context) {
	private val copyView = TextView(context)
	private val shareView = TextView(context)
	private val removeView = TextView(context)
	private val frameLayout = FrameLayout(context)
	private var actionBarPopupWindow: ActionBarPopupWindow? = null
	private val avatarsContainer = AvatarsContainer(context)
	private var usersCount = 0
	private var hideRevokeOption = false
	private val optionsView = ImageView(context)
	private val linkView = TextView(context)
	private var loadingImporters = false
	private var point = FloatArray(2)
	private var link: String? = null
	var delegate: Delegate? = null
	var canEdit = true

	enum class LinkActionType {
		CHANNEL, GROUP, USER
	}

	init {
		orientation = VERTICAL

		linkView.setPadding(AndroidUtilities.dp(20f), AndroidUtilities.dp(18f), AndroidUtilities.dp(40f), AndroidUtilities.dp(18f))
		linkView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
		linkView.ellipsize = TextUtils.TruncateAt.MIDDLE
		linkView.isSingleLine = true

		linkView.background = MaterialShapeDrawable(ShapeAppearanceModel().toBuilder().setAllCorners(CornerFamily.ROUNDED, AndroidUtilities.dp(6.333f).toFloat()).build()).apply {
			setTint(ResourcesCompat.getColor(context.resources, R.color.feed_audio_background, null))
		}

		frameLayout.addView(linkView)

		val optionsDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.overflow_menu, null)
		optionsDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)

		optionsView.setImageDrawable(optionsDrawable)
		optionsView.contentDescription = context.getString(R.string.AccDescrMoreOptions)
		optionsView.scaleType = ImageView.ScaleType.CENTER

		frameLayout.addView(optionsView, createFrame(40, 48, Gravity.RIGHT or Gravity.CENTER_VERTICAL))

		addView(frameLayout, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 4, 0))

		val linearLayout = LinearLayout(context)
		linearLayout.orientation = HORIZONTAL

		copyView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.white, null))
		copyView.gravity = Gravity.CENTER_HORIZONTAL
		shareView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.white, null))

		var spannableStringBuilder = SpannableStringBuilder()
		spannableStringBuilder.append("..").setSpan(ColoredImageSpan(ContextCompat.getDrawable(context, R.drawable.msg_copy_white)), 0, 1, 0)

		spannableStringBuilder.setSpan(FixedWidthSpan(AndroidUtilities.dp(8f)), 1, 2, 0)
		spannableStringBuilder.append(context.getString(R.string.LinkActionCopy))
		spannableStringBuilder.append(".").setSpan(FixedWidthSpan(AndroidUtilities.dp(5f)), spannableStringBuilder.length - 1, spannableStringBuilder.length, 0)

		copyView.text = spannableStringBuilder
		copyView.contentDescription = context.getString(R.string.LinkActionCopy)
		copyView.setPadding(AndroidUtilities.dp(10f), AndroidUtilities.dp(10f), AndroidUtilities.dp(10f), AndroidUtilities.dp(10f))
		copyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
		copyView.typeface = Theme.TYPEFACE_BOLD
		copyView.isSingleLine = true
		copyView.background = ResourcesCompat.getDrawable(context.resources, R.drawable.rounded_button, null)

		linearLayout.addView(copyView, createLinear(0, 40, 1f, 0, 4, 0, 4, 0))

		shareView.gravity = Gravity.CENTER_HORIZONTAL

		spannableStringBuilder = SpannableStringBuilder()
		spannableStringBuilder.append("..").setSpan(ColoredImageSpan(ContextCompat.getDrawable(context, R.drawable.msg_share_white)), 0, 1, 0)
		spannableStringBuilder.setSpan(FixedWidthSpan(AndroidUtilities.dp(8f)), 1, 2, 0)
		spannableStringBuilder.append(context.getString(R.string.LinkActionShare))
		spannableStringBuilder.append(".").setSpan(FixedWidthSpan(AndroidUtilities.dp(5f)), spannableStringBuilder.length - 1, spannableStringBuilder.length, 0)

		shareView.text = spannableStringBuilder
		shareView.contentDescription = context.getString(R.string.LinkActionShare)
		shareView.setPadding(AndroidUtilities.dp(10f), AndroidUtilities.dp(10f), AndroidUtilities.dp(10f), AndroidUtilities.dp(10f))
		shareView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
		shareView.typeface = Theme.TYPEFACE_BOLD
		shareView.isSingleLine = true
		shareView.background = ResourcesCompat.getDrawable(context.resources, R.drawable.rounded_button, null)

		linearLayout.addView(shareView, createLinear(0, 40, 1f, 4, 0, 4, 0))

		removeView.gravity = Gravity.CENTER_HORIZONTAL

		spannableStringBuilder = SpannableStringBuilder()
		spannableStringBuilder.append("..").setSpan(ColoredImageSpan(ContextCompat.getDrawable(context, R.drawable.msg_delete_filled)), 0, 1, 0)
		spannableStringBuilder.setSpan(FixedWidthSpan(AndroidUtilities.dp(8f)), 1, 2, 0)
		spannableStringBuilder.append(context.getString(R.string.DeleteLink))
		spannableStringBuilder.append(".").setSpan(FixedWidthSpan(AndroidUtilities.dp(5f)), spannableStringBuilder.length - 1, spannableStringBuilder.length, 0)

		removeView.text = spannableStringBuilder
		removeView.setPadding(AndroidUtilities.dp(10f), AndroidUtilities.dp(10f), AndroidUtilities.dp(10f), AndroidUtilities.dp(10f))
		removeView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
		removeView.typeface = Theme.TYPEFACE_BOLD
		removeView.isSingleLine = true

		linearLayout.addView(removeView, createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 0, 4, 0))

		removeView.visibility = GONE

		addView(linearLayout, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 20f, 0f, 0f))

		addView(avatarsContainer, createLinear(LayoutHelper.MATCH_PARENT, 28 + 16, 0f, 12f, 0f, 0f))

		copyView.setOnClickListener {
			try {
				if (link == null) {
					return@setOnClickListener
				}

				val clipboard = it.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return@setOnClickListener
				val clip = ClipData.newPlainText("label", link)
				clipboard.setPrimaryClip(clip)

				if (bottomSheet != null) {
					BulletinFactory.createCopyLinkBulletin(bottomSheet.container).show()
				}
				else {
					BulletinFactory.createCopyLinkBulletin(fragment).show()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		if (permanent) {
			avatarsContainer.setOnClickListener {
				delegate?.showUsersForPermanentLink()
			}
		}

		shareView.setOnClickListener {
			try {
				if (link == null) {
					return@setOnClickListener
				}

				val intent = Intent(Intent.ACTION_SEND)
				intent.type = "text/plain"
				intent.putExtra(Intent.EXTRA_TEXT, link)

				fragment.startActivityForResult(Intent.createChooser(intent, context.getString(R.string.InviteToGroupByLink)), 500)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		removeView.setOnClickListener {
			val parentActivity = fragment.parentActivity ?: return@setOnClickListener

			val builder = AlertDialog.Builder(parentActivity)
			builder.setTitle(context.getString(R.string.DeleteLink))
			builder.setMessage(context.getString(R.string.DeleteLinkHelp))

			builder.setPositiveButtonColor(context.getColor(R.color.purple))
			builder.setNegativeButtonColor(context.getColor(R.color.brand))

			builder.setPositiveButton(context.getString(R.string.Delete)) { _, _ ->
				delegate?.removeLink()
			}

			builder.setNegativeButton(context.getString(R.string.Cancel), null)

			fragment.showDialog(builder.create())
		}

		optionsView.setOnClickListener {
			if (actionBarPopupWindow != null) {
				return@setOnClickListener
			}

			val layout = ActionBarPopupWindowLayout(context)
			var subItem: ActionBarMenuSubItem

			if (!permanent && canEdit) {
				subItem = ActionBarMenuSubItem(context, top = true, bottom = false)
				subItem.setTextAndIcon(context.getString(R.string.Edit), R.drawable.msg_edit)

				layout.addView(subItem, createLinear(LayoutHelper.MATCH_PARENT, 48))

				subItem.setOnClickListener {
					actionBarPopupWindow?.dismiss()
					delegate?.editLink()
				}
			}

			subItem = ActionBarMenuSubItem(context, top = true, bottom = false)
			subItem.setTextAndIcon(context.getString(R.string.GetQRCode), R.drawable.msg_qrcode)

			layout.addView(subItem, createLinear(LayoutHelper.MATCH_PARENT, 48))

			subItem.setOnClickListener {
				showQrCode()
			}

			if (!hideRevokeOption) {
				subItem = ActionBarMenuSubItem(context, top = false, bottom = true)
				subItem.setTextAndIcon(context.getString(R.string.RemoveLink), R.drawable.msg_delete)
				subItem.setColors(ResourcesCompat.getColor(context.resources, R.color.text, null), ResourcesCompat.getColor(context.resources, R.color.purple, null))

				subItem.setOnClickListener {
					actionBarPopupWindow?.dismiss()
					revokeLink()
				}

				layout.addView(subItem, createLinear(LayoutHelper.MATCH_PARENT, 48))
			}

			val container = bottomSheet?.container ?: fragment.parentLayout

			if (container != null) {
				var x = 0f

				getPointOnScreen(frameLayout, container, point)

				var y = point[1]

				val finalContainer: FrameLayout = container

				val dimView = object : View(context) {
					override fun onDraw(canvas: Canvas) {
						canvas.drawColor(0x33000000)

						getPointOnScreen(frameLayout, finalContainer, point)

						canvas.save()

						val clipTop = (frameLayout.parent as View).y + frameLayout.y

						if (clipTop < 1) {
							canvas.clipRect(0f, point[1] - clipTop + 1, measuredWidth.toFloat(), measuredHeight.toFloat())
						}

						canvas.translate(point[0], point[1])

						frameLayout.draw(canvas)

						canvas.restore()
					}
				}

				val preDrawListener = ViewTreeObserver.OnPreDrawListener {
					dimView.invalidate()
					true
				}

				finalContainer.viewTreeObserver.addOnPreDrawListener(preDrawListener)

				container.addView(dimView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

				dimView.alpha = 0f
				dimView.animate().alpha(1f).duration = 150

				layout.measure(MeasureSpec.makeMeasureSpec(container.measuredWidth, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(container.measuredHeight, MeasureSpec.UNSPECIFIED))

				actionBarPopupWindow = ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)

				actionBarPopupWindow?.setOnDismissListener {
					actionBarPopupWindow = null

					dimView.animate().cancel()

					dimView.animate().alpha(0f).setDuration(150).setListener(object : AnimatorListenerAdapter() {
						override fun onAnimationEnd(animation: Animator) {
							if (dimView.parent != null) {
								finalContainer.removeView(dimView)
							}

							finalContainer.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
						}
					})
				}

				actionBarPopupWindow?.isOutsideTouchable = true
				actionBarPopupWindow?.isFocusable = true
				actionBarPopupWindow?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
				actionBarPopupWindow?.animationStyle = R.style.PopupContextAnimation
				actionBarPopupWindow?.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
				actionBarPopupWindow?.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED

				layout.setDispatchKeyEventListener {
					if (it.keyCode == KeyEvent.KEYCODE_BACK && it.repeatCount == 0 && actionBarPopupWindow?.isShowing == true) {
						actionBarPopupWindow?.dismiss(true)
					}
				}

				if (AndroidUtilities.isTablet()) {
					y += container.paddingTop.toFloat()
					x -= container.paddingLeft.toFloat()
				}

				actionBarPopupWindow?.showAtLocation(container, 0, (container.measuredWidth - layout.measuredWidth - AndroidUtilities.dp(16f) + container.x + x).toInt(), (y + frameLayout.measuredHeight + container.y).toInt())
			}
		}

		frameLayout.setOnClickListener {
			copyView.callOnClick()
		}
	}

	private fun getPointOnScreen(frameLayout: FrameLayout?, finalContainer: FrameLayout, point: FloatArray) {
		var x = 0f
		var y = 0f
		var v: View? = frameLayout ?: return

		while (v !== finalContainer) {
			y += v?.y ?: 0f
			x += v?.x ?: 0f

			if (v is ScrollView) {
				y -= v.getScrollY().toFloat()
			}

			v = v?.parent as? View

			if (v !is ViewGroup) {
				return
			}
		}

		x -= finalContainer.paddingLeft.toFloat()
		y -= finalContainer.paddingTop.toFloat()

		point[0] = x
		point[1] = y
	}

	private fun showQrCode() {
		actionBarPopupWindow?.dismiss()
		delegate?.showQr()
	}

	fun setLink(link: String?) {
		this.link = link

		if (link == null) {
			linkView.text = context.getString(R.string.Loading)
		}
		else if (link.startsWith("https://")) {
			linkView.text = link.substring("https://".length)
		}
		else {
			linkView.text = link
		}
	}

	fun setRevoke(revoked: Boolean) {
		if (revoked) {
			optionsView.visibility = GONE
			shareView.visibility = GONE
			copyView.visibility = GONE
			removeView.visibility = VISIBLE
		}
		else {
			optionsView.visibility = VISIBLE
			shareView.visibility = VISIBLE
			copyView.visibility = VISIBLE
			removeView.visibility = GONE
		}
	}

	fun hideRevokeOption(b: Boolean) {
		if (hideRevokeOption != b) {
			hideRevokeOption = b
			optionsView.visibility = VISIBLE

			val optionsDrawable = ResourcesCompat.getDrawable(optionsView.context.resources, R.drawable.overflow_menu, null)
			optionsDrawable?.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(context.resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)

			optionsView.setImageDrawable(optionsDrawable)
		}
	}

	private inner class AvatarsContainer(context: Context) : FrameLayout(context) {
		var countTextView: TextView
		var avatarsImageView: AvatarsImageView

		init {
			avatarsImageView = object : AvatarsImageView(context, false) {
				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					val n = min(3, usersCount)
					val x = if (n == 0) 0 else 20 * (n - 1) + 24 + 8
					super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(x.toFloat()), MeasureSpec.EXACTLY), heightMeasureSpec)
				}
			}

			val linearLayout = LinearLayout(context)
			linearLayout.orientation = HORIZONTAL

			addView(linearLayout, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL))

			countTextView = TextView(context)
			countTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			countTextView.typeface = Theme.TYPEFACE_BOLD

			linearLayout.addView(avatarsImageView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT))
			linearLayout.addView(countTextView, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL))

			setPadding(0, AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f))

			avatarsImageView.commitTransition(false)
		}
	}

	private fun revokeLink() {
		val parentActivity = fragment.parentActivity ?: return

		val builder = AlertDialog.Builder(parentActivity)
		builder.setMessage(context.getString(R.string.RevokeAlert))
		builder.setTitle(context.getString(R.string.RevokeLink))

		builder.setPositiveButton(context.getString(R.string.RevokeButton)) { _, _ ->
			delegate?.revokeLink()
		}

		builder.setNegativeButton(context.getString(R.string.Cancel), null)
		builder.show()
	}

	fun setUsers(usersCount: Int, importers: ArrayList<User>?) {
		this.usersCount = usersCount

		if (usersCount == 0) {
			avatarsContainer.visibility = GONE
			setPadding(AndroidUtilities.dp(19f), AndroidUtilities.dp(18f), AndroidUtilities.dp(19f), AndroidUtilities.dp(18f))
		}
		else {
			avatarsContainer.visibility = VISIBLE
			setPadding(AndroidUtilities.dp(19f), AndroidUtilities.dp(18f), AndroidUtilities.dp(19f), AndroidUtilities.dp(10f))
			avatarsContainer.countTextView.text = LocaleController.formatPluralString("PeopleJoined", usersCount)
			avatarsContainer.requestLayout()
		}

		if (importers != null) {
			for (i in 0..2) {
				if (i < importers.size) {
					MessagesController.getInstance(UserConfig.selectedAccount).putUser(importers[i], false)
					avatarsContainer.avatarsImageView.setObject(i, UserConfig.selectedAccount, importers[i])
				}
				else {
					avatarsContainer.avatarsImageView.setObject(i, UserConfig.selectedAccount, null)
				}
			}

			avatarsContainer.avatarsImageView.commitTransition(false)
		}
	}

	fun loadUsers(invite: TLRPC.TL_chatInviteExported?, chatId: Long) {
		if (invite == null) {
			setUsers(0, null)
			return
		}

		setUsers(invite.usage, invite.importers)

		if (invite.usage > 0 && invite.importers == null && !loadingImporters) {
			val req = TLRPC.TL_messages_getChatInviteImporters()
			req.link = invite.link
			req.peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(-chatId)
			req.offset_user = TLRPC.TL_inputUserEmpty()
			req.limit = min(invite.usage, 3)

			loadingImporters = true

			ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					loadingImporters = false

					if (error == null) {
						val inviteImporters = response as TLRPC.TL_messages_chatInviteImporters

						if (invite.importers == null) {
							invite.importers = ArrayList(3)
						}

						invite.importers.clear()

						for (i in inviteImporters.users.indices) {
							invite.importers.addAll(inviteImporters.users)
						}

						setUsers(invite.usage, invite.importers)
					}
				}
			}
		}
	}

	interface Delegate {
		fun showQr()
		fun revokeLink()
		fun editLink() {}
		fun removeLink() {}
		fun showUsersForPermanentLink() {}
	}
}
