/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.Components.Paint;

public class Swatch {
	public int color;
	public float colorLocation;
	public float brushWeight;

	public Swatch(int color, float colorLocation, float brushWeight) {
		this.color = color;
		this.colorLocation = colorLocation;
		this.brushWeight = brushWeight;
	}
}
