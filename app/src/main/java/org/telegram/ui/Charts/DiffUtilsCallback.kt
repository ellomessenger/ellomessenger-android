/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Charts

import android.util.SparseIntArray
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.telegram.ui.statistics.StatisticActivity

class DiffUtilsCallback(private val adapter: StatisticActivity.Adapter, private val layoutManager: LinearLayoutManager) : DiffUtil.Callback() {
    var count = 0
    private var positionToTypeMap = SparseIntArray()
    private var growCell = -1
    private var followersCell = -1
    private var interactionsCell = -1
    private var ivInteractionsCell = -1
    private var viewsBySourceCell = -1
    private var newFollowersBySourceCell = -1
    private var languagesCell = -1
    private var topHoursCell = -1
    private var notificationsCell = -1
    private var groupMembersCell = -1
    private var newMembersBySourceCell = -1
    private var membersLanguageCell = -1
    private var messagesCell = -1
    private var actionsCell = -1
    private var topDayOfWeeksCell = -1
    private var startPosts = -1
    private var endPosts = -1

    private fun saveOldState() {
        positionToTypeMap.clear()

        count = adapter.itemCount

        for (i in 0 until count) {
            positionToTypeMap.put(i, adapter.getItemViewType(i))
        }

        growCell = adapter.growCell
        followersCell = adapter.followersCell
        interactionsCell = adapter.interactionsCell
        ivInteractionsCell = adapter.ivInteractionsCell
        viewsBySourceCell = adapter.viewsBySourceCell
        newFollowersBySourceCell = adapter.newFollowersBySourceCell
        languagesCell = adapter.languagesCell
        topHoursCell = adapter.topHoursCell
        notificationsCell = adapter.notificationsCell
        startPosts = adapter.recentPostsStartRow
        endPosts = adapter.recentPostsEndRow
        groupMembersCell = adapter.groupMembersCell
        newMembersBySourceCell = adapter.newMembersBySourceCell
        membersLanguageCell = adapter.membersLanguageCell
        messagesCell = adapter.messagesCell
        actionsCell = adapter.actionsCell
        topDayOfWeeksCell = adapter.topDayOfWeeksCell
    }

    override fun getOldListSize(): Int {
        return count
    }

    override fun getNewListSize(): Int {
        return adapter.count
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        if (positionToTypeMap[oldItemPosition] == 13 && adapter.getItemViewType(newItemPosition) == 13) {
            return true
        }

        if (positionToTypeMap[oldItemPosition] == 10 && adapter.getItemViewType(newItemPosition) == 10) {
            return true
        }

        if (oldItemPosition in startPosts..endPosts) {
            return oldItemPosition - startPosts == newItemPosition - adapter.recentPostsStartRow
        }

        return if (oldItemPosition == growCell && newItemPosition == adapter.growCell) {
            true
        }
        else if (oldItemPosition == followersCell && newItemPosition == adapter.followersCell) {
            true
        }
        else if (oldItemPosition == interactionsCell && newItemPosition == adapter.interactionsCell) {
            true
        }
        else if (oldItemPosition == ivInteractionsCell && newItemPosition == adapter.ivInteractionsCell) {
            true
        }
        else if (oldItemPosition == viewsBySourceCell && newItemPosition == adapter.viewsBySourceCell) {
            true
        }
        else if (oldItemPosition == newFollowersBySourceCell && newItemPosition == adapter.newFollowersBySourceCell) {
            true
        }
        else if (oldItemPosition == languagesCell && newItemPosition == adapter.languagesCell) {
            true
        }
        else if (oldItemPosition == topHoursCell && newItemPosition == adapter.topHoursCell) {
            true
        }
        else if (oldItemPosition == notificationsCell && newItemPosition == adapter.notificationsCell) {
            true
        }
        else if (oldItemPosition == groupMembersCell && newItemPosition == adapter.groupMembersCell) {
            true
        }
        else if (oldItemPosition == newMembersBySourceCell && newItemPosition == adapter.newMembersBySourceCell) {
            true
        }
        else if (oldItemPosition == membersLanguageCell && newItemPosition == adapter.membersLanguageCell) {
            true
        }
        else if (oldItemPosition == messagesCell && newItemPosition == adapter.messagesCell) {
            true
        }
        else if (oldItemPosition == actionsCell && newItemPosition == adapter.actionsCell) {
            true
        }
        else {
            oldItemPosition == topDayOfWeeksCell && newItemPosition == adapter.topDayOfWeeksCell
        }
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldType = positionToTypeMap[oldItemPosition]
        return oldType == adapter.getItemViewType(newItemPosition)
    }

    fun update() {
        saveOldState()

        adapter.update()

        val start = layoutManager.findFirstVisibleItemPosition()
        val end = layoutManager.findLastVisibleItemPosition()
        var scrollToItemId = RecyclerView.NO_ID
        var offset = 0

        for (i in start..end) {
            if (adapter.getItemId(i) != RecyclerView.NO_ID) {
                val v = layoutManager.findViewByPosition(i)

                if (v != null) {
                    scrollToItemId = adapter.getItemId(i)
                    offset = v.top
                    break
                }
            }
        }

        DiffUtil.calculateDiff(this).dispatchUpdatesTo(adapter)

        if (scrollToItemId != RecyclerView.NO_ID) {
            var position = -1

            for (i in 0 until adapter.itemCount) {
                if (adapter.getItemId(i) == scrollToItemId) {
                    position = i
                    break
                }
            }

            if (position > 0) {
                layoutManager.scrollToPositionWithOffset(position, offset)
            }
        }
    }
}