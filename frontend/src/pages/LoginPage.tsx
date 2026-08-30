import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import ApiError from '../api/ApiError'
import { useAuth } from '../auth/useAuth'
import { getErrorMessage } from '../utils/getErrorMessage'

function LoginPage() {
  const navigate = useNavigate()
  const { session, login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [fieldErrors, setFieldErrors] = useState<
    Readonly<Record<string, string>>
  >({})

  if (session) {
    return (
      <Navigate
        to={session.role === 'VOTER' ? '/dashboard' : '/admin'}
        replace
      />
    )
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})
    setIsSubmitting(true)

    try {
      const authenticatedSession = await login({ email, password })
      navigate(
        authenticatedSession.role === 'VOTER' ? '/dashboard' : '/admin',
        { replace: true },
      )
    } catch (requestError) {
      setError(requestError)

      if (requestError instanceof ApiError) {
        setFieldErrors(requestError.fieldErrors)
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  const emailError = fieldErrors.email
  const passwordError = fieldErrors.password

  return (
    <section className="page auth-page" aria-labelledby="login-heading">
      <p className="eyebrow">Secure account access</p>
      <h1 id="login-heading">Sign in</h1>
      <p>Continue to your protected voter or administrator workspace.</p>

      {error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to sign in. Please try again.')}
        </p>
      )}

      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="login-email">Email address</label>
          <input
            id="login-email"
            name="email"
            type="email"
            autoComplete="email"
            required
            maxLength={320}
            value={email}
            aria-invalid={emailError ? true : undefined}
            aria-describedby={emailError ? 'login-email-error' : undefined}
            onChange={(event) => setEmail(event.target.value)}
          />
          {emailError && <p id="login-email-error">{emailError}</p>}
        </div>

        <div>
          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            maxLength={128}
            value={password}
            aria-invalid={passwordError ? true : undefined}
            aria-describedby={
              passwordError ? 'login-password-error' : undefined
            }
            onChange={(event) => setPassword(event.target.value)}
          />
          {passwordError && <p id="login-password-error">{passwordError}</p>}
        </div>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Signing in...' : 'Sign in'}
        </button>
      </form>

      <p>
        Need an account? <Link to="/register">Register</Link>
      </p>
    </section>
  )
}

export default LoginPage
