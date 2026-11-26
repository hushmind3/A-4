package com.pickle.client.ui

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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pickle.client.data.model.ThemeMode
import com.pickle.client.ui.nav.Routes
import com.pickle.client.ui.screens.chats.CallScreen
import com.pickle.client.ui.screens.chats.CharacterScreen
import com.pickle.client.ui.screens.chats.ChatRoomScreen
import com.pickle.client.ui.screens.chats.CustomizeScreen
import com.pickle.client.ui.screens.chats.NewChatScreen
import com.pickle.client.ui.screens.chats.RoomInfoScreen
import com.pickle.client.ui.screens.chats.SearchScreen
import com.pickle.client.ui.screens.friends.AddFriendScreen
import com.pickle.client.ui.screens.friends.FriendProfileScreen
import com.pickle.client.ui.screens.main.MainScreen
import com.pickle.client.ui.screens.onboarding.OnboardingScreen
import com.pickle.client.ui.screens.profile.ConnectionDetailScreen
import com.pickle.client.ui.screens.profile.ConnectionsScreen
import com.pickle.client.ui.screens.profile.CreditsScreen
import com.pickle.client.ui.screens.profile.InboxScreen
import com.pickle.client.ui.screens.profile.InviteScreen
import com.pickle.client.ui.screens.profile.MemoriesScreen
import com.pickle.client.ui.screens.profile.SchedulesScreen
import com.pickle.client.ui.screens.profile.TasksScreen
import com.pickle.client.ui.screens.profile.VaultScreen
import com.pickle.client.ui.screens.settings.AccountScreen
import com.pickle.client.ui.screens.settings.AppearanceScreen
import com.pickle.client.ui.screens.settings.BlockedUsersScreen
import com.pickle.client.ui.screens.settings.EditProfileScreen
import com.pickle.client.ui.screens.settings.LanguageScreen
import com.pickle.client.ui.screens.settings.LegalScreen
import com.pickle.client.ui.screens.settings.NotificationsSettingsScreen
import com.pickle.client.ui.screens.settings.PrivacyScreen
import com.pickle.client.ui.screens.settings.SettingsScreen
import com.pickle.client.ui.screens.settings.TabLayoutScreen
import com.pickle.client.ui.theme.PickleTheme

@Composable
fun PickleAppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.settings.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    PickleTheme(darkTheme = dark, lang = state.settings.lang) {
        val nav = rememberNavController()
        var appUnlocked by remember { mutableStateOf(!state.settings.appLock) }
        LaunchedEffect(state.settings.appLock) {
            if (!state.settings.appLock) appUnlocked = true
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, state.settings.appLock) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && state.settings.appLock) {
                    appUnlocked = false
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(state.onboarded) {
            val route = nav.currentBackStackEntry?.destination?.route
            if (state.onboarded && route == Routes.ONBOARDING) {
                nav.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } }
            } else if (!state.onboarded && route != Routes.ONBOARDING && route != null) {
                nav.navigate(Routes.ONBOARDING) { popUpTo(0) { inclusive = true } }
            }
        }

        NavHost(
            navController = nav,
            startDestination = if (state.onboarded) Routes.MAIN else Routes.ONBOARDING
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    state = state,
                    vm = vm,
                    onDone = {
                        nav.navigate(Routes.MAIN) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
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

        if (state.settings.appLock && !appUnlocked) {
            AppLockOverlay(onUnlock = { appUnlocked = true })
        }
    }
}

@Composable
private fun AppLockOverlay(onUnlock: () -> Unit) {
    val p = com.pickle.client.ui.theme.LocalPicklePalette.current
    val lang = LocalLang.current
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
                com.pickle.client.ui.components.GlyphIcon(
                    com.pickle.client.ui.components.Glyph.LOCK,
                    p.textPrimary,
                    size = 34.dp
                )
            }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(18.dp))
            androidx.compose.material3.Text(
                tr(lang, "잠겨 있어요", "Pickle is locked"),
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
                    .clickable(onClick = onUnlock)
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
