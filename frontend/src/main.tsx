import '@fontsource/ibm-plex-sans/400.css';
import '@fontsource/ibm-plex-sans/500.css';
import '@fontsource/ibm-plex-sans/600.css';
import '@fontsource/ibm-plex-sans/700.css';
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/source-serif-4/600.css';
import '@mantine/core/styles.css';
import '@mantine/dates/styles.css';
import '@mantine/dropzone/styles.css';
import '@mantine/notifications/styles.css';
import './styles.css';

import { MantineProvider } from '@mantine/core';
import { DatesProvider } from '@mantine/dates';
import { Notifications, notifications } from '@mantine/notifications';
import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import 'dayjs/locale/en-gb';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { ApiError } from './api/client';
import { keys } from './api/hooks';
import { OfflineBanner } from './components/OfflineBanner';
import { router } from './router';
import { cssVariablesResolver, theme } from './theme';

const queryClient: QueryClient = new QueryClient({
  queryCache: new QueryCache({ onError: handleUnauthenticated }),
  mutationCache: new MutationCache({ onError: handleUnauthenticated }),
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
      refetchOnWindowFocus: true,
    },
  },
});

/** The session ended (timed out or signed out elsewhere): go to sign-in, then come back to the same page. */
function handleUnauthenticated(error: unknown) {
  if (error instanceof ApiError && error.status === 401 && queryClient.getQueryData(keys.me)) {
    queryClient.setQueryData(keys.me, null);
    notifications.show({
      id: 'session-ended',
      color: 'blue',
      title: 'Please sign in again',
      message: 'Your session ended. Sign in and you’ll be back where you were.',
      autoClose: 8000,
    });
  }
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MantineProvider theme={theme} cssVariablesResolver={cssVariablesResolver} defaultColorScheme="light">
      <DatesProvider settings={{ locale: 'en-gb', firstDayOfWeek: 1 }}>
        <Notifications position="top-right" />
        <OfflineBanner />
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </DatesProvider>
    </MantineProvider>
  </StrictMode>,
);
