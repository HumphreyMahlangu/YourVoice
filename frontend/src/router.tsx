import { createBrowserRouter } from 'react-router'
import App from './App'
import ProfilePage from './pages/ProfilePage'
import AdminProfileVerificationPage from './pages/AdminProfileVerificationPage'
import RequireAdmin from './auth/RequireAdmin'
import RequireVoter from './auth/RequireVoter'
import AdminAccountsPage from './pages/AdminAccountsPage'
import AdminCreateElectionPage from './pages/AdminCreateElectionPage'
import AdminCreateVotingDistrictPage from './pages/AdminCreateVotingDistrictPage'
import AdminDashboardPage from './pages/AdminDashboardPage'
import AdminElectionBuilderPage from './pages/AdminElectionBuilderPage'
import AdminElectionOperationsPage from './pages/AdminElectionOperationsPage'
import AdminSecurityEventsPage from './pages/AdminSecurityEventsPage'
import AdminVotingDistrictsPage from './pages/AdminVotingDistrictsPage'
import ContestAuditPage from './pages/ContestAuditPage'
import ContestLedgerPage from './pages/ContestLedgerPage'
import ContestResultsPage from './pages/ContestResultsPage'
import ElectionDetailsPage from './pages/ElectionDetailsPage'
import ElectionRegistrationPage from './pages/ElectionRegistrationPage'
import ElectionsPage from './pages/ElectionsPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import RegisterPage from './pages/RegisterPage'
import RouteErrorPage from './pages/RouteErrorPage'
import VotePage from './pages/VotePage'
import VoterDashboardPage from './pages/VoterDashboardPage'

const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        index: true,
        element: <HomePage />,
      },
      {
        path: 'elections',
        element: <ElectionsPage />,
      },
      {
        path: 'elections/:electionId',
        element: <ElectionDetailsPage />,
      },
      {
        path: 'elections/:electionId/contests/:contestId/results',
        element: <ContestResultsPage />,
      },
      {
        path: 'elections/:electionId/contests/:contestId/audit',
        element: <ContestAuditPage />,
      },
      {
        path: 'elections/:electionId/contests/:contestId/ledger',
        element: <ContestLedgerPage />,
      },
      {
        path: 'login',
        element: <LoginPage />,
      },
      {
        path: 'register',
        element: <RegisterPage />,
      },
      {
        element: <RequireVoter />,
        children: [
          {
            path: 'profile', element: <ProfilePage />,
          },
          {
            path: 'dashboard',
            element: <VoterDashboardPage />,
          },
          {
            path: 'elections/:electionId/register',
            element: <ElectionRegistrationPage />,
          },
          {
            path: 'elections/:electionId/contests/:contestId/vote',
            element: <VotePage />,
          },
        ],
      },
      {
        element: <RequireAdmin />,
        children: [
          {
            path: 'admin/profile-verification', element: <AdminProfileVerificationPage />,
          },
          {
            path: 'admin',
            element: <AdminDashboardPage />,
          },
          {
            path: 'admin/security-events',
            element: <AdminSecurityEventsPage />,
          },
          {
            path: 'admin/elections/new',
            element: <AdminCreateElectionPage />,
          },
          {
            path: 'admin/elections/:electionId/configure',
            element: <AdminElectionBuilderPage />,
          },
          {
            path: 'admin/elections/:electionId/operations',
            element: <AdminElectionOperationsPage />,
          },
          {
            path: 'admin/voting-districts',
            element: <AdminVotingDistrictsPage />,
          },
          {
            path: 'admin/voting-districts/new',
            element: <AdminCreateVotingDistrictPage />,
          },
          {
            path: 'admin/accounts',
            element: <AdminAccountsPage />,
          },
        ],
      },
      {
        path: '*',
        element: <NotFoundPage />,
      },
    ],
  },
])

export default router
