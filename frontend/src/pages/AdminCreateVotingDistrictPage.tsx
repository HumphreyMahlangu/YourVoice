import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import ApiError from '../api/ApiError'
import { createVotingDistrict } from '../api/adminManagement'
import { useAuth } from '../auth/useAuth'
import type { VotingDistrict } from '../types/district'
import { getErrorMessage } from '../utils/getErrorMessage'

function AdminCreateVotingDistrictPage() {
  const { session, logout } = useAuth()
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [province, setProvince] = useState('')
  const [municipality, setMunicipality] = useState('')
  const [wardNumber, setWardNumber] = useState('')
  const [createdDistrict, setCreatedDistrict] =
    useState<VotingDistrict | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [fieldErrors, setFieldErrors] = useState<
    Readonly<Record<string, string>>
  >({})

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!session) {
      return
    }

    setCreatedDistrict(null)
    setError(null)
    setFieldErrors({})
    setIsSubmitting(true)

    try {
      const district = await createVotingDistrict(
        {
          code,
          name,
          province,
          municipality,
          wardNumber: Number(wardNumber),
        },
        session.accessToken,
      )
      setCreatedDistrict(district)
      setCode('')
      setName('')
      setProvince('')
      setMunicipality('')
      setWardNumber('')
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        logout()
        return
      }

      setError(requestError)

      if (requestError instanceof ApiError) {
        setFieldErrors(requestError.fieldErrors)
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  const codeError = fieldErrors.code
  const nameError = fieldErrors.name
  const provinceError = fieldErrors.province
  const municipalityError = fieldErrors.municipality
  const wardNumberError = fieldErrors.wardNumber

  return (
    <section className="page admin-page workflow-page" aria-labelledby="create-district-heading">
      <Link to="/admin">Back to admin dashboard</Link>
      <h1 id="create-district-heading">Create voting district</h1>

      {createdDistrict !== null && (
        <section aria-labelledby="district-created-heading">
          <h2 id="district-created-heading">Voting district created</h2>
          <p role="status">
            {createdDistrict.code} — {createdDistrict.name}, ward{' '}
            {createdDistrict.wardNumber}
          </p>
        </section>
      )}

      {error !== null && (
        <p role="alert">
          {getErrorMessage(error, 'Unable to create the voting district.')}
        </p>
      )}

      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="district-code">District code</label>
          <input
            id="district-code"
            name="code"
            type="text"
            required
            maxLength={32}
            pattern="[A-Za-z0-9-]+"
            value={code}
            aria-invalid={codeError ? true : undefined}
            aria-describedby={codeError ? 'district-code-error' : undefined}
            onChange={(event) => setCode(event.target.value)}
          />
          {codeError && <p id="district-code-error">{codeError}</p>}
        </div>

        <div>
          <label htmlFor="district-name">District name</label>
          <input
            id="district-name"
            name="name"
            type="text"
            required
            maxLength={160}
            value={name}
            aria-invalid={nameError ? true : undefined}
            aria-describedby={nameError ? 'district-name-error' : undefined}
            onChange={(event) => setName(event.target.value)}
          />
          {nameError && <p id="district-name-error">{nameError}</p>}
        </div>

        <div>
          <label htmlFor="district-province">Province</label>
          <input
            id="district-province"
            name="province"
            type="text"
            required
            maxLength={80}
            value={province}
            aria-invalid={provinceError ? true : undefined}
            aria-describedby={
              provinceError ? 'district-province-error' : undefined
            }
            onChange={(event) => setProvince(event.target.value)}
          />
          {provinceError && (
            <p id="district-province-error">{provinceError}</p>
          )}
        </div>

        <div>
          <label htmlFor="district-municipality">Municipality</label>
          <input
            id="district-municipality"
            name="municipality"
            type="text"
            required
            maxLength={160}
            value={municipality}
            aria-invalid={municipalityError ? true : undefined}
            aria-describedby={
              municipalityError ? 'district-municipality-error' : undefined
            }
            onChange={(event) => setMunicipality(event.target.value)}
          />
          {municipalityError && (
            <p id="district-municipality-error">{municipalityError}</p>
          )}
        </div>

        <div>
          <label htmlFor="district-ward-number">Ward number</label>
          <input
            id="district-ward-number"
            name="wardNumber"
            type="number"
            required
            min={1}
            max={9999}
            step={1}
            value={wardNumber}
            aria-invalid={wardNumberError ? true : undefined}
            aria-describedby={
              wardNumberError ? 'district-ward-number-error' : undefined
            }
            onChange={(event) => setWardNumber(event.target.value)}
          />
          {wardNumberError && (
            <p id="district-ward-number-error">{wardNumberError}</p>
          )}
        </div>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Creating district...' : 'Create voting district'}
        </button>
      </form>
    </section>
  )
}

export default AdminCreateVotingDistrictPage
