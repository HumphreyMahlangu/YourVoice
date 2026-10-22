import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getElections } from '../api/elections'
import { useAuth } from '../auth/useAuth'
import type { Election, ElectionStatus } from '../types/election'
import { formatDateTime, formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

const publicStatusOptions: ElectionStatus[] = [
  'REGISTRATION_OPEN',
  'REGISTRATION_CLOSED',
  'VOTING_OPEN',
  'COMPLETED',
]

const administratorStatusOptions: ElectionStatus[] = [
  'DRAFT',
  ...publicStatusOptions,
  'CANCELLED',
]

function ElectionsPage() {
  const { session } = useAuth()
  const administrator = session?.role === 'ADMIN'
  const administratorToken = administrator ? session.accessToken : undefined
  const [elections, setElections] = useState<Election[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [retryCount, setRetryCount] = useState(0)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<ElectionStatus | 'ALL'>('ALL')

  const statusOptions = administrator ? administratorStatusOptions : publicStatusOptions
  const selectedStatus = !administrator && (status === 'DRAFT' || status === 'CANCELLED')
    ? 'ALL'
    : status
  const availableElections = administrator
    ? elections
    : elections.filter((election) => election.status !== 'DRAFT' && election.status !== 'CANCELLED')
  const visibleElections = availableElections.filter((election) =>
    election.name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()) &&
    (selectedStatus === 'ALL' || election.status === selectedStatus),
  )

  useEffect(() => {
    const abortController = new AbortController()

    async function loadElections() {
      setError(null)
      setIsLoading(true)
      try {
        const response = await getElections(abortController.signal, administratorToken)

        if (!abortController.signal.aborted) {
          setElections(response)
        }
      } catch (requestError) {
        if (!abortController.signal.aborted) {
          setError(requestError)
        }
      } finally {
        if (!abortController.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    void loadElections()

    return () => abortController.abort()
  }, [retryCount, administratorToken])

  return (
    <section className="page directory-page" aria-labelledby="elections-heading">
      <header className="page-intro">
        <p className="eyebrow">Election directory</p>
        <h1 id="elections-heading">Elections</h1>
        <p>See schedules, contests, and published results.</p>
      </header>

      {isLoading && <p role="status">Loading elections...</p>}

      {!isLoading && error !== null && (
        <div className="directory-error">
          <p role="alert">
            We could not load the directory. {getErrorMessage(error, 'Please try again.')}
          </p>
          <button className="button button--secondary" type="button" onClick={() => setRetryCount((count) => count + 1)}>
            Try again
          </button>
        </div>
      )}

      {!isLoading && error === null && (
        <div className="directory-toolbar">
          <div>
            <label htmlFor="election-search">Search by name</label>
            <input
              id="election-search"
              type="search"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search elections"
            />
          </div>
          <div>
            <label htmlFor="election-status">Election stage</label>
            <select
              id="election-status"
              value={selectedStatus}
              onChange={(event) => setStatus(event.target.value as ElectionStatus | 'ALL')}
            >
              <option value="ALL">All stages</option>
              {statusOptions.map((option) => (
                <option key={option} value={option}>{formatEnumLabel(option)}</option>
              ))}
            </select>
          </div>
          <p role="status">
            {visibleElections.length} {visibleElections.length === 1 ? 'election' : 'elections'} shown
          </p>
        </div>
      )}

      {!isLoading && error === null && visibleElections.length === 0 && (
        <div className="empty-state">
          <strong>{availableElections.length === 0 ? 'No elections available' : 'No matching elections'}</strong>
          <p>{availableElections.length === 0
            ? 'Elections will appear here when registration opens.'
            : 'Try a different name or election stage.'}</p>
        </div>
      )}

      {!isLoading && error === null && visibleElections.length > 0 && (
        <ul className="election-list">
          {visibleElections.map((election) => (
            <li key={election.id}>
              <article aria-labelledby={`election-${election.id}`}>
                <div className="election-list__identity">
                  <span className="election-list__type">{formatEnumLabel(election.type)} election</span>
                  <h2 id={`election-${election.id}`}>
                    <Link to={`/elections/${election.id}`}>{election.name}</Link>
                  </h2>
                </div>
                <div className="election-list__meta">
                  <span className="status-pill">{formatEnumLabel(election.status)}</span>
                  <span>Voting opens <time dateTime={election.votingStartAt}>{formatDateTime(election.votingStartAt)}</time></span>
                </div>
                <Link className="election-list__action" to={`/elections/${election.id}`} aria-label={`View ${election.name}`}>
                  <span aria-hidden="true">→</span>
                </Link>
              </article>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default ElectionsPage
