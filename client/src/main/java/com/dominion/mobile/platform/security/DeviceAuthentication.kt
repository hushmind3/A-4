package com.dominion.mobile.platform.security

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

private val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
    BiometricManager.Authenticators.DEVICE_CREDENTIAL

fun deviceAuthenticationAvailable(context: Context): Boolean =
    context.getSystemService(BiometricManager::class.java)?.canAuthenticate(authenticators) ==
        BiometricManager.BIOMETRIC_SUCCESS

/** UI success is emitted solely by Android's authenticated callback. */
@Composable
fun rememberDeviceAuthentication(title: String, onSuccess: () -> Unit,
    onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val success = rememberUpdatedState(onSuccess)
    val failure = rememberUpdatedState(onError)
    val active = remember { arrayOfNulls<CancellationSignal>(1) }
    DisposableEffect(Unit) { onDispose {
        val previous = active[0]
        active[0] = null
        previous?.cancel()
    } }
    return {
        if (active[0] == null) {
            if (!deviceAuthenticationAvailable(context)) {
                failure.value("기기 잠금 또는 지문을 먼저 설정하세요. / Set device authentication first.")
            } else {
                val signal = CancellationSignal()
                active[0] = signal
                try {
                    BiometricPrompt.Builder(context)
                        .setTitle(title)
                        .setAllowedAuthenticators(authenticators)
                        .build()
                        .authenticate(signal, context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                if (active[0] === signal) {
                                    active[0] = null
                                    success.value()
                                }
                            }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                if (active[0] === signal) {
                                    active[0] = null
                                    failure.value(errString.toString())
                                }
                            }
                        })
                } catch (_: Exception) {
                    active[0] = null
                    failure.value("기기 인증을 시작하지 못했습니다. / Unable to start device authentication.")
                }
            }
        }
    }
}
