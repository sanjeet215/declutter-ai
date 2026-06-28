import { useEffect, useState } from 'react'
import './App.css'

type EmailMetadata = {
  id: number
  gmailMessageId: string
  from: string | null
  subject: string | null
  receivedAt: string | null
  sizeEstimate: number | null
  labels: string[]
}

function App() {
  const [messages, setMessages] = useState<EmailMetadata[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadMessages = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await fetch('/api/gmail/stored', {
        headers: { Accept: 'application/json' },
      })
      const contentType = response.headers.get('content-type') ?? ''

      if (!response.ok || !contentType.includes('application/json')) {
        throw new Error('Connect Gmail before loading your stored messages.')
      }

      setMessages(await response.json())
    } catch (caught) {
      setMessages([])
      setError(caught instanceof Error ? caught.message : 'Unable to load messages.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadMessages()
  }, [])

  return (
    <main>
      <header className="topbar">
        <a className="brand" href="/">Declutter<span>AI</span></a>
        <a className="button secondary" href="http://localhost:8080/oauth2/authorization/google">
          Connect Gmail
        </a>
      </header>

      <section className="hero">
        <p className="eyebrow">A calmer inbox starts here</p>
        <h1>See what is filling your inbox.</h1>
        <p className="lede">
          Import email metadata safely, understand the noise, and make confident
          cleanup decisions without reading message bodies.
        </p>
        <div className="actions">
          <a className="button primary" href="http://localhost:8080/">
            Open sync controls
          </a>
          <button className="button secondary" onClick={() => void loadMessages()}>
            Refresh messages
          </button>
        </div>
      </section>

      <section className="summary" aria-label="Inbox summary">
        <article>
          <strong>{messages.length}</strong>
          <span>messages loaded</span>
        </article>
        <article>
          <strong>{new Set(messages.map((message) => message.from).filter(Boolean)).size}</strong>
          <span>unique senders</span>
        </article>
        <article>
          <strong>{formatBytes(messages.reduce((sum, message) => sum + (message.sizeEstimate ?? 0), 0))}</strong>
          <span>estimated size</span>
        </article>
      </section>

      <section className="inbox">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Stored metadata</p>
            <h2>Recent messages</h2>
          </div>
          <button className="text-button" onClick={() => void loadMessages()}>
            Refresh
          </button>
        </div>

        {loading && <div className="state">Loading stored messages…</div>}
        {!loading && error && (
          <div className="state error">
            <p>{error}</p>
            <a href="http://localhost:8080/oauth2/authorization/google">Connect Gmail</a>
          </div>
        )}
        {!loading && !error && messages.length === 0 && (
          <div className="state">No stored messages yet. Connect Gmail and run a sync.</div>
        )}
        {!loading && !error && messages.length > 0 && (
          <div className="message-list">
            {messages.map((message) => (
              <article className="message" key={message.id}>
                <div className="avatar">{senderInitial(message.from)}</div>
                <div className="message-copy">
                  <strong>{message.from ?? 'Unknown sender'}</strong>
                  <span>{message.subject || '(No subject)'}</span>
                </div>
                <div className="message-meta">
                  <time>{formatDate(message.receivedAt)}</time>
                  <span>{formatBytes(message.sizeEstimate ?? 0)}</span>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  )
}

function senderInitial(sender: string | null) {
  return sender?.trim().charAt(0).toUpperCase() || '?'
}

function formatDate(value: string | null) {
  if (!value) return 'Unknown date'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value))
}

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default App
