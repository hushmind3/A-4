package com.dominion.mobile.platform.audio

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

/** Owns the actual media room; UI navigation does not own microphone lifetime. */
class CallForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repo get() = (application as DominionApplication).container.repository
    private var room: Room? = null
    private var sessionId: String? = null
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_END) {
            repo.update { it.copy(call = null) }
            stopSelf()
            return START_NOT_STICKY
        }
        val call = repo.state.value.call
        if (call == null || repo.isDemo || call.phase == CallPhase.FAILED) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        sessionId = call.sessionId
        try {
            foreground()
        } catch (_: Exception) {
            fail("Microphone foreground service could not start")
            return START_NOT_STICKY
        }
        phase(CallPhase.CONNECTING)
        job = scope.launch {
            try {
                val credentials = withTimeout(30_000) { repo.requestVoiceCall(call.conversationId) }
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
                                    if (repo.state.value.call?.phase != CallPhase.CONNECTING) peerState(media)
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
                    check(media.localParticipant.setMicrophoneEnabled(true)) { "Microphone publish failed" }
                    peerState(media)
                    var appliedMuted = false
                    repo.state.collect { state ->
                        val current = state.call
                        if (current?.sessionId != sessionId || current.phase == CallPhase.FAILED) {
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

    private fun peerState(media: Room) {
        phase(if (media.remoteParticipants.isEmpty()) CallPhase.WAITING_PEER else CallPhase.PEER_PRESENT)
    }

    private fun phase(value: CallPhase, error: String? = null) {
        repo.update { state ->
            val call = state.call
            if (call == null || call.sessionId != sessionId || call.phase == CallPhase.FAILED) state
            else state.copy(call = call.copy(phase = value, error = error,
                startedAt = if (value == CallPhase.PEER_PRESENT && call.phase != CallPhase.PEER_PRESENT &&
                    call.phase != CallPhase.RECONNECTING) System.currentTimeMillis() else call.startedAt))
        }
    }

    private fun fail(message: String) {
        phase(CallPhase.FAILED, message)
        stopSelf()
    }

    private fun foreground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Voice calls", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).apply {
            action = "com.dominion.mobile.action.OPEN_CALL"
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end = PendingIntent.getService(this, 2, Intent(this, CallForegroundService::class.java).apply {
            action = ACTION_END
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
        ServiceCompat.startForeground(this, 4301, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    override fun onDestroy() {
        scope.cancel()
        room?.let { media ->
            runCatching { media.disconnect() }
            runCatching { media.release() }
        }
        room = null
        repo.update { state ->
            if (state.call?.sessionId == sessionId && state.call?.phase != CallPhase.FAILED)
                state.copy(call = null) else state
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "dominion_call"
        private const val ACTION_END = "com.dominion.mobile.action.END_CALL"
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CallForegroundService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
