package com.pickle.client

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.pickle.client.ui.AppViewModel
import com.pickle.client.ui.PickleAppRoot

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as PickleApplication).container.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            PickleAppRoot(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
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
