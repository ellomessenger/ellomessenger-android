/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024-2025.
 * Copyright Nikita Denin, Ello 2025.
 */
package org.telegram.ui.Components

import android.annotation.SuppressLint
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.beint.elloapp.allCornersProvider
import org.telegram.messenger.R
import org.telegram.messenger.databinding.DialogsEmptyItemBinding
import org.telegram.messenger.utils.dp
import org.telegram.messenger.utils.gone
import org.telegram.ui.ActionBar.Theme

class DialogsEmptyView(context: Context) : LinearLayout(context) {
	private val dialogsEmptyAdapter by lazy { DialogsEmptyAdapter() }

	private val viewPager by lazy {
		ViewPager2(context).apply {
			layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

			adapter = dialogsEmptyAdapter

			offscreenPageLimit = 3

			setPageTransformer { page, position ->
				page.scaleY = 1f
				page.alpha = 1f

				if (position < -1) {
					page.translationX = -position
				}
				else if (position <= 1) {
					page.translationX = -position
				}
				else {
					page.translationX = -position
				}
			}
		}
	}

	init {
		orientation = VERTICAL

		val header = TextView(context)
		header.text = context.getString(R.string.meet_ello)
		header.typeface = Theme.TYPEFACE_BOLD
		header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
		header.gravity = Gravity.CENTER_HORIZONTAL
		header.setTextColor(ResourcesCompat.getColor(context.resources, R.color.text, null))
		header.setLineSpacing(0.0f, 0.9f)

		header.gone()

		addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).also {
			it.marginStart = 16.dp
			it.marginEnd = 16.dp
			it.topMargin = 2.dp
		})

		addView(viewPager)
	}

	fun setItems(items: List<CardItem>) {
		dialogsEmptyAdapter.updateItems(items)
	}

	fun setOnCardItemClickListener(listener: (CardItem) -> Unit) {
		dialogsEmptyAdapter.setItemClickListener(listener)
	}

	private class DialogsEmptyAdapter : RecyclerView.Adapter<DialogsEmptyAdapter.ViewHolder>() {
		private val items = mutableListOf<CardItem>()
		private var itemClickListener: ((CardItem) -> Unit)? = null

		class ViewHolder(private val binding: DialogsEmptyItemBinding) : RecyclerView.ViewHolder(binding.root) {
			init {
				binding.logoContainer.clipToOutline = true
				binding.logoContainer.outlineProvider = allCornersProvider(10.dp.toFloat())
			}

			fun bind(cardItem: CardItem, listener: ((CardItem) -> Unit)?) {
				binding.title.text = cardItem.title
				binding.logo.setImageResource(cardItem.drawableRes)

				val text = cardItem.description
				val spannableStringBuilder = SpannableStringBuilder()

				text.split("\n").forEach { line ->
					if (line.startsWith("• ")) {
						spannableStringBuilder.append(line.removePrefix("• "), BulletSpan(8.dp, binding.description.currentTextColor, 2.dp).also { it.verticalOffset = (-2).dp }, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
					}
					else {
						spannableStringBuilder.append(line)
					}

					spannableStringBuilder.append("\n")
				}

				binding.description.text = spannableStringBuilder

				binding.logoContainer.setOnClickListener {
					listener?.invoke(cardItem)
				}

				binding.description.setOnClickListener {
					listener?.invoke(cardItem)
				}
			}
		}

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val inflater = DialogsEmptyItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
			return ViewHolder(inflater)
		}

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val cardItems = items[position]
			holder.bind(cardItems, itemClickListener)
		}

		override fun getItemCount(): Int = items.size

		@SuppressLint("NotifyDataSetChanged")
		fun updateItems(newItems: List<CardItem>) {
			items.clear()
			items.addAll(newItems)

			notifyDataSetChanged()
		}

		fun setItemClickListener(listener: (CardItem) -> Unit) {
			this.itemClickListener = listener
		}
	}
}

data class CardItem(val id: Int, val title: String, val description: String, val drawableRes: Int)
