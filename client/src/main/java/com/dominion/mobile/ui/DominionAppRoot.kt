package com.dominion.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.dominion.mobile.platform.security.rememberDeviceAuthentication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dominion.mobile.data.model.ThemeMode
import com.dominion.mobile.ui.nav.Routes
import com.dominion.mobile.ui.screens.chats.CallScreen
import com.dominion.mobile.ui.screens.chats.CharacterScreen
import com.dominion.mobile.ui.screens.chats.ChatRoomScreen
import com.dominion.mobile.ui.screens.chats.CustomizeScreen
import com.dominion.mobile.ui.screens.chats.NewChatScreen
import com.dominion.mobile.ui.screens.chats.RoomInfoScreen
import com.dominion.mobile.ui.screens.chats.SearchScreen
import com.dominion.mobile.ui.screens.friends.AddFriendScreen
import com.dominion.mobile.ui.screens.friends.FriendProfileScreen
import com.dominion.mobile.ui.screens.main.MainScreen
import com.dominion.mobile.ui.screens.profile.ConnectionDetailScreen
import com.dominion.mobile.ui.screens.profile.ConnectionsScreen
import com.dominion.mobile.ui.screens.profile.CreditsScreen
import com.dominion.mobile.ui.screens.profile.InboxScreen
import com.dominion.mobile.ui.screens.profile.InviteScreen
import com.dominion.mobile.ui.screens.profile.MemoriesScreen
import com.dominion.mobile.ui.screens.profile.SchedulesScreen
import com.dominion.mobile.ui.screens.profile.TasksScreen
import com.dominion.mobile.ui.screens.profile.VaultScreen
import com.dominion.mobile.ui.screens.settings.AccountScreen
import com.dominion.mobile.ui.screens.settings.CloudAccountScreen
import com.dominion.mobile.ui.screens.settings.EngineSettingsScreen
import com.dominion.mobile.ui.screens.settings.AppearanceScreen
import com.dominion.mobile.ui.screens.settings.BlockedUsersScreen
import com.dominion.mobile.ui.screens.settings.EditProfileScreen
import com.dominion.mobile.ui.screens.settings.LanguageScreen
import com.dominion.mobile.ui.screens.settings.LegalScreen
import com.dominion.mobile.ui.screens.settings.NotificationsSettingsScreen
import com.dominion.mobile.ui.screens.settings.PrivacyScreen
import com.dominion.mobile.ui.screens.settings.SettingsScreen
import com.dominion.mobile.ui.screens.settings.TabLayoutScreen
import com.dominion.mobile.ui.theme.DominionTheme

@Composable
fun DominionAppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val notice by vm.notice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        notice?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show(); vm.clearNotice() }
    }
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.settings.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    DominionTheme(darkTheme = dark, lang = state.settings.lang) {
        val nav = rememberNavController()
        val notificationRoom by vm.notificationRoom.collectAsStateWithLifecycle()
        LaunchedEffect(notificationRoom, state.conversations) {
            val roomId = notificationRoom
            // Await server room sync after process restart, not fabricated navigation.
            if (roomId != null && state.conversation(roomId) != null) {
                nav.navigate(Routes.chat(roomId)) { launchSingleTop = true }
                vm.consumeNotificationRoom()
            }
        }
        var appUnlocked by remember { mutableStateOf(!state.settings.appLock) }
        LaunchedEffect(state.settings.appLock) {
            appUnlocked = !state.settings.appLock
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner, vm) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (isActive) {
                        vm.refreshServer()
                        delay(30_000L)
                    }
                }
        }
        DisposableEffect(lifecycleOwner, state.settings.appLock) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    vm.lockVault()
                    if (state.settings.appLock) appUnlocked = false
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        val locked = state.settings.appLock && !appUnlocked
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        LaunchedEffect(locked) {
            if (locked) {
                focus.clearFocus(force = true)
                keyboard?.hide()
            }
        }
        Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = Routes.MAIN,
            modifier = if (locked) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier) {
            composable(Routes.ONBOARDING) {
                com.dominion.mobile.ui.screens.onboarding.OnboardingScreen(
                    state = state, vm = vm, onDone = { nav.popBackStack() },
                    onCloudAccount = { nav.navigate(Routes.CLOUD_ACCOUNT) })
            }
            composable(Routes.MAIN) {
                MainScreen(state = state, vm = vm, onNavigate = { nav.navigate(it) })
            }
            composable(
                route = "chat/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                ChatRoomScreen(
                    state = state,
                    vm = vm,
                    conversationId = id,
                    onBack = { nav.popBackStack() },
                    onCharacter = { nav.navigate(Routes.character(it)) },
                    onRoomInfo = { nav.navigate(Routes.roomInfo(id)) },
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onStartCall = { nav.navigate(Routes.CALL) },
                    onNavigate = { nav.navigate(it) }
                )
            }
            composable(Routes.CALL) {
                CallScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
            composable(
                route = "character/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                CharacterScreen(
                    state = state,
                    vm = vm,
                    characterId = id,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { nav.navigate(Routes.chat(it)) },
                    onStartCall = { nav.navigate(Routes.CALL) },
                    onNavigate = { nav.navigate(it) }
                )
            }
            composable(
                route = "customize/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                CustomizeScreen(state = state, vm = vm, characterId = id, onBack = { nav.popBackStack() })
            }
            composable(
                route = "roominfo/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                RoomInfoScreen(
                    state = state,
                    vm = vm,
                    conversationId = id,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { nav.popBackStack() },
                    onSearch = { nav.navigate(Routes.SEARCH) },
                    onOpenCharacter = {
                        state.conversation(id)?.characterId?.let { nav.navigate(Routes.character(it)) }
                    }
                )
            }
            composable(
                route = "friend/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                FriendProfileScreen(
                    state = state,
                    vm = vm,
                    friendId = id,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { nav.navigate(Routes.chat(it)) }
                )
            }
            composable(
                route = "connection/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                ConnectionDetailScreen(state = state, vm = vm, connectionId = id, onBack = { nav.popBackStack() })
            }
            composable("legal/{document}") { entry ->
                LegalScreen(entry.arguments?.getString("document") ?: "terms", onBack = { nav.popBackStack() })
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { nav.navigate(Routes.chat(it)) }
                )
            }
            composable(Routes.NEW_CHAT) {
                NewChatScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpenChat = { nav.navigate(Routes.chat(it)) },
                    onAddFriend = { nav.navigate(Routes.ADD_FRIEND) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onNavigate = { nav.navigate(it) }
                )
            }
            composable(Routes.ENGINE_SETTINGS) {
                EngineSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.CLOUD_ACCOUNT) {
                CloudAccountScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.ACCOUNT) {
                AccountScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onNavigate = { nav.navigate(it) }
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsSettingsScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.APPEARANCE) {
                AppearanceScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onNavigate = { nav.navigate(it) }
                )
            }
            composable(Routes.LANGUAGE) {
                LanguageScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onNavigate = { nav.navigate(it) }
                )
            }
            composable(Routes.BLOCKED) {
                BlockedUsersScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.TAB_LAYOUT) {
                TabLayoutScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.MEMORIES) {
                MemoriesScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.TASKS) {
                TasksScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.SCHEDULES) {
                SchedulesScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.VAULT) {
                VaultScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.CONNECTIONS) {
                ConnectionsScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpenConnection = { nav.navigate(Routes.connection(it)) }
                )
            }
            composable(Routes.CREDITS) {
                CreditsScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.INVITE) {
                InviteScreen(state = state, onBack = { nav.popBackStack() })
            }
            composable(Routes.INBOX) {
                InboxScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.EDIT_PROFILE) {
                EditProfileScreen(
                    state = state,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onCustomize = { nav.navigate(Routes.customize(state.myCharacter()?.id ?: "")) }
                )
            }
            composable(Routes.ADD_FRIEND) {
                AddFriendScreen(state = state, vm = vm, onBack = { nav.popBackStack() })
            }
        }

        if (locked) {
            BackHandler { }
            AppLockOverlay(onUnlock = { appUnlocked = true })
        }
        }
    }
}

@Composable
private fun AppLockOverlay(onUnlock: () -> Unit) {
    val p = com.dominion.mobile.ui.theme.LocalDominionPalette.current
    val lang = LocalLang.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val authenticate = rememberDeviceAuthentication(
        title = tr(lang, "DOMINION 잠금 해제", "Unlock DOMINION"),
        onSuccess = onUnlock,
        onError = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
    )
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(p.screen)
            .clickable { },
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .size(84.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(p.raised),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                com.dominion.mobile.ui.components.GlyphIcon(
                    com.dominion.mobile.ui.components.Glyph.LOCK,
                    p.textPrimary,
                    size = 34.dp
                )
            }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(18.dp))
            androidx.compose.material3.Text(
                tr(lang, "잠겨 있어요", "Dominion is locked"),
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                color = p.textPrimary
            )
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(6.dp))
            androidx.compose.material3.Text(
                tr(lang, "지문이나 기기 잠금으로 열어주세요.", "Unlock with biometrics or your device lock."),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = p.textTertiary
            )
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(24.dp))
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .background(p.actionPrimary)
                    .clickable(onClick = authenticate)
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                androidx.compose.material3.Text(
                    tr(lang, "잠금 해제", "Unlock"),
                    color = p.onActionPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
