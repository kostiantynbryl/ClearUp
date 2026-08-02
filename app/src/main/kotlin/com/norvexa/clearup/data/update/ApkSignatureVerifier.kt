package com.norvexa.clearup.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

class ApkSignatureVerifier(private val context: Context) {
    fun verify(apkFile: File): VerificationResult {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: return VerificationResult.Failure("APK не распознан Android")
        if (archiveInfo.packageName != context.packageName) {
            return VerificationResult.Failure("Package name обновления не совпадает")
        }

        @Suppress("DEPRECATION")
        val installedInfo = packageManager.getPackageInfo(context.packageName, flags)
        val installedDigests = installedInfo.signatureDigests()
        val archiveDigests = archiveInfo.signatureDigests()
        if (installedDigests.isEmpty() || archiveDigests.isEmpty()) {
            return VerificationResult.Failure("Не удалось прочитать подпись APK")
        }
        if (installedDigests != archiveDigests) {
            return VerificationResult.Failure("APK подписан другим сертификатом")
        }
        return VerificationResult.Success
    }

    private fun android.content.pm.PackageInfo.signatureDigests(): Set<String> {
        val packageSignatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            signatures?.toList().orEmpty()
        }
        return packageSignatures.mapTo(linkedSetOf()) { signature ->
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            digest.joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        }
    }
}

sealed interface VerificationResult {
    data object Success : VerificationResult
    data class Failure(val reason: String) : VerificationResult
}
