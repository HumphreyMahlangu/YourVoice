import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../auth/context'
import type { AuthSession, LoginCredentials } from '../types/auth'
import LoginPage from './LoginPage'

const voterSession: AuthSession = {
  accessToken: 'voter-token',
  tokenType: 'Bearer',
  expiresAt: '2099-01-01T00:00:00Z',
  userId: 'voter-id',
  email: 'voter@example.com',
  role: 'VOTER',
  refreshToken: 'refresh-token',
  refreshTokenExpiresAt: '2099-01-01T00:00:00Z',
}

describe('LoginPage', () => {
  it('reveals the password and sends the entered credentials', async () => {
    const user = userEvent.setup()
    const login = vi.fn(async (_credentials: LoginCredentials) => voterSession)
    const contextValue: AuthContextValue = {
      session: null,
      isInitializing: false,
      login,
      register: vi.fn(),
      logout: vi.fn(),
    }

    render(
      <AuthContext.Provider value={contextValue}>
        <MemoryRouter initialEntries={['/login']}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/dashboard" element={<p>Voter dashboard</p>} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    const password = screen.getByLabelText('Password') as HTMLInputElement
    expect(password).toHaveAttribute('type', 'password')

    await user.click(screen.getByRole('button', { name: 'Show password' }))
    expect(password).toHaveAttribute('type', 'text')
    await user.click(screen.getByRole('button', { name: 'Hide password' }))
    expect(password).toHaveAttribute('type', 'password')

    await user.type(screen.getByLabelText('Email address'), 'voter@example.com')
    await user.type(password, 'CorrectPassword2026!')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(login).toHaveBeenCalledWith({
      email: 'voter@example.com',
      password: 'CorrectPassword2026!',
    }))
    expect(await screen.findByText('Voter dashboard')).toBeVisible()
  })
})
