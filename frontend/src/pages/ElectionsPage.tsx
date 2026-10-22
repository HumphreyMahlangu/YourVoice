import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getElections } from '../api/elections'
import type { Election, ElectionStatus } from '../types/election'
import { formatDateTime, formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

const statusOptions: ElectionStatus[] = [
  'DRAFT',
  'REGISTRATION_OPEN',
  'REGISTRATION_CLOSED',
  'VOTING_OPEN',
  'COMPLETED',
  'CANCELLED',
]

function ElectionsPage() {
  const [elections, setElections] = useState<Election[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [retryCount, setRetryCount] = useState(0)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<ElectionStatus | 'ALL'>('ALL')

  const visibleElections = elections.filter((election) =>
    election.name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()) &&
    (status === 'ALL' || election.status === status),
  )

  useEffect(() => {
    const abortController = new AbortController()

    async function loadElections() {
      setError(null)
      setIsLoading(true)
      try {
        const response = await getElections(abortController.signal)

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
  }, [retryCount])

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
              value={status}
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
          <strong>{elections.length === 0 ? 'No elections yet' : 'No matching elections'}</strong>
          <p>{elections.length === 0
            ? 'Elections will appear here after an administrator creates them.'
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
