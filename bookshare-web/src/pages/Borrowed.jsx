// src/screens/Borrowed.jsx
import React from 'react'
import { Link } from 'react-router-dom'
import { auth, db } from '../firebase'
import {
  collection,
  query,
  where,
  getDocs,
  doc,
  getDoc,
  updateDoc,
  addDoc,
} from 'firebase/firestore'

export default function Borrowed() {
  const user = auth.currentUser
  const [selected, setSelected] = React.useState('Borrowed')

  const [borrowedEntries, setBorrowedEntries] = React.useState([]) // ongoing accepted
  const [borrowRequests, setBorrowRequests] = React.useState([]) // pending
  const [borrowHistory, setBorrowHistory] = React.useState([]) // returned

  const [loading, setLoading] = React.useState(false)
  const [snack, setSnack] = React.useState('')

  const [reviewTarget, setReviewTarget] = React.useState(null)
  const [showReviewDialog, setShowReviewDialog] = React.useState(false)

  // ----- loaders -----

  async function loadBorrowed() {
    if (!user) return
    setLoading(true)
    try {
      const acc = await getDocs(
        query(
          collection(db, 'requests'),
          where('borrowerId', '==', user.uid),
          where('status', '==', 'accepted')
        )
      )
      const list = []
      for (const r of acc.docs) {
        const data = r.data()
        const bookId = data.bookId
        if (!bookId) continue
        const bookSnap = await getDoc(doc(db, 'books', bookId))
        if (!bookSnap.exists()) continue
        const book = { id: bookSnap.id, ...bookSnap.data() }
        list.push({
          requestId: r.id,
          book,
          hasReviewed: !!data.borrowerReviewed,
          exchangeStatus: data.exchangeStatus || '',
          returnStatus: data.returnStatus || '',
        })
      }
      setBorrowedEntries(list)
    } catch (e) {
      console.error(e)
      setBorrowedEntries([])
      setSnack('Failed to load borrowed books.')
    } finally {
      setLoading(false)
    }
  }

  async function loadBorrowRequests() {
    if (!user) return
    setLoading(true)
    try {
      const snap = await getDocs(
        query(
          collection(db, 'requests'),
          where('borrowerId', '==', user.uid),
          where('status', '==', 'pending')
        )
      )
      setBorrowRequests(
        snap.docs.map((d) => ({
          id: d.id,
          ...d.data(),
        }))
      )
    } catch (e) {
      console.error(e)
      setBorrowRequests([])
      setSnack('Failed to load borrow requests.')
    } finally {
      setLoading(false)
    }
  }

  async function loadBorrowHistory() {
    if (!user) return
    setLoading(true)
    try {
      const snap = await getDocs(
        query(
          collection(db, 'requests'),
          where('borrowerId', '==', user.uid),
          where('status', '==', 'returned')
        )
      )
      const list = []
      for (const r of snap.docs) {
        const data = r.data()
        const bookId = data.bookId
        if (!bookId) continue
        const bookSnap = await getDoc(doc(db, 'books', bookId))
        if (!bookSnap.exists()) continue
        const book = { id: bookSnap.id, ...bookSnap.data() }
        list.push({
          requestId: r.id,
          book,
          hasReviewed: !!data.borrowerReviewed,
        })
      }
      setBorrowHistory(list)
    } catch (e) {
      console.error(e)
      setBorrowHistory([])
      setSnack('Failed to load history.')
    } finally {
      setLoading(false)
    }
  }

  React.useEffect(() => {
    if (!user) return
    if (selected === 'Borrowed') loadBorrowed()
    else if (selected === 'Borrow Requests') loadBorrowRequests()
    else if (selected === 'History') loadBorrowHistory()
  }, [selected, user])

  // ----- actions -----

  async function confirmExchange(entry) {
    try {
      const ref = doc(db, 'requests', entry.requestId)
      const now = Date.now()
      await updateDoc(ref, {
        exchangeStatus: 'confirmed',
        exchangeTimestamp: now,
      })
      setBorrowedEntries((prev) =>
        prev.map((e) =>
          e.requestId === entry.requestId
            ? { ...e, exchangeStatus: 'confirmed' }
            : e
        )
      )
      setSnack('Exchange confirmed!')
    } catch (e) {
      console.error(e)
      setSnack('Failed to confirm exchange.')
    }
  }

  async function confirmReturn(entry) {
    try {
      const ref = doc(db, 'requests', entry.requestId)
      const now = Date.now()
      // mark request returned + free the book
      await updateDoc(ref, {
        returnStatus: 'confirmed',
        returnTimestamp: now,
        status: 'returned',
      })
      await updateDoc(doc(db, 'books', entry.book.id), { status: 'LENDING' })

      setSnack('Return confirmed!')
      // move from ongoing to history
      setBorrowedEntries((prev) =>
        prev.filter((e) => e.requestId !== entry.requestId)
      )
      setBorrowHistory((prev) => [
        { ...entry, hasReviewed: false, returnStatus: 'confirmed' },
        ...prev,
      ])
      setReviewTarget({ ...entry, hasReviewed: false })
      setShowReviewDialog(true)
    } catch (e) {
      console.error(e)
      setSnack('Failed to confirm return.')
    }
  }

  async function handleReviewSubmit(bookRating, userRating, comment) {
    if (!reviewTarget || !user) return
    try {
      const now = Date.now()
      const entry = reviewTarget

      // Book review (borrower can rate book)
      if (typeof bookRating === 'number') {
        await addDoc(collection(db, 'bookReviews'), {
          bookId: entry.book.id,
          requestId: entry.requestId,
          reviewerId: user.uid,
          rating: bookRating,
          comment,
          timestamp: now,
        })
      }

      // User review (rate owner)
      await addDoc(collection(db, 'userReviews'), {
        targetUserId: entry.book.ownerId,
        requestId: entry.requestId,
        reviewerId: user.uid,
        rating: userRating,
        comment,
        timestamp: now,
      })

      // mark request as reviewed
      await updateDoc(doc(db, 'requests', entry.requestId), {
        borrowerReviewed: true,
      })

      setBorrowHistory((prev) =>
        prev.map((e) =>
          e.requestId === entry.requestId ? { ...e, hasReviewed: true } : e
        )
      )
      setSnack('Thank you for your review!')
    } catch (e) {
      console.error(e)
      setSnack('Failed to submit review.')
    } finally {
      setShowReviewDialog(false)
      setReviewTarget(null)
    }
  }

  function handleSkipReview() {
    setShowReviewDialog(false)
    setReviewTarget(null)
  }

  // ----- UI -----

  return (
    <div className="container">
      <div className="tabrow">
        <button
          className={`button secondary ${
            selected === 'Borrowed' ? 'active' : ''
          }`}
          onClick={() => setSelected('Borrowed')}
        >
          Borrowed
        </button>
        <button
          className={`button secondary ${
            selected === 'Borrow Requests' ? 'active' : ''
          }`}
          onClick={() => setSelected('Borrow Requests')}
        >
          Borrow Requests
        </button>
        <button
          className={`button secondary ${
            selected === 'History' ? 'active' : ''
          }`}
          onClick={() => setSelected('History')}
        >
          History
        </button>
      </div>

      {loading ? (
        <div className="card">Loading...</div>
      ) : selected === 'Borrowed' ? (
        <BorrowedList
          entries={borrowedEntries}
          onConfirmExchange={confirmExchange}
          onConfirmReturn={confirmReturn}
        />
      ) : selected === 'Borrow Requests' ? (
        <BorrowRequestsList reqs={borrowRequests} />
      ) : (
        <BorrowHistoryList
          entries={borrowHistory}
          onReview={(entry) => {
            setReviewTarget(entry)
            setShowReviewDialog(true)
          }}
        />
      )}

      {snack && <p className="small" style={{ marginTop: 8 }}>{snack}</p>}

      {showReviewDialog && reviewTarget && (
        <ReviewDialog
          bookTitle={reviewTarget.book.title}
          counterpartLabel="Owner"
          showBookRating={true} // borrower can rate the book
          onSubmit={handleReviewSubmit}
          onSkip={handleSkipReview}
        />
      )}
    </div>
  )
}

function BorrowedList({ entries, onConfirmExchange, onConfirmReturn }) {
  if (!entries.length) return <div className="card">No borrowed books yet.</div>

  return (
    <div className="list">
      {entries.map((entry) => (
        <div className="card" key={entry.requestId}>
          <div style={{ fontWeight: 800 }}>{entry.book.title}</div>
          <div className="small">Owner: {entry.book.ownerId}</div>
          <div className="row" style={{ marginTop: 8, gap: 8 }}>
            {entry.exchangeStatus !== 'confirmed' ? (
              <button
                className="button"
                onClick={() => onConfirmExchange(entry)}
              >
                Confirm Exchange
              </button>
            ) : entry.returnStatus !== 'confirmed' ? (
              <button className="button" onClick={() => onConfirmReturn(entry)}>
                Confirm Return
              </button>
            ) : null}
            <Link className="button secondary" to={`/chat/${entry.requestId}`}>
              Message
            </Link>
          </div>

          {entry.exchangeStatus === 'confirmed' &&
            entry.returnStatus !== 'confirmed' && (
              <p className="small" style={{ marginTop: 6 }}>
                Exchange Confirmed ✔
              </p>
            )}
          {entry.returnStatus === 'confirmed' && (
            <p className="small" style={{ marginTop: 6 }}>
              Return Confirmed ✔ Book successfully returned.
            </p>
          )}
        </div>
      ))}
    </div>
  )
}

function BorrowRequestsList({ reqs }) {
  if (!reqs.length) {
    return <div className="card">No pending borrow requests.</div>
  }
  return (
    <div className="list">
      {reqs.map((r) => (
        <div className="card" key={r.id}>
          <div>Book ID: {r.bookId}</div>
          <div>Status: {r.status}</div>
          <div className="small">Waiting for owner’s response...</div>
        </div>
      ))}
    </div>
  )
}

function BorrowHistoryList({ entries, onReview }) {
  if (!entries.length) {
    return <div className="card">No history yet.</div>
  }
  return (
    <div className="list">
      {entries.map((entry) => (
        <div className="card" key={entry.requestId}>
          <div style={{ fontWeight: 800 }}>{entry.book.title}</div>
          <div className="small">Owner: {entry.book.ownerId}</div>
          <div className="small" style={{ marginTop: 4 }}>
            {entry.hasReviewed
              ? 'You have reviewed this exchange.'
              : 'No review yet.'}
          </div>
          <div className="row" style={{ marginTop: 8, gap: 8 }}>
            <button className="button secondary" onClick={() => onReview(entry)}>
              {entry.hasReviewed ? 'Update Review' : 'Review'}
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}

// --- Shared Review Dialog (borrower & lender use same UI) ---

function ReviewDialog({
  bookTitle,
  counterpartLabel,
  showBookRating,
  onSubmit,
  onSkip,
}) {
  const [bookRating, setBookRating] = React.useState(8)
  const [userRating, setUserRating] = React.useState(8)
  const [comment, setComment] = React.useState('')

  function handleSubmit(e) {
    e.preventDefault()
    const bookVal = showBookRating ? bookRating : null
    onSubmit(bookVal, userRating, comment.trim())
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
      <div className="card" style={{ maxWidth: 480 }}>
        <h3>Review your experience</h3>
        <p className="small">Book: {bookTitle}</p>

        <form onSubmit={handleSubmit} className="grid">
          {showBookRating && (
            <div>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>
                Rate the book (0–10): {bookRating}
              </div>
              <input
                type="range"
                min="0"
                max="10"
                value={bookRating}
                onChange={(e) => setBookRating(Number(e.target.value))}
                style={{ width: '100%' }}
              />
            </div>
          )}

          <div>
            <div style={{ fontWeight: 600, marginBottom: 4 }}>
              Rate the {counterpartLabel} (0–10): {userRating}
            </div>
            <input
              type="range"
              min="0"
              max="10"
              value={userRating}
              onChange={(e) => setUserRating(Number(e.target.value))}
              style={{ width: '100%' }}
            />
          </div>

          <textarea
            className="input"
            placeholder="Optional comment"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            rows={3}
          />

          <div className="row" style={{ justifyContent: 'flex-end', gap: 8 }}>
            <button
              type="button"
              className="button secondary"
              onClick={onSkip}
            >
              Skip
            </button>
            <button type="submit" className="button">
              Submit
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
