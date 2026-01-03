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
    val requestId: String,
    val hasReviewed: Boolean = false
)

@Composable
fun LentScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    var selectedTab by remember { mutableStateOf("Lent") }

    var lentBooks by remember { mutableStateOf<List<LentEntry>>(emptyList()) }
    var lendRequests by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var lentHistory by remember { mutableStateOf<List<LentEntry>>(emptyList()) }

    val scope = rememberCoroutineScope()
    var snack by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // review dialog state (for owner -> borrower)
    var reviewTarget by remember { mutableStateOf<LentEntry?>(null) }
    var showReviewDialog by remember { mutableStateOf(false) }

    // ----------------- Firestore loaders -----------------

    suspend fun reloadLent() {
        if (currentUserId == null) return
        isLoading = true
        try {
            val acceptedRequests = db.collection("requests")
                .whereEqualTo("ownerId", currentUserId)
                .whereEqualTo("status", "accepted")
                .get().await()

            val entries = acceptedRequests.documents.mapNotNull { doc ->
                val bookId = doc.getString("bookId") ?: return@mapNotNull null
                val borrowerId = doc.getString("borrowerId") ?: "Unknown"
                val bookSnapshot = db.collection("books").document(bookId).get().await()
                val book = bookSnapshot.toObject(Book::class.java)
                val reviewed = doc.getBoolean("ownerReviewed") ?: false
                if (book != null) LentEntry(book, borrowerId, doc.id, reviewed) else null
            }
            lentBooks = entries
        } catch (e: Exception) {
            lentBooks = emptyList()
            snack = "Failed to load lent books."
        } finally {
            isLoading = false
        }
    }

    suspend fun reloadLendRequests() {
        if (currentUserId == null) return
        isLoading = true
        try {
            val pendingRequests = db.collection("requests")
                .whereEqualTo("ownerId", currentUserId)
                .whereEqualTo("status", "pending")
                .get().await()

            lendRequests = pendingRequests.documents.map { document ->
                val data = document.data ?: emptyMap()
                data + ("id" to document.id)
            }
        } catch (e: Exception) {
            lendRequests = emptyList()
            snack = "Failed to load lend requests."
        } finally {
            isLoading = false
        }
    }

    suspend fun reloadLentHistory() {
        if (currentUserId == null) return
        isLoading = true
        try {
            val returnedRequests = db.collection("requests")
                .whereEqualTo("ownerId", currentUserId)
                .whereEqualTo("status", "returned")
                .get().await()

            val entries = returnedRequests.documents.mapNotNull { doc ->
                val bookId = doc.getString("bookId") ?: return@mapNotNull null
                val borrowerId = doc.getString("borrowerId") ?: "Unknown"
                val bookSnapshot = db.collection("books").document(bookId).get().await()
                val book = bookSnapshot.toObject(Book::class.java)
                val reviewed = doc.getBoolean("ownerReviewed") ?: false
                if (book != null) LentEntry(book, borrowerId, doc.id, reviewed) else null
            }
            lentHistory = entries
        } catch (e: Exception) {
            lentHistory = emptyList()
            snack = "Failed to load history."
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(selectedTab) {
        if (currentUserId == null) return@LaunchedEffect

        when (selectedTab) {
            "Lent" -> reloadLent()
            "Lend Requests" -> reloadLendRequests()
            "History" -> reloadLentHistory()
        }
    }

    // ----------------- UI -----------------

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

                Button(
                    onClick = { selectedTab = "History" },
                    colors = ButtonDefaults.buttonColors(
                        if (selectedTab == "History") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) { Text("History") }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
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
                                        try {
                                            val snap = requestRef.get().await()
                                            exchangeStatus = snap.getString("exchangeStatus") ?: ""
                                            returnStatus = snap.getString("returnStatus") ?: ""
                                        } catch (_: Exception) {
                                        }
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
                                                // Exchange confirm
                                                if (exchangeStatus != "confirmed") {
                                                    Button(
                                                        onClick = {
                                                            scope.launch {
                                                                try {
                                                                    val now =
                                                                        System.currentTimeMillis()
                                                                    requestRef.update(
                                                                        mapOf(
                                                                            "exchangeStatus" to "confirmed",
                                                                            "exchangeTimestamp" to now
                                                                        )
                                                                    ).await()
                                                                    snack = "Exchange confirmed!"
                                                                    exchangeStatus = "confirmed"
                                                                } catch (e: Exception) {
                                                                    snack =
                                                                        "Failed to confirm exchange."
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                                                    ) { Text("Confirm Exchange") }
                                                }
                                                // Return – ALWAYS mark returned + free book
                                                else if (returnStatus != "confirmed") {
                                                    Button(
                                                        onClick = {
                                                            scope.launch {
                                                                try {
                                                                    val now =
                                                                        System.currentTimeMillis()
                                                                    requestRef.update(
                                                                        mapOf(
                                                                            "returnStatus" to "confirmed",
                                                                            "returnTimestamp" to now,
                                                                            "status" to "returned"
                                                                        )
                                                                    ).await()
                                                                    db.collection("books")
                                                                        .document(book.id)
                                                                        .update("status", "LENDING")
                                                                        .await()
                                                                    snack = "Return confirmed!"
                                                                    returnStatus = "confirmed"

                                                                    reloadLentHistory()
                                                                    reviewTarget =
                                                                        entry.copy(hasReviewed = false)
                                                                    showReviewDialog = true
                                                                } catch (e: Exception) {
                                                                    snack =
                                                                        "Failed to confirm return."
                                                                }
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                                                    ) { Text("Confirm Return") }
                                                }

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
                                                        val requestRef =
                                                            db.collection("requests")
                                                                .document(req["id"].toString())
                                                        val bookId = req["bookId"].toString()
                                                        scope.launch {
                                                            try {
                                                                requestRef.update(
                                                                    mapOf(
                                                                        "status" to "accepted",
                                                                        "exchangeStatus" to "none",
                                                                        "exchangeTimestamp" to 0L,
                                                                        "returnStatus" to "none",
                                                                        "returnTimestamp" to 0L,
                                                                        "borrowerReviewed" to false,
                                                                        "ownerReviewed" to false
                                                                    )
                                                                ).await()
                                                                db.collection("books").document(bookId)
                                                                    .update("status", "LENT")
                                                                    .await()
                                                                snack = "Request accepted!"
                                                                reloadLent()
                                                            } catch (e: Exception) {
                                                                snack = "Failed to accept request."
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                                                ) { Text("Accept") }

                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                db.collection("requests")
                                                                    .document(req["id"].toString())
                                                                    .update("status", "rejected")
                                                                    .await()
                                                                snack = "Request rejected."
                                                                reloadLendRequests()
                                                            } catch (e: Exception) {
                                                                snack = "Failed to reject request."
                                                            }
                                                        }
                                                    }
                                                ) { Text("Reject") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // History
                    "History" -> {
                        if (lentHistory.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No history yet.")
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(lentHistory, key = { it.requestId }) { entry ->
                                    val book = entry.book
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                                            Text("Borrower: ${entry.borrowerId}", style = MaterialTheme.typography.bodySmall)
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                if (entry.hasReviewed) "You have reviewed this borrower."
                                                else "No review yet.",
                                                style = MaterialTheme.typography.bodySmall
                                            )

                                            Spacer(Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(onClick = {
                                                    reviewTarget = entry
                                                    showReviewDialog = true
                                                }) {
                                                    Text(
                                                        if (entry.hasReviewed) "Update Review"
                                                        else "Review"
                                                    )
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

            snack?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // --------------- Review Dialog ---------------

    if (showReviewDialog && reviewTarget != null && currentUserId != null) {
        val entry = reviewTarget!!
        ReviewDialog(
            bookTitle = entry.book.title,
            counterpartLabel = "Borrower",
            showBookRating = false,   // lender cannot rate the book
            onSubmit = { _: Int?, userRating: Int, comment: String ->
                scope.launch {
                    try {
                        val now = System.currentTimeMillis()

                        // Only user review (rate borrower)
                        db.collection("userReviews").add(
                            mapOf(
                                "targetUserId" to entry.borrowerId,
                                "requestId" to entry.requestId,
                                "reviewerId" to currentUserId,
                                "rating" to userRating,
                                "comment" to comment,
                                "timestamp" to now
                            )
                        ).await()

                        db.collection("requests").document(entry.requestId)
                            .update("ownerReviewed", true)
                            .await()

                        lentHistory = lentHistory.map {
                            if (it.requestId == entry.requestId) it.copy(hasReviewed = true) else it
                        }

                        snack = "Thank you for your review!"
                    } catch (e: Exception) {
                        snack = "Failed to submit review."
                    }
                }

                showReviewDialog = false
                reviewTarget = null
            },
            onSkip = {
                showReviewDialog = false
                reviewTarget = null
            }
        )
    }
}
