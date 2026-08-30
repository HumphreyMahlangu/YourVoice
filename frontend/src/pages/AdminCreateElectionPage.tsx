import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import ApiError from '../api/ApiError'
import { createElection } from '../api/adminManagement'
import { useAuth } from '../auth/useAuth'
import type { Election, ElectionType } from '../types/election'
import { formatDateTime, formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

const ELECTION_TYPES: ElectionType[] = [
  'NATIONAL',
  'PROVINCIAL',
  'MUNICIPAL',
]

function toUtcInstant(localDateTime: string) {
  return new Date(localDateTime).toISOString()
}

function AdminCreateElectionPage() {
  const { session, logout } = useAuth()
  const [name, setName] = useState('')
  const [type, setType] = useState<ElectionType>('NATIONAL')
  const [registrationStartAt, setRegistrationStartAt] = useState('')
  const [registrationEndAt, setRegistrationEndAt] = useState('')
  const [votingStartAt, setVotingStartAt] = useState('')
  const [votingEndAt, setVotingEndAt] = useState('')
  const [createdElection, setCreatedElection] = useState<Election | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [fieldErrors, setFieldErrors] = useState<
    Readonly<Record<string, string>>
  >({})

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!session) {
      return
    }

    setCreatedElection(null)
    setError(null)
    setFieldErrors({})
    setIsSubmitting(true)

    try {
      const registrationStart = new Date(registrationStartAt)
      const registrationEnd = new Date(registrationEndAt)
      const votingStart = new Date(votingStartAt)
      const votingEnd = new Date(votingEndAt)

      if (registrationStart >= registrationEnd) {
        throw new Error('Registration must start before it ends.')
      }

      if (registrationEnd > votingStart) {
        throw new Error('Registration must end before voting starts.')
      }

      if (votingStart >= votingEnd) {
        throw new Error('Voting must start before it ends.')
      }

      const election = await createElection(
        {
          name,
          type,
          registrationStartAt: toUtcInstant(registrationStartAt),
          registrationEndAt: toUtcInstant(registrationEndAt),
          votingStartAt: toUtcInstant(votingStartAt),
          votingEndAt: toUtcInstant(votingEndAt),
        },
        session.accessToken,
      )
      setCreatedElection(election)
      setName('')
      setType('NATIONAL')
      setRegistrationStartAt('')
      setRegistrationEndAt('')
      setVotingStartAt('')
      setVotingEndAt('')
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

  const nameError = fieldErrors.name
  const typeError = fieldErrors.type
  const registrationStartError = fieldErrors.registrationStartAt
  const registrationEndError = fieldErrors.registrationEndAt
  const votingStartError = fieldErrors.votingStartAt
  const votingEndError = fieldErrors.votingEndAt

  return (
    <section className="page admin-page workflow-page" aria-labelledby="create-election-heading">
      <Link to="/admin">Back to admin dashboard</Link>
      <h1 id="create-election-heading">Create election</h1>
      <p>
        New elections begin in draft status. Times are entered in your local
        timezone and sent to the API as UTC.
      </p>

      {createdElection !== null && (
        <section aria-labelledby="election-created-heading">
          <h2 id="election-created-heading">Election created</h2>
          <p role="status">{createdElection.name} is ready for configuration.</p>
          <dl>
            <dt>Status</dt>
            <dd>{formatEnumLabel(createdElection.status)}</dd>

            <dt>Registration opens</dt>
            <dd>{formatDateTime(createdElection.registrationStartAt)}</dd>

            <dt>Voting opens</dt>
            <dd>{formatDateTime(createdElection.votingStartAt)}</dd>
          </dl>
          <p>
            <Link to={`/admin/elections/${createdElection.id}/configure`}>
              Configure contests and ballot options
            </Link>
          </p>
          <p>
            <Link to={`/elections/${createdElection.id}`}>View election</Link>
          </p>
        </section>
      )}

      {error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to create the election.')}
        </p>
      )}

      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="election-name">Election name</label>
          <input
            id="election-name"
            name="name"
            type="text"
            required
            maxLength={160}
            value={name}
            aria-invalid={nameError ? true : undefined}
            aria-describedby={nameError ? 'election-name-error' : undefined}
            onChange={(event) => setName(event.target.value)}
          />
          {nameError && <p id="election-name-error">{nameError}</p>}
        </div>

        <div>
          <label htmlFor="election-type">Election type</label>
          <select
            id="election-type"
            name="type"
            required
            value={type}
            aria-invalid={typeError ? true : undefined}
            aria-describedby={typeError ? 'election-type-error' : undefined}
            onChange={(event) => setType(event.target.value as ElectionType)}
          >
            {ELECTION_TYPES.map((electionType) => (
              <option key={electionType} value={electionType}>
                {formatEnumLabel(electionType)}
              </option>
            ))}
          </select>
          {typeError && <p id="election-type-error">{typeError}</p>}
        </div>

        <fieldset>
          <legend>Registration window</legend>
          <div>
            <label htmlFor="registration-start">Registration starts</label>
            <input
              id="registration-start"
              name="registrationStartAt"
              type="datetime-local"
              required
              value={registrationStartAt}
              aria-invalid={registrationStartError ? true : undefined}
              aria-describedby={
                registrationStartError
                  ? 'registration-start-error'
                  : undefined
              }
              onChange={(event) => setRegistrationStartAt(event.target.value)}
            />
            {registrationStartError && (
              <p id="registration-start-error">{registrationStartError}</p>
            )}
          </div>

          <div>
            <label htmlFor="registration-end">Registration ends</label>
            <input
              id="registration-end"
              name="registrationEndAt"
              type="datetime-local"
              required
              min={registrationStartAt || undefined}
              value={registrationEndAt}
              aria-invalid={registrationEndError ? true : undefined}
              aria-describedby={
                registrationEndError ? 'registration-end-error' : undefined
              }
              onChange={(event) => setRegistrationEndAt(event.target.value)}
            />
            {registrationEndError && (
              <p id="registration-end-error">{registrationEndError}</p>
            )}
          </div>
        </fieldset>

        <fieldset>
          <legend>Voting window</legend>
          <div>
            <label htmlFor="voting-start">Voting starts</label>
            <input
              id="voting-start"
              name="votingStartAt"
              type="datetime-local"
              required
              min={registrationEndAt || undefined}
              value={votingStartAt}
              aria-invalid={votingStartError ? true : undefined}
              aria-describedby={
                votingStartError ? 'voting-start-error' : undefined
              }
              onChange={(event) => setVotingStartAt(event.target.value)}
            />
            {votingStartError && (
              <p id="voting-start-error">{votingStartError}</p>
            )}
          </div>

          <div>
            <label htmlFor="voting-end">Voting ends</label>
            <input
              id="voting-end"
              name="votingEndAt"
              type="datetime-local"
              required
              min={votingStartAt || undefined}
              value={votingEndAt}
              aria-invalid={votingEndError ? true : undefined}
              aria-describedby={
                votingEndError ? 'voting-end-error' : undefined
              }
              onChange={(event) => setVotingEndAt(event.target.value)}
            />
            {votingEndError && (
              <p id="voting-end-error">{votingEndError}</p>
            )}
          </div>
        </fieldset>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Creating election...' : 'Create draft election'}
        </button>
      </form>
    </section>
  )
}

export default AdminCreateElectionPage
