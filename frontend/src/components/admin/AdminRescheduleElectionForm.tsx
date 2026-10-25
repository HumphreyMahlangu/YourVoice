import { useState, type FormEvent } from 'react'
import ApiError from '../../api/ApiError'
import { updateElection } from '../../api/adminManagement'
import type { Election } from '../../types/election'
import { toDateTimeLocalValue, toUtcInstant } from '../../utils/dateTimeInput'
import { getAdminErrorMessage } from '../../utils/adminErrors'

interface AdminRescheduleElectionFormProps {
  election: Election
  authToken: string
  onSaved: (election: Election) => void
  onUnauthorized: () => void
}

/**
 * Renames or reschedules a DRAFT election. This is also how an election whose
 * registration window passed before its ballot was ready gets a new window.
 */
function AdminRescheduleElectionForm({
  election,
  authToken,
  onSaved,
  onUnauthorized,
}: AdminRescheduleElectionFormProps) {
  const [name, setName] = useState(election.name)
  const [registrationStartAt, setRegistrationStartAt] = useState(
    toDateTimeLocalValue(election.registrationStartAt),
  )
  const [registrationEndAt, setRegistrationEndAt] = useState(
    toDateTimeLocalValue(election.registrationEndAt),
  )
  const [votingStartAt, setVotingStartAt] = useState(
    toDateTimeLocalValue(election.votingStartAt),
  )
  const [votingEndAt, setVotingEndAt] = useState(
    toDateTimeLocalValue(election.votingEndAt),
  )
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [saved, setSaved] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setSaved(false)
    setIsSaving(true)

    try {
      const updated = await updateElection(
        election.id,
        {
          name,
          registrationStartAt: toUtcInstant(registrationStartAt),
          registrationEndAt: toUtcInstant(registrationEndAt),
          votingStartAt: toUtcInstant(votingStartAt),
          votingEndAt: toUtcInstant(votingEndAt),
        },
        authToken,
      )
      setSaved(true)
      onSaved(updated)
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        onUnauthorized()
        return
      }
      setError(requestError)
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <section aria-labelledby="reschedule-heading">
      <h2 id="reschedule-heading">Rename or reschedule</h2>
      <p>
        Draft elections can be renamed and rescheduled. The registration window
        must end in the future. Times are in your browser&apos;s time zone.
      </p>

      {saved && <p role="status">The election schedule was updated.</p>}
      {error !== null && (
        <p role="alert">
          {getAdminErrorMessage(error, 'Unable to update the election.')}
        </p>
      )}

      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="reschedule-name">Election name</label>
          <input
            id="reschedule-name"
            required
            maxLength={160}
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </div>
        <div>
          <label htmlFor="reschedule-registration-start">Registration opens</label>
          <input
            id="reschedule-registration-start"
            type="datetime-local"
            required
            value={registrationStartAt}
            onChange={(event) => setRegistrationStartAt(event.target.value)}
          />
        </div>
        <div>
          <label htmlFor="reschedule-registration-end">Registration closes</label>
          <input
            id="reschedule-registration-end"
            type="datetime-local"
            required
            value={registrationEndAt}
            onChange={(event) => setRegistrationEndAt(event.target.value)}
          />
        </div>
        <div>
          <label htmlFor="reschedule-voting-start">Voting opens</label>
          <input
            id="reschedule-voting-start"
            type="datetime-local"
            required
            value={votingStartAt}
            onChange={(event) => setVotingStartAt(event.target.value)}
          />
        </div>
        <div>
          <label htmlFor="reschedule-voting-end">Voting closes</label>
          <input
            id="reschedule-voting-end"
            type="datetime-local"
            required
            value={votingEndAt}
            onChange={(event) => setVotingEndAt(event.target.value)}
          />
        </div>
        <button type="submit" disabled={isSaving}>
          {isSaving ? 'Saving schedule...' : 'Save schedule'}
        </button>
      </form>
    </section>
  )
}

export default AdminRescheduleElectionForm
