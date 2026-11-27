package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class AvatarRigTest {
    private val base = "original".toByteArray()
    private val rig = AvatarRigSpec("dominion-avatar-rig/v1",AvatarRigSpec.sha256(base),
        floatArrayOf(0f,0.5f,0f),floatArrayOf(0f,0.2f,0f),
        floatArrayOf(1f,0.5f,0f),floatArrayOf(0f,0.5f,1f))
    @Test fun acceptsAuthoredSkinWeights() {
        assertArrayEquals(floatArrayOf(1f,0f,0.5f,0.5f,0f,1f),rig.validated(base,3).weights,0f)
    }
    @Test fun rejectsDifferentMeshAndInvalidWeights() {
        assertTrue(runCatching { rig.validated("other".toByteArray(),3) }.isFailure)
        assertTrue(runCatching { rig.validated(base,4) }.isFailure)
        assertTrue(runCatching { rig.copy(jawWeights=floatArrayOf(1f,0f,0f)).validated(base,3) }.isFailure)
        assertTrue(runCatching { rig.copy(headWeights=floatArrayOf(Float.NaN,0f,0f)).validated(base,3) }.isFailure)
    }
}
