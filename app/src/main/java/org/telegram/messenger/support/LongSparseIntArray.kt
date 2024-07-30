/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger.support

/**
 * SparseLongArrays map integers to longs.  Unlike a normal array of longs,
 * there can be gaps in the indices.  It is intended to be more memory efficient
 * than using a HashMap to map Integers to Longs, both because it avoids
 * auto-boxing keys and values and its data structure doesn't rely on an extra entry object
 * for each mapping.
 *
 *
 * Note that this container keeps its mappings in an array data structure,
 * using a binary search to find keys.  The implementation is not intended to be appropriate for
 * data structures
 * that may contain large numbers of items.  It is generally slower than a traditional
 * HashMap, since lookups require a binary search and adds and removes require inserting
 * and deleting entries in the array.  For containers holding up to hundreds of items,
 * the performance difference is not significant, less than 50%.
 *
 *
 * It is possible to iterate over the items in this container using
 * [.keyAt] and [.valueAt]. Iterating over the keys using
 * `keyAt(int)` with ascending values of the index will return the
 * keys in ascending order, or the values corresponding to the keys in ascending
 * order in the case of `valueAt(int)`.
 */
class LongSparseIntArray @JvmOverloads constructor(initialCapacity: Int = 10) : Cloneable {
	private var mKeys: LongArray
	private var mValues: IntArray
	private var mSize: Int
	/**
	 * Creates a new SparseLongArray containing no mappings that will not
	 * require any additional memory allocation to store the specified
	 * number of mappings.
	 */
	/**
	 * Creates a new SparseLongArray containing no mappings.
	 */
	init {
		@Suppress("NAME_SHADOWING") var initialCapacity = initialCapacity
		initialCapacity = ArrayUtils.idealLongArraySize(initialCapacity)
		mKeys = LongArray(initialCapacity)
		mValues = IntArray(initialCapacity)
		mSize = 0
	}

	public override fun clone(): LongSparseIntArray {
		val clone: LongSparseIntArray?

		try {
			clone = super.clone() as LongSparseIntArray
			clone.mKeys = mKeys.clone()
			clone.mValues = mValues.clone()
		}
		catch (e: CloneNotSupportedException) {
			throw e
		}

		return clone
	}

	/**
	 * Gets the long mapped from the specified key, or the specified value
	 * if no such mapping has been made.
	 */
	/**
	 * Gets the long mapped from the specified key, or `0`
	 * if no such mapping has been made.
	 */
	@JvmOverloads
	operator fun get(key: Long, valueIfKeyNotFound: Int = 0): Int {
		val i = binarySearch(mKeys, 0, mSize, key)

		return if (i < 0) {
			valueIfKeyNotFound
		}
		else {
			mValues[i]
		}
	}

	/**
	 * Removes the mapping from the specified key, if there was any.
	 */
	fun delete(key: Long) {
		val i = binarySearch(mKeys, 0, mSize, key)

		if (i >= 0) {
			removeAt(i)
		}
	}

	/**
	 * Removes the mapping at the given index.
	 */
	fun removeAt(index: Int) {
		System.arraycopy(mKeys, index + 1, mKeys, index, mSize - (index + 1))
		System.arraycopy(mValues, index + 1, mValues, index, mSize - (index + 1))
		mSize--
	}

	/**
	 * Adds a mapping from the specified key to the specified value,
	 * replacing the previous mapping from the specified key if there
	 * was one.
	 */
	fun put(key: Long, value: Int) {
		var i = binarySearch(mKeys, 0, mSize, key)

		if (i >= 0) {
			mValues[i] = value
		}
		else {
			i = i.inv()

			if (mSize >= mKeys.size) {
				growKeyAndValueArrays(mSize + 1)
			}

			if (mSize - i != 0) {
				System.arraycopy(mKeys, i, mKeys, i + 1, mSize - i)
				System.arraycopy(mValues, i, mValues, i + 1, mSize - i)
			}

			mKeys[i] = key
			mValues[i] = value

			mSize++
		}
	}

	/**
	 * Returns the number of key-value mappings that this SparseIntArray
	 * currently stores.
	 */
	fun size(): Int {
		return mSize
	}

	/**
	 * Given an index in the range `0...size()-1`, returns
	 * the key from the `index`th key-value mapping that this
	 * SparseLongArray stores.
	 */
	fun keyAt(index: Int): Long {
		return mKeys[index]
	}

	/**
	 * Given an index in the range `0...size()-1`, returns
	 * the value from the `index`th key-value mapping that this
	 * SparseLongArray stores.
	 */
	fun valueAt(index: Int): Int {
		return mValues[index]
	}

	/**
	 * Returns the index for which [.keyAt] would return the
	 * specified key, or a negative number if the specified
	 * key is not mapped.
	 */
	fun indexOfKey(key: Long): Int {
		return binarySearch(mKeys, 0, mSize, key)
	}

	/**
	 * Returns an index for which [.valueAt] would return the
	 * specified key, or a negative number if no keys map to the
	 * specified value.
	 * Beware that this is a linear search, unlike lookups by key,
	 * and that multiple keys can map to the same value and this will
	 * find only one of them.
	 */
	fun indexOfValue(value: Long): Int {
		for (i in 0 until mSize) {
			if (mValues[i].toLong() == value) {
				return i
			}
		}

		return -1
	}

	/**
	 * Removes all key-value mappings from this SparseIntArray.
	 */
	fun clear() {
		mSize = 0
	}

	/**
	 * Puts a key/value pair into the array, optimizing for the case where
	 * the key is greater than all existing keys in the array.
	 */
	fun append(key: Long, value: Int) {
		if (mSize != 0 && key <= mKeys[mSize - 1]) {
			put(key, value)
			return
		}

		val pos = mSize

		if (pos >= mKeys.size) {
			growKeyAndValueArrays(pos + 1)
		}

		mKeys[pos] = key
		mValues[pos] = value
		mSize = pos + 1
	}

	private fun growKeyAndValueArrays(minNeededSize: Int) {
		val n = ArrayUtils.idealLongArraySize(minNeededSize)
		val nkeys = LongArray(n)
		val nvalues = IntArray(n)

		System.arraycopy(mKeys, 0, nkeys, 0, mKeys.size)
		System.arraycopy(mValues, 0, nvalues, 0, mValues.size)

		mKeys = nkeys
		mValues = nvalues
	}

	val isEmpty: Boolean
		get() = mSize == 0

	val isNotEmpty: Boolean
		get() = mSize > 0

	companion object {
		private fun binarySearch(a: LongArray, start: Int, len: Int, key: Long): Int {
			var high = start + len
			var low = start - 1
			var guess: Int

			while (high - low > 1) {
				guess = (high + low) / 2
				if (a[guess] < key) {
					low = guess
				}
				else {
					high = guess
				}
			}

			return if (high == start + len) {
				(start + len).inv()
			}
			else if (a[high] == key) {
				high
			}
			else {
				high.inv()
			}
		}
	}
}
