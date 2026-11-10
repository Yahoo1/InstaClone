package com.example.instaclone.ui.theme.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import com.example.instaclone.AppwriteConstants
import com.example.instaclone.data.Post
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import coil.request.ImageRequest
import com.example.instaclone.Screen
import com.example.instaclone.data.AuthManager
import com.example.instaclone.data.ProfileViewModel
import com.example.instaclone.data.UploadManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    authManager: AuthManager,
    viewModel: ProfileViewModel,
    targetUserId: String
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 1. Define this FIRST
    // We need an UploadManager instance to handle the actual file transfer
    val uploadManager = remember { UploadManager(authManager, context) }

    // 2. Define the launcher SECOND
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        // Use 'it' for the Uri and the already-defined uploadManager
        uri?.let { selectedUri ->
            // We launch a coroutine here to wait for the result and show the Toast
            scope.launch {
                val isSuccess = viewModel.updateAvatar(selectedUri, uploadManager)

                // Check the ViewModel state after the function completes
                if (isSuccess) {
                    Toast.makeText(context, "Profile updated successfully! ✨", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Upload failed. Check logs.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val isSelf = targetUserId.isEmpty() || targetUserId == authManager.currentUserId

    var userPosts by remember { mutableStateOf<List<Post>>(emptyList()) }
    var userName by remember { mutableStateOf("Loading...") } // State for the name
    var isLoading by remember { mutableStateOf(true) }
    var userBio by remember { mutableStateOf("") }
    var userProfileImageUrl by remember { mutableStateOf("") }

    // Fetch data when screen loads or userId changes
    LaunchedEffect(targetUserId) {
        isLoading = true
        Log.d("PROFILE_DEBUG", "=== OPENING PROFILE ===")
        Log.d("PROFILE_DEBUG", "targetUserId passed: $targetUserId")
        Log.d("PROFILE_DEBUG", "isSelf: $isSelf")

        // --- ADD THIS LOG ---
        Log.d("PROFILE_DEBUG", "targetUserId: $targetUserId | currentUserId: ${authManager.currentUserId} | isSelf: $isSelf")
        // --------------------

        try {
            if (isSelf) {
                val currentUser = authManager.getCurrentUser()
                if (currentUser != null) {
                    userName = currentUser.name
                    val profile = authManager.getUserProfile(currentUser.id)
                    userBio = profile?.bio ?: ""
                    userProfileImageUrl = profile?.profileImageUrl ?: ""
                    userPosts = authManager.getUserPosts(currentUser.id)
                    Log.d("PROFILE_DEBUG", "Loaded SELF profile URL: $userProfileImageUrl")
                }
            } else {
                val profile = authManager.getUserProfile(targetUserId)
                if (profile != null) {
                    userName = profile.username
                    userBio = profile.bio
                    userProfileImageUrl = profile.profileImageUrl
                    Log.d("PROFILE_DEBUG", "Loaded OTHER user profile URL: $userProfileImageUrl")
                    // --- ADD THESE DEBUG LOGS ---
                    Log.d("PROFILE_DOC_DEBUG", "User ID: ${profile.userId}")
                    Log.d("PROFILE_DOC_DEBUG", "Username: ${profile.username}")
                    Log.d("PROFILE_DOC_DEBUG", "Bio: ${profile.bio}")
                    Log.d("PROFILE_DOC_DEBUG", "Profile Image URL in DB: ${profile.profileImageUrl}")
                    // ----------------------------
                } else {
                    userName = "User not found"
                    Log.d("PROFILE_DEBUG", "Profile not found for ID: $targetUserId")
                }
                userPosts = authManager.getUserPosts(targetUserId)
            }
        } catch (e: Exception) {
            android.util.Log.e("Profile", "Error loading profile: ${e.message}")
            userName = "Error loading profile"
        } finally {
            isLoading = false
        }
    }

    val canPop = navController.previousBackStackEntry != null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("$userName's Profile") },
                navigationIcon = {
                    // Show back button if we navigated here from Search/Feed OR if it's not self (if viewing someone else's profile)
                    if (canPop || !isSelf) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    if (isSelf) { //logout button only appears when isSelf is true
                        // Logout Icon Button
                        IconButton(onClick = {
                            scope.launch {
                                val success = authManager.logout()
                                if (success) {
                                    // 1. Show confirmation
                                    Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()

                                    // 2. CRITICAL: Wipe the local memory so User 2 doesn't see User 1's data
                                    authManager.clearLocalSession()

                                    // 3. Navigate to Login and WIPE the history
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Logout",
                                tint = Color.Red
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(1.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                // --- HEADER SECTION (Spans 3 columns) ---
                item(span = { GridItemSpan(3) }) {
                    ProfileHeader(
                        userName = userName,
                        bio = userBio,
                        profileImageUrl = userProfileImageUrl,
                        isUpdating = viewModel.isUpdating,
                        authManager = authManager,
                        isSelf = isSelf,
                        postsCount = userPosts.size,
                        followersCount = viewModel.followersCount,
                        followingCount = viewModel.followingCount,
                        isFollowing = viewModel.isFollowing,
                        onToggleFollow = { viewModel.toggleFollow() },
                        onAvatarClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                }

                // --- POSTS GRID SECTION ---
                if (userPosts.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text("No posts yet. Share your first photo!")
                        }
                    }
                } else {
                    items(userPosts) { post ->
                        val imageUrl = "${AppwriteConstants.ENDPOINT}/storage/buckets/${AppwriteConstants.BUCKET_ID}/files/${post.imageFileId}/view?project=${AppwriteConstants.PROJECT_ID}"

                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "User Post",
                            modifier = Modifier
                                .aspectRatio(1f) // Keep it square
                                .clickable {
                                    // Navigate to Detail Screen if you want!
                                    // navController.navigate("postDetails/${post.id}")
                                    //nově:
                                    //navController.navigate(Screen.PostDetails.createRoute(post.id))
                                },
                            contentScale = ContentScale.Crop // Fill square
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileStats(number: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = number, style = MaterialTheme.typography.titleMedium)
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ProfileHeader(
    userName: String,
    bio: String,
    profileImageUrl: String, // This is the static URL from the DB
    isUpdating: Boolean,
    authManager: AuthManager, // Add this to listen to the live state
    isSelf: Boolean, // Add this parameter
    postsCount: Int,
    followersCount: Int,
    followingCount: Int,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onAvatarClick: () -> Unit
) {
    // 1. Prioritize the live URL from AuthManager if it exists
    // Only use liveUrl if this is YOUR own profile (isSelf)
    val liveUrl = if (isSelf) authManager.myLatestAvatarUrl else null

    val avatarToDisplay = when {
        !liveUrl.isNullOrBlank() -> liveUrl
        profileImageUrl.isNotBlank() -> profileImageUrl
        else -> "https://cloud.appwrite.io/v1/avatars/initials?name=$userName"
    }

    // --- ADD THIS LOG ---
    Log.d("HEADER_DEBUG", "ProfileHeader -> user: $userName | avatarToDisplay: $avatarToDisplay")
    // --------------------

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clickable { if (!isUpdating) onAvatarClick() } // Disable click while uploading
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarToDisplay)
                    .crossfade(true)
                    //.memoryCacheKey(avatarToDisplay) // Force refresh on change
                    .memoryCacheKey(avatarToDisplay) // Use avatarToDisplay so Coil doesn't mix up caches
                    .diskCacheKey(avatarToDisplay)
                    .build(),
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, Color.LightGray, CircleShape),
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Default.AccountCircle)
            )

            // Show a small spinner over the avatar if uploading
            if (isSelf) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(30.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color.White, CircleShape)
                            .border(1.dp, Color.LightGray, CircleShape)
                            .padding(4.dp)
                            .size(20.dp)
                    )
                }
            }
        }

        Text(
            text = userName,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )

        Text(
            text = bio.ifEmpty { "No bio yet" },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        // --- STATS ROW (Posts, Followers, Following) ---
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileStats(number = "$postsCount", label = "Posts")
            ProfileStats(number = "$followersCount", label = "Followers")
            ProfileStats(number = "$followingCount", label = "Following")
        }

        // --- FOLLOW BUTTON (Only shown when viewing other users) ---
        if (!isSelf) {
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.Button(
                onClick = onToggleFollow,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (isFollowing) Color.LightGray else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (isFollowing) "Following" else "Follow",
                    color = if (isFollowing) Color.Black else Color.White
                )
            }
        }

        // The Progress Bar
        if (isUpdating) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(top = 16.dp), thickness = 0.5.dp)
    }
}