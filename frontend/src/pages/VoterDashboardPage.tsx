import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import ApiError from '../api/ApiError'
import { getCurrentAccount } from '../api/auth'
import { getMyRegistrations } from '../api/registrations'
import { useAuth } from '../auth/useAuth'
import type { UserAccount } from '../types/auth'
import type { ElectionRegistration } from '../types/registration'
import { formatDateTime, formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

interface DashboardData {
  account: UserAccount
  registrations: ElectionRegistration[]
}

function VoterDashboardPage() {
  const { session, logout } = useAuth()
  const [dashboard, setDashboard] = useState<DashboardData | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (!session) {
      return
    }

    const authToken = session.accessToken
    const abortController = new AbortController()

    async function loadDashboard() {
      setDashboard(null)
      setError(null)
      setIsLoading(true)

      try {
        const [account, registrations] = await Promise.all([
          getCurrentAccount(authToken, abortController.signal),
          getMyRegistrations(authToken, abortController.signal),
        ])

        if (!abortController.signal.aborted) {
          setDashboard({ account, registrations })
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

    void loadDashboard()

    return () => abortController.abort()
  }, [logout, session])

  return (
    <section className="page dashboard-page" aria-labelledby="dashboard-heading">
      <p className="eyebrow">Voter workspace</p>
      <h1 id="dashboard-heading">Voter dashboard</h1>

      {isLoading && <p role="status">Loading your voter account...</p>}

      {!isLoading && error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to load your voter dashboard.')}
        </p>
      )}

      {!isLoading && error === null && dashboard !== null && (
        <>
          <section aria-labelledby="account-heading">
            <h2 id="account-heading">Account</h2>
            <dl>
              <dt>Email</dt>
              <dd>{dashboard.account.email}</dd>

              <dt>Role</dt>
              <dd>{formatEnumLabel(dashboard.account.role)}</dd>

              <dt>Account status</dt>
              <dd>{dashboard.account.enabled ? 'Enabled' : 'Disabled'}</dd>
            </dl>
          </section>

          <section aria-labelledby="registrations-heading">
            <h2 id="registrations-heading">Election registrations</h2>

            {dashboard.registrations.length === 0 ? (
              <p>
                You have not registered for an election.{' '}
                <Link to="/elections">Browse elections</Link>
              </p>
            ) : (
              <ul>
                {dashboard.registrations.map((registration) => (
                  <li key={registration.id}>
                    <article
                      aria-labelledby={`registration-${registration.id}`}
                    >
                      <h3 id={`registration-${registration.id}`}>
                        <Link to={`/elections/${registration.electionId}`}>
                          {registration.electionName}
                        </Link>
                      </h3>
                      <dl>
                        <dt>Election type</dt>
                        <dd>{formatEnumLabel(registration.electionType)}</dd>

                        <dt>Registration status</dt>
                        <dd>{formatEnumLabel(registration.status)}</dd>

                        <dt>Registered</dt>
                        <dd>
                          <time dateTime={registration.registeredAt}>
                            {formatDateTime(registration.registeredAt)}
                          </time>
                        </dd>

                        <dt>Voting district</dt>
                        <dd>
                          {registration.votingDistrictName} ({
                            registration.votingDistrictCode
                          })
                        </dd>

                        <dt>Province</dt>
                        <dd>{registration.province}</dd>
                      </dl>
                    </article>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </section>
  )
}

export default VoterDashboardPage
