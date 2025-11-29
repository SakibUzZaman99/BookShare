package com.example.bookshare.ocr

data class ParsedBook(
    val title: String? = null,
    val author: String? = null,
    val isbn: String? = null,
    val year: String? = null,
    val publisher: String? = null
)

object BookParser {
    private val isbnRegex  = Regex("""\b(?:ISBN(?:-1[03])?:?\s*)?((?:97[89][- ]?)?\d{1,5}[- ]?\d+[- ]?\d+[- ]?[\dX])\b""", RegexOption.IGNORE_CASE)
    private val yearRegex  = Regex("""\b(19|20)\d{2}\b""")
    private val byRegex    = Regex("""\bby\s+([A-Za-z][A-Za-z .,'-]+)\b""", RegexOption.IGNORE_CASE)
    private val pubHints   = listOf("press", "publisher", "publications", "publishing", "house")

    fun parse(raw: String): ParsedBook {
        val lines = cleanLines(raw)
        val title = lines.firstOrNull()?.takeIf { it.length in 3..120 }
        val author = byRegex.find(raw)?.groupValues?.get(1)?.trim()
        val isbn = firstIsbn(raw)?.let(::normalizeIsbn)
        val year = yearRegex.find(raw)?.value
        val publisher = lines.firstOrNull { l -> pubHints.any { h -> l.contains(h, true) } }
        return ParsedBook(title, author, isbn, year, publisher)
    }

    fun suggestTitles(raw: String): List<String> =
        cleanLines(raw).filter { it.length in 3..120 }.distinct().take(10)

    fun suggestAuthors(raw: String): List<String> {
        val out = mutableSetOf<String>()
        byRegex.findAll(raw).forEach { out.add(it.groupValues[1].trim()) }
        cleanLines(raw).forEach { l ->
            val w = l.trim()
            if (w.split(" ").size in 1..4 && w.any { it.isUpperCase() }) out.add(w)
        }
        return out.toList().take(10)
    }

    fun suggestIsbns(raw: String): List<String> =
        isbnRegex.findAll(raw).map { normalizeIsbn(it.groupValues[1]) }.distinct().take(10).toList()

    fun suggestYears(raw: String): List<String> =
        yearRegex.findAll(raw).map { it.value }.distinct().take(10).toList()
    fun suggestPublishers(raw: String): List<String> =
        cleanLines(raw).filter { l -> pubHints.any { h -> l.contains(h, true) } }.distinct().take(10)

    fun isbnFromBarcodeCandidate(raw: String): String? {
        val digits = raw.filter { it.isDigit() || it == 'X' || it == 'x' }
        return when {
            digits.length == 13 && (digits.startsWith("978") || digits.startsWith("979")) -> digits
            digits.length == 10 -> digits
            else -> null
        }
    }

    private fun firstIsbn(raw: String): String? = isbnRegex.find(raw)?.groupValues?.get(1)
    private fun normalizeIsbn(s: String): String = s.uppercase().replace(" ", "").replace("-", "")
    private fun cleanLines(raw: String): List<String> =
        raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
}
