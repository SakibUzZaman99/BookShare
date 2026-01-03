import React from 'react'
import { Link } from 'react-router-dom'
import { db, auth } from '../firebase'
import { collection, query, where, onSnapshot, getDocs, doc, getDoc } from 'firebase/firestore'
import { BOOK_STATUS } from '../helpers'

export default function Home() {
  const [search, setSearch] = React.useState('')
  const [lendingBooks, setLendingBooks] = React.useState([])
  const [borrowedBooks, setBorrowedBooks] = React.useState([])
  const [lentBooks, setLentBooks] = React.useState([])
  const user = auth.currentUser

  // === Load books for lending ===
  React.useEffect(() => {
    const q = query(collection(db, 'books'), where('status', '==', BOOK_STATUS.LENDING))
    const unsub = onSnapshot(q, (snap) => {
      const data = snap.docs.map((d) => {
        // ✅ Completely ignore any 'id' from Firestore data
        const bookData = d.data() || {}
        const cleanBook = { ...bookData }
        delete cleanBook.id
        return { id: d.id, ...cleanBook }
      })
      console.log('Fetched books with clean IDs:', data.map(b => ({ id: b.id, title: b.title })))
      setLendingBooks(data)
    })
    return () => unsub()
  }, [])

  // === Load Borrowed + Lent ===
  async function loadUserRelated() {
    if (!user) return

    const br = await getDocs(
      query(collection(db, 'requests'), where('borrowerId', '==', user.uid), where('status', '==', 'accepted'))
    )
    const borrowed = []
    for (const r of br.docs) {
      const bookSnap = await getDoc(doc(db, 'books', r.data().bookId))
      if (bookSnap.exists()) {
        const bookData = bookSnap.data() || {}
        delete bookData.id
        borrowed.push({ id: bookSnap.id, ...bookData })
      }
    }
    setBorrowedBooks(borrowed)

    const lr = await getDocs(
      query(collection(db, 'requests'), where('ownerId', '==', user.uid), where('status', '==', 'accepted'))
    )
    const lent = []
    for (const r of lr.docs) {
      const bookSnap = await getDoc(doc(db, 'books', r.data().bookId))
      if (bookSnap.exists()) {
        const bookData = bookSnap.data() || {}
        delete bookData.id
        lent.push({ id: bookSnap.id, ...bookData })
      }
    }
    setLentBooks(lent)
  }

  React.useEffect(() => { loadUserRelated() }, [])

  const filtered = lendingBooks.filter(
    (b) =>
      b.title?.toLowerCase().includes(search.toLowerCase()) ||
      b.author?.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="container" style={{ paddingTop: 12 }}>
      <div className="card">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <Section title="Borrowed" items={borrowedBooks.slice(0, 3).map(b => b.title)} to="/borrowed" />
          <Section title="Lent" items={lentBooks.slice(0, 3).map(b => b.title)} to="/lent" />
        </div>
        <div className="hr" />
        <input
          className="input"
          placeholder="Search for books..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <div className="list" style={{ marginTop: 12 }}>
        {filtered.length === 0 && <div className="card">No books available for lending.</div>}

        {filtered.map((b) => {
          console.log('Book ID check:', b.id, 'Title:', b.title)
          return (
            <Link
              key={b.id}
              className="card row"
              to={`/book/${b.id}`}
              style={{ alignItems: 'center' }}
            >
              <img
                src={b.coverUrl || '/booklogo_profile.png'}
                alt={b.title}
                style={{
                  width: 80,
                  height: 100,
                  objectFit: 'cover',
                  borderRadius: 8,
                  background: '#e5e7eb',
                }}
              />
              <div style={{ marginLeft: 12, flex: 1 }}>
                <div style={{ fontWeight: 800 }}>{b.title}</div>
                <div className="small">Author: {b.author}</div>
              </div>
            </Link>
          )
        })}
      </div>
    </div>
  )
}

function Section({ title, items, to }) {
  return (
    <div className="card" style={{ flex: 1 }}>
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'baseline' }}>
        <div style={{ fontWeight: 800 }}>{title}</div>
        <Link to={to} className="small">View all →</Link>
      </div>
      <div className="grid">
        {items.map((t, i) => (<div key={i}>• {t}</div>))}
      </div>
    </div>
  )
}
