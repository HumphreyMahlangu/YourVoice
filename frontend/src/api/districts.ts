import type { VotingDistrict } from '../types/district'
import { apiRequest } from './client'

const PAGE_SIZE = 500

function isVotingDistrict(value: unknown): value is VotingDistrict {
  if (typeof value !== 'object' || value === null) return false
  const district = value as Record<string, unknown>
  return ['id', 'code', 'name', 'province', 'municipality'].every(
    (field) => typeof district[field] === 'string' && district[field].trim().length > 0,
  ) && typeof district.wardNumber === 'number'
    && Number.isInteger(district.wardNumber)
    && district.wardNumber >= 1 && district.wardNumber <= 9999
}

export async function getVotingDistricts(signal?: AbortSignal) {
  const districts: VotingDistrict[] = []
  // A short page ends the list, including when CORS hides X-Total-Count.
  // Do not return a partial list if any page fails.
  for (let page = 0; ; page += 1) {
    const batch = await apiRequest<unknown>(
      `/api/v1/voting-districts?page=${page}&size=${PAGE_SIZE}`, { signal },
    )
    if (!Array.isArray(batch) || !batch.every(isVotingDistrict)) {
      throw new Error('Invalid area list response')
    }
    districts.push(...batch)
    if (batch.length < PAGE_SIZE) return districts
  }
}
