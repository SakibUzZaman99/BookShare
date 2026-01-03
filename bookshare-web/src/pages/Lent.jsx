// src/screens/Lent.jsx
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

export default function Lent() {
  const user = auth.currentUser
  const [selected, setSelected] = React.useState('Lent')

  const [lentBooks, setLentBooks] = React.useState([]) // accepted
  const [lendRequests, setLendRequests] = React.useState([]) // pending
  const [lentHistory, setLentHistory] = React.useState([]) // returned

  const [loading, setLoading] = React.useState(false)
  const [snack, setSnack] = React.useState('')

  const [reviewTarget, setReviewTarget] = React.useState(null)
  const [showReviewDialog, setShowReviewDialog] = React.useState(false)

  // ----- loaders -----

  async function loadLent() {
    if (!user) return
    setLoading(true)
    try {
      const acc = await getDocs(
        query(
          collection(db, 'requests'),
          where('ownerId', '==', user.uid),
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
          borrowerId: data.borrowerId || 'Unknown',
          book,
          hasReviewed: !!data.ownerReviewed,
          exchangeStatus: data.exchangeStatus || '',
          returnStatus: data.returnStatus || '',
        })
      }
      setLentBooks(list)
    } catch (e) {
      console.error(e)
      setLentBooks([])
      setSnack('Failed to load lent books.')
    } finally {
      setLoading(false)
    }
  }

  async function loadLendRequests() {
    if (!user) return
    setLoading(true)
    try {
      const snap = await getDocs(
        query(
          collection(db, 'requests'),
          where('ownerId', '==', user.uid),
          where('status', '==', 'pending')
        )
      )
      setLendRequests(
        snap.docs.map((d) => ({
          id: d.id,
          ...d.data(),
        }))
      )
    } catch (e) {
      console.error(e)
      setLendRequests([])
      setSnack('Failed to load lend requests.')
    } finally {
      setLoading(false)
    }
  }

  async function loadLentHistory() {
    if (!user) return
    setLoading(true)
    try {
      const snap = await getDocs(
        query(
          collection(db, 'requests'),
          where('ownerId', '==', user.uid),
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
          borrowerId: data.borrowerId || 'Unknown',
          book,
          hasReviewed: !!data.ownerReviewed,
        })
      }
      setLentHistory(list)
    } catch (e) {
      console.error(e)
      setLentHistory([])
      setSnack('Failed to load history.')
    } finally {
      setLoading(false)
    }
  }

  React.useEffect(() => {
    if (!user) return
    if (selected === 'Lent') loadLent()
    else if (selected === 'Lend Requests') loadLendRequests()
    else if (selected === 'History') loadLentHistory()
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
      setLentBooks((prev) =>
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
      await updateDoc(ref, {
        returnStatus: 'confirmed',
        returnTimestamp: now,
        status: 'returned',
      })
      await updateDoc(doc(db, 'books', entry.book.id), { status: 'LENDING' })

      setSnack('Return confirmed!')
      setLentBooks((prev) =>
        prev.filter((e) => e.requestId !== entry.requestId)
      )
      setLentHistory((prev) => [
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

  async function acceptRequest(req) {
    try {
      const ref = doc(db, 'requests', req.id)
      const bookId = req.bookId
      const now = Date.now()
      await updateDoc(ref, {
        status: 'accepted',
        exchangeStatus: 'none',
        exchangeTimestamp: 0,
        returnStatus: 'none',
        returnTimestamp: 0,
        borrowerReviewed: false,
        ownerReviewed: false,
        acceptedAt: now,
      })
      await updateDoc(doc(db, 'books', bookId), { status: 'LENT' })
      setSnack('Request accepted!')
      // reload lists
      loadLent()
      loadLendRequests()
    } catch (e) {
      console.error(e)
      setSnack('Failed to accept request.')
    }
  }

  async function rejectRequest(req) {
    try {
      await updateDoc(doc(db, 'requests', req.id), { status: 'rejected' })
      setSnack('Request rejected.')
      setLendRequests((prev) => prev.filter((r) => r.id !== req.id))
    } catch (e) {
      console.error(e)
      setSnack('Failed to reject request.')
    }
  }

  async function handleReviewSubmit(_bookRatingIgnored, userRating, comment) {
    if (!reviewTarget || !user) return
    try {
      const entry = reviewTarget
      const now = Date.now()

      // Only user review – lender rating borrower (no book rating allowed)
      await addDoc(collection(db, 'userReviews'), {
        targetUserId: entry.borrowerId,
        requestId: entry.requestId,
        reviewerId: user.uid,
        rating: userRating,
        comment,
        timestamp: now,
      })

      await updateDoc(doc(db, 'requests', entry.requestId), {
        ownerReviewed: true,
      })

      setLentHistory((prev) =>
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
          className={`button secondary ${selected === 'Lent' ? 'active' : ''}`}
          onClick={() => setSelected('Lent')}
        >
          Lent
        </button>
        <button
          className={`button secondary ${
            selected === 'Lend Requests' ? 'active' : ''
          }`}
          onClick={() => setSelected('Lend Requests')}
        >
          Lend Requests
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
      ) : selected === 'Lent' ? (
        <LentList
          entries={lentBooks}
          onConfirmExchange={confirmExchange}
          onConfirmReturn={confirmReturn}
        />
      ) : selected === 'Lend Requests' ? (
        <LendRequestsList
          reqs={lendRequests}
          onAccept={acceptRequest}
          onReject={rejectRequest}
        />
      ) : (
        <LentHistoryList
          entries={lentHistory}
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
          counterpartLabel="Borrower"
          showBookRating={false} // lender CANNOT rate the book
          onSubmit={handleReviewSubmit}
          onSkip={handleSkipReview}
        />
      )}
    </div>
  )
}

function LentList({ entries, onConfirmExchange, onConfirmReturn }) {
  if (!entries.length)
    return <div className="card">You haven’t lent any books yet.</div>

  return (
    <div className="list">
      {entries.map((entry) => (
        <div className="card" key={entry.requestId}>
          <div style={{ fontWeight: 800 }}>{entry.book.title}</div>
          <div className="small">Borrower: {entry.borrowerId}</div>
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
              Return Confirmed ✔ Book returned successfully.
            </p>
          )}
        </div>
      ))}
    </div>
  )
}

function LendRequestsList({ reqs, onAccept, onReject }) {
  if (!reqs.length)
    return <div className="card">No new lend requests.</div>

  return (
    <div className="list">
      {reqs.map((r) => (
        <div className="card" key={r.id}>
          <div>Book ID: {r.bookId}</div>
          <div>Borrower ID: {r.borrowerId}</div>
          <div className="row" style={{ marginTop: 8, gap: 8 }}>
            <button className="button" onClick={() => onAccept(r)}>
              Accept
            </button>
            <button
              className="button secondary"
              onClick={() => onReject(r)}
            >
              Reject
            </button>
          </div>
        </div>
      ))}
    </div>
  )
}

function LentHistoryList({ entries, onReview }) {
  if (!entries.length) return <div className="card">No history yet.</div>

  return (
    <div className="list">
      {entries.map((entry) => (
        <div className="card" key={entry.requestId}>
          <div style={{ fontWeight: 800 }}>{entry.book.title}</div>
          <div className="small">Borrower: {entry.borrowerId}</div>
          <div className="small" style={{ marginTop: 4 }}>
            {entry.hasReviewed
              ? 'You have reviewed this borrower.'
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

// Reuse the same ReviewDialog implementation as in Borrowed.jsx
function ReviewDialog(props) {
  // small wrapper so you can share exactly the same UI
  return BorrowedReviewDialogInternal(props)
}

// internal copy (same as in Borrowed.jsx)
function BorrowedReviewDialogInternal({
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
