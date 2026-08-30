import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getContestResult } from '../api/results'
import type { ContestResult } from '../types/result'
import {
  formatDateTime,
  formatEnumLabel,
  formatNumber,
  formatPercentage,
} from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

function ContestResultsPage() {
  const { electionId, contestId } = useParams<{
    electionId: string
    contestId: string
  }>()
  const [result, setResult] = useState<ContestResult | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (!electionId || !contestId) {
      setError(new Error('Election or contest identifier is missing.'))
      setIsLoading(false)
      return
    }

    const requestedElectionId = electionId
    const requestedContestId = contestId
    const abortController = new AbortController()

    async function loadResult() {
      setResult(null)
      setError(null)
      setIsLoading(true)

      try {
        const response = await getContestResult(
          requestedElectionId,
          requestedContestId,
          abortController.signal,
        )

        if (!abortController.signal.aborted) {
          setResult(response)
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

    void loadResult()

    return () => abortController.abort()
  }, [contestId, electionId])

  const electionPath = electionId ? `/elections/${electionId}` : '/elections'

  return (
    <section className="page verification-page" aria-labelledby="contest-results-heading">
      <Link to={electionPath}>Back to election</Link>

      {isLoading && <p role="status">Loading final results...</p>}

      {!isLoading && error !== null && (
        <>
          <h1 id="contest-results-heading">Results unavailable</h1>
          <p role="alert">
            {getErrorMessage(error, 'Unable to load the contest results.')}
          </p>
        </>
      )}

      {!isLoading && error === null && result !== null && (
        <>
          <header>
            <h1 id="contest-results-heading">{result.contestName} results</h1>
            <p>{formatEnumLabel(result.contestType)}</p>
          </header>

          <dl>
            <dt>Registered voters</dt>
            <dd>{formatNumber(result.registeredVoterCount)}</dd>

            <dt>Ballots cast</dt>
            <dd>{formatNumber(result.ballotsCast)}</dd>

            <dt>Valid votes</dt>
            <dd>{formatNumber(result.validVotes)}</dd>

            <dt>Blank ballots</dt>
            <dd>{formatNumber(result.blankBallots)}</dd>

            <dt>Spoilt ballots</dt>
            <dd>{formatNumber(result.spoiltBallots)}</dd>

            <dt>Turnout</dt>
            <dd>{formatPercentage(result.turnoutPercentage)}</dd>

            <dt>Results generated</dt>
            <dd>
              <time dateTime={result.generatedAt}>
                {formatDateTime(result.generatedAt)}
              </time>
            </dd>

            <dt>Ledger head hash</dt>
            <dd>
              <code>{result.ledgerHeadHash}</code>
            </dd>
          </dl>

          <nav aria-label="Result verification">
            <ul>
              <li>
                <Link
                  to={`/elections/${result.electionId}/contests/${result.contestId}/audit`}
                >
                  Verify ledger integrity
                </Link>
              </li>
              <li>
                <Link
                  to={`/elections/${result.electionId}/contests/${result.contestId}/ledger`}
                >
                  View public ledger
                </Link>
              </li>
            </ul>
          </nav>

          <section aria-labelledby="option-results-heading">
            <h2 id="option-results-heading">Valid vote totals</h2>

            {result.options.length === 0 ? (
              <p>No valid-vote options were counted.</p>
            ) : (
              <ol>
                {result.options.map((option) => (
                  <li key={option.contestOptionId}>
                    <article aria-labelledby={`result-${option.contestOptionId}`}>
                      <h3 id={`result-${option.contestOptionId}`}>
                        {option.name}
                      </h3>
                      <dl>
                        <dt>Option type</dt>
                        <dd>{formatEnumLabel(option.optionType)}</dd>

                        <dt>Votes</dt>
                        <dd>{formatNumber(option.voteCount)}</dd>

                        <dt>Share of valid votes</dt>
                        <dd>
                          {formatPercentage(option.percentageOfValidVotes)}
                        </dd>
                      </dl>
                      {option.leading && <p>Highest vote total</p>}
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

export default ContestResultsPage
