/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package com.beint.elloapp

object FileHelper {
	private val ARCHIVE_EXTENSIONS = setOf("7z", "a", "ace", "alz", "arc", "arj", "b1", "ba", "bh", "bz2", "cab", "car", "cfs", /*"cpt",*/ "dar", "dd", "dgc", "dmg", "ear", "ecc", "egt", /*"epub",*/ "esd", "exe", "f", "gz", "gzip", "hfs", "ice", "img", "iso", "jar", "kgb", "lbr", "lha", "lqr", "lz", "lz4", "lz4.c", "lz4f", "lzh", "lzma", "lzo", "lzx", "mar", "msi", "nbh", "nsis", "osz", "pak", "par", "par2", "partimg", "pea", "pim", "pit", "qda", "rar", "rk", "rz", "s7z", "sfx", "shar", "sit", "sitx", "sqx", "tar", "tbz2", "tgz", "tlz", "txz", "uc2", "udf", "ue2", "uha", "war", "wim", "xar", "xdelta", "xeb", "xpi", "xz", "z", "zip", "zipx", "zoo")
	private val ARCHIVE_MIME_TYPES = setOf("application/7z", "application/x-7z-compressed", "application/x-ace-compressed", "application/x-alz-compressed", "application/arc", "application/x-arj", "application/x-b1", "application/vnd.ms-cab-compressed", "application/x-cfs-compressed", /*"application/x-cpt",*/ "application/x-dar", "application/x-dgc-compressed", "application/x-apple-diskimage", /*"application/epub+zip",*/ "application/x-gca-compressed", "application/x-ace", "application/x-gzip", "application/x-hfs", "application/x-iso9660-image", "application/java-archive", "application/x-lha", "application/x-lzip", "application/x-lzma", "application/x-lzop", "application/x-msdownload", "application/x-compress", "application/x-ole-storage", "application/x-rar-compressed", "application/x-renesas-rx", "application/x-samsung-bootloader", "application/x-sea", "application/x-shar", "application/x-stuffit", "application/x-stuffitx", "application/x-tar", "application/x-bzip-compressed-tar", "application/x-tarz", "application/x-xz-compressed-tar", "application/x-msmetafile", "application/x-ustar", "application/vnd.android.package-archive", "application/x-webarchive", "application/x-arc", "application/x-dzip", "application/x-lzh", "application/x-lzx", "application/x-gtar", "application/x-compressed", "application/x-sit", "application/x-sitx", "application/x-deb", "application/x-gz-compressed-tar", "application/x-bzip2-compressed-tar", "application/x-xz-compressed", "application/x-snappy-framed", "application/x-rpm", "application/x-msi", "application/x-nbx", "application/x-nexe", "application/x-nspkg", "application/x-nsis", "application/x-object", "application/x-apache-struts-config", "application/x-zip", "application/x-zip-compressed", "application/x-zoo")
	val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "mp4")
	val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "opus", "ogg", "flac")

	fun isArchive(filePath: String): Boolean {
		val extension = getFileExtension(filePath)
		val mimeType = getMimeType(filePath)

		if (extension.lowercase() in ARCHIVE_EXTENSIONS) {
			return true
		}

		return mimeType?.lowercase() in ARCHIVE_MIME_TYPES
	}

	private fun getFileExtension(filePath: String): String {
		return filePath.substringAfterLast(".", missingDelimiterValue = "")
	}

	fun getMimeType(filePath: String): String? {
		val extension = MimeTypeMap.getFileExtensionFromUrl(filePath)
		return MimeTypeMap.singleton.getMimeTypeFromExtension(extension)
	}

	fun isImageFile(filePath: String?): Boolean {
		if (filePath == null) {
			return false
		}

		val extension = getFileExtension(filePath)
		return extension.lowercase() in IMAGE_EXTENSIONS
	}

	fun isAudioFile(filePath: String?): Boolean {
		if (filePath == null) {
			return false
		}

		val extension = getFileExtension(filePath)
		return extension.lowercase() in AUDIO_EXTENSIONS
	}
}
