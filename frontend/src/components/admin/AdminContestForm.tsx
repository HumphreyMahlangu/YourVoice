import { useState, type FormEvent } from 'react'
import ApiError from '../../api/ApiError'
import { createContest } from '../../api/adminManagement'
import type { Contest, ContestType } from '../../types/contest'
import type { ElectionType } from '../../types/election'
import { formatEnumLabel } from '../../utils/formatters'
import { getAdminErrorMessage, getAdminFieldErrors } from '../../utils/adminErrors'

interface AdminContestFormProps {
  electionId: string
  electionType: ElectionType
  authToken: string
  onCreated: (contest: Contest) => void
  onUnauthorized: () => void
}

function getAllowedContestTypes(electionType: ElectionType) {
  switch (electionType) {
    case 'NATIONAL':
      return ['NATIONAL', 'PROVINCIAL'] satisfies ContestType[]
    case 'PROVINCIAL':
      return ['PROVINCIAL'] satisfies ContestType[]
    case 'MUNICIPAL':
      return ['MUNICIPAL_PR', 'MUNICIPAL_WARD'] satisfies ContestType[]
  }
}

function AdminContestForm({
  electionId,
  electionType,
  authToken,
  onCreated,
  onUnauthorized,
}: AdminContestFormProps) {
  const allowedContestTypes = getAllowedContestTypes(electionType)
  const [name, setName] = useState('')
  const [contestType, setContestType] = useState<ContestType>(
    allowedContestTypes[0],
  )
  const [displayOrder, setDisplayOrder] = useState('')
  const [scopeProvince, setScopeProvince] = useState('')
  const [scopeMunicipality, setScopeMunicipality] = useState('')
  const [scopeWardNumber, setScopeWardNumber] = useState('')
  const [createdContestName, setCreatedContestName] = useState<string | null>(
    null,
  )
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [fieldErrors, setFieldErrors] = useState<
    Readonly<Record<string, string>>
  >({})

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setCreatedContestName(null)
    setError(null)
    setFieldErrors({})
    setIsSubmitting(true)

    try {
      const contest = await createContest(
        electionId,
        {
          name,
          type: contestType,
          displayOrder: Number(displayOrder),
          scopeProvince:
            contestType === 'NATIONAL' ? null : scopeProvince,
          scopeMunicipality:
            contestType === 'MUNICIPAL_PR' ||
            contestType === 'MUNICIPAL_WARD'
              ? scopeMunicipality
              : null,
          scopeWardNumber:
            contestType === 'MUNICIPAL_WARD'
              ? Number(scopeWardNumber)
              : null,
        },
        authToken,
      )
      onCreated(contest)
      setCreatedContestName(contest.name)
      setName('')
      setDisplayOrder('')
      setScopeProvince('')
      setScopeMunicipality('')
      setScopeWardNumber('')
    } catch (requestError) {
      if (requestError instanceof ApiError && requestError.status === 401) {
        onUnauthorized()
        return
      }

      setError(requestError)

      if (requestError instanceof ApiError) {
        setFieldErrors(getAdminFieldErrors(requestError))
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  function handleContestTypeChange(nextType: ContestType) {
    setContestType(nextType)

    if (nextType === 'NATIONAL') {
      setScopeProvince('')
    }

    if (nextType === 'NATIONAL' || nextType === 'PROVINCIAL') {
      setScopeMunicipality('')
    }

    if (nextType !== 'MUNICIPAL_WARD') {
      setScopeWardNumber('')
    }
  }

  const nameError = fieldErrors.name
  const typeError = fieldErrors.type
  const displayOrderError = fieldErrors.displayOrder
  const scopeProvinceError = fieldErrors.scopeProvince
  const scopeMunicipalityError = fieldErrors.scopeMunicipality
  const scopeWardNumberError = fieldErrors.scopeWardNumber

  return (
    <section aria-labelledby="create-contest-heading">
      <h2 id="create-contest-heading">Create contest</h2>

      {createdContestName !== null && (
        <p role="status">{createdContestName} was created.</p>
      )}

      {error !== null && (
        <p role="alert">
          {getAdminErrorMessage(error, 'Unable to create the contest.')}
        </p>
      )}

      <form onSubmit={handleSubmit}>
        <div>
          <label htmlFor="contest-name">Contest name</label>
          <input
            id="contest-name"
            name="name"
            type="text"
            required
            maxLength={160}
            value={name}
            aria-invalid={nameError ? true : undefined}
            aria-describedby={nameError ? 'contest-name-error' : undefined}
            onChange={(event) => setName(event.target.value)}
          />
          {nameError && <p id="contest-name-error">{nameError}</p>}
        </div>

        <div>
          <label htmlFor="contest-type">Contest type</label>
          <select
            id="contest-type"
            name="type"
            required
            value={contestType}
            aria-invalid={typeError ? true : undefined}
            aria-describedby={typeError ? 'contest-type-error' : undefined}
            onChange={(event) =>
              handleContestTypeChange(event.target.value as ContestType)
            }
          >
            {allowedContestTypes.map((type) => (
              <option key={type} value={type}>
                {formatEnumLabel(type)}
              </option>
            ))}
          </select>
          {typeError && <p id="contest-type-error">{typeError}</p>}
        </div>

        <div>
          <label htmlFor="contest-order">Display order</label>
          <input
            id="contest-order"
            name="displayOrder"
            type="number"
            required
            min={1}
            max={9999}
            step={1}
            value={displayOrder}
            aria-invalid={displayOrderError ? true : undefined}
            aria-describedby={
              displayOrderError ? 'contest-order-error' : undefined
            }
            onChange={(event) => setDisplayOrder(event.target.value)}
          />
          {displayOrderError && (
            <p id="contest-order-error">{displayOrderError}</p>
          )}
        </div>

        {contestType !== 'NATIONAL' && (
          <div>
            <label htmlFor="contest-province">Province scope</label>
            <input
              id="contest-province"
              name="scopeProvince"
              type="text"
              required
              maxLength={80}
              value={scopeProvince}
              aria-invalid={scopeProvinceError ? true : undefined}
              aria-describedby={
                scopeProvinceError ? 'contest-province-error' : undefined
              }
              onChange={(event) => setScopeProvince(event.target.value)}
            />
            {scopeProvinceError && (
              <p id="contest-province-error">{scopeProvinceError}</p>
            )}
          </div>
        )}

        {(contestType === 'MUNICIPAL_PR' ||
          contestType === 'MUNICIPAL_WARD') && (
          <div>
            <label htmlFor="contest-municipality">Municipality scope</label>
            <input
              id="contest-municipality"
              name="scopeMunicipality"
              type="text"
              required
              maxLength={160}
              value={scopeMunicipality}
              aria-invalid={scopeMunicipalityError ? true : undefined}
              aria-describedby={
                scopeMunicipalityError
                  ? 'contest-municipality-error'
                  : undefined
              }
              onChange={(event) => setScopeMunicipality(event.target.value)}
            />
            {scopeMunicipalityError && (
              <p id="contest-municipality-error">{scopeMunicipalityError}</p>
            )}
          </div>
        )}

        {contestType === 'MUNICIPAL_WARD' && (
          <div>
            <label htmlFor="contest-ward-number">Ward number scope</label>
            <input
              id="contest-ward-number"
              name="scopeWardNumber"
              type="number"
              required
              min={1}
              max={9999}
              step={1}
              value={scopeWardNumber}
              aria-invalid={scopeWardNumberError ? true : undefined}
              aria-describedby={
                scopeWardNumberError
                  ? 'contest-ward-number-error'
                  : undefined
              }
              onChange={(event) => setScopeWardNumber(event.target.value)}
            />
            {scopeWardNumberError && (
              <p id="contest-ward-number-error">{scopeWardNumberError}</p>
            )}
          </div>
        )}

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Creating contest...' : 'Create contest'}
        </button>
      </form>
    </section>
  )
}

export default AdminContestForm
