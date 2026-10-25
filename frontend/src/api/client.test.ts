import { afterEach, describe, expect, it, vi } from 'vitest'
import ApiError from './ApiError'
import { apiRequest } from './client'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiRequest', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('does not attach authorization unless a token is provided', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ value: 'ok' }))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest<{ value: string }>('/api/test')

    const [, request] = fetchMock.mock.calls[0]
    const headers = new Headers(request?.headers)
    expect(headers.has('Authorization')).toBe(false)
  })

  it('attaches an explicit bearer token and serializes a JSON body', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValue(jsonResponse({ created: true }, 201))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/api/admin/example', {
      method: 'POST',
      authToken: 'admin-token',
      body: { name: 'Example' },
    })

    const [, request] = fetchMock.mock.calls[0]
    const headers = new Headers(request?.headers)
    expect(headers.get('Authorization')).toBe('Bearer admin-token')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(request?.body).toBe(JSON.stringify({ name: 'Example' }))
  })

  it('throws a structured API error with field errors', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(
      jsonResponse(
        {
          timestamp: '2026-08-16T10:00:00Z',
          status: 400,
          error: 'Bad Request',
          message: 'Request validation failed',
          path: '/api/test',
          fieldErrors: { email: 'must be valid' },
        },
        400,
      ),
    )
    vi.stubGlobal('fetch', fetchMock)

    const request = apiRequest('/api/test')

    await expect(request).rejects.toBeInstanceOf(ApiError)
    await expect(request).rejects.toMatchObject({
      status: 400,
      message: 'Request validation failed',
      fieldErrors: { email: 'must be valid' },
    })
  })

  it('uses a plain-language message when the server sends no error body', async () => {
    vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(
      new Response(null, { status: 500 }),
    ))

    await expect(apiRequest('/api/admin/example')).rejects.toMatchObject({
      status: 500,
      message: 'We could not complete your request. Please try again.',
    })
  })
})
