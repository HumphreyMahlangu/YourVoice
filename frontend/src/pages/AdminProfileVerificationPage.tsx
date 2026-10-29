import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { createMunicipality, createProvince, createSourceRecord, getAdminCorrections, getProfileAudit, getSourceRecords, reviewCorrection } from '../api/profiles'
import { useAuth } from '../auth/useAuth'
import LocationFields from '../components/LocationFields'
import RegisteredArea from '../components/RegisteredArea'
import type { LocationCorrection, LocationSelection, ProfileAudit, SimulationRecord, VerificationStatus } from '../types/profile'
import { getAdminErrorMessage } from '../utils/adminErrors'
import { formatDateTime, formatEnumLabel } from '../utils/formatters'

function CorrectionReview({ request, token, onReviewed }: { request: LocationCorrection; token: string; onReviewed: () => void }) {
  const [approve, setApprove] = useState(false)
  const [reason, setReason] = useState('')
  const [reference, setReference] = useState('')
  const [effective, setEffective] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError(null)
    try { await reviewCorrection(token, request.id, { approve, reason, sourceReference: approve ? reference : undefined, effectiveAt: approve ? new Date(effective).toISOString() : undefined }); onReviewed() }
    catch (failure) { setError(failure) }
    finally { setBusy(false) }
  }
  return <article className="contest-card"><h3>{request.voterName} · {formatEnumLabel(request.status)}</h3>
    <p>Requested area: <RegisteredArea province={request.requestedLocation.province} municipality={request.requestedLocation.municipality} wardNumber={request.requestedLocation.wardNumber} /></p>
    <p>{request.reason}</p><p>Requested {formatDateTime(request.requestedAt)}</p>
    {request.reviewReason && <p>Review: {request.reviewReason}</p>}{request.effectiveAt && <p>Effective {formatDateTime(request.effectiveAt)}</p>}
    {request.status === 'PENDING' && <form onSubmit={(e) => void submit(e)}>
      {error !== null && <p role="alert">{getAdminErrorMessage(error, 'Unable to review this correction. Check the fictional source and try again.')}</p>}
      <label htmlFor={`decision-${request.id}`}>Decision</label><select id={`decision-${request.id}`} disabled={busy} value={approve ? 'approve' : 'reject'} onChange={(e) => setApprove(e.target.value === 'approve')}><option value="reject">Reject with reason</option><option value="approve">Approve matching fictional record</option></select>
      {approve && <><label htmlFor={`reference-${request.id}`}>Latest matching fictional source reference</label><input id={`reference-${request.id}`} required maxLength={80} value={reference} onChange={(e) => setReference(e.target.value)} disabled={busy} />
        <label htmlFor={`effective-${request.id}`}>Effective date and time (your local time)</label><input id={`effective-${request.id}`} type="datetime-local" required value={effective} onChange={(e) => setEffective(e.target.value)} disabled={busy} /><p>Choose a future time. The server stores UTC. Existing election registrations keep their saved area.</p></>}
      <label htmlFor={`reason-${request.id}`}>Review reason shown to voter</label><textarea id={`reason-${request.id}`} required maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} disabled={busy} />
      <button type="submit" disabled={busy}>{busy ? 'Saving review...' : 'Save review'}</button>
    </form>}
  </article>
}
const statuses: VerificationStatus[] = ['UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED']
export default function AdminProfileVerificationPage() {
  const { session } = useAuth()
  const [data, setData] = useState<{ records: SimulationRecord[]; corrections: LocationCorrection[]; audit: ProfileAudit[] } | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [loading, setLoading] = useState(true)
  const [attempt, setAttempt] = useState(0)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [reference, setReference] = useState('')
  const [name, setName] = useState('')
  const [identity, setIdentity] = useState('')
  const [fictional, setFictional] = useState(false)
  const [identityStatus, setIdentityStatus] = useState<VerificationStatus>('VERIFIED')
  const [locationStatus, setLocationStatus] = useState<VerificationStatus>('UNVERIFIED')
  const [location, setLocation] = useState<LocationSelection>({})
  const [reason, setReason] = useState('')
  const [geoName, setGeoName] = useState('')
  const [geoKind, setGeoKind] = useState('province')
  const [geoParent, setGeoParent] = useState<LocationSelection>({})
  useEffect(() => {
    if (!session) return
    const controller = new AbortController(); setLoading(true); setError(null)
    Promise.all([getSourceRecords(session.accessToken, controller.signal), getAdminCorrections(session.accessToken, controller.signal), getProfileAudit(session.accessToken, controller.signal)])
      .then(([records, corrections, audit]) => { if (!controller.signal.aborted) setData({ records, corrections, audit }) })
      .catch((failure) => { if (!controller.signal.aborted) setError(failure) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [session, attempt])
  function refresh() { setAttempt((n) => n + 1) }
  async function addRecord(e: FormEvent) {
    e.preventDefault(); if (!session) return
    setBusy(true); setError(null); setNotice('')
    try {
      await createSourceRecord(session.accessToken, { reference, fictional, fullName: name, southAfricanIdNumber: identity, identityStatus, locationStatus, location: locationStatus === 'VERIFIED' ? location : {}, reason })
      setIdentity(''); setReference(''); setFictional(false); setNotice('Fictional source record created. The voter must check verification or submit a correction; no existing registration changed.'); refresh()
    } catch (failure) { setError(failure) } finally { setBusy(false) }
  }
  async function addGeography(e: FormEvent) {
    e.preventDefault(); if (!session) return
    setBusy(true); setError(null); setNotice('')
    try {
      if (geoKind === 'province') await createProvince(session.accessToken, geoName)
      else if (geoParent.provinceId) await createMunicipality(session.accessToken, geoName, geoParent.provinceId)
      else throw new Error('Choose a parent province')
      setGeoName(''); setNotice('Geographic parent saved. Refresh the area fields to see the new record.'); refresh()
    } catch (failure) { setError(failure) } finally { setBusy(false) }
  }
  return <section className="page workflow-page" aria-labelledby="admin-verification-heading">
    <Link to="/admin">Back to administration</Link><h1 id="admin-verification-heading">Fictional profile verification</h1>
    <p className="simulation-notice">Simulation source only. Never enter real identity documents or claim IEC/government verification. Source records and private review reasons are separate from anonymous ballots. Reasons in closed correction requests and audit entries are redacted after 90 days.</p>
    {loading && <p role="status">Loading fictional verification administration...</p>}
    {error !== null && <div><p role="alert">{getAdminErrorMessage(error, 'Unable to complete this request. Review your details and try again.')}</p><button type="button" disabled={loading || busy} onClick={refresh}>Retry administration</button></div>}
    {notice && <p role="status">{notice}</p>}
    <section aria-labelledby="source-create-heading"><h2 id="source-create-heading">Add a fictional source record</h2>
      <p>Records are immutable. A newer reference for the same fictional identity supersedes its earlier source record. Name and protected identity must match. Existing profiles are never automatically marked verified.</p>
      <form onSubmit={(e) => void addRecord(e)}>
        <label htmlFor="source-reference">Unique fictional reference</label><input id="source-reference" required pattern="(SIM|DEMO)-[A-Z0-9-]+" maxLength={80} placeholder="SIM-EXAMPLE-001" value={reference} onChange={(e) => setReference(e.target.value)} disabled={busy} />
        <label htmlFor="source-name">Fictional full name</label><input id="source-name" required maxLength={160} value={name} onChange={(e) => setName(e.target.value)} disabled={busy} />
        <label htmlFor="source-identity">Synthetic South African ID number</label><input id="source-identity" required pattern="[0-9]{13}" minLength={13} maxLength={13} inputMode="numeric" autoComplete="off" value={identity} onChange={(e) => setIdentity(e.target.value)} disabled={busy} />
        <p>The server immediately protects this value; it is never returned by the source API.</p>
        <label htmlFor="source-identity-status">Fictional identity decision</label><select id="source-identity-status" value={identityStatus} onChange={(e) => setIdentityStatus(e.target.value as VerificationStatus)} disabled={busy}>{statuses.map((s) => <option key={s} value={s}>{formatEnumLabel(s)}</option>)}</select>
        <label htmlFor="source-location-status">Fictional location decision</label><select id="source-location-status" value={locationStatus} onChange={(e) => { setLocationStatus(e.target.value as VerificationStatus); setLocation({}) }} disabled={busy}>{statuses.map((s) => <option key={s} value={s}>{formatEnumLabel(s)}</option>)}</select>
        {locationStatus === 'VERIFIED' && <LocationFields key={`source-${attempt}`} id="source" value={location} onChange={setLocation} disabled={busy} />}
        <label htmlFor="source-reason">Decision reason shown to voter</label><textarea id="source-reason" required maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} disabled={busy} />
        <label className="check-label"><input type="checkbox" required checked={fictional} onChange={(e) => setFictional(e.target.checked)} disabled={busy} />I confirm these are fictional simulation details.</label>
        <button type="submit" disabled={busy || !fictional || (locationStatus === 'VERIFIED' && !location.provinceId)}>{busy ? 'Saving...' : 'Create fictional source record'}</button>
      </form>
    </section>
    <section aria-labelledby="review-heading"><h2 id="review-heading">Area correction reviews</h2>
      <p>Approval requires the latest matching fictional source to verify the exact requested area. A request alone never changes eligibility.</p>
      {!loading && data?.corrections.length === 0 && <p>No correction requests.</p>}
      {session && data?.corrections.map((r) => <CorrectionReview key={`${r.id}-${r.status}`} request={r} token={session.accessToken} onReviewed={refresh} />)}
    </section>
    <section aria-labelledby="parent-heading"><h2 id="parent-heading">Province and municipality records</h2><p>Configure parents independently for province-only or municipality-only verification. <Link to="/admin/voting-districts">Manage ward areas</Link>.</p>
      <form onSubmit={(e) => void addGeography(e)}><label htmlFor="geo-kind">Record type</label><select id="geo-kind" value={geoKind} onChange={(e) => setGeoKind(e.target.value)} disabled={busy}><option value="province">Province</option><option value="municipality">Municipality</option></select>
        <label htmlFor="geo-name">Name</label><input id="geo-name" required maxLength={80} value={geoName} onChange={(e) => setGeoName(e.target.value)} disabled={busy} />
        {geoKind === 'municipality' && <LocationFields key={`parent-${attempt}`} id="parent" value={geoParent} onChange={setGeoParent} disabled={busy} />}
        <button type="submit" disabled={busy || (geoKind === 'municipality' && !geoParent.provinceId)}>Save geographic parent</button>
      </form>
    </section>
    <section aria-labelledby="records-heading"><h2 id="records-heading">Configured fictional records</h2>
      {!loading && data?.records.length === 0 && <p>No fictional verification records configured.</p>}
      <ul className="profile-list">{data?.records.map((r) => <li key={r.id}><h3>{r.reference} · {r.fictionalName}</h3><p>Identity: {formatEnumLabel(r.identityStatus)}. Location: {formatEnumLabel(r.locationStatus)}.</p><p><RegisteredArea province={r.location.province} municipality={r.location.municipality} wardNumber={r.location.wardNumber} /></p><p>{r.reason}</p></li>)}</ul>
    </section>
    <section aria-labelledby="profile-audit-heading"><h2 id="profile-audit-heading">Private verification audit</h2>
      {!loading && data?.audit.length === 0 && <p>No verification audit entries.</p>}
      <ul className="profile-list">{data?.audit.map((a) => <li key={a.id}><p>{formatDateTime(a.occurredAt)} · {formatEnumLabel(a.action)}</p><p>Actor: {a.actorUserId}. Source: {a.sourceReference ?? 'None'}. Correction: {a.correctionRequestId ?? 'None'}.</p><p>{a.reason}</p></li>)}</ul>
    </section>
  </section>
}
