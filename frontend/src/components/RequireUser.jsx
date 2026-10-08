import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth-context'

export default function RequireUser({ children, role }) {
  const { user, ready } = useAuth()
  const location = useLocation()
  if (!ready) return null
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (role && user.role !== role && user.role !== 'ADMIN') return <Navigate to="/" replace />
  return children
}
