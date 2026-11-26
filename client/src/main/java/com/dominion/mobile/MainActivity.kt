package com.dominion.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dominion.mobile.ui.AppViewModel
import com.dominion.mobile.ui.DominionAppRoot

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        AppViewModel.Factory((application as DominionApplication).container.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            DominionAppRoot(viewModel)
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
