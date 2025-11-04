package com.example.bookshare.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.example.bookshare.R

@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val activity = context as Activity

    // 🔹 Configure Google Sign-In (for Google logout)
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("My Profile", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        // Show current user info
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Text("Email: ${currentUser.email ?: "N/A"}")
            Spacer(Modifier.height(8.dp))
            Text("User ID: ${currentUser.uid}")
        } else {
            Text("No user logged in.")
        }

        Spacer(Modifier.height(24.dp))

        // 🔹 Logout Button (Firebase + Google)
        Button(
            onClick = {
                // 1️⃣ Firebase Sign-Out
                auth.signOut()
                // 2️⃣ Google Sign-Out (if applicable)
                googleSignInClient.signOut().addOnCompleteListener(activity) {
                    // 3️⃣ Navigate back to Login and clear backstack
                    navController.navigate("login") {
                        popUpTo(0)
                        launchSingleTop = true
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log Out", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
