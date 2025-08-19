package org.telegram.ui.Components

import android.net.Uri
import android.text.TextPaint
import android.text.style.URLSpan
import android.view.View
import org.telegram.messenger.browser.Browser
import org.telegram.ui.Components.TextStyleSpan.TextStyleRun

class URLSpanNoUnderlineBrowser @JvmOverloads constructor(url: String?, val style: TextStyleRun? = null) : URLSpan(url?.replace('\u202E', ' ') ?: url) {

	override fun onClick(widget: View) {
		val uri = Uri.parse(url)
		Browser.openUrl(widget.context, uri)
	}

	override fun updateDrawState(p: TextPaint) {
		super.updateDrawState(p)

		style?.applyStyle(p)

		p.isUnderlineText = false
	}

}
