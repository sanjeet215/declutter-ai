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

type SenderMessageCount = {
  sender: string
  messageCount: number
}

type DomainMessageCount = {
  domain: string
  messageCount: number
}

type StorageBreakdown = {
  limitBytes: number | null
  usedBytes: number
  freeBytes: number | null
  driveBytes: number
  driveTrashBytes: number
  syncedMailBytes: number
  photosAndOtherBytes: number
}

type CsrfResponse = {
  headerName: string
  token: string
}

type DeleteResult = {
  deletedMessages: number
}

let csrf: CsrfResponse | null = null

const mutation = async (url: string, method: 'POST' | 'DELETE') => {
  if (!csrf) {
    const csrfResponse = await fetch('/api/auth/csrf', {
      headers: { Accept: 'application/json' },
    })
    if (!csrfResponse.ok) throw new Error('Unable to initialize a secure request.')
    csrf = await csrfResponse.json()
  }
  const csrfToken = csrf
  if (!csrfToken) throw new Error('Unable to initialize a secure request.')

  return fetch(url, {
    method,
    headers: {
      Accept: 'application/json',
      [csrfToken.headerName]: csrfToken.token,
    },
  })
}

const apiError = async (response: Response, fallback: string) => {
  try {
    const body = await response.json()
    if (typeof body.message === 'string') return body.message
  } catch {
    // The backend did not return a JSON error body.
  }
  if (response.status === 401) return 'Connect Gmail before using this action.'
  if (response.status === 403) {
    return 'Reconnect Gmail and approve permission to move messages to Trash.'
  }
  return fallback
}

function App() {
  const [messages, setMessages] = useState<EmailMetadata[]>([])
  const [senders, setSenders] = useState<SenderMessageCount[]>([])
  const [domains, setDomains] = useState<DomainMessageCount[]>([])
  const [storage, setStorage] = useState<StorageBreakdown | null>(null)
  const [storageError, setStorageError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [deletingSender, setDeletingSender] = useState<string | null>(null)
  const [deletingDomain, setDeletingDomain] = useState<string | null>(null)
  const [deletingMessageId, setDeletingMessageId] = useState<number | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [deleteSuccess, setDeleteSuccess] = useState<string | null>(null)

  const loadMessages = async () => {
    setLoading(true)
    setError(null)

    try {
      const response = await fetch('/api/gmail/stored?limit=100', {
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

  const loadSenders = async () => {
    try {
      const response = await fetch('/api/emails/senders', {
        headers: { Accept: 'application/json' },
      })
      if (response.ok) {
        setSenders(await response.json())
      }
    } catch {
      setSenders([])
    }
  }

  const loadDomains = async () => {
    try {
      const response = await fetch('/api/emails/domains', {
        headers: { Accept: 'application/json' },
      })
      if (response.ok) setDomains(await response.json())
    } catch {
      setDomains([])
    }
  }

  const loadStorage = async () => {
    setStorageError(null)
    try {
      const response = await fetch('/api/storage', {
        headers: { Accept: 'application/json' },
      })
      if (!response.ok) {
        throw new Error(await apiError(response, 'Storage details are unavailable.'))
      }
      setStorage(await response.json())
    } catch (caught) {
      setStorage(null)
      setStorageError(caught instanceof Error ? caught.message : 'Storage details are unavailable.')
    }
  }

  const syncMessages = async () => {
    setSyncing(true)
    setError(null)
    try {
      const response = await mutation('/api/gmail/sync?maxResults=100', 'POST')
      if (!response.ok) {
        throw new Error(await apiError(response, 'Unable to sync messages from Gmail.'))
      }
      await Promise.all([loadMessages(), loadSenders(), loadDomains(), loadStorage()])
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to sync messages.')
    } finally {
      setSyncing(false)
    }
  }

  const deleteDomainMessages = async (domain: string, messageCount: number) => {
    if (!window.confirm(
      `Move every Gmail message from @${domain} and its subdomains to Trash? ` +
      `This matches ${domain} and *.${domain}. ` +
      `${messageCount} ${messageCount === 1 ? 'message is' : 'messages are'} currently synced.`,
    )) return

    setDeletingDomain(domain)
    setDeleteError(null)
    setDeleteSuccess(null)
    try {
      const response = await mutation(
        `/api/emails/domains?${new URLSearchParams({ domain })}`,
        'DELETE',
      )
      if (!response.ok) {
        throw new Error(await apiError(response, `Unable to delete messages from ${domain}.`))
      }
      const result: DeleteResult = await response.json()
      setDeleteSuccess(
        `Moved ${result.deletedMessages} ${result.deletedMessages === 1 ? 'message' : 'messages'} from ${domain} to Trash.`,
      )
      await Promise.all([loadMessages(), loadSenders(), loadDomains(), loadStorage()])
    } catch (caught) {
      setDeleteError(caught instanceof Error ? caught.message : 'Unable to delete messages.')
    } finally {
      setDeletingDomain(null)
    }
  }

  const deleteSenderMessages = async (sender: string, messageCount: number) => {
    if (!window.confirm(
      `Move every Gmail message from ${sender} to Trash? ` +
      `${messageCount} ${messageCount === 1 ? 'message is' : 'messages are'} currently synced.`,
    )) return

    setDeletingSender(sender)
    setDeleteError(null)
    setDeleteSuccess(null)
    try {
      const response = await mutation(
        `/api/emails/senders?${new URLSearchParams({ sender })}`,
        'DELETE',
      )
      if (!response.ok) {
        throw new Error(await apiError(response, 'Unable to delete messages from this sender.'))
      }
      const result: DeleteResult = await response.json()
      setDeleteSuccess(
        `Moved ${result.deletedMessages} ${result.deletedMessages === 1 ? 'message' : 'messages'} to Trash.`,
      )
      await Promise.all([loadMessages(), loadSenders(), loadDomains(), loadStorage()])
    } catch (caught) {
      setDeleteError(caught instanceof Error ? caught.message : 'Unable to delete messages.')
    } finally {
      setDeletingSender(null)
    }
  }

  const deleteMessage = async (message: EmailMetadata) => {
    if (!window.confirm(`Move "${message.subject || '(No subject)'}" to Gmail Trash?`)) return

    setDeletingMessageId(message.id)
    setDeleteError(null)
    setDeleteSuccess(null)
    try {
      const response = await mutation(`/api/emails/${message.id}`, 'DELETE')
      if (!response.ok) {
        throw new Error(await apiError(response, 'Unable to move this message to Trash.'))
      }
      setDeleteSuccess('Moved 1 message to Trash.')
      await Promise.all([loadMessages(), loadSenders(), loadDomains(), loadStorage()])
    } catch (caught) {
      setDeleteError(caught instanceof Error ? caught.message : 'Unable to delete message.')
    } finally {
      setDeletingMessageId(null)
    }
  }

  useEffect(() => {
    void loadMessages()
    void loadSenders()
    void loadDomains()
    void loadStorage()
  }, [])

  return (
    <main>
      <header className="topbar">
        <a className="brand" href="/">Declutter<span>AI</span></a>
        <a className="button secondary" href="/oauth2/authorization/google">
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
          <button className="button primary" disabled={syncing} onClick={() => void syncMessages()}>
            {syncing ? 'Syncing…' : 'Sync latest 100'}
          </button>
          <button className="button secondary" onClick={() => {
            void loadMessages()
            void loadSenders()
            void loadDomains()
            void loadStorage()
          }}>
            Refresh messages
          </button>
        </div>
      </section>

      <div className="overview-grid">
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
            <span>synced mail size</span>
          </article>
        </section>
        <StorageCard storage={storage} error={storageError} />
      </div>

      {deleteSuccess && <p className="inline-success" role="status">{deleteSuccess}</p>}

      {senders.length > 0 && (
        <section className="senders">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Sender breakdown</p>
              <h2>Messages by sender</h2>
            </div>
          </div>
          <div className="sender-list">
            {senders.map(({ sender, messageCount }) => (
              <article className="sender-row" key={sender}>
                <div className="avatar">{senderInitial(sender)}</div>
                <strong>{sender}</strong>
                <div className="sender-actions">
                  <span>{messageCount} {messageCount === 1 ? 'message' : 'messages'}</span>
                  <button
                    className="delete-button"
                    disabled={deletingSender !== null}
                    onClick={() => void deleteSenderMessages(sender, messageCount)}
                  >
                    {deletingSender === sender ? 'Moving…' : 'Move all to Trash'}
                  </button>
                </div>
              </article>
            ))}
          </div>
          {deleteError && <p className="inline-error" role="alert">{deleteError}</p>}
        </section>
      )}

      {domains.length > 0 && (
        <section className="domains">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Domain breakdown</p>
              <h2>Messages by domain</h2>
            </div>
          </div>
          <div className="sender-list">
            {domains.map(({ domain, messageCount }) => (
              <article className="sender-row" key={domain}>
                <div className="avatar">@</div>
                <strong>{domain}</strong>
                <div className="sender-actions">
                  <span>{messageCount} {messageCount === 1 ? 'message' : 'messages'}</span>
                  <button
                    className="delete-button"
                    disabled={deletingDomain !== null || deletingSender !== null}
                    onClick={() => void deleteDomainMessages(domain, messageCount)}
                  >
                    {deletingDomain === domain ? 'Moving…' : 'Move all to Trash'}
                  </button>
                </div>
              </article>
            ))}
          </div>
          {deleteError && <p className="inline-error" role="alert">{deleteError}</p>}
        </section>
      )}

      <section className="inbox">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Stored metadata</p>
            <h2>Recent messages</h2>
          </div>
          <button className="text-button" onClick={() => {
            void loadMessages()
            void loadSenders()
            void loadDomains()
          }}>
            Refresh
          </button>
        </div>

        {loading && <div className="state">Loading stored messages…</div>}
        {!loading && error && (
          <div className="state error">
            <p>{error}</p>
            <a href="/oauth2/authorization/google">Connect Gmail</a>
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
                  <button
                    className="message-delete-button"
                    disabled={deletingMessageId !== null || deletingSender !== null}
                    onClick={() => void deleteMessage(message)}
                  >
                    {deletingMessageId === message.id ? 'Moving…' : 'Delete'}
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
        {deleteError && <p className="inline-error" role="alert">{deleteError}</p>}
      </section>
    </main>
  )
}

function senderInitial(sender: string | null) {
  return sender?.trim().charAt(0).toUpperCase() || '?'
}

function StorageCard({ storage, error }: { storage: StorageBreakdown | null; error: string | null }) {
  if (!storage) {
    return (
      <section className="storage-card">
        <p className="eyebrow">Google storage</p>
        <h2>Storage overview</h2>
        <p className="storage-unavailable">{error ?? 'Loading storage…'}</p>
      </section>
    )
  }

  const total = storage.limitBytes ?? storage.usedBytes
  const percent = (value: number) => total > 0 ? (value / total) * 100 : 0
  const mailEnd = percent(storage.syncedMailBytes)
  const driveEnd = mailEnd + percent(storage.driveBytes)
  const otherEnd = driveEnd + percent(storage.photosAndOtherBytes)
  const chart = {
    background: `conic-gradient(
      #367a50 0 ${mailEnd}%,
      #79a98a ${mailEnd}% ${driveEnd}%,
      #d2a95f ${driveEnd}% ${otherEnd}%,
      #e6ece7 ${otherEnd}% 100%
    )`,
  }

  return (
    <section className="storage-card">
      <p className="eyebrow">Google storage</p>
      <div className="storage-content">
        <div className="donut" style={chart}>
          <div>
            <strong>{formatBytes(storage.usedBytes)}</strong>
            <span>of {storage.limitBytes ? formatBytes(storage.limitBytes) : 'unlimited'}</span>
          </div>
        </div>
        <ul className="storage-legend">
          <StorageLegend color="mail" label="Synced mail" bytes={storage.syncedMailBytes} />
          <StorageLegend color="drive" label="Drive" bytes={storage.driveBytes} />
          <StorageLegend color="other" label="Photos & other" bytes={storage.photosAndOtherBytes} />
          <StorageLegend color="free" label="Free" bytes={storage.freeBytes ?? 0} />
        </ul>
      </div>
    </section>
  )
}

function StorageLegend({ color, label, bytes }: { color: string; label: string; bytes: number }) {
  return (
    <li>
      <i className={color} />
      <span>{label}</span>
      <strong>{formatBytes(bytes)}</strong>
    </li>
  )
}

function formatDate(value: string | null) {
  if (!value) return 'Unknown date'
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value))
}

function formatBytes(bytes: number) {
  if (bytes === 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  if (bytes < 1024 ** 4) return `${(bytes / (1024 ** 3)).toFixed(1)} GB`
  return `${(bytes / (1024 ** 4)).toFixed(1)} TB`
}

export default App
