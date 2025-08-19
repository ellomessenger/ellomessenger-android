/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.databinding.InviteLinkUserBottomSheetBinding;
import org.telegram.messenger.databinding.ManageInviteLinksRowBinding;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLChatChannelParticipant;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.TLRPC.TLChatInviteExported;
import org.telegram.tgnet.TLRPC.TLUserFull;
import org.telegram.tgnet.TLRPC.User;
import org.telegram.tgnet.TLRPCExtensions;
import org.telegram.tgnet.Vector;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.LinkEditActivity;
import org.telegram.ui.ManageLinksActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class InviteLinkBottomSheet extends BottomSheet {
	private final long timeDif;
	public boolean isNeedReopen = false;
	TLRPC.TLChatInviteExported invite;
	HashMap<Long, User> users;
	TLRPC.ChatFull info;
	int creatorHeaderRow;
	int creatorRow;
	int dividerRow;
	int divider2Row;
	int divider3Row;
	int joinedHeaderRow;
	int joinedStartRow;
	int joinedEndRow;
	int linkActionRow;
	int linkInfoRow;
	int loadingRow;
	int emptyView;
	int emptyView2;
	int emptyView3;
	int emptyHintRow;
	int requestedHeaderRow;
	int requestedStartRow;
	int requestedEndRow;
	int titleRow;
	int manageInviteLinksRow;
	boolean usersLoading;
	boolean hasMore;
	int rowCount;
	Adapter adapter;
	BaseFragment fragment;
	ArrayList<TLRPC.TLChatInviteImporter> joinedUsers = new ArrayList<>();
	ArrayList<TLRPC.TLChatInviteImporter> requestedUsers = new ArrayList<>();
	InviteDelegate inviteDelegate;
	private final RecyclerListView listView;
	private TextView titleTextView;
	private AnimatorSet shadowAnimation;
	private final View shadow;
	private int scrollOffsetY;
	private boolean ignoreLayout;
	private final boolean permanent;
	private boolean titleVisible;
	private final long chatId;
	private final boolean isChannel;
	private boolean canEdit = true;
	private final boolean isUser;
	private final TLUserFull userInfo;

	public InviteLinkBottomSheet(Context context, TLRPC.TLChatInviteExported invite, TLRPC.ChatFull info, HashMap<Long, User> users, BaseFragment fragment, long chatId, boolean permanent, boolean isChannel) {
		this(context, invite, info, users, fragment, chatId, permanent, isChannel, false, null);
	}

	public InviteLinkBottomSheet(Context context, TLRPC.TLChatInviteExported invite, TLRPC.ChatFull info, HashMap<Long, User> users, BaseFragment fragment, long chatId, boolean permanent, boolean isChannel, boolean isUser, TLUserFull userInfo) {
		super(context, false);
		this.isUser = isUser;
		this.userInfo = userInfo;
		this.invite = invite;
		this.users = users;
		this.fragment = fragment;
		this.info = info;
		this.chatId = chatId;
		this.permanent = permanent;
		this.isChannel = isChannel;
		fixNavigationBar(context.getColor(R.color.light_background));

		if (this.users == null) {
			this.users = new HashMap<>();
		}

		timeDif = ConnectionsManager.getInstance(currentAccount).getCurrentTime() - (System.currentTimeMillis() / 1000L);

		containerView = new FrameLayout(context) {
			private final RectF rect = new RectF();
			private boolean fullHeight;

			@Override
			public boolean onInterceptTouchEvent(MotionEvent ev) {
				if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
					dismiss();
					return true;
				}
				return super.onInterceptTouchEvent(ev);
			}

			@SuppressLint("ClickableViewAccessibility")
			@Override
			public boolean onTouchEvent(MotionEvent e) {
				return !isDismissed() && super.onTouchEvent(e);
			}

			@Override
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
				int height = MeasureSpec.getSize(heightMeasureSpec);
				ignoreLayout = true;
				setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
				ignoreLayout = false;
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
				fullHeight = true;
			}

			@Override
			protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
				super.onLayout(changed, left, top, right, bottom);
				updateLayout();
			}

			@Override
			public void requestLayout() {
				if (ignoreLayout) {
					return;
				}
				super.requestLayout();
			}

			@Override
			protected void onDraw(@NonNull Canvas canvas) {
				int top = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(8);
				int height = getMeasuredHeight() + AndroidUtilities.dp(36) + backgroundPaddingTop;
				int statusBarHeight = 0;
				float radProgress = 1.0f;
				top += AndroidUtilities.statusBarHeight;
				height -= AndroidUtilities.statusBarHeight;

				if (fullHeight) {
					if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight * 2) {
						int diff = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop);
						top -= diff;
						height += diff;
						radProgress = 1.0f - Math.min(1.0f, (diff * 2) / (float)AndroidUtilities.statusBarHeight);
					}
					if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
						statusBarHeight = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop);
					}
				}

				shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
				shadowDrawable.draw(canvas);

				if (radProgress != 1.0f) {
					Theme.dialogs_onlineCirclePaint.setColor(context.getColor(R.color.background));
					rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
					canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * radProgress, AndroidUtilities.dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
				}

				if (statusBarHeight > 0) {
					int color1 = context.getColor(R.color.background);
					int finalColor = Color.argb(0xff, (int)(Color.red(color1) * 0.8f), (int)(Color.green(color1) * 0.8f), (int)(Color.blue(color1) * 0.8f));
					Theme.dialogs_onlineCirclePaint.setColor(finalColor);
					canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
				}
			}
		};
		containerView.setWillNotDraw(false);

		FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
		frameLayoutParams.topMargin = AndroidUtilities.dp(48);
		shadow = new View(context);
		shadow.setAlpha(0.0f);
		shadow.setVisibility(View.INVISIBLE);
		shadow.setTag(1);
		containerView.addView(shadow, frameLayoutParams);

		listView = new RecyclerListView(context) {

			int lastH;

			@Override
			public void requestLayout() {
				if (ignoreLayout) {
					return;
				}
				super.requestLayout();
			}

			@Override
			protected void onMeasure(int widthSpec, int heightSpec) {
				if (lastH != MeasureSpec.getSize(heightSpec)) {
					lastH = MeasureSpec.getSize(heightSpec);
					ignoreLayout = true;
					listView.setPadding(0, 0, 0, 0);
					ignoreLayout = false;

					measure(widthSpec, View.MeasureSpec.makeMeasureSpec(heightSpec, MeasureSpec.AT_MOST));
					int contentSize = getMeasuredHeight();

					int padding = (int)(lastH / 5f * 2f);
					if (padding < lastH - contentSize + AndroidUtilities.dp(60)) {
						padding = lastH - contentSize;
					}
					ignoreLayout = true;
					listView.setPadding(0, padding, 0, 0);
					ignoreLayout = false;

					measure(widthSpec, View.MeasureSpec.makeMeasureSpec(heightSpec, MeasureSpec.AT_MOST));
				}

				super.onMeasure(widthSpec, heightSpec);
			}
		};
		listView.setTag(14);
		LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
		listView.setLayoutManager(layoutManager);
		listView.setAdapter(adapter = new Adapter());
		listView.setVerticalScrollBarEnabled(false);
		listView.setClipToPadding(false);
		listView.setNestedScrollingEnabled(true);
		listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
				updateLayout();
				if (hasMore && !usersLoading) {
					int lastPosition = layoutManager.findLastVisibleItemPosition();
					if (rowCount - lastPosition < 10) {
						loadUsers();
					}
				}
			}
		});
		listView.setOnItemClickListener((view, position) -> {
			if (position == creatorRow && invite.adminId == UserConfig.getInstance(currentAccount).clientUserId) {
				return;
			}
			boolean isJoinedUserRow = position >= joinedStartRow && position < joinedEndRow;
			boolean isRequestedUserRow = position >= requestedStartRow && position < requestedEndRow;
			if ((position == creatorRow || isJoinedUserRow || isRequestedUserRow) && users != null) {
				long userId = invite.adminId;
				if (isJoinedUserRow) {
					userId = joinedUsers.get(position - joinedStartRow).userId;
				}
				else if (isRequestedUserRow) {
					userId = requestedUsers.get(position - requestedStartRow).userId;
				}
				User user = users.get(userId);
				if (user != null) {
					MessagesController.getInstance(UserConfig.selectedAccount).putUser(user, false);
					AndroidUtilities.runOnUIThread(() -> {
						Bundle bundle = new Bundle();
						bundle.putLong("user_id", user.id);
						ProfileActivity profileActivity = new ProfileActivity(bundle);
						fragment.presentFragment(profileActivity);
						isNeedReopen = true;
					}, 100);
					dismiss();
				}
			}
		});

		containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, !titleVisible ? 0 : 44, 0, 0));

		updateRows();
		if (!isUser) {
			loadUsers();
		}
		if (!isUser && (users == null || users.get(invite.adminId) == null)) {
			loadCreator();
		}

		updateColors();
	}

	public void updateColors() {
		if (titleTextView != null) {
			titleTextView.setTextColor(getContext().getColor(R.color.text));
			titleTextView.setLinkTextColor(getContext().getColor(R.color.brand));
			titleTextView.setHighlightColor(getContext().getColor(R.color.darker_brand));
			if (!titleVisible) {
				titleTextView.setBackgroundColor(getContext().getColor(R.color.background));
			}
		}
		listView.setGlowColor(getContext().getColor(R.color.light_background));
		shadow.setBackgroundResource(R.color.shadow);
		setBackgroundColor(getContext().getColor(R.color.background));

		int count = listView.getHiddenChildCount();

		for (int i = 0; i < listView.getChildCount(); i++) {
			updateColorForView(listView.getChildAt(i));
		}
		for (int a = 0; a < count; a++) {
			updateColorForView(listView.getHiddenChildAt(a));
		}
		count = listView.getCachedChildCount();
		for (int a = 0; a < count; a++) {
			updateColorForView(listView.getCachedChildAt(a));
		}
		count = listView.getAttachedScrapChildCount();
		for (int a = 0; a < count; a++) {
			updateColorForView(listView.getAttachedScrapChildAt(a));
		}
		containerView.invalidate();
	}

	@Override
	public void show() {
		super.show();
		isNeedReopen = false;
	}

	private void updateColorForView(View view) {
		if (view instanceof HeaderCell) {
			((HeaderCell)view).getTextView().setTextColor(getContext().getColor(R.color.brand));
		}
		else if (view instanceof TextInfoPrivacyCell) {
			CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(getContext().getColor(R.color.light_background)), Theme.getThemedDrawable(view.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
			combinedDrawable.setFullSize(true);
			view.setBackground(combinedDrawable);
			((TextInfoPrivacyCell)view).setTextColor(getContext().getColor(R.color.dark_gray));
		}
		else if (view instanceof UserCell) {
			((UserCell)view).update(0);
		}
		RecyclerView.ViewHolder holder = listView.getChildViewHolder(view);
		if (holder != null) {
			if (holder.getItemViewType() == 7) {
				Drawable shadowDrawable = Theme.getThemedDrawable(view.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
				Drawable background = new ColorDrawable(getContext().getColor(R.color.light_background));
				CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
				combinedDrawable.setFullSize(true);
				view.setBackground(combinedDrawable);
			}
			else if (holder.getItemViewType() == 2) {
				Drawable shadowDrawable = Theme.getThemedDrawable(view.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
				Drawable background = new ColorDrawable(getContext().getColor(R.color.light_background));
				CombinedDrawable combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
				combinedDrawable.setFullSize(true);
				view.setBackground(combinedDrawable);
			}
		}
	}

	private void loadCreator() {
		var req = new TLRPC.TLUsersGetUsers();
		req.id.add(MessagesController.getInstance(UserConfig.selectedAccount).getInputUser(invite.adminId));
		ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
			if (error == null) {
				Vector vector = (Vector)response;
				User user = (User)vector.objects.get(0);
				users.put(invite.adminId, user);
				adapter.notifyDataSetChanged();
			}
		}));
	}

	@Override
	protected boolean canDismissWithSwipe() {
		return false;
	}

	private void updateRows() {
		rowCount = 0;
		dividerRow = -1;
		divider2Row = -1;
		divider3Row = -1;
		joinedHeaderRow = -1;
		joinedStartRow = -1;
		joinedEndRow = -1;
		emptyView2 = -1;
		emptyView3 = -1;
		linkActionRow = -1;
		linkInfoRow = -1;
		emptyHintRow = -1;
		requestedHeaderRow = -1;
		requestedStartRow = -1;
		requestedEndRow = -1;
		loadingRow = -1;
		manageInviteLinksRow = -1;

		titleRow = rowCount++;
		if (!permanent) {
			linkActionRow = rowCount++;
			linkInfoRow = rowCount++;
		}

		if (!isUser) {
			creatorHeaderRow = rowCount++;
			creatorRow = rowCount++;
		}
		else {
			creatorHeaderRow = -1;
			creatorRow = -1;
		}
		emptyView = rowCount++;

		boolean needUsers = invite.usage > 0 || invite.usageLimit > 0 || invite.requested > 0;
		boolean needLoadUsers = invite.usage > joinedUsers.size() || invite.requestNeeded && invite.requested > requestedUsers.size();
		boolean usersLoaded = false;
		if (!joinedUsers.isEmpty()) {
			dividerRow = rowCount++;
			joinedHeaderRow = rowCount++;
			joinedStartRow = rowCount;
			rowCount += joinedUsers.size();
			joinedEndRow = rowCount;
			emptyView2 = rowCount++;
			usersLoaded = true;
		}
		if (!requestedUsers.isEmpty()) {
			divider2Row = rowCount++;
			requestedHeaderRow = rowCount++;
			requestedStartRow = rowCount;
			rowCount += requestedUsers.size();
			requestedEndRow = rowCount;
			emptyView3 = rowCount++;
			usersLoaded = true;
		}
		if (needUsers || needLoadUsers) {
			if (!usersLoaded) {
				dividerRow = rowCount++;
				loadingRow = rowCount++;
				emptyView2 = rowCount++;
			}
		}
		if (emptyHintRow == -1) {
			divider3Row = rowCount++;
		}

		adapter.notifyDataSetChanged();
	}

	private void updateLayout() {
		if (listView.getChildCount() <= 0) {
			listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
			titleTextView.setTranslationY(scrollOffsetY);
			shadow.setTranslationY(scrollOffsetY);
			containerView.invalidate();
			return;
		}
		View child = listView.getChildAt(0);
		RecyclerListView.Holder holder = (RecyclerListView.Holder)listView.findContainingViewHolder(child);
		int top = child.getTop();
		int newOffset = 0;
		if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
			newOffset = top;
			runShadowAnimation(false);
		}
		else {
			runShadowAnimation(true);
		}
		if (scrollOffsetY != newOffset) {
			listView.setTopGlowOffset(scrollOffsetY = newOffset);
			if (titleTextView != null) {
				titleTextView.setTranslationY(scrollOffsetY);
			}
			shadow.setTranslationY(scrollOffsetY);
			containerView.invalidate();
		}
	}

	private void runShadowAnimation(final boolean show) {
		if (show && shadow.getTag() != null || !show && shadow.getTag() == null) {
			shadow.setTag(show ? null : 1);
			if (show) {
				shadow.setVisibility(View.VISIBLE);
				titleTextView.setVisibility(View.VISIBLE);
			}
			if (shadowAnimation != null) {
				shadowAnimation.cancel();
			}
			shadowAnimation = new AnimatorSet();
			shadowAnimation.playTogether(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
			if (!titleVisible) {
				shadowAnimation.playTogether(ObjectAnimator.ofFloat(titleTextView, View.ALPHA, show ? 1.0f : 0.0f));
			}
			shadowAnimation.setDuration(150);
			shadowAnimation.addListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation) {
					if (shadowAnimation != null && shadowAnimation.equals(animation)) {
						if (!show) {
							shadow.setVisibility(View.INVISIBLE);
						}
						shadowAnimation = null;
					}
				}

				@Override
				public void onAnimationCancel(Animator animation) {
					if (shadowAnimation != null && shadowAnimation.equals(animation)) {
						shadowAnimation = null;
					}
				}
			});
			shadowAnimation.start();
		}
	}

	public void loadUsers() {
		if (usersLoading) {
			return;
		}

		boolean hasMoreJoinedUsers = invite.usage > joinedUsers.size();
		boolean hasMoreRequestedUsers = invite.requestNeeded && invite.requested > requestedUsers.size();
		boolean loadRequestedUsers;
		if (hasMoreJoinedUsers) {
			loadRequestedUsers = false;
		}
		else if (hasMoreRequestedUsers) {
			loadRequestedUsers = true;
		}
		else {
			return;
		}

		final List<TLRPC.TLChatInviteImporter> importersList = loadRequestedUsers ? requestedUsers : joinedUsers;
		var req = new TLRPC.TLMessagesGetChatInviteImporters();
		req.flags |= 2;
		req.link = invite.link;
		req.peer = MessagesController.getInstance(UserConfig.selectedAccount).getInputPeer(-chatId);
		req.requested = loadRequestedUsers;
		if (importersList.isEmpty()) {
			req.offsetUser = new TLRPC.TLInputUserEmpty();
		}
		else {
			TLRPC.TLChatInviteImporter invitedUser = importersList.get(importersList.size() - 1);
			req.offsetUser = MessagesController.getInstance(currentAccount).getInputUser(users.get(invitedUser.userId));
			req.offsetDate = invitedUser.date;
		}

		usersLoading = true;
		ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
			if (error == null) {
				var inviteImporters = (TLRPC.TLMessagesChatInviteImporters)response;
				importersList.addAll(inviteImporters.importers);
				for (int i = 0; i < inviteImporters.users.size(); i++) {
					User user = inviteImporters.users.get(i);
					users.put(user.id, user);
				}
				hasMore = loadRequestedUsers ? importersList.size() < inviteImporters.count : importersList.size() < inviteImporters.count || hasMoreRequestedUsers;
				updateRows();
			}
			usersLoading = false;
		}));
	}

	public void setInviteDelegate(InviteDelegate inviteDelegate) {
		this.inviteDelegate = inviteDelegate;
	}

	public void setCanEdit(boolean canEdit) {
		this.canEdit = canEdit;
	}

	public interface InviteDelegate {
		void permanentLinkReplaced(TLRPC.TLChatInviteExported oldLink, TLRPC.TLChatInviteExported newLink);

		void linkRevoked(TLRPC.TLChatInviteExported invite);

		void onLinkDeleted(TLRPC.TLChatInviteExported invite);

		void onLinkEdited(TLRPC.TLChatInviteExported invite);
	}

	private class Adapter extends RecyclerListView.SelectionAdapter {

		final int CREATOR_HEADER = 0, CREATOR = 1, DIVIDER = 2, LINK_ACTION = 3, LINK_INFO = 4, LOADING = 5, EMPTY = 6, DIVIDER3 = 7, EMPTY_HINT = 8, TITLE = 9, BUTTON_ROW = 10;

		@Override
		public int getItemViewType(int position) {
			if (position == creatorHeaderRow || position == requestedHeaderRow || position == joinedHeaderRow) {
				return CREATOR_HEADER;
			}
			else if (position == creatorRow || position >= requestedStartRow && position < requestedEndRow || position >= joinedStartRow && position < joinedEndRow) {
				return CREATOR;
			}
			else if (position == dividerRow || position == divider2Row) {
				return DIVIDER;
			}
			else if (position == linkActionRow) {
				return LINK_ACTION;
			}
			else if (position == linkInfoRow) {
				return LINK_INFO;
			}
			else if (position == loadingRow) {
				return LOADING;
			}
			else if (position == emptyView || position == emptyView2 || position == emptyView3) {
				return EMPTY;
			}
			else if (position == divider3Row) {
				return DIVIDER3;
			}
			else if (position == emptyHintRow) {
				return EMPTY_HINT;
			}
			else if (position == titleRow) {
				return TITLE;
			}
			else if (position == manageInviteLinksRow) {
				return BUTTON_ROW;
			}
			return 0;
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view;
			Context context = parent.getContext();
			switch (viewType) {
				default:
				case CREATOR_HEADER:
					HeaderCell headerCell = new HeaderCell(context, 21, 15, 15, true);
					headerCell.getTextView2().setTextColor(getContext().getColor(R.color.purple));
					headerCell.getTextView2().setTextSize(15);
					headerCell.getTextView2().setTypeface(Theme.TYPEFACE_BOLD);
					view = headerCell;
					break;
				case CREATOR:
					view = new UserCell(context, 12, 0, true);
					break;
				case DIVIDER:
					view = new ShadowSectionCell(context, 12, getContext().getColor(R.color.light_background));
					break;
				case LINK_ACTION:

					LinkActionView.LinkActionType type;

					if (isUser) {
						type = LinkActionView.LinkActionType.USER;
					}
					else if (isChannel) {
						type = LinkActionView.LinkActionType.CHANNEL;
					}
					else {
						type = LinkActionView.LinkActionType.GROUP;
					}

					LinkActionView linkActionView = new LinkActionView(context, fragment, InviteLinkBottomSheet.this, false);
					view = linkActionView;
					linkActionView.setDelegate(new LinkActionView.Delegate() {
						@Override
						public void showQr() {
							// unused
						}

						@Override
						public void showUsersForPermanentLink() {
							// unused
						}

						@Override
						public void revokeLink() {
							if (fragment instanceof ManageLinksActivity) {
								((ManageLinksActivity)fragment).revokeLink(invite);
							}
							else {
								var req = new TLRPC.TLMessagesEditExportedChatInvite();
								req.link = invite.link;
								req.revoked = true;
								req.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId);
								ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
									if (error == null) {
										if (response instanceof TLRPC.TLMessagesExportedChatInviteReplaced replaced) {
											if (info != null) {
												info.exportedInvite = replaced.newInvite;
											}
											if (inviteDelegate != null) {
												inviteDelegate.permanentLinkReplaced(invite, (TLChatInviteExported)info.exportedInvite);
											}
										}
										else {
											if (info != null) {
												info.invitesCount--;
												if (info.invitesCount < 0) {
													info.invitesCount = 0;
												}
												MessagesStorage.getInstance(currentAccount).saveChatLinksCount(chatId, info.invitesCount);
											}
											if (inviteDelegate != null) {
												inviteDelegate.linkRevoked(invite);
											}
										}
									}
								}));
							}
							dismiss();
						}

						@Override
						public void editLink() {
							if (fragment instanceof ManageLinksActivity) {
								((ManageLinksActivity)fragment).editLink(invite);
							}
							else {
								LinkEditActivity activity = new LinkEditActivity(LinkEditActivity.EDIT_TYPE, chatId);
								activity.setInviteToEdit(invite);
								activity.setCallback(new LinkEditActivity.Callback() {
									@Override
									public void onLinkCreated(TLObject response) {

									}

									@Override
									public void onLinkEdited(TLRPC.TLChatInviteExported inviteToEdit, TLObject response) {
										if (inviteDelegate != null) {
											inviteDelegate.onLinkEdited(inviteToEdit);
										}
									}

									@Override
									public void onLinkRemoved(TLRPC.TLChatInviteExported inviteFinal) {

									}

									@Override
									public void revokeLink(TLRPC.TLChatInviteExported inviteFinal) {

									}
								});
								fragment.presentFragment(activity);
							}
							dismiss();
						}

						@Override
						public void removeLink() {
							if (fragment instanceof ManageLinksActivity) {
								((ManageLinksActivity)fragment).deleteLink(invite);
							}
							else {
								var req = new TLRPC.TLMessagesDeleteExportedChatInvite();
								req.link = invite.link;
								req.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId);
								ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
									if (error == null) {
										if (inviteDelegate != null) {
											inviteDelegate.onLinkDeleted(invite);
										}
									}
								}));
							}
							dismiss();
						}
					});
					view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
					break;
				case LINK_INFO:
					view = new TimerPrivacyCell(context);
					CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(getContext().getColor(R.color.light_background)), Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
					combinedDrawable.setFullSize(true);
					view.setBackground(combinedDrawable);
					break;
				case LOADING:
					FlickerLoadingView flickerLoadingView = new FlickerLoadingView(context);
					flickerLoadingView.setIsSingleCell(true);
					flickerLoadingView.setViewType(FlickerLoadingView.USERS2_TYPE);
					flickerLoadingView.showDate(false);
					flickerLoadingView.setPaddingLeft(AndroidUtilities.dp(10));
					view = flickerLoadingView;
					break;
				case EMPTY:
					view = new View(context) {
						@Override
						protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
							super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(5), MeasureSpec.EXACTLY));
						}
					};
					break;
				case DIVIDER3:
					view = new ShadowSectionCell(context, 12);
					Drawable shadowDrawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
					Drawable background = new ColorDrawable(getContext().getColor(R.color.light_background));
					combinedDrawable = new CombinedDrawable(background, shadowDrawable, 0, 0);
					combinedDrawable.setFullSize(true);
					view.setBackground(combinedDrawable);
					break;
				case EMPTY_HINT:
					view = new EmptyHintRow(context);
					break;
				case TITLE:
					view = InviteLinkUserBottomSheetBinding.inflate(LayoutInflater.from(context), parent, false).getRoot();
					break;
				case BUTTON_ROW:
					view = ManageInviteLinksRowBinding.inflate(LayoutInflater.from(context), parent, false).getRoot();
					break;
			}
			view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
			return new RecyclerListView.Holder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			switch (holder.getItemViewType()) {
				case CREATOR_HEADER:
					HeaderCell headerCell = (HeaderCell)holder.itemView;
					if (position == creatorHeaderRow) {
						headerCell.setText(getContext().getString(R.string.LinkCreatedeBy));
						headerCell.setText2(null);
					}
					else if (position == joinedHeaderRow) {
						if (invite.usage > 0) {
							headerCell.setText(LocaleController.formatPluralString("PeopleJoined", invite.usage));
						}
						else {
							headerCell.setText(getContext().getString(R.string.NoOneJoined));
						}
						if (!invite.expired && !invite.revoked && invite.usageLimit > 0 && invite.usage > 0) {
							headerCell.setText2(LocaleController.formatPluralString("PeopleJoinedRemaining", invite.usageLimit - invite.usage));
						}
						else {
							headerCell.setText2(null);
						}
					}
					else if (position == requestedHeaderRow) {
						headerCell.setText(LocaleController.formatPluralString("JoinRequests", invite.requested));
					}
					break;
				case CREATOR:
					UserCell userCell = (UserCell)holder.itemView;
					User user;
					String role = null;
					String status = null;
					if (position == creatorRow) {
						user = users.get(invite.adminId);
						if (user == null) {
							user = MessagesController.getInstance(currentAccount).getUser(invite.adminId);
						}
						if (user != null) {
							status = LocaleController.formatDateAudio(invite.date, false);
						}

						if (info != null && user != null && info.participants != null) {
							var participants = TLRPCExtensions.getParticipants(info.participants);

							if (participants != null) {
								for (int i = 0; i < participants.size(); i++) {
									if (participants.get(i).userId == user.id) {
										TLRPC.ChatParticipant part = participants.get(i);

										if (part instanceof TLChatChannelParticipant) {
											TLRPC.ChannelParticipant channelParticipant = ((TLChatChannelParticipant)part).getChannelParticipant();
											if (!TextUtils.isEmpty(channelParticipant.rank)) {
												role = channelParticipant.rank;
											}
											else {
												if (channelParticipant instanceof TLRPC.TLChannelParticipantCreator) {
													role = getContext().getString(R.string.ChannelCreator);
												}
												else if (channelParticipant instanceof TLRPC.TLChannelParticipantAdmin) {
													role = getContext().getString(R.string.ChannelAdmin);
												}
											}
										}
										else {
											if (part instanceof TLRPC.TLChatParticipantCreator) {
												role = getContext().getString(R.string.ChannelCreator);
											}
											else if (part instanceof TLRPC.TLChatParticipantAdmin) {
												role = getContext().getString(R.string.ChannelAdmin);
											}
										}
										break;
									}
								}
							}
						}
					}
					else {
						int startRow = joinedStartRow;
						List<TLRPC.TLChatInviteImporter> usersList = joinedUsers;
						if (requestedStartRow != -1 && position >= requestedStartRow) {
							startRow = requestedStartRow;
							usersList = requestedUsers;
						}
						TLRPC.TLChatInviteImporter invitedUser = usersList.get(position - startRow);
						user = users.get(invitedUser.userId);
					}
					userCell.setAdminRole(role);
					userCell.setData(user, null, status, 0, false);
					break;
				case LINK_ACTION:
					LinkActionView actionView = (LinkActionView)holder.itemView;
					actionView.setUsers(0, null);
					actionView.setLink(invite.link);
					actionView.setRevoke(invite.revoked);
					actionView.setPermanent(invite.permanent);
					actionView.setCanEdit(canEdit);
					actionView.hideRevokeOption(!canEdit);
					break;
				case LINK_INFO:
					TimerPrivacyCell privacyCell = (TimerPrivacyCell)holder.itemView;
					privacyCell.cancelTimer();
					privacyCell.timer = false;
					privacyCell.setTextColor(getContext().getColor(R.color.dark_gray));
					privacyCell.setFixedSize(0);
					if (invite.revoked) {
						privacyCell.setText(getContext().getString(R.string.LinkIsNoActive));
					}
					else if (invite.expired) {
						if (invite.usageLimit > 0 && invite.usageLimit == invite.usage) {
							privacyCell.setText(getContext().getString(R.string.LinkIsExpiredLimitReached));
						}
						else {
							privacyCell.setText(getContext().getString(R.string.LinkIsExpired));
							privacyCell.setTextColor(getContext().getColor(R.color.purple));
						}
					}
					else if (invite.expireDate > 0) {
						long currentTime = System.currentTimeMillis() + timeDif * 1000L;
						long expireTime = invite.expireDate * 1000L;

						long timeLeft = expireTime - currentTime;
						if (timeLeft < 0) {
							timeLeft = 0;
						}
						String time;
						if (timeLeft > 86400000L) {
							time = LocaleController.formatDateAudio(invite.expireDate, false);
							privacyCell.setText(LocaleController.formatString("LinkExpiresIn", R.string.LinkExpiresIn, time));
						}
						else {
							int s = (int)((timeLeft / 1000) % 60);
							int m = (int)((timeLeft / 1000 / 60) % 60);
							int h = (int)((timeLeft / 1000 / 60 / 60));
							time = String.format(Locale.ENGLISH, "%02d", h) + String.format(Locale.ENGLISH, ":%02d", m) + String.format(Locale.ENGLISH, ":%02d", s);
							privacyCell.timer = true;
							privacyCell.runTimer();
							privacyCell.setText(LocaleController.formatString("LinkExpiresInTime", R.string.LinkExpiresInTime, time));
						}
					}
					else {
						privacyCell.setFixedSize(12);
						privacyCell.setText(null);
					}
					break;
				case EMPTY_HINT:
					EmptyHintRow emptyHintRow = (EmptyHintRow)holder.itemView;
					if (invite.usageLimit > 0) {
						emptyHintRow.textView.setText(LocaleController.formatPluralString("PeopleCanJoinViaLinkCount", invite.usageLimit));
						emptyHintRow.textView.setVisibility(View.VISIBLE);
					}
					else {
						emptyHintRow.textView.setVisibility(View.GONE);
					}
					break;
				case BUTTON_ROW:
					ManageInviteLinksRowBinding.bind(holder.itemView).manageInvite.setOnClickListener(v -> {
						ManageLinksActivity manageFragment;

						if (isUser) {
							manageFragment = new ManageLinksActivity(userInfo.id, 0);
							manageFragment.setInfo(userInfo, invite);
						}
						else {
							manageFragment = new ManageLinksActivity(info.id, 0, 0);
							manageFragment.setInfo(info, info.exportedInvite);
						}

						fragment.presentFragment(manageFragment);

						dismiss();
					});

					break;
			}
		}

		@Override
		public int getItemCount() {
			return rowCount;
		}

		@Override
		public boolean isEnabled(RecyclerView.ViewHolder holder) {
			int position = holder.getAdapterPosition();

			if (position == creatorRow) {
				return invite.adminId != UserConfig.getInstance(currentAccount).clientUserId;
			}
			else {
				return position >= joinedStartRow && position < joinedEndRow || position >= requestedStartRow && position < requestedEndRow;
			}
		}
	}

	private class TimerPrivacyCell extends TextInfoPrivacyCell {
		boolean timer;

		Runnable timerRunnable = new Runnable() {
			@Override
			public void run() {
				if (listView != null && listView.getAdapter() != null) {
					int p = listView.getChildAdapterPosition(TimerPrivacyCell.this);
					if (p >= 0) {
						adapter.onBindViewHolder(listView.getChildViewHolder(TimerPrivacyCell.this), p);
					}
				}
				AndroidUtilities.runOnUIThread(this);
			}
		};

		public TimerPrivacyCell(Context context) {
			super(context);
		}

		@Override
		protected void onAttachedToWindow() {
			super.onAttachedToWindow();
			runTimer();
		}

		@Override
		protected void onDetachedFromWindow() {
			super.onDetachedFromWindow();
			cancelTimer();
		}

		public void cancelTimer() {
			AndroidUtilities.cancelRunOnUIThread(timerRunnable);
		}

		public void runTimer() {
			cancelTimer();
			if (timer) {
				AndroidUtilities.runOnUIThread(timerRunnable, 500);
			}
		}
	}

	private static class EmptyHintRow extends FrameLayout {
		TextView textView;

		public EmptyHintRow(@NonNull Context context) {
			super(context);
			textView = new TextView(context);
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
			textView.setTextColor(getContext().getColor(R.color.dark_gray));
			textView.setGravity(Gravity.CENTER_HORIZONTAL);
			addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 60, 0, 60, 0));
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(84), MeasureSpec.EXACTLY));
		}
	}
}
