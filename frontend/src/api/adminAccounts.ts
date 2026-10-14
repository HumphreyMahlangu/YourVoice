import type {
  AdminAccount,
  CreateAdminAccountRequest,
} from '../types/adminAccount'
import { apiRequest } from './client'

const ADMIN_ACCOUNTS_PATH = '/api/v1/admin/accounts'

export function getAdminAccounts(authToken: string, signal?: AbortSignal) {
  return apiRequest<AdminAccount[]>(ADMIN_ACCOUNTS_PATH, { authToken, signal })
}

export function createAdminAccount(
  request: CreateAdminAccountRequest,
  authToken: string,
) {
  return apiRequest<AdminAccount>(ADMIN_ACCOUNTS_PATH, {
    method: 'POST',
    authToken,
    body: request,
  })
}
