package com.example.instaclone.data

data class Post(
    //If you don't want to break your dummy data every time you change the Post class,
    // you can give your properties default values in the data class definition:
    val id: String,
    val username: String = "Unknown", // Default value
    val userId: String, // Add this line!
    val profileImageUrl: String = "",
    val postImageUrl: String = "",
    val imageFileId: String = "",     // Default value
    val caption: String = "",
    val likes: Int = 0,
    val likedBy: List<String> = emptyList(), // Store a list of User IDs
    val comments: List<Comment> = emptyList(), // <-- Add this field
    val createdAt: String = "" // Add this field
)

data class UserProfile(
    val userId: String,
    val username: String,
    val bio: String,
    val profileImageUrl: String
)

data class Comment(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val text: String = "",
    val createdAt: String = ""
)
