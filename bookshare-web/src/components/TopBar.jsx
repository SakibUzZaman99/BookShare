import React from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { auth, db } from '../firebase'
import { signOut } from 'firebase/auth'
import { doc, getDoc } from 'firebase/firestore'

export default function TopBar() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [photoUrl, setPhotoUrl] = React.useState('/default_profile.png')

  // Load current user's photo from Firestore
  React.useEffect(() => {
    const user = auth.currentUser
    if (!user) return
    ;(async () => {
      try {
        const ref = doc(db, 'users', user.uid)
        const snap = await getDoc(ref)
        if (snap.exists()) {
          const data = snap.data()
          if (data.photoUrl) setPhotoUrl(data.photoUrl)
        }
      } catch (err) {
        console.error('Error loading profile image:', err)
      }
    })()
  }, [])

  const titleMap = {
    '/': 'Home',
    '/home': 'Home',
    '/library': 'My Library',
    '/borrowed': 'Borrowed',
    '/lent': 'Lent',
    '/profile': 'My Profile',
    '/ocr': 'OCR',
    '/explore': 'Explore Books',
  }
  const title = titleMap[pathname] || 'BookShare'

  async function logout() {
    await signOut(auth)
    navigate('/login', { replace: true })
  }

  return (
    <div className="topbar">
      <div className="topbar-inner container">
        {/* Left: Logo */}
        <div className="row">
          <Link to="/home" className="row" style={{ gap: 8 }}>
            <img src="/booksharelogo.png" alt="BookShare Logo" width="50" />
            <strong style={{ fontSize: 20 }}>BookShare</strong>
          </Link>
        </div>

        {/* Center: Title */}
        <div style={{ fontSize: 18, fontWeight: 800 }}>{title}</div>

        {/* Right: Buttons + Profile Pic */}
        <div className="row" style={{ alignItems: 'center', gap: 10 }}>
          <Link to="/library" className="button secondary">
            My Library
          </Link>
          <Link to="/explore" className="button secondary">
            Explore
          </Link>
          <Link to="/ocr" className="button secondary">
            OCR
          </Link>

          <Link to="/profile" title="My Profile">
            <img
              src={photoUrl || '/default_profile.png'}
              alt="Profile"
              style={{
                width: 42,
                height: 42,
                borderRadius: '50%',
                objectFit: 'cover',
                border: '2px solid #3b82f6',
                cursor: 'pointer',
                background: '#f3f4f6',
              }}
            />
          </Link>

          <button className="button" onClick={logout}>
            Log out
          </button>
        </div>
      </div>
    </div>
  )
}
