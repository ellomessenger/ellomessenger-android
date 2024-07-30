/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.feed

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.beint.elloapp.allCornersProvider
import com.beint.elloapp.getOutlineProvider
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ChatObject
import org.telegram.messenger.ContactsController
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocationController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.SendMessagesHelper
import org.telegram.messenger.UserConfig
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.databinding.AudioLayoutBinding
import org.telegram.messenger.databinding.DocumentLayoutBinding
import org.telegram.messenger.databinding.FeedViewHolderBinding
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.utils.getChannel
import org.telegram.messenger.utils.getDimensionRaw
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.lastActiveDateToString
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.User
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.AvatarImageView
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.LayoutHelper
import java.util.Date
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class FeedViewHolder(val binding: FeedViewHolderBinding) : RecyclerView.ViewHolder(binding.root) {
	private val avatarDrawable = AvatarDrawable()
	private val avatarImage = AvatarImageView(binding.root.context)
	private var photoVideoAttachmentsAdapter: PhotoVideoAttachmentsAdapter? = null
	private val currentAccount = UserConfig.selectedAccount
	private val locationUpdatesRunnable = Runnable { checkLocationExpired() }
	private var messages: List<MessageObject>? = null
	private var webPreviewImageReceiver: ImageReceiver? = null
	private var likesCount = 0
	private var viewsCount = 0
	var delegate: Delegate? = null

	init {
		binding.root.clipChildren = false
		binding.root.clipToPadding = false

		binding.likesButton.clipToOutline = true
		binding.likesButton.outlineProvider = getOutlineProvider(radius = AndroidUtilities.dp(16f).toFloat(), topCorners = true, bottomCorners = true)

		avatarImage.setRoundRadius(AndroidUtilities.dp(binding.root.context.resources.getDimensionRaw(R.dimen.common_size_42dp) * 0.45f))
		binding.avatarContainer.addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
			override fun onPageSelected(position: Int) {
				updateMediaCounter()
			}
		})

		if (contentWidth == 0) {
			binding.root.doOnPreDraw {
				// contentWidth = AndroidUtilities.getRealScreenSize().x - binding.guideline.right - binding.photoVideoContainer.marginRight
				contentWidth = binding.headerContainer.width // TODO: check
			}
		}

		// MARK: remove to enable translate button
		binding.translateButton.gone()

		// MARK: uncomment to enable 'translate' button
//		binding.translateButton.setOnClickListener {
//			// TODO: translate message
//			Toast.makeText(it.context, "TODO: Translate message", Toast.LENGTH_SHORT).show()
//		}

		binding.webPreviewImage.clipToOutline = true
		binding.webPreviewImage.outlineProvider = allCornersProvider(AndroidUtilities.dp(8f).toFloat())
	}

	private fun createWebPreviewImageReceiver(): ImageReceiver {
		return ImageReceiver(binding.webPreviewImage).apply {
			setDelegate(object : ImageReceiver.ImageReceiverDelegate {
				override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
					imageReceiver.drawable?.let {
						binding.webPreviewImage.setImageDrawable(it)

						val bg = AndroidUtilities.calcDrawableColor(it)?.firstOrNull()

						if (bg != null) {
							binding.webPreviewImage.setBackgroundColor(bg)
						}
						else {
							binding.webPreviewImage.setBackgroundColor(Color.TRANSPARENT)
						}

						binding.webPreviewImage.visible()
					} ?: binding.webPreviewImage.gone()
				}
			})
		}
	}

	fun notifyAboutPlayback(messageObject: MessageObject?) {
		if (messageObject == null) {
			return
		}

		binding.audioContainer.children.forEach {
			if (it is AudioLayout) {
				if (it.messageObject?.id == messageObject.id) {
					it.updateMediaStatus()
				}
			}
		}
	}

	private fun updateMediaCounter() {
		val itemCount = photoVideoAttachmentsAdapter?.itemCount ?: 0

		if (itemCount > 1) {
			binding.counterLabel.visible()

			val position = binding.viewPager.currentItem

			binding.counterLabel.text = itemView.context.getString(R.string.counter_x_of_y, position + 1, itemCount)

			binding.pageIndicatorContainer.children.forEach {
				it.isSelected = (it.tag == position)
			}
		}
		else {
			binding.counterLabel.gone()
			binding.counterLabel.text = null
		}
	}

	fun getMessages(): List<MessageObject>? {
		return messages
	}

	fun bind(messages: List<MessageObject>?, pinned: Boolean) {
		AndroidUtilities.cancelRunOnUIThread(locationUpdatesRunnable)

		likesCount = 0
		viewsCount = 0

		webPreviewImageReceiver?.cancelLoadImage()
		webPreviewImageReceiver = null

		binding.avatarsContainer.removeAllViews()

		this.messages = messages

		if (messages.isNullOrEmpty()) {
			avatarDrawable.setInfo()
			avatarImage.setForUserOrChat(null, avatarDrawable)

			binding.courseIcon.gone()
			binding.paidFeedIcon.gone()
			binding.adultChannelIcon.gone()
			binding.nameLabel.text = null
			binding.verifiedIcon.gone()
			binding.usernameLabel.text = null
			binding.dateLabel.text = null
			binding.pinIcon.gone()
			binding.root.setOnClickListener(null)
			binding.viewPager.adapter = null
			binding.photoVideoContainer.gone()
			binding.messageLabel.gone()
			binding.messageLabel.text = null
			binding.moreButton.setOnClickListener(null)
			binding.webPreviewContainer.gone()

			return
		}

		val channel = messages.first().messageOwner?.getChannel()

		avatarDrawable.setInfo(channel)
		avatarImage.setForUserOrChat(channel, avatarDrawable)

		if (channel?.pay_type == TLRPC.Chat.PAY_TYPE_SUBSCRIBE || channel?.pay_type == TLRPC.Chat.PAY_TYPE_BASE) {
			binding.paidFeedIcon.visible()
		}
		else {
			binding.paidFeedIcon.gone()
		}

		if (ChatObject.isOnlineCourse(channel)) {
			binding.paidFeedIcon.gone()
			binding.courseIcon.visible()
		}
		else {
			binding.courseIcon.gone()
		}

		if (channel?.adult == true) {
			binding.adultChannelIcon.visible()
		}
		else {
			binding.adultChannelIcon.gone()
		}

		binding.nameLabel.text = channel?.title

		if (channel?.verified == true) {
			binding.verifiedIcon.visible()
		}
		else {
			binding.verifiedIcon.gone()
		}

		val username = channel?.username?.run { "@$this" }

		binding.usernameLabel.text = username

		binding.dateLabel.text = Date(messages.first().messageOwner!!.date.toLong() * 1000L).lastActiveDateToString()

		if (pinned) {
			binding.pinIcon.visible()
		}
		else {
			binding.pinIcon.gone()
		}

		binding.moreButton.setOnClickListener {
			delegate?.onContextMenuClick(messages, channel?.id ?: 0L, it)
		}

		val channelId = channel?.id ?: 0
		val noForwards = MessagesController.getInstance(UserConfig.selectedAccount).isChatNoForwards(channelId) || messages.any { it.messageOwner?.noforwards == true } || channel?.pay_type == TLRPC.Chat.PAY_TYPE_SUBSCRIBE || channel?.pay_type == TLRPC.Chat.PAY_TYPE_BASE

		if (noForwards) {
			binding.buttonForward.gone()
		}
		else {
			binding.buttonForward.visible()

			binding.buttonForward.setOnClickListener {
				delegate?.onRepost(messages)
			}
		}

		val text = messages.firstOrNull { !it.messageOwner?.message.isNullOrEmpty() }?.messageOwner?.message

		if (text.isNullOrEmpty()) {
			binding.messageLabel.gone()
			binding.translateButton.gone()
		}
		else {
			binding.messageLabel.text = text
			binding.messageLabel.visible()
			// MARK: uncomment to enable 'translate' button
			// binding.translateButton.visible()
		}

		viewsCount = messages.firstOrNull()?.messageOwner?.views ?: 0

		if (viewsCount == 0) {
			binding.viewsLabel.gone()
			binding.viewsImage.gone()

			binding.viewsLabel.text = null
		}
		else {
			binding.viewsLabel.text = viewsCount.toString()

			binding.viewsLabel.visible()
			binding.viewsImage.visible()
		}

		likesCount = messages.firstOrNull()?.messageOwner?.likes ?: 0

		binding.likesCounterLabel.text = likesCount.toString()

		val chatInfo = MessagesController.getInstance(currentAccount).getChatFull(channelId)
		val linkedChatId = chatInfo?.linked_chat_id ?: 0
		val linked = messages.firstOrNull()?.isLinkedToChat(linkedChatId) ?: false
		val hasDiscussion = ChatObject.isChannel(channel) && channel.has_link && !channel.megagroup

		if (hasDiscussion && linked) {
			binding.commentsContainerPlaceholder.gone()

			val messageWithComments = messages.find { it.repliesCount > 0 }
			val numberOfComments = messageWithComments?.repliesCount ?: 0

			binding.commentsLabel.text = binding.root.context.resources.getQuantityString(R.plurals.comments, numberOfComments, numberOfComments)
			binding.commentsContainer.visible()

			binding.commentsContainer.setOnClickListener {
				delegate?.onOpenComments(messages)
			}

			if (numberOfComments > 0) {
				binding.avatarsContainer.visible()

				val messagesController = MessagesController.getInstance(UserConfig.selectedAccount)

				messageWithComments?.messageOwner?.replies?.recent_repliers?.take(3)?.forEachIndexed { index, peer ->
					val info = messagesController.getUser(peer.user_id)

					val avatarImage = AvatarImageView(binding.root.context)
					avatarImage.setRoundRadius(AndroidUtilities.dp(24f))
					avatarImage.shouldDrawBorder = true
					avatarImage.translationZ = AndroidUtilities.dp(2f - index.toFloat()).toFloat()
					avatarImage.contentDescription = null

					binding.avatarsContainer.addView(avatarImage, LayoutHelper.createLinear(32, 32, if (index == 0) 0f else -7f, 0f, 0f, 0f))

					val avatarDrawable = AvatarDrawable()
					avatarDrawable.setInfo(info)

					avatarImage.setForUserOrChat(info, avatarDrawable)
				}
			}
			else {
				binding.avatarsContainer.gone()
			}
		}
		else {
			binding.commentsContainer.gone()
			binding.commentsContainer.setOnClickListener(null)

			binding.commentsLabel.text = null

			binding.commentsContainerPlaceholder.visible()
		}

		binding.likesButton.setOnClickListener {
			val message = messages.firstOrNull()?.messageOwner ?: return@setOnClickListener

			if (message.is_liked) {
				dislike(message)
			}
			else {
				like(message)
			}
		}

		setupAdapter(messages, channel)

		reloadLikeButton()
	}

	private fun reloadLikeButton() {
		val message = messages?.firstOrNull()?.messageOwner ?: return

		if (message.is_liked) {
			binding.likesButton.setBackgroundResource(R.drawable.likes_button_selected_background)
			binding.likesCounterLabel.setTextColor(Color.WHITE)
		}
		else {
			binding.likesButton.setBackgroundResource(R.drawable.likes_button_background)
			binding.likesCounterLabel.setTextColor(ResourcesCompat.getColor(binding.root.context.resources, R.color.text_fixed, null))
		}
	}

	private fun like(message: Message) {
		SendMessagesHelper.getInstance(currentAccount).like(message) {
			likesCount = message.likes
			binding.likesCounterLabel.text = likesCount.toString()
			reloadLikeButton()
		}
	}

	private fun dislike(message: Message) {
		SendMessagesHelper.getInstance(currentAccount).dislike(message) {
			likesCount = message.likes
			binding.likesCounterLabel.text = likesCount.toString()
			reloadLikeButton()
		}
	}

	private fun setupMediaPageIndicator() {
		binding.pageIndicatorContainer.removeAllViews()

		val itemCount = (photoVideoAttachmentsAdapter?.itemCount ?: 0)

		if (itemCount > 1) {
			for (i in 0 until itemCount) {
				val view = View(binding.pageIndicatorContainer.context)
				view.setBackgroundResource(R.drawable.page_indicator)
				view.tag = i

				binding.pageIndicatorContainer.addView(view, LayoutHelper.createLinear(5, 5))
			}

			binding.pageIndicatorContainer.visible()
		}
		else {
			binding.pageIndicatorContainer.gone()
		}
	}

	private fun clearRecyclerView() {
		photoVideoAttachmentsAdapter = null

		binding.viewPager.adapter = null
		binding.photoVideoContainer.gone()

		binding.pageIndicatorContainer.gone()
		binding.counterLabel.gone()

		binding.contactContainer.gone()

		binding.messageLabel.maxLines = 10
	}

	private fun clearAudio() {
		binding.audioContainer.gone()
		binding.audioContainer.removeAllViews()
	}

	private fun setupPhotoVideo(messages: List<MessageObject>, channel: TLRPC.TL_channel?) {
		photoVideoAttachmentsAdapter = PhotoVideoAttachmentsAdapter(channel = channel, messages = messages, contentWidth = contentWidth, onClickListener = {
			binding.root.performClick()
		})

		photoVideoAttachmentsAdapter?.delegate = delegate

		setupMediaPageIndicator()

		val height = messages.maxOfOrNull {
			FileLoader.getClosestPhotoSizeWithSize(it.photoThumbs, contentWidth, true)?.h ?: 0
		} ?: 0

		val width = messages.maxOfOrNull {
			FileLoader.getClosestPhotoSizeWithSize(it.photoThumbs, contentWidth, true)?.w ?: 0
		} ?: 0

		val ratio = if (width > 0 && height > 0) {
			height.toFloat() / width.toFloat()
		}
		else {
			1f
		}

		var newHeight = (contentWidth * ratio).toInt()

		if (newHeight > AndroidUtilities.getRealScreenSize().y / 2) {
			newHeight = AndroidUtilities.getRealScreenSize().y / 2

			photoVideoAttachmentsAdapter?.setScaleType(ImageView.ScaleType.CENTER_CROP)
		}
		else {
			photoVideoAttachmentsAdapter?.setScaleType(null)
		}

		binding.photoVideoContainer.updateLayoutParams<LinearLayout.LayoutParams> {
			this.height = newHeight
		}

		binding.viewPager.adapter = photoVideoAttachmentsAdapter
		binding.photoVideoContainer.visible()

		binding.messageLabel.maxLines = 3

		updateMediaCounter()
	}

	private fun setupAudio(messages: List<MessageObject>) {
		val filteredMessages = messages.filter {
			val type = detectMessageType(it)
			type == MEDIA_TYPE_VOICE || type == MEDIA_TYPE_AUDIO
		}.sortedBy {
			it.messageOwner?.date
		}

		if (filteredMessages.isEmpty()) {
			return
		}

		for (message in filteredMessages) {
			val audioLayout = AudioLayout(AudioLayoutBinding.inflate(LayoutInflater.from(binding.audioContainer.context), binding.audioContainer, false))
			audioLayout.setMessageObject(message, detectMessageType(message))

			binding.audioContainer.addView(audioLayout)
		}

		binding.audioContainer.visible()
	}

	private fun checkLocationExpired() {
		val locationLayout = binding.audioContainer.children.find { it is LocationLayout } as? LocationLayout ?: return

		binding.messageLabel.visible()
		binding.infoLabel.visible()

		if (locationLayout.expiresIn > 0) {
			binding.messageLabel.text = itemView.context.getString(R.string.location_sharing)
		}
		else {
			binding.messageLabel.text = itemView.context.getString(R.string.location_expired)
		}

		val lastUpdateDiff = abs(ConnectionsManager.getInstance(currentAccount).currentTime - locationLayout.lastUpdateDate)

		if (lastUpdateDiff < 60) {
			binding.infoLabel.text = itemView.context.getString(R.string.location_updated_recently)
		}
		else {
			val minutes = lastUpdateDiff / 60

			if (minutes > 60) {
				val hours = minutes / 60

				if (hours > 24) {
					val days = hours / 24

					if (days > 365) {
						val years = days / 365
						binding.infoLabel.text = itemView.context.getString(R.string.updated_format_ago, years, itemView.resources.getQuantityString(R.plurals.years_no_format, years))
					}
					else if (days > 7) {
						val weeks = days / 7
						binding.infoLabel.text = itemView.context.getString(R.string.updated_format_ago, weeks, itemView.resources.getQuantityString(R.plurals.weeks_no_format, weeks))
					}
					else {
						binding.infoLabel.text = itemView.context.getString(R.string.updated_format_ago, days, itemView.resources.getQuantityString(R.plurals.days_no_format, days))
					}
				}
				else {
					binding.infoLabel.text = itemView.context.getString(R.string.updated_format_ago, hours, itemView.resources.getQuantityString(R.plurals.hours_no_format, hours))
				}
			}
			else {
				binding.infoLabel.text = itemView.context.getString(R.string.updated_format_ago, minutes, itemView.resources.getQuantityString(R.plurals.minutes_no_format, minutes))
			}
		}
	}

	private fun setupGeo(messages: List<MessageObject>) {
		val geoMessage = messages.filter {
			val type = detectMessageType(it)
			type == MEDIA_TYPE_GEO || type == MEDIA_TYPE_LIVE_GEO
		}.minByOrNull {
			it.messageOwner?.date ?: 0
		} ?: return

		val locationLayout = LocationLayout(binding.audioContainer.context, contentWidth)
		locationLayout.messageObject = geoMessage

		binding.audioContainer.addView(locationLayout)
		binding.audioContainer.visible()

		if (locationLayout.isLiveLocation) {
			if (geoMessage.messageOwner?.message.isNullOrEmpty()) {
				binding.messageLabel.typeface = Theme.TYPEFACE_BOLD
				AndroidUtilities.runOnUIThread(locationUpdatesRunnable, 1_000)
				checkLocationExpired()
			}
		}
		else {
			val coordinates = locationLayout.coordinates

			if (coordinates != null) {
				binding.messageLabel.typeface = Theme.TYPEFACE_BOLD
				binding.messageLabel.text = itemView.context.getString(R.string.coordinates_format, coordinates.first, coordinates.second)
				binding.messageLabel.visible()

				LocationController.fetchCoordinatesAddress(coordinates.first, coordinates.second) { _, displayAddress, latitude, longitude ->
					if (latitude != coordinates.first && longitude != coordinates.second) {
						return@fetchCoordinatesAddress
					}

					binding.infoLabel.visible()
					binding.infoLabel.text = displayAddress
				}
			}
			else {
				binding.messageLabel.gone()
				binding.messageLabel.text = null
			}
		}
	}

	private fun setupContacts(messages: List<MessageObject>) {
		binding.contactContainer.visible()
		val avatarDrawable = AvatarDrawable()
		val avatarImage = AvatarImageView(binding.root.context)

		val message = messages.firstOrNull {
			val type = detectMessageType(it)
			type == MEDIA_TYPE_CONTACT
		} ?: return

		val contactInfo = MessagesController.getInstance(UserConfig.selectedAccount).getUser(message.messageOwner?.media?.user_id)

		avatarDrawable.setInfo(contactInfo)
		avatarImage.setForUserOrChat(contactInfo, avatarDrawable)

		avatarImage.setRoundRadius(AndroidUtilities.dp(35f))
		binding.userAvatarContainer.addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		val firstName = contactInfo?.first_name ?: ""
		val lastName = contactInfo?.last_name ?: ""
		val username = contactInfo?.username ?: ""

		binding.userFullName.text = itemView.context.getString(R.string.full_name_template, firstName, lastName)
		binding.username.text = itemView.context.getString(R.string.profile_format, username)

		binding.btAddContact.setOnClickListener {
			if (isUserInContacts(contactInfo?.id)) {
				delegate?.onOpenAddToContacts(contactInfo)
			}
			else {
				Toast.makeText(itemView.context, itemView.context.getString(R.string.user_in_contacts, username), Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun isUserInContacts(userId: Long?): Boolean {
		return ContactsController.getInstance(UserConfig.selectedAccount).contactsDict[userId] == null
	}

	private fun setupWebpage(messages: List<MessageObject>) {
		binding.webPreviewUrl.gone()
		binding.webPreviewTitle.gone()
		binding.webPreviewDescription.gone()
		binding.webPreviewImage.gone()

		val message = messages.firstOrNull {
			val type = detectMessageType(it)
			type == MEDIA_TYPE_WEBPAGE
		} ?: return

		val url = message.messageOwner?.media?.webpage?.display_url
		val title = message.messageOwner?.media?.webpage?.title
		val description = message.messageOwner?.media?.webpage?.description
		val image = message.messageOwner?.media?.webpage?.photo

		if (url.isNullOrEmpty() && title.isNullOrEmpty() && description.isNullOrEmpty() && image == null) {
			binding.webPreviewContainer.gone()
			return
		}
		else {
			binding.webPreviewContainer.visible()
		}

		if (!url.isNullOrEmpty()) {
			binding.webPreviewUrl.text = url
			binding.webPreviewUrl.visible()
		}

		if (!title.isNullOrEmpty()) {
			binding.webPreviewTitle.text = title
			binding.webPreviewTitle.visible()
		}

		if (!description.isNullOrEmpty()) {
			binding.webPreviewDescription.text = description
			binding.webPreviewDescription.visible()
		}

		if (image != null) {
			val contentWidth = contentWidth - AndroidUtilities.dp(9f)
			val currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, contentWidth, true)
			val height = currentPhotoObject?.h ?: 0
			val width = currentPhotoObject?.w ?: 0

			val ratio = if (width > 0 && height > 0) {
				height.toFloat() / width.toFloat()
			}
			else {
				1f
			}

			var newHeight = (contentWidth * ratio).toInt()

			if (newHeight > AndroidUtilities.getRealScreenSize().y / 2) {
				newHeight = AndroidUtilities.getRealScreenSize().y / 2
			}

			binding.webPreviewImage.updateLayoutParams<ConstraintLayout.LayoutParams> {
				this.height = newHeight
			}

			webPreviewImageReceiver = createWebPreviewImageReceiver()
			webPreviewImageReceiver?.setImage(ImageLocation.getForObject(currentPhotoObject, message.photoThumbsObject), null, null, null, 0L, null, null, 1)
		}

		binding.messageLabel.gone()
		binding.translateButton.gone()

		binding.webPreviewContainer.setOnClickListener {
			Browser.openUrl(it.context, url)
		}
	}

	private fun setupDocument(messages: List<MessageObject>) {
		val filteredMessages = messages.filter {
			val type = detectMessageType(it)
			type == MEDIA_TYPE_DOCUMENT
		}.sortedBy {
			it.messageOwner?.date
		}

		if (filteredMessages.isEmpty()) {
			return
		}

		for (message in filteredMessages) {
			val documentLayout = DocumentLayout(DocumentLayoutBinding.inflate(LayoutInflater.from(binding.audioContainer.context), binding.audioContainer, false))
			documentLayout.messageObject = message

			binding.audioContainer.addView(documentLayout)
		}

//		binding.audioContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
//			val margin = AndroidUtilities.dp(12f)
//
//			leftMargin = margin
//			rightMargin = margin
//		}

		binding.audioContainer.visible()
	}

	private fun setupAdapter(messages: List<MessageObject>?, channel: TLRPC.TL_channel?) {
		clearAudio()

		binding.webPreviewContainer.gone()
		binding.webPreviewContainer.setOnClickListener(null)

		binding.messageLabel.typeface = Theme.TYPEFACE_DEFAULT

		binding.infoLabel.gone()
		binding.infoLabel.text = null

		when (detectMessageType(messages?.firstOrNull())) {
			MEDIA_TYPE_PHOTO, MEDIA_TYPE_VIDEO, MEDIA_TYPE_ROUND_VIDEO -> {
				clearRecyclerView()
				setupPhotoVideo(messages!!, channel)
			}

			MEDIA_TYPE_AUDIO, MEDIA_TYPE_VOICE -> {
				clearRecyclerView()
				setupAudio(messages!!)
			}

			MEDIA_TYPE_DOCUMENT -> {
				clearRecyclerView()
				setupDocument(messages!!)
			}

			MEDIA_TYPE_WEBPAGE -> {
				clearRecyclerView()
				setupWebpage(messages!!)
			}

			MEDIA_TYPE_GEO, MEDIA_TYPE_LIVE_GEO -> {
				clearRecyclerView()
				setupGeo(messages!!)
			}

			MEDIA_TYPE_CONTACT -> {
				clearRecyclerView()
				setupContacts(messages!!)
			}

			MEDIA_TYPE_STICKER -> {
				clearRecyclerView()
				// TODO: process
			}

			MEDIA_TYPE_GIF -> {
				clearRecyclerView()
				// TODO: process
			}

			else -> {
				clearRecyclerView()
				// TODO: process
			}
		}
	}

	private fun detectMessageType(messageObject: MessageObject?): Int {
		if (messageObject == null) {
			return MEDIA_TYPE_UNSUPPORTED
		}

		val media = MessageObject.getMedia(messageObject.messageOwner) ?: return MEDIA_TYPE_UNSUPPORTED

		return when {
			media is TLRPC.TL_messageMediaPhoto -> {
				MEDIA_TYPE_PHOTO
			}

			messageObject.isVideo || media is TLRPC.TL_messageMediaDocument && messageObject.document is TLRPC.TL_documentEmpty && media.ttl_seconds != 0 -> {
				MEDIA_TYPE_VIDEO
			}

			messageObject.isVoice -> {
				MEDIA_TYPE_VOICE
			}

			messageObject.isRoundVideo -> {
				MEDIA_TYPE_ROUND_VIDEO
			}

			media is TLRPC.TL_messageMediaGeo || media is TLRPC.TL_messageMediaVenue -> {
				MEDIA_TYPE_GEO
			}

			media is TLRPC.TL_messageMediaGeoLive -> {
				MEDIA_TYPE_LIVE_GEO
			}

			media is TLRPC.TL_messageMediaContact -> {
				MEDIA_TYPE_CONTACT
			}

			media is TLRPC.TL_messageMediaDocument -> {
				if (messageObject.isSticker || MessageObject.isAnimatedStickerDocument(messageObject.document, true)) {
					MEDIA_TYPE_STICKER
				}
				else if (messageObject.isMusic) {
					MEDIA_TYPE_AUDIO
				}
				else if (messageObject.isGif) {
					MEDIA_TYPE_GIF
				}
				else {
					MEDIA_TYPE_DOCUMENT
				}
			}

			media is TLRPC.TL_messageMediaWebPage -> {
				MEDIA_TYPE_WEBPAGE
			}

			else -> {
				MEDIA_TYPE_UNSUPPORTED
			}
		}
	}

	interface Delegate {
		fun onRepost(messages: List<MessageObject>)
		fun onOpenComments(messages: List<MessageObject>)
		fun onOpenImages(messages: List<MessageObject>, dialogId: Long, message: MessageObject, imageView: ImageView)
		fun onContextMenuClick(messages: List<MessageObject>, dialogId: Long, view: View)
		fun fetchNextFeedPage()
		fun onFeedItemClick(message: Message)
		fun onOpenAddToContacts(user: User?)
	}

	companion object {
		const val MEDIA_TYPE_UNSUPPORTED = 0
		const val MEDIA_TYPE_PHOTO = 1
		const val MEDIA_TYPE_VIDEO = 2
		const val MEDIA_TYPE_AUDIO = 3
		const val MEDIA_TYPE_ROUND_VIDEO = 4
		const val MEDIA_TYPE_GEO = 5
		const val MEDIA_TYPE_LIVE_GEO = 6
		const val MEDIA_TYPE_CONTACT = 7
		const val MEDIA_TYPE_STICKER = 8
		const val MEDIA_TYPE_GIF = 9
		const val MEDIA_TYPE_DOCUMENT = 10
		const val MEDIA_TYPE_VOICE = 11
		const val MEDIA_TYPE_WEBPAGE = 12
		private var contentWidth = 0
	}
}
