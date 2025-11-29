package com.example.bookshare.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.bookshare.ocr.BookParser
import com.example.bookshare.ocr.CameraRoiOcrDialog
import com.example.bookshare.ocr.OcrUtils
import com.example.bookshare.ui.components.BookShareTopBar
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// -------------------------------------------------------
// Data + ViewModel
// -------------------------------------------------------

enum class BookStatus { HIDDEN, LENDING }

data class Book(
    val id: String = "",
    val ownerId: String = "",
    val title: String = "",
    val author: String = "",
    val genres: List<String> = emptyList(),
    val publisher: String? = null,
    val year: String? = null,
    val isbn: String? = null,
    val city: String? = null,
    val area: String? = null,
    val status: String = BookStatus.HIDDEN.name
)

class MyLibraryViewModel : ViewModel() {
    private val db = Firebase.firestore
    private val booksCollection = db.collection("books")
    private val currentUserId: String? = FirebaseAuth.getInstance().currentUser?.uid

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    init {
        fetchBooks()
    }

    fun fetchBooks() {
        if (currentUserId == null) {
            Log.w("MyLibraryViewModel", "No user logged in, cannot fetch books.")
            _books.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val snapshot = booksCollection
                    .whereEqualTo("ownerId", currentUserId)
                    .get()
                    .await()
                _books.value = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Book::class.java)?.copy(id = doc.id)
                }
            } catch (e: Exception) {
                Log.e("MyLibraryViewModel", "Error fetching books", e)
                _books.value = emptyList()
            }
        }
    }

    fun addBook(book: Book) {
        if (currentUserId == null) {
            Log.e("MyLibraryViewModel", "Cannot add book, no user logged in.")
            return
        }
        viewModelScope.launch {
            try {
                booksCollection.add(book.copy(ownerId = currentUserId)).await()
                fetchBooks()
            } catch (e: Exception) {
                Log.e("MyLibraryViewModel", "Error adding book", e)
            }
        }
    }

    fun updateBookStatus(bookId: String, newStatus: BookStatus) {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                booksCollection.document(bookId).update("status", newStatus.name).await()
                fetchBooks()
            } catch (e: Exception) {
                Log.e("MyLibraryViewModel", "Error updating book status", e)
            }
        }
    }

    fun deleteBook(bookId: String) {
        if (currentUserId == null) return
        viewModelScope.launch {
            try {
                booksCollection.document(bookId).delete().await()
                fetchBooks()
            } catch (e: Exception) {
                Log.e("MyLibraryViewModel", "Error deleting book", e)
            }
        }
    }
}

// -------------------------------------------------------
// Screen + Book cards
// -------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyLibraryScreen(
    navController: NavController,
    viewModel: MyLibraryViewModel = viewModel()
) {
    val books by viewModel.books.collectAsState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { BookShareTopBar(navController, title = "My Library") },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
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
            if (books.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Your library is empty. Tap '+' to add a book.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(books, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            viewModel = viewModel,
                            navController = navController
                        )
                    }
                }
            }
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            AddBookSheet(
                onAddBook = { newBook ->
                    viewModel.addBook(newBook)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showBottomSheet = false
                    }
                },
                onDismiss = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) showBottomSheet = false
                    }
                }
            )
        }
    }
}

@Composable
fun BookCard(
    book: Book,
    viewModel: MyLibraryViewModel,
    navController: NavController
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (book.id.isNotBlank()) {
                    navController.navigate("book/${book.id}")
                }
            },
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
                    color = if (book.status == BookStatus.LENDING.name)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (book.genres.isNotEmpty()) {
                    Text(
                        "Genres: ${book.genres.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options"
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (book.status == BookStatus.HIDDEN.name) {
                        DropdownMenuItem(
                            text = { Text("Set to Lending") },
                            onClick = {
                                viewModel.updateBookStatus(book.id, BookStatus.LENDING)
                                showMenu = false
                            }
                        )
                    }
                    if (book.status == BookStatus.LENDING.name) {
                        DropdownMenuItem(
                            text = { Text("Set to Hidden") },
                            onClick = {
                                viewModel.updateBookStatus(book.id, BookStatus.HIDDEN)
                                showMenu = false
                            }
                        )
                    }
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
    }
}

// -------------------------------------------------------
// Add Book sheet
// -------------------------------------------------------

@Composable
fun AddBookSheet(
    onAddBook: (Book) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var genres by remember { mutableStateOf("") }
    var publisher by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var isbn by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }

    var titleError by remember { mutableStateOf(false) }
    var authorError by remember { mutableStateOf(false) }
    var genresError by remember { mutableStateOf(false) }

    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    var ocrText by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }

    var showCamTitle by remember { mutableStateOf(false) }
    var showCamAuthor by remember { mutableStateOf(false) }
    var showCamPublisher by remember { mutableStateOf(false) }
    var showCamYear by remember { mutableStateOf(false) }
    var showCamIsbn by remember { mutableStateOf(false) }
    var showCamGenres by remember { mutableStateOf(false) }
    var showCamCity by remember { mutableStateOf(false) }
    var showCamArea by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        pickedImageUri = uri
        ocrText = ""
        info = null
    }

    fun validate(): Boolean {
        titleError = title.isBlank()
        authorError = author.isBlank()
        genresError = genres.isBlank()
        return !titleError && !authorError && !genresError
    }

    fun genericSuggestions(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(20)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Add a New Book", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                Text("Choose Image")
            }
            Button(
                enabled = pickedImageUri != null && !isBusy,
                onClick = {
                    if (pickedImageUri == null) return@Button
                    scope.launch {
                        isBusy = true
                        try {
                            val txt = OcrUtils.mlkitText(context, pickedImageUri!!)
                            ocrText = txt          // just store OCR
                            info = "On-device OCR complete."
                        } finally {
                            isBusy = false
                        }
                    }
                }
            ) {
                Text(if (isBusy) "Scanning..." else "Scan Text (ML Kit)")
            }
        }

        if (ocrText.isNotBlank()) {
            Text("Extracted text:", style = MaterialTheme.typography.labelLarge)
            Text(
                ocrText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        info?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // fullOcrText passed to each field so menu can offer "Insert all extracted text"
        val fullOcr = ocrText.takeIf { it.isNotBlank() }

        FieldWithSuggestions(
            label = "Title*",
            value = title,
            onChange = { title = it; titleError = false },
            isError = titleError,
            suggestions = if (ocrText.isBlank()) emptyList() else BookParser.suggestTitles(ocrText),
            onCameraClick = { showCamTitle = true },
            fullOcrText = fullOcr
        )
        if (showCamTitle) {
            CameraRoiOcrDialog(
                title = "Capture Title",
                onDismiss = { showCamTitle = false },
                onTextExtracted = { text ->
                    val first = text.lines().firstOrNull()?.trim().orEmpty()
                    if (first.isNotEmpty()) title = first
                }
            )
        }

        FieldWithSuggestions(
            label = "Author*",
            value = author,
            onChange = { author = it; authorError = false },
            isError = authorError,
            suggestions = if (ocrText.isBlank()) emptyList() else BookParser.suggestAuthors(ocrText),
            onCameraClick = { showCamAuthor = true },
            fullOcrText = fullOcr
        )
        if (showCamAuthor) {
            CameraRoiOcrDialog(
                title = "Capture Author",
                onDismiss = { showCamAuthor = false },
                onTextExtracted = { text ->
                    val first = text.lines().firstOrNull()?.trim().orEmpty()
                        .removePrefix("by ").removePrefix("By ").trim()
                    if (first.isNotEmpty()) author = first
                }
            )
        }

        FieldWithSuggestions(
            label = "Genre(s)*",
            value = genres,
            onChange = { genres = it; genresError = false },
            isError = genresError,
            suggestions = genericSuggestions(ocrText),
            onCameraClick = { showCamGenres = true },
            fullOcrText = fullOcr
        )
        if (showCamGenres) {
            CameraRoiOcrDialog(
                title = "Capture Genres",
                onDismiss = { showCamGenres = false },
                onTextExtracted = { text ->
                    val line = text.lines().firstOrNull()?.trim().orEmpty()
                    if (line.isNotEmpty()) {
                        genres = if (genres.isBlank()) line else "$genres, $line"
                    }
                }
            )
        }

        FieldWithSuggestions(
            label = "Publisher",
            value = publisher,
            onChange = { publisher = it },
            isError = false,
            suggestions = if (ocrText.isBlank()) emptyList() else BookParser.suggestPublishers(ocrText),
            onCameraClick = { showCamPublisher = true },
            fullOcrText = fullOcr
        )
        if (showCamPublisher) {
            CameraRoiOcrDialog(
                title = "Capture Publisher",
                onDismiss = { showCamPublisher = false },
                onTextExtracted = { text ->
                    val best = text.lines()
                        .firstOrNull {
                            it.contains("press", true) || it.contains("publish", true)
                        }
                        ?: text.lines().firstOrNull()
                    best?.trim()?.let { if (it.isNotEmpty()) publisher = it }
                }
            )
        }

        FieldWithSuggestions(
            label = "Year Published",
            value = year,
            onChange = { year = it },
            isError = false,
            suggestions = if (ocrText.isBlank()) emptyList() else BookParser.suggestYears(ocrText),
            onCameraClick = { showCamYear = true },
            fullOcrText = fullOcr
        )
        if (showCamYear) {
            CameraRoiOcrDialog(
                title = "Capture Year",
                onDismiss = { showCamYear = false },
                onTextExtracted = { text ->
                    BookParser.suggestYears(text).firstOrNull()?.let { year = it }
                }
            )
        }

        FieldWithSuggestions(
            label = "ISBN",
            value = isbn,
            onChange = { isbn = it },
            isError = false,
            suggestions = if (ocrText.isBlank()) emptyList() else BookParser.suggestIsbns(ocrText),
            onCameraClick = { showCamIsbn = true },
            fullOcrText = fullOcr
        )
        if (showCamIsbn) {
            CameraRoiOcrDialog(
                title = "Capture ISBN",
                onDismiss = { showCamIsbn = false },
                onTextExtracted = { text ->
                    BookParser.suggestIsbns(text).firstOrNull()?.let { isbn = it }
                }
            )
        }

        FieldWithSuggestions(
            label = "City",
            value = city,
            onChange = { city = it },
            isError = false,
            suggestions = genericSuggestions(ocrText),
            onCameraClick = { showCamCity = true },
            fullOcrText = fullOcr
        )
        if (showCamCity) {
            CameraRoiOcrDialog(
                title = "Capture City",
                onDismiss = { showCamCity = false },
                onTextExtracted = { text ->
                    val first = text.lines().firstOrNull()?.trim().orEmpty()
                    if (first.isNotEmpty()) city = first
                }
            )
        }

        FieldWithSuggestions(
            label = "Area",
            value = area,
            onChange = { area = it },
            isError = false,
            suggestions = genericSuggestions(ocrText),
            onCameraClick = { showCamArea = true },
            fullOcrText = fullOcr
        )
        if (showCamArea) {
            CameraRoiOcrDialog(
                title = "Capture Area",
                onDismiss = { showCamArea = false },
                onTextExtracted = { text ->
                    val first = text.lines().firstOrNull()?.trim().orEmpty()
                    if (first.isNotEmpty()) area = first
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (validate()) {
                    val genreList = genres.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onAddBook(
                        Book(
                            title = title.trim(),
                            author = author.trim(),
                            genres = genreList,
                            publisher = publisher.trim().ifBlank { null },
                            year = year.trim().ifBlank { null },
                            isbn = isbn.trim().ifBlank { null },
                            city = city.trim().ifBlank { null },
                            area = area.trim().ifBlank { null }
                        )
                    )
                }
            }) {
                Text("Add Book")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// -------------------------------------------------------
// Shared field widgets
// -------------------------------------------------------

@Composable
private fun CameraIcon(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text("\uD83D\uDCF7") } // 📷
}

@Composable
private fun FieldWithSuggestions(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    isError: Boolean,
    suggestions: List<String>,
    onCameraClick: (() -> Unit)? = null,
    fullOcrText: String? = null
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                Row {
                    if (onCameraClick != null) {
                        CameraIcon { onCameraClick() }
                    }
                    if (suggestions.isNotEmpty() || !fullOcrText.isNullOrBlank()) {
                        TextButton(onClick = { menuOpen = !menuOpen }) {
                            Text("⋯")
                        }
                    }
                }
            }
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            // First item: insert all extracted text (if available)
            if (!fullOcrText.isNullOrBlank()) {
                DropdownMenuItem(
                    text = { Text("Insert all extracted text") },
                    onClick = {
                        val newVal = if (value.isBlank()) {
                            fullOcrText
                        } else {
                            "$value $fullOcrText"
                        }
                        onChange(newVal)
                        menuOpen = false   // usually you won't need multiple full-text inserts
                    }
                )
                if (suggestions.isNotEmpty()) {
                    Divider()
                }
            }

            // Then individual suggestions (appendable, can pick multiple)
            suggestions.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s) },
                    onClick = {
                        val parts = value
                            .split(" ", ",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        val exists = parts.any { it.equals(s, ignoreCase = true) }

                        val newVal = when {
                            value.isBlank() -> s
                            exists -> value
                            else -> "$value $s"
                        }
                        onChange(newVal)
                        // keep menu open so user can keep adding chunks
                    }
                )
            }
        }
    }
}
