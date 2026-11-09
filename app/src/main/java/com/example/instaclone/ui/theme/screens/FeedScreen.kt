package com.example.instaclone.ui.theme.screens

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.instaclone.AppwriteConstants
import com.example.instaclone.data.AppwriteUtils.getTimeAgo
import com.example.instaclone.data.AuthManager
import com.example.instaclone.data.FeedViewModel
import com.example.instaclone.data.Post
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ChatBubble
import com.example.instaclone.Screen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun FeedScreen(
    navController: NavController,
    authManager: AuthManager, // Using the centralized manager
    viewModel: FeedViewModel // Make sure you pass your ViewModel here
) {
    // TRIGGER REFRESH EVERY TIME SCREEN IS SHOWN
    LaunchedEffect(Unit) {
        viewModel.fetchPosts()
    }

    // Collect the state from the ViewModel
    val posts by viewModel.posts.collectAsState()
    val isLoading = viewModel.isLoading // Observe the loading state
    // 1. Observe the ID here at the top level of the screen
    val currentUserId = authManager.currentUserId

    // By reading this here, FeedScreen will recompose when it changes
    val activeUserId = authManager.currentUserId
    Log.d("AUTH_CHECK", "Current ID is: $activeUserId")

    // Whenever the currentUserId changes from "" to a real ID, RE-FETCH
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            Log.d("AUTH_CHECK", "ID arrived! Fetching posts for: $currentUserId")
            viewModel.fetchPosts()
        }
    }

    // Box: Receives that calculation and offsets itself so the StoryBar starts exactly where the TopBar ends.
    // PullToRefreshBox replaces your outer Box
    val haptics = LocalHapticFeedback.current

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.fetchPosts()
        },
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (posts.isEmpty() && !isLoading) {
            // Centered empty state
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No posts yet. Be the first to share!")
            }
        } else {
            // 4. Display the posts
            LazyColumn(modifier = Modifier.fillMaxSize()) { //vertical scrollable column

                //zobrazí "horizontally scrollable row of "Stories" with colorful borders sitting right above your feed"
                item { StoryBar() }

                //pak zobrazí feed
                items(
                    items = posts,
                    key = { it.id } // Ensure this is the unique document ID
                ) { post ->
                    // Print this to your logcat to see the comparison live
                    Log.d("ICON_CHECK", "PostID: ${post.userId} | MyID: $currentUserId")
                    // Use authManager.client to satisfy the PostItem requirement
                    PostItem(
                        post = post,
                        navController = navController, // Pass navController here
                        authManager = authManager, // Pass the whole manager, not just the client
                        currentUserId = activeUserId, // 2. PASS THE REACTIVE ID HERE
                        viewModel = viewModel, // PASS IT HERE
                        onDeleteSuccess = {
                            // This tells the ViewModel to reload the list
                            // so the deleted post disappears immediately!
                            viewModel.fetchPosts()
                        }
                    )
                }
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostItem(
    post: Post,
    navController: NavController,
    authManager: AuthManager,
    currentUserId: String,
    viewModel: FeedViewModel,
    onDeleteSuccess: () -> Unit = {}
) {
    val databases = io.appwrite.services.Databases(authManager.client)
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    val isOwner = post.userId == currentUserId && currentUserId.isNotEmpty()

    // Determine Post Image URL
    val displayImageUrl = "${AppwriteConstants.ENDPOINT}/storage/buckets/${AppwriteConstants.BUCKET_ID}/files/${post.imageFileId}/view?project=${AppwriteConstants.PROJECT_ID}"

    // Local state for Likes
    var isLiked by remember { mutableStateOf(post.likedBy.contains(currentUserId)) }
    var likesCount by remember { mutableIntStateOf(post.likedBy.size) }
    var showBigHeart by remember { mutableStateOf(false) }

    // --- REFINED AVATAR LOGIC ---
    val liveUrl = authManager.myLatestAvatarUrl
    val avatarUrl = when {
        // If it's my post and I have a new photo, show that immediately
        post.userId == currentUserId && !liveUrl.isNullOrBlank() -> liveUrl

        // Otherwise use what's saved in the database
        post.profileImageUrl.isNotBlank() -> post.profileImageUrl

        // Fallback if everything else is empty
        else -> "https://cloud.appwrite.io/v1/avatars/initials?name=${post.username}"
    }

    fun syncLikeToAppwrite(isNowLiked: Boolean) {
        scope.launch {
            try {
                val currentList = post.likedBy.toMutableList()
                if (isNowLiked) {
                    if (!currentList.contains(currentUserId)) currentList.add(currentUserId)
                } else {
                    currentList.remove(currentUserId)
                }

                databases.updateDocument(
                    databaseId = AppwriteConstants.DATABASE_ID,
                    collectionId = AppwriteConstants.COLLECTION_ID,
                    documentId = post.id,
                    data = mapOf(
                        "likes" to currentList.size,
                        "likedBy" to currentList
                    )
                )
            } catch (e: Exception) {
                Log.e("FeedScreen", "Sync failed: ${e.message}")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Post") },
            text = { Text("Are you sure you want to remove this post?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch {
                        if (authManager.deletePost(post.id)) onDeleteSuccess()
                    }
                }) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        // --- HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Updated Avatar with explicit crossfade and error handling
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    // This ensures Coil treats different URLs as different images
                    .memoryCacheKey(avatarUrl)
                    .diskCacheKey(avatarUrl)
                    .build(),
                contentDescription = "User Avatar",
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .clickable {
                    navController.navigate(Screen.Profile.createRoute(post.userId))
                    },
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Default.AccountCircle)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // --- USERNAME AND TIME AGO ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = post.username,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable {
                        navController.navigate(Screen.Profile.createRoute(post.userId))
                    }
                )

                // The "Little Circle" (Middle Dot)
                Text(
                    text = " • ",
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )

                Text(
                    text = getTimeAgo(post.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isOwner) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                }
            }
        }

        // --- MAIN IMAGE ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (!isLiked) {
                            isLiked = true
                            likesCount++
                            syncLikeToAppwrite(true)
                        }
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        scope.launch {
                            showBigHeart = true
                            delay(800)
                            showBigHeart = false
                        }
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = displayImageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = showBigHeart,
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = scaleOut()
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(110.dp)
                )
            }
        }

        // --- ACTION BAR ---
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = {
                val willBeLiked = !isLiked
                likesCount = if (willBeLiked) likesCount + 1 else likesCount - 1
                isLiked = willBeLiked
                syncLikeToAppwrite(willBeLiked)
            }) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) Color.Red else Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = {
                navController.navigate(Screen.PostDetails.createRoute(post.id))
            }) {
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = "Comment",
                    tint = Color.Black,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // --- CAPTION ---
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            //if (likesCount > 0) {
            Text(
                //text = "$likesCount likes",
                text = if (likesCount == 0) "No likes yet" else if (likesCount == 1) "$likesCount like" else "$likesCount likes",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
            )
            //}
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Red)) { append(post.username) }
                    append(" ${post.caption}")
                },
                style = MaterialTheme.typography.bodyMedium
            )
            // Comments prompt / view all
            if (post.comments.isNotEmpty()) {
                Text(
                    text = "View all ${post.comments.size} comments",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable {
                            navController.navigate(Screen.PostDetails.createRoute(post.id))
                        }
                )
            } else {
                Text(
                    text = "Add a comment...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable {
                            navController.navigate(Screen.PostDetails.createRoute(post.id))
                        }
                )
            }
        }
    }
}

val dummyPosts = listOf(
    Post(
        "1",
        "nature_lover",
        "https://randomuser.me/api/portraits/women/1.jpg",
        "https://picsum.photos/id/10/800/800",
        imageFileId = "65c2a...", // ADD THIS: Put any string here for now
        caption = "Beautiful sunset!",
        likes = 120,
        createdAt = "2026-02-09T10:00:00.000Z" // ADD THIS (ISO 8601 format)
    ),
    Post(
        "2",
        "traveler_pro",
        "https://randomuser.me/api/portraits/men/2.jpg",
        "https://picsum.photos/id/20/800/800",
        imageFileId = "65c2a...", // ADD THIS: Put any string here for now
        caption = "Beautiful sunset!",
        likes = 85,
        createdAt = "2026-02-08T15:30:00.000Z" // ADD THIS
    ),
    Post(
        "3",
        "tech_geek",
        "https://randomuser.me/api/portraits/men/3.jpg",
        "https://picsum.photos/id/30/800/800",
        imageFileId = "65c2a...", // ADD THIS: Put any string here for now
        caption = "Beautiful sunrise!",
        likes = 666,
        createdAt = "2026-02-07T18:22:00.000Z" // ADD THIS
    )
)
