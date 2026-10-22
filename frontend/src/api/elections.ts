import type { Election } from '../types/election'
import { apiRequest } from './client'

const ELECTIONS_PATH = '/api/v1/elections'

export function getElections(signal?: AbortSignal, authToken?: string) {
  return apiRequest<Election[]>(ELECTIONS_PATH, { signal, authToken })
}

export function getElection(electionId: string, signal?: AbortSignal, authToken?: string) {
  const encodedElectionId = encodeURIComponent(electionId)
  return apiRequest<Election>(`${ELECTIONS_PATH}/${encodedElectionId}`, {
    signal,
    authToken,
  })
}
