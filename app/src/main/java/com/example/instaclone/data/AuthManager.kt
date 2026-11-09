package com.example.instaclone.data

import android.R.attr.password
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.example.instaclone.AppwriteConstants
import io.appwrite.ID
import io.appwrite.Query
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.InputFile
import io.appwrite.services.Account
import io.appwrite.services.Databases
import io.appwrite.services.Storage
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.text.get

class AuthManager(val client: io.appwrite.Client) { // Add 'val' here to make it a public property
    init {
        // This prints a unique memory address for this specific object
        Log.d("AUTH_DEBUG", "AuthManager Created! Memory Address: ${System.identityHashCode(this)}")
    }
    val account = io.appwrite.services.Account(client)
    private val databases = io.appwrite.services.Databases(client)
    private val storage = io.appwrite.services.Storage(client) // Define here

    // Add this variable at the top of the class
    var currentUserId by mutableStateOf("")
    var userName: String? = null

    var myLatestAvatarUrl by mutableStateOf<String?>(null)
        private set // Only AuthManager can change it

    fun updateLocalAvatar(url: String) {
        myLatestAvatarUrl = url // This triggers the 'when' block in your PostItem
    }

    suspend fun getUserPosts(userId: String): List<Post> {
        return try {
            val response = databases.listDocuments(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = AppwriteConstants.COLLECTION_ID,
                // This is the filter that looks for YOUR userId
                queries = listOf(Query.equal("userId", userId))
            )
            response.documents.map { doc ->
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

                Post(
                    id = doc.id, // Here we assign the String ID
                    username = doc.data["username"]?.toString() ?: "Anonymous",
                    userId = doc.data["userId"]?.toString() ?: "",
                    profileImageUrl = doc.data["profileImageUrl"].toString(),
                    postImageUrl = "", // Handled by storage URL in UI
                    imageFileId = doc.data["imageFileId"].toString(),
                    caption = doc.data["caption"].toString(),
                    likes = (doc.data["likes"] as? Number)?.toInt() ?: 0,
                    comments = commentsList,
                    createdAt = doc.createdAt // Matches your new schema
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("Appwrite", "Profile fetch error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAllPosts(): List<Post> {
        return try {
            val response = databases.listDocuments(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = AppwriteConstants.COLLECTION_ID,
                // We omit the Query.equal filter to get everything
                queries = listOf(Query.orderDesc("\$createdAt"))
            )

            // Map the Appwrite documents to your Post data class
            response.documents.map { doc ->
                // Use Log.d here to see the RAW data coming from Appwrite
                Log.d("MAPPING_CHECK", "Doc Data: ${doc.data}")

                // Construct the Appwrite Preview URL for the image
//                val imageId = doc.data["imageFileId"].toString()
//                val imageUrl = "${AppwriteConstants.ENDPOINT}/storage/buckets/${AppwriteConstants.BUCKET_ID}/files/$imageId/preview?project=${AppwriteConstants.PROJECT_ID}"

                val data = doc.data

                // 1. Specifically extract the likedBy list from Appwrite
                // Appwrite returns arrays as List<Any>, so we cast it safely
                val likedByRaw = data["likedBy"] as? List<*>
                val likedByList = likedByRaw?.mapNotNull { it?.toString() } ?: emptyList()

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

                Post(
                    id = doc.id, // Here we assign the String ID
                    username = data["username"]?.toString() ?: "Anonymous",
                    userId = data["userId"]?.toString() ?: "",
                    profileImageUrl = data["profileImageUrl"]?.toString() ?: "",
                    //postImageUrl = doc.data["profileImageUrl"]?.toString() ?: "",
                    imageFileId = data["imageFileId"]?.toString() ?: "",
                    caption = data["caption"]?.toString() ?: "",

                    // 2. Map these to your Post data class
                    likedBy = likedByList,
                    likes = likedByList.size, // This ensures the number matches the list size
                    comments = commentsList,

                    // Appwrite SDK maps $createdAt to doc.createdAt automatically
                    createdAt = doc.createdAt
                )
            }
        } catch (e: AppwriteException) {
            Log.e("AppwriteError", "Failed to fetch posts: ${e.message}")
            emptyList() // Return an empty list if things go wrong
        }
    }

    // 1. REGISTRATION
    suspend fun register(email: String, password: String, name: String): Boolean {
        return try {
            // 1. Create the Auth Account
            val response = account.create(
                userId = ID.unique(),
                email = email,
                password = password,
                name = name
            )

            // This is the "Golden ID" that links Auth to Database
            val userId = response.id

            // 2. Create the Database Row IMMEDIATELY
            // Use the SAME userId for the Document ID
            databases.createDocument(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = "users",
                documentId = userId, // CRITICAL: Link Auth ID to Doc ID
                data = mapOf(
                    "userId" to userId,
                    "username" to name,
                    "bio" to "New to InstaClone!",
                    "profileImageUrl" to ""
                )
            )

            // 3. Log the user in so they can start using the app
            account.createEmailPasswordSession(email, password)
            true
        } catch (e: Exception) {
            Log.e("AuthManager", "Registration Error: ${e.message}")
            false
        }
    }

    // 2. LOGIN (Creating a Session)
    suspend fun login(email: String, pass: String): Boolean {
        return try {
            // 1. Check if we already have a session
            val currentSession = try {
                account.getSession("current")
            } catch (e: Exception) {
                null
            }

            if (currentSession != null) {
                Log.d("AUTH_DEBUG", "Already logged in as: ${currentSession.userId}")
                this.currentUserId = currentSession.userId
                return true
            }

            // 2. No session? Proceed with normal login
            val session = account.createEmailPasswordSession(email, pass)
            // 🔥 IMMEDIATE UPDATE: Don't wait for getCurrentUser
            val user = account.get()
            userName = user.name
            currentUserId = user.id
            Log.d("AUTH_DEBUG", "LOGIN SUCCESS! ID is: $currentUserId")
            true
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Unknown error"
            // Ignore the "session already active" error and treat it as success
            if (errorMsg.contains("prohibited")) {
                return true
            }
            Log.e("AUTH_DEBUG", "LOGIN FAILED: $errorMsg")
            false
        }
    }

    // 3. GET CURRENT USER
    suspend fun getCurrentUser(): io.appwrite.models.User<Map<String, Any>>? {
        return try {
            val user = account.get() // Fetches the current user model, this returns the User object
            currentUserId = user.id // Store the ID for the "isOwner" check!
            Log.d("AUTH_DEBUG", "SUCCESS! ID is: ${user.id}") // Add this
            user // Now name and id are accessible
        } catch (e: Exception) {
            currentUserId = ""
            // CHANGE THIS LINE to see the actual error in Logcat
            Log.e("AUTH_DEBUG", "FAILURE! Reason: ${e.message}", e)
            null
        }
    }

    // 4. LOGOUT
    suspend fun logout() = try {
        account.deleteSession("current")
        currentUserId = "" // Reset this so the UI knows no one is logged in
        true
    } catch (e: Exception) {
        Log.e("AuthManager", "Logout failed", e)
        false
    }

    // 5. DELETE POST
    suspend fun deletePost(postId: String): Boolean {
        return try {
            databases.deleteDocument(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = AppwriteConstants.COLLECTION_ID,
                documentId = postId
            )
            true
        } catch (e: Exception) {
            Log.e("Appwrite", "Delete failed: ${e.message}")
            false
        }
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val document = databases.getDocument(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = "users",
                documentId = userId
            )

            UserProfile(
                userId = document.data["userId"].toString(),
                username = document.data["username"].toString(),
                bio = document.data["bio"].toString(),
                profileImageUrl = document.data["profileImageUrl"].toString()
            )
        } catch (e: Exception) {
            Log.e("AUTH_DEBUG", "Error fetching profile: ${e.message}")
            null
        }
    }

    // 1. The check function
    suspend fun checkUserSession(): Boolean {
        return try {
            val user = account.get()
            currentUserId = user.id
            userName = user.name
            Log.d("AUTH_DEBUG", "Session recovered for: $userName")
            true
        } catch (e: Exception) {
            currentUserId = ""
            userName = null
            false
        }
    }

    // 2. The Sync function we discussed
    suspend fun initializeUserSync() {
        val userId = currentUserId.ifEmpty {
            try { account.get().id } catch(e: Exception) { "" }
        }

        if (userId.isEmpty()) return

        try {
            val document = databases.getDocument(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = "users", // Your users collection ID
                documentId = userId
            )
            val url = document.data["profileImageUrl"]?.toString()
            if (!url.isNullOrBlank()) {
                updateLocalAvatar(url)
                Log.d("AUTH_SYNC", "Initial avatar loaded: $url")
            }
        } catch (e: Exception) {
            Log.e("AUTH_SYNC", "User doc fetch failed: ${e.message}")
        }
    }

    fun clearLocalSession() {
        currentUserId = ""
        myLatestAvatarUrl = null
        // Clear any other cached user data here (username, bio, etc.)
        Log.d("AUTH_CLEANUP", "Local state cleared for logout")
    }

    //function that queries the "users" collection in Appwrite using Query.search
    suspend fun searchUsers(query: String): List<UserProfile> {
        return try {
            // Fetch all users without strict query index requirements
            val response = databases.listDocuments(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = "users"
            )

            response.documents.map { doc ->
                UserProfile(
                    userId = doc.data["userId"]?.toString() ?: doc.id,
                    username = doc.data["username"]?.toString() ?: "Unknown",
                    bio = doc.data["bio"]?.toString() ?: "",
                    profileImageUrl = doc.data["profileImageUrl"]?.toString() ?: ""
                )
            }.filter { user ->
                // Case-insensitive filtering in Kotlin
                user.username.contains(query, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e("AuthManager", "Search users failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun addComment(postId: String, text: String): Boolean {
        return try {
            val user = account.get()
            val userId = user.id
            val username = user.name

            val postDoc = databases.getDocument(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = AppwriteConstants.COLLECTION_ID,
                documentId = postId
            )

            val existingCommentsRaw = postDoc.data["comments"] as? List<*> ?: emptyList<Any>()

            val newCommentJson = org.json.JSONObject().apply {
                put("id", io.appwrite.ID.unique())
                put("userId", userId)
                put("username", username)
                put("text", text)
                put("createdAt", System.currentTimeMillis().toString())
            }.toString()

            val updatedComments = existingCommentsRaw.mapNotNull { it?.toString() } + newCommentJson

            databases.updateDocument(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = AppwriteConstants.COLLECTION_ID,
                documentId = postId,
                data = mapOf("comments" to updatedComments)
            )
            true
        } catch (e: Exception) {
            Log.e("AuthManager", "Add comment failed: ${e.message}")
            false
        }
    }

    suspend fun deleteComment(postId: String, commentId: String): Boolean {
        return try {
            val postDoc = databases.getDocument(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = AppwriteConstants.COLLECTION_ID,
                documentId = postId
            )

            val existingCommentsRaw = postDoc.data["comments"] as? List<*> ?: emptyList<Any>()

            // Filter out the comment matching commentId
            val updatedComments = existingCommentsRaw.mapNotNull { it?.toString() }.filter { jsonStr ->
                try {
                    val json = org.json.JSONObject(jsonStr)
                    json.optString("id") != commentId
                } catch (e: Exception) {
                    true
                }
            }

            databases.updateDocument(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = AppwriteConstants.COLLECTION_ID,
                documentId = postId,
                data = mapOf("comments" to updatedComments)
            )
            true
        } catch (e: Exception) {
            Log.e("AuthManager", "Delete comment failed: ${e.message}")
            false
        }
    }
}
