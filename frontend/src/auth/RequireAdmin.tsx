import { Navigate, Outlet } from 'react-router'
import { useAuth } from './useAuth'

function RequireAdmin() {
  const { session, isInitializing } = useAuth()

  if (isInitializing) {
    return <p role="status">Restoring your session...</p>
  }

  if (!session) {
    return <Navigate to="/login" replace />
  }

  if (session.role !== 'ADMIN') {
    return <Navigate to="/dashboard" replace />
  }

  return <Outlet />
}

export default RequireAdmin
