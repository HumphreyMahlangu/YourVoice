import type { Contest } from '../types/contest'
import { apiRequest } from './client'

export function getElectionContests(
  electionId: string,
  signal?: AbortSignal,
  authToken?: string,
) {
  const encodedElectionId = encodeURIComponent(electionId)

  return apiRequest<Contest[]>(
    `/api/v1/elections/${encodedElectionId}/contests`,
    { signal, authToken },
  )
}
