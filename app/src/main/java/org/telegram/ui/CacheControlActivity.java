/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FilesMigrationService;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Components.StorageDiagramView;
import org.telegram.ui.Components.StorageUsageView;
import org.telegram.ui.Components.UndoView;

import java.io.File;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class CacheControlActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
	private ListAdapter listAdapter;
	private RecyclerListView listView;
	@SuppressWarnings("FieldCanBeLocal")
	private LinearLayoutManager layoutManager;
	AlertDialog progressDialog;

	private int databaseRow;
	private int databaseInfoRow;
	private int keepMediaHeaderRow;
	private int keepMediaInfoRow;
	private int cacheInfoRow;
	private int deviseStorageHeaderRow;
	private int storageUsageRow;
	private int keepMediaChooserRow;
	private int rowCount;

	private long databaseSize = -1;
	private long cacheSize = -1;
	private long documentsSize = -1;
	private long audioSize = -1;
	private long musicSize = -1;
	private long photoSize = -1;
	private long videoSize = -1;
	private long stickersSize = -1;
	private long totalSize = -1;
	private long totalDeviceSize = -1;
	private long totalDeviceFreeSize = -1;
	private final long migrateOldFolderRow = -1;
	private final StorageDiagramView.ClearViewData[] clearViewData = new StorageDiagramView.ClearViewData[7];
	private boolean calculating = true;

	private volatile boolean canceled = false;

	private View bottomSheetView;
	private BottomSheet bottomSheet;
	private View actionTextView;

	private UndoView cacheRemovedTooltip;

	long fragmentCreateTime;

	@Override
	public boolean onFragmentCreate() {
		super.onFragmentCreate();
		getNotificationCenter().addObserver(this, NotificationCenter.didClearDatabase);
		databaseSize = MessagesStorage.getInstance(currentAccount).getDatabaseSize();

		Utilities.globalQueue.postRunnable(() -> {
			cacheSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 0);
			if (canceled) {
				return;
			}
			photoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), 0);
			photoSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC), 0);
			if (canceled) {
				return;
			}
			videoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), 0);
			videoSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO_PUBLIC), 0);
			if (canceled) {
				return;
			}
			documentsSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 1);
			documentsSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), 1);
			if (canceled) {
				return;
			}
			musicSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 2);
			musicSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), 2);
			if (canceled) {
				return;
			}
			stickersSize = getDirectorySize(new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache"), 0);
			if (canceled) {
				return;
			}
			audioSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_AUDIO), 0);
			totalSize = cacheSize + videoSize + audioSize + photoSize + documentsSize + musicSize + stickersSize;

			File path;
			if (Build.VERSION.SDK_INT >= 19) {
				ArrayList<File> storageDirs = AndroidUtilities.getRootDirs();
				String dir = (path = storageDirs.get(0)).getAbsolutePath();
				if (!TextUtils.isEmpty(SharedConfig.storageCacheDir)) {
					for (int a = 0, N = storageDirs.size(); a < N; a++) {
						File file = storageDirs.get(a);
						if (file.getAbsolutePath().startsWith(SharedConfig.storageCacheDir)) {
							path = file;
							break;
						}
					}
				}
			}
			else {
				path = new File(SharedConfig.storageCacheDir);
			}
			try {
				StatFs stat = new StatFs(path.getPath());
				long blockSize;
				long blockSizeExternal;
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
					blockSize = stat.getBlockSizeLong();
				}
				else {
					blockSize = stat.getBlockSize();
				}
				long availableBlocks;
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
					availableBlocks = stat.getAvailableBlocksLong();
				}
				else {
					availableBlocks = stat.getAvailableBlocks();
				}
				long blocksTotal;
				if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
					blocksTotal = stat.getBlockCountLong();
				}
				else {
					blocksTotal = stat.getBlockCount();
				}

				totalDeviceSize = blocksTotal * blockSize;
				totalDeviceFreeSize = availableBlocks * blockSize;
			}
			catch (Exception e) {
				FileLog.e(e);
			}

			AndroidUtilities.runOnUIThread(() -> {
				calculating = false;
				updateStorageUsageRow();
			});
		});

		fragmentCreateTime = System.currentTimeMillis();
		updateRows();
		return true;
	}

	private void updateRows() {
		rowCount = 0;

		keepMediaHeaderRow = rowCount++;
		keepMediaChooserRow = rowCount++;
		keepMediaInfoRow = rowCount++;
		deviseStorageHeaderRow = rowCount++;
		storageUsageRow = rowCount++;

		cacheInfoRow = rowCount++;
		databaseRow = rowCount++;
		databaseInfoRow = rowCount++;
	}

	private void updateStorageUsageRow() {
		View view = layoutManager.findViewByPosition(storageUsageRow);
		if (view instanceof StorageUsageView) {
			StorageUsageView storageUsageView = ((StorageUsageView)view);
			long currentTime = System.currentTimeMillis();
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && currentTime - fragmentCreateTime > 250) {
				TransitionSet transition = new TransitionSet();
				ChangeBounds changeBounds = new ChangeBounds();
				changeBounds.setDuration(250);
				changeBounds.excludeTarget(storageUsageView.legendLayout, true);
				Fade in = new Fade(Fade.IN);
				in.setDuration(290);
				transition.addTransition(new Fade(Fade.OUT).setDuration(250)).addTransition(changeBounds).addTransition(in);
				transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
				transition.setInterpolator(CubicBezierInterpolator.EASE_OUT);
				TransitionManager.beginDelayedTransition(listView, transition);
			}
			storageUsageView.setStorageUsage(calculating, databaseSize, totalSize, totalDeviceFreeSize, totalDeviceSize);
			RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(storageUsageRow);
			if (holder != null) {
				storageUsageView.setEnabled(listAdapter.isEnabled(holder));
			}
		}
		else {
			listAdapter.notifyDataSetChanged();
		}
	}

	@Override
	public void onFragmentDestroy() {
		super.onFragmentDestroy();
		getNotificationCenter().removeObserver(this, NotificationCenter.didClearDatabase);
		try {
			if (progressDialog != null) {
				progressDialog.dismiss();
			}
		}
		catch (Exception e) {

		}
		progressDialog = null;
		canceled = true;
	}

	private long getDirectorySize(File dir, int documentsMusicType) {
		if (dir == null || canceled) {
			return 0;
		}
		long size = 0;
		if (dir.isDirectory()) {
			size = Utilities.getDirSize(dir.getAbsolutePath(), documentsMusicType, false);
		}
		else if (dir.isFile()) {
			size += dir.length();
		}
		return size;
	}

	private void cleanupFolders() {
		final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
		progressDialog.setCanCancel(false);
		progressDialog.showDelayed(500);
		Utilities.globalQueue.postRunnable(() -> {
			boolean imagesCleared = false;
			long clearedSize = 0;
			boolean allItemsClear = true;
			for (int a = 0; a < 7; a++) {
				if (clearViewData[a] == null || !clearViewData[a].clear) {
					if (clearViewData[a] != null) {
						allItemsClear = false;
					}
					continue;
				}
				int type = -1;
				int documentsMusicType = 0;
				if (a == 0) {
					type = FileLoader.MEDIA_DIR_IMAGE;
					clearedSize += photoSize;
				}
				else if (a == 1) {
					type = FileLoader.MEDIA_DIR_VIDEO;
					clearedSize += videoSize;
				}
				else if (a == 2) {
					type = FileLoader.MEDIA_DIR_DOCUMENT;
					documentsMusicType = 1;
					clearedSize += documentsSize;
				}
				else if (a == 3) {
					type = FileLoader.MEDIA_DIR_DOCUMENT;
					documentsMusicType = 2;
					clearedSize += musicSize;
				}
				else if (a == 4) {
					type = FileLoader.MEDIA_DIR_AUDIO;
					clearedSize += audioSize;
				}
				else if (a == 5) {
					type = 100;
					clearedSize += stickersSize;
				}
				else if (a == 6) {
					clearedSize += cacheSize;
					type = FileLoader.MEDIA_DIR_CACHE;
				}
				if (type == -1) {
					continue;
				}
				File file;
				if (type == 100) {
					file = new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache");
				}
				else {
					file = FileLoader.checkDirectory(type);
				}
				if (file != null) {
					Utilities.clearDir(file.getAbsolutePath(), documentsMusicType, Long.MAX_VALUE, false);
				}
				if (type == FileLoader.MEDIA_DIR_IMAGE || type == FileLoader.MEDIA_DIR_VIDEO) {
					int publicDirectoryType;
					if (type == FileLoader.MEDIA_DIR_IMAGE) {
						publicDirectoryType = FileLoader.MEDIA_DIR_IMAGE_PUBLIC;
					}
					else {
						publicDirectoryType = FileLoader.MEDIA_DIR_VIDEO_PUBLIC;
					}
					file = FileLoader.checkDirectory(publicDirectoryType);

					if (file != null) {
						Utilities.clearDir(file.getAbsolutePath(), documentsMusicType, Long.MAX_VALUE, false);
					}
				}
				if (type == FileLoader.MEDIA_DIR_DOCUMENT) {
					file = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES);
					if (file != null) {
						Utilities.clearDir(file.getAbsolutePath(), documentsMusicType, Long.MAX_VALUE, false);
					}
				}

				if (type == FileLoader.MEDIA_DIR_CACHE) {
					cacheSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), documentsMusicType);
					imagesCleared = true;
				}
				else if (type == FileLoader.MEDIA_DIR_AUDIO) {
					audioSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_AUDIO), documentsMusicType);
				}
				else if (type == FileLoader.MEDIA_DIR_DOCUMENT) {
					if (documentsMusicType == 1) {
						documentsSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), documentsMusicType);
						documentsSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), documentsMusicType);
					}
					else {
						musicSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), documentsMusicType);
						musicSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), documentsMusicType);
					}
				}
				else if (type == FileLoader.MEDIA_DIR_IMAGE) {
					imagesCleared = true;
					photoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), documentsMusicType);
					photoSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC), documentsMusicType);
				}
				else if (type == FileLoader.MEDIA_DIR_VIDEO) {
					videoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), documentsMusicType);
					videoSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO_PUBLIC), documentsMusicType);
				}
				else if (type == 100) {
					imagesCleared = true;
					stickersSize = getDirectorySize(new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache"), documentsMusicType);
				}
			}
			final boolean imagesClearedFinal = imagesCleared;
			totalSize = cacheSize + videoSize + audioSize + photoSize + documentsSize + musicSize + stickersSize;

			File path = Environment.getDataDirectory();
			StatFs stat = new StatFs(path.getPath());
			long blockSize;
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
				blockSize = stat.getBlockSizeLong();
			}
			else {
				blockSize = stat.getBlockSize();
			}
			long availableBlocks;
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
				availableBlocks = stat.getAvailableBlocksLong();
			}
			else {
				availableBlocks = stat.getAvailableBlocks();
			}
			long blocksTotal;
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
				blocksTotal = stat.getBlockCountLong();
			}
			else {
				blocksTotal = stat.getBlockCount();
			}

			totalDeviceSize = blocksTotal * blockSize;
			totalDeviceFreeSize = availableBlocks * blockSize;
			long finalClearedSize = clearedSize;

			if (allItemsClear) {
				FileLoader.getInstance(currentAccount).clearFilePaths();
			}
			FileLoader.getInstance(currentAccount).checkCurrentDownloadsFiles();

			AndroidUtilities.runOnUIThread(() -> {
				if (imagesClearedFinal) {
					ImageLoader.getInstance().clearMemory();
				}
				if (listAdapter != null) {
					updateStorageUsageRow();
				}
				try {
					progressDialog.dismiss();
				}
				catch (Exception e) {
					FileLog.e(e);
				}

				getMediaDataController().ringtoneDataStore.checkRingtoneSoundsLoaded();
				cacheRemovedTooltip.setInfoText(LocaleController.formatString("CacheWasCleared", R.string.CacheWasCleared, AndroidUtilities.formatFileSize(finalClearedSize)));
				cacheRemovedTooltip.showWithAction(0, UndoView.ACTION_CACHE_WAS_CLEARED, null, null);
				MediaDataController.getInstance(currentAccount).checkAllMedia(true);
			});
		});
	}

	@Override
	public View createView(@NonNull Context context) {
		actionBar.setBackButtonImage(R.drawable.ic_back_arrow);
		actionBar.setAllowOverlayTitle(true);
		actionBar.setTitle(LocaleController.getString("StorageUsage", R.string.StorageUsage));
		actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
			@Override
			public void onItemClick(int id) {
				if (id == -1) {
					finishFragment();
				}
			}
		});

		listAdapter = new ListAdapter(context);

		fragmentView = new FrameLayout(context);
		FrameLayout frameLayout = (FrameLayout)fragmentView;
		frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

		listView = new RecyclerListView(context);
		listView.setVerticalScrollBarEnabled(false);
		listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
		frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
		listView.setAdapter(listAdapter);
		listView.setOnItemClickListener((view, position) -> {
			if (getParentActivity() == null) {
				return;
			}
			if (position == migrateOldFolderRow) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
					migrateOldFolder();
				}
			}
			else if (position == databaseRow) {
				clearDatabase();
			}
			else if (position == storageUsageRow) {
				if (totalSize <= 0 || getParentActivity() == null) {
					return;
				}
				bottomSheet = new BottomSheet(getParentActivity(), false) {
					@Override
					protected boolean canDismissWithSwipe() {
						return false;
					}
				};
				bottomSheet.fixNavigationBar();
				bottomSheet.setAllowNestedScroll(true);
				bottomSheet.setApplyBottomPadding(false);
				LinearLayout linearLayout = new LinearLayout(getParentActivity());
				bottomSheetView = linearLayout;
				linearLayout.setOrientation(LinearLayout.VERTICAL);
				StorageDiagramView circleDiagramView = new StorageDiagramView(context);
				linearLayout.addView(circleDiagramView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));
				CheckBoxCell lastCreatedCheckbox = null;
				for (int a = 0; a < 7; a++) {
					long size;
					String name;
					int color;
					if (a == 0) {
						size = photoSize;
						name = LocaleController.getString("LocalPhotoCache", R.string.LocalPhotoCache);
						color = context.getColor(R.color.avatar_tint);
					}
					else if (a == 1) {
						size = videoSize;
						name = LocaleController.getString("LocalVideoCache", R.string.LocalVideoCache);
						color = context.getColor(R.color.orange);
					}
					else if (a == 2) {
						size = documentsSize;
						name = LocaleController.getString("LocalDocumentCache", R.string.LocalDocumentCache);
						color = context.getColor(R.color.online);
					}
					else if (a == 3) {
						size = musicSize;
						name = LocaleController.getString("LocalMusicCache", R.string.LocalMusicCache);
						color = context.getColor(R.color.totals_blue_text);
					}
					else if (a == 4) {
						size = audioSize;
						name = LocaleController.getString("LocalAudioCache", R.string.LocalAudioCache);
						color = context.getColor(R.color.purple);
					}
					else if (a == 5) {
						size = stickersSize;
						name = LocaleController.getString("AnimatedStickers", R.string.AnimatedStickers);
						color = context.getColor(R.color.success_background);
					}
					else {
						size = cacheSize;
						name = LocaleController.getString("LocalCache", R.string.LocalCache);
						color = context.getColor(R.color.color_toggle_active);
					}
					if (size > 0) {
						clearViewData[a] = new StorageDiagramView.ClearViewData(circleDiagramView);
						clearViewData[a].size = size;
						clearViewData[a].color = color;
						CheckBoxCell checkBoxCell = new CheckBoxCell(getParentActivity(), 4, 21);
						lastCreatedCheckbox = checkBoxCell;
						checkBoxCell.setTag(a);
						checkBoxCell.setBackground(Theme.getSelectorDrawable(false));
						linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
						checkBoxCell.setText(name, AndroidUtilities.formatFileSize(size), true, true);
						checkBoxCell.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
						checkBoxCell.setCheckBoxColor(color, context.getColor(R.color.dark_gray), context.getColor(R.color.brand));
						checkBoxCell.setOnClickListener(v -> {
							int enabledCount = 0;
							for (StorageDiagramView.ClearViewData clearViewDatum : clearViewData) {
								if (clearViewDatum != null && clearViewDatum.clear) {
									enabledCount++;
								}
							}
							CheckBoxCell cell = (CheckBoxCell)v;
							int num = (Integer)cell.getTag();
							if (enabledCount == 1 && clearViewData[num].clear) {
								AndroidUtilities.shakeView(((CheckBoxCell)v).getCheckBoxView(), 2, 0);
								return;
							}

							clearViewData[num].setClear(!clearViewData[num].clear);
							cell.setChecked(clearViewData[num].clear, true);
						});
					}
					else {
						clearViewData[a] = null;
					}
				}
				if (lastCreatedCheckbox != null) {
					lastCreatedCheckbox.setNeedDivider(false);
				}
				circleDiagramView.setData(clearViewData);
				BottomSheet.BottomSheetCell cell = new BottomSheet.BottomSheetCell(getParentActivity(), 2);
				cell.setTextAndIcon(LocaleController.getString("ClearMediaCache", R.string.ClearMediaCache), 0);
				actionTextView = cell.getTextView();
				cell.getTextView().setOnClickListener(v -> {
					try {
						if (visibleDialog != null) {
							visibleDialog.dismiss();
						}
					}
					catch (Exception e) {
						FileLog.e(e);
					}
					cleanupFolders();
				});
				linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
				NestedScrollView scrollView = new NestedScrollView(context);
				scrollView.setVerticalScrollBarEnabled(false);
				scrollView.addView(linearLayout);
				bottomSheet.setCustomView(scrollView);
				showDialog(bottomSheet);
			}
		});

		cacheRemovedTooltip = new UndoView(context);
		frameLayout.addView(cacheRemovedTooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

		return fragmentView;
	}

	@RequiresApi(api = Build.VERSION_CODES.R)
	private void migrateOldFolder() {
		FilesMigrationService.checkBottomSheet(this);
	}

	private void clearDatabase() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
		builder.setTitle(LocaleController.getString("LocalDatabaseClearTextTitle", R.string.LocalDatabaseClearTextTitle));
		builder.setMessage(LocaleController.getString("LocalDatabaseClearText", R.string.LocalDatabaseClearText));
		builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
		builder.setPositiveButton(LocaleController.getString("CacheClear", R.string.CacheClear), (dialogInterface, i) -> {
			if (getParentActivity() == null) {
				return;
			}
			progressDialog = new AlertDialog(getParentActivity(), 3);
			progressDialog.setCanCancel(false);
			progressDialog.showDelayed(500);
			MessagesController.getInstance(currentAccount).clearQueryTime();
			getMessagesStorage().clearLocalDatabase();
		});
		AlertDialog alertDialog = builder.create();
		showDialog(alertDialog);
		TextView button = (TextView)alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
		if (button != null) {
			button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (listAdapter != null) {
			listAdapter.notifyDataSetChanged();
		}
	}

	@Override
	public void didReceivedNotification(int id, int account, Object... args) {
		if (id == NotificationCenter.didClearDatabase) {
			try {
				if (progressDialog != null) {
					progressDialog.dismiss();
				}
			}
			catch (Exception e) {
				FileLog.e(e);
			}
			progressDialog = null;
			if (listAdapter != null) {
				databaseSize = MessagesStorage.getInstance(currentAccount).getDatabaseSize();
				listAdapter.notifyDataSetChanged();
			}
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
			return position == migrateOldFolderRow || position == databaseRow || (position == storageUsageRow && (totalSize > 0) && !calculating);
		}

		@Override
		public int getItemCount() {
			return rowCount;
		}

		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			View view;
			switch (viewType) {
				case 0:
					view = new TextSettingsCell(mContext);
					view.setBackgroundColor(mContext.getColor(R.color.background));
					break;
				case 2:
					view = new StorageUsageView(mContext);
					view.setBackgroundColor(mContext.getColor(R.color.background));
					break;
				case 3:
					view = new HeaderCell(mContext);
					view.setBackgroundColor(mContext.getColor(R.color.background));
					break;
				case 4:
					SlideChooseView slideChooseView = new SlideChooseView(mContext);
					view = slideChooseView;
					view.setBackgroundColor(mContext.getColor(R.color.background));

					slideChooseView.setCallback(new SlideChooseView.Callback() {
						@Override
						public void onTouchEnd() {
							// unused
						}

						@Override
						public void onOptionSelected(int index) {
							if (index == 0) {
								SharedConfig.setKeepMedia(3);
							}
							else if (index == 1) {
								SharedConfig.setKeepMedia(0);
							}
							else if (index == 2) {
								SharedConfig.setKeepMedia(1);
							}
							else if (index == 3) {
								SharedConfig.setKeepMedia(2);
							}
						}
					});
					int keepMedia = SharedConfig.keepMedia;
					int index;
					if (keepMedia == 3) {
						index = 0;
					}
					else {
						index = keepMedia + 1;
					}
					slideChooseView.setOptions(index, LocaleController.formatPluralString("Days", 3), LocaleController.formatPluralString("Weeks", 1), LocaleController.formatPluralString("Months", 1), LocaleController.getString("KeepMediaForever", R.string.KeepMediaForever));
					break;
				case 1:
				default:
					view = new TextInfoPrivacyCell(mContext);
					break;
			}
			return new RecyclerListView.Holder(view);
		}

		@Override
		public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
			switch (holder.getItemViewType()) {
				case 0:
					TextSettingsCell textCell = (TextSettingsCell)holder.itemView;
					if (position == databaseRow) {
						textCell.setTextAndValue(LocaleController.getString("ClearLocalDatabase", R.string.ClearLocalDatabase), AndroidUtilities.formatFileSize(databaseSize), false);
					}
					else if (position == migrateOldFolderRow) {
						textCell.setTextAndValue(LocaleController.getString("MigrateOldFolder", R.string.MigrateOldFolder), null, false);
					}
					break;
				case 1:
					TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell)holder.itemView;
					if (position == databaseInfoRow) {
						privacyCell.setText(LocaleController.getString("LocalDatabaseInfo", R.string.LocalDatabaseInfo));
						privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
					}
					else if (position == cacheInfoRow) {
						privacyCell.setText("");
						privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
					}
					else if (position == keepMediaInfoRow) {
						privacyCell.setText(AndroidUtilities.replaceTags(LocaleController.getString("KeepMediaInfo", R.string.KeepMediaInfo)));
						privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
					}
					break;
				case 2:
					StorageUsageView storageUsageView = (StorageUsageView)holder.itemView;
					storageUsageView.setStorageUsage(calculating, databaseSize, totalSize, totalDeviceFreeSize, totalDeviceSize);
					break;
				case 3:
					HeaderCell headerCell = (HeaderCell)holder.itemView;
					if (position == keepMediaHeaderRow) {
						headerCell.setText(LocaleController.getString("KeepMedia", R.string.KeepMedia));
					}
					else if (position == deviseStorageHeaderRow) {
						headerCell.setText(LocaleController.getString("DeviceStorage", R.string.DeviceStorage));
					}
					break;
			}
		}

		@Override
		public int getItemViewType(int i) {
			if (i == databaseInfoRow || i == cacheInfoRow || i == keepMediaInfoRow) {
				return 1;
			}
			if (i == storageUsageRow) {
				return 2;
			}
			if (i == keepMediaHeaderRow || i == deviseStorageHeaderRow) {
				return 3;
			}
			if (i == keepMediaChooserRow) {
				return 4;
			}
			return 0;
		}
	}

	@Override
	public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == 4) {
			boolean allGranted = true;
			for (int grantResult : grantResults) {
				if (grantResult != PackageManager.PERMISSION_GRANTED) {
					allGranted = false;
					break;
				}
			}
			if (allGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && FilesMigrationService.filesMigrationBottomSheet != null) {
				FilesMigrationService.filesMigrationBottomSheet.migrateOldFolder();
			}
		}
	}
}
