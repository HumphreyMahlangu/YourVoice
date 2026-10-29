import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { checkProfile, getCorrections, getProfile, requestCorrection } from '../api/profiles'
import { useAuth } from '../auth/useAuth'
import LocationFields from '../components/LocationFields'
import RegisteredArea from '../components/RegisteredArea'
import type { LocationCorrection, LocationSelection, ProfileVerification } from '../types/profile'
import { formatDateTime, formatEnumLabel } from '../utils/formatters'
import { getVoterErrorMessage } from '../utils/voterErrors'

export default function ProfilePage() {
  const { session } = useAuth()
  const [profile, setProfile] = useState<ProfileVerification | null>(null)
  const [corrections, setCorrections] = useState<LocationCorrection[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [attempt, setAttempt] = useState(0)
  const [busy, setBusy] = useState(false)
  const [location, setLocation] = useState<LocationSelection>({})
  const [reason, setReason] = useState('')
  const [notice, setNotice] = useState('')
  useEffect(() => {
    if (!session) return
    const controller = new AbortController()
    setLoading(true); setError(null)
    Promise.all([getProfile(session.accessToken, controller.signal), getCorrections(session.accessToken, controller.signal)])
      .then(([p, requests]) => { if (!controller.signal.aborted) { setProfile(p); setCorrections(requests) } })
      .catch((failure) => { if (!controller.signal.aborted) setError(failure) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [session, attempt])
  async function check() {
    if (!session) return
    setBusy(true); setError(null); setNotice('')
    try { setProfile(await checkProfile(session.accessToken)); setNotice('Fictional source check finished. Review your status below.') }
    catch (failure) { setError(failure) }
    finally { setBusy(false) }
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); if (!session) return
    setBusy(true); setError(null); setNotice('')
    try { const row = await requestCorrection(session.accessToken, location, reason); setCorrections((rows) => [row, ...rows]); setNotice('Your area correction is awaiting administrator review. Your current eligibility has not changed.'); setReason('') }
    catch (failure) { setError(failure) }
    finally { setBusy(false) }
  }
  const open = corrections.some((r) => r.status === 'PENDING' || r.status === 'APPROVED')
  return <section className="page workflow-page" aria-labelledby="profile-heading">
    <Link to="/dashboard">Back to dashboard</Link><h1 id="profile-heading">Your simulation profile</h1>
    <p className="simulation-notice">Fictional simulation verification. No IEC or government voter-roll connection is used. Signing in authenticates your account; it does not verify your identity or registered area.</p>
    {loading && <p role="status">Loading your profile...</p>}
    {error !== null && <div><p role="alert">{getVoterErrorMessage(error, 'We could not load your profile. Try again.')}</p><button type="button" disabled={loading || busy} onClick={() => setAttempt((n) => n + 1)}>Retry loading profile</button></div>}
    {notice && <p role="status">{notice}</p>}
    {!loading && profile && <>
      <section aria-labelledby="verification-heading"><h2 id="verification-heading">Verification progress</h2>
        <dl><dt>Name</dt><dd>{profile.fullName}</dd><dt>Identity</dt><dd>{formatEnumLabel(profile.identityStatus)}</dd><dt>Registered location</dt><dd>{formatEnumLabel(profile.locationStatus)}</dd><dt>Fictional source reference</dt><dd>{profile.sourceReference ?? 'No matching record yet'}</dd></dl>
        <p role="status">{profile.message}</p>
        <p>Only an administrator-configured fictional source can verify details. Ask the simulation administrator to review an unmatched or rejected record, then check again.</p>
        <button type="button" onClick={() => void check()} disabled={busy}>{busy ? 'Checking...' : 'Check fictional verification'}</button>
        <h3>Recorded area summary</h3>
        <p><RegisteredArea province={profile.location.province} municipality={profile.location.municipality} wardNumber={profile.location.wardNumber} /></p>
        {profile.effectiveAt && <p>Effective <time dateTime={profile.effectiveAt}>{formatDateTime(profile.effectiveAt)}</time>.</p>}
        <p>We use your registered area to show the ballots you can vote in. You cast your ballot online. National ballots need verified identity; local ballots also require their matching verified area attributes.</p>
        <Link to="/dashboard">Find your eligible ballots</Link>
      </section>
      <section aria-labelledby="correction-heading"><h2 id="correction-heading">Request an area correction</h2>
        <p>Approval must match the fictional verification source. An approved change takes effect on its recorded date and applies to future election registrations. Elections you have already joined keep their saved area, including after credential issuance.</p>
        {profile.identityStatus !== 'VERIFIED' ? <p>Complete your fictional identity check before requesting a correction.</p> : open ? <p role="status">A correction is awaiting review or its effective date. You can have one open request at a time.</p> : <form onSubmit={(e) => void submit(e)}>
          <LocationFields id="correction" value={location} onChange={setLocation} disabled={busy} />
          <label htmlFor="correction-reason">Reason for correction</label><textarea id="correction-reason" required maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} disabled={busy} />
          <p>Describe the fictional area issue only. Do not include identity numbers, documents or ballot choices. Closed request reasons are redacted after 90 days.</p>
          <button type="submit" disabled={busy}>{busy ? 'Sending...' : 'Request correction'}</button>
        </form>}
        <h3>Your correction requests</h3>
        {corrections.length === 0 ? <p>No area correction requests.</p> : <ul className="profile-list">{corrections.map((r) => <li key={r.id}><article>
          <h4>{formatEnumLabel(r.status)}</h4><p><RegisteredArea province={r.requestedLocation.province} municipality={r.requestedLocation.municipality} wardNumber={r.requestedLocation.wardNumber} /></p>
          <p>{r.reason}</p>{r.reviewReason && <p>Administrator review: {r.reviewReason}</p>}{r.effectiveAt && <p>Effective {formatDateTime(r.effectiveAt)}</p>}
        </article></li>)}</ul>}
      </section>
    </>}
  </section>
}
