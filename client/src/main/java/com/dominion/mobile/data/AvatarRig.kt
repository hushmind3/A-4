package com.dominion.mobile.data

import java.security.MessageDigest

/** Rig binds authored skin weights to the exact original OBJ bytes and expanded
 * triangle-corner order. Head is parent of jaw; all coordinates are normalized. */
data class AvatarRigSpec(val schema: String? = null, val baseSha256: String? = null,
    val headPivot: FloatArray? = null, val jawPivot: FloatArray? = null,
    val headWeights: FloatArray? = null, val jawWeights: FloatArray? = null) {
    fun validated(base: ByteArray, vertices: Int): AvatarRig {
        require(schema == "dominion-avatar-rig/v1" && baseSha256 == sha256(base))
        require(vertices in 3..180000)
        fun pivot(v: FloatArray?): FloatArray = requireNotNull(v).also {
            require(it.size == 3 && it.all { x -> x.isFinite() && x in -2f..2f })
        }.copyOf()
        val head = requireNotNull(headWeights)
        val jaw = requireNotNull(jawWeights)
        require(head.size == vertices && jaw.size == vertices)
        val weights = FloatArray(vertices * 2)
        var hasHead = false
        var hasJaw = false
        for (i in 0 until vertices) {
            val h = head[i]; val j = jaw[i]
            require(h.isFinite() && j.isFinite() && h in 0f..1f && j in 0f..1f && h + j <= 1.000001f)
            // Clamp tiny floating point drift before the GPU sees the weights.
            weights[i * 2] = h
            weights[i * 2 + 1] = j.coerceAtMost(1f - h)
            hasHead = hasHead || h > 0f
            hasJaw = hasJaw || j > 0f
        }
        require(hasHead || hasJaw)
        return AvatarRig(pivot(headPivot), pivot(jawPivot), weights)
    }
    companion object {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

data class AvatarRig(val headPivot: FloatArray, val jawPivot: FloatArray, val weights: FloatArray)
