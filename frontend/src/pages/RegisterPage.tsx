import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import ApiError from '../api/ApiError'
import { getVotingDistricts } from '../api/districts'
import { useAuth } from '../auth/useAuth'
import PasswordVisibilityButton from '../components/PasswordVisibilityButton'
import type { VotingDistrict } from '../types/district'
import type { IdDocumentType } from '../types/registration'
import { formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

const documentTypes: IdDocumentType[] = [
  'GREEN_BARCODED_ID',
  'SMART_ID_CARD',
  'TEMPORARY_ID_CERTIFICATE',
]

function getPasswordFeedback(password: string) {
  const hasLowercase = /[a-z]/.test(password)
  const hasUppercase = /[A-Z]/.test(password)
  const hasNumber = /\d/.test(password)
  const hasSymbol = /[^A-Za-z0-9]/.test(password)
  const missingRequirements = [
    password.length < 12 ? '12 characters' : null,
    !hasUppercase ? 'an uppercase letter' : null,
    !hasLowercase ? 'a lowercase letter' : null,
    !hasNumber ? 'a number' : null,
  ].filter((requirement): requirement is string => requirement !== null)

  if (!password) return { strength: 0, missingRequirements }

  const characterTypes = [hasLowercase, hasUppercase, hasNumber, hasSymbol]
    .filter(Boolean).length
  let strength = 1
  if (password.length >= 12) strength = 2
  if (password.length >= 16 && characterTypes >= 3) strength = 3
  if ((password.length >= 20 && characterTypes >= 3) ||
      (password.length >= 16 && characterTypes === 4)) strength = 4

  return {
    strength: missingRequirements.length > 0 ? Math.min(strength, 2) : strength,
    missingRequirements,
  }
}

const strengthLabels = ['Not measured', 'Weak', 'Fair', 'Good', 'Strong']

function RegisterPage() {
  const navigate = useNavigate()
  const { session, register } = useAuth()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmation, setShowConfirmation] = useState(false)
  const [confirmationTouched, setConfirmationTouched] = useState(false)
  const [southAfricanIdNumber, setSouthAfricanIdNumber] = useState('')
  const [idDocumentType, setIdDocumentType] =
    useState<IdDocumentType>('SMART_ID_CARD')
  const [votingDistrictId, setVotingDistrictId] = useState('')
  const [districts, setDistricts] = useState<VotingDistrict[]>([])
  const [isLoadingDistricts, setIsLoadingDistricts] = useState(true)
  const [districtLoadError, setDistrictLoadError] = useState<unknown>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [fieldErrors, setFieldErrors] = useState<
    Readonly<Record<string, string>>
  >({})

  useEffect(() => {
    const abortController = new AbortController()

    async function loadDistricts() {
      setDistrictLoadError(null)
      setIsLoadingDistricts(true)

      try {
        const response = await getVotingDistricts(abortController.signal)
        const sortedDistricts = response.toSorted((left, right) =>
          `${left.province}-${left.municipality}-${left.code}`.localeCompare(
            `${right.province}-${right.municipality}-${right.code}`,
          ),
        )

        if (!abortController.signal.aborted) {
          setDistricts(sortedDistricts)
        }
      } catch (requestError) {
        if (!abortController.signal.aborted) {
          setDistrictLoadError(requestError)
        }
      } finally {
        if (!abortController.signal.aborted) {
          setIsLoadingDistricts(false)
        }
      }
    }

    void loadDistricts()
    return () => abortController.abort()
  }, [])

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
    setConfirmationTouched(true)

    if (getPasswordFeedback(password).missingRequirements.length > 0) {
      setFieldErrors({
        password: 'Use at least 12 characters with an uppercase letter, lowercase letter, and number.',
      })
      return
    }

    if (password !== confirmPassword) {
      return
    }

    setIsSubmitting(true)

    try {
      const authenticatedSession = await register({
        fullName,
        email,
        password,
        southAfricanIdNumber,
        idDocumentType,
        votingDistrictId,
      })
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

  const fullNameError = fieldErrors.fullName
  const emailError = fieldErrors.email
  const passwordError = fieldErrors.password
  const confirmationError = confirmationTouched && confirmPassword !== password
    ? 'Passwords do not match.'
    : null
  const { strength, missingRequirements } = getPasswordFeedback(password)
  const passwordDescription = [
    'register-password-help',
    'register-password-strength',
    password && missingRequirements.length > 0 ? 'register-password-missing' : null,
    passwordError ? 'register-password-error' : null,
  ].filter(Boolean).join(' ')
  const idNumberError = fieldErrors.southAfricanIdNumber
  const documentTypeError = fieldErrors.idDocumentType
  const votingDistrictError = fieldErrors.votingDistrictId

  return (
    <section className="page auth-page register-page" aria-labelledby="register-heading">
      <header className="register-intro">
        <div className="register-intro__main">
          <p className="eyebrow">YourVoice / South Africa</p>
          <h1 id="register-heading">Create your voter account.</h1>
        </div>
        <p className="register-intro__aside">
          Set up your profile to take part in elections. Your identity stays
          separate from the ballot you cast.
        </p>
      </header>

      {error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to create your account.')}
        </p>
      )}

      {districtLoadError !== null && (
        <p role="alert">
          We couldn't load the area list. Please refresh and try again.
        </p>
      )}

      <form className="register-form" onSubmit={handleSubmit}>
        <div className="register-form__section">
          <div className="register-form__section-intro">
            <span className="register-form__number">01 / Personal details</span>
            <h2>Your details.</h2>
            <p>Use the details shown on your identity document.</p>
          </div>
          <div className="register-form__fields">
            <div>
              <label htmlFor="register-full-name">Full legal name</label>
              <input
                id="register-full-name"
                name="fullName"
                type="text"
                autoComplete="name"
                required
                maxLength={160}
                value={fullName}
                aria-invalid={fullNameError ? true : undefined}
                aria-describedby={
                  fullNameError ? 'register-full-name-error' : undefined
                }
                onChange={(event) => setFullName(event.target.value)}
              />
              {fullNameError && (
                <p id="register-full-name-error">{fullNameError}</p>
              )}
            </div>

            <div>
              <label htmlFor="register-email">Email address</label>
              <input
                id="register-email"
                name="email"
                type="email"
                autoComplete="email"
                required
                maxLength={320}
                value={email}
                aria-invalid={emailError ? true : undefined}
                aria-describedby={
                  emailError
                    ? 'register-email-help register-email-error'
                    : 'register-email-help'
                }
                onChange={(event) => setEmail(event.target.value)}
              />
              {emailError && <p id="register-email-error" className="field-error">{emailError}</p>}
              <p id="register-email-help">
            You'll use this email to sign in.
              </p>
            </div>

            <div>
              <label htmlFor="register-south-african-id-number">
                South African ID number
              </label>
              <input
                id="register-south-african-id-number"
                name="southAfricanIdNumber"
                type="text"
                inputMode="numeric"
                pattern="[0-9]{13}"
                autoComplete="off"
                required
                minLength={13}
                maxLength={13}
                value={southAfricanIdNumber}
                aria-invalid={idNumberError ? true : undefined}
                aria-describedby={
                  idNumberError
                    ? 'register-id-number-help register-id-number-error'
                    : 'register-id-number-help'
                }
                onChange={(event) =>
                  setSouthAfricanIdNumber(event.target.value)
                }
              />
              {idNumberError && (
                <p id="register-id-number-error" className="field-error">{idNumberError}</p>
              )}
              <p id="register-id-number-help">
                Enter the 13-digit number shown on your South African ID document.
              </p>
            </div>

            <div>
              <label htmlFor="register-id-document-type">
                Identity document type
              </label>
              <select
                id="register-id-document-type"
                name="idDocumentType"
                required
                value={idDocumentType}
                aria-invalid={documentTypeError ? true : undefined}
                aria-describedby={
                  documentTypeError
                    ? 'register-document-type-error'
                    : undefined
                }
                onChange={(event) =>
                  setIdDocumentType(event.target.value as IdDocumentType)
                }
              >
                {documentTypes.map((documentType) => (
                  <option key={documentType} value={documentType}>
                    {formatEnumLabel(documentType)}
                  </option>
                ))}
              </select>
              {documentTypeError && (
                <p id="register-document-type-error">{documentTypeError}</p>
              )}
            </div>

            <div className="register-form__field--wide">
              <label htmlFor="register-voting-district">Your province, municipality and ward</label>
              <select
                id="register-voting-district"
                name="votingDistrictId"
                required
                value={votingDistrictId}
                aria-invalid={votingDistrictError ? true : undefined}
                aria-describedby={
                  votingDistrictError
                    ? 'register-voting-district-help register-voting-district-error'
                    : 'register-voting-district-help'
                }
                onChange={(event) => setVotingDistrictId(event.target.value)}
              >
                <option value="">
                  {isLoadingDistricts
                    ? 'Loading areas...'
                    : 'Select your province, municipality and ward'}
                </option>
                {districts.map((district) => (
                  <option key={district.id} value={district.id}>
                    {district.province} · {district.municipality} · Ward{' '}
                    {district.wardNumber} — {district.name} ({district.code})
                  </option>
                ))}
              </select>
              {votingDistrictError && (
                <p id="register-voting-district-error">{votingDistrictError}</p>
              )}
              <p id="register-voting-district-help">
                This decides which provincial and local contests you can vote in.
                Your ballot is still cast online.
              </p>
              {!isLoadingDistricts && districtLoadError === null && districts.length === 0 && (
                <p>No areas are currently available.</p>
              )}
            </div>
          </div>
        </div>

        <div className="register-form__section">
          <div className="register-form__section-intro">
            <span className="register-form__number">02 / Account security</span>
            <h2>Choose a password.</h2>
            <p>Use this password when you sign in to your account.</p>
          </div>
          <div className="register-form__fields">
            <div>
              <label htmlFor="register-password">Password</label>
              <div className="password-input">
                <input
                  id="register-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  required
                  minLength={12}
                  maxLength={128}
                  value={password}
                  aria-invalid={passwordError ? true : undefined}
                  aria-describedby={passwordDescription}
                  onChange={(event) => {
                    setPassword(event.target.value)
                    setFieldErrors((current) => ({ ...current, password: '' }))
                  }}
                />
                <PasswordVisibilityButton
                  visible={showPassword}
                  onToggle={() => setShowPassword((visible) => !visible)}
                  inputId="register-password"
                  label="password"
                />
              </div>
              <p id="register-password-help">
                Use at least 12 characters with an uppercase letter, lowercase
                letter, and number.
              </p>
              <div id="register-password-strength" className="password-strength">
                <div className="password-strength__bar" role="meter" aria-label="Password strength" aria-valuemin={0} aria-valuemax={4} aria-valuenow={strength} aria-valuetext={strengthLabels[strength]}>
                  {[1, 2, 3, 4].map((segment) => (
                    <span key={segment} className={segment <= strength ? `password-strength__segment password-strength__segment--${strength}` : 'password-strength__segment'} />
                  ))}
                </div>
                <p aria-live="polite" aria-atomic="true">Strength: {strengthLabels[strength]}</p>
              </div>
              {password && missingRequirements.length > 0 && (
                <p id="register-password-missing" className="password-requirements">
                  Still needed: {missingRequirements.join(', ')}.
                </p>
              )}
              {passwordError && (
                <p id="register-password-error" className="field-error">{passwordError}</p>
              )}
            </div>

            <div>
              <label htmlFor="register-confirm-password">Confirm password</label>
              <div className="password-input">
                <input
                  id="register-confirm-password"
                  name="confirmPassword"
                  type={showConfirmation ? 'text' : 'password'}
                  autoComplete="new-password"
                  required
                  maxLength={128}
                  value={confirmPassword}
                  aria-invalid={confirmationError ? true : undefined}
                  aria-describedby={confirmationError ? 'register-confirm-password-error' : undefined}
                  onBlur={() => setConfirmationTouched(true)}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                />
                <PasswordVisibilityButton
                  visible={showConfirmation}
                  onToggle={() => setShowConfirmation((visible) => !visible)}
                  inputId="register-confirm-password"
                  label="confirmation password"
                />
              </div>
              {confirmationError && <p id="register-confirm-password-error" className="field-error">{confirmationError}</p>}
            </div>
          </div>
        </div>

        <div className="register-form__actions">
          <button
            type="submit"
            disabled={
              isSubmitting || isLoadingDistricts || districts.length === 0
            }
          >
            {isSubmitting ? 'Creating account...' : 'Create account'}
            {!isSubmitting && <span aria-hidden="true">→</span>}
          </button>
          <p>Already registered? <Link to="/login">Sign in</Link></p>
        </div>
      </form>
    </section>
  )
}

export default RegisterPage
