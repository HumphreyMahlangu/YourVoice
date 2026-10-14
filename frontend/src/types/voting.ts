/** Public RSA key used to blind ballot tokens for one contest. */
export interface BallotKey {
  contestId: string
  modulus: string
  publicExponent: string
  keyFingerprint: string
  tokenDomain: string
}

export interface VotingCredentialRequest {
  blindedToken: string
}

/** The server's signature over the blinded token; unblind it before casting. */
export interface VotingCredential {
  electionId: string
  contestId: string
  blindSignature: string
  keyFingerprint: string
  expiresAt: string
}

export interface BallotCastRequest {
  contestId: string
  contestOptionId: string
  ballotToken: string
  signature: string
}

export interface BallotCastResponse {
  contestId: string
  accepted: boolean
  message: string
}
