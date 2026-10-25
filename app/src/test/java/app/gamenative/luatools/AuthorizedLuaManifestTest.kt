// SPDX-License-Identifier: GPL-3.0-or-later
package app.gamenative.luatools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class AuthorizedLuaManifestTest {
    private fun parse(body: String) = AuthorizedLuaManifest.parse(42, body)

    @Test
    fun credentialsAreDiscardedAndUnsignedGidsRoundTrip() {
        val key = "ab".repeat(32)
        val parsed = parse("""
            -- Local authorized manifest
            addappid(42) -- Test app
            addappid(43, 1, "$key")
            setManifestid(43, "18446744073709551615", 2048)
        """.trimIndent())
        assertEquals(ULong.MAX_VALUE, parsed.manifestGids[43])
        assertFalse(parsed.sanitizedText().contains(key))
        assertEquals(parsed, parse(parsed.sanitizedText()))
    }

    @Test
    fun plainGidBomCommentsAndSemicolonsAreAccepted() {
        assertEquals(mapOf(43 to 123uL), parse("\uFEFFaddappid(42);\nsetManifestid(43,123); -- note").manifestGids)
    }

    @Test
    fun scriptsTokensAndUnknownStatementsAreRejected() {
        listOf(
            "os.execute(\"anything\")", "addtoken(42,\"secret\")", "require(\"module\")",
            "function run() end", "--[[", "setManifestid(43, 3) trailing",
        ).forEach { line ->
            assertThrows(Exception::class.java) { parse("addappid(42)\nsetManifestid(43, 1)\n$line") }
        }
    }

    @Test
    fun appMismatchMissingAppOrMissingReferencesAreRejected() {
        listOf("addappid(41)\nsetManifestid(43,1)", "setManifestid(43,1)", "addappid(42)").forEach {
            assertThrows(IllegalArgumentException::class.java) { parse(it) }
        }
    }

    @Test
    fun outOfRangeIdentifiersAndZeroAreRejected() {
        listOf(
            "setManifestid(43,0)", "setManifestid(0,1)", "setManifestid(2147483648,1)",
            "setManifestid(43,18446744073709551616)", "setManifestid(43,1,18446744073709551616)",
        ).forEach {
            assertThrows(IllegalArgumentException::class.java) { parse("addappid(42)\n$it") }
        }
    }

    @Test
    fun conflictingOrRepeatedDepotsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("addappid(42)\nsetManifestid(43,1)\nsetManifestid(43,2)")
        }
    }

    @Test
    fun tooManyDepotsAndOversizedFilesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parse("addappid(42)\n" + (1..257).joinToString("\n") { "setManifestid($it,1)" })
        }
        assertThrows(IllegalArgumentException::class.java) { parse(" ".repeat(AuthorizedLuaManifest.MAX_BYTES + 1)) }
    }

    @Test
    fun malformedQuotesAndKeysAreRejected() {
        listOf("setManifestid(43,\"123)", "setManifestid(43,123\")", "addappid(43,1,\"badkey\")").forEach {
            assertThrows(Exception::class.java) { parse("addappid(42)\n$it") }
        }
    }
}
