import { useEffect, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import ApiError from '../api/ApiError'
import { getVotingDistricts } from '../api/districts'
import { useAuth } from '../auth/useAuth'
import type { VotingDistrict } from '../types/district'
import type { IdDocumentType } from '../types/registration'
import { formatEnumLabel } from '../utils/formatters'
import { getErrorMessage } from '../utils/getErrorMessage'

const documentTypes: IdDocumentType[] = [
  'GREEN_BARCODED_ID',
  'SMART_ID_CARD',
  'TEMPORARY_ID_CERTIFICATE',
]

function RegisterPage() {
  const navigate = useNavigate()
  const { session, register } = useAuth()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
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
  const idNumberError = fieldErrors.southAfricanIdNumber
  const documentTypeError = fieldErrors.idDocumentType
  const votingDistrictError = fieldErrors.votingDistrictId

  return (
    <section aria-labelledby="register-heading">
      <h1 id="register-heading">Create voter account</h1>

      {error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to create your account.')}
        </p>
      )}

      {districtLoadError !== null && (
        <p role="alert">
          {getErrorMessage(
            districtLoadError,
            'Unable to load voting districts.',
          )}
        </p>
      )}

      <form onSubmit={handleSubmit}>
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
          <p id="register-email-help">
            Your email is used to sign in. It is not used as proof of identity.
          </p>
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
          {emailError && <p id="register-email-error">{emailError}</p>}
        </div>

        <div>
          <label htmlFor="register-south-african-id-number">
            South African ID number
          </label>
          <p id="register-id-number-help">
            Enter all 13 digits. The API stores only a protected hash of this
            number, and you will not need to enter it again for each election.
          </p>
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
            <p id="register-id-number-error">{idNumberError}</p>
          )}
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

        <div>
          <label htmlFor="register-voting-district">Voting district</label>
          <select
            id="register-voting-district"
            name="votingDistrictId"
            required
            value={votingDistrictId}
            aria-invalid={votingDistrictError ? true : undefined}
            aria-describedby={
              votingDistrictError
                ? 'register-voting-district-error'
                : undefined
            }
            onChange={(event) => setVotingDistrictId(event.target.value)}
          >
            <option value="">
              {isLoadingDistricts
                ? 'Loading voting districts...'
                : 'Select your voting district'}
            </option>
            {districts.map((district) => (
              <option key={district.id} value={district.id}>
                {district.code} — {district.name}, {district.municipality}, ward{' '}
                {district.wardNumber}
              </option>
            ))}
          </select>
          {votingDistrictError && (
            <p id="register-voting-district-error">{votingDistrictError}</p>
          )}
          {!isLoadingDistricts && districts.length === 0 && (
            <p>No voting districts are currently available.</p>
          )}
        </div>

        <div>
          <label htmlFor="register-password">Password</label>
          <p id="register-password-help">
            Use at least 12 characters with an uppercase letter, lowercase
            letter, and number.
          </p>
          <input
            id="register-password"
            name="password"
            type="password"
            autoComplete="new-password"
            required
            minLength={12}
            maxLength={128}
            value={password}
            aria-invalid={passwordError ? true : undefined}
            aria-describedby={
              passwordError
                ? 'register-password-help register-password-error'
                : 'register-password-help'
            }
            onChange={(event) => setPassword(event.target.value)}
          />
          {passwordError && (
            <p id="register-password-error">{passwordError}</p>
          )}
        </div>

        <button
          type="submit"
          disabled={
            isSubmitting || isLoadingDistricts || districts.length === 0
          }
        >
          {isSubmitting ? 'Creating account...' : 'Create account'}
        </button>
      </form>

      <p>
        Already registered? <Link to="/login">Sign in</Link>
      </p>
    </section>
  )
}

export default RegisterPage
