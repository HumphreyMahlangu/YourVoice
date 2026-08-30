import { NavLink, Outlet } from 'react-router'
import { useAuth } from './auth/useAuth'
import BrandMark from './components/BrandMark'

function navigationClass({ isActive }: { isActive: boolean }) {
  return isActive ? 'nav-link nav-link--active' : 'nav-link'
}

function App() {
  const { session, logout } = useAuth()

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <header className="app-header">
        <div className="app-header__inner">
          <NavLink className="brand" to="/" aria-label="VoteTrust home">
            <BrandMark />
            <span className="brand__copy">
              <strong>VoteTrust</strong>
              <span>Confidence by design</span>
            </span>
          </NavLink>

          <nav className="primary-nav" aria-label="Primary navigation">
            <NavLink className={navigationClass} to="/" end>
              Overview
            </NavLink>
            <NavLink className={navigationClass} to="/elections">
              Elections
            </NavLink>
            {session?.role === 'VOTER' && (
              <NavLink className={navigationClass} to="/dashboard">
                My dashboard
              </NavLink>
            )}
            {session?.role === 'ADMIN' && (
              <>
                <NavLink className={navigationClass} to="/admin" end>
                  Admin
                </NavLink>
                <NavLink
                  className={navigationClass}
                  to="/admin/security-events"
                >
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
                <button className="button button--quiet" type="button" onClick={logout}>
                  Sign out
                </button>
              </>
            ) : (
              <>
                <NavLink className="button button--quiet" to="/login">
                  Sign in
                </NavLink>
                <NavLink className="button button--primary" to="/register">
                  Create account
                </NavLink>
              </>
            )}
          </div>
        </div>
      </header>

      <main id="main-content" className="app-main">
        <Outlet />
      </main>

      <footer className="app-footer">
        <div className="app-footer__inner">
          <div className="app-footer__brand">
            <BrandMark />
            <div>
              <strong>VoteTrust</strong>
              <p>Transparent participation. Verifiable outcomes.</p>
            </div>
          </div>
          <p className="app-footer__note">
            Built to keep voter identity and ballot choice separate.
          </p>
        </div>
      </footer>
    </div>
  )
}

export default App
