import React from 'react'
import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { onAuthStateChanged } from 'firebase/auth'
import { auth } from './firebase'
import TopBar from './components/TopBar'
import Login from './pages/Login'
import Register from './pages/Register'
import Home from './pages/Home'
import MyLibrary from './pages/MyLibrary'
import Book from './pages/Book'
import Borrowed from './pages/Borrowed'
import Lent from './pages/Lent'
import Profile from './pages/Profile'
import Chat from './pages/Chat'
import Ocr from './pages/Ocr'
import Explore from './pages/Explore'

function RequireAuth({ children }) {
  const [ready, setReady] = React.useState(false)
  const [user, setUser] = React.useState(null)
  const location = useLocation()

  React.useEffect(() => {
    const unsub = onAuthStateChanged(auth, (u) => {
      setUser(u)
      setReady(true)
    })
    return () => unsub()
  }, [])

  if (!ready) return <div className="container" style={{ padding: 40 }}>Loading...</div>
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }
  return children
}

export default function App() {
  const location = useLocation()
  const isAuthScreen = ['/login', '/register'].includes(location.pathname)

  return (
    <>
      {!isAuthScreen && <TopBar />}
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        <Route path="/" element={<Navigate to="/home" replace />} />
        <Route path="/home" element={<RequireAuth><Home /></RequireAuth>} />
        <Route path="/library" element={<RequireAuth><MyLibrary /></RequireAuth>} />
        {/* Ensure this route exists */}
        <Route path="/book/:bookId" element={<RequireAuth><Book /></RequireAuth>} />
        <Route path="/borrowed" element={<RequireAuth><Borrowed /></RequireAuth>} />
        <Route path="/lent" element={<RequireAuth><Lent /></RequireAuth>} />
        <Route path="/profile" element={<RequireAuth><Profile /></RequireAuth>} />
        <Route path="/chat/:requestId" element={<RequireAuth><Chat /></RequireAuth>} />
        <Route path="/ocr" element={<RequireAuth><Ocr /></RequireAuth>} />
        <Route path="/explore" element={<RequireAuth><Explore /></RequireAuth>} />

        {/* Fallback route */}
        <Route path="*" element={<div className="container" style={{ padding: 40 }}>Not found</div>} />
      </Routes>
    </>
  )
}
