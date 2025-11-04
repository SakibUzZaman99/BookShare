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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@Composable
fun BookScreen(navController: NavController, bookId: String) {
    var book by remember { mutableStateOf<Book?>(null) }
    val db = FirebaseFirestore.getInstance()

    // 🔹 Load book details from Firestore
    LaunchedEffect(bookId) {
        val snapshot = db.collection("books").document(bookId).get().await()
        book = snapshot.toObject(Book::class.java)
    }

    Scaffold { padding ->
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
                // 🔹 Title
                Text(
                    "Book Details",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                // 🔹 Book Image
                Image(
                    painter = painterResource(id = R.drawable.booklogo_profile),
                    contentDescription = "Book Cover",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(MaterialTheme.shapes.medium)
                )

                Spacer(Modifier.height(20.dp))

                // 🔹 Book Info Section
                Text(
                    text = book!!.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "by ${book!!.author}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))

                book!!.genres.takeIf { it.isNotEmpty() }?.let {
                    DetailItem(label = "Genres", value = it.joinToString(", "))
                }
                book!!.publisher?.let { DetailItem(label = "Publisher", value = it) }
                book!!.year?.let { DetailItem(label = "Year", value = it) }
                book!!.isbn?.let { DetailItem(label = "ISBN", value = it) }
                book!!.city?.let { DetailItem(label = "City", value = it) }
                book!!.area?.let { DetailItem(label = "Area", value = it) }

                Spacer(Modifier.height(32.dp))

                // 🔹 Request Button
                Button(
                    onClick = {
                        // TODO: Add Firestore request logic
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                ) {
                    Text("Request to Lend", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(32.dp))

                // 🔹 Back button (like other screens navigation style)
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
            text = "$label:",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
