/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.messenger;

import android.text.TextUtils;
import android.util.LongSparseArray;

import org.telegram.messenger.messageobject.MessageObject;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.User;

import androidx.annotation.Nullable;

public class MemberRequestsController extends BaseController {
	private static final MemberRequestsController[] instances = new MemberRequestsController[UserConfig.MAX_ACCOUNT_COUNT];

	public static MemberRequestsController getInstance(int accountNum) {
		MemberRequestsController local = instances[accountNum];
		if (local == null) {
			synchronized (MemberRequestsController.class) {
				local = instances[accountNum];
				if (local == null) {
					local = new MemberRequestsController(accountNum);
					instances[accountNum] = local;
				}
			}
		}
		return local;
	}

	private final LongSparseArray<TLRPC.TLMessagesChatInviteImporters> firstImportersCache = new LongSparseArray<>();

	public MemberRequestsController(int accountNum) {
		super(accountNum);
	}

	@Nullable
	public TLRPC.TLMessagesChatInviteImporters getCachedImporters(long chatId) {
		return firstImportersCache.get(chatId);
	}

	public int getImporters(final long chatId, final String query, TLRPC.TLChatInviteImporter lastImporter, LongSparseArray<User> users, RequestDelegate onComplete) {
		boolean isEmptyQuery = TextUtils.isEmpty(query);

		var req = new TLRPC.TLMessagesGetChatInviteImporters();
		req.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId);
		req.requested = true;
		req.limit = 30;

		if (!isEmptyQuery) {
			req.q = query;
			req.flags |= 4;
		}
		if (lastImporter == null) {
			req.offsetUser = new TLRPC.TLInputUserEmpty();
		}
		else {
			req.offsetUser = getMessagesController().getInputUser(users.get(lastImporter.userId));
			req.offsetDate = lastImporter.date;
		}

		return getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
			if (response instanceof TLRPC.TLMessagesChatInviteImporters importers) {
				firstImportersCache.put(chatId, importers);
			}

			onComplete.run(response, error);
		}));
	}

	public void onPendingRequestsUpdated(TLRPC.TLUpdatePendingJoinRequests update) {
		long peerId = MessageObject.getPeerId(update.peer);
		firstImportersCache.put(-peerId, null);
		TLRPC.ChatFull chatFull = getMessagesController().getChatFull(-peerId);
		if (chatFull != null) {
			chatFull.requestsPending = update.requestsPending;

			chatFull.recentRequesters.clear();
			chatFull.recentRequesters.addAll(update.recentRequesters);

			chatFull.flags |= 131072;

			getMessagesStorage().updateChatInfo(chatFull, false);

			getNotificationCenter().postNotificationName(NotificationCenter.chatInfoDidLoad, chatFull, 0, false, false);
		}
	}
}
