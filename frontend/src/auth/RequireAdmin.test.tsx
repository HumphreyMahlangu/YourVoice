import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import type { AuthSession } from '../types/auth'
import RequireAdmin from './RequireAdmin'
import { AuthContext, type AuthContextValue } from './context'

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

function renderAdminRoute(session: AuthSession | null) {
  const contextValue: AuthContextValue = {
    session,
    isInitializing: false,
    login: vi.fn(async () => adminSession),
    register: vi.fn(async () => adminSession),
    logout: vi.fn(),
  }

  render(
    <AuthContext.Provider value={contextValue}>
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route element={<RequireAdmin />}>
            <Route path="/admin" element={<h1>Admin area</h1>} />
          </Route>
          <Route path="/dashboard" element={<h1>Voter dashboard</h1>} />
          <Route path="/login" element={<h1>Sign in</h1>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

describe('RequireAdmin', () => {
  it('renders the protected route for an administrator', () => {
    renderAdminRoute(adminSession)

    expect(screen.getByRole('heading', { name: 'Admin area' })).toBeVisible()
  })

  it('redirects a voter to the voter dashboard', () => {
    renderAdminRoute({ ...adminSession, role: 'VOTER' })

    expect(
      screen.getByRole('heading', { name: 'Voter dashboard' }),
    ).toBeVisible()
  })

  it('redirects an unauthenticated visitor to sign in', () => {
    renderAdminRoute(null)

    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  })
})
