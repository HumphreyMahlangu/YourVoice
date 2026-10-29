import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, expect, it, vi } from 'vitest'
import { getMyBallots } from '../api/profiles'
import { castBallot, getBallotKey, issueVotingCredential } from '../api/voting'
import { AuthContext } from '../auth/context'
import type { VoterBallot } from '../types/profile'
import VotePage from './VotePage'
vi.mock('../api/profiles')
vi.mock('../api/voting')
vi.mock('../utils/blindSignature', () => ({ blindBallotToken: vi.fn(async () => ({ ballotToken: 'anonymous-token', blindedToken: 'blinded-token', keyFingerprint: 'shared-contest-fingerprint' })), unblindSignature: vi.fn(async () => 'anonymous-signature') }))
const row: VoterBallot = { election: { id: 'e', name: 'Fictional Election', type: 'NATIONAL', status: 'VOTING_OPEN', registrationStartAt: '2026-01-01', registrationEndAt: '2026-02-01', votingStartAt: '2026-03-01', votingEndAt: '2099-01-01' }, contest: { id: 'c', electionId: 'e', name: 'Fictional National Ballot', type: 'NATIONAL', status: 'OPEN', displayOrder: 1, scopeProvince: null, scopeMunicipality: null, scopeWardNumber: null, options: [{ id: 'option', name: 'Fictional Party', optionType: 'PARTY', displayOrder: 1 }] }, eligible: true, registered: true, registrationVerified: true, canRegister: false, canVote: true, credentialIssued: false, message: 'Ready to vote.', registeredProvince: null, registeredMunicipality: null, registeredWardNumber: null }
const session = { accessToken: 'account-token', tokenType: 'Bearer', expiresAt: '2099-01-01', userId: 'u', email: 'fictional@example.test', role: 'VOTER' as const, refreshToken: 'r', refreshTokenExpiresAt: '2099-01-01' }
function view() {
  const router = createMemoryRouter([{ path: '/elections/:electionId/contests/:contestId/vote', element: <VotePage /> }], { initialEntries: ['/elections/e/contests/c/vote'] })
  render(<AuthContext.Provider value={{ session, isInitializing: false, login: vi.fn(), register: vi.fn(), logout: vi.fn() }}><RouterProvider router={router} /></AuthContext.Provider>)
}
beforeEach(() => {
  vi.mocked(getMyBallots).mockResolvedValue([row])
  vi.mocked(getBallotKey).mockResolvedValue({ contestId: 'c', modulus: 'mock', publicExponent: 'mock', keyFingerprint: 'shared', tokenDomain: 'mock' })
  vi.mocked(issueVotingCredential).mockResolvedValue({ electionId: 'e', keyFingerprint: 'shared', contestId: 'c', blindSignature: 'blinded-signature', expiresAt: '2099-01-01' })
})
it('blocks unavailable ballots before requesting a credential', async () => {
  vi.mocked(getMyBallots).mockResolvedValue([{ ...row, canVote: false, message: 'This ballot needs a verified matching province.' }])
  view(); expect(await screen.findByText(/This ballot needs a verified matching province/)).toBeVisible()
  expect(screen.queryByRole('radio')).not.toBeInTheDocument(); expect(issueVotingCredential).not.toHaveBeenCalled()
})
it('retries the same anonymous cast after an uncertain transport failure without another issuance', async () => {
  vi.mocked(castBallot).mockRejectedValueOnce(new TypeError('offline')).mockResolvedValueOnce({ contestId: 'c', accepted: true, message: 'Accepted' })
  const user = userEvent.setup(); view()
  await user.click(await screen.findByRole('radio', { name: /Fictional Party/ }))
  await user.click(screen.getByRole('button', { name: 'Review selection' }))
  await user.click(screen.getByRole('button', { name: 'Confirm and cast ballot' }))
  await user.click(await screen.findByRole('button', { name: 'Retry ballot submission' }))
  expect(await screen.findByText('Ballot accepted')).toBeVisible()
  expect(issueVotingCredential).toHaveBeenCalledOnce(); expect(castBallot).toHaveBeenCalledTimes(2)
  expect(vi.mocked(castBallot).mock.calls[0]).toEqual(vi.mocked(castBallot).mock.calls[1])
  expect(castBallot).toHaveBeenCalledWith({ contestId: 'c', contestOptionId: 'option', ballotToken: 'anonymous-token', signature: 'anonymous-signature' })
  expect(screen.queryByText('You selected:')).not.toBeInTheDocument()
})
