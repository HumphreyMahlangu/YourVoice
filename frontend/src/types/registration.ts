import type { ElectionType } from './election'

export type RegistrationStatus = 'ACTIVE' | 'CANCELLED'

export type IdDocumentType =
  | 'GREEN_BARCODED_ID'
  | 'SMART_ID_CARD'
  | 'TEMPORARY_ID_CERTIFICATE'

export interface ElectionRegistrationRequest {
  votingDistrictId: string
}

export interface ElectionRegistration {
  id: string
  electionId: string
  electionName: string
  electionType: ElectionType
  status: RegistrationStatus
  registeredAt: string
  votingDistrictId: string
  votingDistrictCode: string
  votingDistrictName: string
  province: string
}
