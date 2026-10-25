// SPDX-License-Identifier: GPL-3.0-or-later
package app.gamenative.luatools

/** Data-only LuaTools subset. This class never evaluates Lua or returns credentials. */
data class AuthorizedLuaManifest(val appId: Int, val manifestGids: Map<Int, ULong>) {
    fun sanitizedText(): String = buildString {
        appendLine("addappid($appId)")
        manifestGids.toSortedMap().forEach { (depot, gid) ->
            appendLine("setManifestid($depot, \"$gid\")")
        }
    }

    companion object {
        const val MAX_BYTES = 65_536
        private val appLine = Regex("""addappid\(\s*([0-9]+)\s*(?:,\s*1\s*,\s*"[0-9a-fA-F]{64}"\s*)?\)\s*;?""")
        private val manifestLine = Regex("""setManifestid\(\s*([0-9]+)\s*,\s*(?:"([0-9]+)"|([0-9]+))\s*(?:,\s*([0-9]+)\s*)?\)\s*;?""")

        fun parse(expectedAppId: Int, text: String): AuthorizedLuaManifest {
            require(expectedAppId > 0) { "Invalid AppID" }
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Manifest exceeds 64 KiB" }
            var declaredApp: Int? = null
            val gids = linkedMapOf<Int, ULong>()
            text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, raw ->
                require(!Regex("""--\[=*\[""").containsMatchIn(raw)) { "Lua block comments are unsupported" }
                // Only line comments are supported; all strings in this grammar are numeric/hex.
                val line = raw.substringBefore("--").trim()
                if (line.isEmpty()) return@forEachIndexed
                val app = appLine.matchEntire(line)
                val manifest = manifestLine.matchEntire(line)
                when {
                    app != null -> {
                        val id = app.groupValues[1].toIntOrNull()
                        require(id != null && id > 0) { "Invalid AppID/depot at line ${index + 1}" }
                        if (declaredApp == null) {
                            require(line.matches(Regex("""addappid\(\s*$expectedAppId\s*\)\s*;?"""))) {
                                "First declaration must be addappid($expectedAppId)"
                            }
                            declaredApp = id
                        }
                        // A key in an addappid line is deliberately discarded, never persisted.
                    }
                    manifest != null -> {
                        val depot = manifest.groupValues[1].toIntOrNull()
                        val gid = (manifest.groupValues[2].ifEmpty { manifest.groupValues[3] }).toULongOrNull()
                        require(depot != null && depot > 0 && gid != null && gid > 0uL) {
                            "Invalid depot/manifest at line ${index + 1}"
                        }
                        require(manifest.groupValues[4].isEmpty() || manifest.groupValues[4].toULongOrNull() != null) {
                            "Invalid size at line ${index + 1}"
                        }
                        require(depot !in gids) { "Duplicate depot at line ${index + 1}" }
                        gids[depot] = gid
                    }
                    else -> error("Unsupported Lua statement at line ${index + 1}")
                }
            }
            require(declaredApp == expectedAppId) { "AppID declaration does not match" }
            require(gids.isNotEmpty()) { "No manifest references found" }
            require(gids.size <= 256) { "Too many depots" }
            return AuthorizedLuaManifest(expectedAppId, gids.toMap())
        }
    }
}
