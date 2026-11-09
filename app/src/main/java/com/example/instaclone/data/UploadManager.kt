package com.example.instaclone.data

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import io.appwrite.services.Databases
import io.appwrite.services.Storage
import io.appwrite.models.InputFile
import java.util.UUID
import com.example.instaclone.AppwriteConstants
import io.appwrite.ID
import io.appwrite.exceptions.AppwriteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class UploadManager(private val authManager: AuthManager, private val context: Context) {
    private val storage = Storage(authManager.client)
    private val databases = Databases(authManager.client)

    suspend fun uploadPost(imageUri: Uri, caption: String): Boolean {
        return try {
            // 1. Convert URI to bytes
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val bytes = inputStream?.readBytes() ?: return false
            inputStream.close()

            val userId = authManager.currentUserId ?: return false
            val user = authManager.account.get()
            val actualName = user.name

            // 2. Fetch the user's CURRENT profile image
            val actualAvatarUrl = try {
                // CRITICAL: Ensure this collectionId is your "users" collection, NOT "posts"
                val userDoc = databases.getDocument(
                    databaseId = AppwriteConstants.DATABASE_ID,
                    collectionId = "users", // Double-check this string matches Appwrite
                    documentId = userId
                )
                val dbUrl = userDoc.data["profileImageUrl"]?.toString()

                Log.d("UPLOAD_DEBUG", "Found URL in DB: $dbUrl")

                if (dbUrl.isNullOrBlank()) {
                    "https://cloud.appwrite.io/v1/avatars/initials?name=${actualName}"
                } else {
                    dbUrl
                }
            } catch (e: Exception) {
                // If this hits, it means Appwrite couldn't find the user document
                Log.e("UPLOAD_DEBUG", "Failed to fetch user doc: ${e.message}")
                "https://cloud.appwrite.io/v1/avatars/initials?name=${actualName}"
            }

            // 3. Upload the post image to Storage
            val fileId = ID.unique()
            storage.createFile(
                bucketId = AppwriteConstants.BUCKET_ID,
                fileId = fileId,
                file = InputFile.fromBytes(
                    bytes = bytes,
                    filename = "post_${System.currentTimeMillis()}.jpg",
                    mimeType = "image/jpeg"
                )
            )

            // 4. Create the Document in the Posts Collection
            databases.createDocument(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = AppwriteConstants.COLLECTION_ID, // This is your Posts collection
                documentId = ID.unique(),
                data = mapOf(
                    "userId" to userId,
                    "username" to actualName,
                    "imageFileId" to fileId,
                    "caption" to caption,
                    "createdAt" to System.currentTimeMillis().toString(),
                    "likes" to 0,
                    "likedBy" to emptyList<String>(),
                    "profileImageUrl" to actualAvatarUrl // This will now use the DB URL if found
                )
            )
            true
        } catch (e: Exception) {
            Log.e("UPLOAD_ERROR", "Final upload failed: ${e.message}")
            false
        }
    }

    suspend fun updateProfileImage(imageUri: Uri): String? {
        var fileId = ""
        var newUrl = ""
        val userId = authManager.currentUserId ?: return null

        // Delete previous avatar from AppWrite storage
        // 1. Get the current document to find the old file ID
        val oldUrl = try {
            val userDoc = databases.getDocument(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = "users",
                documentId = userId
            )
            userDoc.data["profileImageUrl"]?.toString()
        } catch (e: Exception) {
            null // Might be a new user with no document yet
        }

        return try {
            //val userId = authManager.currentUserId ?: return null

            // 2. Upload the NEW file to AppWrite storage
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val bytes = inputStream?.readBytes() ?: return null
            inputStream.close()

            val resultFile = storage.createFile(
                bucketId = AppwriteConstants.BUCKET_ID,
                fileId = ID.unique(),
                file = InputFile.fromBytes(
                    bytes = bytes,
                    filename = "avatar_${userId}.jpg",
                    mimeType = "image/jpeg"
                )
            )
            fileId = resultFile.id
            newUrl = "${AppwriteConstants.ENDPOINT}/storage/buckets/${AppwriteConstants.BUCKET_ID}/files/$fileId/view?project=${AppwriteConstants.PROJECT_ID}"

            // 3. Delete the OLD file if it exists and is a storage file
            oldUrl?.let { url ->
                // We only delete if it's a link to OUR storage, not the Initials API
                if (url.contains("/storage/buckets/${AppwriteConstants.BUCKET_ID}/files/")) {
                    val oldFileId = extractFileIdFromUrl(url)
                    if (oldFileId != null) {
                        try {
                            storage.deleteFile(
                                bucketId = AppwriteConstants.BUCKET_ID,
                                fileId = oldFileId
                            )
                            Log.d("CLEANUP", "Deleted old file: $oldFileId")
                        } catch (e: Exception) {
                            // If deletion fails, we don't stop the app—just log it
                            Log.e("CLEANUP", "Delete failed: ${e.message}")
                        }
                    }
                }
            }

            // 4. Update Database with the NEW profile image URL
            try {
                databases.updateDocument(
                    databaseId = AppwriteConstants.DATABASE_ID,
                    collectionId = "users",
                    documentId = userId,
                    data = mapOf("profileImageUrl" to newUrl)
                )
            } catch (e: Exception) {
                val user = authManager.account.get()
                databases.createDocument(
                    databaseId = AppwriteConstants.DATABASE_ID,
                    collectionId = "users",
                    documentId = userId,
                    data = mapOf(
                        "userId" to userId,
                        "username" to user.name,
                        "profileImageUrl" to newUrl,
                        "bio" to "New to InstaClone!"
                    )
                )
            }

            // 5. Update the local state FIRST so the UI feels instant for the current user
            authManager.updateLocalAvatar(newUrl)

            // 6. Launch the database sync in the background so we can return 'newUrl' immediately
            // We find all posts made by this user and update their avatar link
            CoroutineScope(Dispatchers.IO).launch {
                syncAvatarToAllMyPosts(userId, newUrl)
            }

            newUrl

        } catch (e: Exception) {
            Log.e("UploadManager", "Total failure: ${e.message}")
            null
        }
    }

    private suspend fun syncAvatarToAllMyPosts(userId: String, newUrl: String) {
        try {
            val userPosts = databases.listDocuments(
                databaseId = AppwriteConstants.DATABASE_ID,
                collectionId = AppwriteConstants.COLLECTION_ID,
                queries = listOf(
                    io.appwrite.Query.equal("userId", userId),
                    io.appwrite.Query.limit(100) // last 100 posts change avatar
                    )
            )

            userPosts.documents.forEach { postDoc ->
                databases.updateDocument(
                    databaseId = AppwriteConstants.DATABASE_ID,
                    collectionId = AppwriteConstants.COLLECTION_ID,
                    documentId = postDoc.id,
                    data = mapOf("profileImageUrl" to newUrl)
                )
            }
            Log.d("UploadManager", "Synced avatar to ${userPosts.documents.size} posts")
        } catch (e: Exception) {
            Log.e("UploadManager", "Post sync failed: ${e.message}")
        }
    }

    private fun extractFileIdFromUrl(url: String): String? {
        return try {
            // This cuts the string to get exactly what's between /files/ and /view
            url.substringAfter("/files/").substringBefore("/view")
        } catch (e: Exception) {
            null
        }
    }
}
