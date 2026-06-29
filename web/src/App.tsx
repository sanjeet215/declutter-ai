import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
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

type Rule = { id: number; name: string; matchField: string; matchValue: string; category: string; comment: string; canDelete: boolean; subjectContains?: string; senderContains?: string; olderThanDays?: number }
type RulesResponse = { baseRules: Rule[]; userRules: Rule[]; autoDeleteRecommended: boolean }
const EMAIL_CATEGORIES = [
  'Transaction Alert',
  'Finance',
  'Security Alert',
  'Promotion',
  'Newsletter',
  'Shopping',
  'Travel',
  'Social',
  'Personal',
  'Work',
  'Automated',
  'General',
] as const
type SenderDetail = {
  sender: string
  messageCount: number
  messages: Array<{ id: number; subject: string | null; receivedAt: string | null; category: string | null; comment: string | null; canDelete: boolean; matchedRule: string | null }>
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

const mutation = async (url: string, method: 'POST' | 'PUT' | 'DELETE', body?: unknown) => {
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
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      [csrfToken.headerName]: csrfToken.token,
    },
    body: body ? JSON.stringify(body) : undefined,
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
  const classificationPage = window.location.pathname === '/classification'
  const senderDetailPage = window.location.pathname === '/sender'
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
  const [rules, setRules] = useState<RulesResponse | null>(null)
  const [ruleMessage, setRuleMessage] = useState<string | null>(null)
  const [ruleError, setRuleError] = useState<string | null>(null)

  const loadRules = async () => {
    const response = await fetch('/api/rules', { headers: { Accept: 'application/json' } })
    if (response.ok) setRules(await response.json())
  }

  const setAutoDelete = async (enabled: boolean) => {
    const response = await mutation('/api/rules/settings/auto-delete', 'PUT', { enabled })
    if (response.ok) setRules((current) => current ? { ...current, autoDeleteRecommended: enabled } : current)
  }

  const createRule = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const formElement = event.currentTarget
    const form = new FormData(formElement)
    setRuleMessage(null)
    setRuleError(null)
    const optional = (name: string) => {
      const value = form.get(name)?.toString().trim()
      return value || null
    }
    const age = optional('olderThanDays')
    const additionalConditions = [
      { field: optional('condition2Field'), value: optional('condition2Value') },
    ]
    const conditionValue = (field: string) =>
      additionalConditions.find((condition) => condition.field === field)?.value ?? null
    const response = await mutation('/api/rules', 'POST', {
      name: form.get('name'), matchField: form.get('matchField'),
      matchValue: form.get('matchValue'), category: form.get('category'),
      comment: form.get('comment'), canDelete: form.get('canDelete') === 'true', priority: 500,
      subjectContains: conditionValue('SUBJECT'),
      senderContains: conditionValue('SENDER'),
      olderThanDays: form.get('ageOperator') === 'OLDER_THAN' && age ? Number(age) : null,
    })
    if (!response.ok) {
      setRuleError(await apiError(response, 'Unable to add the rule.'))
      return
    }
    formElement.reset()
    await loadRules()
    setRuleMessage('Rule added successfully.')
  }

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
    if (classificationPage) void loadRules()
    else if (!senderDetailPage) void loadSenders(senderPage)
  }, [senderPage, classificationPage, senderDetailPage])

  useEffect(() => {
    if (!syncing) return
    const timer = window.setInterval(() => {
      setSyncSeconds((seconds) => seconds + 1)
    }, 1000)
    return () => window.clearInterval(timer)
  }, [syncing])

  if (classificationPage) {
    return (
      <main>
        <Topbar active="classification" />
        <section className="hero classification-hero">
          <p className="eyebrow">Classification</p>
          <h1>Teach your inbox what matters.</h1>
          <p className="lede">Create rules that categorize messages, explain the recommendation, and decide whether they can be deleted.</p>
        </section>
        <ClassificationPage rules={rules} setAutoDelete={setAutoDelete}
          createRule={createRule} ruleMessage={ruleMessage} ruleError={ruleError} />
      </main>
    )
  }

  if (senderDetailPage) {
    return <main><Topbar active="mailbox" /><SenderDetailPage /></main>
  }

  return (
    <main>
      <Topbar active="mailbox" />

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
                  <strong>
                    <a className="sender-link" href={`/sender?${new URLSearchParams({ sender })}`}>
                      {sender}
                    </a>
                  </strong>
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

function Topbar({ active }: { active: 'mailbox' | 'classification' }) {
  return (
    <header className="topbar">
      <a className="brand" href="/">Declutter<span>AI</span></a>
      <nav className="topnav">
        <a className={active === 'mailbox' ? 'active' : ''} href="/">Mailbox</a>
        <a className={active === 'classification' ? 'active' : ''} href="/classification">Classification</a>
        <a className="button secondary" href="/oauth2/authorization/google">Connect Gmail</a>
      </nav>
    </header>
  )
}

function ClassificationPage({ rules, setAutoDelete, createRule, ruleMessage, ruleError }: {
  rules: RulesResponse | null
  setAutoDelete: (enabled: boolean) => Promise<void>
  createRule: (event: FormEvent<HTMLFormElement>) => Promise<void>
  ruleMessage: string | null
  ruleError: string | null
}) {
  return (
    <section className="classification-panel">
      <div className="section-heading"><div><p className="eyebrow">Rule engine</p><h2>Classification rules</h2></div></div>
        <label className="rule-toggle">
          <input type="checkbox" checked={rules?.autoDeleteRecommended ?? false}
            onChange={(event) => void setAutoDelete(event.target.checked)} />
          Automatically trash messages recommended for deletion
        </label>
        <p className="rule-warning">Off by default. When enabled, matching mail is moved to Gmail Trash during sync.</p>
        <form className="rule-form" onSubmit={(event) => void createRule(event)}>
          <input name="name" placeholder="Rule name" required />
          <select name="category" defaultValue="" required>
            <option value="" disabled>Select category</option>
            {EMAIL_CATEGORIES.map((category) => (
              <option key={category} value={category}>{category}</option>
            ))}
          </select>
          <select name="matchField"><option value="SENDER">Sender contains</option><option value="DOMAIN">Sender domain</option><option value="SUBJECT">Subject contains</option><option value="LABEL">Gmail label</option></select>
          <input name="matchValue" placeholder="Match value" required />
          <select name="condition2Field" defaultValue="SUBJECT">
            <option value="">No additional condition</option>
            <option value="SUBJECT">AND subject contains</option>
            <option value="SENDER">AND sender contains</option>
          </select>
          <input name="condition2Value" placeholder="Condition text (optional)" />
          <select name="ageOperator" defaultValue="ANY">
            <option value="ANY">Any message age</option>
            <option value="OLDER_THAN">Message older than</option>
          </select>
          <select name="olderThanDays" defaultValue="30">
            <option value="7">7 days</option>
            <option value="15">15 days</option>
            <option value="30">30 days</option>
            <option value="60">60 days</option>
            <option value="90">90 days</option>
            <option value="180">6 months</option>
            <option value="365">1 year</option>
          </select>
          <input name="comment" placeholder="Comment shown for matches" required />
          <select name="canDelete" defaultValue="false">
            <option value="false">Keep / review</option>
            <option value="true">Can delete</option>
          </select>
          <button className="button primary rule-submit" type="submit">Add rule</button>
        </form>
        {ruleMessage && <p className="inline-success" role="status">{ruleMessage}</p>}
        {ruleError && <p className="inline-error" role="alert">{ruleError}</p>}
        <div className="rule-list">
          {[...(rules?.userRules ?? []), ...(rules?.baseRules ?? [])].map((rule) => (
            <article key={`${rule.id}-${rule.name}`}>
              <strong>{rule.name}</strong><span>{rule.matchField}: {rule.matchValue}</span>
              {rule.subjectContains && <span>AND subject contains: {rule.subjectContains}</span>}
              {rule.senderContains && <span>AND sender contains: {rule.senderContains}</span>}
              {rule.olderThanDays && <span>AND older than: {rule.olderThanDays} days</span>}
              <span>{rule.category} · {rule.canDelete ? 'Can delete' : 'Keep/review'}</span>
              <p>{rule.comment}</p>
            </article>
          ))}
        </div>
    </section>
  )
}

function SenderDetailPage() {
  const sender = new URLSearchParams(window.location.search).get('sender') ?? ''
  const [detail, setDetail] = useState<SenderDetail | null>(null)
  const [detailError, setDetailError] = useState<string | null>(null)

  useEffect(() => {
    const load = async () => {
      const response = await fetch(`/api/emails/sender-details?${new URLSearchParams({ sender })}`, {
        headers: { Accept: 'application/json' },
      })
      if (!response.ok) {
        setDetailError(await apiError(response, 'Unable to load sender details.'))
        return
      }
      setDetail(await response.json())
    }
    void load()
  }, [sender])

  return (
    <>
      <section className="hero detail-hero">
        <p className="eyebrow">Sender details</p>
        <h1>{sender || 'Unknown sender'}</h1>
        <p className="lede">{detail ? `${detail.messageCount.toLocaleString()} stored messages` : 'Loading classification details…'}</p>
        <a className="text-button back-link" href="/">← Back to senders</a>
      </section>
      {detailError && <p className="inline-error">{detailError}</p>}
      <section className="classification-list">
        {detail?.messages.map((message) => (
          <article key={message.id}>
            <div className="classification-heading">
              <div><strong>{message.subject || '(No subject)'}</strong><span>{formatDate(message.receivedAt)}</span></div>
              <span className={`recommendation ${message.canDelete ? 'delete' : 'keep'}`}>
                {message.canDelete ? 'Can delete' : 'Keep / review'}
              </span>
            </div>
            <dl>
              <div><dt>Category</dt><dd>{message.category || 'Uncategorized'}</dd></div>
              <div><dt>Matched rule</dt><dd>{message.matchedRule || 'No matching rule'}</dd></div>
            </dl>
            <p>{message.comment || 'No classification comment available.'}</p>
          </article>
        ))}
      </section>
    </>
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

function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(value)) : 'Unknown date'
}

export default App
