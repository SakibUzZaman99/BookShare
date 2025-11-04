package com.example.bookshare

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.example.bookshare.navigation.AppNavHost
import com.example.bookshare.ui.theme.BookShareTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        Log.d("BookShareDebug", "Firebase connected successfully!")

        setContent {
            BookShareTheme {
                val navController = rememberNavController()
                AppNavHost(navController)
            }
        }
    }
}
