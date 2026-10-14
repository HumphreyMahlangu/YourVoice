export type SecurityAuditEventType =
  | 'USER_REGISTER'
  | 'USER_LOGIN'
  | 'ADMIN_BOOTSTRAP'
  | 'RATE_LIMIT_BLOCKED'
  | 'TOKEN_REFRESH'
  | 'USER_LOGOUT'
  | 'ADMIN_ACCOUNT_CREATED'

export type SecurityAuditOutcome = 'SUCCESS' | 'FAILURE' | 'BLOCKED'

export interface SecurityAuditEvent {
  id: string
  eventType: SecurityAuditEventType
  outcome: SecurityAuditOutcome
  principalUserId: string | null
  principalEmail: string | null
  clientIp: string
  userAgent: string | null
  detail: string
  occurredAt: string
}
