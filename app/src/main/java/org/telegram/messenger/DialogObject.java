/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

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

		if (dialog instanceof TLRPC.TL_dialog) {
			if (dialog.peer == null) {
				return;
			}

			if (dialog.peer.user_id != 0) {
				dialog.id = dialog.peer.user_id;
			}
			else if (dialog.peer.chat_id != 0) {
				dialog.id = -dialog.peer.chat_id;
			}
			else {
				dialog.id = -dialog.peer.channel_id;
			}
		}
		else if (dialog instanceof TLRPC.TL_dialogFolder) {
			TLRPC.TL_dialogFolder dialogFolder = (TLRPC.TL_dialogFolder)dialog;
			dialog.id = makeFolderDialogId(dialogFolder.folder.id);
		}
	}

	public static long getPeerDialogId(@Nullable TLRPC.Peer peer) {
		if (peer == null) {
			return 0;
		}
		if (peer.user_id != 0) {
			return peer.user_id;
		}
		else if (peer.chat_id != 0) {
			return -peer.chat_id;
		}
		else {
			return -peer.channel_id;
		}
	}

	public static long getPeerDialogId(@Nullable TLRPC.InputPeer peer) {
		if (peer == null) {
			return 0;
		}

		if (peer.user_id != 0) {
			return peer.user_id;
		}
		else if (peer.chat_id != 0) {
			return -peer.chat_id;
		}
		else {
			return -peer.channel_id;
		}
	}

	public static long getLastMessageOrDraftDate(TLRPC.Dialog dialog, @Nullable TLRPC.DraftMessage draftMessage) {
		return draftMessage != null && draftMessage.date >= dialog.last_message_date ? draftMessage.date : dialog.last_message_date;
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
