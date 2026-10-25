// SPDX-License-Identifier: GPL-3.0-or-later
package app.gamenative.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.R
import app.gamenative.luatools.AuthorizedLuaManifest
import app.gamenative.luatools.AuthorizedManifestStore
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsGroupAuthorizedManifests() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appIdText by rememberSaveable { mutableStateOf("") }
    var authorized by rememberSaveable { mutableStateOf(false) }
    var pendingAppId by rememberSaveable { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val appId = pendingAppId
        pendingAppId = null
        if (uri != null && appId != null) {
            busy = true
            scope.launch {
                try {
                    val count = withContext(Dispatchers.IO) {
                        val text = checkNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                            val buffer = ByteArray(AuthorizedLuaManifest.MAX_BYTES + 1)
                            var length = 0
                            while (length < buffer.size) {
                                val n = input.read(buffer, length, buffer.size - length)
                                if (n < 0) break
                                if (n == 0) error("Could not read manifest")
                                length += n
                            }
                            require(length <= AuthorizedLuaManifest.MAX_BYTES) { "Manifest exceeds 64 KiB" }
                            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .decode(ByteBuffer.wrap(buffer, 0, length)).toString()
                        }
                        AuthorizedManifestStore.importManifest(context, appId, text)
                    }
                    status = context.getString(R.string.authorized_manifests_saved, count, appId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Parser errors contain line numbers, never source text or keys.
                    status = context.getString(R.string.authorized_manifests_error, e.message.orEmpty())
                } finally {
                    busy = false
                }
            }
        }
    }

    Column {
        Text(stringResource(R.string.authorized_manifests_description))
        OutlinedTextField(
            value = appIdText,
            onValueChange = { appIdText = it.filter(Char::isDigit).take(10); authorized = false },
            label = { Text("Steam AppID") },
            singleLine = true,
            enabled = !busy && pendingAppId == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Checkbox(checked = authorized, onCheckedChange = { authorized = it }, enabled = !busy && pendingAppId == null)
        Text(stringResource(R.string.authorized_manifests_consent))
        Button(
            enabled = !busy && pendingAppId == null && authorized && (appIdText.toIntOrNull() ?: 0) > 0,
            onClick = {
                pendingAppId = appIdText.toIntOrNull()
                picker.launch(arrayOf("*/*"))
            },
        ) { Text(stringResource(R.string.authorized_manifests_import)) }
        TextButton(
            enabled = !busy && pendingAppId == null && (appIdText.toIntOrNull() ?: 0) > 0,
            onClick = {
                val appId = appIdText.toIntOrNull() ?: return@TextButton
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { AuthorizedManifestStore.clear(context, appId) }
                        status = context.getString(R.string.authorized_manifests_removed, appId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        status = context.getString(R.string.authorized_manifests_error, e.message.orEmpty())
                    } finally {
                        busy = false
                    }
                }
            },
        ) { Text(stringResource(R.string.authorized_manifests_clear)) }
        if (status.isNotEmpty()) Text(status)
    }
}
