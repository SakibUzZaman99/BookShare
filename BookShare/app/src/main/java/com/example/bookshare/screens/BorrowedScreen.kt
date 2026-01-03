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

    data class BorrowedEntry(
        val book: Book,
        val requestId: String,
        val hasReviewed: Boolean = false
    )

    var borrowedEntries by remember { mutableStateOf<List<BorrowedEntry>>(emptyList()) }
    var borrowRequests by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var borrowHistory by remember { mutableStateOf<List<BorrowedEntry>>(emptyList()) }

    val scope = rememberCoroutineScope()
    var snack by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // review dialog state (for borrower -> book + owner)
    var reviewTarget by remember { mutableStateOf<BorrowedEntry?>(null) }
    var showReviewDialog by remember { mutableStateOf(false) }

    // ----------------- Firestore loaders -----------------

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
                val reviewed = reqDoc.getBoolean("borrowerReviewed") ?: false
                if (bookObj != null) BorrowedEntry(bookObj, reqDoc.id, reviewed) else null
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

    suspend fun reloadBorrowHistory() {
        if (currentUserId == null) return
        isLoading = true
        try {
            val returnedReqs = db.collection("requests")
                .whereEqualTo("borrowerId", currentUserId)
                .whereEqualTo("status", "returned")
                .get().await()

            val entries = returnedReqs.documents.mapNotNull { reqDoc ->
                val bookId = reqDoc.getString("bookId") ?: return@mapNotNull null
                val bookSnap = db.collection("books").document(bookId).get().await()
                val bookObj = bookSnap.toObject(Book::class.java)?.copy(id = bookSnap.id)
                val reviewed = reqDoc.getBoolean("borrowerReviewed") ?: false
                if (bookObj != null) BorrowedEntry(bookObj, reqDoc.id, reviewed) else null
            }
            borrowHistory = entries
        } catch (e: Exception) {
            borrowHistory = emptyList()
            snack = "Failed to load history."
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            "Borrowed" -> reloadBorrowed()
            "Borrow Requests" -> reloadBorrowRequests()
            "History" -> reloadBorrowHistory()
        }
    }

    // ----------------- UI -----------------

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
                    // Accepted Borrowed Books (ongoing)
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

                                            val requestRef =
                                                db.collection("requests").document(entry.requestId)
                                            var exchangeStatus by remember { mutableStateOf("") }
                                            var returnStatus by remember { mutableStateOf("") }

                                            // Load statuses
                                            LaunchedEffect(entry.requestId) {
                                                try {
                                                    val snap = requestRef.get().await()
                                                    exchangeStatus =
                                                        snap.getString("exchangeStatus") ?: ""
                                                    returnStatus =
                                                        snap.getString("returnStatus") ?: ""
                                                } catch (_: Exception) {
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                // Exchange handshake (keep as before)
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
                                                // Return – ALWAYS mark returned + free the book
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

                                                                    // reload history + open review
                                                                    reloadBorrowHistory()
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

                    // History: previously returned books
                    "History" -> {
                        if (borrowHistory.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No history yet.")
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(borrowHistory, key = { it.requestId }) { entry ->
                                    val book = entry.book
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                                            Text("Owner: ${book.ownerId}", style = MaterialTheme.typography.bodySmall)
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                if (entry.hasReviewed) "You have reviewed this exchange."
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
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // --------------- Review Dialog ---------------

    if (showReviewDialog && reviewTarget != null && currentUserId != null) {
        val entry = reviewTarget!!
        ReviewDialog(
            bookTitle = entry.book.title,
            counterpartLabel = "Owner",
            showBookRating = true,   // borrower can rate the book
            onSubmit = { bookRating: Int?, userRating: Int, comment: String ->
                scope.launch {
                    try {
                        val now = System.currentTimeMillis()

                        // Book review (only if not null)
                        bookRating?.let { ratingValue ->
                            db.collection("bookReviews").add(
                                mapOf(
                                    "bookId" to entry.book.id,
                                    "requestId" to entry.requestId,
                                    "reviewerId" to currentUserId,
                                    "rating" to ratingValue,
                                    "comment" to comment,
                                    "timestamp" to now
                                )
                            ).await()
                        }

                        // User review (rate owner)
                        db.collection("userReviews").add(
                            mapOf(
                                "targetUserId" to entry.book.ownerId,
                                "requestId" to entry.requestId,
                                "reviewerId" to currentUserId,
                                "rating" to userRating,
                                "comment" to comment,
                                "timestamp" to now
                            )
                        ).await()

                        // mark as reviewed
                        db.collection("requests").document(entry.requestId)
                            .update("borrowerReviewed", true)
                            .await()

                        // update local state
                        borrowHistory = borrowHistory.map {
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
