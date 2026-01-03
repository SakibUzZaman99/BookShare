import React from 'react'
import Tesseract from 'tesseract.js'

export default function Ocr() {
  const [file, setFile] = React.useState(null)
  const [text, setText] = React.useState('')
  const [progress, setProgress] = React.useState(0)
  const [lang, setLang] = React.useState('eng')

  async function run() {
    if (!file) return
    setText('')
    setProgress(0)
    const { data } = await Tesseract.recognize(file, lang, {
      logger: m => {
        if (m.status === 'recognizing text') setProgress(Math.round(m.progress*100))
      }
    })
    setText(data.text)
  }

  return (
    <div className="container" style={{maxWidth: 720}}>
      <div className="card">
        <h2>OCR (Tesseract.js)</h2>
        <div className="row">
          <input type="file" accept="image/*" onChange={e=>setFile(e.target.files?.[0] || null)} />
          <select className="input" value={lang} onChange={e=>setLang(e.target.value)} style={{maxWidth:140}}>
            <option value="eng">English</option>
            <option value="ben">Bengali</option>
          </select>
          <button className="button" onClick={run} disabled={!file}>Extract Text</button>
        </div>
        {progress>0 && progress<100 && <div className="small">Progress: {progress}%</div>}
        {text && <div style={{marginTop:12}}><strong>Result</strong><pre className="code">{text}</pre></div>}
      </div>
    </div>
  )
}