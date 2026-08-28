import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import ApiError from '../api/ApiError'
import { getVotingDistricts } from '../api/districts'
import { getElection } from '../api/elections'
import { registerForElection } from '../api/registrations'
import { useAuth } from '../auth/useAuth'
import ElectionMetadata from '../components/ElectionMetadata'
import type { VotingDistrict } from '../types/district'
import type { Election } from '../types/election'
import type { ElectionRegistration } from '../types/registration'
import { formatDateTime, formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

interface RegistrationPageData {
  election: Election
  districts: VotingDistrict[]
}

function ElectionRegistrationPage() {
  const { electionId } = useParams<{ electionId: string }>()
  const { session, logout } = useAuth()
  const [pageData, setPageData] = useState<RegistrationPageData | null>(null)
  const [votingDistrictId, setVotingDistrictId] = useState('')
  const [registration, setRegistration] =
    useState<ElectionRegistration | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [fieldErrors, setFieldErrors] = useState<
    Readonly<Record<string, string>>
  >({})

  useEffect(() => {
    if (!electionId) {
      setError(new Error('Election identifier is missing.'))
      setIsLoading(false)
      return
    }

    const requestedElectionId = electionId
    const abortController = new AbortController()

    async function loadRegistrationPage() {
      setPageData(null)
      setError(null)
      setIsLoading(true)

      try {
        const [election, districtResponse] = await Promise.all([
          getElection(requestedElectionId, abortController.signal),
          getVotingDistricts(abortController.signal),
        ])
        const districts = districtResponse.toSorted((left, right) =>
          `${left.province}-${left.municipality}-${left.code}`.localeCompare(
            `${right.province}-${right.municipality}-${right.code}`,
          ),
        )

        if (!abortController.signal.aborted) {
          setPageData({ election, districts })
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
    setFieldErrors({})
    setIsSubmitting(true)

    try {
      const response = await registerForElection(
        electionId,
        { votingDistrictId },
        session.accessToken,
      )
      setRegistration(response)
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        logout()
        return
      }

      setError(requestError)

      if (requestError instanceof ApiError) {
        setFieldErrors(requestError.fieldErrors)
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  const electionPath = electionId ? `/elections/${electionId}` : '/elections'
  const districtError = fieldErrors.votingDistrictId

  return (
    <section aria-labelledby="election-registration-heading">
      <Link to={electionPath}>Back to election</Link>
      <h1 id="election-registration-heading">Election registration</h1>

      {isLoading && <p role="status">Loading registration details...</p>}

      {!isLoading && pageData === null && error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to load election registration.')}
        </p>
      )}

      {!isLoading && pageData !== null && (
        <>
          <section aria-labelledby="registration-election-heading">
            <h2 id="registration-election-heading">
              {pageData.election.name}
            </h2>
            <ElectionMetadata election={pageData.election} />
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
          ) : pageData.election.status !== 'REGISTRATION_OPEN' ? (
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

              <div>
                <label htmlFor="voting-district">Voting district</label>
                <p id="voting-district-help">
                  Confirm the district that applies to this election. Your
                  identity details are taken from your protected voter profile.
                </p>
                <select
                  id="voting-district"
                  name="votingDistrictId"
                  required
                  value={votingDistrictId}
                  aria-invalid={districtError ? true : undefined}
                  aria-describedby={
                    districtError
                      ? 'voting-district-help voting-district-error'
                      : 'voting-district-help'
                  }
                  onChange={(event) =>
                    setVotingDistrictId(event.target.value)
                  }
                >
                  <option value="">Select your voting district</option>
                  {pageData.districts.map((district) => (
                    <option key={district.id} value={district.id}>
                      {district.code} — {district.name}, {district.municipality},
                      ward {district.wardNumber}
                    </option>
                  ))}
                </select>
                {districtError && (
                  <p id="voting-district-error">{districtError}</p>
                )}
                {pageData.districts.length === 0 && (
                  <p>No voting districts are currently available.</p>
                )}
              </div>

              <button
                type="submit"
                disabled={isSubmitting || pageData.districts.length === 0}
              >
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
