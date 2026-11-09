package com.example.instaclone.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(private val authManager: AuthManager) : ViewModel() {
    var searchQuery by mutableStateOf("")
        private set

    var searchResults by mutableStateOf<List<UserProfile>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        searchQuery = query
        searchJob?.cancel()

        if (query.isBlank()) {
            searchResults = emptyList()
            isLoading = false
            return
        }

        searchJob = viewModelScope.launch {
            isLoading = true
            delay(300) // Debounce search
            searchResults = authManager.searchUsers(query)
            isLoading = false
        }
    }
}

class SearchViewModelFactory(private val authManager: AuthManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SearchViewModel(authManager) as T
    }
}