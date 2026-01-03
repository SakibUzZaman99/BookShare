import React from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { auth, googleProvider } from '../firebase'
import { signInWithEmailAndPassword, signInWithPopup, sendEmailVerification } from 'firebase/auth'

export default function Login() {
  const [email, setEmail] = React.useState('')
  const [password, setPassword] = React.useState('')
  const [loading, setLoading] = React.useState(false)
  const nav = useNavigate()
  const loc = useLocation()

  async function onLogin(e) {
    e.preventDefault()
    try {
      setLoading(true)
      const cred = await signInWithEmailAndPassword(auth, email, password)
      if (!cred.user.emailVerified) {
        await sendEmailVerification(cred.user)
        alert('Verification email re-sent. Please verify before continuing.')
      }
      const to = (loc.state && loc.state.from && loc.state.from.pathname) || '/home'
      nav(to, { replace: true })
    } catch (e) {
      alert(e.message)
    } finally {
      setLoading(false)
    }
  }

  async function onGoogle() {
    try {
      setLoading(true)
      await signInWithPopup(auth, googleProvider)
      nav('/home', { replace: true })
    } catch (e) {
      alert(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container" style={{maxWidth: 440, paddingTop: 60}}>
      <div className="card">
        <h2>Login</h2>
        <form onSubmit={onLogin}>
          <input className="input" placeholder="Email" value={email} onChange={e=>setEmail(e.target.value)} />
          <input className="input" placeholder="Password" type="password" value={password} onChange={e=>setPassword(e.target.value)} />
          <button className="button" disabled={loading} type="submit">{loading ? 'Loading...' : 'Login'}</button>
        </form>
        <div className="hr" />
        <button className="button secondary" onClick={onGoogle} disabled={loading}>Continue with Google</button>
        <p className="small">No account? <Link to="/register">Register</Link></p>
      </div>
    </div>
  )
}