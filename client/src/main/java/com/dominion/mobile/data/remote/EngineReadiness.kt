package com.dominion.mobile.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

enum class ProbeState(val label: String) {
    OK("조회 성공"), NOT_READY("미준비"), DENIED("인증·권한 거절"),
    UNAVAILABLE("연결 또는 서버 오류"), UNSUPPORTED("API 미지원"), SKIPPED("미확인")
}

data class EngineReadiness(
    val authenticated: ProbeState, val rooms: ProbeState = ProbeState.SKIPPED,
    val providers: ProbeState = ProbeState.SKIPPED, val tools: ProbeState = ProbeState.SKIPPED,
    val whisper: ProbeState = ProbeState.SKIPPED, val piper: ProbeState = ProbeState.SKIPPED,
    val roomCount: Int? = null, val providerCount: Int? = null, val toolCount: Int? = null
)

private data class Probe<T>(val state: ProbeState, val value: T? = null)
private suspend fun <T> probe(block: suspend () -> T): Probe<T> = try {
    Probe(ProbeState.OK, block())
} catch (e: CancellationException) { throw e }
catch (e: HttpException) {
    Probe(when (e.code()) {
        401, 403 -> ProbeState.DENIED
        404, 405 -> ProbeState.UNSUPPORTED
        else -> ProbeState.UNAVAILABLE
    })
} catch (_: Exception) { Probe(ProbeState.UNAVAILABLE) }

/** Read-only checks. No model request, tool execution, token issuance or write. */
suspend fun probeEngine(api: DominionApi): EngineReadiness = coroutineScope {
    val auth = probe { requireNotNull(api.me().data) }
    if (auth.state != ProbeState.OK) return@coroutineScope EngineReadiness(auth.state)
    val rooms = async { probe { api.engineRooms() } }
    val providers = async { probe { api.engineProviders().count { it.enabled != false } } }
    val tools = async { probe { api.engineTools() } }
    val voice = async { probe { api.voiceStatus() } }
    val r = rooms.await(); val p = providers.await(); val t = tools.await(); val v = voice.await()
    fun voiceState(available: Boolean?): ProbeState =
        if (v.state != ProbeState.OK) v.state else if (available == true) ProbeState.OK else ProbeState.NOT_READY
    EngineReadiness(auth.state, r.state, p.state, t.state,
        voiceState(v.value?.whisper?.available), voiceState(v.value?.piper?.available),
        r.value?.size, p.value, t.value?.size)
}
