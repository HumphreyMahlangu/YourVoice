import { describe, expect, it } from 'vitest'
import ApiError from '../api/ApiError'
import { getAdminErrorMessage, getAdminFieldErrors } from './adminErrors'

function apiError(status: number, message: string, fieldErrors: Record<string, string> = {}) {
  return new ApiError({
    timestamp: '2026-10-25T00:00:00Z',
    status,
    error: 'Request Failed',
    message,
    path: '/api/v1/admin/elections',
    fieldErrors,
  })
}

describe('administrator error messages', () => {
  it('explains a ballot rule in language an administrator can act on', () => {
    expect(getAdminErrorMessage(
      apiError(409, 'Municipal ward contests require scopeProvince, scopeMunicipality, and scopeWardNumber'),
      'Could not add the contest.',
    )).toBe('Choose a province, municipality and ward number.')
  })

  it('does not display unfamiliar server details or HTTP statuses', () => {
    expect(getAdminErrorMessage(
      apiError(500, 'SQLException at AdminElectionManagementService'),
      'Could not save the election.',
    )).toBe('Could not save the election. Please try again. If the problem continues, ask for help.')
  })

  it('gives an offline recovery step', () => {
    expect(getAdminErrorMessage(new TypeError('Failed to fetch'), 'Could not save.'))
      .toBe('Could not connect. Check your internet connection and try again.')
  })

  it('turns field validation into plain-language instructions', () => {
    expect(getAdminFieldErrors(apiError(400, 'Request validation failed', {
      email: 'must be a well-formed email address',
      password: 'size must be between 12 and 128',
      scopeWardNumber: 'must be greater than or equal to 1',
    }))).toEqual({
      email: 'Enter a valid email address.',
      password: 'Password must be between 12 and 128 characters.',
      scopeWardNumber: 'Ward number must be 1 or more.',
    })
  })
})
