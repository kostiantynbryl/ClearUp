package com.norvexa.clearup.core.util

object VersionComparator {
    fun compare(candidate: String, current: String): Int {
        val left = Version.parse(candidate) ?: return 0
        val right = Version.parse(current) ?: return 0
        for (index in 0 until maxOf(left.parts.size, right.parts.size)) {
            val leftPart = left.parts.getOrElse(index) { 0 }
            val rightPart = right.parts.getOrElse(index) { 0 }
            if (leftPart != rightPart) return leftPart.compareTo(rightPart)
        }
        return when {
            left.preRelease == right.preRelease -> 0
            left.preRelease -> -1
            else -> 1
        }
    }

    private data class Version(
        val parts: List<Int>,
        val preRelease: Boolean,
    ) {
        companion object {
            fun parse(raw: String): Version? {
                val normalized = raw.trim().removePrefix("v").removePrefix("V")
                val main = normalized.substringBefore('-')
                val parts = main.split('.').map { it.toIntOrNull() ?: return null }
                if (parts.isEmpty()) return null
                return Version(parts = parts, preRelease = '-' in normalized)
            }
        }
    }
}
