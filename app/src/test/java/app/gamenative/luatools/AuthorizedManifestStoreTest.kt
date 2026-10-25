// SPDX-License-Identifier: GPL-3.0-or-later
package app.gamenative.luatools

import android.app.Application
import app.gamenative.data.DepotInfo
import app.gamenative.data.SteamApp
import app.gamenative.data.SteamLicense
import app.gamenative.db.dao.SteamLicenseDao
import app.gamenative.service.SteamService
import `in`.dragonbra.javasteam.enums.ELicenseFlags
import `in`.dragonbra.javasteam.types.SteamID
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.util.EnumSet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [29])
class AuthorizedManifestStoreTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val dao = mockk<SteamLicenseDao>()
    private val license = mockk<SteamLicense>()
    private var previousService: SteamService? = null
    private val text = "addappid(42)\naddappid(43,1,\"${"ab".repeat(32)}\")\nsetManifestid(43,123)"

    @Before
    fun setUp() {
        context.getSharedPreferences("authorized_manifests", 0).edit().clear().commit()
        previousService = SteamService.instance
        val service = mockk<SteamService>()
        every { service.licenseDao } returns dao
        SteamService.instance = service
        mockkObject(SteamService.Companion)
        every { SteamService.isLoggedIn } returns true
        every { SteamService.isConnected } returns true
        every { SteamService.userSteamId } returns SteamID(76561198000000000L)
        every { SteamService.getAppDownloadInfo(42) } returns null
        every { SteamService.getAppInfoOf(42) } returns SteamApp(id = 42, depots = mapOf(43 to mockk<DepotInfo>()))
        every { license.appIds } returns listOf(42)
        every { license.licenseFlags } returns EnumSet.noneOf(ELicenseFlags::class.java)
        coEvery { dao.getAllLicenses() } returns listOf(license)
    }

    @After
    fun tearDown() {
        unmockkObject(SteamService.Companion)
        SteamService.instance = previousService
    }

    @Test
    fun storedDataContainsNoCredentialsAndIsIsolatedByAccount() = runBlocking {
        AuthorizedManifestStore.importManifest(context, 42, text)
        assertEquals(mapOf(43 to 123uL), AuthorizedManifestStore.loadForDownload(context, 42))
        assertFalse(context.getSharedPreferences("authorized_manifests", 0).all.values.toString().contains("ab".repeat(32)))
        every { SteamService.userSteamId } returns SteamID(76561198000000001L)
        assertEquals(emptyMap<Int, ULong>(), AuthorizedManifestStore.loadForDownload(context, 42))
    }

    @Test
    fun expiredOrMissingLicenseCannotImportOrReuseStoredReferences() {
        runBlocking { AuthorizedManifestStore.importManifest(context, 42, text) }
        every { license.licenseFlags } returns EnumSet.of(ELicenseFlags.Expired)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { AuthorizedManifestStore.loadForDownload(context, 42) }
        }
        coEvery { dao.getAllLicenses() } returns emptyList()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { AuthorizedManifestStore.importManifest(context, 42, text) }
        }
    }

    @Test
    fun unknownDepotFailsWithoutReplacingPreviousReferences() = runBlocking {
        AuthorizedManifestStore.importManifest(context, 42, text)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AuthorizedManifestStore.importManifest(context, 42, "addappid(42)\nsetManifestid(99,123)") }
        }
        assertEquals(mapOf(43 to 123uL), AuthorizedManifestStore.loadForDownload(context, 42))
    }

    @Test
    fun clearRestoresDefaultManifestSelection() = runBlocking {
        AuthorizedManifestStore.importManifest(context, 42, text)
        AuthorizedManifestStore.clear(context, 42)
        assertEquals(emptyMap<Int, ULong>(), AuthorizedManifestStore.loadForDownload(context, 42))
    }

    @Test
    fun offlineImportIsRejected() {
        every { SteamService.isConnected } returns false
        assertThrows(IllegalStateException::class.java) {
            runBlocking { AuthorizedManifestStore.importManifest(context, 42, text) }
        }
    }
}
