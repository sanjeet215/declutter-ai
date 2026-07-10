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

type Decision = 'KEEP_IT' | 'SAFE_TO_DELETE' | 'REVIEW'
type Rule = { id: number; name: string; matchField: string; matchValue: string; category: string; comment: string; canDelete: boolean; decision: Decision; subjectContains?: string; senderContains?: string; olderThanDays?: number }
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
  safeToDeleteCount: number
  keepCount: number
  reviewCount: number
  recoverableBytes: number
  page: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
  messages: Array<{ id: number; subject: string | null; receivedAt: string | null; sizeEstimate: number | null; category: string | null; comment: string | null; canDelete: boolean; decision: Decision; matchedRule: string | null }>
}

type DriveFile = {
  id: string
  name: string
  mimeType: string
  size: string | null
  quotaBytesUsed: string | null
  modifiedTime: string | null
  webViewLink: string | null
}

type UntitledFilesReport = {
  files: DriveFile[]
  fileCount: number
  recoverableBytes: number
}

type DriveFileListPage = UntitledFilesReport & {
  nextPageToken: string | null
  hasPrevious: boolean
}

type DriveAccessStatus = {
  hasDriveAccess: boolean
  hasDriveReadonly: boolean
  hasDriveMetadataReadonly: boolean
}

type PhotosAccessStatus = {
  hasPhotosAccess: boolean
}

type PhotosMediaItem = {
  id: string
  productUrl: string | null
  baseUrl: string | null
  mimeType: string
  filename: string | null
  mediaMetadata: { creationTime: string | null; width: string | null; height: string | null } | null
}

type PhotosMediaPage = {
  mediaItems: PhotosMediaItem[]
  itemCount: number
  nextPageToken: string | null
  hasPrevious: boolean
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
  const drivePage = window.location.pathname === '/drive'
  const driveUntitledPage = window.location.pathname === '/drive/untitled'
  const driveFilesPage = window.location.pathname === '/drive/files'
  const photosPage = window.location.pathname === '/photos'
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
  const [editingRule, setEditingRule] = useState<Rule | null>(null)

  const loadRules = async () => {
    const response = await fetch('/api/rules', { headers: { Accept: 'application/json' } })
    if (response.ok) setRules(await response.json())
  }

  const setAutoDelete = async (enabled: boolean) => {
    const response = await mutation('/api/rules/settings/auto-delete', 'PUT', { enabled })
    if (response.ok) setRules((current) => current ? { ...current, autoDeleteRecommended: enabled } : current)
  }

  const deleteRule = async (rule: Rule) => {
    if (!window.confirm(`Delete the rule "${rule.name}"?`)) return
    setRuleMessage(null)
    setRuleError(null)
    try {
      const response = await mutation(`/api/rules/${rule.id}`, 'DELETE')
      if (!response.ok) {
        setRuleError(await apiError(response, 'Unable to delete the rule.'))
        return
      }
      await loadRules()
      setRuleMessage('Rule deleted successfully.')
    } catch (caught) {
      setRuleError(caught instanceof Error ? caught.message : 'Unable to delete the rule.')
    }
  }

  const reapplyRules = async () => {
    setRuleMessage(null)
    setRuleError(null)
    try {
      const response = await mutation('/api/rules/reapply', 'POST')
      if (!response.ok) {
        setRuleError(await apiError(response, 'Unable to reapply rules.'))
        return
      }
      const result = await response.json()
      setRuleMessage(`Reclassified ${result.reclassifiedMessages.toLocaleString()} messages.`)
    } catch (caught) {
      setRuleError(caught instanceof Error ? caught.message : 'Unable to reapply rules.')
    }
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
    const response = await mutation(
      editingRule ? `/api/rules/${editingRule.id}` : '/api/rules',
      editingRule ? 'PUT' : 'POST', {
      name: form.get('name'), matchField: form.get('matchField'),
      matchValue: form.get('matchValue'), category: form.get('category'),
      comment: form.get('comment'), decision: form.get('decision'), priority: 500,
      subjectContains: conditionValue('SUBJECT'),
      senderContains: conditionValue('SENDER'),
      olderThanDays: form.get('ageOperator') === 'OLDER_THAN' && age ? Number(age) : null,
    })
    if (!response.ok) {
      setRuleError(await apiError(response, 'Unable to add the rule.'))
      return
    }
    formElement.reset()
    setEditingRule(null)
    await loadRules()
    setRuleMessage(editingRule ? 'Rule updated successfully.' : 'Rule added successfully.')
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
    else if (!senderDetailPage && !drivePage && !driveUntitledPage && !driveFilesPage && !photosPage) void loadSenders(senderPage)
  }, [senderPage, classificationPage, senderDetailPage, drivePage, driveUntitledPage, driveFilesPage, photosPage])

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
          createRule={createRule} deleteRule={deleteRule}
          reapplyRules={reapplyRules}
          editingRule={editingRule} setEditingRule={setEditingRule}
          ruleMessage={ruleMessage} ruleError={ruleError} />
      </main>
    )
  }

  if (senderDetailPage) {
    return <main><Topbar active="mailbox" /><SenderDetailPage /></main>
  }

  if (drivePage) {
    return <main><Topbar active="drive" /><DrivePage /></main>
  }

  if (driveUntitledPage) {
    return <main><Topbar active="drive" /><DriveUntitledPage /></main>
  }

  if (driveFilesPage) {
    return <main><Topbar active="drive" /><DriveFilesPage /></main>
  }

  if (photosPage) {
    return <main><Topbar active="photos" /><PhotosPage /></main>
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

function Topbar({ active }: { active: 'mailbox' | 'classification' | 'drive' | 'photos' }) {
  return (
    <header className="topbar">
      <a className="brand" href="/">Declutter<span>AI</span></a>
      <nav className="topnav">
        <a className={active === 'mailbox' ? 'active' : ''} href="/">Mailbox</a>
        <a className={active === 'classification' ? 'active' : ''} href="/classification">Classification</a>
        <a className={active === 'drive' ? 'active' : ''} href="/drive">Drive</a>
        <a className={active === 'photos' ? 'active' : ''} href="/photos">Photos</a>
        <a className="button secondary" href="/oauth2/authorization/google">Connect Google</a>
      </nav>
    </header>
  )
}

function PhotosPage() {
  const [page, setPage] = useState<PhotosMediaPage | null>(null)
  const [pageTokens, setPageTokens] = useState<(string | null)[]>([null])
  const [pageIndex, setPageIndex] = useState(0)
  const [photosError, setPhotosError] = useState<string | null>(null)
  const [loadingPhotos, setLoadingPhotos] = useState(false)
  const [shouldLoadPhotos, setShouldLoadPhotos] = useState(false)

  useEffect(() => {
    if (!shouldLoadPhotos) return
    const load = async () => {
      setPhotosError(null)
      setLoadingPhotos(true)
      const statusController = new AbortController()
      const statusTimeout = window.setTimeout(() => statusController.abort(), 8000)
      try {
        const statusResponse = await fetch('/api/photos/status', {
          headers: { Accept: 'application/json' },
          signal: statusController.signal,
        })
        if (statusResponse.ok) {
          const status: PhotosAccessStatus = await statusResponse.json()
          if (!status.hasPhotosAccess) {
            setPhotosError('The current Google session does not include Photos access. Click Upgrade Photos access and approve Google Photos permission.')
            return
          }
        }
        window.clearTimeout(statusTimeout)

        const token = pageTokens[pageIndex]
        const query = new URLSearchParams({ pageSize: '20' })
        if (token) query.set('pageToken', token)
        const controller = new AbortController()
        const timeout = window.setTimeout(() => controller.abort(), 12000)
        const response = await fetch(`/api/photos/media/page?${query}`, {
          headers: { Accept: 'application/json' },
          signal: controller.signal,
        })
        window.clearTimeout(timeout)
        if (!response.ok) {
          setPhotosError(await apiError(response, 'Unable to load Google Photos media.'))
          return
        }
        const result: PhotosMediaPage = await response.json()
        setPage(result)
        if (result.nextPageToken) {
          setPageTokens((tokens) => tokens[pageIndex + 1]
            ? tokens
            : [...tokens, result.nextPageToken])
        }
      } catch (caught) {
        setPhotosError(caught instanceof DOMException && caught.name === 'AbortError'
          ? 'Google Photos is taking too long to respond. Try again in a moment, or use Drive metadata for file sizes.'
          : caught instanceof Error ? caught.message : 'Unable to load Google Photos media.')
      } finally {
        window.clearTimeout(statusTimeout)
        setLoadingPhotos(false)
      }
    }
    void load()
  }, [shouldLoadPhotos, pageIndex, pageTokens])

  return (
    <>
      <section className="hero drive-hero">
        <p className="eyebrow">Google Photos cleanup</p>
        <h1>Photos, carefully.</h1>
        <p className="lede">
          Google Photos API access is limited. This page lists available media metadata only: file names, type, date, and dimensions.
        </p>
      </section>
      <section className="photos-limitation-card">
        <p className="eyebrow">API limitation</p>
        <h2>Google Photos only exposes app-created library data to this API.</h2>
        <p>
          We can build cleanup workflows here, but unlike Drive, Google does not currently expose your whole Photos library for broad third-party cleanup.
        </p>
      </section>
      {photosError && <PhotosPermissionNotice message={photosError} />}
      {!shouldLoadPhotos && (
        <section className="drive-table-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Metadata</p>
              <h2>Load Google Photos metadata</h2>
            </div>
            <button className="button primary" onClick={() => setShouldLoadPhotos(true)}>
              Load first 20
            </button>
          </div>
          <p className="drive-note">
            This is manual so the page never hangs on open. Google Photos does not provide byte size through this API.
          </p>
        </section>
      )}
      {loadingPhotos && !photosError && <div className="state">Loading 20 Photos media items…</div>}
      {page && !photosError && (
        <section className="drive-table-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Media</p>
              <h2>{page.itemCount.toLocaleString()} shown on this page</h2>
            </div>
          </div>
          {page.mediaItems.length === 0 ? (
            <div className="state">No accessible Google Photos media found for this app.</div>
          ) : (
            <div className="drive-table">
              <div className="photos-row photos-head">
                <span>File name</span><span>Type</span><span>Created</span><span>Dimensions</span>
              </div>
              {page.mediaItems.map((item) => (
                <a key={item.id} className="photos-row"
                  href={item.productUrl ?? '#'} target="_blank" rel="noreferrer">
                  <strong>{item.filename ?? '(No filename)'}</strong>
                  <span>{photosMediaType(item.mimeType)}</span>
                  <span>{formatDate(item.mediaMetadata?.creationTime ?? null)}</span>
                  <span>{photosDimensions(item)}</span>
                </a>
              ))}
            </div>
          )}
          <nav className="pagination detail-pagination" aria-label="Google Photos media pages">
            <button className="button secondary" disabled={pageIndex === 0}
              onClick={() => setPageIndex((current) => current - 1)}>Previous</button>
            <span>Page {pageIndex + 1}</span>
            <button className="button secondary" disabled={!page.nextPageToken}
              onClick={() => setPageIndex((current) => current + 1)}>Next 20</button>
          </nav>
        </section>
      )}
    </>
  )
}

function PhotosPermissionNotice({ message }: { message: string }) {
  const currentPath = `${window.location.pathname}${window.location.search}`
  return (
    <section className="drive-permission-card" role="alert">
      <div>
        <p className="eyebrow">Photos permission needed</p>
        <h2>Reconnect Google Photos access</h2>
        <p>{message}</p>
        <p className="permission-help">
          You may also need to enable the Google Photos Library API in the same Google Cloud project.
        </p>
      </div>
      <a className="button primary" href={`/oauth2/authorization/google?${new URLSearchParams({
        force_consent: 'true',
        return_to: currentPath,
      })}`}>
        Upgrade Photos access
      </a>
    </section>
  )
}

function DrivePage() {
  const [preview, setPreview] = useState<DriveFileListPage | null>(null)
  const [driveError, setDriveError] = useState<string | null>(null)
  const [previewLoading, setPreviewLoading] = useState(true)

  useEffect(() => {
    const load = async () => {
      setDriveError(null)
      setPreviewLoading(true)
      const statusResponse = await fetch('/api/drive/status', {
        headers: { Accept: 'application/json' },
      })
      if (statusResponse.ok) {
        const status: DriveAccessStatus = await statusResponse.json()
        if (!status.hasDriveAccess) {
          setDriveError('The current Google session does not include Drive access. Click Upgrade Drive access and approve Google Drive permission.')
          setPreviewLoading(false)
          return
        }
      }
      const controller = new AbortController()
      const timeout = window.setTimeout(() => controller.abort(), 10000)
      try {
        const response = await fetch('/api/drive/untitled/page?pageSize=20', {
          headers: { Accept: 'application/json' },
          signal: controller.signal,
        })
        if (!response.ok) {
          setDriveError(await apiError(response, 'Unable to load Google Drive files.'))
          return
        }
        setPreview(await response.json())
      } catch (caught) {
        setDriveError(caught instanceof DOMException && caught.name === 'AbortError'
          ? 'Drive preview is taking too long. Open the category to load files page by page.'
          : caught instanceof Error ? caught.message : 'Unable to load Google Drive files.')
      } finally {
        window.clearTimeout(timeout)
        setPreviewLoading(false)
      }
    }
    void load()
  }, [])

  return (
    <>
      <section className="hero drive-hero">
        <p className="eyebrow">Google Drive cleanup</p>
        <h1>Untitled files first.</h1>
        <p className="lede">Find forgotten Untitled Drive files and estimate how much storage you could recover if they were removed.</p>
      </section>
      {driveError && <DrivePermissionNotice message={driveError} />}
      <section className="drive-table-card">
        <div className="section-heading">
          <div><p className="eyebrow">Cleanup categories</p><h2>Drive checks</h2></div>
        </div>
        <div className="drive-category-table">
          <a className="drive-category-row" href="/drive/untitled">
            <strong>Untitled documents</strong>
            <span>{preview ? `${preview.fileCount.toLocaleString()}${preview.nextPageToken ? '+' : ''}` : previewLoading ? 'Checking…' : 'Open'}</span>
            <span>{preview ? formatBytes(preview.recoverableBytes) : '—'}</span>
            <small>Open list →</small>
          </a>
          <a className="drive-category-row" href="/drive/files">
            <strong>All Drive files</strong>
            <span>Metadata</span>
            <span>Name + size</span>
            <small>Open list →</small>
          </a>
          <div className="drive-category-row is-disabled">
            <strong>Duplicate files</strong>
            <span>Coming next</span>
            <span>—</span>
            <small>Not scanned yet</small>
          </div>
        </div>
      </section>
      <p className="drive-note">
        The landing page only checks the first 20 files so it stays fast. A plus sign means more files are available on the detail page.
      </p>
    </>
  )
}

function DriveUntitledPage() {
  return (
    <DriveFileListPageView
      title="Untitled documents."
      description="Review untitled Drive files 20 at a time, with estimated recoverable space for this page."
      endpoint="/api/drive/untitled/page"
      backHref="/drive"
      backLabel="Back to Drive checks"
      eyebrow="Untitled files"
      emptyMessage="No untitled Drive files found."
    />
  )
}

function DriveFilesPage() {
  return (
    <DriveFileListPageView
      title="All Drive files."
      description="List Drive file metadata 20 at a time: name, type, modified date, and storage used."
      endpoint="/api/drive/files/page"
      backHref="/drive"
      backLabel="Back to Drive checks"
      eyebrow="Drive files"
      emptyMessage="No Drive files found."
    />
  )
}

function DriveFileListPageView({
  title,
  description,
  endpoint,
  backHref,
  backLabel,
  eyebrow,
  emptyMessage,
}: {
  title: string
  description: string
  endpoint: string
  backHref: string
  backLabel: string
  eyebrow: string
  emptyMessage: string
}) {
  const [page, setPage] = useState<DriveFileListPage | null>(null)
  const [pageTokens, setPageTokens] = useState<(string | null)[]>([null])
  const [pageIndex, setPageIndex] = useState(0)
  const [driveError, setDriveError] = useState<string | null>(null)
  const [loadingPage, setLoadingPage] = useState(true)

  useEffect(() => {
    const load = async () => {
      setDriveError(null)
      setLoadingPage(true)
      const statusResponse = await fetch('/api/drive/status', {
        headers: { Accept: 'application/json' },
      })
      if (statusResponse.ok) {
        const status: DriveAccessStatus = await statusResponse.json()
        if (!status.hasDriveAccess) {
          setDriveError('The current Google session does not include Drive access. Click Upgrade Drive access and approve Google Drive permission.')
          setLoadingPage(false)
          return
        }
      }
      const token = pageTokens[pageIndex]
      const query = new URLSearchParams({ pageSize: '20' })
      if (token) query.set('pageToken', token)
      const controller = new AbortController()
      const timeout = window.setTimeout(() => controller.abort(), 20000)
      try {
      const response = await fetch(`${endpoint}?${query}`, {
          headers: { Accept: 'application/json' },
          signal: controller.signal,
        })
        if (!response.ok) {
          setDriveError(await apiError(response, 'Unable to load Drive files.'))
          return
        }
        const result: DriveFileListPage = await response.json()
        setPage(result)
        if (result.nextPageToken) {
          setPageTokens((tokens) => tokens[pageIndex + 1]
            ? tokens
            : [...tokens, result.nextPageToken])
        }
      } catch (caught) {
        setDriveError(caught instanceof DOMException && caught.name === 'AbortError'
          ? 'Google Drive is taking too long to respond. Try again in a moment.'
          : caught instanceof Error ? caught.message : 'Unable to load Drive files.')
      } finally {
        window.clearTimeout(timeout)
        setLoadingPage(false)
      }
    }
    void load()
  }, [pageIndex, pageTokens])

  return (
    <>
      <section className="hero drive-hero">
        <p className="eyebrow">Google Drive cleanup</p>
        <h1>{title}</h1>
        <p className="lede">{description}</p>
        <a className="text-button back-link" href={backHref}>← {backLabel}</a>
      </section>
      {driveError && <DrivePermissionNotice message={driveError} />}
      {loadingPage && !driveError && <div className="state">Loading 20 Drive files…</div>}
      {page && (
        <section className="drive-table-card">
          <div className="section-heading">
            <div>
              <p className="eyebrow">{eyebrow}</p>
              <h2>{page.fileCount.toLocaleString()} shown on this page</h2>
            </div>
            <strong>{formatBytes(page.recoverableBytes)} recoverable on this page</strong>
          </div>
          {page.files.length === 0 ? (
            <div className="state">{emptyMessage}</div>
          ) : (
            <DriveFileTable files={page.files} />
          )}
          <nav className="pagination detail-pagination" aria-label="Untitled Drive file pages">
            <button className="button secondary" disabled={pageIndex === 0}
              onClick={() => setPageIndex((current) => current - 1)}>Previous</button>
            <span>Page {pageIndex + 1}</span>
            <button className="button secondary" disabled={!page.nextPageToken}
              onClick={() => setPageIndex((current) => current + 1)}>Next 20</button>
          </nav>
        </section>
      )}
    </>
  )
}

function DrivePermissionNotice({ message }: { message: string }) {
  const currentPath = `${window.location.pathname}${window.location.search}`
  return (
    <section className="drive-permission-card" role="alert">
      <div>
        <p className="eyebrow">Drive permission needed</p>
        <h2>Reconnect Google Drive access</h2>
        <p>{message}</p>
        <p className="permission-help">
          If Google does not show a consent screen, remove Declutter AI from your Google Account’s third-party app access, then connect again.
        </p>
      </div>
      <a className="button primary" href={`/oauth2/authorization/google?${new URLSearchParams({
        force_consent: 'true',
        return_to: currentPath,
      })}`}>
        Upgrade Drive access
      </a>
    </section>
  )
}

function DriveFileTable({ files }: { files: DriveFile[] }) {
  return (
    <div className="drive-table">
      <div className="drive-row drive-head"><span>Name</span><span>Type</span><span>Modified</span><span>Space</span></div>
      {files.map((file) => (
        <a className="drive-row" key={file.id} href={file.webViewLink ?? '#'} target="_blank" rel="noreferrer">
          <strong>{file.name}</strong>
          <span>{driveFileType(file.mimeType)}</span>
          <span>{formatDate(file.modifiedTime)}</span>
          <span>{formatBytes(Number(file.quotaBytesUsed ?? file.size ?? 0))}</span>
        </a>
      ))}
    </div>
  )
}

function ClassificationPage({ rules, setAutoDelete, createRule, deleteRule,
  reapplyRules, editingRule, setEditingRule, ruleMessage, ruleError }: {
  rules: RulesResponse | null
  setAutoDelete: (enabled: boolean) => Promise<void>
  createRule: (event: FormEvent<HTMLFormElement>) => Promise<void>
  deleteRule: (rule: Rule) => Promise<void>
  reapplyRules: () => Promise<void>
  editingRule: Rule | null
  setEditingRule: (rule: Rule | null) => void
  ruleMessage: string | null
  ruleError: string | null
}) {
  return (
    <section className="classification-panel">
      <div className="section-heading">
        <div><p className="eyebrow">Rule engine</p><h2>Classification rules</h2></div>
        <button className="button secondary" type="button"
          onClick={() => void reapplyRules()}>Reapply rules</button>
      </div>
        <label className="rule-toggle">
          <input type="checkbox" checked={rules?.autoDeleteRecommended ?? false}
            onChange={(event) => void setAutoDelete(event.target.checked)} />
          Automatically trash messages recommended for deletion
        </label>
        <p className="rule-warning">Off by default. When enabled, matching mail is moved to Gmail Trash during sync.</p>
        <form className="rule-form" key={editingRule?.id ?? 'new'}
          onSubmit={(event) => void createRule(event)}>
          <input name="name" placeholder="Rule name" defaultValue={editingRule?.name ?? ''} required />
          <select name="category" defaultValue={editingRule?.category ?? ''} required>
            <option value="" disabled>Select category</option>
            {EMAIL_CATEGORIES.map((category) => (
              <option key={category} value={category}>{category}</option>
            ))}
          </select>
          <select name="matchField" defaultValue={editingRule?.matchField ?? 'SENDER'}><option value="SENDER">Sender contains</option><option value="DOMAIN">Sender domain</option><option value="SUBJECT">Subject contains</option><option value="LABEL">Gmail label</option></select>
          <input name="matchValue" placeholder="Match value" defaultValue={editingRule?.matchValue ?? ''} required />
          <select name="condition2Field" defaultValue={
            editingRule?.subjectContains ? 'SUBJECT' : editingRule?.senderContains ? 'SENDER' : 'SUBJECT'
          }>
            <option value="">No additional condition</option>
            <option value="SUBJECT">AND subject contains</option>
            <option value="SENDER">AND sender contains</option>
          </select>
          <input name="condition2Value" placeholder="Condition text (optional)"
            defaultValue={editingRule?.subjectContains ?? editingRule?.senderContains ?? ''} />
          <select name="ageOperator" defaultValue={editingRule?.olderThanDays ? 'OLDER_THAN' : 'ANY'}>
            <option value="ANY">Any message age</option>
            <option value="OLDER_THAN">Message older than</option>
          </select>
          <select name="olderThanDays" defaultValue={editingRule?.olderThanDays?.toString() ?? '30'}>
            <option value="7">7 days</option>
            <option value="15">15 days</option>
            <option value="30">30 days</option>
            <option value="60">60 days</option>
            <option value="90">90 days</option>
            <option value="180">6 months</option>
            <option value="365">1 year</option>
          </select>
          <input name="comment" placeholder="Comment shown for matches"
            defaultValue={editingRule?.comment ?? ''} required />
          <select name="decision" defaultValue={editingRule?.decision ?? 'REVIEW'}>
            <option value="KEEP_IT">Keep it</option>
            <option value="SAFE_TO_DELETE">Safe to delete</option>
            <option value="REVIEW">Review</option>
          </select>
          <button className="button primary rule-submit" type="submit">
            {editingRule ? 'Save changes' : 'Add rule'}
          </button>
          {editingRule && <button className="button secondary rule-submit" type="button"
            onClick={() => setEditingRule(null)}>Cancel editing</button>}
        </form>
        {ruleMessage && <p className="inline-success" role="status">{ruleMessage}</p>}
        {ruleError && <p className="inline-error" role="alert">{ruleError}</p>}
        <div className="rule-list">
          {(rules?.userRules ?? []).map((rule) => (
            <article key={`${rule.id}-${rule.name}`}>
              <div className="rule-card-heading">
                <strong>{rule.name}</strong>
                <div className="rule-card-actions">
                  <button className="edit-button" type="button"
                    onClick={() => { setEditingRule(rule); window.scrollTo({ top: 300, behavior: 'smooth' }) }}>Edit</button>
                  <button className="delete-button" type="button"
                    onClick={() => void deleteRule(rule)}>Delete rule</button>
                </div>
              </div>
              <span>{rule.matchField}: {rule.matchValue}</span>
              {rule.subjectContains && <span>AND subject contains: {rule.subjectContains}</span>}
              {rule.senderContains && <span>AND sender contains: {rule.senderContains}</span>}
              {rule.olderThanDays && <span>AND older than: {rule.olderThanDays} days</span>}
              <span>{rule.category} · {formatDecision(rule.decision)}</span>
              <p>{rule.comment}</p>
            </article>
          ))}
          {(rules?.baseRules ?? []).map((rule) => (
            <article key={`${rule.id}-${rule.name}`}>
              <div className="rule-card-heading">
                <strong>{rule.name}</strong>
                <div className="rule-card-actions">
                  <span className="base-rule-badge">Built in</span>
                  <button className="edit-button" type="button"
                    onClick={() => { setEditingRule(rule); window.scrollTo({ top: 300, behavior: 'smooth' }) }}>Edit</button>
                  <button className="delete-button" type="button"
                    onClick={() => void deleteRule(rule)}>Delete rule</button>
                </div>
              </div>
              <span>{rule.matchField}: {rule.matchValue}</span>
              <span>{rule.category} · {formatDecision(rule.decision)}</span>
              <p>{rule.comment}</p>
            </article>
          ))}
        </div>
    </section>
  )
}

function SenderDetailPage() {
  const search = new URLSearchParams(window.location.search)
  const sender = search.get('sender') ?? ''
  const decision = search.get('decision') as Decision | null
  const [messagePage, setMessagePage] = useState(0)
  const [detail, setDetail] = useState<SenderDetail | null>(null)
  const [detailError, setDetailError] = useState<string | null>(null)

  useEffect(() => {
    const load = async () => {
      const query = new URLSearchParams({
        sender,
        page: messagePage.toString(),
        size: '20',
      })
      if (decision) query.set('decision', decision)
      const response = await fetch(`/api/emails/sender-details?${query}`, {
        headers: { Accept: 'application/json' },
      })
      if (!response.ok) {
        setDetailError(await apiError(response, 'Unable to load sender details.'))
        return
      }
      setDetail(await response.json())
    }
    void load()
  }, [sender, decision, messagePage])

  const filterUrl = (filterDecision?: Decision) => {
    const query = new URLSearchParams({ sender })
    if (filterDecision) query.set('decision', filterDecision)
    return `/sender?${query}`
  }

  return (
    <>
      <section className="hero detail-hero">
        <p className="eyebrow">Sender details</p>
        <h1>{sender || 'Unknown sender'}</h1>
        <p className="lede">
          {detail
            ? `${detail.messageCount.toLocaleString()} stored messages${
              decision ? ` · Showing ${formatDecision(decision)}` : ''}`
            : 'Loading classification details…'}
        </p>
        <a className="text-button back-link" href="/">← Back to senders</a>
      </section>
      {detailError && <p className="inline-error">{detailError}</p>}
      {detail && (
        <section className="sender-decision-summary" aria-label="Cleanup recommendation">
          <div className="decision-summary-copy">
            <p className="eyebrow">Cleanup recommendation</p>
            <h2>Your rules have done the sorting.</h2>
            <p>These totals include every stored message from this sender and respect age conditions in the matched rules.</p>
          </div>
          <div className="decision-metrics">
            <a className="safe" href={filterUrl('SAFE_TO_DELETE')}>
              <span>Safe to delete</span>
              <strong>{detail.safeToDeleteCount.toLocaleString()}</strong>
              <small>messages</small>
            </a>
            <article className="space">
              <span>Space recovered</span>
              <strong>{formatBytes(detail.recoverableBytes)}</strong>
              <small>estimated</small>
            </article>
            <article className="keep">
              <span>Keeping</span>
              <strong>{detail.keepCount.toLocaleString()}</strong>
              <small>messages</small>
            </article>
            <a className="review" href={filterUrl('REVIEW')}>
              <span>Needs review</span>
              <strong>{detail.reviewCount.toLocaleString()}</strong>
              <small>messages</small>
            </a>
          </div>
        </section>
      )}
      <section className="classification-list">
        {detail?.messages.map((message) => (
          <article key={message.id}>
            <div className="classification-heading">
              <div><strong>{message.subject || '(No subject)'}</strong><span>{formatDate(message.receivedAt)}</span></div>
              <span className={`recommendation ${message.decision.toLowerCase()}`}>
                {formatDecision(message.decision)}
              </span>
            </div>
            <dl>
              <div><dt>Category</dt><dd>{message.category || 'Uncategorized'}</dd></div>
              <div><dt>Matched rule</dt><dd>{message.matchedRule || 'No matching rule'}</dd></div>
              <div><dt>Message size</dt><dd>{formatBytes(message.sizeEstimate ?? 0)}</dd></div>
            </dl>
            <p>{message.comment || 'No classification comment available.'}</p>
          </article>
        ))}
      </section>
      {detail && detail.totalPages > 1 && (
        <nav className="pagination detail-pagination" aria-label="Message pages">
          <button className="button secondary" disabled={!detail.hasPrevious}
            onClick={() => setMessagePage((page) => page - 1)}>Previous</button>
          <span>Page {detail.page + 1} of {detail.totalPages}</span>
          <button className="button secondary" disabled={!detail.hasNext}
            onClick={() => setMessagePage((page) => page + 1)}>Next 20</button>
        </nav>
      )}
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

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`
}

function formatDecision(decision: Decision) {
  return decision === 'KEEP_IT' ? 'Keep it'
    : decision === 'SAFE_TO_DELETE' ? 'Safe to delete'
      : 'Review'
}

function driveFileType(mimeType: string) {
  if (mimeType.includes('document')) return 'Document'
  if (mimeType.includes('spreadsheet')) return 'Spreadsheet'
  if (mimeType.includes('presentation')) return 'Presentation'
  if (mimeType.includes('form')) return 'Form'
  if (mimeType.includes('folder')) return 'Folder'
  return mimeType.replace('application/vnd.google-apps.', '').replace('application/', '')
}

function photosMediaType(mimeType: string) {
  if (mimeType.startsWith('image/')) return 'Photo'
  if (mimeType.startsWith('video/')) return 'Video'
  return mimeType
}

function photosDimensions(item: PhotosMediaItem) {
  const width = item.mediaMetadata?.width
  const height = item.mediaMetadata?.height
  return width && height ? `${width} × ${height}` : 'Unknown'
}

export default App
