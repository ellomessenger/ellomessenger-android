/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2024.
 */
package org.telegram.ui.Components.sharedmedia

import android.util.SparseArray
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.messageobject.MessageObject
import kotlin.math.max
import kotlin.math.min

class SharedMediaData {
	val messages = mutableListOf<MessageObject>()
		get() = if (isFrozen) frozenMessages else field

	var messagesDict = arrayOf(SparseArray<MessageObject>(), SparseArray<MessageObject>())
	val sections = mutableListOf<String>()
	var sectionArrays = mutableMapOf<String, MutableList<MessageObject>>()
	val fastScrollPeriods = mutableListOf<Period>()
	var totalCount = 0
	var loading = false
	var fastScrollDataLoaded = false
	var endReached = booleanArrayOf(false, true)
	var maxId = intArrayOf(0, 0)
	var minId = 0
	var startReached = true
	var loadingAfterFastScroll = false
	var requestIndex = 0
	var filterType = SharedMediaLayout.FILTER_PHOTOS_AND_VIDEOS
	var isFrozen = false
	val frozenMessages = mutableListOf<MessageObject>()
	private var frozenStartOffset = 0
	private var frozenEndLoadingStubs = 0
	val recycledViewPool by lazy { RecyclerView.RecycledViewPool() }

	var startOffset = 0
		get() = if (isFrozen) frozenStartOffset else field

	var endLoadingStubs = 0
		get() = if (isFrozen) frozenEndLoadingStubs else field

	var hasVideos = false
	var hasPhotos = false

	fun setMaxId(num: Int, value: Int) {
		maxId[num] = value
	}

	fun setEndReached(num: Int, value: Boolean) {
		endReached[num] = value
	}

	fun addMessage(messageObject: MessageObject, loadIndex: Int, isNew: Boolean, enc: Boolean): Boolean {
		if (messagesDict[loadIndex].indexOfKey(messageObject.id) >= 0) {
			return false
		}

		var messageObjects = sectionArrays[messageObject.monthKey]

		if (messageObjects == null) {
			messageObjects = mutableListOf()

			sectionArrays[messageObject.monthKey!!] = messageObjects

			if (isNew) {
				sections.add(0, messageObject.monthKey!!)
			}
			else {
				sections.add(messageObject.monthKey!!)
			}
		}

		if (isNew) {
			messageObjects.add(0, messageObject)
			messages.add(0, messageObject)
		}
		else {
			messageObjects.add(messageObject)
			messages.add(messageObject)
		}

		messagesDict[loadIndex].put(messageObject.id, messageObject)

		if (!enc) {
			if (messageObject.id > 0) {
				maxId[loadIndex] = min(messageObject.id, maxId[loadIndex])
				minId = max(messageObject.id, minId)
			}
		}
		else {
			maxId[loadIndex] = max(messageObject.id, maxId[loadIndex])
			minId = min(messageObject.id, minId)
		}

		if (!hasVideos && messageObject.isVideo) {
			hasVideos = true
		}

		if (!hasPhotos && messageObject.isPhoto) {
			hasPhotos = true
		}

		return true
	}

	fun deleteMessage(mid: Int, loadIndex: Int): MessageObject? {
		val messageObject = messagesDict[loadIndex][mid] ?: return null
		val messageObjects = sectionArrays[messageObject.monthKey] ?: return null

		messageObjects.remove(messageObject)
		messages.remove(messageObject)
		messagesDict[loadIndex].remove(messageObject.id)

		if (messageObjects.isEmpty()) {
			sectionArrays.remove(messageObject.monthKey)
			sections.remove(messageObject.monthKey)
		}

		totalCount--

		return messageObject
	}

	fun replaceMid(oldMid: Int, newMid: Int) {
		val obj = messagesDict[0][oldMid]

		if (obj != null) {
			messagesDict[0].remove(oldMid)
			messagesDict[0].put(newMid, obj)
			obj.messageOwner?.id = newMid
			maxId[0] = min(newMid, maxId[0])
		}
	}

	fun setListFrozen(frozen: Boolean) {
		if (isFrozen == frozen) {
			return
		}

		isFrozen = frozen

		if (frozen) {
			frozenStartOffset = startOffset
			frozenEndLoadingStubs = endLoadingStubs
			frozenMessages.clear()
			frozenMessages.addAll(messages)
		}
	}
}