package com.example.instaclone.ui.theme.screens

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.instaclone.AppwriteConstants
import com.example.instaclone.Screen
import com.example.instaclone.data.AppwriteUtils.getTimeAgo
import com.example.instaclone.data.AuthManager
import com.example.instaclone.data.Comment
import com.example.instaclone.data.Post
import io.appwrite.services.Databases
import io.appwrite.services.Realtime
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    navController: NavController,
    authManager: AuthManager
) {
    val databases = remember { Databases(authManager.client) }
    val scope = rememberCoroutineScope()

    var post by remember { mutableStateOf<Post?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var commentText by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }

    fun fetchPostDetails() {
        scope.launch {
            isLoading = true
            try {
                val doc = databases.getDocument(
                    databaseId = AppwriteConstants.DATABASE_ID,
                    collectionId = AppwriteConstants.COLLECTION_ID,
                    documentId = postId
                )
                val data = doc.data
                val commentsRaw = data["comments"] as? List<*>
                val commentsList = commentsRaw?.mapNotNull { commentObj ->
                    try {
                        val jsonStr = commentObj?.toString() ?: return@mapNotNull null
                        val json = org.json.JSONObject(jsonStr)
                        Comment(
                            id = json.optString("id"),
                            userId = json.optString("userId"),
                            username = json.optString("username"),
                            text = json.optString("text"),
                            createdAt = json.optString("createdAt")
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

                val likedByRaw = data["likedBy"] as? List<*>
                val likedByList = likedByRaw?.mapNotNull { it?.toString() } ?: emptyList()

                post = Post(
                    id = doc.id,
                    username = data["username"]?.toString() ?: "Anonymous",
                    userId = data["userId"]?.toString() ?: "",
                    profileImageUrl = data["profileImageUrl"]?.toString() ?: "",
                    imageFileId = data["imageFileId"]?.toString() ?: "",
                    caption = data["caption"]?.toString() ?: "",
                    likedBy = likedByList,
                    likes = likedByList.size,
                    comments = commentsList,
                    createdAt = doc.createdAt
                )
            } catch (e: Exception) {
                Log.e("PostDetail", "Failed to fetch post: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    val realtime = remember { Realtime(authManager.client) }

    LaunchedEffect(postId) {
        fetchPostDetails()

        // Subscribe to real-time updates for this specific post document
        try {
            realtime.subscribe(
                "databases.${AppwriteConstants.DATABASE_ID}.collections.${AppwriteConstants.COLLECTION_ID}.documents.$postId"
            ) { event ->
                Log.d("PostDetail", "Realtime update for post $postId: ${event.events}")
                fetchPostDetails() // Instantly refresh comments & likes
            }
        } catch (e: Exception) {
            Log.e("PostDetail", "Realtime subscription failed: ${e.message}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comments") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commentText,
                        onValueChange = { commentText = it },
                        placeholder = { Text("Add a comment...") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3
                    )

                    IconButton(
                        onClick = {
                            if (commentText.isNotBlank() && !isPosting) {
                                val textToSend = commentText
                                commentText = ""
                                isPosting = true
                                scope.launch {
                                    val success = authManager.addComment(postId, textToSend)
                                    if (success) {
                                        fetchPostDetails()
                                    }
                                    isPosting = false
                                }
                            }
                        },
                        enabled = commentText.isNotBlank() && !isPosting
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Comment",
                                tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (post == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Post not found.")
            }
        } else {
            val currentPost = post!!
            val displayImageUrl = "${AppwriteConstants.ENDPOINT}/storage/buckets/${AppwriteConstants.BUCKET_ID}/files/${currentPost.imageFileId}/view?project=${AppwriteConstants.PROJECT_ID}"

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = currentPost.profileImageUrl.ifBlank { "https://cloud.appwrite.io/v1/avatars/initials?name=${currentPost.username}" },
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.LightGray)
                                .clickable {
                                    navController.navigate(Screen.Profile.createRoute(currentPost.userId))
                                },
                            contentScale = ContentScale.Crop,
                            error = rememberVectorPainter(Icons.Default.AccountCircle)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = currentPost.username,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.clickable {
                                    navController.navigate(Screen.Profile.createRoute(currentPost.userId))
                                }
                            )
                            if (currentPost.caption.isNotBlank()) {
                                Text(
                                    text = currentPost.caption,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    AsyncImage(
                        model = displayImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp),
                        contentScale = ContentScale.Crop
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Comments (${currentPost.comments.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                if (currentPost.comments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No comments yet. Be the first to comment!", color = Color.Gray)
                        }
                    }
                } else {
                    items(currentPost.comments) { comment ->
                        CommentItem(comment = comment,
                            currentUserId = authManager.currentUserId,
                            postOwnerId = currentPost.userId,
                            navController = navController,
                            onDeleteComment = { commentId ->
                                scope.launch {
                                    authManager.deleteComment(postId, commentId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CommentItem(
    comment: Comment,
    currentUserId: String,
    postOwnerId: String,
    navController: NavController,
    onDeleteComment: (String) -> Unit
) {
    val canDelete = comment.userId == currentUserId || postOwnerId == currentUserId

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = "https://cloud.appwrite.io/v1/avatars/initials?name=${comment.username}",
            contentDescription = "Avatar",
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.LightGray)
                .clickable {
                    if (comment.userId.isNotBlank()) {
                        navController.navigate(Screen.Profile.createRoute(comment.userId))
                    }
                },
            contentScale = ContentScale.Crop,
            error = rememberVectorPainter(Icons.Default.AccountCircle)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.username,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable {
                        if (comment.userId.isNotBlank()) {
                            navController.navigate(Screen.Profile.createRoute(comment.userId))
                        }
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (comment.createdAt.isNotBlank()) {
                    Text(
                        text = getTimeAgo(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (canDelete) {
            IconButton(
                onClick = { onDeleteComment(comment.id) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Comment",
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

}