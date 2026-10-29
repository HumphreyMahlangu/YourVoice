import type { CreateSimulationRecord, LocationCorrection, LocationSelection, Municipality, ProfileAudit, ProfileVerification, Province, SimulationRecord, VoterBallot } from '../types/profile'
import { apiRequest } from './client'

function object(value: unknown): value is Record<string, unknown> { return typeof value === 'object' && value !== null }
function identified(value: unknown): value is Record<string, unknown> & { id: string } { return object(value) && typeof value.id === 'string' }
async function list<T>(path: string, token?: string, signal?: AbortSignal, valid: (row: unknown) => boolean = identified): Promise<T[]> {
  const rows: T[] = []
  for (let page = 0; ; page++) {
    const batch = await apiRequest<unknown>(`${path}${path.includes('?') ? '&' : '?'}page=${page}&size=500`, { authToken: token, signal })
    if (!Array.isArray(batch) || !batch.every(valid)) throw new Error('Invalid list response')
    rows.push(...batch as T[])
    if (batch.length < 500) return rows
  }
}
function profile(value: unknown): ProfileVerification {
  const statuses = ['UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED', 'UNAVAILABLE']
  if (!object(value) || value.source !== 'FICTIONAL_SIMULATION' || !statuses.includes(String(value.identityStatus))
      || !statuses.includes(String(value.locationStatus)) || !object(value.location) || typeof value.sourceAvailable !== 'boolean'
      || typeof value.fullName !== 'string' || typeof value.message !== 'string') throw new Error('Invalid profile response')
  return value as unknown as ProfileVerification
}
export async function getProfile(token: string, signal?: AbortSignal) { return profile(await apiRequest('/api/v1/me/profile', { authToken: token, signal })) }
export async function checkProfile(token: string) { return profile(await apiRequest('/api/v1/me/profile/verification', { authToken: token, method: 'POST' })) }
export async function getCorrections(token: string, signal?: AbortSignal) {
  const rows = await apiRequest<unknown>('/api/v1/me/profile/corrections', { authToken: token, signal })
  if (!Array.isArray(rows) || !rows.every(identified)) throw new Error('Invalid correction response')
  return rows as unknown as LocationCorrection[]
}
export function requestCorrection(token: string, location: LocationSelection, reason: string) {
  return apiRequest<LocationCorrection>('/api/v1/me/profile/corrections', { authToken: token, method: 'POST', body: { location, reason } })
}
async function geography<T>(path: string, signal?: AbortSignal): Promise<T[]> {
  const rows = await apiRequest<unknown>(path, { signal })
  if (!Array.isArray(rows) || !rows.every((row) => identified(row) && typeof row.name === 'string')) throw new Error('Invalid area response')
  return rows as T[]
}
export function getProvinces(signal?: AbortSignal) { return geography<Province>('/api/v1/geography/provinces', signal) }
export function getMunicipalities(signal?: AbortSignal) { return geography<Municipality>('/api/v1/geography/municipalities', signal) }
export function getMyBallots(token: string, signal?: AbortSignal, electionId?: string) {
  return list<VoterBallot>(`/api/v1/me/ballots${electionId ? `?electionId=${encodeURIComponent(electionId)}` : ''}`, token, signal,
    (row) => object(row) && identified(row.election) && identified(row.contest) && typeof row.message === 'string'
      && ['eligible', 'registered', 'registrationVerified', 'canRegister', 'canVote', 'credentialIssued'].every((key) => typeof row[key] === 'boolean'))
}
const adminPath = '/api/v1/admin/profile-verification'
export function getSourceRecords(token: string, signal?: AbortSignal) { return list<SimulationRecord>(`${adminPath}/records`, token, signal) }
export function createSourceRecord(token: string, body: CreateSimulationRecord) { return apiRequest<SimulationRecord>(`${adminPath}/records`, { authToken: token, method: 'POST', body }) }
export function getAdminCorrections(token: string, signal?: AbortSignal) { return list<LocationCorrection>(`${adminPath}/corrections`, token, signal) }
export function reviewCorrection(token: string, id: string, body: { approve: boolean; reason: string; sourceReference?: string; effectiveAt?: string }) {
  return apiRequest<LocationCorrection>(`${adminPath}/corrections/${id}/review`, { authToken: token, method: 'POST', body })
}
export function getProfileAudit(token: string, signal?: AbortSignal) { return list<ProfileAudit>(`${adminPath}/audit`, token, signal) }
export function createProvince(token: string, name: string) { return apiRequest<Province>('/api/v1/admin/geography/provinces', { authToken: token, method: 'POST', body: { name } }) }
export function createMunicipality(token: string, name: string, provinceId: string) { return apiRequest<Municipality>('/api/v1/admin/geography/municipalities', { authToken: token, method: 'POST', body: { name, provinceId } }) }
