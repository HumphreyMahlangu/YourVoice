import { afterEach, describe, expect, it, vi } from 'vitest'
import { getMyBallots, getProfile, getProvinces } from './profiles'
const response = (body: unknown) => new Response(JSON.stringify(body), { headers: { 'Content-Type': 'application/json' } })
afterEach(() => vi.unstubAllGlobals())
describe('profile and ballot API responses', () => {
  it('fetches all ballot pages and rejects a partial list when the next page fails', async () => {
    const row = { election: { id: 'e' }, contest: { id: 'c' }, message: 'Fictional', eligible: true, registered: true, registrationVerified: true, canRegister: false, canVote: true, credentialIssued: false }
    const fetcher = vi.fn().mockResolvedValueOnce(response(Array.from({ length: 500 }, () => row))).mockRejectedValueOnce(new TypeError('offline'))
    vi.stubGlobal('fetch', fetcher)
    await expect(getMyBallots('fictional-token')).rejects.toThrow('offline')
    expect(fetcher.mock.calls[1][0]).toContain('page=1')
  })
  it.each([{}, [null], [{ election: {}, contest: {}, message: 'bad' }]])('rejects malformed discovery rather than returning an empty list', async (body) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(body)))
    await expect(getMyBallots('fictional-token')).rejects.toThrow('Invalid list response')
  })
  it('rejects a profile that claims an official source', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ source: 'IEC', identityStatus: 'VERIFIED' })))
    await expect(getProfile('fictional-token')).rejects.toThrow('Invalid profile response')
  })
  it('reads unpaged geography exactly once even at 500 records', async () => {
    const fetcher = vi.fn().mockResolvedValue(response(Array.from({ length: 500 }, (_, n) => ({ id: String(n), name: `Fictional ${n}` }))))
    vi.stubGlobal('fetch', fetcher)
    expect(await getProvinces()).toHaveLength(500); expect(fetcher).toHaveBeenCalledOnce()
  })
})
