/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.ui.Adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.messageobject.MessageObject;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class MessagesSearchAdapter extends RecyclerListView.SelectionAdapter {
	private final Context mContext;
	private List<MessageObject> searchResultMessages = new ArrayList<>();
	private final int currentAccount = UserConfig.selectedAccount;

	public MessagesSearchAdapter(Context context) {
		mContext = context;
	}

	@SuppressLint("NotifyDataSetChanged")
	@Override
	public void notifyDataSetChanged() {
		searchResultMessages = MediaDataController.getInstance(currentAccount).getFoundMessageObjects();
		super.notifyDataSetChanged();
	}

	@Override
	public int getItemCount() {
		return searchResultMessages.size();
	}

	public Object getItem(int i) {
		if (i < 0 || i >= searchResultMessages.size()) {
			return null;
		}
		return searchResultMessages.get(i);
	}

	@Override
	public long getItemId(int i) {
		return i;
	}

	@Override
	public boolean isEnabled(RecyclerView.ViewHolder holder) {
		return holder.getItemViewType() == 0;
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = switch (viewType) {
			case 0 -> new DialogCell(null, mContext, false, true, currentAccount);
			case 1 -> new LoadingCell(mContext);
			default -> null;
		};
		view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
		return new RecyclerListView.Holder(view);
	}

	@Override
	public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
		if (holder.getItemViewType() == 0) {
			DialogCell cell = (DialogCell)holder.itemView;
			cell.useSeparator = true;
			MessageObject messageObject = (MessageObject)getItem(position);
			cell.setDialog(messageObject.getDialogId(), messageObject, messageObject.messageOwner.date, true);
		}
	}

	@Override
	public int getItemViewType(int i) {
		if (i < searchResultMessages.size()) {
			return 0;
		}
		return 1;
	}
}
