import type { ContestLedgerEntry } from '../types/audit'

export const GENESIS_HASH = '0'.repeat(64)

const PUBLIC_HASH_DOMAIN = 'votetrust-public-ledger-v1'

export interface LedgerVerificationResult {
  valid: boolean
  headHash: string
  violations: string[]
}

async function sha256Hex(value: string) {
  const digest = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(value),
  )
  return Array.from(new Uint8Array(digest), (byte) =>
    byte.toString(16).padStart(2, '0'),
  ).join('')
}

export function calculatePublicHash(
  contestId: string,
  entry: Pick<
    ContestLedgerEntry,
    'contestOptionId' | 'position' | 'previousHash' | 'nonce'
  >,
) {
  return sha256Hex(
    [
      PUBLIC_HASH_DOMAIN,
      contestId,
      entry.contestOptionId,
      String(entry.position),
      entry.previousHash,
      entry.nonce,
    ].join('|'),
  )
}

/**
 * Recomputes the published hash chain in the browser, from the public ledger
 * alone, so a viewer does not have to trust the server's audit result.
 */
export async function verifyPublishedLedger(
  contestId: string,
  entries: ContestLedgerEntry[],
): Promise<LedgerVerificationResult> {
  const violations: string[] = []
  let headHash = GENESIS_HASH

  for (const [expectedPosition, entry] of entries.entries()) {
    if (entry.position !== expectedPosition) {
      violations.push(
        `Position ${expectedPosition} is missing or out of order`,
      )
    }
    if (entry.previousHash !== headHash) {
      violations.push(
        `Entry ${entry.position} does not link to the previous entry`,
      )
    }

    const recalculatedHash = await calculatePublicHash(contestId, entry)
    if (recalculatedHash !== entry.currentHash) {
      violations.push(`Entry ${entry.position} hash does not match its contents`)
    }
    headHash = recalculatedHash
  }

  return { valid: violations.length === 0, headHash, violations }
}
