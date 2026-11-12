import { createBrowserRouter, Navigate } from 'react-router';
import { AppLayout } from './components/AppLayout';
import { Loading } from './components/QueryState';
import { RequireAuth } from './components/RequireAuth';
import { RouteError } from './components/RouteError';
import { OverviewPage } from './pages/OverviewPage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';

/** Screens other than sign-in and the dashboard load when first opened, so the first page appears faster. */
const page = (load: () => Promise<{ Component: React.ComponentType }>) => ({
  lazy: load,
  hydrateFallbackElement: <Loading />,
});

export const router = createBrowserRouter([
  {
    errorElement: <RouteError />,
    children: [
      { path: '/login', element: <LoginPage /> },
      // Public pages, opened from links in emails.
      { path: '/assessor/:token', ...page(() => import('./pages/AssessorPage').then((m) => ({ Component: m.AssessorPage }))) },
      { path: '/my-interview/:token', ...page(() => import('./pages/CandidatePortalPage').then((m) => ({ Component: m.CandidatePortalPage }))) },
      {
        element: <RequireAuth />,
        children: [
          // The shared panel login sees only this page.
          { path: '/panel', ...page(() => import('./pages/PanelPage').then((m) => ({ Component: m.PanelPage }))) },
          {
            element: <AppLayout />,
            children: [
              {
                // A screen that fails shows the error inside the layout, so the menu still works.
                errorElement: <RouteError />,
                children: [
                  { index: true, element: <OverviewPage /> },
                  { path: 'jobs', ...page(() => import('./pages/JobsPage').then((m) => ({ Component: m.JobsPage }))) },
                  { path: 'jobs/new', ...page(() => import('./pages/JobEditorPage').then((m) => ({ Component: m.JobEditorPage }))) },
                  { path: 'jobs/:jobId', ...page(() => import('./pages/JobPage').then((m) => ({ Component: m.JobPage }))) },
                  { path: 'jobs/:jobId/edit', ...page(() => import('./pages/JobEditorPage').then((m) => ({ Component: m.JobEditorPage }))) },
                  {
                    path: 'jobs/:jobId/ai-interviews',
                    ...page(() => import('./pages/AiInterviewsPage').then((m) => ({ Component: m.AiInterviewsPage }))),
                  },
                  { path: 'jobs/:jobId/round1', element: <Navigate to="../ai-interviews" relative="path" replace /> },
                  { path: 'interviews', ...page(() => import('./pages/InterviewsPage').then((m) => ({ Component: m.InterviewsPage }))) },
                  { path: 'reschedule/:requestId', ...page(() => import('./pages/ReschedulePage').then((m) => ({ Component: m.ReschedulePage }))) },
                  { path: 'candidates', ...page(() => import('./pages/CandidatesPage').then((m) => ({ Component: m.CandidatesPage }))) },
                  {
                    path: 'applications/:applicationId',
                    ...page(() => import('./pages/ApplicationPage').then((m) => ({ Component: m.ApplicationPage }))),
                  },
                  { path: 'messages', ...page(() => import('./pages/MessagesPage').then((m) => ({ Component: m.MessagesPage }))) },
                  { path: 'reports', ...page(() => import('./pages/ReportsPage').then((m) => ({ Component: m.ReportsPage }))) },
                  { path: 'audit', ...page(() => import('./pages/AuditTrailPage').then((m) => ({ Component: m.AuditTrailPage }))) },
                  { path: 'settings', ...page(() => import('./pages/SettingsPage').then((m) => ({ Component: m.SettingsPage }))) },
                  { path: '*', element: <NotFoundPage /> },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
]);
