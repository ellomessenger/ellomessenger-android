/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhaylo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui.aibot

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.databinding.AiBuyDescriptionItemBinding
import org.telegram.messenger.databinding.AiBuyPopupBinding
import org.telegram.messenger.databinding.AiSubscriptionPlansBinding
import org.telegram.messenger.utils.capitalizeFirstLetter
import org.telegram.messenger.utils.fillElloCoinLogos
import org.telegram.messenger.utils.getResId
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.invisible
import org.telegram.messenger.utils.toJson
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.TLRPC.TL_error
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.channel.SubscriptionResultFragment

class AiSubscriptionPlansFragment : BaseFragment(), NotificationCenterDelegate {
	sealed interface ItemType {
		object Text : ItemType
		object Picture : ItemType
	}

	data class Description(val description: String, val isSubItem: Boolean = false)

	private val textPlanDescriptions by lazy {
		(1..15).flatMap {
			listOf(
					Description(context!!.getString(getResId("ai_buy_text_item_$it", R.string::class.java))),
					Description(context!!.getString(getResId("ai_buy_text_subitem_$it", R.string::class.java)), true),
			)
		}
	}

	private val picturesPlanDescriptions by lazy {
		(1..5).flatMap {
			listOf(
					Description(context!!.getString(getResId("ai_buy_picture_item_$it", R.string::class.java))),
					Description(context!!.getString(getResId("ai_buy_picture_subitem_$it", R.string::class.java)), true),
			)
		}
	}

	private val descriptions by lazy {
		mapOf(0 to textPlanDescriptions, 1 to picturesPlanDescriptions, 2 to textPlanDescriptions)
	}

	lateinit var viewPager: ViewPager2
	lateinit var binding: AiSubscriptionPlansBinding
	private var selectedPlanTab = PLAN_BUY
	private var viewPagerPosition = 0
	private var loaderAnimationView: RLottieImageView? = null

	override fun onFragmentCreate(): Boolean {
		notificationCenter.let {
			it.addObserver(this, NotificationCenter.aiSubscriptionSuccess)
			it.addObserver(this, NotificationCenter.aiSubscriptionError)
		}

		return super.onFragmentCreate()
	}

	override fun createView(context: Context): View {
		actionBar?.setTitle(context.getString(R.string.ai_buy_fragment_title))
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.castShadows = false

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}
				}
			}
		})

		binding = AiSubscriptionPlansBinding.inflate(LayoutInflater.from(context))

		fragmentView = binding.root

		viewPager = binding.aiBuyViewPager

		binding.tabs.tab1.setOnClickListener {
			it.isEnabled = false

			binding.tabs.tab2.isEnabled = true

			selectedPlanTab = PLAN_BUY

			updateDescriptionItems(VIEW_TEXT)
		}

		binding.tabs.tab2.setOnClickListener {
			it.isEnabled = false

			binding.tabs.tab1.isEnabled = true

			selectedPlanTab = PLAN_BUY

			updateDescriptionItems(VIEW_IMAGE)
		}

		viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
			override fun onPageSelected(position: Int) {
				viewPagerPosition = position

				binding.root.post {
					updateSelectedPageIndicator(position)
					updateDescriptionItems(position)
				}

				binding.freeRequests.text = when(position) {
					VIEW_TEXT -> context.getString(R.string.ai_prompts_per_month_format, DEFAULT_FREE_TEXT_PROMPTS)
					VIEW_IMAGE -> context.getString(R.string.ai_prompts_per_month_format, DEFAULT_FREE_IMAGE_PROMPTS)
					else -> context.getString(R.string.ai_image_text_prompts_per_month_format)
				}

				if (position == VIEW_IMAGE_TEXT) {
					binding.tabsContainer.visible()
				} else {
					binding.tabsContainer.gone()
					binding.tabs.tab1.isEnabled = false
					binding.tabs.tab2.isEnabled = true
				}
			}
		})

		binding.buyButton.setOnClickListener {
			if (selectedPlanTab == PLAN_BUY) {
				createBuyAlert()
			}
			else {
				chatBotController.buyAiItem(true, viewPagerPosition)
			}
		}

		setupPagerIndicator()

		updateUiData()

		initProgressBar()

		viewPager.setCurrentItem(viewPagerPosition, false)

		return binding.root
	}

	fun updateDescriptionItems(position: Int) {
		val descriptionList = descriptions[position]!!

		binding.descriptions.removeAllViews()

		for (desc in descriptionList) {
			val view = AiBuyDescriptionItemBinding.inflate(LayoutInflater.from(binding.root.context))

			if (desc.isSubItem) {
				view.check.invisible()
				view.text.setPadding(view.text.paddingLeft, 0, view.text.paddingRight, 0)
				view.text.typeface = Theme.TYPEFACE_DEFAULT
			}

			view.text.text = desc.description

			binding.descriptions.addView(view.root, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))
		}
	}

	private fun updateSelectedPageIndicator(position: Int) {
		binding.pageIndicators.children.forEach {
			it.isSelected = (it.tag == position)
		}
	}

	private fun setupPagerIndicator() {
		val itemsCount = 3

		binding.pageIndicators.removeAllViews()

		(0 until itemsCount).forEach {
			val view = View(context)
			view.setBackgroundResource(R.drawable.page_indicator)
			view.tag = it
			binding.pageIndicators.addView(view, LayoutHelper.createLinear(5, 5))
		}
	}

	private fun initProgressBar() {
		loaderAnimationView = context?.let { RLottieImageView(it) }
		binding.progressContainer.addView(loaderAnimationView)

		loaderAnimationView?.setAutoRepeat(true)
		loaderAnimationView?.setAnimation(R.raw.ello_loader, 112, 112)
	}

	private fun showProgress(show: Boolean) {
		binding.progressContainer.visibility = if (show) View.VISIBLE else View.GONE
		if (show) loaderAnimationView?.playAnimation() else loaderAnimationView?.stopAnimation()
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.aiSubscriptionSuccess -> {
				FileLog.d("AI bot success: " + if (args[0] is ElloRpc.SubscriptionInfoAiBot) args[0].toJson() else "successfully failed $args")

				if (args[0] is ElloRpc.SubscriptionInfoAiBot) {
					showProgress(false)

					presentFragment(SubscriptionResultFragment(Bundle().apply {
						putBoolean(SubscriptionResultFragment.SUCCESS, true)
						putInt(SubscriptionResultFragment.IMAGE_RES_ID, R.drawable.panda_payment_congrats)
						putString(SubscriptionResultFragment.TITLE, context?.getString(R.string.ai_buy_success_title))

						if (selectedPlanTab == PLAN_BUY) {
							if (viewPagerPosition == VIEW_IMAGE_TEXT) {
								putString(SubscriptionResultFragment.DESCRIPTION, context?.getString(R.string.ai_buy_success_image_text_sub_title))
							} else {
								putString(SubscriptionResultFragment.DESCRIPTION, context?.getString(R.string.ai_buy_success_sub_title, if (viewPagerPosition == VIEW_TEXT) DEFAULT_PAID_TEXT_PROMPTS else DEFAULT_PAID_IMAGE_PROMPTS))
							}
						}
						else {
							putString(SubscriptionResultFragment.DESCRIPTION, context?.getString(R.string.ai_subscription_success))
						}
					}))
				}
			}

			NotificationCenter.aiSubscriptionError -> {
				var drawableResId = R.drawable.panda_payment_error
				val originalError = (args[0] as? TL_error)?.text
				var errorText = originalError?.capitalizeFirstLetter() ?: context?.getString(R.string.ai_subscription_failure)
				var showTopup = false

				if (originalError?.contains("enough") == true && originalError.contains("money")) {
					errorText = context?.getString(R.string.subscription_fail)
					drawableResId = R.drawable.panda_poor
					showTopup = true
				}

				showProgress(false)

				presentFragment(SubscriptionResultFragment(Bundle().apply {
					putBoolean(SubscriptionResultFragment.SUCCESS, false)
					putInt(SubscriptionResultFragment.IMAGE_RES_ID, drawableResId)
					putString(SubscriptionResultFragment.TITLE, context?.getString(R.string.oops))
					putString(SubscriptionResultFragment.DESCRIPTION, errorText)
					putBoolean(SubscriptionResultFragment.SHOW_TOPUP, showTopup)
				}))
			}
		}
	}

	private fun updateUiData() {
		viewPager.adapter = AIBuyAdapter(true)
		viewPager.setCurrentItem(viewPagerPosition, false)
	}

	private fun createBuyAlert() {
		val context = context ?: return
		val builder = BottomSheet.Builder(context)
		builder.setApplyTopPadding(true)

		val bindingPopup = AiBuyPopupBinding.inflate(LayoutInflater.from(context))

		when(viewPagerPosition) {
			VIEW_IMAGE -> {
				bindingPopup.image1.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ai_buy_picture, null))
				bindingPopup.image1Fg.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ai_buy_type_picture, null))
				bindingPopup.packAmount.text = "$DEFAULT_PAID_IMAGE_PROMPTS"
				bindingPopup.priceTag.text = context.getString(R.string.simple_coin_format, DEFAULT_IMAGE_PRICE).fillElloCoinLogos(size = 22f, tintColor = context.getColor(R.color.white_day_night), isIconBold = true)
				bindingPopup.packUnit.text = context.getString(R.string.image_prompts)
			}

			VIEW_TEXT -> {
				bindingPopup.packAmount.text = "$DEFAULT_PAID_TEXT_PROMPTS"
				bindingPopup.priceTag.text = context.getString(R.string.simple_coin_format, DEFAULT_TEXT_PRICE).fillElloCoinLogos(size = 22f, tintColor = context.getColor(R.color.white_day_night), isIconBold = true)
				bindingPopup.packUnit.text = context.getString(R.string.text_prompts)
			}

			else -> {
				bindingPopup.imageLayout.gone()
				bindingPopup.imageTextLayout.visible()

				bindingPopup.itemToBuy.gone()
				bindingPopup.itemToTextImagesBuy.visible()

				bindingPopup.textPackAmount.text = "$DEFAULT_PAID_AI_TEXT_PROMPTS"
				bindingPopup.imagePackAmount.text = "$DEFAULT_PAID_AI_IMAGE_PROMPTS"
				bindingPopup.imageTextPriceTag.text = context.getString(R.string.simple_coin_format, DEFAULT_TEXT_IMAGE_PRICE).fillElloCoinLogos(size = 22f, tintColor = context.getColor(R.color.white_day_night), isIconBold = true)
			}
		}

		builder.customView = bindingPopup.root

		val alert = builder.create()

		bindingPopup.close.setOnClickListener {
			alert.dismiss()
		}

		bindingPopup.buyButton.setOnClickListener {
			showProgress(true)

			chatBotController.buyAiItem(selectedPlanTab == PLAN_SUBSCRIPTION, viewPagerPosition)

			alert.dismiss()
		}

		showDialog(alert)
	}

	override fun onFragmentDestroy() {
		notificationCenter.let {
			it.removeObserver(this, NotificationCenter.aiSubscriptionSuccess)
			it.removeObserver(this, NotificationCenter.aiSubscriptionError)
		}

		super.onFragmentDestroy()
	}

	companion object {
		private const val PLAN_BUY = 1
		private const val PLAN_SUBSCRIPTION = 0
		private const val VIEW_TEXT = 0
		private const val VIEW_IMAGE = 1
		private const val VIEW_IMAGE_TEXT = 2

		const val DEFAULT_PAID_IMAGE_PROMPTS = 100
		const val DEFAULT_PAID_TEXT_PROMPTS = 80
		const val DEFAULT_PAID_AI_IMAGE_PROMPTS = 50
		const val DEFAULT_PAID_AI_TEXT_PROMPTS = 40
		const val DEFAULT_FREE_TEXT_PROMPTS = 10
		const val DEFAULT_FREE_IMAGE_PROMPTS = 5
		const val DEFAULT_IMAGE_PRICE = 5.99f
		const val DEFAULT_TEXT_PRICE = 5.99f
		const val DEFAULT_TEXT_IMAGE_PRICE = 5.99f
		const val DEFAULT_IMAGE_SUB_PRICE = 15.0f
		const val DEFAULT_TEXT_SUB_PRICE = 20.0f
	}
}
