/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package com.beint.elloapp

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider

fun getOutlineProvider(radius: Float, topCorners: Boolean, bottomCorners: Boolean): ViewOutlineProvider {
	return when {
		topCorners && bottomCorners -> allCornersProvider(radius)
		topCorners -> topCornersProvider(radius)
		bottomCorners -> bottomCornersProvider(radius)
		else -> throw IllegalArgumentException("At least one of topCorners or bottomCorners must be true")
	}
}

fun allCornersProvider(radius: Float): ViewOutlineProvider {
	return object : ViewOutlineProvider() {
		override fun getOutline(view: View, outline: Outline) {
			outline.setRoundRect(0, 0, view.measuredWidth, view.measuredHeight, radius)
		}
	}
}

fun bottomCornersProvider(radius: Float): ViewOutlineProvider {
	return object : ViewOutlineProvider() {
		override fun getOutline(view: View, outline: Outline) {
			val left = 0
			val top = 0
			val right = view.measuredWidth
			val bottom = view.measuredHeight

			outline.setRoundRect(left, top - radius.toInt(), right, bottom, radius)
		}
	}
}

fun topCornersProvider(radius: Float): ViewOutlineProvider {
	return object : ViewOutlineProvider() {
		override fun getOutline(view: View, outline: Outline) {
			val left = 0
			val top = 0
			val right = view.measuredWidth
			val bottom = view.measuredHeight

			outline.setRoundRect(left, top, right, bottom + radius.toInt(), radius)
		}
	}
}
