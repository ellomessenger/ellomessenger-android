/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui.Components

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

class FixedWidthSpan(private val width: Int) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        @Suppress("NAME_SHADOWING") var fm = fm

        if (fm == null) {
            fm = paint.fontMetricsInt
        }

        if (fm != null) {
            val h = fm.descent - fm.ascent

            fm.descent = 1 - h
            fm.bottom = fm.descent
            fm.ascent = -1
            fm.top = fm.ascent
        }

        return width
    }

    override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        // unused
    }
}
