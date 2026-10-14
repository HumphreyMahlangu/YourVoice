import type {
  AuthSession,
  LoginCredentials,
  RegistrationDetails,
  UserAccount,
} from '../types/auth'
import { apiRequest } from './client'

export function loginAccount(credentials: LoginCredentials) {
  return apiRequest<AuthSession>('/api/v1/auth/login', {
    method: 'POST',
    body: credentials,
  })
}

export function registerAccount(details: RegistrationDetails) {
  return apiRequest<AuthSession>('/api/v1/auth/register', {
    method: 'POST',
    body: details,
  })
}

export function getCurrentAccount(authToken: string, signal?: AbortSignal) {
  return apiRequest<UserAccount>('/api/v1/auth/me', {
    authToken,
    signal,
  })
}

export function refreshSession(refreshToken: string) {
  return apiRequest<AuthSession>('/api/v1/auth/refresh', {
    method: 'POST',
    body: { refreshToken },
  })
}

export function logoutSession(refreshToken: string) {
  return apiRequest<void>('/api/v1/auth/logout', {
    method: 'POST',
    body: { refreshToken },
  })
}
