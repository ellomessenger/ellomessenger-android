/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.annotation.SuppressLint
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController

object LayoutHelper {
	const val MATCH_PARENT = -1
	const val WRAP_CONTENT = -2

	private fun getSize(size: Float): Int {
		return (if (size < 0) size else AndroidUtilities.dp(size)).toInt()
	}

	//region Gravity
	private fun getAbsoluteGravity(gravity: Int): Int {
		return Gravity.getAbsoluteGravity(gravity, if (LocaleController.isRTL) ViewCompat.LAYOUT_DIRECTION_RTL else ViewCompat.LAYOUT_DIRECTION_LTR)
	}

	@JvmStatic
	@get:SuppressLint("RtlHardcoded")
	val absoluteGravityStart: Int
		get() = if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT

	@get:SuppressLint("RtlHardcoded")
	val absoluteGravityEnd: Int
		get() = if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT

	@JvmStatic
	fun createScroll(width: Int, height: Int, gravity: Int): FrameLayout.LayoutParams {
		return FrameLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()), gravity)
	}

	@JvmStatic
	fun createScroll(width: Int, height: Int, gravity: Int, leftMargin: Float, topMargin: Float, rightMargin: Float, bottomMargin: Float): FrameLayout.LayoutParams {
		val layoutParams = FrameLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()), gravity)
		layoutParams.leftMargin = AndroidUtilities.dp(leftMargin)
		layoutParams.topMargin = AndroidUtilities.dp(topMargin)
		layoutParams.rightMargin = AndroidUtilities.dp(rightMargin)
		layoutParams.bottomMargin = AndroidUtilities.dp(bottomMargin)
		return layoutParams
	}

	@JvmStatic
	fun createFrame(width: Int, height: Float, gravity: Int, leftMargin: Float, topMargin: Float, rightMargin: Float, bottomMargin: Float): FrameLayout.LayoutParams {
		val layoutParams = FrameLayout.LayoutParams(getSize(width.toFloat()), getSize(height), gravity)
		layoutParams.setMargins(AndroidUtilities.dp(leftMargin), AndroidUtilities.dp(topMargin), AndroidUtilities.dp(rightMargin), AndroidUtilities.dp(bottomMargin))
		return layoutParams
	}

	@JvmStatic
	fun createFrame(width: Int, height: Int, gravity: Int): FrameLayout.LayoutParams {
		return FrameLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()), gravity)
	}

	@JvmStatic
	fun createFrame(width: Int, height: Float): FrameLayout.LayoutParams {
		return FrameLayout.LayoutParams(getSize(width.toFloat()), getSize(height))
	}

	fun createConstraint(width: Int, height: Int): ConstraintLayout.LayoutParams {
		return ConstraintLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()))
	}

	@JvmStatic
	fun createFrame(width: Float, height: Float, gravity: Int): FrameLayout.LayoutParams {
		return FrameLayout.LayoutParams(getSize(width), getSize(height), gravity)
	}

	@JvmStatic
	fun createFrameRelatively(width: Float, height: Float, gravity: Int, startMargin: Float, topMargin: Float, endMargin: Float, bottomMargin: Float): FrameLayout.LayoutParams {
		val layoutParams = FrameLayout.LayoutParams(getSize(width), getSize(height), getAbsoluteGravity(gravity))
		layoutParams.leftMargin = AndroidUtilities.dp(if (LocaleController.isRTL) endMargin else startMargin)
		layoutParams.topMargin = AndroidUtilities.dp(topMargin)
		layoutParams.rightMargin = AndroidUtilities.dp(if (LocaleController.isRTL) startMargin else endMargin)
		layoutParams.bottomMargin = AndroidUtilities.dp(bottomMargin)
		return layoutParams
	}

	@JvmStatic
	fun createFrameRelatively(width: Float, height: Float, gravity: Int): FrameLayout.LayoutParams {
		return FrameLayout.LayoutParams(getSize(width), getSize(height), getAbsoluteGravity(gravity))
	}

	@JvmStatic
	fun createRelative(width: Float, height: Float, leftMargin: Int, topMargin: Int, rightMargin: Int, bottomMargin: Int, alignParent: Int, alignRelative: Int, anchorRelative: Int): RelativeLayout.LayoutParams {
		val layoutParams = RelativeLayout.LayoutParams(getSize(width), getSize(height))
		if (alignParent >= 0) {
			layoutParams.addRule(alignParent)
		}
		if (alignRelative >= 0 && anchorRelative >= 0) {
			layoutParams.addRule(alignRelative, anchorRelative)
		}
		layoutParams.leftMargin = AndroidUtilities.dp(leftMargin.toFloat())
		layoutParams.topMargin = AndroidUtilities.dp(topMargin.toFloat())
		layoutParams.rightMargin = AndroidUtilities.dp(rightMargin.toFloat())
		layoutParams.bottomMargin = AndroidUtilities.dp(bottomMargin.toFloat())
		return layoutParams
	}

	@JvmStatic
	fun createRelative(width: Int, height: Int, leftMargin: Int, topMargin: Int, rightMargin: Int, bottomMargin: Int): RelativeLayout.LayoutParams {
		return createRelative(width.toFloat(), height.toFloat(), leftMargin, topMargin, rightMargin, bottomMargin, -1, -1, -1)
	}

	@JvmStatic
	fun createRelative(width: Int, height: Int, leftMargin: Int, topMargin: Int, rightMargin: Int, bottomMargin: Int, alignParent: Int): RelativeLayout.LayoutParams {
		return createRelative(width.toFloat(), height.toFloat(), leftMargin, topMargin, rightMargin, bottomMargin, alignParent, -1, -1)
	}

	@JvmStatic
	fun createRelative(width: Float, height: Float, leftMargin: Int, topMargin: Int, rightMargin: Int, bottomMargin: Int, alignRelative: Int, anchorRelative: Int): RelativeLayout.LayoutParams {
		return createRelative(width, height, leftMargin, topMargin, rightMargin, bottomMargin, -1, alignRelative, anchorRelative)
	}

	@JvmStatic
	fun createRelative(width: Int, height: Int, alignParent: Int, alignRelative: Int, anchorRelative: Int): RelativeLayout.LayoutParams {
		return createRelative(width.toFloat(), height.toFloat(), 0, 0, 0, 0, alignParent, alignRelative, anchorRelative)
	}

	@JvmStatic
	fun createRelative(width: Int, height: Int): RelativeLayout.LayoutParams {
		return createRelative(width.toFloat(), height.toFloat(), 0, 0, 0, 0, -1, -1, -1)
	}

	@JvmStatic
	fun createRelative(width: Int, height: Int, alignParent: Int): RelativeLayout.LayoutParams {
		return createRelative(width.toFloat(), height.toFloat(), 0, 0, 0, 0, alignParent, -1, -1)
	}

	@JvmStatic
	fun createRelative(width: Int, height: Int, alignRelative: Int, anchorRelative: Int): RelativeLayout.LayoutParams {
		return createRelative(width.toFloat(), height.toFloat(), 0, 0, 0, 0, -1, alignRelative, anchorRelative)
	}

	@JvmStatic
	fun createLinear(width: Int, height: Int, weight: Float, gravity: Int, leftMargin: Int, topMargin: Int, rightMargin: Int, bottomMargin: Int): LinearLayout.LayoutParams {
		val layoutParams = LinearLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()), weight)
		layoutParams.setMargins(AndroidUtilities.dp(leftMargin.toFloat()), AndroidUtilities.dp(topMargin.toFloat()), AndroidUtilities.dp(rightMargin.toFloat()), AndroidUtilities.dp(bottomMargin.toFloat()))
		layoutParams.gravity = gravity
		return layoutParams
	}

	@JvmStatic
	fun createLinear(width: Int, height: Int, weight: Float, leftMargin: Int, topMargin: Int, rightMargin: Int, bottomMargin: Int): LinearLayout.LayoutParams {
		val layoutParams = LinearLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()), weight)
		layoutParams.setMargins(AndroidUtilities.dp(leftMargin.toFloat()), AndroidUtilities.dp(topMargin.toFloat()), AndroidUtilities.dp(rightMargin.toFloat()), AndroidUtilities.dp(bottomMargin.toFloat()))
		return layoutParams
	}

	@JvmStatic
	fun createLinear(width: Int, height: Int, gravity: Int, leftMargin: Int, topMargin: Int, rightMargin: Int, bottomMargin: Int): LinearLayout.LayoutParams {
		val layoutParams = LinearLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()))
		layoutParams.setMargins(AndroidUtilities.dp(leftMargin.toFloat()), AndroidUtilities.dp(topMargin.toFloat()), AndroidUtilities.dp(rightMargin.toFloat()), AndroidUtilities.dp(bottomMargin.toFloat()))
		layoutParams.gravity = gravity
		return layoutParams
	}

	@JvmStatic
	fun createLinear(width: Int, height: Int, leftMargin: Float, topMargin: Float, rightMargin: Float, bottomMargin: Float): LinearLayout.LayoutParams {
		val layoutParams = LinearLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()))
		layoutParams.setMargins(AndroidUtilities.dp(leftMargin), AndroidUtilities.dp(topMargin), AndroidUtilities.dp(rightMargin), AndroidUtilities.dp(bottomMargin))
		return layoutParams
	}

	@JvmStatic
	fun createLinear(width: Int, height: Int, weight: Float, gravity: Int): LinearLayout.LayoutParams {
		val layoutParams = LinearLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()), weight)
		layoutParams.gravity = gravity
		return layoutParams
	}

	@JvmStatic
	fun createLinear(width: Int, height: Int, gravity: Int): LinearLayout.LayoutParams {
		val layoutParams = LinearLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()))
		layoutParams.gravity = gravity
		return layoutParams
	}

	@JvmStatic
	fun createLinear(width: Int, height: Int, weight: Float): LinearLayout.LayoutParams {
		return LinearLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()), weight)
	}

	@JvmStatic
	fun createLinear(width: Int, height: Int): LinearLayout.LayoutParams {
		return LinearLayout.LayoutParams(getSize(width.toFloat()), getSize(height.toFloat()))
	}

	@JvmStatic
	fun createLinearRelatively(width: Float, height: Float, gravity: Int, startMargin: Float, topMargin: Float, endMargin: Float, bottomMargin: Float): LinearLayout.LayoutParams {
		val layoutParams = LinearLayout.LayoutParams(getSize(width), getSize(height), getAbsoluteGravity(gravity).toFloat())
		layoutParams.leftMargin = AndroidUtilities.dp(if (LocaleController.isRTL) endMargin else startMargin)
		layoutParams.topMargin = AndroidUtilities.dp(topMargin)
		layoutParams.rightMargin = AndroidUtilities.dp(if (LocaleController.isRTL) startMargin else endMargin)
		layoutParams.bottomMargin = AndroidUtilities.dp(bottomMargin)
		return layoutParams
	}

	@JvmStatic
	fun createLinearRelatively(width: Float, height: Float, gravity: Int): LinearLayout.LayoutParams {
		return LinearLayout.LayoutParams(getSize(width), getSize(height), getAbsoluteGravity(gravity).toFloat())
	}
}
