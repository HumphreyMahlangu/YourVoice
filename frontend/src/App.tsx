import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from './auth/useAuth'

function navigationClass({ isActive }: { isActive: boolean }) {
  return isActive ? 'nav-link nav-link--active' : 'nav-link'
}

function App() {
  const { session, logout } = useAuth()
  const { pathname } = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuToggleRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    setMenuOpen(false)
  }, [pathname])

  useEffect(() => {
    if (!menuOpen) return

    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setMenuOpen(false)
        menuToggleRef.current?.focus()
      }
    }

    window.addEventListener('keydown', handleEscape)
    return () => window.removeEventListener('keydown', handleEscape)
  }, [menuOpen])

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <header className="app-header">
        <div className="app-header__inner">
          <NavLink className="brand" to="/" aria-label="YourVoice home" onClick={() => setMenuOpen(false)}>
            <span className="brand__copy">
              <strong>YourVoice</strong>
              <span>South Africa</span>
            </span>
          </NavLink>

          <button
            className="header-menu-toggle"
            type="button"
            ref={menuToggleRef}
            aria-controls="header-menu"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <span>{menuOpen ? 'Close' : 'Menu'}</span>
            <span className="header-menu-toggle__icon" aria-hidden="true">
              <span />
              <span />
            </span>
          </button>

          <div id="header-menu" className={`header-menu${menuOpen ? ' header-menu--open' : ''}`}>
            <nav className="primary-nav" aria-label="Primary navigation" onClick={() => setMenuOpen(false)}>
              <NavLink className={navigationClass} to="/" end>
                <span className="nav-link__number" aria-hidden="true">01</span>
                Overview
              </NavLink>
              <NavLink className={navigationClass} to="/elections">
                <span className="nav-link__number" aria-hidden="true">02</span>
                Elections
              </NavLink>
              {session?.role === 'VOTER' && (
                <NavLink className={navigationClass} to="/dashboard">
                  <span className="nav-link__number" aria-hidden="true">03</span>
                  My dashboard
                </NavLink>
              )}
              {session?.role === 'VOTER' && <NavLink className={navigationClass} to="/profile">Your profile</NavLink>}
              {session?.role === 'ADMIN' && (
                <>
                  <NavLink className={navigationClass} to="/admin" end>
                    <span className="nav-link__number" aria-hidden="true">03</span>
                    Admin
                  </NavLink>
                  <NavLink
                    className={navigationClass}
                    to="/admin/security-events"
                  >
                    <span className="nav-link__number" aria-hidden="true">04</span>
                    Security
                  </NavLink>
                </>
              )}
            </nav>

            <div className="account-nav">
              {session ? (
                <>
                  <div className="account-chip" title={session.email}>
                    <span className="account-chip__avatar" aria-hidden="true">
                      {session.email.slice(0, 1).toUpperCase()}
                    </span>
                    <span className="account-chip__copy">
                      <strong>{session.email}</strong>
                      <span>{session.role === 'ADMIN' ? 'Administrator' : 'Voter'}</span>
                    </span>
                  </div>
                  <button className="button button--quiet" type="button" onClick={() => { setMenuOpen(false); logout() }}>
                    Sign out
                  </button>
                </>
              ) : (
                <>
                  <NavLink className="button button--quiet" to="/login" onClick={() => setMenuOpen(false)}>
                    Sign in
                  </NavLink>
                  <NavLink className="button button--primary" to="/register" onClick={() => setMenuOpen(false)}>
                    Create account <span aria-hidden="true">↗</span>
                  </NavLink>
                </>
              )}
            </div>
          </div>
        </div>
      </header>

      <main id="main-content" className="app-main">
        <Outlet />
      </main>

      <footer className="app-footer">
        <div className="app-footer__inner">
          <div className="app-footer__brand">
            <div>
              <strong>YourVoice</strong>
              <p>Educational South African voting simulation.</p>
            </div>
          </div>
          <p className="app-footer__note">
            Built to explore private ballots and public verification.
          </p>
        </div>
      </footer>
    </div>
  )
}

export default App
