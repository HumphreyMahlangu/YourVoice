import type { Contest } from './contest'
import type { Election } from './election'
export type VerificationStatus = 'UNVERIFIED' | 'PENDING' | 'VERIFIED' | 'REJECTED' | 'UNAVAILABLE'
export interface LocationSelection { provinceId?: string; municipalityId?: string; votingDistrictId?: string }
export interface RegisteredLocation {
  provinceId: string | null; province: string | null; municipalityId: string | null; municipality: string | null
  votingDistrictId: string | null; districtCode: string | null; districtName: string | null; wardNumber: number | null
}
export interface ProfileVerification {
  fullName: string; source: 'FICTIONAL_SIMULATION'; sourceReference: string | null
  identityStatus: VerificationStatus; locationStatus: VerificationStatus; sourceAvailable: boolean
  location: RegisteredLocation; message: string; checkedAt: string | null; effectiveAt: string | null
}
export interface Province { id: string; name: string }
export interface Municipality extends Province { provinceId: string }
export interface LocationCorrection {
  id: string; voterName: string; requestedLocation: RegisteredLocation; reason: string
  status: 'PENDING' | 'APPROVED' | 'APPLIED' | 'REJECTED'; requestedAt: string
  reviewedAt: string | null; reviewReason: string | null; sourceReference: string | null; effectiveAt: string | null
}
export interface SimulationRecord {
  id: string; reference: string; fictionalName: string; source: string; identityStatus: VerificationStatus
  locationStatus: VerificationStatus; location: RegisteredLocation; reason: string; recordedAt: string
}
export interface CreateSimulationRecord {
  reference: string; fictional: boolean; fullName: string; southAfricanIdNumber: string
  identityStatus: VerificationStatus; locationStatus: VerificationStatus; location: LocationSelection; reason: string
}
export interface ProfileAudit {
  id: string; voterProfileId: string | null; actorUserId: string; correctionRequestId: string | null
  action: string; sourceReference: string | null; reason: string; occurredAt: string; effectiveAt: string | null
}
export interface VoterBallot {
  election: Election; contest: Contest; eligible: boolean; registered: boolean; registrationVerified: boolean
  canRegister: boolean; canVote: boolean; credentialIssued: boolean; message: string
  registeredProvince: string | null; registeredMunicipality: string | null; registeredWardNumber: number | null
}
