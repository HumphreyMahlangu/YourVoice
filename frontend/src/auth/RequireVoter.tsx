import { Navigate, Outlet } from 'react-router'
import { useAuth } from './useAuth'

function RequireVoter() {
  const { session, isInitializing } = useAuth()

  if (isInitializing) {
    return <p role="status">Restoring your session...</p>
  }

  if (!session) {
    return <Navigate to="/login" replace />
  }

  if (session.role !== 'VOTER') {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}

export default RequireVoter
