package org.telegram.ui

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updateLayoutParams
import androidx.customview.widget.ViewDragHelper
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.R
import org.telegram.messenger.databinding.AiBotProcessingBinding
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.Components.LayoutHelper
import java.util.ArrayList

class BotHintBottomSheet(context: Context) : FrameLayout(context) {
    private var dragTop: Int
    private var dragHelper: ViewDragHelper? = null
    private val dragView: View
    private val root: ViewGroup

    init {
        val binding = AiBotProcessingBinding.inflate(LayoutInflater.from(context))
        root = binding.root
        dragView = root.findViewById(R.id.header_view)
        dragView.tag = "DragView"
        addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.BOTTOM))
        dragTop = root.height
        root.removeView(dragView)
        addView(dragView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.CENTER))

        dragHelper = ViewDragHelper.create(this, 1.0f, object : ViewDragHelper.Callback() {
            override fun tryCaptureView(child: View, pointerId: Int): Boolean {
                FileLog.d("aibot tryCaptureView child:${child.tag} pointerId:$pointerId")
                return child == dragView
            }

            override fun onViewPositionChanged(
                changedView: View,
                left: Int,
                top: Int,
                dx: Int,
                dy: Int
            ) {
                dragTop = top
                invalidate()
                FileLog.d("aibot onViewPositionChanged child:${changedView.tag} left:$left top:$top dx:$dx dy:$dy")
            }

            override fun onEdgeTouched(edgeFlags: Int, pointerId: Int) {
                FileLog.d("aibot onEdgeTouched edgeFlags:$edgeFlags pointerId:$pointerId")
                super.onEdgeTouched(edgeFlags, pointerId)
            }


            override fun onEdgeDragStarted(edgeFlags: Int, pointerId: Int) {
                FileLog.d("aibot onEdgeDragStarted edgeFlags:$edgeFlags pointerId:$pointerId")
                dragHelper!!.captureChildView(dragView, pointerId)
            }

            override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
                FileLog.d("aibot clampViewPositionVertical child:${child.tag} top:$top dy:$dy")
                val topBound = paddingTop
                val bottomBound = height - dragView.height
                val newTop = Math.min(Math.max(top, topBound), bottomBound)
                return newTop
            }

            override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
                val leftBound = paddingLeft
                val rightBound = width - dragView.width
                val newLeft = Math.min(Math.max(left, leftBound), rightBound)
                FileLog.d("aibot clampViewPositionHorizontal child:${child.tag} left:$top dx:$dx newLeft:$newLeft")
                return newLeft
            }

        })

        dragHelper!!.setEdgeTrackingEnabled(ViewDragHelper.EDGE_TOP)

    }

    override fun computeScroll() {
        if (dragHelper!!.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        val action = MotionEventCompat.getActionMasked(ev)
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            dragHelper!!.cancel()
            return false
        }
        return dragHelper!!.shouldInterceptTouchEvent(ev!!)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        dragHelper!!.processTouchEvent(event!!)
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        measureChildren(widthMeasureSpec, heightMeasureSpec)
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        val maxHeight = MeasureSpec.getSize(heightMeasureSpec)

        setMeasuredDimension(
            resolveSizeAndState(maxWidth, widthMeasureSpec, 0),
            resolveSizeAndState(maxHeight, heightMeasureSpec, 0)
        );
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        FileLog.d("aibot onLayout rootTop:${dragTop} rootBottom:${dragTop + bottom}")
        super.onLayout(changed, left, top, right, bottom)
    }

    companion object {
        fun createBottomSheet(context: Context): BottomSheet {
            val contentView = AiBotProcessingBinding.inflate(LayoutInflater.from(context)).root
            val bottomSheet = object : BottomSheet(context, false) {

                init{
                    isFullscreen = true
                }

                override fun canDismissWithSwipe(): Boolean {
                    return false
                }

                override fun canDismissWithTouchOutside(): Boolean {
                    return false
                }

            }

            val frameLayout = FrameLayout(context)
            frameLayout.setBackgroundColor(ResourcesCompat.getColor(context.resources,R.color.dark_gray,null))
            frameLayout.addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))
            bottomSheet.setCustomView(frameLayout)
            bottomSheet.setDelegate(object : BottomSheet.BottomSheetDelegateInterface{
                override fun onOpenAnimationStart() {
                  FileLog.d("aibot onOpenAnimationStart")
                }

                override fun onOpenAnimationEnd() {
                    FileLog.d("aibot onOpenAnimationEnd")

                }

                override fun canDismiss()=false


            })


            return bottomSheet
        }
    }
}
