import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from './auth/context'
import App from './App'

const guestContext: AuthContextValue = {
  session: null,
  isInitializing: false,
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
}

describe('App header', () => {
  it('opens and closes the navigation by keyboard and after following a link', async () => {
    const user = userEvent.setup()

    render(
      <AuthContext.Provider value={guestContext}>
        <MemoryRouter initialEntries={['/']}>
          <Routes>
            <Route element={<App />}>
              <Route index element={<p>Home page</p>} />
              <Route path="elections" element={<p>Elections page</p>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    )

    const toggle = screen.getByRole('button', { name: 'Menu' })
    const menu = document.getElementById('header-menu')
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(menu).not.toHaveClass('header-menu--open')

    await user.click(toggle)
    expect(screen.getByRole('button', { name: 'Close' })).toHaveAttribute('aria-expanded', 'true')
    expect(menu).toHaveClass('header-menu--open')
    await user.keyboard('{Escape}')
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(toggle).toHaveFocus()
    expect(menu).not.toHaveClass('header-menu--open')

    await user.click(toggle)
    await user.click(screen.getByRole('link', { name: 'Elections' }))
    expect(await screen.findByText('Elections page')).toBeVisible()
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(menu).not.toHaveClass('header-menu--open')
  })
})
