package com.dominion.mobile.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dominion.mobile.data.local.VaultRecordCipher
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultRecordCipherTest {
    @Test fun actualKeystoreRoundTripRejectsTamperedAndCrossRecordCiphertext() {
        val scope = "test-" + UUID.randomUUID()
        val cipher = VaultRecordCipher(scope)
        val secret = """{"secret":"한국어 비밀번호"}"""
        try {
            val sealed = cipher.seal("record-a", secret)
            assertTrue(cipher.isSealed(sealed))
            assertFalse(sealed.contains("비밀번호"))
            assertNotEquals(sealed, cipher.seal("record-a", secret))
            assertEquals(secret, VaultRecordCipher(scope).open("record-a", sealed))
            assertTrue(runCatching { cipher.open("record-b", sealed) }.isFailure)
            val parts = sealed.split(':')
            val bytes = java.util.Base64.getDecoder().decode(parts.last())
            bytes[0] = (bytes[0].toInt() xor 1).toByte()
            val corrupted = parts.dropLast(1).joinToString(":") + ":" +
                java.util.Base64.getEncoder().encodeToString(bytes)
            assertTrue(runCatching { cipher.open("record-a", corrupted) }.isFailure)
        } finally {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry("dominion.vault.$scope.aes.v1")
        }
    }
}
