package com.example.bookshare.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookshare.ui.components.BookShareTopBar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ContentReview(
    val id: String = "",
    val reviewerId: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val timestamp: Long = 0L
)

data class ReviewableBook(
    val bookKey: String,
    val title: String,
    val author: String
)

/**
 * ExploreScreen:
 * - Shows all unique book titles from `books` collection (lazy / realtime).
 * - Search box filters books by title / author.
 * - Tapping a book opens a dialog to rate the BOOK CONTENT.
 * - Content reviews live in `contentReviews`, keyed by bookKey (lowercased title).
 */
@Composable
fun ExploreScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()

    var books by remember { mutableStateOf<List<ReviewableBook>>(emptyList()) }
    var listLoading by remember { mutableStateOf(true) }
    var listError by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }

    // Dialog + review state
    var selectedBook by remember { mutableStateOf<ReviewableBook?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    var normalizedKey by remember { mutableStateOf<String?>(null) }
    var reviews by remember { mutableStateOf<List<ContentReview>>(emptyList()) }
    var avgRating by remember { mutableStateOf<Double?>(null) }

    var myReviewId by remember { mutableStateOf<String?>(null) }
    var myRating by remember { mutableStateOf(8f) }
    var myComment by remember { mutableStateOf("") }

    var dialogLoading by remember { mutableStateOf(false) }
    var snack by remember { mutableStateOf<String?>(null) }

    // ---- Listen for all books and build unique title list ----
    DisposableEffect(Unit) {
        val registration: ListenerRegistration = db.collection("books")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    listError = "Failed to load books."
                    listLoading = false
                    return@addSnapshotListener
                }

                val map = mutableMapOf<String, ReviewableBook>()
                for (doc in snapshot?.documents ?: emptyList()) {
                    val title = (doc.getString("title") ?: "").trim()
                    if (title.isBlank()) continue
                    val key = title.lowercase()
                    if (!map.containsKey(key)) {
                        val author = doc.getString("author") ?: ""
                        map[key] = ReviewableBook(
                            bookKey = key,
                            title = title,
                            author = author
                        )
                    }
                }
                books = map.values.sortedBy { it.title }
                listLoading = false
                listError = null
            }

        onDispose {
            registration.remove()
        }
    }

    val filteredBooks = books.filter {
        val s = search.trim()
        if (s.isEmpty()) true
        else {
            val q = s.lowercase()
            it.title.lowercase().contains(q) ||
                    it.author.lowercase().contains(q)
        }
    }

    fun resetDialogState() {
        reviews = emptyList()
        avgRating = null
        myReviewId = null
        myRating = 8f
        myComment = ""
        normalizedKey = null
        snack = null
    }

    fun loadReviewsForBook(book: ReviewableBook) {
        val key = book.bookKey
        normalizedKey = key

        scope.launch {
            dialogLoading = true
            try {
                val snap = db.collection("contentReviews")
                    .whereEqualTo("bookKey", key)
                    .get()
                    .await()

                val list = snap.documents.map { doc ->
                    ContentReview(
                        id = doc.id,
                        reviewerId = doc.getString("reviewerId") ?: "",
                        rating = (doc.getLong("rating") ?: 0L).toInt(),
                        comment = doc.getString("comment") ?: "",
                        timestamp = doc.getLong("timestamp") ?: 0L
                    )
                }

                reviews = list
                avgRating = if (list.isNotEmpty()) list.map { it.rating }.average() else null

                val mine = list.firstOrNull { it.reviewerId == currentUserId }
                if (mine != null) {
                    myReviewId = mine.id
                    myRating = mine.rating.toFloat()
                    myComment = mine.comment
                } else {
                    myReviewId = null
                    myRating = 8f
                    myComment = ""
                }

                snack = if (list.isEmpty()) "No content reviews yet for this book." else null
            } catch (e: Exception) {
                snack = "Failed to load reviews."
            } finally {
                dialogLoading = false
            }
        }
    }

    fun submitMyReview() {
        val key = normalizedKey
        val book = selectedBook
        val userId = currentUserId
        if (key == null || book == null) {
            snack = "Select a book first."
            return
        }
        if (userId == null) {
            snack = "You must be logged in to review."
            return
        }

        scope.launch {
            dialogLoading = true
            try {
                val now = System.currentTimeMillis()
                val data = mapOf(
                    "bookKey" to key,
                    "bookTitle" to book.title,
                    "reviewerId" to userId,
                    "rating" to myRating.toInt(),
                    "comment" to myComment.trim(),
                    "timestamp" to now
                )

                val col = db.collection("contentReviews")
                if (myReviewId == null) {
                    val docRef = col.add(data).await()
                    myReviewId = docRef.id
                } else {
                    col.document(myReviewId!!).set(data).await()
                }

                snack = "Review saved."
                loadReviewsForBook(book)
            } catch (e: Exception) {
                snack = "Failed to save review."
                dialogLoading = false
            }
        }
    }

    Scaffold(
        topBar = { BookShareTopBar(navController, title = "Explore Books") }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                "Explore Book Content Reviews",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Search and tap any book to see and rate its content.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search by title or author") },
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            if (listLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (listError != null) {
                Text(
                    listError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (filteredBooks.isEmpty()) {
                Text("No books matched your search.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredBooks) { book ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedBook = book
                                    resetDialogState()
                                    showDialog = true
                                    loadReviewsForBook(book)
                                },
                            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    book.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                if (book.author.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Author: ${book.author}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Tap to rate the book's content →",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            snack?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // ---- Pop-out review dialog ----
    if (showDialog && selectedBook != null) {
        AlertDialog(
            onDismissRequest = {
                showDialog = false
                resetDialogState()
            },
            title = {
                Column {
                    Text("Review: ${selectedBook!!.title}")
                    if (selectedBook!!.author.isNotBlank()) {
                        Text(
                            "Author: ${selectedBook!!.author}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            text = {
                Column {
                    if (dialogLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }

                    avgRating?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Average rating: ${"%.1f".format(it)} / 10 (${reviews.size} review${if (reviews.size == 1) "" else "s"})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (currentUserId == null) {
                        Text(
                            "You must be logged in to submit a review.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            "Your rating: ${myRating.toInt()} / 10",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = myRating,
                            onValueChange = { myRating = it },
                            valueRange = 0f..10f,
                            steps = 9
                        )

                        OutlinedTextField(
                            value = myComment,
                            onValueChange = { myComment = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Comment (optional)") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "All reviews",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(4.dp))

                    if (reviews.isEmpty()) {
                        Text(
                            "No reviews yet. Be the first one!",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                        ) {
                            items(reviews) { r ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(
                                            "Rating: ${r.rating} / 10",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (r.comment.isNotBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(r.comment, style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (r.reviewerId == currentUserId) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                "(Your review)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (currentUserId != null) {
                    TextButton(onClick = { submitMyReview() }) {
                        Text(if (myReviewId == null) "Submit" else "Update")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        resetDialogState()
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}
