package com.example.instaclone.ui.theme.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.instaclone.Screen
import com.example.instaclone.data.AuthManager
import com.example.instaclone.data.UploadManager
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun AddPostScreen(
    navController: NavController,
    uploadManager: UploadManager
) {
    val scope = rememberCoroutineScope() // <--- This matches 'scope.launch'

    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var caption by remember { mutableStateOf("") }

    var isUploading by remember { mutableStateOf(false) }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // 1. Camera Launcher: Receives the photo as a Bitmap
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { result ->
        if (result != null) {
            bitmap = result
        }
    }

    // 2. Permission Launcher: Requests camera access if needed
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch()
        } else {
            Toast.makeText(context, "Camera permission denied, dude!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Create New Post",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // --- IMAGE PREVIEW AREA ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color.DarkGray, shape = RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Post Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("No photo captured", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- CAPTION INPUT ---
        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text("Write a caption...") },
            modifier = Modifier.fillMaxWidth(),
            // Correct Material 3 syntax:
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color.Gray,
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- ACTION BUTTONS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Button to trigger Camera (with permission check)
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val status = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    if (status == PackageManager.PERMISSION_GRANTED) {
                        cameraLauncher.launch()
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            ) {
                Text(if (bitmap == null) "Take Photo" else "Retake")
            }

            // Button to "Share" (Upload to Appwrite)
            Button(
                modifier = Modifier.weight(1f),
                enabled = bitmap != null && caption.isNotBlank() && !isUploading, // Disable if uploading

                onClick = {
                    scope.launch {
                        isUploading = true

                        try {
                            // 1. Convert your Bitmap to a temporary File path
                            val file = File(context.cacheDir, "upload_image.jpg")
                            file.outputStream().use {
                                bitmap?.compress(Bitmap.CompressFormat.JPEG, 90, it)
                            }

                            // Get the URI of the file you just saved
                            val fileUri = Uri.fromFile(file)

                            // 2. Call the new manager function
                            // It handles the Client, User, Storage, and Database in one go!
                            val success = uploadManager.uploadPost(
                                imageUri = fileUri, // Use the URI we just made from the bitmap
                                caption = caption
                            )

                            if (success) {
                                // 3. Handle Success
                                bitmap = null
                                caption = ""
                                Toast.makeText(context, "Post Shared!", Toast.LENGTH_LONG).show()
                                // Specifically target the Feed to ensure you stay in the LOGGED_IN graph
                                navController.navigate(Screen.Feed.route) {
                                    popUpTo(Screen.AddPost.route) { inclusive = true }
                                }
                            } else {
                                Toast.makeText(context, "Upload failed. Check your connection.", Toast.LENGTH_LONG).show()
                            }

                        } catch (e: Exception) {
                            // 4. Handle Unexpected Failures (like File IO errors)
                            Log.e("AppwriteError", "UI layer error: ", e)
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isUploading = false
                        }
                    }
                }
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Share Post")
                }
            }
        }
    }
}