import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getVotingDistricts } from '../api/districts'
import { getElections } from '../api/elections'
import type { VotingDistrict } from '../types/district'
import type { Election, ElectionStatus } from '../types/election'
import { formatEnumLabel, formatNumber } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

interface AdminDashboardData {
  elections: Election[]
  districts: VotingDistrict[]
}

const ELECTION_STATUSES: ElectionStatus[] = [
  'DRAFT',
  'REGISTRATION_OPEN',
  'REGISTRATION_CLOSED',
  'VOTING_OPEN',
  'COMPLETED',
  'CANCELLED',
]

function AdminDashboardPage() {
  const [dashboard, setDashboard] = useState<AdminDashboardData | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    const abortController = new AbortController()

    async function loadDashboard() {
      setError(null)
      setIsLoading(true)

      try {
        const [elections, districts] = await Promise.all([
          getElections(abortController.signal),
          getVotingDistricts(abortController.signal),
        ])

        if (!abortController.signal.aborted) {
          setDashboard({ elections, districts })
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

    void loadDashboard()

    return () => abortController.abort()
  }, [])

  return (
    <section className="page dashboard-page admin-page" aria-labelledby="admin-dashboard-heading">
      <p className="eyebrow">Election control centre</p>
      <h1 id="admin-dashboard-heading">Admin dashboard</h1>

      {isLoading && <p role="status">Loading administration summary...</p>}

      {!isLoading && error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to load the admin dashboard.')}
        </p>
      )}

      {!isLoading && error === null && dashboard !== null && (
        <>
          <section aria-labelledby="election-summary-heading">
            <h2 id="election-summary-heading">Election summary</h2>
            <dl>
              <dt>Total elections</dt>
              <dd>{formatNumber(dashboard.elections.length)}</dd>

              {ELECTION_STATUSES.map((status) => (
                <div key={status}>
                  <dt>{formatEnumLabel(status)}</dt>
                  <dd>
                    {formatNumber(
                      dashboard.elections.filter(
                        (election) => election.status === status,
                      ).length,
                    )}
                  </dd>
                </div>
              ))}
            </dl>
          </section>

          <section aria-labelledby="district-summary-heading">
            <h2 id="district-summary-heading">Voting districts</h2>
            <p>{formatNumber(dashboard.districts.length)} districts available.</p>
          </section>

          <section aria-labelledby="draft-elections-heading">
            <h2 id="draft-elections-heading">Draft election configuration</h2>
            {dashboard.elections.filter(
              (election) => election.status === 'DRAFT',
            ).length === 0 ? (
              <p>No draft elections are available to configure.</p>
            ) : (
              <ul>
                {dashboard.elections
                  .filter((election) => election.status === 'DRAFT')
                  .map((election) => (
                    <li key={election.id}>
                      <Link
                        to={`/admin/elections/${election.id}/configure`}
                      >
                        Configure {election.name}
                      </Link>
                    </li>
                  ))}
              </ul>
            )}
          </section>

          <section aria-labelledby="election-operations-heading">
            <h2 id="election-operations-heading">Election operations</h2>
            {dashboard.elections.length === 0 ? (
              <p>No elections are available.</p>
            ) : (
              <ul>
                {dashboard.elections.map((election) => (
                  <li key={election.id}>
                    <Link to={`/admin/elections/${election.id}/operations`}>
                      {election.name} — {formatEnumLabel(election.status)}
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section aria-labelledby="admin-tools-heading">
            <h2 id="admin-tools-heading">Admin tools</h2>
            <ul>
              <li>
                <Link to="/admin/elections/new">Create an election</Link>
              </li>
              <li>
                <Link to="/admin/voting-districts/new">
                  Create a voting district
                </Link>
              </li>
              <li>
                <Link to="/admin/security-events">
                  Review security audit events
                </Link>
              </li>
            </ul>
          </section>
        </>
      )}
    </section>
  )
}

export default AdminDashboardPage
