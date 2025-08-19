/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

public class SecureDocument extends TLObject {
	public SecureDocumentKey secureDocumentKey;
	public TLRPC.TLSecureFile secureFile;
	public String path;
	public TLRPC.TLInputFile inputFile;
	public byte[] fileSecret;
	public byte[] fileHash;
	public int type;

	public SecureDocument(SecureDocumentKey key, TLRPC.TLSecureFile file, String p, byte[] fh, byte[] secret) {
		secureDocumentKey = key;
		secureFile = file;
		path = p;
		fileHash = fh;
		fileSecret = secret;
	}
}
