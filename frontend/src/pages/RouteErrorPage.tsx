import { Link, isRouteErrorResponse, useRouteError } from 'react-router'
import { getErrorMessage } from '../utils/getErrorMessage'

function getRouteErrorMessage(error: unknown) {
  if (!isRouteErrorResponse(error)) {
    return getErrorMessage(error)
  }

  if (typeof error.data === 'string' && error.data.trim()) {
    return error.data
  }

  if (
    typeof error.data === 'object' &&
    error.data !== null &&
    'message' in error.data &&
    typeof error.data.message === 'string'
  ) {
    return error.data.message
  }

  return error.statusText || `Request failed with status ${error.status}`
}

function RouteErrorPage() {
  const error = useRouteError()

  return (
    <main>
      <section className="page message-page" aria-labelledby="route-error-heading">
        <h1 id="route-error-heading">Something went wrong</h1>
        <p role="alert">{getRouteErrorMessage(error)}</p>
        <Link to="/">Return to the home page</Link>
      </section>
    </main>
  )
}

export default RouteErrorPage
