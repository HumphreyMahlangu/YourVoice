import { describe, expect, it } from 'vitest'
import type { ContestLedgerEntry } from '../types/audit'
import {
  GENESIS_HASH,
  calculatePublicHash,
  verifyPublishedLedger,
} from './ledgerVerification'

const CONTEST_ID = '11111111-1111-1111-1111-111111111111'
const OPTION_ID = '22222222-2222-2222-2222-222222222222'

async function buildLedger(size: number) {
  const entries: ContestLedgerEntry[] = []
  let previousHash = GENESIS_HASH

  for (let position = 0; position < size; position += 1) {
    const entry = {
      position,
      contestOptionId: OPTION_ID,
      previousHash,
      nonce: `nonce-${position}`,
    }
    const currentHash = await calculatePublicHash(CONTEST_ID, entry)
    entries.push({ ...entry, currentHash })
    previousHash = currentHash
  }

  return entries
}

describe('verifyPublishedLedger', () => {
  it('matches the backend public hash format', async () => {
    // SHA-256 of "votetrust-public-ledger-v1|<contest>|<option>|0|<64 zeros>|nonce",
    // computed independently with `printf '%s' ... | sha256sum`.
    await expect(
      calculatePublicHash(CONTEST_ID, {
        contestOptionId: OPTION_ID,
        position: 0,
        previousHash: GENESIS_HASH,
        nonce: 'nonce',
      }),
    ).resolves.toBe(
      '2cdaf1cf60d1147efa190480f29edc33977c19ce377b7053dc8ce4da3c12c78c',
    )
  })

  it('accepts an untampered ledger and returns its head hash', async () => {
    const entries = await buildLedger(3)

    const result = await verifyPublishedLedger(CONTEST_ID, entries)

    expect(result.valid).toBe(true)
    expect(result.headHash).toBe(entries[2].currentHash)
  })

  it('detects an entry whose selection was changed', async () => {
    const entries = await buildLedger(3)
    entries[1] = {
      ...entries[1],
      contestOptionId: '33333333-3333-3333-3333-333333333333',
    }

    const result = await verifyPublishedLedger(CONTEST_ID, entries)

    expect(result.valid).toBe(false)
    expect(result.violations).toContain(
      'Entry 1 hash does not match its contents',
    )
  })
})
