import { useState, type FormEvent } from 'react'
import ApiError from '../../api/ApiError'
import { createContestOption } from '../../api/adminManagement'
import type {
  ContestOption,
  ContestOptionType,
} from '../../types/contest'
import { formatEnumLabel } from '../../utils/formatters'
import { getAdminErrorMessage, getAdminFieldErrors } from '../../utils/adminErrors'

interface AdminContestOptionFormProps {
  electionId: string
  contestId: string
  existingOptions: ContestOption[]
  authToken: string
  onCreated: (option: ContestOption) => void
  onUnauthorized: () => void
}

const OPTION_TYPES: ContestOptionType[] = [
  'PARTY',
  'INDEPENDENT_CANDIDATE',
  'BLANK_BALLOT',
  'SPOILT_BALLOT',
]

function AdminContestOptionForm({
  electionId,
  contestId,
  existingOptions,
  authToken,
  onCreated,
  onUnauthorized,
}: AdminContestOptionFormProps) {
  const [name, setName] = useState('')
  const [optionType, setOptionType] = useState<ContestOptionType>('PARTY')
  const [displayOrder, setDisplayOrder] = useState('')
  const [createdOptionName, setCreatedOptionName] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [fieldErrors, setFieldErrors] = useState<
    Readonly<Record<string, string>>
  >({})

  const availableOptionTypes = OPTION_TYPES.filter(
    (type) =>
      type === 'PARTY' ||
      type === 'INDEPENDENT_CANDIDATE' ||
      !existingOptions.some((option) => option.optionType === type),
  )

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setCreatedOptionName(null)
    setError(null)
    setFieldErrors({})
    setIsSubmitting(true)

    try {
      const option = await createContestOption(
        electionId,
        contestId,
        {
          name,
          optionType,
          displayOrder: Number(displayOrder),
        },
        authToken,
      )
      onCreated(option)
      setCreatedOptionName(option.name)
      setName('')
      setOptionType('PARTY')
      setDisplayOrder('')
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

  const nameError = fieldErrors.name
  const optionTypeError = fieldErrors.optionType
  const displayOrderError = fieldErrors.displayOrder

  return (
    <form onSubmit={handleSubmit}>
      <h4>Add ballot option</h4>

      {createdOptionName !== null && (
        <p role="status">{createdOptionName} was added to the ballot.</p>
      )}

      {error !== null && (
        <p role="alert">
          {getAdminErrorMessage(error, 'Unable to add the ballot option.')}
        </p>
      )}

      <div>
        <label htmlFor={`option-name-${contestId}`}>Option name</label>
        <input
          id={`option-name-${contestId}`}
          name="name"
          type="text"
          required
          maxLength={160}
          value={name}
          aria-invalid={nameError ? true : undefined}
          aria-describedby={
            nameError ? `option-name-error-${contestId}` : undefined
          }
          onChange={(event) => setName(event.target.value)}
        />
        {nameError && (
          <p id={`option-name-error-${contestId}`}>{nameError}</p>
        )}
      </div>

      <div>
        <label htmlFor={`option-type-${contestId}`}>Option type</label>
        <select
          id={`option-type-${contestId}`}
          name="optionType"
          required
          value={optionType}
          aria-invalid={optionTypeError ? true : undefined}
          aria-describedby={
            optionTypeError ? `option-type-error-${contestId}` : undefined
          }
          onChange={(event) =>
            setOptionType(event.target.value as ContestOptionType)
          }
        >
          {availableOptionTypes.map((type) => (
            <option key={type} value={type}>
              {formatEnumLabel(type)}
            </option>
          ))}
        </select>
        {optionTypeError && (
          <p id={`option-type-error-${contestId}`}>{optionTypeError}</p>
        )}
      </div>

      <div>
        <label htmlFor={`option-order-${contestId}`}>Display order</label>
        <input
          id={`option-order-${contestId}`}
          name="displayOrder"
          type="number"
          required
          min={1}
          max={9999}
          step={1}
          value={displayOrder}
          aria-invalid={displayOrderError ? true : undefined}
          aria-describedby={
            displayOrderError ? `option-order-error-${contestId}` : undefined
          }
          onChange={(event) => setDisplayOrder(event.target.value)}
        />
        {displayOrderError && (
          <p id={`option-order-error-${contestId}`}>{displayOrderError}</p>
        )}
      </div>

      <button type="submit" disabled={isSubmitting}>
        {isSubmitting ? 'Adding option...' : 'Add ballot option'}
      </button>
    </form>
  )
}

export default AdminContestOptionForm
