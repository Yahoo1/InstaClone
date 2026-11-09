package com.example.instaclone.data

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.instaclone.AppwriteConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import io.appwrite.services.Realtime
import io.appwrite.models.RealtimeSubscription

class FeedViewModel(val authManager: AuthManager) : ViewModel() {

    // The UI can read this, but only the ViewModel can change it
    // Start as true so the user sees a loader while we check the database
    var isLoading by mutableStateOf(false)
        private set

    // This is the private "writable" state
    private val _posts = MutableStateFlow<List<Post>>(emptyList())

    // This is the public "read-only" state your UI listens to
    val posts: StateFlow<List<Post>> = _posts

    private val realtime = Realtime(authManager.client)
    //private var subscription: RealtimeSubscription? = null

    init {
        viewModelScope.launch {
            // First check if session is active
            if (authManager.checkUserSession()) {
                // Then immediately prime the avatar state
                authManager.initializeUserSync()

                // Now fetch your posts
                fetchPosts()

                startRealtimeSubscription()
            }
        }
    }

    private fun startRealtimeSubscription() {
        try {
            realtime.subscribe(
                "databases.${AppwriteConstants.DATABASE_ID}.collections.${AppwriteConstants.COLLECTION_ID}.documents"
            ) { event ->
                Log.d("Realtime", "Event received: ${event.events}")
                // Automatically re-fetch posts when any document changes
                fetchPosts()
            }
        } catch (e: Exception) {
            Log.e("Realtime", "Subscription failed: ${e.message}")
        }
    }

    private var fetchJob: Job? = null

    fun fetchPosts() {
        // 1. Cancel any previous attempt before starting a new one
        fetchJob?.cancel()

        fetchJob = viewModelScope.launch {
            isLoading = true // Start loading, start the "Circle graphics"
            try {
                // Add a small delay if you want to actually see the spinner
                //delay(500)
                // Use the function we just fixed in the manager!
                Log.d("FeedViewModel", "Starting Fetch...")
                val fetchedPosts = authManager.getAllPosts()
                Log.d("FeedViewModel", "Fetch Finished!")
                // --- THE CHECK GOES HERE ---
                if (isActive) {
                    Log.d("FeedViewModel", "Fetch Finished and active!")
                    _posts.value = fetchedPosts
                } else {
                    Log.d("FeedViewModel", "Fetch finished but job was cancelled. Ignoring result.")
                }
            } catch (e: Exception) {
                // Note: CancellationException is a normal part of coroutines
                if (e is CancellationException) throw e // Let the system handle the cancel
                Log.e("FeedViewModel", "Real error: ${e.message}")
            } finally {
                // Only stop the loading spinner if this specific job is still the current one
                if (isActive) {
                    isLoading = false
                }
            }
        }
    }

//    override fun onCleared() {
//        super.onCleared()
//        try {
//            subscription?.close()
//        } catch (e: Exception) {
//            Log.e("Realtime", "Close subscription failed: ${e.message}")
//        }
//    }
}

class FeedViewModelFactory(private val authManager: AuthManager) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FeedViewModel(authManager) as T
    }
}
