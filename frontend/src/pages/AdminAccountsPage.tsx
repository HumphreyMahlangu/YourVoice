import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import ApiError from '../api/ApiError'
import { createAdminAccount, getAdminAccounts } from '../api/adminAccounts'
import { useAuth } from '../auth/useAuth'
import type { AdminAccount } from '../types/adminAccount'
import { formatDateTime } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

function AdminAccountsPage() {
  const { session, logout } = useAuth()
  const [accounts, setAccounts] = useState<AdminAccount[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<unknown>(null)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [createError, setCreateError] = useState<unknown>(null)
  const [fieldErrors, setFieldErrors] = useState<
    Readonly<Record<string, string>>
  >({})
  const [createdEmail, setCreatedEmail] = useState<string | null>(null)

  useEffect(() => {
    if (!session) {
      return
    }

    const authToken = session.accessToken
    const abortController = new AbortController()

    async function loadAccounts() {
      setLoadError(null)
      setIsLoading(true)

      try {
        const response = await getAdminAccounts(authToken, abortController.signal)
        if (!abortController.signal.aborted) {
          setAccounts(response)
        }
      } catch (requestError) {
        if (abortController.signal.aborted) {
          return
        }
        if (requestError instanceof ApiError && requestError.status === 401) {
          logout()
          return
        }
        setLoadError(requestError)
      } finally {
        if (!abortController.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    void loadAccounts()
    return () => abortController.abort()
  }, [logout, session])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) {
      return
    }

    setCreateError(null)
    setFieldErrors({})
    setCreatedEmail(null)
    setIsSubmitting(true)

    try {
      const account = await createAdminAccount({ email, password }, session.accessToken)
      setAccounts((current) => [...current, account])
      setCreatedEmail(account.email)
      setEmail('')
      setPassword('')
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        logout()
        return
      }
      setCreateError(requestError)
      if (requestError instanceof ApiError) {
        setFieldErrors(requestError.fieldErrors)
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className="page admin-page workflow-page" aria-labelledby="admin-accounts-heading">
      <Link to="/admin">Back to admin dashboard</Link>
      <h1 id="admin-accounts-heading">Administrators</h1>

      {isLoading && <p role="status">Loading administrators...</p>}

      {!isLoading && loadError !== null && (
        <p role="alert">
          {getErrorMessage(loadError, 'Unable to load administrators.')}
        </p>
      )}

      {!isLoading && loadError === null && (
        <ul>
          {accounts.map((account) => (
            <li key={account.id}>
              {account.email} — created{' '}
              <time dateTime={account.createdAt}>
                {formatDateTime(account.createdAt)}
              </time>
              {!account.enabled && ' (disabled)'}
            </li>
          ))}
        </ul>
      )}

      <section aria-labelledby="create-admin-heading">
        <h2 id="create-admin-heading">Add an administrator</h2>
        <p>
          Share the initial password with the new administrator through a
          separate channel. The action is recorded in the security audit log
          with your account.
        </p>

        {createdEmail && (
          <p role="status">Administrator {createdEmail} was created.</p>
        )}
        {createError !== null && (
          <p role="alert">
            {getErrorMessage(createError, 'Unable to create the administrator.')}
          </p>
        )}

        <form onSubmit={handleSubmit}>
          <div>
            <label htmlFor="admin-email">Email address</label>
            <input
              id="admin-email"
              type="email"
              autoComplete="off"
              required
              maxLength={320}
              value={email}
              aria-invalid={fieldErrors.email ? true : undefined}
              aria-describedby={fieldErrors.email ? 'admin-email-error' : undefined}
              onChange={(event) => setEmail(event.target.value)}
            />
            {fieldErrors.email && <p id="admin-email-error">{fieldErrors.email}</p>}
          </div>
          <div>
            <label htmlFor="admin-password">Initial password</label>
            <p id="admin-password-help">
              At least 12 characters with an uppercase letter, lowercase letter,
              and number.
            </p>
            <input
              id="admin-password"
              type="password"
              autoComplete="new-password"
              required
              minLength={12}
              maxLength={128}
              value={password}
              aria-invalid={fieldErrors.password ? true : undefined}
              aria-describedby={
                fieldErrors.password
                  ? 'admin-password-help admin-password-error'
                  : 'admin-password-help'
              }
              onChange={(event) => setPassword(event.target.value)}
            />
            {fieldErrors.password && (
              <p id="admin-password-error">{fieldErrors.password}</p>
            )}
          </div>
          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Creating administrator...' : 'Create administrator'}
          </button>
        </form>
      </section>
    </section>
  )
}

export default AdminAccountsPage
