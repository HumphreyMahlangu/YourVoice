import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getElectionContests } from '../api/contests'
import { getElection } from '../api/elections'
import { useAuth } from '../auth/useAuth'
import AdminContestForm from '../components/admin/AdminContestForm'
import AdminContestOptionForm from '../components/admin/AdminContestOptionForm'
import ElectionMetadata from '../components/ElectionMetadata'
import type { Contest, ContestOption } from '../types/contest'
import type { Election } from '../types/election'
import { formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

interface ElectionBuilderData {
  election: Election
  contests: Contest[]
}

function AdminElectionBuilderPage() {
  const { electionId } = useParams<{ electionId: string }>()
  const { session, logout } = useAuth()
  const [builderData, setBuilderData] = useState<ElectionBuilderData | null>(
    null,
  )
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<unknown>(null)

  useEffect(() => {
    if (!electionId) {
      setLoadError(new Error('Election identifier is missing.'))
      setIsLoading(false)
      return
    }

    const requestedElectionId = electionId
    const abortController = new AbortController()

    async function loadBuilder() {
      setLoadError(null)
      setIsLoading(true)

      try {
        const [election, contests] = await Promise.all([
          getElection(requestedElectionId, abortController.signal),
          getElectionContests(requestedElectionId, abortController.signal),
        ])

        if (!abortController.signal.aborted) {
          setBuilderData({ election, contests })
        }
      } catch (requestError) {
        if (!abortController.signal.aborted) {
          setLoadError(requestError)
        }
      } finally {
        if (!abortController.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    void loadBuilder()

    return () => abortController.abort()
  }, [electionId])

  function handleContestCreated(contest: Contest) {
    setBuilderData((currentData) =>
      currentData
        ? {
            ...currentData,
            contests: [...currentData.contests, contest].toSorted(
              (left, right) => left.displayOrder - right.displayOrder,
            ),
          }
        : currentData,
    )
  }

  function handleOptionCreated(contestId: string, option: ContestOption) {
    setBuilderData((currentData) =>
      currentData
        ? {
            ...currentData,
            contests: currentData.contests.map((contest) =>
              contest.id === contestId
                ? {
                    ...contest,
                    options: [...contest.options, option].toSorted(
                      (left, right) =>
                        left.displayOrder - right.displayOrder,
                    ),
                  }
                : contest,
            ),
          }
        : currentData,
    )
  }

  const isConfigurationOpen =
    builderData?.election.status === 'DRAFT' &&
    new Date(builderData.election.registrationStartAt).getTime() > Date.now()

  return (
    <section className="page admin-page workflow-page" aria-labelledby="election-builder-heading">
      <Link to="/admin">Back to admin dashboard</Link>

      {isLoading && <p role="status">Loading election configuration...</p>}

      {!isLoading && loadError !== null && (
        <>
          <h1 id="election-builder-heading">Election unavailable</h1>
          <p role="alert">
            {getErrorMessage(loadError, 'Unable to load election configuration.')}
          </p>
        </>
      )}

      {!isLoading && loadError === null && builderData !== null && (
        <>
          <header>
            <h1 id="election-builder-heading">
              Configure {builderData.election.name}
            </h1>
            <ElectionMetadata election={builderData.election} />
          </header>
          <p>
            <Link
              to={`/admin/elections/${builderData.election.id}/operations`}
            >
              View lifecycle history and election operations
            </Link>
          </p>

          {!isConfigurationOpen ? (
            <p>
              Ballot configuration is locked because this election is no
              longer an upcoming draft.
            </p>
          ) : session ? (
            <AdminContestForm
              electionId={builderData.election.id}
              electionType={builderData.election.type}
              authToken={session.accessToken}
              onCreated={handleContestCreated}
              onUnauthorized={logout}
            />
          ) : null}

          <section aria-labelledby="configured-contests-heading">
            <h2 id="configured-contests-heading">Configured contests</h2>

            {builderData.contests.length === 0 ? (
              <p>No contests have been configured.</p>
            ) : (
              <ol>
                {builderData.contests.map((contest) => (
                  <li key={contest.id}>
                    <article aria-labelledby={`admin-contest-${contest.id}`}>
                      <h3 id={`admin-contest-${contest.id}`}>
                        {contest.name}
                      </h3>
                      <dl>
                        <dt>Type</dt>
                        <dd>{formatEnumLabel(contest.type)}</dd>

                        <dt>Status</dt>
                        <dd>{formatEnumLabel(contest.status)}</dd>

                        <dt>Display order</dt>
                        <dd>{contest.displayOrder}</dd>

                        {contest.scopeProvince && (
                          <>
                            <dt>Province</dt>
                            <dd>{contest.scopeProvince}</dd>
                          </>
                        )}

                        {contest.scopeMunicipality && (
                          <>
                            <dt>Municipality</dt>
                            <dd>{contest.scopeMunicipality}</dd>
                          </>
                        )}

                        {contest.scopeWardNumber !== null && (
                          <>
                            <dt>Ward</dt>
                            <dd>{contest.scopeWardNumber}</dd>
                          </>
                        )}
                      </dl>

                      <h4>Ballot options</h4>
                      {contest.options.length === 0 ? (
                        <p>No ballot options have been added.</p>
                      ) : (
                        <ol>
                          {contest.options.map((option) => (
                            <li key={option.id}>
                              {option.name} —{' '}
                              {formatEnumLabel(option.optionType)} — order{' '}
                              {option.displayOrder}
                            </li>
                          ))}
                        </ol>
                      )}

                      {isConfigurationOpen && session && (
                        <AdminContestOptionForm
                          electionId={builderData.election.id}
                          contestId={contest.id}
                          existingOptions={contest.options}
                          authToken={session.accessToken}
                          onCreated={(option) =>
                            handleOptionCreated(contest.id, option)
                          }
                          onUnauthorized={logout}
                        />
                      )}
                    </article>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </>
      )}
    </section>
  )
}

export default AdminElectionBuilderPage
