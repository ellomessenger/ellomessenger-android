/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 * Copyright Nikita Denin, Ello 2024.
 */
package org.telegram.messenger

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Notification.MediaStyle
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.RemoteControlClient
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import com.google.android.exoplayer2.C
import org.telegram.messenger.ImageReceiver.ImageReceiverDelegate
import org.telegram.messenger.NotificationCenter.NotificationCenterDelegate
import org.telegram.messenger.messageobject.MessageObject
import org.telegram.ui.LaunchActivity
import kotlin.math.max
import kotlin.math.round

class MusicPlayerService : Service(), NotificationCenterDelegate {
	private var remoteControlClient: RemoteControlClient? = null
	private var audioManager: AudioManager? = null
	private var mediaSession: MediaSession? = null
	private var playbackState: PlaybackState.Builder? = null
	private var albumArtPlaceholder: Bitmap? = null
	private var notificationMessageID = 0
	private var imageReceiver: ImageReceiver? = null
	private var loadingFilePath: String? = null

	private val headsetPlugReceiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent.action) {
				MediaController.getInstance().pauseMessage(MediaController.getInstance().playingMessageObject)
			}
		}
	}

	override fun onBind(intent: Intent): IBinder? {
		return null
	}

	override fun onCreate() {
		audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

		for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
			NotificationCenter.getInstance(a).let {
				it.addObserver(this, NotificationCenter.messagePlayingDidSeek)
				it.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
				it.addObserver(this, NotificationCenter.httpFileDidLoad)
				it.addObserver(this, NotificationCenter.fileLoaded)
			}
		}

		imageReceiver = ImageReceiver(null)

		imageReceiver?.setDelegate(object : ImageReceiverDelegate {
			override fun onAnimationReady(imageReceiver: ImageReceiver) {
				// unused
			}

			override fun didSetImage(imageReceiver: ImageReceiver, set: Boolean, thumb: Boolean, memCache: Boolean) {
				if (set && !loadingFilePath.isNullOrEmpty()) {
					val messageObject = MediaController.getInstance().playingMessageObject

					if (messageObject != null) {
						createNotification(messageObject, true)
					}

					loadingFilePath = null
				}
			}
		})

		mediaSession = MediaSession(this, "telegramAudioPlayer")
		playbackState = PlaybackState.Builder()

		val albumArtPlaceholder = Bitmap.createBitmap(AndroidUtilities.dp(102f), AndroidUtilities.dp(102f), Bitmap.Config.ARGB_8888).also {
			this.albumArtPlaceholder = it
		}

		val placeholder = ResourcesCompat.getDrawable(resources, R.drawable.nocover_big, null)
		placeholder?.setBounds(0, 0, albumArtPlaceholder.width, albumArtPlaceholder.height)
		placeholder?.draw(Canvas(albumArtPlaceholder))

		mediaSession?.setCallback(object : MediaSession.Callback() {
			override fun onPlay() {
				MediaController.getInstance().playMessage(MediaController.getInstance().playingMessageObject)
			}

			override fun onPause() {
				MediaController.getInstance().pauseMessage(MediaController.getInstance().playingMessageObject)
			}

			override fun onSkipToNext() {
				MediaController.getInstance().playNextMessage()
			}

			override fun onSkipToPrevious() {
				MediaController.getInstance().playPreviousMessage()
			}

			override fun onSeekTo(pos: Long) {
				val `object` = MediaController.getInstance().playingMessageObject

				if (`object` != null) {
					MediaController.getInstance().seekToProgress(`object`, pos / 1000 / `object`.duration.toFloat())
					updatePlaybackState(pos)
				}
			}

			override fun onStop() {
				// stopSelf()
			}
		})

		if (mediaSession?.isActive != true) {
			mediaSession?.isActive = true
		}

		registerReceiver(headsetPlugReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

		super.onCreate()
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		try {
			if (intent != null && ("$packageName.STOP_PLAYER") == intent.action) {
				MediaController.getInstance().cleanupPlayer(true, true)
				return START_NOT_STICKY
			}

			val messageObject = MediaController.getInstance().playingMessageObject

			if (messageObject == null) {
				AndroidUtilities.runOnUIThread {
					this.stopSelf()
				}

				return START_STICKY
			}

			if (supportLockScreenControls) {
				val remoteComponentName = ComponentName(applicationContext, MusicPlayerReceiver::class.java.name)

				try {
					if (remoteControlClient == null) {
						audioManager?.registerMediaButtonEventReceiver(remoteComponentName)

						val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
						mediaButtonIntent.setComponent(remoteComponentName)

						val mediaPendingIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

						remoteControlClient = RemoteControlClient(mediaPendingIntent)
						audioManager?.registerRemoteControlClient(remoteControlClient)
					}

					remoteControlClient?.setTransportControlFlags(RemoteControlClient.FLAG_KEY_MEDIA_PLAY or RemoteControlClient.FLAG_KEY_MEDIA_PAUSE or RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE or RemoteControlClient.FLAG_KEY_MEDIA_STOP or RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS or RemoteControlClient.FLAG_KEY_MEDIA_NEXT)
				}
				catch (e: Exception) {
					FileLog.e(e)
				}
			}
			createNotification(messageObject, false)
		}
		catch (e: Exception) {
			FileLog.e(e)
		}

		return START_STICKY
	}

	private fun loadArtworkFromUrl(artworkUrl: String?, big: Boolean, tryLoad: Boolean): Bitmap? {
		val path = ImageLoader.getHttpFilePath(artworkUrl, "jpg")

		if (path.exists()) {
			return ImageLoader.loadBitmap(path.absolutePath, null, (if (big) 600 else 100).toFloat(), (if (big) 600 else 100).toFloat(), false)
		}

		if (tryLoad) {
			loadingFilePath = path.absolutePath

			if (!big) {
				imageReceiver?.setImage(artworkUrl, "48_48", null, null, 0)
			}
		}
		else {
			loadingFilePath = null
		}

		return null
	}

	@SuppressLint("NewApi")
	private fun createNotification(messageObject: MessageObject, forBitmap: Boolean) {
		val songName = messageObject.musicTitle
		val authorName = messageObject.musicAuthor
		val audioInfo = MediaController.getInstance().audioInfo

		val intent = Intent(ApplicationLoader.applicationContext, LaunchActivity::class.java)
		intent.setAction("com.tmessages.openplayer")
		intent.addCategory(Intent.CATEGORY_LAUNCHER)

		val contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

		val notification: Notification
		val artworkUrl = messageObject.getArtworkUrl(true)
		val artworkUrlBig = messageObject.getArtworkUrl(false)
		val duration = (messageObject.duration * 1000).toLong()
		var albumArt = audioInfo?.smallCover
		var fullAlbumArt = audioInfo?.cover

		loadingFilePath = null

		imageReceiver?.setImageBitmap(null as BitmapDrawable?)
		if (albumArt == null && !TextUtils.isEmpty(artworkUrl)) {
			fullAlbumArt = loadArtworkFromUrl(artworkUrlBig, true, !forBitmap)

			if (fullAlbumArt == null) {
				albumArt = loadArtworkFromUrl(artworkUrl, false, !forBitmap)
				fullAlbumArt = albumArt
			}
			else {
				albumArt = loadArtworkFromUrl(artworkUrlBig, false, !forBitmap)
			}
		}
		else {
			loadingFilePath = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(messageObject.document).absolutePath
		}

		val isPlaying = !MediaController.getInstance().isMessagePaused

		val pendingPrev = PendingIntent.getBroadcast(applicationContext, 0, Intent(NOTIFY_PREVIOUS).setComponent(ComponentName(this, MusicPlayerReceiver::class.java)), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		val pendingStop = PendingIntent.getService(applicationContext, 0, Intent(this, javaClass).setAction("$packageName.STOP_PLAYER"), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		val pendingPlayPause = PendingIntent.getBroadcast(applicationContext, 0, Intent(if (isPlaying) NOTIFY_PAUSE else NOTIFY_PLAY).setComponent(ComponentName(this, MusicPlayerReceiver::class.java)), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		val pendingNext = PendingIntent.getBroadcast(applicationContext, 0, Intent(NOTIFY_NEXT).setComponent(ComponentName(this, MusicPlayerReceiver::class.java)), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		val pendingSeek = PendingIntent.getBroadcast(applicationContext, 0, Intent(NOTIFY_SEEK).setComponent(ComponentName(this, MusicPlayerReceiver::class.java)), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)

		NotificationsController.checkOtherNotificationsChannel()

		val bldr = Notification.Builder(this, NotificationsController.OTHER_NOTIFICATIONS_CHANNEL)
		bldr.setSmallIcon(R.drawable.player).setOngoing(isPlaying).setContentTitle(songName).setContentText(authorName).setSubText(audioInfo?.album).setContentIntent(contentIntent).setDeleteIntent(pendingStop).setShowWhen(false).setCategory(Notification.CATEGORY_TRANSPORT).setPriority(Notification.PRIORITY_MAX).setStyle(MediaStyle().setMediaSession(mediaSession!!.sessionToken).setShowActionsInCompactView(0, 1, 2))

		if (albumArt != null) {
			bldr.setLargeIcon(albumArt)
		}
		else {
			bldr.setLargeIcon(albumArtPlaceholder)
		}

		val nextDescription = getString(R.string.Next)
		val previousDescription = getString(R.string.AccDescrPrevious)

		if (MediaController.getInstance().isDownloadingCurrentMessage) {
			playbackState?.setState(PlaybackState.STATE_BUFFERING, 0, 1f)?.setActions(0)
			bldr.addAction(Notification.Action.Builder(R.drawable.ic_action_previous, previousDescription, pendingPrev).build()).addAction(Notification.Action.Builder(R.drawable.loading_animation2, getString(R.string.Loading), null).build()).addAction(Notification.Action.Builder(R.drawable.ic_action_next, nextDescription, pendingNext).build())
		}
		else {
			playbackState?.setState(if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, MediaController.getInstance().playingMessageObject!!.audioProgressSec * 1000L, (if (isPlaying) 1 else 0).toFloat())?.setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_NEXT)
			val playPauseTitle = if (isPlaying) getString(R.string.AccActionPause) else getString(R.string.AccActionPlay)
			bldr.addAction(Notification.Action.Builder(R.drawable.ic_action_previous, previousDescription, pendingPrev).build()).addAction(Notification.Action.Builder(if (isPlaying) R.drawable.ic_action_pause else R.drawable.ic_action_play, playPauseTitle, pendingPlayPause).build()).addAction(Notification.Action.Builder(R.drawable.ic_action_next, nextDescription, pendingNext).build())
		}

		mediaSession?.setPlaybackState(playbackState?.build())

		val meta = MediaMetadata.Builder().putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, fullAlbumArt).putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, authorName).putString(MediaMetadata.METADATA_KEY_ARTIST, authorName).putLong(MediaMetadata.METADATA_KEY_DURATION, duration).putString(MediaMetadata.METADATA_KEY_TITLE, songName).putString(MediaMetadata.METADATA_KEY_ALBUM, audioInfo?.album)

		mediaSession?.setMetadata(meta.build())

		bldr.setVisibility(Notification.VISIBILITY_PUBLIC)

		notification = bldr.build()

		if (isPlaying) {
			startForeground(ID_NOTIFICATION, notification)
		}
		else {
			stopForeground(false)
			val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
			nm.notify(ID_NOTIFICATION, notification)
		}

		if (remoteControlClient != null) {
			val currentID = MediaController.getInstance().playingMessageObject!!.id

			if (notificationMessageID != currentID) {
				notificationMessageID = currentID

				val metadataEditor = remoteControlClient!!.editMetadata(true)
				metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, authorName)
				metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, songName)

				if (audioInfo != null && !TextUtils.isEmpty(audioInfo.album)) {
					metadataEditor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, audioInfo.album)
				}

				metadataEditor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, MediaController.getInstance().playingMessageObject!!.audioPlayerDuration * 1000L)

				if (fullAlbumArt != null) {
					try {
						metadataEditor.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, fullAlbumArt)
					}
					catch (e: Throwable) {
						FileLog.e(e)
					}
				}

				metadataEditor.apply()

				AndroidUtilities.runOnUIThread(object : Runnable {
					override fun run() {
						if (remoteControlClient == null || MediaController.getInstance().playingMessageObject == null) {
							return
						}

						if (MediaController.getInstance().playingMessageObject!!.audioPlayerDuration.toLong() == C.TIME_UNSET) {
							AndroidUtilities.runOnUIThread(this, 500)
							return
						}

						val metadataEditor = remoteControlClient!!.editMetadata(false)
						metadataEditor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, MediaController.getInstance().playingMessageObject!!.audioPlayerDuration * 1000L)
						metadataEditor.apply()

						remoteControlClient?.setPlaybackState(if (MediaController.getInstance().isMessagePaused) RemoteControlClient.PLAYSTATE_PAUSED else RemoteControlClient.PLAYSTATE_PLAYING, max((MediaController.getInstance().playingMessageObject!!.audioProgressSec * 1000L).toDouble(), 100.0).toLong(), if (MediaController.getInstance().isMessagePaused) 0f else 1f)
					}
				}, 1000)
			}

			if (MediaController.getInstance().isDownloadingCurrentMessage) {
				remoteControlClient!!.setPlaybackState(RemoteControlClient.PLAYSTATE_BUFFERING)
			}
			else {
				val metadataEditor = remoteControlClient!!.editMetadata(false)
				metadataEditor.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, MediaController.getInstance().playingMessageObject!!.audioPlayerDuration * 1000L)
				metadataEditor.apply()

				remoteControlClient?.setPlaybackState(if (MediaController.getInstance().isMessagePaused) RemoteControlClient.PLAYSTATE_PAUSED else RemoteControlClient.PLAYSTATE_PLAYING, max((MediaController.getInstance().playingMessageObject!!.audioProgressSec * 1000L).toDouble(), 100.0).toLong(), if (MediaController.getInstance().isMessagePaused) 0f else 1f)
			}
		}
	}

	private fun updatePlaybackState(seekTo: Long) {
		val isPlaying = !MediaController.getInstance().isMessagePaused

		if (MediaController.getInstance().isDownloadingCurrentMessage) {
			playbackState?.setState(PlaybackState.STATE_BUFFERING, 0, 1f)?.setActions(0)
		}
		else {
			playbackState?.setState(if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, seekTo, (if (isPlaying) 1 else 0).toFloat())?.setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SKIP_TO_NEXT)
		}

		mediaSession?.setPlaybackState(playbackState?.build())
	}

	fun setListeners(view: RemoteViews) {
		var pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, Intent(NOTIFY_PREVIOUS), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		view.setOnClickPendingIntent(R.id.player_previous, pendingIntent)

		pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, Intent(NOTIFY_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		view.setOnClickPendingIntent(R.id.player_close, pendingIntent)

		pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, Intent(NOTIFY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		view.setOnClickPendingIntent(R.id.player_pause, pendingIntent)

		pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, Intent(NOTIFY_NEXT), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		view.setOnClickPendingIntent(R.id.player_next, pendingIntent)

		pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, Intent(NOTIFY_PLAY), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		view.setOnClickPendingIntent(R.id.player_play, pendingIntent)
	}

	@SuppressLint("NewApi")
	override fun onDestroy() {
		unregisterReceiver(headsetPlugReceiver)

		super.onDestroy()
		if (remoteControlClient != null) {
			val metadataEditor = remoteControlClient!!.editMetadata(true)
			metadataEditor.clear()
			metadataEditor.apply()

			audioManager?.unregisterRemoteControlClient(remoteControlClient)
		}

		mediaSession?.release()

		for (a in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
			NotificationCenter.getInstance(a).let {
				it.removeObserver(this, NotificationCenter.messagePlayingDidSeek)
				it.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged)
				it.removeObserver(this, NotificationCenter.httpFileDidLoad)
				it.removeObserver(this, NotificationCenter.fileLoaded)
			}
		}
	}

	override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
		when (id) {
			NotificationCenter.messagePlayingPlayStateChanged -> {
				val messageObject = MediaController.getInstance().playingMessageObject

				if (messageObject != null) {
					createNotification(messageObject, false)
				}
				else {
					stopSelf()
				}
			}

			NotificationCenter.messagePlayingDidSeek -> {
				val messageObject = MediaController.getInstance().playingMessageObject

				if (remoteControlClient != null) {
					val progress = round(messageObject!!.audioPlayerDuration * args[1] as Float).toLong() * 1000L
					remoteControlClient?.setPlaybackState(if (MediaController.getInstance().isMessagePaused) RemoteControlClient.PLAYSTATE_PAUSED else RemoteControlClient.PLAYSTATE_PLAYING, progress, if (MediaController.getInstance().isMessagePaused) 0f else 1f)
				}
			}

			NotificationCenter.httpFileDidLoad -> {
				val path = args[0] as String
				val messageObject = MediaController.getInstance().playingMessageObject

				if (messageObject != null && loadingFilePath != null && loadingFilePath == path) {
					createNotification(messageObject, false)
				}
			}

			NotificationCenter.fileLoaded -> {
				val path = args[0] as String
				val messageObject = MediaController.getInstance().playingMessageObject

				if (messageObject != null && loadingFilePath != null && loadingFilePath == path) {
					createNotification(messageObject, false)
				}
			}
		}
	}

	override fun onTaskRemoved(rootIntent: Intent?) {
		super.onTaskRemoved(rootIntent)
		MediaController.getInstance().cleanupPlayer(true, true)
	}

	companion object {
		const val NOTIFY_PREVIOUS: String = "com.beint.elloapp.android.musicplayer.previous"
		const val NOTIFY_CLOSE: String = "com.beint.elloapp.android.musicplayer.close"
		const val NOTIFY_PAUSE: String = "com.beint.elloapp.android.musicplayer.pause"
		const val NOTIFY_PLAY: String = "com.beint.elloapp.android.musicplayer.play"
		const val NOTIFY_NEXT: String = "com.beint.elloapp.android.musicplayer.next"
		const val NOTIFY_SEEK: String = "com.beint.elloapp.android.musicplayer.seek"
		private const val ID_NOTIFICATION = 5
		private val supportLockScreenControls = !TextUtils.isEmpty(AndroidUtilities.getSystemProperty("ro.miui.ui.version.code"))
	}
}
