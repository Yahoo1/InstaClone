package com.example.instaclone

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Logout : Screen("logout")
    object Register : Screen("register")
    object Splash : Screen("splash")
    object Upload : Screen("upload")
    object Feed : Screen("feed")
    object Search : Screen("search")
    object AddPost : Screen("add_post")
    //object Profile : Screen("profile")
    object Profile : Screen("profile/{userId}") {
        fun createRoute(userId: String) = "profile/$userId"
    }
    object PostDetails : Screen("details/{postId}") {
        fun createRoute(postId: String) = "details/$postId" // {postId} is a variable
    }
    // NEW CHAT ROUTES
    object ChatList : Screen("chat_list")
    object ChatRoom : Screen("chat_room/{recipientId}/{recipientName}") {
        fun createRoute(recipientId: String, recipientName: String) = "chat_room/$recipientId/$recipientName"
    }
}

