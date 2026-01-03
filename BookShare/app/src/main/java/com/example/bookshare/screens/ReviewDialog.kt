package com.example.bookshare.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun ReviewDialog(
    bookTitle: String,
    counterpartLabel: String,      // "Owner", "Borrower", etc.
    showBookRating: Boolean,       // true = show book slider (borrower), false = only user rating
    onSubmit: (bookRating: Int?, userRating: Int, comment: String) -> Unit,
    onSkip: () -> Unit
) {
    var bookRating by remember { mutableStateOf(8f) }  // default 8/10
    var userRating by remember { mutableStateOf(8f) }
    var comment by remember { mutableStateOf(TextFieldValue("")) }

    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Review your experience") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Book: $bookTitle")

                if (showBookRating) {
                    Text("Rate the book (0–10): ${bookRating.toInt()}")
                    Slider(
                        value = bookRating,
                        onValueChange = { bookRating = it },
                        valueRange = 0f..10f,
                        steps = 9    // 0..10 inclusive -> 11 values
                    )
                }

                Text("Rate the $counterpartLabel (0–10): ${userRating.toInt()}")
                Slider(
                    value = userRating,
                    onValueChange = { userRating = it },
                    valueRange = 0f..10f,
                    steps = 9
                )

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Optional comment") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val bookValue = if (showBookRating) bookRating.toInt() else null
                onSubmit(bookValue, userRating.toInt(), comment.text.trim())
            }) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Skip")
            }
        }
    )
}
