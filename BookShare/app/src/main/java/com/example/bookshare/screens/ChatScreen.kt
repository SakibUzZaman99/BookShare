package com.example.bookshare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.bookshare.ui.components.BookShareTopBar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ChatMessage(
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0L
)

@Composable
fun ChatScreen(navController: NavController, requestId: String) {
    if (requestId.isBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Invalid chat session.")
        }
        return
    }

    val db = FirebaseFirestore.getInstance()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()

    // Real-time listener
    LaunchedEffect(requestId) {
        val chatRef = db.collection("requests").document(requestId).collection("messages")
        chatRef.orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    messages.clear()
                    for (doc in snapshot.documents) {
                        val msg = doc.toObject(ChatMessage::class.java)
                        if (msg != null) messages.add(msg)
                    }
                }
            }
    }

    Scaffold(
        topBar = { BookShareTopBar(navController, title = "Chat") },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                    decorationBox = { inner ->
                        if (messageText.text.isEmpty()) {
                            Text("Type a message...", color = Color.Gray, fontSize = 16.sp)
                        }
                        inner()
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val text = messageText.text.trim()
                        if (text.isNotEmpty() && currentUser != null) {
                            scope.launch {
                                val msg = ChatMessage(
                                    senderId = currentUser.uid,
                                    text = text,
                                    timestamp = System.currentTimeMillis()
                                )
                                db.collection("requests").document(requestId)
                                    .collection("messages")
                                    .add(msg).await()
                                messageText = TextFieldValue("")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.primary)
                ) {
                    Text("Send")
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages) { msg ->
                    val isMe = msg.senderId == currentUser?.uid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isMe) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                msg.text,
                                color = if (isMe) Color.White else Color.Black,
                                modifier = Modifier.padding(10.dp),
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
