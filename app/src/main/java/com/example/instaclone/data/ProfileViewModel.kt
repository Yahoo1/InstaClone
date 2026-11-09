package com.example.instaclone.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.instaclone.AppwriteConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProfileViewModel(private val authManager: AuthManager,
                       private val targetUserId: String = "") : ViewModel() {

    // States for the UI to observe
    var username by mutableStateOf("")
    var bio by mutableStateOf("")
    var profileImageUrl by mutableStateOf("")
        private set
    var isUpdating by mutableStateOf(false)

    init {
        loadUserProfile()
    }

    private fun loadUserProfile() {
        viewModelScope.launch {
            val userIdToLoad = targetUserId.ifBlank { authManager.currentUserId } //if (targetUserId.isBlank()) authManager.currentUserId else targetUserId
            val user = authManager.getUserProfile(userIdToLoad)
            user?.let {
                username = it.username
                bio = it.bio
                profileImageUrl = it.profileImageUrl
            }
        }
    }

    suspend fun updateAvatar(uri: Uri, uploadManager: UploadManager): Boolean {
        isUpdating = true // Start the progress bar

        return try {
            // This now returns the FULL URL (based on our previous step)
            val newUrl = uploadManager.updateProfileImage(uri)

            if (newUrl != null) {
                profileImageUrl = newUrl // Update your local ViewModel state
                // Give the UI a moment to breathe so the bar doesn't flicker
                delay(800) // Delay for half a second, aby byl vidět celý progress bar
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error updating avatar: ${e.message}")
            false
        } finally {
            // THIS IS THE FIX: No matter what happens above,
            // the progress bar WILL turn off now.
            isUpdating = false
        }
    }
}

// Add this inside your ProfileViewModel.kt file at the bottom (outside the class)
class ProfileViewModelFactory(
    private val authManager: AuthManager,
    private val targetUserId: String = ""
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ProfileViewModel(authManager, targetUserId) as T
    }
}
