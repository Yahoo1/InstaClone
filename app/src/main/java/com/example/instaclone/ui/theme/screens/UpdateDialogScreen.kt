package com.example.instaclone.ui.theme.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.example.instaclone.utils.GithubRelease

@Composable
fun UpdateDialog(
    release: GithubRelease,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Available (${release.tagName}) 🎉") },
        text = {
            Text(
                text = "A new version of InstaClone is available!\n\nRelease Notes:\n${release.releaseNotes}"
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl))
                    context.startActivity(intent)
                    onDismiss()
                }
            ) {
                Text("Download Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}