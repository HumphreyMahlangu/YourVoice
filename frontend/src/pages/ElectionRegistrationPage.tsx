import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import ApiError from '../api/ApiError'
import { getElection } from '../api/elections'
import { registerForElection } from '../api/registrations'
import { useAuth } from '../auth/useAuth'
import ElectionMetadata from '../components/ElectionMetadata'
import type { Election } from '../types/election'
import type { ElectionRegistration } from '../types/registration'
import { formatDateTime, formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

function ElectionRegistrationPage() {
  const { electionId } = useParams<{ electionId: string }>()
  const { session, logout } = useAuth()
  const [election, setElection] = useState<Election | null>(null)
  const [registration, setRegistration] =
    useState<ElectionRegistration | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)

  useEffect(() => {
    if (!electionId) {
      setError(new Error('Election identifier is missing.'))
      setIsLoading(false)
      return
    }

    const requestedElectionId = electionId
    const abortController = new AbortController()

    async function loadRegistrationPage() {
      setElection(null)
      setError(null)
      setIsLoading(true)

      try {
        const response = await getElection(
          requestedElectionId,
          abortController.signal,
        )

        if (!abortController.signal.aborted) {
          setElection(response)
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

    void loadRegistrationPage()

    return () => abortController.abort()
  }, [electionId])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!electionId || !session) {
      return
    }

    setError(null)
    setIsSubmitting(true)

    try {
      const response = await registerForElection(
        electionId,
        {},
        session.accessToken,
      )
      setRegistration(response)
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        logout()
        return
      }

      setError(requestError)
    } finally {
      setIsSubmitting(false)
    }
  }

  const electionPath = electionId ? `/elections/${electionId}` : '/elections'

  return (
    <section className="page workflow-page" aria-labelledby="election-registration-heading">
      <Link to={electionPath}>Back to election</Link>
      <h1 id="election-registration-heading">Election registration</h1>

      {isLoading && <p role="status">Loading registration details...</p>}

      {!isLoading && election === null && error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to load election registration.')}
        </p>
      )}

      {!isLoading && election !== null && (
        <>
          <section aria-labelledby="registration-election-heading">
            <h2 id="registration-election-heading">
              {election.name}
            </h2>
            <ElectionMetadata election={election} />
          </section>

          {registration !== null ? (
            <section aria-labelledby="registration-complete-heading">
              <h2 id="registration-complete-heading">
                Registration complete
              </h2>
              <p>Your election registration was accepted.</p>
              <dl>
                <dt>Election</dt>
                <dd>{registration.electionName}</dd>

                <dt>Status</dt>
                <dd>{formatEnumLabel(registration.status)}</dd>

                <dt>Voting district</dt>
                <dd>
                  {registration.votingDistrictName} ({
                    registration.votingDistrictCode
                  })
                </dd>

                <dt>Registered</dt>
                <dd>
                  <time dateTime={registration.registeredAt}>
                    {formatDateTime(registration.registeredAt)}
                  </time>
                </dd>
              </dl>
              <Link to="/dashboard">Return to voter dashboard</Link>
            </section>
          ) : election.status !== 'REGISTRATION_OPEN' ? (
            <p>Registration is not currently open for this election.</p>
          ) : (
            <form onSubmit={handleSubmit} autoComplete="off">
              {error !== null && (
                <p role="alert">
                  {getErrorMessage(
                    error,
                    'Unable to register for this election.',
                  )}
                </p>
              )}

              <p>
                You will be registered in the voting district on your voter
                profile, which you chose when you created your account. That
                district decides which contests you can vote in. Your identity
                details are taken from your protected voter profile.
              </p>

              <button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Registering...' : 'Register for election'}
              </button>
            </form>
          )}
        </>
      )}
    </section>
  )
}

export default ElectionRegistrationPage
