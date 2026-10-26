package com.kodastream.app.data

import android.content.Context
import com.kodastream.app.BuildConfig
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** A newer release found on GitHub. */
data class AppUpdate(
    /** Version without the leading "v", for example "0.3.0". */
    val version: String,
    val notes: String?,
    /** The APK asset when the release has one, otherwise its release page. */
    val url: String,
)

/**
 * Checks the project's GitHub releases at most once a day. Every failure is
 * silent: an app start must never wait on, or complain about, the network.
 */
object UpdateChecker {
    private const val OWNER_REPO = "mo7AmMeD64/cloudSTREAM3"
    private const val LATEST_URL = "https://api.github.com/repos/$OWNER_REPO/releases/latest"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    suspend fun check(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - AppPrefs.lastUpdateCheck(context) < CHECK_INTERVAL_MS) return@withContext null

        val json = try {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .build()
            app.baseClient.newCall(request).execute().use { response ->
                // A rate limit or a server error also counts as a check, so a
                // failing API is not retried on every single launch.
                AppPrefs.setLastUpdateCheck(context, now)
                if (!response.isSuccessful) return@withContext null
                JSONObject(response.body?.string() ?: return@withContext null)
            }
        } catch (t: Throwable) {
            // Offline or timed out: try again on the next start.
            return@withContext null
        }

        val version = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        if (version.isBlank() || !isNewerVersion(version, BuildConfig.VERSION_NAME)) return@withContext null
        if (AppPrefs.skippedUpdateVersion(context) == version) return@withContext null

        val releaseUrl = json.optString("html_url").takeIf { it.startsWith("https://") } ?: return@withContext null
        AppUpdate(
            version = version,
            notes = cleanNotes(json.optString("body")),
            url = apkUrl(json) ?: releaseUrl,
        )
    }

    private fun apkUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url")
            if (url.startsWith("https://")) return url
        }
        return null
    }

    /** Keeps the release notes short and readable as plain text. */
    private fun cleanNotes(body: String): String? {
        val link = Regex("\\[([^]]+)]\\([^)]*\\)")
        val lines = body.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.startsWith("#") || it.contains("Full Changelog", ignoreCase = true) }
            .map { line ->
                line.replace(link) { it.groupValues[1] }
                    .removePrefix("* ")
                    .removePrefix("- ")
                    .replace("**", "")
                    .replace("`", "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .take(6)
            .toList()
        return lines.joinToString("\n").take(400).trim().ifBlank { null }
    }

    /** Numeric comparison, so "0.10.0" is newer than "0.9.9". */
    internal fun isNewerVersion(candidate: String, current: String): Boolean {
        val new = versionParts(candidate)
        val old = versionParts(current)
        for (i in 0 until maxOf(new.size, old.size)) {
            val a = new.getOrElse(i) { 0 }
            val b = old.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun versionParts(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+', '_')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
