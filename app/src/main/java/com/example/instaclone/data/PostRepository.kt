package com.example.instaclone.data

import com.example.instaclone.ui.theme.screens.dummyPosts

class PostRepository {
    fun getPosts(): List<Post> {
        // In the future, this is where you'd choose
        // between fetching from Room (Local) or Retrofit (API)
        return dummyPosts
    }
}