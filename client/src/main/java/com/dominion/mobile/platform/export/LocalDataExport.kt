package com.dominion.mobile.platform.export

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.dominion.mobile.data.model.AppState
import com.dominion.mobile.data.model.Lang
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit allowlist: never serialize the vault or the entire runtime state. */
object LocalDataExport {
    fun json(state: AppState): String = GsonBuilder().setPrettyPrinting().create().toJson(
        linkedMapOf(
            "format" to "dominion-mobile-local-export-v1",
            "exportedAt" to java.time.Instant.now().toString(),
            "scope" to "local_snapshot_not_full_server_backup",
            "account" to mapOf("name" to state.account.name, "birthday" to state.account.birthday),
            "characters" to state.characters,
            "conversations" to state.conversations,
            "tasks" to state.tasks,
            "schedules" to state.schedules,
            "memories" to state.memories,
            "archive" to state.archive,
            "settings" to state.settings
        )
    )
}

@Composable
fun rememberDataExport(state: AppState, lang: Lang): () -> Unit {
    val context = LocalContext.current
    val latest = rememberUpdatedState(state)
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val snapshot = latest.value
            scope.launch {
                val saved = try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { out ->
                            out.write(LocalDataExport.json(snapshot).toByteArray(Charsets.UTF_8))
                        }
                    }
                    true
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { false }
                Toast.makeText(context, if (saved) {
                    if (lang == Lang.KO) "JSON 파일에 저장했습니다." else "Saved JSON file."
                } else {
                    if (lang == Lang.KO) "파일 저장에 실패했습니다." else "File could not be saved."
                }, Toast.LENGTH_LONG).show()
            }
        }
    }
    return { launcher.launch("dominion-data-" + java.time.LocalDate.now() + ".json") }
}
