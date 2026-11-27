package com.dominion.mobile.data

import org.junit.Assert.*
import org.junit.Test

class ObjMorphTest {
    private val base = "v 0 0 0\nv 2 0 0\nv 0 2 0\nf 1 2 3\n"
    @Test fun targetUsesOriginalCoordinateSystem() {
        val open = base.replace("v 0 2 0", "v 0 1 0")
        val result = ObjAvatarMesh.morph(base, open)
        assertArrayEquals(ObjAvatarMesh.parse(base), result.base, 0f)
        assertEquals(1f, result.base[13], 0f)
        assertEquals(0f, result.open[13], 0f)
        assertEquals(result.base[0], result.open[0], 0f)
    }
    @Test fun rejectsDifferentTopologyAndDegeneracy() {
        assertTrue(runCatching { ObjAvatarMesh.morph(base, base.replace("1 2 3", "1 3 2")) }.isFailure)
        assertTrue(runCatching { ObjAvatarMesh.morph(base, base.replace("v 0 2 0", "v 0 0 0")) }.isFailure)
        assertTrue(runCatching { ObjAvatarMesh.morph(base, base) }.isFailure)
    }
}
