// src/screens/Explore.jsx  (or src/pages/Explore.jsx)
import React from 'react'
import { auth, db } from '../firebase'
import {
  collection,
  onSnapshot,
  query,
  where,
  getDocs,
  addDoc,
  doc,
  setDoc,
} from 'firebase/firestore'

/**
 * Explore:
 * - Live-loads ALL books from `books`.
 * - Builds a unique list of titles (bookKey = lowercased title).
 * - Shows Title + Author in list.
 * - Clicking a book opens a rating popup for BOOK CONTENT (contentReviews).
 */

export default function Explore() {
  const user = auth.currentUser

  // ---------- book list ----------
  const [books, setBooks] = React.useState([]) // [{ bookKey, title, author }]
  const [listLoading, setListLoading] = React.useState(true)
  const [listError, setListError] = React.useState('')
  const [search, setSearch] = React.useState('')

  // ---------- dialog / reviews ----------
  const [dialogOpen, setDialogOpen] = React.useState(false)
  const [activeBook, setActiveBook] = React.useState(null) // { bookKey, title, author }
  const [bookKey, setBookKey] = React.useState(null)
  const [title, setTitle] = React.useState('')

  const [dialogLoading, setDialogLoading] = React.useState(false)
  const [snack, setSnack] = React.useState('')

  const [reviews, setReviews] = React.useState([])
  const [avgRating, setAvgRating] = React.useState(null)
  const [myReviewId, setMyReviewId] = React.useState(null)
  const [myRating, setMyRating] = React.useState(8)
  const [myComment, setMyComment] = React.useState('')

  // ---------- 1. live-load ALL books and build unique titles ----------
  React.useEffect(() => {
    setListLoading(true)
    setListError('')

    const unsub = onSnapshot(
      collection(db, 'books'),
      (snap) => {
        const allBooks = snap.docs.map((d) => {
          const data = d.data() || {}
          const clean = { ...data }
          delete clean.id
          return { id: d.id, ...clean }
        })

        const map = new Map()

        allBooks.forEach((b) => {
          const t = (b.title || '').trim() // your screenshot shows field name is `title`
          if (!t) return
          const key = t.toLowerCase()
          if (!map.has(key)) {
            map.set(key, {
              bookKey: key,
              title: t,
              author: (b.author || '').trim(),
            })
          }
        })

        setBooks(
          Array.from(map.values()).sort((a, b) =>
            a.title.localeCompare(b.title)
          )
        )
        setListLoading(false)
      },
      (err) => {
        console.error(err)
        setListError('Failed to load books.')
        setListLoading(false)
      }
    )

    return () => unsub()
  }, [])

  // ---------- 2. search filter ----------
  const filteredBooks = books.filter((b) => {
    if (!search.trim()) return true
    const s = search.toLowerCase()
    return (
      b.title.toLowerCase().includes(s) ||
      (b.author || '').toLowerCase().includes(s)
    )
  })

  // ---------- 3. load reviews for a chosen book ----------
  async function openBookDialog(book) {
    setActiveBook(book)
    setDialogOpen(true)
    setSnack('')
    setDialogLoading(true)

    setBookKey(book.bookKey)
    setTitle(book.title)

    try {
      const qRef = query(
        collection(db, 'contentReviews'),
        where('bookKey', '==', book.bookKey)
      )
      const snap = await getDocs(qRef)

      const list = snap.docs.map((d) => ({
        id: d.id,
        reviewerId: d.data().reviewerId || '',
        rating: d.data().rating || 0,
        comment: d.data().comment || '',
        timestamp: d.data().timestamp || 0,
      }))

      setReviews(list)

      if (list.length) {
        const avg =
          list.reduce((sum, r) => sum + (r.rating || 0), 0) / list.length
        setAvgRating(avg)
      } else {
        setAvgRating(null)
        setSnack('No content reviews yet for this book.')
      }

      if (user) {
        const mine = list.find((r) => r.reviewerId === user.uid)
        if (mine) {
          setMyReviewId(mine.id)
          setMyRating(mine.rating)
          setMyComment(mine.comment)
        } else {
          setMyReviewId(null)
          setMyRating(8)
          setMyComment('')
        }
      }
    } catch (e) {
      console.error(e)
      setSnack('Failed to load reviews.')
      setReviews([])
      setAvgRating(null)
    } finally {
      setDialogLoading(false)
    }
  }

  // ---------- 4. submit / update my review ----------
  async function handleSubmit(e) {
    e.preventDefault()
    if (!bookKey || !activeBook) {
      setSnack('Select a book first.')
      return
    }
    if (!user) {
      setSnack('You must be logged in to review.')
      return
    }

    setDialogLoading(true)
    try {
      const now = Date.now()
      const data = {
        bookKey,
        bookTitle: title.trim(),
        reviewerId: user.uid,
        rating: Number(myRating),
        comment: myComment.trim(),
        timestamp: now,
      }

      if (myReviewId) {
        await setDoc(doc(db, 'contentReviews', myReviewId), data)
      } else {
        const ref = await addDoc(collection(db, 'contentReviews'), data)
        setMyReviewId(ref.id)
      }

      setSnack('Review saved.')
      await openBookDialog(activeBook)
    } catch (e) {
      console.error(e)
      setSnack('Failed to save review.')
      setDialogLoading(false)
    }
  }

  function closeDialog() {
    setDialogOpen(false)
    setActiveBook(null)
    setReviews([])
    setAvgRating(null)
    setMyReviewId(null)
    setMyRating(8)
    setMyComment('')
    setSnack('')
  }

  // ---------- 5. UI ----------
  return (
    <div className="container" style={{ paddingTop: 12 }}>
      <div className="card">
        <h2>Explore Book Content Reviews</h2>
        
        <input
          className="input"
          style={{ marginTop: 12 }}
          placeholder="Search by title or author..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {listLoading && <div className="card">Loading books...</div>}
      {listError && (
        <p className="small" style={{ marginTop: 8 }}>
          {listError}
        </p>
      )}

      {!listLoading && !listError && filteredBooks.length === 0 && (
        <div className="card" style={{ marginTop: 12 }}>
          No books matched your search.
        </div>
      )}

      {/* book list */}
      <div className="list" style={{ marginTop: 12 }}>
        {filteredBooks.map((b) => (
          <button
            key={b.bookKey}
            className="card row"
            style={{ alignItems: 'center', cursor: 'pointer' }}
            onClick={() => openBookDialog(b)}
          >
            <img
              src="/booklogo_profile.png"
              alt={b.title}
              style={{
                width: 70,
                height: 90,
                objectFit: 'cover',
                borderRadius: 8,
                background: '#e5e7eb',
              }}
            />
            <div
              style={{
                marginLeft: 12,
                flex: 1,
                textAlign: 'left',
                display: 'flex',
                flexDirection: 'column',
                gap: 4,
              }}
            >
              {/* explicitly render title line */}
              <div className="small">
                <strong>{b.title || 'Untitled book'}</strong>
              </div>
              {b.author && (
                <div className="small">Author: {b.author}</div>
              )}
              <div className="small">
                Click to rate the book&apos;s content →
              </div>
            </div>
          </button>
        ))}
      </div>

      {snack && (
        <p className="small" style={{ marginTop: 8 }}>
          {snack}
        </p>
      )}

      {/* ---------- dialog ---------- */}
      {dialogOpen && activeBook && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            background: 'rgba(0,0,0,.45)',
            display: 'grid',
            placeItems: 'center',
            zIndex: 1000,
          }}
        >
          <div className="card" style={{ maxWidth: 520, width: '100%' }}>
            <h3>Review: {activeBook.title}</h3>
            {activeBook.author && (
              <p className="small">Author: {activeBook.author}</p>
            )}

            {dialogLoading && <p className="small">Loading...</p>}

            {!dialogLoading && (
              <>
                {avgRating != null && (
                  <p style={{ fontWeight: 600, marginTop: 4 }}>
                    Average rating: {avgRating.toFixed(1)} / 10 (
                    {reviews.length} review{reviews.length === 1 ? '' : 's'})
                  </p>
                )}

                {!user && (
                  <p className="small" style={{ marginTop: 8 }}>
                    You must be logged in to submit a review.
                  </p>
                )}

                {user && (
                  <form
                    onSubmit={handleSubmit}
                    className="grid"
                    style={{ gap: 8, marginTop: 8 }}
                  >
                    <div>
                      <div style={{ fontWeight: 600, marginBottom: 4 }}>
                        Rating (0–10): {myRating}
                      </div>
                      <input
                        type="range"
                        min="0"
                        max="10"
                        value={myRating}
                        onChange={(e) =>
                          setMyRating(Number(e.target.value))
                        }
                        style={{ width: '100%' }}
                      />
                    </div>

                    <textarea
                      className="input"
                      rows={3}
                      placeholder="Comment (optional)"
                      value={myComment}
                      onChange={(e) => setMyComment(e.target.value)}
                    />

                    <div
                      className="row"
                      style={{ justifyContent: 'flex-end', gap: 8 }}
                    >
                      <button
                        type="button"
                        className="button secondary"
                        onClick={closeDialog}
                      >
                        Close
                      </button>
                      <button type="submit" className="button">
                        {myReviewId ? 'Update Review' : 'Submit Review'}
                      </button>
                    </div>
                  </form>
                )}

                <div
                  className="card"
                  style={{ marginTop: 12, maxHeight: 260, overflowY: 'auto' }}
                >
                  <h4>All Reviews</h4>
                  {reviews.length === 0 && (
                    <p className="small">No reviews yet. Be the first!</p>
                  )}

                  <div className="list">
                    {reviews.map((r) => (
                      <div key={r.id} className="card">
                        <div style={{ fontWeight: 800 }}>
                          Rating: {r.rating} / 10
                        </div>
                        {r.comment && <p>{r.comment}</p>}
                        {user && r.reviewerId === user.uid && (
                          <p className="small" style={{ color: '#2563eb' }}>
                            (Your review)
                          </p>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
