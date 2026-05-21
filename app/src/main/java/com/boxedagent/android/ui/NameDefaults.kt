package com.boxedagent.android.ui

private val TrailingNumberRegex = Regex("^(.*?)(\\d+)(\\s*)$")

internal fun incrementTrailingNumberName(name: String): String? {
    if (name.isBlank()) return null
    val match = TrailingNumberRegex.matchEntire(name) ?: return null
    return match.groupValues[1] + incrementDecimalString(match.groupValues[2]) + match.groupValues[3]
}

internal fun nextReplicatedName(name: String, fallbackBase: String, suffix: String, maxLength: Int = 80): String {
    incrementTrailingNumberName(name)?.let { return it }
    val base = name.trim().ifBlank { fallbackBase }
    val baseLimit = (maxLength - suffix.length).coerceAtLeast(1)
    return "${base.take(baseLimit)}$suffix"
}

private fun incrementDecimalString(digits: String): String {
    val chars = digits.toCharArray()
    var carry = 1
    for (i in chars.indices.reversed()) {
        val next = (chars[i] - '0') + carry
        chars[i] = ('0'.code + (next % 10)).toChar()
        carry = next / 10
        if (carry == 0) break
    }
    return if (carry > 0) "1${String(chars)}" else String(chars)
}
