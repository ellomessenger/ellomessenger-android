/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.text.SpannableStringBuilder
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AccountInstance
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.ContactsController
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.TL_accountDaysTTL
import org.telegram.tgnet.TLRPC.TL_account_getPassword
import org.telegram.tgnet.TLRPC.TL_account_setAccountTTL
import org.telegram.tgnet.TLRPC.TL_account_setGlobalPrivacySettings
import org.telegram.tgnet.TLRPC.TL_boolTrue
import org.telegram.tgnet.TLRPC.TL_contacts_toggleTopPeers
import org.telegram.tgnet.TLRPC.TL_globalPrivacySettings
import org.telegram.tgnet.TLRPC.TL_payments_clearSavedInfo
import org.telegram.tgnet.TLRPC.TL_privacyValueAllowAll
import org.telegram.tgnet.TLRPC.TL_privacyValueAllowChatParticipants
import org.telegram.tgnet.TLRPC.TL_privacyValueAllowUsers
import org.telegram.tgnet.TLRPC.TL_privacyValueDisallowAll
import org.telegram.tgnet.TLRPC.TL_privacyValueDisallowChatParticipants
import org.telegram.tgnet.TLRPC.TL_privacyValueDisallowUsers
import org.telegram.tgnet.TLRPC.account_Password
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.CheckBoxCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.RadioColorCell
import org.telegram.ui.Cells.ShadowSectionCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.Components.TextStyleSpan.TextStyleRun

@SuppressLint("NotifyDataSetChanged")
class PrivacySettingsActivity : BaseFragment(), NotificationCenterDelegate {
	private var listAdapter: ListAdapter? = null
	private var listView: RecyclerListView? = null
	private var progressDialog: AlertDialog? = null
	private var layoutManager: LinearLayoutManager? = null
	private var currentPassword: account_Password? = null
	private var privacySectionRow = 0
	private var blockedRow = 0
	private var phoneNumberRow = 0
	private var lastSeenRow = 0
	private var profilePhotoRow = 0
	private var forwardsRow = 0
	private var callsRow = 0
	private var voicesRow = 0
	private var emailLoginRow = 0
	private var privacyShadowRow = 0
	private var groupsRow = 0
	private var groupsDetailRow = 0
	private var securitySectionRow = 0
	private var passwordRow = 0
	private var sessionsRow = 0
	private var passcodeRow = 0
	private var sessionsDetailRow = 0
	private var newChatsHeaderRow = 0
	private var newChatsRow = 0
	private var newChatsSectionRow = 0
	private var advancedSectionRow = 0
	private var deleteAccountRow = 0
	private var deleteAccountDetailRow = 0
	private var botsSectionRow = 0
	private var passportRow = 0
	private var paymentsClearRow = 0
	private var webSessionsRow = 0
	private var botsDetailRow = 0
	private var contactsSectionRow = 0
	private var contactsDeleteRow = 0
	private var contactsSuggestRow = 0
	private var contactsSyncRow = 0
	private var contactsDetailRow = 0
	private var secretSectionRow = 0
	private var secretMapRow = 0
	private var secretWebpageRow = 0
	private var secretDetailRow = 0
	private var rowCount = 0
	private var currentSync = false
	private var newSync = false
	private var currentSuggest = false
	private var newSuggest = false
	private var archiveChats = false
	private val clear = BooleanArray(2)

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()

		contactsController.loadPrivacySettings()
		messagesController.getBlockedPeers(true)

		newSuggest = userConfig.suggestContacts
		currentSuggest = newSuggest

		val privacySettings = contactsController.globalPrivacySettings

		if (privacySettings != null) {
			archiveChats = privacySettings.archive_and_mute_new_noncontact_peers
		}

		updateRows()

		loadPasswordSettings()

		notificationCenter.addObserver(this, NotificationCenter.privacyRulesUpdated)
		notificationCenter.addObserver(this, NotificationCenter.blockedUsersDidLoad)
		notificationCenter.addObserver(this, NotificationCenter.didSetOrRemoveTwoStepPassword)

		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()

		notificationCenter.removeObserver(this, NotificationCenter.privacyRulesUpdated)
		notificationCenter.removeObserver(this, NotificationCenter.blockedUsersDidLoad)
		notificationCenter.removeObserver(this, NotificationCenter.didSetOrRemoveTwoStepPassword)

		var save = false

		if (newSuggest != currentSuggest) {
			if (!newSuggest) {
				mediaDataController.clearTopPeers()
			}

			userConfig.suggestContacts = newSuggest

			save = true

			val req = TL_contacts_toggleTopPeers()
			req.enabled = newSuggest

			connectionsManager.sendRequest(req)
		}

		val globalPrivacySettings = contactsController.globalPrivacySettings

		if (globalPrivacySettings != null && globalPrivacySettings.archive_and_mute_new_noncontact_peers != archiveChats) {
			globalPrivacySettings.archive_and_mute_new_noncontact_peers = archiveChats

			save = true

			val req = TL_account_setGlobalPrivacySettings()
			req.settings = TL_globalPrivacySettings()
			req.settings.flags = req.settings.flags or 1
			req.settings.archive_and_mute_new_noncontact_peers = archiveChats

			connectionsManager.sendRequest(req)
		}

		if (save) {
			userConfig.saveConfig(false)
		}
	}

	override fun createView(context: Context): View? {
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)
		actionBar?.setTitle(context.getString(R.string.PrivacySettings))

		actionBar?.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment()
				}
			}
		})

		listAdapter = ListAdapter(context)
		fragmentView = FrameLayout(context)

		val frameLayout = fragmentView as FrameLayout
		frameLayout.setBackgroundResource(R.color.light_background)

		listView = RecyclerListView(context)

		listView?.layoutManager = object : LinearLayoutManager(context, VERTICAL, false) {
			override fun supportsPredictiveItemAnimations(): Boolean {
				return false
			}
		}.also { layoutManager = it }

		listView?.isVerticalScrollBarEnabled = false
		listView?.layoutAnimation = null

		frameLayout.addView(listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		listView?.adapter = listAdapter

		listView?.setOnItemClickListener { view, position ->
			if (!view.isEnabled) {
				return@setOnItemClickListener
			}

			if (position == blockedRow) {
				presentFragment(PrivacyUsersActivity())
			}
			else if (position == sessionsRow) {
				presentFragment(SessionsActivity(SessionsActivity.ALL_SESSIONS))
			}
			else if (position == webSessionsRow) {
				presentFragment(SessionsActivity(SessionsActivity.WEB_SESSIONS))
			}
			else if (position == deleteAccountRow) {
				if (parentActivity == null) {
					return@setOnItemClickListener
				}
				val ttl = contactsController.deleteAccountTTL

				val selected = if (ttl <= 31) {
					0
				}
				else if (ttl <= 93) {
					1
				}
				else if (ttl <= 182) {
					2
				}
				else {
					3
				}

				val builder = AlertDialog.Builder(context)
				builder.setTitle(context.getString(R.string.DeleteAccountTitle))

				val items = arrayOf(LocaleController.formatPluralString("Months", 1), LocaleController.formatPluralString("Months", 3), LocaleController.formatPluralString("Months", 6), LocaleController.formatPluralString("Years", 1))

				val linearLayout = LinearLayout(parentActivity)
				linearLayout.orientation = LinearLayout.VERTICAL

				builder.setView(linearLayout)

				for (a in items.indices) {
					val cell = RadioColorCell(parentActivity!!)
					cell.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
					cell.tag = a
					cell.setCheckColor(context.getColor(R.color.brand), context.getColor(R.color.white))
					cell.setTextAndValue(items[a], selected == a)

					linearLayout.addView(cell)

					cell.setOnClickListener { v: View ->
						builder.getDismissRunnable().run()
						val which = v.tag as Int
						var value = 0

						when (which) {
							0 -> value = 30
							1 -> value = 90
							2 -> value = 182
							3 -> value = 365
						}

						val progressDialog = AlertDialog(context, 3)
						progressDialog.setCanCancel(false)
						progressDialog.show()

						val req = TL_account_setAccountTTL()
						req.ttl = TL_accountDaysTTL()
						req.ttl.days = value

						connectionsManager.sendRequest(req) { response, _ ->
							AndroidUtilities.runOnUIThread {
								try {
									progressDialog.dismiss()
								}
								catch (e: Exception) {
									FileLog.e(e)
								}

								if (response is TL_boolTrue) {
									contactsController.deleteAccountTTL = req.ttl.days
									listAdapter?.notifyDataSetChanged()
								}
							}
						}
					}
				}

				builder.setNegativeButton(context.getString(R.string.Cancel), null)

				showDialog(builder.create())
			}
			else if (position == lastSeenRow) {
				presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_LAST_SEEN))
			}
			else if (position == phoneNumberRow) {
				presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHONE))
			}
			else if (position == groupsRow) {
				presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_INVITE))
			}
			else if (position == callsRow) {
				presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_CALLS))
			}
			else if (position == profilePhotoRow) {
				presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_PHOTO))
			}
			else if (position == forwardsRow) {
				presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_FORWARDS))
			}
			else if (position == voicesRow) {
				if (!userConfig.isPremium) {
					try {
						fragmentView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					BulletinFactory.of(this).createRestrictVoiceMessagesPremiumBulletin().show()

					return@setOnItemClickListener
				}

				presentFragment(PrivacyControlActivity(ContactsController.PRIVACY_RULES_TYPE_VOICE_MESSAGES))
			}
			else if (position == emailLoginRow) {
				val currentPassword = currentPassword

				if (currentPassword?.login_email_pattern == null) {
					return@setOnItemClickListener
				}

				val spannable = SpannableStringBuilder.valueOf(currentPassword.login_email_pattern)
				val startIndex = currentPassword.login_email_pattern.indexOf('*')
				val endIndex = currentPassword.login_email_pattern.lastIndexOf('*')

				if (startIndex != endIndex && startIndex != -1 && endIndex != -1) {
					val run = TextStyleRun()
					run.styleFlags = run.styleFlags or TextStyleSpan.FLAG_STYLE_SPOILER
					run.start = startIndex
					run.end = endIndex + 1

					spannable.setSpan(TextStyleSpan(run), startIndex, endIndex + 1, 0)
				}

				// TODO: check
//                new AlertDialog.Builder(context)
//                        .setTitle(spannable)
//                        .setMessage(LocaleController.getString(R.string.EmailLoginChangeMessage))
//                        .setPositiveButton(LocaleController.getString(R.string.ChangeEmail), (dialog, which) -> presentFragment(new LoginActivity().changeEmail(() -> {
//                            Bulletin.LottieLayout layout = new Bulletin.LottieLayout(getContext());
//                            layout.setAnimation(R.raw.email_check_inbox);
//                            layout.textView.setText(LocaleController.getString(R.string.YourLoginEmailChangedSuccess));
//                            int duration = Bulletin.DURATION_SHORT;
//                            Bulletin.make(PrivacySettingsActivity.this, layout, duration).show();
//
//                            try {
//                                fragmentView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
//                            } catch (Exception ignored) {}
//
//                            loadPasswordSettings();
//                        })))
//                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
//                        .show();
			}
			else if (position == passwordRow) {
				val currentPassword = currentPassword ?: return@setOnItemClickListener

				if (!TwoStepVerificationActivity.canHandleCurrentPassword(currentPassword, false)) {
					AlertsCreator.showUpdateAppAlert(parentActivity, context.getString(R.string.UpdateAppAlert), true)
				}

				if (currentPassword.has_password) {
					val fragment = TwoStepVerificationActivity()
					fragment.setPassword(currentPassword)
					presentFragment(fragment)
				}
				else {
					val type = if (currentPassword.email_unconfirmed_pattern.isNullOrEmpty()) {
						TwoStepVerificationSetupActivity.TYPE_INTRO
					}
					else {
						TwoStepVerificationSetupActivity.TYPE_EMAIL_CONFIRM
					}

					presentFragment(TwoStepVerificationSetupActivity(type, currentPassword))
				}
			}
			else if (position == passcodeRow) {
				presentFragment(PasscodeActivity.determineOpenFragment())
			}
			else if (position == secretWebpageRow) {
				if (messagesController.secretWebpagePreview == 1) {
					messagesController.secretWebpagePreview = 0
				}
				else {
					messagesController.secretWebpagePreview = 1
				}

				MessagesController.getGlobalMainSettings().edit().putInt("secretWebpage2", messagesController.secretWebpagePreview).commit()

				if (view is TextCheckCell) {
					view.isChecked = messagesController.secretWebpagePreview == 1
				}
			}
			else if (position == contactsDeleteRow) {
				val parentActivity = parentActivity ?: return@setOnItemClickListener

				val builder = AlertDialog.Builder(parentActivity)
				builder.setTitle(parentActivity.getString(R.string.SyncContactsDeleteTitle))
				builder.setMessage(AndroidUtilities.replaceTags(parentActivity.getString(R.string.SyncContactsDeleteText)))
				builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

				builder.setPositiveButton(parentActivity.getString(R.string.Delete)) { _, _ ->
					val builder12 = AlertDialog.Builder(parentActivity, 3)

					progressDialog = builder12.show()
					progressDialog?.setCanCancel(false)

					if (currentSync != newSync) {
						currentSync = newSync
						userConfig.saveConfig(false)
					}

					contactsController.deleteAllContacts {
						progressDialog?.dismiss()
					}
				}

				val alertDialog = builder.create()
				showDialog(alertDialog)

				val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
				button?.setTextColor(context.getColor(R.color.purple))
			}
			else if (position == contactsSuggestRow) {
				val cell = view as TextCheckCell

				if (newSuggest) {
					val parentActivity = parentActivity ?: return@setOnItemClickListener

					val builder = AlertDialog.Builder(parentActivity)
					builder.setTitle(parentActivity.getString(R.string.SuggestContactsTitle))
					builder.setMessage(parentActivity.getString(R.string.SuggestContactsAlert))

					builder.setPositiveButton(parentActivity.getString(R.string.MuteDisable)) { _, _ ->
						val req = TL_payments_clearSavedInfo()
						req.credentials = clear[1]
						req.info = clear[0]

						userConfig.tmpPassword = null
						userConfig.saveConfig(false)

						connectionsManager.sendRequest(req) { _, _ ->
							AndroidUtilities.runOnUIThread {
								newSuggest = !newSuggest
								cell.isChecked = newSuggest
							}
						}
					}

					builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

					val alertDialog = builder.create()
					showDialog(alertDialog)

					val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
					button?.setTextColor(parentActivity.getColor(R.color.purple))
				}
				else {
					newSuggest = true
					cell.isChecked = true
				}
			}
			else if (position == newChatsRow) {
				val cell = view as TextCheckCell
				archiveChats = !archiveChats
				cell.isChecked = archiveChats
			}
			else if (position == contactsSyncRow) {
				newSync = !newSync

				if (view is TextCheckCell) {
					view.isChecked = newSync
				}
			}
			else if (position == secretMapRow) {
				val parentActivity = parentActivity ?: return@setOnItemClickListener
				AlertsCreator.showSecretLocationAlert(parentActivity, currentAccount, { listAdapter?.notifyDataSetChanged() }, false)
			}
			else if (position == paymentsClearRow) {
				val parentActivity = parentActivity ?: return@setOnItemClickListener

				val builder = AlertDialog.Builder(parentActivity)
				builder.setTitle(parentActivity.getString(R.string.PrivacyPaymentsClearAlertTitle))
				builder.setMessage(parentActivity.getString(R.string.PrivacyPaymentsClearAlertText))

				val linearLayout = LinearLayout(parentActivity)
				linearLayout.orientation = LinearLayout.VERTICAL

				builder.setView(linearLayout)

				for (a in 0..1) {
					val name = if (a == 0) {
						parentActivity.getString(R.string.PrivacyClearShipping)
					}
					else {
						parentActivity.getString(R.string.PrivacyClearPayment)
					}

					clear[a] = true

					val checkBoxCell = CheckBoxCell(parentActivity, 1, 21)
					checkBoxCell.tag = a
					checkBoxCell.background = Theme.getSelectorDrawable(false)
					checkBoxCell.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)

					linearLayout.addView(checkBoxCell, createLinear(LayoutHelper.MATCH_PARENT, 50))

					checkBoxCell.setText(name, null, checked = true, divider = false)
					checkBoxCell.setTextColor(parentActivity.getColor(R.color.text))

					checkBoxCell.setOnClickListener {
						val cell = it as CheckBoxCell
						val num = cell.tag as Int
						clear[num] = !clear[num]
						cell.setChecked(clear[num], true)
					}
				}

				builder.setPositiveButton(parentActivity.getString(R.string.ClearButton)) { _, _ ->
					try {
						visibleDialog?.dismiss()
					}
					catch (e: Exception) {
						FileLog.e(e)
					}

					val builder1 = AlertDialog.Builder(parentActivity)
					builder1.setTitle(parentActivity.getString(R.string.PrivacyPaymentsClearAlertTitle))
					builder1.setMessage(parentActivity.getString(R.string.PrivacyPaymentsClearAlert))

					builder1.setPositiveButton(parentActivity.getString(R.string.ClearButton)) { _, _ ->
						val req = TL_payments_clearSavedInfo()
						req.credentials = clear[1]
						req.info = clear[0]

						userConfig.tmpPassword = null
						userConfig.saveConfig(false)

						connectionsManager.sendRequest(req)

						val text = if (clear[0] && clear[1]) {
							parentActivity.getString(R.string.PrivacyPaymentsPaymentShippingCleared)
						}
						else if (clear[0]) {
							parentActivity.getString(R.string.PrivacyPaymentsShippingInfoCleared)
						}
						else if (clear[1]) {
							parentActivity.getString(R.string.PrivacyPaymentsPaymentInfoCleared)
						}
						else {
							return@setPositiveButton
						}

						BulletinFactory.of(this@PrivacySettingsActivity).createSimpleBulletin(R.raw.chats_infotip, text).show()
					}

					builder1.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

					showDialog(builder1.create())

					val alertDialog = builder1.create()

					showDialog(alertDialog)

					val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
					button?.setTextColor(parentActivity.getColor(R.color.purple))
				}

				builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

				showDialog(builder.create())

				val alertDialog = builder.create()

				showDialog(alertDialog)

				val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView

				button?.setTextColor(parentActivity.getColor(R.color.purple))
			}
			else if (position == passportRow) {
				presentFragment(PassportActivity(PassportActivity.TYPE_PASSWORD, 0, "", "", null, null, null, null, null))
			}
		}

		return fragmentView
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.privacyRulesUpdated -> {
				val privacySettings = contactsController.globalPrivacySettings

				if (privacySettings != null) {
					archiveChats = privacySettings.archive_and_mute_new_noncontact_peers
				}

				listAdapter?.notifyDataSetChanged()
			}

			NotificationCenter.blockedUsersDidLoad -> {
				listAdapter?.notifyItemChanged(blockedRow)
			}

			NotificationCenter.didSetOrRemoveTwoStepPassword -> {
				if (args.isNotEmpty()) {
					currentPassword = args[0] as account_Password
					listAdapter?.notifyItemChanged(passwordRow)
				}
				else {
					currentPassword = null
					loadPasswordSettings()
					updateRows()
				}
			}
		}
	}

	private fun updateRows(notify: Boolean = true) {
		rowCount = 0
		privacySectionRow = rowCount++
		blockedRow = rowCount++
		phoneNumberRow = rowCount++
		lastSeenRow = rowCount++
		profilePhotoRow = rowCount++
		forwardsRow = rowCount++
		callsRow = rowCount++
		groupsRow = rowCount++
		groupsDetailRow = -1

		voicesRow = if (!messagesController.premiumLocked || userConfig.isPremium) {
			rowCount++
		}
		else {
			-1
		}

		privacyShadowRow = rowCount++
		securitySectionRow = rowCount++
		passcodeRow = rowCount++
		passwordRow = rowCount++

		emailLoginRow = if (if (currentPassword != null) currentPassword?.login_email_pattern != null else SharedConfig.hasEmailLogin) {
			rowCount++
		}
		else {
			-1
		}

		if (currentPassword != null) {
			val hasEmail = currentPassword?.login_email_pattern != null

			if (SharedConfig.hasEmailLogin != hasEmail) {
				SharedConfig.hasEmailLogin = hasEmail
				SharedConfig.saveConfig()
			}
		}

		sessionsRow = rowCount++
		sessionsDetailRow = rowCount++

		if (messagesController.autoarchiveAvailable || userConfig.isPremium) {
			newChatsHeaderRow = rowCount++
			newChatsRow = rowCount++
			newChatsSectionRow = rowCount++
		}
		else {
			newChatsHeaderRow = -1
			newChatsRow = -1
			newChatsSectionRow = -1
		}

		advancedSectionRow = rowCount++
		deleteAccountRow = rowCount++
		deleteAccountDetailRow = rowCount++
		botsSectionRow = rowCount++

		passportRow = if (userConfig.hasSecureData) {
			rowCount++
		}
		else {
			-1
		}

		paymentsClearRow = rowCount++
		webSessionsRow = rowCount++
		botsDetailRow = rowCount++
		contactsSectionRow = rowCount++
		contactsDeleteRow = rowCount++
		contactsSyncRow = rowCount++
		contactsSuggestRow = rowCount++
		contactsDetailRow = rowCount++
		secretSectionRow = rowCount++
		secretMapRow = rowCount++
		secretWebpageRow = rowCount++
		secretDetailRow = rowCount++

		if (notify) {
			listAdapter?.notifyDataSetChanged()
		}
	}

	fun setCurrentPassword(currentPassword: account_Password?): PrivacySettingsActivity {
		this.currentPassword = currentPassword

		if (currentPassword != null) {
			initPassword()
		}

		return this
	}

	private fun initPassword() {
		TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword)

		if (!userConfig.hasSecureData && currentPassword!!.has_secure_values) {
			userConfig.hasSecureData = true
			userConfig.saveConfig(false)
			updateRows()
		}
		else {
			if (currentPassword != null) {
				val wasEmailRow = emailLoginRow
				val appear = currentPassword!!.login_email_pattern != null && emailLoginRow == -1
				val disappear = currentPassword!!.login_email_pattern == null && emailLoginRow != -1

				if (appear || disappear) {
					updateRows(false)

					if (appear) {
						listAdapter?.notifyItemInserted(emailLoginRow)
					}
					else {
						listAdapter?.notifyItemRemoved(wasEmailRow)
					}
				}
			}

			listAdapter?.notifyItemChanged(passwordRow)
		}
	}

	private fun loadPasswordSettings() {
		val req = TL_account_getPassword()

		connectionsManager.sendRequest(req, { response, _ ->
			if (response != null) {
				val password = response as account_Password

				AndroidUtilities.runOnUIThread {
					currentPassword = password
					initPassword()
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors or ConnectionsManager.RequestFlagWithoutLogin)
	}

	override fun onResume() {
		super.onResume()
		listAdapter?.notifyDataSetChanged()
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val position = holder.adapterPosition
			return position == passcodeRow || position == passwordRow || position == blockedRow || position == sessionsRow || position == secretWebpageRow || position == webSessionsRow || position == groupsRow && !contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_INVITE) || position == lastSeenRow && !contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_LAST_SEEN) || position == callsRow && !contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_CALLS) || position == profilePhotoRow && !contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_PHOTO) || position == forwardsRow && !contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_FORWARDS) || position == phoneNumberRow && !contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_PHONE) || position == voicesRow && !contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_VOICE_MESSAGES) || position == deleteAccountRow && !contactsController.getLoadingDeleteInfo() || position == newChatsRow && !contactsController.getLoadingGlobalSettings() || position == emailLoginRow || position == paymentsClearRow || position == secretMapRow || position == contactsSyncRow || position == passportRow || position == contactsDeleteRow || position == contactsSuggestRow
		}

		override fun getItemCount(): Int {
			return rowCount
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = TextSettingsCell(mContext)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}

				1 -> {
					view = TextInfoPrivacyCell(mContext)
				}

				2 -> {
					view = HeaderCell(mContext)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}

				4 -> {
					view = ShadowSectionCell(mContext)
				}

				3 -> {
					view = TextCheckCell(mContext)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}

				else -> {
					view = TextCheckCell(mContext)
					view.setBackgroundColor(mContext.getColor(R.color.background))
				}
			}
			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				0 -> {
					var showLoading = false
					var value: String? = null
					var loadingLen = 16
					val animated = holder.itemView.tag != null && holder.itemView.tag as Int == position

					holder.itemView.tag = position

					val textCell = holder.itemView as TextSettingsCell

					if (position == blockedRow) {
						val totalCount = messagesController.totalBlockedCount

						if (totalCount == 0) {
							textCell.setTextAndValue(textCell.context.getString(R.string.BlockedUsers), textCell.context.getString(R.string.BlockedEmpty), true)
						}
						else if (totalCount > 0) {
							textCell.setTextAndValue(textCell.context.getString(R.string.BlockedUsers), String.format("%d", totalCount), true)
						}
						else {
							showLoading = true
							textCell.setText(textCell.context.getString(R.string.BlockedUsers), true)
						}
					}
					else if (position == sessionsRow) {
						textCell.setText(textCell.context.getString(R.string.SessionsTitle), false)
					}
					else if (position == webSessionsRow) {
						textCell.setText(textCell.context.getString(R.string.WebSessionsTitle), false)
					}
					else if (position == passwordRow) {
						if (currentPassword == null) {
							showLoading = true
						}
						else if (currentPassword!!.has_password) {
							value = textCell.context.getString(R.string.PasswordOn)
						}
						else {
							value = textCell.context.getString(R.string.PasswordOff)
						}

						textCell.setTextAndValue(textCell.context.getString(R.string.TwoStepVerification), value, true)
					}
					else if (position == passcodeRow) {
						textCell.setText(textCell.context.getString(R.string.Passcode), true)
					}
					else if (position == emailLoginRow) {
						textCell.setText(textCell.context.getString(R.string.EmailLogin), true)
					}
					else if (position == phoneNumberRow) {
						if (contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_PHONE)) {
							showLoading = true
							loadingLen = 30
						}
						else {
							value = formatRulesString(accountInstance, ContactsController.PRIVACY_RULES_TYPE_PHONE)
						}

						textCell.setTextAndValue(textCell.context.getString(R.string.PrivacyPhone), value, true)
					}
					else if (position == lastSeenRow) {
						if (contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_LAST_SEEN)) {
							showLoading = true
							loadingLen = 30
						}
						else {
							value = formatRulesString(accountInstance, ContactsController.PRIVACY_RULES_TYPE_LAST_SEEN)
						}

						textCell.setTextAndValue(textCell.context.getString(R.string.PrivacyLastSeen), value, true)
					}
					else if (position == groupsRow) {
						if (contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_INVITE)) {
							showLoading = true
							loadingLen = 30
						}
						else {
							value = formatRulesString(accountInstance, ContactsController.PRIVACY_RULES_TYPE_INVITE)
						}

						textCell.setTextAndValue(textCell.context.getString(R.string.GroupsAndChannels), value, true)
					}
					else if (position == callsRow) {
						if (contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_CALLS)) {
							showLoading = true
							loadingLen = 30
						}
						else {
							value = formatRulesString(accountInstance, ContactsController.PRIVACY_RULES_TYPE_CALLS)
						}

						textCell.setTextAndValue(textCell.context.getString(R.string.Calls), value, true)
					}
					else if (position == profilePhotoRow) {
						if (contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_PHOTO)) {
							showLoading = true
							loadingLen = 30
						}
						else {
							value = formatRulesString(accountInstance, ContactsController.PRIVACY_RULES_TYPE_PHOTO)
						}

						textCell.setTextAndValue(textCell.context.getString(R.string.PrivacyProfilePhoto), value, true)
					}
					else if (position == forwardsRow) {
						if (contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_FORWARDS)) {
							showLoading = true
							loadingLen = 30
						}
						else {
							value = formatRulesString(accountInstance, ContactsController.PRIVACY_RULES_TYPE_FORWARDS)
						}

						textCell.setTextAndValue(textCell.context.getString(R.string.PrivacyForwards), value, true)
					}
					else if (position == voicesRow) {
						if (contactsController.getLoadingPrivacyInfo(ContactsController.PRIVACY_RULES_TYPE_VOICE_MESSAGES)) {
							showLoading = true
							loadingLen = 30
						}
						else if (!userConfig.isPremium) {
							value = textCell.context.getString(R.string.P2PEverybody)
						}
						else {
							value = formatRulesString(accountInstance, ContactsController.PRIVACY_RULES_TYPE_VOICE_MESSAGES)
						}

						textCell.setTextAndValue(textCell.context.getString(R.string.PrivacyVoiceMessages), value, false)

						val imageView = textCell.valueImageView

						if (!userConfig.isPremium) {
							imageView.visible()
							imageView.setImageResource(R.drawable.msg_mini_premiumlock)
							imageView.colorFilter = PorterDuffColorFilter(textCell.context.getColor(R.color.dark_gray), PorterDuff.Mode.SRC_IN)
						}
						else {
							imageView.colorFilter = PorterDuffColorFilter(textCell.context.getColor(R.color.dark_gray), PorterDuff.Mode.SRC_IN)
						}
					}
					else if (position == passportRow) {
						textCell.setText(textCell.context.getString(R.string.TelegramPassport), true)
					}
					else if (position == deleteAccountRow) {
						if (contactsController.getLoadingDeleteInfo()) {
							showLoading = true
						}
						else {
							val ttl = contactsController.deleteAccountTTL

							value = if (ttl <= 182) {
								LocaleController.formatPluralString("Months", ttl / 30)
							}
							else if (ttl == 365) {
								LocaleController.formatPluralString("Years", ttl / 365)
							}
							else {
								LocaleController.formatPluralString("Days", ttl)
							}
						}

						textCell.setTextAndValue(textCell.context.getString(R.string.DeleteAccountIfAwayFor3), value, false)
					}
					else if (position == paymentsClearRow) {
						textCell.setText(textCell.context.getString(R.string.PrivacyPaymentsClear), true)
					}
					else if (position == secretMapRow) {
						value = when (SharedConfig.mapPreviewType) {
							SharedConfig.MAP_PREVIEW_PROVIDER_ELLO -> textCell.context.getString(R.string.MapPreviewProviderTelegram)
							SharedConfig.MAP_PREVIEW_PROVIDER_GOOGLE -> textCell.context.getString(R.string.MapPreviewProviderGoogle)
							SharedConfig.MAP_PREVIEW_PROVIDER_NONE -> textCell.context.getString(R.string.MapPreviewProviderNobody)
							SharedConfig.MAP_PREVIEW_PROVIDER_YANDEX -> textCell.context.getString(R.string.MapPreviewProviderYandex)
							else -> textCell.context.getString(R.string.MapPreviewProviderYandex)
						}

						textCell.setTextAndValue(textCell.context.getString(R.string.MapPreviewProvider), value, true)
					}
					else if (position == contactsDeleteRow) {
						textCell.setText(textCell.context.getString(R.string.SyncContactsDelete), true)
					}

					textCell.setDrawLoading(showLoading, loadingLen, animated)
				}

				1 -> {
					val privacyCell = holder.itemView as TextInfoPrivacyCell

					when (position) {
						deleteAccountDetailRow -> {
							privacyCell.setText(privacyCell.context.getString(R.string.DeleteAccountHelp))
							privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, privacyCell.context.getColor(R.color.shadow))
						}

						groupsDetailRow -> {
							privacyCell.setText(privacyCell.context.getString(R.string.GroupsAndChannelsHelp))
							privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, privacyCell.context.getColor(R.color.shadow))
						}

						sessionsDetailRow -> {
							privacyCell.setText(privacyCell.context.getString(R.string.SessionsInfo))
							privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, privacyCell.context.getColor(R.color.shadow))
						}

						secretDetailRow -> {
							privacyCell.setText(privacyCell.context.getString(R.string.SecretWebPageInfo))
							privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, privacyCell.context.getColor(R.color.shadow))
						}

						botsDetailRow -> {
							privacyCell.setText(privacyCell.context.getString(R.string.PrivacyBotsInfo))
							privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, privacyCell.context.getColor(R.color.shadow))
						}

						contactsDetailRow -> {
							privacyCell.setText(privacyCell.context.getString(R.string.SuggestContactsInfo))
							privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, privacyCell.context.getColor(R.color.shadow))
						}

						newChatsSectionRow -> {
							privacyCell.setText(privacyCell.context.getString(R.string.ArchiveAndMuteInfo))
							privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, privacyCell.context.getColor(R.color.shadow))
						}
					}
				}

				2 -> {
					val headerCell = holder.itemView as HeaderCell

					when (position) {
						privacySectionRow -> {
							headerCell.setText(headerCell.context.getString(R.string.PrivacyTitle))
						}

						securitySectionRow -> {
							headerCell.setText(headerCell.context.getString(R.string.SecurityTitle))
						}

						advancedSectionRow -> {
							headerCell.setText(headerCell.context.getString(R.string.DeleteMyAccount))
						}

						secretSectionRow -> {
							headerCell.setText(headerCell.context.getString(R.string.SecretChat))
						}

						botsSectionRow -> {
							headerCell.setText(headerCell.context.getString(R.string.PrivacyBots))
						}

						contactsSectionRow -> {
							headerCell.setText(headerCell.context.getString(R.string.Contacts))
						}

						newChatsHeaderRow -> {
							headerCell.setText(headerCell.context.getString(R.string.NewChatsFromNonContacts))
						}
					}
				}

				3 -> {
					val textCheckCell = holder.itemView as TextCheckCell

					when (position) {
						secretWebpageRow -> {
							textCheckCell.setTextAndCheck(textCheckCell.context.getString(R.string.SecretWebPage), messagesController.secretWebpagePreview == 1, false)
						}

						contactsSyncRow -> {
							textCheckCell.setTextAndCheck(textCheckCell.context.getString(R.string.SyncContacts), newSync, true)
						}

						contactsSuggestRow -> {
							textCheckCell.setTextAndCheck(textCheckCell.context.getString(R.string.SuggestContacts), newSuggest, false)
						}

						newChatsRow -> {
							textCheckCell.setTextAndCheck(textCheckCell.context.getString(R.string.ArchiveAndMute), archiveChats, false)
						}
					}
				}
			}
		}

		override fun getItemViewType(position: Int): Int {
			when (position) {
				passportRow, lastSeenRow, phoneNumberRow, blockedRow, deleteAccountRow, sessionsRow, webSessionsRow, passwordRow, passcodeRow, groupsRow, paymentsClearRow, secretMapRow, contactsDeleteRow, emailLoginRow -> {
					return 0
				}

				deleteAccountDetailRow, groupsDetailRow, sessionsDetailRow, secretDetailRow, botsDetailRow, contactsDetailRow, newChatsSectionRow -> {
					return 1
				}

				securitySectionRow, advancedSectionRow, privacySectionRow, secretSectionRow, botsSectionRow, contactsSectionRow, newChatsHeaderRow -> {
					return 2
				}

				secretWebpageRow, contactsSyncRow, contactsSuggestRow, newChatsRow -> {
					return 3
				}

				privacyShadowRow -> {
					return 4
				}

				else -> {
					return 0
				}
			}
		}
	}

	companion object {
		@JvmStatic
		fun formatRulesString(accountInstance: AccountInstance, rulesType: Int): String {
			val privacyRules = accountInstance.contactsController.getPrivacyRules(rulesType)

			if (privacyRules == null || privacyRules.size == 0) {
				return if (rulesType == 3) {
					ApplicationLoader.applicationContext.getString(R.string.P2PNobody)
				}
				else {
					ApplicationLoader.applicationContext.getString(R.string.LastSeenNobody)
				}
			}

			var type = -1
			var plus = 0
			var minus = 0

			for (a in privacyRules.indices) {
				val rule = privacyRules[a]

				if (rule is TL_privacyValueAllowChatParticipants) {
					var b = 0
					val n = rule.chats.size

					while (b < n) {
						val chat = accountInstance.messagesController.getChat(rule.chats[b])

						if (chat == null) {
							b++
							continue
						}

						plus += chat.participants_count

						b++
					}
				}
				else if (rule is TL_privacyValueDisallowChatParticipants) {
					var b = 0
					val n = rule.chats.size

					while (b < n) {
						val chat = accountInstance.messagesController.getChat(rule.chats[b])

						if (chat == null) {
							b++
							continue
						}

						minus += chat.participants_count

						b++
					}
				}
				else if (rule is TL_privacyValueAllowUsers) {
					plus += rule.users.size
				}
				else if (rule is TL_privacyValueDisallowUsers) {
					minus += rule.users.size
				}
				else if (type == -1) {
					type = when (rule) {
						is TL_privacyValueAllowAll -> 0
						is TL_privacyValueDisallowAll -> 1
						else -> 2
					}
				}
			}

			if (type == 0 || type == -1 && minus > 0) {
				return if (rulesType == 3) {
					if (minus == 0) {
						ApplicationLoader.applicationContext.getString(R.string.P2PEverybody)
					}
					else {
						LocaleController.formatString("P2PEverybodyMinus", R.string.P2PEverybodyMinus, minus)
					}
				}
				else {
					if (minus == 0) {
						ApplicationLoader.applicationContext.getString(R.string.LastSeenEverybody)
					}
					else {
						LocaleController.formatString("LastSeenEverybodyMinus", R.string.LastSeenEverybodyMinus, minus)
					}
				}
			}
			else if (type == 2 || type == -1 && minus > 0 && plus > 0) {
				return if (rulesType == 3) {
					if (plus == 0 && minus == 0) {
						ApplicationLoader.applicationContext.getString(R.string.P2PContacts)
					}
					else {
						if (plus != 0 && minus != 0) {
							LocaleController.formatString("P2PContactsMinusPlus", R.string.P2PContactsMinusPlus, minus, plus)
						}
						else if (minus != 0) {
							LocaleController.formatString("P2PContactsMinus", R.string.P2PContactsMinus, minus)
						}
						else {
							LocaleController.formatString("P2PContactsPlus", R.string.P2PContactsPlus, plus)
						}
					}
				}
				else {
					if (plus == 0 && minus == 0) {
						ApplicationLoader.applicationContext.getString(R.string.LastSeenContacts)
					}
					else {
						if (plus != 0 && minus != 0) {
							LocaleController.formatString("LastSeenContactsMinusPlus", R.string.LastSeenContactsMinusPlus, minus, plus)
						}
						else if (minus != 0) {
							LocaleController.formatString("LastSeenContactsMinus", R.string.LastSeenContactsMinus, minus)
						}
						else {
							LocaleController.formatString("LastSeenContactsPlus", R.string.LastSeenContactsPlus, plus)
						}
					}
				}
			}
			else if (type == 1 || plus > 0) {
				return if (rulesType == 3) {
					if (plus == 0) {
						ApplicationLoader.applicationContext.getString(R.string.P2PNobody)
					}
					else {
						LocaleController.formatString("P2PNobodyPlus", R.string.P2PNobodyPlus, plus)
					}
				}
				else {
					if (plus == 0) {
						ApplicationLoader.applicationContext.getString(R.string.LastSeenNobody)
					}
					else {
						LocaleController.formatString("LastSeenNobodyPlus", R.string.LastSeenNobodyPlus, plus)
					}
				}
			}

			return ApplicationLoader.applicationContext.getString(R.string.NumberUnknown)
		}
	}
}
