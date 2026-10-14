import type { BallotKey } from '../types/voting'

/**
 * Voter side of the RSA blind-signature protocol. Mirrors
 * BlindSignatureService on the backend:
 *
 *   H(t) = MGF1-SHA256("<tokenDomain>|<contestId>|<t>", modulusBytes + 16) mod n
 *   blinded = H(t) * r^e mod n            (sent with the voter's JWT)
 *   signature = blindSignature * r^-1 mod n (sent with the ballot, no JWT)
 *
 * Only public-key operations happen here; the server never sees t or r.
 */

const HASH_EXTRA_BYTES = 16
const BALLOT_TOKEN_BYTES = 32

export interface BlindedBallot {
  contestId: string
  ballotToken: string
  blindedToken: string
  keyFingerprint: string
  blindingFactor: bigint
  modulus: bigint
  publicExponent: bigint
  tokenDomain: string
}

export function base64UrlToBytes(value: string) {
  const base64 = value.replaceAll('-', '+').replaceAll('_', '/')
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
  return Uint8Array.from(atob(padded), (character) =>
    character.charCodeAt(0),
  )
}

export function bytesToBase64Url(bytes: Uint8Array) {
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '')
}

export function bytesToBigInt(bytes: Uint8Array) {
  let value = 0n
  for (const byte of bytes) {
    value = (value << 8n) | BigInt(byte)
  }
  return value
}

export function bigIntToBytes(value: bigint, length = byteLength(value)) {
  const bytes = new Uint8Array(length)
  let remaining = value
  for (let index = length - 1; index >= 0; index -= 1) {
    bytes[index] = Number(remaining & 0xffn)
    remaining >>= 8n
  }
  return bytes
}

export function encodeBigInt(value: bigint) {
  return bytesToBase64Url(bigIntToBytes(value))
}

export function decodeBigInt(value: string) {
  return bytesToBigInt(base64UrlToBytes(value))
}

function byteLength(value: bigint) {
  return Math.max(1, Math.ceil(value.toString(2).length / 8))
}

export function modPow(base: bigint, exponent: bigint, modulus: bigint) {
  let result = 1n
  let factor = base % modulus
  let remaining = exponent
  while (remaining > 0n) {
    if (remaining & 1n) {
      result = (result * factor) % modulus
    }
    factor = (factor * factor) % modulus
    remaining >>= 1n
  }
  return result
}

function gcd(left: bigint, right: bigint) {
  let a = left
  let b = right
  while (b !== 0n) {
    ;[a, b] = [b, a % b]
  }
  return a
}

export function modInverse(value: bigint, modulus: bigint) {
  let [oldR, r] = [value % modulus, modulus]
  let [oldS, s] = [1n, 0n]
  while (r !== 0n) {
    const quotient = oldR / r
    ;[oldR, r] = [r, oldR - quotient * r]
    ;[oldS, s] = [s, oldS - quotient * s]
  }
  if (oldR !== 1n) {
    throw new Error('Value has no inverse for this modulus.')
  }
  return ((oldS % modulus) + modulus) % modulus
}

async function sha256(bytes: Uint8Array<ArrayBuffer>) {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))
}

async function mgf1Sha256(seed: Uint8Array<ArrayBuffer>, length: number) {
  const output = new Uint8Array(length)
  let offset = 0
  for (let counter = 0; offset < length; counter += 1) {
    const input = new Uint8Array(seed.length + 4)
    input.set(seed)
    new DataView(input.buffer).setUint32(seed.length, counter)
    const block = await sha256(input)
    const copied = Math.min(block.length, length - offset)
    output.set(block.subarray(0, copied), offset)
    offset += copied
  }
  return output
}

export async function fullDomainHash(
  tokenDomain: string,
  contestId: string,
  ballotToken: string,
  modulus: bigint,
) {
  const seed = new TextEncoder().encode(
    [tokenDomain, contestId, ballotToken].join('|'),
  )
  const expanded = await mgf1Sha256(
    seed,
    byteLength(modulus) + HASH_EXTRA_BYTES,
  )
  return bytesToBigInt(expanded) % modulus
}

function randomBlindingFactor(modulus: bigint) {
  const bytes = new Uint8Array(byteLength(modulus))
  for (;;) {
    crypto.getRandomValues(bytes)
    const candidate = bytesToBigInt(bytes) % modulus
    if (candidate > 1n && gcd(candidate, modulus) === 1n) {
      return candidate
    }
  }
}

/** Creates a fresh ballot token and blinds it with the contest key. */
export async function blindBallotToken(key: BallotKey): Promise<BlindedBallot> {
  const modulus = decodeBigInt(key.modulus)
  const publicExponent = decodeBigInt(key.publicExponent)
  const tokenBytes = new Uint8Array(BALLOT_TOKEN_BYTES)
  crypto.getRandomValues(tokenBytes)
  const ballotToken = bytesToBase64Url(tokenBytes)
  const blindingFactor = randomBlindingFactor(modulus)
  const hash = await fullDomainHash(
    key.tokenDomain,
    key.contestId,
    ballotToken,
    modulus,
  )
  const blinded = (hash * modPow(blindingFactor, publicExponent, modulus)) % modulus

  return {
    contestId: key.contestId,
    ballotToken,
    blindedToken: encodeBigInt(blinded),
    keyFingerprint: key.keyFingerprint,
    blindingFactor,
    modulus,
    publicExponent,
    tokenDomain: key.tokenDomain,
  }
}

/**
 * Removes the blinding factor and checks the result against the contest key,
 * so a signature from any other key is rejected before the ballot is cast.
 */
export async function unblindSignature(
  ballot: BlindedBallot,
  blindSignature: string,
) {
  const signature =
    (decodeBigInt(blindSignature) *
      modInverse(ballot.blindingFactor, ballot.modulus)) %
    ballot.modulus
  const expected = await fullDomainHash(
    ballot.tokenDomain,
    ballot.contestId,
    ballot.ballotToken,
    ballot.modulus,
  )

  if (modPow(signature, ballot.publicExponent, ballot.modulus) !== expected) {
    throw new Error(
      'The ballot signature does not match the published contest key.',
    )
  }

  return encodeBigInt(signature)
}
