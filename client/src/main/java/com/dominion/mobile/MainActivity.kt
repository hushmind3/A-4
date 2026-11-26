package com.dominion.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.DominionAppRoot

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as DominionApplication).container.repository)
    }

    private var ready = false

    suspend fun applyEngine(connection: com.dominion.mobile.data.local.EngineConnection?) {
        val container = (application as DominionApplication).container
        check(container.repository.state.value.call == null)
        viewModelStore.clear()
        try { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { container.applyEngine(connection) } }
        finally { recreate() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Text("DOMINION · Loading local data") }
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { (application as DominionApplication).container }
                ready = true
                handleIntent(intent)
                setContent { DominionAppRoot(viewModel) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { setContent { Text("Local database could not be opened. Restart to retry.") } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (ready) handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val shared = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getStringArrayListExtra(Intent.EXTRA_TEXT)?.joinToString("\n")
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }
        if (!shared.isNullOrBlank()) viewModel.receiveSharedText(shared)
    }
}
