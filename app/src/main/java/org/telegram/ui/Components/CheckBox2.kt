/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.CheckBox
import androidx.annotation.ColorInt
import org.telegram.messenger.GenericProvider
import org.telegram.ui.Components.CheckBoxBase.ProgressDelegate

class CheckBox2(context: Context, sz: Int) : View(context) {
	private val checkBoxBase = CheckBoxBase(this, sz)

	fun setCirclePaintProvider(circlePaintProvider: GenericProvider<Void?, Paint?>?) {
		checkBoxBase.circlePaintProvider = circlePaintProvider!!
	}

	fun setProgressDelegate(delegate: ProgressDelegate?) {
		checkBoxBase.setProgressDelegate(delegate)
	}

	fun setChecked(num: Int, checked: Boolean, animated: Boolean) {
		checkBoxBase.setChecked(num, checked, animated)
	}

	fun setChecked(checked: Boolean, animated: Boolean) {
		checkBoxBase.setChecked(checked, animated)
	}

	fun setNum(num: Int) {
		checkBoxBase.setNum(num)
	}

	val isChecked: Boolean
		get() = checkBoxBase.isChecked

	fun setColor(@ColorInt background: Int, @ColorInt background2: Int, @ColorInt check: Int) {
		checkBoxBase.setColor(background, background2, check)
	}

	override fun setEnabled(enabled: Boolean) {
		checkBoxBase.setEnabled(enabled)
		super.setEnabled(enabled)
	}

	fun setDrawUnchecked(value: Boolean) {
		checkBoxBase.setDrawUnchecked(value)
	}

	fun setDrawBackgroundAsArc(type: Int) {
		checkBoxBase.setBackgroundType(type)
	}

	val progress: Float
		get() = checkBoxBase.getProgress()

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		checkBoxBase.onAttachedToWindow()
	}

	fun setDuration(duration: Long) {
		checkBoxBase.animationDuration = duration
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		checkBoxBase.onDetachedFromWindow()
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		checkBoxBase.setBounds(0, 0, right - left, bottom - top)
	}

	override fun onDraw(canvas: Canvas) {
		checkBoxBase.draw(canvas)
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.className = CheckBox::class.java.name
		info.isChecked = isChecked
		info.isCheckable = true
	}
}
