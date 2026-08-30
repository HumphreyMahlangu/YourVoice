import { useEffect, useRef, useState } from 'react'
import { Link, useBlocker, useParams } from 'react-router'
import ApiError from '../api/ApiError'
import { getElectionContests } from '../api/contests'
import { getElection } from '../api/elections'
import { castBallot, issueVotingCredential } from '../api/voting'
import { useAuth } from '../auth/useAuth'
import BallotReview from '../components/voting/BallotReview'
import BallotSelection from '../components/voting/BallotSelection'
import LeaveVotingPageWarning from '../components/voting/LeaveVotingPageWarning'
import type { Contest } from '../types/contest'
import type { Election } from '../types/election'
import type { BallotCastResponse } from '../types/voting'
import { formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

type VotingPhase =
  | 'selecting'
  | 'reviewing'
  | 'submitting'
  | 'accepted'
  | 'uncertain'
  | 'failed'

interface VotingPageData {
  election: Election
  contest: Contest
}

function VotePage() {
  const { electionId, contestId } = useParams<{
    electionId: string
    contestId: string
  }>()
  const { session, logout } = useAuth()
  const credentialRef = useRef<string | null>(null)
  const [pageData, setPageData] = useState<VotingPageData | null>(null)
  const [selectedOptionId, setSelectedOptionId] = useState('')
  const [phase, setPhase] = useState<VotingPhase>('selecting')
  const [credentialExpiresAt, setCredentialExpiresAt] = useState<string | null>(
    null,
  )
  const [hasIssuedCredential, setHasIssuedCredential] = useState(false)
  const [acceptance, setAcceptance] = useState<BallotCastResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [pageError, setPageError] = useState<unknown>(null)
  const [submissionError, setSubmissionError] = useState<unknown>(null)
  const blocker = useBlocker(hasIssuedCredential)

  useEffect(() => {
    if (!hasIssuedCredential) {
      return
    }

    function warnBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault()
      event.returnValue = ''
    }

    window.addEventListener('beforeunload', warnBeforeUnload)
    return () => window.removeEventListener('beforeunload', warnBeforeUnload)
  }, [hasIssuedCredential])

  useEffect(() => {
    if (!electionId || !contestId) {
      setPageError(new Error('Election or contest identifier is missing.'))
      setIsLoading(false)
      return
    }

    const requestedElectionId = electionId
    const requestedContestId = contestId
    const abortController = new AbortController()

    async function loadVotingPage() {
      setPageData(null)
      setPageError(null)
      setIsLoading(true)

      try {
        const [election, contests] = await Promise.all([
          getElection(requestedElectionId, abortController.signal),
          getElectionContests(requestedElectionId, abortController.signal),
        ])
        const contest = contests.find(
          (candidate) => candidate.id === requestedContestId,
        )

        if (!contest) {
          throw new Error('Contest not found.')
        }

        if (!abortController.signal.aborted) {
          setPageData({ election, contest })
        }
      } catch (requestError) {
        if (!abortController.signal.aborted) {
          setPageError(requestError)
        }
      } finally {
        if (!abortController.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    void loadVotingPage()

    return () => abortController.abort()
  }, [contestId, electionId])

  const selectedOption = pageData?.contest.options.find(
    (option) => option.id === selectedOptionId,
  )

  function reviewSelection() {
    if (selectedOption) {
      setSubmissionError(null)
      setPhase('reviewing')
    }
  }

  function returnToSelection() {
    if (!hasIssuedCredential) {
      setSubmissionError(null)
      setPhase('selecting')
    }
  }

  function clearCredential() {
    credentialRef.current = null
    setCredentialExpiresAt(null)
    setHasIssuedCredential(false)
  }

  async function submitBallot() {
    if (!session || !pageData || !selectedOption) {
      return
    }

    setSubmissionError(null)
    setPhase('submitting')
    let requestStage: 'credential' | 'ballot' = credentialRef.current
      ? 'ballot'
      : 'credential'

    try {
      let votingCredential = credentialRef.current

      if (!votingCredential) {
        const credential = await issueVotingCredential(
          pageData.election.id,
          pageData.contest.id,
          session.accessToken,
        )
        votingCredential = credential.votingCredential
        credentialRef.current = votingCredential
        setCredentialExpiresAt(credential.expiresAt)
        setHasIssuedCredential(true)
      }

      requestStage = 'ballot'
      const response = await castBallot({
        contestId: pageData.contest.id,
        contestOptionId: selectedOption.id,
        votingCredential,
      })

      clearCredential()
      setSelectedOptionId('')
      setAcceptance(response)
      setPhase('accepted')
    } catch (requestError) {
      if (
        requestStage === 'credential' &&
        requestError instanceof ApiError &&
        requestError.status === 401
      ) {
        logout()
        return
      }

      setSubmissionError(requestError)

      if (
        requestStage === 'credential' &&
        (!(requestError instanceof ApiError) || requestError.status === 409)
      ) {
        clearCredential()
        setPhase('uncertain')
        return
      }

      if (
        requestStage === 'ballot' &&
        requestError instanceof ApiError &&
        requestError.status === 409 &&
        requestError.message.toLowerCase().includes('already been used')
      ) {
        clearCredential()
        setPhase('uncertain')
        return
      }

      const canRetry =
        requestStage === 'ballot' &&
        (!(requestError instanceof ApiError) ||
          requestError.status === 429 ||
          requestError.status >= 500)

      if (canRetry) {
        setPhase('reviewing')
      } else {
        clearCredential()
        setPhase('failed')
      }
    }
  }

  function stayOnVotingPage() {
    if (blocker.state === 'blocked') {
      blocker.reset()
    }
  }

  function leaveVotingPage() {
    if (blocker.state === 'blocked') {
      clearCredential()
      blocker.proceed()
    }
  }

  const electionPath = electionId ? `/elections/${electionId}` : '/elections'
  const votingIsOpen =
    pageData?.election.status === 'VOTING_OPEN' &&
    pageData.contest.status === 'OPEN'

  return (
    <section className="page voting-page" aria-labelledby="vote-heading">
      <Link to={electionPath}>Back to election</Link>

      {isLoading && <p role="status">Loading ballot...</p>}

      {!isLoading && pageData === null && pageError !== null && (
        <>
          <h1 id="vote-heading">Ballot unavailable</h1>
          <p role="alert">
            {getErrorMessage(pageError, 'Unable to load this ballot.')}
          </p>
        </>
      )}

      {!isLoading && pageData !== null && (
        <>
          <header>
            <h1 id="vote-heading">{pageData.contest.name}</h1>
            <p>{pageData.election.name}</p>
            <p>{formatEnumLabel(pageData.contest.type)}</p>
          </header>

          {!votingIsOpen && (
            <p>Voting is not currently open for this contest.</p>
          )}

          {votingIsOpen && phase === 'selecting' && (
            <BallotSelection
              options={pageData.contest.options}
              selectedOptionId={selectedOptionId}
              onSelect={setSelectedOptionId}
              onReview={reviewSelection}
            />
          )}

          {votingIsOpen &&
            (phase === 'reviewing' || phase === 'submitting') &&
            selectedOption && (
              <BallotReview
                option={selectedOption}
                isSubmitting={phase === 'submitting'}
                hasIssuedCredential={hasIssuedCredential}
                credentialExpiresAt={credentialExpiresAt}
                error={submissionError}
                onChangeSelection={returnToSelection}
                onSubmit={() => void submitBallot()}
              />
            )}

          {phase === 'accepted' && acceptance?.accepted && (
            <section aria-labelledby="ballot-accepted-heading">
              <h2 id="ballot-accepted-heading">Ballot accepted</h2>
              <p>Your anonymous ballot was accepted.</p>
              <p>
                No candidate selection, ballot identifier, ledger position, or
                receipt is displayed.
              </p>
              <Link to="/dashboard">Return to voter dashboard</Link>
            </section>
          )}

          {phase === 'uncertain' && (
            <section aria-labelledby="ballot-uncertain-heading">
              <h2 id="ballot-uncertain-heading">
                Ballot outcome cannot be confirmed
              </h2>
              <p role="alert">
                The one-time credential may have been issued or consumed, but
                the browser did not receive a definitive confirmation. The
                frontend cannot safely claim that the ballot succeeded or
                failed.
              </p>
              {submissionError !== null && (
                <p>{getErrorMessage(submissionError)}</p>
              )}
              <Link to="/dashboard">Return to voter dashboard</Link>
            </section>
          )}

          {phase === 'failed' && (
            <section aria-labelledby="ballot-failed-heading">
              <h2 id="ballot-failed-heading">Ballot not submitted</h2>
              <p role="alert">
                {getErrorMessage(
                  submissionError,
                  'The ballot could not be submitted.',
                )}
              </p>
              <Link to={electionPath}>Return to election</Link>
            </section>
          )}

          {blocker.state === 'blocked' && (
            <LeaveVotingPageWarning
              onStay={stayOnVotingPage}
              onLeave={leaveVotingPage}
            />
          )}
        </>
      )}
    </section>
  )
}

export default VotePage
