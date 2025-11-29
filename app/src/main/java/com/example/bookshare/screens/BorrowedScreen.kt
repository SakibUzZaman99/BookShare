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
import com.example.bookshare.ui.components.BookShareTopBar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun BorrowedScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    var selectedTab by remember { mutableStateOf("Borrowed") }

    data class BorrowedEntry(val book: Book, val requestId: String)

    var borrowedEntries by remember { mutableStateOf<List<BorrowedEntry>>(emptyList()) }
    var borrowRequests by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var snack by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

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
            borrowedEntries = emptyList()
            snack = "Failed to load borrowed books."
        } finally {
            isLoading = false
        }
    }

    suspend fun reloadBorrowRequests() {
        if (currentUserId == null) return
        isLoading = true
        try {
            val pendingReqs = db.collection("requests")
                .whereEqualTo("borrowerId", currentUserId)
                .whereEqualTo("status", "pending")
                .get().await()

            borrowRequests = pendingReqs.documents.map { doc ->
                val data = doc.data ?: emptyMap()
                data + ("id" to doc.id)
            }
        } catch (e: Exception) {
            borrowRequests = emptyList()
            snack = "Failed to load borrow requests."
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            "Borrowed" -> scope.launch { reloadBorrowed() }.join()
            "Borrow Requests" -> scope.launch { reloadBorrowRequests() }.join()
        }
    }

    Scaffold(
        topBar = { BookShareTopBar(navController, title = "Borrow Section") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            // Tabs
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
                    // Accepted Borrowed Books
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

                                            val requestRef = db.collection("requests").document(entry.requestId)
                                            var exchangeStatus by remember { mutableStateOf("") }
                                            var returnStatus by remember { mutableStateOf("") }

                                            // Get both statuses
                                            LaunchedEffect(entry.requestId) {
                                                val snap = requestRef.get().await()
                                                exchangeStatus = snap.getString("exchangeStatus") ?: ""
                                                returnStatus = snap.getString("returnStatus") ?: ""
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                if (exchangeStatus != "confirmed") {
                                                    Button(
                                                        onClick = {
                                                            scope.launch {
                                                                val now = System.currentTimeMillis()
                                                                val snap = requestRef.get().await()
                                                                val lastStatus = snap.getString("exchangeStatus") ?: ""
                                                                val lastTime = snap.getLong("exchangeTimestamp") ?: 0L

                                                                if (lastStatus.startsWith("initiated_") && now - lastTime <= 60000) {
                                                                    requestRef.update(
                                                                        mapOf(
                                                                            "exchangeStatus" to "confirmed",
                                                                            "exchangeTimestamp" to now
                                                                        )
                                                                    )
                                                                    snack = "Exchange confirmed!"
                                                                    exchangeStatus = "confirmed"
                                                                } else {
                                                                    requestRef.update(
                                                                        mapOf(
                                                                            "exchangeStatus" to "initiated_by_borrower",
                                                                            "exchangeTimestamp" to now
                                                                        )
                                                                    )
                                                                    snack = "Waiting for owner to confirm..."
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                                                    ) { Text("Initiate Exchange") }
                                                } else if (returnStatus != "confirmed") {
                                                    Button(
                                                        onClick = {
                                                            scope.launch {
                                                                val now = System.currentTimeMillis()
                                                                val snap = requestRef.get().await()
                                                                val lastStatus = snap.getString("returnStatus") ?: ""
                                                                val lastTime = snap.getLong("returnTimestamp") ?: 0L

                                                                if (lastStatus.startsWith("initiated_") && now - lastTime <= 60000) {
                                                                    requestRef.update(
                                                                        mapOf(
                                                                            "returnStatus" to "confirmed",
                                                                            "returnTimestamp" to now,
                                                                            "status" to "returned"
                                                                        )
                                                                    )
                                                                    db.collection("books")
                                                                        .document(book.id)
                                                                        .update("status", "LENDING")
                                                                    snack = "Return confirmed!"
                                                                    returnStatus = "confirmed"
                                                                } else {
                                                                    requestRef.update(
                                                                        mapOf(
                                                                            "returnStatus" to "initiated_by_borrower",
                                                                            "returnTimestamp" to now
                                                                        )
                                                                    )
                                                                    snack = "Waiting for owner to confirm return..."
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                                                    ) { Text("Initiate Return") }
                                                }

                                                OutlinedButton(
                                                    onClick = {
                                                        navController.navigate("chat/${entry.requestId}")
                                                    }
                                                ) { Text("Message") }
                                            }

                                            if (exchangeStatus == "confirmed" && returnStatus != "confirmed") {
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    "Exchange Confirmed ✔",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            } else if (returnStatus == "confirmed") {
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    "Return Confirmed ✔ Book successfully returned.",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pending Borrow Requests
                    "Borrow Requests" -> {
                        if (borrowRequests.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No pending borrow requests.")
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(borrowRequests) { req ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text("Book ID: ${req["bookId"]}")
                                            Text("Status: ${req["status"]}")
                                            Spacer(Modifier.height(8.dp))
                                            Text("Waiting for owner’s response...")
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
            }
        }
    }
}
