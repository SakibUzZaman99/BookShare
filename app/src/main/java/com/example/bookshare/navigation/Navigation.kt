package com.example.bookshare.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.bookshare.screens.*

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "login") {

        // Auth Screens
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }

        // Main Screens
        composable("home") { HomeScreen(navController) }
        composable("library") { MyLibraryScreen(navController) }
        //composable("browse") { BrowseBooksScreen(navController) }
        composable("borrowed") { BorrowedScreen(navController) }
        composable("lent") { LentScreen(navController) }
        composable("profile") { ProfileScreen(navController) }

        // Book Details Screen
        composable("book/{bookId}") { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
            BookScreen(navController, bookId)
        }

        // Chat Screen
        composable("chat/{requestId}") { backStackEntry ->
            val requestId = backStackEntry.arguments?.getString("requestId") ?: ""
            ChatScreen(navController, requestId)
        }
    }
}
