import {
  useCallback,
  useEffect,
  useState,
  type PropsWithChildren,
} from 'react'
import {
  loginAccount,
  logoutSession,
  refreshSession,
  registerAccount,
} from '../api/auth'
import type {
  AuthSession,
  LoginCredentials,
  RegistrationDetails,
} from '../types/auth'
import { AuthContext } from './context'

// The refresh token is kept in sessionStorage so a page reload does not sign
// the user out. It is scoped to this tab and cleared when the tab closes. The
// access token stays in memory only. Cookies are not an option because the
// frontend and API run on different sites.
const REFRESH_TOKEN_KEY = 'yourvoice.refreshToken'
const REFRESH_LEAD_MS = 60_000

// Concurrent refreshes of the same token must share one request: the API treats a second use of a rotated
// refresh token as theft and revokes the whole session. In development, React StrictMode runs the restore
// effect twice, which would otherwise sign the user out on every reload.
const inFlightRefreshes = new Map<string, Promise<AuthSession>>()

function refreshOnce(refreshToken: string) {
  const inFlight = inFlightRefreshes.get(refreshToken)
  if (inFlight) {
    return inFlight
  }
  const request = refreshSession(refreshToken)
  inFlightRefreshes.set(refreshToken, request)
  request
    .finally(() => inFlightRefreshes.delete(refreshToken))
    .catch(() => undefined)
  return request
}

function readStoredRefreshToken() {
  try {
    return window.sessionStorage.getItem(REFRESH_TOKEN_KEY)
  } catch {
    return null
  }
}

function storeRefreshToken(refreshToken: string | null) {
  try {
    if (refreshToken) {
      window.sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    } else {
      window.sessionStorage.removeItem(REFRESH_TOKEN_KEY)
    }
  } catch {
    // Storage can be unavailable (private mode, blocked site data); the
    // session then simply does not survive a reload.
  }
}

function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [isInitializing, setIsInitializing] = useState(
    () => readStoredRefreshToken() !== null,
  )

  const applySession = useCallback((nextSession: AuthSession | null) => {
    storeRefreshToken(nextSession?.refreshToken ?? null)
    setSession(nextSession)
  }, [])

  useEffect(() => {
    const storedRefreshToken = readStoredRefreshToken()
    if (!storedRefreshToken) {
      return
    }

    let cancelled = false
    refreshOnce(storedRefreshToken)
      .then((restoredSession) => {
        if (!cancelled) {
          applySession(restoredSession)
        }
      })
      .catch(() => {
        if (!cancelled) {
          applySession(null)
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsInitializing(false)
        }
      })

    return () => {
      cancelled = true
    }
  }, [applySession])

  useEffect(() => {
    if (!session) {
      return
    }

    const refreshIn = Math.max(
      0,
      new Date(session.expiresAt).getTime() - Date.now() - REFRESH_LEAD_MS,
    )
    const refreshTimer = window.setTimeout(() => {
      refreshOnce(session.refreshToken)
        .then(applySession)
        .catch(() => applySession(null))
    }, refreshIn)

    return () => window.clearTimeout(refreshTimer)
  }, [applySession, session])

  const login = useCallback(
    async (credentials: LoginCredentials) => {
      const authenticatedSession = await loginAccount(credentials)
      applySession(authenticatedSession)
      return authenticatedSession
    },
    [applySession],
  )

  const register = useCallback(
    async (details: RegistrationDetails) => {
      const authenticatedSession = await registerAccount(details)
      applySession(authenticatedSession)
      return authenticatedSession
    },
    [applySession],
  )

  const logout = useCallback(() => {
    if (session) {
      // Best effort: the session is cleared locally even if revocation fails.
      void logoutSession(session.refreshToken).catch(() => undefined)
    }
    applySession(null)
  }, [applySession, session])

  return (
    <AuthContext.Provider
      value={{ session, isInitializing, login, register, logout }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export default AuthProvider
