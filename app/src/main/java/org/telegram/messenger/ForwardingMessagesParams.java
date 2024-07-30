/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.messenger;

import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseBooleanArray;

import org.telegram.messenger.messageobject.GroupedMessages;
import org.telegram.messenger.messageobject.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tlrpc.Message;
import org.telegram.tgnet.tlrpc.MessageEntity;
import org.telegram.tgnet.tlrpc.TL_message;
import org.telegram.tgnet.tlrpc.TL_messageEntitySpoiler;

import java.util.ArrayList;
import java.util.List;

public class ForwardingMessagesParams {
	public LongSparseArray<GroupedMessages> groupedMessagesMap = new LongSparseArray<>();
	public List<MessageObject> messages;
	public ArrayList<MessageObject> previewMessages = new ArrayList<>();
	public SparseBooleanArray selectedIds = new SparseBooleanArray();
	public boolean hideForwardSendersName;
	public boolean hideCaption;
	public boolean hasCaption;
	public boolean hasSenders;
	public boolean isSecret;
	public boolean willSeeSenders;
	public boolean multiplyUsers;
	public boolean hasSpoilers;

	public ArrayList<TLRPC.TL_pollAnswerVoters> pollChoosenAnswers = new ArrayList<>();

	public ForwardingMessagesParams(List<MessageObject> messages, long newDialogId) {
		this.messages = messages;
		hasCaption = false;
		hasSenders = false;
		isSecret = DialogObject.isEncryptedDialog(newDialogId);
		hasSpoilers = false;
		ArrayList<String> hiddenSendersName = new ArrayList<>();
		for (int i = 0; i < messages.size(); i++) {
			MessageObject messageObject = messages.get(i);
			if (!TextUtils.isEmpty(messageObject.caption)) {
				hasCaption = true;
			}
			selectedIds.put(messageObject.getId(), true);

			Message message = new TL_message();
			message.id = messageObject.messageOwner.id;
			message.setGroupId(messageObject.messageOwner.getRealGroupId());
			message.peer_id = messageObject.messageOwner.peer_id;
			message.from_id = messageObject.messageOwner.from_id;
			message.message = messageObject.messageOwner.message;
			message.media = messageObject.messageOwner.media;
			message.action = messageObject.messageOwner.action;
			message.edit_date = 0;
			if (messageObject.messageOwner.entities != null) {
				message.entities.addAll(messageObject.messageOwner.entities);
				if (!hasSpoilers) {
					for (MessageEntity e : message.entities) {
						if (e instanceof TL_messageEntitySpoiler) {
							hasSpoilers = true;
							break;
						}
					}
				}
			}

			message.out = true;
			message.unread = false;
			message.via_bot_id = messageObject.messageOwner.via_bot_id;
			message.reply_markup = messageObject.messageOwner.reply_markup;
			message.post = messageObject.messageOwner.post;
			message.legacy = messageObject.messageOwner.legacy;
			message.restriction_reason = messageObject.messageOwner.restriction_reason;
			message.replyMessage = messageObject.messageOwner.replyMessage;

			TLRPC.MessageFwdHeader header = null;

			long clientUserId = UserConfig.getInstance(messageObject.currentAccount).clientUserId;
			if (!isSecret) {
				if (messageObject.messageOwner.fwd_from != null) {
					header = messageObject.messageOwner.fwd_from;
					if (!messageObject.isDice()) {
						hasSenders = true;
					}
					else {
						willSeeSenders = true;
					}
					if (header.from_id == null && !hiddenSendersName.contains(header.from_name)) {
						hiddenSendersName.add(header.from_name);
					}
				}
				else if (messageObject.messageOwner.from_id.user_id == 0 || messageObject.messageOwner.dialog_id != clientUserId || messageObject.messageOwner.from_id.user_id != clientUserId) {
					header = new TLRPC.TL_messageFwdHeader();
					header.from_id = messageObject.messageOwner.from_id;
					if (!messageObject.isDice()) {
						hasSenders = true;
					}
					else {
						willSeeSenders = true;
					}
				}
			}

			if (header != null) {
				message.fwd_from = header;
				message.flags |= TLRPC.MESSAGE_FLAG_FWD;
			}
			message.dialog_id = newDialogId;

			MessageObject previewMessage = new MessageObject(messageObject.currentAccount, message, true, false) {
				@Override
				public boolean needDrawForwarded() {
					if (hideForwardSendersName) {
						return false;
					}
					return super.needDrawForwarded();
				}
			};
			previewMessage.preview = true;
			if (previewMessage.getGroupId() != 0) {
				GroupedMessages groupedMessages = groupedMessagesMap.get(previewMessage.getGroupId(), null);
				if (groupedMessages == null) {
					groupedMessages = new GroupedMessages();
					groupedMessagesMap.put(previewMessage.getGroupId(), groupedMessages);
				}
				groupedMessages.messages.add(previewMessage);
			}
			previewMessages.add(0, previewMessage);

			if (messageObject.isPoll()) {
				TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll)messageObject.messageOwner.media;
				PreviewMediaPoll newMediaPoll = new PreviewMediaPoll();
				newMediaPoll.poll = mediaPoll.poll;
				newMediaPoll.provider = mediaPoll.provider;
				newMediaPoll.results = new TLRPC.TL_pollResults();
				newMediaPoll.totalVotersCached = newMediaPoll.results.total_voters = mediaPoll.results.total_voters;

				previewMessage.messageOwner.media = newMediaPoll;

				if (messageObject.canUnvote()) {
					for (int a = 0, N = mediaPoll.results.results.size(); a < N; a++) {
						TLRPC.TL_pollAnswerVoters answer = mediaPoll.results.results.get(a);
						if (answer.chosen) {
							TLRPC.TL_pollAnswerVoters newAnswer = new TLRPC.TL_pollAnswerVoters();
							newAnswer.chosen = answer.chosen;
							newAnswer.correct = answer.correct;
							newAnswer.flags = answer.flags;
							newAnswer.option = answer.option;
							newAnswer.voters = answer.voters;
							pollChoosenAnswers.add(newAnswer);
							newMediaPoll.results.results.add(newAnswer);
						}
						else {
							newMediaPoll.results.results.add(answer);
						}
					}
				}
			}
		}

		ArrayList<Long> uids = new ArrayList<>();
		for (int a = 0; a < messages.size(); a++) {
			MessageObject object = messages.get(a);
			long uid;
			if (object.isFromUser()) {
				uid = object.messageOwner.from_id.user_id;
			}
			else {
				TLRPC.Chat chat = MessagesController.getInstance(object.currentAccount).getChat(object.messageOwner.peer_id.channel_id);
				if (ChatObject.isChannel(chat) && chat.megagroup && object.isForwardedChannelPost()) {
					uid = -object.messageOwner.fwd_from.from_id.channel_id;
				}
				else {
					uid = -object.messageOwner.peer_id.channel_id;
				}
			}
			if (!uids.contains(uid)) {
				uids.add(uid);
			}
		}
		if (uids.size() + hiddenSendersName.size() > 1) {
			multiplyUsers = true;
		}
		for (int i = 0; i < groupedMessagesMap.size(); i++) {
			groupedMessagesMap.valueAt(i).calculate();
		}
	}

	public void getSelectedMessages(List<MessageObject> messagesToForward) {
		messagesToForward.clear();
		for (int i = 0; i < messages.size(); i++) {
			MessageObject messageObject = messages.get(i);
			int id = messageObject.getId();
			if (selectedIds.get(id, false)) {
				messagesToForward.add(messageObject);
			}
		}
	}

	public static class PreviewMediaPoll extends TLRPC.TL_messageMediaPoll {
		public int totalVotersCached;
	}
}
