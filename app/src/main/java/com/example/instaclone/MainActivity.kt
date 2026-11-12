package com.example.instaclone

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Abc
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.instaclone.data.AuthManager
import com.example.instaclone.data.FeedViewModel
import com.example.instaclone.data.FeedViewModelFactory
import com.example.instaclone.data.ProfileViewModel
import com.example.instaclone.data.ProfileViewModelFactory
import com.example.instaclone.data.SearchViewModel
import com.example.instaclone.data.SearchViewModelFactory
import com.example.instaclone.data.UploadManager
import com.example.instaclone.ui.theme.InstaCloneTheme
import com.example.instaclone.ui.theme.screens.AddPostScreen
import com.example.instaclone.ui.theme.screens.ChatListScreen
import com.example.instaclone.ui.theme.screens.ChatRoomScreen
import com.example.instaclone.ui.theme.screens.FeedScreen
import com.example.instaclone.ui.theme.screens.LoginScreen
import com.example.instaclone.ui.theme.screens.PostDetailScreen
import com.example.instaclone.ui.theme.screens.ProfileScreen
import com.example.instaclone.ui.theme.screens.RegisterScreen
import com.example.instaclone.ui.theme.screens.SearchScreen
import com.example.instaclone.ui.theme.screens.UpdateDialog
import com.example.instaclone.ui.theme.screens.UploadScreen
import com.example.instaclone.utils.GithubRelease
import com.example.instaclone.utils.GithubUpdateChecker
import io.appwrite.Client
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Request Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // 1. Initialize the client FIRST
        val client = Client(this)
            .setEndpoint(AppwriteConstants.ENDPOINT) // Your API Endpoint
            .setProject(AppwriteConstants.PROJECT_ID) // Your project ID

        // 2. Initialize the AuthManager ONCE here!
        val authManager = AuthManager(client)

        enableEdgeToEdge() // This tells the app to use the full screen, including under the status bar
        setContent {

            AppUpdateCheck() // Check for update on app launch

            InstaCloneTheme(dynamicColor = false) { // Set dynamicColor to false for a consistent IG look
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        val window = (view.context as Activity).window
                        // This makes status bar icons DARK so they show up on white
                        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background // Usually white in Light mode
                ) {
                    MainScreen(authManager = authManager)
                }
            }
        }
    }

    @Composable
    fun AppUpdateCheck() {

        val scope = rememberCoroutineScope()
        var availableUpdate by remember { mutableStateOf<GithubRelease?>(null) }

        // Check for update on app launch
        LaunchedEffect(Unit) {
            scope.launch {
                val update = GithubUpdateChecker.checkForUpdate(applicationContext)
                if (update != null) {
                    availableUpdate = update
                }
            }
        }

        // TEMPORARY FOR TESTING: Force dialog to show
//        var availableUpdate by remember {
//            mutableStateOf<GithubRelease?>(
//                GithubRelease(
//                    tagName = "v1.2",
//                    releaseNotes = "- Added Reels support\n- Bug fixes",
//                    htmlUrl = "https://github.com",
//                    apkUrl = "https://github.com"
//                )
//            )
//        }

        // Show dialog if update is found
        availableUpdate?.let { release ->
            UpdateDialog(
                release = release,
                onDismiss = { availableUpdate = null }
            )
        }

    }
}

sealed interface AuthState {
    data object CHECKING : AuthState
    data object LOGGED_IN : AuthState
    data object LOGGED_OUT : AuthState
}
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainScreen(authManager: AuthManager) {
    // 1. Core controllers and Managers
    val navController = rememberNavController()

    // Before
    // var authState by remember { mutableStateOf("CHECKING") }

    // After (explicitly typed as AuthState)
    // 2. Authentication State (Checking, Logged_In, or Logged_Out)
    var authState by remember { mutableStateOf<AuthState>(AuthState.CHECKING) }

    var isCheckingAuth by remember { mutableStateOf(true) }

    // 3. Initial Session Check
    LaunchedEffect(Unit) {
        try {
            // 1. Capture the User object returned by your function
            val userResult = kotlinx.coroutines.withTimeoutOrNull(5000.milliseconds) {
                authManager.getCurrentUser() // This returns the User object
            }

            // 2. userResult is now either the User or null
            if (userResult != null) {
                // Note: authManager.getCurrentUser() already sets currentUserId internally,
                // but doing it here again is safe.
                authManager.currentUserId = userResult.id
                authState = AuthState.LOGGED_IN
                Log.d("AUTH_SUCCESS", "User verified: ${userResult.id}")
            } else {
                authState = AuthState.LOGGED_OUT
                Log.d("AUTH_TIMEOUT", "Appwrite took too long")
            }
        } catch (e: Exception) {
            authManager.currentUserId = ""
            authState = AuthState.LOGGED_OUT
            Log.d("Auth", "User not logged in: ${e.message}")
        } finally {
            isCheckingAuth = false
        }
    }

    // 4. THE GATEKEEPER
    // If we are still checking the session, show ONLY a centered circle and return.
    // 5. THE MAIN UI (Now isCheckingAuth is guaranteed to be false)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    //val hideBars = listOf("login", "register", "splash")
    val hideBars = listOf(Screen.Login.route, Screen.Register.route, Screen.Splash.route)
    val shouldShowBars = currentRoute != null && currentRoute !in hideBars
    val startDestination = if (authState == AuthState.LOGGED_IN) Screen.Feed.route else Screen.Login.route

    AuthGatekeeper(
        isCheckingAuth = isCheckingAuth,
        onSkipDebug = { authState = AuthState.LOGGED_OUT }
    ) {
        MainScaffold(
            navController = navController,
            currentRoute = currentRoute,
            shouldShowBars = shouldShowBars,
            startDestination = startDestination,
            authManager = authManager
        )
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Text(text = text)
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    authManager: AuthManager,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            LoginScreen(navController = navController, authManager = authManager)
        }
        composable(Screen.Register.route) {
            RegisterScreen(navController = navController, authManager = authManager)
        }
        composable(Screen.Feed.route) {
            val viewModel: FeedViewModel = viewModel(
                factory = FeedViewModelFactory(authManager)
                //factory = viewModelFactory { addInitializer(FeedViewModel::class) { FeedViewModel(authManager) } }
            )
            FeedScreen(navController, authManager, viewModel)
        }
        //composable(Screen.Search.route) { PlaceholderScreen("Search") }
        composable(Screen.Search.route) {
            val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModelFactory(authManager))
            SearchScreen(navController = navController, viewModel = searchViewModel)
        }
        composable(Screen.AddPost.route) {
            AddPostScreen(navController, UploadManager(authManager, LocalContext.current))
        }
        composable(
            route = Screen.Profile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType; nullable = true; defaultValue = "" })
        ) { backStackEntry ->
            val userIdArg = backStackEntry.arguments?.getString("userId") ?: ""
            val targetUserId = if (userIdArg.isEmpty()) authManager.currentUserId else userIdArg

            // Pass targetUserId to the factory here
            // KEY = targetUserId forces a fresh ViewModel when viewing a different user!
            val profileViewModel: ProfileViewModel = viewModel(
                key = targetUserId,
                factory = ProfileViewModelFactory(authManager, targetUserId)
            )

            ProfileScreen(navController = navController, authManager = authManager, viewModel = profileViewModel, targetUserId = targetUserId)
        }
        composable(Screen.ChatList.route) {
            ChatListScreen(navController = navController, authManager = authManager)
        }
        composable(
            route = Screen.ChatRoom.route,
            arguments = listOf(
                navArgument("recipientId") { type = NavType.StringType },
                navArgument("recipientName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val recipientId = backStackEntry.arguments?.getString("recipientId") ?: ""
            val recipientName = backStackEntry.arguments?.getString("recipientName") ?: ""
            ChatRoomScreen(
                navController = navController,
                authManager = authManager,
                recipientId = recipientId,
                recipientName = recipientName
            )
        }
        composable(
            route = Screen.PostDetails.route,
            arguments = listOf(navArgument("postId") { type = NavType.StringType })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            PostDetailScreen(postId, navController, authManager)
        }
        composable(Screen.Upload.route) {
            val context = LocalContext.current
            val uploadManager = remember { UploadManager(authManager, context) }
            UploadScreen(uploadManager = uploadManager, navController = navController)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    navController: NavHostController,
    currentRoute: String?,
    shouldShowBars: Boolean,
    startDestination: String,
    authManager: AuthManager
) {
    Scaffold(
    topBar = {
        // Only show the TopBar if we are on the Feed screen
        if (currentRoute == Screen.Feed.route) {
            TopAppBar(
                title = {
                    Text(
                        "InstaClone",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold // Makes it look more like the IG logo
                        )
                    )
                },
                // Keep this as statusBars so the bar actually sits UNDER the battery/clock
                windowInsets = TopAppBarDefaults.windowInsets,
                actions = {
                    // DM / Chat icon with Notification Badge (Paper Airplane ikona)
                    var hasUnread by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    val context = LocalContext.current
                    var lastNotifiedId by remember { mutableStateOf("") }

                    // Real-time listener for unread messages badge & notifications
                    DisposableEffect(Unit) {
                        val realtime = io.appwrite.services.Realtime(authManager.client)
                        val subscription = realtime.subscribe(
                            "databases.${AppwriteConstants.DATABASE_ID}.collections.${AppwriteConstants.MESSAGE_COLLECTION_ID}.documents"
                        ) { event ->
                            scope.launch {
                                hasUnread = authManager.hasNewMessages()
                                val latest = authManager.getLatestUnreadMessage()
                                if (latest != null && latest.id != lastNotifiedId && latest.senderId != authManager.currentUserId) {
                                    lastNotifiedId = latest.id
                                    com.example.instaclone.utils.NotificationUtils.showMessageNotification(
                                        context = context,
                                        senderName = "New Message",
                                        messageText = latest.messageText
                                    )
                                }
                            }
                        }

                        // Initial check on start
                        scope.launch {
                            hasUnread = authManager.hasNewMessages()
                        }

                        onDispose {
                            try {
                                subscription.close()
                            } catch (e: Exception) {}
                        }
                    }

                    Box(modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(onClick = { navController.navigate(Screen.ChatList.route) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Direct Messages",
                                tint = Color.Black
                            )
                        }
                        if (hasUnread) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.Red, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                    IconButton(onClick = { navController.navigate(Screen.Upload.route) }) {
                        Icon(
                            imageVector = Icons.Default.AddBox,
                            contentDescription = "Upload",
                            tint = Color.Black // Ensures the icon is crisp black
                        )
                    }
                },
                // --- STYLE ADJUSTMENTS START HERE ---
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,       // Solid white background
                    titleContentColor = Color.Black,    // Black text
                    actionIconContentColor = Color.Black // Black icons
                )
            )
        }
    },
    bottomBar = {
        if (shouldShowBars) {
            NavigationBar(
                containerColor = Color.White, // Match the top bar
                tonalElevation = 0.dp         // Removes the greyish tint that Material 3 adds by default
            ) {
                val navItems = listOf(
                    Triple(Screen.Feed, Icons.Default.Home, "Home"),
                    Triple(Screen.Search, Icons.Default.Search, "Search"),
                    Triple(Screen.AddPost, Icons.Default.AddBox, "Post"),
                    Triple(Screen.Profile, Icons.Default.Person, "Profile")
                )

                navItems.forEach { (screen, icon, label) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            unselectedIconColor = Color.Gray,
                            indicatorColor = Color.LightGray.copy(alpha = 0.3f) // Makes the "pill" very subtle
                        ),
                        label = { Text(label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            val route = if (screen == Screen.Profile) {
                                Screen.Profile.createRoute(authManager.currentUserId)
                            } else {
                                screen.route
                            }
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            startDestination = startDestination,
            authManager = authManager,
            modifier = Modifier.padding(if (shouldShowBars) innerPadding else PaddingValues(0.dp))
        )
    }
}

@Composable
fun AuthGatekeeper(
    isCheckingAuth: Boolean,
    onSkipDebug: () -> Unit,
    content: @Composable () -> Unit
) {
    if (isCheckingAuth) {
        // This replaces your first check (Spinner + Debug Skip Button)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Connecting to Appwrite...")
                Button(
                    onClick = onSkipDebug,
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text("Skip to Login (Debug)")
                }
            }
        }
    } else {
        // This replaces the "else" block—it loads your MainScaffold once checking is done!
        content()
    }
}
