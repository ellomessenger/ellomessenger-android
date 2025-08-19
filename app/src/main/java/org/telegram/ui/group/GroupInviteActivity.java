/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.group;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPCExtensions;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextBlockCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class GroupInviteActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
	private ListAdapter listAdapter;
	private final long chatId;
	private boolean loading;
	private TLRPC.ExportedChatInvite invite;
	private int linkRow;
	private int linkInfoRow;
	private int copyLinkRow;
	private int revokeLinkRow;
	private int shareLinkRow;
	private int shadowRow;
	private int rowCount;

	public GroupInviteActivity(long cid) {
		super();
		chatId = cid;
	}

	@Override
	public boolean onFragmentCreate() {
		super.onFragmentCreate();

		NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);

		getMessagesController().loadFullChat(chatId, classGuid, true);

		loading = true;

		rowCount = 0;
		linkRow = rowCount++;
		linkInfoRow = rowCount++;
		copyLinkRow = rowCount++;
		revokeLinkRow = rowCount++;
		shareLinkRow = rowCount++;
		shadowRow = rowCount++;

		return true;
	}

	@Override
	public void onFragmentDestroy() {
		super.onFragmentDestroy();
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
	}

	@Override
	public View createView(@NonNull Context context) {
		actionBar.setBackButtonImage(R.drawable.ic_back_arrow);
		actionBar.setAllowOverlayTitle(true);
		actionBar.setTitle(context.getString(R.string.InviteLink));

		actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
			@Override
			public void onItemClick(int id) {
				if (id == ActionBar.BACK_BUTTON) {
					finishFragment();
				}
			}
		});

		listAdapter = new ListAdapter(context);

		fragmentView = new FrameLayout(context);
		FrameLayout frameLayout = (FrameLayout)fragmentView;
		frameLayout.setBackgroundResource(R.color.light_background);

		EmptyTextProgressView emptyView = new EmptyTextProgressView(context);
		emptyView.showProgress();
		frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

		RecyclerListView listView = new RecyclerListView(context);
		listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
		listView.setEmptyView(emptyView);
		listView.setVerticalScrollBarEnabled(false);
		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
		listView.setAdapter(listAdapter);

		listView.setOnItemClickListener((view, position) -> {
			if (getParentActivity() == null) {
				return;
			}
			if (position == copyLinkRow || position == linkRow) {
				if (invite == null) {
					return;
				}
				try {
					android.content.ClipboardManager clipboard = (android.content.ClipboardManager)ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
					android.content.ClipData clip = android.content.ClipData.newPlainText("label", TLRPCExtensions.getLink(invite));
					clipboard.setPrimaryClip(clip);
					BulletinFactory.createCopyLinkBulletin(this).show();
				}
				catch (Exception e) {
					FileLog.e(e);
				}
			}
			else if (position == shareLinkRow) {
				if (invite == null) {
					return;
				}
				try {
					Intent intent = new Intent(Intent.ACTION_SEND);
					intent.setType("text/plain");
					intent.putExtra(Intent.EXTRA_TEXT, TLRPCExtensions.getLink(invite));
					getParentActivity().startActivityForResult(Intent.createChooser(intent, context.getString(R.string.InviteToGroupByLink)), 500);
				}
				catch (Exception e) {
					FileLog.e(e);
				}
			}
			else if (position == revokeLinkRow) {
				AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
				builder.setMessage(context.getString(R.string.RevokeAlert));
				builder.setTitle(context.getString(R.string.RevokeLink));
				builder.setPositiveButton(context.getString(R.string.RevokeButton), (dialogInterface, i) -> generateLink(true));
				builder.setNegativeButton(context.getString(R.string.Cancel), null);
				showDialog(builder.create());
			}
		});

		return fragmentView;
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.chatInfoDidLoad) {
			TLRPC.ChatFull info = (TLRPC.ChatFull)args[0];
			int guid = (int)args[1];
			if (info.id == chatId && guid == classGuid) {
				invite = getMessagesController().getExportedInvite(chatId);
				if (invite == null) {
					generateLink(false);
				}
				else {
					loading = false;
					if (listAdapter != null) {
						listAdapter.notifyDataSetChanged();
					}
				}
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (listAdapter != null) {
			listAdapter.notifyDataSetChanged();
		}
	}

	private void generateLink(final boolean newRequest) {
		loading = true;
		var req = new TLRPC.TLMessagesExportChatInvite();
		req.peer = getMessagesController().getInputPeer(-chatId);
		final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
			if (error == null) {
				invite = (TLRPC.TLChatInviteExported)response;
				if (newRequest) {
					if (getParentActivity() == null) {
						return;
					}
					AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
					builder.setMessage(getContext().getString(R.string.RevokeAlertNewLink));
					builder.setTitle(getContext().getString(R.string.RevokeLink));
					builder.setNegativeButton(getContext().getString(R.string.OK), null);
					showDialog(builder.create());
				}
			}
			loading = false;
			listAdapter.notifyDataSetChanged();
		}));
		ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
		if (listAdapter != null) {
			listAdapter.notifyDataSetChanged();
		}
	}

	private class ListAdapter extends RecyclerListView.SelectionAdapter {

		private final Context mContext;

		public ListAdapter(Context context) {
			mContext = context;
		}

		@Override
		public boolean isEnabled(RecyclerView.ViewHolder holder) {
			int position = holder.getAdapterPosition();
			return position == revokeLinkRow || position == copyLinkRow || position == shareLinkRow || position == linkRow;
		}

		@Override
		public int getItemCount() {
			return loading ? 0 : rowCount;
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view;
			switch (viewType) {
				case 0:
					view = new TextSettingsCell(mContext);
					view.setBackgroundResource(R.color.background);
					break;
				case 1:
					view = new TextInfoPrivacyCell(mContext);
					break;
				case 2:
				default:
					view = new TextBlockCell(mContext);
					view.setBackgroundResource(R.color.background);
					break;
			}

			return new RecyclerListView.Holder(view);
		}

		@Override
		public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
			switch (holder.getItemViewType()) {
				case 0:
					TextSettingsCell textCell = (TextSettingsCell)holder.itemView;
					if (position == copyLinkRow) {
						textCell.setText(holder.itemView.getContext().getString(R.string.CopyLink), true);
					}
					else if (position == shareLinkRow) {
						textCell.setText(holder.itemView.getContext().getString(R.string.ShareLink), false);
					}
					else if (position == revokeLinkRow) {
						textCell.setText(holder.itemView.getContext().getString(R.string.RevokeLink), true);
					}
					break;
				case 1:
					TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell)holder.itemView;
					if (position == shadowRow) {
						privacyCell.setText("");
						privacyCell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
					}
					else if (position == linkInfoRow) {
						TLRPC.Chat chat = getMessagesController().getChat(chatId);
						if (ChatObject.isChannel(chat) && !chat.megagroup) {
							privacyCell.setText(holder.itemView.getContext().getString(R.string.ChannelLinkInfo));
						}
						else {
							privacyCell.setText(holder.itemView.getContext().getString(R.string.LinkInfo));
						}
						privacyCell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
					}
					break;
				case 2:
					TextBlockCell textBlockCell = (TextBlockCell)holder.itemView;
					textBlockCell.setText(invite != null ? TLRPCExtensions.getLink(invite) : "error", false);
					break;
			}
		}

		@Override
		public int getItemViewType(int position) {
			if (position == copyLinkRow || position == shareLinkRow || position == revokeLinkRow) {
				return 0;
			}
			else if (position == shadowRow || position == linkInfoRow) {
				return 1;
			}
			else if (position == linkRow) {
				return 2;
			}
			return 0;
		}
	}
}
