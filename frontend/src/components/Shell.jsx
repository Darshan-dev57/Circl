import { LogOut, Plus } from 'lucide-react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth-context'

function Wordmark() {
  return (
    <Link to="/" className="wordmark" aria-label="Circl home">
      <svg width="22" height="22" viewBox="0 0 32 32" aria-hidden="true">
        <circle cx="16" cy="16" r="11" fill="none" stroke="currentColor" strokeWidth="3" strokeDasharray="6.4 2.24" />
        <circle cx="16" cy="16" r="4" className="wordmark__dot" />
      </svg>
      circl
    </Link>
  )
}

export default function Shell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="shell">
      <header className="topbar">
        <Wordmark />
        <nav className="topbar__nav">
          <NavLink to="/" end>Explore</NavLink>
          {user && <NavLink to="/bookings">Bookings</NavLink>}
        </nav>
        <div className="topbar__end">
          {user?.role === 'HOST' && (
            <Link to="/host/new" className="button button--small">
              <Plus size={16} aria-hidden="true" /> New activity
            </Link>
          )}
          {user ? (
            <>
              <span className="topbar__user" title={`Reliability score ${user.reliabilityScore}`}>
                {user.name}
                <span className="topbar__score">{user.reliabilityScore}</span>
              </span>
              <button
                className="icon-button"
                onClick={() => logout().then(() => navigate('/'))}
                aria-label="Sign out"
                title="Sign out"
              >
                <LogOut size={18} />
              </button>
            </>
          ) : (
            <Link to="/login" className="button button--small button--quiet">Sign in</Link>
          )}
        </div>
      </header>
      <Outlet />
    </div>
  )
}
