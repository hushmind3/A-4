package com.dominion.mobile.data
import com.dominion.mobile.data.model.CharacterShape
import org.junit.Assert.*
import org.junit.Test

class AvatarMeshTest {
    @Test fun allSixShapesProduceFiniteNativeTriangles() {
        val meshes=CharacterShape.entries.map { shape -> AvatarMesh.build(shape) }
        meshes.forEach { mesh ->
            assertTrue(mesh.isNotEmpty())
            assertEquals(0,mesh.size%18)
            assertTrue(mesh.all { it.isFinite() })
            assertTrue(mesh.size<100000)
        }
        assertEquals(meshes.size,meshes.map { it.contentHashCode() }.distinct().size)
    }
}
