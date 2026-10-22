import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as electionApi from '../api/elections'
import { AuthContext, type AuthContextValue } from '../auth/context'
import type { AuthSession } from '../types/auth'
import type { Election, ElectionStatus } from '../types/election'
import ElectionsPage from './ElectionsPage'

vi.mock('../api/elections')

const adminSession: AuthSession = {
  accessToken: 'admin-token',
  tokenType: 'Bearer',
  expiresAt: '2099-01-01T00:00:00Z',
  userId: 'admin-id',
  email: 'admin@example.com',
  role: 'ADMIN',
  refreshToken: 'admin-refresh-token',
  refreshTokenExpiresAt: '2099-01-01T00:00:00Z',
}

const electionStatuses: ElectionStatus[] = ['DRAFT', 'REGISTRATION_OPEN', 'CANCELLED']
const elections: Election[] = electionStatuses.map((status) => ({
  id: status.toLowerCase(),
  name: `${status} election`,
  type: 'NATIONAL',
  status,
  registrationStartAt: '2026-01-01T00:00:00Z',
  registrationEndAt: '2026-02-01T00:00:00Z',
  votingStartAt: '2026-03-01T00:00:00Z',
  votingEndAt: '2026-04-01T00:00:00Z',
}))

function renderPage(session: AuthSession | null) {
  const contextValue: AuthContextValue = {
    session,
    isInitializing: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  }

  render(
    <AuthContext.Provider value={contextValue}>
      <MemoryRouter>
        <ElectionsPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('ElectionsPage stage visibility', () => {
  beforeEach(() => {
    vi.mocked(electionApi.getElections).mockResolvedValue(elections)
  })

  it('omits Draft and Cancelled from the voter filter and election list', async () => {
    renderPage({ ...adminSession, role: 'VOTER' })

    const stageFilter = await screen.findByRole('combobox', { name: 'Election stage' })
    expect(within(stageFilter).queryByRole('option', { name: 'Draft' })).not.toBeInTheDocument()
    expect(within(stageFilter).queryByRole('option', { name: 'Cancelled' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'REGISTRATION_OPEN election' })).toBeVisible()
    expect(screen.queryByRole('link', { name: 'DRAFT election' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'CANCELLED election' })).not.toBeInTheDocument()
    expect(electionApi.getElections).toHaveBeenCalledWith(expect.any(AbortSignal), undefined)
  })

  it('shows every stage to an administrator using their token', async () => {
    renderPage(adminSession)

    const stageFilter = await screen.findByRole('combobox', { name: 'Election stage' })
    expect(within(stageFilter).getByRole('option', { name: 'Draft' })).toBeInTheDocument()
    expect(within(stageFilter).getByRole('option', { name: 'Cancelled' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'DRAFT election' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'CANCELLED election' })).toBeVisible()
    expect(electionApi.getElections).toHaveBeenCalledWith(expect.any(AbortSignal), 'admin-token')
  })
})
