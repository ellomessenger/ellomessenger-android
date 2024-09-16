/*
 * This is the source code of Ello for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Mykhailo Mykytyn, Ello 2023.
 * Copyright Nikita Denin, Ello 2022-2024.
 * Copyright Shamil Afandiyev, Ello 2024.
 */
package org.telegram.ui

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.updateLayoutParams
import androidx.core.widget.addTextChangedListener
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.FileLoader
import org.telegram.messenger.ImageLoader
import org.telegram.messenger.ImageLocation
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.R
import org.telegram.messenger.databinding.FragmentEditProfileBinding
import org.telegram.messenger.utils.CheckUsername
import org.telegram.messenger.utils.UsernameFilter
import org.telegram.messenger.utils.formatBirthday
import org.telegram.messenger.utils.formatDate
import org.telegram.messenger.utils.gone
import org.telegram.messenger.utils.parseBirthday
import org.telegram.messenger.utils.setUserInteractionsEnabled
import org.telegram.messenger.utils.vibrate
import org.telegram.messenger.utils.visible
import org.telegram.tgnet.ConnectionsManager
import org.telegram.tgnet.CountriesDataSource
import org.telegram.tgnet.ElloRpc
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.tlrpc.TL_photos_photo
import org.telegram.tgnet.tlrpc.TL_userProfilePhotoEmpty
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.ActionBarMenuItem
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Adapters.CountriesAdapter
import org.telegram.ui.Components.AlertsCreator
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.CodepointsLengthInputFilter
import org.telegram.ui.Components.ImageUpdater
import org.telegram.ui.Components.LayoutHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max

class EditProfileFragment(args: Bundle?) : BaseFragment(args), ImageUpdater.ImageUpdaterDelegate, NotificationCenter.NotificationCenterDelegate {
	private var binding: FragmentEditProfileBinding? = null
	private var userId = 0L
	private val avatarDrawable = AvatarDrawable().apply { shouldDrawPlaceholder = false }
	private val imageUpdater = ImageUpdater(false)
	private var avatarImage: AvatarImageView? = null
	private var avatar: TLRPC.FileLocation? = null
	private var avatarBig: TLRPC.FileLocation? = null
	private var doneItem: ActionBarMenuItem? = null
	private var countries: List<Country>? = null
	private var maxFirstNameLastNameLength = 0
	private var maxUsernameLength = 0
	private var infoLoaded = false

	private var changeBigAvatarCallback: ChangeBigAvatarCallback? = null

	private fun userNameChanged(): Boolean {
		return (binding?.usernameView?.text ?: "") != (arguments?.getString("username", ""))
	}

	private val checkUserName = CheckUsername {
		binding?.usernameError?.text = it

		if (it == null) {
			if (this@EditProfileFragment.userNameChanged()) {
				binding?.usernameError?.visible()
			}
			else {
				binding?.usernameError?.gone()
			}

			binding?.usernameError?.text = buildSpannedString {
				inSpans(ForegroundColorSpan(ResourcesCompat.getColor(context?.resources!!, R.color.green, null))) {
					append(context?.getString(R.string.LinkAvailable))
				}
			}
			doneItem?.isEnabled = true
			doneItem?.alpha = 1f
		}
		else {
			binding?.usernameError?.visible()
			doneItem?.isEnabled = false
			doneItem?.alpha = .5f
		}
	}

	override fun onFragmentCreate(): Boolean {
		userId = arguments?.getLong("user_id", 0) ?: return false
		return true
	}

	fun setChangeBigAvatarCallback(callback: ChangeBigAvatarCallback) {
		this.changeBigAvatarCallback = callback
	}

	override fun saveSelfArgs(args: Bundle?) {
		binding?.description?.text?.toString().let {
			args?.putString("descriptionText", it)
		}
	}

	override fun restoreSelfArgs(args: Bundle?) {
		val text = args?.getString("descriptionText")

		if (text != null) {
			binding?.description?.setText(text)
		}
	}

	override fun createView(context: Context): View? {
		actionBar?.setAddToContainer(true)
		actionBar?.setBackButtonImage(R.drawable.ic_back_arrow)
		actionBar?.setTitle(getContext()?.getString(R.string.cont_desc_edit_profile))

		val menu = actionBar?.createMenu()

		doneItem = menu?.addItem(DONE_MENU_ITEM, ResourcesCompat.getDrawable(context.resources, R.drawable.ic_checkmark, null))!!

		actionBar?.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
			override fun onItemClick(id: Int) {
				when (id) {
					ActionBar.BACK_BUTTON -> {
						finishFragment()
					}

					DONE_MENU_ITEM -> {
						saveProfile()
					}
				}
			}
		})

		binding = FragmentEditProfileBinding.inflate(LayoutInflater.from(context))

		binding?.firstNameView?.addTextChangedListener {
			binding?.firstNameLayout?.error = null
			binding?.firstNameLayout?.isErrorEnabled = false

			maxFirstNameLastNameLength = FIRST_LAST_NAME_MAX_LENGTH
			val remainingChars = it?.length?.let { length -> maxFirstNameLastNameLength.minus(length) }

			binding?.firstNameCounter?.text = remainingChars.toString()
		}

		binding?.lastNameView?.addTextChangedListener {
			binding?.lastNameLayout?.error = null
			binding?.lastNameLayout?.isErrorEnabled = false

			maxFirstNameLastNameLength = FIRST_LAST_NAME_MAX_LENGTH
			val remainingChars = it?.length?.let { length -> maxFirstNameLastNameLength.minus(length) }

			binding?.lastNameCounter?.text = remainingChars.toString()
		}

		binding?.usernameView?.addTextChangedListener {
			val username = it?.toString() ?: ""
			checkUserName.check(username)

			maxUsernameLength = USERNAME_MAX_LENGTH
			val remainingChars = it?.length?.let { length -> maxUsernameLength.minus(length) }

			binding?.usernameCounter?.text = remainingChars.toString()
		}

		binding?.usernameView?.filters = arrayOf(UsernameFilter(), InputFilter.LengthFilter(USERNAME_MAX_LENGTH))

		binding?.description?.addTextChangedListener {
			binding?.description?.error = null

			val maxLength = DESCRIPTION_MAX_LENGTH
			val currentLength = it?.length ?: 0

			if (currentLength > maxLength) {
				val trimmedText = it?.toString()?.substring(0, maxLength)

				binding?.description?.setText(trimmedText)
				binding?.description?.setSelection(trimmedText?.length ?: 0)
			}

			val remainingChars = max(0, maxLength - currentLength)
			binding?.descriptionCounter?.text = remainingChars.toString()
		}

		binding?.countrySpinner?.addTextChangedListener {
			binding?.countrySpinnerLayout?.isErrorEnabled = false
			binding?.countrySpinnerLayout?.error = null
		}

		binding?.countrySpinner?.setOnItemClickListener { _, _, _, _ ->
			setCountriesAdapter()
		}

		binding?.description?.filters = arrayOf(object : CodepointsLengthInputFilter(messagesController.aboutLimit) {
			override fun filter(source: CharSequence?, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
				val result = super.filter(source, start, end, dest, dstart, dend)

				if (result != null && source != null && result.length != source.length) {
					parentActivity?.vibrate()
					AndroidUtilities.shakeView(binding?.description, 2f, 0)
				}

				return result
			}
		})

		binding?.changeAvatarButton?.setOnClickListener {
			changeAvatar()
		}

		binding?.genderSpinner?.setAdapter(ArrayAdapter(context, android.R.layout.simple_list_item_1, context.resources.getStringArray(R.array.gender)))

		binding?.birthdayField?.setOnClickListener {
			val calendar = Calendar.getInstance()
			val year = calendar[Calendar.YEAR]
			val month = calendar[Calendar.MONTH]
			val day = calendar[Calendar.DAY_OF_MONTH]

			val datePickerDialog = DatePickerDialog(it.context, { _, y, m, d ->
				val formatter = SimpleDateFormat("ddMMyyyy", Locale.getDefault())
				val date = formatter.parse(String.format(Locale.getDefault(), "%02d%02d%d", d, m + 1, y))
				binding?.birthdayField?.setText(date?.formatBirthday())
			}, year, month, day)

			datePickerDialog.datePicker.maxDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(365 * 13) // -13 years from now

			datePickerDialog.show()
		}

		binding?.birthdayField?.addTextChangedListener {
			binding?.birthdayFieldLayout?.isErrorEnabled = false
			binding?.birthdayFieldLayout?.error = null
		}

		avatarImage = object : AvatarImageView(context) {
			override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
				super.onInitializeAccessibilityNodeInfo(info)

				if (imageReceiver.hasNotThumb()) {
					info.text = context.getString(R.string.AccDescrProfilePicture)
					info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, context.getString(R.string.Open)))
					info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, context.getString(R.string.AccDescrOpenInPhotoViewer)))
				}
				else {
					info.isVisibleToUser = false
				}
			}
		}

		val avatarRadius = AndroidUtilities.dp(35f)

		avatarImage?.imageReceiver?.setAllowDecodeSingleFrame(true)
		avatarImage?.setRoundRadius(avatarRadius, avatarRadius, avatarRadius, avatarRadius)
		avatarImage?.pivotX = 0f
		avatarImage?.pivotY = 0f

		binding?.profileImageContainer?.addView(avatarImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

		val userInfo = messagesController.getUserFull(userId)

		avatarDrawable.setInfo(userInfo?.user)

		imageUpdater.setOpenWithFrontfaceCamera(true)
		imageUpdater.parentFragment = this
		imageUpdater.setDelegate(this)

		if (userInfo == null) {
			binding?.progressBar?.visible()
			binding?.root?.setUserInteractionsEnabled(false)
		}
		else {
			reloadScreen()
		}

		notificationCenter.addObserver(this, NotificationCenter.userInfoDidLoad)

		messagesController.loadFullUser(messagesController.getUser(userId), classGuid, true)
		messagesController.loadUserInfo(userConfig.getCurrentUser(), true, classGuid)

		if (messagesController.getUser(userId)?.is_business == true) {
			binding?.firstNameLabel?.text = context.getString(R.string.business_name)
			binding?.firstNameView?.hint = context.getString(R.string.business_name)
			binding?.description?.hint = context.getString(R.string.BioOptionalPlaceholder)

			binding?.lastNameLayout?.gone()
			binding?.lastNameView?.gone()
			binding?.lastNameCounter?.gone()
			binding?.lastNameLabel?.gone()

			binding?.birthdayField?.gone()
			binding?.birthdayFieldLayout?.gone()
			binding?.birthdayLabel?.gone()

			binding?.genderSpinner?.gone()
			binding?.genderSpinnerLayout?.gone()
			binding?.genderLabel?.gone()

			binding?.countryLabel?.updateLayoutParams<ConstraintLayout.LayoutParams> {
				topToBottom = binding?.descriptionLabel?.id!!
			}

			binding?.description?.updateLayoutParams<ConstraintLayout.LayoutParams> {
				topToBottom = binding?.usernameDescription?.id!!
			}

			binding?.usernameLabel?.updateLayoutParams<ConstraintLayout.LayoutParams> {
				topToBottom = binding?.firstNameLayout?.id!!
			}
		}

		fragmentView = binding?.root

		return binding?.root
	}

	override fun onResume() {
		super.onResume()
		loadCountries()
		reloadScreen()
		restoreSelfArgs(arguments)
	}

	private fun saveProfile() {
		val context = context ?: return
		val username = binding?.usernameView?.text?.toString()?.trim()

		if (username.isNullOrEmpty()) {
			binding?.usernameLayout?.error = context.getString(R.string.field_required)
			return
		}

		val firstName = binding?.firstNameView?.text?.toString()?.trim()

		if (firstName.isNullOrEmpty()) {
			binding?.firstNameLayout?.error = context.getString(R.string.first_name_empty)
			return
		}

		val lastName = binding?.lastNameView?.text?.toString()?.trim() ?: ""
		val bio = binding?.description?.text?.toString()?.trim() ?: ""

		val country = (binding?.countrySpinner?.adapter as? CountriesAdapter)?.countries?.find {
			it.name == binding?.countrySpinner?.text?.toString()?.trim()
		}

		val countryCode = country?.code

		if (countryCode.isNullOrEmpty()) {
			binding?.countrySpinnerLayout?.error = context.getString(R.string.country_is_empty)
			return
		}

		val birthday = binding?.birthdayField?.text?.toString()?.takeIf { it.isNotEmpty() }?.parseBirthday()

		val gender = when (binding?.genderSpinner?.text?.toString()) {
			context.getString(R.string.female) -> ElloRpc.Gender.WOMAN
			context.getString(R.string.male) -> ElloRpc.Gender.MAN
			context.getString(R.string.non_binary) -> ElloRpc.Gender.NON_BINARY
			else -> ElloRpc.Gender.OTHER
		}

		updateProfile(bio = bio, firstName = firstName, lastName = lastName, username = username, birthday = birthday, gender = gender, country = countryCode)
	}

	private fun updateProfile(bio: String, firstName: String, lastName: String, username: String, birthday: Date?, gender: String?, country: String) {
		setControlsEnabled(false)

		val req = ElloRpc.changeProfileRequest(firstName = firstName, lastName = lastName, username = username, bio = bio, gender = gender, birthday = birthday?.formatDate(), country = country)

		binding?.progressBar?.visible()

		val reqId = connectionsManager.sendRequest(req, { _, error ->
			if (error == null) {
				val userFull = messagesController.getUserFull(userConfig.getClientUserId())

				AndroidUtilities.runOnUIThread {
					setControlsEnabled(true)

					binding?.progressBar?.gone()

					if (userFull != null) {
						userFull.about = bio
						userFull.user?.first_name = firstName
						userFull.user?.last_name = lastName
						userFull.user?.username = username
						userFull.gender = gender
						userFull.date_of_birth = ((birthday?.time ?: 0L) / 1000L).toInt()
						userFull.country = country

						notificationCenter.postNotificationName(NotificationCenter.userInfoDidLoad, userFull.id, userFull)
					}

					finishFragment()
				}
			}
			else {
				AndroidUtilities.runOnUIThread {
					setControlsEnabled(true)
					binding?.progressBar?.gone()
					AlertsCreator.processError(currentAccount, error, this, req)
				}
			}
		}, ConnectionsManager.RequestFlagFailOnServerErrors)

		connectionsManager.bindRequestToGuid(reqId, classGuid)
	}

	private fun setControlsEnabled(enabled: Boolean) {
		binding?.birthdayField?.isEnabled = enabled
		binding?.genderSpinner?.isEnabled = enabled
		binding?.firstNameView?.isEnabled = enabled
		binding?.lastNameView?.isEnabled = enabled
		binding?.description?.isEnabled = enabled
		binding?.changeAvatarButton?.isEnabled = enabled
		binding?.usernameView?.isEnabled = enabled
	}

	private fun changeAvatar() {
		var user = messagesController.getUser(userConfig.getClientUserId())

		if (user == null) {
			user = userConfig.getCurrentUser()
		}

		if (user == null) {
			return
		}

		imageUpdater.openMenu(user.photo?.photo_big != null && user.photo !is TL_userProfilePhotoEmpty, {
			messagesController.deleteUserPhoto(null)
		}) {
			// unused
		}
	}

	private fun reloadScreen() {
		val userInfo = messagesController.getUserFull(userId) ?: return
		val context = context ?: return

		if (!infoLoaded) {
			infoLoaded = true

			binding?.firstNameView?.setText(userInfo.user?.first_name)
			binding?.lastNameView?.setText(userInfo.user?.last_name)
			binding?.usernameView?.setText(userInfo.user?.username)
			binding?.description?.setText(userInfo.about)
			binding?.birthdayField?.setText(Date(userInfo.date_of_birth.toLong() * 1000L).formatBirthday())

			binding?.genderSpinner?.setText(when (userInfo.gender) {
				ElloRpc.Gender.WOMAN -> context.getString(R.string.female)
				ElloRpc.Gender.MAN -> context.getString(R.string.male)
				ElloRpc.Gender.NON_BINARY -> context.getString(R.string.non_binary)
				ElloRpc.Gender.OTHER -> context.getString(R.string.other)
				else -> context.getString(R.string.male)
			}, false)
		}

		val user = messagesController.getUser(userId) ?: return

		avatarDrawable.setInfo(user)

		val photo = ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_BIG)

		if (photo != null) {
			avatarImage?.setImageDrawable(null)
			avatarImage?.setImage(photo, null, avatarDrawable, user)
		}
		else {
			avatarImage?.setImageResource(R.drawable.avatar_upload_placeholder)
		}
	}

	override fun onFragmentDestroy() {
		super.onFragmentDestroy()
		notificationCenter.removeObserver(this, NotificationCenter.userInfoDidLoad)
		avatarImage?.setImageDrawable(null)
		imageUpdater.clear()
		binding = null
		changeBigAvatarCallback = null
	}

	private fun setCountriesAdapter() {
		val countries = countries ?: return
		val context = context ?: return
		val countriesAdapter = CountriesAdapter(context, R.layout.country_view_holder, countries)
		binding?.countrySpinner?.setAdapter(countriesAdapter)
	}

	private fun loadCountries() {
		CountriesDataSource.instance.loadCountries callback@{ countries, error ->
			val context = context ?: return@callback

			if (error == null && countries != null) {
				this.countries = countries

				setCountriesAdapter()

				val userInfo = messagesController.getUserFull(userId) ?: return@callback

				countries.find {
					it.code == userInfo.country
				}?.let { country ->
					binding?.countrySpinner?.setText(country.name, false)
				}
			}
			else {
				Toast.makeText(context, R.string.failed_to_load_countries, Toast.LENGTH_SHORT).show()
			}
		}
	}

	override fun didUploadPhoto(photo: TLRPC.InputFile?, video: TLRPC.InputFile?, videoStartTimestamp: Double, videoPath: String?, bigSize: TLRPC.PhotoSize?, smallSize: TLRPC.PhotoSize?) {
		AndroidUtilities.runOnUIThread top@{
			if (photo != null) {
				val req = TLRPC.TL_photos_uploadProfilePhoto()
				req.file = photo
				req.flags = req.flags or 1

				connectionsManager.sendRequest(req) { response, error ->
					AndroidUtilities.runOnUIThread {
						if (error == null) {
							var user = messagesController.getUser(userConfig.getClientUserId())

							if (user == null) {
								user = userConfig.getCurrentUser()

								if (user == null) {
									return@runOnUIThread
								}

								messagesController.putUser(user, false)
							}
							else {
								userConfig.setCurrentUser(user)
							}

							val photosPhoto = response as TL_photos_photo
							val sizes = photosPhoto.photo?.sizes
							val small = FileLoader.getClosestPhotoSizeWithSize(sizes, 150)
							val big = FileLoader.getClosestPhotoSizeWithSize(sizes, 800)
							val videoSize = photosPhoto.photo?.video_sizes?.firstOrNull()

							user.photo = TLRPC.TL_userProfilePhoto()
							user.photo?.photo_id = photosPhoto.photo?.id ?: 0L

							if (small != null) {
								user.photo?.photo_small = small.location
							}

							if (big != null) {
								user.photo?.photo_big = big.location
							}

							if (small != null && avatar != null) {
								val destFile = fileLoader.getPathToAttach(small, true)
								val src = fileLoader.getPathToAttach(avatar, true)
								src.renameTo(destFile)
								val oldKey = avatar?.volume_id?.toString() + "_" + avatar?.local_id + "@50_50"
								val newKey = small.location.volume_id.toString() + "_" + small.location.local_id + "@50_50"

								ImageLocation.getForUserOrChat(user, ImageLocation.TYPE_SMALL)?.let {
									ImageLoader.instance.replaceImageInCache(oldKey, newKey, it, false)
								}
							}

							if (big != null && avatarBig != null) {
								val destFile = fileLoader.getPathToAttach(big, true)
								val src = fileLoader.getPathToAttach(avatarBig, true)
								src.renameTo(destFile)
							}

							if (videoSize != null && videoPath != null) {
								val destFile = fileLoader.getPathToAttach(videoSize, "mp4", true)
								val src = File(videoPath)
								src.renameTo(destFile)
							}

							messagesStorage.clearUserPhotos(user.id)

							messagesStorage.putUsersAndChats(arrayListOf(user), null, false, true)
						}

						avatar = null
						avatarBig = null

						reloadScreen()

						notificationCenter.postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL)
						notificationCenter.postNotificationName(NotificationCenter.mainUserInfoChanged)

						userConfig.saveConfig(true)
					}
				}
			}
			else {
				avatar = smallSize?.location
				avatarBig = bigSize?.location

				avatarImage?.setImage(ImageLocation.getForLocal(avatarBig), null, avatarDrawable, null)
				changeBigAvatarCallback?.changeBigAvatar()
			}
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.userInfoDidLoad -> {
				val uid = args[0] as? Long ?: return

				if (uid == userId) {
					binding?.progressBar?.gone()
					reloadScreen()
					binding?.root?.setUserInteractionsEnabled(true)
					binding?.birthdayField?.isFocusable = false
				}
			}
		}
	}

	companion object {
		private const val DONE_MENU_ITEM = 1
		private const val USERNAME_MAX_LENGTH = 32
		private const val FIRST_LAST_NAME_MAX_LENGTH = 64
		private const val DESCRIPTION_MAX_LENGTH = 70
	}

	override fun onPause() {
		super.onPause()
		saveSelfArgs(arguments)
	}

	interface ChangeBigAvatarCallback {
		fun changeBigAvatar()
	}

}
