import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import ApiError from '../api/ApiError'
import {
  cancelElection,
  getElectionLifecycleEvents,
} from '../api/adminManagement'
import { getElection } from '../api/elections'
import { useAuth } from '../auth/useAuth'
import ElectionMetadata from '../components/ElectionMetadata'
import type { Election } from '../types/election'
import type { ElectionLifecycleEvent } from '../types/electionLifecycle'
import { formatDateTime, formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

interface ElectionOperationsData {
  election: Election
  events: ElectionLifecycleEvent[]
}

function AdminElectionOperationsPage() {
  const { electionId } = useParams<{ electionId: string }>()
  const { session, logout } = useAuth()
  const [operationsData, setOperationsData] =
    useState<ElectionOperationsData | null>(null)
  const [showCancellationConfirmation, setShowCancellationConfirmation] =
    useState(false)
  const [wasCancelled, setWasCancelled] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isCancelling, setIsCancelling] = useState(false)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [cancellationError, setCancellationError] = useState<unknown>(null)
  const [historyRefreshError, setHistoryRefreshError] =
    useState<unknown>(null)

  useEffect(() => {
    if (!electionId || !session) {
      return
    }

    const requestedElectionId = electionId
    const authToken = session.accessToken
    const abortController = new AbortController()

    async function loadOperations() {
      setLoadError(null)
      setIsLoading(true)

      try {
        const [election, events] = await Promise.all([
          getElection(requestedElectionId, abortController.signal),
          getElectionLifecycleEvents(
            requestedElectionId,
            authToken,
            abortController.signal,
          ),
        ])

        if (!abortController.signal.aborted) {
          setOperationsData({ election, events })
        }
      } catch (requestError) {
        if (abortController.signal.aborted) {
          return
        }

        if (requestError instanceof ApiError && requestError.status === 401) {
          logout()
          return
        }

        setLoadError(requestError)
      } finally {
        if (!abortController.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    void loadOperations()

    return () => abortController.abort()
  }, [electionId, logout, session])

  async function handleCancellation() {
    if (!electionId || !session) {
      return
    }

    const authToken = session.accessToken
    setCancellationError(null)
    setHistoryRefreshError(null)
    setIsCancelling(true)

    try {
      const election = await cancelElection(electionId, authToken)
      setOperationsData((currentData) =>
        currentData ? { ...currentData, election } : currentData,
      )
      setWasCancelled(true)
      setShowCancellationConfirmation(false)

      try {
        const events = await getElectionLifecycleEvents(electionId, authToken)
        setOperationsData((currentData) =>
          currentData ? { ...currentData, events } : currentData,
        )
      } catch (requestError) {
        if (requestError instanceof ApiError && requestError.status === 401) {
          logout()
          return
        }

        setHistoryRefreshError(requestError)
      }
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        logout()
        return
      }

      setCancellationError(requestError)
    } finally {
      setIsCancelling(false)
    }
  }

  const canCancel =
    operationsData !== null &&
    operationsData.election.status !== 'COMPLETED' &&
    operationsData.election.status !== 'CANCELLED'

  return (
    <section className="page admin-page workflow-page" aria-labelledby="election-operations-heading">
      <Link to="/admin">Back to admin dashboard</Link>

      {isLoading && <p role="status">Loading election operations...</p>}

      {!isLoading && loadError !== null && (
        <>
          <h1 id="election-operations-heading">Election unavailable</h1>
          <p role="alert">
            {getErrorMessage(loadError, 'Unable to load election operations.')}
          </p>
        </>
      )}

      {!isLoading && loadError === null && operationsData !== null && (
        <>
          <header>
            <h1 id="election-operations-heading">
              Operations for {operationsData.election.name}
            </h1>
            <ElectionMetadata election={operationsData.election} />
          </header>

          {operationsData.election.status === 'DRAFT' && (
            <p>
              <Link
                to={`/admin/elections/${operationsData.election.id}/configure`}
              >
                Configure contests and ballot options
              </Link>
            </p>
          )}

          <section aria-labelledby="cancellation-heading">
            <h2 id="cancellation-heading">Emergency cancellation</h2>

            {wasCancelled && (
              <p role="status">The election was cancelled successfully.</p>
            )}

            {cancellationError !== null && (
              <p role="alert">
                {getErrorMessage(
                  cancellationError,
                  'Unable to cancel the election.',
                )}
              </p>
            )}

            {canCancel && !showCancellationConfirmation && (
              <button
                type="button"
                onClick={() => setShowCancellationConfirmation(true)}
              >
                Emergency cancel election
              </button>
            )}

            {canCancel && showCancellationConfirmation && (
              <section aria-labelledby="confirm-cancellation-heading">
                <h3 id="confirm-cancellation-heading">
                  Confirm election cancellation
                </h3>
                <p>
                  This action stops the election, closes open contests, and is
                  permanently recorded with your administrator identity.
                </p>
                <button
                  type="button"
                  disabled={isCancelling}
                  onClick={() => void handleCancellation()}
                >
                  {isCancelling
                    ? 'Cancelling election...'
                    : 'Confirm emergency cancellation'}
                </button>{' '}
                <button
                  type="button"
                  disabled={isCancelling}
                  onClick={() => setShowCancellationConfirmation(false)}
                >
                  Keep election active
                </button>
              </section>
            )}

            {!canCancel && !wasCancelled && (
              <p>
                This election cannot be cancelled because it is already{' '}
                {formatEnumLabel(operationsData.election.status).toLowerCase()}.
              </p>
            )}
          </section>

          <section aria-labelledby="lifecycle-history-heading">
            <h2 id="lifecycle-history-heading">Lifecycle history</h2>

            {historyRefreshError !== null && (
              <p role="alert">
                The cancellation succeeded, but the updated lifecycle history
                could not be loaded. Reload this page to try again.
              </p>
            )}

            {operationsData.events.length === 0 ? (
              <p>No lifecycle events have been recorded.</p>
            ) : (
              <table>
                <caption>Election lifecycle events in chronological order</caption>
                <thead>
                  <tr>
                    <th scope="col">Occurred</th>
                    <th scope="col">Transition</th>
                    <th scope="col">Trigger</th>
                    <th scope="col">Outcome</th>
                    <th scope="col">Actor</th>
                    <th scope="col">Detail</th>
                  </tr>
                </thead>
                <tbody>
                  {operationsData.events.map((lifecycleEvent) => (
                    <tr key={lifecycleEvent.id}>
                      <td>
                        <time dateTime={lifecycleEvent.occurredAt}>
                          {formatDateTime(lifecycleEvent.occurredAt)}
                        </time>
                      </td>
                      <td>
                        {formatEnumLabel(lifecycleEvent.previousStatus)} to{' '}
                        {lifecycleEvent.newStatus
                          ? formatEnumLabel(lifecycleEvent.newStatus)
                          : 'No target status'}
                      </td>
                      <td>{formatEnumLabel(lifecycleEvent.trigger)}</td>
                      <td>{formatEnumLabel(lifecycleEvent.outcome)}</td>
                      <td>{lifecycleEvent.actorEmail ?? 'System'}</td>
                      <td>{lifecycleEvent.detail}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      )}
    </section>
  )
}

export default AdminElectionOperationsPage
