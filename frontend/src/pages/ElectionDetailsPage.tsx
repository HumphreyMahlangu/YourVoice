import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getElectionContests } from '../api/contests'
import { getElection } from '../api/elections'
import { useAuth } from '../auth/useAuth'
import ElectionMetadata from '../components/ElectionMetadata'
import type { Contest } from '../types/contest'
import type { Election } from '../types/election'
import { formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

interface ElectionDetails {
  election: Election
  contests: Contest[]
}

function ElectionDetailsPage() {
  const { electionId } = useParams<{ electionId: string }>()
  const { session } = useAuth()
  const [details, setDetails] = useState<ElectionDetails | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (!electionId) {
      setError(new Error('Election identifier is missing.'))
      setIsLoading(false)
      return
    }

    const requestedElectionId = electionId
    const abortController = new AbortController()

    async function loadElectionDetails() {
      setDetails(null)
      setError(null)
      setIsLoading(true)

      try {
        const [election, contests] = await Promise.all([
          getElection(requestedElectionId, abortController.signal),
          getElectionContests(requestedElectionId, abortController.signal),
        ])

        if (!abortController.signal.aborted) {
          setDetails({ election, contests })
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

    void loadElectionDetails()

    return () => abortController.abort()
  }, [electionId])

  return (
    <section className="page detail-page" aria-labelledby="election-details-heading">
      <Link to="/elections">Back to elections</Link>

      {isLoading && <p role="status">Loading election details...</p>}

      {!isLoading && error !== null && (
        <>
          <h1 id="election-details-heading">Election unavailable</h1>
          <p role="alert">
            {getErrorMessage(error, 'Unable to load the election.')}
          </p>
        </>
      )}

      {!isLoading && error === null && details !== null && (
        <>
          <article>
            <h1 id="election-details-heading">{details.election.name}</h1>
            <ElectionMetadata election={details.election} />
          </article>

          {details.election.status === 'REGISTRATION_OPEN' && (
            <section aria-labelledby="voter-registration-heading">
              <h2 id="voter-registration-heading">Voter registration</h2>
              {!session ? (
                <p>
                  <Link to="/login">Sign in</Link> to register for this election.
                </p>
              ) : session.role === 'VOTER' ? (
                <p>
                  <Link to={`/elections/${details.election.id}/register`}>
                    Register for this election
                  </Link>
                </p>
              ) : (
                <p>Administrator accounts cannot register as voters.</p>
              )}
            </section>
          )}

          <section aria-labelledby="contests-heading">
            <h2 id="contests-heading">Contests</h2>

            {details.contests.length === 0 && (
              <p>No contests have been published for this election.</p>
            )}

            {details.contests.length > 0 && (
              <ol>
                {details.contests.map((contest) => (
                  <li key={contest.id}>
                    <article aria-labelledby={`contest-${contest.id}`}>
                      <h3 id={`contest-${contest.id}`}>{contest.name}</h3>
                      <dl>
                        <dt>Type</dt>
                        <dd>{formatEnumLabel(contest.type)}</dd>

                        <dt>Status</dt>
                        <dd>{formatEnumLabel(contest.status)}</dd>

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
                              {option.name} ({formatEnumLabel(option.optionType)})
                            </li>
                          ))}
                        </ol>
                      )}

                      {details.election.status === 'COMPLETED' &&
                        contest.status === 'CLOSED' && (
                          <p>
                            <Link
                              to={`/elections/${details.election.id}/contests/${contest.id}/results`}
                            >
                              View final results
                            </Link>
                          </p>
                        )}

                      {details.election.status === 'VOTING_OPEN' &&
                        contest.status === 'OPEN' &&
                        (!session ? (
                          <p>
                            <Link to="/login">Sign in</Link> to vote in this
                            contest.
                          </p>
                        ) : session.role === 'VOTER' ? (
                          <p>
                            <Link
                              to={`/elections/${details.election.id}/contests/${contest.id}/vote`}
                            >
                              Open ballot
                            </Link>
                          </p>
                        ) : (
                          <p>Administrator accounts cannot cast ballots.</p>
                        ))}
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

export default ElectionDetailsPage
