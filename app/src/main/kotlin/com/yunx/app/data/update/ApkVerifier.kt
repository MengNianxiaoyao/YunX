package com.yunx.app.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object ApkVerifier {
    fun verify(context: Context, uri: Uri, expectedSha256: String? = null): Boolean = runCatching {
        val temp = File.createTempFile("verify-", ".apk", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
                ?: return false
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
            val archive = context.packageManager.getPackageArchiveInfo(temp.path, flags) ?: return false
            val current = context.packageManager.getPackageInfo(context.packageName, flags)
            val archiveSignatures = if (Build.VERSION.SDK_INT >= 28) {
                archive.signingInfo?.apkContentsSigners.orEmpty()
            } else {
                @Suppress("DEPRECATION") archive.signatures.orEmpty()
            }
            val currentSignatures = if (Build.VERSION.SDK_INT >= 28) {
                current.signingInfo?.apkContentsSigners.orEmpty()
            } else {
                @Suppress("DEPRECATION") current.signatures.orEmpty()
            }
            if (archiveSignatures.isEmpty() || currentSignatures.isEmpty()) return false
            if (archiveSignatures.map { sha256(it.toByteArray()) }.toSet() !=
                currentSignatures.map { sha256(it.toByteArray()) }.toSet()) return false
            expectedSha256 == null || sha256(temp.inputStream()) == expectedSha256.lowercase()
        } finally {
            temp.delete()
        }
    }.getOrDefault(false)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(input: InputStream): String = input.use {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
