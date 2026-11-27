package com.dominion.mobile.data.remote

data class VoiceCapability(val available: Boolean?)
data class EngineVoiceStatus(val whisper: VoiceCapability?, val piper: VoiceCapability?)
data class VoiceTranscript(val text: String?)
