package com.example.bookshare.screens

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.bookshare.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    val user = auth.currentUser ?: return
    val userId = user.uid

    // 🔹 Profile Data
    var fullName by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var employment by remember { mutableStateOf("") }
    var joinedAt by remember { mutableStateOf("N/A") }
    var rating by remember { mutableStateOf("N/A") }
    var totalBorrowed by remember { mutableStateOf("0") }
    var totalLent by remember { mutableStateOf("0") }
    var totalBooks by remember { mutableStateOf("0") }

    var saving by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }

    // 🔹 Load or create profile document
    LaunchedEffect(Unit) {
        val userDoc = db.collection("users").document(userId)
        val snapshot = userDoc.get().await()

        if (snapshot.exists()) {
            fullName = snapshot.getString("fullName") ?: ""
            bio = snapshot.getString("bio") ?: ""
            city = snapshot.getString("city") ?: ""
            employment = snapshot.getString("employment") ?: ""
            joinedAt = snapshot.getString("joinedAt") ?: "N/A"
            // we'll overwrite rating below using reviews
        } else {
            val joinedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(Date(user.metadata?.creationTimestamp ?: System.currentTimeMillis()))
            val defaultData = mapOf(
                "fullName" to "",
                "bio" to "",
                "city" to "",
                "employment" to "",
                "joinedAt" to joinedDate,
                "rating" to 0
            )
            userDoc.set(defaultData).await()
            joinedAt = joinedDate
        }

        // 🔹 Fetch live stats
        fetchUserStats(db, userId,
            onBooks = { totalBooks = it.toString() },
            onBorrowed = { totalBorrowed = it.toString() },
            onLent = { totalLent = it.toString() }
        )

        // 🔹 Compute rating from userReviews (0–10)
        try {
            val reviewsSnap = db.collection("userReviews")
                .whereEqualTo("targetUserId", userId)
                .get()
                .await()

            val ratings = reviewsSnap.documents.mapNotNull { doc ->
                when (val v = doc.get("rating")) {
                    is Long -> v.toInt()
                    is Int -> v
                    is Double -> v.toInt()
                    else -> null
                }
            }

            rating = if (ratings.isNotEmpty()) {
                val avg = ratings.average()
                String.format("%.1f / 10", avg)
            } else {
                "N/A"
            }

            // (optional) also store average in users collection for faster listing
            userDoc.update("rating", rating).addOnFailureListener { /* ignore */ }

        } catch (e: Exception) {
            rating = "N/A"
        }
    }


    // 🔹 Logout setup
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🔹 Header row with edit icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "My Profile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { editMode = !editMode }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 🔹 Profile picture
            Image(
                painter = painterResource(id = R.drawable.default_profile),
                contentDescription = "Profile Picture",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { /* future: upload photo */ }
            )

            Spacer(Modifier.height(20.dp))

            // 🔹 Editable vs View mode
            if (editMode) {
                EditableField("Full Name", fullName) { fullName = it }
                EditableField("Bio", bio, maxLines = 3) { bio = it }
                EditableField("City", city) { city = it }
                EditableField("Employment / Student at", employment) { employment = it }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            db.collection("users").document(userId).update(
                                mapOf(
                                    "fullName" to fullName,
                                    "bio" to bio,
                                    "city" to city,
                                    "employment" to employment
                                )
                            ).addOnSuccessListener {
                                saving = false
                                editMode = false
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saving) "Saving..." else "Save Changes")
                }
            } else {
                InfoDisplay("Full Name", fullName.ifEmpty { "N/A" })
                InfoDisplay("Bio", bio.ifEmpty { "N/A" })
                InfoDisplay("City", city.ifEmpty { "N/A" })
                InfoDisplay("Employment / Student at", employment.ifEmpty { "N/A" })
            }

            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            // 🔹 Dynamic Stats
            InfoRow("Joined At", joinedAt)
            InfoRow("Rating", rating)
            InfoRow("Total Borrowed", totalBorrowed)
            InfoRow("Total Lent", totalLent)
            InfoRow("Total Books in Library", totalBooks)

            Spacer(Modifier.height(32.dp))

            // 🔹 Logout button
            Button(
                onClick = {
                    auth.signOut()
                    googleSignInClient.signOut().addOnCompleteListener(activity) {
                        navController.navigate("login") {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log Out", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

// 🔹 Fetch user statistics dynamically
suspend fun fetchUserStats(
    db: FirebaseFirestore,
    userId: String,
    onBooks: (Int) -> Unit,
    onBorrowed: (Int) -> Unit,
    onLent: (Int) -> Unit
) {
    try {
        // 📚 Total books owned by this user
        val booksCount = db.collection("books")
            .whereEqualTo("ownerId", userId)
            .get().await().size()
        onBooks(booksCount)

        // 📘 Total borrowed (accepted or returned)
        val borrowedSnapshot = db.collection("requests")
            .whereEqualTo("borrowerId", userId)
            .whereIn("status", listOf("accepted", "returned"))
            .get().await()
        onBorrowed(borrowedSnapshot.size())

        // 📗 Total lent (accepted or returned)
        val lentSnapshot = db.collection("requests")
            .whereEqualTo("ownerId", userId)
            .whereIn("status", listOf("accepted", "returned"))
            .get().await()
        onLent(lentSnapshot.size())
    } catch (e: Exception) {
        onBooks(0)
        onBorrowed(0)
        onLent(0)
    }
}


// 🔹 Reusable Components
@Composable
fun EditableField(label: String, value: String, maxLines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = maxLines == 1,
        maxLines = maxLines,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
fun InfoDisplay(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontWeight = FontWeight.Bold)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
