import { useEffect, useState } from 'react'
import './App.css'

type SenderMessageCount = {
  sender: string
  messageCount: number
}

type SenderPage = {
  senders: SenderMessageCount[]
  page: number
  totalPages: number
  totalSenders: number
  hasNext: boolean
  hasPrevious: boolean
}

type CsrfResponse = {
  headerName: string
  token: string
}

type DeleteResult = {
  deletedMessages: number
}

type SyncStatus = {
  state: 'IDLE' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  processed: number
  created: number
  updated: number
  total: number
  error: string | null
}

let csrf: CsrfResponse | null = null

const mutation = async (url: string, method: 'POST' | 'DELETE') => {
  if (!csrf) {
    const response = await fetch('/api/auth/csrf', {
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) throw new Error('Unable to initialize a secure request.')
    csrf = await response.json()
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
  if (response.status === 403) return 'Reconnect Gmail and approve Gmail access.'
  return fallback
}

function App() {
  const [senders, setSenders] = useState<SenderMessageCount[]>([])
  const [senderPage, setSenderPage] = useState(0)
  const [senderPageInfo, setSenderPageInfo] = useState<SenderPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [syncSeconds, setSyncSeconds] = useState(0)
  const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null)
  const [deletingSender, setDeletingSender] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  const loadSenders = async (page = senderPage) => {
    setLoading(true)
    setError(null)
    try {
      const response = await fetch(`/api/emails/senders?${new URLSearchParams({
        page: page.toString(),
        size: '15',
      })}`, {
        headers: { Accept: 'application/json' },
      })
      if (!response.ok) {
        throw new Error(await apiError(response, 'Unable to load sender counts.'))
      }
      const result: SenderPage = await response.json()
      setSenders(result.senders)
      setSenderPageInfo(result)
    } catch (caught) {
      setSenders([])
      setError(caught instanceof Error ? caught.message : 'Unable to load sender counts.')
    } finally {
      setLoading(false)
    }
  }

  const syncMailbox = async () => {
    setSyncing(true)
    setSyncSeconds(0)
    setSyncStatus(null)
    setError(null)
    setSuccess(null)
    try {
      const response = await mutation('/api/gmail/sync', 'POST')
      if (!response.ok) {
        throw new Error(await apiError(response, 'Unable to sync the Gmail mailbox.'))
      }
      const initialStatus: SyncStatus = await response.json()
      setSyncStatus(initialStatus)
      const result = await waitForSync()
      if (result.state === 'FAILED') {
        throw new Error(result.error ?? 'Mailbox sync failed.')
      }
      setSuccess(`Synced ${result.processed} messages from Gmail.`)
      await loadSenders(senderPage)
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to sync the Gmail mailbox.')
    } finally {
      setSyncing(false)
    }
  }

  const waitForSync = async (): Promise<SyncStatus> => {
    while (true) {
      await new Promise((resolve) => window.setTimeout(resolve, 1000))
      const response = await fetch('/api/gmail/sync/status', {
        headers: { Accept: 'application/json' },
      })
      if (!response.ok) {
        throw new Error(await apiError(response, 'Unable to read mailbox sync progress.'))
      }
      const status: SyncStatus = await response.json()
      setSyncStatus(status)
      if (status.state !== 'RUNNING') return status
    }
  }

  const deleteSenderMessages = async (sender: string, messageCount: number) => {
    if (!window.confirm(
      `Move every Gmail message from ${sender} to Trash? ` +
      `${messageCount} ${messageCount === 1 ? 'message is' : 'messages are'} currently synced.`,
    )) return

    setDeletingSender(sender)
    setError(null)
    setSuccess(null)
    try {
      const response = await mutation(
        `/api/emails/senders?${new URLSearchParams({ sender })}`,
        'DELETE',
      )
      if (!response.ok) {
        throw new Error(await apiError(response, 'Unable to delete messages from this sender.'))
      }
      const result: DeleteResult = await response.json()
      setSuccess(`Moved ${result.deletedMessages} messages to Trash.`)
      if (senders.length === 1 && senderPage > 0) {
        setSenderPage((page) => page - 1)
      } else {
        await loadSenders(senderPage)
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to delete messages.')
    } finally {
      setDeletingSender(null)
    }
  }

  useEffect(() => {
    void loadSenders(senderPage)
  }, [senderPage])

  useEffect(() => {
    if (!syncing) return
    const timer = window.setInterval(() => {
      setSyncSeconds((seconds) => seconds + 1)
    }, 1000)
    return () => window.clearInterval(timer)
  }, [syncing])

  return (
    <main>
      <header className="topbar">
        <a className="brand" href="/">Declutter<span>AI</span></a>
        <a className="button secondary" href="/oauth2/authorization/google">Connect Gmail</a>
      </header>

      <section className="hero">
        <p className="eyebrow">Mailbox overview</p>
        <h1>Messages by sender.</h1>
        <p className="lede">
          Sync mailbox metadata, then see how many messages came from each sender.
          Message bodies and attachments are not stored.
        </p>
        <div className="actions">
          <button className="button primary" disabled={syncing} onClick={() => void syncMailbox()}>
            {syncing ? 'Syncing full mailbox…' : 'Sync full mailbox'}
          </button>
          <button className="button secondary" disabled={loading} onClick={() => void loadSenders(senderPage)}>
            Refresh counts
          </button>
        </div>
      </section>

      {(syncing || syncStatus) && (
        <section className={`sync-status ${
          syncing ? 'is-syncing' : syncStatus?.state === 'FAILED' ? 'is-failed' : 'is-complete'
        }`} aria-live="polite">
          <div className="sync-indicator" aria-hidden="true">
            {syncing
              ? <span className="sync-spinner" />
              : <span className="sync-check">{syncStatus?.state === 'FAILED' ? '!' : '✓'}</span>}
          </div>
          <div className="sync-copy">
            <strong>
              {syncing
                ? 'Sync in progress'
                : syncStatus?.state === 'FAILED' ? 'Mailbox sync failed' : 'Mailbox sync complete'}
            </strong>
            <span>
              {syncing
                ? 'Reading Gmail metadata page by page. Large mailboxes can take a few minutes.'
                : `${syncStatus?.processed.toLocaleString()} messages processed.`}
            </span>
          </div>
          <div className="sync-numbers">
            {syncing ? (
              <>
                <SyncMetric value={formatDuration(syncSeconds)} label="elapsed" />
                <SyncMetric
                  value={`${(syncStatus?.processed ?? 0).toLocaleString()} / ${(syncStatus?.total || 0).toLocaleString()}`}
                  label="processed"
                />
                <SyncMetric value={(syncStatus?.created ?? 0).toLocaleString()} label="new" />
              </>
            ) : (
              <>
                <SyncMetric value={(syncStatus?.processed ?? 0).toLocaleString()} label="processed" />
                <SyncMetric value={(syncStatus?.created ?? 0).toLocaleString()} label="new" />
                <SyncMetric value={(syncStatus?.updated ?? 0).toLocaleString()} label="updated" />
              </>
            )}
          </div>
          {syncing && (
            <div className="sync-progress" aria-hidden="true">
              <span
                className={syncStatus?.total ? 'is-determinate' : ''}
                style={syncStatus?.total
                  ? { width: `${Math.min(100, (syncStatus.processed / syncStatus.total) * 100)}%` }
                  : undefined}
              />
            </div>
          )}
        </section>
      )}

      {success && <p className="inline-success" role="status">{success}</p>}
      {error && <p className="inline-error" role="alert">{error}</p>}

      <section className="senders">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Sender breakdown</p>
            <h2>Sender vs count</h2>
          </div>
        </div>

        {loading && <div className="state">Loading sender counts…</div>}
        {!loading && !error && senders.length === 0 && (
          <div className="state">No stored messages yet. Connect Gmail and sync the mailbox.</div>
        )}
        {!loading && senders.length > 0 && (
          <>
            <div className="sender-list">
              {senders.map(({ sender, messageCount }) => (
                <article className="sender-row" key={sender}>
                  <div className="avatar">{senderInitial(sender)}</div>
                  <strong>{sender}</strong>
                  <div className="sender-actions">
                    <span>{messageCount}</span>
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
            <nav className="pagination" aria-label="Sender pages">
              <button
                className="button secondary"
                disabled={loading || !senderPageInfo?.hasPrevious}
                onClick={() => setSenderPage((page) => page - 1)}
              >
                Previous
              </button>
              <span>
                Page {senderPage + 1} of {senderPageInfo?.totalPages ?? 1}
                {' · '}{senderPageInfo?.totalSenders.toLocaleString()} senders
              </span>
              <button
                className="button secondary"
                disabled={loading || !senderPageInfo?.hasNext}
                onClick={() => setSenderPage((page) => page + 1)}
              >
                Next 15
              </button>
            </nav>
          </>
        )}
      </section>
    </main>
  )
}

function senderInitial(sender: string) {
  return sender.trim().charAt(0).toUpperCase() || '?'
}

function SyncMetric({ value, label }: { value: string; label: string }) {
  return (
    <div>
      <strong>{value}</strong>
      <span>{label}</span>
    </div>
  )
}

function formatDuration(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return minutes > 0
    ? `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`
    : `${remainingSeconds}s`
}

export default App
