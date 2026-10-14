import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/auth'
import type { AuthSession } from '../types/auth'
import AuthProvider from './AuthProvider'
import { useAuth } from './useAuth'

vi.mock('../api/auth')

const restoredSession: AuthSession = {
  accessToken: 'restored-access-token',
  tokenType: 'Bearer',
  expiresAt: '2099-01-01T00:00:00Z',
  userId: 'voter-id',
  email: 'voter@example.com',
  role: 'VOTER',
  refreshToken: 'rotated-refresh-token',
  refreshTokenExpiresAt: '2099-01-01T12:00:00Z',
}

function SessionProbe() {
  const { session, isInitializing, logout } = useAuth()
  if (isInitializing) {
    return <p>restoring</p>
  }
  return (
    <>
      <p>{session ? `signed in as ${session.email}` : 'signed out'}</p>
      <button type="button" onClick={logout}>
        Sign out
      </button>
    </>
  )
}

describe('AuthProvider', () => {
  beforeEach(() => {
    window.sessionStorage.clear()
  })

  afterEach(() => {
    window.sessionStorage.clear()
  })

  it('restores the session from the refresh token kept in this tab', async () => {
    window.sessionStorage.setItem('votetrust.refreshToken', 'stored-refresh-token')
    vi.mocked(authApi.refreshSession).mockResolvedValue(restoredSession)

    render(
      <AuthProvider>
        <SessionProbe />
      </AuthProvider>,
    )

    expect(screen.getByText('restoring')).toBeInTheDocument()
    expect(
      await screen.findByText('signed in as voter@example.com'),
    ).toBeInTheDocument()
    expect(authApi.refreshSession).toHaveBeenCalledWith('stored-refresh-token')
    expect(window.sessionStorage.getItem('votetrust.refreshToken')).toBe(
      'rotated-refresh-token',
    )
  })

  it('signs out and forgets the stored token when it cannot be refreshed', async () => {
    window.sessionStorage.setItem('votetrust.refreshToken', 'revoked-refresh-token')
    vi.mocked(authApi.refreshSession).mockRejectedValue(new Error('revoked'))

    render(
      <AuthProvider>
        <SessionProbe />
      </AuthProvider>,
    )

    expect(await screen.findByText('signed out')).toBeInTheDocument()
    expect(window.sessionStorage.getItem('votetrust.refreshToken')).toBeNull()
  })

  it('revokes the session on the server when signing out', async () => {
    window.sessionStorage.setItem('votetrust.refreshToken', 'stored-refresh-token')
    vi.mocked(authApi.refreshSession).mockResolvedValue(restoredSession)
    vi.mocked(authApi.logoutSession).mockResolvedValue(undefined)

    render(
      <AuthProvider>
        <SessionProbe />
      </AuthProvider>,
    )
    await screen.findByText('signed in as voter@example.com')

    await act(async () => {
      await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    })

    await waitFor(() => expect(screen.getByText('signed out')).toBeInTheDocument())
    expect(authApi.logoutSession).toHaveBeenCalledWith('rotated-refresh-token')
    expect(window.sessionStorage.getItem('votetrust.refreshToken')).toBeNull()
  })
})
