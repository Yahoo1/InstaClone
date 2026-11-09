package com.example.instaclone.data

data class Story(
    val id: Int,
    val username: String,
    val imageUrl: String
)

val dummyStories = listOf(
    Story(1, "Your Story", "https://randomuser.me/api/portraits/men/5.jpg"),
    Story(2, "kristin_w", "https://randomuser.me/api/portraits/women/6.jpg"),
    Story(3, "mike_travels", "https://randomuser.me/api/portraits/men/7.jpg"),
    Story(4, "art_daily", "https://randomuser.me/api/portraits/women/8.jpg"),
    Story(5, "chef_mario", "https://randomuser.me/api/portraits/men/9.jpg")
)
