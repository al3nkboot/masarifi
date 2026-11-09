package com.midroid.masarifi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateCheckerTest {
    @Test fun compareEqual() {
        assertEquals(0, UpdateChecker.compareVersions("2.5.1", "v2.5.1"))
        assertEquals(0, UpdateChecker.compareVersions("2.6.0", "2.6.0"))
    }

    @Test fun compareRemoteNewer() {
        assertTrue(UpdateChecker.compareVersions("2.5.1", "2.6.0") < 0)
        assertTrue(UpdateChecker.compareVersions("2.6.0", "v2.6.1") < 0)
        assertTrue(UpdateChecker.compareVersions("2.6", "2.6.1") < 0)
    }

    @Test fun compareCurrentNewer() {
        assertTrue(UpdateChecker.compareVersions("2.6.0", "2.5.1") > 0)
        assertTrue(UpdateChecker.compareVersions("v3.0.0", "2.9.9") > 0)
    }

    @Test fun pickFullAsset() {
        val assets = listOf(
            UpdateChecker.Asset("masarifi-v2.6.0-nosms.apk", "https://ex/nosms"),
            UpdateChecker.Asset("masarifi-v2.6.0.apk", "https://ex/full"),
            UpdateChecker.Asset("notes.txt", "https://ex/txt"),
        )
        assertEquals("https://ex/full", UpdateChecker.pickAssetUrl(assets, smsEnabled = true, version = "2.6.0"))
    }

    @Test fun pickNosmsAsset() {
        val assets = listOf(
            UpdateChecker.Asset("masarifi-v2.6.0.apk", "https://ex/full"),
            UpdateChecker.Asset("masarifi-v2.6.0-nosms.apk", "https://ex/nosms"),
        )
        assertEquals("https://ex/nosms", UpdateChecker.pickAssetUrl(assets, smsEnabled = false, version = "2.6.0"))
    }

    @Test fun pickMissingFallsNull() {
        assertNull(UpdateChecker.pickAssetUrl(emptyList(), smsEnabled = true, version = "2.6.0"))
    }

    @Test fun evaluateUpToDate() {
        val rel = UpdateChecker.Release("v2.6.0", "2.6.0", "2.6.0", "", UpdateChecker.RELEASES_PAGE, emptyList())
        val r = UpdateChecker.evaluate("2.6.0", rel, true)
        assertTrue(r is UpdateChecker.CheckResult.UpToDate)
    }

    @Test fun evaluateUpdateAvailable() {
        val assets = listOf(UpdateChecker.Asset("masarifi-v2.7.0.apk", "https://ex/apk"))
        val rel = UpdateChecker.Release("v2.7.0", "2.7.0", "2.7.0", "- currencies\n- updates", "https://ex/rel", assets)
        val r = UpdateChecker.evaluate("2.6.0", rel, true) as UpdateChecker.CheckResult.UpdateAvailable
        assertEquals("2.7.0", r.release.version)
        assertEquals("https://ex/apk", r.assetUrl)
    }

    @Test fun parseReleaseJson() {
        val json = """{"tag_name":"v2.5.1","name":"2.5.1","body":"notes","html_url":"https://github.com/al3nkboot/masarifi/releases/tag/v2.5.1","assets":[{"name":"masarifi-v2.5.1.apk","browser_download_url":"https://ex/a"},{"name":"masarifi-v2.5.1-nosms.apk","browser_download_url":"https://ex/b"}]}"""
        val r = UpdateChecker.parseReleaseJson(json)
        assertEquals("v2.5.1", r.tag)
        assertEquals("2.5.1", r.version)
        assertEquals(2, r.assets.size)
        assertEquals("https://ex/b", UpdateChecker.pickAssetUrl(r.assets, smsEnabled = false, version = r.version))
    }

    @Test fun notesSnippetTrims() {
        val s = UpdateChecker.notesSnippet("# Title\n\n" + "x".repeat(400))
        assertTrue(s.endsWith("…"))
        assertTrue(s.length <= 281)
    }
}
