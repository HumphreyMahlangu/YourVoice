import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import ApiError from '../api/ApiError'
import { useAuth } from '../auth/useAuth'
import PasswordVisibilityButton from '../components/PasswordVisibilityButton'
import { getErrorMessage } from '../utils/getErrorMessage'

function LoginPage() {
  const navigate = useNavigate()
  const { session, login } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
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
    <section className="page auth-page login-page" aria-labelledby="login-heading">
      <header className="login-intro">
        <div className="login-intro__main">
          <p className="eyebrow">YourVoice / Account access</p>
          <h1 id="login-heading">Welcome back.</h1>
        </div>
        <p className="login-intro__aside">
          Sign in to continue to your account and review available elections.
        </p>
      </header>

      {error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to sign in. Please try again.')}
        </p>
      )}

      <form className="login-form" onSubmit={handleSubmit}>
        <div className="login-form__section">
          <div className="login-form__section-intro">
            <span className="login-form__number">01 / Sign in</span>
            <h2>Your sign-in details.</h2>
            <p>Use the email and password you chose when creating your account.</p>
          </div>

          <div className="login-form__fields">
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
              {emailError && <p id="login-email-error" className="field-error">{emailError}</p>}
            </div>

            <div>
              <label htmlFor="login-password">Password</label>
              <div className="password-input">
                <input
                  id="login-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  required
                  maxLength={128}
                  value={password}
                  aria-invalid={passwordError ? true : undefined}
                  aria-describedby={passwordError ? 'login-password-error' : undefined}
                  onChange={(event) => setPassword(event.target.value)}
                />
                <PasswordVisibilityButton
                  visible={showPassword}
                  onToggle={() => setShowPassword((visible) => !visible)}
                  inputId="login-password"
                  label="password"
                />
              </div>
              {passwordError && <p id="login-password-error" className="field-error">{passwordError}</p>}
            </div>

            <div className="login-form__actions">
              <button type="submit" disabled={isSubmitting}>
                {isSubmitting ? 'Signing in...' : 'Sign in'}
                {!isSubmitting && <span aria-hidden="true">→</span>}
              </button>
              <p>Need an account? <Link to="/register">Create one</Link></p>
            </div>
          </div>
        </div>
      </form>
    </section>
  )
}

export default LoginPage
