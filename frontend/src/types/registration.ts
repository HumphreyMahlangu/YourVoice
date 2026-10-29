import type { ElectionType } from './election'

export type RegistrationStatus = 'ACTIVE' | 'CANCELLED'

export type IdDocumentType =
  | 'GREEN_BARCODED_ID'
  | 'SMART_ID_CARD'
  | 'TEMPORARY_ID_CERTIFICATE'

export interface ElectionRegistrationRequest {
  /** Optional confirmation; the API always registers the voter in their profile district. */
  votingDistrictId?: string
}

export interface ElectionRegistration {
  id: string
  electionId: string
  electionName: string
  electionType: ElectionType
  status: RegistrationStatus
  registeredAt: string
  votingDistrictId: string | null
  votingDistrictCode: string | null
  votingDistrictName: string | null
  province: string | null
  municipality: string | null
  wardNumber: number | null
  identityVerified: boolean
  verificationSource: string | null
  sourceReference: string | null
}
