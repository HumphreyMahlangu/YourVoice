import { Link } from 'react-router'
import { useAuth } from '../auth/useAuth'
import BrandMark from '../components/BrandMark'

function HomePage() {
  const { session } = useAuth()
  const accountPath = session?.role === 'ADMIN' ? '/admin' : '/dashboard'

  return (
    <section className="page home-page" aria-labelledby="home-heading">
      <div className="hero">
        <div className="hero__copy">
          <p className="eyebrow">Digital voting infrastructure</p>
          <h1 id="home-heading">
            Public confidence,
            <span> built into every vote.</span>
          </h1>
          <p className="hero__lead">
            VoteTrust brings registration, secure ballot access, and public
            result verification into one clear civic experience.
          </p>

          <div className="hero__actions">
            <Link className="button button--primary button--large" to="/elections">
              Explore elections
            </Link>
            {session ? (
              <Link className="button button--secondary button--large" to={accountPath}>
                Open my workspace
              </Link>
            ) : (
              <Link className="button button--secondary button--large" to="/register">
                Create voter account
              </Link>
            )}
          </div>

          <ul className="trust-points" aria-label="Platform safeguards">
            <li>Protected voter profile</li>
            <li>Anonymous ballot submission</li>
            <li>Public result ledger</li>
          </ul>
        </div>

        <aside className="integrity-card" aria-label="VoteTrust assurance model">
          <div className="integrity-card__header">
            <span>Assurance model</span>
            <span className="system-status">
              <span aria-hidden="true" /> Designed for verification
            </span>
          </div>

          <div className="integrity-card__seal">
            <BrandMark size="large" />
            <div>
              <span>Vote integrity</span>
              <strong>Identity stays separate from choice</strong>
            </div>
          </div>

          <ol className="assurance-flow">
            <li>
              <span>01</span>
              <div>
                <strong>Establish eligibility</strong>
                <p>Register once with a protected voter profile.</p>
              </div>
            </li>
            <li>
              <span>02</span>
              <div>
                <strong>Cast anonymously</strong>
                <p>A one-time credential authorises the ballot, not the identity.</p>
              </div>
            </li>
            <li>
              <span>03</span>
              <div>
                <strong>Verify publicly</strong>
                <p>Published ledgers make completed results inspectable.</p>
              </div>
            </li>
          </ol>
        </aside>
      </div>

      <section className="principles" aria-labelledby="principles-heading">
        <div className="section-heading">
          <p className="eyebrow">A deliberate trust architecture</p>
          <h2 id="principles-heading">Clear at every critical moment.</h2>
          <p>
            Each screen explains what is happening, what is irreversible, and
            what a voter can verify next.
          </p>
        </div>

        <div className="principle-grid">
          <article>
            <span className="principle-grid__number">01</span>
            <h3>Private by structure</h3>
            <p>
              Account identity establishes eligibility while the ballot is
              submitted through a separate, short-lived credential.
            </p>
          </article>
          <article>
            <span className="principle-grid__number">02</span>
            <h3>Calm under pressure</h3>
            <p>
              Plain-language review, expiry, and uncertainty states guide voters
              without obscuring the consequences of an action.
            </p>
          </article>
          <article>
            <span className="principle-grid__number">03</span>
            <h3>Evidence after the count</h3>
            <p>
              Public audit and ledger views expose the records behind final
              results instead of asking citizens to accept a number on trust.
            </p>
          </article>
        </div>
      </section>

      <section className="home-cta" aria-labelledby="home-cta-heading">
        <div>
          <p className="eyebrow">Ready when you are</p>
          <h2 id="home-cta-heading">See the democratic process clearly.</h2>
        </div>
        <Link className="button button--light button--large" to="/elections">
          Browse all elections
        </Link>
      </section>
    </section>
  )
}

export default HomePage
