/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components.Reactions

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import android.util.LongSparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.DocumentObject.getSvgThumb
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.UserObject
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.gone
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC.InputStickerSet
import org.telegram.tgnet.TLRPC.MessagePeerReaction
import org.telegram.tgnet.tlrpc.Reaction
import org.telegram.tgnet.tlrpc.ReactionCount
import org.telegram.tgnet.TLRPC.TL_messagePeerReaction
import org.telegram.tgnet.TLRPC.TL_messages_getMessageReactionsList
import org.telegram.tgnet.TLRPC.TL_messages_messageReactionsList
import org.telegram.tgnet.TLRPC.TL_peerUser
import org.telegram.tgnet.tlrpc.TL_reactionCustomEmoji
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BackupImageView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.FlickerLoadingView
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.MessageContainsEmojiButton
import org.telegram.ui.Components.Reactions.VisibleReaction.Companion.fromTLReaction
import org.telegram.ui.Components.RecyclerListView
import kotlin.math.min

class ReactedUsersListView(context: Context, private val currentAccount: Int, private val message: MessageObject, reactionCount: ReactionCount?, addPadding: Boolean) : FrameLayout(context) {
	private var predictiveCount: Int
	private val filter: Reaction?
	var listView: RecyclerListView
	private var adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = null
	private val loadingView: FlickerLoadingView
	private val userReactions: MutableList<MessagePeerReaction> = ArrayList()
	private val peerReactionMap = LongSparseArray<ArrayList<MessagePeerReaction>>()
	private var offset: String? = null
	var isLoading = false

	@JvmField
	var isLoaded = false

	var canLoadMore = true
	private var onlySeenNow = false
	private var onHeightChangedListener: OnHeightChangedListener? = null
	private var onProfileSelectedListener: OnProfileSelectedListener? = null
	private var onCustomEmojiSelectedListener: OnCustomEmojiSelectedListener? = null

	var customReactionsEmoji = ArrayList<VisibleReaction>()
	var customEmojiStickerSets = ArrayList<InputStickerSet>()
	var messageContainsEmojiButton: MessageContainsEmojiButton? = null

	init {
		filter = reactionCount?.reaction

		predictiveCount = reactionCount?.count ?: VISIBLE_ITEMS

		listView = object : RecyclerListView(context) {
			override fun onMeasure(widthSpec: Int, heightSpec: Int) {
				messageContainsEmojiButton?.measure(widthSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightSpec), MeasureSpec.UNSPECIFIED))
				super.onMeasure(widthSpec, heightSpec)
				updateHeight()
			}
		}

		val llm = LinearLayoutManager(context)

		listView.layoutManager = llm

		if (addPadding) {
			listView.setPadding(0, 0, 0, AndroidUtilities.dp(8f))
			listView.clipToPadding = false
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			listView.verticalScrollbarThumbDrawable = ColorDrawable(context.getColor(R.color.light_background))
		}

		listView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
			override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
				val view = when (viewType) {
					USER_VIEW_TYPE -> {
						ReactedUserHolderView(context)
					}

					CUSTOM_EMOJI_VIEW_TYPE -> {
						if (messageContainsEmojiButton != null) {
							(messageContainsEmojiButton?.parent as? ViewGroup)?.removeView(messageContainsEmojiButton)
						}
						else {
							updateCustomReactionsButton()
						}

						val frameLayout = FrameLayout(context)

						val gap = View(context)
						gap.setBackgroundColor(context.getColor(R.color.divider))

						frameLayout.addView(gap, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8f))
						frameLayout.addView(messageContainsEmojiButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), 0, 0f, 8f, 0f, 0f))
						frameLayout
					}

					else -> {
						if (messageContainsEmojiButton != null) {
							(messageContainsEmojiButton?.parent as? ViewGroup)?.removeView(messageContainsEmojiButton)
						}
						else {
							updateCustomReactionsButton()
						}

						val frameLayout = FrameLayout(context)

						val gap = View(context)
						gap.setBackgroundColor(context.getColor(R.color.divider))

						frameLayout.addView(gap, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8f))
						frameLayout.addView(messageContainsEmojiButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), 0, 0f, 8f, 0f, 0f))
						frameLayout
					}
				}
				return RecyclerListView.Holder(view)
			}

			override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
				if (holder.itemViewType == USER_VIEW_TYPE) {
					val rhv = holder.itemView as ReactedUserHolderView
					rhv.setUserReaction(userReactions[position])
				}
			}

			override fun getItemCount(): Int {
				return userReactions.size + if (customReactionsEmoji.isNotEmpty() && !MessagesController.getInstance(currentAccount).premiumLocked) 1 else 0
			}

			override fun getItemViewType(position: Int): Int {
				return if (position < userReactions.size) {
					USER_VIEW_TYPE
				}
				else {
					CUSTOM_EMOJI_VIEW_TYPE
				}
			}
		}.also {
			adapter = it
		}

		listView.setOnItemClickListener { _, position ->
			val itemViewType = adapter?.getItemViewType(position)

			if (itemViewType == USER_VIEW_TYPE) {
				onProfileSelectedListener?.onProfileSelected(this, MessageObject.getPeerId(userReactions[position].peer_id), userReactions[position])
			}
			else if (itemViewType == CUSTOM_EMOJI_VIEW_TYPE) {
				onCustomEmojiSelectedListener?.showCustomEmojiAlert(this, customEmojiStickerSets)
			}
		}

		listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				if (isLoaded && canLoadMore && !isLoading && llm.findLastVisibleItemPosition() >= adapter!!.itemCount - 1 - loadCount) {
					load()
				}
			}
		})

		listView.isVerticalScrollBarEnabled = true
		listView.alpha = 0f

		addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		loadingView = object : FlickerLoadingView(context) {
			override val additionalHeight: Int
				get() = if (customReactionsEmoji.isNotEmpty() && messageContainsEmojiButton != null) messageContainsEmojiButton!!.measuredHeight + AndroidUtilities.dp(8f) else 0
		}

		loadingView.setIsSingleCell(true)
		loadingView.setItemsCount(predictiveCount)

		addView(loadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		if (!addPadding && filter != null && filter is TL_reactionCustomEmoji && !MessagesController.getInstance(currentAccount).premiumLocked) {
			customReactionsEmoji.clear()
			customReactionsEmoji.add(fromTLReaction(filter))
			updateCustomReactionsButton()
		}

		loadingView.setViewType(if (customReactionsEmoji.isEmpty()) FlickerLoadingView.REACTED_TYPE else FlickerLoadingView.REACTED_TYPE_WITH_EMOJI_HINT)
	}

	@SuppressLint("NotifyDataSetChanged")
	fun setSeenUsers(users: List<User>): ReactedUsersListView {
		val nr: MutableList<TL_messagePeerReaction> = ArrayList(users.size)

		for (u in users) {
			var userReactions = peerReactionMap[u.id]

			if (userReactions != null) {
				continue
			}

			val r = TL_messagePeerReaction()
			r.reaction = null
			r.peer_id = TL_peerUser()
			r.peer_id.user_id = u.id

			userReactions = ArrayList()
			userReactions.add(r)

			peerReactionMap.put(MessageObject.getPeerId(r.peer_id), userReactions)

			nr.add(r)
		}

		if (userReactions.isEmpty()) {
			onlySeenNow = true
		}

		userReactions.addAll(nr)

		userReactions.sortBy {
			if (it.reaction != null) 0 else 1
		}

		adapter?.notifyDataSetChanged()

		updateHeight()

		return this
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()

		if (!isLoaded && !isLoading) {
			load()
		}
	}

	@SuppressLint("NotifyDataSetChanged")
	private fun load() {
		isLoading = true

		val ctrl = MessagesController.getInstance(currentAccount)

		val getList = TL_messages_getMessageReactionsList()
		getList.peer = ctrl.getInputPeer(message.dialogId)
		getList.id = message.id
		getList.limit = loadCount
		getList.reaction = filter
		getList.offset = offset

		if (filter != null) {
			getList.flags = getList.flags or 1
		}
		if (offset != null) {
			getList.flags = getList.flags or 2
		}

		ConnectionsManager.getInstance(currentAccount).sendRequest(getList, { response, _ ->
			AndroidUtilities.runOnUIThread {
				NotificationCenter.getInstance(currentAccount).doOnIdle {
					if (response is TL_messages_messageReactionsList) {
						for (u in response.users) {
							MessagesController.getInstance(currentAccount).putUser(u, false)
						}

						val visibleCustomEmojiReactions = HashSet<VisibleReaction>()

						for (i in response.reactions.indices) {
							userReactions.add(response.reactions[i])

							val peerId = MessageObject.getPeerId(response.reactions[i].peer_id)
							var currentUserReactions = peerReactionMap[peerId]

							if (currentUserReactions == null) {
								currentUserReactions = ArrayList()
							}

							var k = 0

							while (k < currentUserReactions.size) {
								if (currentUserReactions[k].reaction == null) {
									currentUserReactions.removeAt(k)
									k--
								}
								k++
							}

							val visibleReaction = fromTLReaction(response.reactions[i].reaction)

							if (visibleReaction.documentId != 0L) {
								visibleCustomEmojiReactions.add(visibleReaction)
							}

							currentUserReactions.add(response.reactions[i])

							peerReactionMap.put(peerId, currentUserReactions)
						}

						if (filter == null) {
							customReactionsEmoji.clear()
							customReactionsEmoji.addAll(visibleCustomEmojiReactions)
							updateCustomReactionsButton()
						}

						userReactions.sortBy {
							if (it.reaction != null) 0 else 1
						}

						adapter?.notifyDataSetChanged()

						if (!isLoaded) {
							val anim = ValueAnimator.ofFloat(0f, 1f).setDuration(150)
							anim.interpolator = CubicBezierInterpolator.DEFAULT

							anim.addUpdateListener {
								val `val` = it.animatedValue as Float
								listView.alpha = `val`
								loadingView.alpha = 1f - `val`
							}

							anim.addListener(object : AnimatorListenerAdapter() {
								override fun onAnimationEnd(animation: Animator) {
									loadingView.gone()
								}
							})

							anim.start()
							updateHeight()

							isLoaded = true
						}

						offset = response.next_offset

						if (offset == null) {
							canLoadMore = false
						}

						isLoading = false
					}
					else {
						isLoading = false
					}
				}
			}
		}, ConnectionsManager.RequestFlagInvokeAfter)
	}

	private fun updateCustomReactionsButton() {
		customEmojiStickerSets.clear()

		val sets = ArrayList<InputStickerSet>()
		val setIds = HashSet<Long>()

		for (i in customReactionsEmoji.indices) {
			val stickerSet = MessageObject.getInputStickerSet(AnimatedEmojiDrawable.findDocument(currentAccount, customReactionsEmoji[i].documentId))

			if (stickerSet != null && !setIds.contains(stickerSet.id)) {
				sets.add(stickerSet)
				setIds.add(stickerSet.id)
			}
		}

		if (MessagesController.getInstance(currentAccount).premiumLocked) {
			return
		}

		customEmojiStickerSets.addAll(sets)

		messageContainsEmojiButton = MessageContainsEmojiButton(currentAccount, context, sets, MessageContainsEmojiButton.REACTIONS_TYPE)
		messageContainsEmojiButton?.checkWidth = false
	}

	private fun updateHeight() {
		val onHeightChangedListener = onHeightChangedListener ?: return
		val h: Int
		var count = userReactions.size

		if (count == 0) {
			count = predictiveCount
		}

		var measuredHeight = AndroidUtilities.dp((ITEM_HEIGHT_DP * count).toFloat())

		messageContainsEmojiButton?.let {
			measuredHeight += it.measuredHeight + AndroidUtilities.dp(8f)
		}

		h = if (listView.measuredHeight != 0) {
			min(listView.measuredHeight, measuredHeight)
		}
		else {
			measuredHeight
		}

		onHeightChangedListener.onHeightChanged(this@ReactedUsersListView, h)
	}

	private val loadCount: Int
		get() = if (filter == null) 100 else 50

	private inner class ReactedUserHolderView(context: Context) : FrameLayout(context) {
		val avatarView = BackupImageView(context)
		val titleView = TextView(context)
		val reactView = BackupImageView(context)
		val avatarDrawable = AvatarDrawable()
		val overlaySelectorView = View(context)

		init {
			layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48f))

			avatarView.setRoundRadius(AndroidUtilities.dp(32f))

			addView(avatarView, LayoutHelper.createFrameRelatively(36f, 36f, Gravity.START or Gravity.CENTER_VERTICAL, 8f, 0f, 0f, 0f))

			titleView.setLines(1)
			titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			titleView.setTextColor(context.getColor(R.color.text))
			titleView.ellipsize = TextUtils.TruncateAt.END
			titleView.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO

			addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT.toFloat(), LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.START or Gravity.CENTER_VERTICAL, 58f, 0f, 44f, 0f))

			addView(reactView, LayoutHelper.createFrameRelatively(24f, 24f, Gravity.END or Gravity.CENTER_VERTICAL, 0f, 0f, 12f, 0f))

			overlaySelectorView.background = Theme.getSelectorDrawable(false)

			addView(overlaySelectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
		}

		fun setUserReaction(reaction: MessagePeerReaction) {
			val u = MessagesController.getInstance(currentAccount).getUser(MessageObject.getPeerId(reaction.peer_id)) ?: return

			avatarDrawable.setInfo(u)

			titleView.text = UserObject.getUserName(u)

			var thumb: Drawable? = avatarDrawable

			if (u.photo?.strippedBitmap != null) {
				thumb = u.photo?.strippedBitmap
			}

			avatarView.setImage(ImageLocation.getForUser(u, ImageLocation.TYPE_SMALL), "50_50", thumb, u)

			if (reaction.reaction != null) {
				val visibleReaction = fromTLReaction(reaction.reaction)

				if (visibleReaction.emojicon != null) {
					val r = MediaDataController.getInstance(currentAccount).reactionsMap[visibleReaction.emojicon]

					if (r != null) {
						val svgThumb = getSvgThumb(r.static_icon?.thumbs, ResourcesCompat.getColor(context.resources, R.color.light_background, null), 1.0f)
						reactView.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_lastframe", "webp", svgThumb, r)
					}
					else {
						reactView.setImageDrawable(null)
					}
				}
				else {
					reactView.animatedEmojiDrawable = AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, currentAccount, visibleReaction.documentId)
				}

				contentDescription = LocaleController.formatString("AccDescrReactedWith", R.string.AccDescrReactedWith, UserObject.getUserName(u), reaction.reaction)
			}
			else {
				reactView.setImageDrawable(null)
				contentDescription = LocaleController.formatString("AccDescrPersonHasSeen", R.string.AccDescrPersonHasSeen, UserObject.getUserName(u))
			}
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(ITEM_HEIGHT_DP.toFloat()), MeasureSpec.EXACTLY))
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
		}
	}

	fun setOnProfileSelectedListener(onProfileSelectedListener: OnProfileSelectedListener?): ReactedUsersListView {
		this.onProfileSelectedListener = onProfileSelectedListener
		return this
	}

	fun setOnHeightChangedListener(onHeightChangedListener: OnHeightChangedListener?): ReactedUsersListView {
		this.onHeightChangedListener = onHeightChangedListener
		return this
	}

	fun interface OnHeightChangedListener {
		fun onHeightChanged(view: ReactedUsersListView?, newHeight: Int)
	}

	fun interface OnProfileSelectedListener {
		fun onProfileSelected(view: ReactedUsersListView?, userId: Long, messagePeerReaction: MessagePeerReaction?)
	}

	fun interface OnCustomEmojiSelectedListener {
		fun showCustomEmojiAlert(reactedUsersListView: ReactedUsersListView?, stickerSets: ArrayList<InputStickerSet>?)
	}

	fun setPredictiveCount(predictiveCount: Int) {
		this.predictiveCount = predictiveCount
		loadingView.setItemsCount(predictiveCount)
	}

	class ContainerLinerLayout(context: Context) : LinearLayout(context) {
		@JvmField
		var hasHeader = false

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			var maxWidth = 0
			var listView: RecyclerListView? = null

			if (!hasHeader) {
				for (k in 0 until childCount) {
					if (getChildAt(k) is ReactedUsersListView) {
						listView = (getChildAt(k) as ReactedUsersListView).listView

						if (listView.adapter!!.itemCount == listView.childCount) {
							val count = listView.childCount

							for (i in 0 until count) {
								listView.getChildAt(i).measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.UNSPECIFIED), heightMeasureSpec)

								if (listView.getChildAt(i).measuredWidth > maxWidth) {
									maxWidth = listView.getChildAt(i).measuredWidth
								}
							}

							maxWidth += AndroidUtilities.dp(16f)
						}
					}
				}
			}

			var size = MeasureSpec.getSize(widthMeasureSpec)

			if (size < AndroidUtilities.dp(240f)) {
				size = AndroidUtilities.dp(240f)
			}

			if (size > AndroidUtilities.dp(280f)) {
				size = AndroidUtilities.dp(280f)
			}

			if (size < 0) {
				size = 0
			}

			if (maxWidth != 0 && maxWidth < size) {
				size = maxWidth
			}

			if (listView != null) {
				for (i in 0 until listView.childCount) {
					listView.getChildAt(i).measure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), heightMeasureSpec)
				}
			}

			super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), heightMeasureSpec)
		}
	}

	fun setOnCustomEmojiSelectedListener(onCustomEmojiSelectedListener: OnCustomEmojiSelectedListener?): ReactedUsersListView {
		this.onCustomEmojiSelectedListener = onCustomEmojiSelectedListener
		return this
	}

	companion object {
		const val VISIBLE_ITEMS = 6
		const val ITEM_HEIGHT_DP = 48
		private const val USER_VIEW_TYPE = 0
		private const val CUSTOM_EMOJI_VIEW_TYPE = 1
	}
}
