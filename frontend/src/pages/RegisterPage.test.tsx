import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as districtApi from '../api/districts'
import { AuthContext, type AuthContextValue } from '../auth/context'
import type { AuthSession, RegistrationDetails } from '../types/auth'
import RegisterPage from './RegisterPage'

vi.mock('../api/districts')

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

function renderPage(register = vi.fn(async (_details: RegistrationDetails) => voterSession)) {
  const contextValue: AuthContextValue = {
    session: null,
    isInitializing: false,
    login: vi.fn(),
    register,
    logout: vi.fn(),
  }

  render(
    <AuthContext.Provider value={contextValue}>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </AuthContext.Provider>,
  )

  return register
}

describe('RegisterPage', () => {
  afterEach(() => vi.unstubAllGlobals())
  beforeEach(() => {
    vi.mocked(districtApi.getVotingDistricts).mockResolvedValue([{
      id: 'district-id',
      code: 'WC001-0001',
      name: 'Cape Town Ward',
      province: 'Western Cape',
      municipality: 'Cape Town',
      wardNumber: 1,
    }])
  })

  it('announces loading separately from an empty list', async () => {
    let finish!: (value: []) => void
    vi.mocked(districtApi.getVotingDistricts).mockReturnValueOnce(new Promise((resolve) => { finish = resolve }))
    renderPage()
    expect(screen.getByRole('status')).toHaveTextContent('Loading areas...')
    expect(screen.getByLabelText('Your province, municipality and ward')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled()
    finish([])
    expect(await screen.findByText(/Registration areas haven't been set up yet/)).toBeVisible()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled()
  })

  it('retries failures without losing entered details or displaying an empty result', async () => {
    vi.mocked(districtApi.getVotingDistricts).mockRejectedValueOnce(new Error('offline'))
    const user = userEvent.setup()
    renderPage()
    await user.type(screen.getByLabelText('Full legal name'), 'Fictional voter')
    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't load the areas")
    expect(screen.queryByText(/Registration areas haven't/)).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Retry loading areas' }))
    expect(await screen.findByRole('option', { name: /WC001-0001/ })).toBeVisible()
    expect(screen.getByLabelText('Full legal name')).toHaveValue('Fictional voter')
    expect(screen.getByLabelText('Your province, municipality and ward')).toBeEnabled()
  })

  it('shows retry for malformed records and preserves all entered details', async () => {
    const actualApi = await vi.importActual<typeof districtApi>('../api/districts')
    vi.mocked(districtApi.getVotingDistricts).mockImplementationOnce(actualApi.getVotingDistricts)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('[null]', {
      headers: { 'Content-Type': 'application/json' },
    })))
    const user = userEvent.setup()
    renderPage()
    const details = [
      ['Full legal name', 'Fictional QA Voter'],
      ['Email address', 'fictional@example.test'],
      ['South African ID number', '8001015009087'],
      ['Password', 'FictionalPassword2026!'],
      ['Confirm password', 'FictionalPassword2026!'],
    ] as const
    for (const [label, value] of details) await user.type(screen.getByLabelText(label), value)
    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't load the areas")
    expect(screen.getByLabelText('Your province, municipality and ward')).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled()
    const retry = screen.getByRole('button', { name: 'Retry loading areas' })
    retry.focus()
    await user.keyboard('{Enter}')
    await screen.findByRole('option', { name: /WC001-0001/ })
    for (const [label, value] of details) expect(screen.getByLabelText(label)).toHaveValue(value)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Your province, municipality and ward')).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Create account' })).toBeEnabled()
  })

  it('updates the strength bar and reveals each password independently', async () => {
    const user = userEvent.setup()
    renderPage()
    const password = screen.getByLabelText('Password') as HTMLInputElement
    const confirmation = screen.getByLabelText('Confirm password') as HTMLInputElement
    const meter = screen.getByRole('meter', { name: 'Password strength' })

    await user.type(password, 'short')
    expect(meter).toHaveAttribute('aria-valuetext', 'Weak')

    await user.clear(password)
    await user.type(password, 'averylongpassword')
    expect(meter).toHaveAttribute('aria-valuetext', 'Fair')
    expect(screen.getByText('Still needed: an uppercase letter, a number.')).toBeVisible()

    await user.clear(password)
    await user.type(password, 'LongerPassword2026')
    expect(meter).toHaveAttribute('aria-valuetext', 'Good')

    await user.type(password, '!')
    expect(meter).toHaveAttribute('aria-valuetext', 'Strong')

    await user.click(screen.getByRole('button', { name: 'Show password' }))
    expect(password).toHaveAttribute('type', 'text')
    expect(confirmation).toHaveAttribute('type', 'password')

    await user.click(screen.getByRole('button', { name: 'Show confirmation password' }))
    expect(confirmation).toHaveAttribute('type', 'text')
  })

  it('blocks a mismatched confirmation before sending the registration', async () => {
    const user = userEvent.setup()
    const register = renderPage()

    await user.type(screen.getByLabelText('Full legal name'), 'Thandi Mokoena')
    await user.type(screen.getByLabelText('Email address'), 'thandi@example.com')
    await user.type(screen.getByLabelText('South African ID number'), '8001015009087')
    await screen.findByRole('option', { name: /WC001-0001/ })
    await user.selectOptions(screen.getByLabelText('Your province, municipality and ward'), 'district-id')
    await user.type(screen.getByLabelText('Password'), 'MuchLongerPassword2026!')
    await user.type(screen.getByLabelText('Confirm password'), 'SomethingDifferent2026!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(screen.getByText('Passwords do not match.')).toBeVisible()
    expect(register).not.toHaveBeenCalled()

    await user.clear(screen.getByLabelText('Confirm password'))
    await user.type(screen.getByLabelText('Confirm password'), 'MuchLongerPassword2026!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    await waitFor(() => expect(register).toHaveBeenCalledOnce())
    expect(register).toHaveBeenCalledWith(expect.objectContaining({
      password: 'MuchLongerPassword2026!',
    }))
    expect(register.mock.calls[0][0]).not.toHaveProperty('confirmPassword')
  })
})
