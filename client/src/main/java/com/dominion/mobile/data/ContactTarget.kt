package com.dominion.mobile.data

object ContactTarget {
    fun phone(value: String): String {
        val normalized = value.trim().replace(Regex("[\\s().-]"), "")
        require(normalized.matches(Regex("\\+?[0-9]{3,20}")))
        return normalized
    }

    fun email(value: String): String {
        val normalized = value.trim()
        require(normalized.length <= 254 &&
            normalized.matches(Regex("[A-Za-z0-9.!#$%&'*+/=_^|~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}")) &&
            !normalized.contains(".."))
        return normalized
    }
}
