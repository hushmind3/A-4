package com.dominion.mobile.platform.audio

import android.Manifest
import android.content.pm.PackageManager
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dominion.mobile.DominionApplication
import com.dominion.mobile.MainActivity
import com.dominion.mobile.R
import com.dominion.mobile.data.model.CallPhase
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Ephemeral media state: never saved in the conversation database. */
data class CallMediaState(val sessionId: String? = null, val room: Room? = null,
    val localVideo: VideoTrack? = null, val remoteVideo: VideoTrack? = null,
    val audioLevel: Float = 0f, val speaker: Boolean = false, val cameraBusy: Boolean = false,
    val error: String? = null, val sharingScreen: Boolean = false,
    val remoteScreen: VideoTrack? = null, val screenBusy: Boolean = false,
    val localScreen: VideoTrack? = null, val remoteAudioLevel: Float = 0f)

/** Owns the actual media room; UI navigation does not own microphone lifetime. */
class CallForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repo get() = (application as DominionApplication).container.repository
    private var room: Room? = null
    private var sessionId: String? = null
    private var job: Job? = null
    private val cameraGate = Mutex()
    private val screenGate = Mutex()
    private var screenBusy = false
    private var ready = false
    private var cameraBusy = false
    private var mediaError: String? = null
    private var cameraActive = false
    private var agentIdentity: String? = null
    private var agentVideoAllowed = false
    private var agentRoomSession: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!(application as DominionApplication).containerReady) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_END) {
            val expected = intent.getStringExtra("sessionId")
            if (expected == null || repo.state.value.call?.sessionId != expected || sessionId != expected)
                return START_NOT_STICKY
            repo.update { com.dominion.mobile.data.CallHistory.finish(it) }
            stopSelf()
            return START_NOT_STICKY
        }
        val call = repo.state.value.call
        if (call == null || call.phase == CallPhase.FAILED) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) {
            when (intent?.action) {
                ACTION_CAMERA -> if (ready) scope.launch { applyCamera(intent.getBooleanExtra("enable", false)) }
                ACTION_SPEAKER -> if (ready) selectSpeaker(intent.getBooleanExtra("enable", false))
                ACTION_SCREEN -> if (ready && intent.getStringExtra("sessionId") == sessionId &&
                    call.sessionId == sessionId) {
                    val enable = intent.getBooleanExtra("enable", false)
                    val permissionData = IntentCompat.getParcelableExtra(intent, "projectionData", Intent::class.java)
                    // Remove the one-time permission result before returning; never persist it.
                    intent.removeExtra("projectionData")
                    if (!enable || permissionData != null) scope.launch { applyScreen(enable, permissionData) }
                }
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CAMERA || intent?.action == ACTION_SPEAKER || intent?.action == ACTION_SCREEN) {
            stopSelf()
            return START_NOT_STICKY
        }
        sessionId = call.sessionId
        try {
            foreground(call.requestVideo && hasCameraPermission())
        } catch (_: Exception) {
            fail("Microphone foreground service could not start")
            return START_NOT_STICKY
        }
        phase(CallPhase.CONNECTING)
        job = scope.launch {
            try {
                val credentials = withTimeout(30_000) {
                    if (call.realtimeAgent) {
                        val grant = repo.requestRealtimeCall(call.conversationId, call.sessionId, call.requestVideo)
                        agentIdentity = requireNotNull(grant.agentIdentity)
                        agentVideoAllowed = grant.videoAllowed
                        agentRoomSession = grant.sessionId
                        requireNotNull(grant.media)
                    } else repo.requestVoiceCall(call.conversationId, call.requestVideo)
                }
                val media = LiveKit.create(applicationContext)
                room = media
                coroutineScope {
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        media.events.collect { event ->
                            when (event) {
                                is RoomEvent.Reconnecting -> phase(CallPhase.RECONNECTING)
                                is RoomEvent.Reconnected,
                                is RoomEvent.ParticipantConnected,
                                is RoomEvent.ParticipantDisconnected -> {
                                    if (agentIdentity != null && !agentMembership(media, requirePresent = ready)) {
                                        fail("Realtime AI participant changed; media stopped")
                                    } else if (repo.state.value.call?.phase != CallPhase.CONNECTING) peerState(media)
                                }
                                is RoomEvent.Disconnected, is RoomEvent.FailedToConnect ->
                                    fail("Media connection closed")
                                else -> Unit
                            }
                        }
                    }
                    launch {
                        repo.state.collect { state ->
                            if (state.call?.sessionId != sessionId) stopSelf()
                        }
                    }
                    withTimeout(35_000) { media.connect(credentials.url, credentials.token) }
                    if (agentIdentity != null) {
                        // No microphone or camera publication until the authorized agent joins.
                        withTimeout(25_000L) {
                            while (!agentMembership(media, requirePresent = true)) {
                                check(agentMembership(media, requirePresent = false)) { "Unexpected media participant" }
                                check(repo.state.value.call?.sessionId == call.sessionId)
                                delay(100)
                            }
                        }
                    }
                    check(media.localParticipant.setMicrophoneEnabled(true)) { "Microphone publish failed" }
                    ready = true
                    peerState(media)
                    if (call.requestVideo) applyCamera(true)
                    launch {
                        while (isActive) {
                            if (agentIdentity != null) {
                                check(agentMembership(media, requirePresent = true)) { "Realtime agent left" }
                                check(repo.state.value.conversation(call.conversationId)?.sessionId == agentRoomSession) {
                                    "Native room changed"
                                }
                            }
                            publishMedia(media)
                            delay(250)
                        }
                    }
                    var appliedMuted = false
                    repo.state.collect { state ->
                        val current = state.call
                        if (current == null || current.sessionId != sessionId || current.phase == CallPhase.FAILED) {
                            stopSelf()
                        } else if (current.phase in setOf(CallPhase.WAITING_PEER, CallPhase.PEER_PRESENT) &&
                            current.muted != appliedMuted) {
                            check(media.localParticipant.setMicrophoneEnabled(!current.muted)) { "Microphone update failed" }
                            appliedMuted = current.muted
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) fail("Voice connection timed out")
                throw cancelled
            } catch (_: Exception) {
                fail("Voice connection or microphone failed")
            }
        }
        return START_NOT_STICKY
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    private fun selectSpeaker(enable: Boolean) {
        val handler = room?.audioSwitchHandler ?: return
        val target = handler.availableAudioDevices.firstOrNull {
            if (enable) it is AudioDevice.Speakerphone else it is AudioDevice.Earpiece
        } ?: if (!enable) handler.availableAudioDevices.firstOrNull { it !is AudioDevice.Speakerphone } else null
        if (target != null) {
            mediaError = null
            handler.selectDevice(target)
        } else mediaError = "선택한 오디오 출력 장치를 사용할 수 없습니다."
    }

    private suspend fun applyCamera(enable: Boolean) = cameraGate.withLock {
        val media = room ?: return@withLock
        if (!ready) return@withLock
        cameraBusy = true
        mediaError = null
        publishMedia(media)
        try {
            if (enable) {
                check(hasCameraPermission()) { "카메라 권한이 필요합니다." }
                if (agentIdentity != null) check(agentVideoAllowed && agentMembership(media, true)) {
                    "Realtime agent video not authorized"
                }
                // Called only after explicit UI request; Android enforces while-in-use permission.
                foreground(true)
                check(media.localParticipant.setCameraEnabled(true)) { "카메라 영상 송출 실패" }
                cameraActive = true
            } else {
                check(media.localParticipant.setCameraEnabled(false)) { "카메라 중지 실패" }
                cameraActive = false
                foreground(false)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mediaError = "카메라 전환 실패 · 권한과 연결 상태를 확인하세요."
            if (enable) {
                // Do not retain a partially published camera after a failed enable.
                val stopped = runCatching { media.localParticipant.setCameraEnabled(false) }.getOrDefault(false)
                cameraActive = media.localParticipant.isCameraEnabled
                if (stopped && !cameraActive) runCatching { foreground(false) }
                else if (cameraActive) { fail("카메라 종료 확인 실패 · 통화를 중단합니다.") }
            }
        } finally {
            cameraBusy = false
            repo.update { state ->
                val current = state.call
                if (current != null && current.sessionId == sessionId) state.copy(call = current.copy(video = cameraActive, requestVideo = false, hadVideo = current.hadVideo || cameraActive)) else state
            }
            publishMedia(media)
        }
    }

    private fun screenPublished(media: Room): Boolean =
        media.localParticipant.getTrackPublication(Track.Source.SCREEN_SHARE)
            ?.let { !it.muted && it.track is VideoTrack } == true

    private suspend fun applyScreen(enable: Boolean, permissionData: Intent?) = screenGate.withLock {
        val media = room ?: return@withLock
        if (!ready || repo.state.value.call?.sessionId != sessionId) return@withLock
        if (enable && screenPublished(media)) return@withLock
        if (!enable && !screenPublished(media)) return@withLock
        screenBusy = true
        mediaError = null
        publishMedia(media)
        try {
            if (enable && agentIdentity != null) check(agentVideoAllowed && agentMembership(media, true))
            val params = if (enable) ScreenCaptureParams(requireNotNull(permissionData),
                notificationId = 4302, notification = screenNotification(), onStop = {
                    scope.launch { if (room === media) publishMedia(media) }
                }) else null
            check(media.localParticipant.setScreenShareEnabled(enable, params))
            check(screenPublished(media) == enable)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            mediaError = "화면 공유 전환 실패 · 연결 및 화면 공유 권한을 확인하세요."
            if (enable) runCatching { media.localParticipant.setScreenShareEnabled(false) }
            if (screenPublished(media)) {
                // A partially published screen must not remain live after failed stop.
                fail("화면 공유 종료 확인 실패 · 통화를 중단합니다.")
            }
        } finally {
            screenBusy = false
            if (room === media) publishMedia(media)
        }
    }

    private fun screenNotification(): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("dominion_screen_share", "화면 공유", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 3, Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_SCREEN
            putExtra("sessionId", sessionId)
            putExtra("enable", false)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "dominion_screen_share")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DOMINION 화면 공유 중")
            .setContentText("현재 통화방에 화면을 송출하고 있습니다.")
            .setOngoing(true)
            .addAction(R.drawable.ic_notification, "화면 공유 중지", stop)
            .build()
    }

    private fun publishMedia(media: Room) {
        val current = repo.state.value.call
        if (current?.sessionId != sessionId) return
        val local = media.localParticipant.getTrackPublication(Track.Source.CAMERA)
            ?.takeUnless { it.muted }?.track as? VideoTrack
        val remote = media.remoteParticipants.values.asSequence().mapNotNull {
            it.getTrackPublication(Track.Source.CAMERA)?.takeUnless { p -> p.muted }?.track as? VideoTrack
        }.firstOrNull()
        val remoteScreen = media.remoteParticipants.values.asSequence().mapNotNull {
            it.getTrackPublication(Track.Source.SCREEN_SHARE)?.takeUnless { p -> p.muted }?.track as? VideoTrack
        }.firstOrNull()
        val remoteLevel = (media.remoteParticipants.values.maxOfOrNull { it.audioLevel } ?: 0f)
            .coerceIn(0f, 1f)
        val level = maxOf(media.localParticipant.audioLevel, remoteLevel).coerceIn(0f, 1f)
        _mediaState.value = CallMediaState(sessionId, media, local, remote, level,
            media.audioSwitchHandler?.selectedAudioDevice is AudioDevice.Speakerphone, cameraBusy, mediaError,
            sharingScreen = screenPublished(media), remoteScreen = remoteScreen, screenBusy = screenBusy,
            localScreen = media.localParticipant.getTrackPublication(Track.Source.SCREEN_SHARE)
                ?.takeUnless { it.muted }?.track as? VideoTrack, remoteAudioLevel = remoteLevel)
    }

    private fun agentMembership(media: Room, requirePresent: Boolean): Boolean {
        val agent = agentIdentity ?: return true
        val peers = media.remoteParticipants.values.map { it.identity?.value.orEmpty() }
        return com.dominion.mobile.data.remote.RealtimeCallGrant.matchesParticipants(agent, peers, requirePresent)
    }

    private fun peerState(media: Room) {
        if (agentIdentity != null && !agentMembership(media, requirePresent = true)) {
            fail("Realtime AI participant missing or unauthorized")
            return
        }
        phase(if (media.remoteParticipants.isEmpty()) CallPhase.WAITING_PEER else CallPhase.PEER_PRESENT)
    }

    private fun phase(value: CallPhase, error: String? = null) {
        repo.update { state ->
            val call = state.call
            if (call == null || call.sessionId != sessionId || call.phase == CallPhase.FAILED) state
            else state.copy(call = call.copy(phase = value, error = error,
                startedAt = if (value == CallPhase.PEER_PRESENT && call.connectedAt == null)
                    System.currentTimeMillis() else call.startedAt,
                connectedAt = call.connectedAt ?: if (value == CallPhase.PEER_PRESENT) System.currentTimeMillis() else null))
        }
    }

    private fun fail(message: String) {
        phase(CallPhase.FAILED, message)
        stopSelf()
    }

    private fun foreground(camera: Boolean = false) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Voice calls", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).apply {
            action = "com.dominion.mobile.action.OPEN_CALL"
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end = PendingIntent.getService(this, 2, Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_END
            putExtra("sessionId", sessionId)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("음성 세션 · Voice session")
            .setContentIntent(open)
            .addAction(R.drawable.ic_notification, "종료 · End", end)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
        ServiceCompat.startForeground(this, 4301, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
            (if (camera) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0))
    }

    override fun onDestroy() {
        ready = false
        scope.cancel()
        if (_mediaState.value.sessionId == sessionId) _mediaState.value = CallMediaState()
        room?.let { media ->
            runCatching { media.disconnect() }
            runCatching { media.release() }
        }
        room = null
        if ((application as DominionApplication).containerReady) repo.update { state ->
            if (state.call?.sessionId == sessionId && state.call?.phase != CallPhase.FAILED)
                com.dominion.mobile.data.CallHistory.finish(state) else state
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "dominion_call"
        private const val ACTION_END = "com.dominion.mobile.action.END_CALL"
        private const val ACTION_CAMERA = "com.dominion.mobile.action.CALL_CAMERA"
        private const val ACTION_SPEAKER = "com.dominion.mobile.action.CALL_SPEAKER"
        private const val ACTION_SCREEN = "com.dominion.mobile.action.CALL_SCREEN"
        private val _mediaState = MutableStateFlow(CallMediaState())
        val mediaState = _mediaState.asStateFlow()
        fun camera(context: Context, enabled: Boolean) {
            context.startService(Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_CAMERA
                putExtra("enable", enabled)
            })
        }
        fun screen(context: Context, expectedSession: String, enabled: Boolean, permissionData: Intent? = null) {
            require(!enabled || permissionData != null)
            context.startService(Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_SCREEN
                putExtra("sessionId", expectedSession)
                putExtra("enable", enabled)
                if (enabled) putExtra("projectionData", permissionData)
            })
        }
        fun speaker(context: Context, enabled: Boolean) {
            context.startService(Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_SPEAKER
                putExtra("enable", enabled)
            })
        }
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CallForegroundService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
