export interface ContestAudit {
  electionId: string
  contestId: string
  chainValid: boolean
  ledgerEntryCount: number
  genesisHash: string
  computedHeadHash: string
  storedHeadHash: string
  storedNextLedgerIndex: number | null
  publishedLedgerEntryCount: number
  computedPublishedHeadHash: string
  publishedHeadHash: string
  verifiedAt: string
  violations: string[]
}

/** An entry in the published ledger. Ballots are shuffled before publication. */
export interface ContestLedgerEntry {
  position: number
  contestOptionId: string
  previousHash: string
  currentHash: string
  nonce: string
}
