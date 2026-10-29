import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { getElection } from '../api/elections'
import { getMyBallots, getProfile } from '../api/profiles'
import { registerForElection } from '../api/registrations'
import { useAuth } from '../auth/useAuth'
import ElectionMetadata from '../components/ElectionMetadata'
import RegisteredArea from '../components/RegisteredArea'
import type { Election } from '../types/election'
import type { ProfileVerification, VoterBallot } from '../types/profile'
import type { ElectionRegistration } from '../types/registration'
import { getVoterErrorMessage } from '../utils/voterErrors'

export default function ElectionRegistrationPage() {
  const { electionId } = useParams<{ electionId: string }>()
  const { session } = useAuth()
  const [data, setData] = useState<{ election: Election; profile: ProfileVerification; ballots: VoterBallot[] } | null>(null)
  const [registration, setRegistration] = useState<ElectionRegistration | null>(null)
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (!session || !electionId) return
    const controller = new AbortController(); setLoading(true); setError(null)
    Promise.all([getElection(electionId, controller.signal), getProfile(session.accessToken, controller.signal), getMyBallots(session.accessToken, controller.signal, electionId)])
      .then(([election, profile, ballots]) => { if (!controller.signal.aborted) setData({ election, profile, ballots }) })
      .catch((failure) => { if (!controller.signal.aborted) setError(failure) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [session, electionId, attempt])
  async function submit(event: FormEvent) {
    event.preventDefault(); if (!session || !electionId) return
    setBusy(true); setError(null)
    try { setRegistration(await registerForElection(electionId, {}, session.accessToken)) }
    catch (failure) { setError(failure) }
    finally { setBusy(false) }
  }
  const canRegister = data?.ballots.some((b) => b.canRegister) ?? false
  const registered = data?.ballots.some((b) => b.registrationVerified) ?? false
  return <section className="page workflow-page" aria-labelledby="registration-heading">
    <Link to={electionId ? `/elections/${electionId}` : '/elections'}>Back to election</Link><h1 id="registration-heading">Election registration</h1>
    {loading && <p role="status">Loading registration details...</p>}
    {error !== null && <div><p role="alert">{getVoterErrorMessage(error, 'Unable to load or complete election registration. Try again.')}</p><button type="button" disabled={loading || busy} onClick={() => setAttempt((n) => n + 1)}>Retry registration details</button></div>}
    {!loading && data && <>
      <h2>{data.election.name}</h2><ElectionMetadata election={data.election} />
      {registration ? <section aria-labelledby="registration-complete-heading"><h2 id="registration-complete-heading">Registration complete</h2><p>Your verified simulation registration was accepted.</p><p>Saved area: <RegisteredArea province={registration.province} municipality={registration.municipality} wardNumber={registration.wardNumber} /></p><Link to="/dashboard">Find your ballots</Link></section> : registered ? <p role="status">You already have a verified registration for this election. <Link to="/dashboard">Find your ballots</Link>.</p> : <>
        <p>Fictional verification only. Your server-side profile supplies your verified attributes; you do not select an area again.</p>
        <p>Current profile area: <RegisteredArea province={data.profile.location.province} municipality={data.profile.location.municipality} wardNumber={data.profile.location.wardNumber} /></p>
        <p>This election saves those verified attributes when you join. Later corrections apply to future registrations. National identity-only registration does not grant local ballots.</p>
        {data.ballots.length === 0 && <p>No published contests are configured for this election.</p>}
        {!canRegister && <p role="status">{data.ballots[0]?.message ?? 'Registration is unavailable.'} <Link to="/profile">Review your simulation profile</Link>.</p>}
        <form onSubmit={(e) => void submit(e)}><button type="submit" disabled={!canRegister || busy}>{busy ? 'Registering...' : data.ballots.some((b) => b.registered) ? 'Confirm existing registration' : 'Register for election'}</button></form>
      </>}
    </>}
  </section>
}
