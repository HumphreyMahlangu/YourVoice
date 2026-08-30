import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getElections } from '../api/elections'
import ElectionMetadata from '../components/ElectionMetadata'
import type { Election } from '../types/election'
import { getErrorMessage } from '../utils/getErrorMessage'

function ElectionsPage() {
  const [elections, setElections] = useState<Election[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    const abortController = new AbortController()

    async function loadElections() {
      try {
        const response = await getElections(abortController.signal)

        if (!abortController.signal.aborted) {
          setElections(response)
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

    void loadElections()

    return () => abortController.abort()
  }, [])

  return (
    <section className="page directory-page" aria-labelledby="elections-heading">
      <p className="eyebrow">Public election directory</p>
      <h1 id="elections-heading">Elections</h1>
      <p>Review election timelines, published contests, and verified outcomes.</p>

      {isLoading && <p role="status">Loading elections...</p>}

      {!isLoading && error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to load elections.')}
        </p>
      )}

      {!isLoading && error === null && elections.length === 0 && (
        <p>No elections are currently available.</p>
      )}

      {!isLoading && error === null && elections.length > 0 && (
        <ul>
          {elections.map((election) => (
            <li key={election.id}>
              <article aria-labelledby={`election-${election.id}`}>
                <h2 id={`election-${election.id}`}>
                  <Link to={`/elections/${election.id}`}>{election.name}</Link>
                </h2>
                <ElectionMetadata election={election} />
              </article>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

export default ElectionsPage
