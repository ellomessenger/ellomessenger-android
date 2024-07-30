/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhaylo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2023.
 */
package org.telegram.ui.Components

import android.content.Context
import android.view.LayoutInflater
import org.telegram.messenger.databinding.AiChatbotStatusBinding
import org.telegram.ui.ActionBar.BottomSheet

class AiChatbotStatusBottomSheet(context: Context) : BottomSheet(context, false) {
	init {
		val binding = AiChatbotStatusBinding.inflate(LayoutInflater.from(context))

		setCustomView(binding.root)

		setApplyBottomPadding(false)
		setApplyTopPadding(false)

		isFullscreen = false
		fullWidth = true

		setCanDismissWithSwipe(false)
		setCanceledOnTouchOutside(false)
	}
}
