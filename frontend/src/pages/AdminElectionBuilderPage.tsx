import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import ApiError from '../api/ApiError'
import { deleteContest, deleteContestOption } from '../api/adminManagement'
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
  // Identifies the contest or option awaiting delete confirmation, e.g. "contest:<id>" or "option:<id>".
  const [pendingDelete, setPendingDelete] = useState<string | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<unknown>(null)

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

  async function confirmDelete(contestId: string, optionId?: string) {
    if (!builderData || !session) {
      return
    }
    setDeleteError(null)
    setIsDeleting(true)

    try {
      if (optionId) {
        await deleteContestOption(
          builderData.election.id,
          contestId,
          optionId,
          session.accessToken,
        )
      } else {
        await deleteContest(builderData.election.id, contestId, session.accessToken)
      }
      setBuilderData((currentData) =>
        currentData
          ? {
              ...currentData,
              contests: optionId
                ? currentData.contests.map((contest) =>
                    contest.id === contestId
                      ? {
                          ...contest,
                          options: contest.options.filter(
                            (option) => option.id !== optionId,
                          ),
                        }
                      : contest,
                  )
                : currentData.contests.filter(
                    (contest) => contest.id !== contestId,
                  ),
            }
          : currentData,
      )
      setPendingDelete(null)
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        logout()
        return
      }
      setDeleteError(requestError)
    } finally {
      setIsDeleting(false)
    }
  }

  function deleteControls(key: string, label: string, onConfirm: () => void) {
    if (!isConfigurationOpen) {
      return null
    }
    return pendingDelete === key ? (
      <>
        {' '}
        <button type="button" disabled={isDeleting} onClick={onConfirm}>
          {isDeleting ? 'Removing...' : `Confirm removing ${label}`}
        </button>{' '}
        <button
          type="button"
          disabled={isDeleting}
          onClick={() => setPendingDelete(null)}
        >
          Keep
        </button>
      </>
    ) : (
      <>
        {' '}
        <button
          type="button"
          onClick={() => {
            setDeleteError(null)
            setPendingDelete(key)
          }}
        >
          Remove
        </button>
      </>
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

  // Mirrors the API: a DRAFT election stays configurable until its registration window ends,
  // so a ballot that was not ready when registration was due to open can still be completed.
  const isDraft = builderData?.election.status === 'DRAFT'
  const registrationWindowEnded =
    builderData !== null &&
    new Date(builderData.election.registrationEndAt).getTime() <= Date.now()
  const isConfigurationOpen = isDraft && !registrationWindowEnded
  const isRegistrationOverdue =
    isConfigurationOpen &&
    builderData !== null &&
    new Date(builderData.election.registrationStartAt).getTime() <= Date.now()

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

          {isRegistrationOverdue && (
            <p role="status">
              Registration was due to open but this ballot is not ready yet.
              Every contest needs at least two party or independent candidate
              options. Registration opens automatically once the ballot is
              complete.
            </p>
          )}

          {!isConfigurationOpen ? (
            <p>
              {isDraft ? (
                <>
                  The registration window ended before the ballot was ready, so
                  this election can no longer open.{' '}
                  <Link
                    to={`/admin/elections/${builderData.election.id}/operations`}
                  >
                    Cancel it from election operations
                  </Link>{' '}
                  and create a new election.
                </>
              ) : (
                'Ballot configuration is locked because registration has opened.'
              )}
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

            {deleteError !== null && (
              <p role="alert">
                {getErrorMessage(deleteError, 'Unable to remove it.')}
              </p>
            )}

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
                      {deleteControls(
                        `contest:${contest.id}`,
                        `${contest.name} and its options`,
                        () => void confirmDelete(contest.id),
                      )}
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
                              {deleteControls(`option:${option.id}`, option.name, () =>
                                void confirmDelete(contest.id, option.id),
                              )}
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
