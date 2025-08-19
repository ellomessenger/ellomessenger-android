/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024-2025.
 */
package org.telegram.ui.Components

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.IntDef
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLoader.Companion.getClosestPhotoSizeWithSize
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaDataController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.messageobject.MessageObject.Companion.getInputStickerSet
import org.telegram.messenger.messageobject.MessageObject.Companion.isAnimatedStickerDocument
import org.telegram.messenger.messageobject.MessageObject.Companion.isGifDocument
import org.telegram.messenger.messageobject.MessageObject.Companion.isVideoSticker
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.StickerSetCovered
import org.telegram.tgnet.TLRPC.TLMessagesStickerSet
import org.telegram.tgnet.cover
import org.telegram.tgnet.covers
import org.telegram.tgnet.thumbs
import org.telegram.ui.Components.Bulletin.TwoLineLayout
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet.Companion.limitTypeToServerString
import org.telegram.ui.LaunchActivity
import org.telegram.ui.PremiumPreviewFragment

@SuppressLint("ViewConstructor")
class StickerSetBulletinLayout(context: Context, setObject: TLObject?, count: Int, @Type type: Int, sticker: TLRPC.Document?) : TwoLineLayout(context) {
	constructor(context: Context, setObject: TLObject?, @Type type: Int) : this(context, setObject, 1, type, null)

	constructor(context: Context, setObject: TLObject?, @Type type: Int, sticker: TLRPC.Document?) : this(context, setObject, 1, type, sticker)

	init {
		@Suppress("NAME_SHADOWING") var sticker = sticker
		var stickerSet: TLRPC.TLStickerSet?

		when (setObject) {
			is TLMessagesStickerSet -> {
				stickerSet = setObject.set
				val documents = setObject.documents
				sticker = documents.firstOrNull()
			}

			is StickerSetCovered -> {
				stickerSet = setObject.set
				sticker = setObject.cover ?: setObject.covers?.firstOrNull()
			}

			else -> {
				require(!(sticker == null && setObject != null && BuildConfig.DEBUG)) { "Invalid type of the given setObject: " + setObject?.javaClass }
				stickerSet = null
			}
		}

		if (stickerSet == null && sticker != null) {
			val set = MediaDataController.getInstance(UserConfig.selectedAccount).getStickerSet(getInputStickerSet(sticker), true)

			if (set != null) {
				stickerSet = set.set
			}
		}

		if (sticker != null) {
			var `object`: TLObject? = if (stickerSet == null) null else getClosestPhotoSizeWithSize(stickerSet.thumbs, 90)

			if (`object` == null) {
				`object` = sticker
			}

			val imageLocation: ImageLocation?

			if (`object` is TLRPC.Document) {
				val thumb = getClosestPhotoSizeWithSize(sticker.thumbs, 90)
				imageLocation = ImageLocation.getForDocument(thumb, sticker)
			}
			else {
				val thumb = `object` as PhotoSize
				var thumbVersion = 0

				if (setObject is StickerSetCovered) {
					thumbVersion = setObject.set?.thumbVersion ?: 0
				}
				else if (setObject is TLMessagesStickerSet) {
					thumbVersion = setObject.set?.thumbVersion ?: 0
				}

				imageLocation = ImageLocation.getForSticker(thumb, sticker, thumbVersion)
			}

			if (`object` is TLRPC.Document && isAnimatedStickerDocument(sticker, true) || isVideoSticker(sticker) || isGifDocument(sticker)) {
				imageView.setImage(ImageLocation.getForDocument(sticker), "50_50", imageLocation, null, 0, setObject)
			}
			else if (imageLocation != null && imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE) {
				imageView.setImage(imageLocation, "50_50", "tgs", null, setObject)
			}
			else {
				imageView.setImage(imageLocation, "50_50", "webp", null, setObject)
			}
		}
		else {
			imageView.setImage(null, null, "webp", null, setObject)
		}

		when (type) {
			TYPE_ADDED -> if (stickerSet != null) {
				if (stickerSet.masks) {
					titleTextView.text = context.getString(R.string.AddMasksInstalled)
					subtitleTextView.text = LocaleController.formatString("AddMasksInstalledInfo", R.string.AddMasksInstalledInfo, stickerSet.title)
				}
				else if (stickerSet.emojis) {
					titleTextView.text = context.getString(R.string.AddEmojiInstalled)

					if (count > 1) {
						subtitleTextView.text = LocaleController.formatPluralString("AddEmojiMultipleInstalledInfo", count)
					}
					else {
						subtitleTextView.text = LocaleController.formatString("AddEmojiInstalledInfo", R.string.AddEmojiInstalledInfo, stickerSet.title)
					}
				}
				else {
					titleTextView.text = context.getString(R.string.AddStickersInstalled)
					subtitleTextView.text = LocaleController.formatString("AddStickersInstalledInfo", R.string.AddStickersInstalledInfo, stickerSet.title)
				}
			}

			TYPE_REMOVED -> if (stickerSet != null) {
				if (stickerSet.masks) {
					titleTextView.text = context.getString(R.string.MasksRemoved)
					subtitleTextView.text = LocaleController.formatString("MasksRemovedInfo", R.string.MasksRemovedInfo, stickerSet.title)
				}
				else if (stickerSet.emojis) {
					titleTextView.text = context.getString(R.string.EmojiRemoved)

					if (count > 1) {
						subtitleTextView.text = LocaleController.formatPluralString("EmojiRemovedMultipleInfo", count)
					}
					else {
						subtitleTextView.text = LocaleController.formatString("EmojiRemovedInfo", R.string.EmojiRemovedInfo, stickerSet.title)
					}
				}
				else {
					titleTextView.text = context.getString(R.string.StickersRemoved)
					subtitleTextView.text = LocaleController.formatString("StickersRemovedInfo", R.string.StickersRemovedInfo, stickerSet.title)
				}
			}

			TYPE_ARCHIVED -> if (stickerSet != null) {
				if (stickerSet.masks) {
					titleTextView.text = context.getString(R.string.MasksArchived)
					subtitleTextView.text = LocaleController.formatString("MasksArchivedInfo", R.string.MasksArchivedInfo, stickerSet.title)
				}
				else {
					titleTextView.text = context.getString(R.string.StickersArchived)
					subtitleTextView.text = LocaleController.formatString("StickersArchivedInfo", R.string.StickersArchivedInfo, stickerSet.title)
				}
			}

			TYPE_REMOVED_FROM_FAVORITES -> {
				titleTextView.text = context.getString(R.string.RemovedFromFavorites)
				subtitleTextView.visibility = GONE
			}

			TYPE_ADDED_TO_FAVORITES -> {
				titleTextView.text = context.getString(R.string.AddedToFavorites)
				subtitleTextView.visibility = GONE
			}

			TYPE_REPLACED_TO_FAVORITES -> {
				if (!UserConfig.getInstance(UserConfig.selectedAccount).isPremium && !MessagesController.getInstance(UserConfig.selectedAccount).premiumLocked) {
					titleTextView.text = LocaleController.formatString("LimitReachedFavoriteStickers", R.string.LimitReachedFavoriteStickers, MessagesController.getInstance(UserConfig.selectedAccount).stickersFavedLimitDefault)

					val str = AndroidUtilities.replaceSingleTag(LocaleController.formatString("LimitReachedFavoriteStickersSubtitle", R.string.LimitReachedFavoriteStickersSubtitle, MessagesController.getInstance(UserConfig.selectedAccount).stickersFavedLimitPremium)) {
						val activity = AndroidUtilities.findActivity(context)

						if (activity is LaunchActivity) {
							activity.presentFragment(PremiumPreviewFragment(limitTypeToServerString(LimitReachedBottomSheet.TYPE_STICKERS)))
						}
					}

					subtitleTextView.text = str
				}
				else {
					titleTextView.text = LocaleController.formatString("LimitReachedFavoriteStickers", R.string.LimitReachedFavoriteStickers, MessagesController.getInstance(UserConfig.selectedAccount).stickersFavedLimitPremium)
					subtitleTextView.text = LocaleController.formatString("LimitReachedFavoriteStickersSubtitlePremium", R.string.LimitReachedFavoriteStickersSubtitlePremium)
				}
			}

			TYPE_REPLACED_TO_FAVORITES_GIFS -> {
				if (!UserConfig.getInstance(UserConfig.selectedAccount).isPremium && !MessagesController.getInstance(UserConfig.selectedAccount).premiumLocked) {
					titleTextView.text = LocaleController.formatString("LimitReachedFavoriteGifs", R.string.LimitReachedFavoriteGifs, MessagesController.getInstance(UserConfig.selectedAccount).savedGifsLimitDefault)

					val str = AndroidUtilities.replaceSingleTag(LocaleController.formatString("LimitReachedFavoriteGifsSubtitle", R.string.LimitReachedFavoriteGifsSubtitle, MessagesController.getInstance(UserConfig.selectedAccount).savedGifsLimitPremium)) {
						val activity = AndroidUtilities.findActivity(context)

						if (activity is LaunchActivity) {
							activity.presentFragment(PremiumPreviewFragment(limitTypeToServerString(LimitReachedBottomSheet.TYPE_GIFS)))
						}
					}

					subtitleTextView.text = str
				}
				else {
					titleTextView.text = LocaleController.formatString("LimitReachedFavoriteGifs", R.string.LimitReachedFavoriteGifs, MessagesController.getInstance(UserConfig.selectedAccount).savedGifsLimitPremium)
					subtitleTextView.text = LocaleController.formatString("LimitReachedFavoriteGifsSubtitlePremium", R.string.LimitReachedFavoriteGifsSubtitlePremium)
				}
			}

			TYPE_REMOVED_FROM_RECENT -> {
				titleTextView.text = context.getString(R.string.RemovedFromRecent)
				subtitleTextView.visibility = GONE
			}

			TYPE_EMPTY -> {
				// unused
			}
		}
	}

	@IntDef(value = [TYPE_EMPTY, TYPE_REMOVED, TYPE_ARCHIVED, TYPE_ADDED, TYPE_REMOVED_FROM_RECENT, TYPE_REMOVED_FROM_FAVORITES, TYPE_ADDED_TO_FAVORITES, TYPE_REPLACED_TO_FAVORITES, TYPE_REPLACED_TO_FAVORITES_GIFS])
	annotation class Type
	companion object {
		const val TYPE_EMPTY: Int = -1
		const val TYPE_REMOVED: Int = 0
		const val TYPE_ARCHIVED: Int = 1
		const val TYPE_ADDED: Int = 2
		const val TYPE_REMOVED_FROM_RECENT: Int = 3
		const val TYPE_REMOVED_FROM_FAVORITES: Int = 4
		const val TYPE_ADDED_TO_FAVORITES: Int = 5
		const val TYPE_REPLACED_TO_FAVORITES: Int = 6
		const val TYPE_REPLACED_TO_FAVORITES_GIFS: Int = 7
	}
}
