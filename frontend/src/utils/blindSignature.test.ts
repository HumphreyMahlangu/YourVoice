import { describe, expect, it } from 'vitest'
import type { BallotKey } from '../types/voting'
import {
  bigIntToBytes,
  blindBallotToken,
  decodeBigInt,
  encodeBigInt,
  fullDomainHash,
  modPow,
  unblindSignature,
} from './blindSignature'

const CONTEST_ID = '11111111-1111-1111-1111-111111111111'
const TOKEN_DOMAIN = 'votetrust-ballot-token-v1'

async function sha256Hex(bytes: Uint8Array<ArrayBuffer>) {
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
  return Array.from(digest, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

async function generateTestKey() {
  const keyPair = await crypto.subtle.generateKey(
    {
      name: 'RSA-PSS',
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: 'SHA-256',
    },
    true,
    ['sign', 'verify'],
  )
  const jwk = await crypto.subtle.exportKey('jwk', keyPair.privateKey)
  const key: BallotKey = {
    contestId: CONTEST_ID,
    modulus: jwk.n ?? '',
    publicExponent: jwk.e ?? '',
    keyFingerprint: 'test',
    tokenDomain: TOKEN_DOMAIN,
  }
  return { key, privateExponent: decodeBigInt(jwk.d ?? '') }
}

describe('blind signatures', () => {
  it('computes the same full-domain hash as the backend', async () => {
    // Shared vector with BlindSignatureServiceTest, computed independently in Python.
    const modulus = 2n ** 2048n - 159n

    const hash = await fullDomainHash(
      TOKEN_DOMAIN,
      CONTEST_ID,
      'q8Xb0Hw2Lr5nKcTzW1sYd9aEoP3uJvMfGtBiN7xR4lA',
      modulus,
    )

    expect(hash.toString(16)).toMatch(
      /^70b6c050203b515ee756494d4b9e4c7bb8f9063e8264e6bc1502870aee2ae3ea/,
    )
    await expect(sha256Hex(bigIntToBytes(hash, 256))).resolves.toBe(
      '431887e9e599b9f61fc58b11bcf064381b9048705b1e50464a095d7aadd8a51a',
    )
  })

  it('blinds, gets signed, and unblinds to a valid signature', async () => {
    const { key, privateExponent } = await generateTestKey()
    const ballot = await blindBallotToken(key)
    const modulus = decodeBigInt(key.modulus)

    const blindSignature = encodeBigInt(
      modPow(decodeBigInt(ballot.blindedToken), privateExponent, modulus),
    )
    const signature = await unblindSignature(ballot, blindSignature)

    const expectedHash = await fullDomainHash(
      TOKEN_DOMAIN,
      CONTEST_ID,
      ballot.ballotToken,
      modulus,
    )
    expect(
      modPow(decodeBigInt(signature), decodeBigInt(key.publicExponent), modulus),
    ).toBe(expectedHash)
    expect(signature).not.toBe(blindSignature)
  })

  it('rejects a signature that was not made with the contest key', async () => {
    const { key } = await generateTestKey()
    const ballot = await blindBallotToken(key)

    await expect(unblindSignature(ballot, 'AQID')).rejects.toThrow(
      'does not match the published contest key',
    )
  })
})
