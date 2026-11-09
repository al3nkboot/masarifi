package com.midroid.masarifi.util

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub Releases for a newer Masarifi APK. Pure HTTP to the public API;
 * no tokens, no analytics. Opens download URLs in the system browser.
 */
object UpdateChecker {
    const val API_LATEST = "https://api.github.com/repos/al3nkboot/masarifi/releases/latest"
    const val RELEASES_PAGE = "https://github.com/al3nkboot/masarifi/releases"
    private const val UA = "Masarifi-UpdateCheck/1.0 (+https://github.com/al3nkboot/masarifi)"

    data class Asset(val name: String, val downloadUrl: String)
    data class Release(
        val tag: String,
        val version: String,
        val name: String,
        val body: String,
        val htmlUrl: String,
        val assets: List<Asset>,
    )

    sealed class CheckResult {
        data class UpToDate(val current: String, val latest: String) : CheckResult()
        data class UpdateAvailable(val current: String, val release: Release, val assetUrl: String?) : CheckResult()
        data class Error(val kind: Kind, val detail: String? = null) : CheckResult() {
            enum class Kind { NETWORK, RATE_LIMIT, PARSE, OTHER }
        }
    }

    /** Compare two version strings like "2.5.1" / "v2.6.0". Returns <0 if a<b, 0 if equal, >0 if a>b. */
    fun compareVersions(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun parseVersion(raw: String): List<Int> =
        raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.', '_', ' ')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
            .ifEmpty { listOf(0) }

    /**
     * Picks the APK asset matching the installed flavor.
     * full → masarifi-vX.Y.Z.apk (no -nosms suffix); nosms → …-nosms.apk.
     */
    fun pickAssetUrl(assets: List<Asset>, smsEnabled: Boolean, version: String): String? {
        val ver = version.removePrefix("v").removePrefix("V")
        val wantNosms = !smsEnabled
        val apk = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val match = if (wantNosms) {
            apk.firstOrNull { it.name.contains("-nosms", ignoreCase = true) }
                ?: apk.firstOrNull { it.name.equals("masarifi-v$ver-nosms.apk", ignoreCase = true) }
        } else {
            apk.firstOrNull { !it.name.contains("-nosms", ignoreCase = true) }
                ?: apk.firstOrNull { it.name.equals("masarifi-v$ver.apk", ignoreCase = true) }
        }
        return match?.downloadUrl
    }

    fun parseReleaseJson(json: String): Release {
        val o = JSONObject(json)
        fun str(obj: JSONObject, key: String): String {
            if (!obj.has(key) || obj.isNull(key)) return ""
            return obj.get(key).toString()
        }
        val tag = str(o, "tag_name").ifBlank { str(o, "name") }
        val assetsArr = o.optJSONArray("assets")
        val assets = buildList {
            if (assetsArr != null) for (i in 0 until assetsArr.length()) {
                val a = assetsArr.getJSONObject(i)
                add(Asset(str(a, "name"), str(a, "browser_download_url")))
            }
        }
        return Release(
            tag = tag,
            version = tag.removePrefix("v").removePrefix("V"),
            name = str(o, "name").ifBlank { tag },
            body = str(o, "body"),
            htmlUrl = str(o, "html_url").ifBlank { RELEASES_PAGE },
            assets = assets,
        )
    }

    fun evaluate(currentVersion: String, release: Release, smsEnabled: Boolean): CheckResult {
        val cmp = compareVersions(currentVersion, release.version)
        return if (cmp >= 0) CheckResult.UpToDate(currentVersion, release.version)
        else CheckResult.UpdateAvailable(
            current = currentVersion,
            release = release,
            assetUrl = pickAssetUrl(release.assets, smsEnabled, release.version),
        )
    }

    fun fetchAndCompare(currentVersion: String, smsEnabled: Boolean): CheckResult {
        return try {
            val conn = (URL(API_LATEST).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", UA)
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                instanceFollowRedirects = true
            }
            val code = try { conn.responseCode } catch (e: Exception) {
                return CheckResult.Error(CheckResult.Error.Kind.NETWORK, e.message)
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = try {
                stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
            } catch (e: Exception) {
                return CheckResult.Error(CheckResult.Error.Kind.NETWORK, e.message)
            } finally {
                conn.disconnect()
            }
            when (code) {
                200 -> try {
                    evaluate(currentVersion, parseReleaseJson(body), smsEnabled)
                } catch (e: Exception) {
                    CheckResult.Error(CheckResult.Error.Kind.PARSE, e.message)
                }
                403, 429 -> CheckResult.Error(CheckResult.Error.Kind.RATE_LIMIT, "HTTP $code")
                else -> CheckResult.Error(CheckResult.Error.Kind.OTHER, "HTTP $code")
            }
        } catch (e: Exception) {
            CheckResult.Error(CheckResult.Error.Kind.NETWORK, e.message)
        }
    }

    /** Short release-notes snippet for the dialog (first non-empty lines, trimmed). */
    fun notesSnippet(body: String, maxChars: Int = 280): String {
        val cleaned = body.lines()
            .map { it.trimEnd() }
            .dropWhile { it.isBlank() || it.startsWith("#") }
            .joinToString("\n")
            .trim()
        if (cleaned.length <= maxChars) return cleaned
        return cleaned.take(maxChars).trimEnd() + "…"
    }
}
