/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPCExtensions;

import androidx.annotation.Nullable;

public class DialogObject {
	public static boolean isChannel(@Nullable TLRPC.Dialog dialog) {
		return dialog != null && (dialog.flags & 1) != 0;
	}

	public static long makeFolderDialogId(int folderId) {
		return 0x2000000000000000L | (long)folderId;
	}

	public static boolean isFolderDialogId(long dialogId) {
		return (dialogId & 0x2000000000000000L) != 0 && (dialogId & 0x8000000000000000L) == 0;
	}

	public static void initDialog(@Nullable TLRPC.Dialog dialog) {
		if (dialog == null || dialog.id != 0) {
			return;
		}

		if (dialog instanceof TLRPC.TLDialog) {
			if (dialog.peer == null) {
				return;
			}

			if (TLRPCExtensions.getUserId(dialog.peer) != 0) {
				dialog.id = TLRPCExtensions.getUserId(dialog.peer);
			}
			else if (TLRPCExtensions.getChatId(dialog.peer) != 0) {
				dialog.id = -TLRPCExtensions.getChatId(dialog.peer);
			}
			else {
				dialog.id = -TLRPCExtensions.getChannelId(dialog.peer);
			}
		}
		else if (dialog instanceof TLRPC.TLDialogFolder dialogFolder) {
			if (dialogFolder.folder != null) {
				dialog.id = makeFolderDialogId(dialogFolder.folder.id);
			}
		}
	}

	public static long getPeerDialogId(@Nullable TLRPC.Peer peer) {
		if (peer == null) {
			return 0;
		}

		if (TLRPCExtensions.getUserId(peer) != 0) {
			return TLRPCExtensions.getUserId(peer);
		}
		else if (TLRPCExtensions.getChatId(peer) != 0) {
			return -TLRPCExtensions.getChatId(peer);
		}
		else {
			return -TLRPCExtensions.getChannelId(peer);
		}
	}

	public static long getPeerDialogId(@Nullable TLRPC.InputPeer peer) {
		if (peer == null) {
			return 0;
		}

		if (peer.userId != 0) {
			return peer.userId;
		}
		else if (TLRPCExtensions.getChatId(peer) != 0) {
			return -TLRPCExtensions.getChatId(peer);
		}
		else {
			return -peer.channelId;
		}
	}

	public static long getLastMessageOrDraftDate(TLRPC.Dialog dialog, @Nullable TLRPC.DraftMessage draftMessage) {
		return draftMessage != null && draftMessage.date >= dialog.lastMessageDate ? draftMessage.date : dialog.lastMessageDate;
	}

	public static boolean isChatDialog(@Nullable Long dialogId) {
		if (dialogId == null) {
			return false;
		}

		return !isEncryptedDialog(dialogId) && !isFolderDialogId(dialogId) && dialogId < 0;
	}

	public static boolean isUserDialog(@Nullable Long dialogId) {
		if (dialogId == null) {
			return false;
		}

		return !isEncryptedDialog(dialogId) && !isFolderDialogId(dialogId) && dialogId > 0;
	}

	public static boolean isEncryptedDialog(@Nullable Long dialogId) {
		if (dialogId == null) {
			return false;
		}

		return (dialogId & 0x4000000000000000L) != 0 && (dialogId & 0x8000000000000000L) == 0;
	}

	public static long makeEncryptedDialogId(long chatId) {
		return 0x4000000000000000L | (chatId & 0x00000000ffffffffL);
	}

	public static int getEncryptedChatId(long dialogId) {
		return (int)(dialogId & 0x00000000ffffffffL);
	}

	public static int getFolderId(long dialogId) {
		return (int)dialogId;
	}
}
