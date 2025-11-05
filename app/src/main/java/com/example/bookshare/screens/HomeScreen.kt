package com.example.bookshare.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.bookshare.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.min

// ✅ Reuse Book and BookStatus from MyLibraryScreen
import com.example.bookshare.screens.Book
import com.example.bookshare.screens.BookStatus

// 🔹 ViewModel for HomeScreen — now with real Borrowed and Lent lists
class HomeViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val booksCollection = db.collection("books")
    private val requestsCollection = db.collection("requests")
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    var lendingBooks by mutableStateOf<List<Book>>(emptyList())
        private set
    var borrowedBooks by mutableStateOf<List<Book>>(emptyList())
        private set
    var lentBooks by mutableStateOf<List<Book>>(emptyList())
        private set

    private var listener: ListenerRegistration? = null

    init {
        startListeningForBooks()
        loadUserRelatedBooks()
    }

    private fun startListeningForBooks() {
        listener = booksCollection
            .whereEqualTo("status", BookStatus.LENDING.name)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                lendingBooks = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Book::class.java)?.copy(id = doc.id)
                } ?: emptyList()
            }
    }

    // 🔹 Loads Borrowed and Lent book titles for current user
    fun loadUserRelatedBooks() {
        viewModelScope.launch {
            if (currentUserId == null) return@launch

            try {
                // Borrowed
                val borrowedRequests = requestsCollection
                    .whereEqualTo("borrowerId", currentUserId)
                    .whereEqualTo("status", "accepted")
                    .get()
                    .await()

                borrowedBooks = borrowedRequests.documents.mapNotNull { doc ->
                    val bookId = doc.getString("bookId") ?: return@mapNotNull null
                    val bookSnap = booksCollection.document(bookId).get().await()
                    bookSnap.toObject(Book::class.java)?.copy(id = bookId)
                }

                // Lent
                val lentRequests = requestsCollection
                    .whereEqualTo("ownerId", currentUserId)
                    .whereEqualTo("status", "accepted")
                    .get()
                    .await()

                lentBooks = lentRequests.documents.mapNotNull { doc ->
                    val bookId = doc.getString("bookId") ?: return@mapNotNull null
                    val bookSnap = booksCollection.document(bookId).get().await()
                    bookSnap.toObject(Book::class.java)?.copy(id = bookId)
                }
            } catch (e: Exception) {
                borrowedBooks = emptyList()
                lentBooks = emptyList()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadUserRelatedBooks()
            val snapshot = booksCollection
                .whereEqualTo("status", BookStatus.LENDING.name)
                .get()
                .await()
            lendingBooks = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Book::class.java)?.copy(id = doc.id)
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    var searchQuery by remember { mutableStateOf("") }

    val lendingBooks = viewModel.lendingBooks
    val borrowedBooks = viewModel.borrowedBooks
    val lentBooks = viewModel.lentBooks

    val filteredBooks = lendingBooks.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
                it.author.contains(searchQuery, ignoreCase = true)
    }

    val listState = rememberLazyListState()
    var showExtras by remember { mutableStateOf(true) }
    var lastVisibleItemIndex by remember { mutableStateOf(0) }
    var lastScrollOffset by remember { mutableStateOf(0) }

    // Pull-to-refresh
    var refreshing by remember { mutableStateOf(false) }
    val refreshState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = {
            refreshing = true
            viewModel.refresh()
            refreshing = false
        }
    )

    // Scroll animation logic
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val currentIndex = listState.firstVisibleItemIndex
        val currentOffset = listState.firstVisibleItemScrollOffset
        val scrollThreshold = 50
        if (currentIndex == 0 && currentOffset == 0) showExtras = true
        else if (currentIndex > lastVisibleItemIndex && listState.isScrollInProgress) showExtras = false
        else if (currentIndex < lastVisibleItemIndex) showExtras = true
        else if (currentIndex == lastVisibleItemIndex) {
            val delta = lastScrollOffset - currentOffset
            if (delta < -scrollThreshold && listState.isScrollInProgress) showExtras = false
            else if (delta > scrollThreshold) showExtras = true
        }
        lastVisibleItemIndex = currentIndex
        lastScrollOffset = currentOffset
    }

    val logoSize by animateDpAsState(if (showExtras) 85.dp else 55.dp)
    val profileSize by animateDpAsState(if (showExtras) 65.dp else 45.dp)
    val titleSize by animateFloatAsState(if (showExtras) 34f else 26f)
    val titlePadding by animateDpAsState(if (showExtras) 12.dp else 4.dp)

    Scaffold { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                stickyHeader {
                    Surface(
                        tonalElevation = 3.dp,
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = titlePadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.booksharelogo),
                                        contentDescription = "BookShare Logo",
                                        modifier = Modifier
                                            .size(logoSize)
                                            .clip(RoundedCornerShape(20.dp))
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Text(
                                        "BookShare",
                                        fontSize = titleSize.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    Image(
                                        painter = painterResource(id = R.drawable.default_profile),
                                        contentDescription = "Profile",
                                        modifier = Modifier
                                            .size(profileSize)
                                            .clip(CircleShape)
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                ),
                                                CircleShape
                                            )
                                            .clickable { expanded = !expanded }
                                    )
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                        modifier = Modifier.width(180.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("👤 My Profile") },
                                            onClick = {
                                                expanded = false
                                                navController.navigate("profile")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("📚 My Library") },
                                            onClick = {
                                                expanded = false
                                                navController.navigate("library")
                                            }
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = showExtras,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 3 })
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        SectionCard(
                                            title = "Borrowed",
                                            books = borrowedBooks.take(3).map { it.title },
                                            onClick = { navController.navigate("borrowed") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        SectionCard(
                                            title = "Lent",
                                            books = lentBooks.take(3).map { it.title },
                                            onClick = { navController.navigate("lent") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        placeholder = { Text("Search for books...") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }

                // Book List (all lending books)
                items(filteredBooks) { book ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .height(150.dp)
                            .clickable { navController.navigate("book/${book.id}") },
                        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.booklogo_profile),
                                contentDescription = "Book Cover",
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(12.dp)
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    book.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "Author: ${book.author}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }

            PullRefreshIndicator(refreshing, refreshState, Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    books: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.height(8.dp))
            books.take(3).forEach { book ->
                Text("• $book", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "View all →",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
