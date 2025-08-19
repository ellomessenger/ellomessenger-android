/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.messenger;

import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseBooleanArray;

import org.telegram.messenger.messageobject.GroupedMessages;
import org.telegram.messenger.messageobject.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.MessageEntity;
import org.telegram.tgnet.TLRPC.TLMessage;
import org.telegram.tgnet.TLRPCExtensions;

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

	public ArrayList<TLRPC.TLPollAnswerVoters> pollChoosenAnswers = new ArrayList<>();

	public ForwardingMessagesParams(List<MessageObject> messages, long newDialogId) {
		this.messages = messages;
		hasCaption = false;
		hasSenders = false;
		isSecret = DialogObject.isEncryptedDialog(newDialogId);
		hasSpoilers = false;

		ArrayList<String> hiddenSendersName = new ArrayList<>();

		for (int i = 0; i < messages.size(); i++) {
			MessageObject messageObject = messages.get(i);

			if (messageObject.messageOwner instanceof TLRPC.TLMessage messageOwner) {
				if (!TextUtils.isEmpty(messageObject.caption)) {
					hasCaption = true;
				}

				selectedIds.put(messageObject.getId(), true);

				var message = new TLMessage();

				message.id = messageOwner.id;
				message.groupedId = messageOwner.groupedId;
				message.message = messageOwner.message;
				message.media = messageOwner.media;
				message.peerId = messageOwner.peerId;
				message.fromId = messageOwner.fromId;

				message.editDate = 0;
				message.viaBotId = messageOwner.viaBotId;
				message.replyMarkup = messageOwner.replyMarkup;

				message.restrictionReason.clear();
				message.restrictionReason.addAll(messageOwner.restrictionReason);

				if (!messageOwner.entities.isEmpty()) {
					message.entities.addAll(messageOwner.entities);

					if (!hasSpoilers) {
						for (MessageEntity e : message.entities) {
							if (e instanceof TLRPC.TLMessageEntitySpoiler) {
								hasSpoilers = true;
								break;
							}
						}
					}
				}

				message.out = true;
				message.unread = false;
				message.post = messageOwner.post;
				message.legacy = messageOwner.legacy;
				message.replyMessage = messageOwner.replyMessage;

				TLRPC.TLMessageFwdHeader header = null;

				long clientUserId = UserConfig.getInstance(messageObject.currentAccount).clientUserId;

				if (!isSecret) {
					if (messageOwner.fwdFrom != null) {
						header = messageOwner.fwdFrom;

						if (!messageObject.isDice()) {
							hasSenders = true;
						}
						else {
							willSeeSenders = true;
						}
						if (header.fromId == null && !hiddenSendersName.contains(header.fromName)) {
							hiddenSendersName.add(header.fromName);
						}
					}
					else if (TLRPCExtensions.getUserId(messageOwner.fromId) == 0 || messageOwner.dialogId != clientUserId || TLRPCExtensions.getUserId(messageOwner.fromId) != clientUserId) {
						header = new TLRPC.TLMessageFwdHeader();
						header.fromId = messageOwner.fromId;

						if (!messageObject.isDice()) {
							hasSenders = true;
						}
						else {
							willSeeSenders = true;
						}
					}
				}

				if (header != null) {
					message.fwdFrom = header;
					message.flags |= TLRPC.MESSAGE_FLAG_FWD;
				}

				message.dialogId = newDialogId;

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
					var mediaPoll = (TLRPC.TLMessageMediaPoll)TLRPCExtensions.getMedia(messageOwner);

					if (mediaPoll != null) {
						var newMediaPoll = new PreviewMediaPoll();

						newMediaPoll.poll = mediaPoll.poll;
						// newMediaPoll.provider = mediaPoll.provider;
						newMediaPoll.results = new TLRPC.TLPollResults();
						newMediaPoll.totalVotersCached = newMediaPoll.results.totalVoters = mediaPoll.results.totalVoters;

						((TLRPC.TLMessage)previewMessage.messageOwner).media = newMediaPoll;

						if (messageObject.canUnvote()) {
							for (int a = 0, N = mediaPoll.results.results.size(); a < N; a++) {
								TLRPC.TLPollAnswerVoters answer = mediaPoll.results.results.get(a);

								if (answer.chosen) {
									var newAnswer = new TLRPC.TLPollAnswerVoters();
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
			}
		}

		ArrayList<Long> uids = new ArrayList<>();

		for (int a = 0; a < messages.size(); a++) {
			MessageObject object = messages.get(a);
			long uid;

			if (object.isFromUser()) {
				uid = TLRPCExtensions.getUserId(object.messageOwner.fromId);
			}
			else {
				TLRPC.Chat chat = MessagesController.getInstance(object.currentAccount).getChat(TLRPCExtensions.getChannelId(object.messageOwner.peerId));

				if (ChatObject.isChannel(chat) && chat.megagroup && object.isForwardedChannelPost()) {
					uid = -TLRPCExtensions.getChannelId(((TLMessage)object.messageOwner).fwdFrom.fromId);
				}
				else {
					uid = -TLRPCExtensions.getChannelId(object.messageOwner.peerId);
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

	public static class PreviewMediaPoll extends TLRPC.TLMessageMediaPoll {
		public int totalVotersCached;
	}
}
