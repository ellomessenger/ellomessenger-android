/*
 * This is the source code of ElloApp
 *  for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.feed

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.TLRPC

class PhotoVideoAttachmentsAdapter(private val channel: TLRPC.TL_channel?, private val messages: List<MessageObject>, private val contentWidth: Int, private val onClickListener: View.OnClickListener) : RecyclerView.Adapter<PhotoVideoAttachmentViewHolder>() {
	private var scaleType: ImageView.ScaleType? = null
	var delegate: FeedViewHolder.Delegate? = null

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoVideoAttachmentViewHolder {
		val view = ConstraintLayout(parent.context)
		view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT)

		return PhotoVideoAttachmentViewHolder(view, contentWidth, onClickListener)
	}

	override fun getItemCount(): Int {
		return messages.size
	}

	@SuppressLint("NotifyDataSetChanged")
	fun setScaleType(scaleType: ImageView.ScaleType?) {
		this.scaleType = scaleType
		notifyDataSetChanged()
	}

	override fun onBindViewHolder(holder: PhotoVideoAttachmentViewHolder, position: Int) {
		val message = messages[position]

		holder.itemView.tag = position
		holder.messageObject = message
		holder.setScaleType(scaleType)

		holder.imageView.setOnClickListener {
			delegate?.onOpenImages(messages, channel?.id ?: 0L, message, holder.imageView)
		}
	}
}
