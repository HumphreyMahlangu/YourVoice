import { useEffect, useState } from 'react'
import ApiError from '../api/ApiError'
import { getSecurityAuditEvents } from '../api/securityAudit'
import { useAuth } from '../auth/useAuth'
import type { SecurityAuditEvent } from '../types/securityAudit'
import { formatDateTime, formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

type AuditEventLimit = 25 | 50 | 100

const AUDIT_EVENT_LIMITS: AuditEventLimit[] = [25, 50, 100]

function AdminSecurityEventsPage() {
  const { session, logout } = useAuth()
  const [events, setEvents] = useState<SecurityAuditEvent[] | null>(null)
  const [limit, setLimit] = useState<AuditEventLimit>(50)
  const [refreshCount, setRefreshCount] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (!session) {
      return
    }

    const authToken = session.accessToken
    const abortController = new AbortController()

    async function loadEvents() {
      setError(null)
      setIsLoading(true)

      try {
        const auditEvents = await getSecurityAuditEvents(
          authToken,
          limit,
          abortController.signal,
        )

        if (!abortController.signal.aborted) {
          setEvents(auditEvents)
        }
      } catch (requestError) {
        if (abortController.signal.aborted) {
          return
        }

        if (requestError instanceof ApiError && requestError.status === 401) {
          logout()
          return
        }

        setError(requestError)
      } finally {
        if (!abortController.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    void loadEvents()

    return () => abortController.abort()
  }, [limit, logout, refreshCount, session])

  return (
    <section className="page admin-page verification-page" aria-labelledby="security-events-heading">
      <h1 id="security-events-heading">Security audit events</h1>
      <p>
        Review recent account authentication, administrator bootstrap, and
        rate-limit activity.
      </p>

      <div>
        <label htmlFor="security-event-limit">Events to show</label>
        <select
          id="security-event-limit"
          value={limit}
          onChange={(event) =>
            setLimit(Number(event.target.value) as AuditEventLimit)
          }
        >
          {AUDIT_EVENT_LIMITS.map((eventLimit) => (
            <option key={eventLimit} value={eventLimit}>
              {eventLimit}
            </option>
          ))}
        </select>
        <button
          type="button"
          disabled={isLoading}
          onClick={() => setRefreshCount((count) => count + 1)}
        >
          Refresh
        </button>
      </div>

      {isLoading && <p role="status">Loading security audit events...</p>}

      {!isLoading && error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to load security audit events.')}
        </p>
      )}

      {!isLoading && error === null && events?.length === 0 && (
        <p>No security audit events are available.</p>
      )}

      {!isLoading && error === null && events && events.length > 0 && (
        <table>
          <caption>Most recent security audit events</caption>
          <thead>
            <tr>
              <th scope="col">Occurred</th>
              <th scope="col">Event</th>
              <th scope="col">Outcome</th>
              <th scope="col">Principal</th>
              <th scope="col">Client IP</th>
              <th scope="col">Detail</th>
              <th scope="col">User agent</th>
            </tr>
          </thead>
          <tbody>
            {events.map((auditEvent) => (
              <tr key={auditEvent.id}>
                <td>
                  <time dateTime={auditEvent.occurredAt}>
                    {formatDateTime(auditEvent.occurredAt)}
                  </time>
                </td>
                <td>{formatEnumLabel(auditEvent.eventType)}</td>
                <td>{formatEnumLabel(auditEvent.outcome)}</td>
                <td>{auditEvent.principalEmail ?? 'Not available'}</td>
                <td>{auditEvent.clientIp}</td>
                <td>{auditEvent.detail}</td>
                <td>{auditEvent.userAgent ?? 'Not available'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

export default AdminSecurityEventsPage
