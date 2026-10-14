import { createContext } from 'react'
import type {
  AuthSession,
  LoginCredentials,
  RegistrationDetails,
} from '../types/auth'

export interface AuthContextValue {
  session: AuthSession | null
  /** True while a session stored in this tab is being restored after a page load. */
  isInitializing: boolean
  login: (credentials: LoginCredentials) => Promise<AuthSession>
  register: (details: RegistrationDetails) => Promise<AuthSession>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
