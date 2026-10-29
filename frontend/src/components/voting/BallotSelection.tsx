import type { FormEvent } from 'react'
import type { ContestOption } from '../../types/contest'
import { formatEnumLabel } from '../../utils/formatters'

interface BallotSelectionProps {
  options: ContestOption[]
  selectedOptionId: string
  onSelect: (optionId: string) => void
  onReview: () => void
}

function BallotSelection({
  options,
  selectedOptionId,
  onSelect,
  onReview,
}: BallotSelectionProps) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onReview()
  }

  return (
    <form onSubmit={handleSubmit}>
      <fieldset>
        <legend>Select one ballot option</legend>
        {options.map((option) => (
          <label key={option.id} htmlFor={`option-${option.id}`}>
            <input
              id={`option-${option.id}`}
              name="contestOption"
              type="radio"
              required
              value={option.id}
              checked={selectedOptionId === option.id}
              onChange={(event) => onSelect(event.target.value)}
            />
            <span>{option.name} ({formatEnumLabel(option.optionType)})</span>
          </label>
        ))}
      </fieldset>
      {options.length === 0 ? (
        <p>No ballot options are available.</p>
      ) : (
        <button type="submit">Review selection</button>
      )}
    </form>
  )
}

export default BallotSelection
