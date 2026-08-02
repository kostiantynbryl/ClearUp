package com.norvexa.clearup.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.norvexa.clearup.BuildConfig
import com.norvexa.clearup.core.util.VersionComparator
import com.norvexa.clearup.domain.model.ReleaseUpdate
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.coroutineContext

class UpdateRepository(private val context: Context) {
    private val signatureVerifier = ApkSignatureVerifier(context)

    suspend fun checkLatest(): ReleaseUpdate? = withContext(Dispatchers.IO) {
        val endpoint = "https://api.github.com/repos/${BuildConfig.UPDATE_REPOSITORY}/releases/latest"
        val json = JSONObject(readText(endpoint, MAX_METADATA_BYTES))
        val tag = json.optString("tag_name").trim()
        if (tag.isBlank() || VersionComparator.compare(tag, BuildConfig.VERSION_NAME) <= 0) {
            return@withContext null
        }

        val assets = json.optJSONArray("assets") ?: return@withContext null
        var apkName = ""
        var apkUrl = ""
        val checksumAssets = mutableMapOf<String, String>()
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            when {
                name.endsWith(".apk", ignoreCase = true) &&
                    !name.contains("debug", ignoreCase = true) &&
                    !name.contains("unsigned", ignoreCase = true) &&
                    apkUrl.isBlank() -> {
                    apkName = name
                    apkUrl = url
                }
                name.endsWith(".sha256", ignoreCase = true) -> {
                    checksumAssets[name.lowercase(Locale.ROOT)] = url
                }
            }
        }
        check(apkUrl.isNotBlank()) { "В релизе нет подписанного APK" }

        val expectedChecksumName = "$apkName.sha256".lowercase(Locale.ROOT)
        val checksumUrl = checksumAssets[expectedChecksumName]
            ?: checksumAssets.entries.firstOrNull { (name, _) ->
                name.removeSuffix(".sha256") == apkName.lowercase(Locale.ROOT)
            }?.value
            ?: error("Для APK отсутствует обязательный файл .sha256")

        ReleaseUpdate(
            tag = tag,
            title = json.optString("name").ifBlank { tag },
            notes = json.optString("body").take(MAX_NOTES_CHARS),
            publishedAt = json.optString("published_at"),
            apkName = apkName,
            apkUrl = apkUrl,
            checksumUrl = checksumUrl,
        )
    }

    suspend fun downloadVerified(update: ReleaseUpdate): File = withContext(Dispatchers.IO) {
        val checksumText = readText(update.checksumUrl, MAX_CHECKSUM_BYTES)
        val expectedHash = CHECKSUM_REGEX.find(checksumText)?.value?.lowercase(Locale.ROOT)
            ?: error("Файл SHA-256 имеет неверный формат")

        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, update.apkName.replace(UNSAFE_FILE_CHARS, "_"))
        val temporary = File(directory, "${target.name}.part")
        temporary.delete()
        try {
            download(update.apkUrl, temporary)

            val actualHash = sha256(temporary)
            check(actualHash == expectedHash) { "SHA-256 загруженного APK не совпадает" }

            when (val verification = signatureVerifier.verify(temporary)) {
                VerificationResult.Success -> Unit
                is VerificationResult.Failure -> error(verification.reason)
            }

            target.delete()
            check(temporary.renameTo(target)) { "Не удалось сохранить проверенный APK" }
            target
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    fun createInstallIntent(apkFile: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            apkFile,
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun readText(url: String, maxBytes: Int): String {
        val connection = open(url)
        return try {
            checkSuccess(connection)
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    check(output.size() + read <= maxBytes) { "Ответ сервера слишком большой" }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            bytes.toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(url: String, target: File) {
        val connection = open(url)
        try {
            checkSuccess(connection)
            val declaredSize = connection.contentLengthLong
            if (declaredSize > MAX_APK_BYTES) error("APK превышает допустимый размер")
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        check(total <= MAX_APK_BYTES) { "APK превышает допустимый размер" }
                        output.write(buffer, 0, read)
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ClearUp/${BuildConfig.VERSION_NAME}")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

    private fun checkSuccess(connection: HttpURLConnection) {
        if (connection.responseCode !in 200..299) {
            error("Сервер обновлений вернул код ${connection.responseCode}")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val MAX_METADATA_BYTES = 1 * 1024 * 1024
        private const val MAX_CHECKSUM_BYTES = 16 * 1024
        private const val MAX_APK_BYTES = 300L * 1024L * 1024L
        private const val MAX_NOTES_CHARS = 20_000
        private val CHECKSUM_REGEX = Regex("(?i)\\b[0-9a-f]{64}\\b")
        private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")
    }
}
