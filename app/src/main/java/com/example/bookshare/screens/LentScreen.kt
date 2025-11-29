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

data class LentEntry(
    val book: Book,
    val borrowerId: String,
    val requestId: String
)

@Composable
fun LentScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    var selectedTab by remember { mutableStateOf("Lent") }

    var lentBooks by remember { mutableStateOf<List<LentEntry>>(emptyList()) }
    var lendRequests by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    val scope = rememberCoroutineScope()
    var snack by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedTab) {
        if (currentUserId == null) return@LaunchedEffect

        when (selectedTab) {
            "Lent" -> {
                val acceptedRequests = db.collection("requests")
                    .whereEqualTo("ownerId", currentUserId)
                    .whereEqualTo("status", "accepted")
                    .get().await()

                val entries = acceptedRequests.documents.mapNotNull { doc ->
                    val bookId = doc.getString("bookId") ?: return@mapNotNull null
                    val borrowerId = doc.getString("borrowerId") ?: "Unknown"
                    val bookSnapshot = db.collection("books").document(bookId).get().await()
                    val book = bookSnapshot.toObject(Book::class.java)
                    if (book != null) LentEntry(book, borrowerId, doc.id) else null
                }
                lentBooks = entries
            }

            "Lend Requests" -> {
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

    Scaffold(
        topBar = { BookShareTopBar(navController, title = "Lent Section") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

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

            when (selectedTab) {
                // Lent books
                "Lent" -> {
                    if (lentBooks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("You haven’t lent any books yet.")
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(lentBooks) { entry ->
                                val book = entry.book
                                val borrowerId = entry.borrowerId
                                val requestId = entry.requestId
                                val requestRef = db.collection("requests").document(requestId)

                                var exchangeStatus by remember { mutableStateOf("") }
                                var returnStatus by remember { mutableStateOf("") }

                                // Load statuses
                                LaunchedEffect(requestId) {
                                    val snap = requestRef.get().await()
                                    exchangeStatus = snap.getString("exchangeStatus") ?: ""
                                    returnStatus = snap.getString("returnStatus") ?: ""
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(book.title, style = MaterialTheme.typography.titleMedium)
                                        Text("Borrower: $borrowerId", style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.height(8.dp))

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            // Stage 1: Initiate Exchange
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
                                                                        "exchangeStatus" to "initiated_by_owner",
                                                                        "exchangeTimestamp" to now
                                                                    )
                                                                )
                                                                snack = "Waiting for borrower to confirm..."
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                                                ) { Text("Initiate Exchange") }
                                            }

                                            // Stage 2: Initiate Return
                                            else if (returnStatus != "confirmed") {
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
                                                                        "returnStatus" to "initiated_by_owner",
                                                                        "returnTimestamp" to now
                                                                    )
                                                                )
                                                                snack = "Waiting for borrower to confirm return..."
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                                                ) { Text("Initiate Return") }
                                            }

                                            // Chat button
                                            OutlinedButton(
                                                onClick = { navController.navigate("chat/$requestId") }
                                            ) { Text("Message") }
                                        }

                                        // Status indicators
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
                                                "Return Confirmed ✔ Book returned successfully.",
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

                // Lend Requests
                "Lend Requests" -> {
                    if (lendRequests.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                                                    requestRef.update(
                                                        mapOf(
                                                            "status" to "accepted",
                                                            "exchangeStatus" to "none",
                                                            "exchangeTimestamp" to 0L,
                                                            "returnStatus" to "none",
                                                            "returnTimestamp" to 0L
                                                        )
                                                    )
                                                    db.collection("books").document(bookId)
                                                        .update("status", "LENT")
                                                    snack = "Request accepted!"
                                                },
                                                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                                            ) { Text("Accept") }

                                            OutlinedButton(
                                                onClick = {
                                                    db.collection("requests").document(req["id"].toString())
                                                        .update("status", "rejected")
                                                    snack = "Request rejected."
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

            snack?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
