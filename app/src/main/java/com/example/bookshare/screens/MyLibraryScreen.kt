package com.example.bookshare.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObjects
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Defines the possible states for a book.
 */
enum class BookStatus {
    HIDDEN, // Default state, not visible to others
    LENDING // Available for lending/sharing
}

/**
 * Data class to represent a Book.
 * Added 'ownerId' to link the book to a user.
 * Added 'status' to track its sharing state.
 */
data class Book(
    val id: String = "", // Document ID from Firestore
    val ownerId: String = "", // Firebase Auth UID
    val title: String = "",
    val author: String = "",
    val genres: List<String> = emptyList(),
    val publisher: String? = null,
    val year: String? = null,
    val isbn: String? = null,
    val city: String? = null,
    val area: String? = null,
    val status: String = BookStatus.HIDDEN.name // Default status is HIDDEN
)

/**
 * ViewModel to manage the state and Firebase interactions for MyLibraryScreen.
 * It now filters books by the logged-in user's UID and manages status updates/deletions.
 */
class MyLibraryViewModel : ViewModel() {

    private val db = Firebase.firestore
    private val booksCollection = db.collection("books")

    // Get the current user's UID
    private val currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    // Private MutableStateFlow to hold the list of books
    private val _books = MutableStateFlow<List<Book>>(emptyList())
    // Public immutable StateFlow for the Composable to observe
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    init {
        // Load books when the ViewModel is created
        fetchBooks()
    }

    /**
     * Fetches books ONLY for the currently logged-in user.
     */
    fun fetchBooks() {
        // If no user is logged in, don't try to fetch.
        if (currentUserId == null) {
            Log.w("MyLibraryViewModel", "No user logged in, cannot fetch books.")
            _books.value = emptyList() // Ensure list is empty
            return
        }

        viewModelScope.launch {
            try {
                // Add a query to filter by the owner's UID
                val snapshot = booksCollection
                    .whereEqualTo("ownerId", currentUserId) // <-- THE QUERY FILTER
                    .get()
                    .await()

                // Map Firestore documents to Book objects, including the document ID
                _books.value = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Book::class.java)?.copy(id = doc.id)
                }
            } catch (e: Exception) {
                // Handle errors (e.g., show a toast or log)
                Log.e("MyLibraryViewModel", "Error fetching books", e)
                _books.value = emptyList() // Clear list on error
            }
        }
    }

    /**
     * Adds a new book document, stamping it with the current user's UID.
     * The 'status' will default to HIDDEN as defined in the Book data class.
     */
    fun addBook(book: Book) {
        // Don't allow adding a book if user is not logged in
        if (currentUserId == null) {
            Log.e("MyLibraryViewModel", "Cannot add book, no user logged in.")
            return
        }

        viewModelScope.launch {
            try {
                // Create a new book object from the UI one, adding the ownerId
                // The 'status' field will use the default from the 'Book' data class
                val bookWithOwner = book.copy(ownerId = currentUserId)

                // Add the new book with the ownerId.
                booksCollection.add(bookWithOwner).await()

                // Refresh the list (fetchBooks() will automatically filter)
                fetchBooks()
            } catch (e: Exception) {
                Log.e("MyLibraryViewModel", "Error adding book", e)
            }
        }
    }

    /**
     * Updates the 'status' field of a specific book document in Firestore.
     */
    fun updateBookStatus(bookId: String, newStatus: BookStatus) {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                booksCollection.document(bookId)
                    .update("status", newStatus.name)
                    .await()
                // Refresh the list to show the change
                fetchBooks()
            } catch (e: Exception) {
                Log.e("MyLibraryViewModel", "Error updating book status", e)
            }
        }
    }

    /**
     * Deletes a specific book document from Firestore.
     */
    fun deleteBook(bookId: String) {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                booksCollection.document(bookId)
                    .delete()
                    .await()
                // Refresh the list
                fetchBooks()
            } catch (e: Exception) {
                Log.e("MyLibraryViewModel", "Error deleting book", e)
            }
        }
    }
}


/**
 * The main screen for "My Library".
 * It observes the list of books from MyLibraryViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyLibraryScreen(
    navController: NavController,
    viewModel: MyLibraryViewModel = viewModel() // Get the ViewModel instance
) {
    // Collect the list of books as state
    val books by viewModel.books.collectAsState()

    // State for managing the bottom sheet
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true }, // Show the sheet
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Book", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("My Library", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text("Manage and view books you’ve added to share.", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(16.dp))

            // Show a message if the library is empty
            if (books.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Your library is empty. Tap '+' to add a book.")
                }
            } else {
                // Display the list of books
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(books, key = { it.id }) { book ->
                        BookCard(book = book, viewModel = viewModel)
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for adding a new book
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            // Content of the bottom sheet
            AddBookSheet(
                onAddBook = { newBook ->
                    viewModel.addBook(newBook)
                    // Hide the sheet after adding
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                },
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showBottomSheet = false
                        }
                    }
                }
            )
        }
    }
}

/**
 * A simple card to display book information.
 * Now includes a dropdown menu to change status or delete the book.
 */
@Composable
fun BookCard(book: Book, viewModel: MyLibraryViewModel) {
    var showMenu by remember { mutableStateOf(false) }

    // Box is used to anchor the DropdownMenu to the Card
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showMenu = true }, // Click to show menu
            colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium)
                    Text("by ${book.author}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Status: ${book.status.lowercase().capitalize(Locale.current)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (book.status == BookStatus.LENDING.name) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    book.genres.takeIf { it.isNotEmpty() }?.let {
                        Text(
                            "Genres: ${it.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                // Icon to indicate the menu (optional, as the whole card is clickable)
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Dropdown Menu for options
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            // Option 1: Set to LENDING
            if (book.status == BookStatus.HIDDEN.name) {
                DropdownMenuItem(
                    text = { Text("Set to Lending") },
                    onClick = {
                        viewModel.updateBookStatus(book.id, BookStatus.LENDING)
                        showMenu = false
                    }
                )
            }

            // Option 2: Set to HIDDEN
            if (book.status == BookStatus.LENDING.name) {
                DropdownMenuItem(
                    text = { Text("Set to Hidden") },
                    onClick = {
                        viewModel.updateBookStatus(book.id, BookStatus.HIDDEN)
                        showMenu = false
                    }
                )
            }

            // Option 3: Delete
            if (book.status != "LENT") {
                DropdownMenuItem(
                    text = { Text("Delete Book", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        viewModel.deleteBook(book.id)
                        showMenu = false
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Cannot delete while lent", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = { showMenu = false }
                )
            }
        }
    }
}


/**
 * The content for the "Add Book" modal bottom sheet.
 * Contains a form for all the book fields.
 * No changes needed here, as the default 'status' is set in the data class.
 */
@Composable
fun AddBookSheet(
    onAddBook: (Book) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var genres by remember { mutableStateOf("") } // Comma-separated string
    var publisher by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var isbn by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }

    var titleError by remember { mutableStateOf(false) }
    var authorError by remember { mutableStateOf(false) }
    var genresError by remember { mutableStateOf(false) }

    // Function to validate required fields
    fun validate(): Boolean {
        titleError = title.isBlank()
        authorError = author.isBlank()
        genresError = genres.isBlank()
        return !titleError && !authorError && !genresError
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Make the form scrollable
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Add a New Book", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            // --- Required Fields ---
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; titleError = false },
                label = { Text("Title*") },
                isError = titleError,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it; authorError = false },
                label = { Text("Author*") },
                isError = authorError,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = genres,
                onValueChange = { genres = it; genresError = false },
                label = { Text("Genre(s)*") },
                placeholder = { Text("e.g. Sci-Fi, Fantasy, Mystery") },
                isError = genresError,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // --- Optional Fields ---
            OutlinedTextField(
                value = publisher,
                onValueChange = { publisher = it },
                label = { Text("Publisher") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = year,
                onValueChange = { year = it },
                label = { Text("Year Published") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = isbn,
                onValueChange = { isbn = it },
                label = { Text("ISBN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("City") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = area,
                onValueChange = { area = it },
                label = { Text("Area") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            // --- Action Buttons ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (validate()) {
                            // Split genres string by comma and trim whitespace
                            val genreList = genres.split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            val newBook = Book(
                                // 'id', 'ownerId', and 'status' are set
                                // automatically or by default.
                                title = title.trim(),
                                author = author.trim(),
                                genres = genreList,
                                publisher = publisher.trim().takeIf { it.isNotEmpty() },
                                year = year.trim().takeIf { it.isNotEmpty() },
                                isbn = isbn.trim().takeIf { it.isNotEmpty() },
                                city = city.trim().takeIf { it.isNotEmpty() },
                                area = area.trim().takeIf { it.isNotEmpty() }
                            )
                            onAddBook(newBook)
                        }
                    }
                ) {
                    Text("Add Book")
                }
            }
            // Add padding for the navigation bar
            Spacer(Modifier.height(32.dp))
        }
    }
}