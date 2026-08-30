import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { getContestLedger } from '../api/audit'
import type { ContestLedgerEntry } from '../types/audit'
import { formatNumber } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

function ContestLedgerPage() {
  const { electionId, contestId } = useParams<{
    electionId: string
    contestId: string
  }>()
  const [entries, setEntries] = useState<ContestLedgerEntry[]>([])
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

    async function loadLedger() {
      setEntries([])
      setError(null)
      setIsLoading(true)

      try {
        const response = await getContestLedger(
          requestedElectionId,
          requestedContestId,
          abortController.signal,
        )

        if (!abortController.signal.aborted) {
          setEntries(response)
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

    void loadLedger()

    return () => abortController.abort()
  }, [contestId, electionId])

  const auditPath =
    electionId && contestId
      ? `/elections/${electionId}/contests/${contestId}/audit`
      : '/elections'

  return (
    <section className="page verification-page" aria-labelledby="contest-ledger-heading">
      <Link to={auditPath}>Back to ledger verification</Link>
      <h1 id="contest-ledger-heading">Public contest ledger</h1>
      <p>
        Entries are anonymous and expose only a coarse recording date. They do
        not contain voter identities or exact voting times.
      </p>

      {isLoading && <p role="status">Loading public ledger entries...</p>}

      {!isLoading && error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to load the public ledger.')}
        </p>
      )}

      {!isLoading && error === null && entries.length === 0 && (
        <p>The public ledger does not contain any entries.</p>
      )}

      {!isLoading && error === null && entries.length > 0 && (
        <table>
          <caption>
            {formatNumber(entries.length)} anonymous ledger entries
          </caption>
          <thead>
            <tr>
              <th scope="col">Index</th>
              <th scope="col">Option identifier</th>
              <th scope="col">Recorded date</th>
              <th scope="col">Previous hash</th>
              <th scope="col">Current hash</th>
              <th scope="col">Nonce</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.ledgerIndex}>
                <th scope="row">{formatNumber(entry.ledgerIndex)}</th>
                <td>
                  <code>{entry.contestOptionId}</code>
                </td>
                <td>
                  <time dateTime={entry.recordedDate}>{entry.recordedDate}</time>
                </td>
                <td>
                  <code>{entry.previousHash}</code>
                </td>
                <td>
                  <code>{entry.currentHash}</code>
                </td>
                <td>
                  <code>{entry.nonce}</code>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

export default ContestLedgerPage
