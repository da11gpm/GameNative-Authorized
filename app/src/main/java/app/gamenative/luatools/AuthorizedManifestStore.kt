// SPDX-License-Identifier: GPL-3.0-or-later
package app.gamenative.luatools

import android.content.Context
import app.gamenative.service.SteamService
import `in`.dragonbra.javasteam.enums.ELicenseFlags

/** Only sanitized manifest IDs are stored, scoped to the signed-in Steam account. */
object AuthorizedManifestStore {
    private fun account(): String {
        check(SteamService.isLoggedIn && SteamService.isConnected) { "Sign in to Steam first" }
        return checkNotNull(SteamService.userSteamId).convertToUInt64().toString()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("authorized_manifests", Context.MODE_PRIVATE)

    suspend fun importManifest(context: Context, appId: Int, text: String): Int {
        val accountId = account()
        val parsed = AuthorizedLuaManifest.parse(appId, text)
        validate(appId, parsed)
        check(SteamService.getAppDownloadInfo(appId) == null) { "Pause the download before changing references" }
        check(accountId == account()) { "Steam account changed; import cancelled" }
        check(prefs(context).edit().putString("$accountId/$appId", parsed.sanitizedText()).commit()) {
            "Could not save manifest references"
        }
        return parsed.manifestGids.size
    }

    fun clear(context: Context, appId: Int) {
        require(appId > 0) { "Invalid AppID" }
        check(SteamService.getAppDownloadInfo(appId) == null) { "Pause the download before changing references" }
        check(prefs(context).edit().remove("${account()}/$appId").commit()) { "Could not remove references" }
    }

    suspend fun loadForDownload(context: Context, appId: Int): Map<Int, ULong> {
        val accountId = account()
        val text = prefs(context).getString("$accountId/$appId", null) ?: return emptyMap()
        val parsed = AuthorizedLuaManifest.parse(appId, text)
        validate(appId, parsed)
        check(accountId == account()) { "Steam account changed; download cancelled" }
        return parsed.manifestGids
    }

    private suspend fun validate(appId: Int, parsed: AuthorizedLuaManifest) {
        val licenses = checkNotNull(SteamService.instance).licenseDao.getAllLicenses()
        check(licenses.any { appId in it.appIds && ELicenseFlags.Expired !in it.licenseFlags }) {
            "This app has no active Steam library license"
        }
        val app = checkNotNull(SteamService.getAppInfoOf(appId)) { "Sync the Steam library first" }
        require(parsed.manifestGids.keys.all { it in app.depots }) { "Manifest references an unknown depot" }
    }
}
