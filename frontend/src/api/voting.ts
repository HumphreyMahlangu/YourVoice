import type {
  BallotCastRequest,
  BallotCastResponse,
  BallotKey,
  VotingCredential,
} from '../types/voting'
import { apiRequest } from './client'

function getContestPath(electionId: string, contestId: string) {
  const encodedElectionId = encodeURIComponent(electionId)
  const encodedContestId = encodeURIComponent(contestId)
  return `/api/v1/elections/${encodedElectionId}/contests/${encodedContestId}`
}

export function getBallotKey(electionId: string, contestId: string) {
  return apiRequest<BallotKey>(
    `${getContestPath(electionId, contestId)}/ballot-key`,
  )
}

export function issueVotingCredential(
  electionId: string,
  contestId: string,
  blindedToken: string,
  authToken: string,
) {
  return apiRequest<VotingCredential>(
    `${getContestPath(electionId, contestId)}/credentials`,
    {
      method: 'POST',
      authToken,
      body: { blindedToken },
    },
  )
}

export function castBallot(request: BallotCastRequest) {
  return apiRequest<BallotCastResponse>('/api/v1/ballots', {
    method: 'POST',
    body: request,
  })
}
