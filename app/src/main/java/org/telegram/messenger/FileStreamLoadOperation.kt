/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.BaseDataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.TL_document
import org.telegram.tgnet.TLRPC.TL_documentAttributeAudio
import org.telegram.tgnet.TLRPC.TL_documentAttributeFilename
import org.telegram.tgnet.TLRPC.TL_documentAttributeVideo
import java.io.EOFException
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch

class FileStreamLoadOperation() : BaseDataSource(false), FileLoadOperationStream {
	private var loadOperation: FileLoadOperation? = null
	private var uri: Uri? = null
	private var bytesRemaining: Long = 0
	private var opened = false
	private var currentOffset: Long = 0
	private var countDownLatch: CountDownLatch? = null
	private var file: RandomAccessFile? = null
	private var document: TLRPC.Document? = null
	private var parentObject: Any? = null
	private var currentAccount = 0

	@Deprecated("")
	constructor(listener: TransferListener?) : this() {
		listener?.let { addTransferListener(it) }
	}

	@Throws(IOException::class)
	override fun open(dataSpec: DataSpec): Long {
		uri = dataSpec.uri
		currentAccount = Utilities.parseInt(uri?.getQueryParameter("account"))
		parentObject = FileLoader.getInstance(currentAccount).getParentObject(Utilities.parseInt(uri?.getQueryParameter("rid")))

		document = TL_document()
		document?.access_hash = Utilities.parseLong(uri?.getQueryParameter("hash"))
		document?.id = Utilities.parseLong(uri?.getQueryParameter("id"))
		document?.size = Utilities.parseLong(uri?.getQueryParameter("size"))
		document?.dc_id = Utilities.parseInt(uri?.getQueryParameter("dc"))
		document?.mime_type = uri?.getQueryParameter("mime")
		document?.file_reference = Utilities.hexToBytes(uri?.getQueryParameter("reference"))

		val filename = TL_documentAttributeFilename()
		filename.file_name = uri?.getQueryParameter("name")

		document?.attributes?.add(filename)

		if (document?.mime_type?.startsWith("video") == true) {
			document?.attributes?.add(TL_documentAttributeVideo())
		}
		else if (document?.mime_type?.startsWith("audio") == true) {
			document?.attributes?.add(TL_documentAttributeAudio())
		}

		loadOperation = FileLoader.getInstance(currentAccount).loadStreamFile(this, document, null, parentObject, dataSpec.position.also { currentOffset = it }, false)
		bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) (document?.size ?: 0) - dataSpec.position else dataSpec.length

		if (bytesRemaining < 0) {
			throw EOFException()
		}

		opened = true

		transferStarted(dataSpec)

		if (loadOperation != null) {
			file = RandomAccessFile(loadOperation?.currentFile, "r")
			file?.seek(currentOffset)
		}

		return bytesRemaining
	}

	@Throws(IOException::class)
	override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
		@Suppress("NAME_SHADOWING") var readLength = readLength

		return if (readLength == 0) {
			0
		}
		else if (bytesRemaining == 0L) {
			C.RESULT_END_OF_INPUT
		}
		else {
			var availableLength = 0

			try {
				if (bytesRemaining < readLength) {
					readLength = bytesRemaining.toInt()
				}
				while (availableLength == 0 && opened) {
					availableLength = loadOperation?.getDownloadedLengthFromOffset(currentOffset, readLength.toLong())?.firstOrNull()?.toInt() ?: 0

					if (availableLength == 0) {
						FileLoader.getInstance(currentAccount).loadStreamFile(this, document, null, parentObject, currentOffset, false)

						countDownLatch = CountDownLatch(1)
						countDownLatch?.await()
					}
				}

				if (!opened) {
					return 0
				}

				file?.readFully(buffer, offset, availableLength)

				currentOffset += availableLength.toLong()
				bytesRemaining -= availableLength.toLong()

				bytesTransferred(availableLength)
			}
			catch (e: Exception) {
				throw IOException(e)
			}

			availableLength
		}
	}

	override fun getUri(): Uri? {
		return uri
	}

	override fun close() {
		loadOperation?.removeStreamListener(this)

		try {
			file?.close()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		file = null

		uri = null

		if (opened) {
			opened = false
			transferEnded()
		}

		countDownLatch?.countDown()
	}

	override fun newDataAvailable() {
		countDownLatch?.countDown()
	}
}
