package com.dominion.mobile.data
import org.junit.Assert.*
import org.junit.Test

class ObjAvatarMeshTest {
    @Test fun actualGeometryAndNormals() {
        val mesh=ObjAvatarMesh.parse("v 0 0 0\nv 4 0 0\nv 0 4 0\nvn 0 0 1\nf -3//1 -2//1 -1//1")
        assertEquals(18,mesh.size)
        assertEquals(-1f,mesh[0],0.001f)
        assertEquals(1f,mesh[5],0.001f)
        assertEquals(1f,mesh[6],0.001f)
    }
    @Test fun rejectsInvalidAndUnsupportedMeshes() {
        assertTrue(runCatching { ObjAvatarMesh.parse("v NaN 0 0\nf 1 2 3") }.isFailure)
        assertTrue(runCatching { ObjAvatarMesh.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 5") }.isFailure)
        assertTrue(runCatching { ObjAvatarMesh.parse("v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3 1") }.isFailure)
    }
}
