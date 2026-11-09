package com.example.instaclone.data

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.appwrite.Client
import io.appwrite.services.Databases
import io.appwrite.services.Storage
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

object AppwriteUtils {
    private lateinit var client: Client
    private lateinit var databases: Databases
    private lateinit var storage: Storage

    @RequiresApi(Build.VERSION_CODES.O)
    fun getTimeAgo(dateString: String): String {
        // 1. If the string is empty or just " ", show nothing
        Log.d("DATE_DEBUG", "Date string: $dateString")
        if (dateString.isNullOrBlank()) return ""
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            val date = sdf.parse(dateString)
            val now = System.currentTimeMillis()
            val diff = now - (date?.time ?: now)

            val minutes = diff / 60000
            val hours = minutes / 60
            val days = hours / 24

            when {
                minutes < 1 -> "Just now"
                minutes < 60 -> "${minutes}m"
                hours < 24 -> "${hours}h"
                else -> "${days}d"
            }
        } catch (e: Exception) {
            ""
        }
    }
}
