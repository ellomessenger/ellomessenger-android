/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.FontMetricsInt
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.text.style.URLSpan
import android.text.util.Linkify
import android.util.SparseArray
import androidx.collection.LongSparseArray
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import org.telegram.SQLite.SQLiteCursor
import org.telegram.SQLite.SQLiteException
import org.telegram.messenger.ImageReceiver.ImageReceiverDelegate
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.messenger.ringtone.RingtoneDataStore
import org.telegram.messenger.ringtone.RingtoneUploader
import org.telegram.messenger.support.SparseLongArray
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.NativeByteBuffer
import org.telegram.tgnet.SerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.BotInfo
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.DraftMessage
import org.telegram.tgnet.TLRPC.EmojiStatus
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.InputStickerSet
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.StickerSet
import org.telegram.tgnet.TLRPC.StickerSetCovered
import org.telegram.tgnet.TLRPC.TL_account_emojiStatuses
import org.telegram.tgnet.TLRPC.TL_account_emojiStatusesNotModified
import org.telegram.tgnet.TLRPC.TL_account_getDefaultEmojiStatuses
import org.telegram.tgnet.TLRPC.TL_account_getRecentEmojiStatuses
import org.telegram.tgnet.TLRPC.TL_account_saveRingtone
import org.telegram.tgnet.TLRPC.TL_account_savedRingtoneConverted
import org.telegram.tgnet.TLRPC.TL_attachMenuBot
import org.telegram.tgnet.TLRPC.TL_attachMenuBotIcon
import org.telegram.tgnet.TLRPC.TL_attachMenuBots
import org.telegram.tgnet.TLRPC.TL_attachMenuBotsNotModified
import org.telegram.tgnet.TLRPC.TL_attachMenuPeerTypeBotPM
import org.telegram.tgnet.TLRPC.TL_attachMenuPeerTypeBroadcast
import org.telegram.tgnet.TLRPC.TL_attachMenuPeerTypeChat
import org.telegram.tgnet.TLRPC.TL_attachMenuPeerTypePM
import org.telegram.tgnet.TLRPC.TL_attachMenuPeerTypeSameBotPM
import org.telegram.tgnet.TLRPC.TL_boolTrue
import org.telegram.tgnet.TLRPC.TL_channels_getMessages
import org.telegram.tgnet.TLRPC.TL_contacts_getTopPeers
import org.telegram.tgnet.TLRPC.TL_contacts_resetTopPeerRating
import org.telegram.tgnet.TLRPC.TL_contacts_topPeers
import org.telegram.tgnet.TLRPC.TL_contacts_topPeersDisabled
import org.telegram.tgnet.TLRPC.TL_documentAttributeAnimated
import org.telegram.tgnet.TLRPC.TL_documentAttributeAudio
import org.telegram.tgnet.TLRPC.TL_documentAttributeCustomEmoji
import org.telegram.tgnet.TLRPC.TL_documentAttributeSticker
import org.telegram.tgnet.TLRPC.TL_documentAttributeVideo
import org.telegram.tgnet.TLRPC.TL_documentEmpty
import org.telegram.tgnet.TLRPC.TL_draftMessage
import org.telegram.tgnet.TLRPC.TL_draftMessageEmpty
import org.telegram.tgnet.TLRPC.TL_emojiKeyword
import org.telegram.tgnet.TLRPC.TL_emojiKeywordDeleted
import org.telegram.tgnet.TLRPC.TL_emojiKeywordsDifference
import org.telegram.tgnet.TLRPC.TL_emojiStatus
import org.telegram.tgnet.TLRPC.TL_help_getPremiumPromo
import org.telegram.tgnet.TLRPC.TL_help_premiumPromo
import org.telegram.tgnet.TLRPC.TL_inputDocument
import org.telegram.tgnet.TLRPC.TL_inputMessagesFilterDocument
import org.telegram.tgnet.TLRPC.TL_inputMessagesFilterEmpty
import org.telegram.tgnet.TLRPC.TL_inputMessagesFilterGif
import org.telegram.tgnet.TLRPC.TL_inputMessagesFilterMusic
import org.telegram.tgnet.TLRPC.TL_inputMessagesFilterPhotoVideo
import org.telegram.tgnet.TLRPC.TL_inputMessagesFilterPhotos
import org.telegram.tgnet.TLRPC.TL_inputMessagesFilterPinned
import org.telegram.tgnet.TLRPC.TL_inputMessagesFilterRoundVoice
import org.telegram.tgnet.TLRPC.TL_inputMessagesFilterUrl
import org.telegram.tgnet.TLRPC.TL_inputMessagesFilterVideo
import org.telegram.tgnet.TLRPC.TL_inputStickerSetAnimatedEmoji
import org.telegram.tgnet.TLRPC.TL_inputStickerSetDice
import org.telegram.tgnet.TLRPC.TL_inputStickerSetEmojiDefaultStatuses
import org.telegram.tgnet.TLRPC.TL_inputStickerSetEmojiGenericAnimations
import org.telegram.tgnet.TLRPC.TL_inputStickerSetEmpty
import org.telegram.tgnet.TLRPC.TL_inputStickerSetID
import org.telegram.tgnet.TLRPC.TL_inputStickerSetPremiumGifts
import org.telegram.tgnet.TLRPC.TL_inputStickerSetShortName
import org.telegram.tgnet.TLRPC.TL_messageActionGameScore
import org.telegram.tgnet.TLRPC.TL_messageActionHistoryClear
import org.telegram.tgnet.TLRPC.TL_messageActionPaymentSent
import org.telegram.tgnet.TLRPC.TL_messageActionPinMessage
import org.telegram.tgnet.TLRPC.TL_messageEmpty
import org.telegram.tgnet.TLRPC.TL_messageMediaDocument
import org.telegram.tgnet.TLRPC.TL_messageMediaPhoto
import org.telegram.tgnet.TLRPC.TL_messageReplyHeader
import org.telegram.tgnet.TLRPC.TL_messageService
import org.telegram.tgnet.TLRPC.TL_message_secret
import org.telegram.tgnet.TLRPC.TL_messages_allStickers
import org.telegram.tgnet.TLRPC.TL_messages_archivedStickers
import org.telegram.tgnet.TLRPC.TL_messages_availableReactions
import org.telegram.tgnet.TLRPC.TL_messages_availableReactionsNotModified
import org.telegram.tgnet.TLRPC.TL_messages_clearRecentReactions
import org.telegram.tgnet.TLRPC.TL_messages_clearRecentStickers
import org.telegram.tgnet.TLRPC.TL_messages_faveSticker
import org.telegram.tgnet.TLRPC.TL_messages_favedStickers
import org.telegram.tgnet.TLRPC.TL_messages_featuredStickers
import org.telegram.tgnet.TLRPC.TL_messages_getAllDrafts
import org.telegram.tgnet.TLRPC.TL_messages_getAllStickers
import org.telegram.tgnet.TLRPC.TL_messages_getArchivedStickers
import org.telegram.tgnet.TLRPC.TL_messages_getAttachMenuBots
import org.telegram.tgnet.TLRPC.TL_messages_getAvailableReactions
import org.telegram.tgnet.TLRPC.TL_messages_getEmojiKeywords
import org.telegram.tgnet.TLRPC.TL_messages_getEmojiKeywordsDifference
import org.telegram.tgnet.TLRPC.TL_messages_getEmojiStickers
import org.telegram.tgnet.TLRPC.TL_messages_getFavedStickers
import org.telegram.tgnet.TLRPC.TL_messages_getFeaturedEmojiStickers
import org.telegram.tgnet.TLRPC.TL_messages_getFeaturedStickers
import org.telegram.tgnet.TLRPC.TL_messages_getMaskStickers
import org.telegram.tgnet.TLRPC.TL_messages_getMessages
import org.telegram.tgnet.TLRPC.TL_messages_getRecentReactions
import org.telegram.tgnet.TLRPC.TL_messages_getRecentStickers
import org.telegram.tgnet.TLRPC.TL_messages_getSavedGifs
import org.telegram.tgnet.TLRPC.TL_messages_getScheduledMessages
import org.telegram.tgnet.TLRPC.TL_messages_getSearchCounters
import org.telegram.tgnet.TLRPC.TL_messages_getStickerSet
import org.telegram.tgnet.TLRPC.TL_messages_getStickers
import org.telegram.tgnet.TLRPC.TL_messages_getTopReactions
import org.telegram.tgnet.TLRPC.TL_messages_installStickerSet
import org.telegram.tgnet.TLRPC.TL_messages_reactions
import org.telegram.tgnet.TLRPC.TL_messages_readFeaturedStickers
import org.telegram.tgnet.TLRPC.TL_messages_recentStickers
import org.telegram.tgnet.TLRPC.TL_messages_saveDraft
import org.telegram.tgnet.TLRPC.TL_messages_saveGif
import org.telegram.tgnet.TLRPC.TL_messages_saveRecentSticker
import org.telegram.tgnet.TLRPC.TL_messages_savedGifs
import org.telegram.tgnet.TLRPC.TL_messages_search
import org.telegram.tgnet.TLRPC.TL_messages_searchCounter
import org.telegram.tgnet.TLRPC.TL_messages_stickerSet
import org.telegram.tgnet.TLRPC.TL_messages_stickerSetInstallResultArchive
import org.telegram.tgnet.TLRPC.TL_messages_stickers
import org.telegram.tgnet.TLRPC.TL_messages_toggleStickerSets
import org.telegram.tgnet.TLRPC.TL_messages_uninstallStickerSet
import org.telegram.tgnet.TLRPC.TL_peerChat
import org.telegram.tgnet.TLRPC.TL_peerUser
import org.telegram.tgnet.TLRPC.TL_stickerSetFullCovered
import org.telegram.tgnet.TLRPC.TL_theme
import org.telegram.tgnet.TLRPC.TL_topPeer
import org.telegram.tgnet.TLRPC.TL_topPeerCategoryBotsInline
import org.telegram.tgnet.TLRPC.TL_topPeerCategoryCorrespondents
import org.telegram.tgnet.TLRPC.TL_updateBotCommands
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.tgnet.TLRPC.account_EmojiStatuses
import org.telegram.tgnet.tlrpc.Message
import org.telegram.tgnet.tlrpc.MessageEntity
import org.telegram.tgnet.tlrpc.Reaction
import org.telegram.tgnet.tlrpc.TLObject
import org.telegram.tgnet.tlrpc.TL_availableReaction
import org.telegram.tgnet.tlrpc.TL_inputMessageEntityMentionName
import org.telegram.tgnet.tlrpc.TL_message
import org.telegram.tgnet.tlrpc.TL_messageEntityBlockquote
import org.telegram.tgnet.tlrpc.TL_messageEntityBold
import org.telegram.tgnet.tlrpc.TL_messageEntityCode
import org.telegram.tgnet.tlrpc.TL_messageEntityCustomEmoji
import org.telegram.tgnet.tlrpc.TL_messageEntityEmail
import org.telegram.tgnet.tlrpc.TL_messageEntityHashtag
import org.telegram.tgnet.tlrpc.TL_messageEntityItalic
import org.telegram.tgnet.tlrpc.TL_messageEntityMentionName
import org.telegram.tgnet.tlrpc.TL_messageEntityPre
import org.telegram.tgnet.tlrpc.TL_messageEntitySpoiler
import org.telegram.tgnet.tlrpc.TL_messageEntityStrike
import org.telegram.tgnet.tlrpc.TL_messageEntityTextUrl
import org.telegram.tgnet.tlrpc.TL_messageEntityUnderline
import org.telegram.tgnet.tlrpc.TL_messageEntityUrl
import org.telegram.tgnet.tlrpc.TL_messages_channelMessages
import org.telegram.tgnet.tlrpc.TL_messages_messages
import org.telegram.tgnet.tlrpc.TL_messages_messagesSlice
import org.telegram.tgnet.tlrpc.User
import org.telegram.tgnet.tlrpc.Vector
import org.telegram.tgnet.tlrpc.messages_Messages
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.EmojiThemes
import org.telegram.ui.Components.AnimatedEmojiDrawable
import org.telegram.ui.Components.AnimatedEmojiSpan
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.Bulletin
import org.telegram.ui.Components.ChatThemeBottomSheet.ChatThemeItem
import org.telegram.ui.Components.Reactions.ReactionsEffectOverlay
import org.telegram.ui.Components.Reactions.ReactionsUtils
import org.telegram.ui.Components.StickerSetBulletinLayout
import org.telegram.ui.Components.StickersArchiveAlert
import org.telegram.ui.Components.TextStyleSpan
import org.telegram.ui.Components.TextStyleSpan.TextStyleRun
import org.telegram.ui.Components.URLSpanReplacement
import org.telegram.ui.Components.URLSpanUserMention
import org.telegram.ui.LaunchActivity
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class MediaDataController(num: Int) : BaseController(num) {
	private var menuBotsUpdateHash = 0L

	var attachMenuBots: TL_attachMenuBots = TL_attachMenuBots()
		private set

	private var isLoadingMenuBots = false
	private var menuBotsUpdateDate = 0
	private var reactionsUpdateHash = 0
	private val reactionsList = mutableListOf<TL_availableReaction>()
	val enabledReactionsList = mutableListOf<TL_availableReaction>()

	@JvmField
	val reactionsMap = mutableMapOf<String, TL_availableReaction>()

	var doubleTapReaction: String? = null
		get() {
			if (field != null) {
				return field
			}

			if (getReactionsList().isNotEmpty()) {
				val savedReaction = MessagesController.getEmojiSettings(currentAccount).getString("reaction_on_double_tap", null)

				if (savedReaction != null && (reactionsMap[savedReaction] != null || savedReaction.startsWith("animated_"))) {
					field = savedReaction
					return field
				}

				return getReactionsList().firstOrNull()?.reaction
			}

			return null
		}
		set(reaction) {
			MessagesController.getEmojiSettings(currentAccount).edit().putString("reaction_on_double_tap", reaction).commit()
			field = reaction
		}

	private var isLoadingReactions = false
	private var reactionsUpdateDate = 0
	private var reactionsCacheGenerated = false

	var premiumPromo: TL_help_premiumPromo? = null
		private set

	private var isLoadingPremiumPromo = false
	private var premiumPromoUpdateDate = 0

	private val stickerSets = arrayOf(mutableListOf<TL_messages_stickerSet>(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
	private val stickersByIds = arrayOf(LongSparseArray<TLRPC.Document>(), LongSparseArray<TLRPC.Document>(), LongSparseArray<TLRPC.Document>(), LongSparseArray<TLRPC.Document>(), LongSparseArray<TLRPC.Document>(), LongSparseArray<TLRPC.Document>())
	private val stickerSetsById = LongSparseArray<TL_messages_stickerSet>()
	private val installedStickerSetsById = LongSparseArray<TL_messages_stickerSet>()
	private val installedForceStickerSetsById = mutableListOf<Long>()
	private val uninstalledForceStickerSetsById = mutableListOf<Long>()
	private val groupStickerSets = LongSparseArray<TL_messages_stickerSet>()
	private val stickerSetsByName = ConcurrentHashMap<String, TL_messages_stickerSet>(100, 1.0f, 1)
	private var stickerSetDefaultStatuses: TL_messages_stickerSet? = null
	private val diceStickerSetsByEmoji = mutableMapOf<String, TL_messages_stickerSet>()
	private val diceEmojiStickerSetsById = LongSparseArray<String>()
	private val loadingDiceStickerSets = mutableSetOf<String>()
	private val removingStickerSetsUndos = LongSparseArray<Runnable>()
	private val scheduledLoadStickers = arrayOfNulls<Runnable>(7)
	private val loadingStickers = BooleanArray(7)
	private val stickersLoaded = BooleanArray(7)
	private val loadHash = LongArray(7)
	private val loadDate = IntArray(7)

	@JvmField
	val ringtoneUploaderHashMap = mutableMapOf<String, RingtoneUploader>()

	private val verifyingMessages = mutableMapOf<String, MutableList<Message>>()
	private val archivedStickersCount = IntArray(7)

	private var stickersByEmoji = LongSparseArray<String>()

	var allStickers = mutableMapOf<String, MutableList<TLRPC.Document>>()
		private set

	private var allStickersFeatured = mutableMapOf<String, MutableList<TLRPC.Document>>()

	private val recentStickers = arrayOf(mutableListOf<TLRPC.Document>(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf(), mutableListOf())
	private val loadingRecentStickers = BooleanArray(9)
	private val recentStickersLoaded = BooleanArray(9)

	private var recentGifs = mutableListOf<TLRPC.Document>()
	private var loadingRecentGifs = false
	private var recentGifsLoaded = false

	private var loadingPremiumGiftStickers = false
	private var loadingGenericAnimations = false

	private val loadFeaturedHash = LongArray(2)
	private val loadFeaturedDate = IntArray(2)

	@JvmField
	var loadFeaturedPremium: Boolean = false

	private val featuredStickerSets = arrayOf(mutableListOf<StickerSetCovered>(), mutableListOf())
	private val featuredStickerSetsById = arrayOf(LongSparseArray<StickerSetCovered>(), LongSparseArray<StickerSetCovered>())
	private val unreadStickerSets = arrayOf(mutableListOf<Long>(), mutableListOf())
	private val readingStickerSets = arrayOf(mutableListOf<Long>(), mutableListOf())
	private val loadingFeaturedStickers = BooleanArray(2)
	private val featuredStickersLoaded = BooleanArray(2)
	private var greetingsSticker: TLRPC.Document? = null

	@JvmField
	val ringtoneDataStore: RingtoneDataStore

	@JvmField
	val defaultEmojiThemes = mutableListOf<ChatThemeItem>()

	@JvmField
	val premiumPreviewStickers = mutableListOf<TLRPC.Document>()

	private var previewStickersLoading: Boolean = false

	private val emojiStatusesHash = LongArray(2)
	private val emojiStatuses = arrayOfNulls<ArrayList<EmojiStatus>?>(2)
	private val emojiStatusesFetchDate = arrayOfNulls<Long>(2)
	private val emojiStatusesFromCacheFetched = BooleanArray(2)
	private val emojiStatusesFetching = BooleanArray(2)

	fun cleanup() {
		for (a in recentStickers.indices) {
			recentStickers[a].clear()
			loadingRecentStickers[a] = false
			recentStickersLoaded[a] = false
		}

		for (a in 0..3) {
			loadHash[a] = 0
			loadDate[a] = 0
			stickerSets[a].clear()
			loadingStickers[a] = false
			stickersLoaded[a] = false
		}

		loadingPinnedMessages.clear()

		loadFeaturedDate[0] = 0
		loadFeaturedHash[0] = 0
		loadFeaturedDate[1] = 0
		loadFeaturedHash[1] = 0

		allStickers.clear()
		allStickersFeatured.clear()
		stickersByEmoji.clear()
		featuredStickerSetsById[0].clear()
		featuredStickerSets[0].clear()
		featuredStickerSetsById[1].clear()
		featuredStickerSets[1].clear()
		unreadStickerSets[0].clear()
		unreadStickerSets[1].clear()
		recentGifs.clear()
		stickerSetsById.clear()
		installedStickerSetsById.clear()
		stickerSetsByName.clear()
		diceStickerSetsByEmoji.clear()
		diceEmojiStickerSetsById.clear()
		loadingDiceStickerSets.clear()
		loadingFeaturedStickers[0] = false
		featuredStickersLoaded[0] = false
		loadingFeaturedStickers[1] = false
		featuredStickersLoaded[1] = false
		loadingRecentGifs = false
		recentGifsLoaded = false

		currentFetchingEmoji.clear()

		if (Build.VERSION.SDK_INT >= 25) {
			Utilities.globalQueue.postRunnable {
				try {
					ShortcutManagerCompat.removeAllDynamicShortcuts(ApplicationLoader.applicationContext)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		verifyingMessages.clear()

		loading = false
		loaded = false

		hints.clear()
		inlineBots.clear()

		AndroidUtilities.runOnUIThread {
			notificationCenter.postNotificationName(NotificationCenter.reloadHints)
			notificationCenter.postNotificationName(NotificationCenter.reloadInlineHints)
		}

		drafts.clear()
		draftMessages.clear()
		draftPreferences?.edit()?.clear()?.commit()

		botInfos.clear()
		botKeyboards.clear()
		botKeyboardsByMids.clear()
	}

	fun areStickersLoaded(type: Int): Boolean {
		return stickersLoaded[type]
	}

	fun checkStickers(type: Int) {
		if (!loadingStickers[type] && (!stickersLoaded[type] || abs((System.currentTimeMillis() / 1000 - loadDate[type]).toDouble()) >= 60 * 60)) {
			loadStickers(type, cache = true, useHash = false, retry = true)
		}
	}

	fun checkReactions() {
		if (!isLoadingReactions && abs((System.currentTimeMillis() / 1000 - reactionsUpdateDate).toDouble()) >= 60 * 60) {
			loadReactions(cache = true, force = false)
		}
	}

	private fun checkMenuBots() {
		if (!isLoadingMenuBots && abs((System.currentTimeMillis() / 1000 - menuBotsUpdateDate).toDouble()) >= 60 * 60) {
			loadAttachMenuBots(cache = true, force = false)
		}
	}

	private fun checkPremiumPromo() {
		if (!isLoadingPremiumPromo && abs((System.currentTimeMillis() / 1000 - premiumPromoUpdateDate).toDouble()) >= 60 * 60) {
			loadPremiumPromo(true)
		}
	}

	fun loadAttachMenuBots(cache: Boolean, force: Boolean) {
		isLoadingMenuBots = true

		if (cache) {
			messagesStorage.storageQueue.postRunnable {
				var c: SQLiteCursor? = null
				var hash: Long = 0
				var date = 0
				var bots: TL_attachMenuBots? = null

				try {
					c = messagesStorage.database.queryFinalized("SELECT data, hash, date FROM attach_menu_bots")

					if (c.next()) {
						val data = c.byteBufferValue(0)

						if (data != null) {
							val attachMenuBots = TL_attachMenuBots.TLdeserialize(data, data.readInt32(false), true)

							if (attachMenuBots is TL_attachMenuBots) {
								bots = attachMenuBots
							}

							data.reuse()
						}

						hash = c.longValue(1)
						date = c.intValue(2)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
				finally {
					c?.dispose()
				}

				processLoadedMenuBots(bots, hash, date, true)
			}
		}
		else {
			val req = TL_messages_getAttachMenuBots()
			req.hash = if (force) 0 else menuBotsUpdateHash

			connectionsManager.sendRequest(req) { response, _ ->
				val date = (System.currentTimeMillis() / 1000).toInt()

				if (response is TL_attachMenuBotsNotModified) {
					processLoadedMenuBots(null, 0, date, false)
				}
				else if (response is TL_attachMenuBots) {
					processLoadedMenuBots(response, response.hash, date, false)
				}
			}
		}
	}

	fun processLoadedMenuBots(bots: TL_attachMenuBots?, hash: Long, date: Int, cache: Boolean) {
		if (bots != null && date != 0) {
			attachMenuBots = bots
			menuBotsUpdateHash = hash
		}

		menuBotsUpdateDate = date

		if (bots != null) {
			messagesController.putUsers(bots.users, cache)

			AndroidUtilities.runOnUIThread {
				NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.attachMenuBotsDidLoad)
			}
		}

		if (!cache) {
			putMenuBotsToCache(bots, hash, date)
		}
		else if (abs((System.currentTimeMillis() / 1000 - date).toDouble()) >= 60 * 60) {
			loadAttachMenuBots(cache = false, force = true)
		}
	}

	private fun putMenuBotsToCache(bots: TL_attachMenuBots?, hash: Long, date: Int) {
		messagesStorage.storageQueue.postRunnable {
			try {
				if (bots != null) {
					messagesStorage.database.executeFast("DELETE FROM attach_menu_bots").stepThis().dispose()

					val state = messagesStorage.database.executeFast("REPLACE INTO attach_menu_bots VALUES(?, ?, ?)")
					state.requery()

					val data = NativeByteBuffer(bots.objectSize)

					bots.serializeToStream(data)

					state.bindByteBuffer(1, data)
					state.bindLong(2, hash)
					state.bindInteger(3, date)
					state.step()

					data.reuse()

					state.dispose()
				}
				else {
					val state = messagesStorage.database.executeFast("UPDATE attach_menu_bots SET date = ?")
					state.requery()
					state.bindLong(1, date.toLong())
					state.step()
					state.dispose()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun loadPremiumPromo(cache: Boolean) {
		isLoadingPremiumPromo = true

		if (cache) {
			messagesStorage.storageQueue.postRunnable {
				var c: SQLiteCursor? = null
				var date = 0
				var premiumPromo: TL_help_premiumPromo? = null

				try {
					c = messagesStorage.database.queryFinalized("SELECT data, date FROM premium_promo")

					if (c.next()) {
						val data = c.byteBufferValue(0)

						if (data != null) {
							premiumPromo = TL_help_premiumPromo.TLdeserialize(data, data.readInt32(false), true)
							data.reuse()
						}

						date = c.intValue(1)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
				finally {
					c?.dispose()
				}

				if (premiumPromo != null) {
					processLoadedPremiumPromo(premiumPromo, date, true)
				}
			}
		}
		else {
			val req = TL_help_getPremiumPromo()

			connectionsManager.sendRequest(req) { response, _ ->
				val date = (System.currentTimeMillis() / 1000).toInt()

				if (response is TL_help_premiumPromo) {
					processLoadedPremiumPromo(response, date, false)
				}
			}
		}
	}

	private fun processLoadedPremiumPromo(premiumPromo: TL_help_premiumPromo, date: Int, cache: Boolean) {
		this.premiumPromo = premiumPromo
		premiumPromoUpdateDate = date
		messagesController.putUsers(premiumPromo.users, cache)

		AndroidUtilities.runOnUIThread {
			notificationCenter.postNotificationName(NotificationCenter.premiumPromoUpdated)
		}

		if (!cache) {
			putPremiumPromoToCache(premiumPromo, date)
		}
		else if (abs((System.currentTimeMillis() / 1000 - date).toDouble()) >= 60 * 60 * 24 || BuildConfig.DEBUG_PRIVATE_VERSION) {
			loadPremiumPromo(false)
		}
	}

	private fun putPremiumPromoToCache(premiumPromo: TL_help_premiumPromo?, date: Int) {
		messagesStorage.storageQueue.postRunnable {
			try {
				if (premiumPromo != null) {
					messagesStorage.database.executeFast("DELETE FROM premium_promo").stepThis().dispose()

					val state = messagesStorage.database.executeFast("REPLACE INTO premium_promo VALUES(?, ?)")
					state.requery()

					val data = NativeByteBuffer(premiumPromo.objectSize)

					premiumPromo.serializeToStream(data)

					state.bindByteBuffer(1, data)
					state.bindInteger(2, date)
					state.step()

					data.reuse()

					state.dispose()
				}
				else {
					val state = messagesStorage.database.executeFast("UPDATE premium_promo SET date = ?")
					state.requery()
					state.bindInteger(1, date)
					state.step()
					state.dispose()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun getReactionsList(): List<TL_availableReaction> {
		return reactionsList
	}

	fun loadReactions(cache: Boolean, force: Boolean) {
		isLoadingReactions = true

		if (cache) {
			messagesStorage.storageQueue.postRunnable {
				var c: SQLiteCursor? = null
				var hash = 0
				var date = 0
				var reactions: MutableList<TL_availableReaction>? = null

				try {
					c = messagesStorage.database.queryFinalized("SELECT data, hash, date FROM reactions")

					if (c.next()) {
						val data = c.byteBufferValue(0)

						if (data != null) {
							val count = data.readInt32(false)

							reactions = mutableListOf()

							for (i in 0 until count) {
								val react = TL_availableReaction.TLdeserialize(data, data.readInt32(false), true)

								if (react != null) {
									reactions.add(react)
								}
							}

							data.reuse()
						}

						hash = c.intValue(1)
						date = c.intValue(2)
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
				finally {
					c?.dispose()
				}

				processLoadedReactions(reactions, hash, date, true)
			}
		}
		else {
			val req = TL_messages_getAvailableReactions()
			req.hash = if (force) 0 else reactionsUpdateHash

			connectionsManager.sendRequest(req) { response, _ ->
				val date = (System.currentTimeMillis() / 1000).toInt()

				if (response is TL_messages_availableReactionsNotModified) {
					processLoadedReactions(null, 0, date, false)
				}
				else if (response is TL_messages_availableReactions) {
					processLoadedReactions(response.reactions, response.hash, date, false)
				}
			}
		}
	}

	fun processLoadedReactions(reactions: List<TL_availableReaction>?, hash: Int, date: Int, cache: Boolean) {
		if (reactions != null && date != 0) {
			reactionsList.clear()
			reactionsMap.clear()
			enabledReactionsList.clear()
			reactionsList.addAll(reactions)

			for (i in reactionsList.indices) {
				reactionsList[i].positionInList = i
				reactionsMap[reactionsList[i].reaction!!] = reactionsList[i]

				if (!reactionsList[i].inactive) {
					enabledReactionsList.add(reactionsList[i])
				}
			}

			reactionsUpdateHash = hash
		}

		reactionsUpdateDate = date

		if (reactions != null) {
			AndroidUtilities.runOnUIThread {
				preloadReactions()
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.reactionsDidLoad)
			}
		}

		isLoadingReactions = false

		if (!cache) {
			putReactionsToCache(reactions, hash, date)
		}
		else if (abs((System.currentTimeMillis() / 1000 - date).toDouble()) >= 60 * 60) {
			loadReactions(cache = false, force = true)
		}
	}

	fun preloadReactions() {
		if (reactionsList.isEmpty() || reactionsCacheGenerated) {
			return
		}

		reactionsCacheGenerated = true

		val arrayList = reactionsList.toList()

		for (reaction in arrayList) {
			val size = ReactionsEffectOverlay.sizeForBigReaction()
			preloadImage(ImageLocation.getForDocument(reaction.around_animation), ReactionsEffectOverlay.filterForAroundAnimation)
			preloadImage(ImageLocation.getForDocument(reaction.effect_animation), size.toString() + "_" + size)
			preloadImage(ImageLocation.getForDocument(reaction.activate_animation), null)
			preloadImage(ImageLocation.getForDocument(reaction.appear_animation), ReactionsUtils.APPEAR_ANIMATION_FILTER)
			preloadImage(ImageLocation.getForDocument(reaction.center_icon), null)
		}
	}

	private fun preloadImage(location: ImageLocation?, filter: String?) {
		val imageReceiver = ImageReceiver()

		imageReceiver.setDelegate(object : ImageReceiverDelegate {
			override fun onAnimationReady(imageReceiver: ImageReceiver) {
				// unused
			}

			override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
				if (set) {
					val rLottieDrawable = imageReceiver.lottieAnimation

					if (rLottieDrawable != null) {
						rLottieDrawable.checkCache(Runnable {
							imageReceiver.clearImage()
							imageReceiver.setDelegate(null)
						})
					}
					else {
						imageReceiver.clearImage()
						imageReceiver.setDelegate(null)
					}
				}
			}
		})

		imageReceiver.fileLoadingPriority = FileLoader.PRIORITY_LOW
		imageReceiver.uniqueKeyPrefix = "preload"
		imageReceiver.setImage(location, filter, null, null, 0, FileLoader.PRELOAD_CACHE_TYPE)
	}

	private fun putReactionsToCache(reactions: List<TL_availableReaction>?, hash: Int, date: Int) {
		val reactionsFinal = reactions?.toList()

		messagesStorage.storageQueue.postRunnable {
			try {
				if (reactionsFinal != null) {
					messagesStorage.database.executeFast("DELETE FROM reactions").stepThis().dispose()

					val state = messagesStorage.database.executeFast("REPLACE INTO reactions VALUES(?, ?, ?)")
					state.requery()

					var size = 4 // Integer.BYTES

					for (a in reactionsFinal.indices) {
						size += reactionsFinal[a].objectSize
					}

					val data = NativeByteBuffer(size)
					data.writeInt32(reactionsFinal.size)

					for (a in reactionsFinal.indices) {
						reactionsFinal[a].serializeToStream(data)
					}

					state.bindByteBuffer(1, data)
					state.bindInteger(2, hash)
					state.bindInteger(3, date)
					state.step()

					data.reuse()

					state.dispose()
				}
				else {
					val state = messagesStorage.database.executeFast("UPDATE reactions SET date = ?")
					state.requery()
					state.bindLong(1, date.toLong())
					state.step()
					state.dispose()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun checkFeaturedStickers() {
		if (!loadingFeaturedStickers[0] && (!featuredStickersLoaded[0] || abs((System.currentTimeMillis() / 1000 - loadFeaturedDate[0]).toDouble()) >= 60 * 60)) {
			loadFeaturedStickers(emoji = false, cache = true, force = false, retry = true)
		}
	}

	fun checkFeaturedEmoji() {
		if (!loadingFeaturedStickers[1] && (!featuredStickersLoaded[1] || abs((System.currentTimeMillis() / 1000 - loadFeaturedDate[1]).toDouble()) >= 60 * 60)) {
			loadFeaturedStickers(emoji = true, cache = true, force = false, retry = true)
		}
	}

	fun getRecentStickers(type: Int): List<TLRPC.Document> {
		val arrayList = recentStickers[type]

		if (type == TYPE_PREMIUM_STICKERS) {
			return recentStickers[type].toList()
		}

		return arrayList.subList(0, min(arrayList.size.toDouble(), 20.0).toInt()).toList()
	}

	fun getRecentStickersNoCopy(type: Int): List<TLRPC.Document> {
		return recentStickers[type]
	}

	fun isStickerInFavorites(document: TLRPC.Document?): Boolean {
		if (document == null) {
			return false
		}

		for (d in recentStickers[TYPE_FAVE]) {
			if (d.id == document.id && d.dc_id == document.dc_id) {
				return true
			}
		}

		return false
	}

	fun clearRecentStickers() {
		val req = TL_messages_clearRecentStickers()

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is TL_boolTrue) {
					messagesStorage.storageQueue.postRunnable {
						try {
							messagesStorage.database.executeFast("DELETE FROM web_recent_v3 WHERE type = " + 3).stepThis().dispose()
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}

					recentStickers[TYPE_IMAGE].clear()

					NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recentDocumentsDidLoad, false, TYPE_IMAGE)
				}
			}
		}
	}

	fun addRecentSticker(type: Int, parentObject: Any?, document: TLRPC.Document, date: Int, remove: Boolean) {
		if (type == TYPE_GREETINGS || !MessageObject.isStickerDocument(document) && !MessageObject.isAnimatedStickerDocument(document, true)) {
			return
		}

		var found = false

		for (a in recentStickers[type].indices) {
			val image = recentStickers[type][a]

			if (image.id == document.id) {
				recentStickers[type].removeAt(a)

				if (!remove) {
					recentStickers[type].add(0, image)
				}

				found = true

				break
			}
		}

		if (!found && !remove) {
			recentStickers[type].add(0, document)
		}

		val maxCount: Int

		if (type == TYPE_FAVE) {
			if (remove) {
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_STICKER, document, StickerSetBulletinLayout.TYPE_REMOVED_FROM_FAVORITES)
			}
			else {
				val replace = recentStickers[type].size > messagesController.maxFaveStickersCount
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_STICKER, document, if (replace) StickerSetBulletinLayout.TYPE_REPLACED_TO_FAVORITES else StickerSetBulletinLayout.TYPE_ADDED_TO_FAVORITES)
			}

			val req = TL_messages_faveSticker()
			req.id = TL_inputDocument()
			req.id.id = document.id
			req.id.access_hash = document.access_hash
			req.id.file_reference = document.file_reference

			if (req.id.file_reference == null) {
				req.id.file_reference = ByteArray(0)
			}

			req.unfave = remove

			connectionsManager.sendRequest(req) { _, error ->
				if (error != null && FileRefController.isFileRefError(error.text) && parentObject != null) {
					fileRefController.requestReference(parentObject, req)
				}
				else {
					AndroidUtilities.runOnUIThread {
						mediaDataController.loadRecents(TYPE_FAVE, gif = false, cache = false, force = true)
					}
				}
			}

			maxCount = messagesController.maxFaveStickersCount
		}
		else {
			if (type == TYPE_IMAGE && remove) {
				NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_STICKER, document, StickerSetBulletinLayout.TYPE_REMOVED_FROM_RECENT)

				val req = TL_messages_saveRecentSticker()
				req.id = TL_inputDocument()
				req.id.id = document.id
				req.id.access_hash = document.access_hash
				req.id.file_reference = document.file_reference

				if (req.id.file_reference == null) {
					req.id.file_reference = ByteArray(0)
				}

				req.unsave = true

				connectionsManager.sendRequest(req) { _, error ->
					if (error != null && FileRefController.isFileRefError(error.text) && parentObject != null) {
						fileRefController.requestReference(parentObject, req)
					}
				}
			}

			maxCount = messagesController.maxRecentStickersCount
		}

		if (recentStickers[type].size > maxCount || remove) {
			val old = if (remove) document else recentStickers[type].removeAt(recentStickers[type].size - 1)

			messagesStorage.storageQueue.postRunnable {
				val cacheType = when (type) {
					TYPE_IMAGE -> 3
					TYPE_MASK -> 4
					TYPE_EMOJIPACKS -> 7
					else -> 5
				}

				try {
					messagesStorage.database.executeFast("DELETE FROM web_recent_v3 WHERE id = '" + old.id + "' AND type = " + cacheType).stepThis().dispose()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		if (!remove) {
			processLoadedRecentDocuments(type, listOf(document), false, date, false)
		}

		if (type == TYPE_FAVE || type == TYPE_IMAGE && remove) {
			notificationCenter.postNotificationName(NotificationCenter.recentDocumentsDidLoad, false, type)
		}
	}

	fun getRecentGifs(): List<TLRPC.Document> {
		return recentGifs.toList()
	}

	fun removeRecentGif(document: TLRPC.Document) {
		var i = 0
		val n = recentGifs.size

		while (i < n) {
			if (recentGifs[i].id == document.id) {
				recentGifs.removeAt(i)
				break
			}

			i++
		}

		val req = TL_messages_saveGif()
		req.id = TL_inputDocument()
		req.id.id = document.id
		req.id.access_hash = document.access_hash
		req.id.file_reference = document.file_reference

		if (req.id.file_reference == null) {
			req.id.file_reference = ByteArray(0)
		}

		req.unsave = true

		connectionsManager.sendRequest(req) { _, error ->
			if (error != null && FileRefController.isFileRefError(error.text)) {
				fileRefController.requestReference("gif", req)
			}
		}

		messagesStorage.storageQueue.postRunnable {
			try {
				messagesStorage.database.executeFast("DELETE FROM web_recent_v3 WHERE id = '" + document.id + "' AND type = 2").stepThis().dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun hasRecentGif(document: TLRPC.Document): Boolean {
		for (a in recentGifs.indices) {
			val image = recentGifs[a]

			if (image.id == document.id) {
				recentGifs.removeAt(a)
				recentGifs.add(0, image)
				return true
			}
		}

		return false
	}

	fun addRecentGif(document: TLRPC.Document?, date: Int, showReplaceBulletin: Boolean) {
		if (document == null) {
			return
		}

		var found = false

		for (a in recentGifs.indices) {
			val image = recentGifs[a]

			if (image.id == document.id) {
				recentGifs.removeAt(a)
				recentGifs.add(0, image)
				found = true
				break
			}
		}

		if (!found) {
			recentGifs.add(0, document)
		}

		if ((recentGifs.size > messagesController.savedGifsLimitDefault && !UserConfig.getInstance(currentAccount).isPremium) || recentGifs.size > messagesController.savedGifsLimitPremium) {
			val old = recentGifs.removeAt(recentGifs.size - 1)

			messagesStorage.storageQueue.postRunnable {
				try {
					messagesStorage.database.executeFast("DELETE FROM web_recent_v3 WHERE id = '" + old.id + "' AND type = 2").stepThis().dispose()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			if (showReplaceBulletin) {
				AndroidUtilities.runOnUIThread {
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_STICKER, document, StickerSetBulletinLayout.TYPE_REPLACED_TO_FAVORITES_GIFS)
				}
			}
		}

		processLoadedRecentDocuments(0, listOf(document), true, date, false)
	}

	fun isLoadingStickers(type: Int): Boolean {
		return loadingStickers[type]
	}

	fun replaceStickerSet(set: TL_messages_stickerSet) {
		var existingSet = stickerSetsById[set.set.id]
		val emoji = diceEmojiStickerSetsById[set.set.id]

		if (emoji != null) {
			diceStickerSetsByEmoji[emoji] = set
			putDiceStickersToCache(emoji, set, (System.currentTimeMillis() / 1000).toInt())
		}

		var isGroupSet = false

		if (existingSet == null) {
			existingSet = stickerSetsByName[set.set.short_name]
		}

		if (existingSet == null) {
			existingSet = groupStickerSets[set.set.id]

			if (existingSet != null) {
				isGroupSet = true
			}
		}

		if (existingSet == null) {
			return
		}

		var changed = false

		if ("AnimatedEmojies" == set.set.short_name) {
			changed = true

			existingSet.documents = set.documents
			existingSet.packs = set.packs
			existingSet.set = set.set

			AndroidUtilities.runOnUIThread {
				val stickersById = getStickerByIds(TYPE_EMOJI)

				for (b in set.documents.indices) {
					val document = set.documents[b]
					stickersById.put(document.id, document)
				}
			}
		}
		else {
			val documents = LongSparseArray<TLRPC.Document>()

			for (document in set.documents) {
				documents.put(document.id, document)
			}

			var a = 0
			val size = existingSet.documents.size

			while (a < size) {
				val document = existingSet.documents[a]
				val newDocument = documents[document.id]

				if (newDocument != null) {
					existingSet.documents[a] = newDocument
					changed = true
				}

				a++
			}
		}

		if (changed) {
			if (isGroupSet) {
				putSetToCache(existingSet)
			}
			else {
				var type = TYPE_IMAGE

				if (set.set.masks) {
					type = TYPE_MASK
				}
				else if (set.set.emojis) {
					type = TYPE_EMOJIPACKS
				}

				putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type])

				if ("AnimatedEmojies" == set.set.short_name) {
					type = TYPE_EMOJI
					putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type])
				}
			}
		}
	}

	fun getStickerSetByName(name: String?): TL_messages_stickerSet? {
		return stickerSetsByName[name]
	}

	fun getStickerSetByEmojiOrName(emoji: String?): TL_messages_stickerSet? {
		return diceStickerSetsByEmoji[emoji]
	}

	fun getStickerSetById(id: Long): TL_messages_stickerSet? {
		return stickerSetsById[id]
	}

	fun getGroupStickerSetById(stickerSet: StickerSet?): TL_messages_stickerSet? {
		if (stickerSet == null) {
			return null
		}

		var set = stickerSetsById[stickerSet.id]

		if (set == null) {
			set = groupStickerSets[stickerSet.id]

			if (set?.set == null) {
				loadGroupStickerSet(stickerSet, true)
			}
			else if (set.set.hash != stickerSet.hash) {
				loadGroupStickerSet(stickerSet, false)
			}
		}

		return set
	}

	fun putGroupStickerSet(stickerSet: TL_messages_stickerSet) {
		groupStickerSets.put(stickerSet.set.id, stickerSet)
	}

	fun getStickerSet(inputStickerSet: InputStickerSet?, cacheOnly: Boolean): TL_messages_stickerSet? {
		return getStickerSet(inputStickerSet, cacheOnly, null)
	}

	fun getStickerSet(inputStickerSet: InputStickerSet?, cacheOnly: Boolean, onNotFound: Runnable?): TL_messages_stickerSet? {
		if (inputStickerSet == null) {
			return null
		}

		if (inputStickerSet is TL_inputStickerSetID && stickerSetsById.containsKey(inputStickerSet.id)) {
			return stickerSetsById[inputStickerSet.id]
		}
		else if (inputStickerSet is TL_inputStickerSetShortName && inputStickerSet.short_name != null && stickerSetsByName.containsKey(inputStickerSet.short_name.lowercase())) {
			return stickerSetsByName[inputStickerSet.short_name.lowercase()]
		}
		else if (inputStickerSet is TL_inputStickerSetEmojiDefaultStatuses && stickerSetDefaultStatuses != null) {
			return stickerSetDefaultStatuses
		}

		if (cacheOnly) {
			return null
		}

		val req = TL_messages_getStickerSet()
		req.stickerset = inputStickerSet

		connectionsManager.sendRequest(req) { response, _ ->
			if (response is TL_messages_stickerSet) {
				AndroidUtilities.runOnUIThread {
					if (response.set == null) {
						return@runOnUIThread
					}

					stickerSetsById.put(response.set.id, response)
					stickerSetsByName[response.set.short_name.lowercase()] = response

					if (inputStickerSet is TL_inputStickerSetEmojiDefaultStatuses) {
						stickerSetDefaultStatuses = response
					}

					notificationCenter.postNotificationName(NotificationCenter.groupStickersDidLoad, response.set.id, response)
				}
			}
			else {
				onNotFound?.run()
			}
		}

		return null
	}

	private fun loadGroupStickerSet(stickerSet: StickerSet, cache: Boolean) {
		if (cache) {
			messagesStorage.storageQueue.postRunnable {
				try {
					val set: TL_messages_stickerSet?
					val cursor = messagesStorage.database.queryFinalized("SELECT document FROM web_recent_v3 WHERE id = 's_" + stickerSet.id + "'")

					if (cursor.next() && !cursor.isNull(0)) {
						val data = cursor.byteBufferValue(0)

						if (data != null) {
							set = TL_messages_stickerSet.TLdeserialize(data, data.readInt32(false), false)
							data.reuse()
						}
						else {
							set = null
						}
					}
					else {
						set = null
					}

					cursor.dispose()

					if (set?.set == null || set.set.hash != stickerSet.hash) {
						loadGroupStickerSet(stickerSet, false)
					}

					if (set?.set != null) {
						AndroidUtilities.runOnUIThread {
							groupStickerSets.put(set.set.id, set)
							notificationCenter.postNotificationName(NotificationCenter.groupStickersDidLoad, set.set.id, set)
						}
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}
		}
		else {
			val req = TL_messages_getStickerSet()
			req.stickerset = TL_inputStickerSetID()
			req.stickerset.id = stickerSet.id
			req.stickerset.access_hash = stickerSet.access_hash

			connectionsManager.sendRequest(req) { response, _ ->
				if (response is TL_messages_stickerSet) {
					AndroidUtilities.runOnUIThread {
						groupStickerSets.put(response.set.id, response)
						notificationCenter.postNotificationName(NotificationCenter.groupStickersDidLoad, response.set.id, response)
					}
				}
			}
		}
	}

	private fun putSetToCache(set: TL_messages_stickerSet) {
		messagesStorage.storageQueue.postRunnable {
			try {
				val database = messagesStorage.database

				val state = database.executeFast("REPLACE INTO web_recent_v3 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
				state.requery()
				state.bindString(1, "s_" + set.set.id)
				state.bindInteger(2, 6)
				state.bindString(3, "")
				state.bindString(4, "")
				state.bindString(5, "")
				state.bindInteger(6, 0)
				state.bindInteger(7, 0)
				state.bindInteger(8, 0)
				state.bindInteger(9, 0)

				val data = NativeByteBuffer(set.objectSize)

				set.serializeToStream(data)

				state.bindByteBuffer(10, data)
				state.step()

				data.reuse()

				state.dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun getEmojiAnimatedSticker(message: CharSequence?): TLRPC.Document? {
		if (message == null) {
			return null
		}

		val emoji = message.toString().replace("\uFE0F", "")
		val arrayList = getStickerSets(TYPE_EMOJI)

		for (set in arrayList) {
			for (pack in set.packs) {
				if (pack.documents.isNotEmpty() && TextUtils.equals(pack.emoticon, emoji)) {
					val stickerByIds = getStickerByIds(TYPE_EMOJI)
					return stickerByIds[pack.documents[0]]
				}
			}
		}

		return null
	}

	fun canAddStickerToFavorites(): Boolean {
		return !stickersLoaded[0] || stickerSets[0].size >= 5 || recentStickers[TYPE_FAVE].isNotEmpty()
	}

	fun getStickerSets(type: Int): MutableList<TL_messages_stickerSet> {
		return if (type == TYPE_FEATURED) {
			stickerSets[2]
		}
		else {
			stickerSets[type]
		}
	}

	private fun getStickerByIds(type: Int): LongSparseArray<TLRPC.Document> {
		return stickersByIds[type]
	}

	fun getFeaturedStickerSets(): List<StickerSetCovered> {
		return featuredStickerSets[0]
	}

	val featuredEmojiSets: List<StickerSetCovered>
		get() = featuredStickerSets[1]

	fun getUnreadStickerSets(): List<Long> {
		return unreadStickerSets[0]
	}

	val unreadEmojiSets: List<Long>
		get() = unreadStickerSets[1]

	fun areAllTrendingStickerSetsUnread(emoji: Boolean): Boolean {
		var a = 0
		val n = featuredStickerSets[if (emoji) 1 else 0].size

		while (a < n) {
			val pack = featuredStickerSets[if (emoji) 1 else 0][a]

			if (isStickerPackInstalled(pack.set.id) || pack.covers.isEmpty() && pack.cover == null) {
				a++
				continue
			}

			if (!unreadStickerSets[if (emoji) 1 else 0].contains(pack.set.id)) {
				return false
			}

			a++
		}

		return true
	}

	fun isStickerPackInstalled(id: Long): Boolean {
		return isStickerPackInstalled(id, true)
	}

	fun isStickerPackInstalled(id: Long, countForced: Boolean): Boolean {
		return (installedStickerSetsById.indexOfKey(id) >= 0 || countForced && installedForceStickerSetsById.contains(id)) && (!countForced || !uninstalledForceStickerSetsById.contains(id))
	}

	fun isStickerPackUnread(emoji: Boolean, id: Long): Boolean {
		return unreadStickerSets[if (emoji) 1 else 0].contains(id)
	}

	fun isStickerPackInstalled(name: String?): Boolean {
		return stickerSetsByName.containsKey(name)
	}

	fun getEmojiForSticker(id: Long): String {
		return stickersByEmoji[id] ?: ""
	}

	fun loadRecents(type: Int, gif: Boolean, cache: Boolean, force: Boolean) {
		@Suppress("NAME_SHADOWING") var cache = cache

		if (gif) {
			if (loadingRecentGifs) {
				return
			}

			loadingRecentGifs = true

			if (recentGifsLoaded) {
				cache = false
			}
		}
		else {
			if (loadingRecentStickers[type]) {
				return
			}

			loadingRecentStickers[type] = true

			if (recentStickersLoaded[type]) {
				cache = false
			}
		}

		if (cache) {
			messagesStorage.storageQueue.postRunnable {
				try {
					val cacheType = if (gif) {
						2
					}
					else if (type == TYPE_IMAGE) {
						3
					}
					else if (type == TYPE_MASK) {
						4
					}
					else if (type == TYPE_GREETINGS) {
						6
					}
					else if (type == TYPE_EMOJIPACKS) {
						7
					}
					else if (type == TYPE_PREMIUM_STICKERS) {
						8
					}
					else {
						5
					}

					val cursor = messagesStorage.database.queryFinalized("SELECT document FROM web_recent_v3 WHERE type = $cacheType ORDER BY date DESC")
					val arrayList = mutableListOf<TLRPC.Document>()

					while (cursor.next()) {
						if (!cursor.isNull(0)) {
							val data = cursor.byteBufferValue(0)

							if (data != null) {
								val document = TLRPC.Document.TLdeserialize(data, data.readInt32(false), false)

								if (document != null) {
									arrayList.add(document)
								}

								data.reuse()
							}
						}
					}
					cursor.dispose()
					AndroidUtilities.runOnUIThread {
						if (gif) {
							recentGifs = arrayList
							loadingRecentGifs = false
							recentGifsLoaded = true
						}
						else {
							recentStickers[type] = arrayList
							loadingRecentStickers[type] = false
							recentStickersLoaded[type] = true
						}

						if (type == TYPE_GREETINGS) {
							preloadNextGreetingsSticker()
						}

						notificationCenter.postNotificationName(NotificationCenter.recentDocumentsDidLoad, gif, type)

						loadRecents(type, gif, cache = false, force = false)
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}
		}
		else {
			val preferences = MessagesController.getEmojiSettings(currentAccount)

			if (!force) {
				val lastLoadTime = if (gif) {
					preferences.getLong("lastGifLoadTime", 0)
				}
				else if (type == TYPE_IMAGE) {
					preferences.getLong("lastStickersLoadTime", 0)
				}
				else if (type == TYPE_MASK) {
					preferences.getLong("lastStickersLoadTimeMask", 0)
				}
				else if (type == TYPE_GREETINGS) {
					preferences.getLong("lastStickersLoadTimeGreet", 0)
				}
				else if (type == TYPE_EMOJIPACKS) {
					preferences.getLong("lastStickersLoadTimeEmojiPacks", 0)
				}
				else if (type == TYPE_PREMIUM_STICKERS) {
					preferences.getLong("lastStickersLoadTimePremiumStickers", 0)
				}
				else {
					preferences.getLong("lastStickersLoadTimeFavs", 0)
				}

				if (abs((System.currentTimeMillis() - lastLoadTime).toDouble()) < 60 * 60 * 1000) {
					if (gif) {
						loadingRecentGifs = false
					}
					else {
						loadingRecentStickers[type] = false
					}

					return
				}
			}

			if (gif) {
				val req = TL_messages_getSavedGifs()
				req.hash = calcDocumentsHash(recentGifs)

				connectionsManager.sendRequest(req) { response, _ ->
					var arrayList: List<TLRPC.Document>? = null

					if (response is TL_messages_savedGifs) {
						arrayList = response.gifs
					}

					processLoadedRecentDocuments(type, arrayList, true, 0, true)
				}
			}
			else {
				val request: TLObject

				when (type) {
					TYPE_FAVE -> {
						val req = TL_messages_getFavedStickers()
						req.hash = calcDocumentsHash(recentStickers[type])
						request = req
					}

					TYPE_GREETINGS -> {
						val req = TL_messages_getStickers()
						req.emoticon = "\uD83D\uDC4B" + Emoji.fixEmoji("⭐")
						req.hash = calcDocumentsHash(recentStickers[type])
						request = req
					}

					TYPE_PREMIUM_STICKERS -> {
						val req = TL_messages_getStickers()
						req.emoticon = "\uD83D\uDCC2" + Emoji.fixEmoji("⭐")
						req.hash = calcDocumentsHash(recentStickers[type])
						request = req
					}

					else -> {
						val req = TL_messages_getRecentStickers()
						req.hash = calcDocumentsHash(recentStickers[type])
						req.attached = type == TYPE_MASK
						request = req
					}
				}

				connectionsManager.sendRequest(request) { response, _ ->
					var arrayList: List<TLRPC.Document>? = null

					if (type == TYPE_GREETINGS || type == TYPE_PREMIUM_STICKERS) {
						if (response is TL_messages_stickers) {
							arrayList = response.stickers
						}
					}
					else if (type == TYPE_FAVE) {
						if (response is TL_messages_favedStickers) {
							arrayList = response.stickers
						}
					}
					else {
						if (response is TL_messages_recentStickers) {
							arrayList = response.stickers
						}
					}

					processLoadedRecentDocuments(type, arrayList, false, 0, true)
				}
			}
		}
	}

	private fun preloadNextGreetingsSticker() {
		if (recentStickers[TYPE_GREETINGS].isEmpty()) {
			return
		}

		greetingsSticker = recentStickers[TYPE_GREETINGS][Utilities.random.nextInt(recentStickers[TYPE_GREETINGS].size)]

		fileLoader.loadFile(ImageLocation.getForDocument(greetingsSticker), greetingsSticker, null, 0, 1)
	}

	fun getGreetingsSticker(): TLRPC.Document? {
		val result = greetingsSticker
		preloadNextGreetingsSticker()
		return result
	}

	fun processLoadedRecentDocuments(type: Int, documents: List<TLRPC.Document>?, gif: Boolean, date: Int, replace: Boolean) {
		if (documents != null) {
			messagesStorage.storageQueue.postRunnable {
				try {
					val database = messagesStorage.database

					val maxCount = if (gif) {
						messagesController.maxRecentGifsCount
					}
					else {
						when (type) {
							TYPE_GREETINGS, TYPE_PREMIUM_STICKERS -> 200
							TYPE_FAVE -> messagesController.maxFaveStickersCount
							else -> messagesController.maxRecentStickersCount
						}
					}

					database.beginTransaction()

					val state = database.executeFast("REPLACE INTO web_recent_v3 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
					val count = documents.size

					val cacheType = if (gif) {
						2
					}
					else if (type == TYPE_IMAGE) {
						3
					}
					else if (type == TYPE_MASK) {
						4
					}
					else if (type == TYPE_GREETINGS) {
						6
					}
					else if (type == TYPE_EMOJIPACKS) {
						7
					}
					else if (type == TYPE_PREMIUM_STICKERS) {
						8
					}
					else {
						5
					}

					if (replace) {
						database.executeFast("DELETE FROM web_recent_v3 WHERE type = $cacheType").stepThis().dispose()
					}

					for (a in 0 until count) {
						if (a == maxCount) {
							break
						}

						val document = documents[a]

						state.requery()
						state.bindString(1, "" + document.id)
						state.bindInteger(2, cacheType)
						state.bindString(3, "")
						state.bindString(4, "")
						state.bindString(5, "")
						state.bindInteger(6, 0)
						state.bindInteger(7, 0)
						state.bindInteger(8, 0)
						state.bindInteger(9, if (date != 0) date else count - a)

						val data = NativeByteBuffer(document.objectSize)

						document.serializeToStream(data)

						state.bindByteBuffer(10, data)
						state.step()

						data.reuse()
					}

					state.dispose()

					database.commitTransaction()

					if (documents.size >= maxCount) {
						database.beginTransaction()

						for (a in maxCount until documents.size) {
							database.executeFast("DELETE FROM web_recent_v3 WHERE id = '" + documents[a].id + "' AND type = " + cacheType).stepThis().dispose()
						}

						database.commitTransaction()
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}

		if (date == 0) {
			AndroidUtilities.runOnUIThread {
				val editor = MessagesController.getEmojiSettings(currentAccount).edit()

				if (gif) {
					loadingRecentGifs = false
					recentGifsLoaded = true

					editor.putLong("lastGifLoadTime", System.currentTimeMillis()).commit()
				}
				else {
					loadingRecentStickers[type] = false
					recentStickersLoaded[type] = true

					when (type) {
						TYPE_IMAGE -> editor.putLong("lastStickersLoadTime", System.currentTimeMillis()).commit()
						TYPE_MASK -> editor.putLong("lastStickersLoadTimeMask", System.currentTimeMillis()).commit()
						TYPE_GREETINGS -> editor.putLong("lastStickersLoadTimeGreet", System.currentTimeMillis()).commit()
						TYPE_EMOJIPACKS -> editor.putLong("lastStickersLoadTimeEmojiPacks", System.currentTimeMillis()).commit()
						TYPE_PREMIUM_STICKERS -> editor.putLong("lastStickersLoadTimePremiumStickers", System.currentTimeMillis()).commit()
						else -> editor.putLong("lastStickersLoadTimeFavs", System.currentTimeMillis()).commit()
					}
				}

				if (documents != null) {
					if (gif) {
						recentGifs = documents.toMutableList()
					}
					else {
						recentStickers[type] = documents.toMutableList()
					}

					if (type == TYPE_GREETINGS) {
						preloadNextGreetingsSticker()
					}

					notificationCenter.postNotificationName(NotificationCenter.recentDocumentsDidLoad, gif, type)
				}
			}
		}
	}

	fun reorderStickers(type: Int, order: List<Long>, forceUpdateUi: Boolean) {
		stickerSets[type].sortWith { lhs, rhs ->
			val index1 = order.indexOf(lhs.set.id)
			val index2 = order.indexOf(rhs.set.id)

			return@sortWith if (index1 > index2) {
				1
			}
			else if (index1 < index2) {
				-1
			}
			else {
				0
			}
		}

		loadHash[type] = calcStickersHash(stickerSets[type])

		notificationCenter.postNotificationName(NotificationCenter.stickersDidLoad, type, forceUpdateUi)
	}

	fun calcNewHash(type: Int) {
		loadHash[type] = calcStickersHash(stickerSets[type])
	}

	fun storeTempStickerSet(set: TL_messages_stickerSet) {
		stickerSetsById.put(set.set.id, set)
		stickerSetsByName[set.set.short_name] = set
	}

	fun addNewStickerSet(set: TL_messages_stickerSet) {
		if (stickerSetsById.indexOfKey(set.set.id) >= 0 || stickerSetsByName.containsKey(set.set.short_name)) {
			return
		}

		var type = TYPE_IMAGE

		if (set.set.masks) {
			type = TYPE_MASK
		}
		else if (set.set.emojis) {
			type = TYPE_EMOJIPACKS
		}

		stickerSets[type].add(0, set)
		stickerSetsById.put(set.set.id, set)
		installedStickerSetsById.put(set.set.id, set)
		stickerSetsByName[set.set.short_name] = set

		val stickersById = LongSparseArray<TLRPC.Document>()

		for (a in set.documents.indices) {
			val document = set.documents[a]
			stickersById.put(document.id, document)
		}

		for (a in set.packs.indices) {
			val stickerPack = set.packs[a]
			stickerPack.emoticon = stickerPack.emoticon.replace("\uFE0F", "")

			var arrayList = allStickers[stickerPack.emoticon]

			if (arrayList == null) {
				arrayList = mutableListOf()
				allStickers[stickerPack.emoticon] = arrayList
			}

			for (c in stickerPack.documents.indices) {
				val id = stickerPack.documents[c]

				if (stickersByEmoji.indexOfKey(id) < 0) {
					stickersByEmoji.put(id, stickerPack.emoticon)
				}

				val sticker = stickersById[id]

				if (sticker != null) {
					arrayList.add(sticker)
				}
			}
		}

		loadHash[type] = calcStickersHash(stickerSets[type])

		notificationCenter.postNotificationName(NotificationCenter.stickersDidLoad, type, true)

		loadStickers(type, cache = false, useHash = true, retry = true)
	}

	private fun loadFeaturedStickers(emoji: Boolean, cache: Boolean, force: Boolean, retry: Boolean) {
		if (loadingFeaturedStickers[if (emoji) 1 else 0]) {
			return
		}

		loadingFeaturedStickers[if (emoji) 1 else 0] = true

		if (cache) {
			messagesStorage.storageQueue.postRunnable {
				var newStickerArray: List<StickerSetCovered>? = null
				val unread = mutableListOf<Long>()
				var date = 0
				var hash: Long = 0
				var premium = false
				var cursor: SQLiteCursor? = null

				try {
					cursor = messagesStorage.database.queryFinalized("SELECT data, unread, date, hash, premium FROM stickers_featured WHERE emoji = " + (if (emoji) 1 else 0))

					if (cursor.next()) {
						var data = cursor.byteBufferValue(0)

						if (data != null) {
							newStickerArray = mutableListOf()

							val count = data.readInt32(false)

							for (a in 0 until count) {
								val stickerSet = StickerSetCovered.TLdeserialize(data, data.readInt32(false), false)
								newStickerArray.add(stickerSet)
							}

							data.reuse()
						}

						data = cursor.byteBufferValue(1)

						if (data != null) {
							val count = data.readInt32(false)

							for (a in 0 until count) {
								unread.add(data.readInt64(false))
							}

							data.reuse()
						}

						date = cursor.intValue(2)
						hash = calcFeaturedStickersHash(emoji, newStickerArray)
						premium = cursor.intValue(4) == 1
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
				finally {
					cursor?.dispose()
				}

				processLoadedFeaturedStickers(emoji, newStickerArray, unread, premium, true, date, hash, retry)
			}
		}
		else {
			val hash: Long
			val req: TLObject

			if (emoji) {
				val request = TL_messages_getFeaturedEmojiStickers()
				hash = if (force) 0 else loadFeaturedHash[1]
				request.hash = hash
				req = request
			}
			else {
				val request = TL_messages_getFeaturedStickers()
				hash = if (force) 0 else loadFeaturedHash[0]
				request.hash = hash
				req = request
			}

			connectionsManager.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					if (response is TL_messages_featuredStickers) {
						processLoadedFeaturedStickers(emoji, response.sets, response.unread, response.premium, false, (System.currentTimeMillis() / 1000).toInt(), response.hash, retry)
					}
					else {
						processLoadedFeaturedStickers(emoji, null, null, premium = false, cache = false, date = (System.currentTimeMillis() / 1000).toInt(), hash = hash, retry = retry)
					}
				}
			}
		}
	}

	private fun processLoadedFeaturedStickers(emoji: Boolean, res: List<StickerSetCovered>?, unreadStickers: List<Long>?, premium: Boolean, cache: Boolean, date: Int, hash: Long, retry: Boolean) {
		AndroidUtilities.runOnUIThread {
			loadingFeaturedStickers[if (emoji) 1 else 0] = false
			featuredStickersLoaded[if (emoji) 1 else 0] = true
		}

		Utilities.stageQueue.postRunnable {
			if (cache && (res == null || abs((System.currentTimeMillis() / 1000 - date).toDouble()) >= 60 * 60) || !cache && res == null && hash == 0L) {
				AndroidUtilities.runOnUIThread({
					if (res != null && hash != 0L) {
						loadFeaturedHash[if (emoji) 1 else 0] = hash
					}

					loadingFeaturedStickers[if (emoji) 1 else 0] = false

					if (retry) {
						loadFeaturedStickers(emoji, cache = false, force = false, retry = false)
					}
				}, (if (res == null && !cache) 1000 else 0).toLong())
			}

			if (res != null) {
				try {
					val stickerSetsNew = mutableListOf<StickerSetCovered>()
					val stickerSetsByIdNew = LongSparseArray<StickerSetCovered>()

					for (a in res.indices) {
						val stickerSet = res[a]
						stickerSetsNew.add(stickerSet)
						stickerSetsByIdNew.put(stickerSet.set.id, stickerSet)
					}

					if (!cache) {
						putFeaturedStickersToCache(emoji, stickerSetsNew, unreadStickers, date, hash, premium)
					}

					AndroidUtilities.runOnUIThread {
						unreadStickerSets[if (emoji) 1 else 0] = unreadStickers?.toMutableList() ?: mutableListOf()
						featuredStickerSetsById[if (emoji) 1 else 0] = stickerSetsByIdNew
						featuredStickerSets[if (emoji) 1 else 0] = stickerSetsNew
						loadFeaturedHash[if (emoji) 1 else 0] = hash
						loadFeaturedDate[if (emoji) 1 else 0] = date
						loadFeaturedPremium = premium

						loadStickers(if (emoji) TYPE_FEATURED_EMOJIPACKS else TYPE_FEATURED, cache = true, useHash = false, retry = true)

						notificationCenter.postNotificationName(if (emoji) NotificationCenter.featuredEmojiDidLoad else NotificationCenter.featuredStickersDidLoad)
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					loadFeaturedDate[if (emoji) 1 else 0] = date
				}

				putFeaturedStickersToCache(emoji, null, null, date, 0, premium)
			}
		}
	}

	private fun putFeaturedStickersToCache(emoji: Boolean, stickers: List<StickerSetCovered>?, unreadStickers: List<Long>?, date: Int, hash: Long, premium: Boolean) {
		val stickersFinal = stickers?.toList()

		messagesStorage.storageQueue.postRunnable {
			try {
				if (stickersFinal != null) {
					val state = messagesStorage.database.executeFast("REPLACE INTO stickers_featured VALUES(?, ?, ?, ?, ?, ?, ?)")
					state.requery()

					var size = 4

					for (a in stickersFinal.indices) {
						size += stickersFinal[a].objectSize
					}

					val data = NativeByteBuffer(size)

					val data2 = NativeByteBuffer(4 + unreadStickers!!.size * 8)

					data.writeInt32(stickersFinal.size)

					for (a in stickersFinal.indices) {
						stickersFinal[a].serializeToStream(data)
					}

					data2.writeInt32(unreadStickers.size)

					for (a in unreadStickers.indices) {
						data2.writeInt64(unreadStickers[a])
					}

					state.bindInteger(1, 1)
					state.bindByteBuffer(2, data)
					state.bindByteBuffer(3, data2)
					state.bindInteger(4, date)
					state.bindLong(5, hash)
					state.bindInteger(6, if (premium) 1 else 0)
					state.bindInteger(7, if (emoji) 1 else 0)
					state.step()

					data.reuse()
					data2.reuse()

					state.dispose()
				}
				else {
					val state = messagesStorage.database.executeFast("UPDATE stickers_featured SET date = ?")
					state.requery()
					state.bindInteger(1, date)
					state.step()
					state.dispose()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun calcFeaturedStickersHash(emoji: Boolean, sets: List<StickerSetCovered>?): Long {
		if (sets.isNullOrEmpty()) {
			return 0
		}

		var acc: Long = 0

		for (a in sets.indices) {
			val set = sets[a].set

			if (set.archived) {
				continue
			}

			acc = calcHash(acc, set.id)

			if (unreadStickerSets[if (emoji) 1 else 0].contains(set.id)) {
				acc = calcHash(acc, 1)
			}
		}

		return acc
	}

	fun markFeaturedStickersAsRead(emoji: Boolean, query: Boolean) {
		if (unreadStickerSets[if (emoji) 1 else 0].isEmpty()) {
			return
		}

		unreadStickerSets[if (emoji) 1 else 0].clear()
		loadFeaturedHash[if (emoji) 1 else 0] = calcFeaturedStickersHash(emoji, featuredStickerSets[if (emoji) 1 else 0])
		notificationCenter.postNotificationName(if (emoji) NotificationCenter.featuredEmojiDidLoad else NotificationCenter.featuredStickersDidLoad)

		putFeaturedStickersToCache(emoji, featuredStickerSets[if (emoji) 1 else 0], unreadStickerSets[if (emoji) 1 else 0], loadFeaturedDate[if (emoji) 1 else 0], loadFeaturedHash[if (emoji) 1 else 0], loadFeaturedPremium)

		if (query) {
			connectionsManager.sendRequest(TL_messages_readFeaturedStickers())
		}
	}

	fun getFeaturedStickersHashWithoutUnread(emoji: Boolean): Long {
		var acc: Long = 0

		for (a in featuredStickerSets[if (emoji) 1 else 0].indices) {
			val set = featuredStickerSets[if (emoji) 1 else 0][a].set

			if (set.archived) {
				continue
			}

			acc = calcHash(acc, set.id)
		}

		return acc
	}

	fun markFeaturedStickersByIdAsRead(emoji: Boolean, id: Long) {
		if (!unreadStickerSets[if (emoji) 1 else 0].contains(id) || readingStickerSets[if (emoji) 1 else 0].contains(id)) {
			return
		}

		readingStickerSets[if (emoji) 1 else 0].add(id)

		val req = TL_messages_readFeaturedStickers()
		req.id.add(id)

		connectionsManager.sendRequest(req)

		AndroidUtilities.runOnUIThread({
			unreadStickerSets[if (emoji) 1 else 0].remove(id)
			readingStickerSets[if (emoji) 1 else 0].remove(id)
			loadFeaturedHash[if (emoji) 1 else 0] = calcFeaturedStickersHash(emoji, featuredStickerSets[if (emoji) 1 else 0])
			notificationCenter.postNotificationName(if (emoji) NotificationCenter.featuredEmojiDidLoad else NotificationCenter.featuredStickersDidLoad)

			putFeaturedStickersToCache(emoji, featuredStickerSets[if (emoji) 1 else 0], unreadStickerSets[if (emoji) 1 else 0], loadFeaturedDate[if (emoji) 1 else 0], loadFeaturedHash[if (emoji) 1 else 0], loadFeaturedPremium)
		}, 1000)
	}

	fun getArchivedStickersCount(type: Int): Int {
		return archivedStickersCount[type]
	}

	@JvmOverloads
	fun verifyAnimatedStickerMessage(message: Message?, safe: Boolean = false) {
		if (message == null) {
			return
		}

		val document = MessageObject.getDocument(message)
		val name = MessageObject.getStickerSetName(document)

		if (name.isNullOrEmpty()) {
			return
		}

		val stickerSet = stickerSetsByName[name]

		if (stickerSet != null) {
			for (sticker in stickerSet.documents) {
				if (sticker.id == document?.id && sticker.dc_id == document.dc_id) {
					message.stickerVerified = 1
					break
				}
			}

			return
		}

		if (safe) {
			AndroidUtilities.runOnUIThread {
				verifyAnimatedStickerMessageInternal(message, name)
			}
		}
		else {
			verifyAnimatedStickerMessageInternal(message, name)
		}
	}

	private fun verifyAnimatedStickerMessageInternal(message: Message, name: String) {
		var messages = verifyingMessages[name]

		if (messages == null) {
			messages = mutableListOf()
			verifyingMessages[name] = messages
		}

		messages.add(message)

		val req = TL_messages_getStickerSet()
		req.stickerset = MessageObject.getInputStickerSet(message)

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				val arrayList = verifyingMessages[name] ?: listOf()

				if (response is TL_messages_stickerSet) {
					storeTempStickerSet(response)

					var b = 0
					val n2 = arrayList.size

					while (b < n2) {
						val m = arrayList[b]
						val d = MessageObject.getDocument(m)
						var a = 0
						val n = response.documents.size

						while (a < n) {
							val sticker = response.documents[a]

							if (sticker.id == d?.id && sticker.dc_id == d.dc_id) {
								m.stickerVerified = 1
								break
							}

							a++
						}

						if (m.stickerVerified == 0) {
							m.stickerVerified = 2
						}

						b++
					}
				}
				else {
					var b = 0
					val n2 = arrayList.size

					while (b < n2) {
						arrayList[b].stickerVerified = 2
						b++
					}
				}

				notificationCenter.postNotificationName(NotificationCenter.didVerifyMessagesStickers, arrayList)

				messagesStorage.updateMessageVerifyFlags(arrayList)
			}
		}
	}

	private fun loadArchivedStickersCount(type: Int, cache: Boolean) {
		if (cache) {
			val preferences = MessagesController.getNotificationsSettings(currentAccount)
			val count = preferences.getInt("archivedStickersCount$type", -1)

			if (count == -1) {
				loadArchivedStickersCount(type, false)
			}
			else {
				archivedStickersCount[type] = count
				notificationCenter.postNotificationName(NotificationCenter.archivedStickersCountDidLoad, type)
			}
		}
		else {
			val req = TL_messages_getArchivedStickers()
			req.limit = 0
			req.masks = type == TYPE_MASK
			req.emojis = type == TYPE_EMOJIPACKS

			connectionsManager.sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					if (response is TL_messages_archivedStickers) {
						archivedStickersCount[type] = response.count
						val preferences = MessagesController.getNotificationsSettings(currentAccount)
						preferences.edit().putInt("archivedStickersCount$type", response.count).commit()
						notificationCenter.postNotificationName(NotificationCenter.archivedStickersCountDidLoad, type)
					}
				}
			}
		}
	}

	private fun processLoadStickersResponse(type: Int, res: TL_messages_allStickers, onDone: Runnable? = null, retry: Boolean = true) {
		val newStickerArray = mutableListOf<TL_messages_stickerSet?>()

		if (res.sets.isEmpty()) {
			processLoadedStickers(type, newStickerArray, false, (System.currentTimeMillis() / 1000).toInt(), res.hash, onDone, retry)
		}
		else {
			val newStickerSets = LongSparseArray<TL_messages_stickerSet?>()

			for (a in res.sets.indices) {
				val stickerSet = res.sets[a]
				val oldSet = stickerSetsById[stickerSet.id]

				if (oldSet != null && oldSet.set.hash == stickerSet.hash) {
					oldSet.set.archived = stickerSet.archived
					oldSet.set.installed = stickerSet.installed
					oldSet.set.official = stickerSet.official

					newStickerSets.put(oldSet.set.id, oldSet)

					newStickerArray.add(oldSet)

					if (newStickerSets.size() == res.sets.size) {
						processLoadedStickers(type, newStickerArray, false, (System.currentTimeMillis() / 1000).toInt(), res.hash, retry)
					}

					continue
				}

				newStickerArray.add(null)

				val req = TL_messages_getStickerSet()
				req.stickerset = TL_inputStickerSetID()
				req.stickerset.id = stickerSet.id
				req.stickerset.access_hash = stickerSet.access_hash

				connectionsManager.sendRequest(req) { response, _ ->
					AndroidUtilities.runOnUIThread {
						val res1 = response as? TL_messages_stickerSet
						newStickerArray[a] = res1
						newStickerSets.put(stickerSet.id, res1)

						if (newStickerSets.size() == res.sets.size) {
							var a1 = 0

							while (a1 < newStickerArray.size) {
								if (newStickerArray[a1] == null) {
									newStickerArray.removeAt(a1)
									a1--
								}

								a1++
							}

							processLoadedStickers(type, newStickerArray, false, (System.currentTimeMillis() / 1000).toInt(), res.hash, retry)
						}
					}
				}
			}

			onDone?.run()
		}
	}

	fun checkPremiumGiftStickers() {
		if (userConfig.premiumGiftsStickerPack != null) {
			val packName = userConfig.premiumGiftsStickerPack
			var set = getStickerSetByName(packName)

			if (set == null) {
				set = getStickerSetByEmojiOrName(packName)
			}

			if (set == null) {
				getInstance(currentAccount).loadStickersByEmojiOrName(packName, isEmoji = false, cache = true)
			}
		}

		if (loadingPremiumGiftStickers || System.currentTimeMillis() - userConfig.lastUpdatedPremiumGiftsStickerPack < 86400000) {
			return
		}

		loadingPremiumGiftStickers = true

		val req = TL_messages_getStickerSet()
		req.stickerset = TL_inputStickerSetPremiumGifts()

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is TL_messages_stickerSet) {
					userConfig.premiumGiftsStickerPack = response.set.short_name
					userConfig.lastUpdatedPremiumGiftsStickerPack = System.currentTimeMillis()
					userConfig.saveConfig(false)

					processLoadedDiceStickers(userConfig.premiumGiftsStickerPack!!, false, response, false, (System.currentTimeMillis() / 1000).toInt())

					notificationCenter.postNotificationName(NotificationCenter.didUpdatePremiumGiftStickers)
				}
			}
		}
	}

	private fun checkGenericAnimations() {
		if (userConfig.genericAnimationsStickerPack != null) {
			val packName = userConfig.genericAnimationsStickerPack
			var set = getStickerSetByName(packName)

			if (set == null) {
				set = getStickerSetByEmojiOrName(packName)
			}

			if (set == null) {
				getInstance(currentAccount).loadStickersByEmojiOrName(packName, isEmoji = false, cache = true)
			}
		}

		if (loadingGenericAnimations /*|| System.currentTimeMillis() - getUserConfig().lastUpdatedGenericAnimations < 86400000*/) {
			return
		}

		loadingGenericAnimations = true

		val req = TL_messages_getStickerSet()
		req.stickerset = TL_inputStickerSetEmojiGenericAnimations()

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response is TL_messages_stickerSet) {
					userConfig.genericAnimationsStickerPack = response.set.short_name
					userConfig.lastUpdatedGenericAnimations = System.currentTimeMillis()
					userConfig.saveConfig(false)

					processLoadedDiceStickers(userConfig.genericAnimationsStickerPack!!, false, response, false, (System.currentTimeMillis() / 1000).toInt())

					for (document in response.documents) {
						preloadImage(ImageLocation.getForDocument(document), null)
					}
				}
			}
		}
	}

	fun loadStickersByEmojiOrName(name: String?, isEmoji: Boolean, cache: Boolean) {
		if (name.isNullOrEmpty() || loadingDiceStickerSets.contains(name) || isEmoji && diceStickerSetsByEmoji[name] != null) {
			return
		}

		loadingDiceStickerSets.add(name)

		if (cache) {
			messagesStorage.storageQueue.postRunnable {
				var stickerSet: TL_messages_stickerSet? = null
				var date = 0
				var cursor: SQLiteCursor? = null

				try {
					cursor = messagesStorage.database.queryFinalized("SELECT data, date FROM stickers_dice WHERE emoji = ?", name)

					if (cursor.next()) {
						val data = cursor.byteBufferValue(0)

						if (data != null) {
							stickerSet = TL_messages_stickerSet.TLdeserialize(data, data.readInt32(false), false)
							data.reuse()
						}

						date = cursor.intValue(1)
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
				finally {
					cursor?.dispose()
				}

				processLoadedDiceStickers(name, isEmoji, stickerSet, true, date)
			}
		}
		else {
			val req = TL_messages_getStickerSet()

			if (userConfig.premiumGiftsStickerPack == name) {
				req.stickerset = TL_inputStickerSetPremiumGifts()
			}
			else if (isEmoji) {
				val inputStickerSetDice = TL_inputStickerSetDice()
				inputStickerSetDice.emoticon = name

				req.stickerset = inputStickerSetDice
			}
			else {
				val inputStickerSetShortName = TL_inputStickerSetShortName()
				inputStickerSetShortName.short_name = name

				req.stickerset = inputStickerSetShortName
			}

			connectionsManager.sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					if (BuildConfig.DEBUG && error != null) { // suppress test backend warning
						return@runOnUIThread
					}

					processLoadedDiceStickers(name, isEmoji, response as? TL_messages_stickerSet, false, (System.currentTimeMillis() / 1000).toInt())
				}
			}
		}
	}

	private fun processLoadedDiceStickers(name: String, isEmoji: Boolean, res: TL_messages_stickerSet?, cache: Boolean, date: Int) {
		AndroidUtilities.runOnUIThread {
			loadingDiceStickerSets.remove(name)
		}

		Utilities.stageQueue.postRunnable {
			if (cache && (res == null || abs((System.currentTimeMillis() / 1000 - date).toDouble()) >= 60 * 60 * 24) || !cache && res == null) {
				AndroidUtilities.runOnUIThread({ loadStickersByEmojiOrName(name, isEmoji, false) }, (if (res == null && !cache) 1000 else 0).toLong())

				if (res == null) {
					return@postRunnable
				}
			}

			if (res != null) {
				if (!cache) {
					putDiceStickersToCache(name, res, date)
				}

				AndroidUtilities.runOnUIThread {
					diceStickerSetsByEmoji[name] = res
					diceEmojiStickerSetsById.put(res.set.id, name)
					notificationCenter.postNotificationName(NotificationCenter.diceStickersDidLoad, name)
				}
			}
			else if (!cache) {
				putDiceStickersToCache(name, null, date)
			}
		}
	}

	private fun putDiceStickersToCache(emoji: String?, stickers: TL_messages_stickerSet?, date: Int) {
		if (emoji.isNullOrEmpty()) {
			return
		}

		messagesStorage.storageQueue.postRunnable {
			try {
				if (stickers != null) {
					val state = messagesStorage.database.executeFast("REPLACE INTO stickers_dice VALUES(?, ?, ?)")
					state.requery()

					val data = NativeByteBuffer(stickers.objectSize)

					stickers.serializeToStream(data)

					state.bindString(1, emoji)
					state.bindByteBuffer(2, data)
					state.bindInteger(3, date)
					state.step()

					data.reuse()

					state.dispose()
				}
				else {
					val state = messagesStorage.database.executeFast("UPDATE stickers_dice SET date = ?")
					state.requery()
					state.bindInteger(1, date)
					state.step()
					state.dispose()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun markSetInstalling(id: Long, installing: Boolean) {
		uninstalledForceStickerSetsById.remove(id)

		if (installing && !installedForceStickerSetsById.contains(id)) {
			installedForceStickerSetsById.add(id)
		}

		if (!installing) {
			installedForceStickerSetsById.remove(id)
		}
	}

	private fun markSetUninstalling(id: Long, uninstalling: Boolean) {
		installedForceStickerSetsById.remove(id)

		if (uninstalling && !uninstalledForceStickerSetsById.contains(id)) {
			uninstalledForceStickerSetsById.add(id)
		}

		if (!uninstalling) {
			uninstalledForceStickerSetsById.remove(id)
		}
	}

	fun loadStickers(type: Int, cache: Boolean, useHash: Boolean, retry: Boolean) {
		loadStickers(type, cache, useHash, false, null, retry)
	}

	fun loadStickers(type: Int, cache: Boolean, force: Boolean, scheduleIfLoading: Boolean, retry: Boolean) {
		loadStickers(type, cache, force, scheduleIfLoading, null, retry)
	}

	fun loadStickers(type: Int, cache: Boolean, force: Boolean, scheduleIfLoading: Boolean, onFinish: Utilities.Callback<List<TL_messages_stickerSet>?>?, retry: Boolean) {
		if (loadingStickers[type]) {
			if (scheduleIfLoading) {
				scheduledLoadStickers[type] = Runnable { loadStickers(type, false, force, false, onFinish, retry) }
			}
			else {
				onFinish?.run(null)
			}

			return
		}

		if (type == TYPE_FEATURED) {
			if (featuredStickerSets[0].isEmpty() || !messagesController.preloadFeaturedStickers) {
				onFinish?.run(null)
				return
			}
		}
		else if (type == TYPE_FEATURED_EMOJIPACKS) {
			if (featuredStickerSets[1].isEmpty() || !messagesController.preloadFeaturedStickers) {
				onFinish?.run(null)
				return
			}
		}
		else if (type != TYPE_EMOJI) {
			loadArchivedStickersCount(type, cache)
		}

		loadingStickers[type] = true

		if (cache) {
			messagesStorage.storageQueue.postRunnable {
				val newStickerArray = ArrayList<TL_messages_stickerSet>()
				var date = 0
				var hash: Long = 0
				var cursor: SQLiteCursor? = null

				try {
					cursor = messagesStorage.database.queryFinalized("SELECT data, date, hash FROM stickers_v2 WHERE id = " + (type + 1))

					if (cursor.next()) {
						val data = cursor.byteBufferValue(0)

						if (data != null) {
							val count = data.readInt32(false)

							for (a in 0 until count) {
								val stickerSet = TL_messages_stickerSet.TLdeserialize(data, data.readInt32(false), false)
								newStickerArray.add(stickerSet)
							}

							data.reuse()
						}

						date = cursor.intValue(1)
						hash = calcStickersHash(newStickerArray)
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
				finally {
					cursor?.dispose()
				}

				processLoadedStickers(type, newStickerArray, true, date, hash, {
					onFinish?.run(newStickerArray)
				}, retry)
			}
		}
		else {
			when (type) {
				TYPE_FEATURED, TYPE_FEATURED_EMOJIPACKS -> {
					val emoji = type == TYPE_FEATURED_EMOJIPACKS

					val response = TL_messages_allStickers()
					response.hash = loadFeaturedHash[if (emoji) 1 else 0]

					var a = 0
					val size = featuredStickerSets[if (emoji) 1 else 0].size

					while (a < size) {
						response.sets.add(featuredStickerSets[if (emoji) 1 else 0][a].set)
						a++
					}

					processLoadStickersResponse(type, response, {
						onFinish?.run(null)
					}, retry)
				}

				TYPE_EMOJI -> {
					val req = TL_messages_getStickerSet()
					req.stickerset = TL_inputStickerSetAnimatedEmoji()

					connectionsManager.sendRequest(req) { response, error ->
						var innerRetry = retry

						if (error != null) {
							innerRetry = false
						}

						if (response is TL_messages_stickerSet) {
							val newStickerArray = ArrayList<TL_messages_stickerSet>()
							newStickerArray.add(response)

							processLoadedStickers(type, newStickerArray, false, (System.currentTimeMillis() / 1000).toInt(), calcStickersHash(newStickerArray), {
								onFinish?.run(null)
							}, innerRetry)
						}
						else {
							processLoadedStickers(type, null, false, (System.currentTimeMillis() / 1000).toInt(), 0, {
								onFinish?.run(null)
							}, innerRetry)
						}
					}
				}

				else -> {
					val req: TLObject
					val hash: Long

					when (type) {
						TYPE_IMAGE -> {
							req = TL_messages_getAllStickers()
							req.hash = if (force) 0 else loadHash[type]

							hash = req.hash
						}

						TYPE_EMOJIPACKS -> {
							req = TL_messages_getEmojiStickers()
							req.hash = if (force) 0 else loadHash[type]

							hash = req.hash
						}

						else -> {
							req = TL_messages_getMaskStickers()
							req.hash = if (force) 0 else loadHash[type]

							hash = req.hash
						}
					}

					connectionsManager.sendRequest(req) { response, error ->
						AndroidUtilities.runOnUIThread {
							var innerRetry = retry

							if (error != null) {
								innerRetry = false
							}

							if (response is TL_messages_allStickers) {
								processLoadStickersResponse(type, response, {
									onFinish?.run(null)
								}, innerRetry)
							}
							else {
								processLoadedStickers(type, null, false, (System.currentTimeMillis() / 1000).toInt(), hash, {
									onFinish?.run(null)
								}, innerRetry)
							}
						}
					}
				}
			}
		}
	}

	private fun putStickersToCache(type: Int, stickers: List<TL_messages_stickerSet>?, date: Int, hash: Long) {
		val stickersFinal = stickers?.toList()

		messagesStorage.storageQueue.postRunnable {
			try {
				if (stickersFinal != null) {
					val state = messagesStorage.database.executeFast("REPLACE INTO stickers_v2 VALUES(?, ?, ?, ?)")
					state.requery()

					var size = 4

					for (a in stickersFinal.indices) {
						size += stickersFinal[a].objectSize
					}

					val data = NativeByteBuffer(size)
					data.writeInt32(stickersFinal.size)

					for (a in stickersFinal.indices) {
						stickersFinal[a].serializeToStream(data)
					}

					state.bindInteger(1, type + 1)
					state.bindByteBuffer(2, data)
					state.bindInteger(3, date)
					state.bindLong(4, hash)
					state.step()

					data.reuse()

					state.dispose()
				}
				else {
					val state = messagesStorage.database.executeFast("UPDATE stickers_v2 SET date = ?")
					state.requery()
					state.bindLong(1, date.toLong())
					state.step()
					state.dispose()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun getStickerSetName(setId: Long): String? {
		val stickerSet = stickerSetsById[setId]

		if (stickerSet != null) {
			return stickerSet.set.short_name
		}

		var stickerSetCovered = featuredStickerSetsById[0][setId]

		if (stickerSetCovered != null) {
			return stickerSetCovered.set.short_name
		}

		stickerSetCovered = featuredStickerSetsById[1][setId]

		if (stickerSetCovered != null) {
			return stickerSetCovered.set.short_name
		}

		return null
	}

	private fun processLoadedStickers(type: Int, res: List<TL_messages_stickerSet?>, cache: Boolean, date: Int, hash: Long, retry: Boolean) {
		processLoadedStickers(type, res, cache, date, hash, null, retry)
	}

	private fun processLoadedStickers(type: Int, res: List<TL_messages_stickerSet?>?, cache: Boolean, date: Int, hash: Long, onFinish: Runnable?, retry: Boolean) {
		AndroidUtilities.runOnUIThread {
			loadingStickers[type] = false
			stickersLoaded[type] = true

			scheduledLoadStickers[type]?.run()
			scheduledLoadStickers[type] = null
		}

		Utilities.stageQueue.postRunnable {
			if (cache && (res == null || BuildConfig.DEBUG_PRIVATE_VERSION || abs((System.currentTimeMillis() / 1000 - date).toDouble()) >= 60 * 60) || !cache && res == null && hash == 0L) {
				AndroidUtilities.runOnUIThread({
					if (res != null && hash != 0L) {
						loadHash[type] = hash
					}

					if (retry) {
						loadStickers(type, cache = false, useHash = false, retry = true)
					}
				}, if (res == null && !cache) 1000L else 0L)

				if (res == null) {
					onFinish?.run()
					return@postRunnable
				}
			}

			if (res != null) {
				try {
					val stickerSetsNew = mutableListOf<TL_messages_stickerSet>()
					val stickerSetsByIdNew = LongSparseArray<TL_messages_stickerSet>()
					val stickerSetsByNameNew = mutableMapOf<String, TL_messages_stickerSet>()
					val stickersByEmojiNew = LongSparseArray<String>()
					val stickersByIdNew = LongSparseArray<TLRPC.Document>()
					val allStickersNew = HashMap<String, MutableList<TLRPC.Document>>()

					for (stickerSet in res) {
						if (stickerSet == null || removingStickerSetsUndos.indexOfKey(stickerSet.set.id) >= 0) {
							continue
						}

						stickerSetsNew.add(stickerSet)
						stickerSetsByIdNew.put(stickerSet.set.id, stickerSet)
						stickerSetsByNameNew[stickerSet.set.short_name] = stickerSet

						for (document in stickerSet.documents) {
							if (document == null || document is TL_documentEmpty) {
								continue
							}

							stickersByIdNew.put(document.id, document)
						}

						if (!stickerSet.set.archived) {
							for (stickerPack in stickerSet.packs) {
								if (stickerPack?.emoticon == null) {
									continue
								}

								stickerPack.emoticon = stickerPack.emoticon.replace("\uFE0F", "")

								var arrayList = allStickersNew[stickerPack.emoticon]

								if (arrayList == null) {
									arrayList = mutableListOf()

									allStickersNew[stickerPack.emoticon] = arrayList
								}

								for (id in stickerPack.documents) {
									if (stickersByEmojiNew.indexOfKey(id) < 0) {
										stickersByEmojiNew.put(id, stickerPack.emoticon)
									}

									val sticker = stickersByIdNew[id]

									if (sticker != null) {
										arrayList.add(sticker)
									}
								}
							}
						}
					}

					if (!cache) {
						putStickersToCache(type, stickerSetsNew, date, hash)
					}

					AndroidUtilities.runOnUIThread {
						for (set in stickerSets[type]) {
							val stickerSet = set.set

							stickerSetsById.remove(stickerSet.id)
							stickerSetsByName.remove(stickerSet.short_name)

							if (type != TYPE_FEATURED && type != TYPE_FEATURED_EMOJIPACKS && type != TYPE_EMOJI) {
								installedStickerSetsById.remove(stickerSet.id)
							}
						}

						for (a in 0 until stickerSetsByIdNew.size()) {
							stickerSetsById.put(stickerSetsByIdNew.keyAt(a), stickerSetsByIdNew.valueAt(a))

							if (type != TYPE_FEATURED && type != TYPE_FEATURED_EMOJIPACKS && type != TYPE_EMOJI) {
								installedStickerSetsById.put(stickerSetsByIdNew.keyAt(a), stickerSetsByIdNew.valueAt(a))
							}
						}

						stickerSetsByName.putAll(stickerSetsByNameNew)
						stickerSets[type] = stickerSetsNew
						loadHash[type] = hash
						loadDate[type] = date
						stickersByIds[type] = stickersByIdNew

						if (type == TYPE_IMAGE) {
							allStickers = allStickersNew
							stickersByEmoji = stickersByEmojiNew
						}
						else if (type == TYPE_FEATURED) {
							allStickersFeatured = allStickersNew
						}

						notificationCenter.postNotificationName(NotificationCenter.stickersDidLoad, type, true)

						onFinish?.run()
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
					onFinish?.run()
				}
			}
			else if (!cache) {
				AndroidUtilities.runOnUIThread {
					loadDate[type] = date
				}

				putStickersToCache(type, null, date, 0)

				onFinish?.run()
			}
			else {
				onFinish?.run()
			}
		}
	}

	fun cancelRemovingStickerSet(id: Long): Boolean {
		val undoAction = removingStickerSetsUndos[id]

		if (undoAction != null) {
			undoAction.run()
			return true
		}
		else {
			return false
		}
	}

	fun preloadStickerSetThumb(stickerSet: TL_messages_stickerSet) {
		val thumb = FileLoader.getClosestPhotoSizeWithSize(stickerSet.set.thumbs, 90)

		if (thumb != null) {
			val documents = stickerSet.documents

			if (documents != null && documents.isNotEmpty()) {
				loadStickerSetThumbInternal(thumb, stickerSet, documents[0], stickerSet.set.thumb_version)
			}
		}
	}

	fun preloadStickerSetThumb(stickerSet: StickerSetCovered) {
		val thumb = FileLoader.getClosestPhotoSizeWithSize(stickerSet.set.thumbs, 90)

		if (thumb != null) {
			val sticker = if (stickerSet.cover != null) {
				stickerSet.cover
			}
			else if (stickerSet.covers.isNotEmpty()) {
				stickerSet.covers[0]
			}
			else {
				return
			}

			loadStickerSetThumbInternal(thumb, stickerSet, sticker, stickerSet.set.thumb_version)
		}
	}

	private fun loadStickerSetThumbInternal(thumb: PhotoSize, parentObject: Any, sticker: TLRPC.Document, thumbVersion: Int) {
		val imageLocation = ImageLocation.getForSticker(thumb, sticker, thumbVersion) ?: return
		val ext = if (imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE) "tgs" else "webp"
		fileLoader.loadFile(imageLocation, parentObject, ext, FileLoader.PRIORITY_HIGH, 1)
	}

	/**
	 * @param toggle 0 - remove, 1 - archive, 2 - add
	 */
	@JvmOverloads
	fun toggleStickerSet(context: Context, stickerSetObject: TLObject, toggle: Int, baseFragment: BaseFragment?, showSettings: Boolean, showTooltip: Boolean, onUndo: Runnable? = null) {
		val stickerSet: StickerSet
		val messagesStickerSet: TL_messages_stickerSet?

		if (stickerSetObject is TL_messages_stickerSet) {
			messagesStickerSet = stickerSetObject
			stickerSet = messagesStickerSet.set
		}
		else if (stickerSetObject is StickerSetCovered) {
			stickerSet = stickerSetObject.set

			if (toggle != 2) {
				messagesStickerSet = stickerSetsById[stickerSet.id]

				if (messagesStickerSet == null) {
					return
				}
			}
			else {
				// messagesStickerSet = null // MARK: check if this is correct
				return
			}
		}
		else {
			throw IllegalArgumentException("Invalid type of the given stickerSetObject: " + stickerSetObject.javaClass)
		}

		var type1 = TYPE_IMAGE

		if (stickerSet.masks) {
			type1 = TYPE_MASK
		}
		else if (stickerSet.emojis) {
			type1 = TYPE_EMOJIPACKS
		}

		val type = type1

		stickerSet.archived = toggle == 1

		var currentIndex = 0

		for (a in stickerSets[type].indices) {
			val set = stickerSets[type][a]

			if (set.set.id == stickerSet.id) {
				currentIndex = a

				stickerSets[type].removeAt(a)

				if (toggle == 2) {
					stickerSets[type].add(0, set)
				}
				else {
					stickerSetsById.remove(set.set.id)
					installedStickerSetsById.remove(set.set.id)
					stickerSetsByName.remove(set.set.short_name)
				}

				break
			}
		}

		loadHash[type] = calcStickersHash(stickerSets[type])

		putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type])

		if (toggle == 2) {
			if (!cancelRemovingStickerSet(stickerSet.id)) {
				toggleStickerSetInternal(context, toggle, baseFragment, showSettings, stickerSetObject, stickerSet, type, showTooltip)
			}
		}
		else if (!showTooltip || baseFragment == null) {
			toggleStickerSetInternal(context, toggle, baseFragment, showSettings, stickerSetObject, stickerSet, type, false)
		}
		else {
			val bulletinLayout = StickerSetBulletinLayout(context, stickerSetObject, toggle)
			val finalCurrentIndex = currentIndex
			val undoDone = BooleanArray(1)

			markSetUninstalling(stickerSet.id, true)

			val undoButton = Bulletin.UndoButton(context, false).setUndoAction {
				if (undoDone[0]) {
					return@setUndoAction
				}

				undoDone[0] = true
				markSetUninstalling(stickerSet.id, false)
				stickerSet.archived = false

				stickerSets[type].add(finalCurrentIndex, messagesStickerSet)
				stickerSetsById.put(stickerSet.id, messagesStickerSet)
				installedStickerSetsById.put(stickerSet.id, messagesStickerSet)
				stickerSetsByName[stickerSet.short_name] = messagesStickerSet
				removingStickerSetsUndos.remove(stickerSet.id)

				loadHash[type] = calcStickersHash(stickerSets[type])

				putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type])

				onUndo?.run()

				notificationCenter.postNotificationName(NotificationCenter.stickersDidLoad, type, true)
			}.setDelayedAction {
				if (undoDone[0]) {
					return@setDelayedAction
				}

				undoDone[0] = true

				toggleStickerSetInternal(context, toggle, baseFragment, showSettings, stickerSetObject, stickerSet, type, false)
			}

//			bulletinLayout.button = undoButton
			bulletinLayout.setButton(undoButton)

			removingStickerSetsUndos.put(stickerSet.id, Runnable { undoButton.undo() })

			Bulletin.make(baseFragment, bulletinLayout, Bulletin.DURATION_LONG).show()
		}

		notificationCenter.postNotificationName(NotificationCenter.stickersDidLoad, type, true)
	}

	fun removeMultipleStickerSets(context: Context, fragment: BaseFragment, sets: List<TL_messages_stickerSet>?) {
		if (sets.isNullOrEmpty()) {
			return
		}

		val messagesStickerSet = sets.lastOrNull() ?: return
		var type1 = TYPE_IMAGE

		if (messagesStickerSet.set.masks) {
			type1 = TYPE_MASK
		}
		else if (messagesStickerSet.set.emojis) {
			type1 = TYPE_EMOJIPACKS
		}

		val type = type1

		for (i in sets.indices) {
			sets[i].set.archived = false
		}

		val currentIndexes = IntArray(sets.size)

		for (a in stickerSets[type].indices) {
			val set = stickerSets[type][a]

			for (b in sets.indices) {
				if (set.set.id == sets[b].set.id) {
					currentIndexes[b] = a
					stickerSets[type].removeAt(a)
					stickerSetsById.remove(set.set.id)
					installedStickerSetsById.remove(set.set.id)
					stickerSetsByName.remove(set.set.short_name)
					break
				}
			}
		}

		putStickersToCache(type, stickerSets[type], loadDate[type], calcStickersHash(stickerSets[type]).also { loadHash[type] = it })

		notificationCenter.postNotificationName(NotificationCenter.stickersDidLoad, type, true)

		for (i in sets.indices) {
			markSetUninstalling(sets[i].set.id, true)
		}

		val bulletinLayout = StickerSetBulletinLayout(context, messagesStickerSet, sets.size, StickerSetBulletinLayout.TYPE_REMOVED, null)
		val undoDone = BooleanArray(1)

		val undoButton = Bulletin.UndoButton(context, false).setUndoAction {
			if (undoDone[0]) {
				return@setUndoAction
			}

			undoDone[0] = true

			for (i in sets.indices) {
				markSetUninstalling(sets[i].set.id, false)
				sets[i].set.archived = false

				stickerSets[type].add(currentIndexes[i], sets[i])
				stickerSetsById.put(sets[i].set.id, sets[i])
				installedStickerSetsById.put(sets[i].set.id, sets[i])
				stickerSetsByName[sets[i].set.short_name] = sets[i]
				removingStickerSetsUndos.remove(sets[i].set.id)
			}

			putStickersToCache(type, stickerSets[type], loadDate[type], calcStickersHash(stickerSets[type]).also { loadHash[type] = it })
			notificationCenter.postNotificationName(NotificationCenter.stickersDidLoad, type, true)
		}.setDelayedAction {
			if (undoDone[0]) {
				return@setDelayedAction
			}

			undoDone[0] = true

			for (i in sets.indices) {
				toggleStickerSetInternal(context, 0, fragment, true, sets[i], sets[i].set, type, false)
			}
		}

		bulletinLayout.setButton(undoButton)

		for (i in sets.indices) {
			removingStickerSetsUndos.put(sets[i].set.id, Runnable { undoButton.undo() })
		}

		Bulletin.make(fragment, bulletinLayout, Bulletin.DURATION_LONG).show()
	}

	private fun toggleStickerSetInternal(context: Context, toggle: Int, baseFragment: BaseFragment?, showSettings: Boolean, stickerSetObject: TLObject, stickerSet: StickerSet, type: Int, showTooltip: Boolean) {
		val stickerSetID = TL_inputStickerSetID()
		stickerSetID.access_hash = stickerSet.access_hash
		stickerSetID.id = stickerSet.id

		if (toggle != 0) {
			val req = TL_messages_installStickerSet()
			req.stickerset = stickerSetID
			req.archived = toggle == 1

			markSetInstalling(stickerSet.id, true)

			connectionsManager.sendRequest(req) { response, error ->
				AndroidUtilities.runOnUIThread {
					removingStickerSetsUndos.remove(stickerSet.id)

					if (response is TL_messages_stickerSetInstallResultArchive) {
						processStickerSetInstallResultArchive(baseFragment, showSettings, type, response)
					}

					loadStickers(type, cache = false, force = false, scheduleIfLoading = true, onFinish = { markSetInstalling(stickerSet.id, false) }, retry = true)

					if (error == null && showTooltip && baseFragment != null) {
						Bulletin.make(baseFragment, StickerSetBulletinLayout(context, stickerSetObject, StickerSetBulletinLayout.TYPE_ADDED), Bulletin.DURATION_SHORT).show()
					}
				}
			}
		}
		else {
			markSetUninstalling(stickerSet.id, true)

			val req = TL_messages_uninstallStickerSet()
			req.stickerset = stickerSetID

			connectionsManager.sendRequest(req) { _, _ ->
				AndroidUtilities.runOnUIThread {
					removingStickerSetsUndos.remove(stickerSet.id)
					loadStickers(type, cache = false, force = true, scheduleIfLoading = false, onFinish = { markSetUninstalling(stickerSet.id, false) }, retry = true)
				}
			}
		}
	}

	/**
	 * @param toggle 0 - uninstall, 1 - archive, 2 - unarchive
	 */
	fun toggleStickerSets(stickerSetList: List<StickerSet>, type: Int, toggle: Int, baseFragment: BaseFragment?, showSettings: Boolean) {
		val stickerSetListSize = stickerSetList.size
		val inputStickerSets = ArrayList<InputStickerSet>(stickerSetListSize)

		for (i in 0 until stickerSetListSize) {
			val stickerSet = stickerSetList[i]

			val inputStickerSet: InputStickerSet = TL_inputStickerSetID()
			inputStickerSet.access_hash = stickerSet.access_hash
			inputStickerSet.id = stickerSet.id
			inputStickerSets.add(inputStickerSet)

			if (toggle != 0) {
				stickerSet.archived = toggle == 1
			}

			var a = 0
			val size = stickerSets[type].size

			while (a < size) {
				val set = stickerSets[type][a]

				if (set.set.id == inputStickerSet.id) {
					stickerSets[type].removeAt(a)

					if (toggle == 2) {
						stickerSets[type].add(0, set)
					}
					else {
						stickerSetsById.remove(set.set.id)
						installedStickerSetsById.remove(set.set.id)
						stickerSetsByName.remove(set.set.short_name)
					}

					break
				}

				a++
			}
		}

		loadHash[type] = calcStickersHash(stickerSets[type])

		putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type])

		notificationCenter.postNotificationName(NotificationCenter.stickersDidLoad, type, true)

		val req = TL_messages_toggleStickerSets()
		req.stickersets = inputStickerSets

		when (toggle) {
			0 -> req.uninstall = true
			1 -> req.archive = true
			2 -> req.unarchive = true
		}

		connectionsManager.sendRequest(req) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (toggle != 0) {
					if (response is TL_messages_stickerSetInstallResultArchive) {
						processStickerSetInstallResultArchive(baseFragment, showSettings, type, response)
					}

					loadStickers(type, cache = false, useHash = false, retry = true)
				}
				else {
					loadStickers(type, cache = false, useHash = true, retry = true)
				}
			}
		}
	}

	fun processStickerSetInstallResultArchive(baseFragment: BaseFragment?, showSettings: Boolean, type: Int, response: TL_messages_stickerSetInstallResultArchive) {
		var i = 0
		val size = response.sets.size

		while (i < size) {
			installedStickerSetsById.remove(response.sets[i].set.id)
			i++
		}

		loadArchivedStickersCount(type, false)

		notificationCenter.postNotificationName(NotificationCenter.needAddArchivedStickers, response.sets)

		if (baseFragment != null && baseFragment.parentActivity != null) {
			val alert = StickersArchiveAlert(baseFragment.parentActivity, if (showSettings) baseFragment else null, response.sets)
			baseFragment.showDialog(alert.create())
		}
	}

	//---------------- STICKERS END ----------------
	private var reqId = 0
	private var mergeReqId = 0
	private var lastMergeDialogId: Long = 0
	private var lastReplyMessageId = 0
	private var lastDialogId: Long = 0
	private var lastReqId = 0
	private var lastGuid = 0
	private var lastSearchUser: User? = null
	private var lastSearchChat: Chat? = null
	private val messagesSearchCount = intArrayOf(0, 0)
	private val messagesSearchEndReached = booleanArrayOf(false, false)
	val foundMessageObjects = mutableListOf<MessageObject>()
	private val searchResultMessagesMap = arrayOf(SparseArray<MessageObject>(), SparseArray<MessageObject>())

	var lastSearchQuery: String? = null
		private set

	private var lastReturnedNum = 0
	private var loadingMoreSearchMessages = false

	private val mask: Int
		get() {
			var mask = 0

			if (lastReturnedNum < foundMessageObjects.size - 1 || !messagesSearchEndReached[0] || !messagesSearchEndReached[1]) {
				mask = mask or 1
			}

			if (lastReturnedNum > 0) {
				mask = mask or 2
			}

			return mask
		}

	fun clearFoundMessageObjects() {
		foundMessageObjects.clear()
	}

	fun isMessageFound(messageId: Int, mergeDialog: Boolean): Boolean {
		return searchResultMessagesMap[if (mergeDialog) 1 else 0].indexOfKey(messageId) >= 0
	}

	fun searchMessagesInChat(query: String?, dialogId: Long, mergeDialogId: Long, guid: Int, direction: Int, replyMessageId: Int, user: User?, chat: Chat?) {
		searchMessagesInChat(query, dialogId, mergeDialogId, guid, direction, replyMessageId, false, user, chat, true)
	}

	fun jumpToSearchedMessage(guid: Int, index: Int) {
		if (index < 0 || index >= foundMessageObjects.size) {
			return
		}

		lastReturnedNum = index

		val messageObject = foundMessageObjects[lastReturnedNum]

		notificationCenter.postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.id, mask, messageObject.dialogId, lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1], true)
	}

	fun loadMoreSearchMessages() {
		if (loadingMoreSearchMessages || messagesSearchEndReached[0] && lastMergeDialogId == 0L && messagesSearchEndReached[1]) {
			return
		}

		val temp = foundMessageObjects.size

		lastReturnedNum = foundMessageObjects.size

		searchMessagesInChat(null, lastDialogId, lastMergeDialogId, lastGuid, 1, lastReplyMessageId, false, lastSearchUser, lastSearchChat, false)

		lastReturnedNum = temp
		loadingMoreSearchMessages = true
	}

	private fun searchMessagesInChat(query: String?, dialogId: Long, mergeDialogId: Long, guid: Int, direction: Int, replyMessageId: Int, internal: Boolean, user: User?, chat: Chat?, jumpToMessage: Boolean) {
		@Suppress("NAME_SHADOWING") var query = query
		var maxId = 0
		var queryWithDialog = dialogId
		var firstQuery = !internal

		if (reqId != 0) {
			connectionsManager.cancelRequest(reqId, true)
			reqId = 0
		}

		if (mergeReqId != 0) {
			connectionsManager.cancelRequest(mergeReqId, true)
			mergeReqId = 0
		}

		if (query == null) {
			if (foundMessageObjects.isEmpty()) {
				return
			}

			if (direction == 1) {
				lastReturnedNum++

				if (lastReturnedNum < foundMessageObjects.size) {
					val messageObject = foundMessageObjects[lastReturnedNum]
					notificationCenter.postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.id, mask, messageObject.dialogId, lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1], jumpToMessage)
					return
				}
				else {
					if (messagesSearchEndReached[0] && mergeDialogId == 0L && messagesSearchEndReached[1]) {
						lastReturnedNum--
						return
					}

					firstQuery = false
					query = lastSearchQuery

					val messageObject = foundMessageObjects[foundMessageObjects.size - 1]

					if (messageObject.dialogId == dialogId && !messagesSearchEndReached[0]) {
						maxId = messageObject.id
						queryWithDialog = dialogId
					}
					else {
						if (messageObject.dialogId == mergeDialogId) {
							maxId = messageObject.id
						}

						queryWithDialog = mergeDialogId

						messagesSearchEndReached[1] = false
					}
				}
			}
			else if (direction == 2) {
				lastReturnedNum--

				if (lastReturnedNum < 0) {
					lastReturnedNum = 0
					return
				}

				if (lastReturnedNum >= foundMessageObjects.size) {
					lastReturnedNum = foundMessageObjects.size - 1
				}

				val messageObject = foundMessageObjects[lastReturnedNum]

				notificationCenter.postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.id, mask, messageObject.dialogId, lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1], jumpToMessage)

				return
			}
			else {
				return
			}
		}
		else if (firstQuery) {
			messagesSearchEndReached[1] = false
			messagesSearchEndReached[0] = false
			messagesSearchCount[1] = 0
			messagesSearchCount[0] = 0
			foundMessageObjects.clear()
			searchResultMessagesMap[0].clear()
			searchResultMessagesMap[1].clear()
			notificationCenter.postNotificationName(NotificationCenter.chatSearchResultsLoading, guid)
		}

		if (messagesSearchEndReached[0] && !messagesSearchEndReached[1] && mergeDialogId != 0L) {
			queryWithDialog = mergeDialogId
		}

		if (queryWithDialog == dialogId && firstQuery) {
			if (mergeDialogId != 0L) {
				val inputPeer = messagesController.getInputPeer(mergeDialogId)

				val req = TL_messages_search()
				req.peer = inputPeer

				lastMergeDialogId = mergeDialogId

				req.limit = 1
				req.q = query

				if (user != null) {
					req.from_id = MessagesController.getInputPeer(user)
					req.flags = req.flags or 1
				}
				else if (chat != null) {
					req.from_id = MessagesController.getInputPeer(chat)
					req.flags = req.flags or 1
				}

				req.filter = TL_inputMessagesFilterEmpty()

				mergeReqId = connectionsManager.sendRequest(req, { response, _ ->
					AndroidUtilities.runOnUIThread {
						if (lastMergeDialogId == mergeDialogId) {
							mergeReqId = 0

							if (response is messages_Messages) {
								messagesSearchEndReached[1] = response.messages.isEmpty()
								messagesSearchCount[1] = if (response is TL_messages_messagesSlice) response.count else response.messages.size

								searchMessagesInChat(req.q, dialogId, mergeDialogId, guid, direction, replyMessageId, true, user, chat, jumpToMessage)
							}
							else {
								messagesSearchEndReached[1] = true
								messagesSearchCount[1] = 0

								searchMessagesInChat(req.q, dialogId, mergeDialogId, guid, direction, replyMessageId, true, user, chat, jumpToMessage)
							}
						}
					}
				}, ConnectionsManager.RequestFlagFailOnServerErrors)

				return
			}
			else {
				lastMergeDialogId = 0
				messagesSearchEndReached[1] = true
				messagesSearchCount[1] = 0
			}
		}

		val req = TL_messages_search()
		req.peer = messagesController.getInputPeer(queryWithDialog)

		if (req.peer == null) {
			return
		}

		lastGuid = guid
		lastDialogId = dialogId
		lastSearchUser = user
		lastSearchChat = chat
		lastReplyMessageId = replyMessageId

		req.limit = 21
		req.q = query ?: ""
		req.offset_id = maxId

		if (user != null) {
			req.from_id = MessagesController.getInputPeer(user)
			req.flags = req.flags or 1
		}
		else if (chat != null) {
			req.from_id = MessagesController.getInputPeer(chat)
			req.flags = req.flags or 1
		}

		if (lastReplyMessageId != 0) {
			req.top_msg_id = lastReplyMessageId
			req.flags = req.flags or 2
		}

		req.filter = TL_inputMessagesFilterEmpty()

		val currentReqId = ++lastReqId

		lastSearchQuery = query

		val queryWithDialogFinal = queryWithDialog
		val finalQuery = query

		reqId = connectionsManager.sendRequest(req, { response, _ ->
			val messageObjects = ArrayList<MessageObject>()

			if (response is messages_Messages) {
				val n = min(response.messages.size.toDouble(), 20.0).toInt()

				for (a in 0 until n) {
					val message = response.messages[a]
					val messageObject = MessageObject(currentAccount, message, generateLayout = false, checkMediaExists = false)
					messageObject.setQuery(finalQuery)
					messageObjects.add(messageObject)
				}
			}

			AndroidUtilities.runOnUIThread {
				if (currentReqId == lastReqId) {
					reqId = 0

					if (!jumpToMessage) {
						loadingMoreSearchMessages = false
					}

					if (response is messages_Messages) {
						run {
							var a = 0

							while (a < response.messages.size) {
								val message = response.messages[a]

								if (message is TL_messageEmpty || message.action is TL_messageActionHistoryClear) {
									response.messages.removeAt(a)
									a--
								}

								a++
							}
						}

						messagesStorage.putUsersAndChats(response.users, response.chats, true, true)
						messagesController.putUsers(response.users, false)
						messagesController.putChats(response.chats, false)

						if (req.offset_id == 0 && queryWithDialogFinal == dialogId) {
							lastReturnedNum = 0
							foundMessageObjects.clear()
							searchResultMessagesMap[0].clear()
							searchResultMessagesMap[1].clear()
							messagesSearchCount[0] = 0
							notificationCenter.postNotificationName(NotificationCenter.chatSearchResultsLoading, guid)
						}

						var added = false
						val n = min(response.messages.size.toDouble(), 20.0).toInt()

						for (a in 0 until n) {
							added = true
							val messageObject = messageObjects[a]
							foundMessageObjects.add(messageObject)
							searchResultMessagesMap[if (queryWithDialogFinal == dialogId) 0 else 1].put(messageObject.id, messageObject)
						}

						messagesSearchEndReached[if (queryWithDialogFinal == dialogId) 0 else 1] = response.messages.size < 21
						messagesSearchCount[if (queryWithDialogFinal == dialogId) 0 else 1] = if (response is TL_messages_messagesSlice || response is TL_messages_channelMessages) response.count else response.messages.size

						if (foundMessageObjects.isEmpty()) {
							notificationCenter.postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, 0, mask, 0L, 0, 0, jumpToMessage)
						}
						else {
							if (added) {
								if (lastReturnedNum >= foundMessageObjects.size) {
									lastReturnedNum = foundMessageObjects.size - 1
								}

								val messageObject = foundMessageObjects[lastReturnedNum]

								notificationCenter.postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.id, mask, messageObject.dialogId, lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1], jumpToMessage)
							}
						}

						if (queryWithDialogFinal == dialogId && messagesSearchEndReached[0] && mergeDialogId != 0L && !messagesSearchEndReached[1]) {
							searchMessagesInChat(lastSearchQuery, dialogId, mergeDialogId, guid, 0, replyMessageId, true, user, chat, jumpToMessage)
						}
					}
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)
	}

	fun loadMedia(dialogId: Long, count: Int, maxId: Int, minId: Int, type: Int, fromCache: Int, classGuid: Int, requestIndex: Int) {
		val isChannel = DialogObject.isChatDialog(dialogId) && ChatObject.isChannel(-dialogId, currentAccount)

		if (BuildConfig.DEBUG) {
			FileLog.d("load media did $dialogId count = $count max_id $maxId type = $type cache = $fromCache classGuid = $classGuid")
		}

		if ((fromCache != 0 || DialogObject.isEncryptedDialog(dialogId))) {
			loadMediaDatabase(dialogId, count, maxId, minId, type, classGuid, fromCache, requestIndex)
		}
		else {
			val req = TL_messages_search()
			req.limit = count

			if (minId != 0) {
				req.offset_id = minId
				req.add_offset = -count
			}
			else {
				req.offset_id = maxId
			}

			when (type) {
				MEDIA_PHOTOVIDEO -> req.filter = TL_inputMessagesFilterPhotoVideo()
				MEDIA_PHOTOS_ONLY -> req.filter = TL_inputMessagesFilterPhotos()
				MEDIA_VIDEOS_ONLY -> req.filter = TL_inputMessagesFilterVideo()
				MEDIA_FILE -> req.filter = TL_inputMessagesFilterDocument()
				MEDIA_AUDIO -> req.filter = TL_inputMessagesFilterRoundVoice()
				MEDIA_URL -> req.filter = TL_inputMessagesFilterUrl()
				MEDIA_MUSIC -> req.filter = TL_inputMessagesFilterMusic()
				MEDIA_GIF -> req.filter = TL_inputMessagesFilterGif()
			}

			req.q = ""
			req.peer = messagesController.getInputPeer(dialogId)

			if (req.peer == null) {
				return
			}

			val reqId = connectionsManager.sendRequest(req) { response, _ ->
				if (response is messages_Messages) {
					messagesController.removeDeletedMessagesFromArray(dialogId, response.messages)

					val topReached = if (minId != 0) {
						response.messages.size <= 1
					}
					else {
						response.messages.size == 0
					}

					processLoadedMedia(response, dialogId, count, maxId, minId, type, 0, classGuid, topReached, requestIndex)
				}
			}

			connectionsManager.bindRequestToGuid(reqId, classGuid)
		}
	}

	fun getMediaCounts(dialogId: Long, classGuid: Int) {
		messagesStorage.storageQueue.postRunnable {
			try {
				val counts = intArrayOf(-1, -1, -1, -1, -1, -1, -1, -1)
				val countsFinal = intArrayOf(-1, -1, -1, -1, -1, -1, -1, -1)
				val old = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
				var cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT type, count, old FROM media_counts_v2 WHERE uid = %d", dialogId))

				while (cursor.next()) {
					val type = cursor.intValue(0)

					if (type in 0..<MEDIA_TYPES_COUNT) {
						counts[type] = cursor.intValue(1)
						countsFinal[type] = counts[type]
						old[type] = cursor.intValue(2)
					}
				}

				cursor.dispose()

				if (DialogObject.isEncryptedDialog(dialogId)) {
					for (a in counts.indices) {
						if (counts[a] == -1) {
							cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM media_v4 WHERE uid = %d AND type = %d LIMIT 1", dialogId, a))

							if (cursor.next()) {
								counts[a] = cursor.intValue(0)
							}
							else {
								counts[a] = 0
							}

							cursor.dispose()

							putMediaCountDatabase(dialogId, a, counts[a])
						}
					}

					AndroidUtilities.runOnUIThread {
						notificationCenter.postNotificationName(NotificationCenter.mediaCountsDidLoad, dialogId, counts)
					}
				}
				else {
					var missing = false

					val req = TL_messages_getSearchCounters()
					req.peer = messagesController.getInputPeer(dialogId)

					for (a in counts.indices) {
						if (req.peer == null) {
							counts[a] = 0
							continue
						}

						if (counts[a] == -1 || old[a] == 1) {
							when (a) {
								MEDIA_PHOTOVIDEO -> req.filters.add(TL_inputMessagesFilterPhotoVideo())
								MEDIA_FILE -> req.filters.add(TL_inputMessagesFilterDocument())
								MEDIA_AUDIO -> req.filters.add(TL_inputMessagesFilterRoundVoice())
								MEDIA_URL -> req.filters.add(TL_inputMessagesFilterUrl())
								MEDIA_MUSIC -> req.filters.add(TL_inputMessagesFilterMusic())
								MEDIA_PHOTOS_ONLY -> req.filters.add(TL_inputMessagesFilterPhotos())
								MEDIA_VIDEOS_ONLY -> req.filters.add(TL_inputMessagesFilterVideo())
								else -> req.filters.add(TL_inputMessagesFilterGif())
							}

							if (counts[a] == -1) {
								missing = true
							}
							else if (old[a] == 1) {
								counts[a] = -1
							}
						}
					}

					if (req.filters.isNotEmpty()) {
						val reqId = connectionsManager.sendRequest(req) { response, _ ->
							for (i in counts.indices) {
								if (counts[i] < 0) {
									counts[i] = 0
								}
							}

							if (response is Vector) {
								var a = 0
								val n = response.objects.size

								while (a < n) {
									val searchCounter = response.objects[a] as TL_messages_searchCounter

									val type = if (searchCounter.filter is TL_inputMessagesFilterPhotoVideo) {
										MEDIA_PHOTOVIDEO
									}
									else if (searchCounter.filter is TL_inputMessagesFilterDocument) {
										MEDIA_FILE
									}
									else if (searchCounter.filter is TL_inputMessagesFilterRoundVoice) {
										MEDIA_AUDIO
									}
									else if (searchCounter.filter is TL_inputMessagesFilterUrl) {
										MEDIA_URL
									}
									else if (searchCounter.filter is TL_inputMessagesFilterMusic) {
										MEDIA_MUSIC
									}
									else if (searchCounter.filter is TL_inputMessagesFilterGif) {
										MEDIA_GIF
									}
									else if (searchCounter.filter is TL_inputMessagesFilterPhotos) {
										MEDIA_PHOTOS_ONLY
									}
									else if (searchCounter.filter is TL_inputMessagesFilterVideo) {
										MEDIA_VIDEOS_ONLY
									}
									else {
										a++
										continue
									}

									counts[type] = searchCounter.count
									putMediaCountDatabase(dialogId, type, counts[type])
									a++
								}
							}

							AndroidUtilities.runOnUIThread {
								notificationCenter.postNotificationName(NotificationCenter.mediaCountsDidLoad, dialogId, counts)
							}
						}
						connectionsManager.bindRequestToGuid(reqId, classGuid)
					}

					if (!missing) {
						AndroidUtilities.runOnUIThread {
							notificationCenter.postNotificationName(NotificationCenter.mediaCountsDidLoad, dialogId, countsFinal)
						}
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun getMediaCount(dialogId: Long, type: Int, classGuid: Int, fromCache: Boolean) {
		if (fromCache || DialogObject.isEncryptedDialog(dialogId)) {
			getMediaCountDatabase(dialogId, type, classGuid)
		}
		else {
			val req = TL_messages_getSearchCounters()

			when (type) {
				MEDIA_PHOTOVIDEO -> req.filters.add(TL_inputMessagesFilterPhotoVideo())
				MEDIA_FILE -> req.filters.add(TL_inputMessagesFilterDocument())
				MEDIA_AUDIO -> req.filters.add(TL_inputMessagesFilterRoundVoice())
				MEDIA_URL -> req.filters.add(TL_inputMessagesFilterUrl())
				MEDIA_MUSIC -> req.filters.add(TL_inputMessagesFilterMusic())
				MEDIA_GIF -> req.filters.add(TL_inputMessagesFilterGif())
			}

			req.peer = messagesController.getInputPeer(dialogId)

			if (req.peer == null) {
				return
			}

			val reqId = connectionsManager.sendRequest(req) { response, _ ->
				if (response is Vector) {
					if (response.objects.isNotEmpty()) {
						val counter = response.objects[0] as TL_messages_searchCounter
						processLoadedMediaCount(counter.count, dialogId, type, classGuid, false, 0)
					}
				}
			}

			connectionsManager.bindRequestToGuid(reqId, classGuid)
		}
	}

	private fun processLoadedMedia(res: messages_Messages, dialogId: Long, count: Int, maxId: Int, minId: Int, type: Int, fromCache: Int, classGuid: Int, topReached: Boolean, requestIndex: Int) {
		if (BuildConfig.DEBUG) {
			FileLog.d("process load media did $dialogId count = $count max_id=$maxId min_id=$minId type = $type cache = $fromCache classGuid = $classGuid")
		}

		if (fromCache != 0 && ((res.messages.isEmpty() && minId == 0) || (res.messages.size <= 1 && minId != 0)) && !DialogObject.isEncryptedDialog(dialogId)) {
			if (fromCache == 2) {
				return
			}

			loadMedia(dialogId, count, maxId, minId, type, 0, classGuid, requestIndex)
		}
		else {
			if (fromCache == 0) {
				ImageLoader.saveMessagesThumbs(res.messages)
				messagesStorage.putUsersAndChats(res.users, res.chats, true, true)
				putMediaDatabase(dialogId, type, res.messages, maxId, minId, topReached)
			}

			Utilities.searchQueue.postRunnable {
				val usersDict = LongSparseArray<User>()

				for (u in res.users) {
					usersDict.put(u.id, u)
				}

				val objects = mutableListOf<MessageObject>()

				for (message in res.messages) {
					val messageObject = MessageObject(currentAccount, message, usersDict, generateLayout = true, checkMediaExists = false)
					messageObject.createStrippedThumb()
					objects.add(messageObject)
				}

				fileLoader.checkMediaExistence(objects)

				AndroidUtilities.runOnUIThread {
					val totalCount = res.count

					messagesController.putUsers(res.users, fromCache != 0)
					messagesController.putChats(res.chats, fromCache != 0)
					notificationCenter.postNotificationName(NotificationCenter.mediaDidLoad, dialogId, totalCount, objects, classGuid, type, topReached, minId != 0, requestIndex)
				}
			}
		}
	}

	private fun processLoadedMediaCount(count: Int, dialogId: Long, type: Int, classGuid: Int, fromCache: Boolean, old: Int) {
		AndroidUtilities.runOnUIThread {
			val isEncryptedDialog = DialogObject.isEncryptedDialog(dialogId)
			val reload = fromCache && (count == -1 || count == 0 && type == 2) && !isEncryptedDialog

			if (reload || old == 1 && !isEncryptedDialog) {
				getMediaCount(dialogId, type, classGuid, false)
			}

			if (!reload) {
				if (!fromCache) {
					putMediaCountDatabase(dialogId, type, count)
				}

				notificationCenter.postNotificationName(NotificationCenter.mediaCountDidLoad, dialogId, (if (fromCache && count == -1) 0 else count), fromCache, type)
			}
		}
	}

	private fun putMediaCountDatabase(uid: Long, type: Int, count: Int) {
		messagesStorage.storageQueue.postRunnable {
			try {
				val state2 = messagesStorage.database.executeFast("REPLACE INTO media_counts_v2 VALUES(?, ?, ?, ?)")
				state2.requery()
				state2.bindLong(1, uid)
				state2.bindInteger(2, type)
				state2.bindInteger(3, count)
				state2.bindInteger(4, 0)
				state2.step()
				state2.dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun getMediaCountDatabase(dialogId: Long, type: Int, classGuid: Int) {
		messagesStorage.storageQueue.postRunnable {
			try {
				var count = -1
				var old = 0
				var cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT count, old FROM media_counts_v2 WHERE uid = %d AND type = %d LIMIT 1", dialogId, type))

				if (cursor.next()) {
					count = cursor.intValue(0)
					old = cursor.intValue(1)
				}

				cursor.dispose()

				if (count == -1 && DialogObject.isEncryptedDialog(dialogId)) {
					cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM media_v4 WHERE uid = %d AND type = %d LIMIT 1", dialogId, type))

					if (cursor.next()) {
						count = cursor.intValue(0)
					}

					cursor.dispose()

					if (count != -1) {
						putMediaCountDatabase(dialogId, type, count)
					}
				}

				processLoadedMediaCount(count, dialogId, type, classGuid, true, old)
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun loadMediaDatabase(uid: Long, count: Int, maxId: Int, minId: Int, type: Int, classGuid: Int, fromCache: Int, requestIndex: Int) {
		@Suppress("UNUSED_VALUE") val runnable: Runnable = object : Runnable {
			override fun run() {
				var topReached = false
				val res = TL_messages_messages()

				try {
					val usersToLoad = mutableListOf<Long>()
					val chatsToLoad = mutableListOf<Long>()
					val countToLoad = count + 1
					var cursor: SQLiteCursor
					val database = messagesStorage.database
					var isEnd = false
					var reverseMessages = false

					if (!DialogObject.isEncryptedDialog(uid)) {
						if (minId == 0) {
							cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM media_holes_v2 WHERE uid = %d AND type = %d AND start IN (0, 1)", uid, type))

							if (cursor.next()) {
								isEnd = cursor.intValue(0) == 1
							}
							else {
								cursor.dispose()

								cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM media_v4 WHERE uid = %d AND type = %d AND mid > 0", uid, type))

								if (cursor.next()) {
									val mid = cursor.intValue(0)

									if (mid != 0) {
										val state = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)")
										state.requery()
										state.bindLong(1, uid)
										state.bindInteger(2, type)
										state.bindInteger(3, 0)
										state.bindInteger(4, mid)
										state.step()
										state.dispose()
									}
								}
							}
							cursor.dispose()
						}

						var holeMessageId = 0

						if (maxId != 0) {
							@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE") var startHole = 0

							cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM media_holes_v2 WHERE uid = %d AND type = %d AND start <= %d ORDER BY end DESC LIMIT 1", uid, type, maxId))

							if (cursor.next()) {
								startHole = cursor.intValue(0)
								holeMessageId = cursor.intValue(1)
							}

							cursor.dispose()

							if (holeMessageId > 1) {
								cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > 0 AND mid < %d AND mid >= %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, maxId, holeMessageId, type, countToLoad))
								isEnd = false
							}
							else {
								cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > 0 AND mid < %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, maxId, type, countToLoad))
							}
						}
						else if (minId != 0) {
							var startHole = 0

							cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM media_holes_v2 WHERE uid = %d AND type = %d AND end >= %d ORDER BY end ASC LIMIT 1", uid, type, minId))

							if (cursor.next()) {
								startHole = cursor.intValue(0)
								holeMessageId = cursor.intValue(1)
							}

							cursor.dispose()
							reverseMessages = true

							if (startHole > 1) {
								cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > 0 AND mid >= %d AND mid <= %d AND type = %d ORDER BY date ASC, mid ASC LIMIT %d", uid, minId, startHole, type, countToLoad))
							}
							else {
								isEnd = true
								cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > 0 AND mid >= %d AND type = %d ORDER BY date ASC, mid ASC LIMIT %d", uid, minId, type, countToLoad))
							}
						}
						else {
							cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(end) FROM media_holes_v2 WHERE uid = %d AND type = %d", uid, type))

							if (cursor.next()) {
								holeMessageId = cursor.intValue(0)
							}

							cursor.dispose()

							cursor = if (holeMessageId > 1) {
								database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid >= %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, holeMessageId, type, countToLoad))
							}
							else {
								database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > 0 AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, type, countToLoad))
							}
						}
					}
					else {
						isEnd = true

						cursor = if (maxId != 0) {
							database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_v4 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid > %d AND type = %d ORDER BY m.mid ASC LIMIT %d", uid, maxId, type, countToLoad))
						}
						else if (minId != 0) {
							database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_v4 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid < %d AND type = %d ORDER BY m.mid DESC LIMIT %d", uid, minId, type, countToLoad))
						}
						else {
							database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_v4 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid WHERE m.uid = %d AND type = %d ORDER BY m.mid ASC LIMIT %d", uid, type, countToLoad))
						}
					}

					while (cursor.next()) {
						val data = cursor.byteBufferValue(0)

						if (data != null) {
							val message = Message.TLdeserialize(data, data.readInt32(false), false)

							if (message != null) {
								message.readAttachPath(data, userConfig.clientUserId)

								data.reuse()

								message.id = cursor.intValue(1)
								message.dialog_id = uid

								if (DialogObject.isEncryptedDialog(uid)) {
									message.random_id = cursor.longValue(2)
								}

								if (reverseMessages) {
									res.messages.add(0, message)
								}
								else {
									res.messages.add(message)
								}

								MessagesStorage.addUsersAndChatsFromMessage(message, ArrayList(usersToLoad), ArrayList(chatsToLoad), null)
							}
							else {
								data.reuse()
							}
						}
					}

					cursor.dispose()

					if (usersToLoad.isNotEmpty()) {
						messagesStorage.getUsersInternal(usersToLoad.joinToString(","), res.users)
					}

					if (chatsToLoad.isNotEmpty()) {
						messagesStorage.getChatsInternal(chatsToLoad.joinToString(","), res.chats)
					}

					if (res.messages.size > count && minId == 0) {
						res.messages.removeAt(res.messages.size - 1)
					}
					else {
						topReached = if (minId != 0) {
							false
						}
						else {
							isEnd
						}
					}
				}
				catch (e: Exception) {
					res.messages.clear()
					res.chats.clear()
					res.users.clear()

					FileLog.e(e)
				}
				finally {
					val task: Runnable = this

					AndroidUtilities.runOnUIThread {
						messagesStorage.completeTaskForGuid(task, classGuid)
					}

					processLoadedMedia(res, uid, count, maxId, minId, type, fromCache, classGuid, topReached, requestIndex)
				}
			}
		}

		val messagesStorage = messagesStorage
		messagesStorage.storageQueue.postRunnable(runnable)
		messagesStorage.bindTaskToGuid(runnable, classGuid)
	}

	private fun putMediaDatabase(uid: Long, type: Int, messages: List<Message>, maxId: Int, minId: Int, topReached: Boolean) {
		messagesStorage.storageQueue.postRunnable {
			try {
				if (minId == 0 && (messages.isEmpty() || topReached)) {
					messagesStorage.doneHolesInMedia(uid, maxId, type)

					if (messages.isEmpty()) {
						return@postRunnable
					}
				}

				messagesStorage.database.beginTransaction()

				val state2 = messagesStorage.database.executeFast("REPLACE INTO media_v4 VALUES(?, ?, ?, ?, ?)")

				for (message in messages) {
					if (canAddMessageToMedia(message)) {
						state2.requery()

						val data = NativeByteBuffer(message.objectSize)

						message.serializeToStream(data)

						state2.bindInteger(1, message.id)
						state2.bindLong(2, uid)
						state2.bindInteger(3, message.date)
						state2.bindInteger(4, type)
						state2.bindByteBuffer(5, data)
						state2.step()

						data.reuse()
					}
				}

				state2.dispose()

				if (!topReached || maxId != 0 || minId != 0) {
					@Suppress("NAME_SHADOWING") val minId = if ((topReached && minId == 0)) 1 else messages[messages.size - 1].id

					if (minId != 0) {
						messagesStorage.closeHolesInMedia(uid, minId, messages[0].id, type)
					}
					else if (maxId != 0) {
						messagesStorage.closeHolesInMedia(uid, minId, maxId, type)
					}
					else {
						messagesStorage.closeHolesInMedia(uid, minId, Int.MAX_VALUE, type)
					}
				}

				messagesStorage.database.commitTransaction()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun loadMusic(dialogId: Long, maxId: Long, minId: Long) {
		messagesStorage.storageQueue.postRunnable {
			val arrayListBegin = mutableListOf<MessageObject>()
			val arrayListEnd = mutableListOf<MessageObject>()

			try {
				for (a in 0..1) {
					val arrayList = if (a == 0) arrayListBegin else arrayListEnd

					val cursor = if (a == 0) {
						if (!DialogObject.isEncryptedDialog(dialogId)) {
							messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid < %d AND type = %d ORDER BY date DESC, mid DESC LIMIT 1000", dialogId, maxId, MEDIA_MUSIC))
						}
						else {
							messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > %d AND type = %d ORDER BY date DESC, mid DESC LIMIT 1000", dialogId, maxId, MEDIA_MUSIC))
						}
					}
					else {
						if (!DialogObject.isEncryptedDialog(dialogId)) {
							messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > %d AND type = %d ORDER BY date DESC, mid DESC LIMIT 1000", dialogId, minId, MEDIA_MUSIC))
						}
						else {
							messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid < %d AND type = %d ORDER BY date DESC, mid DESC LIMIT 1000", dialogId, minId, MEDIA_MUSIC))
						}
					}

					while (cursor.next()) {
						val data = cursor.byteBufferValue(0)

						if (data != null) {
							val message = Message.TLdeserialize(data, data.readInt32(false), false)

							if (message != null) {
								message.readAttachPath(data, userConfig.clientUserId)

								data.reuse()

								if (MessageObject.isMusicMessage(message)) {
									message.id = cursor.intValue(1)
									message.dialog_id = dialogId

									arrayList.add(0, MessageObject(currentAccount, message, generateLayout = false, checkMediaExists = true))
								}
							}
							else {
								data.reuse()
							}
						}
					}

					cursor.dispose()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			AndroidUtilities.runOnUIThread {
				notificationCenter.postNotificationName(NotificationCenter.musicDidLoad, dialogId, arrayListBegin, arrayListEnd)
			}
		}
	}

	//---------------- MEDIA END ----------------
	@JvmField
	var hints = mutableListOf<TL_topPeer>()

	var inlineBots = mutableListOf<TL_topPeer>()
	var loaded: Boolean = false
	var loading: Boolean = false

	fun buildShortcuts() {
		var maxShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(ApplicationLoader.applicationContext) - 2

		if (maxShortcuts <= 0) {
			maxShortcuts = 5
		}

		val hintsFinal = ArrayList<TL_topPeer>()

		if (SharedConfig.passcodeHash.isEmpty()) {
			for (a in hints.indices) {
				hintsFinal.add(hints[a])

				if (hintsFinal.size == maxShortcuts - 2) {
					break
				}
			}
		}

		Utilities.globalQueue.postRunnable {
			runCatching {
				if (SharedConfig.directShareHash == null) {
					SharedConfig.directShareHash = UUID.randomUUID().toString()
					ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putString("directShareHash2", SharedConfig.directShareHash).commit()
				}

				val currentShortcuts = ShortcutManagerCompat.getDynamicShortcuts(ApplicationLoader.applicationContext)
				val shortcutsToUpdate = mutableListOf<String>()
				val newShortcutsIds = mutableListOf<String>()
				val shortcutsToDelete = mutableListOf<String>()

				if (currentShortcuts.isNotEmpty()) {
					newShortcutsIds.add("compose")

					for (a in hintsFinal.indices) {
						val hint = hintsFinal[a]
						newShortcutsIds.add("did3_" + MessageObject.getPeerId(hint.peer))
					}

					for (a in currentShortcuts.indices) {
						val id = currentShortcuts[a].id

						if (!newShortcutsIds.remove(id)) {
							shortcutsToDelete.add(id)
						}

						shortcutsToUpdate.add(id)
					}

					if (newShortcutsIds.isEmpty() && shortcutsToDelete.isEmpty()) {
						return@postRunnable
					}
				}

				val intent = Intent(ApplicationLoader.applicationContext, LaunchActivity::class.java)
				intent.setAction("new_dialog")

				val arrayList = mutableListOf<ShortcutInfoCompat>()
				arrayList.add(ShortcutInfoCompat.Builder(ApplicationLoader.applicationContext, "compose").setShortLabel(ApplicationLoader.applicationContext.getString(R.string.NewConversationShortcut)).setLongLabel(ApplicationLoader.applicationContext.getString(R.string.NewConversationShortcut)).setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.shortcut_compose)).setIntent(intent).build())

				if (shortcutsToUpdate.contains("compose")) {
					ShortcutManagerCompat.updateShortcuts(ApplicationLoader.applicationContext, arrayList)
				}
				else {
					ShortcutManagerCompat.addDynamicShortcuts(ApplicationLoader.applicationContext, arrayList)
				}

				arrayList.clear()

				if (shortcutsToDelete.isNotEmpty()) {
					ShortcutManagerCompat.removeDynamicShortcuts(ApplicationLoader.applicationContext, shortcutsToDelete)
				}

				val category = HashSet<String>(1)
				category.add(SHORTCUT_CATEGORY)

				for (a in hintsFinal.indices) {
					val shortcutIntent = Intent(ApplicationLoader.applicationContext, OpenChatReceiver::class.java)
					val hint = hintsFinal[a]

					var user: User? = null
					var chat: Chat? = null
					val peerId = MessageObject.getPeerId(hint.peer)

					if (DialogObject.isUserDialog(peerId)) {
						shortcutIntent.putExtra("userId", peerId)
						user = messagesController.getUser(peerId)
					}
					else {
						chat = messagesController.getChat(-peerId)
						shortcutIntent.putExtra("chatId", -peerId)
					}

					if ((user == null || UserObject.isDeleted(user)) && chat == null) {
						continue
					}

					var name: String?
					var photo: FileLocation? = null

					if (user != null) {
						name = ContactsController.formatName(user.first_name, user.last_name)

						if (user.photo != null) {
							photo = user.photo?.photo_small
						}
					}
					else {
						name = chat?.title

						if (chat?.photo != null) {
							photo = chat.photo?.photo_small
						}
					}

					shortcutIntent.putExtra("currentAccount", currentAccount)
					shortcutIntent.setAction("com.tmessages.openchat$peerId")
					shortcutIntent.putExtra("dialogId", peerId)
					shortcutIntent.putExtra("hash", SharedConfig.directShareHash)
					shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

					var bitmap: Bitmap? = null

					if (photo != null) {
						try {
							val path = fileLoader.getPathToAttach(photo, true)

							bitmap = BitmapFactory.decodeFile(path.toString())

							if (bitmap != null) {
								val size = AndroidUtilities.dp(48f)
								val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
								val canvas = Canvas(result)

								if (roundPaint == null) {
									roundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
									bitmapRect = RectF()

									erasePaint = Paint(Paint.ANTI_ALIAS_FLAG)
									erasePaint?.setXfermode(PorterDuffXfermode(PorterDuff.Mode.CLEAR))

									roundPath = Path()
									roundPath?.addCircle((size / 2).toFloat(), (size / 2).toFloat(), (size / 2 - AndroidUtilities.dp(2f)).toFloat(), Path.Direction.CW)
									roundPath?.toggleInverseFillType()
								}

								bitmapRect?.set(AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(2f).toFloat(), AndroidUtilities.dp(46f).toFloat(), AndroidUtilities.dp(46f).toFloat())

								canvas.drawBitmap(bitmap, null, bitmapRect!!, roundPaint)
								canvas.drawPath(roundPath!!, erasePaint!!)

								runCatching {
									canvas.setBitmap(null)
								}

								bitmap = result
							}
						}
						catch (e: Throwable) {
							FileLog.e(e)
						}
					}

					val id = "did3_$peerId"

					if (name.isNullOrEmpty()) {
						name = " "
					}

					val builder = ShortcutInfoCompat.Builder(ApplicationLoader.applicationContext, id).setShortLabel(name).setLongLabel(name).setIntent(shortcutIntent)

					if (SharedConfig.directShare) {
						builder.setCategories(category)
					}

					if (bitmap != null) {
						builder.setIcon(IconCompat.createWithBitmap(bitmap))
					}
					else {
						builder.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.shortcut_user))
					}

					arrayList.add(builder.build())

					if (shortcutsToUpdate.contains(id)) {
						ShortcutManagerCompat.updateShortcuts(ApplicationLoader.applicationContext, arrayList)
					}
					else {
						ShortcutManagerCompat.addDynamicShortcuts(ApplicationLoader.applicationContext, arrayList)
					}

					arrayList.clear()
				}
			}
		}
	}

	fun loadHints(cache: Boolean) {
		if (loading || !userConfig.suggestContacts) {
			return
		}

		if (cache) {
			if (loaded) {
				return
			}

			loading = true

			messagesStorage.storageQueue.postRunnable {
				val hintsNew = mutableListOf<TL_topPeer>()
				val inlineBotsNew = mutableListOf<TL_topPeer>()
				val users = mutableListOf<User>()
				val chats = mutableListOf<Chat>()
				val selfUserId = userConfig.getClientUserId()

				try {
					val usersToLoad = mutableListOf<Long>()
					val chatsToLoad = mutableListOf<Long>()
					val cursor = messagesStorage.database.queryFinalized("SELECT did, type, rating FROM chat_hints WHERE 1 ORDER BY rating DESC")

					while (cursor.next()) {
						val did = cursor.longValue(0)

						if (did == selfUserId) {
							continue
						}

						val type = cursor.intValue(1)

						val peer = TL_topPeer()
						peer.rating = cursor.doubleValue(2)

						if (did > 0) {
							peer.peer = TL_peerUser()
							peer.peer.user_id = did
							usersToLoad.add(did)
						}
						else {
							peer.peer = TL_peerChat()
							peer.peer.chat_id = -did
							chatsToLoad.add(-did)
						}

						if (type == 0) {
							hintsNew.add(peer)
						}
						else if (type == 1) {
							inlineBotsNew.add(peer)
						}
					}

					cursor.dispose()

					if (usersToLoad.isNotEmpty()) {
						messagesStorage.getUsersInternal(usersToLoad.joinToString(","), users)
					}

					if (chatsToLoad.isNotEmpty()) {
						messagesStorage.getChatsInternal(chatsToLoad.joinToString(","), chats)
					}

					AndroidUtilities.runOnUIThread {
						messagesController.putUsers(users, true)
						messagesController.putChats(chats, true)

						loading = false
						loaded = true
						hints = hintsNew
						inlineBots = inlineBotsNew

						buildShortcuts()

						notificationCenter.postNotificationName(NotificationCenter.reloadHints)
						notificationCenter.postNotificationName(NotificationCenter.reloadInlineHints)

						if (abs((userConfig.lastHintsSyncTime - (System.currentTimeMillis() / 1000).toInt()).toDouble()) >= 24 * 60 * 60) {
							loadHints(false)
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}

			loaded = true
		}
		else {
			loading = true

			val req = TL_contacts_getTopPeers()
			req.hash = 0
			req.bots_pm = false
			req.correspondents = true
			req.groups = false
			req.channels = false
			req.bots_inline = true
			req.offset = 0
			req.limit = 20

			connectionsManager.sendRequest(req) { response, _ ->
				if (response is TL_contacts_topPeers) {
					AndroidUtilities.runOnUIThread {
						messagesController.putUsers(response.users, false)
						messagesController.putChats(response.chats, false)

						for (category in response.categories) {
							if (category.category is TL_topPeerCategoryBotsInline) {
								inlineBots = category.peers
								userConfig.botRatingLoadTime = (System.currentTimeMillis() / 1000).toInt()
							}
							else {
								hints = category.peers

								val selfUserId = userConfig.getClientUserId()

								for (b in hints.indices) {
									val topPeer = hints[b]

									if (topPeer.peer.user_id == selfUserId) {
										hints.removeAt(b)
										break
									}
								}

								userConfig.ratingLoadTime = (System.currentTimeMillis() / 1000).toInt()
							}
						}

						userConfig.saveConfig(false)

						buildShortcuts()

						notificationCenter.postNotificationName(NotificationCenter.reloadHints)
						notificationCenter.postNotificationName(NotificationCenter.reloadInlineHints)

						messagesStorage.storageQueue.postRunnable {
							try {
								messagesStorage.database.executeFast("DELETE FROM chat_hints WHERE 1").stepThis().dispose()
								messagesStorage.database.beginTransaction()

								messagesStorage.putUsersAndChats(response.users, response.chats, false, false)

								val state = messagesStorage.database.executeFast("REPLACE INTO chat_hints VALUES(?, ?, ?, ?)")

								for (a in response.categories.indices) {
									val category = response.categories[a]

									val type = if (category.category is TL_topPeerCategoryBotsInline) {
										1
									}
									else {
										0
									}

									for (b in category.peers.indices) {
										val peer = category.peers[b]
										state.requery()
										state.bindLong(1, MessageObject.getPeerId(peer.peer))
										state.bindInteger(2, type)
										state.bindDouble(3, peer.rating)
										state.bindInteger(4, 0)
										state.step()
									}
								}

								state.dispose()

								messagesStorage.database.commitTransaction()

								AndroidUtilities.runOnUIThread {
									userConfig.suggestContacts = true
									userConfig.lastHintsSyncTime = (System.currentTimeMillis() / 1000).toInt()
									userConfig.saveConfig(false)
								}
							}
							catch (e: Exception) {
								FileLog.e(e)
							}
						}
					}
				}
				else if (response is TL_contacts_topPeersDisabled) {
					AndroidUtilities.runOnUIThread {
						userConfig.suggestContacts = false
						userConfig.lastHintsSyncTime = (System.currentTimeMillis() / 1000).toInt()
						userConfig.saveConfig(false)
						clearTopPeers()
					}
				}
			}
		}
	}

	fun clearTopPeers() {
		hints.clear()
		inlineBots.clear()
		notificationCenter.postNotificationName(NotificationCenter.reloadHints)
		notificationCenter.postNotificationName(NotificationCenter.reloadInlineHints)

		messagesStorage.storageQueue.postRunnable {
			runCatching {
				messagesStorage.database.executeFast("DELETE FROM chat_hints WHERE 1").stepThis().dispose()
			}
		}

		buildShortcuts()
	}

	fun increaseInlineRating(uid: Long) {
		if (!userConfig.suggestContacts) {
			return
		}

		val dt = if (userConfig.botRatingLoadTime != 0) {
			max(1.0, (((System.currentTimeMillis() / 1000).toInt()) - userConfig.botRatingLoadTime).toDouble()).toInt()
		}
		else {
			60
		}

		var peer: TL_topPeer? = null

		for (a in inlineBots.indices) {
			val p = inlineBots[a]

			if (p.peer.user_id == uid) {
				peer = p
				break
			}
		}

		if (peer == null) {
			peer = TL_topPeer()
			peer.peer = TL_peerUser()
			peer.peer.user_id = uid

			inlineBots.add(peer)
		}

		peer.rating += exp((dt / messagesController.ratingDecay).toDouble())

		inlineBots.sortWith { lhs, rhs ->
			if (lhs.rating > rhs.rating) {
				return@sortWith -1
			}
			else if (lhs.rating < rhs.rating) {
				return@sortWith 1
			}
			0
		}

		if (inlineBots.size > 20) {
			inlineBots.removeAt(inlineBots.size - 1)
		}

		savePeer(uid, 1, peer.rating)

		notificationCenter.postNotificationName(NotificationCenter.reloadInlineHints)
	}

	fun removeInline(dialogId: Long) {
		for (a in inlineBots.indices) {
			if (inlineBots[a].peer.user_id == dialogId) {
				inlineBots.removeAt(a)

				val req = TL_contacts_resetTopPeerRating()
				req.category = TL_topPeerCategoryBotsInline()
				req.peer = messagesController.getInputPeer(dialogId)

				connectionsManager.sendRequest(req)

				deletePeer(dialogId, 1)

				notificationCenter.postNotificationName(NotificationCenter.reloadInlineHints)

				return
			}
		}
	}

	fun removePeer(uid: Long) {
		for (a in hints.indices) {
			if (hints[a].peer.user_id == uid) {
				hints.removeAt(a)

				notificationCenter.postNotificationName(NotificationCenter.reloadHints)

				val req = TL_contacts_resetTopPeerRating()
				req.category = TL_topPeerCategoryCorrespondents()
				req.peer = messagesController.getInputPeer(uid)

				deletePeer(uid, 0)

				connectionsManager.sendRequest(req)

				return
			}
		}
	}

	fun increasePeerRating(dialogId: Long) {
		if (!userConfig.suggestContacts) {
			return
		}

		if (!DialogObject.isUserDialog(dialogId)) {
			return
		}

		val user = messagesController.getUser(dialogId)

		if (user == null || user.bot || user.self) {
			return
		}

		messagesStorage.storageQueue.postRunnable {
			var dt = 0.0

			try {
				var lastTime = 0
				var lastMid = 0
				val cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT MAX(mid), MAX(date) FROM messages_v2 WHERE uid = %d AND out = 1", dialogId))

				if (cursor.next()) {
					lastMid = cursor.intValue(0)
					lastTime = cursor.intValue(1)
				}

				cursor.dispose()

				if (lastMid > 0 && userConfig.ratingLoadTime != 0) {
					dt = (lastTime - userConfig.ratingLoadTime).toDouble()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			val dtFinal = dt

			AndroidUtilities.runOnUIThread {
				var peer: TL_topPeer? = null

				for (a in hints.indices) {
					val p = hints[a]

					if (p.peer.user_id == dialogId) {
						peer = p
						break
					}
				}

				if (peer == null) {
					peer = TL_topPeer()
					peer.peer = TL_peerUser()
					peer.peer.user_id = dialogId

					hints.add(peer)
				}

				peer.rating += exp(dtFinal / messagesController.ratingDecay)

				hints.sortWith { lhs, rhs ->
					if (lhs.rating > rhs.rating) {
						return@sortWith -1
					}
					else if (lhs.rating < rhs.rating) {
						return@sortWith 1
					}
					0
				}

				savePeer(dialogId, 0, peer.rating)

				notificationCenter.postNotificationName(NotificationCenter.reloadHints)
			}
		}
	}

	private fun savePeer(did: Long, type: Int, rating: Double) {
		messagesStorage.storageQueue.postRunnable {
			try {
				val state = messagesStorage.database.executeFast("REPLACE INTO chat_hints VALUES(?, ?, ?, ?)")
				state.requery()
				state.bindLong(1, did)
				state.bindInteger(2, type)
				state.bindDouble(3, rating)
				state.bindInteger(4, System.currentTimeMillis().toInt() / 1000)
				state.step()
				state.dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun deletePeer(dialogId: Long, type: Int) {
		messagesStorage.storageQueue.postRunnable {
			try {
				messagesStorage.database.executeFast(String.format(Locale.US, "DELETE FROM chat_hints WHERE did = %d AND type = %d", dialogId, type)).stepThis().dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun createInternalShortcutIntent(dialogId: Long): Intent? {
		val shortcutIntent = Intent(ApplicationLoader.applicationContext, OpenChatReceiver::class.java)

		if (DialogObject.isEncryptedDialog(dialogId)) {
			val encryptedChatId = DialogObject.getEncryptedChatId(dialogId)
			shortcutIntent.putExtra("encId", encryptedChatId)
		}
		else if (DialogObject.isUserDialog(dialogId)) {
			shortcutIntent.putExtra("userId", dialogId)
		}
		else if (DialogObject.isChatDialog(dialogId)) {
			shortcutIntent.putExtra("chatId", -dialogId)
		}
		else {
			return null
		}

		shortcutIntent.putExtra("currentAccount", currentAccount)
		shortcutIntent.setAction("com.tmessages.openchat$dialogId")
		shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

		return shortcutIntent
	}

	fun installShortcut(dialogId: Long) {
		try {
			val shortcutIntent = createInternalShortcutIntent(dialogId)
			var user: User? = null
			var chat: Chat? = null

			if (DialogObject.isEncryptedDialog(dialogId)) {
				val encryptedChatId = DialogObject.getEncryptedChatId(dialogId)
				val encryptedChat = messagesController.getEncryptedChat(encryptedChatId) ?: return

				user = messagesController.getUser(encryptedChat.user_id)
			}
			else if (DialogObject.isUserDialog(dialogId)) {
				user = messagesController.getUser(dialogId)
			}
			else if (DialogObject.isChatDialog(dialogId)) {
				chat = messagesController.getChat(-dialogId)
			}
			else {
				return
			}

			if (user == null && chat == null) {
				return
			}

			val name: String?
			var photo: FileLocation? = null
			var overrideAvatar = false

			if (user != null) {
				if (UserObject.isReplyUser(user)) {
					name = ApplicationLoader.applicationContext.getString(R.string.RepliesTitle)
					overrideAvatar = true
				}
				else if (UserObject.isUserSelf(user)) {
					name = ApplicationLoader.applicationContext.getString(R.string.SavedMessages)
					overrideAvatar = true
				}
				else {
					name = ContactsController.formatName(user.first_name, user.last_name)
					if (user.photo != null) {
						photo = user.photo?.photo_small
					}
				}
			}
			else {
				name = chat?.title

				if (chat?.photo != null) {
					photo = chat.photo?.photo_small
				}
			}

			var bitmap: Bitmap? = null

			if (overrideAvatar || photo != null) {
				try {
					if (!overrideAvatar) {
						val path = fileLoader.getPathToAttach(photo, true)
						bitmap = BitmapFactory.decodeFile(path.toString())
					}

					if (overrideAvatar || bitmap != null) {
						val size = AndroidUtilities.dp(58f)

						val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
						result.eraseColor(Color.TRANSPARENT)

						val canvas = Canvas(result)

						if (overrideAvatar) {
							val avatarDrawable = AvatarDrawable(user)

							if (UserObject.isReplyUser(user)) {
								avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_REPLIES
							}
							else {
								avatarDrawable.avatarType = AvatarDrawable.AVATAR_TYPE_SAVED
							}

							avatarDrawable.setBounds(0, 0, size, size)
							avatarDrawable.draw(canvas)
						}
						else {
							val shader = BitmapShader(bitmap!!, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

							if (roundPaint == null) {
								roundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
								bitmapRect = RectF()
							}

							val scale = size / bitmap.width.toFloat()

							canvas.save()
							canvas.scale(scale, scale)

							roundPaint?.setShader(shader)
							bitmapRect?.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

							canvas.drawRoundRect(bitmapRect!!, bitmap.width.toFloat(), bitmap.height.toFloat(), roundPaint!!)
							canvas.restore()
						}

						val drawable = ResourcesCompat.getDrawable(ApplicationLoader.applicationContext.resources, R.drawable.book_logo, null)
						val w = AndroidUtilities.dp(15f)
						val left = size - w - AndroidUtilities.dp(2f)
						val top = size - w - AndroidUtilities.dp(2f)

						drawable?.setBounds(left, top, left + w, top + w)
						drawable?.draw(canvas)

						try {
							canvas.setBitmap(null)
						}
						catch (e: Exception) {
							// don't prompt, this will crash on 2.x
						}

						bitmap = result
					}
				}
				catch (e: Throwable) {
					FileLog.e(e)
				}
			}

			if (Build.VERSION.SDK_INT >= 26) {
				if (shortcutIntent != null) {
					val pinShortcutInfo = ShortcutInfoCompat.Builder(ApplicationLoader.applicationContext, "sdid_$dialogId").setShortLabel(name ?: "").setIntent(shortcutIntent)

					if (bitmap != null) {
						pinShortcutInfo.setIcon(IconCompat.createWithBitmap(bitmap))
					}
					else {
						if (user != null) {
							if (user.bot) {
								pinShortcutInfo.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_bot))
							}
							else {
								pinShortcutInfo.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_user))
							}
						}
						else {
							if (ChatObject.isChannel(chat) && !chat.megagroup) {
								pinShortcutInfo.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_channel))
							}
							else {
								pinShortcutInfo.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_group))
							}
						}
					}

					ShortcutManagerCompat.requestPinShortcut(ApplicationLoader.applicationContext, pinShortcutInfo.build(), null)
				}
			}
			else {
				val addIntent = Intent()

				if (bitmap != null) {
					addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap)
				}
				else {
					if (user != null) {
						if (user.bot) {
							addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_bot))
						}
						else {
							addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_user))
						}
					}
					else {
						if (ChatObject.isChannel(chat) && !chat.megagroup) {
							addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_channel))
						}
						else {
							addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_group))
						}
					}
				}

				addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
				addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
				addIntent.putExtra("duplicate", false)
				addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT")

				ApplicationLoader.applicationContext.sendBroadcast(addIntent)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	fun uninstallShortcut(dialogId: Long) {
		try {
			if (Build.VERSION.SDK_INT >= 26) {
				val arrayList = listOf("sdid_$dialogId", "ndid_$dialogId")

				ShortcutManagerCompat.removeDynamicShortcuts(ApplicationLoader.applicationContext, arrayList)

				if (Build.VERSION.SDK_INT >= 30) {
					val shortcutManager = ApplicationLoader.applicationContext.getSystemService(ShortcutManager::class.java)
					shortcutManager.removeLongLivedShortcuts(arrayList)
				}
			}
			else {
				var user: User? = null
				var chat: Chat? = null

				if (DialogObject.isEncryptedDialog(dialogId)) {
					val encryptedChatId = DialogObject.getEncryptedChatId(dialogId)
					val encryptedChat = messagesController.getEncryptedChat(encryptedChatId) ?: return

					user = messagesController.getUser(encryptedChat.user_id)
				}
				else if (DialogObject.isUserDialog(dialogId)) {
					user = messagesController.getUser(dialogId)
				}
				else if (DialogObject.isChatDialog(dialogId)) {
					chat = messagesController.getChat(-dialogId)
				}
				else {
					return
				}

				if (user == null && chat == null) {
					return
				}

				val name = if (user != null) {
					ContactsController.formatName(user.first_name, user.last_name)
				}
				else {
					chat?.title ?: ""
				}

				val addIntent = Intent()
				addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, createInternalShortcutIntent(dialogId))
				addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
				addIntent.putExtra("duplicate", false)
				addIntent.setAction("com.android.launcher.action.UNINSTALL_SHORTCUT")

				ApplicationLoader.applicationContext.sendBroadcast(addIntent)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private val loadingPinnedMessages = LongSparseArray<Boolean>()

	fun loadPinnedMessages(dialogId: Long, maxId: Int, fallback: Int) {
		if (loadingPinnedMessages.indexOfKey(dialogId) >= 0) {
			return
		}

		loadingPinnedMessages.put(dialogId, true)

		val req = TL_messages_search()
		req.peer = messagesController.getInputPeer(dialogId)
		req.limit = 40
		req.offset_id = maxId
		req.q = ""
		req.filter = TL_inputMessagesFilterPinned()

		connectionsManager.sendRequest(req) { response, _ ->
			val ids = mutableListOf<Int>()
			val messages = mutableMapOf<Int, MessageObject>()
			var totalCount = 0
			val endReached: Boolean

			if (response is messages_Messages) {
				val usersDict = LongSparseArray<User>()

				for (user in response.users) {
					usersDict.put(user.id, user)
				}

				val chatsDict = LongSparseArray<Chat>()

				for (chat in response.chats) {
					chatsDict.put(chat.id, chat)
				}

				messagesStorage.putUsersAndChats(response.users, response.chats, true, true)

				messagesController.putUsers(response.users, false)
				messagesController.putChats(response.chats, false)

				var a = 0
				val n = response.messages.size

				while (a < n) {
					val message = response.messages[a]

					if (message is TL_messageService || message is TL_messageEmpty) {
						a++
						continue
					}

					ids.add(message.id)
					messages[message.id] = MessageObject(currentAccount, message, usersDict, chatsDict, generateLayout = false, checkMediaExists = false)
					a++
				}

				if (fallback != 0 && ids.isEmpty()) {
					ids.add(fallback)
				}

				endReached = response.messages.size < req.limit
				totalCount = max(response.count.toDouble(), response.messages.size.toDouble()).toInt()
			}
			else {
				if (fallback != 0) {
					ids.add(fallback)
					totalCount = 1
				}
				endReached = false
			}

			messagesStorage.updatePinnedMessages(dialogId, ids, true, totalCount, maxId, endReached, messages)

			AndroidUtilities.runOnUIThread {
				loadingPinnedMessages.remove(dialogId)
			}
		}
	}

	fun loadPinnedMessages(dialogId: Long, channelId: Long, mids: List<Int>, useQueue: Boolean): List<MessageObject>? {
		if (useQueue) {
			messagesStorage.storageQueue.postRunnable {
				loadPinnedMessageInternal(dialogId, channelId, mids, false)
			}
		}
		else {
			return loadPinnedMessageInternal(dialogId, channelId, mids, true)
		}

		return null
	}

	private fun loadPinnedMessageInternal(dialogId: Long, channelId: Long, mids: List<Int>, returnValue: Boolean): List<MessageObject>? {
		try {
			val midsCopy = mids.toMutableList()
			val longIds: CharSequence

			if (channelId != 0L) {
				val builder = StringBuilder()
				var a = 0
				val n = mids.size

				while (a < n) {
					val messageId = mids[a]

					if (builder.isNotEmpty()) {
						builder.append(",")
					}

					builder.append(messageId)

					a++
				}

				longIds = builder
			}
			else {
				longIds = TextUtils.join(",", mids)
			}

			val results = mutableListOf<Message>()
			val users = mutableListOf<User>()
			val chats = mutableListOf<Chat>()
			val usersToLoad = mutableListOf<Long>()
			val chatsToLoad = mutableListOf<Long>()
			val selfUserId = userConfig.clientUserId

			var cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date FROM messages_v2 WHERE mid IN (%s) AND uid = %d", longIds, dialogId))

			while (cursor.next()) {
				val data = cursor.byteBufferValue(0)

				if (data != null) {
					val result = Message.TLdeserialize(data, data.readInt32(false), false)

					if (result != null && result.action !is TL_messageActionHistoryClear) {
						result.readAttachPath(data, selfUserId)
						result.id = cursor.intValue(1)
						result.date = cursor.intValue(2)
						result.dialog_id = dialogId

						MessagesStorage.addUsersAndChatsFromMessage(result, ArrayList(usersToLoad), ArrayList(chatsToLoad), null)

						results.add(result)

						midsCopy.remove(result.id)
					}

					data.reuse()
				}
			}

			cursor.dispose()

			if (midsCopy.isNotEmpty()) {
				cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT data FROM chat_pinned_v2 WHERE uid = %d AND mid IN (%s)", dialogId, midsCopy.joinToString(",")))

				while (cursor.next()) {
					val data = cursor.byteBufferValue(0)

					if (data != null) {
						val result = Message.TLdeserialize(data, data.readInt32(false), false)

						if (result != null && result.action !is TL_messageActionHistoryClear) {
							result.readAttachPath(data, selfUserId)
							result.dialog_id = dialogId

							MessagesStorage.addUsersAndChatsFromMessage(result, ArrayList(usersToLoad), ArrayList(chatsToLoad), null)

							results.add(result)

							midsCopy.remove(result.id)
						}

						data.reuse()
					}
				}

				cursor.dispose()
			}

			if (midsCopy.isNotEmpty()) {
				if (channelId != 0L) {
					val req = TL_channels_getMessages()
					req.channel = messagesController.getInputChannel(channelId)
					req.id = ArrayList(midsCopy)

					connectionsManager.sendRequest(req) { response, _ ->
						var ok = false

						if (response is messages_Messages) {
							removeEmptyMessages(response.messages)

							if (response.messages.isNotEmpty()) {
								ImageLoader.saveMessagesThumbs(response.messages)
								broadcastPinnedMessage(response.messages, response.users, response.chats, isCache = false, returnValue = false)
								messagesStorage.putUsersAndChats(response.users, response.chats, true, true)
								savePinnedMessages(dialogId, response.messages)
								ok = true
							}
						}

						if (!ok) {
							messagesStorage.updatePinnedMessages(dialogId, req.id, false, -1, 0, false, null)
						}
					}
				}
				else {
					val req = TL_messages_getMessages()
					req.id = ArrayList(midsCopy)

					connectionsManager.sendRequest(req) { response, _ ->
						var ok = false

						if (response is messages_Messages) {
							removeEmptyMessages(response.messages)

							if (response.messages.isNotEmpty()) {
								ImageLoader.saveMessagesThumbs(response.messages)
								broadcastPinnedMessage(response.messages, response.users, response.chats, isCache = false, returnValue = false)
								messagesStorage.putUsersAndChats(response.users, response.chats, true, true)
								savePinnedMessages(dialogId, response.messages)
								ok = true
							}
						}

						if (!ok) {
							messagesStorage.updatePinnedMessages(dialogId, req.id, false, -1, 0, false, null)
						}
					}
				}
			}

			if (results.isNotEmpty()) {
				if (usersToLoad.isNotEmpty()) {
					messagesStorage.getUsersInternal(usersToLoad.joinToString(","), users)
				}

				if (chatsToLoad.isNotEmpty()) {
					messagesStorage.getChatsInternal(chatsToLoad.joinToString(","), chats)
				}
				if (returnValue) {
					return broadcastPinnedMessage(results, users, chats, isCache = true, returnValue = true)
				}
				else {
					broadcastPinnedMessage(results, users, chats, isCache = true, returnValue = false)
				}
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		return null
	}

	private fun savePinnedMessages(dialogId: Long, arrayList: List<Message>) {
		if (arrayList.isEmpty()) {
			return
		}

		messagesStorage.storageQueue.postRunnable {
			try {
				messagesStorage.database.beginTransaction()
				//SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("UPDATE chat_pinned_v2 SET data = ? WHERE uid = ? AND mid = ?");
				val state = messagesStorage.database.executeFast("REPLACE INTO chat_pinned_v2 VALUES(?, ?, ?)")

				for (message in arrayList) {
					val data = NativeByteBuffer(message.objectSize)

					message.serializeToStream(data)

					state.requery()
					state.bindLong(1, dialogId)
					state.bindInteger(2, message.id)
					state.bindByteBuffer(3, data)
					state.step()

					data.reuse()
				}

				state.dispose()

				messagesStorage.database.commitTransaction()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun broadcastPinnedMessage(results: List<Message>, users: List<User>, chats: List<Chat>, isCache: Boolean, returnValue: Boolean): List<MessageObject>? {
		if (results.isEmpty()) {
			return null
		}

		val usersDict = LongSparseArray<User>()

		for (user in users) {
			usersDict.put(user.id, user)
		}

		val chatsDict = LongSparseArray<Chat>()

		for (chat in chats) {
			chatsDict.put(chat.id, chat)
		}

		val messageObjects = mutableListOf<MessageObject>()

		if (returnValue) {
			AndroidUtilities.runOnUIThread {
				messagesController.putUsers(users, isCache)
				messagesController.putChats(chats, isCache)
			}

			var checkedCount = 0

			for (message in results) {
				if (MessageObject.getMedia(message) is TL_messageMediaDocument || MessageObject.getMedia(message) is TL_messageMediaPhoto) {
					checkedCount++
				}
				messageObjects.add(MessageObject(currentAccount, message, usersDict, chatsDict, false, checkedCount < 30))
			}

			return messageObjects
		}
		else {
			AndroidUtilities.runOnUIThread {
				messagesController.putUsers(users, isCache)
				messagesController.putChats(chats, isCache)

				var checkedCount = 0

				for (message in results) {
					if (MessageObject.getMedia(message) is TL_messageMediaDocument || MessageObject.getMedia(message) is TL_messageMediaPhoto) {
						checkedCount++
					}

					messageObjects.add(MessageObject(currentAccount, message, usersDict, chatsDict, false, checkedCount < 30))
				}

				AndroidUtilities.runOnUIThread {
					notificationCenter.postNotificationName(NotificationCenter.didLoadPinnedMessages, messageObjects[0].dialogId, null, true, messageObjects, null, 0, -1, false)
				}
			}
		}

		return null
	}

	fun loadReplyMessagesForMessages(messages: List<MessageObject>?, dialogId: Long, scheduled: Boolean, callback: Runnable?) {
		if (DialogObject.isEncryptedDialog(dialogId)) {
			val replyMessages = mutableListOf<Long>()
			val replyMessageRandomOwners = LongSparseArray<MutableList<MessageObject>>()

			if (!messages.isNullOrEmpty()) {
				for (messageObject in messages) {
					if (messageObject.isReply && messageObject.replyMessageObject == null) {
						val id = messageObject.messageOwner?.reply_to?.reply_to_random_id ?: 0L
						var messageObjects = replyMessageRandomOwners[id]

						if (messageObjects == null) {
							messageObjects = mutableListOf()
							replyMessageRandomOwners.put(id, messageObjects)
						}

						messageObjects.add(messageObject)

						if (!replyMessages.contains(id)) {
							replyMessages.add(id)
						}
					}
				}
			}

			if (replyMessages.isEmpty()) {
				callback?.run()
				return
			}

			messagesStorage.storageQueue.postRunnable {
				try {
					val loadedMessages = mutableListOf<MessageObject>()
					val cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, m.date, r.random_id FROM randoms_v2 as r INNER JOIN messages_v2 as m ON r.mid = m.mid AND r.uid = m.uid WHERE r.random_id IN(%s)", TextUtils.join(",", replyMessages)))

					while (cursor.next()) {
						val data = cursor.byteBufferValue(0)

						if (data != null) {
							val message = Message.TLdeserialize(data, data.readInt32(false), false)

							if (message != null) {
								message.readAttachPath(data, userConfig.clientUserId)

								data.reuse()

								message.id = cursor.intValue(1)
								message.date = cursor.intValue(2)
								message.dialog_id = dialogId

								val value = cursor.longValue(3)
								val arrayList = replyMessageRandomOwners[value]
								replyMessageRandomOwners.remove(value)

								if (arrayList != null) {
									val messageObject = MessageObject(currentAccount, message, generateLayout = false, checkMediaExists = false)

									loadedMessages.add(messageObject)

									for (b in arrayList.indices) {
										val `object` = arrayList[b]
										`object`.replyMessageObject = messageObject
										`object`.messageOwner?.reply_to = TL_messageReplyHeader()
										`object`.messageOwner?.reply_to?.reply_to_msg_id = messageObject.id
									}
								}
							}
							else {
								data.reuse()
							}
						}
					}

					cursor.dispose()

					if (replyMessageRandomOwners.size() != 0) {
						for (b in 0 until replyMessageRandomOwners.size()) {
							val arrayList = replyMessageRandomOwners.valueAt(b)

							for (a in arrayList.indices) {
								val message = arrayList[a].messageOwner
								message?.reply_to?.reply_to_random_id = 0
							}
						}
					}

					AndroidUtilities.runOnUIThread {
						notificationCenter.postNotificationName(NotificationCenter.replyMessagesDidLoad, dialogId, loadedMessages, null)
					}

					callback?.run()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
		else {
			val replyMessageOwners = LongSparseArray<SparseArray<MutableList<MessageObject>>>()
			val dialogReplyMessagesIds = LongSparseArray<MutableList<Int>>()

			if (messages != null) {
				for (messageObject in messages) {
					if (messageObject.id > 0 && messageObject.isReply) {
						val messageId = messageObject.messageOwner?.reply_to?.reply_to_msg_id ?: 0
						var channelId: Long = 0

						if (messageObject.messageOwner?.reply_to?.reply_to_peer_id != null) {
							if (messageObject.messageOwner!!.reply_to!!.reply_to_peer_id.channel_id != 0L) {
								channelId = messageObject.messageOwner!!.reply_to!!.reply_to_peer_id.channel_id
							}
						}
						else if (messageObject.messageOwner!!.peer_id!!.channel_id != 0L) {
							channelId = messageObject.messageOwner!!.peer_id!!.channel_id
						}

						if (messageObject.replyMessageObject != null) {
							if (messageObject.replyMessageObject!!.messageOwner == null || messageObject.replyMessageObject!!.messageOwner!!.peer_id == null || messageObject.messageOwner is TL_messageEmpty) {
								continue
							}

							if (messageObject.replyMessageObject!!.messageOwner!!.peer_id!!.channel_id == channelId) {
								continue
							}
						}

						var sparseArray = replyMessageOwners[dialogId]
						var ids = dialogReplyMessagesIds[channelId]

						if (sparseArray == null) {
							sparseArray = SparseArray()
							replyMessageOwners.put(dialogId, sparseArray)
						}

						if (ids == null) {
							ids = mutableListOf()
							dialogReplyMessagesIds.put(channelId, ids)
						}

						var arrayList = sparseArray[messageId]

						if (arrayList == null) {
							arrayList = mutableListOf()

							sparseArray.put(messageId, arrayList)

							if (!ids.contains(messageId)) {
								ids.add(messageId)
							}
						}

						arrayList.add(messageObject)
					}
				}
			}

			if (replyMessageOwners.isEmpty) {
				callback?.run()
				return
			}

			messagesStorage.storageQueue.postRunnable {
				try {
					val result = mutableListOf<Message>()
					val users = mutableListOf<User>()
					val chats = mutableListOf<Chat>()
					val usersToLoad = mutableListOf<Long>()
					val chatsToLoad = mutableListOf<Long>()

					var b = 0
					val n2 = replyMessageOwners.size()

					while (b < n2) {
						val did = replyMessageOwners.keyAt(b)
						val ids = dialogReplyMessagesIds[did]

						if (ids == null) {
							b++
							continue
						}

						val cursor = if (scheduled) {
							messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM scheduled_messages_v2 WHERE mid IN(%s) AND uid = %d", TextUtils.join(",", ids), dialogId))
						}
						else {
							messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM messages_v2 WHERE mid IN(%s) AND uid = %d", TextUtils.join(",", ids), dialogId))
						}

						while (cursor.next()) {
							val data = cursor.byteBufferValue(0)

							if (data != null) {
								val message = Message.TLdeserialize(data, data.readInt32(false), false)

								if (message != null) {
									message.readAttachPath(data, userConfig.clientUserId)

									data.reuse()

									message.id = cursor.intValue(1)
									message.date = cursor.intValue(2)
									message.dialog_id = dialogId

									MessagesStorage.addUsersAndChatsFromMessage(message, ArrayList(usersToLoad), ArrayList(chatsToLoad), null)

									result.add(message)

									val channelId = message.peer_id?.channel_id ?: 0L
									val mids = dialogReplyMessagesIds[channelId]

									if (mids != null) {
										mids.remove(message.id)

										if (mids.isEmpty()) {
											dialogReplyMessagesIds.remove(channelId)
										}
									}
								}
								else {
									data.reuse()
								}
							}
						}

						cursor.dispose()

						b++
					}

					if (usersToLoad.isNotEmpty()) {
						messagesStorage.getUsersInternal(usersToLoad.joinToString(","), users)
					}

					if (chatsToLoad.isNotEmpty()) {
						messagesStorage.getChatsInternal(chatsToLoad.joinToString(","), chats)
					}

					broadcastReplyMessages(result, replyMessageOwners, users, chats, dialogId, true)

					if (!dialogReplyMessagesIds.isEmpty) {
						var a = 0
						val n = dialogReplyMessagesIds.size()
						while (a < n) {
							val channelId = dialogReplyMessagesIds.keyAt(a)

							if (scheduled) {
								val req = TL_messages_getScheduledMessages()
								req.peer = messagesController.getInputPeer(dialogId)
								req.id = dialogReplyMessagesIds.valueAt(a)?.let { ArrayList(it) }

								connectionsManager.sendRequest(req) { response, error ->
									if (error == null) {
										val messagesRes = response as messages_Messages?

										for (i in messagesRes!!.messages.indices) {
											val message = messagesRes.messages[i]

											if (message.dialog_id == 0L) {
												message.dialog_id = dialogId
											}
										}

										MessageObject.fixMessagePeer(messagesRes.messages, channelId)
										ImageLoader.saveMessagesThumbs(messagesRes.messages)

										broadcastReplyMessages(messagesRes.messages, replyMessageOwners, messagesRes.users, messagesRes.chats, dialogId, false)

										messagesStorage.putUsersAndChats(messagesRes.users, messagesRes.chats, true, true)

										saveReplyMessages(replyMessageOwners, messagesRes.messages, true)
									}

									if (callback != null) {
										AndroidUtilities.runOnUIThread(callback)
									}
								}
							}
							else if (channelId != 0L) {
								val req = TL_channels_getMessages()
								req.channel = messagesController.getInputChannel(channelId)
								req.id = dialogReplyMessagesIds.valueAt(a)?.let { ArrayList(it) }

								connectionsManager.sendRequest(req) { response, _ ->
									if (response is messages_Messages) {
										for (i in response.messages.indices) {
											val message = response.messages[i]

											if (message.dialog_id == 0L) {
												message.dialog_id = dialogId
											}
										}

										MessageObject.fixMessagePeer(response.messages, channelId)
										ImageLoader.saveMessagesThumbs(response.messages)

										broadcastReplyMessages(response.messages, replyMessageOwners, response.users, response.chats, dialogId, false)

										messagesStorage.putUsersAndChats(response.users, response.chats, true, true)

										saveReplyMessages(replyMessageOwners, response.messages, false)
									}

									if (callback != null) {
										AndroidUtilities.runOnUIThread(callback)
									}
								}
							}
							else {
								val req = TL_messages_getMessages()
								req.id = dialogReplyMessagesIds.valueAt(a)?.let { ArrayList(it) }

								connectionsManager.sendRequest(req) { response, _ ->
									if (response is messages_Messages) {
										for (i in response.messages.indices) {
											val message = response.messages[i]

											if (message.dialog_id == 0L) {
												message.dialog_id = dialogId
											}
										}

										ImageLoader.saveMessagesThumbs(response.messages)
										broadcastReplyMessages(response.messages, replyMessageOwners, response.users, response.chats, dialogId, false)

										messagesStorage.putUsersAndChats(response.users, response.chats, true, true)

										saveReplyMessages(replyMessageOwners, response.messages, false)
									}

									if (callback != null) {
										AndroidUtilities.runOnUIThread(callback)
									}
								}
							}

							a++
						}
					}
					else {
						if (callback != null) {
							AndroidUtilities.runOnUIThread(callback)
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
		}
	}

	private fun saveReplyMessages(replyMessageOwners: LongSparseArray<SparseArray<MutableList<MessageObject>>>, result: List<Message>, scheduled: Boolean) {
		messagesStorage.storageQueue.postRunnable {
			try {
				messagesStorage.database.beginTransaction()

				val state = if (scheduled) {
					messagesStorage.database.executeFast("UPDATE scheduled_messages_v2 SET replydata = ?, reply_to_message_id = ? WHERE mid = ? AND uid = ?")
				}
				else {
					messagesStorage.database.executeFast("UPDATE messages_v2 SET replydata = ?, reply_to_message_id = ? WHERE mid = ? AND uid = ?")
				}

				for (a in result.indices) {
					val message = result[a]
					val dialogId = MessageObject.getDialogId(message)
					val sparseArray = replyMessageOwners[dialogId] ?: continue
					val messageObjects = sparseArray[message.id]

					if (messageObjects != null) {
						val data = NativeByteBuffer(message.objectSize)

						message.serializeToStream(data)

						for (b in messageObjects.indices) {
							val messageObject = messageObjects[b]

							state.requery()
							state.bindByteBuffer(1, data)
							state.bindInteger(2, message.id)
							state.bindInteger(3, messageObject.id)
							state.bindLong(4, messageObject.dialogId)
							state.step()
						}

						data.reuse()
					}
				}

				state.dispose()

				messagesStorage.database.commitTransaction()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun broadcastReplyMessages(result: List<Message>, replyMessageOwners: LongSparseArray<SparseArray<MutableList<MessageObject>>>, users: List<User>, chats: List<Chat>, dialogId: Long, isCache: Boolean) {
		val usersDict = LongSparseArray<User>()

		for (user in users) {
			usersDict.put(user.id, user)
		}

		val chatsDict = LongSparseArray<Chat>()

		for (chat in chats) {
			chatsDict.put(chat.id, chat)
		}

		val messageObjects = mutableListOf<MessageObject>()

		for (msg in result) {
			messageObjects.add(MessageObject(currentAccount, msg, usersDict, chatsDict, generateLayout = false, checkMediaExists = false))
		}

		AndroidUtilities.runOnUIThread {
			messagesController.putUsers(users, isCache)
			messagesController.putChats(chats, isCache)

			var changed = false

			for (messageObject in messageObjects) {
				@Suppress("NAME_SHADOWING") val dialogId = messageObject.dialogId
				val sparseArray = replyMessageOwners[dialogId] ?: continue
				val arrayList = sparseArray[messageObject.id]

				if (arrayList != null) {
					for (b in arrayList.indices) {
						val m = arrayList[b]
						m.replyMessageObject = messageObject

						when (m.messageOwner?.action) {
							is TL_messageActionPinMessage -> m.generatePinMessageText(null, null)
							is TL_messageActionGameScore -> m.generateGameMessageText(null)
							is TL_messageActionPaymentSent -> m.generatePaymentSentMessageText(null)
						}
					}

					changed = true
				}
			}

			if (changed) {
				notificationCenter.postNotificationName(NotificationCenter.replyMessagesDidLoad, dialogId, messageObjects, replyMessageOwners)
			}
		}
	}

	fun substring(source: CharSequence?, start: Int, end: Int): CharSequence {
		return when (source) {
			is SpannableStringBuilder -> source.subSequence(start, end)
			is SpannedString -> source.subSequence(start, end)
			else -> TextUtils.substring(source, start, end)
		}
	}

	private fun addStyle(flags: Int, spanStart: Int, spanEnd: Int, entities: MutableList<MessageEntity>) {
		if ((flags and TextStyleSpan.FLAG_STYLE_SPOILER) != 0) {
			entities.add(setEntityStartEnd(TL_messageEntitySpoiler(), spanStart, spanEnd))
		}

		if ((flags and TextStyleSpan.FLAG_STYLE_BOLD) != 0) {
			entities.add(setEntityStartEnd(TL_messageEntityBold(), spanStart, spanEnd))
		}

		if ((flags and TextStyleSpan.FLAG_STYLE_ITALIC) != 0) {
			entities.add(setEntityStartEnd(TL_messageEntityItalic(), spanStart, spanEnd))
		}

		if ((flags and TextStyleSpan.FLAG_STYLE_MONO) != 0) {
			entities.add(setEntityStartEnd(TL_messageEntityCode(), spanStart, spanEnd))
		}

		if ((flags and TextStyleSpan.FLAG_STYLE_STRIKE) != 0) {
			entities.add(setEntityStartEnd(TL_messageEntityStrike(), spanStart, spanEnd))
		}

		if ((flags and TextStyleSpan.FLAG_STYLE_UNDERLINE) != 0) {
			entities.add(setEntityStartEnd(TL_messageEntityUnderline(), spanStart, spanEnd))
		}

		if ((flags and TextStyleSpan.FLAG_STYLE_QUOTE) != 0) {
			entities.add(setEntityStartEnd(TL_messageEntityBlockquote(), spanStart, spanEnd))
		}
	}

	private fun setEntityStartEnd(entity: MessageEntity, spanStart: Int, spanEnd: Int): MessageEntity {
		entity.offset = spanStart
		entity.length = spanEnd - spanStart
		return entity
	}

	fun getEntities(message: Array<CharSequence?>?, allowStrike: Boolean): List<MessageEntity>? {
		if (message == null || message[0] == null) {
			return null
		}

		val entities = mutableListOf<MessageEntity>()
		var index: Int
		var start = -1
		var lastIndex = 0
		var isPre = false
		val mono = "`"
		val pre = "```"

		while ((TextUtils.indexOf(message[0], if (!isPre) mono else pre, lastIndex).also { index = it }) != -1) {
			if (start == -1) {
				isPre = message[0]!!.length - index > 2 && message[0]!![index + 1] == '`' && message[0]!![index + 2] == '`'
				start = index
				lastIndex = index + (if (isPre) 3 else 1)
			}
			else {
				for (a in index + (if (isPre) 3 else 1) until message[0]!!.length) {
					if (message[0]!![a] == '`') {
						index++
					}
					else {
						break
					}
				}

				lastIndex = index + (if (isPre) 3 else 1)

				if (isPre) {
					var firstChar = (if (start > 0) message[0]!![start - 1] else 0.toChar()).code
					var replacedFirst = firstChar == ' '.code || firstChar == '\n'.code
					var startMessage = substring(message[0], 0, start - (if (replacedFirst) 1 else 0))
					val content = substring(message[0], start + 3, index)

					firstChar = (if (index + 3 < message[0]!!.length) message[0]!![index + 3] else 0.toChar()).code
					var endMessage = substring(message[0], index + 3 + (if (firstChar == ' '.code || firstChar == '\n'.code) 1 else 0), message[0]!!.length)

					if (startMessage.isNotEmpty()) {
						startMessage = AndroidUtilities.concat(startMessage, "\n")
					}
					else {
						replacedFirst = true
					}

					if (endMessage.isNotEmpty()) {
						endMessage = AndroidUtilities.concat("\n", endMessage)
					}

					if (content.isNotEmpty()) {
						message[0] = AndroidUtilities.concat(startMessage, content, endMessage)

						val entity = TL_messageEntityPre()
						entity.offset = start + (if (replacedFirst) 0 else 1)
						entity.length = index - start - 3 + (if (replacedFirst) 0 else 1)
						entity.language = ""

						entities.add(entity)

						lastIndex -= 6
					}
				}
				else {
					if (start + 1 != index) {
						message[0] = AndroidUtilities.concat(substring(message[0], 0, start), substring(message[0], start + 1, index), substring(message[0], index + 1, message[0]!!.length))

						val entity = TL_messageEntityCode()
						entity.offset = start
						entity.length = index - start - 1

						entities.add(entity)

						lastIndex -= 2
					}
				}

				start = -1
				isPre = false
			}
		}

		if (start != -1 && isPre) {
			message[0] = AndroidUtilities.concat(substring(message[0], 0, start), substring(message[0], start + 2, message[0]!!.length))

			val entity = TL_messageEntityCode()
			entity.offset = start
			entity.length = 1

			entities.add(entity)
		}

		if (message[0] is Spanned) {
			val spannable = message[0] as Spanned
			val spans = spannable.getSpans(0, message[0]!!.length, TextStyleSpan::class.java)

			if (spans != null && spans.isNotEmpty()) {
				for (a in spans.indices) {
					val span = spans[a]
					val spanStart = spannable.getSpanStart(span)
					val spanEnd = spannable.getSpanEnd(span)

					if (checkInclusion(spanStart, entities, false) || checkInclusion(spanEnd, entities, true) || checkIntersection(spanStart, spanEnd, entities)) {
						continue
					}

					addStyle(span.style.styleFlags, spanStart, spanEnd, entities)
				}
			}

			val spansMentions = spannable.getSpans(0, message[0]!!.length, URLSpanUserMention::class.java)

			if (spansMentions != null && spansMentions.isNotEmpty()) {
				for (b in spansMentions.indices) {
					val entity = TL_inputMessageEntityMentionName()
					entity.userId = messagesController.getInputUser(Utilities.parseLong(spansMentions[b].url))

					if (entity.userId != null) {
						entity.offset = spannable.getSpanStart(spansMentions[b])
						entity.length = (min(spannable.getSpanEnd(spansMentions[b]).toDouble(), message[0]!!.length.toDouble()) - entity.offset).toInt()

						if (message[0]?.get(entity.offset + entity.length - 1) == ' ') {
							entity.length--
						}

						entities.add(entity)
					}
				}
			}

			val spansUrlReplacement = spannable.getSpans(0, message[0]!!.length, URLSpanReplacement::class.java)

			if (spansUrlReplacement != null && spansUrlReplacement.isNotEmpty()) {
				for (b in spansUrlReplacement.indices) {
					val entity = TL_messageEntityTextUrl()
					entity.offset = spannable.getSpanStart(spansUrlReplacement[b])
					entity.length = (min(spannable.getSpanEnd(spansUrlReplacement[b]).toDouble(), message[0]!!.length.toDouble()) - entity.offset).toInt()
					entity.url = spansUrlReplacement[b].url

					entities.add(entity)

					val style = spansUrlReplacement[b].textStyleRun

					if (style != null) {
						addStyle(style.styleFlags, entity.offset, entity.offset + entity.length, entities)
					}
				}
			}

			val animatedEmojiSpans = spannable.getSpans(0, message[0]!!.length, AnimatedEmojiSpan::class.java)

			if (animatedEmojiSpans != null && animatedEmojiSpans.isNotEmpty()) {
				for (b in animatedEmojiSpans.indices) {
					val span = animatedEmojiSpans[b]

					if (span != null) {
						try {
							val entity = TL_messageEntityCustomEmoji()
							entity.offset = spannable.getSpanStart(span)
							entity.length = (min(spannable.getSpanEnd(span).toDouble(), message[0]!!.length.toDouble()) - entity.offset).toInt()
							entity.documentId = span.getDocumentId()
							entity.document = span.document

							entities.add(entity)
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}
			}

			if (spannable is Spannable) {
				AndroidUtilities.addLinks(spannable as Spannable?, Linkify.WEB_URLS, false, false)

				val spansUrl = spannable.getSpans(0, message[0]!!.length, URLSpan::class.java)

				if (spansUrl != null && spansUrl.isNotEmpty()) {
					for (b in spansUrl.indices) {
						if (spansUrl[b] is URLSpanReplacement || spansUrl[b] is URLSpanUserMention) {
							continue
						}

						val entity = TL_messageEntityUrl()
						entity.offset = spannable.getSpanStart(spansUrl[b])
						entity.length = (min(spannable.getSpanEnd(spansUrl[b]).toDouble(), message[0]!!.length.toDouble()) - entity.offset).toInt()
						entity.url = spansUrl[b].url

						entities.add(entity)
					}
				}
			}
		}

		val hashtags = findHashtags(message[0])

		if (!hashtags.isNullOrEmpty()) {
			entities.addAll(hashtags)
		}

		var cs = message[0]

		cs = parsePattern(cs, BOLD_PATTERN, entities) { TL_messageEntityBold() }
		cs = parsePattern(cs, ITALIC_PATTERN, entities) { TL_messageEntityItalic() }
		cs = parsePattern(cs, SPOILER_PATTERN, entities) { TL_messageEntitySpoiler() }

		if (allowStrike) {
			cs = parsePattern(cs, STRIKE_PATTERN, entities) { TL_messageEntityStrike() }
		}

		message[0] = cs

		return entities
	}

	private fun findHashtags(cs: CharSequence?): Set<TL_messageEntityHashtag>? {
		if (cs == null) {
			return null
		}

		val result = mutableSetOf<TL_messageEntityHashtag>()
		val pattern = Pattern.compile("(^|\\s)#\\D[\\w@.]+")
		val matcher = pattern.matcher(cs)

		while (matcher.find()) {
			var start = matcher.start()
			val end = matcher.end()

			if (cs[start] != '@' && cs[start] != '#') {
				start++
			}

			val entity = TL_messageEntityHashtag()
			entity.offset = start
			entity.length = end - start

			result.add(entity)
		}

		return result.toSet()
	}

	private fun parsePattern(cs: CharSequence?, pattern: Pattern, entities: MutableList<MessageEntity>, entityProvider: GenericProvider<Void?, MessageEntity>): CharSequence {
		@Suppress("NAME_SHADOWING") var cs = cs ?: ""
		val m = pattern.matcher(cs)
		var offset = 0

		while (m.find()) {
			val gr = m.group(1)
			var allowEntity = true

			if (cs is Spannable) {
				// check if it is inside a link: do not convert __ ** to styles inside links
				val spansUrl = cs.getSpans(m.start() - offset, m.end() - offset, URLSpan::class.java)

				if (spansUrl != null && spansUrl.isNotEmpty()) {
					allowEntity = false
				}
			}

			if (allowEntity) {
				cs = cs.subSequence(0, m.start() - offset).toString() + gr + cs.subSequence(m.end() - offset, cs.length)

				val entity = entityProvider.provide(null)
				entity.offset = m.start() - offset
				entity.length = gr?.length ?: 0
				entities.add(entity)
			}

			offset += m.end() - m.start() - (gr?.length ?: 0)
		}

		return cs
	}

	//---------------- MESSAGES END ----------------
	private val draftsFolderIds = LongSparseArray<Int>()

	@JvmField
	val drafts: LongSparseArray<SparseArray<DraftMessage>> = LongSparseArray()

	private val draftMessages = LongSparseArray<SparseArray<Message>>()
	private var inTransaction = false
	private var draftPreferences: SharedPreferences? = null
	private var loadingDrafts = false

	fun loadDraftsIfNeed() {
		if (userConfig.draftsLoaded || loadingDrafts) {
			return
		}

		loadingDrafts = true

		connectionsManager.sendRequest(TL_messages_getAllDrafts()) { response, error ->
			if (error != null) {
				AndroidUtilities.runOnUIThread {
					loadingDrafts = false
				}
			}
			else {
				messagesController.processUpdates(response as? Updates, false)

				AndroidUtilities.runOnUIThread {
					loadingDrafts = false

					val userConfig = userConfig
					userConfig.draftsLoaded = true
					userConfig.saveConfig(false)
				}
			}
		}
	}

	fun getDraftFolderId(dialogId: Long): Int {
		return draftsFolderIds[dialogId, 0]
	}

	fun setDraftFolderId(dialogId: Long, folderId: Int) {
		draftsFolderIds.put(dialogId, folderId)
	}

	fun clearDraftsFolderIds() {
		draftsFolderIds.clear()
	}

	fun getDraft(dialogId: Long, threadId: Int): DraftMessage? {
		val threads = drafts[dialogId] ?: return null
		return threads[threadId]
	}

	fun getDraftMessage(dialogId: Long, threadId: Int): Message? {
		val threads = draftMessages[dialogId] ?: return null
		return threads[threadId]
	}

	@JvmOverloads
	fun saveDraft(dialogId: Long, threadId: Int, message: CharSequence?, entities: List<MessageEntity>?, replyToMessage: Message?, noWebpage: Boolean, clean: Boolean = false) {
		val draftMessage = if (!message.isNullOrEmpty() || replyToMessage != null) {
			TL_draftMessage()
		}
		else {
			TL_draftMessageEmpty()
		}

		draftMessage.date = (System.currentTimeMillis() / 1000).toInt()
		draftMessage.message = message?.toString() ?: ""
		draftMessage.no_webpage = noWebpage

		if (replyToMessage != null) {
			draftMessage.reply_to_msg_id = replyToMessage.id
			draftMessage.flags = draftMessage.flags or 1
		}

		if (!entities.isNullOrEmpty()) {
			draftMessage.entities = ArrayList(entities)
			draftMessage.flags = draftMessage.flags or 8
		}

		val threads = drafts[dialogId]
		val currentDraft = threads?.get(threadId)

		if (!clean) {
			if (currentDraft != null && currentDraft.message == draftMessage.message && currentDraft.reply_to_msg_id == draftMessage.reply_to_msg_id && currentDraft.no_webpage == draftMessage.no_webpage || currentDraft == null && TextUtils.isEmpty(draftMessage.message) && draftMessage.reply_to_msg_id == 0) {
				return
			}
		}

		saveDraft(dialogId, threadId, draftMessage, replyToMessage, false)

		if (threadId == 0) {
			if (!DialogObject.isEncryptedDialog(dialogId)) {
				val req = TL_messages_saveDraft()
				req.peer = messagesController.getInputPeer(dialogId)

				if (req.peer == null) {
					return
				}

				req.message = draftMessage.message
				req.no_webpage = draftMessage.no_webpage
				req.reply_to_msg_id = draftMessage.reply_to_msg_id
				req.entities = draftMessage.entities
				req.flags = draftMessage.flags

				connectionsManager.sendRequest(req)
			}

			messagesController.sortDialogs(null)

			notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
		}
	}

	fun saveDraft(dialogId: Long, threadId: Int, draft: DraftMessage?, replyToMessage: Message?, fromServer: Boolean) {
		val editor = draftPreferences!!.edit()
		val messagesController = messagesController

		if (draft == null || draft is TL_draftMessageEmpty) {
			run {
				val threads = drafts[dialogId]

				if (threads != null) {
					threads.remove(threadId)

					if (threads.size() == 0) {
						drafts.remove(dialogId)
					}
				}
			}

			run {
				val threads = draftMessages[dialogId]

				if (threads != null) {
					threads.remove(threadId)

					if (threads.size() == 0) {
						draftMessages.remove(dialogId)
					}
				}
			}

			if (threadId == 0) {
				draftPreferences?.edit()?.remove("" + dialogId)?.remove("r_$dialogId")?.commit()
			}
			else {
				draftPreferences?.edit()?.remove("t_" + dialogId + "_" + threadId)?.remove("rt_" + dialogId + "_" + threadId)?.commit()
			}

			messagesController.removeDraftDialogIfNeed(dialogId)
		}
		else {
			var threads = drafts[dialogId]

			if (threads == null) {
				threads = SparseArray()
				drafts.put(dialogId, threads)
			}

			threads.put(threadId, draft)

			if (threadId == 0) {
				messagesController.putDraftDialogIfNeed(dialogId, draft)
			}
			try {
				val serializedData = SerializedData(draft.objectSize)

				draft.serializeToStream(serializedData)

				editor.putString(if (threadId == 0) ("" + dialogId) else ("t_" + dialogId + "_" + threadId), Utilities.bytesToHex(serializedData.toByteArray()))

				serializedData.cleanup()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		var threads = draftMessages[dialogId]

		if (replyToMessage == null) {
			if (threads != null) {
				threads.remove(threadId)

				if (threads.size() == 0) {
					draftMessages.remove(dialogId)
				}
			}

			if (threadId == 0) {
				editor.remove("r_$dialogId")
			}
			else {
				editor.remove("rt_" + dialogId + "_" + threadId)
			}
		}
		else {
			if (threads == null) {
				threads = SparseArray()
				draftMessages.put(dialogId, threads)
			}

			threads.put(threadId, replyToMessage)

			val serializedData = SerializedData(replyToMessage.objectSize)

			replyToMessage.serializeToStream(serializedData)

			editor.putString(if (threadId == 0) ("r_$dialogId") else ("rt_" + dialogId + "_" + threadId), Utilities.bytesToHex(serializedData.toByteArray()))

			serializedData.cleanup()
		}

		editor.commit()

		if (fromServer && threadId == 0) {
			if (draft != null && draft.reply_to_msg_id != 0 && replyToMessage == null) {
				var user: User? = null
				var chat: Chat? = null

				if (DialogObject.isUserDialog(dialogId)) {
					user = messagesController.getUser(dialogId)
				}
				else {
					chat = messagesController.getChat(-dialogId)
				}

				if (user != null || chat != null) {
					val channelId = if (ChatObject.isChannel(chat)) chat.id else 0
					val messageId = draft.reply_to_msg_id

					messagesStorage.storageQueue.postRunnable {
						try {
							var message: Message? = null
							val cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d and uid = %d", messageId, dialogId))

							if (cursor.next()) {
								val data = cursor.byteBufferValue(0)

								if (data != null) {
									message = Message.TLdeserialize(data, data.readInt32(false), false)
									message?.readAttachPath(data, userConfig.clientUserId)
									data.reuse()
								}
							}

							cursor.dispose()

							if (message == null) {
								if (channelId != 0L) {
									val req = TL_channels_getMessages()
									req.channel = messagesController.getInputChannel(channelId)
									req.id.add(messageId)

									connectionsManager.sendRequest(req) { response, _ ->
										if (response is messages_Messages) {
											if (response.messages.isNotEmpty()) {
												saveDraftReplyMessage(dialogId, threadId, response.messages[0])
											}
										}
									}
								}
								else {
									val req = TL_messages_getMessages()
									req.id.add(messageId)

									connectionsManager.sendRequest(req) { response, _ ->
										if (response is messages_Messages) {
											if (response.messages.isNotEmpty()) {
												saveDraftReplyMessage(dialogId, threadId, response.messages[0])
											}
										}
									}
								}
							}
							else {
								saveDraftReplyMessage(dialogId, threadId, message)
							}
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}
			}

			notificationCenter.postNotificationName(NotificationCenter.newDraftReceived, dialogId)
		}
	}

	private fun saveDraftReplyMessage(dialogId: Long, threadId: Int, message: Message?) {
		if (message == null) {
			return
		}

		AndroidUtilities.runOnUIThread {
			val threads = drafts[dialogId]
			val draftMessage = threads?.get(threadId)

			if (draftMessage != null && draftMessage.reply_to_msg_id == message.id) {
				var threads2 = draftMessages[dialogId]

				if (threads2 == null) {
					threads2 = SparseArray()
					draftMessages.put(dialogId, threads2)
				}

				threads2.put(threadId, message)

				val serializedData = SerializedData(message.objectSize)

				message.serializeToStream(serializedData)

				draftPreferences?.edit()?.putString(if (threadId == 0) ("r_$dialogId") else ("rt_" + dialogId + "_" + threadId), Utilities.bytesToHex(serializedData.toByteArray()))?.commit()

				notificationCenter.postNotificationName(NotificationCenter.newDraftReceived, dialogId)

				serializedData.cleanup()
			}
		}
	}

	fun clearAllDrafts(notify: Boolean) {
		drafts.clear()
		draftMessages.clear()
		draftsFolderIds.clear()
		draftPreferences?.edit()?.clear()?.commit()

		if (notify) {
			messagesController.sortDialogs(null)
			notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
		}
	}

	fun cleanDraft(dialogId: Long, threadId: Int, replyOnly: Boolean) {
		val threads2 = drafts[dialogId]
		val draftMessage = threads2?.get(threadId) ?: return

		if (!replyOnly) {
			run {
				val threads = drafts[dialogId]

				if (threads != null) {
					threads.remove(threadId)

					if (threads.size() == 0) {
						drafts.remove(dialogId)
					}
				}
			}

			run {
				val threads = draftMessages[dialogId]

				if (threads != null) {
					threads.remove(threadId)

					if (threads.size() == 0) {
						draftMessages.remove(dialogId)
					}
				}
			}

			if (threadId == 0) {
				draftPreferences?.edit()?.remove("" + dialogId)?.remove("r_$dialogId")?.commit()
				messagesController.sortDialogs(null)
				notificationCenter.postNotificationName(NotificationCenter.dialogsNeedReload)
			}
			else {
				draftPreferences?.edit()?.remove("t_" + dialogId + "_" + threadId)?.remove("rt_" + dialogId + "_" + threadId)?.commit()
			}
		}
		else if (draftMessage.reply_to_msg_id != 0) {
			draftMessage.reply_to_msg_id = 0
			draftMessage.flags = draftMessage.flags and 1.inv()

			saveDraft(dialogId, threadId, draftMessage.message, draftMessage.entities, null, draftMessage.no_webpage, true)
		}
	}

	fun beginTransaction() {
		inTransaction = true
	}

	fun endTransaction() {
		inTransaction = false
	}

	//---------------- DRAFT END ----------------
	private val botInfos = mutableMapOf<String, BotInfo>()
	private val botKeyboards = LongSparseArray<Message>()
	private val botKeyboardsByMids = SparseLongArray()

	fun clearBotKeyboard(dialogId: Long, messages: List<Int>?) {
		AndroidUtilities.runOnUIThread {
			if (messages != null) {
				for (a in messages.indices) {
					val did1 = botKeyboardsByMids[messages[a]]

					if (did1 != 0L) {
						botKeyboards.remove(did1)
						botKeyboardsByMids.delete(messages[a])
						notificationCenter.postNotificationName(NotificationCenter.botKeyboardDidLoad, null, did1)
					}
				}
			}
			else {
				botKeyboards.remove(dialogId)
				notificationCenter.postNotificationName(NotificationCenter.botKeyboardDidLoad, null, dialogId)
			}
		}
	}

	fun loadBotKeyboard(dialogId: Long) {
		val keyboard = botKeyboards[dialogId]

		if (keyboard != null) {
			notificationCenter.postNotificationName(NotificationCenter.botKeyboardDidLoad, keyboard, dialogId)
			return
		}

		messagesStorage.storageQueue.postRunnable {
			try {
				var botKeyboard: Message? = null
				val cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT info FROM bot_keyboard WHERE uid = %d", dialogId))

				if (cursor.next()) {
					val data: NativeByteBuffer?

					if (!cursor.isNull(0)) {
						data = cursor.byteBufferValue(0)

						if (data != null) {
							botKeyboard = Message.TLdeserialize(data, data.readInt32(false), false)

							data.reuse()
						}
					}
				}
				cursor.dispose()

				if (botKeyboard != null) {
					AndroidUtilities.runOnUIThread {
						notificationCenter.postNotificationName(NotificationCenter.botKeyboardDidLoad, botKeyboard, dialogId)
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	@Throws(SQLiteException::class)
	private fun loadBotInfoInternal(uid: Long, dialogId: Long): BotInfo? {
		var botInfo: BotInfo? = null
		val cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT info FROM bot_info_v2 WHERE uid = %d AND dialogId = %d", uid, dialogId))

		if (cursor.next()) {
			val data: NativeByteBuffer?

			if (!cursor.isNull(0)) {
				data = cursor.byteBufferValue(0)

				if (data != null) {
					botInfo = BotInfo.TLdeserialize(data, data.readInt32(false), false)

					data.reuse()
				}
			}
		}

		cursor.dispose()

		return botInfo
	}

	fun loadBotInfo(uid: Long, dialogId: Long, cache: Boolean, classGuid: Int) {
		if (cache) {
			val botInfo = botInfos[uid.toString() + "_" + dialogId]

			if (botInfo != null) {
				notificationCenter.postNotificationName(NotificationCenter.botInfoDidLoad, botInfo, classGuid)
				return
			}
		}

		messagesStorage.storageQueue.postRunnable {
			try {
				val botInfo = loadBotInfoInternal(uid, dialogId)

				if (botInfo != null) {
					AndroidUtilities.runOnUIThread {
						notificationCenter.postNotificationName(NotificationCenter.botInfoDidLoad, botInfo, classGuid)
					}
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun putBotKeyboard(dialogId: Long, message: Message?) {
		if (message == null) {
			return
		}

		try {
			var mid = 0
			val cursor = messagesStorage.database.queryFinalized(String.format(Locale.US, "SELECT mid FROM bot_keyboard WHERE uid = %d", dialogId))

			if (cursor.next()) {
				mid = cursor.intValue(0)
			}

			cursor.dispose()

			if (mid >= message.id) {
				return
			}

			val state = messagesStorage.database.executeFast("REPLACE INTO bot_keyboard VALUES(?, ?, ?)")
			state.requery()

			val data = NativeByteBuffer(message.objectSize)

			message.serializeToStream(data)

			state.bindLong(1, dialogId)
			state.bindInteger(2, message.id)
			state.bindByteBuffer(3, data)
			state.step()

			data.reuse()

			state.dispose()

			AndroidUtilities.runOnUIThread {
				val old = botKeyboards[dialogId]
				botKeyboards.put(dialogId, message)
				val channelId = MessageObject.getChannelId(message)

				if (channelId == 0L) {
					if (old != null) {
						botKeyboardsByMids.delete(old.id)
					}

					botKeyboardsByMids.put(message.id, dialogId)
				}

				notificationCenter.postNotificationName(NotificationCenter.botKeyboardDidLoad, message, dialogId)
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	fun putBotInfo(dialogId: Long, botInfo: BotInfo?) {
		if (botInfo == null) {
			return
		}

		botInfos[botInfo.user_id.toString() + "_" + dialogId] = botInfo

		messagesStorage.storageQueue.postRunnable {
			try {
				val state = messagesStorage.database.executeFast("REPLACE INTO bot_info_v2 VALUES(?, ?, ?)")
				state.requery()

				val data = NativeByteBuffer(botInfo.objectSize)

				botInfo.serializeToStream(data)

				state.bindLong(1, botInfo.user_id)
				state.bindLong(2, dialogId)
				state.bindByteBuffer(3, data)
				state.step()

				data.reuse()

				state.dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun updateBotInfo(dialogId: Long, update: TL_updateBotCommands) {
		val botInfo = botInfos[update.bot_id.toString() + "_" + dialogId]

		if (botInfo != null) {
			botInfo.commands = update.commands
			notificationCenter.postNotificationName(NotificationCenter.botInfoDidLoad, botInfo, 0)
		}

		messagesStorage.storageQueue.postRunnable {
			try {
				val info = loadBotInfoInternal(update.bot_id, dialogId) ?: return@postRunnable
				info.commands = update.commands

				val state = messagesStorage.database.executeFast("REPLACE INTO bot_info_v2 VALUES(?, ?, ?)")
				state.requery()

				val data = NativeByteBuffer(info.objectSize)

				info.serializeToStream(data)

				state.bindLong(1, info.user_id)
				state.bindLong(2, dialogId)
				state.bindByteBuffer(3, data)
				state.step()

				data.reuse()

				state.dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun uploadRingtone(filePath: String) {
		if (ringtoneUploaderHashMap.containsKey(filePath)) {
			return
		}

		ringtoneUploaderHashMap[filePath] = RingtoneUploader(filePath, currentAccount)
		ringtoneDataStore.addUploadingTone(filePath)
	}

	fun onRingtoneUploaded(filePath: String, document: TLRPC.Document?, error: Boolean) {
		ringtoneUploaderHashMap.remove(filePath)
		ringtoneDataStore.onRingtoneUploaded(filePath, document, error)
	}

	fun checkRingtones() {
		ringtoneDataStore.loadUserRingtones()
	}

	fun saveToRingtones(document: TLRPC.Document?): Boolean {
		if (document == null) {
			return false
		}

		if (ringtoneDataStore.contains(document.id)) {
			return true
		}

		if (document.size > MessagesController.getInstance(currentAccount).ringtoneSizeMax) {
			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR_SUBTITLE, LocaleController.formatString("TooLargeError", R.string.TooLargeError), LocaleController.formatString("ErrorRingtoneSizeTooBig", R.string.ErrorRingtoneSizeTooBig, (MessagesController.getInstance(UserConfig.selectedAccount).ringtoneSizeMax / 1024)))
			return false
		}

		for (attribute in document.attributes) {
			if (attribute is TL_documentAttributeAudio) {
				if (attribute.duration > MessagesController.getInstance(currentAccount).ringtoneDurationMax) {
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR_SUBTITLE, LocaleController.formatString("TooLongError", R.string.TooLongError), LocaleController.formatString("ErrorRingtoneDurationTooLong", R.string.ErrorRingtoneDurationTooLong, MessagesController.getInstance(UserConfig.selectedAccount).ringtoneDurationMax))
					return false
				}
			}
		}

		val saveRingtone = TL_account_saveRingtone()
		saveRingtone.id = TL_inputDocument()
		saveRingtone.id.id = document.id
		saveRingtone.id.file_reference = document.file_reference
		saveRingtone.id.access_hash = document.access_hash

		connectionsManager.sendRequest(saveRingtone) { response, _ ->
			AndroidUtilities.runOnUIThread {
				if (response != null) {
					if (response is TL_account_savedRingtoneConverted) {
						ringtoneDataStore.addTone(response.document)
					}
					else {
						ringtoneDataStore.addTone(document)
					}
				}
			}
		}

		return true
	}

	fun preloadPremiumPreviewStickers() {
		if (previewStickersLoading || premiumPreviewStickers.isNotEmpty()) {
			for (i in 0 until min(premiumPreviewStickers.size.toDouble(), 3.0).toInt()) {
				val document = premiumPreviewStickers[if (i == 2) premiumPreviewStickers.size - 1 else i]

				if (MessageObject.isPremiumSticker(document)) {
					var imageReceiver = ImageReceiver()
					imageReceiver.setImage(ImageLocation.getForDocument(document), null, null, "webp", null, 1)

					ImageLoader.getInstance().loadImageForImageReceiver(imageReceiver)

					imageReceiver = ImageReceiver()
					imageReceiver.setImage(ImageLocation.getForDocument(MessageObject.getPremiumStickerAnimation(document), document), null, null, null, "tgs", null, 1)

					ImageLoader.getInstance().loadImageForImageReceiver(imageReceiver)
				}
			}
			return
		}

		val req2 = TL_messages_getStickers()
		req2.emoticon = Emoji.fixEmoji("⭐") + Emoji.fixEmoji("⭐")
		req2.hash = 0

		previewStickersLoading = true

		connectionsManager.sendRequest(req2) { response, error ->
			AndroidUtilities.runOnUIThread {
				if (error != null) {
					return@runOnUIThread
				}

				previewStickersLoading = false

				val res = response as? TL_messages_stickers ?: return@runOnUIThread

				premiumPreviewStickers.clear()
				premiumPreviewStickers.addAll(res.stickers)

				NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.premiumStickersPreviewLoaded)
			}
		}
	}

	fun checkAllMedia(force: Boolean) {
		if (force) {
			reactionsUpdateDate = 0
			loadFeaturedDate[0] = 0
			loadFeaturedDate[1] = 0
		}

		loadRecents(TYPE_FAVE, gif = false, cache = true, force = false)
		loadRecents(TYPE_GREETINGS, gif = false, cache = true, force = false)
		loadRecents(TYPE_PREMIUM_STICKERS, gif = false, cache = false, force = true)
		checkFeaturedStickers()
		checkFeaturedEmoji()
		checkReactions()
		checkMenuBots()
		checkPremiumPromo()
		checkPremiumGiftStickers()
		checkGenericAnimations()
	}

	fun moveStickerSetToTop(setId: Long, emojis: Boolean, masks: Boolean) {
		val type = if (emojis) {
			TYPE_EMOJIPACKS
		}
		else if (masks) {
			TYPE_MASK
		}
		else {
			0
		}

		val arrayList = getStickerSets(type)

		for (i in arrayList.indices) {
			if (arrayList[i].set.id == setId) {
				val set = arrayList[i]
				arrayList.removeAt(i)
				arrayList.add(0, set)
				notificationCenter.postNotificationName(NotificationCenter.stickersDidLoad, type, false)
				break
			}
		}
	}

	//---------------- BOT END ----------------
	//---------------- EMOJI START ----------------
	class KeywordResult {
		constructor()

		constructor(emoji: String?, keyword: String?) {
			this.emoji = emoji
			this.keyword = keyword
		}

		@JvmField
		var emoji: String? = null
		var keyword: String? = null
	}

	fun interface KeywordResultCallback {
		fun run(param: List<KeywordResult>?, alias: String?)
	}

	private val currentFetchingEmoji = mutableMapOf<String, Boolean>()

	fun fetchNewEmojiKeywords(langCodes: Array<String>?) {
		if (langCodes == null) {
			return
		}

		for (a in langCodes.indices) {
			val langCode = langCodes[a]

			if (langCode.isEmpty()) {
				return
			}

			if (currentFetchingEmoji[langCode] != null) {
				return
			}

			currentFetchingEmoji[langCode] = true

			messagesStorage.storageQueue.postRunnable {
				var version = -1
				var alias: String? = null
				var date: Long = 0

				try {
					val cursor = messagesStorage.database.queryFinalized("SELECT alias, version, date FROM emoji_keywords_info_v2 WHERE lang = ?", langCode)

					if (cursor.next()) {
						alias = cursor.stringValue(0)
						version = cursor.intValue(1)
						date = cursor.longValue(2)
					}

					cursor.dispose()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				if (!BuildConfig.DEBUG && abs((System.currentTimeMillis() - date).toDouble()) < 60 * 60 * 1000) {
					AndroidUtilities.runOnUIThread {
						currentFetchingEmoji.remove(langCode)
					}

					return@postRunnable
				}

				val request: TLObject

				if (version == -1) {
					val req = TL_messages_getEmojiKeywords()
					req.lang_code = langCode

					request = req
				}
				else {
					val req = TL_messages_getEmojiKeywordsDifference()
					req.lang_code = langCode
					req.from_version = version

					request = req
				}

				val aliasFinal = alias
				val versionFinal = version

				connectionsManager.sendRequest(request) { response, _ ->
					if (response is TL_emojiKeywordsDifference) {
						if (versionFinal != -1 && response.lang_code != aliasFinal) {
							messagesStorage.storageQueue.postRunnable {
								try {
									val deleteState = messagesStorage.database.executeFast("DELETE FROM emoji_keywords_info_v2 WHERE lang = ?")
									deleteState.bindString(1, langCode)
									deleteState.step()
									deleteState.dispose()

									AndroidUtilities.runOnUIThread {
										currentFetchingEmoji.remove(langCode)
										fetchNewEmojiKeywords(arrayOf(langCode))
									}
								}
								catch (e: Exception) {
									FileLog.e(e)
								}
							}
						}
						else {
							putEmojiKeywords(langCode, response)
						}
					}
					else {
						AndroidUtilities.runOnUIThread {
							currentFetchingEmoji.remove(langCode)
						}
					}
				}
			}
		}
	}

	private fun putEmojiKeywords(lang: String, res: TL_emojiKeywordsDifference?) {
		if (res == null) {
			return
		}

		messagesStorage.storageQueue.postRunnable {
			try {
				if (res.keywords.isNotEmpty()) {
					val insertState = messagesStorage.database.executeFast("REPLACE INTO emoji_keywords_v2 VALUES(?, ?, ?)")
					val deleteState = messagesStorage.database.executeFast("DELETE FROM emoji_keywords_v2 WHERE lang = ? AND keyword = ? AND emoji = ?")

					messagesStorage.database.beginTransaction()

					for (keyword in res.keywords) {
						if (keyword is TL_emojiKeyword) {
							val key = keyword.keyword.lowercase()

							for (emoticon in keyword.emoticons) {
								insertState.requery()
								insertState.bindString(1, res.lang_code)
								insertState.bindString(2, key)
								insertState.bindString(3, emoticon)
								insertState.step()
							}
						}
						else if (keyword is TL_emojiKeywordDeleted) {
							val key = keyword.keyword.lowercase()

							for (emoticon in keyword.emoticons) {
								deleteState.requery()
								deleteState.bindString(1, res.lang_code)
								deleteState.bindString(2, key)
								deleteState.bindString(3, emoticon)
								deleteState.step()
							}
						}
					}

					messagesStorage.database.commitTransaction()

					insertState.dispose()
					deleteState.dispose()
				}

				val infoState = messagesStorage.database.executeFast("REPLACE INTO emoji_keywords_info_v2 VALUES(?, ?, ?, ?)")
				infoState.bindString(1, lang)
				infoState.bindString(2, res.lang_code)
				infoState.bindInteger(3, res.version)
				infoState.bindLong(4, System.currentTimeMillis())
				infoState.step()
				infoState.dispose()

				AndroidUtilities.runOnUIThread {
					currentFetchingEmoji.remove(lang)
					notificationCenter.postNotificationName(NotificationCenter.newEmojiSuggestionsAvailable, lang)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	fun getAnimatedEmojiByKeywords(query: String?, onResult: Utilities.Callback<List<Long>?>?) {
		if (query == null) {
			onResult?.run(listOf())
			return
		}

		val stickerSets = getStickerSets(TYPE_EMOJIPACKS)
		val featuredStickerSets = featuredEmojiSets

		Utilities.searchQueue.postRunnable {
			val fullMatch = mutableListOf<Long>()
			val halfMatch = mutableListOf<Long>()
			val queryLowercased = query.lowercase()

			for (i in stickerSets.indices) {
				if (stickerSets[i].keywords != null) {
					val keywords = stickerSets[i].keywords

					for (j in keywords.indices) {
						for (k in keywords[j].keyword.indices) {
							val keyword = keywords[j].keyword[k]

							if (queryLowercased == keyword) {
								fullMatch.add(keywords[j].document_id)
							}
							else if (queryLowercased.contains(keyword) || keyword.contains(queryLowercased)) {
								halfMatch.add(keywords[j].document_id)
							}
						}
					}
				}
			}

			for (i in featuredStickerSets.indices) {
				if (featuredStickerSets[i] is TL_stickerSetFullCovered && (featuredStickerSets[i] as TL_stickerSetFullCovered).keywords != null) {
					val keywords = (featuredStickerSets[i] as TL_stickerSetFullCovered).keywords

					for (j in keywords.indices) {
						for (k in keywords[j].keyword.indices) {
							val keyword = keywords[j].keyword[k]

							if (queryLowercased == keyword) {
								fullMatch.add(keywords[j].document_id)
							}
							else if (queryLowercased.contains(keyword) || keyword.contains(queryLowercased)) {
								halfMatch.add(keywords[j].document_id)
							}
						}
					}
				}
			}

			fullMatch.addAll(halfMatch)
			onResult?.run(fullMatch)
		}
	}

	fun getEmojiSuggestions(langCodes: Array<String>?, keyword: String?, fullMatch: Boolean, callback: KeywordResultCallback?, allowAnimated: Boolean) {
		getEmojiSuggestions(langCodes, keyword, fullMatch, callback, null, allowAnimated, null)
	}

	fun getEmojiSuggestions(langCodes: Array<String>?, keyword: String?, fullMatch: Boolean, callback: KeywordResultCallback?, sync: CountDownLatch?, allowAnimated: Boolean) {
		getEmojiSuggestions(langCodes, keyword, fullMatch, callback, sync, allowAnimated, null)
	}

	fun getEmojiSuggestions(langCodes: Array<String>?, keyword: String?, fullMatch: Boolean, callback: KeywordResultCallback?, sync: CountDownLatch?, allowAnimated: Boolean, maxAnimatedPerEmoji: Int?) {
		if (callback == null) {
			return
		}

		if (keyword.isNullOrEmpty() || langCodes == null) {
			callback.run(listOf(), null)
			return
		}

		val recentEmoji = Emoji.recentEmoji.toList()

		messagesStorage.storageQueue.postRunnable {
			val result = mutableListOf<KeywordResult>()
			val resultMap = mutableMapOf<String, Boolean>()
			var alias: String? = null

			try {
				var cursor: SQLiteCursor
				var hasAny = false

				for (a in langCodes.indices) {
					cursor = messagesStorage.database.queryFinalized("SELECT alias FROM emoji_keywords_info_v2 WHERE lang = ?", langCodes[a])

					if (cursor.next()) {
						alias = cursor.stringValue(0)
					}

					cursor.dispose()

					if (alias != null) {
						hasAny = true
					}
				}

				if (!hasAny) {
					AndroidUtilities.runOnUIThread {
						for (a in langCodes.indices) {
							if (currentFetchingEmoji[langCodes[a]] != null) {
								return@runOnUIThread
							}
						}

						callback.run(result, null)
					}

					return@postRunnable
				}

				var key = keyword.lowercase()

				for (a in 0..1) {
					if (a == 1) {
						val translitKey = LocaleController.getInstance().getTranslitString(key, false, false)

						if (translitKey == key) {
							continue
						}

						key = translitKey
					}

					var key2: String? = null
					val nextKey = StringBuilder(key)
					var pos = nextKey.length

					while (pos > 0) {
						pos--

						var value = nextKey[pos]

						value++

						nextKey.setCharAt(pos, value)

						if (value.code != 0) {
							key2 = nextKey.toString()
							break
						}
					}

					if (fullMatch) {
						cursor = messagesStorage.database.queryFinalized("SELECT emoji, keyword FROM emoji_keywords_v2 WHERE keyword = ?", key)
					}
					else if (key2 != null) {
						cursor = messagesStorage.database.queryFinalized("SELECT emoji, keyword FROM emoji_keywords_v2 WHERE keyword >= ? AND keyword < ?", key, key2)
					}
					else {
						key += "%"
						cursor = messagesStorage.database.queryFinalized("SELECT emoji, keyword FROM emoji_keywords_v2 WHERE keyword LIKE ?", key)
					}

					while (cursor.next()) {
						val value = cursor.stringValue(0).replace("\ufe0f", "")

						if (resultMap[value] != null) {
							continue
						}

						resultMap[value] = true

						val keywordResult = KeywordResult()
						keywordResult.emoji = value
						keywordResult.keyword = cursor.stringValue(1)

						result.add(keywordResult)
					}

					cursor.dispose()
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}

			result.sortWith { o1, o2 ->
				var idx1 = recentEmoji.indexOf(o1.emoji)

				if (idx1 < 0) {
					idx1 = Int.MAX_VALUE
				}

				var idx2 = recentEmoji.indexOf(o2.emoji)

				if (idx2 < 0) {
					idx2 = Int.MAX_VALUE
				}
				if (idx1 < idx2) {
					return@sortWith -1
				}
				else if (idx1 > idx2) {
					return@sortWith 1
				}
				else {
					val len1 = o1.keyword?.length ?: 0
					val len2 = o2.keyword?.length ?: 0

					if (len1 < len2) {
						return@sortWith -1
					}
					else if (len1 > len2) {
						return@sortWith 1
					}

					return@sortWith 0
				}
			}

			val aliasFinal = alias

			if (allowAnimated && SharedConfig.suggestAnimatedEmoji) {
				fillWithAnimatedEmoji(result, maxAnimatedPerEmoji) {
					if (sync != null) {
						callback.run(result, aliasFinal)
						sync.countDown()
					}
					else {
						AndroidUtilities.runOnUIThread {
							callback.run(result, aliasFinal)
						}
					}
				}
			}
			else {
				if (sync != null) {
					callback.run(result, aliasFinal)
					sync.countDown()
				}
				else {
					AndroidUtilities.runOnUIThread {
						callback.run(result, aliasFinal)
					}
				}
			}
		}

		if (sync != null) {
			runCatching {
				sync.await()
			}
		}
	}

	private var triedLoadingEmojipacks = false

	fun fillWithAnimatedEmoji(result: MutableList<KeywordResult>?, maxAnimatedPerEmojiInput: Int?, onDone: Runnable?) {
		if (result.isNullOrEmpty()) {
			onDone?.run()
			return
		}

		val emojiPacks: Array<List<TL_messages_stickerSet>?> = arrayOfNulls(2)
		emojiPacks[0] = getStickerSets(TYPE_EMOJIPACKS)

		val fillRunnable = Runnable {
			val featuredSets = featuredEmojiSets
			val animatedResult = mutableListOf<KeywordResult>()
			val animatedEmoji = mutableListOf<TLRPC.Document>()
			val maxAnimatedPerEmoji = maxAnimatedPerEmojiInput ?: if (result.size > 5) 1 else (if (result.size > 2) 2 else 3)
			val len = if (maxAnimatedPerEmojiInput == null) min(15.0, result.size.toDouble()).toInt() else result.size

			for (i in 0 until len) {
				val emoji = result[i].emoji ?: continue

				animatedEmoji.clear()

				val isPremium = UserConfig.getInstance(currentAccount).isPremium

				for (j in Emoji.recentEmoji.indices) {
					if (Emoji.recentEmoji[j].startsWith("animated_")) {
						runCatching {
							val documentId = Emoji.recentEmoji[j].substring(9).toLong()
							val document = AnimatedEmojiDrawable.findDocument(currentAccount, documentId)

							if (document != null && (isPremium || MessageObject.isFreeEmoji(document)) && emoji == MessageObject.findAnimatedEmojiEmoticon(document, null)) {
								animatedEmoji.add(document)
							}
						}
					}

					if (animatedEmoji.size >= maxAnimatedPerEmoji) {
						break
					}
				}

				if (animatedEmoji.size < maxAnimatedPerEmoji && emojiPacks[0] != null) {
					for (j in emojiPacks[0]!!.indices) {
						val set = emojiPacks[0]!![j]

						if (set.documents != null) {
							for (d in set.documents.indices) {
								val document = set.documents[d]

								if (document?.attributes != null && !animatedEmoji.contains(document)) {
									var attribute: TL_documentAttributeCustomEmoji? = null

									for (k in document.attributes.indices) {
										val attr = document.attributes[k]

										if (attr is TL_documentAttributeCustomEmoji) {
											attribute = attr
											break
										}
									}

									if (attribute != null && emoji == attribute.alt && (isPremium || attribute.free)) {
										var duplicate = false

										for (l in animatedEmoji.indices) {
											if (animatedEmoji[l].id == document.id) {
												duplicate = true
												break
											}
										}

										if (!duplicate) {
											animatedEmoji.add(document)

											if (animatedEmoji.size >= maxAnimatedPerEmoji) {
												break
											}
										}
									}
								}
							}
						}

						if (animatedEmoji.size >= maxAnimatedPerEmoji) {
							break
						}
					}
				}

				if (animatedEmoji.size < maxAnimatedPerEmoji) {
					for (j in featuredSets.indices) {
						val set = featuredSets[j]
						val documents = if (set is TL_stickerSetFullCovered) set.documents else set.covers

						if (documents == null) {
							continue
						}

						for (d in documents.indices) {
							val document = documents[d]

							if (document?.attributes != null && !animatedEmoji.contains(document)) {
								var attribute: TL_documentAttributeCustomEmoji? = null

								for (k in document.attributes.indices) {
									val attr = document.attributes[k]

									if (attr is TL_documentAttributeCustomEmoji) {
										attribute = attr
										break
									}
								}

								if (attribute != null && emoji == attribute.alt && (isPremium || attribute.free)) {
									var duplicate = false

									for (l in animatedEmoji.indices) {
										if (animatedEmoji[l].id == document.id) {
											duplicate = true
											break
										}
									}

									if (!duplicate) {
										animatedEmoji.add(document)

										if (animatedEmoji.size >= maxAnimatedPerEmoji) {
											break
										}
									}
								}
							}
						}

						if (animatedEmoji.size >= maxAnimatedPerEmoji) {
							break
						}
					}
				}

				if (animatedEmoji.isNotEmpty()) {
					val keyword = result[i].keyword

					for (p in animatedEmoji.indices) {
						val document = animatedEmoji[p]

						val keywordResult = KeywordResult()
						keywordResult.emoji = "animated_" + document.id
						keywordResult.keyword = keyword

						animatedResult.add(keywordResult)
					}
				}
			}

			result.addAll(0, animatedResult)

			onDone?.run()
		}

		if (emojiPacks[0].isNullOrEmpty() && !triedLoadingEmojipacks) {
			triedLoadingEmojipacks = true
			var done = false

			AndroidUtilities.runOnUIThread {
				loadStickers(TYPE_EMOJIPACKS, cache = true, force = false, scheduleIfLoading = false, onFinish = {
					if (!done) {
						emojiPacks[0] = it
						fillRunnable.run()
						done = true
					}
				}, retry = true)
			}

			AndroidUtilities.runOnUIThread({
				if (!done) {
					fillRunnable.run()
					done = true
				}
			}, 900)
		}
		else {
			fillRunnable.run()
		}
	}

	private fun loadEmojiThemes() {
		val preferences = ApplicationLoader.applicationContext.getSharedPreferences("emojithemes_config_$currentAccount", Context.MODE_PRIVATE)
		val count = preferences.getInt("count", 0)

		val previewItems = mutableListOf<ChatThemeItem>()
		previewItems.add(ChatThemeItem(EmojiThemes.createHomePreviewTheme()))

		for (i in 0 until count) {
			val value = preferences.getString("theme_$i", "")
			val serializedData = SerializedData(Utilities.hexToBytes(value))

			try {
				val theme = TLRPC.Theme.TLdeserialize(serializedData, serializedData.readInt32(true), true)
				val fullTheme = EmojiThemes.createPreviewFullTheme(theme)

				if (fullTheme.items.size >= 4) {
					previewItems.add(ChatThemeItem(fullTheme))
				}

				ChatThemeController.chatThemeQueue.postRunnable {
					previewItems.forEach {
						it.chatTheme.loadPreviewColors(0)
					}

					AndroidUtilities.runOnUIThread {
						defaultEmojiThemes.clear()
						defaultEmojiThemes.addAll(previewItems)
					}
				}
			}
			catch (e: Throwable) {
				FileLog.e(e)
			}
		}
	}

	fun generateEmojiPreviewThemes(emojiPreviewThemes: List<TL_theme>, currentAccount: Int) {
		val preferences = ApplicationLoader.applicationContext.getSharedPreferences("emojithemes_config_$currentAccount", Context.MODE_PRIVATE)

		val editor = preferences.edit()
		editor.putInt("count", emojiPreviewThemes.size)

		for (i in emojiPreviewThemes.indices) {
			val tlChatTheme = emojiPreviewThemes[i]
			val data = SerializedData(tlChatTheme.objectSize)
			tlChatTheme.serializeToStream(data)
			editor.putString("theme_$i", Utilities.bytesToHex(data.toByteArray()))
		}

		editor.commit()

		if (emojiPreviewThemes.isNotEmpty()) {
			val previewItems = mutableListOf<ChatThemeItem>()
			previewItems.add(ChatThemeItem(EmojiThemes.createHomePreviewTheme()))

			for (i in emojiPreviewThemes.indices) {
				val theme = emojiPreviewThemes[i]
				val chatTheme = EmojiThemes.createPreviewFullTheme(theme)
				val item = ChatThemeItem(chatTheme)

				if (chatTheme.items.size >= 4) {
					previewItems.add(item)
				}
			}

			ChatThemeController.chatThemeQueue.postRunnable {
				for (i in previewItems.indices) {
					previewItems[i].chatTheme.loadPreviewColors(currentAccount)
				}

				AndroidUtilities.runOnUIThread {
					defaultEmojiThemes.clear()
					defaultEmojiThemes.addAll(previewItems)
					NotificationCenter.globalInstance.postNotificationName(NotificationCenter.emojiPreviewThemesChanged)
				}
			}
		}
		else {
			defaultEmojiThemes.clear()
			NotificationCenter.globalInstance.postNotificationName(NotificationCenter.emojiPreviewThemesChanged)
		}
	}

	val defaultEmojiStatuses: List<EmojiStatus>?
		//---------------- EMOJI END ----------------
		get() {
			val type = 1 // default

			if (!emojiStatusesFromCacheFetched[type]) {
				fetchEmojiStatuses(type, true)
			}
			else if ( /*emojiStatusesHash[type] == 0 || */emojiStatusesFetchDate[type] == null || (System.currentTimeMillis() / 1000 - emojiStatusesFetchDate[type]!!) > 60 * 30) {
				fetchEmojiStatuses(type, false)
			}

			return emojiStatuses[type]
		}

	val recentEmojiStatuses: List<EmojiStatus>?
		get() {
			val type = 0 // recent

			if (!emojiStatusesFromCacheFetched[type]) {
				fetchEmojiStatuses(type, true)
			}
			else if ( /*emojiStatusesHash[type] == 0 || */emojiStatusesFetchDate[type] == null || (System.currentTimeMillis() / 1000 - emojiStatusesFetchDate[type]!!) > 60 * 30) {
				fetchEmojiStatuses(type, false)
			}

			return emojiStatuses[type]
		}

	fun clearRecentEmojiStatuses(): List<EmojiStatus>? {
		val type = 0 // recent

		emojiStatuses[type]?.clear()

		emojiStatusesHash[type] = 0

		messagesStorage.storageQueue.postRunnable {
			runCatching {
				messagesStorage.database.executeFast("DELETE FROM emoji_statuses WHERE type = $type").stepThis().dispose()
			}
		}

		return emojiStatuses[type]
	}

	fun pushRecentEmojiStatus(status: EmojiStatus) {
		val type = 0 // recent

		if (emojiStatuses[type] != null) {
			if (status is TL_emojiStatus) {
				val documentId = status.document_id
				var i = 0

				while (i < emojiStatuses[type]!!.size) {
					if (emojiStatuses[type]!![i] is TL_emojiStatus && (emojiStatuses[type]!![i] as TL_emojiStatus).document_id == documentId) {
						emojiStatuses[type]!!.removeAt(i--)
					}

					++i
				}
			}

			emojiStatuses[type]!!.add(0, status)

			while (emojiStatuses[type]!!.size > 50) {
				emojiStatuses[type]!!.removeAt(emojiStatuses[type]!!.size - 1)
			}

			val statuses = TL_account_emojiStatuses()
			// TODO: calc hash
			statuses.hash = emojiStatusesHash[type]
			statuses.statuses = emojiStatuses[type]
			updateEmojiStatuses(type, statuses)
		}
	}

	fun fetchEmojiStatuses(type: Int, cache: Boolean) {
		if (emojiStatusesFetching[type]) {
			return
		}

		emojiStatusesFetching[type] = true

		if (cache) {
			messagesStorage.storageQueue.postRunnable {
				var done = false

				try {
					val cursor = messagesStorage.database.queryFinalized("SELECT data FROM emoji_statuses WHERE type = $type LIMIT 1")

					if (cursor.next() && cursor.columnCount > 0 && !cursor.isNull(0)) {
						val data = cursor.byteBufferValue(0)

						if (data != null) {
							val response = account_EmojiStatuses.TLdeserialize(data, data.readInt32(false), false)

							if (response is TL_account_emojiStatuses) {
								emojiStatusesHash[type] = response.hash
								emojiStatuses[type] = response.statuses

								done = true
							}

							data.reuse()
						}
					}

					cursor.dispose()
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				emojiStatusesFromCacheFetched[type] = true
				emojiStatusesFetching[type] = false

				if (done) {
					AndroidUtilities.runOnUIThread {
						notificationCenter.postNotificationName(NotificationCenter.recentEmojiStatusesUpdate)
					}
				}
				else {
					fetchEmojiStatuses(type, false)
				}
			}
		}
		else {
			val req: TLObject

			if (type == 0) {
				val recentReq = TL_account_getRecentEmojiStatuses()
				recentReq.hash = emojiStatusesHash[type]

				req = recentReq
			}
			else {
				val defaultReq = TL_account_getDefaultEmojiStatuses()
				defaultReq.hash = emojiStatusesHash[type]

				req = defaultReq
			}

			connectionsManager.sendRequest(req) { response, _ ->
				emojiStatusesFetchDate[type] = System.currentTimeMillis() / 1000

				if (response is TL_account_emojiStatusesNotModified) {
					emojiStatusesFetching[type] = false
				}
				else if (response is TL_account_emojiStatuses) {
					emojiStatusesHash[type] = response.hash
					emojiStatuses[type] = response.statuses

					updateEmojiStatuses(type, response)

					AndroidUtilities.runOnUIThread {
						notificationCenter.postNotificationName(NotificationCenter.recentEmojiStatusesUpdate)
					}
				}
			}
		}
	}

	private fun updateEmojiStatuses(type: Int, response: TL_account_emojiStatuses) {
		messagesStorage.storageQueue.postRunnable {
			try {
				messagesStorage.database.executeFast("DELETE FROM emoji_statuses WHERE type = $type").stepThis().dispose()

				val state = messagesStorage.database.executeFast("INSERT INTO emoji_statuses VALUES(?, ?)")
				state.requery()

				val data = NativeByteBuffer(response.objectSize)

				response.serializeToStream(data)

				state.bindByteBuffer(1, data)
				state.bindInteger(2, type)
				state.step()

				data.reuse()

				state.dispose()
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
			emojiStatusesFetching[type] = false
		}
	}

	val recentReactions = mutableListOf<Reaction>()
	val topReactions = mutableListOf<Reaction>()
	private var loadingRecentReactions = false

	init {
		draftPreferences = if (currentAccount == 0) {
			ApplicationLoader.applicationContext.getSharedPreferences("drafts", Activity.MODE_PRIVATE)
		}
		else {
			ApplicationLoader.applicationContext.getSharedPreferences("drafts$currentAccount", Activity.MODE_PRIVATE)
		}

		val values = draftPreferences?.all

		if (values != null) {
			for ((key, value) in values) {
				try {
					val did = Utilities.parseLong(key)
					val bytes = Utilities.hexToBytes(value as String?)
					val serializedData = SerializedData(bytes)
					var isThread = false

					if (key.startsWith("r_") || (key.startsWith("rt_").also { isThread = it })) {
						val message = Message.TLdeserialize(serializedData, serializedData.readInt32(true), true)

						if (message != null) {
							message.readAttachPath(serializedData, userConfig.clientUserId)

							var threads = draftMessages[did]

							if (threads == null) {
								threads = SparseArray()
								draftMessages.put(did, threads)
							}

							val threadId = if (isThread) Utilities.parseInt(key.substring(key.lastIndexOf('_') + 1)) else 0

							threads?.put(threadId, message)
						}
					}
					else {
						val draftMessage = DraftMessage.TLdeserialize(serializedData, serializedData.readInt32(true), true)

						if (draftMessage != null) {
							var threads = drafts[did]

							if (threads == null) {
								threads = SparseArray()
								drafts.put(did, threads)
							}

							val threadId = if (key.startsWith("t_")) Utilities.parseInt(key.substring(key.lastIndexOf('_') + 1)) else 0
							threads?.put(threadId, draftMessage)
						}
					}

					serializedData.cleanup()
				}
				catch (e: Exception) {
					// ignore
				}
			}
		}

		loadStickersByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME, isEmoji = false, cache = true)
		loadEmojiThemes()
		loadRecentAndTopReactions(false)

		ringtoneDataStore = RingtoneDataStore(currentAccount)
	}

	fun clearRecentReactions() {
		recentReactions.clear()

		val recentReactionsPref = ApplicationLoader.applicationContext.getSharedPreferences("recent_reactions_$currentAccount", Context.MODE_PRIVATE)
		recentReactionsPref.edit().clear().commit()

		val clearRecentReaction = TL_messages_clearRecentReactions()

		connectionsManager.sendRequest(clearRecentReaction)
	}

	fun loadRecentAndTopReactions(force: Boolean) {
		if (loadingRecentReactions || recentReactions.isNotEmpty() || force) {
			return
		}

		val recentReactionsPref = ApplicationLoader.applicationContext.getSharedPreferences("recent_reactions_$currentAccount", Context.MODE_PRIVATE)
		val topReactionsPref = ApplicationLoader.applicationContext.getSharedPreferences("top_reactions_$currentAccount", Context.MODE_PRIVATE)

		recentReactions.clear()
		topReactions.clear()
		recentReactions.addAll(loadReactionsFromPref(recentReactionsPref))
		topReactions.addAll(loadReactionsFromPref(topReactionsPref))
		loadingRecentReactions = true

		val loadFromServer = true

		if (loadFromServer) {
			val recentReactionsRequest = TL_messages_getRecentReactions()
			recentReactionsRequest.hash = recentReactionsPref.getLong("hash", 0)
			recentReactionsRequest.limit = 50

			connectionsManager.sendRequest(recentReactionsRequest) { response, error ->
				if (error == null) {
					if (response is TL_messages_reactions) {
						recentReactions.clear()
						recentReactions.addAll(response.reactions)

						saveReactionsToPref(recentReactionsPref, response.hash, response.reactions)
					}
				}
			}

			val topReactionsRequest = TL_messages_getTopReactions()
			topReactionsRequest.hash = topReactionsPref.getLong("hash", 0)
			topReactionsRequest.limit = 100

			connectionsManager.sendRequest(topReactionsRequest) { response, error ->
				if (error == null) {
					if (response is TL_messages_reactions) {
						topReactions.clear()
						topReactions.addAll(response.reactions)

						saveReactionsToPref(topReactionsPref, response.hash, response.reactions)
					}
				}
			}
		}
	}

	companion object {
		private const val ATTACH_MENU_BOT_ANIMATED_ICON_KEY: String = "android_animated"
		private const val ATTACH_MENU_BOT_STATIC_ICON_KEY: String = "default_static"
		private const val ATTACH_MENU_BOT_PLACEHOLDER_STATIC_KEY: String = "placeholder_static"
		const val ATTACH_MENU_BOT_COLOR_LIGHT_ICON: String = "light_icon"
		const val ATTACH_MENU_BOT_COLOR_LIGHT_TEXT: String = "light_text"
		const val ATTACH_MENU_BOT_COLOR_DARK_ICON: String = "dark_icon"
		const val ATTACH_MENU_BOT_COLOR_DARK_TEXT: String = "dark_text"
		private val BOLD_PATTERN: Pattern = Pattern.compile("\\*\\*(.+?)\\*\\*")
		private val ITALIC_PATTERN: Pattern = Pattern.compile("__(.+?)__")
		private val SPOILER_PATTERN: Pattern = Pattern.compile("\\|\\|(.+?)\\|\\|")
		private val STRIKE_PATTERN: Pattern = Pattern.compile("~~(.+?)~~")
		var SHORTCUT_CATEGORY: String = "org.telegram.messenger.SHORTCUT_SHARE"
		private val instances = arrayOfNulls<MediaDataController>(UserConfig.MAX_ACCOUNT_COUNT)

		@JvmStatic
		fun getInstance(num: Int): MediaDataController {
			var localInstance = instances[num]

			if (localInstance == null) {
				synchronized(MediaDataController::class) {
					localInstance = instances[num]

					if (localInstance == null) {
						localInstance = MediaDataController(num)
						instances[num] = localInstance
					}
				}
			}

			return localInstance!!
		}

		const val TYPE_IMAGE: Int = 0
		const val TYPE_MASK: Int = 1
		const val TYPE_FAVE: Int = 2
		const val TYPE_FEATURED: Int = 3
		const val TYPE_EMOJI: Int = 4
		const val TYPE_EMOJIPACKS: Int = 5
		const val TYPE_FEATURED_EMOJIPACKS: Int = 6
		const val TYPE_PREMIUM_STICKERS: Int = 7
		const val TYPE_GREETINGS: Int = 3

		fun canShowAttachMenuBotForTarget(bot: TL_attachMenuBot, target: String): Boolean {
			for (peerType in bot.peer_types) {
				if ((peerType is TL_attachMenuPeerTypeSameBotPM || peerType is TL_attachMenuPeerTypeBotPM) && (target == "bots") || (peerType is TL_attachMenuPeerTypeBroadcast && target == "channels") || (peerType is TL_attachMenuPeerTypeChat && target == "groups") || (peerType is TL_attachMenuPeerTypePM && target == "users")) {
					return true
				}
			}

			return false
		}

		fun canShowAttachMenuBot(bot: TL_attachMenuBot, peer: TLObject?): Boolean {
			val user = peer as? User
			val chat = peer as? Chat

			for (peerType in bot.peer_types) {
				if (peerType is TL_attachMenuPeerTypeSameBotPM && user != null && user.bot && user.id == bot.bot_id || peerType is TL_attachMenuPeerTypeBotPM && user != null && user.bot && user.id != bot.bot_id || peerType is TL_attachMenuPeerTypePM && user != null && !user.bot || peerType is TL_attachMenuPeerTypeChat && chat != null && !ChatObject.isChannelAndNotMegaGroup(chat) || peerType is TL_attachMenuPeerTypeBroadcast && chat != null && ChatObject.isChannelAndNotMegaGroup(chat)) {
					return true
				}
			}

			return false
		}

		fun getAnimatedAttachMenuBotIcon(bot: TL_attachMenuBot): TL_attachMenuBotIcon? {
			for (icon in bot.icons) {
				if (icon.name == ATTACH_MENU_BOT_ANIMATED_ICON_KEY) {
					return icon
				}
			}

			return null
		}

		@JvmStatic
		fun getStaticAttachMenuBotIcon(bot: TL_attachMenuBot): TL_attachMenuBotIcon? {
			for (icon in bot.icons) {
				if (icon.name == ATTACH_MENU_BOT_STATIC_ICON_KEY) {
					return icon
				}
			}

			return null
		}

		@JvmStatic
		fun getPlaceholderStaticAttachMenuBotIcon(bot: TL_attachMenuBot): TL_attachMenuBotIcon? {
			for (icon in bot.icons) {
				if (icon.name == ATTACH_MENU_BOT_PLACEHOLDER_STATIC_KEY) {
					return icon
				}
			}

			return null
		}

		@JvmOverloads
		@JvmStatic
		fun calcDocumentsHash(arrayList: List<TLRPC.Document>?, maxCount: Int = 200): Long {
			if (arrayList == null) {
				return 0
			}

			var acc: Long = 0
			var a = 0
			val n = min(maxCount.toDouble(), arrayList.size.toDouble()).toInt()

			while (a < n) {
				val document = arrayList[a]
				acc = calcHash(acc, document.id)
				a++
			}

			return acc
		}

		@JvmStatic
		fun calcHash(hash: Long, id: Long): Long {
			@Suppress("NAME_SHADOWING") var hash = hash
			hash = hash xor (id shr 21)
			hash = hash xor (id shl 35)
			hash = hash xor (id shr 4)
			return hash + id
		}

		@JvmStatic
		fun getStickerSetId(document: TLRPC.Document): Long {
			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeSticker) {
					if (attribute.stickerset is TL_inputStickerSetID) {
						return attribute.stickerset.id
					}

					break
				}
			}

			return -1
		}

		@JvmStatic
		fun getInputStickerSet(document: TLRPC.Document): InputStickerSet? {
			for (attribute in document.attributes) {
				if (attribute is TL_documentAttributeSticker) {
					if (attribute.stickerset is TL_inputStickerSetEmpty) {
						return null
					}

					return attribute.stickerset
				}
			}

			return null
		}

		private fun calcStickersHash(sets: List<TL_messages_stickerSet>): Long {
			var acc: Long = 0

			for (a in sets.indices) {
				val set = sets[a].set

				if (set.archived) {
					continue
				}

				acc = calcHash(acc, set.hash.toLong())
			}

			return acc
		}

		//---------------- MESSAGE SEARCH END ----------------
		const val TEXT_ONLY = -1
		const val MEDIA_PHOTOVIDEO: Int = 0
		const val MEDIA_FILE: Int = 1
		const val MEDIA_AUDIO: Int = 2
		const val MEDIA_URL: Int = 3
		const val MEDIA_MUSIC: Int = 4
		const val MEDIA_GIF: Int = 5
		const val MEDIA_PHOTOS_ONLY: Int = 6
		const val MEDIA_VIDEOS_ONLY: Int = 7
		const val MEDIA_TYPES_COUNT: Int = 8

		@JvmStatic
		fun getMediaType(message: Message?): Int {
			if (message == null) {
				return TEXT_ONLY
			}

			if (MessageObject.getMedia(message) is TL_messageMediaPhoto) {
				return MEDIA_PHOTOVIDEO
			}
			else if (MessageObject.getMedia(message) is TL_messageMediaDocument) {
				val document = MessageObject.getMedia(message)?.document ?: return -1
				var isAnimated = false
				var isVideo = false
				var isVoice = false
				var isMusic = false
				var isSticker = false

				for (attribute in document.attributes) {
					when (attribute) {
						is TL_documentAttributeVideo -> {
							isVoice = attribute.round_message
							isVideo = !attribute.round_message
						}

						is TL_documentAttributeAnimated -> {
							isAnimated = true
						}

						is TL_documentAttributeAudio -> {
							isVoice = attribute.voice
							isMusic = !attribute.voice
						}

						is TL_documentAttributeSticker -> {
							isSticker = true
						}
					}
				}

				return if (isVoice) {
					MEDIA_AUDIO
				}
				else if (isVideo && !isAnimated && !isSticker) {
					MEDIA_PHOTOVIDEO
				}
				else if (isSticker) {
					-1
				}
				else if (isAnimated) {
					MEDIA_GIF
				}
				else if (isMusic) {
					MEDIA_MUSIC
				}
				else {
					MEDIA_FILE
				}
			}
			else if (message.entities.isNotEmpty()) {
				for (entity in message.entities) {
					if (entity is TL_messageEntityUrl || entity is TL_messageEntityTextUrl || entity is TL_messageEntityEmail) {
						return MEDIA_URL
					}
				}
			}

			return -1
		}

		@JvmStatic
		fun canAddMessageToMedia(message: Message?): Boolean {
			return if (message is TL_message_secret && (MessageObject.getMedia(message) is TL_messageMediaPhoto || MessageObject.isVideoMessage(message) || MessageObject.isGifMessage(message)) && MessageObject.getMedia(message)!!.ttl_seconds != 0 && MessageObject.getMedia(message)!!.ttl_seconds <= 60) {
				false
			}
			else if (message !is TL_message_secret && message is TL_message && (MessageObject.getMedia(message) is TL_messageMediaPhoto || MessageObject.getMedia(message) is TL_messageMediaDocument) && MessageObject.getMedia(message)!!.ttl_seconds != 0) {
				false
			}
			else {
				getMediaType(message) != -1
			}
		}

		private var roundPaint: Paint? = null
		private var erasePaint: Paint? = null
		private var bitmapRect: RectF? = null
		private var roundPath: Path? = null

		//---------------- SEARCH END ----------------
		private val entityComparator = Comparator { entity1: MessageEntity, entity2: MessageEntity ->
			if (entity1.offset > entity2.offset) {
				return@Comparator 1
			}
			else if (entity1.offset < entity2.offset) {
				return@Comparator -1
			}
			0
		}

		private fun removeEmptyMessages(messages: MutableList<Message>) {
			var a = 0

			while (a < messages.size) {
				val message = messages[a]

				if (message is TL_messageEmpty || message.action is TL_messageActionHistoryClear) {
					messages.removeAt(a)
					a--
				}

				a++
			}
		}

		fun sortEntities(entities: MutableList<MessageEntity>?) {
			if (entities == null) {
				return
			}

			Collections.sort(entities, entityComparator)
		}

		private fun checkInclusion(index: Int, entities: List<MessageEntity>?, end: Boolean): Boolean {
			if (entities.isNullOrEmpty()) {
				return false
			}

			val count = entities.size

			for (a in 0 until count) {
				val entity = entities[a]

				if ((if (end) entity.offset < index else entity.offset <= index) && entity.offset + entity.length > index) {
					return true
				}
			}

			return false
		}

		private fun checkIntersection(start: Int, end: Int, entities: List<MessageEntity>?): Boolean {
			if (entities.isNullOrEmpty()) {
				return false
			}

			val count = entities.size

			for (a in 0 until count) {
				val entity = entities[a]

				if (entity.offset > start && entity.offset + entity.length <= end) {
					return true
				}
			}

			return false
		}

		private fun createNewSpan(baseSpan: CharacterStyle, textStyleRun: TextStyleRun, newStyleRun: TextStyleRun?, allowIntersection: Boolean): CharacterStyle? {
			val run = TextStyleRun(textStyleRun)

			if (newStyleRun != null) {
				if (allowIntersection) {
					run.merge(newStyleRun)
				}
				else {
					run.replace(newStyleRun)
				}
			}

			if (baseSpan is TextStyleSpan) {
				return TextStyleSpan(run)
			}
			else if (baseSpan is URLSpanReplacement) {
				return URLSpanReplacement(baseSpan.url, run)
			}

			return null
		}

		fun addStyleToText(span: TextStyleSpan?, start: Int, end: Int, editable: Spannable, allowIntersection: Boolean) {
			@Suppress("NAME_SHADOWING") var start = start
			@Suppress("NAME_SHADOWING") var end = end

			try {
				val spans = editable.getSpans(start, end, CharacterStyle::class.java)

				if (spans != null && spans.isNotEmpty()) {
					for (a in spans.indices) {
						val oldSpan = spans[a]
						var textStyleRun: TextStyleRun?
						val newStyleRun = span?.style ?: TextStyleRun()

						if (oldSpan is TextStyleSpan) {
							textStyleRun = oldSpan.style
						}
						else if (oldSpan is URLSpanReplacement) {
							textStyleRun = oldSpan.textStyleRun

							if (textStyleRun == null) {
								textStyleRun = TextStyleRun()
							}
						}
						else {
							continue
						}

						val spanStart = editable.getSpanStart(oldSpan)
						val spanEnd = editable.getSpanEnd(oldSpan)

						editable.removeSpan(oldSpan)

						if (spanStart > start && end > spanEnd) {
							editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

							if (span != null) {
								editable.setSpan(TextStyleSpan(TextStyleRun(newStyleRun)), spanEnd, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
							}

							end = spanStart
						}
						else {
							val startTemp = start

							if (spanStart <= start) {
								if (spanStart != start) {
									editable.setSpan(createNewSpan(oldSpan, textStyleRun, null, allowIntersection), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
								}

								if (spanEnd > start) {
									if (span != null) {
										editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), start, min(spanEnd.toDouble(), end.toDouble()).toInt(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
									}

									start = spanEnd
								}
							}

							if (spanEnd >= end) {
								if (spanEnd != end) {
									editable.setSpan(createNewSpan(oldSpan, textStyleRun, null, allowIntersection), end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
								}

								if (end > spanStart && spanEnd <= startTemp) {
									if (span != null) {
										editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), spanStart, min(spanEnd.toDouble(), end.toDouble()).toInt(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
									}

									end = spanStart
								}
							}
						}
					}
				}

				if (span != null && start < end && start < editable.length) {
					editable.setSpan(span, start, min(editable.length.toDouble(), end.toDouble()).toInt(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}

		fun addTextStyleRuns(msg: MessageObject?, text: Spannable) {
			addTextStyleRuns(msg?.messageOwner?.entities, msg?.messageText, text, -1)
		}

		fun addTextStyleRuns(msg: DraftMessage?, text: Spannable, allowedFlags: Int) {
			addTextStyleRuns(msg?.entities, msg?.message, text, allowedFlags)
		}

		fun addTextStyleRuns(msg: MessageObject?, text: Spannable, allowedFlags: Int) {
			addTextStyleRuns(msg?.messageOwner?.entities, msg?.messageText, text, allowedFlags)
		}

		@JvmOverloads
		@JvmStatic
		fun addTextStyleRuns(entities: List<MessageEntity>?, messageText: CharSequence?, text: Spannable, allowedFlags: Int = -1) {
			for (prevSpan in text.getSpans(0, text.length, TextStyleSpan::class.java)) {
				text.removeSpan(prevSpan)
			}

			for (run in getTextStyleRuns(entities, messageText, allowedFlags)) {
				addStyleToText(TextStyleSpan(run), run.start, run.end, text, true)
			}
		}

		@JvmStatic
		fun addAnimatedEmojiSpans(entities: List<MessageEntity>?, messageText: CharSequence?, fontMetricsInt: FontMetricsInt?) {
			if (messageText !is Spannable || entities == null) {
				return
			}

			val emojiSpans = messageText.getSpans(0, messageText.length, AnimatedEmojiSpan::class.java)

			for (j in emojiSpans.indices) {
				val span = emojiSpans[j]

				if (span != null) {
					messageText.removeSpan(span)
				}
			}

			for (i in entities.indices) {
				val messageEntity = entities[i]

				if (messageEntity is TL_messageEntityCustomEmoji) {
					val start = messageEntity.offset
					val end = messageEntity.offset + messageEntity.length

					if (start < end && end <= messageText.length) {
						val span = if (messageEntity.document != null) {
							AnimatedEmojiSpan(messageEntity.document!!, fontMetricsInt)
						}
						else {
							AnimatedEmojiSpan(messageEntity.documentId, fontMetricsInt)
						}

						messageText.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
				}
			}
		}

		private fun getTextStyleRuns(entities: List<MessageEntity>?, text: CharSequence?, allowedFlags: Int): List<TextStyleRun> {
			val runs = mutableListOf<TextStyleRun>()
			val entitiesCopy = entities?.toMutableList() ?: return runs

			entitiesCopy.sortWith { o1, o2 ->
				if (o1.offset > o2.offset) {
					return@sortWith 1
				}
				else if (o1.offset < o2.offset) {
					return@sortWith -1
				}

				0
			}

			var a = 0
			val n = entitiesCopy.size

			while (a < n) {
				val entity = entitiesCopy[a]

				if (entity.length <= 0 || entity.offset < 0 || entity.offset >= text!!.length) {
					a++
					continue
				}
				else if (entity.offset + entity.length > text.length) {
					entity.length = text.length - entity.offset
				}

				if (entity is TL_messageEntityCustomEmoji) {
					a++
					continue
				}

				val newRun = TextStyleRun()
				newRun.start = entity.offset
				newRun.end = newRun.start + entity.length

				when (entity) {
					is TL_messageEntitySpoiler -> {
						newRun.styleFlags = TextStyleSpan.FLAG_STYLE_SPOILER
					}

					is TL_messageEntityStrike -> {
						newRun.styleFlags = TextStyleSpan.FLAG_STYLE_STRIKE
					}

					is TL_messageEntityUnderline -> {
						newRun.styleFlags = TextStyleSpan.FLAG_STYLE_UNDERLINE
					}

					is TL_messageEntityBlockquote -> {
						newRun.styleFlags = TextStyleSpan.FLAG_STYLE_QUOTE
					}

					is TL_messageEntityBold -> {
						newRun.styleFlags = TextStyleSpan.FLAG_STYLE_BOLD
					}

					is TL_messageEntityItalic -> {
						newRun.styleFlags = TextStyleSpan.FLAG_STYLE_ITALIC
					}

					is TL_messageEntityCode, is TL_messageEntityPre -> {
						newRun.styleFlags = TextStyleSpan.FLAG_STYLE_MONO
					}

					is TL_messageEntityMentionName -> {
						newRun.styleFlags = TextStyleSpan.FLAG_STYLE_MENTION
						newRun.urlEntity = entity
					}

					is TL_inputMessageEntityMentionName -> {
						newRun.styleFlags = TextStyleSpan.FLAG_STYLE_MENTION
						newRun.urlEntity = entity
					}

					else -> {
						newRun.styleFlags = TextStyleSpan.FLAG_STYLE_URL
						newRun.urlEntity = entity
					}
				}

				newRun.styleFlags = newRun.styleFlags and allowedFlags

				var b = 0
				var n2 = runs.size

				while (b < n2) {
					val run = runs[b]

					if (newRun.start > run.start) {
						if (newRun.start >= run.end) {
							b++
							continue
						}

						if (newRun.end < run.end) {
							var r = TextStyleRun(newRun)
							r.merge(run)
							b++
							n2++
							runs.add(b, r)

							r = TextStyleRun(run)
							r.start = newRun.end
							b++
							n2++
							runs.add(b, r)
						}
						else {
							val r = TextStyleRun(newRun)
							r.merge(run)
							r.end = run.end
							b++
							n2++
							runs.add(b, r)
						}

						val temp = newRun.start
						newRun.start = run.end
						run.end = temp
					}
					else {
						if (run.start >= newRun.end) {
							b++
							continue
						}

						val temp = run.start

						if (newRun.end == run.end) {
							run.merge(newRun)
						}
						else if (newRun.end < run.end) {
							val r = TextStyleRun(run)
							r.merge(newRun)
							r.end = newRun.end
							b++
							n2++
							runs.add(b, r)

							run.start = newRun.end
						}
						else {
							val r = TextStyleRun(newRun)
							r.start = run.end
							b++
							n2++
							runs.add(b, r)

							run.merge(newRun)
						}

						newRun.end = temp
					}

					b++
				}

				if (newRun.start < newRun.end) {
					runs.add(newRun)
				}

				a++
			}

			return runs
		}

		fun saveReactionsToPref(preferences: SharedPreferences, hash: Long, `object`: List<TLObject>) {
			val editor = preferences.edit()
			editor.putInt("count", `object`.size)
			editor.putLong("hash", hash)

			for (i in `object`.indices) {
				val tlObject = `object`[i]
				val data = SerializedData(tlObject.objectSize)
				tlObject.serializeToStream(data)
				editor.putString("object_$i", Utilities.bytesToHex(data.toByteArray()))
			}

			editor.commit()
		}

		fun loadReactionsFromPref(preferences: SharedPreferences): List<Reaction> {
			val count = preferences.getInt("count", 0)
			val objects = mutableListOf<Reaction>()

			if (count > 0) {
				for (i in 0 until count) {
					val value = preferences.getString("object_$i", "")
					val serializedData = SerializedData(Utilities.hexToBytes(value))

					try {
						val reaction = Reaction.TLdeserialize(serializedData, serializedData.readInt32(true), true)

						if (reaction != null) {
							objects.add(reaction)
						}
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}
			}

			return objects
		}
	}
}
