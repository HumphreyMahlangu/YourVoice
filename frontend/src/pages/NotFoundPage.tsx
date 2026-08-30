import { Link } from 'react-router'

function NotFoundPage() {
  return (
    <section className="page message-page" aria-labelledby="not-found-heading">
      <h1 id="not-found-heading">Page not found</h1>
      <p>The page you requested does not exist.</p>
      <Link to="/">Return to the home page</Link>
    </section>
  )
}

export default NotFoundPage
