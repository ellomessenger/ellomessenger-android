/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui

import java.util.Objects

class Country {
	@JvmField
	var name: String? = null

	@JvmField
	var code: String? = null

	@JvmField
	var shortname: String? = null

	override fun equals(other: Any?): Boolean {
		if (this === other) {
			return true
		}
		if (other == null || javaClass != other.javaClass) {
			return false
		}

		val that = other as Country

		return name == that.name && code == that.code
	}

	override fun hashCode(): Int {
		return Objects.hash(name, code)
	}

	override fun toString(): String {
		return name ?: ""
	}
}
