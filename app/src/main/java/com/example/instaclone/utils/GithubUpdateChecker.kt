package com.example.instaclone.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GithubRelease(
    val tagName: String,
    val releaseNotes: String?,
    val htmlUrl: String,
    val apkUrl: String?
)

object GithubUpdateChecker {
    private const val GITHUB_OWNER = "Yahoo1"
    private const val GITHUB_REPO = "InstaClone"

    suspend fun checkForUpdate(context: Context): GithubRelease? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseString)

                val tagName = json.getString("tag_name") // e.g. "v1.1" or "1.1"
                val body = json.optString("body", "No release notes provided.")
                val htmlUrl = json.getString("html_url")

                // Find APK asset download URL if attached to release
                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                }

                val currentVersion = getAppVersionName(context)
                val cleanRemoteVersion = tagName.removePrefix("v")

                // Compare versions (simple string or semantic comparison)
                if (isVersionNewer(currentVersion, cleanRemoteVersion)) {
                    return@withContext GithubRelease(
                        tagName = tagName,
                        releaseNotes = body,
                        htmlUrl = htmlUrl,
                        apkUrl = apkUrl ?: htmlUrl // Fallback to release page if no direct APK asset
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun getAppVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private fun isVersionNewer(current: String, remote: String): Boolean {
        // Simple comparison: if versions differ string-wise or you can parse numbers
        // For robust semver comparison:
        val currentParts = current.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val remoteParts = remote.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }

        for (i in 0 until maxOf(currentParts.size, remoteParts.size)) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
