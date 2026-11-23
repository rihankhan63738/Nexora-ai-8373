package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.UiState
import com.example.data.preferences.LocalPreferencesStore
import com.example.firebase.attemptAutoSignIn
import com.example.firebase.authStateFlow
import com.example.firebase.performSignOut
import com.example.ui.screens.AssistantsScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ExploreModelsScreen
import com.example.ui.screens.FileAnalysisScreen
import com.example.ui.screens.HelpAndFeedbackScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.ImageStudioScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WebResearchScreen
import com.example.ui.theme.NexoraTheme
import com.example.util.VoiceEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private data class NavItem(
    val route: NexoraRoute,
    val label: String,
    val icon: ImageVector
)

private val PrimaryBottomNavItems = listOf(
    NavItem(NexoraRoute.CHAT, "Chat", Icons.AutoMirrored.Filled.Chat),
    NavItem(NexoraRoute.HISTORY, "History", Icons.Filled.History),
    NavItem(NexoraRoute.IMAGE_STUDIO, "Images", Icons.Filled.Image),
    NavItem(NexoraRoute.WEB_RESEARCH, "Research", Icons.Filled.Public),
    NavItem(NexoraRoute.ASSISTANTS, "Assistants", Icons.Filled.SmartToy)
)

private val AllDrawerNavItems = listOf(
    NavItem(NexoraRoute.CHAT, "Main AI Chat", Icons.AutoMirrored.Filled.Chat),
    NavItem(NexoraRoute.HISTORY, "Chat History", Icons.Filled.History),
    NavItem(NexoraRoute.MODELS, "Explore AI Models", Icons.Filled.Hub),
    NavItem(NexoraRoute.IMAGE_STUDIO, "Image Generation", Icons.Filled.Image),
    NavItem(NexoraRoute.FILE_ANALYSIS, "File & Vision Analysis", Icons.Filled.Description),
    NavItem(NexoraRoute.WEB_RESEARCH, "Web Research", Icons.Filled.Public),
    NavItem(NexoraRoute.ASSISTANTS, "Custom Assistants", Icons.Filled.SmartToy),
    NavItem(NexoraRoute.MEMORY, "Memory System", Icons.Filled.Psychology),
    NavItem(NexoraRoute.NOTIFICATIONS, "Notifications", Icons.Filled.Notifications),
    NavItem(NexoraRoute.PROFILE, "User Profile", Icons.Filled.Person),
    NavItem(NexoraRoute.SETTINGS, "Settings", Icons.Filled.Settings),
    NavItem(NexoraRoute.HELP, "Help & Feedback", Icons.AutoMirrored.Filled.HelpOutline)
)

@Composable
fun NexoraApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsStore = remember(context) { LocalPreferencesStore(context.applicationContext) }
    val themeMode by prefsStore.themeMode.collectAsStateWithLifecycle(initialValue = "DARK")
    val onboardingCompleted by prefsStore.onboardingCompleted.collectAsStateWithLifecycle(initialValue = false)

    val currentUser by Firebase.auth.authStateFlow().collectAsStateWithLifecycle(
        initialValue = Firebase.auth.currentUser
    )
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var forceShowOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    NexoraTheme(themeMode = themeMode) {
        val user = currentUser
        if (user == null) {
            if (!onboardingCompleted || forceShowOnboarding) {
                OnboardingScreen(
                    onGetStarted = {
                        forceShowOnboarding = false
                        scope.launch { prefsStore.setOnboardingCompleted(true) }
                    }
                )
            } else {
                AuthScreen(
                    onAuthSuccess = {},
                    onReplayOnboarding = { forceShowOnboarding = true }
                )
            }
        } else {
            key(user.uid) {
                AuthenticatedNexoraShell(
                    firebaseUser = user,
                    prefsStore = prefsStore,
                    onSignOut = {
                        performSignOut(credentialManager, scope)
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedNexoraShell(
    firebaseUser: FirebaseUser,
    prefsStore: LocalPreferencesStore,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val voiceEngine = remember(context) { VoiceEngine(context.applicationContext) }
    DisposableEffect(voiceEngine) {
        onDispose { voiceEngine.release() }
    }

    val viewModel: NexoraViewModel = viewModel(
        key = firebaseUser.uid,
        factory = NexoraViewModel.provideFactory(
            context = context,
            currentUserId = firebaseUser.uid,
            firebaseUser = firebaseUser,
            prefsStore = prefsStore,
            voiceEngine = voiceEngine
        )
    )

    val currentRoute by viewModel.currentRoute.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val unreadCount = remember(notificationsState) {
        (notificationsState as? UiState.Success)?.data?.count { !it.read } ?: 0
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Back press on any secondary screen returns to Main AI Chat
    if (currentRoute != NexoraRoute.CHAT) {
        BackHandler {
            viewModel.navigateTo(NexoraRoute.CHAT)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isExpanded,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("NEXORA AI", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "One AI. Infinite Possibilities.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        AllDrawerNavItems.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label) },
                                selected = currentRoute == item.route,
                                icon = {
                                    if (item.route == NexoraRoute.NOTIFICATIONS && unreadCount > 0) {
                                        BadgedBox(badge = { Badge { Text(unreadCount.toString()) } }) {
                                            Icon(item.icon, null)
                                        }
                                    } else {
                                        Icon(item.icon, null)
                                    }
                                },
                                onClick = {
                                    viewModel.navigateTo(item.route)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .testTag("drawer_nav_${item.route.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!isExpanded) {
                        NavigationBar(
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            PrimaryBottomNavItems.forEach { item ->
                                NavigationBarItem(
                                    selected = currentRoute == item.route,
                                    onClick = { viewModel.navigateTo(item.route) },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1) },
                                    modifier = Modifier.testTag("bottom_nav_${item.route.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .statusBarsPadding()
                ) {
                    if (isExpanded) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(Modifier.height(8.dp))
                            AllDrawerNavItems.forEach { item ->
                                NavigationRailItem(
                                    selected = currentRoute == item.route,
                                    onClick = { viewModel.navigateTo(item.route) },
                                    icon = {
                                        if (item.route == NexoraRoute.NOTIFICATIONS && unreadCount > 0) {
                                            BadgedBox(badge = { Badge { Text(unreadCount.toString()) } }) {
                                                Icon(item.icon, contentDescription = item.label)
                                            }
                                        } else {
                                            Icon(item.icon, contentDescription = item.label)
                                        }
                                    },
                                    label = { Text(item.route.label, maxLines = 1) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (currentRoute) {
                            NexoraRoute.CHAT -> ChatScreen(
                                viewModel = viewModel,
                                showDrawerButton = !isExpanded,
                                onOpenDrawer = { scope.launch { drawerState.open() } }
                            )
                            NexoraRoute.HISTORY -> HistoryScreen(viewModel)
                            NexoraRoute.MODELS -> ExploreModelsScreen(viewModel)
                            NexoraRoute.IMAGE_STUDIO -> ImageStudioScreen(viewModel)
                            NexoraRoute.FILE_ANALYSIS -> FileAnalysisScreen(viewModel)
                            NexoraRoute.WEB_RESEARCH -> WebResearchScreen(viewModel)
                            NexoraRoute.ASSISTANTS -> AssistantsScreen(viewModel)
                            NexoraRoute.MEMORY -> MemoryScreen(viewModel)
                            NexoraRoute.PROFILE -> ProfileScreen(viewModel, onSignOut = onSignOut)
                            NexoraRoute.SETTINGS -> SettingsScreen(viewModel)
                            NexoraRoute.NOTIFICATIONS -> NotificationsScreen(viewModel)
                            NexoraRoute.HELP -> HelpAndFeedbackScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}
