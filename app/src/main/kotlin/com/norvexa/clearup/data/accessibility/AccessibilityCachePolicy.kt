package com.norvexa.clearup.data.accessibility

import java.util.Locale

object AccessibilityCachePolicy {
    private val packagePattern = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")

    private val allowedSettingsPackages = setOf(
        "com.android.settings",
        "com.google.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.huawei.systemmanager",
        "com.hihonor.systemmanager",
        "com.vivo.permissionmanager",
        "com.iqoo.secure",
    )

    private val clearCacheLabels = setOf(
        "clear cache",
        "очистить кэш",
        "очистить кеш",
        "очистити кеш",
        "wyczyść pamięć podręczną",
        "tøm hurtigbuffer",
        "tøm buffer",
        "cache leeren",
        "vider le cache",
        "borrar caché",
        "svuota cache",
        "limpar cache",
        "önbelleği temizle",
    )

    private val storageLabels = setOf(
        "storage & cache",
        "storage and cache",
        "storage",
        "хранилище и кэш",
        "хранилище и кеш",
        "память и кэш",
        "память и кеш",
        "хранилище",
        "память",
        "сховище й кеш",
        "сховище та кеш",
        "пам'ять і кеш",
        "пам’ять і кеш",
        "сховище",
        "пам'ять",
        "пам’ять",
        "pamięć i pamięć podręczna",
        "pamięć",
        "lagring og hurtigbuffer",
        "lagring",
        "speicher und cache",
        "speicher",
        "stockage et cache",
        "stockage",
        "almacenamiento y caché",
        "almacenamiento",
        "archiviazione e cache",
        "archiviazione",
        "armazenamento e cache",
        "armazenamento",
        "depolama ve önbellek",
        "depolama",
    )

    private val dangerousLabels = setOf(
        "clear storage",
        "clear data",
        "delete data",
        "erase data",
        "очистить хранилище",
        "стереть данные",
        "удалить данные",
        "очистити сховище",
        "стерти дані",
        "видалити дані",
        "wyczyść dane",
        "slett data",
        "daten löschen",
        "effacer les données",
        "borrar datos",
        "cancella dati",
        "limpar dados",
        "verileri temizle",
    )

    private val clearCacheViewIds = setOf(
        "clear_cache",
        "clear_cache_button",
        "button_clear_cache",
        "cache_clear",
    )

    private val storageViewIds = setOf(
        "storage_settings",
        "storage_and_cache",
        "storage_usage",
        "app_storage",
    )

    fun isValidPackageName(packageName: String): Boolean =
        packagePattern.matches(packageName)

    fun isAllowedSettingsPackage(packageName: String?): Boolean =
        packageName != null && packageName in allowedSettingsPackages

    fun isExactClearCacheLabel(value: CharSequence?): Boolean =
        normalize(value) in clearCacheLabels

    fun isExactStorageLabel(value: CharSequence?): Boolean =
        normalize(value) in storageLabels

    fun isDangerousLabel(value: CharSequence?): Boolean =
        normalize(value) in dangerousLabels

    fun isClearCacheViewId(viewId: String?): Boolean =
        resourceEntry(viewId) in clearCacheViewIds

    fun isStorageViewId(viewId: String?): Boolean =
        resourceEntry(viewId) in storageViewIds

    private fun normalize(value: CharSequence?): String = value
        ?.toString()
        ?.replace('\u00A0', ' ')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.replace(Regex("\\s+"), " ")
        .orEmpty()

    private fun resourceEntry(viewId: String?): String = viewId
        ?.substringAfterLast('/')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        .orEmpty()
}
