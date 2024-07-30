/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.R
import org.telegram.messenger.UserObject
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.LayoutHelper.createFrameRelatively
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.ProfileActivity
import java.util.concurrent.atomic.AtomicReference

object BackButtonMenu {
	fun show(thisFragment: BaseFragment?, backButton: View, currentDialogId: Long): ActionBarPopupWindow? {
		if (thisFragment == null) {
			return null
		}

		val parentLayout = thisFragment.parentLayout
		val context: Context? = thisFragment.parentActivity
		val fragmentView = thisFragment.fragmentView

		if (parentLayout == null || context == null || fragmentView == null) {
			return null
		}
		val dialogs = getStackedHistoryDialogs(thisFragment, currentDialogId)
		if (dialogs.size <= 0) {
			return null
		}

		val layout = ActionBarPopupWindowLayout(context)
		val backgroundPaddings = Rect()
		val shadowDrawable = ResourcesCompat.getDrawable(context.resources, R.drawable.popup_fixed_alert, null)?.mutate()

		shadowDrawable?.getPadding(backgroundPaddings)

		layout.setBackgroundColor(ResourcesCompat.getColor(context.resources, R.color.background, null))

		val scrimPopupWindowRef = AtomicReference<ActionBarPopupWindow?>()

		for (i in dialogs.indices) {
			val pDialog = dialogs[i]
			val chat = pDialog.chat
			val user = pDialog.user

			val cell = FrameLayout(context)
			cell.minimumWidth = AndroidUtilities.dp(200f)

			val imageView = BackupImageView(context)
			imageView.setRoundRadius(AndroidUtilities.dp(32f))

			cell.addView(imageView, createFrameRelatively(32f, 32f, Gravity.START or Gravity.CENTER_VERTICAL, 13f, 0f, 0f, 0f))

			val titleView = TextView(context)
			titleView.setLines(1)
			titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			titleView.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
			titleView.ellipsize = TextUtils.TruncateAt.END

			cell.addView(titleView, createFrameRelatively(LayoutHelper.MATCH_PARENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 59f, 0f, 12f, 0f))

			val avatarDrawable = AvatarDrawable()
			avatarDrawable.setSmallSize(true)

			var thumb: Drawable? = avatarDrawable

			if (chat != null) {
				avatarDrawable.setInfo(chat)

				if (chat.photo != null && chat.photo.strippedBitmap != null) {
					thumb = chat.photo.strippedBitmap
				}

				imageView.setImage(ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL), "50_50", thumb, chat)
				titleView.text = chat.title
			}
			else if (user != null) {
				var name: String?

				if (user.photo?.strippedBitmap != null) {
					thumb = user.photo?.strippedBitmap
				}

				if (pDialog.activity == ChatActivity::class.java && UserObject.isUserSelf(user)) {
					name = context.getString(R.string.SavedMessages)
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED
					imageView.setImageDrawable(avatarDrawable)
				}
				else if (UserObject.isReplyUser(user)) {
					name = context.getString(R.string.RepliesTitle)
					avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_REPLIES
					imageView.setImageDrawable(avatarDrawable)
				}
				else if (UserObject.isDeleted(user)) {
					name = context.getString(R.string.HiddenName)
					avatarDrawable.setInfo(user)
					imageView.setImage(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, user)
				}
				else {
					name = UserObject.getUserName(user)
					avatarDrawable.setInfo(user)
					imageView.setImage(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), "50_50", thumb, user)
				}

				titleView.text = name
			}
			cell.background = Theme.getSelectorDrawable(Theme.getColor(Theme.key_listSelector), false)

			cell.setOnClickListener {
				if (scrimPopupWindowRef.get() != null) {
					scrimPopupWindowRef.getAndSet(null)?.dismiss()
				}

				if (pDialog.stackIndex >= 0) {
					var nextFragmentDialogId: Long? = null

					if (parentLayout.fragmentsStack == null || pDialog.stackIndex >= parentLayout.fragmentsStack.size) {
						nextFragmentDialogId = null
					}
					else {
						val nextFragment = parentLayout.fragmentsStack[pDialog.stackIndex]

						if (nextFragment is ChatActivity) {
							nextFragmentDialogId = nextFragment.dialogId
						}
						else if (nextFragment is ProfileActivity) {
							nextFragmentDialogId = nextFragment.getDialogId()
						}
					}

					if (nextFragmentDialogId != null && nextFragmentDialogId != pDialog.dialogId) {
						for (j in parentLayout.fragmentsStack.size - 2 downTo pDialog.stackIndex + 1) {
							parentLayout.removeFragmentFromStack(j)
						}
					}
					else {
						if (parentLayout.fragmentsStack != null) {
							for (j in parentLayout.fragmentsStack.size - 2 downTo pDialog.stackIndex + 1) {
								if (j >= 0 && j < parentLayout.fragmentsStack.size) {
									parentLayout.removeFragmentFromStack(j)
								}
							}

							if (pDialog.stackIndex < parentLayout.fragmentsStack.size) {
								parentLayout.showFragment(pDialog.stackIndex)
								parentLayout.closeLastFragment(true)
								return@setOnClickListener
							}
						}
					}
				}

				goToPulledDialog(thisFragment, pDialog)
			}

			layout.addView(cell, createLinear(LayoutHelper.MATCH_PARENT, 48))
		}

		val scrimPopupWindow = ActionBarPopupWindow(layout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
		scrimPopupWindowRef.set(scrimPopupWindow)
		scrimPopupWindow.setPauseNotifications(true)
		scrimPopupWindow.setDismissAnimationDuration(220)
		scrimPopupWindow.isOutsideTouchable = true
		scrimPopupWindow.isClippingEnabled = true
		scrimPopupWindow.animationStyle = R.style.PopupContextAnimation
		scrimPopupWindow.isFocusable = true

		layout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), View.MeasureSpec.AT_MOST))

		scrimPopupWindow.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
		scrimPopupWindow.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
		scrimPopupWindow.contentView.isFocusableInTouchMode = true

		layout.setFitItems(true)

		var popupX = AndroidUtilities.dp(8f) - backgroundPaddings.left

		if (AndroidUtilities.isTablet()) {
			val location = IntArray(2)
			fragmentView.getLocationInWindow(location)
			popupX += location[0]
		}

		scrimPopupWindow.showAtLocation(fragmentView, Gravity.LEFT or Gravity.TOP, popupX, (backButton.bottom - backgroundPaddings.top - AndroidUtilities.dp(8f)))

		return scrimPopupWindow
	}

	private fun goToPulledDialog(fragment: BaseFragment, dialog: PulledDialog?) {
		if (dialog == null) {
			return
		}

		if (dialog.activity == ChatActivity::class.java) {
			val bundle = Bundle()

			if (dialog.chat != null) {
				bundle.putLong("chat_id", dialog.chat!!.id)
			}
			else if (dialog.user != null) {
				bundle.putLong("user_id", dialog.user!!.id)
			}

			bundle.putInt("dialog_folder_id", dialog.folderId)
			bundle.putInt("dialog_filter_id", dialog.filterId)

			fragment.presentFragment(ChatActivity(bundle), true)
		}
		else if (dialog.activity == ProfileActivity::class.java) {
			val bundle = Bundle()
			bundle.putLong("dialog_id", dialog.dialogId)
			fragment.presentFragment(ProfileActivity(bundle), true)
		}
	}

	private fun getStackedHistoryDialogs(thisFragment: BaseFragment?, ignoreDialogId: Long): ArrayList<PulledDialog> {
		val dialogs = ArrayList<PulledDialog>()

		if (thisFragment == null) {
			return dialogs
		}

		val parentLayout = thisFragment.parentLayout ?: return dialogs
		val fragmentsStack = parentLayout.fragmentsStack
		val pulledDialogs = parentLayout.pulledDialogs

		if (fragmentsStack != null) {
			val count = fragmentsStack.size

			for (i in 0 until count) {
				val fragment = fragmentsStack[i]
				var activity: Class<*>
				var chat: Chat?
				var user: User? = null
				var dialogId: Long
				var folderId: Int
				var filterId: Int

				if (fragment is ChatActivity) {
					activity = ChatActivity::class.java

					if (fragment.chatMode != 0 || fragment.isReport) {
						continue
					}

					chat = fragment.currentChat
					user = fragment.currentUser
					dialogId = fragment.dialogId
					folderId = fragment.dialogFolderId
					filterId = fragment.dialogFilterId
				}
				else if (fragment is ProfileActivity) {
					activity = ProfileActivity::class.java
					chat = fragment.currentChat

					try {
						user = fragment.userInfo!!.user
					}
					catch (ignore: Exception) {
					}

					dialogId = fragment.getDialogId()
					folderId = 0
					filterId = 0
				}
				else {
					continue
				}

				if (dialogId != ignoreDialogId && !(ignoreDialogId == 0L && UserObject.isUserSelf(user))) {
					var alreadyAddedDialog = false

					for (d in dialogs.indices) {
						if (dialogs[d].dialogId == dialogId) {
							alreadyAddedDialog = true
							break
						}
					}

					if (!alreadyAddedDialog) {
						val pDialog = PulledDialog()
						pDialog.activity = activity
						pDialog.stackIndex = i
						pDialog.chat = chat
						pDialog.user = user
						pDialog.dialogId = dialogId
						pDialog.folderId = folderId
						pDialog.filterId = filterId

						if (pDialog.chat != null || pDialog.user != null) {
							dialogs.add(pDialog)
						}
					}
				}
			}
		}

		if (pulledDialogs != null) {
			val count = pulledDialogs.size

			for (i in count - 1 downTo 0) {
				val pulledDialog = pulledDialogs[i]

				if (pulledDialog.dialogId == ignoreDialogId) {
					continue
				}

				var alreadyAddedDialog = false

				for (d in dialogs.indices) {
					if (dialogs[d].dialogId == pulledDialog.dialogId) {
						alreadyAddedDialog = true
						break
					}
				}

				if (!alreadyAddedDialog) {
					dialogs.add(pulledDialog)
				}
			}
		}

		dialogs.sortWith { d1, d2 ->
			d2.stackIndex - d1.stackIndex
		}

		return dialogs
	}

	@JvmStatic
	fun addToPulledDialogs(thisFragment: BaseFragment?, stackIndex: Int, chat: Chat?, user: User?, dialogId: Long, folderId: Int, filterId: Int) {
		if (chat == null && user == null) {
			return
		}

		if (thisFragment == null) {
			return
		}

		val parentLayout = thisFragment.parentLayout ?: return

		if (parentLayout.pulledDialogs == null) {
			parentLayout.pulledDialogs = ArrayList()
		}

		var alreadyAdded = false

		for (d in parentLayout.pulledDialogs) {
			if (d.dialogId == dialogId) {
				alreadyAdded = true
				break
			}
		}

		if (!alreadyAdded) {
			val d = PulledDialog()
			d.activity = ChatActivity::class.java
			d.stackIndex = stackIndex
			d.dialogId = dialogId
			d.filterId = filterId
			d.folderId = folderId
			d.chat = chat
			d.user = user

			parentLayout.pulledDialogs.add(d)
		}
	}

	@JvmStatic
	fun clearPulledDialogs(thisFragment: BaseFragment?, fromIndex: Int) {
		if (thisFragment == null) {
			return
		}

		val parentLayout = thisFragment.parentLayout ?: return

		if (parentLayout.pulledDialogs != null) {
			var i = 0

			while (i < parentLayout.pulledDialogs.size) {
				if (parentLayout.pulledDialogs[i].stackIndex > fromIndex) {
					parentLayout.pulledDialogs.removeAt(i)
					i--
				}

				++i
			}
		}
	}

	class PulledDialog {
		var activity: Class<*>? = null
		var stackIndex = 0
		var chat: Chat? = null
		var user: User? = null
		var dialogId: Long = 0
		var folderId = 0
		var filterId = 0
	}
}
