/** Converts a `datetime-local` input value (browser local time) to a UTC ISO instant. */
export function toUtcInstant(localDateTime: string) {
  return new Date(localDateTime).toISOString()
}

/** Converts a UTC ISO instant to a `datetime-local` input value in browser local time. */
export function toDateTimeLocalValue(instant: string) {
  const date = new Date(instant)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const offsetMs = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16)
}
