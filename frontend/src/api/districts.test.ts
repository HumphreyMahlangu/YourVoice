import { afterEach, describe, expect, it, vi } from 'vitest'
import { getVotingDistricts } from './districts'

const district = { id: 'fictional-area', code: 'DEMO-1', name: 'Demo', province: 'Demo province', municipality: 'Demo municipality', wardNumber: 1 }
function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}
afterEach(() => vi.unstubAllGlobals())

describe('area pagination', () => {
  it('loads later pages without relying on exposed total headers', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(response(Array.from({ length: 500 }, (_, i) => ({ ...district, id: `demo-${i}` })))).mockResolvedValueOnce(response([{ ...district, id: 'last-area' }]))
    vi.stubGlobal('fetch', fetch)
    const signal = new AbortController().signal
    const result = await getVotingDistricts(signal)
    expect(result).toHaveLength(501)
    expect(result.at(-1)?.id).toBe('last-area')
    expect(fetch.mock.calls[1][0]).toContain('page=1&size=500')
    expect(fetch.mock.calls[1][1].signal).toBe(signal)
  })

  it('recognises a genuinely empty list', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response([])))
    await expect(getVotingDistricts()).resolves.toEqual([])
  })

  it('rejects partial results when a later page fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(response(Array(500).fill(district))).mockResolvedValueOnce(response({}, 503)))
    await expect(getVotingDistricts()).rejects.toThrow()
  })

  it.each([
    null,
    {},
    { ...district, id: '' },
    { ...district, province: null },
    { ...district, name: 42 },
    { ...district, wardNumber: '1' },
    { ...district, wardNumber: 0 },
  ])('rejects malformed area records before rendering: %j', async (invalidDistrict) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response([invalidDistrict])))
    await expect(getVotingDistricts()).rejects.toThrow('Invalid area list response')
  })

  it('rejects a malformed success response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ content: [] })))
    await expect(getVotingDistricts()).rejects.toThrow('Invalid area list response')
  })
})
