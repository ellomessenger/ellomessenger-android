/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.messenger.messageobject

import org.telegram.PhoneFormat.PhoneFormat
import org.telegram.messenger.AndroidUtilities
import java.io.BufferedReader
import java.io.StringReader
import java.nio.charset.Charset

class VCardData {
	private val emails = mutableListOf<String>()
	private val phones = mutableListOf<String>()
	private var company: String? = null

	companion object {
		@JvmStatic
		fun parse(data: String?): CharSequence? {
			if (data == null) {
				return null
			}

			runCatching {
				var currentData: VCardData? = null
				var finished = false

				BufferedReader(StringReader(data)).use { bufferedReader ->
					var line: String
					var originalLine: String
					var pendingLine: String? = null

					while (bufferedReader.readLine().also { line = it }.also { originalLine = it } != null) {
						if (originalLine.startsWith("PHOTO")) {
							continue
						}
						else {
							if (originalLine.indexOf(':') >= 0) {
								if (originalLine.startsWith("BEGIN:VCARD")) {
									currentData = VCardData()
								}
								else if (originalLine.startsWith("END:VCARD")) {
									if (currentData != null) {
										finished = true
									}
								}
							}
						}

						if (pendingLine != null) {
							pendingLine += line
							line = pendingLine
							pendingLine = null
						}

						if (line.contains("=QUOTED-PRINTABLE") && line.endsWith("=")) {
							pendingLine = line.substring(0, line.length - 1)
							continue
						}

						val idx = line.indexOf(":")

						val args = if (idx >= 0) {
							arrayOf(line.substring(0, idx), line.substring(idx + 1).trim { it <= ' ' })
						}
						else {
							arrayOf(line.trim { it <= ' ' })
						}

						if (args.size < 2 || currentData == null) {
							continue
						}

						if (args[0].startsWith("ORG")) {
							var nameEncoding: String? = null
							var nameCharset: String? = null
							val params = args[0].split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

							for (param in params) {
								val args2 = param.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

								if (args2.size != 2) {
									continue
								}

								if (args2[0] == "CHARSET") {
									nameCharset = args2[1]
								}
								else if (args2[0] == "ENCODING") {
									nameEncoding = args2[1]
								}
							}

							currentData?.company = args[1]

							if (nameEncoding != null && nameEncoding.equals("QUOTED-PRINTABLE", ignoreCase = true)) {
								val bytes = AndroidUtilities.decodeQuotedPrintable(AndroidUtilities.getStringBytes(currentData?.company))

								if (bytes != null && bytes.isNotEmpty()) {
									currentData?.company = String(bytes, Charset.forName(nameCharset))
								}
							}

							currentData?.company = currentData?.company?.replace(';', ' ')
						}
						else if (args[0].startsWith("TEL")) {
							if (args[1].isNotEmpty()) {
								currentData?.phones?.add(args[1])
							}
						}
						else if (args[0].startsWith("EMAIL")) {
							val email = args[1]

							if (email.isNotEmpty()) {
								currentData?.emails?.add(email)
							}
						}
					}
				}

				if (finished) {
					return buildString {
						currentData?.phones?.forEach { phone ->
							if (isNotEmpty()) {
								append('\n')
							}

							if (phone.contains("#") || phone.contains("*")) {
								append(phone)
							}
							else {
								append(PhoneFormat.getInstance().format(phone))
							}
						}

						currentData?.emails?.forEach {
							if (isNotEmpty()) {
								append('\n')
							}

							append(PhoneFormat.getInstance().format(it))
						}

						val company = currentData?.company

						if (!company.isNullOrEmpty()) {
							if (isNotEmpty()) {
								append('\n')
							}

							append(company)
						}
					}
				}
			}

			return null
		}
	}
}
