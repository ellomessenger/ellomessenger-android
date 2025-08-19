/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.aibot

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLog
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.R
import org.telegram.messenger.databinding.AiBuyPopupBinding
import org.telegram.messenger.databinding.AiSubsActivePopupBinding
import org.telegram.messenger.databinding.AiSubscriptionPlansBinding
import org.telegram.messenger.utils.capitalizeFirstLetter
import org.telegram.messenger.utils.fillElloCoinLogos
import org.telegram.messenger.utils.getResId
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.toJson
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.TLRPC.TLError
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.RLottieImageView
import org.telegram.ui.channel.SubscriptionResultFragment
import org.telegram.ui.profile.utils.toFormattedDate

class AiSubscriptionPlansFragment(args: Bundle) : BaseFragment(args), NotificationCenterDelegate {
	data class Description(val title: String, val description: String)

	private val textPlanDescriptions by lazy {
		ArrayList((1..16).flatMap {
			listOf(Description(context!!.getString(getResId("ai_buy_text_item_$it", R.string::class.java)), context!!.getString(getResId("ai_buy_text_subitem_$it", R.string::class.java))))
		})
	}

	private val picturesPlanDescriptions by lazy {
		ArrayList((1..5).flatMap {
			listOf(Description(context!!.getString(getResId("ai_buy_picture_item_$it", R.string::class.java)), context!!.getString(getResId("ai_buy_picture_subitem_$it", R.string::class.java))))
		})
	}

	private var binding: AiSubscriptionPlansBinding? = null
	private var selectedPlanTab = PLAN_BUY
	private var aiPurchasePosition = 0
	private var loaderAnimationView: RLottieImageView? = null
	private var adapter: AIBuyAdapter? = null
	private var packPrice = 0f
	private var subsPackPrice = 0f
	private var priceInfo: ElloRpc.PriceInfoResponse? = null
	private var isSubscription = false

	override fun onFragmentCreate(): Boolean {
		notificationCenter.let {
			it.addObserver(this, NotificationCenter.aiSubscriptionSuccess)
			it.addObserver(this, NotificationCenter.aiSubscriptionError)
		}

		aiPurchasePosition = arguments?.getInt("ai_purchase", 0) ?: 0

		priceInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			arguments?.getSerializable("price_info", ElloRpc.PriceInfoResponse::class.java)
		}
		else {
			@Suppress("DEPRECATION") arguments?.getSerializable("price_info") as? ElloRpc.PriceInfoResponse
		}

		return super.onFragmentCreate()
	}

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(false)
		actionBar?.background = null
		actionBar?.setItemsColor(-0x1, false)

		binding = AiSubscriptionPlansBinding.inflate(LayoutInflater.from(context))
		fragmentView = binding?.root

		binding?.back?.background = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(34f), 0, 0x28ffffff)
		binding?.packCheckBox?.isChecked = true

		chatBotController.updateSubscriptionInfo(0L)

		initAdapter()
		initAiPurchase()
		initListeners()
		initProgressBar()

		return binding?.root
	}

	private fun initAiPurchase() {
		val binding = binding ?: return

		when (aiPurchasePosition) {
			VIEW_TEXT -> {
				packPrice = priceInfo?.textPackPrice ?: 0.0f
				subsPackPrice = priceInfo?.textSubscriptionPrice ?: 0.0f

				binding.root.setBackgroundResource(R.drawable.background_ai_chat_purchase)
				binding.actionBarTitle.text = context?.getString(R.string.ai_buy_text)
				binding.iconAiPurchaseType.setBackgroundResource(R.drawable.ic_ai_chat_purchase)
				binding.txtAiPurchaseType.text = context?.getString(R.string.ai_buy_plans, context?.getString(R.string.ai_buy_text))

				binding.unlimitedPromptsPrice.text = "${priceInfo?.textSubscriptionPrice}"
				binding.packCount.text = "${priceInfo?.textDefaultQuantity}"
				binding.packPromptsPrice.text = "${priceInfo?.textPackPrice}"

				binding.buyButton.text = context?.getString(R.string.buy_pack_text, priceInfo?.textPackPrice)

				updateDescriptionItems(textPlanDescriptions)
			}

			VIEW_IMAGE -> {
				packPrice = priceInfo?.imagePackPrice ?: 0.0f
				subsPackPrice = priceInfo?.imageSubscriptionPrice ?: 0.0f

				binding.root.setBackgroundResource(R.drawable.background_ai_images_purchase)
				binding.actionBarTitle.text = context?.getString(R.string.ai_buy_picture)
				binding.iconAiPurchaseType.setBackgroundResource(R.drawable.ic_ai_images_purchase)
				binding.txtAiPurchaseType.text = context?.getString(R.string.ai_buy_plans, context?.getString(R.string.ai_buy_picture))

				binding.unlimitedPromptsPrice.text = "${priceInfo?.imageSubscriptionPrice}"
				binding.packCount.text = "${priceInfo?.imageDefaultQuantity}"
				binding.packPromptsPrice.text = "${priceInfo?.imagePackPrice}"

				binding.buyButton.text = context?.getString(R.string.buy_pack_text, priceInfo?.imagePackPrice)

				updateDescriptionItems(picturesPlanDescriptions)
			}

			VIEW_IMAGE_TEXT -> {
				packPrice = priceInfo?.doublePackPrice ?: 0.0f
				subsPackPrice = priceInfo?.doubleSubscriptionPrice ?: 0.0f

				binding.root.setBackgroundResource(R.drawable.background_ai_chat_images_purchase)
				binding.actionBarTitle.text = context?.getString(R.string.ai_buy_chat_picture)
				binding.iconAiPurchaseType.setBackgroundResource(R.drawable.ic_ai_chat_images_purchase)
				binding.txtAiPurchaseType.text = context?.getString(R.string.ai_buy_chat_picture)

				binding.tabsContainer.visible()
				binding.defaultPackPriceContainer.gone()
				binding.combinedPackPriceContainer.visible()

				binding.unlimitedPromptsPrice.text = "${priceInfo?.doubleSubscriptionPrice}"
				binding.chatsPackCount.text = "${priceInfo?.doublePackTextQuantity}"
				binding.imagesPackCount.text = "${priceInfo?.doublePackImageQuantity}"
				binding.combinedPackPromptsPrice.text = "${priceInfo?.doublePackPrice}"

				binding.buyButton.text = context?.getString(R.string.buy_pack_text, priceInfo?.doublePackPrice)

				updateDescriptionItems(textPlanDescriptions)
			}
		}
	}

	private fun initAdapter() {
		adapter = AIBuyAdapter()

		binding?.descriptions?.layoutManager = LinearLayoutManager(context)
		binding?.descriptions?.adapter = adapter
	}

	private fun updateDescriptionItems(descriptionList: ArrayList<Description>) {
		adapter?.setDescription(descriptionList)
	}

	private fun initListeners() {
		binding?.back?.setOnClickListener {
			finishFragment()
		}

		binding?.packCheckBox?.setOnClickListener {
			binding?.unlimSubsActiveButton?.gone()
			binding?.buyButton?.visible()

			binding?.unlimitedCheckBox?.isChecked = false
			binding?.unlimitedCheckBox?.isClickable = true
			binding?.packCheckBox?.isClickable = false

			isSubscription = false

			binding?.buyButton?.text = context?.getString(R.string.buy_pack_text, packPrice)
		}

		binding?.unlimitedCheckBox?.setOnClickListener {
			checkSubsIsExpired()

			binding?.packCheckBox?.isChecked = false
			binding?.unlimitedCheckBox?.isClickable = false
			binding?.packCheckBox?.isClickable = true

			isSubscription = true

			binding?.buyButton?.text = context?.getString(R.string.buy_pack_text, subsPackPrice)
		}

		binding?.tabs?.tab1?.setOnClickListener {
			it.isEnabled = false

			binding?.tabs?.tab2?.isEnabled = true

			selectedPlanTab = PLAN_BUY

			updateDescriptionItems(textPlanDescriptions)
		}

		binding?.tabs?.tab2?.setOnClickListener {
			it.isEnabled = false

			binding?.tabs?.tab1?.isEnabled = true

			selectedPlanTab = PLAN_BUY

			updateDescriptionItems(picturesPlanDescriptions)
		}

		binding?.buyButton?.setOnClickListener {
			if (selectedPlanTab == PLAN_BUY) {
				createBuyAlert()
			}
			else {
				chatBotController.buyAiItem(isSubscription, aiPurchasePosition)
			}
		}

		binding?.unlimSubsActiveButton?.setOnClickListener {
			createSubsActiveAlert()
		}
	}

	private fun checkSubsIsExpired() {
		val subscriptionExpired = when (aiPurchasePosition) {
			VIEW_TEXT -> chatBotController.lastSubscriptionInfo?.textSubExpired
			VIEW_IMAGE -> chatBotController.lastSubscriptionInfo?.imgSubExpired
			VIEW_IMAGE_TEXT -> chatBotController.lastSubscriptionInfo?.doubleSubExpired
			else -> true
		}

		if (subscriptionExpired == false) {
			binding?.buyButton?.gone()
			binding?.unlimSubsActiveButton?.visible()

			val (subscriptionText, expiresAt) = getSubscriptionDetails(aiPurchasePosition)

			binding?.subscriptionStatusText?.text = context?.getString(R.string.plans_subscription_status, subscriptionText)
			binding?.expiresUntilText?.text = context?.getString(R.string.plans_expires_until, expiresAt)
		}
	}

	private fun getSubscriptionDetails(position: Int): Pair<String?, String?> {
		val subsInfo = chatBotController.lastSubscriptionInfo

		val subscriptionText = when (position) {
			VIEW_TEXT -> context?.getString(R.string.ai_buy_text)
			VIEW_IMAGE -> context?.getString(R.string.ai_buy_picture)
			VIEW_IMAGE_TEXT -> context?.getString(R.string.ai_buy_text_pictures)
			else -> ""
		}

		val expiresAt = when (position) {
			VIEW_TEXT -> (subsInfo?.textExpirationDate?.times(1000L))?.toFormattedDate()
			VIEW_IMAGE -> (subsInfo?.imgExpirationDate?.times(1000L))?.toFormattedDate()
			VIEW_IMAGE_TEXT -> (subsInfo?.doubleExpirationDate?.times(1000L))?.toFormattedDate()
			else -> ""
		}

		return Pair(subscriptionText, expiresAt)
	}

	private fun initProgressBar() {
		loaderAnimationView = context?.let { RLottieImageView(it) }
		binding?.progressContainer?.addView(loaderAnimationView)

		loaderAnimationView?.setAutoRepeat(true)
		loaderAnimationView?.setAnimation(R.raw.ello_loader, 112, 112)
	}

	private fun showProgress(show: Boolean) {
		binding?.progressContainer?.visibility = if (show) View.VISIBLE else View.GONE
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
						putInt(SubscriptionResultFragment.IMAGE_RES_ID, R.drawable.panda_payment_success)
						putString(SubscriptionResultFragment.TITLE, context?.getString(R.string.success_excl))

						if (isSubscription) {
							when (aiPurchasePosition) {
								VIEW_TEXT -> putString(SubscriptionResultFragment.DESCRIPTION, context?.getString(R.string.unlimited_ai_pack_purchase_message))
								VIEW_IMAGE -> putString(SubscriptionResultFragment.DESCRIPTION, context?.getString(R.string.unlimited_ai_pack_image_purchase_message))
								VIEW_IMAGE_TEXT -> putString(SubscriptionResultFragment.DESCRIPTION, context?.getString(R.string.unlimited_ai_pack_mixed_purchase_message))
							}
						}
						else {
							if (selectedPlanTab == PLAN_BUY) {
								if (aiPurchasePosition == VIEW_IMAGE_TEXT) {
									putString(SubscriptionResultFragment.DESCRIPTION, context?.getString(R.string.ai_buy_success_image_text_sub_title))
								}
								else {
									putString(SubscriptionResultFragment.DESCRIPTION, context?.getString(R.string.ai_buy_success_sub_title, if (aiPurchasePosition == VIEW_TEXT) priceInfo?.textDefaultQuantity else priceInfo?.imageDefaultQuantity))
								}
							}
							else {
								putString(SubscriptionResultFragment.DESCRIPTION, context?.getString(R.string.ai_subscription_success))
							}
						}
					}))
				}
			}

			NotificationCenter.aiSubscriptionError -> {
				var drawableResId = R.drawable.panda_payment_error
				val originalError = (args[0] as? TLError)?.text
				var errorText = originalError?.capitalizeFirstLetter() ?: context?.getString(R.string.ai_subscription_failure)
				var showTopup = false

				if (originalError?.contains("enough") == true && originalError.contains("money")) {
					errorText = context?.getString(R.string.subscription_fail)
					drawableResId = R.drawable.panda_payment_error
					showTopup = true
				}

				showProgress(false)

				presentFragment(SubscriptionResultFragment(Bundle().apply {
					putBoolean(SubscriptionResultFragment.SUCCESS, false)
					putInt(SubscriptionResultFragment.IMAGE_RES_ID, drawableResId)
					putString(SubscriptionResultFragment.TITLE, context?.getString(R.string.sorry))
					putString(SubscriptionResultFragment.DESCRIPTION, errorText)
					putBoolean(SubscriptionResultFragment.SHOW_TOPUP, showTopup)
				}))
			}
		}
	}

	private fun createBuyAlert() {
		val context = context ?: return
		val builder = BottomSheet.Builder(context)
		builder.setApplyTopPadding(true)

		val bindingPopup = AiBuyPopupBinding.inflate(LayoutInflater.from(context))

		when (aiPurchasePosition) {
			VIEW_IMAGE -> {
				bindingPopup.image1.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ai_buy_picture, null))
				bindingPopup.image1Fg.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ai_buy_type_picture, null))

				if (isSubscription) {
					bindingPopup.packAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.resources, R.drawable.unlimited_icon, null), null, null, null)
					bindingPopup.priceTag.text = context.getString(R.string.simple_coin_format_for_month, priceInfo?.imageSubscriptionPrice).fillElloCoinLogos(size = 22f, tintColor = context.getColor(R.color.white_day_night), isIconBold = true)
					bindingPopup.packUnit.text = context.getString(R.string.unlimited_prompts)
				}
				else {
					bindingPopup.packAmount.text = "${priceInfo?.imageDefaultQuantity}"
					bindingPopup.priceTag.text = context.getString(R.string.simple_coin_format, priceInfo?.imagePackPrice).fillElloCoinLogos(size = 22f, tintColor = context.getColor(R.color.white_day_night), isIconBold = true)
					bindingPopup.packUnit.text = context.getString(R.string.image_prompts)
				}
			}

			VIEW_TEXT -> {
				if (isSubscription) {
					bindingPopup.packAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.resources, R.drawable.unlimited_icon, null), null, null, null)
					bindingPopup.priceTag.text = context.getString(R.string.simple_coin_format_for_month, priceInfo?.textSubscriptionPrice).fillElloCoinLogos(size = 22f, tintColor = context.getColor(R.color.white_day_night), isIconBold = true)
					bindingPopup.packUnit.text = context.getString(R.string.unlimited_prompts)
				}
				else {
					bindingPopup.packAmount.text = "${priceInfo?.textDefaultQuantity}"
					bindingPopup.priceTag.text = context.getString(R.string.simple_coin_format, priceInfo?.textPackPrice).fillElloCoinLogos(size = 22f, tintColor = context.getColor(R.color.white_day_night), isIconBold = true)
					bindingPopup.packUnit.text = context.getString(R.string.text_prompts)
				}
			}

			else -> {
				bindingPopup.imageLayout.gone()
				bindingPopup.imageTextLayout.visible()

				bindingPopup.itemToBuy.gone()
				bindingPopup.itemToTextImagesBuy.visible()

				if (isSubscription) {
					bindingPopup.imageTextPriceTag.text = context.getString(R.string.simple_coin_format_for_month, priceInfo?.doubleSubscriptionPrice).fillElloCoinLogos(size = 22f, tintColor = context.getColor(R.color.white_day_night), isIconBold = true)
					bindingPopup.textPackAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.resources, R.drawable.unlimited_icon, null), null, null, null)
					bindingPopup.imagePackAmount.setCompoundDrawablesRelativeWithIntrinsicBounds(ResourcesCompat.getDrawable(context.resources, R.drawable.unlimited_icon, null), null, null, null)

					bindingPopup.textDoubleAiChat.text = context.getString(R.string.unlimited_text_prompts)
					bindingPopup.textDoubleAiImages.text = context.getString(R.string.unlimited_image_prompts)
				}
				else {
					bindingPopup.textPackAmount.text = "${priceInfo?.doublePackTextQuantity}"
					bindingPopup.imagePackAmount.text = "${priceInfo?.doublePackImageQuantity}"
					bindingPopup.imageTextPriceTag.text = context.getString(R.string.simple_coin_format, priceInfo?.doublePackPrice).fillElloCoinLogos(size = 22f, tintColor = context.getColor(R.color.white_day_night), isIconBold = true)
				}
			}
		}

		builder.customView = bindingPopup.root

		val alert = builder.create()

		bindingPopup.close.setOnClickListener {
			alert.dismiss()
		}

		bindingPopup.buyButton.setOnClickListener {
			showProgress(true)

			chatBotController.buyAiItem(isSubscription, aiPurchasePosition)

			alert.dismiss()
		}

		showDialog(alert)
	}

	private fun createSubsActiveAlert() {
		val subsInfo = chatBotController.lastSubscriptionInfo

		val context = context ?: return
		val builder = BottomSheet.Builder(context)
		builder.setApplyTopPadding(true)

		val bindingPopup = AiSubsActivePopupBinding.inflate(LayoutInflater.from(context))

		when (aiPurchasePosition) {
			VIEW_IMAGE -> {
				bindingPopup.image1.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ai_buy_picture, null))
				bindingPopup.image1Fg.setImageDrawable(ResourcesCompat.getDrawable(context.resources, R.drawable.ai_buy_type_picture, null))
				bindingPopup.subscriptionStatusText.text = context.getString(R.string.plans_subscription_status, context.getString(R.string.ai_buy_picture))
				bindingPopup.expiresUntilText.text = context.getString(R.string.plans_expires_until, (subsInfo?.imgExpirationDate?.times(1000L))?.toFormattedDate())
			}

			VIEW_TEXT -> {
				bindingPopup.subscriptionStatusText.text = context.getString(R.string.plans_subscription_status, context.getString(R.string.ai_buy_text))
				bindingPopup.expiresUntilText.text = context.getString(R.string.plans_expires_until, (subsInfo?.textExpirationDate?.times(1000L))?.toFormattedDate())
			}

			else -> {
				bindingPopup.imageLayout.gone()
				bindingPopup.imageTextLayout.visible()
				bindingPopup.subscriptionStatusText.text = context.getString(R.string.plans_subscription_status, context.getString(R.string.ai_buy_text_pictures))
				bindingPopup.expiresUntilText.text = context.getString(R.string.plans_expires_until, (subsInfo?.doubleExpirationDate?.times(1000L))?.toFormattedDate())
			}
		}

		builder.customView = bindingPopup.root

		val alert = builder.create()

		bindingPopup.close.setOnClickListener {
			alert.dismiss()
		}

		bindingPopup.buyButton.setOnClickListener {
			alert.dismiss()
		}

		showDialog(alert)
	}

	override fun onFragmentDestroy() {
		binding = null

		notificationCenter.let {
			it.removeObserver(this, NotificationCenter.aiSubscriptionSuccess)
			it.removeObserver(this, NotificationCenter.aiSubscriptionError)
		}

		super.onFragmentDestroy()
	}

	companion object {
		// private const val PLAN_SUBSCRIPTION = 0
		private const val PLAN_BUY = 1
		private const val VIEW_TEXT = 0
		private const val VIEW_IMAGE = 1
		private const val VIEW_IMAGE_TEXT = 2
	}
}
