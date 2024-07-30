/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2022-2023.
 */
package org.telegram.ui

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentIntroBinding
import org.telegram.messenger.databinding.FragmentIntroPage1Binding
import org.telegram.messenger.databinding.FragmentIntroPage2Binding
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.LayoutHelper

class IntroActivity : BaseFragment() {
	private var binding: FragmentIntroBinding? = null
	private var startPressed = false
	private var isOnLogout = false

	private val onPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
		override fun onPageSelected(position: Int) {
			binding?.pageIndicatorContainer?.children?.forEach {
				it.isSelected = (it.tag == position)
			}
		}
	}

	override fun onFragmentCreate(): Boolean {
		MessagesController.getGlobalMainSettings().edit().putLong("intro_crashed_time", System.currentTimeMillis()).commit()
		return true
	}

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(false)

		binding = FragmentIntroBinding.inflate(LayoutInflater.from(context))

		binding?.viewPager?.registerOnPageChangeCallback(onPageChangeCallback)

		binding?.startButton?.setOnClickListener(View.OnClickListener {
			if (startPressed) {
				return@OnClickListener
			}

			startPressed = true

			presentFragment(LoginActivity().setIntroView(binding!!.root), true)
		})

		val adapter = IntroAdapter().also {
			binding?.viewPager?.adapter = it
		}

		for (i in 0 until adapter.itemCount) {
			val view = View(context)
			view.setBackgroundResource(R.drawable.empty_page_indicator)
			view.tag = i

			if (i == 0) {
				view.isSelected = true
			}

			binding?.pageIndicatorContainer?.addView(view, LayoutHelper.createLinear(11, 11))
		}

		fragmentView = binding?.root

		connectionsManager.updateDcSettings()

		return fragmentView
	}

	override fun hasForceLightStatusBar(): Boolean {
		return true
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		binding?.viewPager?.unregisterOnPageChangeCallback(onPageChangeCallback)
		binding = null
		MessagesController.getGlobalMainSettings().edit().putLong("intro_crashed_time", 0).commit()
	}

	fun setOnLogout(): IntroActivity {
		isOnLogout = true
		return this
	}

	override fun onCustomTransitionAnimation(isOpen: Boolean, callback: Runnable): AnimatorSet? {
		if (isOnLogout) {
			val set = AnimatorSet().setDuration(50)
			set.playTogether(ValueAnimator.ofFloat())
			return set
		}

		return null
	}

	override fun isLightStatusBar(): Boolean {
		return !AndroidUtilities.isDarkTheme()
	}

	private inner class IntroAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
		override fun getItemViewType(position: Int): Int {
			return position
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
			return when (viewType) {
				0 -> FragmentIntroPage1Binding.inflate(LayoutInflater.from(parent.context), parent, false).root
				1 -> FragmentIntroPage2Binding.inflate(LayoutInflater.from(parent.context), parent, false).root
				else -> throw IllegalArgumentException("Invalid view type: $viewType")
			}.let {
				object : RecyclerView.ViewHolder(it) {}
			}
		}

		override fun getItemCount(): Int {
			return 2
		}

		override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
			// unused
		}
	}
}
