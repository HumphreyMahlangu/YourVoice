import { useEffect, useRef, useState } from 'react'
import { Link, useBlocker, useParams } from 'react-router'
import ApiError from '../api/ApiError'
import { getElectionContests } from '../api/contests'
import { getElection } from '../api/elections'
import {
  castBallot,
  getBallotKey,
  issueVotingCredential,
} from '../api/voting'
import { useAuth } from '../auth/useAuth'
import BallotReview from '../components/voting/BallotReview'
import BallotSelection from '../components/voting/BallotSelection'
import LeaveVotingPageWarning from '../components/voting/LeaveVotingPageWarning'
import type { Contest } from '../types/contest'
import type { Election } from '../types/election'
import type { BallotCastResponse } from '../types/voting'
import {
  blindBallotToken,
  unblindSignature,
  type BlindedBallot,
} from '../utils/blindSignature'
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

// Lives only in a ref: the blinded ballot (so issuance can be retried with
// the same blinded token) and, once unblinded, the ballot signature.
interface HeldCredential {
  ballot: BlindedBallot
  signature: string | null
}

function VotePage() {
  const { electionId, contestId } = useParams<{
    electionId: string
    contestId: string
  }>()
  const { session, logout } = useAuth()
  const credentialRef = useRef<HeldCredential | null>(null)
  const [pageData, setPageData] = useState<VotingPageData | null>(null)
  const [selectedOptionId, setSelectedOptionId] = useState('')
  const [phase, setPhase] = useState<VotingPhase>('selecting')
  const [credentialExpiresAt, setCredentialExpiresAt] = useState<string | null>(
    null,
  )
  const [hasIssuedCredential, setHasIssuedCredential] = useState(false)
  const [acceptance, setAcceptance] = useState<BallotCastResponse | null>(null)
  const [keyFingerprint, setKeyFingerprint] = useState<string | null>(null)
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
    let requestStage: 'credential' | 'ballot' = credentialRef.current?.signature
      ? 'ballot'
      : 'credential'

    try {
      let heldCredential = credentialRef.current

      if (!heldCredential) {
        const ballotKey = await getBallotKey(
          pageData.election.id,
          pageData.contest.id,
        )
        heldCredential = {
          ballot: await blindBallotToken(ballotKey),
          signature: null,
        }
        credentialRef.current = heldCredential
        setHasIssuedCredential(true)
      }

      if (!heldCredential.signature) {
        // Retrying with the same blinded token is safe: the API returns the
        // same signature instead of refusing a second credential.
        const credential = await issueVotingCredential(
          pageData.election.id,
          pageData.contest.id,
          heldCredential.ballot.blindedToken,
          session.accessToken,
        )
        heldCredential.signature = await unblindSignature(
          heldCredential.ballot,
          credential.blindSignature,
        )
        setCredentialExpiresAt(credential.expiresAt)
      }

      requestStage = 'ballot'
      const response = await castBallot({
        contestId: pageData.contest.id,
        contestOptionId: selectedOption.id,
        ballotToken: heldCredential.ballot.ballotToken,
        signature: heldCredential.signature,
      })

      setKeyFingerprint(heldCredential.ballot.keyFingerprint)
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

      const isRetryable =
        !(requestError instanceof ApiError) ||
        requestError.status === 429 ||
        requestError.status >= 500

      if (
        requestStage === 'credential' &&
        requestError instanceof ApiError &&
        requestError.status === 409 &&
        requestError.message.toLowerCase().includes('already been issued')
      ) {
        // A credential was issued for a different blinded token, e.g. in
        // another tab. This page cannot tell whether that ballot was cast.
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

      if (isRetryable && credentialRef.current) {
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
            <p className="voting-page__eyebrow">Ballot / YourVoice</p>
            <h1 id="vote-heading">{pageData.contest.name}</h1>
            <p>{pageData.election.name}</p>
            <p>{formatEnumLabel(pageData.contest.type)}</p>
          </header>

          {votingIsOpen && (
            <div className="vote-progress" role="group" aria-label="Ballot progress">
              <ol>
                {[
                  { label: 'Choose', current: phase === 'selecting' },
                  { label: 'Review', current: phase === 'reviewing' },
                  { label: 'Submit', current: phase === 'submitting' || phase === 'accepted' || phase === 'uncertain' || phase === 'failed' },
                ].map((step, index) => (
                  <li key={step.label} className={step.current ? 'vote-progress__current' : undefined} aria-current={step.current ? 'step' : undefined}>
                    <span>{String(index + 1).padStart(2, '0')}</span>
                    {step.label}
                  </li>
                ))}
              </ol>
            </div>
          )}

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
              <p>
                Your ballot was authorised by a blind signature, so the server
                could check you were eligible without being able to link this
                ballot to you.
                {keyFingerprint && (
                  <>
                    {' '}
                    Contest key fingerprint:{' '}
                    <code>{keyFingerprint.slice(0, 16)}</code>. Every voter in
                    this contest should see the same fingerprint.
                  </>
                )}
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
                A ballot credential for this contest was already issued or used,
                possibly from another tab or device, so this page cannot confirm
                whether your ballot was counted. To protect ballot secrecy the
                system cannot look this up for you.
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
