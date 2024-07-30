/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.tgnet.tlrpc

import org.telegram.tgnet.AbstractSerializedData
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TL_inputFolderPeer

class TL_folders_editPeerFolders : TLObject() {
	var folder_peers: ArrayList<TL_inputFolderPeer> = ArrayList()

	override fun deserializeResponse(stream: AbstractSerializedData?, constructor: Int, exception: Boolean): TLObject? {
		return TLRPC.Updates.TLdeserialize(stream, constructor, exception)
	}

	override fun serializeToStream(stream: AbstractSerializedData?) {
		if (stream == null) {
			throw RuntimeException("Input stream is null")
		}

		stream.writeInt32(constructor)
		stream.writeInt32(0x1cb5c415)
		stream.writeInt32(folder_peers.size)

		for (folderPeer in folder_peers) {
			folderPeer.serializeToStream(stream)
		}
	}

	companion object {
		const val constructor = 0x6847d0ab
	}
}
