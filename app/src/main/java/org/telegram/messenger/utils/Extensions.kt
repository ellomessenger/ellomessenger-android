/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Shamil Afandiyev, Ello 2024.
 * Copyright Nikita Denin, Ello 2022-2025.
 */
package org.telegram.messenger.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.SpannedString
import android.text.style.ImageSpan
import android.text.style.URLSpan
import android.util.Base64
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.text.buildSpannedString
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.DateTimeFormat
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.ChatObject
import org.telegram.messenger.Emoji
import org.telegram.messenger.FileLog
import org.telegram.messenger.MessagesController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.messenger.Utilities
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.ConnectionsManager.ResolveHostByNameSync
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.Message
import org.telegram.tgnet.TLRPC.User
import org.telegram.tgnet.action
import org.telegram.tgnet.channelId
import org.telegram.tgnet.chatId
import org.telegram.tgnet.userId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.absoluteValue

const val collapsedBioLength = 40
val decimalFormat = DecimalFormat("###,###", DecimalFormatSymbols().apply { groupingSeparator = ' ' })

fun View.gone() {
	visibility = View.GONE
}

fun View.visible() {
	visibility = View.VISIBLE
}

fun View.invisible() {
	visibility = View.INVISIBLE
}

val Float.dp: Float
	get() = AndroidUtilities.dp(this).toFloat()

val Int.dp: Int
	get() = AndroidUtilities.dp(this.toFloat())

fun String.validateEmail(): Boolean {
	val emailPatter = Pattern.compile("[a-zA-Z\\d+._%\\-]{1,64}" + "@" + "[a-zA-Z\\d][a-zA-Z\\d\\-]{1,255}" + "(" + "\\." + "[a-zA-Z\\d][a-zA-Z\\d\\-]{0,25}" + ")+")
	return emailPatter.matcher(this).matches()
}

fun String.validatePassword(): Boolean {
	val lengthValid = this.length >= 6
	val digitValid = this.any { it.isDigit() }
	val letterValid = this.any { it.isLetter() }
	val capitalValid = this.any { it.isUpperCase() }
	val lowercaseValid = this.any { it.isLowerCase() }
	val latinOnly = this.all { (33..126).contains(it.code) }

	return lengthValid && digitValid && letterValid && capitalValid && lowercaseValid && latinOnly
}

fun String.parseDate(): Date? = runCatching {
	DateTime(this).toDate()
}.onFailure {
	FileLog.e("Failed to parse date $this: $it")
}.getOrNull()

fun Date.formatDate(withTimezone: Boolean = true): String? = runCatching {
	val pattern = if (withTimezone) "yyyy-MM-dd'T'HH:mm:ssZ" else "yyyy-MM-dd'T'HH:mm:ss"
	DateTime(this).withZone(DateTimeZone.UTC).toString(pattern)
}.onFailure {
	FileLog.e("Failed to parse date $this: $it")
}.getOrNull()

fun Date.formatBirthday(): String? = runCatching {
	DateTime(this).toString(DateTimeFormat.shortDate())
}.onFailure {
	FileLog.e("Failed to parse date $this: $it")
}.getOrNull()

fun String.parseBirthday(): Date? = runCatching {
	DateTimeFormat.shortDate().parseDateTime(this).withZone(DateTimeZone.UTC).toDate()
}.onFailure {
	FileLog.e("Failed to parse date $this: $it")
}.getOrNull()

fun Date.lastActiveDateToString(): String {
	return toDisplayString()
}

private val isoTimeStringFormatter = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)
private val isoDateStringFormatter = SimpleDateFormat.getDateInstance()
private val longDateFormatter = SimpleDateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)

fun Date.toLongDateString(): String {
	return longDateFormatter.format(this)
}

fun Date.toDateOnlyString(): String {
	return isoDateStringFormatter.format(this)
}

private fun sameDays(date1: Date, date2: Date): Boolean {
	val cal1 = Calendar.getInstance()
	val cal2 = Calendar.getInstance()
	cal1.time = date1
	cal2.time = date2
	return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun Date.toDisplayString(): String {
	val now = Date()

	return if (sameDays(now, this)) {
		isoTimeStringFormatter.format(this)
	}
	else {
		isoDateStringFormatter.format(this)
	}
}

fun <T : RecyclerView.ViewHolder> RecyclerView.Adapter<T>.reload(startingPosition: Int, currentItemsCount: Int, previousItemsCount: Int) {
	val diff = previousItemsCount - currentItemsCount

	when {
		diff < 0 -> { // more items than before
			notifyItemRangeInserted(startingPosition + previousItemsCount, diff.absoluteValue)
			notifyItemRangeChanged(startingPosition, previousItemsCount)
		}

		diff > 0 -> { // fewer items than before
			notifyItemRangeRemoved(startingPosition + previousItemsCount, diff)
			notifyItemRangeChanged(startingPosition, currentItemsCount)
		}

		else -> {
			notifyItemRangeChanged(startingPosition, currentItemsCount - startingPosition)
		}
	}
}

fun Int.largeAmountToString(context: Context): String {
	val billions = this / 1000000000.0

	if (billions > 0) {
		return String.format(Locale.getDefault(), "%.1f", billions) + context.getString(R.string.billions)
	}

	val millions = this / 1000000.0

	if (millions > 0) {
		return String.format(Locale.getDefault(), "%.1f", millions) + context.getString(R.string.millions)
	}

	val thousands = this / 1000.0

	if (thousands > 0) {
		return String.format(Locale.getDefault(), "%.1f", thousands) + context.getString(R.string.thousands)
	}

	return this.toString()
}

/**
 * Vibrates the device for the specified duration.
 * @param duration The duration of the vibration in milliseconds.
 */
@Suppress("DEPRECATION")
fun Context.vibrate(duration: Long = 200) {
	val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
		(getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
	}
	else {
		getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
	}

	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
		vibrator?.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
	}
	else {
		vibrator?.vibrate(duration)
	}
}

fun Message.getChannel(): TLRPC.TLChannel? {
	val channelId = peerId.channelId

	if (channelId == 0L) {
		return null
	}

	return MessagesController.getInstance(UserConfig.selectedAccount).getChat(channelId) as? TLRPC.TLChannel
}

fun Message.getChat(): TLRPC.Chat? {
	val channelId = peerId.channelId

	if (channelId != 0L) {
		return MessagesController.getInstance(UserConfig.selectedAccount).getChat(channelId)
	}

	val chatId = peerId.chatId

	if (chatId != 0L) {
		return MessagesController.getInstance(UserConfig.selectedAccount).getChat(chatId)
	}

	return null
}

fun Message.getUser(): User? {
	val userId = peerId?.userId

	if (userId != 0L) {
		return MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId)
	}

	return null
}

@DrawableRes
fun Resources.getDrawableResourceByName(name: String): Int {
	return try {
		val resId = getIdentifier(name, "drawable", ApplicationLoader.applicationContext.packageName)
		if (resId != 0) resId else -1
	}
	catch (e: Exception) {
		FileLog.e(e)
		-1
	}
}

/**
 * Returns the raw value of a dimension resource, without applying the current density.
 * @param id The resource identifier of the dimension to retrieve.
 */
fun Resources.getDimensionRaw(@DimenRes id: Int): Float {
	return (getDimension(id) / displayMetrics.density)
}

val gson = Gson()

inline fun <reified T> String.fromJson(): T? {
	return try {
		gson.fromJson(this, T::class.java)
	}
	catch (e: Exception) {
		FileLog.e(e)
		null
	}
}

inline fun <reified T> T.toJson(): String? {
	return try {
		gson.toJson(this)
	}
	catch (e: Exception) {
		FileLog.e(e)
		null
	}
}

// https://stackoverflow.com/a/51768312/318460
fun View.addRipple(foreground: Boolean = false) = with(TypedValue()) {
	context.theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)

	if (foreground) {
		setForeground(ResourcesCompat.getDrawable(resources, resourceId, context.theme))
	}
	else {
		setBackgroundResource(resourceId)
	}
}

// https://stackoverflow.com/a/51768312/318460
fun View.addCircleRipple(foreground: Boolean = false) = with(TypedValue()) {
	context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, this, true)

	if (foreground) {
		setForeground(ResourcesCompat.getDrawable(resources, resourceId, context.theme))
	}
	else {
		setBackgroundResource(resourceId)
	}
}

fun View.setUserInteractionsEnabled(enabled: Boolean) {
	isEnabled = enabled

	if (this is ViewGroup) {
		children.forEach {
			it.setUserInteractionsEnabled(enabled)
		}
	}
}

fun String.getHostByNameSync(): String {
	return runBlocking(context = Dispatchers.IO) {
		suspendCoroutine { continuation ->
			val resolvedDomain = ConnectionsManager.dnsCache[this@getHostByNameSync]

			if (resolvedDomain != null && SystemClock.elapsedRealtime() - resolvedDomain.ttl < 5 * 60 * 1000) {
				continuation.resume(resolvedDomain.address)
				return@suspendCoroutine
			}
			else {
				val task = ResolveHostByNameSync(this@getHostByNameSync)
				val result = task.resolve()
				continuation.resume(result?.address ?: "")
			}
		}
	}
}

@SuppressLint("RestrictedApi")
fun Context.showMenu(v: View, @MenuRes menuRes: Int): PopupMenu {
	val popUp = PopupMenu(this, v)
	popUp.menuInflater.inflate(menuRes, popUp.menu)

	if (popUp.menu is MenuBuilder) {
		val menuBuilder = popUp.menu as MenuBuilder
		menuBuilder.setOptionalIconsVisible(true)

		for (item in menuBuilder.visibleItems) {
			val iconMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16.toFloat(), resources.displayMetrics).toInt()

			if (item.icon != null) {
				item.icon = InsetDrawable(item.icon, iconMarginPx, 0, iconMarginPx, 0)
				item.icon?.setTint(ResourcesCompat.getColor(resources, R.color.dark, null))
			}
		}
	}

	popUp.show()

	return popUp
}

private val stringLongHashDigest = MessageDigest.getInstance("SHA-512")

fun String.toLongHash(): Long {
	val hashBytes = stringLongHashDigest.digest(this.toByteArray(StandardCharsets.UTF_8))
	var hashLong: Long = 0

	for (i in hashBytes.indices) {
		hashLong = hashLong shl 8
		hashLong = hashLong or (hashBytes[i].toLong() and 0xFF)
	}

	return hashLong
}

private const val TRANSFORMATION = "AES/CBC/PKCS5PADDING"
private const val KEY_LENGTH = 16

fun String.encrypt(): String? {
	return runCatching {
		val secretKeySpec = SecretKeySpec(getEncryptionKey(), "AES")
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
		val encryptedBytes = cipher.doFinal(toByteArray())
		Base64.encodeToString(encryptedBytes, Base64.NO_WRAP or Base64.NO_PADDING)
	}.getOrNull()
}

fun String.decrypt(): String? {
	return runCatching {
		val secretKeySpec = SecretKeySpec(getEncryptionKey(), "AES")
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
		val encryptedBytes = Base64.decode(this, Base64.NO_WRAP or Base64.NO_PADDING)
		val decryptedBytes = cipher.doFinal(encryptedBytes)
		String(decryptedBytes)
	}.getOrNull()
}

private fun getEncryptionKey(): ByteArray {
	val userId = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId
	val mergedParts = userId.toString() + BuildConfig.APPLICATION_ID
	val sha1 = Utilities.computeSHA1(mergedParts.toByteArray(StandardCharsets.UTF_8))
	return (sha1!!).copyOfRange(0, KEY_LENGTH)
}

val Context.currentLocale: Locale
	get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
		resources.configuration.locales.get(0)
	}
	else {
		@Suppress("DEPRECATION") resources.configuration.locale
	}

fun String.capitalizeFirstLetter(): String {
	return replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

fun getResId(resName: String, c: Class<*>): Int {
	return try {
		val idField = c.getDeclaredField(resName)
		idField.getInt(idField)
	}
	catch (e: Exception) {
		-1
	}
}

fun combineDrawables(height: Int, drawables: List<Drawable>, rightMargin: Int = 4): Drawable {
	val scaledSizes = drawables.map { drawable ->
		val ratio = height.toFloat() / drawable.intrinsicHeight.toFloat()
		val w = (drawable.intrinsicWidth * ratio).toInt()
		val h = (drawable.intrinsicHeight * ratio).toInt()
		w to h
	}

	val layerDrawable = LayerDrawable(drawables.toTypedArray())
	var left = 0
	var spacing = 0.dp
	val newSpacing = ((height * 4.dp) / 16.dp)

	drawables.forEachIndexed { index, drawable ->
		drawable.setBounds(0, 0, scaledSizes[index].first, scaledSizes[index].second)

		layerDrawable.setLayerSize(index, scaledSizes[index].first, scaledSizes[index].second)
		layerDrawable.setLayerInset(index, left + spacing, (height - scaledSizes[index].second) / 2, rightMargin, 0)

		spacing = newSpacing

		left += scaledSizes[index].first
	}

	return layerDrawable
}

@JvmOverloads
fun createCombinedChatPropertiesDrawable(chat: TLRPC.Chat?, context: Context, height: Float = 16f): Drawable? {
	val drawAdult = chat?.adult == true
	val drawCourse = ChatObject.isMasterclass(chat)
	val drawPaid = ChatObject.isSubscriptionChannel(chat) || ChatObject.isPaidChannel(chat)
	val drawPrivate = chat != null && chat.username.isNullOrEmpty()

	val drawables = mutableListOf<Drawable>()

	if (drawPrivate) {
		drawables.add(ResourcesCompat.getDrawable(context.resources, R.drawable.lock_ello, null)!!.mutate())
	}

	if (drawAdult) {
		drawables.add(ResourcesCompat.getDrawable(context.resources, R.drawable.adult_channel_icon, null)!!.mutate())
	}

	if (drawCourse) {
		drawables.add(ResourcesCompat.getDrawable(context.resources, R.drawable.online_course, null)!!.mutate())
	}
	else if (drawPaid) {
		drawables.add(ResourcesCompat.getDrawable(context.resources, R.drawable.ic_paid_channel, null)!!.mutate())
	}

	var leftIcon: Drawable? = null

	if (drawables.isNotEmpty()) {
		leftIcon = combineDrawables(AndroidUtilities.dp(height), drawables)
	}

	return leftIcon
}

fun String.processForLinks(context: Context, parseLinks: Boolean, clickListener: LinkClickListener?): SpannableStringBuilder {
	val stringBuilder = SpannableStringBuilder(this)
	MessageObject.addLinks(false, stringBuilder, botCommands = false, check = false, internalOnly = !parseLinks)
	Emoji.replaceEmoji(stringBuilder, null, false)

	val urls = stringBuilder.getSpans(0, stringBuilder.length, URLSpan::class.java)

	for (url in urls) {
		makeLinkClickable(stringBuilder, url, context, clickListener)
	}

	return stringBuilder
}

fun String.fillElloCoinLogos(topIconPadding: Int = 0, size: Float = 12f, tintColor: Int = ApplicationLoader.applicationContext.getColor(R.color.dark), isIconBold: Boolean = false): SpannedString {
	return buildSpannedString {
		append(this@fillElloCoinLogos)

		val drawable = if (isIconBold) {
			ResourcesCompat.getDrawable(ApplicationLoader.applicationContext.resources, R.drawable.bold_ello_dollar_wallet_logo, null)!!.mutate()
		}
		else {
			ResourcesCompat.getDrawable(ApplicationLoader.applicationContext.resources, R.drawable.ello_dollar_wallet_logo, null)!!.mutate()
		}

		val bitmapDrawable = drawable.toBitmapDrawable(AndroidUtilities.dp(size), AndroidUtilities.dp(size))
		val padding = AndroidUtilities.dp(1f)
		val paddedBitmap = addPaddingToBitmap(bitmapDrawable.bitmap, padding)

		val paddedDrawable = paddedBitmap.toDrawable(ApplicationLoader.applicationContext.resources)
		paddedDrawable.colorFilter = PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
		paddedDrawable.setBounds(0, topIconPadding, paddedBitmap.width, paddedBitmap.height)

		val span = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ImageSpan(paddedDrawable, ImageSpan.ALIGN_CENTER)
		}
		else {
			ImageSpan(paddedDrawable, ImageSpan.ALIGN_BASELINE)
		}

		val placeholder = "$\$coin$$"

		this.setSpan(span, this@fillElloCoinLogos.indexOf(placeholder), this@fillElloCoinLogos.indexOf(placeholder) + placeholder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
	}
}

fun Drawable.toBitmapDrawable(width: Int = this.intrinsicWidth, height: Int = this.intrinsicHeight): BitmapDrawable {
	val bitmap = createBitmap(width, height)
	val canvas = Canvas(bitmap)
	this.setBounds(0, 0, canvas.width, canvas.height)
	this.draw(canvas)
	return bitmap.toDrawable(ApplicationLoader.applicationContext.resources)
}

fun addPaddingToBitmap(bitmap: Bitmap, padding: Int): Bitmap {
	val paddedBitmap = createBitmap(bitmap.width + 2 * padding, bitmap.height + 2 * padding)
	val canvas = Canvas(paddedBitmap)
	canvas.drawBitmap(bitmap, padding.toFloat(), padding.toFloat(), null)
	return paddedBitmap
}

fun TextInputLayout.setError(context: Context?, errorText: Int?) {
	val colorStateList = context?.let { ContextCompat.getColorStateList(it, R.color.purple) }

	boxStrokeErrorColor = colorStateList
	setErrorTextColor(colorStateList)
	error = errorText?.let { context?.getString(it) }

	requestFocus()
}

fun TextInputLayout.resetError() {
	isErrorEnabled = false
	error = null
}

fun getImageDimensions(imagePath: String): Pair<Int, Int> {
	return runCatching {
		val options = BitmapFactory.Options()
		options.inJustDecodeBounds = true
		BitmapFactory.decodeFile(imagePath, options)
		Pair(options.outWidth, options.outHeight)
	}.getOrNull() ?: Pair(0, 0)
}

fun <T> MutableList<T>.removeDuplicates(lock: Any) {
	synchronized(lock) {
		val setItems: Set<T> = LinkedHashSet(this)
		clear()
		addAll(setItems)
	}
}

fun String.isYouTubeShortsLink(): Boolean {
	return contains("youtube.com") && contains("/shorts/")
}

fun List<MessageObject>.hasServiceMessagesOnly(): Boolean {
	return none { it.messageOwner?.action == null }
}

fun ByteArray.toHexString(): String {
	val result = StringBuilder(this.size * 2)

	for (i in this.indices) {
		result.append(Character.forDigit((this[i].toInt() shr 4) and 0xF, 16)).append(Character.forDigit(this[i].toInt() and 0xF, 16))
	}

	return result.toString()
}

fun Int?.formatCount(): String {
	return when {
		this == null -> "0"
		this >= 1000000 -> "${this / 1000000}m"
		this >= 1000 -> "${this / 1000}k"
		else -> this.toString()
	}
}
