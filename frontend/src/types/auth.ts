import type { IdDocumentType } from './registration'

export type AccountRole = 'VOTER' | 'ADMIN'

export interface AuthSession {
  accessToken: string
  tokenType: string
  expiresAt: string
  userId: string
  email: string
  role: AccountRole
}

export interface LoginCredentials {
  email: string
  password: string
}

export interface RegistrationDetails {
  fullName: string
  email: string
  password: string
  southAfricanIdNumber: string
  idDocumentType: IdDocumentType
  votingDistrictId: string
}

export interface UserAccount {
  id: string
  email: string
  role: AccountRole
  enabled: boolean
}
