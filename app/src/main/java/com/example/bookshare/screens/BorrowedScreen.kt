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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun BorrowedScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    var selectedTab by remember { mutableStateOf("Borrowed") }

    // We keep requestId with the book so "Return" can update the exact request doc
    data class BorrowedEntry(val book: Book, val requestId: String)

    var borrowedEntries by remember { mutableStateOf<List<BorrowedEntry>>(emptyList()) }
    var requests by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var snack by remember { mutableStateOf<String?>(null) }

    suspend fun reloadBorrowed() {
        if (currentUserId == null) return
        isLoading = true
        try {
            val acceptedReqs = db.collection("requests")
                .whereEqualTo("borrowerId", currentUserId)
                .whereEqualTo("status", "accepted")
                .get().await()

            val entries = acceptedReqs.documents.mapNotNull { reqDoc ->
                val bookId = reqDoc.getString("bookId") ?: return@mapNotNull null
                val bookSnap = db.collection("books").document(bookId).get().await()
                val bookObj = bookSnap.toObject(Book::class.java)?.copy(id = bookSnap.id)
                if (bookObj != null) BorrowedEntry(bookObj, reqDoc.id) else null
            }
            borrowedEntries = entries
        } catch (e: Exception) {
            e.printStackTrace()
            borrowedEntries = emptyList()
            snack = "Failed to load borrowed books."
        } finally {
            isLoading = false
        }
    }

    suspend fun reloadPendingRequests() {
        if (currentUserId == null) return
        isLoading = true
        try {
            val snapshot = db.collection("requests")
                .whereEqualTo("borrowerId", currentUserId)
                .whereEqualTo("status", "pending")
                .get().await()
            requests = snapshot.documents.map { it.data ?: emptyMap() }
        } catch (e: Exception) {
            e.printStackTrace()
            requests = emptyList()
            snack = "Failed to load pending requests."
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            "Borrowed" -> scope.launch { reloadBorrowed() }.join()
            "Borrow Requests" -> scope.launch { reloadPendingRequests() }.join()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Borrow Section", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { selectedTab = "Borrowed" },
                    colors = ButtonDefaults.buttonColors(
                        if (selectedTab == "Borrowed") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("Borrowed") }

                Button(
                    onClick = { selectedTab = "Borrow Requests" },
                    colors = ButtonDefaults.buttonColors(
                        if (selectedTab == "Borrow Requests") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("Borrow Requests") }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    "Borrowed" -> {
                        if (borrowedEntries.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No borrowed books yet.")
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(borrowedEntries, key = { it.book.id }) { entry ->
                                    val book = entry.book
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                                            Text("Owner: ${book.ownerId}", style = MaterialTheme.typography.bodySmall)
                                            Spacer(Modifier.height(8.dp))

                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            // 1) Set book back to LENDING
                                                            db.collection("books")
                                                                .document(book.id)
                                                                .update("status", "LENDING")
                                                                .await()

                                                            // 2) Mark THIS request as returned
                                                            db.collection("requests")
                                                                .document(entry.requestId)
                                                                .update("status", "returned")
                                                                .await()

                                                            // 3) Refresh list
                                                            reloadBorrowed()
                                                            snack = "Book returned."
                                                        } catch (e: Exception) {
                                                            e.printStackTrace()
                                                            snack = "Return failed."
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                                            ) {
                                                Text("Return Book")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Borrow Requests" -> {
                        if (requests.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No pending borrow requests.")
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(requests) { req ->
                                    Card(
                                        Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text("Book ID: ${req["bookId"]}")
                                            Text("Status: ${req["status"]}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            snack?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                LaunchedEffect(it) {
                    // auto-clear message
                    kotlinx.coroutines.delay(2000)
                    snack = null
                }
            }
        }
    }
}
