import type {
  Contest,
  ContestOption,
  CreateContestOptionRequest,
  CreateContestRequest,
} from '../types/contest'
import type {
  CreateVotingDistrictRequest,
  VotingDistrict,
} from '../types/district'
import type {
  CreateElectionRequest,
  Election,
  UpdateElectionRequest,
} from '../types/election'
import type { ElectionLifecycleEvent } from '../types/electionLifecycle'
import { apiRequest } from './client'

const ADMIN_PATH = '/api/v1/admin'

export function createVotingDistrict(
  request: CreateVotingDistrictRequest,
  authToken: string,
) {
  return apiRequest<VotingDistrict>(`${ADMIN_PATH}/voting-districts`, {
    method: 'POST',
    authToken,
    body: request,
  })
}

export function createElection(
  request: CreateElectionRequest,
  authToken: string,
) {
  return apiRequest<Election>(`${ADMIN_PATH}/elections`, {
    method: 'POST',
    authToken,
    body: request,
  })
}

export function createContest(
  electionId: string,
  request: CreateContestRequest,
  authToken: string,
) {
  const encodedElectionId = encodeURIComponent(electionId)

  return apiRequest<Contest>(
    `${ADMIN_PATH}/elections/${encodedElectionId}/contests`,
    {
      method: 'POST',
      authToken,
      body: request,
    },
  )
}

export function createContestOption(
  electionId: string,
  contestId: string,
  request: CreateContestOptionRequest,
  authToken: string,
) {
  const encodedElectionId = encodeURIComponent(electionId)
  const encodedContestId = encodeURIComponent(contestId)

  return apiRequest<ContestOption>(
    `${ADMIN_PATH}/elections/${encodedElectionId}/contests/${encodedContestId}/options`,
    {
      method: 'POST',
      authToken,
      body: request,
    },
  )
}

export function getElectionLifecycleEvents(
  electionId: string,
  authToken: string,
  signal?: AbortSignal,
) {
  const encodedElectionId = encodeURIComponent(electionId)

  return apiRequest<ElectionLifecycleEvent[]>(
    `${ADMIN_PATH}/elections/${encodedElectionId}/lifecycle-events`,
    {
      authToken,
      signal,
    },
  )
}

export function cancelElection(electionId: string, authToken: string) {
  const encodedElectionId = encodeURIComponent(electionId)

  return apiRequest<Election>(
    `${ADMIN_PATH}/elections/${encodedElectionId}/status`,
    {
      method: 'PATCH',
      authToken,
      body: { status: 'CANCELLED' },
    },
  )
}

export function updateElection(
  electionId: string,
  request: UpdateElectionRequest,
  authToken: string,
) {
  return apiRequest<Election>(
    `${ADMIN_PATH}/elections/${encodeURIComponent(electionId)}`,
    { method: 'PUT', authToken, body: request },
  )
}

function contestPath(electionId: string, contestId: string) {
  return `${ADMIN_PATH}/elections/${encodeURIComponent(electionId)}/contests/${encodeURIComponent(contestId)}`
}

export function updateContest(
  electionId: string,
  contestId: string,
  request: CreateContestRequest,
  authToken: string,
) {
  return apiRequest<Contest>(contestPath(electionId, contestId), {
    method: 'PUT',
    authToken,
    body: request,
  })
}

export function deleteContest(
  electionId: string,
  contestId: string,
  authToken: string,
) {
  return apiRequest<void>(contestPath(electionId, contestId), {
    method: 'DELETE',
    authToken,
  })
}

export function updateContestOption(
  electionId: string,
  contestId: string,
  optionId: string,
  request: CreateContestOptionRequest,
  authToken: string,
) {
  return apiRequest<ContestOption>(
    `${contestPath(electionId, contestId)}/options/${encodeURIComponent(optionId)}`,
    { method: 'PUT', authToken, body: request },
  )
}

export function deleteContestOption(
  electionId: string,
  contestId: string,
  optionId: string,
  authToken: string,
) {
  return apiRequest<void>(
    `${contestPath(electionId, contestId)}/options/${encodeURIComponent(optionId)}`,
    { method: 'DELETE', authToken },
  )
}

export function updateVotingDistrict(
  votingDistrictId: string,
  request: CreateVotingDistrictRequest,
  authToken: string,
) {
  return apiRequest<VotingDistrict>(
    `${ADMIN_PATH}/voting-districts/${encodeURIComponent(votingDistrictId)}`,
    { method: 'PUT', authToken, body: request },
  )
}

export function deleteVotingDistrict(votingDistrictId: string, authToken: string) {
  return apiRequest<void>(
    `${ADMIN_PATH}/voting-districts/${encodeURIComponent(votingDistrictId)}`,
    { method: 'DELETE', authToken },
  )
}
