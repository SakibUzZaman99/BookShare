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
import kotlinx.coroutines.tasks.await

@Composable
fun LentScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    var selectedTab by remember { mutableStateOf("Lent") }

    // 🔹 Lists for data
    var lentBooks by remember { mutableStateOf<List<Pair<Book, String>>>(emptyList()) } // Pair<Book, BorrowerNameOrId>
    var lendRequests by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    // 🔹 Fetch data whenever tab changes
    LaunchedEffect(selectedTab) {
        if (currentUserId == null) return@LaunchedEffect

        when (selectedTab) {
            "Lent" -> {
                // Show only accepted lend requests
                val acceptedRequests = db.collection("requests")
                    .whereEqualTo("ownerId", currentUserId)
                    .whereEqualTo("status", "accepted")
                    .get().await()

                val bookPairs = acceptedRequests.documents.mapNotNull { doc ->
                    val bookId = doc.getString("bookId") ?: return@mapNotNull null
                    val borrowerId = doc.getString("borrowerId") ?: "Unknown"

                    val bookSnapshot = db.collection("books").document(bookId).get().await()
                    val book = bookSnapshot.toObject(Book::class.java)
                    if (book != null) Pair(book, borrowerId) else null
                }
                lentBooks = bookPairs
            }

            "Lend Requests" -> {
                // Show pending requests for this user's books
                val pendingRequests = db.collection("requests")
                    .whereEqualTo("ownerId", currentUserId)
                    .whereEqualTo("status", "pending")
                    .get().await()

                lendRequests = pendingRequests.documents.map { document ->
                    val data = document.data ?: emptyMap()
                    data + ("id" to document.id)
                }
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Lent Section", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            // 🔹 Tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { selectedTab = "Lent" },
                    colors = ButtonDefaults.buttonColors(
                        if (selectedTab == "Lent") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("Lent") }

                Button(
                    onClick = { selectedTab = "Lend Requests" },
                    colors = ButtonDefaults.buttonColors(
                        if (selectedTab == "Lend Requests") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("Lend Requests") }
            }

            Spacer(Modifier.height(16.dp))

            // 🔹 Content
            when (selectedTab) {
                "Lent" -> {
                    if (lentBooks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("You haven’t lent any books yet.")
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(lentBooks) { (book, borrowerId) ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(book.title, style = MaterialTheme.typography.titleMedium)
                                        Text("Borrower: $borrowerId", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }

                "Lend Requests" -> {
                    if (lendRequests.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No new lend requests.")
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(lendRequests) { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("Book ID: ${req["bookId"]}")
                                        Text("Borrower ID: ${req["borrowerId"]}")
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    val requestRef = db.collection("requests").document(req["id"].toString())
                                                    val bookId = req["bookId"].toString()

                                                    // ✅ Accept request
                                                    requestRef.update("status", "accepted")

                                                    // ✅ Update book status to LENT
                                                    db.collection("books").document(bookId)
                                                        .update("status", "LENT")

                                                },
                                                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                                            ) { Text("Accept") }


                                            OutlinedButton(
                                                onClick = {
                                                    db.collection("requests").document(req["id"].toString())
                                                        .update("status", "rejected")
                                                }
                                            ) { Text("Reject") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
