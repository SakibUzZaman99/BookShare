package com.example.bookshare.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookshare.R
import com.example.bookshare.ui.components.BookShareTopBar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Book details + "Request to Lend" screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(
    navController: NavController,
    bookId: String
) {
    var book by remember { mutableStateOf<Book?>(null) }
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    // Load book details once for this bookId
    LaunchedEffect(bookId) {
        try {
            val snapshot = db.collection("books").document(bookId).get().await()
            book = snapshot.toObject(Book::class.java)?.copy(id = snapshot.id)
        } catch (e: Exception) {
            message = "Failed to load book."
        }
    }

    Scaffold(
        topBar = { BookShareTopBar(navController, title = "Book Details") }
    ) { padding ->
        if (book == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    "Book Details",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                // Static placeholder cover
                Image(
                    painter = painterResource(id = R.drawable.booklogo_profile),
                    contentDescription = "Book Cover",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(MaterialTheme.shapes.medium)
                )

                Spacer(Modifier.height(20.dp))

                // Main book info
                Text(
                    book!!.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "by ${book!!.author}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                // Details
                book!!.genres.takeIf { it.isNotEmpty() }?.let {
                    DetailItem("Genres", it.joinToString(", "))
                }
                book!!.publisher?.let { DetailItem("Publisher", it) }
                book!!.year?.let { DetailItem("Year", it) }
                book!!.isbn?.let { DetailItem("ISBN", it) }
                book!!.city?.let { DetailItem("City", it) }
                book!!.area?.let { DetailItem("Area", it) }

                Spacer(Modifier.height(32.dp))

                // Request button (only if not your own book)
                Button(
                    onClick = {
                        if (currentUserId != null && book!!.ownerId != currentUserId) {
                            sending = true
                            val request = hashMapOf(
                                "bookId" to bookId,
                                "ownerId" to book!!.ownerId,
                                "borrowerId" to currentUserId,
                                "status" to "pending",
                                "timestamp" to System.currentTimeMillis()
                            )
                            db.collection("requests").add(request)
                                .addOnSuccessListener {
                                    message = "Request sent successfully!"
                                    sending = false
                                }
                                .addOnFailureListener {
                                    message = "Failed to send request."
                                    sending = false
                                }
                        } else {
                            message = "You cannot request your own book."
                        }
                    },
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (sending) "Sending..." else "Request to Lend")
                }

                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.secondary)
                }

                Spacer(Modifier.height(32.dp))

                TextButton(onClick = { navController.popBackStack() }) {
                    Text("Back", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            "$label:",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
