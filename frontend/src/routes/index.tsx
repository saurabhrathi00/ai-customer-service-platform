import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import { isAuthenticated } from '@/store/auth';
import { AppLayout } from '@/components/app/AppLayout';
import { ErrorBoundary } from '@/components/app/ErrorBoundary';
import { PageSkeleton } from '@/components/app/PageSkeleton';

const LoginPage = lazy(() => import('./LoginPage'));
const RegisterPage = lazy(() => import('./RegisterPage'));
const DashboardPage = lazy(() => import('./DashboardPage'));
const CallsListPage = lazy(() => import('./CallsListPage'));
const CallDetailPage = lazy(() => import('./CallDetailPage'));
const KnowledgePage = lazy(() => import('./KnowledgePage'));
const LeadsListPage = lazy(() => import('./LeadsListPage'));
const LeadDetailPage = lazy(() => import('./LeadDetailPage'));
const ConfigurationsPage = lazy(() => import('./ConfigurationsPage'));
const SettingsPage = lazy(() => import('./SettingsPage'));
const NotFoundPage = lazy(() => import('./NotFoundPage'));

function Protected() {
  if (!isAuthenticated()) return <Navigate to="/login" replace />;
  return <Outlet />;
}

function Suspended({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<PageSkeleton />}>{children}</Suspense>;
}

export const router = createBrowserRouter([
  {
    path: '/login',
    element: (
      <Suspended>
        <LoginPage />
      </Suspended>
    ),
  },
  {
    path: '/register',
    element: (
      <Suspended>
        <RegisterPage />
      </Suspended>
    ),
  },
  {
    element: <Protected />,
    errorElement: <ErrorBoundary />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <Suspended><DashboardPage /></Suspended> },
          { path: 'calls', element: <Suspended><CallsListPage /></Suspended> },
          { path: 'calls/:callId', element: <Suspended><CallDetailPage /></Suspended> },
          { path: 'leads', element: <Suspended><LeadsListPage /></Suspended> },
          { path: 'leads/:leadId', element: <Suspended><LeadDetailPage /></Suspended> },
          { path: 'knowledge', element: <Suspended><KnowledgePage /></Suspended> },
          { path: 'configurations', element: <Suspended><ConfigurationsPage /></Suspended> },
          { path: 'settings', element: <Suspended><SettingsPage /></Suspended> },
          { path: '*', element: <Suspended><NotFoundPage /></Suspended> },
        ],
      },
    ],
  },
]);
