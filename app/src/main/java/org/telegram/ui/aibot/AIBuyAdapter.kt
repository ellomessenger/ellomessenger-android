/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhaylo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023-2024.
 */
package org.telegram.ui.aibot

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.telegram.messenger.R
import org.telegram.messenger.databinding.AiSubscriptionPlanViewHolderBinding
import org.telegram.messenger.utils.fillElloCoinLogos
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.visible

class AIBuyAdapter(private val isBuyPage: Boolean) : RecyclerView.Adapter<AiBuyViewHolder>() {
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = AiBuyViewHolder(AiSubscriptionPlanViewHolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

	@SuppressLint("SetTextI18n")
	override fun onBindViewHolder(holder: AiBuyViewHolder, position: Int) {
		val binding = AiSubscriptionPlanViewHolderBinding.bind(holder.itemView)
		val context = holder.itemView.context

		when (position) {
			2 -> {
				binding.typeTextImage.visible()

				binding.background.setImageResource(R.drawable.ai_buy_text_pictures)
				binding.typeImage.setImageResource(R.drawable.ai_buy_type_picture)
				binding.typeTextImage.setImageResource(R.drawable.ai_buy_type_text)
				binding.title.text = context.getString(R.string.ai_buy_text_pictures)

				if (isBuyPage) {
					binding.periodLabel.gone()
					binding.title.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.common_size_20dp))
					binding.description.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.common_size_16dp))
					binding.description.setText(R.string.ai_buy_description)
					binding.monthPrice.text = context.getString(R.string.ai_buy_text_pictures_request_item, AiSubscriptionPlansFragment.DEFAULT_TEXT_PRICE).fillElloCoinLogos(size = 18f, tintColor = context.getColor(R.color.dark_fixed), isIconBold = true)
					binding.monthPrice.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.common_size_18dp))
				}
				else {
					binding.periodLabel.text = "/" + context.getString(R.string.ai_prompts_per_month_format, 1000)
					binding.periodLabel.visible()
					binding.description.setText(R.string.ai_monthly_subs_description)
					binding.monthPrice.text = context.getString(R.string.ai_buy_price_no_frac, AiSubscriptionPlansFragment.DEFAULT_TEXT_SUB_PRICE)
				}
			}

			1 -> {
				binding.background.setImageResource(R.drawable.ai_buy_picture)
				binding.typeImage.setImageResource(R.drawable.ai_buy_type_picture)
				binding.title.text = context.getString(R.string.ai_buy_picture)

				if (isBuyPage) {
					binding.periodLabel.gone()
					binding.title.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.common_size_20dp))
					binding.description.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.common_size_16dp))
					binding.description.setText(R.string.ai_buy_description)
					binding.monthPrice.text = context.getString(R.string.ai_buy_request_item, AiSubscriptionPlansFragment.DEFAULT_PAID_IMAGE_PROMPTS, AiSubscriptionPlansFragment.DEFAULT_IMAGE_PRICE).fillElloCoinLogos(size = 24f, tintColor = context.getColor(R.color.dark_fixed), isIconBold = true)
					binding.monthPrice.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.common_size_28dp))
				}
				else {
					binding.periodLabel.text = "/" + context.getString(R.string.ai_prompts_per_month_format, 400)
					binding.periodLabel.visible()
					binding.description.setText(R.string.ai_monthly_subs_description)
					binding.monthPrice.text = context.getString(R.string.ai_buy_price_no_frac, AiSubscriptionPlansFragment.DEFAULT_IMAGE_SUB_PRICE)
				}
			}

			0 -> {
				binding.background.setImageResource(R.drawable.ai_buy_text)
				binding.typeImage.setImageResource(R.drawable.ai_buy_type_text)

				if (isBuyPage) {
					binding.periodLabel.gone()
					binding.title.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.common_size_20dp))
					binding.description.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.common_size_16dp))
					binding.description.setText(R.string.ai_buy_description)
					binding.monthPrice.text = context.getString(R.string.ai_buy_request_item, AiSubscriptionPlansFragment.DEFAULT_PAID_TEXT_PROMPTS, AiSubscriptionPlansFragment.DEFAULT_TEXT_PRICE).fillElloCoinLogos(size = 24f, tintColor = context.getColor(R.color.dark_fixed), isIconBold = true)
					binding.monthPrice.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.common_size_28dp))
				}
				else {
					binding.periodLabel.text = "/" + context.getString(R.string.ai_prompts_per_month_format, 1000)
					binding.periodLabel.visible()
					binding.description.setText(R.string.ai_monthly_subs_description)
					binding.monthPrice.text = context.getString(R.string.ai_buy_price_no_frac, AiSubscriptionPlansFragment.DEFAULT_TEXT_SUB_PRICE)
				}
			}
		}
	}

	override fun getItemCount() = 3
}
