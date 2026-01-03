// src/screens/Profile.jsx
import React from 'react'
import { auth, db } from '../firebase'
import {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  collection,
  query,
  where,
  getDocs,
} from 'firebase/firestore'

export default function Profile() {
  const user = auth.currentUser
  const [data, setData] = React.useState({
    fullName: '',
    bio: '',
    city: '',
    employment: '',
    joinedAt: 'N/A',
    rating: 'N/A',
    photoUrl: '',
  })
  const [stats, setStats] = React.useState({
    totalBooks: 0,
    totalBorrowed: 0,
    totalLent: 0,
  })
  const [edit, setEdit] = React.useState(false)
  const [saving, setSaving] = React.useState(false)

  // --- Load user data ---
  React.useEffect(() => {
    ;(async () => {
      if (!user) return
      const ref = doc(db, 'users', user.uid)
      const snap = await getDoc(ref)
      if (snap.exists()) {
        setData((prev) => ({ ...prev, ...snap.data() }))
      } else {
        const joinedAt = new Date(user.metadata.creationTime).toLocaleDateString(
          undefined,
          { day: '2-digit', month: 'short', year: 'numeric' }
        )
        await setDoc(ref, {
          fullName: '',
          bio: '',
          city: '',
          employment: '',
          joinedAt,
          rating: 'N/A',
          photoUrl: '',
        })
        setData((d) => ({ ...d, joinedAt }))
      }

      await fetchStats()
      await fetchRatingFromReviews()
    })()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // --- Fetch stats (books, borrowed, lent) ---
  async function fetchStats() {
    if (!user) return
    // Books owned
    const b = await getDocs(
      query(collection(db, 'books'), where('ownerId', '==', user.uid))
    )
    // Borrowed (accepted or returned)
    const br = await getDocs(
      query(
        collection(db, 'requests'),
        where('borrowerId', '==', user.uid),
        where('status', 'in', ['accepted', 'returned'])
      )
    )
    // Lent (accepted or returned)
    const lr = await getDocs(
      query(
        collection(db, 'requests'),
        where('ownerId', '==', user.uid),
        where('status', 'in', ['accepted', 'returned'])
      )
    )

    setStats({
      totalBooks: b.size,
      totalBorrowed: br.size,
      totalLent: lr.size,
    })
  }

  // --- Compute rating from userReviews (0–10) ---
  async function fetchRatingFromReviews() {
    if (!user) return
    try {
      const qSnap = await getDocs(
        query(
          collection(db, 'userReviews'),
          where('targetUserId', '==', user.uid)
        )
      )

      const ratings = qSnap.docs
        .map((d) => d.data().rating)
        .map((v) => (typeof v === 'number' ? v : Number(v)))
        .filter((v) => Number.isFinite(v))

      let ratingStr = 'N/A'
      if (ratings.length) {
        const avg = ratings.reduce((a, b) => a + b, 0) / ratings.length
        ratingStr = `${avg.toFixed(1)} / 10`
      }

      setData((prev) => ({ ...prev, rating: ratingStr }))

      // Optional: cache in users doc for faster future reads
      await updateDoc(doc(db, 'users', user.uid), { rating: ratingStr }).catch(
        () => {}
      )
    } catch (e) {
      console.error('Failed to load rating from reviews', e)
    }
  }

  // --- Save changes ---
  async function save() {
    if (!user) return
    setSaving(true)
    await updateDoc(doc(db, 'users', user.uid), {
      fullName: data.fullName,
      bio: data.bio,
      city: data.city,
      employment: data.employment,
    })
    setSaving(false)
    setEdit(false)
  }

  return (
    <div className="container" style={{ maxWidth: 720 }}>
      {/* Top heading */}
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <h2>My Profile</h2>
        <button className="button" onClick={() => setEdit((e) => !e)}>
          {edit ? 'Close' : 'Edit'}
        </button>
      </div>

      {/* --- Profile picture section --- */}
      <div style={{ textAlign: 'center', marginTop: 20, marginBottom: 20 }}>
        <img
          src={data.photoUrl || '/default_profile.png'}
          alt="Profile"
          style={{
            width: 120,
            height: 120,
            borderRadius: '50%',
            objectFit: 'cover',
            border: '3px solid #3b82f6',
            background: '#f3f4f6',
          }}
        />
        <h3 style={{ marginTop: 10 }}>{data.fullName || 'Unnamed User'}</h3>
        <p className="small">{data.city || 'City not set'}</p>
      </div>

      {/* --- Editable info --- */}
      <div className="card">
        {edit ? (
          <div className="grid">
            <input
              className="input"
              placeholder="Full Name"
              value={data.fullName}
              onChange={(e) => setData({ ...data, fullName: e.target.value })}
            />
            <textarea
              className="input"
              placeholder="Bio"
              value={data.bio}
              onChange={(e) => setData({ ...data, bio: e.target.value })}
            />
            <input
              className="input"
              placeholder="City"
              value={data.city}
              onChange={(e) => setData({ ...data, city: e.target.value })}
            />
            <input
              className="input"
              placeholder="Employment / Student at"
              value={data.employment}
              onChange={(e) =>
                setData({ ...data, employment: e.target.value })
              }
            />
            <div className="row" style={{ justifyContent: 'flex-end' }}>
              <button className="button" onClick={save} disabled={saving}>
                {saving ? 'Saving...' : 'Save Changes'}
              </button>
            </div>
          </div>
        ) : (
          <div className="grid">
            <Field label="Full Name" value={data.fullName || 'N/A'} />
            <Field label="Bio" value={data.bio || 'N/A'} />
            <Field label="City" value={data.city || 'N/A'} />
            <Field
              label="Employment / Student at"
              value={data.employment || 'N/A'}
            />
          </div>
        )}
      </div>

      {/* --- Stats section --- */}
      <div className="card grid grid-2" style={{ marginTop: 12 }}>
        <Info label="Joined At" value={data.joinedAt} />
        <Info label="Rating" value={String(data.rating)} />
        <Info label="Total Borrowed" value={String(stats.totalBorrowed)} />
        <Info label="Total Lent" value={String(stats.totalLent)} />
        <Info label="Total Books in Library" value={String(stats.totalBooks)} />
      </div>
    </div>
  )
}

function Field({ label, value }) {
  return (
    <div>
      <div style={{ fontWeight: 700 }}>{label}</div>
      <div className="small">{value}</div>
    </div>
  )
}

function Info({ label, value }) {
  return (
    <div className="row" style={{ justifyContent: 'space-between' }}>
      <strong>{label}</strong>
      <span className="small">{value}</span>
    </div>
  )
}
