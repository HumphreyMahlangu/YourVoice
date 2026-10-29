import ApiError from '../api/ApiError'

/** Never show transport, parser or database details in voter flows. */
export function getVoterErrorMessage(error: unknown, fallback: string) {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your session has ended. Sign in again to continue.'
    if (error.status === 403) return 'Your account cannot perform this action.'
    if (error.status === 503) return 'The fictional verification source is unavailable. Please try again later.'
    if (error.status === 409) return 'Your profile or registration has changed, or a request is already open. Refresh its details before trying again.'
    if (error.status === 400 || error.status === 422) return 'Check your fictional verification, requested area and election dates, then try again. An administrator can help with an unmatched record.'
    if (error.status === 429) return 'Too many attempts. Wait a moment and try again.'
  }
  return fallback
}
