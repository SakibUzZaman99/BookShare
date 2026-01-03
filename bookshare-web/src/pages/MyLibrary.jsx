// src/screens/MyLibrary.jsx
import React from 'react'
import Tesseract from 'tesseract.js'
import { auth, db } from '../firebase'
import { onAuthStateChanged } from 'firebase/auth'
import {
  collection,
  query,
  where,
  getDocs,
  addDoc,
  updateDoc,
  deleteDoc,
  doc,
} from 'firebase/firestore'
import { BOOK_STATUS, trimOrNull } from '../helpers'

export default function MyLibrary() {
  const [books, setBooks] = React.useState([])
  const [user, setUser] = React.useState(null)
  const [loading, setLoading] = React.useState(true)
  const [open, setOpen] = React.useState(false)

  // Handle auth state
  React.useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (currentUser) => {
      setUser(currentUser)
      if (currentUser) {
        await loadBooks(currentUser)
      } else {
        setBooks([])
      }
      setLoading(false)
    })
    return () => unsubscribe()
  }, [])

  // Load user’s books (ignore Firestore's stored id)
  async function loadBooks(currentUser) {
    if (!currentUser) return
    const q = query(
      collection(db, 'books'),
      where('ownerId', '==', currentUser.uid)
    )
    const snap = await getDocs(q)

    const list = snap.docs.map((d) => {
      const bookData = d.data() || {}
      delete bookData.id
      return { id: d.id, ...bookData }
    })
    setBooks(list)
  }

  // Add new book (called from modal with structured data)
  async function addBookFromModal(form) {
    if (!user) {
      alert('Please sign in first.')
      return
    }

    const title = form.title?.trim()
    const author = form.author?.trim()
    const genresStr = form.genres?.trim()

    if (!title || !author || !genresStr) {
      alert('Title, Author, and Genres are required.')
      return
    }

    const book = {
      ownerId: user.uid,
      title,
      author,
      genres: genresStr
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
      publisher: trimOrNull(form.publisher),
      year: trimOrNull(form.year),
      isbn: trimOrNull(form.isbn),
      city: trimOrNull(form.city),
      area: trimOrNull(form.area),
      status: BOOK_STATUS.HIDDEN,
      createdAt: new Date().toISOString(),
    }

    await addDoc(collection(db, 'books'), book)
    setOpen(false)
    await loadBooks(user)
  }

  // Update book status safely
  async function setStatus(id, status) {
    if (!id) {
      console.error('❌ Missing book id in setStatus')
      alert('Something went wrong — no book ID found.')
      return
    }
    try {
      await updateDoc(doc(db, 'books', id), { status })
      await loadBooks(user)
    } catch (err) {
      console.error('Error updating status:', err)
    }
  }

  // Delete book
  async function remove(id, status) {
    if (status === BOOK_STATUS.LENT) return alert('Cannot delete while lent.')
    if (!id) return console.error('❌ Missing book id in delete')
    await deleteDoc(doc(db, 'books', id))
    await loadBooks(user)
  }

  if (loading) return <div className="card">Loading your library...</div>

  return (
    <div className="container">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2>My Library</h2>
        <button className="button" onClick={() => setOpen(true)}>
          + Add Book
        </button>
      </div>

      {books.length === 0 ? (
        <div className="card">Your library is empty. Click “+ Add Book”.</div>
      ) : (
        <div className="list">
          {books.map((b) => (
            <div
              key={b.id}
              className="card row"
              style={{ justifyContent: 'space-between' }}
            >
              <div>
                <div style={{ fontWeight: 800 }}>{b.title}</div>
                <div className="small">by {b.author}</div>
                <div className="small">
                  Status: <span className="badge">{b.status}</span>
                </div>
                {b.genres?.length ? (
                  <div className="small">Genres: {b.genres.join(', ')}</div>
                ) : null}
              </div>

              <div className="row">
                {b.status === BOOK_STATUS.HIDDEN && (
                  <button
                    className="button secondary"
                    onClick={() => setStatus(b.id, BOOK_STATUS.LENDING)}
                  >
                    Set to Lending
                  </button>
                )}
                {b.status === BOOK_STATUS.LENDING && (
                  <button
                    className="button secondary"
                    onClick={() => setStatus(b.id, BOOK_STATUS.HIDDEN)}
                  >
                    Set to Hidden
                  </button>
                )}
                <button
                  className="button"
                  onClick={() => remove(b.id, b.status)}
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {open && (
        <AddBookModal
          onClose={() => setOpen(false)}
          onSubmit={addBookFromModal}
        />
      )}
    </div>
  )
}

/**
 * Simple client-side preprocessing for OCR images
 */
async function preprocessImage(file) {
  return new Promise((resolve) => {
    const img = new Image()
    img.onload = () => {
      const canvas = document.createElement('canvas')
      const ctx = canvas.getContext('2d')

      const targetWidth = 1500
      const scale = img.width > targetWidth ? targetWidth / img.width : 1
      canvas.width = img.width * scale
      canvas.height = img.height * scale

      // basic enhancement
      ctx.filter = 'grayscale(100%) contrast(180%) brightness(120%)'
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height)

      canvas.toBlob(
        (blob) => {
          resolve(blob || file)
        },
        file.type || 'image/png',
        0.95
      )
    }
    img.onerror = () => resolve(file)
    img.src = URL.createObjectURL(file)
  })
}

function AddBookModal({ onClose, onSubmit }) {
  const [form, setForm] = React.useState({
    title: '',
    author: '',
    genres: '',
    publisher: '',
    year: '',
    isbn: '',
    city: '',
    area: '',
  })

  // Which field is currently running OCR or API fetch
  const [ocrTargetField, setOcrTargetField] = React.useState(null)
  const [loadingField, setLoadingField] = React.useState(null) // Replaces ocrLoadingField to be generic
  
  const fileInputRef = React.useRef(null)

  function updateField(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  // --- GOOGLE BOOKS API INTEGRATION ---
  async function handleIsbnSearch() {
    const rawIsbn = form.isbn?.trim()
    if (!rawIsbn) {
      alert('Please enter an ISBN first.')
      return
    }

    setLoadingField('isbn')
    try {
      // Remove dashes or spaces for the API query
      const cleanIsbn = rawIsbn.replace(/[\s-]/g, '')
      console.log('Fetching from Google Books for ISBN:', cleanIsbn)
      
      const res = await fetch(`https://www.googleapis.com/books/v1/volumes?q=isbn:${cleanIsbn}`)
      const data = await res.json()

      if (data.totalItems > 0 && data.items && data.items.length > 0) {
        const info = data.items[0].volumeInfo
        const newDetails = {}

        if (info.title) newDetails.title = info.title
        if (info.authors && info.authors.length) newDetails.author = info.authors.join(', ')
        if (info.categories && info.categories.length) newDetails.genres = info.categories.join(', ')
        if (info.publisher) newDetails.publisher = info.publisher
        if (info.publishedDate) newDetails.year = info.publishedDate.substring(0, 4) // extract YYYY

        // Bulk update form
        setForm(prev => ({ ...prev, ...newDetails }))
        // alert(`Found book: "${info.title}"! Details updated.`) 
        // Optional: Remove alert to make it smoother, or keep it for feedback
      } else {
        alert('Book not found in Google Books database.')
      }
    } catch (e) {
      console.error('Google Books API error:', e)
      alert('Failed to fetch book info from Google.')
    } finally {
      setLoadingField(null)
    }
  }

  // --- OCR HANDLERS (For other fields) ---
  function startOcr(field) {
    setOcrTargetField(field)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
      fileInputRef.current.click()
    }
  }

  async function handleFileChange(e) {
    const file = e.target.files?.[0]
    if (!file || !ocrTargetField) {
      e.target.value = ''
      return
    }

    setLoadingField(ocrTargetField)
    try {
      const processed = await preprocessImage(file)
      const { data } = await Tesseract.recognize(processed, 'eng', {
        logger: () => {},
      })

      let rawText = (data.text || '').trim()
      if (!rawText) {
        alert('No text detected.')
        return
      }

      const collapsed = rawText
        .split('\n')
        .map((l) => l.trim())
        .filter(Boolean)
        .join(' ')

      let value = collapsed

      // Simple regex filters for specific fields
      if (ocrTargetField === 'year') {
        const match = rawText.match(/\b(19|20)\d{2}\b/)
        if (match) value = match[0]
      } else if (ocrTargetField === 'isbn') {
        // If user accidentally uses OCR for ISBN (shouldn't happen with new UI, but safe to keep)
        const cleaned = rawText.replace(/[\s-]/g, '')
        const match = cleaned.match(/\b(?:\d{9}[\dX]|\d{13})\b/i)
        if (match) value = match[0]
      }

      updateField(ocrTargetField, value)
    } catch (err) {
      console.error('OCR error', err)
      alert('Failed to run OCR for this field.')
    } finally {
      setLoadingField(null)
      setOcrTargetField(null)
      e.target.value = ''
    }
  }

  function handleSubmit(e) {
    e.preventDefault()
    onSubmit(form)
  }

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,.4)',
        display: 'grid',
        placeItems: 'center',
        zIndex: 1000,
      }}
    >
      {/* Hidden file input used for OCR scans */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        style={{ display: 'none' }}
        onChange={handleFileChange}
      />

      <div
        className="card"
        style={{ width: 520, maxHeight: '90vh', overflowY: 'auto' }}
      >
        <h3>Add a New Book</h3>
        <form onSubmit={handleSubmit} className="grid">
          {/* Title */}
          <LabeledInputWithOcr
            label="Title*"
            placeholder="Title*"
            value={form.title}
            onChange={(v) => updateField('title', v)}
            onScan={() => startOcr('title')}
            loading={loadingField === 'title'}
          />

          {/* Author */}
          <LabeledInputWithOcr
            label="Author*"
            placeholder="Author*"
            value={form.author}
            onChange={(v) => updateField('author', v)}
            onScan={() => startOcr('author')}
            loading={loadingField === 'author'}
          />

          {/* Genres */}
          <LabeledInputWithOcr
            label="Genre(s)*"
            placeholder="Sci-Fi, Fantasy"
            value={form.genres}
            onChange={(v) => updateField('genres', v)}
            onScan={() => startOcr('genres')}
            loading={loadingField === 'genres'}
          />

          <div className="grid grid-2">
            {/* Publisher */}
            <LabeledInputWithOcr
              label="Publisher"
              placeholder="Publisher"
              value={form.publisher}
              onChange={(v) => updateField('publisher', v)}
              onScan={() => startOcr('publisher')}
              loading={loadingField === 'publisher'}
            />

            {/* Year */}
            <LabeledInputWithOcr
              label="Year"
              placeholder="Year"
              value={form.year}
              onChange={(v) => updateField('year', v)}
              onScan={() => startOcr('year')}
              loading={loadingField === 'year'}
            />
          </div>

          <div className="grid grid-2">
            {/* ISBN - Modified to use API Search instead of OCR */}
            <LabeledInputWithOcr
              label="ISBN (Type to Autofill)"
              placeholder="Enter ISBN..."
              value={form.isbn}
              onChange={(v) => updateField('isbn', v)}
              onScan={handleIsbnSearch} // Calls API directly
              loading={loadingField === 'isbn'}
              buttonLabel="Search" // Custom label
            />

            {/* City */}
            <LabeledInputWithOcr
              label="City"
              placeholder="City"
              value={form.city}
              onChange={(v) => updateField('city', v)}
              onScan={() => startOcr('city')}
              loading={loadingField === 'city'}
            />
          </div>

          {/* Area */}
          <LabeledInputWithOcr
            label="Area"
            placeholder="Area"
            value={form.area}
            onChange={(v) => updateField('area', v)}
            onScan={() => startOcr('area')}
            loading={loadingField === 'area'}
          />

          <div
            className="row"
            style={{ justifyContent: 'flex-end', marginTop: 8 }}
          >
            <button
              type="button"
              className="button secondary"
              onClick={onClose}
            >
              Cancel
            </button>
            <button className="button" type="submit">
              Add Book
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function LabeledInputWithOcr({
  label,
  placeholder,
  value,
  onChange,
  onScan,
  loading,
  buttonLabel = 'Scan', // Default to "Scan", but can be overridden
}) {
  return (
    <div>
      <div style={{ fontWeight: 600, marginBottom: 4 }}>{label}</div>
      <div className="row" style={{ gap: 8 }}>
        <input
          className="input"
          placeholder={placeholder}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          style={{ flex: 1 }}
        />
        <button
          type="button"
          className="button secondary"
          onClick={onScan}
          disabled={loading}
          style={{ minWidth: 80 }}
        >
          {loading ? '...' : buttonLabel}
        </button>
      </div>
    </div>
  )
}