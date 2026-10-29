import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api/profiles'
import * as districts from '../api/districts'
import * as elections from '../api/elections'
import * as registrations from '../api/registrations'
import { AuthContext } from '../auth/context'
import type { AuthSession } from '../types/auth'
import type { ProfileVerification, VoterBallot } from '../types/profile'
import EligibleBallots from '../components/EligibleBallots'
import ProfilePage from './ProfilePage'
import ElectionRegistrationPage from './ElectionRegistrationPage'
import AdminProfileVerificationPage from './AdminProfileVerificationPage'
vi.mock('../api/profiles')
vi.mock('../api/districts')
vi.mock('../api/elections')
vi.mock('../api/registrations')
const session: AuthSession = { accessToken: 'fictional-token', tokenType: 'Bearer', expiresAt: '2099-01-01', userId: 'fictional-user', email: 'fictional@example.test', role: 'VOTER', refreshToken: 'refresh', refreshTokenExpiresAt: '2099-01-01' }
const location = { provinceId: null, province: null, municipalityId: null, municipality: null, votingDistrictId: null, districtCode: null, districtName: null, wardNumber: null }
const profile: ProfileVerification = { fullName: 'Fictional Voter', source: 'FICTIONAL_SIMULATION', sourceReference: null, identityStatus: 'UNVERIFIED', locationStatus: 'UNVERIFIED', sourceAvailable: true, location, message: 'Check the fictional source to continue.', checkedAt: null, effectiveAt: null }
const election = { id: 'election', name: 'Fictional Mixed Election', type: 'MIXED', status: 'REGISTRATION_OPEN', registrationStartAt: '2026-10-01T00:00:00Z', registrationEndAt: '2026-11-01T00:00:00Z', votingStartAt: '2026-11-02T00:00:00Z', votingEndAt: '2026-11-03T00:00:00Z' } as const
const ballot: VoterBallot = { election, contest: { id: 'contest', electionId: 'election', name: 'Fictional National Ballot', type: 'NATIONAL', status: 'DRAFT', displayOrder: 1, scopeProvince: null, scopeMunicipality: null, scopeWardNumber: null, options: [] }, eligible: true, registered: false, registrationVerified: false, canRegister: true, canVote: false, credentialIssued: false, message: 'Register for this election to access this ballot.', registeredProvince: null, registeredMunicipality: null, registeredWardNumber: null }
function view(element: React.ReactNode, route = '/', admin = false) {
  return render(<AuthContext.Provider value={{ session: { ...session, role: admin ? 'ADMIN' : 'VOTER' }, isInitializing: false, login: vi.fn(), register: vi.fn(), logout: vi.fn() }}><MemoryRouter initialEntries={[route]}><Routes><Route path="*" element={element} /><Route path="/elections/:electionId/register" element={element} /></Routes></MemoryRouter></AuthContext.Provider>)
}
beforeEach(() => {
  vi.mocked(api.getProfile).mockResolvedValue(profile)
  vi.mocked(api.getCorrections).mockResolvedValue([])
  vi.mocked(api.getProvinces).mockResolvedValue([{ id: 'province', name: 'Fictional Province' }])
  vi.mocked(api.getMunicipalities).mockResolvedValue([{ id: 'municipality', name: 'Fictional Town', provinceId: 'province' }])
  vi.mocked(districts.getVotingDistricts).mockResolvedValue([{ id: 'area', code: 'SIM-1', name: 'Fictional Area', province: 'Fictional Province', municipality: 'Fictional Town', wardNumber: 1 }])
  vi.mocked(api.getMyBallots).mockResolvedValue([ballot])
  vi.mocked(elections.getElection).mockResolvedValue(election)
  vi.mocked(api.getSourceRecords).mockResolvedValue([]); vi.mocked(api.getAdminCorrections).mockResolvedValue([]); vi.mocked(api.getProfileAudit).mockResolvedValue([])
})
describe('voter experience', () => {
  it.each(['UNVERIFIED', 'PENDING', 'REJECTED', 'UNAVAILABLE'] as const)('shows %s honestly and offers another fictional check', async (status) => {
    vi.mocked(api.getProfile).mockResolvedValue({ ...profile, identityStatus: status, message: `${status}: ask the simulation administrator.` })
    vi.mocked(api.checkProfile).mockResolvedValue({ ...profile, identityStatus: 'VERIFIED', message: 'Fictional identity matched.' })
    view(<ProfilePage />)
    expect(await screen.findByText(`${status}: ask the simulation administrator.`)).toBeVisible()
    expect(screen.getByText(/No IEC or government/)).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Check fictional verification' }))
    expect(await screen.findByText('Fictional identity matched.')).toBeVisible()
  })
  it('keeps the correction reason and chosen area through a failed request and keyboard retry', async () => {
    vi.mocked(api.getProfile).mockResolvedValue({ ...profile, identityStatus: 'VERIFIED' })
    vi.mocked(api.requestCorrection).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce({ id: 'request', voterName: 'Fictional Voter', requestedLocation: location, reason: 'Fictional correction', status: 'PENDING', requestedAt: '2026-10-29T12:00:00Z', reviewedAt: null, reviewReason: null, sourceReference: null, effectiveAt: null })
    const user = userEvent.setup(); view(<ProfilePage />)
    await screen.findByRole('option', { name: 'Fictional Province' })
    await user.selectOptions(screen.getByLabelText('Province (optional)'), 'province')
    await user.selectOptions(screen.getByLabelText('Municipality (optional)'), 'municipality')
    await user.selectOptions(screen.getByLabelText('Ward area (optional)'), 'area')
    await user.type(screen.getByLabelText('Reason for correction'), 'Fictional correction')
    await user.click(screen.getByRole('button', { name: 'Request correction' }))
    await screen.findByRole('alert')
    expect(screen.getByLabelText('Reason for correction')).toHaveValue('Fictional correction')
    expect(screen.getByLabelText('Ward area (optional)')).toHaveValue('area')
    screen.getByRole('button', { name: 'Request correction' }).focus(); await user.keyboard('{Enter}')
    expect(await screen.findByText(/Your area correction is awaiting/)).toBeVisible()
    expect(api.requestCorrection).toHaveBeenLastCalledWith('fictional-token', { provinceId: 'province', municipalityId: 'municipality', votingDistrictId: 'area' }, 'Fictional correction')
  })
  it('distinguishes failed discovery from genuinely empty ballots and recovers with retry', async () => {
    vi.mocked(api.getMyBallots).mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce([])
    view(<EligibleBallots />)
    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't load your ballot availability")
    expect(screen.queryByText(/No published ballots/)).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Retry ballot discovery' }))
    expect(await screen.findByText(/No published ballots are configured/)).toBeVisible()
  })
  it('offers only server-authorised ballot actions and never treats an issued credential as a cast ballot', async () => {
    vi.mocked(api.getMyBallots).mockResolvedValue([{ ...ballot, canRegister: false, canVote: true, credentialIssued: true, registered: true }])
    view(<EligibleBallots />)
    expect(await screen.findByText(/Use the browser page holding your credential/)).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Open eligible ballot' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Register for election' })).not.toBeInTheDocument()
  })
  it('registers nationally using server attributes without any area selector', async () => {
    vi.mocked(registrations.registerForElection).mockResolvedValue({ id: 'registration', electionId: 'election', electionName: election.name, electionType: 'MIXED', status: 'ACTIVE', registeredAt: '2026-10-29T00:00:00Z', votingDistrictId: null, votingDistrictCode: null, votingDistrictName: null, province: null, municipality: null, wardNumber: null, identityVerified: true, verificationSource: 'FICTIONAL_SIMULATION', sourceReference: 'SIM-NATIONAL' })
    view(<ElectionRegistrationPage />, '/elections/election/register')
    const submit = await screen.findByRole('button', { name: 'Register for election' })
    expect(submit).toBeEnabled(); expect(screen.queryByRole('combobox')).not.toBeInTheDocument()
    await userEvent.click(submit)
    expect(await screen.findByText('Registration complete')).toBeVisible()
    expect(registrations.registerForElection).toHaveBeenCalledWith('election', {}, 'fictional-token')
  })
  it('disables registration when verification or geographic requirements are not met', async () => {
    vi.mocked(api.getMyBallots).mockResolvedValue([{ ...ballot, eligible: false, canRegister: false, message: 'Complete your fictional identity check.' }])
    view(<ElectionRegistrationPage />, '/elections/election/register')
    expect(await screen.findByRole('button', { name: 'Register for election' })).toBeDisabled()
    expect(screen.getByRole('link', { name: 'Review your simulation profile' })).toHaveAttribute('href', '/profile')
  })
  it('requires an explicit fictional declaration before an administrator can save a source record', async () => {
    view(<AdminProfileVerificationPage />, '/', true)
    await screen.findByText('No fictional verification records configured.')
    const submit = screen.getByRole('button', { name: 'Create fictional source record' })
    expect(submit).toBeDisabled()
    fireEvent.click(screen.getByLabelText('I confirm these are fictional simulation details.'))
    expect(submit).toBeEnabled()
    expect(screen.queryByLabelText('Province (optional)')).not.toBeInTheDocument()
  })
  it('keeps review fields after a failed approval and requires matching evidence and an effective date', async () => {
    const pending = { id: 'review', voterName: 'Fictional Voter', requestedLocation: location, reason: 'Fictional correction', status: 'PENDING' as const, requestedAt: '2026-10-29T12:00:00Z', reviewedAt: null, reviewReason: null, sourceReference: null, effectiveAt: null }
    vi.mocked(api.getAdminCorrections).mockResolvedValue([pending])
    vi.mocked(api.reviewCorrection).mockRejectedValueOnce(new TypeError('offline'))
    const user = userEvent.setup(); view(<AdminProfileVerificationPage />, '/', true)
    await user.selectOptions(await screen.findByLabelText('Decision'), 'approve')
    await user.type(screen.getByLabelText('Latest matching fictional source reference'), 'SIM-REVIEW')
    fireEvent.change(screen.getByLabelText('Effective date and time (your local time)'), { target: { value: '2099-01-01T12:00' } })
    await user.type(screen.getByLabelText('Review reason shown to voter'), 'Matched fictional evidence')
    await user.click(screen.getByRole('button', { name: 'Save review' }))
    expect(await screen.findByRole('alert')).toBeVisible()
    expect(screen.getByLabelText('Review reason shown to voter')).toHaveValue('Matched fictional evidence')
    expect(screen.getByLabelText('Latest matching fictional source reference')).toHaveValue('SIM-REVIEW')
    expect(api.reviewCorrection).toHaveBeenCalledWith('fictional-token', 'review', expect.objectContaining({ approve: true, sourceReference: 'SIM-REVIEW', reason: 'Matched fictional evidence' }))
  })

})
