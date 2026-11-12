import { Navigate, Outlet, useLocation } from 'react-router';
import { useMe } from '../api/hooks';
import { Loading } from './QueryState';
import { ChoosePasswordScreen } from './team/ChoosePasswordScreen';

/**
 * Sends signed-out visitors to the sign-in page and brings them back afterwards. The shared panel login only ever
 * sees the panel review pages; HR logins never see them.
 */
export function RequireAuth() {
  const { data: me, isLoading } = useMe();
  const location = useLocation();
  if (isLoading) {
    return <Loading label="Checking your session…" />;
  }
  if (!me) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  if (me.mustChangePassword) {
    return <ChoosePasswordScreen />;
  }
  const onPanelPage = location.pathname === '/panel' || location.pathname.startsWith('/panel/');
  if (me.role === 'panel' && !onPanelPage) {
    return <Navigate to="/panel" replace />;
  }
  if (me.role !== 'panel' && onPanelPage) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
