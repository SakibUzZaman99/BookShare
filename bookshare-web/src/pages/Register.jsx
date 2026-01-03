import React from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { auth } from '../firebase'
import { createUserWithEmailAndPassword, sendEmailVerification } from 'firebase/auth'

export default function Register() {
  const [email, setEmail] = React.useState('')
  const [password, setPassword] = React.useState('')
  const [loading, setLoading] = React.useState(false)
  const nav = useNavigate()

  async function onRegister(e) {
    e.preventDefault()
    try {
      setLoading(true)
      const cred = await createUserWithEmailAndPassword(auth, email, password)
      await sendEmailVerification(cred.user)
      alert('Verification email sent. Please verify before logging in.')
      nav('/login', { replace: true })
    } catch (e) {
      alert(e.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container" style={{maxWidth: 440, paddingTop: 60}}>
      <div className="card">
        <h2>Create Account</h2>
        <form onSubmit={onRegister}>
          <input className="input" placeholder="Email" value={email} onChange={e=>setEmail(e.target.value)} />
          <input className="input" placeholder="Password (min 6 chars)" type="password" value={password} onChange={e=>setPassword(e.target.value)} />
          <button className="button" disabled={loading} type="submit">{loading ? 'Loading...' : 'Register'}</button>
        </form>
        <p className="small">Already have an account? <Link to="/login">Login</Link></p>
      </div>
    </div>
  )
}