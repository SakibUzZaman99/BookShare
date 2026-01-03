// src/screens/Book.jsx
import React from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { db, auth } from '../firebase'
import {
  doc,
  getDoc,
  addDoc,
  collection,
  query,
  where,
  getDocs,
} from 'firebase/firestore'

export default function Book() {
  const { bookId } = useParams()
  const [book, setBook] = React.useState(null)
  const [sending, setSending] = React.useState(false)
  const [msg, setMsg] = React.useState('')

  const [avgRating, setAvgRating] = React.useState('N/A')
  const [totalRatings, setTotalRatings] = React.useState(0)

  const nav = useNavigate()

  // Load book details
  React.useEffect(() => {
    async function load() {
      const ref = doc(db, 'books', bookId)
      const snap = await getDoc(ref)
      if (snap.exists()) {
        setBook({ id: snap.id, ...snap.data() })
      } else {
        setBook(false) // to show not found message
      }
    }
    load()
  }, [bookId])

  // Load book rating from bookReviews (0–10)
  React.useEffect(() => {
    async function loadRatings() {
      try {
        const q = query(
          collection(db, 'bookReviews'),
          where('bookId', '==', bookId)
        )
        const snap = await getDocs(q)
        if (snap.empty) {
          setAvgRating('N/A')
          setTotalRatings(0)
          return
        }

        const ratings = snap.docs
          .map((d) => d.data().rating)
          .map((v) => (typeof v === 'number' ? v : Number(v)))
          .filter((v) => Number.isFinite(v))

        if (!ratings.length) {
          setAvgRating('N/A')
          setTotalRatings(0)
          return
        }

        const sum = ratings.reduce((a, b) => a + b, 0)
        const avg = sum / ratings.length
        setAvgRating(`${avg.toFixed(1)} / 10`)
        setTotalRatings(ratings.length)
      } catch (e) {
        console.error('Error loading ratings', e)
        setAvgRating('N/A')
        setTotalRatings(0)
      }
    }

    if (bookId) loadRatings()
  }, [bookId])

  async function requestToLend() {
    const user = auth.currentUser
    if (!user) return
    if (book.ownerId === user.uid) {
      setMsg('You cannot request your own book.')
      return
    }
    setSending(true)
    try {
      await addDoc(collection(db, 'requests'), {
        bookId,
        ownerId: book.ownerId,
        borrowerId: user.uid,
        status: 'pending',
        timestamp: Date.now(),
      })
      setMsg('Request sent successfully!')
    } catch (e) {
      console.error(e)
      setMsg('Failed to send request.')
    } finally {
      setSending(false)
    }
  }

  if (book === null)
    return (
      <div className="container" style={{ padding: 40 }}>
        <div className="card">Loading...</div>
      </div>
    )
  if (book === false)
    return (
      <div className="container" style={{ padding: 40 }}>
        <div className="card">Book not found or removed.</div>
      </div>
    )

  return (
    <div className="container" style={{ maxWidth: 720 }}>
      <div className="card">
        <h2>Book Details</h2>
        <div className="hr" />
        <h3>{book.title}</h3>
        <div className="small">by {book.author}</div>

        {/* Rating section */}
        <div style={{ marginTop: 12, marginBottom: 12 }}>
          <div style={{ fontWeight: 700 }}>Rating</div>
          <div className="small">
            {avgRating}{' '}
            {totalRatings > 0
              ? `(${totalRatings} rating${totalRatings > 1 ? 's' : ''})`
              : '(No ratings yet)'}
          </div>
        </div>

        <div className="hr" />

        <Details label="Genres" value={(book.genres || []).join(', ')} />
        <Details label="Publisher" value={book.publisher} />
        <Details label="Year" value={book.year} />
        <Details label="ISBN" value={book.isbn} />
        <Details label="City" value={book.city} />
        <Details label="Area" value={book.area} />

        <div className="hr" />
        <button className="button" onClick={requestToLend} disabled={sending}>
          {sending ? 'Sending...' : 'Request to Lend'}
        </button>
        {msg && (
          <p className="small" style={{ marginTop: 8 }}>
            {msg}
          </p>
        )}
        <div className="hr" />
        <button className="button secondary" onClick={() => nav(-1)}>
          Back
        </button>
      </div>
    </div>
  )
}

function Details({ label, value }) {
  if (!value) return null
  return (
    <div style={{ margin: '6px 0' }}>
      <div style={{ fontWeight: 700 }}>{label}</div>
      <div className="small">{value}</div>
    </div>
  )
}
