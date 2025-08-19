/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Cells;

import android.os.Bundle;
import android.text.style.CharacterStyle;

import org.telegram.messenger.messageobject.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.TLReactionCount;
import org.telegram.tgnet.TLRPC.User;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.PinchToZoomHelper;

import java.util.List;

import androidx.annotation.Nullable;

public interface ChatMessageCellDelegate {
	default void didPressExtendedMediaPreview(ChatMessageCell cell, TLRPC.KeyboardButton button) {
	}

	default void didPressUserAvatar(ChatMessageCell cell, User user, float touchX, float touchY) {
	}

	default boolean didLongPressUserAvatar(ChatMessageCell cell, User user, float touchX, float touchY) {
		return false;
	}

	default void didPressHiddenForward(ChatMessageCell cell) {
	}

	default void didPressViaBotNotInline(ChatMessageCell cell, long botId) {
	}

	default void didPressViaBot(ChatMessageCell cell, String username) {
	}

	default void didPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY) {
	}

	default boolean didLongPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId, float touchX, float touchY) {
		return false;
	}

	default void didPressCancelSendButton(ChatMessageCell cell) {
	}

	default void didLongPress(ChatMessageCell cell, float x, float y) {
	}

	default void didPressReplyMessage(ChatMessageCell cell, int id) {
	}

	default void didPressUrl(ChatMessageCell cell, CharacterStyle url, boolean longPress) {
	}

	default void needOpenWebView(MessageObject message, String url, String title, String description, String originalUrl, int w, int h) {
	}

	default void didPressImage(ChatMessageCell cell, float x, float y) {
	}

	default void didPressSideButton(ChatMessageCell cell) {
	}

	default void didPressOther(ChatMessageCell cell, float otherX, float otherY) {
	}

	default void didPressTime(ChatMessageCell cell) {
	}

	default void didPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {
	}

	default void didLongPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {
	}

	default void didPressReaction(ChatMessageCell cell, TLReactionCount reaction, boolean longpress) {
	}

	default void didPressVoteButtons(ChatMessageCell cell, List<TLRPC.TLPollAnswer> buttons, int showCount, int x, int y) {
	}

	default void didPressInstantButton(ChatMessageCell cell, int type) {
	}

	default void didPressCommentButton(ChatMessageCell cell) {
	}

	default void didPressHint(ChatMessageCell cell, int type) {
	}

	default void needShowPremiumFeatures(String source) {
	}

	@Nullable
	default String getAdminRank(long uid) {
		return null;
	}

	default boolean needPlayMessage(MessageObject messageObject) {
		return false;
	}

	default boolean canPerformActions() {
		return false;
	}

	default boolean onAccessibilityAction(int action, Bundle arguments) {
		return false;
	}

	default void videoTimerReached() {
	}

	default void didStartVideoStream(MessageObject message) {
	}

	default boolean shouldRepeatSticker(MessageObject message) {
		return true;
	}

	default void setShouldNotRepeatSticker(MessageObject message) {
	}

	@Nullable
	default TextSelectionHelper.ChatListTextSelectionHelper getTextSelectionHelper() {
		return null;
	}

	default boolean hasSelectedMessages() {
		return false;
	}

	default void needReloadPolls() {

	}

	default void onDiceFinished() {

	}

	default boolean shouldDrawThreadProgress(ChatMessageCell cell) {
		return false;
	}

	@Nullable
	default PinchToZoomHelper getPinchToZoomHelper() {
		return null;
	}

	default boolean keyboardIsOpened() {
		return false;
	}

	default boolean isLandscape() {
		return false;
	}

	default void invalidateBlur() {
	}

	default boolean canDrawOutboundsContent() {
		return true;
	}

	default boolean didPressAnimatedEmoji(@Nullable AnimatedEmojiSpan span) {
		return false;
	}
}
