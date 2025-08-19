/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.provider.MediaStore
import android.text.TextUtils
import android.util.LongSparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.util.isEmpty
import androidx.core.util.size
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MediaController.AudioEntry
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.userId
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.SharedAudioCell
import org.telegram.ui.Components.ChatAttachAlert.AttachAlertLayout
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import java.io.File
import java.util.Locale

@SuppressLint("NotifyDataSetChanged")
class ChatAttachAlertAudioLayout(alert: ChatAttachAlert, context: Context) : AttachAlertLayout(alert, context), NotificationCenterDelegate {
	private val frameLayout = FrameLayout(context)

	private val searchField = object : SearchField(context, false) {
		override fun onTextChange(text: String) {
			if (text.isEmpty()) {
				if (listView.adapter !== listAdapter) {
					listView.adapter = listAdapter
					listAdapter.notifyDataSetChanged()
				}
			}
			searchAdapter.search(text)
		}

		override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
			parentAlert.makeFocusable(searchEditText, true)
			return super.onInterceptTouchEvent(ev)
		}

		override fun processTouchEvent(event: MotionEvent) {
			val e = MotionEvent.obtain(event)
			e.setLocation(e.rawX, e.rawY - parentAlert.sheetContainer.translationY - AndroidUtilities.dp(58f))
			listView.dispatchTouchEvent(e)
			e.recycle()
		}

		override fun onFieldTouchUp(editText: EditTextBoldCursor) {
			parentAlert.makeFocusable(editText, true)
		}
	}

	private val shadow = View(context)
	private val listAdapter = ListAdapter(context)
	private val searchAdapter = SearchAdapter(context)
	private val layoutManager: LinearLayoutManager
	private val progressView = EmptyTextProgressView(context, null)
	private val emptyView = LinearLayout(context)
	private val emptyImageView = ImageView(context)
	private val emptyTitleTextView = TextView(context)
	private val emptySubtitleTextView = TextView(context)

	private val listView = object : RecyclerListView(context) {
		override fun allowSelectChildAtPosition(x: Float, y: Float): Boolean {
			return y >= parentAlert.scrollOffsetY[0] + AndroidUtilities.dp(30f) + if (!parentAlert.inBubbleMode) AndroidUtilities.statusBarHeight else 0
		}
	}

	private val selectedAudiosOrder = ArrayList<AudioEntry>()
	private val selectedAudios = LongSparseArray<AudioEntry>()
	private var shadowAnimation: AnimatorSet? = null
	private var currentEmptyView: View? = null
	private var maxSelectedFiles = -1
	private var sendPressed = false
	private var loadingAudio = false
	private var ignoreLayout = false
	private var audioEntries = ArrayList<AudioEntry>()
	private var delegate: AudioSelectDelegate? = null
	private var playingAudio: MessageObject? = null
	private var currentPanTranslationProgress = 0f

	init {
		NotificationCenter.getInstance(parentAlert.currentAccount).let {
			it.addObserver(this, NotificationCenter.messagePlayingDidReset)
			it.addObserver(this, NotificationCenter.messagePlayingDidStart)
			it.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
		}

		loadAudio()

		frameLayout.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.background, null))

		searchField.setHint(context.getString(R.string.SearchMusic))

		frameLayout.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))

		progressView.showProgress()

		addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		emptyView.orientation = LinearLayout.VERTICAL
		emptyView.gravity = Gravity.CENTER
		emptyView.visibility = GONE

		addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		emptyView.setOnTouchListener { _, _ -> true }

		emptyImageView.setImageResource(R.drawable.music_empty)
		emptyImageView.colorFilter = PorterDuffColorFilter(ResourcesCompat.getColor(resources, R.color.dark_gray, null), PorterDuff.Mode.SRC_IN)
		emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

		emptyTitleTextView.setTextColor(ResourcesCompat.getColor(resources, R.color.dark_gray, null))
		emptyTitleTextView.gravity = Gravity.CENTER
		emptyTitleTextView.typeface = Theme.TYPEFACE_BOLD
		emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
		emptyTitleTextView.setPadding(AndroidUtilities.dp(40f), 0, AndroidUtilities.dp(40f), 0)

		emptyView.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0))

		emptySubtitleTextView.setTextColor(ResourcesCompat.getColor(resources, R.color.dark_gray, null))
		emptySubtitleTextView.gravity = Gravity.CENTER
		emptySubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
		emptySubtitleTextView.setPadding(AndroidUtilities.dp(40f), 0, AndroidUtilities.dp(40f), 0)

		emptyView.addView(emptySubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0))

		listView.clipToPadding = false

		listView.layoutManager = object : FillLastLinearLayoutManager(getContext(), VERTICAL, false, AndroidUtilities.dp(9f), listView) {
			override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
				val linearSmoothScroller: LinearSmoothScroller = object : LinearSmoothScroller(recyclerView.context) {
					override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
						var dy = super.calculateDyToMakeVisible(view, snapPreference)
						dy -= listView.paddingTop - AndroidUtilities.dp(7f)
						return dy
					}

					override fun calculateTimeForDeceleration(dx: Int): Int {
						return super.calculateTimeForDeceleration(dx) * 2
					}
				}

				linearSmoothScroller.targetPosition = position
				startSmoothScroll(linearSmoothScroller)
			}
		}.also { layoutManager = it }

		listView.isHorizontalScrollBarEnabled = false
		listView.isVerticalScrollBarEnabled = false

		addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), Gravity.TOP or Gravity.LEFT, 0f, 0f, 0f, 0f))

		listView.adapter = listAdapter
		listView.setGlowColor(ResourcesCompat.getColor(resources, R.color.light_background, null))

		listView.setOnItemClickListener { view, _ ->
			onItemClick(view)
		}

		listView.setOnItemLongClickListener { view, _ ->
			onItemClick(view)
			true
		}

		listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				parentAlert.updateLayout(this@ChatAttachAlertAudioLayout, true, dy)
				updateEmptyViewPosition()
			}
		})

		val frameLayoutParams = LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP or Gravity.LEFT)
		frameLayoutParams.topMargin = AndroidUtilities.dp(58f)

		shadow.setBackgroundColor(ResourcesCompat.getColor(resources, R.color.shadow, null))
		shadow.alpha = 0.0f
		shadow.tag = 1

		addView(shadow, frameLayoutParams)

		addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT or Gravity.TOP))

		updateEmptyView()
	}

	override fun onDestroy() {
		onHide()

		NotificationCenter.getInstance(parentAlert.currentAccount).let {
			it.removeObserver(this, NotificationCenter.messagePlayingDidReset)
			it.removeObserver(this, NotificationCenter.messagePlayingDidStart)
			it.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
		}
	}

	override fun onHide() {
		if (playingAudio != null && MediaController.getInstance().isPlayingMessage(playingAudio)) {
			MediaController.getInstance().cleanupPlayer(true, true)
		}

		playingAudio = null
	}

	private fun updateEmptyViewPosition() {
		if (currentEmptyView?.visibility != VISIBLE) {
			return
		}

		val child = listView.getChildAt(0) ?: return

		currentEmptyView?.translationY = ((currentEmptyView?.measuredHeight ?: 0) - measuredHeight + child.top) / 2 - currentPanTranslationProgress / 2
	}

	private fun updateEmptyView() {
		if (loadingAudio) {
			currentEmptyView = progressView
			emptyView.visibility = GONE
		}
		else {
			if (listView.adapter === searchAdapter) {
				emptyTitleTextView.text = context.getString(R.string.NoAudioFound)
			}
			else {
				emptyTitleTextView.text = context.getString(R.string.NoAudioFiles)
				emptySubtitleTextView.text = context.getString(R.string.NoAudioFilesInfo)
			}

			currentEmptyView = emptyView
			progressView.visibility = GONE
		}

		val visible = if (listView.adapter === searchAdapter) {
			searchAdapter.searchResult.isEmpty()
		}
		else {
			audioEntries.isEmpty()
		}

		currentEmptyView?.visibility = if (visible) VISIBLE else GONE

		updateEmptyViewPosition()
	}

	fun setMaxSelectedFiles(value: Int) {
		maxSelectedFiles = value
	}

	override fun scrollToTop() {
		listView.smoothScrollToPosition(0)
	}

	override var currentItemTop: Int
		get() {
			if (listView.childCount <= 0) {
				return Int.MAX_VALUE
			}

			val child = listView.getChildAt(0)
			val holder = listView.findContainingViewHolder(child) as RecyclerListView.Holder?
			val top = child.top - AndroidUtilities.dp(8f)
			var newOffset = if (top > 0 && holder != null && holder.adapterPosition == 0) top else 0

			if (top >= 0 && holder != null && holder.adapterPosition == 0) {
				newOffset = top
				runShadowAnimation(false)
			}
			else {
				runShadowAnimation(true)
			}

			frameLayout.translationY = newOffset.toFloat()

			return newOffset + AndroidUtilities.dp(12f)
		}
		set(currentItemTop) {
			super.currentItemTop = currentItemTop
		}

	override val firstOffset: Int
		get() = listTopPadding + AndroidUtilities.dp(4f)

	override fun setTranslationY(translationY: Float) {
		super.setTranslationY(translationY)
		parentAlert.sheetContainer.invalidate()
	}

	override fun onDismiss(): Boolean {
		if (playingAudio != null && MediaController.getInstance().isPlayingMessage(playingAudio)) {
			MediaController.getInstance().cleanupPlayer(true, true)
		}

		return super.onDismiss()
	}

	override val listTopPadding: Int
		get() = listView.paddingTop

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		updateEmptyViewPosition()
	}

	override fun onPreMeasure(availableWidth: Int, availableHeight: Int) {
		val padding: Int

		if (parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20f)) {
			padding = AndroidUtilities.dp(8f)
			parentAlert.setAllowNestedScroll(false)
		}
		else {
			padding = if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
				(availableHeight / 3.5f).toInt()
			}
			else {
				availableHeight / 5 * 2
			}

			parentAlert.setAllowNestedScroll(true)
		}

		if (listView.paddingTop != padding) {
			ignoreLayout = true
			listView.setPadding(0, padding, 0, AndroidUtilities.dp(48f))
			ignoreLayout = false
		}
	}

	override fun onShow(previousLayout: AttachAlertLayout?) {
		layoutManager.scrollToPositionWithOffset(0, 0)
		listAdapter.notifyDataSetChanged()
	}

	override fun onHidden() {
		selectedAudios.clear()
		selectedAudiosOrder.clear()
	}

	override fun requestLayout() {
		if (ignoreLayout) {
			return
		}

		super.requestLayout()
	}

	private fun runShadowAnimation(show: Boolean) {
		if (show && shadow.tag != null || !show && shadow.tag == null) {
			shadow.tag = if (show) null else 1

			if (show) {
				shadow.visibility = VISIBLE
			}
			shadowAnimation?.cancel()

			shadowAnimation = AnimatorSet()
			shadowAnimation?.playTogether(ObjectAnimator.ofFloat(shadow, ALPHA, if (show) 1.0f else 0.0f))
			shadowAnimation?.duration = 150

			shadowAnimation?.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (shadowAnimation != null && shadowAnimation == animation) {
						if (!show) {
							shadow.visibility = INVISIBLE
						}

						shadowAnimation = null
					}
				}

				override fun onAnimationCancel(animation: Animator) {
					if (shadowAnimation != null && shadowAnimation == animation) {
						shadowAnimation = null
					}
				}
			})

			shadowAnimation?.start()
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.messagePlayingDidReset, NotificationCenter.messagePlayingPlayStateChanged -> {
				val count = listView.childCount

				for (a in 0 until count) {
					val view = listView.getChildAt(a)

					if (view is SharedAudioCell) {
						val messageObject = view.message

						if (messageObject != null) {
							view.updateButtonState(ifSame = false, animated = true)
						}
					}
				}
			}

			NotificationCenter.messagePlayingDidStart -> {
				val messageObject = args[0] as MessageObject

				if (messageObject.eventId != 0L) {
					return
				}

				val count = listView.childCount

				for (a in 0 until count) {
					val view = listView.getChildAt(a)

					if (view is SharedAudioCell) {
						val messageObject1 = view.message

						if (messageObject1 != null) {
							view.updateButtonState(ifSame = false, animated = true)
						}
					}
				}
			}
		}
	}

	private fun showErrorBox(error: String) {
		AlertDialog.Builder(context).setTitle(context.getString(R.string.AppName)).setMessage(error).setPositiveButton(context.getString(R.string.OK), null).show()
	}

	private fun onItemClick(view: View) {
		if (view !is SharedAudioCell) {
			return
		}

		val audioEntry = view.tag as AudioEntry

		val add = if (selectedAudios.indexOfKey(audioEntry.id) >= 0) {
			selectedAudios.remove(audioEntry.id)
			selectedAudiosOrder.remove(audioEntry)
			view.setChecked(checked = false, animated = true)
			false
		}
		else {
			if (maxSelectedFiles >= 0 && selectedAudios.size >= maxSelectedFiles) {
				showErrorBox(LocaleController.formatString("PassportUploadMaxReached", R.string.PassportUploadMaxReached, LocaleController.formatPluralString("Files", maxSelectedFiles)))
				return
			}

			selectedAudios.put(audioEntry.id, audioEntry)
			selectedAudiosOrder.add(audioEntry)
			view.setChecked(checked = true, animated = true)
			true
		}

		parentAlert.updateCountButton(if (add) 1 else 2)
	}

	override val selectedItemsCount: Int
		get() = selectedAudios.size

	override fun sendSelectedItems(notify: Boolean, scheduleDate: Int) {
		if (selectedAudios.isEmpty() || delegate == null || sendPressed) {
			return
		}

		sendPressed = true

		val audios = ArrayList<MessageObject>()

		for (a in selectedAudiosOrder.indices) {
			audios.add(selectedAudiosOrder[a].messageObject)
		}

		delegate?.didSelectAudio(audios, parentAlert.commentTextView.text, notify, scheduleDate)
	}

	fun setDelegate(audioSelectDelegate: AudioSelectDelegate?) {
		delegate = audioSelectDelegate
	}

	private fun loadAudio() {
		loadingAudio = true

		Utilities.globalQueue.postRunnable {
			val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM)
			val newAudioEntries = ArrayList<AudioEntry>()

			try {
				ApplicationLoader.applicationContext.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE)?.use { cursor ->
					var id = -2000000000

					while (cursor.moveToNext()) {
						val audioEntry = AudioEntry()
						audioEntry.id = cursor.getInt(0).toLong()
						audioEntry.author = cursor.getString(1)
						audioEntry.title = cursor.getString(2)
						audioEntry.path = cursor.getString(3)
						audioEntry.duration = (cursor.getLong(4) / 1000).toInt()
						audioEntry.genre = cursor.getString(5)

						val file = File(audioEntry.path)

						val message = TLRPC.TLMessage()
						message.out = true
						message.id = id
						message.peerId = TLRPC.TLPeerUser()
						message.fromId = TLRPC.TLPeerUser()
						message.fromId?.userId = UserConfig.getInstance(parentAlert.currentAccount).getClientUserId()
						message.peerId?.userId = message.fromId!!.userId
						message.date = (System.currentTimeMillis() / 1000).toInt()
						message.message = ""
						message.attachPath = audioEntry.path

						val media = TLRPC.TLMessageMediaDocument()
						val document = TLRPC.TLDocument()

						message.media = media.also {
							it.flags = it.flags or 3
							it.document = document
						}

						message.flags = message.flags or (TLRPC.MESSAGE_FLAG_HAS_MEDIA or TLRPC.MESSAGE_FLAG_HAS_FROM_ID)

						val ext = FileLoader.getFileExtension(file)

						document.id = 0
						document.accessHash = 0
						document.fileReference = ByteArray(0)
						document.date = message.date
						document.mimeType = "audio/" + ext.ifEmpty { "mp3" }
						document.size = file.length().toInt().toLong()
						document.dcId = 0

						val attributeAudio = TLRPC.TLDocumentAttributeAudio()
						attributeAudio.duration = audioEntry.duration
						attributeAudio.title = audioEntry.title
						attributeAudio.performer = audioEntry.author
						attributeAudio.flags = attributeAudio.flags or 3

						document.attributes.add(attributeAudio)

						val fileName = TLRPC.TLDocumentAttributeFilename()
						fileName.fileName = file.name

						document.attributes.add(fileName)

						audioEntry.messageObject = MessageObject(parentAlert.currentAccount, message, generateLayout = false, checkMediaExists = true)

						newAudioEntries.add(audioEntry)

						id--
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			AndroidUtilities.runOnUIThread {
				loadingAudio = false
				audioEntries = newAudioEntries
				listAdapter.notifyDataSetChanged()
			}
		}
	}

	override fun onContainerTranslationUpdated(currentPanTranslationY: Float) {
		currentPanTranslationProgress = currentPanTranslationY
		super.onContainerTranslationUpdated(currentPanTranslationY)
		updateEmptyViewPosition()
	}

	fun interface AudioSelectDelegate {
		fun didSelectAudio(audios: ArrayList<MessageObject>, caption: CharSequence?, notify: Boolean, scheduleDate: Int)
	}

	private inner class ListAdapter(private val mContext: Context) : SelectionAdapter() {
		override fun getItemCount(): Int {
			return 1 + audioEntries.size + if (audioEntries.isEmpty()) 0 else 1
		}

		override fun getItemId(i: Int): Long {
			return i.toLong()
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType == 0
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					val sharedAudioCell = object : SharedAudioCell(mContext) {
						public override fun needPlayMessage(messageObject: MessageObject): Boolean {
							playingAudio = messageObject
							val arrayList = ArrayList<MessageObject>()
							arrayList.add(messageObject)
							return MediaController.getInstance().setPlaylist(arrayList, messageObject, 0)
						}
					}

					sharedAudioCell.setCheckForButtonPress(true)

					view = sharedAudioCell
				}

				1 -> {
					view = View(mContext)
					view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56f))
				}

				else -> {
					view = View(mContext)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			@Suppress("NAME_SHADOWING") var position = position

			if (holder.itemViewType == 0) {
				position--

				val audioEntry = audioEntries[position]

				val audioCell = holder.itemView as SharedAudioCell
				audioCell.tag = audioEntry
				audioCell.setMessageObject(audioEntry.messageObject, position != audioEntries.size - 1)
				audioCell.setChecked(selectedAudios.indexOfKey(audioEntry.id) >= 0, false)
			}
		}

		override fun getItemViewType(i: Int): Int {
			if (i == itemCount - 1) {
				return 2
			}

			if (i == 0) {
				return 1
			}

			return 0
		}

		override fun notifyDataSetChanged() {
			super.notifyDataSetChanged()
			updateEmptyView()
		}
	}

	inner class SearchAdapter(private val mContext: Context) : SelectionAdapter() {
		var searchResult = ArrayList<AudioEntry>()
		private var searchRunnable: Runnable? = null
		private var lastSearchId = 0

		fun search(query: String) {
			if (searchRunnable != null) {
				AndroidUtilities.cancelRunOnUIThread(searchRunnable)
				searchRunnable = null
			}

			if (TextUtils.isEmpty(query)) {
				if (searchResult.isNotEmpty()) {
					searchResult.clear()
				}

				if (listView.adapter !== listAdapter) {
					listView.adapter = listAdapter
				}

				notifyDataSetChanged()
			}
			else {
				val searchId = ++lastSearchId

				AndroidUtilities.runOnUIThread(Runnable {
					val copy = ArrayList(audioEntries)

					Utilities.searchQueue.postRunnable {
						val search1 = query.trim { it <= ' ' }.lowercase(Locale.getDefault())

						if (search1.isEmpty()) {
							updateSearchResults(ArrayList(), query, lastSearchId)
							return@postRunnable
						}

						var search2 = LocaleController.getInstance().getTranslitString(search1)

						if (search1 == search2 || search2.isNullOrEmpty()) {
							search2 = null
						}

						val search = arrayOfNulls<String>(1 + if (search2 != null) 1 else 0)

						search[0] = search1

						if (search2 != null) {
							search[1] = search2
						}

						val resultArray = ArrayList<AudioEntry>()

						for (a in copy.indices) {
							val entry = copy[a]

							for (b in search.indices) {
								val q = search[b] ?: continue
								var ok = false

								if (entry.author != null) {
									ok = entry.author.lowercase(Locale.getDefault()).contains(q)
								}

								if (!ok && entry.title != null) {
									ok = entry.title.lowercase(Locale.getDefault()).contains(q)
								}

								if (ok) {
									resultArray.add(entry)
									break
								}
							}
						}
						updateSearchResults(resultArray, query, searchId)
					}
				}.also { searchRunnable = it }, 300)
			}
		}

		private fun updateSearchResults(result: ArrayList<AudioEntry>, query: String, searchId: Int) {
			AndroidUtilities.runOnUIThread {
				if (searchId != lastSearchId) {
					return@runOnUIThread
				}

				if (searchId != -1 && listView.adapter !== searchAdapter) {
					listView.adapter = searchAdapter
				}

				if (listView.adapter === searchAdapter) {
					emptySubtitleTextView.text = AndroidUtilities.replaceTags(LocaleController.formatString("NoAudioFoundInfo", R.string.NoAudioFoundInfo, query))
				}

				searchResult = result

				notifyDataSetChanged()
			}
		}

		override fun notifyDataSetChanged() {
			super.notifyDataSetChanged()
			updateEmptyView()
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			return holder.itemViewType == 0
		}

		override fun getItemCount(): Int {
			return 1 + searchResult.size + if (searchResult.isEmpty()) 0 else 1
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					val sharedAudioCell = object : SharedAudioCell(mContext) {
						public override fun needPlayMessage(messageObject: MessageObject): Boolean {
							playingAudio = messageObject
							val arrayList = ArrayList<MessageObject>()
							arrayList.add(messageObject)
							return MediaController.getInstance().setPlaylist(arrayList, messageObject, 0)
						}
					}

					sharedAudioCell.setCheckForButtonPress(true)
					view = sharedAudioCell
				}

				1 -> {
					view = View(mContext)
					view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56f))
				}

				else -> {
					view = View(mContext)
				}
			}

			return RecyclerListView.Holder(view)
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			@Suppress("NAME_SHADOWING") var position = position

			if (holder.itemViewType == 0) {
				position--

				val audioEntry = searchResult[position]

				val audioCell = holder.itemView as SharedAudioCell
				audioCell.tag = audioEntry
				audioCell.setMessageObject(audioEntry.messageObject, position != searchResult.size - 1)
				audioCell.setChecked(selectedAudios.indexOfKey(audioEntry.id) >= 0, false)
			}
		}

		override fun getItemViewType(i: Int): Int {
			if (i == itemCount - 1) {
				return 2
			}

			if (i == 0) {
				return 1
			}

			return 0
		}
	}
}
