import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getMyBallots } from '../api/profiles'
import { useAuth } from '../auth/useAuth'
import type { VoterBallot } from '../types/profile'
import { formatDateTime, formatEnumLabel } from '../utils/formatters'
import RegisteredArea from './RegisteredArea'

export default function EligibleBallots({ electionId }: { electionId?: string }) {
  const { session } = useAuth()
  const [rows, setRows] = useState<VoterBallot[] | null>(null)
  const [error, setError] = useState(false)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    if (session?.role !== 'VOTER') return
    const controller = new AbortController(); setRows(null); setError(false)
    getMyBallots(session.accessToken, controller.signal, electionId).then((value) => { if (!controller.signal.aborted) setRows(value) })
      .catch(() => { if (!controller.signal.aborted) setError(true) })
    return () => controller.abort()
  }, [session, electionId, attempt])
  if (session?.role !== 'VOTER') return null
  return <section aria-labelledby={`eligible-ballots-${electionId ?? 'all'}`}>
    <h2 id={`eligible-ballots-${electionId ?? 'all'}`}>Your ballots</h2>
    <p>These decisions use your verified simulation profile and saved election registration. <Link to="/profile">Review verification or request an area correction</Link>.</p>
    {!rows && !error && <p role="status">Finding your ballots...</p>}
    {error && <div><p role="alert">We couldn't load your ballot availability. Try again.</p><button type="button" onClick={() => setAttempt((n) => n + 1)}>Retry ballot discovery</button></div>}
    {rows?.length === 0 && <p role="status">No published ballots are configured yet. Check the election directory again later.</p>}
    {rows && rows.length > 0 && <ul className="ballot-list">{rows.map((row) => <li key={row.contest.id}><article className="contest-card">
      <h3>{row.contest.name}</h3><p><Link to={`/elections/${row.election.id}`}>{row.election.name}</Link> · {formatEnumLabel(row.contest.type)}</p>
      <p>{row.message}</p><p>Registration ends {formatDateTime(row.election.registrationEndAt)}. Voting: {formatDateTime(row.election.votingStartAt)} – {formatDateTime(row.election.votingEndAt)}.</p>
      {row.registered && <p>Saved election area: <RegisteredArea province={row.registeredProvince} municipality={row.registeredMunicipality} wardNumber={row.registeredWardNumber} /></p>}
      {row.canRegister && <Link className="button" to={`/elections/${row.election.id}/register`}>{row.registered ? 'Confirm existing registration' : 'Register for election'}</Link>}
      {row.canVote && !row.credentialIssued && <Link className="button" to={`/elections/${row.election.id}/contests/${row.contest.id}/vote`}>Open eligible ballot</Link>}
      {row.canVote && row.credentialIssued && <p role="status">Use the browser page holding your credential to finish. YourVoice cannot identify your anonymous ballot or recover a lost credential.</p>}
      {row.election.status === 'COMPLETED' && row.contest.status === 'CLOSED' && <Link to={`/elections/${row.election.id}/contests/${row.contest.id}/results`}>View final results</Link>}
    </article></li>)}</ul>}
  </section>
}
