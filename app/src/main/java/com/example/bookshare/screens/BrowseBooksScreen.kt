package com.example.bookshare.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun BrowseBooksScreen(navController: NavController) {
    val availableBooks = remember {
        mutableStateListOf(
            "Atomic Habits by James Clear",
            "Sapiens by Yuval Noah Harari",
            "Deep Work by Cal Newport",
            "The Pragmatic Programmer"
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Browse Books", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Discover books shared by others.", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableBooks) { book ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(book, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { /* TODO: Send borrow request via Firestore */ },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Request")
                            }
                        }
                    }
                }
            }
        }
    }
}
