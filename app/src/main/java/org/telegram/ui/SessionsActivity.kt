/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DocumentObject
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.SvgHelper.SvgDrawable
import org.telegram.messenger.UserConfig
import org.telegram.messenger.UserConfig.Companion.getInstance
import org.telegram.messenger.UserObject.getFirstName
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TL_account_authorizations
import org.telegram.tgnet.TLRPC.TL_account_getAuthorizations
import org.telegram.tgnet.TLRPC.TL_account_getWebAuthorizations
import org.telegram.tgnet.TLRPC.TL_account_resetAuthorization
import org.telegram.tgnet.TLRPC.TL_account_resetWebAuthorization
import org.telegram.tgnet.TLRPC.TL_account_resetWebAuthorizations
import org.telegram.tgnet.TLRPC.TL_account_setAuthorizationTTL
import org.telegram.tgnet.TLRPC.TL_account_webAuthorizations
import org.telegram.tgnet.TLRPC.TL_auth_acceptLoginToken
import org.telegram.tgnet.TLRPC.TL_auth_resetAuthorizations
import org.telegram.tgnet.TLRPC.TL_authorization
import org.telegram.tgnet.TLRPC.TL_boolTrue
import org.telegram.tgnet.TLRPC.TL_error
import org.telegram.tgnet.TLRPC.TL_messages_stickerSet
import org.telegram.tgnet.TLRPC.TL_webAuthorization
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.CameraScanActivity.CameraScanActivityDelegate
import org.telegram.ui.Cells.CheckBoxCell
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.RadioColorCell
import org.telegram.ui.Cells.SessionCell
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Cells.TextInfoPrivacyCell
import org.telegram.ui.Cells.TextSettingsCell
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.ColoredImageSpan
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EmptyTextProgressView
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LinkSpanDrawable.LinksTextView
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.Components.URLSpanNoUnderline
import org.telegram.ui.Components.UndoView
import java.util.Objects

@SuppressLint("NotifyDataSetChanged")
class SessionsActivity(private val currentType: Int) : BaseFragment(), NotificationCenterDelegate {
	private var listAdapter: ListAdapter? = null
	private var listView: RecyclerListView? = null
	private var emptyView: EmptyTextProgressView? = null
	private var globalFlickerLoadingView: FlickerLoadingView? = null
	private val sessions = ArrayList<TLObject>()
	private val passwordSessions = ArrayList<TLObject>()
	private var currentSession: TL_authorization? = null
	private var loading = false
	private var undoView: UndoView? = null
	private var ttlDays = 0
	private var currentSessionSectionRow = 0
	private var currentSessionRow = 0
	private var terminateAllSessionsRow = 0
	private var terminateAllSessionsDetailRow = 0
	private var passwordSessionsSectionRow = 0
	private var passwordSessionsStartRow = 0
	private var passwordSessionsEndRow = 0
	private var passwordSessionsDetailRow = 0
	private var otherSessionsSectionRow = 0
	private var otherSessionsStartRow = 0
	private var otherSessionsEndRow = 0
	private var otherSessionsTerminateDetail = 0
	private var noOtherSessionsRow = 0
	private var qrCodeRow = 0
	private var qrCodeDividerRow = 0
	private var rowCount = 0
	private var ttlHeaderRow = 0
	private var ttlRow = 0
	private var ttlDivideRow = 0
	private var repeatLoad = 0

	override fun onFragmentCreate(): Boolean {
		super.onFragmentCreate()
		updateRows()
		loadSessions(false)
		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newSessionReceived)
		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newSessionReceived)
	}

	override fun createView(context: Context): View? {
		globalFlickerLoadingView = FlickerLoadingView(context)
		globalFlickerLoadingView?.setIsSingleCell(true)

		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setAllowOverlayTitle(true)

		if (currentType == 0) {
			actionBar?.setTitle(context.getString(R.string.Devices))
		}
		else {
			actionBar?.setTitle(context.getString(R.string.WebSessionsTitle))
		}

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

		emptyView = EmptyTextProgressView(context)
		emptyView?.showProgress()

		frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER))

		listView = RecyclerListView(context)

		listView?.layoutManager = object : LinearLayoutManager(context, VERTICAL, false) {
			override fun supportsPredictiveItemAnimations(): Boolean {
				return true
			}
		}

		listView?.isVerticalScrollBarEnabled = false
		listView?.setEmptyView(emptyView)
		listView?.setAnimateEmptyView(true, 0)
		listView?.adapter = listAdapter

		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		val itemAnimator = DefaultItemAnimator()
		itemAnimator.setDurations(150)
		itemAnimator.moveInterpolator = CubicBezierInterpolator.DEFAULT
		itemAnimator.setTranslationInterpolator(CubicBezierInterpolator.DEFAULT)

		listView?.itemAnimator = itemAnimator

		listView?.setOnItemClickListener { _, position ->
			if (position == ttlRow) {
				val parentActivity = parentActivity ?: return@setOnItemClickListener

				val selected = if (ttlDays <= 7) {
					0
				}
				else if (ttlDays <= 93) {
					1
				}
				else if (ttlDays <= 183) {
					2
				}
				else {
					3
				}

				val builder = AlertDialog.Builder(parentActivity)
				builder.setTitle(parentActivity.getString(R.string.SessionsSelfDestruct))

				val items = arrayOf(LocaleController.formatPluralString("Weeks", 1), LocaleController.formatPluralString("Months", 3), LocaleController.formatPluralString("Months", 6), LocaleController.formatPluralString("Years", 1))

				val linearLayout = LinearLayout(parentActivity)
				linearLayout.orientation = LinearLayout.VERTICAL

				builder.setView(linearLayout)

				for (a in items.indices) {
					val cell = RadioColorCell(parentActivity)
					cell.setPadding(AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(4f), 0)
					cell.tag = a
					cell.setCheckColor(ResourcesCompat.getColor(parentActivity.resources, R.color.brand, null), ResourcesCompat.getColor(parentActivity.resources, R.color.brand, null))
					cell.setTextAndValue(items[a], selected == a)

					linearLayout.addView(cell)

					cell.setOnClickListener {
						builder.getDismissRunnable().run()

						val value = when (it.tag as Int) {
							0 -> 7
							1 -> 90
							2 -> 183
							3 -> 365
							else -> 0
						}

						val req = TL_account_setAuthorizationTTL()
						req.authorization_ttl_days = value

						ttlDays = value

						listAdapter?.notifyDataSetChanged()

						connectionsManager.sendRequest(req) { _, _ -> }
					}
				}
				builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

				showDialog(builder.create())
			}
			else if (position == terminateAllSessionsRow) {
				val parentActivity = parentActivity ?: return@setOnItemClickListener
				val builder = AlertDialog.Builder(parentActivity)

				val buttonText = if (currentType == 0) {
					builder.setMessage(parentActivity.getString(R.string.AreYouSureSessions))
					builder.setTitle(parentActivity.getString(R.string.AreYouSureSessionsTitle))

					parentActivity.getString(R.string.Terminate)
				}
				else {
					builder.setMessage(parentActivity.getString(R.string.AreYouSureWebSessions))
					builder.setTitle(parentActivity.getString(R.string.TerminateWebSessionsTitle))

					parentActivity.getString(R.string.Disconnect)
				}

				builder.setPositiveButton(buttonText) { _, _ ->
					if (currentType == 0) {
						val req = TL_auth_resetAuthorizations()

						ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
							AndroidUtilities.runOnUIThread {
								if (error == null && response is TL_boolTrue) {
									BulletinFactory.of(this@SessionsActivity).createSimpleBulletin(R.raw.contact_check, parentActivity.getString(R.string.AllSessionsTerminated)).show()
									loadSessions(false)
								}
							}

							for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
								val userConfig = getInstance(a)

								if (!userConfig.isClientActivated) {
									continue
								}

								userConfig.registeredForPush = false
								userConfig.saveConfig(false)

								MessagesController.getInstance(a).registerForPush(SharedConfig.pushType, SharedConfig.pushString)

								ConnectionsManager.getInstance(a).setUserId(userConfig.getClientUserId())
							}
						}
					}
					else {
						val req = TL_account_resetWebAuthorizations()

						ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
							AndroidUtilities.runOnUIThread {
								if (error == null && response is TL_boolTrue) {
									BulletinFactory.of(this@SessionsActivity).createSimpleBulletin(R.raw.contact_check, parentActivity.getString(R.string.AllWebSessionsTerminated)).show()
								}
								else {
									BulletinFactory.of(this@SessionsActivity).createSimpleBulletin(R.raw.error, parentActivity.getString(R.string.UnknownError)).show()
								}

								loadSessions(false)
							}
						}
					}
				}

				builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

				val alertDialog = builder.create()

				showDialog(alertDialog)

				val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
				button?.setTextColor(ResourcesCompat.getColor(parentActivity.resources, R.color.purple, null))
			}
			else if (position in otherSessionsStartRow until otherSessionsEndRow || position in passwordSessionsStartRow until passwordSessionsEndRow || position == currentSessionRow) {
				val parentActivity = parentActivity ?: return@setOnItemClickListener

				if (currentType == 0) {
					val authorization: TL_authorization?
					var isCurrentSession = false

					when (position) {
						currentSessionRow -> {
							authorization = currentSession
							isCurrentSession = true
						}

						in otherSessionsStartRow until otherSessionsEndRow -> {
							authorization = sessions[position - otherSessionsStartRow] as TL_authorization
						}

						else -> {
							authorization = passwordSessions[position - passwordSessionsStartRow] as TL_authorization
						}
					}

					showSessionBottomSheet(authorization, isCurrentSession)

					return@setOnItemClickListener
				}

				val builder = AlertDialog.Builder(parentActivity)
				val param = BooleanArray(1)

				val authorization = sessions[position - otherSessionsStartRow] as TL_webAuthorization

				builder.setMessage(LocaleController.formatString("TerminateWebSessionText", R.string.TerminateWebSessionText, authorization.domain))
				builder.setTitle(parentActivity.getString(R.string.TerminateWebSessionTitle))

				val frameLayout1 = FrameLayout(parentActivity)
				val user = MessagesController.getInstance(currentAccount).getUser(authorization.bot_id)

				val name = if (user != null) {
					getFirstName(user)
				}
				else {
					""
				}

				val cell = CheckBoxCell(parentActivity, 1)
				cell.background = Theme.getSelectorDrawable(false)
				cell.setText(LocaleController.formatString("TerminateWebSessionStop", R.string.TerminateWebSessionStop, name), "", checked = false, divider = false)
				cell.setPadding(if (LocaleController.isRTL) AndroidUtilities.dp(16f) else AndroidUtilities.dp(8f), 0, if (LocaleController.isRTL) AndroidUtilities.dp(8f) else AndroidUtilities.dp(16f), 0)

				frameLayout1.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f))

				cell.setOnClickListener {
					if (!it.isEnabled) {
						return@setOnClickListener
					}

					val cell1 = it as CheckBoxCell
					param[0] = !param[0]
					cell1.setChecked(param[0], true)
				}

				builder.setCustomViewOffset(16)
				builder.setView(frameLayout1)

				builder.setPositiveButton(parentActivity.getString(R.string.Disconnect)) { _, _ ->
					val progressDialog = AlertDialog(parentActivity, 3)
					progressDialog.setCanCancel(false)
					progressDialog.show()

					if (currentType == 0) {
						@Suppress("NAME_SHADOWING") val authorization = if (position in otherSessionsStartRow until otherSessionsEndRow) {
							sessions[position - otherSessionsStartRow] as TL_authorization
						}
						else {
							passwordSessions[position - passwordSessionsStartRow] as TL_authorization
						}

						val req = TL_account_resetAuthorization()
						req.hash = authorization.hash

						ConnectionsManager.getInstance(currentAccount).sendRequest(req) { _, error ->
							AndroidUtilities.runOnUIThread {
								try {
									progressDialog.dismiss()
								}
								catch (e: Exception) {
									FileLog.e(e)
								}

								if (error == null) {
									sessions.remove(authorization)
									passwordSessions.remove(authorization)

									updateRows()

									listAdapter?.notifyDataSetChanged()
								}
							}
						}
					}
					else {
						@Suppress("NAME_SHADOWING") val authorization = sessions[position - otherSessionsStartRow] as TL_webAuthorization

						val req = TL_account_resetWebAuthorization()
						req.hash = authorization.hash

						ConnectionsManager.getInstance(currentAccount).sendRequest(req) { _, error ->
							AndroidUtilities.runOnUIThread {
								try {
									progressDialog.dismiss()
								}
								catch (e: Exception) {
									FileLog.e(e)
								}

								if (error == null) {
									sessions.remove(authorization)
									updateRows()

									listAdapter?.notifyDataSetChanged()
								}
							}
						}

						if (param[0]) {
							MessagesController.getInstance(currentAccount).blockPeer(authorization.bot_id)
						}
					}
				}

				builder.setNegativeButton(parentActivity.getString(R.string.Cancel), null)

				val alertDialog = builder.create()

				showDialog(alertDialog)

				val button = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as? TextView
				button?.setTextColor(ResourcesCompat.getColor(parentActivity.resources, R.color.purple, null))
			}
		}

		if (currentType == 0) {
			undoView = object : UndoView(context) {
				override fun hide(apply: Boolean, animated: Int) {
					if (!apply) {
						val authorization = currentInfoObject as TL_authorization

						val req = TL_account_resetAuthorization()
						req.hash = authorization.hash

						ConnectionsManager.getInstance(currentAccount).sendRequest(req) { _, error ->
							AndroidUtilities.runOnUIThread {
								if (error == null) {
									sessions.remove(authorization)
									passwordSessions.remove(authorization)

									updateRows()

									listAdapter?.notifyDataSetChanged()

									loadSessions(true)
								}
							}
						}
					}

					super.hide(apply, animated)
				}
			}

			frameLayout.addView(undoView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.BOTTOM or Gravity.LEFT, 8f, 0f, 8f, 8f))
		}

		updateRows()

		return fragmentView
	}

	private fun showSessionBottomSheet(authorization: TL_authorization?, isCurrentSession: Boolean) {
		if (authorization == null) {
			return
		}

		val bottomSheet = SessionBottomSheet(this, authorization, isCurrentSession) {
			sessions.remove(it)
			passwordSessions.remove(it)

			updateRows()

			listAdapter?.notifyDataSetChanged()

			val req = TL_account_resetAuthorization()
			req.hash = it.hash

			ConnectionsManager.getInstance(currentAccount).sendRequest(req) { _, _ -> }
		}

		bottomSheet.show()
	}

	override fun onPause() {
		super.onPause()
		undoView?.hide(true, 0)
	}

	override fun onBecomeFullyHidden() {
		undoView?.hide(true, 0)
	}

	override fun onResume() {
		super.onResume()
		listAdapter?.notifyDataSetChanged()
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.newSessionReceived) {
			loadSessions(true)
		}
	}

	private fun loadSessions(silent: Boolean) {
		if (loading) {
			return
		}

		if (!silent) {
			loading = true
		}

		if (currentType == 0) {
			val req = TL_account_getAuthorizations()

			val reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					loading = false

					if (error == null && response is TL_account_authorizations) {
						sessions.clear()
						passwordSessions.clear()

						for (authorization in response.authorizations) {
							if (authorization.flags and 1 != 0) {
								currentSession = authorization
							}
							else if (authorization.password_pending) {
								passwordSessions.add(authorization)
							}
							else {
								sessions.add(authorization)
							}
						}

						ttlDays = response.authorization_ttl_days

						updateRows()
					}

					if (rowCount == 0) {
						emptyView?.showTextView()
					}

					listAdapter?.notifyDataSetChanged()

					if (repeatLoad > 0) {
						repeatLoad--

						if (repeatLoad > 0) {
							AndroidUtilities.runOnUIThread({
								loadSessions(silent)
							}, 2500)
						}
					}
				}
			}

			ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid)
		}
		else {
			val req = TL_account_getWebAuthorizations()

			val reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					loading = false

					if (error == null && response is TL_account_webAuthorizations) {
						sessions.clear()

						MessagesController.getInstance(currentAccount).putUsers(response.users, false)

						sessions.addAll(response.authorizations)

						updateRows()
					}

					if (rowCount == 0) {
						emptyView?.showTextView()
					}

					listAdapter?.notifyDataSetChanged()

					if (repeatLoad > 0) {
						repeatLoad--

						if (repeatLoad > 0) {
							AndroidUtilities.runOnUIThread({
								loadSessions(silent)
							}, 2500)
						}
					}
				}
			}

			ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid)
		}
	}

	private fun updateRows() {
		rowCount = 0
		currentSessionSectionRow = -1
		currentSessionRow = -1
		terminateAllSessionsRow = -1
		terminateAllSessionsDetailRow = -1
		passwordSessionsSectionRow = -1
		passwordSessionsStartRow = -1
		passwordSessionsEndRow = -1
		passwordSessionsDetailRow = -1
		otherSessionsSectionRow = -1
		otherSessionsStartRow = -1
		otherSessionsEndRow = -1
		otherSessionsTerminateDetail = -1
		noOtherSessionsRow = -1
		qrCodeRow = -1
		qrCodeDividerRow = -1
		ttlHeaderRow = -1
		ttlRow = -1
		ttlDivideRow = -1

		if (currentType == 0 && messagesController.qrLoginCamera) {
			qrCodeRow = rowCount++
			qrCodeDividerRow = rowCount++
		}

		if (loading) {
			if (currentType == 0) {
				currentSessionSectionRow = rowCount++
				currentSessionRow = rowCount++
			}

			return
		}

		if (currentSession != null) {
			currentSessionSectionRow = rowCount++
			currentSessionRow = rowCount++
		}

		if (passwordSessions.isNotEmpty() || sessions.isNotEmpty()) {
			terminateAllSessionsRow = rowCount++
			terminateAllSessionsDetailRow = rowCount++
			noOtherSessionsRow = -1
		}
		else {
			terminateAllSessionsRow = -1
			terminateAllSessionsDetailRow = -1

			noOtherSessionsRow = if (currentType == 1 || currentSession != null) {
				rowCount++
			}
			else {
				-1
			}
		}

		if (passwordSessions.isNotEmpty()) {
			passwordSessionsSectionRow = rowCount++
			passwordSessionsStartRow = rowCount
			rowCount += passwordSessions.size
			passwordSessionsEndRow = rowCount
			passwordSessionsDetailRow = rowCount++
		}

		if (sessions.isNotEmpty()) {
			otherSessionsSectionRow = rowCount++
			otherSessionsStartRow = rowCount
			otherSessionsEndRow = rowCount + sessions.size
			rowCount += sessions.size
			otherSessionsTerminateDetail = rowCount++
		}

		if (ttlDays > 0) {
			ttlHeaderRow = rowCount++
			ttlRow = rowCount++
			ttlDivideRow = rowCount++
		}
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		init {
			setHasStableIds(true)
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val position = holder.adapterPosition
			return position == terminateAllSessionsRow || position in otherSessionsStartRow until otherSessionsEndRow || position in passwordSessionsStartRow until passwordSessionsEndRow || position == currentSessionRow || position == ttlRow
		}

		override fun getItemCount(): Int {
			return rowCount
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				VIEW_TYPE_TEXT -> {
					view = TextCell(mContext)
					view.setBackgroundResource(R.color.background)
				}

				VIEW_TYPE_INFO -> {
					view = TextInfoPrivacyCell(mContext)
				}

				VIEW_TYPE_HEADER -> {
					view = HeaderCell(mContext)
					view.setBackgroundResource(R.color.background)
				}

				VIEW_TYPE_SCAN_QR -> {
					view = ScanQRCodeView(mContext)
				}

				VIEW_TYPE_SETTINGS -> {
					view = TextSettingsCell(mContext)
					view.setBackgroundResource(R.color.background)
				}

				VIEW_TYPE_SESSION -> {
					view = SessionCell(mContext, currentType)
					view.setBackgroundResource(R.color.background)
				}

				else -> {
					view = SessionCell(mContext, currentType)
					view.setBackgroundResource(R.color.background)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			when (holder.itemViewType) {
				VIEW_TYPE_TEXT -> {
					val textCell = holder.itemView as TextCell

					if (position == terminateAllSessionsRow) {
						textCell.setColors(ResourcesCompat.getColor(textCell.resources, R.color.purple, null), ResourcesCompat.getColor(textCell.resources, R.color.purple, null))

						if (currentType == 0) {
							textCell.setTextAndIcon(textCell.context.getString(R.string.TerminateAllSessions), R.drawable.msg_block2, false)
						}
						else {
							textCell.setTextAndIcon(textCell.context.getString(R.string.TerminateAllWebSessions), R.drawable.msg_block2, false)
						}
					}
					else if (position == qrCodeRow) {
						textCell.setColors(ResourcesCompat.getColor(textCell.resources, R.color.brand, null), ResourcesCompat.getColor(textCell.resources, R.color.brand, null))
						textCell.setTextAndIcon(textCell.context.getString(R.string.AuthAnotherClient), R.drawable.msg_qrcode, sessions.isNotEmpty())
					}
				}

				VIEW_TYPE_INFO -> {
					val privacyCell = holder.itemView as TextInfoPrivacyCell
					privacyCell.setFixedSize(0)

					if (position == terminateAllSessionsDetailRow) {
						if (currentType == 0) {
							privacyCell.setText(privacyCell.context.getString(R.string.ClearOtherSessionsHelp))
						}
						else {
							privacyCell.setText(privacyCell.context.getString(R.string.ClearOtherWebSessionsHelp))
						}

						privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow)
					}
					else if (position == otherSessionsTerminateDetail) {
						if (currentType == 0) {
							privacyCell.setText("")
						}
						else {
							privacyCell.setText(privacyCell.context.getString(R.string.TerminateWebSessionInfo))
						}

						privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)
					}
					else if (position == passwordSessionsDetailRow) {
						privacyCell.setText(privacyCell.context.getString(R.string.LoginAttemptsInfo))

						if (otherSessionsTerminateDetail == -1) {
							privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow)
						}
						else {
							privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow)
						}
					}
					else if (position == qrCodeDividerRow || position == ttlDivideRow || position == noOtherSessionsRow) {
						privacyCell.background = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow)
						privacyCell.setText("")
						privacyCell.setFixedSize(12)
					}
				}

				VIEW_TYPE_HEADER -> {
					val headerCell = holder.itemView as HeaderCell

					if (position == currentSessionSectionRow) {
						headerCell.setText(headerCell.context.getString(R.string.CurrentSession))
					}
					else if (position == otherSessionsSectionRow) {
						if (currentType == 0) {
							headerCell.setText(headerCell.context.getString(R.string.OtherSessions))
						}
						else {
							headerCell.setText(headerCell.context.getString(R.string.OtherWebSessions))
						}
					}
					else if (position == passwordSessionsSectionRow) {
						headerCell.setText(headerCell.context.getString(R.string.LoginAttempts))
					}
					else if (position == ttlHeaderRow) {
						headerCell.setText(headerCell.context.getString(R.string.TerminateOldSessionHeader))
					}
				}

				VIEW_TYPE_SCAN_QR -> {
					// unused
				}

				VIEW_TYPE_SETTINGS -> {
					val textSettingsCell = holder.itemView as TextSettingsCell

					val value = when (ttlDays) {
						in 31..183 -> {
							LocaleController.formatPluralString("Months", ttlDays / 30)
						}

						365 -> {
							LocaleController.formatPluralString("Years", ttlDays / 365)
						}

						else -> {
							LocaleController.formatPluralString("Weeks", ttlDays / 7)
						}
					}

					textSettingsCell.setTextAndValue(textSettingsCell.context.getString(R.string.IfInactiveFor), value, animated = true, divider = false)
				}

				VIEW_TYPE_SESSION -> {
					val sessionCell = holder.itemView as SessionCell

					if (position == currentSessionRow) {
						if (currentSession == null) {
							sessionCell.showStub(globalFlickerLoadingView)
						}
						else {
							sessionCell.setSession(currentSession, sessions.isNotEmpty() || passwordSessions.isNotEmpty() || qrCodeRow != -1)
						}
					}
					else if (position in otherSessionsStartRow until otherSessionsEndRow) {
						sessionCell.setSession(sessions[position - otherSessionsStartRow], position != otherSessionsEndRow - 1)
					}
					else if (position in passwordSessionsStartRow until passwordSessionsEndRow) {
						sessionCell.setSession(passwordSessions[position - passwordSessionsStartRow], position != passwordSessionsEndRow - 1)
					}
				}

				else -> {
					val sessionCell = holder.itemView as SessionCell

					if (position == currentSessionRow) {
						if (currentSession == null) {
							sessionCell.showStub(globalFlickerLoadingView)
						}
						else {
							sessionCell.setSession(currentSession, sessions.isNotEmpty() || passwordSessions.isNotEmpty() || qrCodeRow != -1)
						}
					}
					else if (position in otherSessionsStartRow until otherSessionsEndRow) {
						sessionCell.setSession(sessions[position - otherSessionsStartRow], position != otherSessionsEndRow - 1)
					}
					else if (position in passwordSessionsStartRow until passwordSessionsEndRow) {
						sessionCell.setSession(passwordSessions[position - passwordSessionsStartRow], position != passwordSessionsEndRow - 1)
					}
				}
			}
		}

		override fun getItemId(position: Int): Long {
			if (position == terminateAllSessionsRow) {
				return Objects.hash(0, 0).toLong()
			}
			else if (position == terminateAllSessionsDetailRow) {
				return Objects.hash(0, 1).toLong()
			}
			else if (position == otherSessionsTerminateDetail) {
				return Objects.hash(0, 2).toLong()
			}
			else if (position == passwordSessionsDetailRow) {
				return Objects.hash(0, 3).toLong()
			}
			else if (position == qrCodeDividerRow) {
				return Objects.hash(0, 4).toLong()
			}
			else if (position == ttlDivideRow) {
				return Objects.hash(0, 5).toLong()
			}
			else if (position == noOtherSessionsRow) {
				return Objects.hash(0, 6).toLong()
			}
			else if (position == currentSessionSectionRow) {
				return Objects.hash(0, 7).toLong()
			}
			else if (position == otherSessionsSectionRow) {
				return Objects.hash(0, 8).toLong()
			}
			else if (position == passwordSessionsSectionRow) {
				return Objects.hash(0, 9).toLong()
			}
			else if (position == ttlHeaderRow) {
				return Objects.hash(0, 10).toLong()
			}
			else if (position == currentSessionRow) {
				return Objects.hash(0, 11).toLong()
			}
			else if (position in otherSessionsStartRow until otherSessionsEndRow) {
				val session = sessions[position - otherSessionsStartRow]

				if (session is TL_authorization) {
					return Objects.hash(1, session.hash).toLong()
				}
				else if (session is TL_webAuthorization) {
					return Objects.hash(1, session.hash).toLong()
				}
			}
			else if (position in passwordSessionsStartRow until passwordSessionsEndRow) {
				val session = passwordSessions[position - passwordSessionsStartRow]

				if (session is TL_authorization) {
					return Objects.hash(2, session.hash).toLong()
				}
				else if (session is TL_webAuthorization) {
					return Objects.hash(2, session.hash).toLong()
				}
			}
			else if (position == qrCodeRow) {
				return Objects.hash(0, 12).toLong()
			}
			else if (position == ttlRow) {
				return Objects.hash(0, 13).toLong()
			}

			return Objects.hash(0, -1).toLong()
		}

		override fun getItemViewType(position: Int): Int {
			return when (position) {
				terminateAllSessionsRow -> {
					VIEW_TYPE_TEXT
				}

				terminateAllSessionsDetailRow, otherSessionsTerminateDetail, passwordSessionsDetailRow, qrCodeDividerRow, ttlDivideRow, noOtherSessionsRow -> {
					VIEW_TYPE_INFO
				}

				currentSessionSectionRow, otherSessionsSectionRow, passwordSessionsSectionRow, ttlHeaderRow -> {
					VIEW_TYPE_HEADER
				}

				currentSessionRow, in otherSessionsStartRow until otherSessionsEndRow, in passwordSessionsStartRow until passwordSessionsEndRow -> {
					VIEW_TYPE_SESSION
				}

				qrCodeRow -> {
					VIEW_TYPE_SCAN_QR
				}

				ttlRow -> {
					VIEW_TYPE_SETTINGS
				}

				else -> {
					VIEW_TYPE_TEXT
				}
			}
		}
	}

	private inner class ScanQRCodeView(context: Context) : FrameLayout(context), NotificationCenterDelegate {
		var imageView: BackupImageView
		var textView: TextView

		init {
			imageView = BackupImageView(context)

			addView(imageView, LayoutHelper.createFrame(120, 120f, Gravity.CENTER_HORIZONTAL, 0f, 16f, 0f, 0f))

			imageView.setOnClickListener {
				val lottieAnimation = imageView.imageReceiver.lottieAnimation ?: return@setOnClickListener

				if (!lottieAnimation.isRunning) {
					lottieAnimation.setCurrentFrame(0, false)
					lottieAnimation.restart()
				}
			}

			val colors = IntArray(8)
			colors[0] = 0x333333
			colors[1] = ResourcesCompat.getColor(resources, R.color.text, null)
			colors[2] = 0xffffff
			colors[3] = ResourcesCompat.getColor(resources, R.color.background, null)
			colors[4] = 0x50a7ea
			colors[5] = ResourcesCompat.getColor(resources, R.color.brand, null)
			colors[6] = 0x212020
			colors[7] = ResourcesCompat.getColor(resources, R.color.background, null)

			textView = LinksTextView(context)

			addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), 0, 36f, 152f, 36f, 0f))

			textView.gravity = Gravity.CENTER_HORIZONTAL
			textView.setTextColor(ResourcesCompat.getColor(resources, R.color.text, null))
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			textView.setLinkTextColor(ResourcesCompat.getColor(resources, R.color.brand, null))
			textView.highlightColor = ResourcesCompat.getColor(resources, R.color.darker_brand, null)

			setBackgroundColor(ResourcesCompat.getColor(resources, R.color.background, null))

			var text = context.getString(R.string.AuthAnotherClientInfo4)
			val spanned = SpannableStringBuilder(text)
			var index1 = text.indexOf('*')
			var index2 = text.indexOf('*', index1 + 1)

			if (index1 != -1 && index2 != -1 && index1 != index2) {
				textView.movementMethod = AndroidUtilities.LinkMovementMethodMy()

				spanned.replace(index2, index2 + 1, "")
				spanned.replace(index1, index1 + 1, "")
				spanned.setSpan(URLSpanNoUnderline(context.getString(R.string.AuthAnotherClientDownloadClientUrl)), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}

			text = spanned.toString()
			index1 = text.indexOf('*')
			index2 = text.indexOf('*', index1 + 1)

			if (index1 != -1 && index2 != -1 && index1 != index2) {
				textView.movementMethod = AndroidUtilities.LinkMovementMethodMy()
				spanned.replace(index2, index2 + 1, "")
				spanned.replace(index1, index1 + 1, "")
				spanned.setSpan(URLSpanNoUnderline(context.getString(R.string.AuthAnotherWebClientUrl)), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}

			textView.text = spanned

			val buttonTextView = TextView(context)
			buttonTextView.setPadding(AndroidUtilities.dp(34f), 0, AndroidUtilities.dp(34f), 0)
			buttonTextView.gravity = Gravity.CENTER
			buttonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			buttonTextView.typeface = Theme.TYPEFACE_BOLD

			val spannableStringBuilder = SpannableStringBuilder()
			spannableStringBuilder.append(".  ").append(context.getString(R.string.LinkDesktopDevice))
			spannableStringBuilder.setSpan(ColoredImageSpan(ContextCompat.getDrawable(getContext(), R.drawable.msg_mini_qr)), 0, 1, 0)

			buttonTextView.text = spannableStringBuilder
			buttonTextView.setTextColor(ResourcesCompat.getColor(resources, R.color.brand, null))
			buttonTextView.background = Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6f), ResourcesCompat.getColor(resources, R.color.brand, null), ResourcesCompat.getColor(resources, R.color.darker_brand, null))

			buttonTextView.setOnClickListener {
				val parentActivity = parentActivity ?: return@setOnClickListener

				if (parentActivity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
					parentActivity.requestPermissions(arrayOf(Manifest.permission.CAMERA), ActionIntroActivity.CAMERA_PERMISSION_REQUEST_CODE)
					return@setOnClickListener
				}

				openCameraScanActivity()
			}

			addView(buttonTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, Gravity.BOTTOM, 16f, 15f, 16f, 16f))

			setSticker()
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(276f), MeasureSpec.EXACTLY))
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			setSticker()
			NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad)
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()
			NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad)
		}

		override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
			if (id == NotificationCenter.diceStickersDidLoad) {
				val name = args[0] as String

				if (AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME == name) {
					setSticker()
				}
			}
		}

		private fun setSticker() {
			val imageFilter: String?
			var document: TLRPC.Document? = null
			var set: TL_messages_stickerSet?

			set = MediaDataController.getInstance(currentAccount).getStickerSetByName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME)

			if (set == null) {
				set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME)
			}

			if (set != null && set.documents.size > 6) {
				document = set.documents[6]
			}

			imageFilter = "130_130"

			var svgThumb: SvgDrawable? = null

			if (document != null) {
				svgThumb = DocumentObject.getSvgThumb(document.thumbs, ResourcesCompat.getColor(context.resources, R.color.light_background, null), 0.2f)
			}

			svgThumb?.overrideWidthAndHeight(512, 512)

			if (document != null) {
				val imageLocation = ImageLocation.getForDocument(document)
				imageView.setImage(imageLocation, imageFilter, "tgs", svgThumb, set)
				imageView.imageReceiver.setAutoRepeat(2)
			}
			else {
				MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME, false, set == null)
			}
		}
	}

	private fun openCameraScanActivity() {
		CameraScanActivity.showAsSheet(this@SessionsActivity, false, CameraScanActivity.TYPE_QR_LOGIN, object : CameraScanActivityDelegate {
			private var response: TLObject? = null
			private var error: TL_error? = null

			override fun didFindQr(link: String) {
				if (response is TL_authorization) {
					val authorization = response as TL_authorization

					if ((response as TL_authorization).password_pending) {
						passwordSessions.add(0, authorization)
						repeatLoad = 4
						loadSessions(false)
					}
					else {
						sessions.add(0, authorization)
					}

					updateRows()

					listAdapter?.notifyDataSetChanged()
					undoView?.showWithAction(0, UndoView.ACTION_QR_SESSION_ACCEPTED, response)
				}
				else if (error != null) {
					AndroidUtilities.runOnUIThread {
						val context = this@SessionsActivity.context ?: return@runOnUIThread

						val text = if (error?.text == "AUTH_TOKEN_EXCEPTION") {
							context.getString(R.string.AccountAlreadyLoggedIn)
						}
						else {
							"${context.getString(R.string.ErrorOccurred)}\n${error?.text}"
						}

						AlertsCreator.showSimpleAlert(this@SessionsActivity, context.getString(R.string.AuthAnotherClient), text)
					}
				}
			}

			override fun processQr(link: String, onLoadEnd: Runnable): Boolean {
				response = null
				error = null

				AndroidUtilities.runOnUIThread({
					try {
						var code = link.substring("elloapp://login?token=".length)
						code = code.replace("/".toRegex(), "_")
						code = code.replace("\\+".toRegex(), "-")

						val token = Base64.decode(code, Base64.URL_SAFE)

						val req = TL_auth_acceptLoginToken()
						req.token = token

						connectionsManager.sendRequest(req) { response, error ->
							AndroidUtilities.runOnUIThread {
								this.response = response
								this.error = error
								onLoadEnd.run()
							}
						}
					}
					catch (e: Exception) {
						FileLog.e("Failed to pass qr code auth", e)

						AndroidUtilities.runOnUIThread inner@{
							val context = this@SessionsActivity.context ?: return@inner
							AlertsCreator.showSimpleAlert(this@SessionsActivity, context.getString(R.string.AuthAnotherClient), context.getString(R.string.ErrorOccurred))
						}

						onLoadEnd.run()
					}
				}, 750)

				return true
			}
		})
	}

	override fun onRequestPermissionsResultFragment(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		val parentActivity = parentActivity ?: return

		if (requestCode == ActionIntroActivity.CAMERA_PERMISSION_REQUEST_CODE) {
			if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				openCameraScanActivity()
			}
			else {
				AlertDialog.Builder(parentActivity).setMessage(AndroidUtilities.replaceTags(parentActivity.getString(R.string.QRCodePermissionNoCameraWithHint))).setPositiveButton(parentActivity.getString(R.string.PermissionOpenSettings)) { _, _ ->
					try {
						val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
						intent.data = Uri.parse("package:" + ApplicationLoader.applicationContext.packageName)
						parentActivity.startActivity(intent)
					}
					catch (e: Exception) {
						FileLog.e(e)
					}
				}.setNegativeButton(parentActivity.getString(R.string.ContactsPermissionAlertNotNow), null).setTopAnimation(R.raw.permission_request_camera, 72, false, ResourcesCompat.getColor(parentActivity.resources, R.color.brand, null)).show()
			}
		}
	}

	companion object {
		private const val VIEW_TYPE_TEXT = 0
		private const val VIEW_TYPE_INFO = 1
		private const val VIEW_TYPE_HEADER = 2
		private const val VIEW_TYPE_SESSION = 4
		private const val VIEW_TYPE_SCAN_QR = 5
		private const val VIEW_TYPE_SETTINGS = 6
		const val ALL_SESSIONS = 0
		const val WEB_SESSIONS = 1
	}
}
