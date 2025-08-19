/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2023-2025.
 */
package org.telegram.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.IntEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.database.DataSetObserver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.MetricAffectingSpan
import android.text.style.URLSpan
import android.util.Property
import android.util.SparseArray
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.TextureView
import android.view.VelocityTracker
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewAnimationUtils
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.CustomViewCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.annotation.Keep
import androidx.collection.LongSparseArray
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.util.size
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.GridLayoutManagerFixed
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import org.json.JSONObject
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.DownloadController
import org.telegram.messenger.DownloadController.FileDownloadProgressListener
import org.telegram.messenger.FileLoader
import org.telegram.messenger.FileLog
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.ImageReceiver
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MediaController
import org.telegram.messenger.MessagesController
import org.telegram.messenger.MessagesStorage
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.WebFile
import org.telegram.messenger.browser.Browser
import org.telegram.messenger.messageobject.GroupedMessagePosition
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Chat
import org.telegram.tgnet.TLRPC.FileLocation
import org.telegram.tgnet.TLRPC.PageBlock
import org.telegram.tgnet.TLRPC.PhotoSize
import org.telegram.tgnet.TLRPC.RichText
import org.telegram.tgnet.TLRPC.TLChannelsJoinChannel
import org.telegram.tgnet.TLRPC.TLContactsResolveUsername
import org.telegram.tgnet.TLRPC.TLContactsResolvedPeer
import org.telegram.tgnet.TLRPC.TLDocumentAttributeAudio
import org.telegram.tgnet.TLRPC.TLDocumentAttributeVideo
import org.telegram.tgnet.TLRPC.TLError
import org.telegram.tgnet.TLRPC.TLMessageActionChatAddUser
import org.telegram.tgnet.TLRPC.TLMessageMediaDocument
import org.telegram.tgnet.TLRPC.TLMessagesGetWebPage
import org.telegram.tgnet.TLRPC.TLPage
import org.telegram.tgnet.TLRPC.TLPageBlockAnchor
import org.telegram.tgnet.TLRPC.TLPageBlockAudio
import org.telegram.tgnet.TLRPC.TLPageBlockAuthorDate
import org.telegram.tgnet.TLRPC.TLPageBlockBlockquote
import org.telegram.tgnet.TLRPC.TLPageBlockChannel
import org.telegram.tgnet.TLRPC.TLPageBlockCollage
import org.telegram.tgnet.TLRPC.TLPageBlockCover
import org.telegram.tgnet.TLRPC.TLPageBlockDetails
import org.telegram.tgnet.TLRPC.TLPageBlockDivider
import org.telegram.tgnet.TLRPC.TLPageBlockEmbed
import org.telegram.tgnet.TLRPC.TLPageBlockEmbedPost
import org.telegram.tgnet.TLRPC.TLPageBlockFooter
import org.telegram.tgnet.TLRPC.TLPageBlockHeader
import org.telegram.tgnet.TLRPC.TLPageBlockKicker
import org.telegram.tgnet.TLRPC.TLPageBlockList
import org.telegram.tgnet.TLRPC.TLPageBlockMap
import org.telegram.tgnet.TLRPC.TLPageBlockOrderedList
import org.telegram.tgnet.TLRPC.TLPageBlockParagraph
import org.telegram.tgnet.TLRPC.TLPageBlockPhoto
import org.telegram.tgnet.TLRPC.TLPageBlockPreformatted
import org.telegram.tgnet.TLRPC.TLPageBlockPullquote
import org.telegram.tgnet.TLRPC.TLPageBlockRelatedArticles
import org.telegram.tgnet.TLRPC.TLPageBlockSlideshow
import org.telegram.tgnet.TLRPC.TLPageBlockSubheader
import org.telegram.tgnet.TLRPC.TLPageBlockSubtitle
import org.telegram.tgnet.TLRPC.TLPageBlockTable
import org.telegram.tgnet.TLRPC.TLPageBlockTitle
import org.telegram.tgnet.TLRPC.TLPageBlockUnsupported
import org.telegram.tgnet.TLRPC.TLPageBlockVideo
import org.telegram.tgnet.TLRPC.TLPageListItemBlocks
import org.telegram.tgnet.TLRPC.TLPageListItemText
import org.telegram.tgnet.TLRPC.TLPageListOrderedItemBlocks
import org.telegram.tgnet.TLRPC.TLPageListOrderedItemText
import org.telegram.tgnet.TLRPC.TLPageTableCell
import org.telegram.tgnet.TLRPC.TLPeerUser
import org.telegram.tgnet.TLRPC.TLTextAnchor
import org.telegram.tgnet.TLRPC.TLTextBold
import org.telegram.tgnet.TLRPC.TLTextConcat
import org.telegram.tgnet.TLRPC.TLTextEmail
import org.telegram.tgnet.TLRPC.TLTextEmpty
import org.telegram.tgnet.TLRPC.TLTextFixed
import org.telegram.tgnet.TLRPC.TLTextImage
import org.telegram.tgnet.TLRPC.TLTextItalic
import org.telegram.tgnet.TLRPC.TLTextMarked
import org.telegram.tgnet.TLRPC.TLTextPhone
import org.telegram.tgnet.TLRPC.TLTextPlain
import org.telegram.tgnet.TLRPC.TLTextStrike
import org.telegram.tgnet.TLRPC.TLTextSubscript
import org.telegram.tgnet.TLRPC.TLTextSuperscript
import org.telegram.tgnet.TLRPC.TLTextUnderline
import org.telegram.tgnet.TLRPC.TLTextUrl
import org.telegram.tgnet.TLRPC.TLUpdateNewChannelMessage
import org.telegram.tgnet.TLRPC.TLUser
import org.telegram.tgnet.TLRPC.TLWebPage
import org.telegram.tgnet.TLRPC.TLWebPageNotModified
import org.telegram.tgnet.TLRPC.Updates
import org.telegram.tgnet.TLRPC.WebPage
import org.telegram.tgnet.action
import org.telegram.tgnet.cachedPage
import org.telegram.tgnet.document
import org.telegram.tgnet.entities
import org.telegram.tgnet.lat
import org.telegram.tgnet.lon
import org.telegram.tgnet.media
import org.telegram.tgnet.message
import org.telegram.tgnet.photo
import org.telegram.tgnet.siteName
import org.telegram.tgnet.sizes
import org.telegram.tgnet.thumbs
import org.telegram.tgnet.userId
import org.telegram.tgnet.webpage
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.ActionBarMenuItem.ActionBarMenuItemDelegate
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.BottomSheet.BottomSheetDelegate
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.TextSelectionHelper
import org.telegram.ui.Cells.TextSelectionHelper.ArticleSelectableView
import org.telegram.ui.Cells.TextSelectionHelper.ArticleTextSelectionHelper
import org.telegram.ui.Cells.TextSelectionHelper.IgnoreCopySpannable
import org.telegram.ui.Components.AlertsCreator.processError
import org.telegram.ui.Components.AnchorSpan
import org.telegram.ui.Components.AnimatedArrowDrawable
import org.telegram.ui.Components.AnimationProperties
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.BulletinFactory.Companion.of
import org.telegram.ui.Components.CloseProgressDrawable2
import org.telegram.ui.Components.CombinedDrawable
import org.telegram.ui.Components.ContextProgressView
import org.telegram.ui.Components.CubicBezierInterpolator
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.LayoutHelper.createFrame
import org.telegram.ui.Components.LayoutHelper.createLinear
import org.telegram.ui.Components.LineProgressView
import org.telegram.ui.Components.LinkPath
import org.telegram.ui.Components.LinkSpanDrawable
import org.telegram.ui.Components.LinkSpanDrawable.LinkCollector
import org.telegram.ui.Components.MediaActionDrawable
import org.telegram.ui.Components.RadialProgress2
import org.telegram.ui.Components.RadioButton
import org.telegram.ui.Components.RecyclerListView
import org.telegram.ui.Components.RecyclerListView.OnItemClickListenerExtended
import org.telegram.ui.Components.RecyclerListView.SelectionAdapter
import org.telegram.ui.Components.SeekBar
import org.telegram.ui.Components.SeekBarView
import org.telegram.ui.Components.SeekBarView.SeekBarViewDelegate
import org.telegram.ui.Components.ShareAlert
import org.telegram.ui.Components.StaticLayoutEx
import org.telegram.ui.Components.TableLayout
import org.telegram.ui.Components.TableLayout.TableLayoutDelegate
import org.telegram.ui.Components.TextPaintImageReceiverSpan
import org.telegram.ui.Components.TextPaintMarkSpan
import org.telegram.ui.Components.TextPaintSpan
import org.telegram.ui.Components.TextPaintUrlSpan
import org.telegram.ui.Components.TextPaintWebpageUrlSpan
import org.telegram.ui.Components.TranslateAlert
import org.telegram.ui.Components.TypefaceSpan
import org.telegram.ui.Components.WebPlayerView
import org.telegram.ui.Components.WebPlayerView.WebPlayerViewDelegate
import org.telegram.ui.PhotoViewer.EmptyPhotoViewerProvider
import org.telegram.ui.PhotoViewer.PageBlocksAdapter
import org.telegram.ui.PhotoViewer.PlaceProviderObject
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@SuppressLint("AppCompatCustomView")
class ArticleViewer : NotificationCenterDelegate {
	private val BOTTOM_SHEET_VIEW_TAG = "bottomSheet"
	private val createdWebViews = mutableListOf<BlockEmbedCell>()
	private val fontCells = arrayOfNulls<FontCell>(2)
	private val headerPaint = Paint()
	private val headerProgressPaint = Paint()
	private val interpolator = DecelerateInterpolator(1.5f)
	private val links = LinkCollector()
	private val navigationBarPaint = Paint()
	private val pagesStack = mutableListOf<WebPage>()
	private val statusBarPaint = Paint()
	private var adapter: Array<WebpageAdapter>? = null
	private var allowAnimationIndex = -1
	private var anchorsOffsetMeasuredWidth = 0
	private var animateClear = true
	private var animationEndRunnable: Runnable? = null
	private var animationInProgress = 0
	private var attachedToWindow = false
	private var backButton: ImageView? = null
	private var backDrawable: BackDrawable? = null
	private var backgroundPaint: Paint? = null
	private var checkingForLongPress = false
	private var clearButton: ImageView? = null
	private var collapsed = false
	private var containerView: FrameLayout? = null
	private var currentAccount = 0
	private var currentHeaderHeight = 0
	private var currentPlayingVideo: WebPlayerView? = null
	private var customView: View? = null
	private var customViewCallback: CustomViewCallback? = null
	private var deleteView: TextView? = null
	private var drawBlockSelection = false
	private var fullscreenAspectRatioView: AspectRatioFrameLayout? = null
	private var fullscreenTextureView: TextureView? = null
	private var fullscreenVideoContainer: FrameLayout? = null
	private var fullscreenedVideo: WebPlayerView? = null
	private var hasCutout = false
	private var headerView: FrameLayout? = null
	private var ignoreOnTextChange = false
	private var keyboardVisible = false
	private var lastBlockNum = 1
	private var lastInsets: Any? = null
	private var lastReqId = 0
	private var layerShadowDrawable: Drawable? = null
	private var layoutManager: Array<LinearLayoutManager>? = null
	private var lineProgressTickRunnable: Runnable? = null
	private var lineProgressView: LineProgressView? = null
	private var linkSheet: BottomSheet? = null
	private var listView: Array<RecyclerListView>? = null
	private var loadedChannel: Chat? = null
	private var loadingChannel = false
	private var menuButton: ActionBarMenuItem? = null
	private var menuContainer: FrameLayout? = null
	private var openUrlReqId = 0
	private var pageSwitchAnimation: AnimatorSet? = null
	private var parentActivity: Activity? = null
	private var parentFragment: BaseFragment? = null
	private var pendingCheckForLongPress: CheckForLongPress? = null
	private var pendingCheckForTap: CheckForTap? = null
	private var popupLayout: ActionBarPopupWindowLayout? = null
	private var popupRect: Rect? = null
	private var popupWindow: ActionBarPopupWindow? = null
	private var pressCount = 0
	private var pressedLayoutY = 0
	private var pressedLink: LinkSpanDrawable<TextPaintUrlSpan>? = null
	private var pressedLinkOwnerLayout: DrawingText? = null
	private var pressedLinkOwnerView: View? = null
	private var previewsReqId = 0
	private var progressView: ContextProgressView? = null
	private var progressViewAnimation: AnimatorSet? = null
	private var runAfterKeyboardClose: AnimatorSet? = null
	private var scrimPaint: Paint? = null
	private var searchContainer: FrameLayout? = null
	private var searchCountText: SimpleTextView? = null
	private var searchDownButton: ImageView? = null
	private var searchField: EditTextBoldCursor? = null
	private var searchPanel: FrameLayout? = null
	private var searchShadow: View? = null
	private var searchUpButton: ImageView? = null
	private var selectedFont = 0
	private var slideDotBigDrawable: Drawable? = null
	private var slideDotDrawable: Drawable? = null
	private var titleTextView: SimpleTextView? = null
	private var transitionAnimationStartTime: Long = 0
	private var visibleDialog: Dialog? = null
	private var windowLayoutParams: WindowManager.LayoutParams? = null
	private var windowView: WindowView? = null
	var pinchToZoomHelper: PinchToZoomHelper? = null
	var textSelectionHelper: ArticleTextSelectionHelper? = null
	var textSelectionHelperBottomSheet: ArticleTextSelectionHelper? = null

	var isVisible: Boolean = false
		private set

	private class TLPageBlockRelatedArticlesChild : PageBlock() {
		var parent: TLPageBlockRelatedArticles? = null
		var num = 0
	}

	private class TLPageBlockRelatedArticlesShadow : PageBlock() {
		var parent: TLPageBlockRelatedArticles? = null
	}

	private class TLPageBlockDetailsChild : PageBlock() {
		var parent: PageBlock? = null
		var block: PageBlock? = null
	}

	private class TLPageBlockDetailsBottom : PageBlock() {
		private val parent: TLPageBlockDetails? = null
	}

	private inner class TLPageBlockListParent : PageBlock() {
		var pageBlockList: TLPageBlockList? = null
		val items = mutableListOf<TLPageBlockListItem>()
		var maxNumWidth = 0
		var lastMaxNumCalcWidth = 0
		var lastFontSize = 0
		// var level = 0
	}

	private inner class TLPageBlockListItem : PageBlock() {
		var parent: TLPageBlockListParent? = null
		var blockItem: PageBlock? = null
		var textItem: RichText? = null
		var num: String? = null
		var numLayout: DrawingText? = null
		var index: Int = Int.MAX_VALUE
	}

	private inner class TLPageBlockOrderedListParent : PageBlock() {
		var pageBlockOrderedList: TLPageBlockOrderedList? = null
		val items: ArrayList<TLPageBlockOrderedListItem> = ArrayList()
		var maxNumWidth: Int = 0
		var lastMaxNumCalcWidth: Int = 0
		var lastFontSize: Int = 0
	}

	private inner class TLPageBlockOrderedListItem : PageBlock() {
		var parent: TLPageBlockOrderedListParent? = null
		var blockItem: PageBlock? = null
		var textItem: RichText? = null
		var num: String? = null
		var numLayout: DrawingText? = null
		var index: Int = Int.MAX_VALUE
	}

	private class TLPageBlockEmbedPostCaption : TLPageBlockEmbedPost() {
		var parent: TLPageBlockEmbedPost? = null
	}

	inner class DrawingText : TextSelectionHelper.TextLayoutBlock {
		@JvmField
		var latestParentView: View? = null

		var textLayout: StaticLayout? = null
		var textPath: LinkPath? = null
		var markPath: LinkPath? = null
		var searchPath: LinkPath? = null
		var searchIndex: Int = -1
		var parentBlock: PageBlock? = null
		var parentText: Any? = null
		private var x = 0
		private var y = 0
		private var row: Int = 0
		private var prefix: CharSequence? = null

		fun draw(canvas: Canvas, view: View) {
			latestParentView = view

			if (!searchResults.isNullOrEmpty()) {
				val result = searchResults!![currentSearchIndex]

				if (result.block === parentBlock && (result.text === parentText || result.text is String && parentText == null)) {
					if (searchIndex != result.index) {
						searchPath = LinkPath(true)
						searchPath?.setAllowReset(false)
						searchPath?.setCurrentLayout(textLayout, result.index, 0f)
						searchPath?.setBaselineShift(0)

						textLayout?.getSelectionPath(result.index, result.index + searchText!!.length, searchPath)

						searchPath?.setAllowReset(true)
					}
				}
				else {
					searchIndex = -1
					searchPath = null
				}
			}
			else {
				searchIndex = -1
				searchPath = null
			}

			if (searchPath != null) {
				canvas.drawPath(searchPath!!, webpageSearchPaint!!)
			}

			if (textPath != null) {
				canvas.drawPath(textPath!!, webpageUrlPaint!!)
			}

			if (markPath != null) {
				canvas.drawPath(markPath!!, webpageMarkPaint!!)
			}

			if (links.draw(canvas, this)) {
				view.invalidate()
			}

			if (pressedLinkOwnerLayout === this && pressedLink == null && drawBlockSelection) {
				val width: Float
				val x: Float

				if (lineCount == 1) {
					width = getLineWidth(0)
					x = getLineLeft(0)
				}
				else {
					width = this.width.toFloat()
					x = 0f
				}

				canvas.drawRect(-AndroidUtilities.dp(2f) + x, 0f, x + width + AndroidUtilities.dp(2f), height.toFloat(), urlPaint!!)
			}

			textLayout?.draw(canvas)
		}

		val text: CharSequence?
			get() = textLayout?.text

		val lineCount: Int
			get() = textLayout?.lineCount ?: 0

		fun getLineAscent(line: Int): Int {
			return textLayout?.getLineAscent(line) ?: 0
		}

		fun getLineLeft(line: Int): Float {
			return textLayout?.getLineLeft(line) ?: 0f
		}

		fun getLineWidth(line: Int): Float {
			return textLayout?.getLineWidth(line) ?: 0f
		}

		val height: Int
			get() = textLayout?.height ?: 0

		val width: Int
			get() = textLayout?.width ?: 0

		override fun getLayout(): StaticLayout? {
			return textLayout
		}

		override fun getX(): Int {
			return x
		}

		fun setX(x: Int) {
			this.x = x
		}

		override fun getY(): Int {
			return y
		}

		fun setY(y: Int) {
			this.y = y
		}

		override fun getRow(): Int {
			return row
		}

		fun setRow(row: Int) {
			this.row = row
		}

		override fun getPrefix(): CharSequence? {
			return prefix
		}

		fun setPrefix(prefix: CharSequence?) {
			this.prefix = prefix
		}
	}

	private inner class TextSizeCell(context: Context) : FrameLayout(context) {
		private val sizeBar: SeekBarView
		private val startFontSize = 12
		private val endFontSize = 30
		private var lastWidth = 0

		private val textPaint: TextPaint

		init {
			setWillNotDraw(false)

			textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
			textPaint.textSize = AndroidUtilities.dp(16f).toFloat()
			textPaint.setTypeface(Theme.TYPEFACE_DEFAULT)

			sizeBar = SeekBarView(context)
			sizeBar.setReportChanges(true)
			sizeBar.setSeparatorsCount(endFontSize - startFontSize + 1)

			sizeBar.setDelegate(object : SeekBarViewDelegate {
				override fun onSeekBarDrag(stop: Boolean, progress: Float) {
					val fontSize = Math.round(startFontSize + (endFontSize - startFontSize) * progress)

					if (fontSize != SharedConfig.ivFontSize) {
						SharedConfig.ivFontSize = fontSize

						MessagesController.getGlobalMainSettings().edit {
							putInt("iv_font_size", SharedConfig.ivFontSize)
						}

						adapter?.get(0)?.searchTextOffset?.clear()
						updatePaintSize()
						invalidate()
					}
				}

				override fun onSeekBarPressed(pressed: Boolean) {
					// unused
				}

				override fun getContentDescription(): CharSequence {
					return Math.round(startFontSize + (endFontSize - startFontSize) * sizeBar.progress).toString()
				}

				override fun getStepsCount(): Int {
					return endFontSize - startFontSize
				}
			})

			addView(sizeBar, createFrame(LayoutHelper.MATCH_PARENT, 38f, Gravity.LEFT or Gravity.TOP, 5f, 5f, 39f, 0f))
		}

		override fun onDraw(canvas: Canvas) {
			textPaint.color = context.getColor(R.color.brand)
			canvas.drawText("" + SharedConfig.ivFontSize, (measuredWidth - AndroidUtilities.dp(39f)).toFloat(), AndroidUtilities.dp(28f).toFloat(), textPaint)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)

			val w = MeasureSpec.getSize(widthMeasureSpec)

			if (lastWidth != w) {
				sizeBar.progress = (SharedConfig.ivFontSize - startFontSize) / (endFontSize - startFontSize).toFloat()
				lastWidth = w
			}
		}

		override fun invalidate() {
			super.invalidate()
			sizeBar.invalidate()
		}
	}

	class FontCell(context: Context) : FrameLayout(context) {
		private val textView: TextView
		private val radioButton: RadioButton

		init {
			background = Theme.createSelectorDrawable(context.getColor(R.color.light_background), 2)

			radioButton = RadioButton(context)
			radioButton.setSize(AndroidUtilities.dp(20f))
			radioButton.setColor(Theme.getColor(Theme.key_dialogRadioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked))

			addView(radioButton, createFrame(22, 22f, (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 0 else 22).toFloat(), 13f, (if (LocaleController.isRTL) 22 else 0).toFloat(), 0f))

			textView = TextView(context)
			textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
			textView.setLines(1)
			textView.maxLines = 1
			textView.isSingleLine = true
			textView.gravity = (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL

			addView(textView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat(), (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.TOP, (if (LocaleController.isRTL) 17 else 17 + 45).toFloat(), 0f, (if (LocaleController.isRTL) 17 + 45 else 17).toFloat(), 0f))
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48f), MeasureSpec.EXACTLY))
		}

		fun select(value: Boolean, animated: Boolean) {
			radioButton.setChecked(value, animated)
		}

		fun setTextAndTypeface(text: String?, typeface: Typeface?) {
			textView.text = text
			textView.typeface = typeface
			contentDescription = text
			invalidate()
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.className = RadioButton::class.java.name
			info.isChecked = radioButton.isChecked
			info.isCheckable = true
		}
	}

	private inner class CheckForTap : Runnable {
		override fun run() {
			if (pendingCheckForLongPress == null) {
				pendingCheckForLongPress = CheckForLongPress()
			}

			pendingCheckForLongPress?.currentPressCount = ++pressCount

			windowView?.postDelayed(pendingCheckForLongPress, (ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout()).toLong())
		}
	}

	private var closeAnimationInProgress = false

	inner class WindowView(context: Context) : FrameLayout(context) {
		private val attachRunnable: Runnable? = null
		private val blackPaint = Paint()
		private val selfLayout = false
		private var alpha = 0f
		private var bHeight = 0
		private var bWidth = 0
		private var bX = 0
		private var bY = 0
		private var innerTranslationX = 0f
		private var maybeStartTracking = false
		private var startedTracking = false
		private var startedTrackingPointerId = 0
		private var startedTrackingX = 0
		private var startedTrackingY = 0
		private var tracker: VelocityTracker? = null
		var movingPage: Boolean = false
		var startMovingHeaderHeight: Int = 0

		override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
			val oldInsets = lastInsets as WindowInsets?
			lastInsets = insets

			if (oldInsets == null || oldInsets.toString() != insets.toString()) {
				windowView?.requestLayout()
			}

			if (Build.VERSION.SDK_INT >= 28 && parentActivity != null) {
				val cutout = parentActivity?.window?.decorView?.rootWindowInsets?.displayCutout

				if (cutout != null) {
					val rects = cutout.boundingRects

					if (!rects.isEmpty()) {
						hasCutout = rects[0].height() != 0
					}
				}
			}

			return super.dispatchApplyWindowInsets(insets)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			var widthSize = MeasureSpec.getSize(widthMeasureSpec)
			var heightSize = MeasureSpec.getSize(heightMeasureSpec)

			if (lastInsets != null) {
				setMeasuredDimension(widthSize, heightSize)

				val insets = lastInsets as WindowInsets

				if (AndroidUtilities.incorrectDisplaySizeFix) {
					if (heightSize > AndroidUtilities.displaySize.y) {
						heightSize = AndroidUtilities.displaySize.y
					}

					heightSize += AndroidUtilities.statusBarHeight
				}

				heightSize -= insets.systemWindowInsetBottom
				widthSize -= insets.systemWindowInsetRight + insets.systemWindowInsetLeft

				if (insets.systemWindowInsetRight != 0) {
					bWidth = insets.systemWindowInsetRight
					bHeight = heightSize
				}
				else if (insets.systemWindowInsetLeft != 0) {
					bWidth = insets.systemWindowInsetLeft
					bHeight = heightSize
				}
				else {
					bWidth = widthSize
					bHeight = insets.stableInsetBottom
				}

				heightSize -= insets.systemWindowInsetTop
			}
			else {
				setMeasuredDimension(widthSize, heightSize)
			}

			menuButton?.setAdditionalYOffset(-(currentHeaderHeight - AndroidUtilities.dp(56f)) / 2)

			keyboardVisible = heightSize < AndroidUtilities.displaySize.y - AndroidUtilities.dp(100f)

			containerView?.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY))

			fullscreenVideoContainer?.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY))
		}

		override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
			if (pinchToZoomHelper?.isInOverlayMode == true) {
				ev.offsetLocation(-containerView!!.x, -containerView!!.y)
				return pinchToZoomHelper?.onTouchEvent(ev) == true
			}

			val selectionOverlay = textSelectionHelper?.getOverlayView(context)
			val textSelectionEv = MotionEvent.obtain(ev)
			textSelectionEv.offsetLocation(-containerView!!.x, -containerView!!.y)

			if (textSelectionHelper!!.isSelectionMode && textSelectionHelper!!.getOverlayView(context).onTouchEvent(textSelectionEv)) {
				return true
			}

			if (selectionOverlay?.checkOnTap(ev) == true) {
				ev.action = MotionEvent.ACTION_CANCEL
			}

			if (ev.action == MotionEvent.ACTION_DOWN && textSelectionHelper!!.isSelectionMode && (ev.y < containerView!!.top || ev.y > containerView!!.bottom)) {
				return if (textSelectionHelper!!.getOverlayView(context).onTouchEvent(textSelectionEv)) {
					super.dispatchTouchEvent(ev)
				}
				else {
					true
				}
			}

			return super.dispatchTouchEvent(ev)
		}

		override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
			if (selfLayout) {
				return
			}

			val width = right - left

			if (anchorsOffsetMeasuredWidth != width) {
				for (i in listView!!.indices) {
					adapter?.get(i)?.anchorsOffset?.entries?.forEach {
						it.setValue(-1)
					}
				}

				anchorsOffsetMeasuredWidth = width
			}

			val x: Int
			var y = 0

			if (lastInsets != null) {
				val insets = lastInsets as WindowInsets
				x = insets.systemWindowInsetLeft

				if (insets.systemWindowInsetRight != 0) {
					bX = width - bWidth
					bY = 0
				}
				else if (insets.systemWindowInsetLeft != 0) {
					bX = 0
					bY = 0
				}
				else {
					bX = 0
					bY = bottom - top - bHeight
				}

				y += insets.systemWindowInsetTop
			}
			else {
				x = 0
			}

			containerView?.layout(x, y, x + containerView!!.measuredWidth, y + containerView!!.measuredHeight)
			fullscreenVideoContainer?.layout(x, y, x + fullscreenVideoContainer!!.measuredWidth, y + fullscreenVideoContainer!!.measuredHeight)

			runAfterKeyboardClose?.start()
			runAfterKeyboardClose = null
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			attachedToWindow = true
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()
			attachedToWindow = false
		}

		override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
			handleTouchEvent(null)
			super.requestDisallowInterceptTouchEvent(disallowIntercept)
		}

		override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
			return !collapsed && (handleTouchEvent(ev) || super.onInterceptTouchEvent(ev))
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return !collapsed && (handleTouchEvent(event) || super.onTouchEvent(event))
		}

		@Keep
		fun setInnerTranslationX(value: Float) {
			innerTranslationX = value
			(parentActivity as? LaunchActivity)?.drawerLayoutContainer?.setAllowDrawContent(!isVisible || alpha != 1.0f || innerTranslationX != 0f)
			invalidate()
		}

		override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
			val width = measuredWidth
			val translationX = innerTranslationX.toInt()

			val restoreCount = canvas.save()
			canvas.clipRect(translationX, 0, width, height)
			val result = super.drawChild(canvas, child, drawingTime)
			canvas.restoreToCount(restoreCount)

			if (translationX != 0 && child === containerView) {
				var opacity = min(0.8, ((width - translationX) / width.toFloat()).toDouble()).toFloat()

				if (opacity < 0) {
					opacity = 0f
				}

				scrimPaint?.color = (((-0x67000000 and -0x1000000) ushr 24) * opacity).toInt() shl 24
				canvas.drawRect(0f, 0f, translationX.toFloat(), height.toFloat(), scrimPaint!!)

				val alpha = max(0.0, min(((width - translationX) / AndroidUtilities.dp(20f).toFloat()).toDouble(), 1.0)).toFloat()

				layerShadowDrawable?.setBounds(translationX - layerShadowDrawable!!.intrinsicWidth, child.getTop(), translationX, child.getBottom())
				layerShadowDrawable?.alpha = (0xff * alpha).toInt()
				layerShadowDrawable?.draw(canvas)
			}

			return result
		}

		@Keep
		fun getInnerTranslationX(): Float {
			return innerTranslationX
		}

		fun prepareForMoving(ev: MotionEvent) {
			maybeStartTracking = false
			startedTracking = true
			startedTrackingX = ev.x.toInt()

			if (pagesStack.size > 1) {
				movingPage = true
				startMovingHeaderHeight = currentHeaderHeight

				listView!![1].visibility = VISIBLE
				listView!![1].alpha = 1.0f
				listView!![1].translationX = 0.0f
				listView!![0].setBackgroundColor(backgroundPaint!!.color)

				updateInterfaceForCurrentPage(pagesStack[pagesStack.size - 2], true, -1)
			}
			else {
				movingPage = false
			}

			cancelCheckLongPress()
		}

		fun handleTouchEvent(event: MotionEvent?): Boolean {
			if (pageSwitchAnimation == null && !closeAnimationInProgress && fullscreenVideoContainer!!.visibility != VISIBLE && !textSelectionHelper!!.isSelectionMode) {
				if (event != null && event.action == MotionEvent.ACTION_DOWN && !startedTracking && !maybeStartTracking) {
					startedTrackingPointerId = event.getPointerId(0)
					maybeStartTracking = true
					startedTrackingX = event.x.toInt()
					startedTrackingY = event.y.toInt()

					tracker?.clear()
				}
				else if (event != null && event.action == MotionEvent.ACTION_MOVE && event.getPointerId(0) == startedTrackingPointerId) {
					if (tracker == null) {
						tracker = VelocityTracker.obtain()
					}

					val dx = max(0.0, (event.x - startedTrackingX).toInt().toDouble()).toInt()
					val dy = abs((event.y.toInt() - startedTrackingY).toDouble()).toInt()

					tracker?.addMovement(event)

					if (maybeStartTracking && !startedTracking && dx >= AndroidUtilities.getPixelsInCM(0.4f, true) && abs(dx.toDouble()) / 3 > dy) {
						prepareForMoving(event)
					}
					else if (startedTracking) {
						pressedLinkOwnerLayout = null
						pressedLinkOwnerView = null

						if (movingPage) {
							listView!![0].translationX = dx.toFloat()
						}
						else {
							containerView!!.translationX = dx.toFloat()
							setInnerTranslationX(dx.toFloat())
						}
					}
				}
				else if (event != null && event.getPointerId(0) == startedTrackingPointerId && (event.action == MotionEvent.ACTION_CANCEL || event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_POINTER_UP)) {
					if (tracker == null) {
						tracker = VelocityTracker.obtain()
					}

					tracker?.computeCurrentVelocity(1000)
					val velX = tracker!!.xVelocity
					val velY = tracker!!.yVelocity

					if (!startedTracking && velX >= 3500 && velX > abs(velY.toDouble())) {
						prepareForMoving(event)
					}

					if (startedTracking) {
						val movingView: View = if (movingPage) listView!![0] else containerView!!
						val x = movingView.x
						val backAnimation = x < movingView.measuredWidth / 3.0f && (velX < 3500 || velX < velY)
						val distToMove: Float
						val animatorSet = AnimatorSet()

						if (!backAnimation) {
							distToMove = movingView.measuredWidth - x

							if (movingPage) {
								animatorSet.playTogether(ObjectAnimator.ofFloat(listView!![0], TRANSLATION_X, movingView.measuredWidth.toFloat()))
							}
							else {
								animatorSet.playTogether(ObjectAnimator.ofFloat(containerView, TRANSLATION_X, movingView.measuredWidth.toFloat()), ObjectAnimator.ofFloat(this, ARTICLE_VIEWER_INNER_TRANSLATION_X, movingView.measuredWidth.toFloat()))
							}
						}
						else {
							distToMove = x

							if (movingPage) {
								animatorSet.playTogether(ObjectAnimator.ofFloat(listView!![0], TRANSLATION_X, 0f))
							}
							else {
								animatorSet.playTogether(ObjectAnimator.ofFloat(containerView, TRANSLATION_X, 0f), ObjectAnimator.ofFloat(this, ARTICLE_VIEWER_INNER_TRANSLATION_X, 0.0f))
							}
						}

						animatorSet.setDuration(max((200.0f / movingView.measuredWidth * distToMove).toInt().toDouble(), 50.0).toLong())

						animatorSet.addListener(object : AnimatorListenerAdapter() {
							override fun onAnimationEnd(animator: Animator) {
								if (movingPage) {
									listView?.get(0)?.background = null

									if (!backAnimation) {
										val adapter = adapter

										if (adapter != null) {
											val adapterToUpdate = adapter[1]
											adapter[1] = adapter[0]
											adapter[0] = adapterToUpdate

											val listToUpdate = listView!![1]
											listView!![1] = listView!![0]
											listView!![0] = listToUpdate

											val layoutManagerToUpdate = layoutManager!![1]
											layoutManager!![1] = layoutManager!![0]
											layoutManager!![0] = layoutManagerToUpdate

											pagesStack.removeAt(pagesStack.size - 1)

											textSelectionHelper?.setParentView(listView!![0])
											textSelectionHelper?.layoutManager = layoutManager!![0]
											titleTextView?.setText(adapter[0].currentPage!!.siteName ?: "")
											textSelectionHelper?.clear(true)
											headerView?.invalidate()
										}
									}

									listView?.get(1)?.visibility = GONE

									headerView?.invalidate()
								}
								else {
									if (!backAnimation) {
										saveCurrentPagePosition()
										onClosed()
									}
								}

								movingPage = false
								startedTracking = false
								closeAnimationInProgress = false
							}
						})

						animatorSet.start()
						closeAnimationInProgress = true
					}
					else {
						maybeStartTracking = false
						startedTracking = false
						movingPage = false
					}

					tracker?.recycle()
					tracker = null
				}
				else if (event == null) {
					maybeStartTracking = false
					startedTracking = false
					movingPage = false

					tracker?.recycle()
					tracker = null

					if (textSelectionHelper != null && !textSelectionHelper!!.isSelectionMode) {
						textSelectionHelper!!.clear()
					}
				}

				return startedTracking
			}

			return false
		}

		override fun dispatchDraw(canvas: Canvas) {
			super.dispatchDraw(canvas)
			if (lastInsets == null) {
				if (bWidth != 0 && bHeight != 0) {
					blackPaint.alpha = (255 * windowView!!.getAlpha()).toInt()

					if (bX == 0 && bY == 0) {
						canvas.drawRect(bX.toFloat(), bY.toFloat(), (bX + bWidth).toFloat(), (bY + bHeight).toFloat(), blackPaint)
					}
					else {
						canvas.drawRect(bX - translationX, bY.toFloat(), bX + bWidth - translationX, (bY + bHeight).toFloat(), blackPaint)
					}
				}
			}
		}

		override fun onDraw(canvas: Canvas) {
			val w = measuredWidth
			val h = measuredHeight

			canvas.drawRect(innerTranslationX, 0f, w.toFloat(), h.toFloat(), backgroundPaint!!)

			if (lastInsets != null) {
				val insets = lastInsets as WindowInsets
				canvas.drawRect(innerTranslationX, 0f, w.toFloat(), insets.systemWindowInsetTop.toFloat(), statusBarPaint)

				if (hasCutout) {
					val left = insets.systemWindowInsetLeft

					if (left != 0) {
						canvas.drawRect(0f, 0f, left.toFloat(), h.toFloat(), statusBarPaint)
					}

					val right = insets.systemWindowInsetRight

					if (right != 0) {
						canvas.drawRect((w - right).toFloat(), 0f, w.toFloat(), h.toFloat(), statusBarPaint)
					}
				}

				canvas.drawRect(0f, (h - insets.stableInsetBottom).toFloat(), w.toFloat(), h.toFloat(), navigationBarPaint)
			}
		}

		@Keep
		override fun setAlpha(value: Float) {
			backgroundPaint!!.alpha = (255 * value).toInt()
			statusBarPaint.alpha = (255 * value).toInt()
			alpha = value

			(parentActivity as? LaunchActivity)?.drawerLayoutContainer?.setAllowDrawContent(!isVisible || alpha != 1.0f || innerTranslationX != 0f)

			invalidate()
		}

		@Keep
		override fun getAlpha(): Float {
			return alpha
		}

		override fun dispatchKeyEventPreIme(event: KeyEvent?): Boolean {
			if (event != null && event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
				if (searchField?.isFocused == true) {
					searchField?.clearFocus()
					AndroidUtilities.hideKeyboard(searchField)
				}
				else {
					close(true, false)
				}

				return true
			}

			return super.dispatchKeyEventPreIme(event)
		}
	}

	internal inner class CheckForLongPress : Runnable {
		var currentPressCount: Int = 0

		override fun run() {
			if (checkingForLongPress && windowView != null) {
				checkingForLongPress = false

				if (pressedLink != null) {
					windowView?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
					showCopyPopup(pressedLink!!.span.url)

					pressedLink = null
					pressedLinkOwnerLayout = null

					pressedLinkOwnerView?.invalidate()
				}
				else if (pressedLinkOwnerView != null && textSelectionHelper!!.isSelectable(pressedLinkOwnerView)) {
					if (pressedLinkOwnerView!!.tag != null && pressedLinkOwnerView!!.tag === BOTTOM_SHEET_VIEW_TAG && textSelectionHelperBottomSheet != null) {
						textSelectionHelperBottomSheet!!.trySelect()
					}
					else {
						textSelectionHelper!!.trySelect()
					}
					if (textSelectionHelper!!.isSelectionMode) {
						windowView!!.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
					}
				}
				else if (pressedLinkOwnerLayout != null && pressedLinkOwnerView != null) {
					windowView!!.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

					val location = IntArray(2)
					pressedLinkOwnerView!!.getLocationInWindow(location)
					var y = location[1] + pressedLayoutY - AndroidUtilities.dp(54f)

					if (y < 0) {
						y = 0
					}

					pressedLinkOwnerView!!.invalidate()
					drawBlockSelection = true
					showPopup(pressedLinkOwnerView!!, Gravity.TOP, 0, y)
					listView!![0].isLayoutFrozen = true
					listView!![0].isLayoutFrozen = false
				}
			}
		}
	}

	private fun createPaint(update: Boolean) {
		if (quoteLinePaint == null) {
			quoteLinePaint = Paint()

			preformattedBackgroundPaint = Paint()

			tableLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
			tableLinePaint!!.style = Paint.Style.STROKE
			tableLinePaint!!.strokeWidth = AndroidUtilities.dp(1f).toFloat()

			tableHalfLinePaint = Paint()
			tableHalfLinePaint!!.style = Paint.Style.STROKE
			tableHalfLinePaint!!.strokeWidth = AndroidUtilities.dp(1f) / 2.0f

			tableHeaderPaint = Paint()
			tableStripPaint = Paint()

			urlPaint = Paint()
			webpageUrlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
			webpageSearchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
			photoBackgroundPaint = Paint()
			dividerPaint = Paint()
			webpageMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		}
		else if (!update) {
			return
		}

		val color2 = parentActivity!!.getColor(R.color.background)
		val lightness = (0.2126f * Color.red(color2) + 0.7152f * Color.green(color2) + 0.0722f * Color.blue(color2)) / 255.0f
		webpageSearchPaint!!.color = if (lightness <= 0.705f) -0x2e67d2 else -0x1997
		webpageUrlPaint!!.color = Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection) and 0x33ffffff
		webpageUrlPaint!!.setPathEffect(LinkPath.getRoundedEffect())
		urlPaint!!.color = Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection) and 0x33ffffff
		urlPaint!!.setPathEffect(LinkPath.getRoundedEffect())
		tableHalfLinePaint!!.color = Theme.getColor(Theme.key_windowBackgroundWhiteInputField)
		tableLinePaint!!.color = Theme.getColor(Theme.key_windowBackgroundWhiteInputField)

		photoBackgroundPaint!!.color = 0x0f000000
		webpageMarkPaint!!.color = Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection) and 0x33ffffff
		webpageMarkPaint!!.setPathEffect(LinkPath.getRoundedEffect())

		var color = Theme.getColor(Theme.key_switchTrack)
		var r = Color.red(color)
		var g = Color.green(color)
		var b = Color.blue(color)
		tableStripPaint!!.color = Color.argb(20, r, g, b)
		tableHeaderPaint!!.color = Color.argb(34, r, g, b)

		color = Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection)
		r = Color.red(color)
		g = Color.green(color)
		b = Color.blue(color)
		preformattedBackgroundPaint!!.color = Color.argb(20, r, g, b)

		quoteLinePaint?.color = Theme.getColor(Theme.key_chat_inReplyLine)
	}

	private fun showCopyPopup(urlFinal: String) {
		val parentActivity = parentActivity ?: return

		linkSheet?.dismiss()
		linkSheet = null

		val builder = BottomSheet.Builder(parentActivity)
		builder.setTitle(urlFinal)

		builder.setItems(arrayOf<CharSequence>(parentActivity.getString(R.string.Open), parentActivity.getString(R.string.Copy))) { _, which ->
			if (which == 0) {
				val index: Int

				if ((urlFinal.lastIndexOf('#').also { index = it }) != -1) {
					val webPageUrl = if (!TextUtils.isEmpty(adapter!![0].currentPage?.cachedPage?.url)) {
						adapter!![0].currentPage?.cachedPage?.url?.lowercase()
					}
					else {
						adapter!![0].currentPage?.url?.lowercase()
					} ?: ""

					val anchor = try {
						URLDecoder.decode(urlFinal.substring(index + 1), "UTF-8")
					}
					catch (ignore: Exception) {
						""
					}

					if (urlFinal.lowercase().contains(webPageUrl)) {
						if (TextUtils.isEmpty(anchor)) {
							layoutManager!![0].scrollToPositionWithOffset(0, 0)
							checkScrollAnimated()
						}
						else {
							scrollToAnchor(anchor)
						}

						return@setItems
					}
				}

				Browser.openUrl(parentActivity, urlFinal)
			}
			else if (which == 1) {
				var url = urlFinal
				if (url.startsWith("mailto:")) {
					url = url.substring(7)
				}
				else if (url.startsWith("tel:")) {
					url = url.substring(4)
				}
				AndroidUtilities.addToClipboard(url)
			}
		}
		builder.setOnPreDismissListener { di: DialogInterface? -> links.clear() }
		val sheet = builder.create()
		showDialog(sheet)
	}

	private fun showPopup(parent: View, gravity: Int, x: Int, y: Int) {
		if (popupWindow != null && popupWindow!!.isShowing) {
			popupWindow!!.dismiss()
			return
		}

		if (popupLayout == null) {
			popupRect = Rect()
			popupLayout = ActionBarPopupWindowLayout(parentActivity!!)
			popupLayout!!.setPadding(AndroidUtilities.dp(1f), AndroidUtilities.dp(1f), AndroidUtilities.dp(1f), AndroidUtilities.dp(1f))
			popupLayout!!.setBackgroundDrawable(parentActivity!!.resources.getDrawable(R.drawable.menu_copy))
			popupLayout!!.setAnimationEnabled(false)
			popupLayout!!.setOnTouchListener { v: View, event: MotionEvent ->
				if (event.actionMasked == MotionEvent.ACTION_DOWN) {
					if (popupWindow != null && popupWindow!!.isShowing) {
						v.getHitRect(popupRect)
						if (!popupRect!!.contains(event.x.toInt(), event.y.toInt())) {
							popupWindow!!.dismiss()
						}
					}
				}
				false
			}
			popupLayout!!.setDispatchKeyEventListener { keyEvent: KeyEvent ->
				if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK && keyEvent.repeatCount == 0 && popupWindow != null && popupWindow!!.isShowing) {
					popupWindow!!.dismiss()
				}
			}
			popupLayout!!.setShownFromBottom(false)

			deleteView = TextView(parentActivity)
			deleteView!!.background = Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2)
			deleteView!!.gravity = Gravity.CENTER_VERTICAL
			deleteView!!.setPadding(AndroidUtilities.dp(20f), 0, AndroidUtilities.dp(20f), 0)
			deleteView!!.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
			deleteView!!.setTypeface(Theme.TYPEFACE_BOLD)
			deleteView!!.text = parentActivity?.getString(R.string.Copy)?.uppercase()

			deleteView!!.setOnClickListener { v ->
				if (pressedLinkOwnerLayout != null) {
					AndroidUtilities.addToClipboard(pressedLinkOwnerLayout!!.text)
					if (AndroidUtilities.shouldShowClipboardToast()) {
						Toast.makeText(parentActivity, v.context.getString(R.string.TextCopied), Toast.LENGTH_SHORT).show()
					}
				}
				if (popupWindow != null && popupWindow!!.isShowing) {
					popupWindow!!.dismiss(true)
				}
			}

			popupLayout!!.addView(deleteView, createFrame(LayoutHelper.WRAP_CONTENT, 48f))

			popupWindow = ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT)
			popupWindow!!.setAnimationEnabled(false)
			popupWindow!!.animationStyle = R.style.PopupContextAnimation
			popupWindow!!.isOutsideTouchable = true
			popupWindow!!.isClippingEnabled = true
			popupWindow!!.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
			popupWindow!!.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
			popupWindow!!.contentView.isFocusableInTouchMode = true

			popupWindow!!.setOnDismissListener {
				if (pressedLinkOwnerView != null) {
					pressedLinkOwnerLayout = null
					pressedLinkOwnerView?.invalidate()
					pressedLinkOwnerView = null
				}
			}
		}

		deleteView!!.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem))
		if (popupLayout != null) {
			popupLayout!!.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground))
		}

		popupLayout!!.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000f), MeasureSpec.AT_MOST))
		popupWindow!!.isFocusable = true
		popupWindow!!.showAtLocation(parent, gravity, x, y)
		popupWindow!!.startAnimation()
	}

	private fun getBlockCaption(block: PageBlock?, type: Int): RichText? {
		if (type == 2) {
			var text1 = getBlockCaption(block, 0)
			if (text1 is TLTextEmpty) {
				text1 = null
			}
			var text2 = getBlockCaption(block, 1)
			if (text2 is TLTextEmpty) {
				text2 = null
			}
			if (text1 != null && text2 == null) {
				return text1
			}
			else if (text1 == null && text2 != null) {
				return text2
			}
			else if (text1 != null && text2 != null) {
				val text3 = TLTextPlain()
				text3.text = " "

				val textConcat = TLTextConcat()
				textConcat.texts.add(text1)
				textConcat.texts.add(text3)
				textConcat.texts.add(text2)
				return textConcat
			}
			else {
				return null
			}
		}
		if (block is TLPageBlockEmbedPost) {
			if (type == 0) {
				return block.caption!!.text
			}
			else if (type == 1) {
				return block.caption!!.credit
			}
		}
		else if (block is TLPageBlockSlideshow) {
			if (type == 0) {
				return block.caption!!.text
			}
			else if (type == 1) {
				return block.caption!!.credit
			}
		}
		else if (block is TLPageBlockPhoto) {
			if (type == 0) {
				return block.caption!!.text
			}
			else if (type == 1) {
				return block.caption!!.credit
			}
		}
		else if (block is TLPageBlockCollage) {
			if (type == 0) {
				return block.caption!!.text
			}
			else if (type == 1) {
				return block.caption!!.credit
			}
		}
		else if (block is TLPageBlockEmbed) {
			if (type == 0) {
				return block.caption!!.text
			}
			else if (type == 1) {
				return block.caption!!.credit
			}
		}
		else if (block is TLPageBlockBlockquote) {
			return block.caption
		}
		else if (block is TLPageBlockVideo) {
			if (type == 0) {
				return block.caption!!.text
			}
			else if (type == 1) {
				return block.caption!!.credit
			}
		}
		else if (block is TLPageBlockPullquote) {
			return block.caption
		}
		else if (block is TLPageBlockAudio) {
			if (type == 0) {
				return block.caption!!.text
			}
			else if (type == 1) {
				return block.caption!!.credit
			}
		}
		else if (block is TLPageBlockCover) {
			return getBlockCaption(block.cover, type)
		}
		else if (block is TLPageBlockMap) {
			if (type == 0) {
				return block.caption!!.text
			}
			else if (type == 1) {
				return block.caption!!.credit
			}
		}
		return null
	}

	private fun getLastNonListCell(view: View): View {
		if (view is BlockListItemCell) {
			if (view.blockLayout != null) {
				return getLastNonListCell(view.blockLayout!!.itemView)
			}
		}
		else if (view is BlockOrderedListItemCell) {
			if (view.blockLayout != null) {
				return getLastNonListCell(view.blockLayout!!.itemView)
			}
		}
		return view
	}

	private fun isListItemBlock(block: PageBlock?): Boolean {
		return block is TLPageBlockListItem || block is TLPageBlockOrderedListItem
	}

	private fun getLastNonListPageBlock(block: PageBlock?): PageBlock? {
		if (block is TLPageBlockListItem) {
			return getLastNonListPageBlock(block.blockItem)
		}
		else if (block is TLPageBlockOrderedListItem) {
			return getLastNonListPageBlock(block.blockItem)
		}

		return block
	}

	private fun openAllParentBlocks(child: TLPageBlockDetailsChild?): Boolean {
		if (child == null) {
			return false
		}

		var parentBlock = getLastNonListPageBlock(child.parent)

		if (parentBlock is TLPageBlockDetails) {
			if (!parentBlock.open) {
				parentBlock.open = true
				return true
			}

			return false
		}
		else if (parentBlock is TLPageBlockDetailsChild) {
			parentBlock = getLastNonListPageBlock(parentBlock.block)

			var opened = false

			if (parentBlock is TLPageBlockDetails) {
				if (!parentBlock.open) {
					parentBlock.open = true
					opened = true
				}
			}

			return openAllParentBlocks(parentBlock as? TLPageBlockDetailsChild) || opened
		}

		return false
	}

	private fun fixListBlock(parentBlock: PageBlock?, childBlock: PageBlock): PageBlock {
		if (parentBlock is TLPageBlockListItem) {
			parentBlock.blockItem = childBlock
			return parentBlock
		}
		else if (parentBlock is TLPageBlockOrderedListItem) {
			parentBlock.blockItem = childBlock
			return parentBlock
		}
		return childBlock
	}

	private fun wrapInTableBlock(parentBlock: PageBlock?, childBlock: PageBlock): PageBlock {
		if (parentBlock is TLPageBlockListItem) {
			val item = TLPageBlockListItem()
			item.parent = parentBlock.parent
			item.blockItem = wrapInTableBlock(parentBlock.blockItem, childBlock)
			return item
		}
		else if (parentBlock is TLPageBlockOrderedListItem) {
			val item = TLPageBlockOrderedListItem()
			item.parent = parentBlock.parent
			item.blockItem = wrapInTableBlock(parentBlock.blockItem, childBlock)
			return item
		}
		return childBlock
	}

	private fun updateInterfaceForCurrentPage(webPage: WebPage?, previous: Boolean, order: Int) {
		if (webPage == null || webPage.cachedPage == null) {
			return
		}

		val adapter = adapter ?: return

		if (!previous && order != 0) {
			val adapterToUpdate = adapter[1]
			adapter[1] = adapter[0]
			adapter[0] = adapterToUpdate

			val listToUpdate = listView!![1]
			listView!![1] = listView!![0]
			listView!![0] = listToUpdate

			val layoutManagerToUpdate = layoutManager!![1]
			layoutManager!![1] = layoutManager!![0]
			layoutManager!![0] = layoutManagerToUpdate

			val index1 = containerView!!.indexOfChild(listView!![0])
			val index2 = containerView!!.indexOfChild(listView!![1])
			if (order == 1) {
				if (index1 < index2) {
					containerView!!.removeView(listView!![0])
					containerView!!.addView(listView!![0], index2)
				}
			}
			else {
				if (index2 < index1) {
					containerView!!.removeView(listView!![0])
					containerView!!.addView(listView!![0], index1)
				}
			}

			pageSwitchAnimation = AnimatorSet()
			listView!![0].visibility = View.VISIBLE
			val index = if (order == 1) 0 else 1
			listView!![index].setBackgroundColor(backgroundPaint!!.color)
			if (Build.VERSION.SDK_INT >= 18) {
				listView!![index].setLayerType(View.LAYER_TYPE_HARDWARE, null)
			}
			if (order == 1) {
				pageSwitchAnimation!!.playTogether(ObjectAnimator.ofFloat(listView!![0], View.TRANSLATION_X, AndroidUtilities.dp(56f).toFloat(), 0f), ObjectAnimator.ofFloat(listView!![0], View.ALPHA, 0.0f, 1.0f))
			}
			else if (order == -1) {
				listView!![0].alpha = 1.0f
				listView!![0].translationX = 0.0f
				pageSwitchAnimation!!.playTogether(ObjectAnimator.ofFloat(listView!![1], View.TRANSLATION_X, 0f, AndroidUtilities.dp(56f).toFloat()), ObjectAnimator.ofFloat(listView!![1], View.ALPHA, 1.0f, 0.0f))
			}
			pageSwitchAnimation!!.setDuration(150)
			pageSwitchAnimation!!.interpolator = interpolator
			pageSwitchAnimation!!.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					listView!![1].visibility = View.GONE
					textSelectionHelper!!.setParentView(listView!![0])
					textSelectionHelper!!.layoutManager = layoutManager!![0]
					listView!![index].setBackgroundDrawable(null)
					if (Build.VERSION.SDK_INT >= 18) {
						listView!![index].setLayerType(View.LAYER_TYPE_NONE, null)
					}
					pageSwitchAnimation = null
				}
			})
			pageSwitchAnimation!!.start()
		}
		if (!previous) {
			titleTextView!!.setText(webPage.siteName ?: "")
			textSelectionHelper!!.clear(true)
			headerView!!.invalidate()
		}
		val index = if (previous) 1 else 0
		val page = if (previous) pagesStack[pagesStack.size - 2] else webPage
		adapter[index].isRtl = webPage.cachedPage?.rtl == true
		adapter[index].cleanup()
		adapter[index].currentPage = page

		val numBlocks = 0
		val count: Int = page.cachedPage?.blocks?.size ?: 0

		for (a in 0..<count) {
			val block = page.cachedPage?.blocks?.get(a)

			if (a == 0) {
				block?.first = true

				if (block is TLPageBlockCover) {
					val caption = getBlockCaption(block, 0)
					val credit = getBlockCaption(block, 1)

					if ((caption != null && caption !is TLTextEmpty || credit != null && credit !is TLTextEmpty) && count > 1) {
						val next = page.cachedPage?.blocks?.get(1)

						if (next is TLPageBlockChannel) {
							adapter[index].channelBlock = next
						}
					}
				}
			}
			else if (a == 1 && adapter[index].channelBlock != null) {
				continue
			}
			adapter[index].addBlock(adapter[index], block, 0, 0, if (a == count - 1) a else 0)
		}

		adapter[index].notifyDataSetChanged()

		if (pagesStack.size == 1 || order == -1) {
			val preferences = ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE)
			val key = "article" + page.id
			val position = preferences.getInt(key, -1)
			val offset = if (preferences.getBoolean(key + "r", true) == AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
				preferences.getInt(key + "o", 0) - listView!![index].paddingTop
			}
			else {
				AndroidUtilities.dp(10f)
			}
			if (position != -1) {
				layoutManager!![index].scrollToPositionWithOffset(position, offset)
			}
		}
		else {
			layoutManager!![index].scrollToPositionWithOffset(0, 0)
		}
		if (!previous) {
			checkScrollAnimated()
		}
	}

	private fun addPageToStack(webPage: WebPage, anchor: String?, order: Int): Boolean {
		saveCurrentPagePosition()
		pagesStack.add(webPage)
		showSearch(false)
		updateInterfaceForCurrentPage(webPage, false, order)
		return scrollToAnchor(anchor)
	}

	private fun scrollToAnchor(anchor: String?): Boolean {
		val adapter = adapter ?: return false
		val anchor = anchor?.lowercase()

		if (anchor.isNullOrEmpty()) {
			return false
		}

		var row = adapter[0].anchors[anchor]

		if (row != null) {
			val textAnchor = adapter[0].anchorsParent[anchor]

			if (textAnchor != null) {
				val parentActivity = parentActivity ?: return false

				val paragraph = TLPageBlockParagraph()
				paragraph.text = textAnchor.text

				val type = adapter[0].getTypeForBlock(paragraph)
				val holder = adapter[0].onCreateViewHolder(listView!![0], type)
				adapter[0].bindBlockToHolder(type, holder, paragraph, 0, 0)

				val builder = BottomSheet.Builder(parentActivity)
				builder.setApplyTopPadding(false)
				builder.setApplyBottomPadding(false)
				val linearLayout = LinearLayout(parentActivity)
				linearLayout.orientation = LinearLayout.VERTICAL

				textSelectionHelperBottomSheet = ArticleTextSelectionHelper()
				textSelectionHelperBottomSheet?.setParentView(linearLayout)

				textSelectionHelperBottomSheet?.setCallback(object : TextSelectionHelper.Callback() {
					override fun onStateChanged(isSelected: Boolean) {
						linkSheet?.setDisableScroll(isSelected)
					}
				})

				val textView = object : TextView(parentActivity) {
					override fun onDraw(canvas: Canvas) {
						canvas.drawLine(0f, (measuredHeight - 1).toFloat(), measuredWidth.toFloat(), (measuredHeight - 1).toFloat(), dividerPaint!!)
						super.onDraw(canvas)
					}
				}

				textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
				textView.setTypeface(Theme.TYPEFACE_BOLD)
				textView.text = parentActivity.getString(R.string.InstantViewReference)
				textView.gravity = (if (adapter[0].isRtl) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
				textView.setTextColor(textColor)
				textView.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
				linearLayout.addView(textView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48f) + 1))

				holder.itemView.tag = BOTTOM_SHEET_VIEW_TAG
				linearLayout.addView(holder.itemView, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0f, 7f, 0f, 0f))

				val overlayView = textSelectionHelperBottomSheet?.getOverlayView(parentActivity)

				val frameLayout = object : FrameLayout(parentActivity) {
					override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
						val selectionOverlay = textSelectionHelperBottomSheet?.getOverlayView(context)

						val textSelectionEv = MotionEvent.obtain(ev)
						textSelectionEv.offsetLocation(-linearLayout.x, -linearLayout.y)

						if (textSelectionHelperBottomSheet!!.isSelectionMode && textSelectionHelperBottomSheet!!.getOverlayView(context).onTouchEvent(textSelectionEv)) {
							return true
						}

						if (selectionOverlay?.checkOnTap(ev) == true) {
							ev.action = MotionEvent.ACTION_CANCEL
						}

						if (ev.action == MotionEvent.ACTION_DOWN && textSelectionHelperBottomSheet!!.isSelectionMode && (ev.y < linearLayout.top || ev.y > linearLayout.bottom)) {
							return if (textSelectionHelperBottomSheet!!.getOverlayView(context).onTouchEvent(textSelectionEv)) {
								super.dispatchTouchEvent(ev)
							}
							else {
								true
							}
						}
						return super.dispatchTouchEvent(ev)
					}

					override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
						var heightMeasureSpec = heightMeasureSpec
						super.onMeasure(widthMeasureSpec, heightMeasureSpec)
						heightMeasureSpec = MeasureSpec.makeMeasureSpec(linearLayout.measuredHeight + AndroidUtilities.dp(8f), MeasureSpec.EXACTLY)
						super.onMeasure(widthMeasureSpec, heightMeasureSpec)
					}
				}

				builder.setDelegate(object : BottomSheetDelegate() {
					override fun canDismiss(): Boolean {
						if (textSelectionHelperBottomSheet != null && textSelectionHelperBottomSheet!!.isSelectionMode) {
							textSelectionHelperBottomSheet!!.clear()
							return false
						}
						return true
					}
				})

				frameLayout.addView(linearLayout, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
				frameLayout.addView(overlayView, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT)
				builder.setCustomView(frameLayout)
				if (textSelectionHelper!!.isSelectionMode) {
					textSelectionHelper!!.clear()
				}
				showDialog(builder.create().also { linkSheet = it })
			}
			else {
				if (row < 0 || row >= adapter[0].blocks.size) {
					return false
				}
				val originalBlock = adapter[0].blocks[row]
				val block = getLastNonListPageBlock(originalBlock)

				if (block is TLPageBlockDetailsChild) {
					if (openAllParentBlocks(block)) {
						adapter[0].updateRows()
						adapter[0].notifyDataSetChanged()
					}
				}
				val position = adapter[0].localBlocks.indexOf(originalBlock)
				if (position != -1) {
					row = position
				}

				var offset = adapter[0].anchorsOffset[anchor]

				if (offset != null) {
					if (offset == -1) {
						val type = adapter[0].getTypeForBlock(originalBlock)
						val holder = adapter[0].onCreateViewHolder(listView!![0], type)
						adapter[0].bindBlockToHolder(type, holder, originalBlock, 0, 0)
						holder.itemView.measure(MeasureSpec.makeMeasureSpec(listView!![0].measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
						offset = adapter[0].anchorsOffset[anchor]

						if (offset == -1) {
							offset = 0
						}
					}
				}
				else {
					offset = 0
				}

				layoutManager!![0].scrollToPositionWithOffset(row, currentHeaderHeight - AndroidUtilities.dp(56f) - offset!!)
			}

			return true
		}

		return false
	}

	private fun removeLastPageFromStack(): Boolean {
		if (pagesStack.size < 2) {
			return false
		}

		pagesStack.removeAt(pagesStack.size - 1)

		updateInterfaceForCurrentPage(pagesStack[pagesStack.size - 1], false, -1)

		return true
	}

	protected fun startCheckLongPress(x: Float, y: Float, parentView: View) {
		if (checkingForLongPress) {
			return
		}

		checkingForLongPress = true

		if (pendingCheckForTap == null) {
			pendingCheckForTap = CheckForTap()
		}

		if (parentView.tag != null && parentView.tag === BOTTOM_SHEET_VIEW_TAG && textSelectionHelperBottomSheet != null) {
			textSelectionHelperBottomSheet?.setMaybeView(x.toInt(), y.toInt(), parentView)
		}
		else {
			textSelectionHelper?.setMaybeView(x.toInt(), y.toInt(), parentView)
		}

		windowView?.postDelayed(pendingCheckForTap, ViewConfiguration.getTapTimeout().toLong())
	}

	protected fun cancelCheckLongPress() {
		checkingForLongPress = false
		if (pendingCheckForLongPress != null) {
			windowView!!.removeCallbacks(pendingCheckForLongPress)
			pendingCheckForLongPress = null
		}
		if (pendingCheckForTap != null) {
			windowView!!.removeCallbacks(pendingCheckForTap)
			pendingCheckForTap = null
		}
	}

	private fun getTextFlags(richText: RichText?): Int {
		if (richText is TLTextFixed) {
			return getTextFlags(richText.parentRichText) or TEXT_FLAG_MONO
		}
		else if (richText is TLTextItalic) {
			return getTextFlags(richText.parentRichText) or TEXT_FLAG_ITALIC
		}
		else if (richText is TLTextBold) {
			return getTextFlags(richText.parentRichText) or TEXT_FLAG_MEDIUM
		}
		else if (richText is TLTextUnderline) {
			return getTextFlags(richText.parentRichText) or TEXT_FLAG_UNDERLINE
		}
		else if (richText is TLTextStrike) {
			return getTextFlags(richText.parentRichText) or TEXT_FLAG_STRIKE
		}
		else if (richText is TLTextEmail) {
			return getTextFlags(richText.parentRichText) or TEXT_FLAG_URL
		}
		else if (richText is TLTextPhone) {
			return getTextFlags(richText.parentRichText) or TEXT_FLAG_URL
		}
		else if (richText is TLTextUrl) {
			return if (richText.webpageId != 0L) {
				getTextFlags(richText.parentRichText) or TEXT_FLAG_WEBPAGE_URL
			}
			else {
				getTextFlags(richText.parentRichText) or TEXT_FLAG_URL
			}
		}
		else if (richText is TLTextSubscript) {
			return getTextFlags(richText.parentRichText) or TEXT_FLAG_SUB
		}
		else if (richText is TLTextSuperscript) {
			return getTextFlags(richText.parentRichText) or TEXT_FLAG_SUP
		}
		else if (richText is TLTextMarked) {
			return getTextFlags(richText.parentRichText) or TEXT_FLAG_MARKED
		}
		else if (richText != null) {
			return getTextFlags(richText.parentRichText)
		}
		return TEXT_FLAG_REGULAR
	}

	private fun getLastRichText(richText: RichText?): RichText? {
		if (richText == null) {
			return null
		}
		if (richText is TLTextFixed) {
			return getLastRichText(richText.text)
		}
		else if (richText is TLTextItalic) {
			return getLastRichText(richText.text)
		}
		else if (richText is TLTextBold) {
			return getLastRichText(richText.text)
		}
		else if (richText is TLTextUnderline) {
			return getLastRichText(richText.text)
		}
		else if (richText is TLTextStrike) {
			return getLastRichText(richText.text)
		}
		else if (richText is TLTextEmail) {
			return getLastRichText(richText.text)
		}
		else if (richText is TLTextUrl) {
			return getLastRichText(richText.text)
		}
		else if (richText is TLTextAnchor) {
			getLastRichText(richText.text)
		}
		else if (richText is TLTextSubscript) {
			return getLastRichText(richText.text)
		}
		else if (richText is TLTextSuperscript) {
			return getLastRichText(richText.text)
		}
		else if (richText is TLTextMarked) {
			return getLastRichText(richText.text)
		}
		else if (richText is TLTextPhone) {
			return getLastRichText(richText.text)
		}
		return richText
	}

	private fun getText(adapter: WebpageAdapter, parentView: View?, parentRichText: RichText?, richText: RichText?, parentBlock: PageBlock?, maxWidth: Int): CharSequence? {
		return getText(adapter.currentPage, parentView, parentRichText, richText, parentBlock, maxWidth)
	}

	private fun getText(page: WebPage?, parentView: View?, parentRichText: RichText?, richText: RichText?, parentBlock: PageBlock?, maxWidth: Int): CharSequence? {
		var maxWidth = maxWidth
		if (richText == null) {
			return null
		}
		if (richText is TLTextFixed) {
			return getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth)
		}
		else if (richText is TLTextItalic) {
			return getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth)
		}
		else if (richText is TLTextBold) {
			return getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth)
		}
		else if (richText is TLTextUnderline) {
			return getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth)
		}
		else if (richText is TLTextStrike) {
			return getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth)
		}
		else if (richText is TLTextEmail) {
			val spannableStringBuilder = SpannableStringBuilder(getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth))
			val innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length, MetricAffectingSpan::class.java)

			if (spannableStringBuilder.isNotEmpty()) {
				spannableStringBuilder.setSpan(TextPaintUrlSpan(if (innerSpans == null || innerSpans.size == 0) getTextPaint(parentRichText, richText, parentBlock) else null, "mailto:" + getUrl(richText)), 0, spannableStringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}

			return spannableStringBuilder
		}
		else if (richText is TLTextUrl) {
			val spannableStringBuilder = SpannableStringBuilder(getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth))
			val innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length, MetricAffectingSpan::class.java)
			val paint = if (innerSpans == null || innerSpans.size == 0) getTextPaint(parentRichText, richText, parentBlock) else null

			val span: MetricAffectingSpan = if (richText.webpageId != 0L) {
				TextPaintWebpageUrlSpan(paint, getUrl(richText))
			}
			else {
				TextPaintUrlSpan(paint, getUrl(richText))
			}

			if (spannableStringBuilder.isNotEmpty()) {
				spannableStringBuilder.setSpan(span, 0, spannableStringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}

			return spannableStringBuilder
		}
		else if (richText is TLTextPlain) {
			return richText.text
		}
		else if (richText is TLTextAnchor) {
			val spannableStringBuilder = SpannableStringBuilder(getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth))
			spannableStringBuilder.setSpan(AnchorSpan(richText.name), 0, spannableStringBuilder.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
			return spannableStringBuilder
		}
		else if (richText is TLTextEmpty) {
			return ""
		}
		else if (richText is TLTextConcat) {
			val spannableStringBuilder = SpannableStringBuilder()
			val count: Int = richText.texts.size

			for (a in 0..<count) {
				val innerRichText = richText.texts[a]
				val lastRichText = getLastRichText(innerRichText)
				val extraSpace = maxWidth >= 0 && innerRichText is TLTextUrl && innerRichText.webpageId != 0L

				if (extraSpace && spannableStringBuilder.isNotEmpty() && spannableStringBuilder[spannableStringBuilder.length - 1] != '\n') {
					spannableStringBuilder.append(" ")
					spannableStringBuilder.setSpan(IgnoreCopySpannable(), spannableStringBuilder.length - 1, spannableStringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}

				val innerText = getText(page, parentView, parentRichText, innerRichText, parentBlock, maxWidth)
				val flags = getTextFlags(lastRichText)
				val startLength = spannableStringBuilder.length
				spannableStringBuilder.append(innerText)
				if (flags != 0 && innerText !is SpannableStringBuilder) {
					if ((flags and TEXT_FLAG_URL) != 0 || (flags and TEXT_FLAG_WEBPAGE_URL) != 0) {
						var url = getUrl(innerRichText)
						if (url == null) {
							url = getUrl(parentRichText)
						}
						val span: MetricAffectingSpan = if ((flags and TEXT_FLAG_WEBPAGE_URL) != 0) {
							TextPaintWebpageUrlSpan(getTextPaint(parentRichText, lastRichText, parentBlock), url)
						}
						else {
							TextPaintUrlSpan(getTextPaint(parentRichText, lastRichText, parentBlock), url)
						}
						if (startLength != spannableStringBuilder.length) {
							spannableStringBuilder.setSpan(span, startLength, spannableStringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
						}
					}
					else {
						if (startLength != spannableStringBuilder.length) {
							spannableStringBuilder.setSpan(TextPaintSpan(getTextPaint(parentRichText, lastRichText, parentBlock)), startLength, spannableStringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
						}
					}
				}
				if (extraSpace && a != count - 1) {
					spannableStringBuilder.append(" ")
					spannableStringBuilder.setSpan(IgnoreCopySpannable(), spannableStringBuilder.length - 1, spannableStringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
			}
			return spannableStringBuilder
		}
		else if (richText is TLTextSubscript) {
			return getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth)
		}
		else if (richText is TLTextSuperscript) {
			return getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth)
		}
		else if (richText is TLTextMarked) {
			val spannableStringBuilder = SpannableStringBuilder(getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth))
			val innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length, MetricAffectingSpan::class.java)
			if (spannableStringBuilder.isNotEmpty()) {
				spannableStringBuilder.setSpan(TextPaintMarkSpan(if (innerSpans == null || innerSpans.size == 0) getTextPaint(parentRichText, richText, parentBlock) else null), 0, spannableStringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
			return spannableStringBuilder
		}
		else if (richText is TLTextPhone) {
			val spannableStringBuilder = SpannableStringBuilder(getText(page, parentView, parentRichText, richText.text, parentBlock, maxWidth))
			val innerSpans = spannableStringBuilder.getSpans(0, spannableStringBuilder.length, MetricAffectingSpan::class.java)
			if (spannableStringBuilder.isNotEmpty()) {
				spannableStringBuilder.setSpan(TextPaintUrlSpan(if (innerSpans == null || innerSpans.size == 0) getTextPaint(parentRichText, richText, parentBlock) else null, "tel:" + getUrl(richText)), 0, spannableStringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
			return spannableStringBuilder
		}
		else if (richText is TLTextImage) {
			val document = WebPageUtils.getDocumentWithId(page, richText.documentId)
			if (document != null) {
				val spannableStringBuilder = SpannableStringBuilder("*")
				var w = AndroidUtilities.dp(richText.w.toFloat())
				var h = AndroidUtilities.dp(richText.h.toFloat())
				maxWidth = abs(maxWidth.toDouble()).toInt()
				if (w > maxWidth) {
					val scale = maxWidth / w.toFloat()
					w = maxWidth
					h = (h * scale).toInt()
				}
				if (parentView != null) {
					val color = parentActivity!!.getColor(R.color.background)
					val lightness = (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255.0f
					spannableStringBuilder.setSpan(TextPaintImageReceiverSpan(parentView, document, page, w, h, false, lightness <= 0.705f), 0, spannableStringBuilder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
				}
				return spannableStringBuilder
			}
			else {
				return ""
			}
		}
		return "not supported $richText"
	}

	private val textColor: Int
		get() = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)

	private val linkTextColor: Int
		get() = Theme.getColor(Theme.key_windowBackgroundWhiteLinkText)

	private fun getTextPaint(parentRichText: RichText?, richText: RichText?, parentBlock: PageBlock?): TextPaint? {
		val flags = getTextFlags(richText)
		var currentMap: SparseArray<TextPaint>? = null
		var textSize = AndroidUtilities.dp(14f)
		var textColor = -0x10000

		val additionalSize = AndroidUtilities.dp((SharedConfig.ivFontSize - 16).toFloat())

		if (parentBlock is TLPageBlockPhoto) {
			if (parentBlock.caption!!.text === richText || parentBlock.caption!!.text === parentRichText) {
				currentMap = photoCaptionTextPaints
				textSize = AndroidUtilities.dp(14f)
			}
			else {
				currentMap = photoCreditTextPaints
				textSize = AndroidUtilities.dp(12f)
			}
			textColor = grayTextColor
		}
		else if (parentBlock is TLPageBlockMap) {
			if (parentBlock.caption!!.text === richText || parentBlock.caption!!.text === parentRichText) {
				currentMap = photoCaptionTextPaints
				textSize = AndroidUtilities.dp(14f)
			}
			else {
				currentMap = photoCreditTextPaints
				textSize = AndroidUtilities.dp(12f)
			}
			textColor = grayTextColor
		}
		else if (parentBlock is TLPageBlockTitle) {
			currentMap = titleTextPaints
			textSize = AndroidUtilities.dp(23f)
			textColor = this.textColor
		}
		else if (parentBlock is TLPageBlockKicker) {
			currentMap = kickerTextPaints
			textSize = AndroidUtilities.dp(14f)
			textColor = this.textColor
		}
		else if (parentBlock is TLPageBlockAuthorDate) {
			currentMap = authorTextPaints
			textSize = AndroidUtilities.dp(14f)
			textColor = grayTextColor
		}
		else if (parentBlock is TLPageBlockFooter) {
			currentMap = footerTextPaints
			textSize = AndroidUtilities.dp(14f)
			textColor = grayTextColor
		}
		else if (parentBlock is TLPageBlockSubtitle) {
			currentMap = subtitleTextPaints
			textSize = AndroidUtilities.dp(20f)
			textColor = this.textColor
		}
		else if (parentBlock is TLPageBlockHeader) {
			currentMap = headerTextPaints
			textSize = AndroidUtilities.dp(20f)
			textColor = this.textColor
		}
		else if (parentBlock is TLPageBlockSubheader) {
			currentMap = subheaderTextPaints
			textSize = AndroidUtilities.dp(17f)
			textColor = this.textColor
		}
		else if (parentBlock is TLPageBlockBlockquote) {
			if (parentBlock.text === parentRichText) {
				currentMap = quoteTextPaints
				textSize = AndroidUtilities.dp(15f)
				textColor = this.textColor
			}
			else if (parentBlock.caption === parentRichText) {
				currentMap = photoCaptionTextPaints
				textSize = AndroidUtilities.dp(14f)
				textColor = grayTextColor
			}
		}
		else if (parentBlock is TLPageBlockPullquote) {
			if (parentBlock.text === parentRichText) {
				currentMap = quoteTextPaints
				textSize = AndroidUtilities.dp(15f)
				textColor = this.textColor
			}
			else if (parentBlock.caption === parentRichText) {
				currentMap = photoCaptionTextPaints
				textSize = AndroidUtilities.dp(14f)
				textColor = grayTextColor
			}
		}
		else if (parentBlock is TLPageBlockPreformatted) {
			currentMap = preformattedTextPaints
			textSize = AndroidUtilities.dp(14f)
			textColor = this.textColor
		}
		else if (parentBlock is TLPageBlockParagraph) {
			currentMap = paragraphTextPaints
			textSize = AndroidUtilities.dp(16f)
			textColor = this.textColor
		}
		else if (isListItemBlock(parentBlock)) {
			currentMap = listTextPaints
			textSize = AndroidUtilities.dp(16f)
			textColor = this.textColor
		}
		else if (parentBlock is TLPageBlockEmbed) {
			if (parentBlock.caption!!.text === richText || parentBlock.caption!!.text === parentRichText) {
				currentMap = photoCaptionTextPaints
				textSize = AndroidUtilities.dp(14f)
			}
			else {
				currentMap = photoCreditTextPaints
				textSize = AndroidUtilities.dp(12f)
			}
			textColor = grayTextColor
		}
		else if (parentBlock is TLPageBlockSlideshow) {
			if (parentBlock.caption!!.text === richText || parentBlock.caption!!.text === parentRichText) {
				currentMap = photoCaptionTextPaints
				textSize = AndroidUtilities.dp(14f)
			}
			else {
				currentMap = photoCreditTextPaints
				textSize = AndroidUtilities.dp(12f)
			}
			textColor = grayTextColor
		}
		else if (parentBlock is TLPageBlockCollage) {
			if (parentBlock.caption!!.text === richText || parentBlock.caption!!.text === parentRichText) {
				currentMap = photoCaptionTextPaints
				textSize = AndroidUtilities.dp(14f)
			}
			else {
				currentMap = photoCreditTextPaints
				textSize = AndroidUtilities.dp(12f)
			}
			textColor = grayTextColor
		}
		else if (parentBlock is TLPageBlockEmbedPost) {
			if (richText === parentBlock.caption!!.text) {
				currentMap = photoCaptionTextPaints
				textSize = AndroidUtilities.dp(14f)
				textColor = grayTextColor
			}
			else if (richText === parentBlock.caption!!.credit) {
				currentMap = photoCreditTextPaints
				textSize = AndroidUtilities.dp(12f)
				textColor = grayTextColor
			}
			else if (richText != null) {
				currentMap = embedPostTextPaints
				textSize = AndroidUtilities.dp(14f)
				textColor = this.textColor
			}
		}
		else if (parentBlock is TLPageBlockVideo) {
			if (richText === parentBlock.caption!!.text) {
				currentMap = mediaCaptionTextPaints
				textSize = AndroidUtilities.dp(14f)
			}
			else {
				currentMap = mediaCreditTextPaints
				textSize = AndroidUtilities.dp(12f)
			}
			textColor = this.textColor
		}
		else if (parentBlock is TLPageBlockAudio) {
			if (richText === parentBlock.caption!!.text) {
				currentMap = mediaCaptionTextPaints
				textSize = AndroidUtilities.dp(14f)
			}
			else {
				currentMap = mediaCreditTextPaints
				textSize = AndroidUtilities.dp(12f)
			}
			textColor = this.textColor
		}
		else if (parentBlock is TLPageBlockRelatedArticles) {
			currentMap = relatedArticleTextPaints
			textSize = AndroidUtilities.dp(15f)
			textColor = grayTextColor
		}
		else if (parentBlock is TLPageBlockDetails) {
			currentMap = detailsTextPaints
			textSize = AndroidUtilities.dp(15f)
			textColor = this.textColor
		}
		else if (parentBlock is TLPageBlockTable) {
			currentMap = tableTextPaints
			textSize = AndroidUtilities.dp(15f)
			textColor = this.textColor
		}
		if ((flags and TEXT_FLAG_SUP) != 0 || (flags and TEXT_FLAG_SUB) != 0) {
			textSize -= AndroidUtilities.dp(4f)
		}
		if (currentMap == null) {
			if (errorTextPaint == null) {
				errorTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
				errorTextPaint!!.setTypeface(Theme.TYPEFACE_DEFAULT)
				errorTextPaint!!.color = -0x10000
			}
			errorTextPaint!!.textSize = AndroidUtilities.dp(14f).toFloat()
			return errorTextPaint
		}
		var paint = currentMap[flags]
		if (paint == null) {
			paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
			paint.setTypeface(Theme.TYPEFACE_DEFAULT)

			if ((flags and TEXT_FLAG_MONO) != 0) {
				paint.setTypeface(Theme.TYPEFACE_MONOSPACE)
			}
			else {
				if (parentBlock is TLPageBlockRelatedArticles) {
					paint.setTypeface(Theme.TYPEFACE_BOLD)
				}
				else if (selectedFont == 1 || parentBlock is TLPageBlockTitle || parentBlock is TLPageBlockKicker || parentBlock is TLPageBlockHeader || parentBlock is TLPageBlockSubtitle || parentBlock is TLPageBlockSubheader) {
					if (parentBlock is TLPageBlockTitle || parentBlock is TLPageBlockHeader || parentBlock is TLPageBlockSubtitle || parentBlock is TLPageBlockSubheader) {
						paint.setTypeface(Theme.TYPEFACE_REAL_BOLD)
					}
					else {
						if ((flags and TEXT_FLAG_MEDIUM) != 0 && (flags and TEXT_FLAG_ITALIC) != 0) {
							paint.setTypeface(Theme.TYPEFACE_BOLD_ITALIC)
						}
						else if ((flags and TEXT_FLAG_MEDIUM) != 0) {
							paint.setTypeface(Theme.TYPEFACE_BOLD)
						}
						else if ((flags and TEXT_FLAG_ITALIC) != 0) {
							paint.setTypeface(Theme.TYPEFACE_ITALIC)
						}
						else {
							paint.setTypeface(Theme.TYPEFACE_DEFAULT)
						}
					}
				}
				else {
					if ((flags and TEXT_FLAG_MEDIUM) != 0 && (flags and TEXT_FLAG_ITALIC) != 0) {
						paint.setTypeface(Theme.TYPEFACE_BOLD_ITALIC)
					}
					else if ((flags and TEXT_FLAG_MEDIUM) != 0) {
						paint.setTypeface(Theme.TYPEFACE_BOLD)
					}
					else if ((flags and TEXT_FLAG_ITALIC) != 0) {
						paint.setTypeface(Theme.TYPEFACE_ITALIC)
					}
				}
			}
			if ((flags and TEXT_FLAG_STRIKE) != 0) {
				paint.flags = paint.flags or TextPaint.STRIKE_THRU_TEXT_FLAG
			}
			if ((flags and TEXT_FLAG_UNDERLINE) != 0) {
				paint.flags = paint.flags or TextPaint.UNDERLINE_TEXT_FLAG
			}
			if ((flags and TEXT_FLAG_URL) != 0 || (flags and TEXT_FLAG_WEBPAGE_URL) != 0) {
				paint.flags = paint.flags
				textColor = linkTextColor
			}
			if ((flags and TEXT_FLAG_SUP) != 0) {
				paint.baselineShift -= AndroidUtilities.dp(6.0f)
			}
			else if ((flags and TEXT_FLAG_SUB) != 0) {
				paint.baselineShift += AndroidUtilities.dp(2.0f)
			}
			paint.color = textColor
			currentMap.put(flags, paint)
		}
		paint.textSize = (textSize + additionalSize).toFloat()
		return paint
	}

	private fun createLayoutForText(parentView: View, plainText: CharSequence?, richText: RichText?, width: Int, textY: Int, parentBlock: PageBlock, align: Layout.Alignment, parentAdapter: WebpageAdapter): DrawingText? {
		return createLayoutForText(parentView, plainText, richText, width, 0, parentBlock, align, 0, parentAdapter)
	}

	private fun createLayoutForText(parentView: View, plainText: CharSequence?, richText: RichText?, width: Int, textY: Int, parentBlock: PageBlock, parentAdapter: WebpageAdapter): DrawingText? {
		return createLayoutForText(parentView, plainText, richText, width, textY, parentBlock, Layout.Alignment.ALIGN_NORMAL, 0, parentAdapter)
	}

	private fun createLayoutForText(parentView: View, plainText: CharSequence?, richText: RichText?, width: Int, textY: Int, parentBlock: PageBlock?, align: Layout.Alignment, maxLines: Int, parentAdapter: WebpageAdapter): DrawingText? {
		var width = width
		if (plainText == null && (richText == null || richText is TLTextEmpty)) {
			return null
		}
		if (width < 0) {
			width = AndroidUtilities.dp(10f)
		}

		var text: CharSequence?
		text = plainText ?: getText(parentAdapter, parentView, richText, richText, parentBlock, width)
		if (TextUtils.isEmpty(text)) {
			return null
		}

		val additionalSize = AndroidUtilities.dp((SharedConfig.ivFontSize - 16).toFloat())

		val paint: TextPaint?
		if (parentBlock is TLPageBlockEmbedPost && richText == null) {
			if (parentBlock.author === plainText) {
				if (embedPostAuthorPaint == null) {
					embedPostAuthorPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
					embedPostAuthorPaint!!.color = textColor
					embedPostAuthorPaint!!.setTypeface(Theme.TYPEFACE_DEFAULT)
				}
				embedPostAuthorPaint!!.textSize = (AndroidUtilities.dp(15f) + additionalSize).toFloat()
				paint = embedPostAuthorPaint
			}
			else {
				if (embedPostDatePaint == null) {
					embedPostDatePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
					embedPostDatePaint!!.color = grayTextColor
					embedPostDatePaint!!.setTypeface(Theme.TYPEFACE_DEFAULT)
				}
				embedPostDatePaint!!.textSize = (AndroidUtilities.dp(14f) + additionalSize).toFloat()
				paint = embedPostDatePaint
			}
		}
		else if (parentBlock is TLPageBlockChannel) {
			if (channelNamePaint == null) {
				channelNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
				channelNamePaint!!.setTypeface(Theme.TYPEFACE_BOLD)

				channelNamePhotoPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
				channelNamePhotoPaint!!.setTypeface(Theme.TYPEFACE_BOLD)
			}
			channelNamePaint!!.color = textColor
			channelNamePaint!!.textSize = AndroidUtilities.dp(15f).toFloat()

			channelNamePhotoPaint!!.color = -0x1
			channelNamePhotoPaint!!.textSize = AndroidUtilities.dp(15f).toFloat()

			paint = if (parentAdapter.channelBlock != null) channelNamePhotoPaint else channelNamePaint
		}
		else if (parentBlock is TLPageBlockRelatedArticlesChild) {
			if (plainText === parentBlock.parent!!.articles[parentBlock.num].title) {
				if (relatedArticleHeaderPaint == null) {
					relatedArticleHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
					relatedArticleHeaderPaint!!.setTypeface(Theme.TYPEFACE_BOLD)
				}
				relatedArticleHeaderPaint!!.color = textColor
				relatedArticleHeaderPaint!!.textSize = (AndroidUtilities.dp(15f) + additionalSize).toFloat()
				paint = relatedArticleHeaderPaint
			}
			else {
				if (relatedArticleTextPaint == null) {
					relatedArticleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
					relatedArticleTextPaint!!.setTypeface(Theme.TYPEFACE_DEFAULT)
				}
				relatedArticleTextPaint!!.color = grayTextColor
				relatedArticleTextPaint!!.textSize = (AndroidUtilities.dp(14f) + additionalSize).toFloat()
				paint = relatedArticleTextPaint
			}
		}
		else if (isListItemBlock(parentBlock) && plainText != null) {
			if (listTextPointerPaint == null) {
				listTextPointerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
				listTextPointerPaint!!.color = textColor
				listTextPointerPaint!!.setTypeface(Theme.TYPEFACE_DEFAULT)
			}
			if (listTextNumPaint == null) {
				listTextNumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
				listTextNumPaint!!.color = textColor
				listTextNumPaint!!.setTypeface(Theme.TYPEFACE_DEFAULT)
			}
			listTextPointerPaint!!.textSize = (AndroidUtilities.dp(19f) + additionalSize).toFloat()
			listTextNumPaint!!.textSize = (AndroidUtilities.dp(16f) + additionalSize).toFloat()

			paint = if (parentBlock is TLPageBlockListItem) {
				listTextPointerPaint
			}
			else {
				listTextNumPaint
			}
		}
		else {
			paint = getTextPaint(richText, richText, parentBlock)
		}

		val result: StaticLayout?

		if (maxLines != 0) {
			result = if (parentBlock is TLPageBlockPullquote) {
				StaticLayoutEx.createStaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false, TextUtils.TruncateAt.END, width, maxLines)
			}
			else {
				StaticLayoutEx.createStaticLayout(text, paint, width, align, 1.0f, AndroidUtilities.dp(4f).toFloat(), false, TextUtils.TruncateAt.END, width, maxLines)
			}
		}
		else {
			if (text!![text.length - 1] == '\n') {
				text = text.subSequence(0, text.length - 1)
			}
			result = if (parentBlock is TLPageBlockPullquote) {
				StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false)
			}
			else {
				StaticLayout(text, paint, width, align, 1.0f, AndroidUtilities.dp(4f).toFloat(), false)
			}
		}

		if (result == null) {
			return null
		}

		val finalText = result.text
		var textPath: LinkPath? = null
		var markPath: LinkPath? = null

		if (textY >= 0) {
			if (!searchResults.isNullOrEmpty() && searchText != null) {
				val lowerString = text.toString().lowercase()
				var startIndex = 0
				var index: Int

				while ((lowerString.indexOf(searchText!!, startIndex).also { index = it }) >= 0) {
					startIndex = index + searchText!!.length

					if (index == 0 || AndroidUtilities.isPunctuationCharacter(lowerString[index - 1])) {
						adapter!![0].searchTextOffset[searchText + parentBlock + richText + index] = textY + result.getLineTop(result.getLineForOffset(index))
					}
				}
			}
		}

		if (finalText is Spanned) {
			try {
				val innerSpans = finalText.getSpans(0, finalText.length, AnchorSpan::class.java)
				val linesCount = result.lineCount

				if (innerSpans != null && innerSpans.isNotEmpty()) {
					for (innerSpan in innerSpans) {
						if (linesCount <= 1) {
							parentAdapter.anchorsOffset[innerSpan.name] = textY
						}
						else {
							parentAdapter.anchorsOffset[innerSpan.name] = textY + result.getLineTop(result.getLineForOffset(finalText.getSpanStart(innerSpan)))
						}
					}
				}
			}
			catch (e: Exception) {
				// ignored
			}

			try {
				val innerSpans = finalText.getSpans(0, finalText.length, TextPaintWebpageUrlSpan::class.java)
				if (innerSpans != null && innerSpans.isNotEmpty()) {
					textPath = LinkPath(true)
					textPath.setAllowReset(false)
					for (innerSpan in innerSpans) {
						val start = finalText.getSpanStart(innerSpan)
						val end = finalText.getSpanEnd(innerSpan)
						textPath.setCurrentLayout(result, start, 0f)
						val shift = if (innerSpan.textPaint != null) innerSpan.textPaint.baselineShift else 0
						textPath.setBaselineShift(if (shift != 0) shift + AndroidUtilities.dp((if (shift > 0) 5 else -2).toFloat()) else 0)
						result.getSelectionPath(start, end, textPath)
					}
					textPath.setAllowReset(true)
				}
			}
			catch (ignore: Exception) {
			}
			try {
				val innerSpans = finalText.getSpans(0, finalText.length, TextPaintMarkSpan::class.java)
				if (innerSpans != null && innerSpans.isNotEmpty()) {
					markPath = LinkPath(true)
					markPath.setAllowReset(false)
					for (innerSpan in innerSpans) {
						val start = finalText.getSpanStart(innerSpan)
						val end = finalText.getSpanEnd(innerSpan)
						markPath.setCurrentLayout(result, start, 0f)
						val shift = if (innerSpan.textPaint != null) innerSpan.textPaint.baselineShift else 0
						markPath.setBaselineShift(if (shift != 0) shift + AndroidUtilities.dp((if (shift > 0) 5 else -2).toFloat()) else 0)
						result.getSelectionPath(start, end, markPath)
					}
					markPath.setAllowReset(true)
				}
			}
			catch (ignore: Exception) {
			}
		}
		val drawingText = DrawingText()
		drawingText.textLayout = result
		drawingText.textPath = textPath
		drawingText.markPath = markPath
		drawingText.parentBlock = parentBlock
		drawingText.parentText = richText
		return drawingText
	}

	private fun checkLayoutForLinks(adapter: WebpageAdapter, event: MotionEvent, parentView: View?, drawingText: DrawingText?, layoutX: Int, layoutY: Int): Boolean {
		if (pageSwitchAnimation != null || parentView == null || !textSelectionHelper!!.isSelectable(parentView)) {
			return false
		}
		pressedLinkOwnerView = parentView
		if (drawingText != null) {
			val layout = drawingText.textLayout
			val x = event.x.toInt()
			val y = event.y.toInt()
			var removeLink = false
			if (event.action == MotionEvent.ACTION_DOWN) {
				var width = 0f
				var left = Int.MAX_VALUE.toFloat()
				var a = 0
				val N = layout!!.lineCount
				while (a < N) {
					width = max(layout.getLineWidth(a).toDouble(), width.toDouble()).toFloat()
					left = min(layout.getLineLeft(a).toDouble(), left.toDouble()).toFloat()
					a++
				}
				if (x >= layoutX + left && x <= left + layoutX + width && y >= layoutY && y <= layoutY + layout.height) {
					pressedLinkOwnerLayout = drawingText
					pressedLayoutY = layoutY
					val text = layout.text
					if (text is Spannable) {
						try {
							val checkX = x - layoutX
							val checkY = y - layoutY
							val line = layout.getLineForVertical(checkY)
							val off = layout.getOffsetForHorizontal(line, checkX.toFloat())
							left = layout.getLineLeft(line)
							if (left <= checkX && left + layout.getLineWidth(line) >= checkX) {
								val buffer = layout.text as Spannable
								val link = buffer.getSpans(off, off, TextPaintUrlSpan::class.java)
								if (link != null && link.isNotEmpty()) {
									var selectedLink = link[0]
									var pressedStart = buffer.getSpanStart(selectedLink)
									var pressedEnd = buffer.getSpanEnd(selectedLink)
									for (a in 1..<link.size) {
										val span = link[a]
										val start = buffer.getSpanStart(span)
										val end = buffer.getSpanEnd(span)
										if (pressedStart > start || end > pressedEnd) {
											selectedLink = span
											pressedStart = start
											pressedEnd = end
										}
									}
									if (pressedLink != null) {
										links.removeLink(pressedLink)
									}
									pressedLink = LinkSpanDrawable(selectedLink, x.toFloat(), y.toFloat())
									pressedLink!!.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkSelection) and 0x33ffffff)
									links.addLink(pressedLink, pressedLinkOwnerLayout)
									try {
										val path = pressedLink!!.obtainNewPath()
										path.setCurrentLayout(layout, pressedStart, 0f)
										val textPaint = selectedLink.textPaint
										val shift = textPaint?.baselineShift ?: 0
										path.setBaselineShift(if (shift != 0) shift + AndroidUtilities.dp((if (shift > 0) 5 else -2).toFloat()) else 0)
										layout.getSelectionPath(pressedStart, pressedEnd, path)
										parentView.invalidate()
									}
									catch (e: Exception) {
										FileLog.e(e)
									}
								}
							}
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}
			}
			else if (event.action == MotionEvent.ACTION_UP) {
				if (pressedLink != null) {
					removeLink = true

					val url = pressedLink!!.span.url

					if (url != null) {
						linkSheet?.dismiss()
						linkSheet = null

						val index: Int
						var isAnchor = false
						var anchor: String?

						if ((url.lastIndexOf('#').also { index = it }) != -1) {
							val webPageUrl = if (!TextUtils.isEmpty(adapter.currentPage?.cachedPage?.url)) {
								adapter.currentPage.cachedPage?.url?.lowercase()
							}
							else {
								adapter.currentPage?.url?.lowercase()
							} ?: ""

							anchor = try {
								URLDecoder.decode(url.substring(index + 1), "UTF-8")
							}
							catch (ignore: Exception) {
								""
							}
							if (url.lowercase().contains(webPageUrl)) {
								if (TextUtils.isEmpty(anchor)) {
									layoutManager!![0].scrollToPositionWithOffset(0, 0)
									checkScrollAnimated()
								}
								else {
									scrollToAnchor(anchor)
								}
								isAnchor = true
							}
						}
						else {
							anchor = null
						}
						if (!isAnchor) {
							openWebpageUrl(pressedLink!!.span.url, anchor)
						}
					}
				}
			}
			else if (event.action == MotionEvent.ACTION_CANCEL && (popupWindow == null || !popupWindow!!.isShowing)) {
				removeLink = true
			}
			if (removeLink) {
				removePressedLink()
			}
		}
		if (event.action == MotionEvent.ACTION_DOWN) {
			startCheckLongPress(event.x, event.y, parentView)
		}
		if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) {
			cancelCheckLongPress()
		}
		return if (parentView is BlockDetailsCell) {
			pressedLink != null
		}
		else {
			pressedLinkOwnerLayout != null
		}
	}

	private fun removePressedLink() {
		if (pressedLink == null && pressedLinkOwnerView == null) {
			return
		}
		val parentView = pressedLinkOwnerView
		links.clear()
		pressedLink = null
		pressedLinkOwnerLayout = null
		pressedLinkOwnerView = null
		parentView?.invalidate()
	}

	private fun openWebpageUrl(url: String?, anchor: String?) {
		if (openUrlReqId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(openUrlReqId, false)
			openUrlReqId = 0
		}
		val reqId = ++lastReqId
		showProgressView(true, true)
		val req = TLMessagesGetWebPage()
		req.url = url
		req.hash = 0
		openUrlReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response: TLObject?, error: TLError? ->
			AndroidUtilities.runOnUIThread {
				if (openUrlReqId == 0 || reqId != lastReqId) {
					return@runOnUIThread
				}
				openUrlReqId = 0
				showProgressView(true, false)
				if (isVisible) {
					if (response is TLWebPage && response.cachedPage is TLPage) {
						addPageToStack(response, anchor, 1)
					}
					else {
						Browser.openUrl(parentActivity, req.url)
					}
				}
			}
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		if (id == NotificationCenter.messagePlayingDidStart) {
			val messageObject = args[0] as MessageObject
			if (listView != null) {
				for (recyclerListView in listView!!) {
					val count = recyclerListView.childCount
					for (a in 0..<count) {
						val view = recyclerListView.getChildAt(a)
						if (view is BlockAudioCell) {
							view.updateButtonState(true)
						}
					}
				}
			}
		}
		else if (id == NotificationCenter.messagePlayingDidReset || id == NotificationCenter.messagePlayingPlayStateChanged) {
			if (listView != null) {
				for (recyclerListView in listView!!) {
					val count = recyclerListView.childCount
					for (a in 0..<count) {
						val view = recyclerListView.getChildAt(a)
						if (view is BlockAudioCell) {
							val messageObject = view.messageObject
							if (messageObject != null) {
								view.updateButtonState(true)
							}
						}
					}
				}
			}
		}
		else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
			val mid = args[0] as Int
			if (listView != null) {
				for (recyclerListView in listView!!) {
					val count = recyclerListView.childCount
					for (a in 0..<count) {
						val view = recyclerListView.getChildAt(a)
						if (view is BlockAudioCell) {
							val playing = view.messageObject
							if (playing != null && playing.id == mid) {
								val player = MediaController.getInstance().playingMessageObject
								if (player != null) {
									playing.audioProgress = player.audioProgress
									playing.audioProgressSec = player.audioProgressSec
									playing.audioPlayerDuration = player.audioPlayerDuration
									view.updatePlayingMessageProgress()
								}
								break
							}
						}
					}
				}
			}
		}
	}

	fun updateThemeColors(progress: Float) {
		refreshThemeColors()
		updatePaintColors()

		if (windowView != null) {
			listView!![0].invalidateViews()
			listView!![1].invalidateViews()

			windowView?.invalidate()
			searchPanel?.invalidate()

			if (progress == 1f) {
				adapter!![0].notifyDataSetChanged()
				adapter!![1].notifyDataSetChanged()
			}
		}
	}

	private fun updatePaintSize() {
		for (i in 0..1) {
			adapter?.get(i)?.notifyDataSetChanged()
		}
	}

	private fun updatePaintFonts() {
		ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit { putInt("font_type", selectedFont) }

		val typefaceNormal = if (selectedFont == 0) Theme.TYPEFACE_DEFAULT else Typeface.SERIF
		val typefaceItalic = if (selectedFont == 0) Theme.TYPEFACE_ITALIC else Typeface.create("serif", Typeface.ITALIC)
		val typefaceBold = if (selectedFont == 0) Theme.TYPEFACE_BOLD else Typeface.create("serif", Typeface.BOLD)
		val typefaceBoldItalic = if (selectedFont == 0) Theme.TYPEFACE_BOLD_ITALIC else Typeface.create("serif", Typeface.BOLD_ITALIC)

		for (a in 0..<quoteTextPaints.size) {
			updateFontEntry(quoteTextPaints.keyAt(a), quoteTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<preformattedTextPaints.size) {
			updateFontEntry(preformattedTextPaints.keyAt(a), preformattedTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<paragraphTextPaints.size) {
			updateFontEntry(paragraphTextPaints.keyAt(a), paragraphTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<listTextPaints.size) {
			updateFontEntry(listTextPaints.keyAt(a), listTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<embedPostTextPaints.size) {
			updateFontEntry(embedPostTextPaints.keyAt(a), embedPostTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<mediaCaptionTextPaints.size) {
			updateFontEntry(mediaCaptionTextPaints.keyAt(a), mediaCaptionTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<mediaCreditTextPaints.size) {
			updateFontEntry(mediaCreditTextPaints.keyAt(a), mediaCreditTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<photoCaptionTextPaints.size) {
			updateFontEntry(photoCaptionTextPaints.keyAt(a), photoCaptionTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<photoCreditTextPaints.size) {
			updateFontEntry(photoCreditTextPaints.keyAt(a), photoCreditTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<authorTextPaints.size) {
			updateFontEntry(authorTextPaints.keyAt(a), authorTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<footerTextPaints.size) {
			updateFontEntry(footerTextPaints.keyAt(a), footerTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<embedPostCaptionTextPaints.size) {
			updateFontEntry(embedPostCaptionTextPaints.keyAt(a), embedPostCaptionTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<relatedArticleTextPaints.size) {
			updateFontEntry(relatedArticleTextPaints.keyAt(a), relatedArticleTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<detailsTextPaints.size) {
			updateFontEntry(detailsTextPaints.keyAt(a), detailsTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
		for (a in 0..<tableTextPaints.size) {
			updateFontEntry(tableTextPaints.keyAt(a), tableTextPaints.valueAt(a), typefaceNormal, typefaceBoldItalic, typefaceBold, typefaceItalic)
		}
	}

	private fun updateFontEntry(flags: Int, paint: TextPaint, typefaceNormal: Typeface, typefaceBoldItalic: Typeface, typefaceBold: Typeface, typefaceItalic: Typeface) {
		if ((flags and TEXT_FLAG_MEDIUM) != 0 && (flags and TEXT_FLAG_ITALIC) != 0) {
			paint.setTypeface(typefaceBoldItalic)
		}
		else if ((flags and TEXT_FLAG_MEDIUM) != 0) {
			paint.setTypeface(typefaceBold)
		}
		else if ((flags and TEXT_FLAG_ITALIC) != 0) {
			paint.setTypeface(typefaceItalic)
		}
		else if ((flags and TEXT_FLAG_MONO) != 0) {
			//change nothing
		}
		else {
			paint.setTypeface(typefaceNormal)
		}
	}

	private fun updatePaintColors() {
		backgroundPaint!!.color = parentActivity!!.getColor(R.color.background)
		for (recyclerListView in listView!!) {
			recyclerListView.setGlowColor(parentActivity!!.getColor(R.color.background))
		}

		if (listTextPointerPaint != null) {
			listTextPointerPaint!!.color = textColor
		}
		if (listTextNumPaint != null) {
			listTextNumPaint!!.color = textColor
		}
		if (embedPostAuthorPaint != null) {
			embedPostAuthorPaint!!.color = textColor
		}
		if (channelNamePaint != null) {
			channelNamePaint!!.color = textColor
		}
		if (channelNamePhotoPaint != null) {
			channelNamePhotoPaint!!.color = -0x1
		}
		if (relatedArticleHeaderPaint != null) {
			relatedArticleHeaderPaint!!.color = textColor
		}
		if (relatedArticleTextPaint != null) {
			relatedArticleTextPaint!!.color = grayTextColor
		}

		if (embedPostDatePaint != null) {
			embedPostDatePaint!!.color = grayTextColor
		}

		createPaint(true)

		setMapColors(titleTextPaints)
		setMapColors(kickerTextPaints)
		setMapColors(subtitleTextPaints)
		setMapColors(headerTextPaints)
		setMapColors(subheaderTextPaints)
		setMapColors(quoteTextPaints)
		setMapColors(preformattedTextPaints)
		setMapColors(paragraphTextPaints)
		setMapColors(listTextPaints)
		setMapColors(embedPostTextPaints)
		setMapColors(mediaCaptionTextPaints)
		setMapColors(mediaCreditTextPaints)
		setMapColors(photoCaptionTextPaints)
		setMapColors(photoCreditTextPaints)
		setMapColors(authorTextPaints)
		setMapColors(footerTextPaints)
		setMapColors(embedPostCaptionTextPaints)
		setMapColors(relatedArticleTextPaints)
		setMapColors(detailsTextPaints)
		setMapColors(tableTextPaints)
	}

	private fun setMapColors(map: SparseArray<TextPaint>) {
		for (a in 0..<map.size()) {
			val flags = map.keyAt(a)
			val paint = map.valueAt(a)
			if ((flags and TEXT_FLAG_URL) != 0 || (flags and TEXT_FLAG_WEBPAGE_URL) != 0) {
				paint.color = linkTextColor
			}
			else {
				paint.color = textColor
			}
		}
	}

	fun setParentActivity(activity: Activity, fragment: BaseFragment?) {
		parentFragment = fragment
		currentAccount = UserConfig.selectedAccount

		NotificationCenter.getInstance(currentAccount).let {
			it.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged)
			it.addObserver(this, NotificationCenter.messagePlayingDidReset)
			it.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
			it.addObserver(this, NotificationCenter.messagePlayingDidStart)
		}

		if (parentActivity === activity) {
			updatePaintColors()
			refreshThemeColors()
			return
		}

		parentActivity = activity

		val sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE)
		selectedFont = sharedPreferences.getInt("font_type", 0)
		createPaint(false)
		backgroundPaint = Paint()

		layerShadowDrawable = activity.resources.getDrawable(R.drawable.layer_shadow)
		slideDotDrawable = activity.resources.getDrawable(R.drawable.slide_dot_small)
		slideDotBigDrawable = activity.resources.getDrawable(R.drawable.slide_dot_big)
		scrimPaint = Paint()

		windowView = WindowView(activity)
		windowView!!.setWillNotDraw(false)
		windowView!!.clipChildren = true
		windowView!!.isFocusable = false
		containerView = object : FrameLayout(activity) {
			override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
				if (windowView!!.movingPage) {
					val width = measuredWidth
					val translationX = listView!![0].translationX.toInt()
					var clipLeft = 0
					var clipRight = width

					if (child === listView!![1]) {
						clipRight = translationX
					}
					else if (child === listView!![0]) {
						clipLeft = translationX
					}

					val restoreCount = canvas.save()
					canvas.clipRect(clipLeft, 0, clipRight, height)
					val result = super.drawChild(canvas, child, drawingTime)
					canvas.restoreToCount(restoreCount)

					if (translationX != 0) {
						if (child === listView!![0]) {
							val alpha = max(0.0, min(((width - translationX) / AndroidUtilities.dp(20f).toFloat()).toDouble(), 1.0)).toFloat()
							layerShadowDrawable?.setBounds(translationX - layerShadowDrawable!!.intrinsicWidth, child.getTop(), translationX, child.getBottom())
							layerShadowDrawable?.alpha = (0xff * alpha).toInt()
							layerShadowDrawable?.draw(canvas)
						}
						else if (child === listView!![1]) {
							var opacity = min(0.8, ((width - translationX) / width.toFloat()).toDouble()).toFloat()

							if (opacity < 0) {
								opacity = 0f
							}

							scrimPaint?.color = (((-0x67000000 and -0x1000000) ushr 24) * opacity).toInt() shl 24

							canvas.drawRect(clipLeft.toFloat(), 0f, clipRight.toFloat(), height.toFloat(), scrimPaint!!)
						}
					}

					return result
				}
				else {
					return super.drawChild(canvas, child, drawingTime)
				}
			}
		}

		windowView?.addView(containerView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP or Gravity.LEFT))
		//containerView.setFitsSystemWindows(true);
		windowView?.fitsSystemWindows = true

		containerView?.setOnApplyWindowInsetsListener(View.OnApplyWindowInsetsListener { _, insets ->
			if (Build.VERSION.SDK_INT >= 30) {
				return@OnApplyWindowInsetsListener WindowInsets.CONSUMED
			}
			else {
				return@OnApplyWindowInsetsListener insets.consumeSystemWindowInsets()
			}
		})

		fullscreenVideoContainer = FrameLayout(activity)
		fullscreenVideoContainer?.setBackgroundColor(-0x1000000)
		fullscreenVideoContainer?.visibility = View.INVISIBLE

		windowView?.addView(fullscreenVideoContainer, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		fullscreenAspectRatioView = AspectRatioFrameLayout(activity)
		fullscreenAspectRatioView!!.visibility = View.GONE
		fullscreenVideoContainer!!.addView(fullscreenAspectRatioView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER))

		fullscreenTextureView = TextureView(activity)

		adapter = (0..<2).map { WebpageAdapter(activity) }.toTypedArray()
		layoutManager = (0..<2).map { LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false) }.toTypedArray()

		listView = (0..<2).map { i ->
			val webpageAdapter = adapter!![i]

			val listView = object : RecyclerListView(activity) {
				override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
					super.onLayout(changed, l, t, r, b)
					val count = childCount

					for (a in 0..<count) {
						val child = getChildAt(a)
						if (child.tag is Int) {
							if (tag == 90) {
								val bottom = child.bottom
								if (bottom < measuredHeight) {
									val height = measuredHeight
									child.layout(0, height - child.measuredHeight, child.measuredWidth, height)
									break
								}
							}
						}
					}
				}

				override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
					if (pressedLinkOwnerLayout != null && pressedLink == null && (popupWindow == null || !popupWindow!!.isShowing) && (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL)) {
						pressedLink = null
						pressedLinkOwnerLayout = null
						pressedLinkOwnerView = null
					}
					else if (pressedLinkOwnerLayout != null && pressedLink != null && e.action == MotionEvent.ACTION_UP) {
						checkLayoutForLinks(webpageAdapter, e, pressedLinkOwnerView, pressedLinkOwnerLayout, 0, 0)
					}
					return super.onInterceptTouchEvent(e)
				}

				override fun onTouchEvent(e: MotionEvent): Boolean {
					if (pressedLinkOwnerLayout != null && pressedLink == null && (popupWindow == null || !popupWindow!!.isShowing) && (e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL)) {
						pressedLink = null
						pressedLinkOwnerLayout = null
						pressedLinkOwnerView = null
					}
					return super.onTouchEvent(e)
				}

				override fun setTranslationX(translationX: Float) {
					super.setTranslationX(translationX)

					if (windowView!!.movingPage) {
						containerView?.invalidate()
						val progress = translationX / measuredWidth
						setCurrentHeaderHeight((windowView!!.startMovingHeaderHeight + (AndroidUtilities.dp(56f) - windowView!!.startMovingHeaderHeight) * progress).toInt())
					}
				}
			}

			(listView.itemAnimator as DefaultItemAnimator).setDelayAnimations(false)
			listView.layoutManager = layoutManager!![i]
			listView.setAdapter(webpageAdapter)
			listView.clipToPadding = false
			listView.visibility = if (i == 0) View.VISIBLE else View.GONE
			listView.setPadding(0, AndroidUtilities.dp(56f), 0, 0)
			listView.topGlowOffset = AndroidUtilities.dp(56f)

			containerView?.addView(listView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

			listView.setOnItemLongClickListener { view, _ ->
				if (view is BlockRelatedArticlesCell) {
					showCopyPopup(view.currentBlock!!.parent!!.articles[view.currentBlock!!.num].url!!)
					return@setOnItemLongClickListener true
				}

				false
			}

			listView.setOnItemClickListener(object : OnItemClickListenerExtended {
				override fun onDoubleTap(view: View, position: Int, x: Float, y: Float) {
					// unused
				}

				override fun hasDoubleTap(view: View, position: Int): Boolean {
					return false
				}

				override fun onItemClick(view: View, position: Int, x: Float, y: Float) {
					var view = view
					if (textSelectionHelper != null) {
						if (textSelectionHelper!!.isSelectionMode) {
							textSelectionHelper!!.clear()
							return
						}
						textSelectionHelper!!.clear()
					}
					if (view is ReportCell && webpageAdapter.currentPage != null) {
						if (previewsReqId != 0 || view.hasViews && x < view.getMeasuredWidth() / 2) {
							return
						}
						val `object` = MessagesController.getInstance(currentAccount).getUserOrChat("previews")
						if (`object` is TLUser) {
							openPreviewsChat(`object` as TLRPC.User, webpageAdapter.currentPage!!.id)
						}
						else {
							val currentAccount = UserConfig.selectedAccount
							val pageId = webpageAdapter.currentPage!!.id
							showProgressView(true, true)
							val req = TLContactsResolveUsername()
							req.username = "previews"
							previewsReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response: TLObject?, error: TLError? ->
								AndroidUtilities.runOnUIThread {
									if (previewsReqId == 0) {
										return@runOnUIThread
									}
									previewsReqId = 0
									showProgressView(true, false)
									if (response != null) {
										val res = response as TLContactsResolvedPeer
										MessagesController.getInstance(currentAccount).putUsers(res.users, false)
										MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, false, true)
										if (!res.users.isEmpty()) {
											openPreviewsChat(res.users[0], pageId)
										}
									}
								}
							}
						}
					}
					else if (position >= 0 && position < webpageAdapter.localBlocks.size) {
						var pageBlock: PageBlock? = webpageAdapter.localBlocks[position]
						val originalBlock = pageBlock

						pageBlock = getLastNonListPageBlock(pageBlock)

						if (pageBlock is TLPageBlockDetailsChild) {
							pageBlock = pageBlock.block
						}
						if (pageBlock is TLPageBlockChannel) {
							MessagesController.getInstance(currentAccount).openByUserName(pageBlock.channel!!.username, parentFragment, 2)
							close(false, true)
						}
						else if (pageBlock is TLPageBlockRelatedArticlesChild) {
							openWebpageUrl(pageBlock.parent!!.articles[pageBlock.num].url, null)
						}
						else if (pageBlock is TLPageBlockDetails) {
							view = getLastNonListCell(view)
							if (view !is BlockDetailsCell) {
								return
							}

							pressedLinkOwnerLayout = null
							pressedLinkOwnerView = null
							val index = webpageAdapter.blocks.indexOf(originalBlock)
							if (index < 0) {
								return
							}
							pageBlock.open = !pageBlock.open

							val oldCount = webpageAdapter.itemCount
							webpageAdapter.updateRows()
							val newCount = webpageAdapter.itemCount
							val changeCount = abs((newCount - oldCount).toDouble()).toInt()

							view.arrow.setAnimationProgressAnimated(if (pageBlock.open) 0.0f else 1.0f)
							view.invalidate()
							if (changeCount != 0) {
								if (pageBlock.open) {
									webpageAdapter.notifyItemRangeInserted(position + 1, changeCount)
								}
								else {
									webpageAdapter.notifyItemRangeRemoved(position + 1, changeCount)
								}
							}
						}
					}
				}
			})

			listView.setOnScrollListener(object : RecyclerView.OnScrollListener() {
				override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
					if (newState == RecyclerView.SCROLL_STATE_IDLE) {
						textSelectionHelper!!.stopScrolling()
					}
				}

				override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
					if (recyclerView.childCount == 0) {
						return
					}
					textSelectionHelper!!.onParentScrolled()
					headerView!!.invalidate()
					checkScroll(dy)
				}
			})

			listView
		}.toTypedArray()

		headerPaint.color = -0x1000000
		statusBarPaint.color = -0x1000000
		headerProgressPaint.color = -0xdbdbda
		navigationBarPaint.color = Color.BLACK
		headerView = object : FrameLayout(activity) {
			override fun onDraw(canvas: Canvas) {
				val width = measuredWidth
				val height = measuredHeight
				canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), headerPaint)
				if (layoutManager == null) {
					return
				}
				val first = layoutManager!![0].findFirstVisibleItemPosition()
				val last = layoutManager!![0].findLastVisibleItemPosition()
				val count = layoutManager!![0].itemCount
				val view = if (last >= count - 2) {
					layoutManager!![0].findViewByPosition(count - 2)
				}
				else {
					layoutManager!![0].findViewByPosition(first)
				}
				if (view == null) {
					return
				}

				val itemProgress = width / (count - 1).toFloat()
				val childCount = layoutManager!![0].childCount
				val viewHeight = view.measuredHeight.toFloat()

				val viewProgress = if (last >= count - 2) {
					(count - 2 - first) * itemProgress * (listView!![0].measuredHeight - view.top) / viewHeight
				}
				else {
					(itemProgress * (1.0f - (min(0.0, (view.top - listView!![0].paddingTop).toDouble()) + viewHeight) / viewHeight)).toFloat()
				}

				val progress = first * itemProgress + viewProgress

				canvas.drawRect(0f, 0f, progress, height.toFloat(), headerProgressPaint)
			}
		}

		headerView?.setWillNotDraw(false)
		containerView?.addView(headerView, createFrame(LayoutHelper.MATCH_PARENT, 56f))

		headerView?.setOnClickListener {
			listView?.get(0)?.smoothScrollToPosition(0)
		}

		titleTextView = SimpleTextView(activity)
		titleTextView!!.setGravity(Gravity.CENTER_VERTICAL or Gravity.LEFT)
		titleTextView!!.setTextSize(20)
		titleTextView!!.setTypeface(Theme.TYPEFACE_BOLD)
		titleTextView!!.textColor = -0x4c4c4d
		titleTextView!!.pivotX = 0.0f
		titleTextView!!.pivotY = AndroidUtilities.dp(28f).toFloat()

		headerView?.addView(titleTextView, createFrame(LayoutHelper.MATCH_PARENT, 56f, Gravity.LEFT or Gravity.TOP, 72f, 0f, (48 * 2).toFloat(), 0f))

		lineProgressView = LineProgressView(activity)
		lineProgressView!!.setProgressColor(-0x1)
		lineProgressView!!.pivotX = 0.0f
		lineProgressView!!.pivotY = AndroidUtilities.dp(2f).toFloat()

		headerView?.addView(lineProgressView, createFrame(LayoutHelper.MATCH_PARENT, 2f, Gravity.LEFT or Gravity.BOTTOM, 0f, 0f, 0f, 1f))

		lineProgressTickRunnable = Runnable {
			val progressLeft = 0.7f - lineProgressView!!.currentProgress

			if (progressLeft > 0.0f) {
				val tick = if (progressLeft < 0.25f) {
					0.01f
				}
				else {
					0.02f
				}

				lineProgressView?.setProgress(lineProgressView!!.currentProgress + tick, true)

				AndroidUtilities.runOnUIThread(lineProgressTickRunnable, 100)
			}
		}

		menuContainer = FrameLayout(activity)

		headerView?.addView(menuContainer, createFrame(48, 56, Gravity.TOP or Gravity.RIGHT))

		searchShadow = View(activity)
		searchShadow!!.setBackgroundResource(R.drawable.header_shadow)
		searchShadow!!.alpha = 0.0f

		containerView?.addView(searchShadow, createFrame(LayoutHelper.MATCH_PARENT, 3f, Gravity.LEFT or Gravity.TOP, 0f, 56f, 0f, 0f))

		searchContainer = FrameLayout(parentActivity!!)
		searchContainer!!.setBackgroundColor(parentActivity!!.getColor(R.color.background))
		searchContainer!!.visibility = View.INVISIBLE

		headerView?.addView(searchContainer, createFrame(LayoutHelper.MATCH_PARENT, 56f))

		searchField = object : EditTextBoldCursor(parentActivity) {
			override fun onTouchEvent(event: MotionEvent): Boolean {
				if (event.action == MotionEvent.ACTION_DOWN) {
					if (!AndroidUtilities.showKeyboard(this)) {
						clearFocus()
						requestFocus()
					}
				}
				return super.onTouchEvent(event)
			}
		}

		searchField?.setCursorWidth(1.5f)
		searchField?.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
		searchField?.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
		searchField?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
		searchField?.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText))
		searchField?.setSingleLine(true)
		searchField?.setHint(activity.getString(R.string.Search))
		searchField?.setBackgroundResource(0)
		searchField?.setPadding(0, 0, 0, 0)

		val inputType = searchField!!.inputType or EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS

		searchField?.setInputType(inputType)

		searchField?.setOnEditorActionListener(OnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
			if (event != null && (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_SEARCH || event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
				AndroidUtilities.hideKeyboard(searchField)
			}

			false
		})

		searchField?.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
				// unused
			}

			override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
				if (ignoreOnTextChange) {
					ignoreOnTextChange = false
					return
				}

				processSearch(s.toString().lowercase())

				if (clearButton != null) {
					if (TextUtils.isEmpty(s)) {
						if (clearButton!!.tag != null) {
							clearButton!!.tag = null
							clearButton!!.clearAnimation()

							if (animateClear) {
								clearButton!!.animate().setInterpolator(DecelerateInterpolator()).alpha(0.0f).setDuration(180).scaleY(0.0f).scaleX(0.0f).rotation(45f).withEndAction {
									clearButton!!.visibility = View.INVISIBLE
								}.start()
							}
							else {
								clearButton!!.alpha = 0.0f
								clearButton!!.rotation = 45f
								clearButton!!.scaleX = 0.0f
								clearButton!!.scaleY = 0.0f
								clearButton!!.visibility = View.INVISIBLE
								animateClear = true
							}
						}
					}
					else {
						if (clearButton!!.tag == null) {
							clearButton!!.tag = 1
							clearButton!!.clearAnimation()
							clearButton!!.visibility = View.VISIBLE
							if (animateClear) {
								clearButton!!.animate().setInterpolator(DecelerateInterpolator()).alpha(1.0f).setDuration(180).scaleY(1.0f).scaleX(1.0f).rotation(0f).start()
							}
							else {
								clearButton!!.alpha = 1.0f
								clearButton!!.rotation = 0f
								clearButton!!.scaleX = 1.0f
								clearButton!!.scaleY = 1.0f
								animateClear = true
							}
						}
					}
				}
			}

			override fun afterTextChanged(s: Editable) {
			}
		})

		searchField?.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_SEARCH)
		searchField?.setTextIsSelectable(false)

		searchContainer?.addView(searchField, createFrame(LayoutHelper.MATCH_PARENT, 36f, Gravity.CENTER_VERTICAL, 72f, 0f, 48f, 0f))

		clearButton = object : ImageView(parentActivity) {
			override fun onDetachedFromWindow() {
				super.onDetachedFromWindow()

				clearAnimation()

				if (tag == null) {
					clearButton!!.visibility = INVISIBLE
					clearButton!!.alpha = 0.0f
					clearButton!!.rotation = 45f
					clearButton!!.scaleX = 0.0f
					clearButton!!.scaleY = 0.0f
				}
				else {
					clearButton!!.alpha = 1.0f
					clearButton!!.rotation = 0f
					clearButton!!.scaleX = 1.0f
					clearButton!!.scaleY = 1.0f
				}
			}
		}

		clearButton?.setImageDrawable(object : CloseProgressDrawable2() {
			override fun getCurrentColor(): Int {
				return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
			}
		})

		clearButton?.setScaleType(ImageView.ScaleType.CENTER)
		clearButton?.setAlpha(0.0f)
		clearButton?.rotation = 45f
		clearButton?.scaleX = 0.0f
		clearButton?.scaleY = 0.0f

		clearButton?.setOnClickListener(View.OnClickListener {
			if (searchField?.length() != 0) {
				searchField?.setText("")
			}

			searchField?.requestFocus()

			AndroidUtilities.showKeyboard(searchField)
		})

		clearButton?.setContentDescription(activity.getString(R.string.ClearButton))

		searchContainer?.addView(clearButton, createFrame(48, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL or Gravity.RIGHT))

		backButton = ImageView(activity)
		backButton!!.scaleType = ImageView.ScaleType.CENTER

		backDrawable = BackDrawable(false)
		backDrawable!!.setAnimationTime(200.0f)
		backDrawable!!.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
		backDrawable!!.setRotatedColor(-0x4c4c4d)
		backDrawable!!.setRotation(1.0f, false)

		backButton!!.setImageDrawable(backDrawable)
		backButton!!.background = Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR)

		headerView?.addView(backButton, createFrame(54, 56f))

		backButton?.setOnClickListener {
			/*if (collapsed) {
                uncollapse();
            } else {
                collapse();
            }*/

			if (searchContainer?.tag != null) {
				showSearch(false)
			}
			else {
				close(true, true)
			}
		}

		backButton?.contentDescription = activity.getString(R.string.AccDescrGoBack)

		menuButton = object : ActionBarMenuItem(activity, null, Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, -0x4c4c4d) {
			override fun toggleSubMenu() {
				super.toggleSubMenu()
				listView?.get(0)?.stopScroll()
				checkScrollAnimated()
			}
		}

		menuButton?.setLayoutInScreen(true)
		menuButton?.isDuplicateParentStateEnabled = false
		menuButton?.isClickable = true
		menuButton?.setIcon(R.drawable.overflow_menu)
		menuButton?.addSubItem(search_item, R.drawable.msg_search, activity.getString(R.string.Search))
		menuButton?.addSubItem(share_item, R.drawable.msg_share, activity.getString(R.string.ShareFile))
		menuButton?.addSubItem(open_item, R.drawable.msg_openin, activity.getString(R.string.OpenInExternalApp))
		menuButton?.addSubItem(settings_item, R.drawable.msg_settings_old, activity.getString(R.string.Settings))
		menuButton?.background = Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR)
		menuButton?.setContentDescription(activity.getString(R.string.AccDescrMoreOptions))

		menuContainer?.addView(menuButton, createFrame(48, 56f))

		progressView = ContextProgressView(activity, 2)
		progressView!!.visibility = View.GONE

		menuContainer?.addView(progressView, createFrame(48, 56f))

		menuButton?.setOnClickListener { menuButton?.toggleSubMenu() }

		menuButton?.setDelegate(ActionBarMenuItemDelegate { id ->
			if (adapter!![0].currentPage == null || parentActivity == null) {
				return@ActionBarMenuItemDelegate
			}

			if (id == search_item) {
				showSearch(true)
			}
			else if (id == share_item) {
				showDialog(ShareAlert(activity, null, adapter!![0].currentPage!!.url, false, adapter!![0].currentPage!!.url, false))
			}
			else if (id == open_item) {
				val webPageUrl = if (!TextUtils.isEmpty(adapter!![0].currentPage.cachedPage?.url)) {
					adapter!![0].currentPage.cachedPage?.url
				}
				else {
					adapter!![0].currentPage?.url
				}

				Browser.openUrl(activity, webPageUrl, true, false)
			}
			else if (id == settings_item) {
				val builder = BottomSheet.Builder(activity)
				builder.setApplyTopPadding(false)

				val settingsContainer = LinearLayout(activity)
				settingsContainer.setPadding(0, 0, 0, AndroidUtilities.dp(4f))
				settingsContainer.orientation = LinearLayout.VERTICAL

				var headerCell = HeaderCell(activity)
				headerCell.setText(activity.getString(R.string.FontSize))
				settingsContainer.addView(headerCell, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP, 3, 1, 3, 0))

				val sizeCell: TextSizeCell = TextSizeCell(activity)
				settingsContainer.addView(sizeCell, createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP, 3, 0, 3, 0))

				headerCell = HeaderCell(activity)
				headerCell.setText(activity.getString(R.string.FontType))
				settingsContainer.addView(headerCell, createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT or Gravity.TOP, 3, 4, 3, 2))

				for (a in 0..1) {
					fontCells[a] = FontCell(activity)

					when (a) {
						0 -> fontCells[a]!!.setTextAndTypeface(activity.getString(R.string.Default), Theme.TYPEFACE_DEFAULT)

						1 -> fontCells[a]!!.setTextAndTypeface("Serif", Typeface.SERIF)
					}

					fontCells[a]!!.select(a == selectedFont, false)
					fontCells[a]!!.tag = a

					fontCells[a]!!.setOnClickListener { v ->
						val num = v.tag as Int
						selectedFont = num

						for (a1 in 0..1) {
							fontCells[a1]!!.select(a1 == num, true)
						}
						updatePaintFonts()

						adapter?.forEach {
							it.notifyDataSetChanged()
						}
					}

					settingsContainer.addView(fontCells[a], createLinear(LayoutHelper.MATCH_PARENT, 50))
				}

				builder.setCustomView(settingsContainer)
				showDialog(builder.create().also { linkSheet = it })
			}
		})

		searchPanel = object : FrameLayout(activity) {
			public override fun onDraw(canvas: Canvas) {
				val bottom = Theme.chat_composeShadowDrawable.intrinsicHeight
				Theme.chat_composeShadowDrawable.setBounds(0, 0, measuredWidth, bottom)
				Theme.chat_composeShadowDrawable.draw(canvas)
				canvas.drawRect(0f, bottom.toFloat(), measuredWidth.toFloat(), measuredHeight.toFloat(), Theme.chat_composeBackgroundPaint)
			}
		}

		searchPanel?.setOnTouchListener { _, _ -> true }
		searchPanel?.setWillNotDraw(false)
		searchPanel?.visibility = View.INVISIBLE
		searchPanel?.isFocusable = true
		searchPanel?.setFocusableInTouchMode(true)
		searchPanel?.isClickable = true
		searchPanel?.setPadding(0, AndroidUtilities.dp(3f), 0, 0)

		containerView?.addView(searchPanel, createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM))

		searchUpButton = ImageView(activity)
		searchUpButton!!.scaleType = ImageView.ScaleType.CENTER
		searchUpButton!!.setImageResource(R.drawable.msg_go_up)
		searchUpButton!!.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY)
		searchUpButton!!.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1))

		searchPanel?.addView(searchUpButton, createFrame(48, 48f, Gravity.RIGHT or Gravity.TOP, 0f, 0f, 48f, 0f))

		searchUpButton?.setOnClickListener {
			scrollToSearchIndex(currentSearchIndex - 1)
		}

		searchUpButton?.contentDescription = activity.getString(R.string.AccDescrSearchNext)

		searchDownButton = ImageView(activity)
		searchDownButton!!.scaleType = ImageView.ScaleType.CENTER
		searchDownButton!!.setImageResource(R.drawable.msg_go_down)
		searchDownButton!!.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY)
		searchDownButton!!.background = Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1)

		searchPanel?.addView(searchDownButton, createFrame(48, 48f, Gravity.RIGHT or Gravity.TOP, 0f, 0f, 0f, 0f))

		searchDownButton?.setOnClickListener {
			scrollToSearchIndex(currentSearchIndex + 1)
		}

		searchDownButton?.contentDescription = activity.getString(R.string.AccDescrSearchPrev)

		searchCountText = SimpleTextView(activity)
		searchCountText!!.textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
		searchCountText!!.setTextSize(15)
		searchCountText!!.setTypeface(Theme.TYPEFACE_BOLD)
		searchCountText!!.setGravity(Gravity.LEFT)

		searchPanel?.addView(searchCountText, createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT.toFloat(), Gravity.LEFT or Gravity.CENTER_VERTICAL, 18f, 0f, 108f, 0f))

		windowLayoutParams = WindowManager.LayoutParams()
		windowLayoutParams!!.height = WindowManager.LayoutParams.MATCH_PARENT
		windowLayoutParams!!.format = PixelFormat.TRANSLUCENT
		windowLayoutParams!!.width = WindowManager.LayoutParams.MATCH_PARENT
		windowLayoutParams!!.gravity = Gravity.TOP or Gravity.LEFT
		windowLayoutParams!!.type = WindowManager.LayoutParams.LAST_APPLICATION_WINDOW - 1
		windowLayoutParams!!.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
		windowLayoutParams!!.flags = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM

		var uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
		val navigationColor = Theme.getColor(Theme.key_windowBackgroundGray, null, true)
		val navigationBrightness = AndroidUtilities.computePerceivedBrightness(navigationColor)
		val isLightNavigation = navigationBrightness >= 0.721f

		if (isLightNavigation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			uiFlags = uiFlags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
			navigationBarPaint.color = navigationColor
		}
		else if (!isLightNavigation) {
			navigationBarPaint.color = navigationColor
		}

		windowLayoutParams!!.systemUiVisibility = uiFlags

		windowLayoutParams?.flags = windowLayoutParams!!.flags or (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

		if (Build.VERSION.SDK_INT >= 28) {
			windowLayoutParams?.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
		}

		textSelectionHelper = ArticleTextSelectionHelper()
		textSelectionHelper!!.setParentView(listView!!.get(0))

		if (MessagesController.getGlobalMainSettings().getBoolean("translate_button", false)) {
			textSelectionHelper?.setOnTranslate { text, fromLang, toLang, onAlertDismiss ->
				TranslateAlert.showAlert(parentActivity, parentFragment, fromLang, toLang, text, false, null, onAlertDismiss)
			}
		}

		textSelectionHelper!!.layoutManager = layoutManager!![0]

		textSelectionHelper!!.setCallback(object : TextSelectionHelper.Callback() {
			override fun onStateChanged(isSelected: Boolean) {
				if (isSelected) {
					showSearch(false)
				}
			}

			override fun onTextCopied() {
				if (AndroidUtilities.shouldShowClipboardToast()) {
					of(containerView!!).createCopyBulletin(activity.getString(R.string.TextCopied)).show()
				}
			}
		})

		containerView?.addView(textSelectionHelper!!.getOverlayView(activity))

		pinchToZoomHelper = PinchToZoomHelper(containerView, windowView)

		pinchToZoomHelper!!.setClipBoundsListener { topBottom ->
			topBottom[0] = currentHeaderHeight.toFloat()
			topBottom[1] = listView?.get(0)?.measuredHeight?.toFloat() ?: 0f
		}

		pinchToZoomHelper?.setCallback(object : PinchToZoomHelper.Callback {
			override fun onZoomStarted(messageObject: MessageObject?) {
				listView?.get(0)?.cancelClickRunnables(true)
			}
		})

		updatePaintColors()
	}

	private fun showSearch(show: Boolean) {
		val searchContainer = searchContainer ?: return

		if ((searchContainer.tag != null) == show) {
			return
		}

		searchContainer.tag = if (show) 1 else null
		searchResults?.clear()
		searchText = null
		adapter?.get(0)?.searchTextOffset?.clear()
		currentSearchIndex = 0

		if (attachedToWindow) {
			val animatorSet = AnimatorSet()
			animatorSet.setDuration(250)

			if (show) {
				searchContainer.visibility = View.VISIBLE
				backDrawable?.setRotation(0.0f, true)
			}
			else {
				menuButton!!.visibility = View.VISIBLE
				listView!![0].invalidateViews()
				AndroidUtilities.hideKeyboard(searchField)
				updateWindowLayoutParamsForSearch()
			}

			val animators = ArrayList<Animator>()

			if (show) {
				searchContainer.alpha = 1.0f
			}

			val x = menuContainer!!.left + menuContainer!!.measuredWidth / 2
			val y = menuContainer!!.top + menuContainer!!.measuredHeight / 2
			val rad = sqrt((x * x + y * y).toDouble()).toFloat()

			val animator = ViewAnimationUtils.createCircularReveal(searchContainer, x, y, if (show) 0f else rad, if (show) rad else 0f)

			animators.add(animator)

			animator.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (!show) {
						searchContainer.alpha = 0.0f
					}
				}
			})

			if (!show) {
				animators.add(ObjectAnimator.ofFloat(searchPanel, View.ALPHA, 0.0f))
			}

			animators.add(ObjectAnimator.ofFloat(searchShadow, View.ALPHA, if (show) 1.0f else 0.0f))
			animatorSet.playTogether(animators)

			animatorSet.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (show) {
						updateWindowLayoutParamsForSearch()
						searchField!!.requestFocus()
						AndroidUtilities.showKeyboard(searchField)
						menuButton!!.visibility = View.INVISIBLE
					}
					else {
						searchContainer.visibility = View.INVISIBLE
						searchPanel!!.visibility = View.INVISIBLE
						searchField!!.setText("")
					}
				}

				override fun onAnimationStart(animation: Animator) {
					if (!show) {
						backDrawable!!.setRotation(1.0f, true)
					}
				}
			})

			animatorSet.interpolator = CubicBezierInterpolator.EASE_OUT

			if (!show && !AndroidUtilities.usingHardwareInput && keyboardVisible) {
				runAfterKeyboardClose = animatorSet

				AndroidUtilities.runOnUIThread({
					runAfterKeyboardClose?.start()
					runAfterKeyboardClose = null
				}, 300)
			}
			else {
				animatorSet.start()
			}
		}
		else {
			searchContainer.alpha = if (show) 1.0f else 0.0f
			menuButton!!.visibility = if (show) View.INVISIBLE else View.VISIBLE
			backDrawable!!.setRotation(if (show) 0.0f else 1.0f, false)
			searchShadow!!.alpha = if (show) 1.0f else 0.0f

			if (show) {
				searchContainer.visibility = View.VISIBLE
			}
			else {
				searchContainer.visibility = View.INVISIBLE
				searchPanel!!.visibility = View.INVISIBLE
				searchField!!.setText("")
			}

			updateWindowLayoutParamsForSearch()
		}
	}

	private fun updateWindowLayoutParamsForSearch() {/*try {
            WindowManager wm = (WindowManager) parentActivity.getSystemService(Context.WINDOW_SERVICE);
            if (searchContainer.getTag() != null) {
                windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            } else {
                windowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
            }
            wm.updateViewLayout(windowView, windowLayoutParams);
        } catch (Exception e) {
            FileLog.e(e);
        }*/
	}

	private fun updateSearchButtons() {
		val searchResults = searchResults ?: return
		val context = parentActivity ?: return

		searchUpButton?.isEnabled = !searchResults.isEmpty() && currentSearchIndex != 0
		searchDownButton?.isEnabled = !searchResults.isEmpty() && currentSearchIndex != searchResults.size - 1
		searchUpButton?.alpha = if (searchUpButton!!.isEnabled) 1.0f else 0.5f
		searchDownButton?.alpha = if (searchDownButton!!.isEnabled) 1.0f else 0.5f

		val count = searchResults.size

		if (count == 0) {
			searchCountText?.setText(context.getString(R.string.NoResult))
		}
		else if (count == 1) {
			searchCountText?.setText(context.getString(R.string.OneResult))
		}
		else {
			searchCountText?.setText(String.format(LocaleController.getPluralString("CountOfResults", count), currentSearchIndex + 1, count))
		}
	}

	private var searchRunnable: Runnable? = null
	private var searchResults: ArrayList<SearchResult>? = ArrayList<SearchResult>()
	private var searchText: String? = null
	private var currentSearchIndex = 0
	private var lastSearchIndex = -1

	private class SearchResult {
		var index: Int = 0
		var text: Any? = null
		var block: PageBlock? = null
	}

	private fun processSearch(text: String?) {
		if (searchRunnable != null) {
			AndroidUtilities.cancelRunOnUIThread(searchRunnable)
			searchRunnable = null
		}

		if (text.isNullOrEmpty()) {
			searchResults?.clear()
			searchText = text
			adapter?.get(0)?.searchTextOffset?.clear()
			searchPanel!!.visibility = View.INVISIBLE
			listView!![0].invalidateViews()
			scrollToSearchIndex(0)
			lastSearchIndex = -1
			return
		}

		val searchIndex = ++lastSearchIndex

		AndroidUtilities.runOnUIThread(Runnable {
			val copy = HashMap(adapter!![0].textToBlocks)
			val array = ArrayList(adapter!![0].textBlocks)

			searchRunnable = null

			Utilities.searchQueue.postRunnable {
				val results = ArrayList<SearchResult>()
				var b = 0
				val N = array.size

				while (b < N) {
					val `object` = array[b]
					val block = copy[`object`]
					var textToSearchIn: String? = null

					if (`object` is RichText) {
						val innerText = getText(adapter!![0], null, `object`, `object`, block, 1000)

						if (!innerText.isNullOrEmpty()) {
							textToSearchIn = innerText.toString().lowercase()
						}
					}
					else if (`object` is String) {
						textToSearchIn = `object`.lowercase()
					}

					if (textToSearchIn != null) {
						var startIndex = 0
						var index: Int

						while ((textToSearchIn.indexOf(text, startIndex).also { index = it }) >= 0) {
							startIndex = index + text.length

							if (index == 0 || AndroidUtilities.isPunctuationCharacter(textToSearchIn[index - 1])) {
								val result = SearchResult()
								result.index = index
								result.block = block
								result.text = `object`
								results.add(result)
							}
						}
					}

					b++
				}

				AndroidUtilities.runOnUIThread {
					if (searchIndex == lastSearchIndex) {
						searchPanel!!.alpha = 1.0f
						searchPanel!!.visibility = View.VISIBLE
						searchResults = results
						searchText = text
						adapter!![0].searchTextOffset.clear()
						listView!![0].invalidateViews()
						scrollToSearchIndex(0)
					}
				}
			}
		}.also { searchRunnable = it }, 400)
	}

	private fun scrollToSearchIndex(index: Int) {
		val searchResults = searchResults ?: return
		val adapter = adapter ?: return

		if (index < 0 || index >= searchResults.size) {
			updateSearchButtons()
			return
		}

		currentSearchIndex = index
		updateSearchButtons()

		val result = searchResults[index]
		val block = getLastNonListPageBlock(result.block)

		var row = -1

		run {
			var a = 0
			val N = adapter[0].blocks.size
			while (a < N) {
				val localBlock = adapter[0].blocks[a]
				if (localBlock is TLPageBlockDetailsChild) {
					if (localBlock.block === result.block || localBlock.block === block) {
						if (openAllParentBlocks(localBlock)) {
							adapter[0].updateRows()
							adapter[0].notifyDataSetChanged()
						}
						break
					}
				}
				a++
			}
		}

		var a = 0
		val N = adapter[0].localBlocks.size

		while (a < N) {
			val localBlock = adapter[0].localBlocks[a]

			if (localBlock === result.block || localBlock === block) {
				row = a
				break
			}
			else if (localBlock is TLPageBlockDetailsChild) {
				if (localBlock.block === result.block || localBlock.block === block) {
					row = a
					break
				}
			}
			a++
		}

		if (row == -1) {
			return
		}

		if (block is TLPageBlockDetailsChild) {
			if (openAllParentBlocks(block)) {
				adapter[0].updateRows()
				adapter[0].notifyDataSetChanged()
			}
		}

		val key = searchText + result.block + result.text + result.index
		var offset = adapter[0].searchTextOffset[key]

		if (offset == null) {
			val type = adapter[0].getTypeForBlock(result.block)
			val holder = adapter[0].onCreateViewHolder(listView!![0], type)
			adapter[0].bindBlockToHolder(type, holder, result.block, 0, 0)
			holder.itemView.measure(MeasureSpec.makeMeasureSpec(listView!![0].measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
			offset = adapter[0].searchTextOffset[key]
			if (offset == null) {
				offset = 0
			}
		}

		layoutManager!![0].scrollToPositionWithOffset(row, currentHeaderHeight - AndroidUtilities.dp(56f) - offset + AndroidUtilities.dp(100f))
		listView!![0].invalidateViews()
	}

	class ScrollEvaluator : IntEvaluator() {
		override fun evaluate(fraction: Float, startValue: Int, endValue: Int): Int {
			return super.evaluate(fraction, startValue, endValue)
		}
	}

	private fun checkScrollAnimated() {
		val maxHeight = AndroidUtilities.dp(56f)
		if (currentHeaderHeight == maxHeight) {
			return
		}
		val va = ValueAnimator.ofObject(IntEvaluator(), currentHeaderHeight, AndroidUtilities.dp(56f)).setDuration(180)
		va.interpolator = DecelerateInterpolator()
		va.addUpdateListener { animation: ValueAnimator -> setCurrentHeaderHeight(animation.animatedValue as Int) }
		va.start()
	}

	private fun setCurrentHeaderHeight(newHeight: Int) {
		var newHeight = newHeight
		if (searchContainer!!.tag != null) {
			return
		}
		val maxHeight = AndroidUtilities.dp(56f)
		val minHeight = max(AndroidUtilities.statusBarHeight.toDouble(), AndroidUtilities.dp(24f).toDouble()).toInt()

		if (newHeight < minHeight) {
			newHeight = minHeight
		}
		else if (newHeight > maxHeight) {
			newHeight = maxHeight
		}

		var heightDiff = (maxHeight - minHeight).toFloat()
		if (heightDiff == 0f) {
			heightDiff = 1f
		}

		currentHeaderHeight = newHeight
		val scale = 0.8f + (currentHeaderHeight - minHeight) / heightDiff * 0.2f
		val scale2 = 0.5f + (currentHeaderHeight - minHeight) / heightDiff * 0.5f
		backButton!!.scaleX = scale
		backButton!!.scaleY = scale
		backButton!!.translationY = ((maxHeight - currentHeaderHeight) / 2).toFloat()
		menuContainer!!.scaleX = scale
		menuContainer!!.scaleY = scale
		titleTextView!!.scaleX = scale
		titleTextView!!.scaleY = scale
		lineProgressView!!.scaleY = scale2
		menuContainer!!.translationY = ((maxHeight - currentHeaderHeight) / 2).toFloat()
		titleTextView!!.translationY = ((maxHeight - currentHeaderHeight) / 2).toFloat()
		headerView!!.translationY = (currentHeaderHeight - maxHeight).toFloat()
		searchShadow!!.translationY = (currentHeaderHeight - maxHeight).toFloat()
		menuButton!!.setAdditionalYOffset(-(currentHeaderHeight - maxHeight) / 2 + (if (Build.VERSION.SDK_INT < 21) AndroidUtilities.statusBarHeight else 0))
		textSelectionHelper!!.setTopOffset(currentHeaderHeight)
		for (recyclerListView in listView!!) {
			recyclerListView.topGlowOffset = currentHeaderHeight
		}
	}

	private fun checkScroll(dy: Int) {
		setCurrentHeaderHeight(currentHeaderHeight - dy)
	}

	private fun openPreviewsChat(user: TLRPC.User?, wid: Long) {
		if (user == null || parentActivity !is LaunchActivity) {
			return
		}
		val args = Bundle()
		args.putLong("user_id", user.id)
		args.putString("botUser", "webpage$wid")
		(parentActivity as LaunchActivity).presentFragment(ChatActivity(args), false, true)
		close(false, true)
	}

	fun open(messageObject: MessageObject?): Boolean {
		return open(messageObject, null, null, true)
	}

	fun open(webpage: TLWebPage?, url: String?): Boolean {
		return open(null, webpage, url, true)
	}

	private fun open(messageObject: MessageObject?, webpage: WebPage?, url: String?, first: Boolean): Boolean {
		var webpage = webpage
		var url = url

		if (parentActivity == null || isVisible && !collapsed || messageObject == null && webpage == null) {
			return false
		}

		var anchor: String? = null

		if (messageObject != null) {
			webpage = messageObject.messageOwner.media.webpage

			val entities = messageObject.messageOwner?.entities

			if (!entities.isNullOrEmpty()) {
				var index: Int

				for (entity in entities) {
					if (entity is TLRPC.TLMessageEntityUrl) {
						try {
							url = messageObject.messageOwner.message?.substring(entity.offset, entity.offset + entity.length)?.lowercase() ?: ""

							val webPageUrl = if (!webpage?.cachedPage?.url.isNullOrEmpty()) {
								webpage.cachedPage?.url?.lowercase()
							}
							else {
								webpage?.url?.lowercase()
							} ?: ""

							if (url.contains(webPageUrl) || webPageUrl.contains(url)) {
								if ((url.lastIndexOf('#').also { index = it }) != -1) {
									anchor = url.substring(index + 1)
								}

								break
							}
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}
			}
		}
		else if (url != null) {
			val index: Int

			if ((url.lastIndexOf('#').also { index = it }) != -1) {
				anchor = url.substring(index + 1)
			}
		}

		pagesStack.clear()
		collapsed = false
		containerView!!.translationX = 0f
		containerView!!.translationY = 0f
		listView!![0].translationY = 0f
		listView!![0].translationX = 0.0f
		listView!![1].translationX = 0.0f
		listView!![0].alpha = 1.0f
		windowView!!.setInnerTranslationX(0f)

		layoutManager!![0].scrollToPositionWithOffset(0, 0)
		if (first) {
			setCurrentHeaderHeight(AndroidUtilities.dp(56f))
		}
		else {
			checkScrollAnimated()
		}

		val scrolledToAnchor = if (webpage != null) {
			addPageToStack(webpage, anchor, 0)
		}
		else {
			false
		}

		if (first) {
			val anchorFinal = if (!scrolledToAnchor && anchor != null) anchor else null
			val req = TLMessagesGetWebPage()
			req.url = webpage?.url

			if (webpage.cachedPage?.part == true) {
				req.hash = 0
			}
			else {
				req.hash = webpage?.hash ?: 0
			}

			val webPageFinal = webpage
			val currentAccount = UserConfig.selectedAccount

			ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, _ ->
				AndroidUtilities.runOnUIThread {
					if (response is TLWebPage) {
						if (response.cachedPage == null) {
							return@runOnUIThread
						}

						if (!pagesStack.isEmpty() && pagesStack[0] === webPageFinal) {
							if (messageObject != null) {
								messageObject.messageOwner?.media?.webpage = response

								val messagesRes = TLRPC.TLMessagesMessages()
								messagesRes.messages.add(messageObject.messageOwner!!)

								MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, messageObject.dialogId, -2, 0, false, messageObject.scheduled)
							}
							pagesStack[0] = response

							if (pagesStack.size == 1) {
								ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit { remove("article" + response.id) }

								updateInterfaceForCurrentPage(response, false, 0)

								if (anchorFinal != null) {
									scrollToAnchor(anchorFinal)
								}
							}
						}

						val webpages = LongSparseArray<WebPage>(1)
						webpages.put(response.id, response)

						MessagesStorage.getInstance(currentAccount).putWebPages(webpages)
					}
					else if (response is TLWebPageNotModified) {
						if (webPageFinal != null && webPageFinal.cachedPage != null) {
							if (webPageFinal.cachedPage?.views != response.cachedPageViews) {
								webPageFinal.cachedPage?.views = response.cachedPageViews
								webPageFinal.cachedPage?.flags = webPageFinal.cachedPage!!.flags or 8

								adapter?.let { adapter ->
									for (a in adapter.indices) {
										if (adapter[a].currentPage === webPageFinal) {
											val p = adapter[a].itemCount - 1
											val holder = listView!![a].findViewHolderForAdapterPosition(p)

											if (holder != null) {
												adapter[a].onViewAttachedToWindow(holder)
											}
										}
									}
								}

								if (messageObject != null) {
									val messagesRes = TLRPC.TLMessagesMessages()
									messagesRes.messages.add(messageObject.messageOwner!!)

									MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, messageObject.dialogId, -2, 0, false, messageObject.scheduled)
								}
							}
						}
					}
				}
			}
		}

		lastInsets = null

		if (!isVisible) {
			val wm = parentActivity!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager

			if (attachedToWindow) {
				try {
					wm.removeView(windowView)
				}
				catch (e: Exception) {
					// ignored
				}
			}
			try {
				windowLayoutParams!!.flags = WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION

				if (Build.VERSION.SDK_INT >= 28) {
					windowLayoutParams!!.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
				}

				windowView!!.isFocusable = false
				containerView!!.isFocusable = false

				wm.addView(windowView, windowLayoutParams)
			}
			catch (e: Exception) {
				FileLog.e(e)
				return false
			}
		}
		else {
			windowLayoutParams!!.flags = windowLayoutParams!!.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
			val wm = parentActivity!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
			wm.updateViewLayout(windowView, windowLayoutParams)
		}

		isVisible = true
		animationInProgress = 1
		windowView!!.alpha = 0f
		containerView!!.alpha = 0f

		val animatorSet = AnimatorSet()
		animatorSet.playTogether(ObjectAnimator.ofFloat(windowView, View.ALPHA, 0f, 1.0f), ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.0f, 1.0f), ObjectAnimator.ofFloat(windowView, View.TRANSLATION_X, AndroidUtilities.dp(56f).toFloat(), 0f))

		animationEndRunnable = Runnable {
			if (containerView == null || windowView == null) {
				return@Runnable
			}
			if (Build.VERSION.SDK_INT >= 18) {
				containerView!!.setLayerType(View.LAYER_TYPE_NONE, null)
			}
			animationInProgress = 0
			AndroidUtilities.hideKeyboard(parentActivity!!.currentFocus)
		}

		animatorSet.setDuration(150)
		animatorSet.interpolator = interpolator
		animatorSet.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				AndroidUtilities.runOnUIThread {
					NotificationCenter.getInstance(currentAccount).onAnimationFinish(allowAnimationIndex)
					if (animationEndRunnable != null) {
						animationEndRunnable!!.run()
						animationEndRunnable = null
					}
				}
			}
		})
		transitionAnimationStartTime = System.currentTimeMillis()
		AndroidUtilities.runOnUIThread {
			allowAnimationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(allowAnimationIndex, intArrayOf(NotificationCenter.dialogsNeedReload, NotificationCenter.closeChats))
			animatorSet.start()
		}
		if (Build.VERSION.SDK_INT >= 18) {
			containerView!!.setLayerType(View.LAYER_TYPE_HARDWARE, null)
		}
		return true
	}

	private fun showProgressView(useLine: Boolean, show: Boolean) {
		if (useLine) {
			AndroidUtilities.cancelRunOnUIThread(lineProgressTickRunnable)
			if (show) {
				lineProgressView!!.setProgress(0.0f, false)
				lineProgressView!!.setProgress(0.3f, true)
				AndroidUtilities.runOnUIThread(lineProgressTickRunnable, 100)
			}
			else {
				lineProgressView!!.setProgress(1.0f, true)
			}
		}
		else {
			if (progressViewAnimation != null) {
				progressViewAnimation!!.cancel()
			}
			progressViewAnimation = AnimatorSet()
			if (show) {
				progressView!!.visibility = View.VISIBLE
				menuContainer!!.isEnabled = false
				progressViewAnimation!!.playTogether(ObjectAnimator.ofFloat(menuButton, View.SCALE_X, 0.1f), ObjectAnimator.ofFloat(menuButton, View.SCALE_Y, 0.1f), ObjectAnimator.ofFloat(menuButton, View.ALPHA, 0.0f), ObjectAnimator.ofFloat(progressView, View.SCALE_X, 1.0f), ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 1.0f), ObjectAnimator.ofFloat(progressView, View.ALPHA, 1.0f))
			}
			else {
				menuButton!!.visibility = View.VISIBLE
				menuContainer!!.isEnabled = true
				progressViewAnimation!!.playTogether(ObjectAnimator.ofFloat(progressView, View.SCALE_X, 0.1f), ObjectAnimator.ofFloat(progressView, View.SCALE_Y, 0.1f), ObjectAnimator.ofFloat(progressView, View.ALPHA, 0.0f), ObjectAnimator.ofFloat(menuButton, View.SCALE_X, 1.0f), ObjectAnimator.ofFloat(menuButton, View.SCALE_Y, 1.0f), ObjectAnimator.ofFloat(menuButton, View.ALPHA, 1.0f))
			}
			progressViewAnimation!!.addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (progressViewAnimation != null && progressViewAnimation == animation) {
						if (!show) {
							progressView!!.visibility = View.INVISIBLE
						}
						else {
							menuButton!!.visibility = View.INVISIBLE
						}
					}
				}

				override fun onAnimationCancel(animation: Animator) {
					if (progressViewAnimation != null && progressViewAnimation == animation) {
						progressViewAnimation = null
					}
				}
			})
			progressViewAnimation!!.setDuration(150)
			progressViewAnimation!!.start()
		}
	}

	fun collapse() {
		if (parentActivity == null || !isVisible || checkAnimation()) {
			return
		}
		if (fullscreenVideoContainer!!.visibility == View.VISIBLE) {
			if (customView != null) {
				fullscreenVideoContainer!!.visibility = View.INVISIBLE
				customViewCallback!!.onCustomViewHidden()
				fullscreenVideoContainer!!.removeView(customView)
				customView = null
			}
			else if (fullscreenedVideo != null) {
				fullscreenedVideo!!.exitFullscreen()
			}
		}
		try {
			if (visibleDialog != null) {
				visibleDialog!!.dismiss()
				visibleDialog = null
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		val animatorSet = AnimatorSet()
		animatorSet.playTogether(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, (containerView!!.measuredWidth - AndroidUtilities.dp(56f)).toFloat()), ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, (ActionBar.getCurrentActionBarHeight() + (AndroidUtilities.statusBarHeight)).toFloat()), ObjectAnimator.ofFloat(windowView, View.ALPHA, 0.0f), ObjectAnimator.ofFloat(listView!![0], View.ALPHA, 0.0f), ObjectAnimator.ofFloat(listView!![0], View.TRANSLATION_Y, -AndroidUtilities.dp(56f).toFloat()), ObjectAnimator.ofFloat(headerView, View.TRANSLATION_Y, 0f), ObjectAnimator.ofFloat(backButton, View.SCALE_X, 1.0f), ObjectAnimator.ofFloat(backButton, View.SCALE_Y, 1.0f), ObjectAnimator.ofFloat(backButton, View.TRANSLATION_Y, 0f), ObjectAnimator.ofFloat(menuContainer, View.SCALE_X, 1.0f), ObjectAnimator.ofFloat(menuContainer, View.TRANSLATION_Y, 0f), ObjectAnimator.ofFloat(menuContainer, View.SCALE_Y, 1.0f))

		collapsed = true
		animationInProgress = 2

		animationEndRunnable = Runnable {
			if (containerView == null) {
				return@Runnable
			}

			containerView?.setLayerType(View.LAYER_TYPE_NONE, null)

			animationInProgress = 0

			val wm = parentActivity!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
			wm.updateViewLayout(windowView, windowLayoutParams)
		}

		animatorSet.interpolator = DecelerateInterpolator()
		animatorSet.setDuration(250)

		animatorSet.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				animationEndRunnable?.run()
				animationEndRunnable = null
			}
		})

		transitionAnimationStartTime = System.currentTimeMillis()

		containerView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
		backDrawable?.setRotation(1f, true)

		animatorSet.start()
	}

	fun uncollapse() {
		if (parentActivity == null || !isVisible || checkAnimation()) {
			return
		}

		val animatorSet = AnimatorSet()
		animatorSet.playTogether(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, 0f), ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0f), ObjectAnimator.ofFloat(windowView, View.ALPHA, 1.0f), ObjectAnimator.ofFloat(listView!![0], View.ALPHA, 1.0f), ObjectAnimator.ofFloat(listView!![0], View.TRANSLATION_Y, 0f), ObjectAnimator.ofFloat(headerView, View.TRANSLATION_Y, 0f), ObjectAnimator.ofFloat(backButton, View.SCALE_X, 1.0f), ObjectAnimator.ofFloat(backButton, View.SCALE_Y, 1.0f), ObjectAnimator.ofFloat(backButton, View.TRANSLATION_Y, 0f), ObjectAnimator.ofFloat(menuContainer, View.SCALE_X, 1.0f), ObjectAnimator.ofFloat(menuContainer, View.TRANSLATION_Y, 0f), ObjectAnimator.ofFloat(menuContainer, View.SCALE_Y, 1.0f))

		collapsed = false
		animationInProgress = 2

		animationEndRunnable = Runnable {
			if (containerView == null) {
				return@Runnable
			}

			containerView?.setLayerType(View.LAYER_TYPE_NONE, null)

			animationInProgress = 0
		}

		animatorSet.setDuration(250)
		animatorSet.interpolator = DecelerateInterpolator()

		animatorSet.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				animationEndRunnable?.run()
				animationEndRunnable = null
			}
		})

		transitionAnimationStartTime = System.currentTimeMillis()

		containerView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
		backDrawable?.setRotation(0f, true)

		animatorSet.start()
	}

	private fun saveCurrentPagePosition() {
		val adapter = adapter ?: return

		if (adapter[0].currentPage == null) {
			return
		}
		val position = layoutManager!![0].findFirstVisibleItemPosition()

		if (position != RecyclerView.NO_POSITION) {
			val offset: Int
			val view = layoutManager!![0].findViewByPosition(position)
			offset = view?.top ?: 0

			ApplicationLoader.applicationContext.getSharedPreferences("articles", Activity.MODE_PRIVATE).edit {
				val key = "article" + adapter[0].currentPage!!.id
				putInt(key, position).putInt(key + "o", offset).putBoolean(key + "r", AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y)
			}
		}
	}

	private fun refreshThemeColors() {
		deleteView?.background = Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2)
		deleteView?.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem))

		popupLayout?.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground))

		searchContainer?.setBackgroundColor(parentActivity!!.getColor(R.color.background))

		searchField?.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
		searchField?.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
		searchField?.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText))

		searchUpButton?.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY)
		searchUpButton?.background = Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1)


		searchDownButton?.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY)
		searchDownButton?.background = Theme.createSelectorDrawable(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), 1)


		searchCountText?.textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)


		menuButton?.redrawPopup(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground))
		menuButton?.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem), false)
		menuButton?.setPopupItemsColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), true)

		clearButton?.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.MULTIPLY)

		backDrawable?.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))

	}

	fun close(byBackPress: Boolean, force: Boolean) {
		if (parentActivity == null || closeAnimationInProgress || !isVisible || checkAnimation()) {
			return
		}
		if (fullscreenVideoContainer!!.visibility == View.VISIBLE) {
			if (customView != null) {
				fullscreenVideoContainer!!.visibility = View.INVISIBLE
				customViewCallback!!.onCustomViewHidden()
				fullscreenVideoContainer!!.removeView(customView)
				customView = null
			}
			else if (fullscreenedVideo != null) {
				fullscreenedVideo!!.exitFullscreen()
			}
			if (!force) {
				return
			}
		}
		if (textSelectionHelper!!.isSelectionMode) {
			textSelectionHelper!!.clear()
			return
		}
		if (searchContainer!!.tag != null) {
			showSearch(false)
			return
		}
		if (openUrlReqId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(openUrlReqId, true)
			openUrlReqId = 0
			showProgressView(true, false)
		}
		if (previewsReqId != 0) {
			ConnectionsManager.getInstance(currentAccount).cancelRequest(previewsReqId, true)
			previewsReqId = 0
			showProgressView(true, false)
		}
		saveCurrentPagePosition()
		if (byBackPress && !force) {
			if (removeLastPageFromStack()) {
				return
			}
		}

		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged)
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidReset)
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
		NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.messagePlayingDidStart)
		parentFragment = null
		try {
			if (visibleDialog != null) {
				visibleDialog!!.dismiss()
				visibleDialog = null
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		val animatorSet = AnimatorSet()
		animatorSet.playTogether(ObjectAnimator.ofFloat(windowView, View.ALPHA, 0f), ObjectAnimator.ofFloat(containerView, View.ALPHA, 0.0f), ObjectAnimator.ofFloat(windowView, View.TRANSLATION_X, 0f, AndroidUtilities.dp(56f).toFloat()))
		animationInProgress = 2
		animationEndRunnable = Runnable {
			if (containerView == null) {
				return@Runnable
			}
			containerView!!.setLayerType(View.LAYER_TYPE_NONE, null)
			animationInProgress = 0
			onClosed()
		}
		animatorSet.setDuration(150)
		animatorSet.interpolator = interpolator
		animatorSet.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				if (animationEndRunnable != null) {
					animationEndRunnable!!.run()
					animationEndRunnable = null
				}
			}
		})
		transitionAnimationStartTime = System.currentTimeMillis()
		if (Build.VERSION.SDK_INT >= 18) {
			containerView!!.setLayerType(View.LAYER_TYPE_HARDWARE, null)
		}
		animatorSet.start()
	}

	private fun onClosed() {
		isVisible = false
		for (i in listView!!.indices) {
			adapter!![i].cleanup()
		}
		try {
			parentActivity!!.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
		for (a in createdWebViews.indices) {
			val cell = createdWebViews[a]
			cell.destroyWebView(false)
		}
		containerView!!.post {
			try {
				if (windowView!!.parent != null) {
					val wm = parentActivity!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
					wm.removeView(windowView)
				}
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
		}
	}

	private fun loadChannel(cell: BlockChannelCell, adapter: WebpageAdapter, channel: Chat) {
		if (loadingChannel || TextUtils.isEmpty(channel.username)) {
			return
		}
		loadingChannel = true
		val req = TLContactsResolveUsername()
		req.username = channel.username
		val currentAccount = UserConfig.selectedAccount
		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response: TLObject?, error: TLError? ->
			AndroidUtilities.runOnUIThread {
				loadingChannel = false
				if (parentFragment == null || adapter.blocks.isEmpty()) {
					return@runOnUIThread
				}
				if (error == null) {
					val res = response as TLContactsResolvedPeer?
					if (!res!!.chats.isEmpty()) {
						MessagesController.getInstance(currentAccount).putUsers(res.users, false)
						MessagesController.getInstance(currentAccount).putChats(res.chats, false)
						MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, false, true)

						loadedChannel = res.chats[0]

						if (loadedChannel!!.left /* && !loadedChannel.kicked*/) {
							cell.setState(0, false)
						}
						else {
							cell.setState(4, false)
						}
					}
					else {
						cell.setState(4, false)
					}
				}
				else {
					cell.setState(4, false)
				}
			}
		}
	}

	private fun joinChannel(cell: BlockChannelCell, channel: Chat) {
		val req = TLChannelsJoinChannel()
		req.channel = MessagesController.getInputChannel(channel)

		val currentAccount = UserConfig.selectedAccount

		ConnectionsManager.getInstance(currentAccount).sendRequest(req) { response, error ->
			if (error != null) {
				AndroidUtilities.runOnUIThread {
					cell.setState(0, false)
					processError(currentAccount, error, parentFragment, req, true)
				}

				return@sendRequest
			}

			var hasJoinMessage = false

			if (response is Updates) {
				for (update in response.updates) {
					if (update is TLUpdateNewChannelMessage) {
						if (update.message?.action is TLMessageActionChatAddUser) {
							hasJoinMessage = true
							break
						}
					}
				}

				MessagesController.getInstance(currentAccount).processUpdates(response, false)
			}

			if (!hasJoinMessage) {
				MessagesController.getInstance(currentAccount).generateJoinMessage(channel.id, true)
			}

			AndroidUtilities.runOnUIThread {
				cell.setState(2, false)
			}

			AndroidUtilities.runOnUIThread({
				MessagesController.getInstance(currentAccount).loadFullChat(channel.id, 0, true)
			}, 1000)

			MessagesStorage.getInstance(currentAccount).updateDialogsWithDeletedMessages(-channel.id, channel.id, listOf(), null, true)
		}
	}

	private fun checkAnimation(): Boolean {
		if (animationInProgress != 0) {
			if (abs((transitionAnimationStartTime - System.currentTimeMillis()).toDouble()) >= 500) {
				if (animationEndRunnable != null) {
					animationEndRunnable!!.run()
					animationEndRunnable = null
				}
				animationInProgress = 0
			}
		}
		return animationInProgress != 0
	}

	fun destroyArticleViewer() {
		if (parentActivity == null || windowView == null) {
			return
		}
		try {
			if (windowView!!.parent != null) {
				val wm = parentActivity!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
				wm.removeViewImmediate(windowView)
			}
			windowView = null
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
		for (a in createdWebViews.indices) {
			val cell = createdWebViews[a]
			cell.destroyWebView(true)
		}
		createdWebViews.clear()
		try {
			parentActivity!!.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
		parentActivity = null
		parentFragment = null
		Instance = null
	}

	fun showDialog(dialog: Dialog) {
		if (parentActivity == null) {
			return
		}
		try {
			if (visibleDialog != null) {
				visibleDialog!!.dismiss()
				visibleDialog = null
			}
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
		try {
			visibleDialog = dialog
			visibleDialog!!.setCanceledOnTouchOutside(true)
			visibleDialog!!.setOnDismissListener { dialog1: DialogInterface? ->
				visibleDialog = null
			}
			dialog.show()
		}
		catch (e: Exception) {
			FileLog.e(e)
		}
	}

	private object WebPageUtils {
		fun getPhotoWithId(page: WebPage?, id: Long): TLRPC.Photo? {
			if (page == null || page.cachedPage == null) {
				return null
			}

			if (page.photo?.id == id) {
				return page.photo
			}

			val photos = page.cachedPage?.photos

			if (!photos.isNullOrEmpty()) {
				for (photo in photos) {
					if (photo.id == id) {
						return photo
					}
				}
			}

			return null
		}

		fun getDocumentWithId(page: WebPage?, id: Long): TLRPC.Document? {
			if (page == null || page.cachedPage == null) {
				return null
			}

			if (page.document?.id == id) {
				return page.document
			}

			val documents = page.cachedPage?.documents

			if (!documents.isNullOrEmpty()) {
				for (document in documents) {
					if (document.id == id) {
						return document
					}
				}
			}

			return null
		}

		fun isVideo(page: WebPage?, block: PageBlock?): Boolean {
			if (block is TLPageBlockVideo) {
				val document = getDocumentWithId(page, block.videoId)

				if (document != null) {
					return MessageObject.isVideoDocument(document)
				}
			}

			return false
		}

		fun getMedia(page: WebPage?, block: PageBlock?): TLObject? {
			return if (block is TLPageBlockPhoto) {
				getPhotoWithId(page, block.photoId)
			}
			else if (block is TLPageBlockVideo) {
				getDocumentWithId(page, block.videoId)
			}
			else {
				null
			}
		}

		fun getMediaFile(page: WebPage?, block: PageBlock?): File? {
			if (block is TLPageBlockPhoto) {
				val photo = getPhotoWithId(page, block.photoId)

				if (photo != null) {
					val sizeFull = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize())

					if (sizeFull != null) {
						return FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(sizeFull, true)
					}
				}
			}
			else if (block is TLPageBlockVideo) {
				val document = getDocumentWithId(page, block.videoId)

				if (document != null) {
					return FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true)
				}
			}

			return null
		}
	}

	inner class WebpageAdapter(private val context: Context) : SelectionAdapter() {
		val localBlocks = mutableListOf<PageBlock>()
		val blocks = mutableListOf<PageBlock>()
		val photoBlocks = mutableListOf<PageBlock>()
		val anchors = mutableMapOf<String, Int>()
		val anchorsOffset = mutableMapOf<String, Int>()
		val anchorsParent = mutableMapOf<String, TLTextAnchor>()
		val audioBlocks = mutableMapOf<TLPageBlockAudio, MessageObject>()
		val audioMessages = mutableListOf<MessageObject>()
		val textToBlocks = mutableMapOf<Any, PageBlock>()
		val textBlocks = mutableListOf<Any>()
		val searchTextOffset = mutableMapOf<String, Int>()
		var currentPage: WebPage? = null
		var channelBlock: TLPageBlockChannel? = null
		var isRtl: Boolean = false

		fun getPhotoWithId(id: Long): TLRPC.Photo? {
			return WebPageUtils.getPhotoWithId(currentPage, id)
		}

		fun getDocumentWithId(id: Long?): TLRPC.Document? {
			if (id == null) {
				return null
			}

			return WebPageUtils.getDocumentWithId(currentPage, id)
		}

		fun setRichTextParents(parentRichText: RichText?, richText: RichText?) {
			if (richText == null) {
				return
			}

			richText.parentRichText = parentRichText

			if (richText is TLTextFixed) {
				setRichTextParents(richText, richText.text)
			}
			else if (richText is TLTextItalic) {
				setRichTextParents(richText, richText.text)
			}
			else if (richText is TLTextBold) {
				setRichTextParents(richText, richText.text)
			}
			else if (richText is TLTextUnderline) {
				setRichTextParents(richText, richText.text)
			}
			else if (richText is TLTextStrike) {
				setRichTextParents(richText, richText.text)
			}
			else if (richText is TLTextEmail) {
				setRichTextParents(richText, richText.text)
			}
			else if (richText is TLTextPhone) {
				setRichTextParents(richText, richText.text)
			}
			else if (richText is TLTextUrl) {
				setRichTextParents(richText, richText.text)
			}
			else if (richText is TLTextConcat) {
				val count: Int = richText.texts.size

				for (a in 0..<count) {
					setRichTextParents(richText, richText.texts[a])
				}
			}
			else if (richText is TLTextSubscript) {
				setRichTextParents(richText, richText.text)
			}
			else if (richText is TLTextSuperscript) {
				setRichTextParents(richText, richText.text)
			}
			else if (richText is TLTextMarked) {
				setRichTextParents(richText, richText.text)
			}
			else if (richText is TLTextAnchor) {
				setRichTextParents(richText, richText.text)

				val name = richText.name?.lowercase()

				if (!name.isNullOrEmpty()) {
					anchors[name] = blocks.size

					if (richText.text is TLTextPlain) {
						(richText.text as? TLTextPlain)?.takeIf { !it.text.isNullOrEmpty() }?.let {
							anchorsParent[name] = richText
						}
					}
					else if (richText.text !is TLTextEmpty) {
						anchorsParent[name] = richText
					}

					anchorsOffset[name] = -1
				}
			}
		}

		fun addTextBlock(text: Any?, block: PageBlock) {
			if (text == null || text is TLTextEmpty || textToBlocks.containsKey(text)) {
				return
			}

			textToBlocks[text] = block
			textBlocks.add(text)
		}

		fun setRichTextParents(block: PageBlock?) {
			if (block is TLPageBlockEmbedPost) {
				setRichTextParents(null, block.caption!!.text)
				setRichTextParents(null, block.caption!!.credit)
				addTextBlock(block.caption!!.text, block)
				addTextBlock(block.caption!!.credit, block)
			}
			else if (block is TLPageBlockParagraph) {
				setRichTextParents(null, block.text)
				addTextBlock(block.text, block)
			}
			else if (block is TLPageBlockKicker) {
				setRichTextParents(null, block.text)
				addTextBlock(block.text, block)
			}
			else if (block is TLPageBlockFooter) {
				setRichTextParents(null, block.text)
				addTextBlock(block.text, block)
			}
			else if (block is TLPageBlockHeader) {
				setRichTextParents(null, block.text)
				addTextBlock(block.text, block)
			}
			else if (block is TLPageBlockPreformatted) {
				setRichTextParents(null, block.text)
				addTextBlock(block.text, block)
			}
			else if (block is TLPageBlockSubheader) {
				setRichTextParents(null, block.text)
				addTextBlock(block.text, block)
			}
			else if (block is TLPageBlockSlideshow) {
				setRichTextParents(null, block.caption!!.text)
				setRichTextParents(null, block.caption!!.credit)
				addTextBlock(block.caption!!.text, block)
				addTextBlock(block.caption!!.credit, block)
				var a = 0
				val size = block.items.size
				while (a < size) {
					setRichTextParents(block.items[a])
					a++
				}
			}
			else if (block is TLPageBlockPhoto) {
				setRichTextParents(null, block.caption!!.text)
				setRichTextParents(null, block.caption!!.credit)
				addTextBlock(block.caption!!.text, block)
				addTextBlock(block.caption!!.credit, block)
			}
			else if (block is TLPageBlockListItem) {
				if (block.textItem != null) {
					setRichTextParents(null, block.textItem)
					addTextBlock(block.textItem, block)
				}
				else if (block.blockItem != null) {
					setRichTextParents(block.blockItem)
				}
			}
			else if (block is TLPageBlockOrderedListItem) {
				if (block.textItem != null) {
					setRichTextParents(null, block.textItem)
					addTextBlock(block.textItem, block)
				}
				else if (block.blockItem != null) {
					setRichTextParents(block.blockItem)
				}
			}
			else if (block is TLPageBlockCollage) {
				setRichTextParents(null, block.caption!!.text)
				setRichTextParents(null, block.caption!!.credit)
				addTextBlock(block.caption!!.text, block)
				addTextBlock(block.caption!!.credit, block)
				var a = 0
				val size = block.items.size
				while (a < size) {
					setRichTextParents(block.items[a])
					a++
				}
			}
			else if (block is TLPageBlockEmbed) {
				setRichTextParents(null, block.caption!!.text)
				setRichTextParents(null, block.caption!!.credit)
				addTextBlock(block.caption!!.text, block)
				addTextBlock(block.caption!!.credit, block)
			}
			else if (block is TLPageBlockSubtitle) {
				setRichTextParents(null, block.text)
				addTextBlock(block.text, block)
			}
			else if (block is TLPageBlockBlockquote) {
				setRichTextParents(null, block.text)
				setRichTextParents(null, block.caption)
				addTextBlock(block.text, block)
				addTextBlock(block.caption, block)
			}
			else if (block is TLPageBlockDetails) {
				setRichTextParents(null, block.title)
				addTextBlock(block.title, block)
				var a = 0
				val size = block.blocks.size
				while (a < size) {
					setRichTextParents(block.blocks[a])
					a++
				}
			}
			else if (block is TLPageBlockVideo) {
				setRichTextParents(null, block.caption!!.text)
				setRichTextParents(null, block.caption!!.credit)
				addTextBlock(block.caption!!.text, block)
				addTextBlock(block.caption!!.credit, block)
			}
			else if (block is TLPageBlockPullquote) {
				setRichTextParents(null, block.text)
				setRichTextParents(null, block.caption)
				addTextBlock(block.text, block)
				addTextBlock(block.caption, block)
			}
			else if (block is TLPageBlockAudio) {
				setRichTextParents(null, block.caption!!.text)
				setRichTextParents(null, block.caption!!.credit)
				addTextBlock(block.caption!!.text, block)
				addTextBlock(block.caption!!.credit, block)
			}
			else if (block is TLPageBlockTable) {
				setRichTextParents(null, block.title)
				addTextBlock(block.title, block)
				var a = 0
				val size = block.rows.size
				while (a < size) {
					val row = block.rows[a]
					var b = 0
					val size2 = row.cells.size
					while (b < size2) {
						val cell = row.cells[b]
						setRichTextParents(null, cell.text)
						addTextBlock(cell.text, block)
						b++
					}
					a++
				}
			}
			else if (block is TLPageBlockTitle) {
				setRichTextParents(null, block.text)
				addTextBlock(block.text, block)
			}
			else if (block is TLPageBlockCover) {
				setRichTextParents(block.cover)
			}
			else if (block is TLPageBlockAuthorDate) {
				setRichTextParents(null, block.author)
				addTextBlock(block.author, block)
			}
			else if (block is TLPageBlockMap) {
				setRichTextParents(null, block.caption!!.text)
				setRichTextParents(null, block.caption!!.credit)
				addTextBlock(block.caption!!.text, block)
				addTextBlock(block.caption!!.credit, block)
			}
			else if (block is TLPageBlockRelatedArticles) {
				setRichTextParents(null, block.title)
				addTextBlock(block.title, block)
			}
		}

		fun addBlock(adapter: WebpageAdapter, block: PageBlock?, level: Int, listLevel: Int, position: Int) {
			var block = block
			val originalBlock = block

			if (block is TLPageBlockDetailsChild) {
				block = block.block
			}

			if (!(block is TLPageBlockList || block is TLPageBlockOrderedList)) {
				setRichTextParents(block)
				addAllMediaFromBlock(adapter, block)
			}

			block = getLastNonListPageBlock(block)

			if (block is TLPageBlockUnsupported) {
				return
			}
			else if (block is TLPageBlockAnchor) {
				anchors[block.name!!.lowercase()] = blocks.size
				return
			}

			if (originalBlock != null && !(block is TLPageBlockList || block is TLPageBlockOrderedList)) {
				blocks.add(originalBlock)
			}

			if (block is TLPageBlockAudio) {
				val message = TLRPC.TLMessage()
				message.out = true

				block.mid = -block.audioId.hashCode()

				message.id = block.mid

				message.peerId = TLPeerUser().also {
					it.userId = UserConfig.getInstance(currentAccount).getClientUserId()
				}

				message.fromId = TLPeerUser().also {
					it.userId = message.peerId?.userId ?: 0L
				}

				message.date = (System.currentTimeMillis() / 1000).toInt()
				message.message = ""

				message.media = TLMessageMediaDocument().also {
					it.webpage = currentPage
					it.flags = it.flags or 3
					it.document = getDocumentWithId(block.audioId)
				}

				message.flags = message.flags or (TLRPC.MESSAGE_FLAG_HAS_MEDIA or TLRPC.MESSAGE_FLAG_HAS_FROM_ID)

				val messageObject = MessageObject(UserConfig.selectedAccount, message, generateLayout = false, checkMediaExists = true)

				audioMessages.add(messageObject)
				audioBlocks[block] = messageObject

				val author = messageObject.getMusicAuthor(false)
				val title = messageObject.getMusicTitle(false)
				if (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(author)) {
					var stringBuilder: SpannableStringBuilder
					if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(author)) {
						addTextBlock(String.format("%s - %s", author, title), block)
					}
					else if (!TextUtils.isEmpty(title)) {
						addTextBlock(title, block)
					}
					else {
						addTextBlock(author, block)
					}
				}
			}
			else if (block is TLPageBlockEmbedPost) {
				if (block.blocks.isNotEmpty()) {
					block.level = -1

					for (b in block.blocks.indices) {
						val innerBlock = block.blocks[b]

						if (innerBlock is TLPageBlockUnsupported) {
							continue
						}
						else if (innerBlock is TLPageBlockAnchor) {
							anchors[innerBlock.name!!.lowercase()] = blocks.size
							continue
						}

						innerBlock.level = 1

						if (b == block.blocks.size - 1) {
							innerBlock.bottom = true
						}
						blocks.add(innerBlock)
						addAllMediaFromBlock(adapter, innerBlock)
					}

					if (!TextUtils.isEmpty(getPlainText(block.caption!!.text)) || !TextUtils.isEmpty(getPlainText(block.caption!!.credit))) {
						val pageBlockEmbedPostCaption = TLPageBlockEmbedPostCaption()
						pageBlockEmbedPostCaption.parent = block
						pageBlockEmbedPostCaption.caption = block.caption
						blocks.add(pageBlockEmbedPostCaption)
					}
				}
			}
			else if (block is TLPageBlockRelatedArticles) {
				var shadow = TLPageBlockRelatedArticlesShadow()
				shadow.parent = block
				blocks.add(blocks.size - 1, shadow)

				var b = 0
				val size = block.articles.size
				while (b < size) {
					val child = TLPageBlockRelatedArticlesChild()
					child.parent = block
					child.num = b
					blocks.add(child)
					b++
				}
				if (position == 0) {
					shadow = TLPageBlockRelatedArticlesShadow()
					shadow.parent = block
					blocks.add(shadow)
				}
			}
			else if (block is TLPageBlockDetails) {
				var b = 0
				val size = block.blocks.size
				while (b < size) {
					val child = TLPageBlockDetailsChild()
					child.parent = originalBlock
					child.block = block.blocks[b]
					addBlock(adapter, wrapInTableBlock(originalBlock, child), level + 1, listLevel, position)
					b++
				}/*if (level == 0) {
                    TLPageBlockDetailsBottom child = new TLPageBlockDetailsBottom();
                    child.parent = pageBlockDetails;
                    blocks.add(wrapInTableBlock(originalBlock, child));
                } else {
                    TLPageBlockDetailsBottom bottom = new TLPageBlockDetailsBottom();
                    bottom.parent = pageBlockDetails;

                    TLPageBlockDetailsChild child = new TLPageBlockDetailsChild();
                    child.parent = originalBlock;
                    child.block = bottom;
                    blocks.add(wrapInTableBlock(originalBlock, child));
                }*/
			}
			else if (block is TLPageBlockList) {
				val pageBlockListParent = TLPageBlockListParent()
				pageBlockListParent.pageBlockList = block
				pageBlockListParent.level = listLevel

				var b = 0
				val size = block.items.size
				while (b < size) {
					var item = block.items[b]

					var pageBlockListItem = TLPageBlockListItem()
					pageBlockListItem.index = b
					pageBlockListItem.parent = pageBlockListParent

//					if (block.ordered) {
//						if (isRtl) {
//							pageBlockListItem.num = String.format(".%d", b + 1)
//						}
//						else {
//							pageBlockListItem.num = String.format("%d.", b + 1)
//						}
//					}
//					else {
					pageBlockListItem.num = "•"
//					}

					pageBlockListParent.items.add(pageBlockListItem)

					if (item is TLPageListItemText) {
						pageBlockListItem.textItem = item.text
					}
					else if (item is TLPageListItemBlocks) {
						if (!item.blocks.isEmpty()) {
							pageBlockListItem.blockItem = item.blocks[0]
						}
						else {
							val text = TLPageListItemText()
							val textPlain = TLTextPlain()
							textPlain.text = " "
							text.text = textPlain
							item = text
						}
					}
					if (originalBlock is TLPageBlockDetailsChild) {
						val child = TLPageBlockDetailsChild()
						child.parent = originalBlock.parent
						child.block = pageBlockListItem
						addBlock(adapter, child, level, listLevel + 1, position)
					}
					else {
						val finalBlock = if (b == 0) {
							fixListBlock(originalBlock, pageBlockListItem)
						}
						else {
							pageBlockListItem
						}
						addBlock(adapter, finalBlock, level, listLevel + 1, position)
					}

					if (item is TLPageListItemBlocks) {
						var c = 1
						val size2 = item.blocks.size
						while (c < size2) {
							pageBlockListItem = TLPageBlockListItem()
							pageBlockListItem.blockItem = item.blocks[c]
							pageBlockListItem.parent = pageBlockListParent

							if (originalBlock is TLPageBlockDetailsChild) {
								val child = TLPageBlockDetailsChild()
								child.parent = originalBlock.parent
								child.block = pageBlockListItem
								addBlock(adapter, child, level, listLevel + 1, position)
							}
							else {
								addBlock(adapter, pageBlockListItem, level, listLevel + 1, position)
							}
							pageBlockListParent.items.add(pageBlockListItem)
							c++
						}
					}
					b++
				}
			}
			else if (block is TLPageBlockOrderedList) {
				val pageBlockOrderedListParent = TLPageBlockOrderedListParent()
				pageBlockOrderedListParent.pageBlockOrderedList = block
				pageBlockOrderedListParent.level = listLevel

				var b = 0
				val size = block.items.size
				while (b < size) {
					var item = block.items[b]

					var pageBlockOrderedListItem = TLPageBlockOrderedListItem()
					pageBlockOrderedListItem.index = b
					pageBlockOrderedListItem.parent = pageBlockOrderedListParent
					pageBlockOrderedListParent.items.add(pageBlockOrderedListItem)

					if (item is TLPageListOrderedItemText) {
						pageBlockOrderedListItem.textItem = item.text

						if (TextUtils.isEmpty(item.num)) {
							if (isRtl) {
								pageBlockOrderedListItem.num = String.format(".%d", b + 1)
							}
							else {
								pageBlockOrderedListItem.num = String.format("%d.", b + 1)
							}
						}
						else {
							if (isRtl) {
								pageBlockOrderedListItem.num = "." + item.num
							}
							else {
								pageBlockOrderedListItem.num = item.num + "."
							}
						}
					}
					else if (item is TLPageListOrderedItemBlocks) {
						if (!item.blocks.isEmpty()) {
							pageBlockOrderedListItem.blockItem = item.blocks[0]
						}
						else {
							val text = TLPageListOrderedItemText()
							val textPlain = TLTextPlain()
							textPlain.text = " "
							text.text = textPlain
							item = text
						}

						if (TextUtils.isEmpty(item.num)) {
							if (isRtl) {
								pageBlockOrderedListItem.num = String.format(".%d", b + 1)
							}
							else {
								pageBlockOrderedListItem.num = String.format("%d.", b + 1)
							}
						}
						else {
							if (isRtl) {
								pageBlockOrderedListItem.num = "." + item.num
							}
							else {
								pageBlockOrderedListItem.num = item.num + "."
							}
						}
					}
					if (originalBlock is TLPageBlockDetailsChild) {
						val child = TLPageBlockDetailsChild()
						child.parent = originalBlock.parent
						child.block = pageBlockOrderedListItem
						addBlock(adapter, child, level, listLevel + 1, position)
					}
					else {
						val finalBlock = if (b == 0) {
							fixListBlock(originalBlock, pageBlockOrderedListItem)
						}
						else {
							pageBlockOrderedListItem
						}
						addBlock(adapter, finalBlock, level, listLevel + 1, position)
					}

					if (item is TLPageListOrderedItemBlocks) {
						var c = 1
						val size2 = item.blocks.size
						while (c < size2) {
							pageBlockOrderedListItem = TLPageBlockOrderedListItem()
							pageBlockOrderedListItem.blockItem = item.blocks[c]
							pageBlockOrderedListItem.parent = pageBlockOrderedListParent

							if (originalBlock is TLPageBlockDetailsChild) {
								val child = TLPageBlockDetailsChild()
								child.parent = originalBlock.parent
								child.block = pageBlockOrderedListItem
								addBlock(adapter, child, level, listLevel + 1, position)
							}
							else {
								addBlock(adapter, pageBlockOrderedListItem, level, listLevel + 1, position)
							}
							pageBlockOrderedListParent.items.add(pageBlockOrderedListItem)
							c++
						}
					}
					b++
				}
			}
		}

		fun addAllMediaFromBlock(adapter: WebpageAdapter, block: PageBlock?) {
			if (block is TLPageBlockPhoto) {
				val photo = getPhotoWithId(block.photoId)

				if (photo != null) {
					block.thumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 56, true)
					block.thumbObject = photo
					photoBlocks.add(block)
				}
			}
			else if (block is TLPageBlockVideo && WebPageUtils.isVideo(adapter.currentPage, block)) {
				val document = getDocumentWithId(block.videoId)

				if (document != null) {
					block.thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 56, true)
					block.thumbObject = document
					photoBlocks.add(block)
				}
			}
			else if (block is TLPageBlockSlideshow) {
				val count = block.items.size

				for (a in 0..<count) {
					val innerBlock = block.items[a]
					innerBlock.groupId = lastBlockNum
					addAllMediaFromBlock(adapter, innerBlock)
				}

				lastBlockNum++
			}
			else if (block is TLPageBlockCollage) {
				val count = block.items.size

				for (a in 0..<count) {
					val innerBlock = block.items[a]
					innerBlock.groupId = lastBlockNum
					addAllMediaFromBlock(adapter, innerBlock)
				}

				lastBlockNum++
			}
			else if (block is TLPageBlockCover) {
				addAllMediaFromBlock(adapter, block.cover)
			}
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			val view: View

			when (viewType) {
				0 -> {
					view = BlockParagraphCell(context, this)
				}

				1 -> {
					view = BlockHeaderCell(context, this)
				}

				2 -> {
					view = BlockDividerCell(context)
				}

				3 -> {
					view = BlockEmbedCell(context, this)
				}

				4 -> {
					view = BlockSubtitleCell(context, this)
				}

				5 -> {
					view = BlockVideoCell(context, this, 0)
				}

				6 -> {
					view = BlockPullquoteCell(context, this)
				}

				7 -> {
					view = BlockBlockquoteCell(context, this)
				}

				8 -> {
					view = BlockSlideshowCell(context, this)
				}

				9 -> {
					view = BlockPhotoCell(context, this, 0)
				}

				10 -> {
					view = BlockAuthorDateCell(context, this)
				}

				11 -> {
					view = BlockTitleCell(context, this)
				}

				12 -> {
					view = BlockListItemCell(context, this)
				}

				13 -> {
					view = BlockFooterCell(context, this)
				}

				14 -> {
					view = BlockPreformattedCell(context, this)
				}

				15 -> {
					view = BlockSubheaderCell(context, this)
				}

				16 -> {
					view = BlockEmbedPostCell(context, this)
				}

				17 -> {
					view = BlockCollageCell(context, this)
				}

				18 -> {
					view = BlockChannelCell(context, this, 0)
				}

				19 -> {
					view = BlockAudioCell(context, this)
				}

				20 -> {
					view = BlockKickerCell(context, this)
				}

				21 -> {
					view = BlockOrderedListItemCell(context, this)
				}

				22 -> {
					view = BlockMapCell(context, this, 0)
				}

				23 -> {
					view = BlockRelatedArticlesCell(context, this)
				}

				24 -> {
					view = BlockDetailsCell(context, this)
				}

				25 -> {
					view = BlockTableCell(context, this)
				}

				26 -> {
					view = BlockRelatedArticlesHeaderCell(context, this)
				}

				27 -> {
					view = BlockDetailsBottomCell(context)
				}

				28 -> {
					view = BlockRelatedArticlesShadowCell(context)
				}

				90 -> {
					view = ReportCell(context)
				}

				100 -> {
					val textView = TextView(context)
					textView.setBackgroundColor(-0x10000)
					textView.setTextColor(-0x1000000)
					textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
					view = textView
				}

				else -> {
					val textView = TextView(context)
					textView.setBackgroundColor(-0x10000)
					textView.setTextColor(-0x1000000)
					textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
					view = textView
				}
			}

			view.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
			view.isFocusable = true

			return RecyclerListView.Holder(view)
		}

		override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
			val type = holder.itemViewType
			return type == 23 || type == 24
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			if (position < localBlocks.size) {
				val block = localBlocks[position]
				bindBlockToHolder(holder.itemViewType, holder, block, position, localBlocks.size)
			}
		}

		override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
			if (holder.itemViewType == 90) {
				val cell = holder.itemView as ReportCell
				cell.setViews(currentPage.cachedPage?.views ?: 0)
			}
		}

		fun bindBlockToHolder(type: Int, holder: RecyclerView.ViewHolder, block: PageBlock?, position: Int, total: Int) {
			var block = block
			val originalBlock = block
			if (block is TLPageBlockCover) {
				block = block.cover
			}
			else if (block is TLPageBlockDetailsChild) {
				block = block.block
			}
			when (type) {
				0 -> {
					val cell = holder.itemView as BlockParagraphCell
					cell.setBlock(block as TLPageBlockParagraph?)
				}

				1 -> {
					val cell = holder.itemView as BlockHeaderCell
					cell.setBlock(block as TLPageBlockHeader?)
				}

				2 -> {
					val cell = holder.itemView as BlockDividerCell
				}

				3 -> {
					val cell = holder.itemView as BlockEmbedCell
					cell.setBlock(block as TLPageBlockEmbed)
				}

				4 -> {
					val cell = holder.itemView as BlockSubtitleCell
					cell.setBlock(block as TLPageBlockSubtitle?)
				}

				5 -> {
					val cell = holder.itemView as BlockVideoCell
					cell.setBlock(block as TLPageBlockVideo?, position == 0, position == total - 1)
					cell.setParentBlock(channelBlock, originalBlock)
				}

				6 -> {
					val cell = holder.itemView as BlockPullquoteCell
					cell.setBlock(block as TLPageBlockPullquote?)
				}

				7 -> {
					val cell = holder.itemView as BlockBlockquoteCell
					cell.setBlock(block as TLPageBlockBlockquote?)
				}

				8 -> {
					val cell = holder.itemView as BlockSlideshowCell
					cell.setBlock(block as TLPageBlockSlideshow?)
				}

				9 -> {
					val cell = holder.itemView as BlockPhotoCell
					cell.setBlock(block as TLPageBlockPhoto?, position == 0, position == total - 1)
					cell.setParentBlock(originalBlock)
				}

				10 -> {
					val cell = holder.itemView as BlockAuthorDateCell
					cell.setBlock(block as TLPageBlockAuthorDate?)
				}

				11 -> {
					val cell = holder.itemView as BlockTitleCell
					cell.setBlock(block as TLPageBlockTitle?)
				}

				12 -> {
					val cell = holder.itemView as BlockListItemCell
					cell.setBlock(block as TLPageBlockListItem?)
				}

				13 -> {
					val cell = holder.itemView as BlockFooterCell
					cell.setBlock(block as TLPageBlockFooter?)
				}

				14 -> {
					val cell = holder.itemView as BlockPreformattedCell
					cell.setBlock(block as TLPageBlockPreformatted?)
				}

				15 -> {
					val cell = holder.itemView as BlockSubheaderCell
					cell.setBlock(block as TLPageBlockSubheader?)
				}

				16 -> {
					val cell = holder.itemView as BlockEmbedPostCell
					cell.setBlock(block as TLPageBlockEmbedPost?)
				}

				17 -> {
					val cell = holder.itemView as BlockCollageCell
					cell.setBlock(block as TLPageBlockCollage?)
				}

				18 -> {
					val cell = holder.itemView as BlockChannelCell
					cell.setBlock(block as TLPageBlockChannel)
				}

				19 -> {
					val cell = holder.itemView as BlockAudioCell
					cell.setBlock(block as TLPageBlockAudio?, position == 0, position == total - 1)
				}

				20 -> {
					val cell = holder.itemView as BlockKickerCell
					cell.setBlock(block as TLPageBlockKicker?)
				}

				21 -> {
					val cell = holder.itemView as BlockOrderedListItemCell
					cell.setBlock(block as TLPageBlockOrderedListItem?)
				}

				22 -> {
					val cell = holder.itemView as BlockMapCell
					cell.setBlock(block as TLPageBlockMap?, position == 0, position == total - 1)
				}

				23 -> {
					val cell = holder.itemView as BlockRelatedArticlesCell
					cell.setBlock(block as TLPageBlockRelatedArticlesChild?)
				}

				24 -> {
					val cell = holder.itemView as BlockDetailsCell
					cell.setBlock(block as TLPageBlockDetails)
				}

				25 -> {
					val cell = holder.itemView as BlockTableCell
					cell.setBlock(block as TLPageBlockTable?)
				}

				26 -> {
					val cell = holder.itemView as BlockRelatedArticlesHeaderCell
					cell.setBlock(block as TLPageBlockRelatedArticles?)
				}

				27 -> {
					val cell = holder.itemView as BlockDetailsBottomCell
				}

				100 -> {
					val textView = holder.itemView as TextView
					textView.text = "unsupported block $block"
				}
			}
		}

		fun getTypeForBlock(block: PageBlock?): Int {
			return if (block is TLPageBlockParagraph) {
				0
			}
			else if (block is TLPageBlockHeader) {
				1
			}
			else if (block is TLPageBlockDivider) {
				2
			}
			else if (block is TLPageBlockEmbed) {
				3
			}
			else if (block is TLPageBlockSubtitle) {
				4
			}
			else if (block is TLPageBlockVideo) {
				5
			}
			else if (block is TLPageBlockPullquote) {
				6
			}
			else if (block is TLPageBlockBlockquote) {
				7
			}
			else if (block is TLPageBlockSlideshow) {
				8
			}
			else if (block is TLPageBlockPhoto) {
				9
			}
			else if (block is TLPageBlockAuthorDate) {
				10
			}
			else if (block is TLPageBlockTitle) {
				11
			}
			else if (block is TLPageBlockListItem) {
				12
			}
			else if (block is TLPageBlockFooter) {
				13
			}
			else if (block is TLPageBlockPreformatted) {
				14
			}
			else if (block is TLPageBlockSubheader) {
				15
			}
			else if (block is TLPageBlockEmbedPost) {
				16
			}
			else if (block is TLPageBlockCollage) {
				17
			}
			else if (block is TLPageBlockChannel) {
				18
			}
			else if (block is TLPageBlockAudio) {
				19
			}
			else if (block is TLPageBlockKicker) {
				20
			}
			else if (block is TLPageBlockOrderedListItem) {
				21
			}
			else if (block is TLPageBlockMap) {
				22
			}
			else if (block is TLPageBlockRelatedArticlesChild) {
				23
			}
			else if (block is TLPageBlockDetails) {
				24
			}
			else if (block is TLPageBlockTable) {
				25
			}
			else if (block is TLPageBlockRelatedArticles) {
				26
			}
			else if (block is TLPageBlockDetailsBottom) {
				27
			}
			else if (block is TLPageBlockRelatedArticlesShadow) {
				28
			}
			else if (block is TLPageBlockDetailsChild) {
				getTypeForBlock(block.block)
			}
			else if (block is TLPageBlockCover) {
				getTypeForBlock(block.cover)
			}
			else {
				100
			}
		}

		override fun getItemViewType(position: Int): Int {
			if (position == localBlocks.size) {
				return 90
			}
			return getTypeForBlock(localBlocks[position])
		}

		fun getItem(position: Int): PageBlock? {
			return localBlocks[position]
		}

		override fun getItemCount(): Int {
			return if (currentPage != null && currentPage.cachedPage != null) localBlocks.size + 1 else 0
		}

		private fun isBlockOpened(child: TLPageBlockDetailsChild?): Boolean {
			var parentBlock = getLastNonListPageBlock(child?.parent)

			if (parentBlock is TLPageBlockDetails) {
				return parentBlock.open
			}
			else if (parentBlock is TLPageBlockDetailsChild) {
				parentBlock = getLastNonListPageBlock(parentBlock.block)

				if (parentBlock is TLPageBlockDetails && !parentBlock.open) {
					return false
				}

				if (parentBlock is TLPageBlockDetailsChild) {
					return isBlockOpened(parentBlock)
				}
			}

			return false
		}

		fun updateRows() {
			localBlocks.clear()
			var a = 0
			val size = blocks.size
			while (a < size) {
				val originalBlock = blocks[a]
				val block = getLastNonListPageBlock(originalBlock)
				if (block is TLPageBlockDetailsChild) {
					if (!isBlockOpened(block)) {
						a++
						continue
					}
				}
				localBlocks.add(originalBlock)
				a++
			}
		}

		fun cleanup() {
			currentPage = null
			blocks.clear()
			photoBlocks.clear()
			audioBlocks.clear()
			audioMessages.clear()
			anchors.clear()
			anchorsParent.clear()
			anchorsOffset.clear()
			textBlocks.clear()
			textToBlocks.clear()
			channelBlock = null
			notifyDataSetChanged()
		}

		override fun notifyDataSetChanged() {
			updateRows()
			super.notifyDataSetChanged()
		}

		override fun notifyItemChanged(position: Int) {
			updateRows()
			super.notifyItemChanged(position)
		}

		override fun notifyItemChanged(position: Int, payload: Any?) {
			updateRows()
			super.notifyItemChanged(position, payload)
		}

		override fun notifyItemRangeChanged(positionStart: Int, itemCount: Int) {
			updateRows()
			super.notifyItemRangeChanged(positionStart, itemCount)
		}

		override fun notifyItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
			updateRows()
			super.notifyItemRangeChanged(positionStart, itemCount, payload)
		}

		override fun notifyItemInserted(position: Int) {
			updateRows()
			super.notifyItemInserted(position)
		}

		override fun notifyItemMoved(fromPosition: Int, toPosition: Int) {
			updateRows()
			super.notifyItemMoved(fromPosition, toPosition)
		}

		override fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) {
			updateRows()
			super.notifyItemRangeInserted(positionStart, itemCount)
		}

		override fun notifyItemRemoved(position: Int) {
			updateRows()
			super.notifyItemRemoved(position)
		}

		override fun notifyItemRangeRemoved(positionStart: Int, itemCount: Int) {
			updateRows()
			super.notifyItemRangeRemoved(positionStart, itemCount)
		}
	}

	private inner class BlockVideoCell(context: Context, private val parentAdapter: WebpageAdapter, type: Int) : FrameLayout(context), FileDownloadProgressListener, ArticleSelectableView {
		private var captionLayout: DrawingText? = null
		private var creditLayout: DrawingText? = null
		val imageView: ImageReceiver
		private val radialProgress: RadialProgress2
		private val channelCell: BlockChannelCell
		private val currentType: Int
		private var isFirst = false
		private var textX = 0
		private var textY = 0
		private var creditOffset = 0

		private var buttonX = 0
		private var buttonY = 0
		private var photoPressed = false
		private var buttonState = 0
		private var buttonPressed = 0

		private val TAG: Int

		var currentBlock: TLPageBlockVideo? = null
		private var parentBlock: PageBlock? = null
		private var currentDocument: TLRPC.Document? = null
		private var isGif = false

		private var autoDownload = false

		private var cancelLoading = false

		var groupPosition: GroupedMessagePosition? = null

		init {
			setWillNotDraw(false)
			imageView = ImageReceiver(this)
			imageView.isNeedsQualityThumb = true
			imageView.isShouldGenerateQualityThumb = true
			currentType = type
			radialProgress = RadialProgress2(this)
			radialProgress.setProgressColor(-0x1)
			radialProgress.setColors(0x66000000, 0x7f000000, -0x1, -0x262627)
			TAG = DownloadController.getInstance(currentAccount).generateObserverTag()
			channelCell = BlockChannelCell(context, parentAdapter, 1)
			addView(channelCell, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
		}

		fun setBlock(block: TLPageBlockVideo?, first: Boolean, last: Boolean) {
			currentBlock = block
			parentBlock = null
			currentDocument = parentAdapter.getDocumentWithId(currentBlock?.videoId)
			isGif = MessageObject.isVideoDocument(currentDocument) || MessageObject.isGifDocument(currentDocument) /* && currentBlock.autoplay*/
			isFirst = first
			channelCell.visibility = INVISIBLE
			updateButtonState(false)
			requestLayout()
		}

		fun setParentBlock(channelBlock: TLPageBlockChannel?, block: PageBlock?) {
			parentBlock = block
			if (channelBlock != null && parentBlock is TLPageBlockCover) {
				channelCell.setBlock(channelBlock)
				channelCell.visibility = VISIBLE
			}
		}

		fun getChannelCell(): View {
			return channelCell
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (pinchToZoomHelper!!.checkPinchToZoom(event, this, imageView, null)) {
				return true
			}
			val x = event.x
			val y = event.y
			if (channelCell.visibility == VISIBLE && y > channelCell.translationY && y < channelCell.translationY + AndroidUtilities.dp(39f)) {
				if (parentAdapter.channelBlock != null && event.action == MotionEvent.ACTION_UP) {
					MessagesController.getInstance(currentAccount).openByUserName(parentAdapter.channelBlock!!.channel!!.username, parentFragment, 2)
					close(false, true)
				}
				return true
			}
			if (event.action == MotionEvent.ACTION_DOWN && imageView.isInsideImage(x, y)) {
				if (buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48f) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48f) || buttonState == 0) {
					buttonPressed = 1
					invalidate()
				}
				else {
					photoPressed = true
				}
			}
			else if (event.action == MotionEvent.ACTION_UP) {
				if (photoPressed) {
					photoPressed = false
					openPhoto(currentBlock, parentAdapter)
				}
				else if (buttonPressed == 1) {
					buttonPressed = 0
					playSoundEffect(SoundEffectConstants.CLICK)
					didPressedButton(true)
					invalidate()
				}
			}
			else if (event.action == MotionEvent.ACTION_CANCEL) {
				photoPressed = false
			}
			return photoPressed || buttonPressed != 0 || checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			var width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0
			if (currentType == 1) {
				width = (parent as View).measuredWidth
				height = (parent as View).measuredHeight
			}
			else if (currentType == 2) {
				height = ceil((groupPosition!!.ph * max(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) * 0.5f).toDouble()).toInt()
			}

			if (currentBlock != null) {
				var photoWidth = width
				var photoHeight = height
				var photoX: Int
				val textWidth: Int
				if (currentType == 0 && currentBlock!!.level > 0) {
					photoX = AndroidUtilities.dp((14 * currentBlock!!.level).toFloat()) + AndroidUtilities.dp(18f)
					textX = photoX
					photoWidth -= photoX + AndroidUtilities.dp(18f)
					textWidth = photoWidth
				}
				else {
					photoX = 0
					textX = AndroidUtilities.dp(18f)
					textWidth = width - AndroidUtilities.dp(36f)
				}
				if (currentDocument != null) {
					val size = AndroidUtilities.dp(48f)
					val thumb = FileLoader.getClosestPhotoSizeWithSize(currentDocument.thumbs, 48)
					if (currentType == 0) {
						var scale: Float
						var found = false
						var a = 0
						val count: Int = (currentDocument as? TLRPC.TLDocument)?.attributes?.size ?: 0

						while (a < count) {
							val attribute = (currentDocument as? TLRPC.TLDocument)?.attributes?.get(a)

							if (attribute is TLDocumentAttributeVideo) {
								scale = photoWidth / attribute.w.toFloat()
								height = (scale * attribute.h).toInt()
								found = true
								break
							}
							a++
						}
						val w = thumb?.w?.toFloat() ?: 100.0f
						val h = thumb?.h?.toFloat() ?: 100.0f
						if (!found) {
							scale = photoWidth / w
							height = (scale * h).toInt()
						}
						if (parentBlock is TLPageBlockCover) {
							height = min(height.toDouble(), photoWidth.toDouble()).toInt()
						}
						else {
							val maxHeight = ((max(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) - AndroidUtilities.dp(56f)) * 0.9f).toInt()
							if (height > maxHeight) {
								height = maxHeight
								scale = height / h
								photoWidth = (scale * w).toInt()
								photoX += (width - photoX - photoWidth) / 2
							}
						}
						if (height == 0) {
							height = AndroidUtilities.dp(100f)
						}
						else if (height < size) {
							height = size
						}
						photoHeight = height
					}
					else if (currentType == 2) {
						if ((groupPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT) == 0) {
							photoWidth -= AndroidUtilities.dp(2f)
						}
						if ((groupPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM) == 0) {
							photoHeight -= AndroidUtilities.dp(2f)
						}
					}
					imageView.qualityThumbDocument = currentDocument
					imageView.setImageCoordinates(photoX.toFloat(), (if (isFirst || currentType == 1 || currentType == 2 || (currentBlock?.level ?: 0) > 0) 0
					else AndroidUtilities.dp(8f)).toFloat(), photoWidth.toFloat(), photoHeight.toFloat())

					if (isGif) {
						autoDownload = DownloadController.getInstance(currentAccount).canDownloadMedia(DownloadController.AUTODOWNLOAD_TYPE_VIDEO, currentDocument!!.size)
						val path = FileLoader.getInstance(currentAccount).getPathToAttach(currentDocument, true)
						if (autoDownload || path.exists()) {
							imageView.strippedLocation = null
							imageView.setImage(ImageLocation.getForDocument(currentDocument), ImageLoader.AUTOPLAY_FILTER, null, null, ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", null, currentDocument!!.size, null, parentAdapter.currentPage, 1)
						}
						else {
							imageView.strippedLocation = ImageLocation.getForDocument(currentDocument)
							imageView.setImage(null, null, null, null, ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", null, currentDocument!!.size, null, parentAdapter.currentPage, 1)
						}
					}
					else {
						imageView.strippedLocation = null
						imageView.setImage(null, null, ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", 0, null, parentAdapter.currentPage, 1)
					}
					imageView.isAspectFit = true
					buttonX = (imageView.imageX + (imageView.imageWidth - size) / 2.0f).toInt()
					buttonY = (imageView.imageY + (imageView.imageHeight - size) / 2.0f).toInt()
					radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size)
				}
				textY = (imageView.imageY + imageView.imageHeight + AndroidUtilities.dp(8f)).toInt()
				if (currentType == 0) {
					captionLayout = createLayoutForText(this, null, currentBlock!!.caption!!.text, textWidth, textY, currentBlock!!, parentAdapter)
					if (captionLayout != null) {
						creditOffset = AndroidUtilities.dp(4f) + captionLayout!!.height
						height += creditOffset + AndroidUtilities.dp(4f)
						captionLayout?.setX(textX)
						captionLayout?.setY(textY)
					}
					creditLayout = createLayoutForText(this, null, currentBlock!!.caption!!.credit, textWidth, textY + creditOffset, currentBlock!!, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
					if (creditLayout != null) {
						height += AndroidUtilities.dp(4f) + creditLayout!!.height
						creditLayout!!.x = textX
						creditLayout!!.y = textY + creditOffset
					}
				}
				if (!isFirst && currentType == 0 && (currentBlock?.level ?: 0) <= 0) {
					height += AndroidUtilities.dp(8f)
				}
				val nextIsChannel = parentBlock is TLPageBlockCover && parentAdapter.blocks.size > 1 && parentAdapter.blocks[1] is TLPageBlockChannel
				if (currentType != 2 && !nextIsChannel) {
					height += AndroidUtilities.dp(8f)
				}
			}
			else {
				height = 1
			}
			channelCell.measure(widthMeasureSpec, heightMeasureSpec)
			channelCell.translationY = imageView.imageHeight - AndroidUtilities.dp(39f)

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			if (!imageView.hasBitmapImage() || imageView.currentAlpha != 1.0f) {
				canvas.drawRect(imageView.drawRegion, photoBackgroundPaint!!)
			}
			if (!pinchToZoomHelper!!.isInOverlayModeFor(this)) {
				imageView.draw(canvas)
				if (imageView.visible) {
					radialProgress.draw(canvas)
				}
			}
			var count = 0
			if (captionLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this, count++)
				captionLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (creditLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), (textY + creditOffset).toFloat())
				drawTextSelection(canvas, this, count)
				creditLayout!!.draw(canvas, this)
				canvas.restore()
			}

			if ((currentBlock?.level ?: 0) > 0) {
				canvas.drawRect(AndroidUtilities.dp(18f).toFloat(), 0f, AndroidUtilities.dp(20f).toFloat(), (measuredHeight - (if (currentBlock?.bottom == true) AndroidUtilities.dp(6f) else 0)).toFloat(), quoteLinePaint!!)
			}
		}

		val iconForCurrentState: Int
			get() {
				when (buttonState) {
					0 -> {
						return MediaActionDrawable.ICON_DOWNLOAD
					}

					1 -> {
						return MediaActionDrawable.ICON_CANCEL
					}

					2 -> {
						return MediaActionDrawable.ICON_GIF
					}

					3 -> {
						return MediaActionDrawable.ICON_PLAY
					}

					else -> {
						return MediaActionDrawable.ICON_NONE
					}
				}
			}

		fun updateButtonState(animated: Boolean) {
			val fileName = FileLoader.getAttachFileName(currentDocument)
			val path = FileLoader.getInstance(currentAccount).getPathToAttach(currentDocument, true)
			val fileExists = path.exists()

			if (fileName.isNullOrEmpty()) {
				radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, false)
				return
			}

			if (fileExists) {
				DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)

				buttonState = if (!isGif) {
					3
				}
				else {
					-1
				}
				radialProgress.setIcon(iconForCurrentState, false, animated)
			}
			else {
				DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this)
				var setProgress = 0f
				var progressVisible = false
				if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
					if (!cancelLoading && autoDownload && isGif) {
						progressVisible = true
						buttonState = 1
					}
					else {
						buttonState = 0
					}
				}
				else {
					progressVisible = true
					buttonState = 1
					val progress = ImageLoader.getInstance().getFileProgress(fileName)
					setProgress = progress ?: 0f
				}
				radialProgress.setIcon(iconForCurrentState, progressVisible, animated)
				radialProgress.setProgress(setProgress, false)
			}
			invalidate()
		}

		fun didPressedButton(animated: Boolean) {
			when (buttonState) {
				0 -> {
					cancelLoading = false
					radialProgress.setProgress(0f, false)
					if (isGif) {
						val thumb = FileLoader.getClosestPhotoSizeWithSize(currentDocument.thumbs, 40)
						imageView.setImage(ImageLocation.getForDocument(currentDocument), null, ImageLocation.getForDocument(thumb, currentDocument), "80_80_b", currentDocument!!.size, null, parentAdapter.currentPage, 1)
					}
					else {
						FileLoader.getInstance(currentAccount).loadFile(currentDocument, parentAdapter.currentPage, FileLoader.PRIORITY_NORMAL, 1)
					}
					buttonState = 1
					radialProgress.setIcon(iconForCurrentState, true, animated)
					invalidate()
				}

				1 -> {
					cancelLoading = true
					if (isGif) {
						imageView.cancelLoadImage()
					}
					else {
						FileLoader.getInstance(currentAccount).cancelLoadFile(currentDocument)
					}
					buttonState = 0
					radialProgress.setIcon(iconForCurrentState, false, animated)
					invalidate()
				}

				2 -> {
					imageView.allowStartAnimation = true
					imageView.startAnimation()
					buttonState = -1
					radialProgress.setIcon(iconForCurrentState, false, animated)
				}

				3 -> {
					openPhoto(currentBlock, parentAdapter)
				}
			}
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()
			imageView.onDetachedFromWindow()
			DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			imageView.onAttachedToWindow()
			updateButtonState(false)
		}

		override fun onFailedDownload(fileName: String, canceled: Boolean) {
			updateButtonState(false)
		}

		override fun onSuccessDownload(fileName: String) {
			radialProgress.setProgress(1f, true)
			if (isGif) {
				buttonState = 2
				didPressedButton(true)
			}
			else {
				updateButtonState(true)
			}
		}

		override fun onProgressUpload(fileName: String, uploadedSize: Long, totalSize: Long, isEncrypted: Boolean) {
		}

		override fun onProgressDownload(fileName: String, downloadSize: Long, totalSize: Long) {
			radialProgress.setProgress(min(1.0, (downloadSize / totalSize.toFloat()).toDouble()).toFloat(), true)
			if (buttonState != 1) {
				updateButtonState(true)
			}
		}

		override fun getObserverTag(): Int {
			return TAG
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
			val sb = StringBuilder(context.getString(R.string.AttachVideo))
			if (captionLayout != null) {
				sb.append(", ")
				sb.append(captionLayout!!.text)
			}
			info.text = sb.toString()
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (captionLayout != null) {
				blocks.add(captionLayout!!)
			}
			if (creditLayout != null) {
				blocks.add(creditLayout!!)
			}
		}
	}

	private inner class BlockAudioCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), FileDownloadProgressListener, ArticleSelectableView {
		private var captionLayout: DrawingText? = null
		private var creditLayout: DrawingText? = null
		private val radialProgress = RadialProgress2(this)
		private val seekBar: SeekBar
		private var isFirst = false
		private var textX = 0
		private val textY = AndroidUtilities.dp(58f)
		private var creditOffset = 0
		private var lastTimeString: String? = null
		private var titleLayout: DrawingText? = null
		private var durationLayout: StaticLayout? = null
		private var seekBarX = 0
		private var seekBarY = 0
		private var buttonX = 0
		private var buttonY = 0
		private var buttonState = 0
		private var buttonPressed = 0
		private val TAG: Int
		private var currentBlock: TLPageBlockAudio? = null
		private var currentDocument: TLRPC.Document? = null

		var messageObject: MessageObject? = null
			private set

		init {
			radialProgress.setCircleRadius(AndroidUtilities.dp(24f))
			TAG = DownloadController.getInstance(currentAccount).generateObserverTag()

			seekBar = SeekBar(this)

			seekBar.setDelegate(object : SeekBar.SeekBarDelegate {
				override fun onSeekBarContinuousDrag(progress: Float) {
					// unused
				}

				override fun onSeekBarDrag(progress: Float) {
					if (messageObject == null) {
						return
					}

					messageObject?.audioProgress = progress

					MediaController.getInstance().seekToProgress(messageObject, progress)
				}
			})
		}

		fun setBlock(block: TLPageBlockAudio?, first: Boolean, last: Boolean) {
			currentBlock = block

			messageObject = parentAdapter.audioBlocks[currentBlock]
			if (messageObject != null) {
				currentDocument = messageObject!!.document
			}

			isFirst = first

			seekBar.setColors(Theme.getColor(Theme.key_chat_inAudioSeekbar), Theme.getColor(Theme.key_chat_inAudioCacheSeekbar), Theme.getColor(Theme.key_chat_inAudioSeekbarFill), Theme.getColor(Theme.key_chat_inAudioSeekbarFill), Theme.getColor(Theme.key_chat_inAudioSeekbarSelected))

			updateButtonState(false)
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			val x = event.x
			val y = event.y

			val result = seekBar.onTouch(event.action, event.x - seekBarX, event.y - seekBarY)
			if (result) {
				if (event.action == MotionEvent.ACTION_DOWN) {
					parent.requestDisallowInterceptTouchEvent(true)
				}
				invalidate()
				return true
			}
			if (event.action == MotionEvent.ACTION_DOWN) {
				if (buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48f) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48f) || buttonState == 0) {
					buttonPressed = 1
					invalidate()
				}
			}
			else if (event.action == MotionEvent.ACTION_UP) {
				if (buttonPressed == 1) {
					buttonPressed = 0
					playSoundEffect(SoundEffectConstants.CLICK)
					didPressedButton(true)
					invalidate()
				}
			}
			else if (event.action == MotionEvent.ACTION_CANCEL) {
				buttonPressed = 0
			}
			return buttonPressed != 0 || checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event)
		}

		@SuppressLint("DrawAllocation", "NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = AndroidUtilities.dp(54f)
			val currentBlock = currentBlock

			if (currentBlock != null) {
				textX = if (currentBlock.level > 0) {
					AndroidUtilities.dp((14 * currentBlock.level).toFloat()) + AndroidUtilities.dp(18f)
				}
				else {
					AndroidUtilities.dp(18f)
				}
				val textWidth = width - textX - AndroidUtilities.dp(18f)
				val size = AndroidUtilities.dp(44f)
				buttonX = AndroidUtilities.dp(16f)
				buttonY = AndroidUtilities.dp(5f)
				radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size)

				captionLayout = createLayoutForText(this, null, currentBlock.caption!!.text, textWidth, textY, currentBlock, parentAdapter)
				if (captionLayout != null) {
					creditOffset = AndroidUtilities.dp(8f) + captionLayout!!.height
					height += creditOffset + AndroidUtilities.dp(8f)
				}
				creditLayout = createLayoutForText(this, null, currentBlock.caption!!.credit, textWidth, textY + creditOffset, currentBlock, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
				if (creditLayout != null) {
					height += AndroidUtilities.dp(4f) + creditLayout!!.height
				}

				if (!isFirst && currentBlock.level <= 0) {
					height += AndroidUtilities.dp(8f)
				}

				val author = messageObject!!.getMusicAuthor(false)
				val title = messageObject!!.getMusicTitle(false)
				seekBarX = buttonX + AndroidUtilities.dp(50f) + size
				val w = width - seekBarX - AndroidUtilities.dp(18f)
				if (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(author)) {
					val stringBuilder = if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(author)) {
						SpannableStringBuilder(String.format("%s - %s", author, title))
					}
					else if (!TextUtils.isEmpty(title)) {
						SpannableStringBuilder(title)
					}
					else {
						SpannableStringBuilder(author)
					}
					if (!TextUtils.isEmpty(author)) {
						val span = TypefaceSpan(Theme.TYPEFACE_BOLD)
						stringBuilder.setSpan(span, 0, author!!.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
					}
					val stringFinal = TextUtils.ellipsize(stringBuilder, Theme.chat_audioTitlePaint, w.toFloat(), TextUtils.TruncateAt.END)
					titleLayout = DrawingText()
					titleLayout!!.textLayout = StaticLayout(stringFinal, audioTimePaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
					titleLayout!!.parentBlock = currentBlock
					seekBarY = buttonY + (size - AndroidUtilities.dp(30f)) / 2 + AndroidUtilities.dp(11f)
				}
				else {
					titleLayout = null
					seekBarY = buttonY + (size - AndroidUtilities.dp(30f)) / 2
				}
				seekBar.setSize(w, AndroidUtilities.dp(30f))
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
			updatePlayingMessageProgress()
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			radialProgress.setColors(Theme.key_chat_inLoader, Theme.key_chat_inLoaderSelected, Theme.key_chat_inMediaIcon, Theme.key_chat_inMediaIconSelected)
			radialProgress.setProgressColor(Theme.getColor(Theme.key_chat_inFileProgress))
			radialProgress.draw(canvas)
			canvas.save()
			canvas.translate(seekBarX.toFloat(), seekBarY.toFloat())
			seekBar.draw(canvas)
			canvas.restore()
			var count = 0
			if (durationLayout != null) {
				canvas.save()
				canvas.translate((buttonX + AndroidUtilities.dp(54f)).toFloat(), (seekBarY + AndroidUtilities.dp(6f)).toFloat())
				durationLayout!!.draw(canvas)
				canvas.restore()
			}
			if (titleLayout != null) {
				canvas.save()
				titleLayout!!.x = buttonX + AndroidUtilities.dp(54f)
				titleLayout!!.y = seekBarY - AndroidUtilities.dp(16f)
				canvas.translate(titleLayout!!.x.toFloat(), titleLayout!!.y.toFloat())
				drawTextSelection(canvas, this, count++)
				titleLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (captionLayout != null) {
				canvas.save()
				captionLayout!!.x = textX
				captionLayout!!.y = textY
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this, count++)
				captionLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (creditLayout != null) {
				canvas.save()
				creditLayout!!.x = textX
				creditLayout!!.y = textY + creditOffset
				canvas.translate(textX.toFloat(), (textY + creditOffset).toFloat())
				drawTextSelection(canvas, this, count)
				creditLayout!!.draw(canvas, this)
				canvas.restore()
			}

			if ((currentBlock?.level ?: 0) > 0) {
				canvas.drawRect(AndroidUtilities.dp(18f).toFloat(), 0f, AndroidUtilities.dp(20f).toFloat(), (measuredHeight - (if (currentBlock?.bottom == true) AndroidUtilities.dp(6f) else 0)).toFloat(), quoteLinePaint!!)
			}
		}

		val iconForCurrentState: Int
			get() {
				if (buttonState == 1) {
					return MediaActionDrawable.ICON_PAUSE
				}
				else if (buttonState == 2) {
					return MediaActionDrawable.ICON_DOWNLOAD
				}
				else if (buttonState == 3) {
					return MediaActionDrawable.ICON_CANCEL
				}
				return MediaActionDrawable.ICON_PLAY
			}

		fun updatePlayingMessageProgress() {
			if (currentDocument == null || messageObject == null) {
				return
			}

			if (!seekBar.isDragging) {
				seekBar.progress = messageObject!!.audioProgress
			}

			var duration = 0

			if (MediaController.getInstance().isPlayingMessage(messageObject)) {
				duration = messageObject!!.audioProgressSec
			}
			else {
				val attributes = (currentDocument as? TLRPC.TLDocument)?.attributes

				if (!attributes.isNullOrEmpty()) {
					for (attribute in attributes) {
						if (attribute is TLDocumentAttributeAudio) {
							duration = attribute.duration
							break
						}
					}
				}
			}

			val timeString = AndroidUtilities.formatShortDuration(duration)
			if (lastTimeString == null || lastTimeString != timeString) {
				lastTimeString = timeString
				audioTimePaint.textSize = AndroidUtilities.dp(16f).toFloat()
				val timeWidth = ceil(audioTimePaint.measureText(timeString).toDouble()).toInt()
				durationLayout = StaticLayout(timeString, audioTimePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
			}
			audioTimePaint.color = textColor
			invalidate()
		}

		fun updateButtonState(animated: Boolean) {
			val fileName = FileLoader.getAttachFileName(currentDocument)
			val path = FileLoader.getInstance(currentAccount).getPathToAttach(currentDocument, true)
			val fileExists = path.exists()
			if (TextUtils.isEmpty(fileName)) {
				radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, false)
				return
			}
			if (fileExists) {
				DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
				val playing = MediaController.getInstance().isPlayingMessage(messageObject)
				buttonState = if (!playing || playing && MediaController.getInstance().isMessagePaused) {
					0
				}
				else {
					1
				}
				radialProgress.setIcon(iconForCurrentState, false, animated)
			}
			else {
				DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this)
				if (!FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
					buttonState = 2
					radialProgress.setProgress(0f, animated)
					radialProgress.setIcon(iconForCurrentState, false, animated)
				}
				else {
					buttonState = 3
					val progress = ImageLoader.getInstance().getFileProgress(fileName)
					if (progress != null) {
						radialProgress.setProgress(progress, animated)
					}
					else {
						radialProgress.setProgress(0f, animated)
					}
					radialProgress.setIcon(iconForCurrentState, true, animated)
				}
			}
			updatePlayingMessageProgress()
		}

		fun didPressedButton(animated: Boolean) {
			if (buttonState == 0) {
				if (MediaController.getInstance().setPlaylist(parentAdapter.audioMessages, messageObject, 0, false, null)) {
					buttonState = 1
					radialProgress.setIcon(iconForCurrentState, false, animated)
					invalidate()
				}
			}
			else if (buttonState == 1) {
				val result = MediaController.getInstance().pauseMessage(messageObject)
				if (result) {
					buttonState = 0
					radialProgress.setIcon(iconForCurrentState, false, animated)
					invalidate()
				}
			}
			else if (buttonState == 2) {
				radialProgress.setProgress(0f, false)
				FileLoader.getInstance(currentAccount).loadFile(currentDocument, parentAdapter.currentPage, 1, 1)
				buttonState = 3
				radialProgress.setIcon(iconForCurrentState, true, animated)
				invalidate()
			}
			else if (buttonState == 3) {
				FileLoader.getInstance(currentAccount).cancelLoadFile(currentDocument)
				buttonState = 2
				radialProgress.setIcon(iconForCurrentState, false, animated)
				invalidate()
			}
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()
			DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			updateButtonState(false)
		}

		override fun onFailedDownload(fileName: String, canceled: Boolean) {
			updateButtonState(true)
		}

		override fun onSuccessDownload(fileName: String) {
			radialProgress.setProgress(1f, true)
			updateButtonState(true)
		}

		override fun onProgressUpload(fileName: String, uploadedSize: Long, totalSize: Long, isEncrypted: Boolean) {
		}

		override fun onProgressDownload(fileName: String, downloadSize: Long, totalSize: Long) {
			radialProgress.setProgress(min(1.0, (downloadSize / totalSize.toFloat()).toDouble()).toFloat(), true)
			if (buttonState != 3) {
				updateButtonState(true)
			}
		}

		override fun getObserverTag(): Int {
			return TAG
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (titleLayout != null) {
				blocks.add(titleLayout!!)
			}
			if (captionLayout != null) {
				blocks.add(captionLayout!!)
			}
			if (creditLayout != null) {
				blocks.add(creditLayout!!)
			}
		}
	}

	private inner class BlockEmbedPostCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private val avatarImageView = ImageReceiver(this)
		private val avatarDrawable: AvatarDrawable
		private var dateLayout: DrawingText? = null
		private var nameLayout: DrawingText? = null
		private var captionLayout: DrawingText? = null
		private var creditLayout: DrawingText? = null
		private var avatarVisible = false
		private val nameX = 0
		private val dateX = 0

		private var textX = 0
		private var textY = 0
		private var creditOffset = 0

		private var lineHeight = 0

		private var currentBlock: TLPageBlockEmbedPost? = null

		init {
			avatarImageView.setRoundRadius(AndroidUtilities.dp(20f))
			avatarImageView.setImageCoordinates(AndroidUtilities.dp((18 + 14).toFloat()).toFloat(), AndroidUtilities.dp(8f).toFloat(), AndroidUtilities.dp(40f).toFloat(), AndroidUtilities.dp(40f).toFloat())

			avatarDrawable = AvatarDrawable()
		}

		fun setBlock(block: TLPageBlockEmbedPost?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height: Int
			val currentBlock = currentBlock

			if (currentBlock != null) {
				if (currentBlock is TLPageBlockEmbedPostCaption) {
					height = 0
					textX = AndroidUtilities.dp(18f)
					textY = AndroidUtilities.dp(4f)
					val textWidth = width - AndroidUtilities.dp((36 + 14).toFloat())
					captionLayout = createLayoutForText(this, null, currentBlock.caption!!.text, textWidth, textY, currentBlock, parentAdapter)
					if (captionLayout != null) {
						creditOffset = AndroidUtilities.dp(4f) + captionLayout!!.height
						height += creditOffset + AndroidUtilities.dp(4f)
					}
					creditLayout = createLayoutForText(this, null, currentBlock.caption!!.credit, textWidth, textY + creditOffset, currentBlock, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
					if (creditLayout != null) {
						height += AndroidUtilities.dp(4f) + creditLayout!!.height
					}
				}
				else {
					if ((currentBlock.authorPhotoId != 0L).also { avatarVisible = it }) {
						val photo = parentAdapter.getPhotoWithId(currentBlock.authorPhotoId)

						if ((photo is TLRPC.TLPhoto).also { avatarVisible = it }) {
							avatarDrawable.setInfo(currentBlock.author, null)
							val image = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.dp(40f), true)
							avatarImageView.setImage(ImageLocation.getForPhoto(image, photo), "40_40", avatarDrawable, 0, null, parentAdapter.currentPage, 1)
						}
					}

					nameLayout = createLayoutForText(this, currentBlock.author, null, width - AndroidUtilities.dp((36 + 14 + (if (avatarVisible) 40 + 14 else 0)).toFloat()), 0, currentBlock, Layout.Alignment.ALIGN_NORMAL, 1, parentAdapter)

					if (nameLayout != null) {
						nameLayout!!.x = AndroidUtilities.dp((18 + 14 + (if (avatarVisible) 40 + 14 else 0)).toFloat())
						nameLayout!!.y = AndroidUtilities.dp((if (dateLayout != null) 10 else 19).toFloat())
					}

					dateLayout = if (currentBlock.date != 0) {
						createLayoutForText(this, LocaleController.getInstance().chatFullDate.format(currentBlock.date.toLong() * 1000), null, width - AndroidUtilities.dp((36 + 14 + (if (avatarVisible) 40 + 14 else 0)).toFloat()), AndroidUtilities.dp(29f), currentBlock, parentAdapter)
					}
					else {
						null
					}

					height = AndroidUtilities.dp((40 + 8 + 8).toFloat())

					if (currentBlock.blocks.isEmpty()) {
						textX = AndroidUtilities.dp((18 + 14).toFloat())
						textY = AndroidUtilities.dp((40 + 8 + 8).toFloat())
						val textWidth = width - AndroidUtilities.dp((36 + 14).toFloat())
						captionLayout = createLayoutForText(this, null, currentBlock.caption!!.text, textWidth, textY, currentBlock, parentAdapter)
						if (captionLayout != null) {
							creditOffset = AndroidUtilities.dp(4f) + captionLayout!!.height
							height += creditOffset + AndroidUtilities.dp(4f)
						}
						creditLayout = createLayoutForText(this, null, currentBlock.caption!!.credit, textWidth, textY + creditOffset, currentBlock, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
						if (creditLayout != null) {
							height += AndroidUtilities.dp(4f) + creditLayout!!.height
						}
					}
					else {
						captionLayout = null
						creditLayout = null
					}

					if (dateLayout != null) {
						dateLayout!!.x = AndroidUtilities.dp((18 + 14 + (if (avatarVisible) 40 + 14 else 0)).toFloat())
						dateLayout!!.y = AndroidUtilities.dp(29f)
					}

					if (captionLayout != null) {
						captionLayout!!.x = textX
						captionLayout!!.y = textY
					}
					if (creditLayout != null) {
						creditLayout!!.x = textX
						creditLayout!!.y = textY
					}
				}
				lineHeight = height
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			var count = 0
			if (currentBlock !is TLPageBlockEmbedPostCaption) {
				if (avatarVisible) {
					avatarImageView.draw(canvas)
				}
				if (nameLayout != null) {
					canvas.save()
					canvas.translate(AndroidUtilities.dp((18 + 14 + (if (avatarVisible) 40 + 14 else 0)).toFloat()).toFloat(), AndroidUtilities.dp((if (dateLayout != null) 10 else 19).toFloat()).toFloat())
					drawTextSelection(canvas, this, count++)
					nameLayout!!.draw(canvas, this)
					canvas.restore()
				}
				if (dateLayout != null) {
					canvas.save()
					canvas.translate(AndroidUtilities.dp((18 + 14 + (if (avatarVisible) 40 + 14 else 0)).toFloat()).toFloat(), AndroidUtilities.dp(29f).toFloat())
					drawTextSelection(canvas, this, count++)
					dateLayout!!.draw(canvas, this)
					canvas.restore()
				}
				canvas.drawRect(AndroidUtilities.dp(18f).toFloat(), AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp(20f).toFloat(), (lineHeight - (if (currentBlock?.level != 0) 0 else AndroidUtilities.dp(6f))).toFloat(), quoteLinePaint!!)
			}
			if (captionLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this, count++)
				captionLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (creditLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), (textY + creditOffset).toFloat())
				drawTextSelection(canvas, this, count)
				creditLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (nameLayout != null) {
				blocks.add(nameLayout!!)
			}
			if (dateLayout != null) {
				blocks.add(dateLayout!!)
			}
			if (captionLayout != null) {
				blocks.add(captionLayout!!)
			}
			if (creditLayout != null) {
				blocks.add(creditLayout!!)
			}
		}
	}

	inner class BlockParagraphCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		var textLayout: DrawingText? = null
		var textX: Int = 0
		var textY: Int = 0

		private var currentBlock: TLPageBlockParagraph? = null

		fun setBlock(block: TLPageBlockParagraph?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0
			val currentBlock = currentBlock

			if (currentBlock != null) {
				if (currentBlock.level == 0) {
					textY = AndroidUtilities.dp(8f)
					textX = AndroidUtilities.dp(18f)
				}
				else {
					textY = 0
					textX = AndroidUtilities.dp((18 + 14 * currentBlock.level).toFloat())
				}
				textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(18f) - textX, textY, currentBlock, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, 0, parentAdapter)
				if (textLayout != null) {
					height = textLayout!!.height
					height += if (currentBlock.level > 0) {
						AndroidUtilities.dp(8f)
					}
					else {
						AndroidUtilities.dp((8 + 8).toFloat())
					}
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			val currentBlock = currentBlock ?: return

			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout?.draw(canvas, this)
				canvas.restore()
			}

			if (currentBlock.level > 0) {
				canvas.drawRect(AndroidUtilities.dp(18f).toFloat(), 0f, AndroidUtilities.dp(20f).toFloat(), (measuredHeight - (if (currentBlock.bottom) AndroidUtilities.dp(6f) else 0)).toFloat(), quoteLinePaint!!)
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
			if (textLayout == null) {
				return
			}
			info.text = textLayout!!.text
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private inner class BlockEmbedCell @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface") constructor(context: Context, private val parentAdapter: WebpageAdapter) : FrameLayout(context), ArticleSelectableView {
		private inner class TelegramWebviewProxy {
			@JavascriptInterface
			fun postEvent(eventName: String, eventData: String) {
				AndroidUtilities.runOnUIThread {
					if ("resize_frame" == eventName) {
						try {
							val `object` = JSONObject(eventData)
							exactWebViewHeight = Utilities.parseInt(`object`.getString("height"))
							requestLayout()
						}
						catch (ignore: Throwable) {
						}
					}
				}
			}
		}

		private val webView = TouchyWebView(context)
		private lateinit var videoView: WebPlayerView
		private var captionLayout: DrawingText? = null
		private var creditLayout: DrawingText? = null
		private var textX = 0
		private var textY = 0
		private var creditOffset = 0
		private var listX = 0
		private var exactWebViewHeight = 0
		private var wasUserInteraction = false

		private var currentBlock: TLPageBlockEmbed? = null

		inner class TouchyWebView(context: Context) : WebView(context) {
			init {
				isFocusable = false
			}

			override fun onTouchEvent(event: MotionEvent): Boolean {
				wasUserInteraction = true

				if (currentBlock != null) {
					if (currentBlock?.allowScrolling == true) {
						requestDisallowInterceptTouchEvent(true)
					}
					else {
						windowView?.requestDisallowInterceptTouchEvent(true)
					}
				}

				return super.onTouchEvent(event)
			}
		}

		init {
			setWillNotDraw(false)

			videoView = WebPlayerView(context, false, false, object : WebPlayerViewDelegate {
				override fun onInitFailed() {
					webView.visibility = VISIBLE
					videoView.visibility = INVISIBLE
					videoView.loadVideo(null, null, null, null, false)
					val args = HashMap<String, String>()
					args["Referer"] = ApplicationLoader.applicationContext.packageName
					webView.loadUrl(currentBlock!!.url!!, args)
				}

				override fun onVideoSizeChanged(aspectRatio: Float, rotation: Int) {
					fullscreenAspectRatioView!!.setAspectRatio(aspectRatio, rotation)
				}

				override fun onInlineSurfaceTextureReady() {
				}

				override fun onSwitchToFullscreen(controlsView: View, fullscreen: Boolean, aspectRatio: Float, rotation: Int, byButton: Boolean): TextureView? {
					if (fullscreen) {
						fullscreenAspectRatioView!!.addView(fullscreenTextureView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
						fullscreenAspectRatioView!!.visibility = VISIBLE
						fullscreenAspectRatioView!!.setAspectRatio(aspectRatio, rotation)
						fullscreenedVideo = videoView
						fullscreenVideoContainer!!.addView(controlsView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
						fullscreenVideoContainer!!.visibility = VISIBLE
					}
					else {
						fullscreenAspectRatioView!!.removeView(fullscreenTextureView)
						fullscreenedVideo = null
						fullscreenAspectRatioView!!.visibility = GONE
						fullscreenVideoContainer!!.visibility = INVISIBLE
					}
					return fullscreenTextureView
				}

				override fun prepareToSwitchInlineMode(inline: Boolean, switchInlineModeRunnable: Runnable, aspectRatio: Float, animated: Boolean) {
				}

				override fun onSwitchInlineMode(controlsView: View, inline: Boolean, videoWidth: Int, videoHeight: Int, rotation: Int, animated: Boolean): TextureView? {
					return null
				}

				override fun onSharePressed() {
					if (parentActivity == null) {
						return
					}
					showDialog(ShareAlert(parentActivity!!, null, currentBlock!!.url, false, currentBlock!!.url, false))
				}

				override fun onPlayStateChanged(playerView: WebPlayerView, playing: Boolean) {
					if (playing) {
						if (currentPlayingVideo != null && currentPlayingVideo !== playerView) {
							currentPlayingVideo!!.pause()
						}
						currentPlayingVideo = playerView
						try {
							parentActivity!!.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
					else {
						if (currentPlayingVideo === playerView) {
							currentPlayingVideo = null
						}
						try {
							parentActivity!!.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
						}
						catch (e: Exception) {
							FileLog.e(e)
						}
					}
				}

				override fun checkInlinePermissions(): Boolean {
					return false
				}

				override fun getTextureViewContainer(): ViewGroup? {
					return null
				}
			})
			addView(videoView)
			createdWebViews.add(this)

			webView.settings.javaScriptEnabled = true
			webView.settings.domStorageEnabled = true

			webView.settings.allowContentAccess = true
			webView.settings.mediaPlaybackRequiresUserGesture = false
			webView.addJavascriptInterface(TelegramWebviewProxy(), "TelegramWebviewProxy")

			webView.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

			val cookieManager = CookieManager.getInstance()
			cookieManager.setAcceptThirdPartyCookies(webView, true)

			webView.webChromeClient = object : WebChromeClient() {
				override fun onShowCustomView(view: View, requestedOrientation: Int, callback: CustomViewCallback) {
					onShowCustomView(view, callback)
				}

				override fun onShowCustomView(view: View, callback: CustomViewCallback) {
					if (customView != null) {
						callback.onCustomViewHidden()
						return
					}
					customView = view
					customViewCallback = callback
					AndroidUtilities.runOnUIThread({
						if (customView != null) {
							fullscreenVideoContainer!!.addView(customView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
							fullscreenVideoContainer!!.visibility = VISIBLE
						}
					}, 100)
				}

				override fun onHideCustomView() {
					super.onHideCustomView()
					if (customView == null) {
						return
					}
					fullscreenVideoContainer!!.visibility = INVISIBLE
					fullscreenVideoContainer!!.removeView(customView)
					if (customViewCallback != null && !customViewCallback!!.javaClass.name.contains(".chromium.")) {
						customViewCallback!!.onCustomViewHidden()
					}
					customView = null
				}
			}

			webView.webViewClient = object : WebViewClient() {
				override fun onLoadResource(view: WebView, url: String) {
					super.onLoadResource(view, url)
				}

				override fun onPageFinished(view: WebView, url: String) {
					super.onPageFinished(view, url)
					//progressBar.setVisibility(INVISIBLE);
				}

				override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
					if (wasUserInteraction) {
						Browser.openUrl(parentActivity, url)
						return true
					}
					return false
				}
			}
			addView(webView)
		}

		fun destroyWebView(completely: Boolean) {
			try {
				webView.stopLoading()
				webView.loadUrl("about:blank")
				if (completely) {
					webView.destroy()
				}
				currentBlock = null
			}
			catch (e: Exception) {
				FileLog.e(e)
			}
			videoView.destroy()
		}

		fun setBlock(block: TLPageBlockEmbed) {
			val previousBlock = currentBlock
			currentBlock = block
			webView.setBackgroundColor(parentActivity!!.getColor(R.color.background))
			if (previousBlock !== currentBlock) {
				wasUserInteraction = false
				if (currentBlock?.allowScrolling == true) {
					webView.isVerticalScrollBarEnabled = true
					webView.isHorizontalScrollBarEnabled = true
				}
				else {
					webView.isVerticalScrollBarEnabled = false
					webView.isHorizontalScrollBarEnabled = false
				}
				exactWebViewHeight = 0
				try {
					webView.loadUrl("about:blank")
				}
				catch (e: Exception) {
					FileLog.e(e)
				}

				try {
					if (currentBlock!!.html != null) {
						webView.loadDataWithBaseURL("https://ello.team/embed", currentBlock!!.html!!, "text/html", "UTF-8", null)
						videoView.visibility = INVISIBLE
						videoView.loadVideo(null, null, null, null, false)
						webView.visibility = VISIBLE
					}
					else {
						val thumb = if (currentBlock!!.posterPhotoId != 0L) parentAdapter.getPhotoWithId(currentBlock!!.posterPhotoId) else null
						val handled = videoView.loadVideo(block.url, thumb, parentAdapter.currentPage, null, false)
						if (handled) {
							webView.visibility = INVISIBLE
							videoView.visibility = VISIBLE
							webView.stopLoading()
							webView.loadUrl("about:blank")
						}
						else {
							webView.visibility = VISIBLE
							videoView.visibility = INVISIBLE
							videoView.loadVideo(null, null, null, null, false)
							val args = HashMap<String, String>()
							args["Referer"] = ApplicationLoader.applicationContext.packageName
							webView.loadUrl(currentBlock!!.url!!, args)
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
			requestLayout()
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()
			if (!isVisible) {
				currentBlock = null
			}
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height: Int
			val currentBlock = currentBlock

			if (currentBlock != null) {
				var listWidth = width
				val textWidth: Int
				if (currentBlock.level > 0) {
					listX = AndroidUtilities.dp((14 * currentBlock.level).toFloat()) + AndroidUtilities.dp(18f)
					textX = listX
					listWidth -= listX + AndroidUtilities.dp(18f)
					textWidth = listWidth
				}
				else {
					listX = 0
					textX = AndroidUtilities.dp(18f)
					textWidth = width - AndroidUtilities.dp(36f)
					if (!currentBlock.fullWidth) {
						listWidth -= AndroidUtilities.dp(36f)
						listX += AndroidUtilities.dp(18f)
					}
				}
				val scale = if (currentBlock.w == 0) {
					1f
				}
				else {
					width / currentBlock.w.toFloat()
				}
				height = if (exactWebViewHeight != 0) {
					AndroidUtilities.dp(exactWebViewHeight.toFloat())
				}
				else {
					(if (currentBlock.w == 0) AndroidUtilities.dp(currentBlock.h.toFloat()) * scale else currentBlock.h * scale).toInt()
				}
				if (height == 0) {
					height = AndroidUtilities.dp(10f)
				}
				webView.measure(MeasureSpec.makeMeasureSpec(listWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
				if (videoView.parent === this) {
					videoView.measure(MeasureSpec.makeMeasureSpec(listWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height + AndroidUtilities.dp(10f), MeasureSpec.EXACTLY))
				}

				textY = AndroidUtilities.dp(8f) + height
				captionLayout = createLayoutForText(this, null, currentBlock.caption!!.text, textWidth, textY, currentBlock, parentAdapter)
				if (captionLayout != null) {
					creditOffset = AndroidUtilities.dp(4f) + captionLayout!!.height
					height += creditOffset + AndroidUtilities.dp(4f)
				}
				else {
					creditOffset = 0
				}
				creditLayout = createLayoutForText(this, null, currentBlock.caption!!.credit, textWidth, textY + creditOffset, currentBlock, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
				if (creditLayout != null) {
					height += AndroidUtilities.dp(4f) + creditLayout!!.height
					creditLayout!!.x = textX
					creditLayout!!.y = creditOffset
				}

				height += AndroidUtilities.dp(5f)

				if (currentBlock.level > 0 && !currentBlock.bottom) {
					height += AndroidUtilities.dp(8f)
				}
				else if (currentBlock.level === 0 && captionLayout != null) {
					height += AndroidUtilities.dp(8f)
				}
				if (captionLayout != null) {
					captionLayout!!.x = textX
					captionLayout!!.y = textY
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
			webView.layout(listX, 0, listX + webView.measuredWidth, webView.measuredHeight)
			if (videoView.parent === this) {
				videoView.layout(listX, 0, listX + videoView.measuredWidth, videoView.measuredHeight)
			}
		}

		override fun onDraw(canvas: Canvas) {
			val currentBlock = currentBlock ?: return
			var count = 0

			if (captionLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this, count++)
				captionLayout!!.draw(canvas, this)
				canvas.restore()
			}

			if (creditLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), (textY + creditOffset).toFloat())
				drawTextSelection(canvas, this, count)
				creditLayout!!.draw(canvas, this)
				canvas.restore()
			}

			if (currentBlock.level > 0) {
				canvas.drawRect(AndroidUtilities.dp(18f).toFloat(), 0f, AndroidUtilities.dp(20f).toFloat(), (measuredHeight - (if (currentBlock.bottom) AndroidUtilities.dp(6f) else 0)).toFloat(), quoteLinePaint!!)
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (captionLayout != null) {
				blocks.add(captionLayout!!)
			}

			if (creditLayout != null) {
				blocks.add(creditLayout!!)
			}
		}
	}

	inner class BlockTableCell(context: Context, private val parentAdapter: WebpageAdapter) : FrameLayout(context), TableLayoutDelegate, ArticleSelectableView {
		private val scrollView: HorizontalScrollView
		private var titleLayout: DrawingText? = null
		private val tableLayout = TableLayout(context, this, textSelectionHelper)
		private var listX = 0
		private var listY = 0
		private var textX = 0
		private var textY = 0

		private var firstLayout = false

		private var currentBlock: TLPageBlockTable? = null

		init {
			scrollView = object : HorizontalScrollView(context) {
				override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
					val intercept = super.onInterceptTouchEvent(ev)
					if (tableLayout.measuredWidth > measuredWidth - AndroidUtilities.dp(36f) && intercept) {
						windowView!!.requestDisallowInterceptTouchEvent(true)
					}
					return intercept
				}

				override fun onTouchEvent(ev: MotionEvent): Boolean {
					if (tableLayout.measuredWidth <= measuredWidth - AndroidUtilities.dp(36f)) {
						return false
					}
					return super.onTouchEvent(ev)
				}

				override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
					super.onScrollChanged(l, t, oldl, oldt)
					if (pressedLinkOwnerLayout != null) {
						pressedLinkOwnerLayout = null
						pressedLinkOwnerView = null
					}
					updateChildTextPositions()
					if (textSelectionHelper != null && textSelectionHelper!!.isSelectionMode) {
						textSelectionHelper!!.invalidate()
					}
				}

				override fun overScrollBy(deltaX: Int, deltaY: Int, scrollX: Int, scrollY: Int, scrollRangeX: Int, scrollRangeY: Int, maxOverScrollX: Int, maxOverScrollY: Int, isTouchEvent: Boolean): Boolean {
					removePressedLink()
					return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent)
				}

				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					tableLayout.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight, MeasureSpec.UNSPECIFIED), heightMeasureSpec)
					setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), tableLayout.measuredHeight)
				}
			}
			scrollView.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
			scrollView.setClipToPadding(false)
			addView(scrollView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

			tableLayout.orientation = TableLayout.HORIZONTAL
			tableLayout.isRowOrderPreserved = true
			scrollView.addView(tableLayout, LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT))

			setWillNotDraw(false)
		}

		override fun createTextLayout(cell: TLPageTableCell?, maxWidth: Int): DrawingText? {
			if (cell == null) {
				return null
			}

			val alignment = if (cell.alignRight) {
				Layout.Alignment.ALIGN_OPPOSITE
			}
			else if (cell.alignCenter) {
				Layout.Alignment.ALIGN_CENTER
			}
			else {
				Layout.Alignment.ALIGN_NORMAL
			}

			return createLayoutForText(this, null, cell.text, maxWidth, -1, currentBlock, alignment, 0, parentAdapter)
		}

		override fun getLinePaint(): Paint {
			return tableLinePaint!!
		}

		override fun getHalfLinePaint(): Paint {
			return tableHalfLinePaint!!
		}

		override fun getHeaderPaint(): Paint {
			return tableHeaderPaint!!
		}

		override fun getStripPaint(): Paint {
			return tableStripPaint!!
		}

		override fun onLayoutChild(text: DrawingText?, x: Int, y: Int) {
			if (text != null && !searchResults.isNullOrEmpty() && searchText != null) {
				val lowerString = text.textLayout!!.text.toString().lowercase()
				var startIndex = 0
				var index: Int
				while ((lowerString.indexOf(searchText!!, startIndex).also { index = it }) >= 0) {
					startIndex = index + searchText!!.length
					if (index == 0 || AndroidUtilities.isPunctuationCharacter(lowerString[index - 1])) {
						adapter!![0].searchTextOffset[searchText + currentBlock + text.parentText + index] = y + text.textLayout!!.getLineTop(text.textLayout!!.getLineForOffset(index))
					}
				}
			}
		}

		fun setBlock(block: TLPageBlockTable?) {
			currentBlock = block
			AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, parentActivity!!.getColor(R.color.background))
			tableLayout.removeAllChildrens()
			tableLayout.setDrawLines(currentBlock!!.bordered)
			tableLayout.setStriped(currentBlock!!.striped)
			tableLayout.setRtl(parentAdapter.isRtl)

			var maxCols = 0

			if (!currentBlock!!.rows.isEmpty()) {
				val row = currentBlock!!.rows[0]
				var c = 0
				val size2 = row.cells.size
				while (c < size2) {
					val cell = row.cells[c]
					maxCols += (if (cell.colspan != 0) cell.colspan else 1)
					c++
				}
			}

			var r = 0
			val size = currentBlock!!.rows.size
			while (r < size) {
				val row = currentBlock!!.rows[r]
				var cols = 0
				var c = 0
				val size2 = row.cells.size
				while (c < size2) {
					val cell = row.cells[c]
					val colspan = (if (cell.colspan != 0) cell.colspan else 1)
					val rowspan = (if (cell.rowspan != 0) cell.rowspan else 1)
					if (cell.text != null) {
						tableLayout.addChild(cell, cols, r, colspan)
					}
					else {
						tableLayout.addChild(cols, r, colspan, rowspan)
					}
					cols += colspan
					c++
				}
				r++
			}
			tableLayout.columnCount = maxCols
			firstLayout = true
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			var i = 0
			val N = tableLayout.childCount
			while (i < N) {
				val c = tableLayout.getChildAt(i)
				if (checkLayoutForLinks(parentAdapter, event, this, c.textLayout, scrollView.paddingLeft - scrollView.scrollX + listX + c.getTextX(), listY + c.getTextY())) {
					return true
				}
				i++
			}
			return checkLayoutForLinks(parentAdapter, event, this, titleLayout, textX, textY) || super.onTouchEvent(event)
		}

		override fun invalidate() {
			super.invalidate()
			tableLayout.invalidate()
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0
			val currentBlock = currentBlock

			if (currentBlock != null) {
				val textWidth: Int
				if (currentBlock.level > 0) {
					listX = AndroidUtilities.dp((14 * currentBlock.level).toFloat())
					textX = listX + AndroidUtilities.dp(18f)
					textWidth = width - textX
				}
				else {
					listX = 0
					textX = AndroidUtilities.dp(18f)
					textWidth = width - AndroidUtilities.dp(36f)
				}

				titleLayout = createLayoutForText(this, null, currentBlock.title, textWidth, 0, currentBlock, Layout.Alignment.ALIGN_CENTER, 0, parentAdapter)
				if (titleLayout != null) {
					textY = 0
					height += titleLayout!!.height + AndroidUtilities.dp(8f)
					listY = height
					titleLayout!!.x = textX
					titleLayout!!.y = textY
				}
				else {
					listY = AndroidUtilities.dp(8f)
				}

				scrollView.measure(MeasureSpec.makeMeasureSpec(width - listX, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
				height += scrollView.measuredHeight + AndroidUtilities.dp(8f)

				if (currentBlock.level > 0 && !currentBlock.bottom) {
					height += AndroidUtilities.dp(8f)
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
			updateChildTextPositions()
		}

		private fun updateChildTextPositions() {
			var count = if (titleLayout == null) 0 else 1
			var i = 0
			val N = tableLayout.childCount
			while (i < N) {
				val c = tableLayout.getChildAt(i)
				if (c.textLayout != null) {
					c.textLayout?.x = c.getTextX() + listX + AndroidUtilities.dp(18f) - scrollView.scrollX
					c.textLayout?.y = c.getTextY() + listY
					c.textLayout?.setRow(c.row)
					c.setSelectionIndex(count++)
				}
				i++
			}
		}

		override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
			scrollView.layout(listX, listY, listX + scrollView.measuredWidth, listY + scrollView.measuredHeight)
			if (firstLayout) {
				if (parentAdapter.isRtl) {
					scrollView.scrollX = tableLayout.measuredWidth - scrollView.measuredWidth + AndroidUtilities.dp(36f)
				}
				else {
					scrollView.scrollX = 0
				}
				firstLayout = false
			}
		}

		override fun onDraw(canvas: Canvas) {
			val currentBlock = currentBlock ?: return

			if (titleLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this, 0)
				titleLayout?.draw(canvas, this)
				canvas.restore()
			}

			if (currentBlock.level > 0) {
				canvas.drawRect(AndroidUtilities.dp(18f).toFloat(), 0f, AndroidUtilities.dp(20f).toFloat(), (measuredHeight - (if (currentBlock.bottom) AndroidUtilities.dp(6f) else 0)).toFloat(), quoteLinePaint!!)
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (titleLayout != null) {
				blocks.add(titleLayout!!)
			}

			var i = 0
			val N = tableLayout.childCount
			while (i < N) {
				val c = tableLayout.getChildAt(i)
				if (c.textLayout != null) {
					blocks.add(c.textLayout)
				}
				i++
			}
		}
	}

	private class MessageGroupedLayoutAttempt {
		var lineCounts: IntArray
		var heights: FloatArray

		constructor(i1: Int, i2: Int, f1: Float, f2: Float) {
			lineCounts = intArrayOf(i1, i2)
			heights = floatArrayOf(f1, f2)
		}

		constructor(i1: Int, i2: Int, i3: Int, f1: Float, f2: Float, f3: Float) {
			lineCounts = intArrayOf(i1, i2, i3)
			heights = floatArrayOf(f1, f2, f3)
		}

		constructor(i1: Int, i2: Int, i3: Int, i4: Int, f1: Float, f2: Float, f3: Float, f4: Float) {
			lineCounts = intArrayOf(i1, i2, i3, i4)
			heights = floatArrayOf(f1, f2, f3, f4)
		}
	}

	private inner class BlockCollageCell(context: Context, private val parentAdapter: WebpageAdapter) : FrameLayout(context), ArticleSelectableView {
		val innerListView: RecyclerListView
		private var innerAdapter: RecyclerView.Adapter<*>? = null
		private var captionLayout: DrawingText? = null
		private var creditLayout: DrawingText? = null
		private var listX = 0
		private var textX = 0
		private var textY = 0
		private var creditOffset = 0

		private var inLayout = false

		private var currentBlock: TLPageBlockCollage? = null
		private val group: GroupedMessages = GroupedMessages()

		inner class GroupedMessages {
			var groupId: Long = 0
			var hasSibling: Boolean = false
			var posArray: ArrayList<GroupedMessagePosition> = ArrayList()
			var positions: HashMap<TLObject?, GroupedMessagePosition> = HashMap()

			private val maxSizeWidth = 1000

			private fun multiHeight(array: FloatArray, start: Int, end: Int): Float {
				var sum = 0f
				for (a in start..<end) {
					sum += array[a]
				}
				return maxSizeWidth / sum
			}

			fun calculate() {
				posArray.clear()
				positions.clear()
				val count = currentBlock!!.items.size
				if (count <= 1) {
					return
				}

				val maxSizeHeight = 814.0f
				val proportions = StringBuilder()
				var averageAspectRatio = 1.0f
				var forceCalc = false
				hasSibling = false

				for (a in 0..<count) {
					val photoSize: PhotoSize?

					val `object`: TLObject = currentBlock!!.items[a]
					if (`object` is TLPageBlockPhoto) {
						val photo = parentAdapter.getPhotoWithId(`object`.photoId) ?: continue
						photoSize = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize())
					}
					else if (`object` is TLPageBlockVideo) {
						val document = parentAdapter.getDocumentWithId(`object`.videoId) ?: continue
						photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90)
					}
					else {
						continue
					}

					val position = GroupedMessagePosition()
					position.last = a == count - 1
					position.aspectRatio = if (photoSize == null) 1.0f else photoSize.w / photoSize.h.toFloat()

					if (position.aspectRatio > 1.2f) {
						proportions.append("w")
					}
					else if (position.aspectRatio < 0.8f) {
						proportions.append("n")
					}
					else {
						proportions.append("q")
					}

					averageAspectRatio += position.aspectRatio

					if (position.aspectRatio > 2.0f) {
						forceCalc = true
					}

					positions[`object`] = position
					posArray.add(position)
				}

				val minHeight = AndroidUtilities.dp(120f)
				val minWidth = (AndroidUtilities.dp(120f) / (min(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) / maxSizeWidth.toFloat())).toInt()
				val paddingsWidth = (AndroidUtilities.dp(40f) / (min(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) / maxSizeWidth.toFloat())).toInt()

				val maxAspectRatio = maxSizeWidth / maxSizeHeight
				averageAspectRatio = averageAspectRatio / count

				if (!forceCalc && (count == 2 || count == 3 || count == 4)) {
					if (count == 2) {
						val position1 = posArray[0]
						val position2 = posArray[1]
						val pString = proportions.toString()
						if (pString == "ww" && averageAspectRatio > 1.4 * maxAspectRatio && position1.aspectRatio - position2.aspectRatio < 0.2) {
							val height = Math.round(min((maxSizeWidth / position1.aspectRatio).toDouble(), min((maxSizeWidth / position2.aspectRatio).toDouble(), (maxSizeHeight / 2.0f).toDouble())).toFloat()) / maxSizeHeight
							position1.set(0, 0, 0, 0, maxSizeWidth, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)
							position2.set(0, 0, 1, 1, maxSizeWidth, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)
						}
						else if (pString == "ww" || pString == "qq") {
							val width = maxSizeWidth / 2
							val height = Math.round(min((width / position1.aspectRatio).toDouble(), min((width / position2.aspectRatio).toDouble(), maxSizeHeight.toDouble())).toFloat()) / maxSizeHeight
							position1.set(0, 0, 0, 0, width, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
							position2.set(1, 1, 0, 0, width, height, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
						}
						else {
							var secondWidth = max((0.4f * maxSizeWidth).toDouble(), Math.round((maxSizeWidth / position1.aspectRatio / (1.0f / position1.aspectRatio + 1.0f / position2.aspectRatio))).toDouble()).toInt()
							var firstWidth = maxSizeWidth - secondWidth
							if (firstWidth < minWidth) {
								val diff = minWidth - firstWidth
								firstWidth = minWidth
								secondWidth -= diff
							}

							val height = (min(maxSizeHeight.toDouble(), Math.round(min((firstWidth / position1.aspectRatio).toDouble(), (secondWidth / position2.aspectRatio).toDouble()).toFloat()).toDouble()) / maxSizeHeight).toFloat()
							position1.set(0, 0, 0, 0, firstWidth, height, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
							position2.set(1, 1, 0, 0, secondWidth, height, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)
						}
					}
					else if (count == 3) {
						val position1 = posArray[0]
						val position2 = posArray[1]
						val position3 = posArray[2]
						if (proportions[0] == 'n') {
							val thirdHeight = min((maxSizeHeight * 0.5f).toDouble(), Math.round(position2.aspectRatio * maxSizeWidth / (position3.aspectRatio + position2.aspectRatio)).toDouble()).toFloat()
							val secondHeight = maxSizeHeight - thirdHeight
							val rightWidth = max(minWidth.toDouble(), min((maxSizeWidth * 0.5f).toDouble(), Math.round(min((thirdHeight * position3.aspectRatio).toDouble(), (secondHeight * position2.aspectRatio).toDouble()).toFloat()).toDouble())).toInt()

							val leftWidth = Math.round(min((maxSizeHeight * position1.aspectRatio + paddingsWidth).toDouble(), (maxSizeWidth - rightWidth).toDouble()).toFloat())
							position1.set(0, 0, 0, 1, leftWidth, 1.0f, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM or MessageObject.POSITION_FLAG_TOP)

							position2.set(1, 1, 0, 0, rightWidth, secondHeight / maxSizeHeight, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)

							position3.set(0, 1, 1, 1, rightWidth, thirdHeight / maxSizeHeight, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)
							position3.spanSize = maxSizeWidth

							position1.siblingHeights = floatArrayOf(thirdHeight / maxSizeHeight, secondHeight / maxSizeHeight)

							position2.spanSize = maxSizeWidth - leftWidth
							position3.leftSpanOffset = leftWidth

							hasSibling = true
						}
						else {
							val firstHeight = Math.round(min((maxSizeWidth / position1.aspectRatio).toDouble(), ((maxSizeHeight) * 0.66f).toDouble()).toFloat()) / maxSizeHeight
							position1.set(0, 1, 0, 0, maxSizeWidth, firstHeight, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)

							val width = maxSizeWidth / 2
							val secondHeight = (min((maxSizeHeight - firstHeight).toDouble(), Math.round(min((width / position2.aspectRatio).toDouble(), (width / position3.aspectRatio).toDouble()).toFloat()).toDouble()) / maxSizeHeight).toFloat()
							position2.set(0, 0, 1, 1, width, secondHeight, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM)
							position3.set(1, 1, 1, 1, width, secondHeight, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)
						}
					}
					else if (count == 4) {
						val position1 = posArray[0]
						val position2 = posArray[1]
						val position3 = posArray[2]
						val position4 = posArray[3]
						if (proportions[0] == 'w') {
							val h0 = Math.round(min((maxSizeWidth / position1.aspectRatio).toDouble(), (maxSizeHeight * 0.66f).toDouble()).toFloat()) / maxSizeHeight
							position1.set(0, 2, 0, 0, maxSizeWidth, h0, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)

							var h = Math.round(maxSizeWidth / (position2.aspectRatio + position3.aspectRatio + position4.aspectRatio)).toFloat()
							val w0 = max(minWidth.toDouble(), min((maxSizeWidth * 0.4f).toDouble(), (h * position2.aspectRatio).toDouble())).toInt()
							val w2 = max(max(minWidth.toDouble(), (maxSizeWidth * 0.33f).toDouble()), (h * position4.aspectRatio).toDouble()).toInt()
							val w1 = maxSizeWidth - w0 - w2
							h = min((maxSizeHeight - h0).toDouble(), h.toDouble()).toFloat()
							h /= maxSizeHeight
							position2.set(0, 0, 1, 1, w0, h, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_BOTTOM)
							position3.set(1, 1, 1, 1, w1, h, MessageObject.POSITION_FLAG_BOTTOM)
							position4.set(2, 2, 1, 1, w2, h, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)
						}
						else {
							val w = max(minWidth.toDouble(), Math.round(maxSizeHeight / (1.0f / position2.aspectRatio + 1.0f / position3.aspectRatio + 1.0f / posArray[3].aspectRatio)).toDouble()).toInt()
							val h0 = min(0.33, (max(minHeight.toDouble(), (w / position2.aspectRatio).toDouble()) / maxSizeHeight).toDouble()).toFloat()
							val h1 = min(0.33, (max(minHeight.toDouble(), (w / position3.aspectRatio).toDouble()) / maxSizeHeight).toDouble()).toFloat()
							val h2 = 1.0f - h0 - h1
							val w0 = Math.round(min((maxSizeHeight * position1.aspectRatio + paddingsWidth).toDouble(), (maxSizeWidth - w).toDouble()).toFloat())

							position1.set(0, 0, 0, 2, w0, h0 + h1 + h2, MessageObject.POSITION_FLAG_LEFT or MessageObject.POSITION_FLAG_TOP or MessageObject.POSITION_FLAG_BOTTOM)

							position2.set(1, 1, 0, 0, w, h0, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_TOP)

							position3.set(0, 1, 1, 1, w, h1, MessageObject.POSITION_FLAG_RIGHT)
							position3.spanSize = maxSizeWidth

							position4.set(0, 1, 2, 2, w, h2, MessageObject.POSITION_FLAG_RIGHT or MessageObject.POSITION_FLAG_BOTTOM)
							position4.spanSize = maxSizeWidth

							position2.spanSize = maxSizeWidth - w0
							position3.leftSpanOffset = w0
							position4.leftSpanOffset = w0

							position1.siblingHeights = floatArrayOf(h0, h1, h2)
							hasSibling = true
						}
					}
				}
				else {
					val croppedRatios = FloatArray(posArray.size)
					for (a in 0..<count) {
						if (averageAspectRatio > 1.1f) {
							croppedRatios[a] = max(1.0, posArray[a].aspectRatio.toDouble()).toFloat()
						}
						else {
							croppedRatios[a] = min(1.0, posArray[a].aspectRatio.toDouble()).toFloat()
						}
						croppedRatios[a] = max(0.66667, min(1.7, croppedRatios[a].toDouble())).toFloat()
					}
					var secondLine: Int
					var thirdLine: Int
					var fourthLine: Int
					val attempts = ArrayList<MessageGroupedLayoutAttempt>()
					var firstLine = 1
					while (firstLine < croppedRatios.size) {
						secondLine = croppedRatios.size - firstLine
						if (firstLine > 3 || secondLine > 3) {
							firstLine++
							continue
						}
						attempts.add(MessageGroupedLayoutAttempt(firstLine, secondLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, croppedRatios.size)))
						firstLine++
					}

					firstLine = 1
					while (firstLine < croppedRatios.size - 1) {
						secondLine = 1
						while (secondLine < croppedRatios.size - firstLine) {
							thirdLine = croppedRatios.size - firstLine - secondLine
							if (firstLine > 3 || secondLine > (if (averageAspectRatio < 0.85f) 4 else 3) || thirdLine > 3) {
								secondLine++
								continue
							}
							attempts.add(MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, croppedRatios.size)))
							secondLine++
						}
						firstLine++
					}

					firstLine = 1
					while (firstLine < croppedRatios.size - 2) {
						secondLine = 1
						while (secondLine < croppedRatios.size - firstLine) {
							thirdLine = 1
							while (thirdLine < croppedRatios.size - firstLine - secondLine) {
								fourthLine = croppedRatios.size - firstLine - secondLine - thirdLine
								if (firstLine > 3 || secondLine > 3 || thirdLine > 3 || fourthLine > 3) {
									thirdLine++
									continue
								}
								attempts.add(MessageGroupedLayoutAttempt(firstLine, secondLine, thirdLine, fourthLine, multiHeight(croppedRatios, 0, firstLine), multiHeight(croppedRatios, firstLine, firstLine + secondLine), multiHeight(croppedRatios, firstLine + secondLine, firstLine + secondLine + thirdLine), multiHeight(croppedRatios, firstLine + secondLine + thirdLine, croppedRatios.size)))
								thirdLine++
							}
							secondLine++
						}
						firstLine++
					}

					var optimal: MessageGroupedLayoutAttempt? = null
					var optimalDiff = 0.0f
					val maxHeight = (maxSizeWidth / 3 * 4).toFloat()
					for (a in attempts.indices) {
						val attempt = attempts[a]
						var height = 0f
						var minLineHeight = Float.MAX_VALUE
						for (b in attempt.heights.indices) {
							height += attempt.heights[b]
							if (attempt.heights[b] < minLineHeight) {
								minLineHeight = attempt.heights[b]
							}
						}

						var diff = abs((height - maxHeight).toDouble()).toFloat()
						if (attempt.lineCounts.size > 1) {
							if (attempt.lineCounts[0] > attempt.lineCounts[1] || (attempt.lineCounts.size > 2 && attempt.lineCounts[1] > attempt.lineCounts[2]) || (attempt.lineCounts.size > 3 && attempt.lineCounts[2] > attempt.lineCounts[3])) {
								diff *= 1.2f
							}
						}

						if (minLineHeight < minWidth) {
							diff *= 1.5f
						}

						if (optimal == null || diff < optimalDiff) {
							optimal = attempt
							optimalDiff = diff
						}
					}
					if (optimal == null) {
						return
					}

					var index = 0
					var y = 0.0f

					for (i in optimal.lineCounts.indices) {
						val c = optimal.lineCounts[i]
						val lineHeight = optimal.heights[i]
						var spanLeft = maxSizeWidth
						var posToFix: GroupedMessagePosition? = null
						for (k in 0..<c) {
							val ratio = croppedRatios[index]
							val width = (ratio * lineHeight).toInt()
							spanLeft -= width
							val pos = posArray[index]
							var flags = 0
							if (i == 0) {
								flags = flags or MessageObject.POSITION_FLAG_TOP
							}
							if (i == optimal.lineCounts.size - 1) {
								flags = flags or MessageObject.POSITION_FLAG_BOTTOM
							}
							if (k == 0) {
								flags = flags or MessageObject.POSITION_FLAG_LEFT
							}
							if (k == c - 1) {
								flags = flags or MessageObject.POSITION_FLAG_RIGHT
								posToFix = pos
							}
							pos.set(k, k, i, i, width, lineHeight / maxSizeHeight, flags)
							index++
						}
						posToFix!!.pw += spanLeft
						posToFix!!.spanSize += spanLeft
						y += lineHeight
					}
				}
				for (a in 0..<count) {
					val pos = posArray[a]

					if ((pos.flags and MessageObject.POSITION_FLAG_LEFT) != 0) {
						pos.edge = true
					}
				}
			}
		}

		init {
			innerListView = object : RecyclerListView(context) {
				override fun requestLayout() {
					if (inLayout) {
						return
					}
					super.requestLayout()
				}
			}
			innerListView.addItemDecoration(object : RecyclerView.ItemDecoration() {
				override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
					outRect.bottom = 0
					val position = if (view is BlockPhotoCell) {
						group.positions[view.currentBlock]
					}
					else if (view is BlockVideoCell) {
						group.positions[view.currentBlock]
					}
					else {
						null
					}
					if (position?.siblingHeights != null) {
						val maxHeight = (max(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) * 0.5f).toFloat()
						var h = 0
						for (a in position.siblingHeights!!.indices) {
							h += ceil((maxHeight * position.siblingHeights!![a]).toDouble()).toInt()
						}
						h += (position.maxY - position.minY) * AndroidUtilities.dp2(11f)
						val count = group.posArray.size
						for (a in 0..<count) {
							val pos = group.posArray[a]
							if (pos.minY != position.minY || pos.minX == position.minX && pos.maxX == position.maxX && pos.minY == position.minY && pos.maxY == position.maxY) {
								continue
							}
							if (pos.minY == position.minY) {
								h -= ceil((maxHeight * pos.ph).toDouble()).toInt() - AndroidUtilities.dp(4f)
								break
							}
						}
						outRect.bottom = -h
					}

					//outRect.top = outRect.left = 0;
					//outRect.bottom = outRect.right = AndroidUtilities.dp(2);
				}
			})

			val gridLayoutManager: GridLayoutManager = object : GridLayoutManagerFixed(context, 1000, VERTICAL, true) {
				override fun supportsPredictiveItemAnimations(): Boolean {
					return false
				}

				override fun shouldLayoutChildFromOppositeSide(child: View): Boolean {
					return false
				}

				override fun hasSiblingChild(position: Int): Boolean {
					val message: TLObject = currentBlock!!.items[currentBlock!!.items.size - position - 1]
					val pos = group.positions[message]
					if (pos!!.minX == pos.maxX || pos.minY != pos.maxY || pos.minY.toInt() == 0) {
						return false
					}
					val count = group.posArray.size
					for (a in 0..<count) {
						val p = group.posArray[a]
						if (p == pos) {
							continue
						}
						if (p.minY <= pos.minY && p.maxY >= pos.minY) {
							return true
						}
					}
					return false
				}
			}
			gridLayoutManager.spanSizeLookup = object : SpanSizeLookup() {
				override fun getSpanSize(position: Int): Int {
					val message: TLObject = currentBlock!!.items[currentBlock!!.items.size - position - 1]
					return group.positions[message]!!.spanSize
				}
			}

			innerListView.setLayoutManager(gridLayoutManager)

			innerListView.setAdapter(object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
				override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
					val view: View = when (viewType) {
						0 -> {
							BlockPhotoCell(getContext(), parentAdapter, 2)
						}

						1 -> {
							BlockVideoCell(getContext(), parentAdapter, 2)
						}

						else -> {
							BlockVideoCell(getContext(), parentAdapter, 2)
						}
					}
					return RecyclerListView.Holder(view)
				}

				override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
					val pageBlock = currentBlock!!.items[currentBlock!!.items.size - position - 1]
					when (holder.itemViewType) {
						0 -> {
							val cell = holder.itemView as BlockPhotoCell
							cell.groupPosition = group.positions[pageBlock]
							cell.setBlock(pageBlock as TLPageBlockPhoto, true, true)
						}

						1 -> {
							val cell = holder.itemView as BlockVideoCell
							cell.groupPosition = group.positions[pageBlock]
							cell.setBlock(pageBlock as TLPageBlockVideo, true, true)
						}

						else -> {
							val cell = holder.itemView as BlockVideoCell
							cell.groupPosition = group.positions[pageBlock]
							cell.setBlock(pageBlock as TLPageBlockVideo, true, true)
						}
					}
				}

				override fun getItemCount(): Int {
					if (currentBlock == null) {
						return 0
					}
					return currentBlock!!.items.size
				}

				override fun getItemViewType(position: Int): Int {
					val block = currentBlock!!.items[currentBlock!!.items.size - position - 1]
					return if (block is TLPageBlockPhoto) {
						0
					}
					else {
						1
					}
				}
			}.also { innerAdapter = it })
			addView(innerListView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
			setWillNotDraw(false)
		}

		fun setBlock(block: TLPageBlockCollage?) {
			if (currentBlock !== block) {
				currentBlock = block
				group.calculate()
			}
			innerAdapter!!.notifyDataSetChanged()
			innerListView.setGlowColor(context.getColor(R.color.background))
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			inLayout = true
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height: Int
			val currentBlock = currentBlock

			if (currentBlock != null) {
				var listWidth = width
				val textWidth: Int
				if (currentBlock.level > 0) {
					listX = AndroidUtilities.dp((14 * currentBlock.level).toFloat()) + AndroidUtilities.dp(18f)
					textX = listX
					listWidth -= listX + AndroidUtilities.dp(18f)
					textWidth = listWidth
				}
				else {
					listX = 0
					textX = AndroidUtilities.dp(18f)
					textWidth = width - AndroidUtilities.dp(36f)
				}

				innerListView.measure(MeasureSpec.makeMeasureSpec(listWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
				height = innerListView.measuredHeight

				textY = height + AndroidUtilities.dp(8f)
				captionLayout = createLayoutForText(this, null, currentBlock.caption!!.text, textWidth, textY, currentBlock, parentAdapter)
				if (captionLayout != null) {
					creditOffset = AndroidUtilities.dp(4f) + captionLayout!!.height
					height += creditOffset + AndroidUtilities.dp(4f)
					captionLayout!!.x = textX
					captionLayout!!.y = textY
				}
				else {
					creditOffset = 0
				}
				creditLayout = createLayoutForText(this, null, currentBlock.caption!!.credit, textWidth, textY + creditOffset, currentBlock, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
				if (creditLayout != null) {
					height += AndroidUtilities.dp(4f) + creditLayout!!.height
					creditLayout!!.x = textX
					creditLayout!!.y = textY + creditOffset
				}

				height += AndroidUtilities.dp(16f)
				if (currentBlock.level > 0 && !currentBlock.bottom) {
					height += AndroidUtilities.dp(8f)
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
			inLayout = false
		}

		override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
			innerListView.layout(listX, AndroidUtilities.dp(8f), listX + innerListView.measuredWidth, innerListView.measuredHeight + AndroidUtilities.dp(8f))
		}

		override fun onDraw(canvas: Canvas) {
			val currentBlock = currentBlock ?: return
			var count = 0

			if (captionLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this, count++)
				captionLayout!!.draw(canvas, this)
				canvas.restore()
			}

			if (creditLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), (textY + creditOffset).toFloat())
				drawTextSelection(canvas, this, count)
				creditLayout!!.draw(canvas, this)
				canvas.restore()
			}

			if (currentBlock.level > 0) {
				canvas.drawRect(AndroidUtilities.dp(18f).toFloat(), 0f, AndroidUtilities.dp(20f).toFloat(), (measuredHeight - (if (currentBlock.bottom) AndroidUtilities.dp(6f) else 0)).toFloat(), quoteLinePaint!!)
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (captionLayout != null) {
				blocks.add(captionLayout!!)
			}
			if (creditLayout != null) {
				blocks.add(creditLayout!!)
			}
		}
	}

	private class ObjectContainer {
		var block: PageBlock? = null
		var view: View? = null
	}

	private inner class BlockSlideshowCell(context: Context, private val parentAdapter: WebpageAdapter) : FrameLayout(context), ArticleSelectableView {
		val innerListView: ViewPager
		private var innerAdapter: PagerAdapter? = null
		private lateinit var dotsContainer: View

		var currentBlock: TLPageBlockSlideshow? = null
		private var captionLayout: DrawingText? = null
		private var creditLayout: DrawingText? = null
		private val textX = AndroidUtilities.dp(18f)
		private var textY = 0
		private var creditOffset = 0

		private var pageOffset = 0f
		private var currentPage = 0

		init {
			if (dotsPaint == null) {
				dotsPaint = Paint(Paint.ANTI_ALIAS_FLAG)
				dotsPaint!!.color = -0x1
			}

			innerListView = object : ViewPager(context) {
				override fun onTouchEvent(ev: MotionEvent): Boolean {
					return super.onTouchEvent(ev)
				}

				override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
					windowView!!.requestDisallowInterceptTouchEvent(true)
					cancelCheckLongPress()
					return super.onInterceptTouchEvent(ev)
				}
			}
			innerListView.addOnPageChangeListener(object : OnPageChangeListener {
				override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
					val width = innerListView.getMeasuredWidth().toFloat()
					if (width == 0f) {
						return
					}
					pageOffset = (position * width + positionOffsetPixels - currentPage * width) / width
					dotsContainer.invalidate()
				}

				override fun onPageSelected(position: Int) {
					currentPage = position
					dotsContainer.invalidate()
				}

				override fun onPageScrollStateChanged(state: Int) {
				}
			})
			innerListView.setAdapter(object : PagerAdapter() {
				override fun getCount(): Int {
					if (currentBlock == null) {
						return 0
					}
					return currentBlock!!.items.size
				}

				override fun isViewFromObject(view: View, `object`: Any): Boolean {
					return (`object` as ObjectContainer).view === view
				}

				override fun getItemPosition(`object`: Any): Int {
					val objectContainer: ObjectContainer = `object` as ObjectContainer
					if (currentBlock!!.items.contains(objectContainer.block)) {
						return POSITION_UNCHANGED
					}
					return POSITION_NONE
				}

				override fun instantiateItem(container: ViewGroup, position: Int): Any {
					val view: View
					val block = currentBlock!!.items[position]
					if (block is TLPageBlockPhoto) {
						view = BlockPhotoCell(getContext(), parentAdapter, 1)
						view.setBlock(block, true, true)
					}
					else {
						view = BlockVideoCell(getContext(), parentAdapter, 1)
						view.setBlock(block as TLPageBlockVideo, true, true)
					}
					container.addView(view)

					val objectContainer = ObjectContainer()
					objectContainer.view = view
					objectContainer.block = block

					return objectContainer
				}

				override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
					container.removeView((`object` as ObjectContainer).view)
				}

				override fun unregisterDataSetObserver(observer: DataSetObserver) {
					super.unregisterDataSetObserver(observer)
				}
			}.also { innerAdapter = it })

			AndroidUtilities.setViewPagerEdgeEffectColor(innerListView, context.getColor(R.color.background))

			addView(innerListView)

			dotsContainer = object : View(context) {
				override fun onDraw(canvas: Canvas) {
					if (currentBlock == null) {
						return
					}

					val count = innerAdapter!!.count
					val totalWidth = count * AndroidUtilities.dp(7f) + (count - 1) * AndroidUtilities.dp(6f) + AndroidUtilities.dp(4f)
					var xOffset: Int
					if (totalWidth < measuredWidth) {
						xOffset = (measuredWidth - totalWidth) / 2
					}
					else {
						xOffset = AndroidUtilities.dp(4f)
						val size = AndroidUtilities.dp(13f)
						val halfCount = (measuredWidth - AndroidUtilities.dp(8f)) / 2 / size
						if (currentPage == count - halfCount - 1 && pageOffset < 0) {
							xOffset -= (pageOffset * size).toInt() + (count - halfCount * 2 - 1) * size
						}
						else if (currentPage >= count - halfCount - 1) {
							xOffset -= (count - halfCount * 2 - 1) * size
						}
						else if (currentPage > halfCount) {
							xOffset -= (pageOffset * size).toInt() + (currentPage - halfCount) * size
						}
						else if (currentPage == halfCount && pageOffset > 0) {
							xOffset -= (pageOffset * size).toInt()
						}
					}
					for (a in currentBlock!!.items.indices) {
						val cx = xOffset + AndroidUtilities.dp(4f) + AndroidUtilities.dp(13f) * a
						val drawable = if (currentPage == a) slideDotBigDrawable else slideDotDrawable
						drawable!!.setBounds(cx - AndroidUtilities.dp(5f), 0, cx + AndroidUtilities.dp(5f), AndroidUtilities.dp(10f))
						drawable.draw(canvas)
					}
				}
			}
			addView(dotsContainer)

			setWillNotDraw(false)
		}

		fun setBlock(block: TLPageBlockSlideshow?) {
			currentBlock = block
			innerAdapter!!.notifyDataSetChanged()
			innerListView.setCurrentItem(0, false)
			innerListView.forceLayout()
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height: Int

			if (currentBlock != null) {
				height = AndroidUtilities.dp(310f)
				innerListView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
				val count = currentBlock!!.items.size
				dotsContainer.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(10f), MeasureSpec.EXACTLY))

				val textWidth = width - AndroidUtilities.dp(36f)
				textY = height + AndroidUtilities.dp(16f)
				captionLayout = createLayoutForText(this, null, currentBlock!!.caption!!.text, textWidth, textY, currentBlock!!, parentAdapter)
				if (captionLayout != null) {
					creditOffset = AndroidUtilities.dp(4f) + captionLayout!!.height
					height += creditOffset + AndroidUtilities.dp(4f)
					captionLayout!!.x = textX
					captionLayout!!.y = textY
				}
				else {
					creditOffset = 0
				}
				creditLayout = createLayoutForText(this, null, currentBlock!!.caption!!.credit, textWidth, textY + creditOffset, currentBlock!!, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
				if (creditLayout != null) {
					height += AndroidUtilities.dp(4f) + creditLayout!!.height
					creditLayout!!.x = textX
					creditLayout!!.y = textY + creditOffset
				}

				height += AndroidUtilities.dp(16f)
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
			innerListView.layout(0, AndroidUtilities.dp(8f), innerListView.measuredWidth, AndroidUtilities.dp(8f) + innerListView.measuredHeight)
			val y = innerListView.bottom - AndroidUtilities.dp((7 + 16).toFloat())
			dotsContainer.layout(0, y, dotsContainer.measuredWidth, y + dotsContainer.measuredHeight)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			var count = 0
			if (captionLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this, count++)
				captionLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (creditLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), (textY + creditOffset).toFloat())
				drawTextSelection(canvas, this, count)
				creditLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (captionLayout != null) {
				blocks.add(captionLayout!!)
			}
			if (creditLayout != null) {
				blocks.add(creditLayout!!)
			}
		}
	}

	private inner class BlockListItemCell(context: Context?, private val parentAdapter: WebpageAdapter) : ViewGroup(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		var blockLayout: RecyclerView.ViewHolder? = null
		private var textX = 0
		private var textY = 0
		private var numOffsetY = 0
		private var blockX = 0
		private var blockY = 0

		var verticalAlign: Boolean = false
		private var currentBlockType = 0
		private var currentBlock: TLPageBlockListItem? = null
		private var drawDot = false

		init {
			setWillNotDraw(false)
		}

		fun setBlock(block: TLPageBlockListItem?) {
			if (currentBlock !== block) {
				currentBlock = block
				if (blockLayout != null) {
					removeView(blockLayout!!.itemView)
					blockLayout = null
				}
				if (currentBlock!!.blockItem != null) {
					currentBlockType = parentAdapter.getTypeForBlock(currentBlock!!.blockItem)
					blockLayout = parentAdapter.onCreateViewHolder(this, currentBlockType)
					addView(blockLayout!!.itemView)
				}
			}
			if (currentBlock!!.blockItem != null) {
				parentAdapter.bindBlockToHolder(currentBlockType, blockLayout!!, currentBlock!!.blockItem, 0, 0)
			}
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY)) {
				return true
			}
			return super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0

			if (currentBlock != null) {
				textLayout = null
				textY = if (currentBlock!!.index == 0 && currentBlock!!.parent!!.level == 0) AndroidUtilities.dp(10f) else 0
				numOffsetY = 0
				if (currentBlock!!.parent!!.lastMaxNumCalcWidth != width || currentBlock!!.parent!!.lastFontSize != SharedConfig.ivFontSize) {
					currentBlock!!.parent!!.lastMaxNumCalcWidth = width
					currentBlock!!.parent!!.lastFontSize = SharedConfig.ivFontSize
					currentBlock!!.parent!!.maxNumWidth = 0
					var a = 0
					val size = currentBlock!!.parent!!.items.size
					while (a < size) {
						val item = currentBlock!!.parent!!.items[a]
						if (item.num == null) {
							a++
							continue
						}
						item.numLayout = createLayoutForText(this, item.num, null, width - AndroidUtilities.dp((36 + 18).toFloat()), textY, currentBlock!!, parentAdapter)
						currentBlock!!.parent!!.maxNumWidth = max(currentBlock!!.parent!!.maxNumWidth.toDouble(), ceil(item.numLayout!!.getLineWidth(0).toDouble()).toInt().toDouble()).toInt()
						a++
					}
					currentBlock!!.parent!!.maxNumWidth = max(currentBlock!!.parent!!.maxNumWidth.toDouble(), ceil(listTextNumPaint!!.measureText("00.").toDouble()).toInt().toDouble()).toInt()
				}

				drawDot = true // !currentBlock!!.parent!!.pageBlockList.ordered

				textX = if (parentAdapter.isRtl) {
					AndroidUtilities.dp(18f)
				}
				else {
					AndroidUtilities.dp((18 + 6).toFloat()) + currentBlock!!.parent!!.maxNumWidth + currentBlock!!.parent!!.level * AndroidUtilities.dp(12f)
				}
				var maxWidth = width - AndroidUtilities.dp(18f) - textX
				if (parentAdapter.isRtl) {
					maxWidth -= AndroidUtilities.dp(6f) + currentBlock!!.parent!!.maxNumWidth + currentBlock!!.parent!!.level * AndroidUtilities.dp(12f)
				}
				if (currentBlock!!.textItem != null) {
					textLayout = createLayoutForText(this, null, currentBlock!!.textItem, maxWidth, textY, currentBlock!!, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
					if (textLayout != null && textLayout!!.lineCount > 0) {
						if (currentBlock!!.numLayout != null && currentBlock!!.numLayout!!.lineCount > 0) {
							val ascent = textLayout!!.getLineAscent(0)
							numOffsetY = (currentBlock!!.numLayout!!.getLineAscent(0) + AndroidUtilities.dp(2.5f)) - ascent
						}
						height += textLayout!!.height + AndroidUtilities.dp(8f)
					}
				}
				else if (currentBlock!!.blockItem != null) {
					blockX = textX
					blockY = textY
					if (blockLayout != null) {
						if (blockLayout!!.itemView is BlockParagraphCell) {
							blockY -= AndroidUtilities.dp(8f)
							if (!parentAdapter.isRtl) {
								blockX -= AndroidUtilities.dp(18f)
							}
							maxWidth += AndroidUtilities.dp(18f)
							height -= AndroidUtilities.dp(8f)
						}
						else if (blockLayout!!.itemView is BlockHeaderCell || blockLayout!!.itemView is BlockSubheaderCell || blockLayout!!.itemView is BlockTitleCell || blockLayout!!.itemView is BlockSubtitleCell) {
							if (!parentAdapter.isRtl) {
								blockX -= AndroidUtilities.dp(18f)
							}
							maxWidth += AndroidUtilities.dp(18f)
						}
						else if (isListItemBlock(currentBlock!!.blockItem)) {
							blockX = 0
							blockY = 0
							textY = 0
							if (currentBlock!!.index == 0 && currentBlock!!.parent!!.level == 0) {
								height -= AndroidUtilities.dp(10f)
							}
							maxWidth = width
							height -= AndroidUtilities.dp(8f)
						}
						else if (blockLayout!!.itemView is BlockTableCell) {
							blockX -= AndroidUtilities.dp(18f)
							maxWidth += AndroidUtilities.dp(36f)
						}
						blockLayout!!.itemView.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
						if (blockLayout!!.itemView is BlockParagraphCell && currentBlock!!.numLayout != null && currentBlock!!.numLayout!!.lineCount > 0) {
							(blockLayout?.itemView as? BlockParagraphCell)?.textLayout?.let {
								if (it.lineCount > 0) {
									val ascent = it.getLineAscent(0)
									numOffsetY = (currentBlock!!.numLayout!!.getLineAscent(0) + AndroidUtilities.dp(2.5f)) - ascent
								}
							}
						}
						if (currentBlock!!.blockItem is TLPageBlockDetails) {
							verticalAlign = true
							blockY = 0
							if (currentBlock!!.index == 0 && currentBlock!!.parent!!.level == 0) {
								height -= AndroidUtilities.dp(10f)
							}
							height -= AndroidUtilities.dp(8f)
						}
						else if (blockLayout!!.itemView is BlockOrderedListItemCell) {
							verticalAlign = (blockLayout!!.itemView as BlockOrderedListItemCell).verticalAlign
						}
						else if (blockLayout!!.itemView is BlockListItemCell) {
							verticalAlign = (blockLayout!!.itemView as BlockListItemCell).verticalAlign
						}
						if (verticalAlign && currentBlock!!.numLayout != null) {
							textY = (blockLayout!!.itemView.measuredHeight - currentBlock!!.numLayout!!.height) / 2 - AndroidUtilities.dp(4f)
							drawDot = false
						}
						height += blockLayout!!.itemView.measuredHeight
					}
					height += AndroidUtilities.dp(8f)
				}
				if (currentBlock!!.parent!!.items[currentBlock!!.parent!!.items.size - 1] === currentBlock) {
					height += AndroidUtilities.dp(8f)
				}
				if (currentBlock!!.index == 0 && currentBlock!!.parent!!.level == 0) {
					height += AndroidUtilities.dp(10f)
				}
				if (textLayout != null) {
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
				if (blockLayout != null && blockLayout!!.itemView is ArticleSelectableView) {
					textSelectionHelper!!.arrayList.clear()
					(blockLayout!!.itemView as ArticleSelectableView).fillTextLayoutBlocks(textSelectionHelper!!.arrayList)
					for (block in textSelectionHelper!!.arrayList) {
						if (block is DrawingText) {
							block.x += blockX
							block.y += blockY
						}
					}
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
			if (blockLayout != null) {
				blockLayout!!.itemView.layout(blockX, blockY, blockX + blockLayout!!.itemView.measuredWidth, blockY + blockLayout!!.itemView.measuredHeight)
			}
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			val width = measuredWidth
			if (currentBlock!!.numLayout != null) {
				canvas.save()
				if (parentAdapter.isRtl) {
					canvas.translate((width - AndroidUtilities.dp(15f) - currentBlock!!.parent!!.maxNumWidth - currentBlock!!.parent!!.level * AndroidUtilities.dp(12f)).toFloat(), (textY + numOffsetY - (if (drawDot) AndroidUtilities.dp(1f) else 0)).toFloat())
				}
				else {
					canvas.translate((AndroidUtilities.dp(15f) + currentBlock!!.parent!!.maxNumWidth - ceil(currentBlock!!.numLayout!!.getLineWidth(0).toDouble()).toInt() + currentBlock!!.parent!!.level * AndroidUtilities.dp(12f)).toFloat(), (textY + numOffsetY - (if (drawDot) AndroidUtilities.dp(1f) else 0)).toFloat())
				}
				currentBlock!!.numLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun invalidate() {
			super.invalidate()
			if (blockLayout != null) {
				blockLayout!!.itemView.invalidate()
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
			if (textLayout == null) {
				return
			}
			info.text = textLayout!!.text
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (blockLayout != null && blockLayout!!.itemView is ArticleSelectableView) {
				(blockLayout!!.itemView as ArticleSelectableView).fillTextLayoutBlocks(blocks)
			}
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private inner class BlockOrderedListItemCell(context: Context?, private val parentAdapter: WebpageAdapter) : ViewGroup(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		var blockLayout: RecyclerView.ViewHolder? = null
		private var textX = 0
		private var textY = 0
		private var numOffsetY = 0
		private var blockX = 0
		private var blockY = 0

		private var currentBlockType = 0
		var verticalAlign: Boolean = false

		private var currentBlock: TLPageBlockOrderedListItem? = null

		init {
			setWillNotDraw(false)
		}

		fun setBlock(block: TLPageBlockOrderedListItem?) {
			if (currentBlock !== block) {
				currentBlock = block
				if (blockLayout != null) {
					removeView(blockLayout!!.itemView)
					blockLayout = null
				}
				if (currentBlock!!.blockItem != null) {
					currentBlockType = parentAdapter.getTypeForBlock(currentBlock!!.blockItem)
					blockLayout = parentAdapter.onCreateViewHolder(this, currentBlockType)
					addView(blockLayout!!.itemView)
				}
			}
			if (currentBlock!!.blockItem != null) {
				parentAdapter.bindBlockToHolder(currentBlockType, blockLayout!!, currentBlock!!.blockItem, 0, 0)
			}
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY)) {
				return true
			}
			return super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0

			if (currentBlock != null) {
				textLayout = null
				textY = if (currentBlock!!.index == 0 && currentBlock!!.parent!!.level == 0) AndroidUtilities.dp(10f) else 0
				numOffsetY = 0
				if (currentBlock!!.parent!!.lastMaxNumCalcWidth != width || currentBlock!!.parent!!.lastFontSize != SharedConfig.ivFontSize) {
					currentBlock!!.parent!!.lastMaxNumCalcWidth = width
					currentBlock!!.parent!!.lastFontSize = SharedConfig.ivFontSize
					currentBlock!!.parent!!.maxNumWidth = 0
					var a = 0
					val size = currentBlock!!.parent!!.items.size
					while (a < size) {
						val item = currentBlock!!.parent!!.items[a]
						if (item.num == null) {
							a++
							continue
						}
						item.numLayout = createLayoutForText(this, item.num, null, width - AndroidUtilities.dp((36 + 18).toFloat()), textY, currentBlock!!, parentAdapter)
						currentBlock!!.parent!!.maxNumWidth = max(currentBlock!!.parent!!.maxNumWidth.toDouble(), ceil(item.numLayout!!.getLineWidth(0).toDouble()).toInt().toDouble()).toInt()
						a++
					}
					currentBlock!!.parent!!.maxNumWidth = max(currentBlock!!.parent!!.maxNumWidth.toDouble(), ceil(listTextNumPaint!!.measureText("00.").toDouble()).toInt().toDouble()).toInt()
				}
				textX = if (parentAdapter.isRtl) {
					AndroidUtilities.dp(18f)
				}
				else {
					AndroidUtilities.dp((18 + 6).toFloat()) + currentBlock!!.parent!!.maxNumWidth + currentBlock!!.parent!!.level * AndroidUtilities.dp(20f)
				}
				verticalAlign = false
				var maxWidth = width - AndroidUtilities.dp(18f) - textX
				if (parentAdapter.isRtl) {
					maxWidth -= AndroidUtilities.dp(6f) + currentBlock!!.parent!!.maxNumWidth + currentBlock!!.parent!!.level * AndroidUtilities.dp(20f)
				}
				if (currentBlock!!.textItem != null) {
					textLayout = createLayoutForText(this, null, currentBlock!!.textItem, maxWidth, textY, currentBlock!!, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
					if (textLayout != null && textLayout!!.lineCount > 0) {
						if (currentBlock!!.numLayout != null && currentBlock!!.numLayout!!.lineCount > 0) {
							val ascent = textLayout!!.getLineAscent(0)
							numOffsetY = currentBlock!!.numLayout!!.getLineAscent(0) - ascent
						}
						height += textLayout!!.height + AndroidUtilities.dp(8f)
					}
				}
				else if (currentBlock!!.blockItem != null) {
					blockX = textX
					blockY = textY
					if (blockLayout != null) {
						if (blockLayout!!.itemView is BlockParagraphCell) {
							blockY -= AndroidUtilities.dp(8f)
							if (!parentAdapter.isRtl) {
								blockX -= AndroidUtilities.dp(18f)
							}
							maxWidth += AndroidUtilities.dp(18f)
							height -= AndroidUtilities.dp(8f)
						}
						else if (blockLayout!!.itemView is BlockHeaderCell || blockLayout!!.itemView is BlockSubheaderCell || blockLayout!!.itemView is BlockTitleCell || blockLayout!!.itemView is BlockSubtitleCell) {
							if (!parentAdapter.isRtl) {
								blockX -= AndroidUtilities.dp(18f)
							}
							maxWidth += AndroidUtilities.dp(18f)
						}
						else if (isListItemBlock(currentBlock!!.blockItem)) {
							blockX = 0
							blockY = 0
							textY = 0
							maxWidth = width
							height -= AndroidUtilities.dp(8f)
						}
						else if (blockLayout!!.itemView is BlockTableCell) {
							blockX -= AndroidUtilities.dp(18f)
							maxWidth += AndroidUtilities.dp(36f)
						}
						blockLayout!!.itemView.measure(MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
						if (blockLayout!!.itemView is BlockParagraphCell && currentBlock!!.numLayout != null && currentBlock!!.numLayout!!.lineCount > 0) {
							(blockLayout?.itemView as? BlockParagraphCell)?.textLayout?.let {
								if (it.lineCount > 0) {
									val ascent = it.getLineAscent(0)
									numOffsetY = currentBlock!!.numLayout!!.getLineAscent(0) - ascent
								}
							}
						}
						if (currentBlock!!.blockItem is TLPageBlockDetails) {
							verticalAlign = true
							blockY = 0
							height -= AndroidUtilities.dp(8f)
						}
						else if (blockLayout!!.itemView is BlockOrderedListItemCell) {
							verticalAlign = (blockLayout!!.itemView as BlockOrderedListItemCell).verticalAlign
						}
						else if (blockLayout!!.itemView is BlockListItemCell) {
							verticalAlign = (blockLayout!!.itemView as BlockListItemCell).verticalAlign
						}
						if (verticalAlign && currentBlock!!.numLayout != null) {
							textY = (blockLayout!!.itemView.measuredHeight - currentBlock!!.numLayout!!.height) / 2
						}
						height += blockLayout!!.itemView.measuredHeight
					}
					height += AndroidUtilities.dp(8f)
				}
				if (currentBlock!!.parent!!.items[currentBlock!!.parent!!.items.size - 1] === currentBlock) {
					height += AndroidUtilities.dp(8f)
				}
				if (currentBlock!!.index == 0 && currentBlock!!.parent!!.level == 0) {
					height += AndroidUtilities.dp(10f)
				}
				if (textLayout != null) {
					textLayout!!.x = textX
					textLayout!!.y = textY
					if (currentBlock!!.numLayout != null) {
						textLayout?.setPrefix( currentBlock!!.numLayout!!.textLayout!!.text)
					}
				}
				if (blockLayout != null && blockLayout!!.itemView is ArticleSelectableView) {
					textSelectionHelper!!.arrayList.clear()
					(blockLayout!!.itemView as ArticleSelectableView).fillTextLayoutBlocks(textSelectionHelper!!.arrayList)
					for (block in textSelectionHelper!!.arrayList) {
						if (block is DrawingText) {
							block.x += blockX
							block.y += blockY
						}
					}
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
			if (blockLayout != null) {
				blockLayout!!.itemView.layout(blockX, blockY, blockX + blockLayout!!.itemView.measuredWidth, blockY + blockLayout!!.itemView.measuredHeight)
			}
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			val width = measuredWidth
			if (currentBlock!!.numLayout != null) {
				canvas.save()
				if (parentAdapter.isRtl) {
					canvas.translate((width - AndroidUtilities.dp(18f) - currentBlock!!.parent!!.maxNumWidth - currentBlock!!.parent!!.level * AndroidUtilities.dp(20f)).toFloat(), (textY + numOffsetY).toFloat())
				}
				else {
					canvas.translate((AndroidUtilities.dp(18f) + currentBlock!!.parent!!.maxNumWidth - ceil(currentBlock!!.numLayout!!.getLineWidth(0).toDouble()).toInt() + currentBlock!!.parent!!.level * AndroidUtilities.dp(20f)).toFloat(), (textY + numOffsetY).toFloat())
				}
				currentBlock!!.numLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun invalidate() {
			super.invalidate()
			if (blockLayout != null) {
				blockLayout!!.itemView.invalidate()
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
			if (textLayout == null) {
				return
			}
			info.text = textLayout!!.text
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (blockLayout != null && blockLayout!!.itemView is ArticleSelectableView) {
				(blockLayout!!.itemView as ArticleSelectableView).fillTextLayoutBlocks(blocks)
			}
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private inner class BlockDetailsCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), Drawable.Callback, ArticleSelectableView {
		private var textLayout: DrawingText? = null
		private val textX = AndroidUtilities.dp((44 + 6).toFloat())
		private var textY = AndroidUtilities.dp(11f) + 1
		val arrow: AnimatedArrowDrawable = AnimatedArrowDrawable(grayTextColor, true)

		private var currentBlock: TLPageBlockDetails? = null

		override fun invalidateDrawable(drawable: Drawable) {
			invalidate()
		}

		override fun scheduleDrawable(drawable: Drawable, runnable: Runnable, l: Long) {
		}

		override fun unscheduleDrawable(drawable: Drawable, runnable: Runnable) {
		}

		fun setBlock(block: TLPageBlockDetails) {
			currentBlock = block
			arrow.animationProgress = if (block.open) 0.0f else 1.0f
			arrow.callback = this
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var h = AndroidUtilities.dp(39f)
			if (currentBlock != null) {
				textLayout = createLayoutForText(this, null, currentBlock!!.title, width - AndroidUtilities.dp((36 + 16).toFloat()), 0, currentBlock!!, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
				if (textLayout != null) {
					h = max(h.toDouble(), (AndroidUtilities.dp(21f) + textLayout!!.height).toDouble()).toInt()
					textY = (textLayout!!.height + AndroidUtilities.dp(21f) - textLayout!!.height) / 2
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
			}
			setMeasuredDimension(width, h + 1)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			canvas.save()
			canvas.translate(AndroidUtilities.dp(18f).toFloat(), ((measuredHeight - AndroidUtilities.dp(13f) - 1) / 2).toFloat())
			arrow.draw(canvas)
			canvas.restore()

			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}

			val y = measuredHeight - 1
			canvas.drawLine(0f, y.toFloat(), measuredWidth.toFloat(), y.toFloat(), dividerPaint!!)
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private class BlockDetailsBottomCell(context: Context?) : View(context) {
		private val rect = RectF()

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 1 + AndroidUtilities.dp(4f))
		}

		override fun onDraw(canvas: Canvas) {
			canvas.drawLine(0f, 0f, measuredWidth.toFloat(), 0f, dividerPaint!!)
		}
	}

	private class BlockRelatedArticlesShadowCell(context: Context?) : View(context) {
		private val shadowDrawable: CombinedDrawable

		init {
			val drawable = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, -0x1000000)
			shadowDrawable = CombinedDrawable(ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable)
			shadowDrawable.setFullSize(true)
			setBackgroundDrawable(shadowDrawable)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(12f))
			Theme.setCombinedDrawableColor(shadowDrawable, Theme.getColor(Theme.key_windowBackgroundGray), false)
		}
	}

	private inner class BlockRelatedArticlesHeaderCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		private val textX = AndroidUtilities.dp(18f)
		private var textY = 0

		private var currentBlock: TLPageBlockRelatedArticles? = null

		fun setBlock(block: TLPageBlockRelatedArticles?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			if (currentBlock != null) {
				textLayout = createLayoutForText(this, null, currentBlock!!.title, width - AndroidUtilities.dp((36 + 16).toFloat()), 0, currentBlock, Layout.Alignment.ALIGN_NORMAL, 1, parentAdapter)
				if (textLayout != null) {
					textY = AndroidUtilities.dp(6f) + (AndroidUtilities.dp(32f) - textLayout!!.height) / 2
				}
			}
			if (textLayout != null) {
				setMeasuredDimension(width, AndroidUtilities.dp(38f))
				textLayout!!.x = textX
				textLayout!!.y = textY
			}
			else {
				setMeasuredDimension(width, 1)
			}
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private inner class BlockRelatedArticlesCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		private var textLayout2: DrawingText? = null
		private var divider = false
		private var drawImage = false

		private val imageView = ImageReceiver(this)

		var currentBlock: TLPageBlockRelatedArticlesChild? = null

		private val textX = AndroidUtilities.dp(18f)
		private val textY = AndroidUtilities.dp(10f)
		private var textOffset = 0

		init {
			imageView.setRoundRadius(AndroidUtilities.dp(6f))
		}

		fun setBlock(block: TLPageBlockRelatedArticlesChild?) {
			currentBlock = block
			requestLayout()
		}

		@SuppressLint("DrawAllocation", "NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)

			divider = currentBlock!!.num != currentBlock!!.parent!!.articles.size - 1
			val item = currentBlock!!.parent!!.articles[currentBlock!!.num]

			val additionalHeight = AndroidUtilities.dp((SharedConfig.ivFontSize - 16).toFloat())

			val photo = if (item.photoId != 0L) parentAdapter.getPhotoWithId(item.photoId) else null
			if (photo != null) {
				drawImage = true
				val image = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize())
				var thumb = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, 80, true)
				if (image === thumb) {
					thumb = null
				}
				imageView.setImage(ImageLocation.getForPhoto(image, photo), "64_64", ImageLocation.getForPhoto(thumb, photo), "64_64_b", image?.size?.toLong() ?: 0L, null, parentAdapter.currentPage, 1)
			}
			else {
				drawImage = false
			}

			var layoutHeight = AndroidUtilities.dp((16 + 44).toFloat())
			var availableWidth = width - AndroidUtilities.dp((18 + 18).toFloat())
			if (drawImage) {
				val imageWidth = AndroidUtilities.dp(44f)
				imageView.setImageCoordinates((width - imageWidth - AndroidUtilities.dp(8f)).toFloat(), AndroidUtilities.dp(8f).toFloat(), imageWidth.toFloat(), imageWidth.toFloat())
				availableWidth = (availableWidth - (imageView.imageWidth + AndroidUtilities.dp(6f))).toInt()
			}

			var height = AndroidUtilities.dp(18f)

			var isTitleRtl = false
			if (item.title != null) {
				textLayout = createLayoutForText(this, item.title, null, availableWidth, textY, currentBlock, Layout.Alignment.ALIGN_NORMAL, 3, parentAdapter)
			}
			var lineCount = 4
			if (textLayout != null) {
				val count = textLayout!!.lineCount
				lineCount -= count
				textOffset = textLayout!!.height + AndroidUtilities.dp(6f) + additionalHeight
				height += textLayout!!.height
				for (a in 0..<count) {
					if (textLayout!!.getLineLeft(a) != 0f) {
						isTitleRtl = true
						break
					}
				}
				textLayout!!.x = textX
				textLayout!!.y = textY
			}
			else {
				textOffset = 0
			}
			val description = if (item.publishedDate != 0 && !TextUtils.isEmpty(item.author)) {
				LocaleController.formatString("ArticleDateByAuthor", R.string.ArticleDateByAuthor, LocaleController.getInstance().chatFullDate.format(item.publishedDate as Long * 1000), item.author)
			}
			else if (!TextUtils.isEmpty(item.author)) {
				LocaleController.formatString("ArticleByAuthor", R.string.ArticleByAuthor, item.author)
			}
			else if (item.publishedDate != 0) {
				LocaleController.getInstance().chatFullDate.format(item.publishedDate as Long * 1000)
			}
			else if (!TextUtils.isEmpty(item.description)) {
				item.description
			}
			else {
				item.url
			}
			textLayout2 = createLayoutForText(this, description, null, availableWidth, textY + textOffset, currentBlock, if (parentAdapter.isRtl || isTitleRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, lineCount, parentAdapter)
			if (textLayout2 != null) {
				height += textLayout2!!.height
				if (textLayout != null) {
					height += AndroidUtilities.dp(6f) + additionalHeight
				}
				textLayout2!!.x = textX
				textLayout2!!.y = textY + textOffset
			}
			layoutHeight = max(layoutHeight.toDouble(), height.toDouble()).toInt()

			setMeasuredDimension(width, layoutHeight + (if (divider) 1 else 0))
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			if (drawImage) {
				imageView.draw(canvas)
			}
			var count = 0
			canvas.save()
			canvas.translate(textX.toFloat(), AndroidUtilities.dp(10f).toFloat())
			if (textLayout != null) {
				drawTextSelection(canvas, this, count++)
				textLayout!!.draw(canvas, this)
			}
			if (textLayout2 != null) {
				canvas.translate(0f, textOffset.toFloat())
				drawTextSelection(canvas, this, count)
				textLayout2!!.draw(canvas, this)
			}
			canvas.restore()
			if (divider) {
				canvas.drawLine((if (parentAdapter.isRtl) 0 else AndroidUtilities.dp(17f)).toFloat(), (measuredHeight - 1).toFloat(), (measuredWidth - (if (parentAdapter.isRtl) AndroidUtilities.dp(17f) else 0)).toFloat(), (measuredHeight - 1).toFloat(), dividerPaint!!)
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
			if (textLayout2 != null) {
				blocks.add(textLayout2!!)
			}
		}
	}

	private inner class BlockHeaderCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		private val textX = AndroidUtilities.dp(18f)
		private val textY = AndroidUtilities.dp(8f)

		private var currentBlock: TLPageBlockHeader? = null

		fun setBlock(block: TLPageBlockHeader?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0

			if (currentBlock != null) {
				textLayout = createLayoutForText(this, null, currentBlock!!.text, width - AndroidUtilities.dp(36f), textY, currentBlock!!, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
				if (textLayout != null) {
					height += AndroidUtilities.dp((8 + 8).toFloat()) + textLayout!!.height
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
			if (textLayout == null) {
				return
			}
			info.text = textLayout!!.text.toString() + ", " + context.getString(R.string.AccDescrIVHeading)
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private class BlockDividerCell(context: Context?) : View(context) {
		private val rect = RectF()

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp((2 + 16).toFloat()))
		}

		override fun onDraw(canvas: Canvas) {
			val width = measuredWidth / 3
			rect[width.toFloat(), AndroidUtilities.dp(8f).toFloat(), (width * 2).toFloat()] = AndroidUtilities.dp(10f).toFloat()
			canvas.drawRoundRect(rect, AndroidUtilities.dp(1f).toFloat(), AndroidUtilities.dp(1f).toFloat(), dividerPaint!!)
		}
	}

	private inner class BlockSubtitleCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		private val textX = AndroidUtilities.dp(18f)
		private val textY = AndroidUtilities.dp(8f)

		private var currentBlock: TLPageBlockSubtitle? = null

		fun setBlock(block: TLPageBlockSubtitle?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0

			if (currentBlock != null) {
				textLayout = createLayoutForText(this, null, currentBlock!!.text, width - AndroidUtilities.dp(36f), textY, currentBlock!!, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
				if (textLayout != null) {
					height += AndroidUtilities.dp((8 + 8).toFloat()) + textLayout!!.height
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
			if (textLayout == null) {
				return
			}
			info.text = textLayout!!.text.toString() + ", " + context.getString(R.string.AccDescrIVHeading)
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private inner class BlockPullquoteCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		private var textLayout2: DrawingText? = null
		private var textY2 = 0
		private val textX = AndroidUtilities.dp(18f)
		private val textY = AndroidUtilities.dp(8f)

		private var currentBlock: TLPageBlockPullquote? = null

		fun setBlock(block: TLPageBlockPullquote?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, textLayout2, textX, textY2) || super.onTouchEvent(event)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0

			if (currentBlock != null) {
				textLayout = createLayoutForText(this, null, currentBlock!!.text, width - AndroidUtilities.dp(36f), textY, currentBlock!!, parentAdapter)
				if (textLayout != null) {
					height += AndroidUtilities.dp(8f) + textLayout!!.height
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
				textY2 = height + AndroidUtilities.dp(2f)
				textLayout2 = createLayoutForText(this, null, currentBlock!!.caption, width - AndroidUtilities.dp(36f), textY2, currentBlock!!, parentAdapter)
				if (textLayout2 != null) {
					height += AndroidUtilities.dp(8f) + textLayout2!!.height
					textLayout2!!.x = textX
					textLayout2!!.y = textY2
				}
				if (height != 0) {
					height += AndroidUtilities.dp(8f)
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			var count = 0
			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this, count++)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (textLayout2 != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY2.toFloat())
				drawTextSelection(canvas, this, count)
				textLayout2!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
			if (textLayout2 != null) {
				blocks.add(textLayout2!!)
			}
		}
	}

	private inner class BlockBlockquoteCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		private var textLayout2: DrawingText? = null
		private var textY2 = 0
		private var textX = 0
		private val textY = AndroidUtilities.dp(8f)

		private var currentBlock: TLPageBlockBlockquote? = null

		fun setBlock(block: TLPageBlockBlockquote?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, textLayout2, textX, textY2) || super.onTouchEvent(event)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0
			val currentBlock = currentBlock

			if (currentBlock != null) {
				var textWidth = width - AndroidUtilities.dp((36 + 14).toFloat())
				if (currentBlock.level > 0) {
					textWidth -= AndroidUtilities.dp((14 * currentBlock.level).toFloat())
				}
				textLayout = createLayoutForText(this, null, currentBlock.text, textWidth, textY, currentBlock, parentAdapter)
				if (textLayout != null) {
					height += AndroidUtilities.dp(8f) + textLayout!!.height
				}
				textX = if (currentBlock.level > 0) {
					if (parentAdapter.isRtl) {
						AndroidUtilities.dp((14 + currentBlock.level * 14).toFloat())
					}
					else {
						AndroidUtilities.dp((14 * currentBlock.level).toFloat()) + AndroidUtilities.dp((18 + 14).toFloat())
					}
				}
				else {
					if (parentAdapter.isRtl) {
						AndroidUtilities.dp(14f)
					}
					else {
						AndroidUtilities.dp((18 + 14).toFloat())
					}
				}
				textY2 = height + AndroidUtilities.dp(8f)
				textLayout2 = createLayoutForText(this, null, currentBlock.caption, textWidth, textY2, currentBlock, parentAdapter)
				if (textLayout2 != null) {
					height += AndroidUtilities.dp(8f) + textLayout2!!.height
				}
				if (height != 0) {
					height += AndroidUtilities.dp(8f)
				}
				if (textLayout != null) {
					textLayout!!.x = textX
					textLayout!!.y = textY
				}

				if (textLayout2 != null) {
					textLayout2!!.x = textX
					textLayout2!!.y = textY2
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			val currentBlock = currentBlock ?: return
			var counter = 0
			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this, counter++)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (textLayout2 != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY2.toFloat())
				drawTextSelection(canvas, this, counter)
				textLayout2!!.draw(canvas, this)
				canvas.restore()
			}
			if (parentAdapter.isRtl) {
				val x = measuredWidth - AndroidUtilities.dp(20f)
				canvas.drawRect(x.toFloat(), AndroidUtilities.dp(6f).toFloat(), (x + AndroidUtilities.dp(2f)).toFloat(), (measuredHeight - AndroidUtilities.dp(6f)).toFloat(), quoteLinePaint!!)
			}
			else {
				canvas.drawRect(AndroidUtilities.dp((18 + currentBlock.level * 14).toFloat()).toFloat(), AndroidUtilities.dp(6f).toFloat(), AndroidUtilities.dp((20 + currentBlock.level * 14).toFloat()).toFloat(), (measuredHeight - AndroidUtilities.dp(6f)).toFloat(), quoteLinePaint!!)
			}
			if (currentBlock.level > 0) {
				canvas.drawRect(AndroidUtilities.dp(18f).toFloat(), 0f, AndroidUtilities.dp(20f).toFloat(), (measuredHeight - (if (currentBlock.bottom) AndroidUtilities.dp(6f) else 0)).toFloat(), quoteLinePaint!!)
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
			if (textLayout2 != null) {
				blocks.add(textLayout2!!)
			}
		}
	}

	private inner class BlockPhotoCell(context: Context, private val parentAdapter: WebpageAdapter, type: Int) : FrameLayout(context), FileDownloadProgressListener, ArticleSelectableView {
		private var captionLayout: DrawingText? = null
		private var creditLayout: DrawingText? = null
		val imageView: ImageReceiver
		private val radialProgress: RadialProgress2
		private val channelCell: BlockChannelCell
		private val currentType: Int
		private var isFirst = false
		private var textX = 0
		private var textY = 0
		private var creditOffset = 0

		private var buttonX = 0
		private var buttonY = 0
		private var photoPressed = false
		private var buttonState = 0
		private var buttonPressed = 0

		private var currentPhotoObject: PhotoSize? = null
		private var currentFilter: String? = null
		private var currentPhotoObjectThumb: PhotoSize? = null
		private var currentThumbFilter: String? = null
		private var currentPhoto: TLRPC.Photo? = null

		private val TAG: Int

		var currentBlock: TLPageBlockPhoto? = null
		private var parentBlock: PageBlock? = null

		var groupPosition: GroupedMessagePosition? = null
		private var linkDrawable: Drawable? = null

		var autoDownload: Boolean = false

		init {
			setWillNotDraw(false)
			imageView = ImageReceiver(this)
			channelCell = BlockChannelCell(context, parentAdapter, 1)
			radialProgress = RadialProgress2(this)
			radialProgress.setProgressColor(-0x1)
			radialProgress.setColors(0x66000000, 0x7f000000, -0x1, -0x262627)
			TAG = DownloadController.getInstance(currentAccount).generateObserverTag()
			addView(channelCell, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))
			currentType = type
		}

		fun setBlock(block: TLPageBlockPhoto?, first: Boolean, last: Boolean) {
			parentBlock = null
			currentBlock = block
			isFirst = first
			channelCell.visibility = INVISIBLE

			if (!currentBlock?.url.isNullOrEmpty()) {
				linkDrawable = resources.getDrawable(R.drawable.msg_instant_link)
			}
			if (currentBlock != null) {
				val photo = parentAdapter.getPhotoWithId(currentBlock!!.photoId)

				currentPhotoObject = if (photo != null) {
					FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize())
				}
				else {
					null
				}
			}
			else {
				currentPhotoObject = null
			}

			updateButtonState(false)
			requestLayout()
		}

		fun setParentBlock(block: PageBlock?) {
			parentBlock = block
			if (parentAdapter.channelBlock != null && parentBlock is TLPageBlockCover) {
				channelCell.setBlock(parentAdapter.channelBlock!!)
				channelCell.visibility = VISIBLE
			}
		}

		fun getChannelCell(): View {
			return channelCell
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (pinchToZoomHelper!!.checkPinchToZoom(event, this, imageView, null)) {
				return true
			}
			val x = event.x
			val y = event.y
			if (channelCell.visibility == VISIBLE && y > channelCell.translationY && y < channelCell.translationY + AndroidUtilities.dp(39f)) {
				if (parentAdapter.channelBlock != null && event.action == MotionEvent.ACTION_UP) {
					MessagesController.getInstance(currentAccount).openByUserName(parentAdapter.channelBlock!!.channel!!.username, parentFragment, 2)
					close(false, true)
				}
				return true
			}
			if (event.action == MotionEvent.ACTION_DOWN && imageView.isInsideImage(x, y)) {
				if (buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48f) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48f) || buttonState == 0) {
					buttonPressed = 1
					invalidate()
				}
				else {
					photoPressed = true
				}
			}
			else if (event.action == MotionEvent.ACTION_UP) {
				if (photoPressed) {
					photoPressed = false
					openPhoto(currentBlock, parentAdapter)
				}
				else if (buttonPressed == 1) {
					buttonPressed = 0
					playSoundEffect(SoundEffectConstants.CLICK)
					didPressedButton(true)
					invalidate()
				}
			}
			else if (event.action == MotionEvent.ACTION_CANCEL) {
				photoPressed = false
				buttonPressed = 0
			}
			return photoPressed || buttonPressed != 0 || checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			var width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0
			val currentBlock = currentBlock

			if (currentType == 1) {
				width = (parent as View).measuredWidth
				height = (parent as View).measuredHeight
			}
			else if (currentType == 2) {
				height = ceil((groupPosition!!.ph * max(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) * 0.5f).toDouble()).toInt()
			}

			if (currentBlock != null) {
				currentPhoto = parentAdapter.getPhotoWithId(currentBlock.photoId)
				val size = AndroidUtilities.dp(48f)
				var photoWidth = width
				var photoHeight = height
				var photoX: Int
				val textWidth: Int
				if (currentType == 0 && currentBlock.level > 0) {
					photoX = AndroidUtilities.dp((14 * currentBlock.level).toFloat()) + AndroidUtilities.dp(18f)
					textX = photoX
					photoWidth -= photoX + AndroidUtilities.dp(18f)
					textWidth = photoWidth
				}
				else {
					photoX = 0
					textX = AndroidUtilities.dp(18f)
					textWidth = width - AndroidUtilities.dp(36f)
				}
				if (currentPhoto != null && currentPhotoObject != null) {
					currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(currentPhoto.sizes, 40, true)
					if (currentPhotoObject === currentPhotoObjectThumb) {
						currentPhotoObjectThumb = null
					}
					if (currentType == 0) {
						var scale: Float
						scale = photoWidth / currentPhotoObject!!.w.toFloat()
						height = (scale * currentPhotoObject!!.h).toInt()
						if (parentBlock is TLPageBlockCover) {
							height = min(height.toDouble(), photoWidth.toDouble()).toInt()
						}
						else {
							val maxHeight = ((max(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) - AndroidUtilities.dp(56f)) * 0.9f).toInt()
							if (height > maxHeight) {
								height = maxHeight
								scale = height / currentPhotoObject!!.h.toFloat()
								photoWidth = (scale * currentPhotoObject!!.w).toInt()
								photoX += (width - photoX - photoWidth) / 2
							}
						}
						photoHeight = height
					}
					else if (currentType == 2) {
						if ((groupPosition!!.flags and MessageObject.POSITION_FLAG_RIGHT) == 0) {
							photoWidth -= AndroidUtilities.dp(2f)
						}
						if ((groupPosition!!.flags and MessageObject.POSITION_FLAG_BOTTOM) == 0) {
							photoHeight -= AndroidUtilities.dp(2f)
						}
						if (groupPosition!!.leftSpanOffset != 0) {
							val offset = ceil((width * groupPosition!!.leftSpanOffset / 1000.0f).toDouble()).toInt()
							photoWidth -= offset
							photoX += offset
						}
					}
					imageView.setImageCoordinates(photoX.toFloat(), (if (isFirst || currentType == 1 || currentType == 2 || currentBlock.level > 0) 0
					else AndroidUtilities.dp(8f)).toFloat(), photoWidth.toFloat(), photoHeight.toFloat())
					currentFilter = if (currentType == 0) {
						null
					}
					else {
						String.format(Locale.US, "%d_%d", photoWidth, photoHeight)
					}
					currentThumbFilter = "80_80_b"

					autoDownload = (DownloadController.getInstance(currentAccount).currentDownloadMask and DownloadController.AUTODOWNLOAD_TYPE_PHOTO) != 0
					val path = FileLoader.getInstance(currentAccount).getPathToAttach(currentPhotoObject, true)
					if (autoDownload || path.exists()) {
						imageView.strippedLocation = null
						imageView.setImage(ImageLocation.getForPhoto(currentPhotoObject, currentPhoto), currentFilter, ImageLocation.getForPhoto(currentPhotoObjectThumb, currentPhoto), currentThumbFilter, currentPhotoObject?.size?.toLong() ?: 0L, null, parentAdapter.currentPage, 1)
					}
					else {
						imageView.strippedLocation = ImageLocation.getForPhoto(currentPhotoObject, currentPhoto)
						imageView.setImage(null, currentFilter, ImageLocation.getForPhoto(currentPhotoObjectThumb, currentPhoto), currentThumbFilter, currentPhotoObject?.size?.toLong() ?: 0L, null, parentAdapter.currentPage, 1)
					}
					buttonX = (imageView.imageX + (imageView.imageWidth - size) / 2.0f).toInt()
					buttonY = (imageView.imageY + (imageView.imageHeight - size) / 2.0f).toInt()
					radialProgress.setProgressRect(buttonX, buttonY, buttonX + size, buttonY + size)
				}
				textY = (imageView.imageY + imageView.imageHeight + AndroidUtilities.dp(8f)).toInt()

				if (currentType == 0) {
					captionLayout = createLayoutForText(this, null, currentBlock.caption!!.text, textWidth, textY, currentBlock, parentAdapter)
					if (captionLayout != null) {
						creditOffset = AndroidUtilities.dp(4f) + captionLayout!!.height
						height += creditOffset + AndroidUtilities.dp(4f)
					}
					creditLayout = createLayoutForText(this, null, currentBlock.caption!!.credit, textWidth, textY + creditOffset, currentBlock, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, 0, parentAdapter)
					if (creditLayout != null) {
						height += AndroidUtilities.dp(4f) + creditLayout!!.height
					}
				}
				if (!isFirst && currentType == 0 && currentBlock.level <= 0) {
					height += AndroidUtilities.dp(8f)
				}
				val nextIsChannel = parentBlock is TLPageBlockCover && parentAdapter.blocks != null && parentAdapter.blocks.size > 1 && parentAdapter.blocks[1] is TLPageBlockChannel
				if (currentType != 2 && !nextIsChannel) {
					height += AndroidUtilities.dp(8f)
				}
				if (captionLayout != null) {
					captionLayout!!.x = textX
					captionLayout!!.y = textY
				}

				if (creditLayout != null) {
					creditLayout!!.x = textX
					creditLayout!!.y = textY + creditOffset
				}
			}
			else {
				height = 1
			}
			channelCell.measure(widthMeasureSpec, heightMeasureSpec)
			channelCell.translationY = imageView.imageHeight - AndroidUtilities.dp(39f)

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			val currentBlock = currentBlock ?: return
			if (!imageView.hasBitmapImage() || imageView.currentAlpha != 1.0f) {
				canvas.drawRect(imageView.imageX, imageView.imageY, imageView.imageX2, imageView.imageY2, photoBackgroundPaint!!)
			}
			if (!pinchToZoomHelper!!.isInOverlayModeFor(this)) {
				imageView.draw(canvas)
				if (imageView.visible) {
					radialProgress.draw(canvas)
				}
			}
			if (!TextUtils.isEmpty(currentBlock.url)) {
				val x = measuredWidth - AndroidUtilities.dp((11 + 24).toFloat())
				val y = (imageView.imageY + AndroidUtilities.dp(11f)).toInt()
				linkDrawable!!.setBounds(x, y, x + AndroidUtilities.dp(24f), y + AndroidUtilities.dp(24f))
				linkDrawable!!.draw(canvas)
			}
			var count = 0
			if (captionLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this, count++)
				captionLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (creditLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), (textY + creditOffset).toFloat())
				drawTextSelection(canvas, this, count)
				creditLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (currentBlock.level > 0) {
				canvas.drawRect(AndroidUtilities.dp(18f).toFloat(), 0f, AndroidUtilities.dp(20f).toFloat(), (measuredHeight - (if (currentBlock.bottom) AndroidUtilities.dp(6f) else 0)).toFloat(), quoteLinePaint!!)
			}
		}

		val iconForCurrentState: Int
			get() {
				if (buttonState == 0) {
					return MediaActionDrawable.ICON_DOWNLOAD
				}
				else if (buttonState == 1) {
					return MediaActionDrawable.ICON_CANCEL
				}
				return MediaActionDrawable.ICON_NONE
			}

		fun didPressedButton(animated: Boolean) {
			if (buttonState == 0) {
				radialProgress.setProgress(0f, animated)
				imageView.setImage(ImageLocation.getForPhoto(currentPhotoObject, currentPhoto), currentFilter, ImageLocation.getForPhoto(currentPhotoObjectThumb, currentPhoto), currentThumbFilter, currentPhotoObject?.size?.toLong() ?: 0L, null, parentAdapter.currentPage, 1)
				buttonState = 1
				radialProgress.setIcon(iconForCurrentState, true, animated)
				invalidate()
			}
			else if (buttonState == 1) {
				imageView.cancelLoadImage()
				buttonState = 0
				radialProgress.setIcon(iconForCurrentState, false, animated)
				invalidate()
			}
		}

		fun updateButtonState(animated: Boolean) {
			val fileName = FileLoader.getAttachFileName(currentPhotoObject)
			val path = FileLoader.getInstance(currentAccount).getPathToAttach(currentPhotoObject, true)
			val fileExists = path.exists()
			if (TextUtils.isEmpty(fileName)) {
				radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, false)
				return
			}

			if (fileExists) {
				DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
				buttonState = -1
				radialProgress.setIcon(iconForCurrentState, false, animated)
			}
			else {
				DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this)
				var setProgress = 0f
				if (autoDownload || FileLoader.getInstance(currentAccount).isLoadingFile(fileName)) {
					buttonState = 1
					val progress = ImageLoader.getInstance().getFileProgress(fileName)
					setProgress = progress ?: 0f
				}
				else {
					buttonState = 0
				}
				radialProgress.setIcon(iconForCurrentState, true, animated)
				radialProgress.setProgress(setProgress, false)
			}
			invalidate()
		}

		override fun onDetachedFromWindow() {
			super.onDetachedFromWindow()
			imageView.onDetachedFromWindow()
			DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this)
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			imageView.onAttachedToWindow()
			updateButtonState(false)
		}

		override fun onFailedDownload(fileName: String, canceled: Boolean) {
			updateButtonState(false)
		}

		override fun onSuccessDownload(fileName: String) {
			radialProgress.setProgress(1f, true)
			updateButtonState(true)
		}

		override fun onProgressUpload(fileName: String, uploadedSize: Long, totalSize: Long, isEncrypted: Boolean) {
		}

		override fun onProgressDownload(fileName: String, downloadSize: Long, totalSize: Long) {
			radialProgress.setProgress(min(1.0, (downloadSize / totalSize.toFloat()).toDouble()).toFloat(), true)
			if (buttonState != 1) {
				updateButtonState(true)
			}
		}

		override fun getObserverTag(): Int {
			return TAG
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
			val sb = StringBuilder(context.getString(R.string.AttachPhoto))
			if (captionLayout != null) {
				sb.append(", ")
				sb.append(captionLayout!!.text)
			}
			info.text = sb.toString()
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (captionLayout != null) {
				blocks.add(captionLayout!!)
			}
			if (creditLayout != null) {
				blocks.add(creditLayout!!)
			}
		}
	}

	private inner class BlockMapCell(context: Context, private val parentAdapter: WebpageAdapter, type: Int) : FrameLayout(context), ArticleSelectableView {
		private var captionLayout: DrawingText? = null
		private var creditLayout: DrawingText? = null
		private val imageView: ImageReceiver
		private val currentType: Int
		private var isFirst = false
		private var textX = 0
		private var textY = 0
		private var creditOffset = 0
		private var photoPressed = false
		private var currentMapProvider = 0

		private var currentBlock: TLPageBlockMap? = null

		init {
			setWillNotDraw(false)
			imageView = ImageReceiver(this)
			currentType = type
		}

		fun setBlock(block: TLPageBlockMap?, first: Boolean, last: Boolean) {
			currentBlock = block
			isFirst = first
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			val x = event.x
			val y = event.y

			if (event.action == MotionEvent.ACTION_DOWN && imageView.isInsideImage(x, y)) {
				photoPressed = true
			}
			else if (event.action == MotionEvent.ACTION_UP && photoPressed) {
				photoPressed = false
				try {
					val lat = currentBlock?.geo?.lat ?: 0.0
					val lon = currentBlock?.geo?.lon ?: 0.0
					parentActivity?.startActivity(Intent(Intent.ACTION_VIEW, "geo:$lat,$lon?q=$lat,$lon".toUri()))
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
			else if (event.action == MotionEvent.ACTION_CANCEL) {
				photoPressed = false
			}

			return photoPressed || checkLayoutForLinks(parentAdapter, event, this, captionLayout, textX, textY) || checkLayoutForLinks(parentAdapter, event, this, creditLayout, textX, textY + creditOffset) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			var width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0
			val currentBlock = currentBlock

			if (currentType == 1) {
				width = (parent as View).measuredWidth
				height = (parent as View).measuredHeight
			}
			else if (currentType == 2) {
				height = width
			}

			if (currentBlock != null) {
				var photoWidth = width
				var photoX: Int
				val textWidth: Int

				if (currentType == 0 && currentBlock.level > 0) {
					photoX = AndroidUtilities.dp((14 * currentBlock.level).toFloat()) + AndroidUtilities.dp(18f)
					textX = photoX
					photoWidth -= photoX + AndroidUtilities.dp(18f)
					textWidth = photoWidth
				}
				else {
					photoX = 0
					textX = AndroidUtilities.dp(18f)
					textWidth = width - AndroidUtilities.dp(36f)
				}

				if (currentType == 0) {
					var scale: Float
					scale = photoWidth / currentBlock.w.toFloat()
					height = (scale * currentBlock.h).toInt()

					val maxHeight = ((max(AndroidUtilities.displaySize.x.toDouble(), AndroidUtilities.displaySize.y.toDouble()) - AndroidUtilities.dp(56f)) * 0.9f).toInt()

					if (height > maxHeight) {
						height = maxHeight
						scale = height / currentBlock.h.toFloat()
						photoWidth = (scale * currentBlock.w).toInt()
						photoX += (width - photoX - photoWidth) / 2
					}
				}
				imageView.setImageCoordinates(photoX.toFloat(), (if (isFirst || currentType == 1 || currentType == 2 || currentBlock.level > 0) 0
				else AndroidUtilities.dp(8f)).toFloat(), photoWidth.toFloat(), height.toFloat())

				val currentUrl = AndroidUtilities.formatMapUrl(currentAccount, currentBlock.geo?.lat ?: 0.0, currentBlock.geo?.lon ?: 0.0, (photoWidth / AndroidUtilities.density).toInt(), (height / AndroidUtilities.density).toInt(), true, 15, -1)
				val currentWebFile = WebFile.createWithGeoPoint(currentBlock.geo, (photoWidth / AndroidUtilities.density).toInt(), (height / AndroidUtilities.density).toInt(), 15, min(2.0, ceil(AndroidUtilities.density.toDouble()).toInt().toDouble()).toInt())

				currentMapProvider = MessagesController.getInstance(currentAccount).mapProvider
				if (currentMapProvider == MessagesController.MAP_PROVIDER_ELLO) {
					if (currentWebFile != null) {
						imageView.setImage(ImageLocation.getForWebFile(currentWebFile), null, null, null, parentAdapter.currentPage, 0)
					}
				}
				else if (currentUrl != null) {
					imageView.setImage(currentUrl, null, null, null, 0)
				}
				textY = (imageView.imageY + imageView.imageHeight + AndroidUtilities.dp(8f)).toInt()
				if (currentType == 0) {
					captionLayout = createLayoutForText(this, null, currentBlock.caption!!.text, textWidth, textY, currentBlock, parentAdapter)
					if (captionLayout != null) {
						creditOffset = AndroidUtilities.dp(4f) + captionLayout!!.height
						height += creditOffset + AndroidUtilities.dp(4f)
						captionLayout!!.x = textX
						captionLayout!!.y = textY
					}
					creditLayout = createLayoutForText(this, null, currentBlock.caption!!.credit, textWidth, textY + creditOffset, currentBlock, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
					if (creditLayout != null) {
						height += AndroidUtilities.dp(4f) + creditLayout!!.height
						creditLayout!!.x = textX
						creditLayout!!.y = textY + creditOffset
					}
				}
				if (!isFirst && currentType == 0 && currentBlock.level <= 0) {
					height += AndroidUtilities.dp(8f)
				}
				if (currentType != 2) {
					height += AndroidUtilities.dp(8f)
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			val currentBlock = currentBlock ?: return

			Theme.chat_docBackPaint.color = Theme.getColor(Theme.key_chat_inLocationBackground)
			canvas.drawRect(imageView.imageX, imageView.imageY, imageView.imageX2, imageView.imageY2, Theme.chat_docBackPaint)
			val left = (imageView.centerX - Theme.chat_locationDrawable[0].intrinsicWidth / 2).toInt()
			val top = (imageView.centerY - Theme.chat_locationDrawable[0].intrinsicHeight / 2).toInt()
			Theme.chat_locationDrawable[0].setBounds(left, top, left + Theme.chat_locationDrawable[0].intrinsicWidth, top + Theme.chat_locationDrawable[0].intrinsicHeight)
			Theme.chat_locationDrawable[0].draw(canvas)

			imageView.draw(canvas)
			if (currentMapProvider == MessagesController.MAP_PROVIDER_ELLO && imageView.hasNotThumb()) {
				val w = (Theme.chat_redLocationIcon.intrinsicWidth * 0.8f).toInt()
				val h = (Theme.chat_redLocationIcon.intrinsicHeight * 0.8f).toInt()
				val x = (imageView.imageX + (imageView.imageWidth - w) / 2).toInt()
				val y = (imageView.imageY + (imageView.imageHeight / 2 - h)).toInt()
				Theme.chat_redLocationIcon.alpha = (255 * imageView.currentAlpha).toInt()
				Theme.chat_redLocationIcon.setBounds(x, y, x + w, y + h)
				Theme.chat_redLocationIcon.draw(canvas)
			}
			var count = 0
			if (captionLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this, count++)
				captionLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (creditLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), (textY + creditOffset).toFloat())
				drawTextSelection(canvas, this, count)
				creditLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (currentBlock.level > 0) {
				canvas.drawRect(AndroidUtilities.dp(18f).toFloat(), 0f, AndroidUtilities.dp(20f).toFloat(), (measuredHeight - (if (currentBlock.bottom) AndroidUtilities.dp(6f) else 0)).toFloat(), quoteLinePaint!!)
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
			val sb = StringBuilder(context.getString(R.string.Map))
			if (captionLayout != null) {
				sb.append(", ")
				sb.append(captionLayout!!.text)
			}
			info.text = sb.toString()
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (captionLayout != null) {
				blocks.add(captionLayout!!)
			}
			if (creditLayout != null) {
				blocks.add(creditLayout!!)
			}
		}
	}

	private inner class BlockChannelCell(context: Context, private val parentAdapter: WebpageAdapter, type: Int) : FrameLayout(context), ArticleSelectableView {
		private val progressView: ContextProgressView
		private val textView: TextView
		private val imageView: ImageView
		private var currentState = 0

		private var textLayout: DrawingText? = null
		private var buttonWidth = 0
		private val textX = AndroidUtilities.dp(18f)
		private val textY = AndroidUtilities.dp(11f)
		private var textX2 = 0
		private val backgroundPaint: Paint
		private var currentAnimation: AnimatorSet? = null
		private val currentType: Int

		private var currentBlock: TLPageBlockChannel? = null

		init {
			setWillNotDraw(false)
			backgroundPaint = Paint()
			currentType = type

			textView = TextView(context)
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
			textView.typeface = Theme.TYPEFACE_BOLD
			textView.text = context.getString(R.string.ChannelJoin)
			textView.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
			addView(textView, createFrame(LayoutHelper.WRAP_CONTENT, 39, Gravity.RIGHT or Gravity.TOP))
			textView.setOnClickListener { v: View? ->
				if (currentState != 0) {
					return@setOnClickListener
				}
				setState(1, true)
				joinChannel(this@BlockChannelCell, loadedChannel!!)
			}

			imageView = ImageView(context)
			imageView.setImageResource(R.drawable.list_check)
			imageView.scaleType = ImageView.ScaleType.CENTER
			addView(imageView, createFrame(39, 39, Gravity.RIGHT or Gravity.TOP))

			progressView = ContextProgressView(context, 0)
			addView(progressView, createFrame(39, 39, Gravity.RIGHT or Gravity.TOP))
		}

		fun setBlock(block: TLPageBlockChannel) {
			currentBlock = block

			if (currentType == 0) {
				val color = Theme.getColor(Theme.key_switchTrack)
				val r = Color.red(color)
				val g = Color.green(color)
				val b = Color.blue(color)
				textView.setTextColor(linkTextColor)
				backgroundPaint.color = Color.argb(34, r, g, b)
				imageView.colorFilter = PorterDuffColorFilter(grayTextColor, PorterDuff.Mode.MULTIPLY)
			}
			else {
				textView.setTextColor(-0x1)
				backgroundPaint.color = 0x7f000000
				imageView.colorFilter = PorterDuffColorFilter(-0x1, PorterDuff.Mode.MULTIPLY)
			}

			val channel = MessagesController.getInstance(currentAccount).getChat(block.channel?.id)

			if (channel == null || channel.min) {
				loadChannel(this, parentAdapter, block.channel!!)
				setState(1, false)
			}
			else {
				loadedChannel = channel

				if (channel.left /* && !channel.kicked */) {
					setState(0, false)
				}
				else {
					setState(4, false)
				}
			}
			requestLayout()
		}

		fun setState(state: Int, animated: Boolean) {
			if (currentAnimation != null) {
				currentAnimation!!.cancel()
			}
			currentState = state
			if (animated) {
				currentAnimation = AnimatorSet()
				currentAnimation!!.playTogether(ObjectAnimator.ofFloat(textView, ALPHA, if (state == 0) 1.0f else 0.0f), ObjectAnimator.ofFloat(textView, SCALE_X, if (state == 0) 1.0f else 0.1f), ObjectAnimator.ofFloat(textView, SCALE_Y, if (state == 0) 1.0f else 0.1f),

						ObjectAnimator.ofFloat(progressView, ALPHA, if (state == 1) 1.0f else 0.0f), ObjectAnimator.ofFloat(progressView, SCALE_X, if (state == 1) 1.0f else 0.1f), ObjectAnimator.ofFloat(progressView, SCALE_Y, if (state == 1) 1.0f else 0.1f),

						ObjectAnimator.ofFloat(imageView, ALPHA, if (state == 2) 1.0f else 0.0f), ObjectAnimator.ofFloat(imageView, SCALE_X, if (state == 2) 1.0f else 0.1f), ObjectAnimator.ofFloat(imageView, SCALE_Y, if (state == 2) 1.0f else 0.1f))
				currentAnimation!!.setDuration(150)
				currentAnimation!!.start()
			}
			else {
				textView.alpha = if (state == 0) 1.0f else 0.0f
				textView.scaleX = if (state == 0) 1.0f else 0.1f
				textView.scaleY = if (state == 0) 1.0f else 0.1f

				progressView.alpha = if (state == 1) 1.0f else 0.0f
				progressView.scaleX = if (state == 1) 1.0f else 0.1f
				progressView.scaleY = if (state == 1) 1.0f else 0.1f

				imageView.alpha = if (state == 2) 1.0f else 0.0f
				imageView.scaleX = if (state == 2) 1.0f else 0.1f
				imageView.scaleY = if (state == 2) 1.0f else 0.1f
			}
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			if (currentType != 0) {
				return super.onTouchEvent(event)
			}
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			setMeasuredDimension(width, AndroidUtilities.dp((39 + 9).toFloat()))

			textView.measure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39f), MeasureSpec.EXACTLY))
			buttonWidth = textView.measuredWidth
			progressView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39f), MeasureSpec.EXACTLY))
			imageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(39f), MeasureSpec.EXACTLY))
			if (currentBlock != null) {
				textLayout = createLayoutForText(this, currentBlock!!.channel!!.title, null, width - AndroidUtilities.dp((36 + 16).toFloat()) - buttonWidth, textY, currentBlock!!, StaticLayoutEx.ALIGN_LEFT(), parentAdapter)
				textX2 = if (parentAdapter.isRtl) {
					textX
				}
				else {
					measuredWidth - textX - buttonWidth
				}
				if (textLayout != null) {
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
			}
		}

		override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
			imageView.layout(textX2 + buttonWidth / 2 - AndroidUtilities.dp(19f), 0, textX2 + buttonWidth / 2 + AndroidUtilities.dp(20f), AndroidUtilities.dp(39f))
			progressView.layout(textX2 + buttonWidth / 2 - AndroidUtilities.dp(19f), 0, textX2 + buttonWidth / 2 + AndroidUtilities.dp(20f), AndroidUtilities.dp(39f))
			textView.layout(textX2, 0, textX2 + textView.measuredWidth, textView.measuredHeight)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			canvas.drawRect(0f, 0f, measuredWidth.toFloat(), AndroidUtilities.dp(39f).toFloat(), backgroundPaint)
			if (textLayout != null && textLayout!!.lineCount > 0) {
				canvas.save()
				if (parentAdapter.isRtl) {
					canvas.translate(measuredWidth - textLayout!!.getLineWidth(0) - textX, textY.toFloat())
				}
				else {
					canvas.translate(textX.toFloat(), textY.toFloat())
				}
				if (currentType == 0) {
					drawTextSelection(canvas, this)
				}
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private inner class BlockAuthorDateCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		private var textX = 0
		private val textY = AndroidUtilities.dp(8f)

		private var currentBlock: TLPageBlockAuthorDate? = null

		fun setBlock(block: TLPageBlockAuthorDate?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event)
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0
			val currentBlock = currentBlock

			if (currentBlock != null) {
				var text: CharSequence?
				val author = getText(parentAdapter, this, currentBlock.author, currentBlock.author, currentBlock, width)
				val spannableAuthor: Spannable?
				val spans: Array<MetricAffectingSpan>?
				if (author is Spannable) {
					spannableAuthor = author
					spans = spannableAuthor.getSpans(0, author.length, MetricAffectingSpan::class.java)
				}
				else {
					spannableAuthor = null
					spans = null
				}
				text = if (currentBlock.publishedDate != 0 && !TextUtils.isEmpty(author)) {
					LocaleController.formatString("ArticleDateByAuthor", R.string.ArticleDateByAuthor, LocaleController.getInstance().chatFullDate.format(currentBlock.publishedDate as Long * 1000), author)
				}
				else if (!TextUtils.isEmpty(author)) {
					LocaleController.formatString("ArticleByAuthor", R.string.ArticleByAuthor, author)
				}
				else {
					LocaleController.getInstance().chatFullDate.format(currentBlock.publishedDate as Long * 1000)
				}
				try {
					if (spans != null && spans.isNotEmpty()) {
						val idx = TextUtils.indexOf(text, author)
						if (idx != -1) {
							val spannable = Spannable.Factory.getInstance().newSpannable(text)
							text = spannable
							for (span in spans) {
								spannable.setSpan(span, idx + spannableAuthor!!.getSpanStart(span), idx + spannableAuthor.getSpanEnd(span), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
							}
						}
					}
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
				textLayout = createLayoutForText(this, text, null, width - AndroidUtilities.dp(36f), textY, currentBlock, parentAdapter)
				if (textLayout != null) {
					height += AndroidUtilities.dp((8 + 8).toFloat()) + textLayout!!.height
					textX = if (parentAdapter.isRtl) {
						floor((width - textLayout!!.getLineWidth(0) - AndroidUtilities.dp(16f)).toDouble()).toInt()
					}
					else {
						AndroidUtilities.dp(18f)
					}
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
			if (textLayout == null) {
				return
			}
			info.text = textLayout!!.text
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private inner class BlockTitleCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null

		private var currentBlock: TLPageBlockTitle? = null
		private val textX = AndroidUtilities.dp(18f)
		private var textY = 0

		fun setBlock(block: TLPageBlockTitle?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0
			val currentBlock = currentBlock

			if (currentBlock != null) {
				if (currentBlock.first) {
					height += AndroidUtilities.dp(8f)
					textY = AndroidUtilities.dp(16f)
				}
				else {
					textY = AndroidUtilities.dp(8f)
				}
				textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(36f), textY, currentBlock, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
				if (textLayout != null) {
					height += AndroidUtilities.dp((8 + 8).toFloat()) + textLayout!!.height
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
			if (textLayout == null) {
				return
			}
			info.text = textLayout!!.text.toString() + ", " + context.getString(R.string.AccDescrIVTitle)
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private inner class BlockKickerCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null

		private var currentBlock: TLPageBlockKicker? = null
		private val textX = AndroidUtilities.dp(18f)
		private var textY = 0

		fun setBlock(block: TLPageBlockKicker?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0
			val currentBlock = currentBlock

			if (currentBlock != null) {
				if (currentBlock.first) {
					textY = AndroidUtilities.dp(16f)
					height += AndroidUtilities.dp(8f)
				}
				else {
					textY = AndroidUtilities.dp(8f)
				}
				textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(36f), textY, currentBlock, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
				if (textLayout != null) {
					height += AndroidUtilities.dp((8 + 8).toFloat()) + textLayout!!.height
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private inner class BlockFooterCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		private var textX = AndroidUtilities.dp(18f)
		private var textY = AndroidUtilities.dp(8f)

		private var currentBlock: TLPageBlockFooter? = null

		fun setBlock(block: TLPageBlockFooter?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0
			val currentBlock = currentBlock

			if (currentBlock != null) {
				if (currentBlock.level == 0) {
					textY = AndroidUtilities.dp(8f)
					textX = AndroidUtilities.dp(18f)
				}
				else {
					textY = 0
					textX = AndroidUtilities.dp((18 + 14 * currentBlock.level).toFloat())
				}
				textLayout = createLayoutForText(this, null, currentBlock.text, width - AndroidUtilities.dp(18f) - textX, textY, currentBlock, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
				if (textLayout != null) {
					height = textLayout!!.height
					height += if (currentBlock.level > 0) {
						AndroidUtilities.dp(8f)
					}
					else {
						AndroidUtilities.dp((8 + 8).toFloat())
					}
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			val currentBlock = currentBlock ?: return

			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
			if (currentBlock.level > 0) {
				canvas.drawRect(AndroidUtilities.dp(18f).toFloat(), 0f, AndroidUtilities.dp(20f).toFloat(), (measuredHeight - (if (currentBlock.bottom) AndroidUtilities.dp(6f) else 0)).toFloat(), quoteLinePaint!!)
			}
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private inner class BlockPreformattedCell(context: Context, private val parentAdapter: WebpageAdapter) : FrameLayout(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		private val scrollView: HorizontalScrollView
		private lateinit var textContainer: View
		private var currentBlock: TLPageBlockPreformatted? = null

		init {
			scrollView = object : HorizontalScrollView(context) {
				override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
					if (textContainer.measuredWidth > measuredWidth) {
						windowView?.requestDisallowInterceptTouchEvent(true)
					}

					return super.onInterceptTouchEvent(ev)
				}

				override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
					super.onScrollChanged(l, t, oldl, oldt)

					if (pressedLinkOwnerLayout != null) {
						pressedLinkOwnerLayout = null
						pressedLinkOwnerView = null
					}
				}
			}
			scrollView.setPadding(0, AndroidUtilities.dp(8f), 0, AndroidUtilities.dp(8f))
			addView(scrollView, createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat()))

			textContainer = object : View(context) {
				override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
					var height = 0
					var width = 1
					if (currentBlock != null) {
						textLayout = createLayoutForText(this, null, currentBlock!!.text, AndroidUtilities.dp(5000f), 0, currentBlock!!, parentAdapter)
						if (textLayout != null) {
							height += textLayout!!.height
							var a = 0
							val count = textLayout!!.lineCount
							while (a < count) {
								width = max(ceil(textLayout!!.getLineWidth(a).toDouble()).toInt().toDouble(), width.toDouble()).toInt()
								a++
							}
						}
					}
					else {
						height = 1
					}
					setMeasuredDimension(width + AndroidUtilities.dp(32f), height)
				}

				override fun onTouchEvent(event: MotionEvent): Boolean {
					return checkLayoutForLinks(parentAdapter, event, this@BlockPreformattedCell, textLayout, 0, 0) || super.onTouchEvent(event)
				}

				override fun onDraw(canvas: Canvas) {
					if (textLayout != null) {
						canvas.save()
						drawTextSelection(canvas, this@BlockPreformattedCell)
						textLayout!!.draw(canvas, this)
						canvas.restore()
						textLayout!!.x = x.toInt()
						textLayout!!.y = y.toInt()
					}
				}
			}
			val layoutParams = LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT)
			layoutParams.rightMargin = AndroidUtilities.dp(16f)
			layoutParams.leftMargin = layoutParams.rightMargin
			layoutParams.bottomMargin = AndroidUtilities.dp(12f)
			layoutParams.topMargin = layoutParams.bottomMargin
			scrollView.addView(textContainer, layoutParams)

			if (Build.VERSION.SDK_INT >= 23) {
				scrollView.setOnScrollChangeListener { v: View?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int ->
					if (textSelectionHelper != null && textSelectionHelper!!.isSelectionMode) {
						textSelectionHelper!!.invalidate()
					}
				}
			}

			setWillNotDraw(false)
		}

		fun setBlock(block: TLPageBlockPreformatted?) {
			currentBlock = block
			scrollView.scrollX = 0
			textContainer.requestLayout()
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			scrollView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
			setMeasuredDimension(width, scrollView.measuredHeight)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			canvas.drawRect(0f, AndroidUtilities.dp(8f).toFloat(), measuredWidth.toFloat(), (measuredHeight - AndroidUtilities.dp(8f)).toFloat(), preformattedBackgroundPaint!!)
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}

		override fun invalidate() {
			textContainer.invalidate()
			super.invalidate()
		}
	}

	private inner class BlockSubheaderCell(context: Context?, private val parentAdapter: WebpageAdapter) : View(context), ArticleSelectableView {
		private var textLayout: DrawingText? = null
		private val textX = AndroidUtilities.dp(18f)
		private val textY = AndroidUtilities.dp(8f)

		private var currentBlock: TLPageBlockSubheader? = null

		fun setBlock(block: TLPageBlockSubheader?) {
			currentBlock = block
			requestLayout()
		}

		override fun onTouchEvent(event: MotionEvent): Boolean {
			return checkLayoutForLinks(parentAdapter, event, this, textLayout, textX, textY) || super.onTouchEvent(event)
		}

		@SuppressLint("NewApi")
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val width = MeasureSpec.getSize(widthMeasureSpec)
			var height = 0

			if (currentBlock != null) {
				textLayout = createLayoutForText(this, null, currentBlock!!.text, width - AndroidUtilities.dp(36f), textY, currentBlock!!, if (parentAdapter.isRtl) StaticLayoutEx.ALIGN_RIGHT() else Layout.Alignment.ALIGN_NORMAL, parentAdapter)
				if (textLayout != null) {
					height += AndroidUtilities.dp((8 + 8).toFloat()) + textLayout!!.height
					textLayout!!.x = textX
					textLayout!!.y = textY
				}
			}
			else {
				height = 1
			}

			setMeasuredDimension(width, height)
		}

		override fun onDraw(canvas: Canvas) {
			if (currentBlock == null) {
				return
			}
			if (textLayout != null) {
				canvas.save()
				canvas.translate(textX.toFloat(), textY.toFloat())
				drawTextSelection(canvas, this)
				textLayout!!.draw(canvas, this)
				canvas.restore()
			}
		}

		override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
			super.onInitializeAccessibilityNodeInfo(info)
			info.isEnabled = true
			if (textLayout == null) {
				return
			}
			info.text = textLayout!!.text.toString() + ", " + context.getString(R.string.AccDescrIVHeading)
		}

		override fun fillTextLayoutBlocks(blocks: ArrayList<TextSelectionHelper.TextLayoutBlock>) {
			if (textLayout != null) {
				blocks.add(textLayout!!)
			}
		}
	}

	private class ReportCell(context: Context) : FrameLayout(context) {
		private val textView: TextView
		private val viewsTextView: TextView
		var hasViews: Boolean = false

		init {
			tag = 90

			textView = TextView(context)
			textView.text = LocaleController.getString("PreviewFeedback2", R.string.PreviewFeedback2)
			textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
			textView.gravity = Gravity.CENTER
			textView.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
			addView(textView, createFrame(LayoutHelper.MATCH_PARENT, 34f, Gravity.LEFT or Gravity.TOP, 0f, 10f, 0f, 0f))

			viewsTextView = TextView(context)
			viewsTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f)
			viewsTextView.gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
			viewsTextView.setPadding(AndroidUtilities.dp(18f), 0, AndroidUtilities.dp(18f), 0)
			addView(viewsTextView, createFrame(LayoutHelper.MATCH_PARENT, 34f, Gravity.LEFT or Gravity.TOP, 0f, 10f, 0f, 0f))
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(44f), MeasureSpec.EXACTLY))
		}

		fun setViews(count: Int) {
			if (count == 0) {
				hasViews = false
				viewsTextView.visibility = GONE
				textView.gravity = Gravity.CENTER
			}
			else {
				hasViews = true
				viewsTextView.visibility = VISIBLE
				textView.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
				viewsTextView.text = LocaleController.formatPluralStringComma("Views", count)
			}
			val color = Theme.getColor(Theme.key_switchTrack)
			textView.setTextColor(grayTextColor)
			viewsTextView.setTextColor(grayTextColor)
			textView.setBackgroundColor(Color.argb(34, Color.red(color), Color.green(color), Color.blue(color)))
		}
	}

	private fun drawTextSelection(canvas: Canvas, view: ArticleSelectableView, i: Int = 0) {
		val v = view as View
		if (v.tag != null && v.tag === BOTTOM_SHEET_VIEW_TAG && textSelectionHelperBottomSheet != null) {
			textSelectionHelperBottomSheet!!.draw(canvas, view, i)
		}
		else {
			textSelectionHelper!!.draw(canvas, view, i)
		}
	}

	fun openPhoto(block: PageBlock?, adapter: WebpageAdapter): Boolean {
		val index: Int
		val pageBlocks: List<PageBlock>
		if (block !is TLPageBlockVideo || WebPageUtils.isVideo(adapter.currentPage, block)) {
			pageBlocks = ArrayList(adapter.photoBlocks)
			index = adapter.photoBlocks.indexOf(block)
		}
		else {
			pageBlocks = listOf<PageBlock>(block)
			index = 0
		}
		val photoViewer = PhotoViewer.getInstance()
		photoViewer.setParentActivity(parentFragment)
		return photoViewer.openPhoto(index, RealPageBlocksAdapter(adapter.currentPage!!, pageBlocks), PageBlocksPhotoViewerProvider(pageBlocks))
	}

	private inner class RealPageBlocksAdapter(private val page: WebPage, private val pageBlocks: List<PageBlock>) : PageBlocksAdapter {
		override fun getItemsCount(): Int {
			return pageBlocks.size
		}

		override fun get(index: Int): PageBlock {
			return pageBlocks[index]
		}

		override fun getAll(): List<PageBlock> {
			return pageBlocks
		}

		override fun isVideo(index: Int): Boolean {
			return !(index >= pageBlocks.size || index < 0) && WebPageUtils.isVideo(page, get(index))
		}

		override fun getMedia(index: Int): TLObject? {
			if (index >= pageBlocks.size || index < 0) {
				return null
			}
			return WebPageUtils.getMedia(page, get(index))
		}

		override fun getFile(index: Int): File? {
			if (index >= pageBlocks.size || index < 0) {
				return null
			}
			return WebPageUtils.getMediaFile(page, get(index))
		}

		override fun getFileName(index: Int): String {
			var media = getMedia(index)

			if (media is TLRPC.Photo) {
				media = FileLoader.getClosestPhotoSizeWithSize(media.sizes, AndroidUtilities.getPhotoSize())
			}

			return FileLoader.getAttachFileName(media)
		}

		override fun getCaption(index: Int): CharSequence? {
			var caption: CharSequence? = null
			val pageBlock = get(index)
			if (pageBlock is TLPageBlockPhoto) {
				val url = pageBlock.url
				if (!TextUtils.isEmpty(url)) {
					val stringBuilder = SpannableStringBuilder(url)
					stringBuilder.setSpan(object : URLSpan(url) {
						override fun onClick(widget: View) {
							openWebpageUrl(getURL(), null)
						}
					}, 0, url!!.length, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
					caption = stringBuilder
				}
			}
			if (caption == null) {
				val captionRichText = getBlockCaption(pageBlock, 2)
				caption = getText(page, null, captionRichText, captionRichText, pageBlock, -AndroidUtilities.dp(100f))
				if (caption is Spannable) {
					val spans = caption.getSpans(0, caption.length, TextPaintUrlSpan::class.java)
					val builder = SpannableStringBuilder(caption.toString())
					caption = builder
					if (spans != null && spans.isNotEmpty()) {
						for (span in spans) {
							builder.setSpan(object : URLSpan(span.url) {
								override fun onClick(widget: View) {
									openWebpageUrl(url, null)
								}
							}, caption.getSpanStart(span), caption.getSpanEnd(span), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
						}
					}
				}
			}
			return caption
		}

		override fun getFileLocation(media: TLObject, size: IntArray): PhotoSize? {
			if (media is TLRPC.Photo) {
				val sizeFull = FileLoader.getClosestPhotoSizeWithSize(media.sizes, AndroidUtilities.getPhotoSize())
				if (sizeFull != null) {
					size[0] = sizeFull.size
					if (size[0] == 0) {
						size[0] = -1
					}
					return sizeFull
				}
				else {
					size[0] = -1
				}
			}
			else if (media is TLRPC.Document) {
				val thumb = FileLoader.getClosestPhotoSizeWithSize(media.thumbs, 90)
				if (thumb != null) {
					size[0] = thumb.size
					if (size[0] == 0) {
						size[0] = -1
					}
					return thumb
				}
			}
			return null
		}

		override fun updateSlideshowCell(currentPageBlock: PageBlock) {
			val count = listView!![0].childCount
			for (a in 0..<count) {
				val child = listView!![0].getChildAt(a)
				if (child is BlockSlideshowCell) {
					val idx = child.currentBlock!!.items.indexOf(currentPageBlock)
					if (idx != -1) {
						child.innerListView.setCurrentItem(idx, false)
						break
					}
				}
			}
		}

		override fun getParentObject(): Any {
			return page
		}
	}

	private inner class PageBlocksPhotoViewerProvider(private val pageBlocks: List<PageBlock>) : EmptyPhotoViewerProvider() {
		private val tempArr = IntArray(2)

		override fun getPlaceForPhoto(messageObject: MessageObject?, fileLocation: FileLocation?, index: Int, needPreview: Boolean): PlaceProviderObject? {
			if (index < 0 || index >= pageBlocks.size) {
				return null
			}
			val imageReceiver = getImageReceiverFromListView(listView!![0], pageBlocks[index], tempArr)
			if (imageReceiver == null) {
				return null
			}
			val `object` = PlaceProviderObject()
			`object`.viewX = tempArr[0]
			`object`.viewY = tempArr[1]
			`object`.parentView = listView!![0]
			`object`.imageReceiver = imageReceiver
			`object`.thumb = imageReceiver.bitmapSafe
			`object`.radius = imageReceiver.getRoundRadius()
			`object`.clipTopAddition = currentHeaderHeight
			return `object`
		}

		fun getImageReceiverFromListView(listView: ViewGroup, pageBlock: PageBlock, coords: IntArray?): ImageReceiver? {
			val count = listView.childCount
			for (a in 0..<count) {
				val imageReceiver = getImageReceiverView(listView.getChildAt(a), pageBlock, coords)
				if (imageReceiver != null) {
					return imageReceiver
				}
			}
			return null
		}

		fun getImageReceiverView(view: View, pageBlock: PageBlock, coords: IntArray?): ImageReceiver? {
			if (view is BlockPhotoCell) {
				if (view.currentBlock === pageBlock) {
					view.getLocationInWindow(coords)
					return view.imageView
				}
			}
			else if (view is BlockVideoCell) {
				if (view.currentBlock === pageBlock) {
					view.getLocationInWindow(coords)
					return view.imageView
				}
			}
			else if (view is BlockCollageCell) {
				return getImageReceiverFromListView(view.innerListView, pageBlock, coords)
			}
			else if (view is BlockSlideshowCell) {
				return getImageReceiverFromListView(view.innerListView, pageBlock, coords)
			}
			else if (view is BlockListItemCell) {
				if (view.blockLayout != null) {
					return getImageReceiverView(view.blockLayout!!.itemView, pageBlock, coords)
				}
			}
			else if (view is BlockOrderedListItemCell) {
				if (view.blockLayout != null) {
					return getImageReceiverView(view.blockLayout!!.itemView, pageBlock, coords)
				}
			}
			return null
		}
	}

	companion object {
		private const val search_item = 1
		private const val share_item = 2
		private const val open_item = 3
		private const val settings_item = 4

		@SuppressLint("StaticFieldLeak")
		@Volatile
		private var Instance: ArticleViewer? = null

		@JvmStatic
		fun getInstance(): ArticleViewer {
			var localInstance = Instance
			if (localInstance == null) {
				synchronized(ArticleViewer::class.java) {
					localInstance = Instance
					if (localInstance == null) {
						localInstance = ArticleViewer()
						Instance = localInstance
					}
				}
			}
			return localInstance!!
		}

		@JvmStatic
		fun hasInstance(): Boolean {
			return Instance != null
		}

		val ARTICLE_VIEWER_INNER_TRANSLATION_X: Property<WindowView, Float> = object : AnimationProperties.FloatProperty<WindowView>("innerTranslationX") {
			override fun setValue(`object`: WindowView, value: Float) {
				`object`.setInnerTranslationX(value)
			}

			override fun get(`object`: WindowView): Float {
				return `object`.getInnerTranslationX()
			}
		}

		private const val TEXT_FLAG_REGULAR = 0
		private const val TEXT_FLAG_MEDIUM = 1
		private const val TEXT_FLAG_ITALIC = 2
		private const val TEXT_FLAG_MONO = 4
		private const val TEXT_FLAG_URL = 8
		private const val TEXT_FLAG_UNDERLINE = 16
		private const val TEXT_FLAG_STRIKE = 32
		private const val TEXT_FLAG_MARKED = 64
		private const val TEXT_FLAG_SUB = 128
		private const val TEXT_FLAG_SUP = 256
		private const val TEXT_FLAG_WEBPAGE_URL = 512

		private val audioTimePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
		private var errorTextPaint: TextPaint? = null
		private val photoCaptionTextPaints = SparseArray<TextPaint>()
		private val photoCreditTextPaints = SparseArray<TextPaint>()
		private val titleTextPaints = SparseArray<TextPaint>()
		private val kickerTextPaints = SparseArray<TextPaint>()
		private val headerTextPaints = SparseArray<TextPaint>()
		private val subtitleTextPaints = SparseArray<TextPaint>()
		private val subheaderTextPaints = SparseArray<TextPaint>()
		private val authorTextPaints = SparseArray<TextPaint>()
		private val footerTextPaints = SparseArray<TextPaint>()
		private val paragraphTextPaints = SparseArray<TextPaint>()
		private val listTextPaints = SparseArray<TextPaint>()
		private val preformattedTextPaints = SparseArray<TextPaint>()
		private val quoteTextPaints = SparseArray<TextPaint>()
		private val embedPostTextPaints = SparseArray<TextPaint>()
		private val embedPostCaptionTextPaints = SparseArray<TextPaint>()
		private val mediaCaptionTextPaints = SparseArray<TextPaint>()
		private val mediaCreditTextPaints = SparseArray<TextPaint>()
		private val relatedArticleTextPaints = SparseArray<TextPaint>()
		private val detailsTextPaints = SparseArray<TextPaint>()
		private val tableTextPaints = SparseArray<TextPaint>()

		private var embedPostAuthorPaint: TextPaint? = null
		private var embedPostDatePaint: TextPaint? = null
		private var channelNamePaint: TextPaint? = null
		private var channelNamePhotoPaint: TextPaint? = null
		private var relatedArticleHeaderPaint: TextPaint? = null
		private var relatedArticleTextPaint: TextPaint? = null

		private var listTextPointerPaint: TextPaint? = null
		private var listTextNumPaint: TextPaint? = null

		private var photoBackgroundPaint: Paint? = null
		private var preformattedBackgroundPaint: Paint? = null
		private var quoteLinePaint: Paint? = null
		private var dividerPaint: Paint? = null
		private var tableLinePaint: Paint? = null
		private var tableHalfLinePaint: Paint? = null
		private var tableHeaderPaint: Paint? = null
		private var tableStripPaint: Paint? = null
		private var urlPaint: Paint? = null
		private var webpageUrlPaint: Paint? = null
		private var webpageSearchPaint: Paint? = null
		private var webpageMarkPaint: Paint? = null

		init {
			audioTimePaint.setTypeface(Theme.TYPEFACE_DEFAULT)
		}

		fun getPlainText(richText: RichText?): CharSequence? {
			if (richText == null) {
				return ""
			}
			if (richText is TLTextFixed) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextItalic) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextBold) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextUnderline) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextStrike) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextEmail) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextUrl) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextPlain) {
				return richText.text
			}
			else if (richText is TLTextAnchor) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextEmpty) {
				return ""
			}
			else if (richText is TLTextConcat) {
				val stringBuilder = StringBuilder()
				val count: Int = richText.texts.size
				for (a in 0..<count) {
					stringBuilder.append(getPlainText(richText.texts[a]))
				}
				return stringBuilder
			}
			else if (richText is TLTextSubscript) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextSuperscript) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextMarked) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextPhone) {
				return getPlainText(richText.text)
			}
			else if (richText is TLTextImage) {
				return ""
			}
			return ""
		}

		fun getUrl(richText: RichText?): String? {
			if (richText is TLTextFixed) {
				return getUrl(richText.text)
			}
			else if (richText is TLTextItalic) {
				return getUrl(richText.text)
			}
			else if (richText is TLTextBold) {
				return getUrl(richText.text)
			}
			else if (richText is TLTextUnderline) {
				return getUrl(richText.text)
			}
			else if (richText is TLTextStrike) {
				return getUrl(richText.text)
			}
			else if (richText is TLTextEmail) {
				return richText.email
			}
			else if (richText is TLTextUrl) {
				return richText.url
			}
			else if (richText is TLTextPhone) {
				return richText.phone
			}
			return null
		}

		private val grayTextColor: Int
			get() = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)

		private var dotsPaint: Paint? = null
	}
}
