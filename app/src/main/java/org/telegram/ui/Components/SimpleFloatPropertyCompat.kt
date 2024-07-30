package org.telegram.ui.Components

import androidx.dynamicanimation.animation.FloatPropertyCompat

class SimpleFloatPropertyCompat<T>(name: String, private val getter: Getter<T>, private val setter: Setter<T>) : FloatPropertyCompat<T>(name) {
	var multiplier = 1f
		private set

	fun setMultiplier(multiplier: Float): SimpleFloatPropertyCompat<T> {
		this.multiplier = multiplier
		return this
	}

	override fun getValue(`object`: T): Float {
		return getter[`object`] * multiplier
	}

	override fun setValue(`object`: T, value: Float) {
		setter[`object`] = value / multiplier
	}

	fun interface Getter<T> {
		operator fun get(obj: T): Float
	}

	fun interface Setter<T> {
		operator fun set(obj: T, value: Float)
	}
}
