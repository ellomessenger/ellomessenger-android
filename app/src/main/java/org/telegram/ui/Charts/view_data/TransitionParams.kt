/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts.view_data

class TransitionParams {
	var pickerStartOut = 0f
	var pickerEndOut = 0f
	var xPercentage = 0f
	var date: Long = 0
	var pX = 0f
	var pY = 0f
	var needScaleY = true
	var progress = 0f
	var startX: FloatArray? = null
	var startY: FloatArray? = null
	var endX: FloatArray? = null
	var endY: FloatArray? = null
	var angle: FloatArray? = null
}
