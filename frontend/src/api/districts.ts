import type { VotingDistrict } from '../types/district'
import { apiRequest } from './client'

const PAGE_SIZE = 500

export async function getVotingDistricts(signal?: AbortSignal) {
  const districts: VotingDistrict[] = []
  // A short page ends the list, including when CORS hides X-Total-Count.
  // Do not return a partial list if any page fails.
  for (let page = 0; ; page += 1) {
    const batch = await apiRequest<VotingDistrict[]>(
      `/api/v1/voting-districts?page=${page}&size=${PAGE_SIZE}`, { signal },
    )
    if (!Array.isArray(batch)) throw new Error('Invalid area list response')
    districts.push(...batch)
    if (batch.length < PAGE_SIZE) return districts
  }
}
