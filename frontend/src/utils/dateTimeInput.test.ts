import { describe, expect, it } from 'vitest'
import { toDateTimeLocalValue, toUtcInstant } from './dateTimeInput'

describe('dateTimeInput', () => {
  it('round-trips an instant through a datetime-local value', () => {
    const instant = '2099-04-01T08:30:00.000Z'

    expect(toUtcInstant(toDateTimeLocalValue(instant))).toBe(instant)
  })

  it('returns an empty value for an invalid instant', () => {
    expect(toDateTimeLocalValue('not-a-date')).toBe('')
  })
})
