import { Link } from 'react-router'
import { useAuth } from '../auth/useAuth'

function HomePage() {
  const { session } = useAuth()
  const accountPath = session?.role === 'ADMIN' ? '/admin' : '/dashboard'

  return (
    <section className="page home-page" aria-labelledby="home-heading">
      <header className="home-intro">
        <div className="home-intro__main">
          <p className="eyebrow">YourVoice / South Africa</p>
          <h1 id="home-heading">An election, explained as you go.</h1>
        </div>
        <div className="home-intro__aside">
          <p>
            Follow registration, review a ballot, and examine published results
            when a contest is complete.
          </p>
          <div className="home-intro__actions">
            <Link className="button button--primary" to="/elections">
              Browse elections <span aria-hidden="true">→</span>
            </Link>
            <Link className="home-intro__secondary" to={session ? accountPath : '/register'}>
              {session ? 'Open your workspace' : 'Create an account'}
            </Link>
          </div>
        </div>
      </header>

      <section className="home-index" aria-labelledby="home-index-heading">
        <div className="home-index__heading">
          <p className="eyebrow">On this site</p>
          <h2 id="home-index-heading">Start with what you need.</h2>
        </div>
        <div className="home-index__links">
          <Link to="/elections">
            <span>01</span>
            <strong>Find an election</strong>
            <span>See schedules, contests, and each election's current stage.</span>
            <span aria-hidden="true">↗</span>
          </Link>
          <Link to={session ? accountPath : '/register'}>
            <span>02</span>
            <strong>{session ? 'Open your workspace' : 'Create a voter profile'}</strong>
            <span>Set up a profile to follow the voter journey.</span>
            <span aria-hidden="true">↗</span>
          </Link>
          <Link to="/elections">
            <span>03</span>
            <strong>Inspect a completed result</strong>
            <span>Read the tally, audit, and public ledger when available.</span>
            <span aria-hidden="true">↗</span>
          </Link>
        </div>
      </section>

      <section className="home-note" aria-labelledby="home-note-heading">
        <div>
          <p className="eyebrow">How it works</p>
          <h2 id="home-note-heading">A ballot should be private. The count should be open to scrutiny.</h2>
        </div>
        <div className="home-note__body">
          <p>
            An account establishes eligibility. A separate
            one-time credential authorises the ballot without attaching a voter
            identity to the choice.
          </p>
          <p>
            After an election is complete, published results, audit information,
            and a public ledger let visitors inspect the outcome.
          </p>
        </div>
      </section>
    </section>
  )
}

export default HomePage
