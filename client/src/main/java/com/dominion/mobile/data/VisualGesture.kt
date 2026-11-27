package com.dominion.mobile.data

import java.security.MessageDigest

/** Coordinates in thousandths of the captured window, not the physical display. */
data class VisualGesture(val frame: String, val x: Int, val y: Int,
    val endX: Int? = null, val endY: Int? = null) {
    companion object {
        fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        fun parse(action: String, value: String?): VisualGesture? = runCatching {
            val fields = requireNotNull(value).split(':')
            require(fields.size == if (action == "tap_point") 3 else 5)
            require(action in setOf("tap_point","swipe_point"))
            require(fields[0].matches(Regex("[a-f0-9]{64}")))
            val coordinates = fields.drop(1).map { raw ->
                require(raw.matches(Regex("[0-9]{1,4}")))
                raw.toInt().also { require(it in 25..975) }
            }
            if (coordinates.size == 4)
                require(kotlin.math.abs(coordinates[0]-coordinates[2]) +
                    kotlin.math.abs(coordinates[1]-coordinates[3]) >= 100)
            VisualGesture(fields[0],coordinates[0],coordinates[1],coordinates.getOrNull(2),coordinates.getOrNull(3))
        }.getOrNull()
    }
}
