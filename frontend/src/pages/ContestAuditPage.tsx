import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getContestAudit } from '../api/audit'
import type { ContestAudit } from '../types/audit'
import { formatDateTime, formatNumber } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

function ContestAuditPage() {
  const { electionId, contestId } = useParams<{
    electionId: string
    contestId: string
  }>()
  const [audit, setAudit] = useState<ContestAudit | null>(null)
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

    async function loadAudit() {
      setAudit(null)
      setError(null)
      setIsLoading(true)

      try {
        const response = await getContestAudit(
          requestedElectionId,
          requestedContestId,
          abortController.signal,
        )

        if (!abortController.signal.aborted) {
          setAudit(response)
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

    void loadAudit()

    return () => abortController.abort()
  }, [contestId, electionId])

  const resultsPath =
    electionId && contestId
      ? `/elections/${electionId}/contests/${contestId}/results`
      : '/elections'

  return (
    <section className="page verification-page" aria-labelledby="contest-audit-heading">
      <Link to={resultsPath}>Back to results</Link>

      {isLoading && <p role="status">Verifying ledger integrity...</p>}

      {!isLoading && error !== null && (
        <>
          <h1 id="contest-audit-heading">Audit unavailable</h1>
          <p role="alert">
            {getErrorMessage(error, 'Unable to verify the contest ledger.')}
          </p>
        </>
      )}

      {!isLoading && error === null && audit !== null && (
        <>
          <h1 id="contest-audit-heading">Contest ledger verification</h1>
          <p role={audit.chainValid ? 'status' : 'alert'}>
            {audit.chainValid
              ? 'Both ledger hash chains are valid and contain the same ballots.'
              : 'The ledger has integrity violations.'}
          </p>

          <dl>
            <dt>Internal ledger entries verified</dt>
            <dd>{formatNumber(audit.ledgerEntryCount)}</dd>

            <dt>Published ledger entries verified</dt>
            <dd>{formatNumber(audit.publishedLedgerEntryCount)}</dd>

            <dt>Verification time</dt>
            <dd>
              <time dateTime={audit.verifiedAt}>
                {formatDateTime(audit.verifiedAt)}
              </time>
            </dd>

            <dt>Stored next internal ledger index</dt>
            <dd>
              {audit.storedNextLedgerIndex === null
                ? 'Not available'
                : formatNumber(audit.storedNextLedgerIndex)}
            </dd>

            <dt>Genesis hash</dt>
            <dd>
              <code>{audit.genesisHash}</code>
            </dd>

            <dt>Computed internal head hash</dt>
            <dd>
              <code>{audit.computedHeadHash}</code>
            </dd>

            <dt>Stored internal head hash</dt>
            <dd>
              <code>{audit.storedHeadHash}</code>
            </dd>

            <dt>Computed published head hash</dt>
            <dd>
              <code>{audit.computedPublishedHeadHash}</code>
            </dd>

            <dt>Stored published head hash</dt>
            <dd>
              <code>{audit.publishedHeadHash}</code>
            </dd>
          </dl>
          <p>
            The internal ledger records ballots in the order they were cast and
            stays on the server. The published ledger holds the same ballots in a
            random order, so you can verify it yourself without learning who voted
            when.
          </p>

          <section aria-labelledby="violations-heading">
            <h2 id="violations-heading">Integrity violations</h2>
            {audit.violations.length === 0 ? (
              <p>No integrity violations were found.</p>
            ) : (
              <ul>
                {audit.violations.map((violation, index) => (
                  <li key={`${index}-${violation}`}>{violation}</li>
                ))}
              </ul>
            )}
          </section>

          <p>
            <Link
              to={`/elections/${audit.electionId}/contests/${audit.contestId}/ledger`}
            >
              View public ledger entries
            </Link>
          </p>
        </>
      )}
    </section>
  )
}

export default ContestAuditPage
