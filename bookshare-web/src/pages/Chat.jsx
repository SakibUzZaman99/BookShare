import React from 'react'
import { useParams } from 'react-router-dom'
import { auth, db } from '../firebase'
import { collection, query, orderBy, onSnapshot, addDoc } from 'firebase/firestore'

export default function Chat() {
  const { requestId } = useParams()
  const [messages, setMessages] = React.useState([])
  const [text, setText] = React.useState('')
  const user = auth.currentUser

  React.useEffect(()=>{
    if (!requestId) return
    const q = query(collection(db, 'requests', requestId, 'messages'), orderBy('timestamp', 'asc'))
    const unsub = onSnapshot(q, snap => {
      setMessages(snap.docs.map(d => d.data()))
    })
    return () => unsub()
  }, [requestId])

  async function send() {
    const t = text.trim()
    if (!t) return
    await addDoc(collection(db, 'requests', requestId, 'messages'), {
      senderId: user.uid,
      text: t,
      timestamp: Date.now()
    })
    setText('')
  }

  return (
    <div className="container" style={{maxWidth:720}}>
      <div className="card" style={{minHeight: 300}}>
        <div className="list">
          {messages.map((m, i) => (
            <div key={i} style={{ display:'flex', justifyContent: m.senderId===user.uid ? 'flex-end' : 'flex-start' }}>
              <div className="card" style={{background: m.senderId===user.uid ? 'var(--primary)' : '#f3f4f6', color: m.senderId===user.uid ? 'white' : 'inherit'}}>
                {m.text}
              </div>
            </div>
          ))}
        </div>
        <div className="row" style={{marginTop: 12}}>
          <input className="input" value={text} onChange={e=>setText(e.target.value)} placeholder="Type a message..." />
          <button className="button" onClick={send}>Send</button>
        </div>
      </div>
    </div>
  )
}