import { API_BASE_URL } from '../config/environment'
import ApiError from './ApiError'
import type { ApiErrorResponse } from './ApiErrorResponse'

interface ApiRequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  authToken?: string
}

function buildApiUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE_URL}${normalizedPath}`
}

async function readJson(response: Response): Promise<unknown> {
  const contentType = response.headers.get('content-type')

  if (!contentType?.includes('application/json')) {
    return undefined
  }

  return response.json()
}

function createApiError(
  response: Response,
  responseBody: unknown,
  requestPath: string,
) {
  const errorBody = (responseBody ?? {}) as Partial<ApiErrorResponse>

  return new ApiError({
    timestamp: errorBody.timestamp || new Date().toISOString(),
    status: response.status,
    error: errorBody.error || response.statusText || 'Request Failed',
    message:
      errorBody.message || 'We could not complete your request. Please try again.',
    path: errorBody.path || requestPath,
    fieldErrors: errorBody.fieldErrors || {},
  })
}

export async function apiRequest<T>(
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const {
    authToken,
    body,
    headers: initialHeaders,
    ...requestOptions
  } = options
  const headers = new Headers(initialHeaders)

  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json')
  }

  if (body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (authToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${authToken}`)
  }

  const response = await fetch(buildApiUrl(path), {
    ...requestOptions,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const responseBody = await readJson(response)

  if (!response.ok) {
    throw createApiError(response, responseBody, path)
  }

  return responseBody as T
}
