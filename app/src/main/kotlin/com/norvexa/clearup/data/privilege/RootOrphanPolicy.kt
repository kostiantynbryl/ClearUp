package com.norvexa.clearup.data.privilege

import com.norvexa.clearup.domain.model.OrphanLocation

object RootOrphanPolicy {
    val allowedRoots: List<String> = listOf(
        "/data/user/0",
        "/data/data",
        "/storage/emulated/0/Android/data",
        "/storage/emulated/0/Android/media",
        "/storage/emulated/0/Android/obb",
    )

    private val packagePattern = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")

    fun isValidPackageName(packageName: String): Boolean =
        packagePattern.matches(packageName)

    fun expectedPaths(packageName: String): Set<String> {
        require(isValidPackageName(packageName)) { "Invalid package name" }
        return allowedRoots.mapTo(linkedSetOf()) { root -> "$root/$packageName" }
    }

    fun isAllowedPath(packageName: String, path: String): Boolean =
        isValidPackageName(packageName) && path in expectedPaths(packageName)

    fun locationForRoot(root: String): OrphanLocation? = when (root) {
        "/data/user/0" -> OrphanLocation.INTERNAL_USER
        "/data/data" -> OrphanLocation.INTERNAL_LEGACY
        "/storage/emulated/0/Android/data" -> OrphanLocation.EXTERNAL_DATA
        "/storage/emulated/0/Android/media" -> OrphanLocation.EXTERNAL_MEDIA
        "/storage/emulated/0/Android/obb" -> OrphanLocation.EXTERNAL_OBB
        else -> null
    }
}
