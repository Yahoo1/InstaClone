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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.instaclone.AppwriteConstants
import com.example.instaclone.Screen
import com.example.instaclone.data.AuthManager
import com.example.instaclone.data.UserProfile
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    navController: NavController,
    authManager: AuthManager
) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var unreadSenderIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    val currentUserId = authManager.currentUserId

    // Fetch users and unread status, plus listen in real-time
    DisposableEffect(Unit) {
        val realtime = io.appwrite.services.Realtime(authManager.client)
        val subscription = realtime.subscribe(
            "databases.${AppwriteConstants.DATABASE_ID}.collections.${AppwriteConstants.MESSAGE_COLLECTION_ID}.documents"
        ) { _ ->
            scope.launch {
                unreadSenderIds = authManager.getUnreadSenderIds()
            }
        }

        scope.launch {
            isLoading = true
            val allUsers = authManager.searchUsers("")
            users = allUsers.filter { it.userId != currentUserId }
            unreadSenderIds = authManager.getUnreadSenderIds()
            isLoading = false
        }

        onDispose {
            try {
                subscription.close()
            } catch (e: Exception) {
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.Black)
            } else if (users.isEmpty()) {
                Text("No other users found to chat with.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(users) { user ->
                        val hasUnread = unreadSenderIds.contains(user.userId)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navController.navigate(
                                        Screen.ChatRoom.createRoute(
                                            user.userId,
                                            user.username
                                        )
                                    )
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                AsyncImage(
                                    model = user.profileImageUrl.ifBlank { "https://cloud.appwrite.io/v1/avatars/initials?name=${user.username}" },
                                    contentDescription = null,
                                    placeholder = rememberVectorPainter(Icons.Default.AccountCircle),
                                    error = rememberVectorPainter(Icons.Default.AccountCircle),
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                )
                                // Red unread dot on profile picture
                                if (hasUnread) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color.Red, CircleShape)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user.username,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (hasUnread) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                                Text(
                                    text = if (hasUnread) "New message" else user.bio.ifBlank { "Tap to chat" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (hasUnread) Color.Black else Color.Gray,
                                    fontWeight = if (hasUnread) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}