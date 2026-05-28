import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore, type JwtClaims } from '@/store/auth';
import { decodeJwt } from '@/lib/utils';
import { Dialog, DialogHeader, DialogTitle, DialogBody, DialogFooter } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import axios from 'axios';

const WARNING_BEFORE_MS = 30_000;
const TICK_MS = 1_000;

export function SessionExpiryGuard() {
  const navigate = useNavigate();
  const { accessToken, refreshToken, setAccessToken, logout } = useAuthStore();

  const [secondsLeft, setSecondsLeft] = React.useState<number | null>(null);
  const [refreshing, setRefreshing] = React.useState(false);

  const showModal = secondsLeft !== null && secondsLeft <= 30;

  React.useEffect(() => {
    if (!accessToken) return;
    const claims = decodeJwt<JwtClaims>(accessToken);
    if (!claims?.exp) return;

    const expiresAtMs = claims.exp * 1000;

    function tick() {
      const remaining = expiresAtMs - Date.now();
      if (remaining <= 0) {
        logout();
        navigate('/login', { replace: true });
        return;
      }
      if (remaining <= WARNING_BEFORE_MS) {
        setSecondsLeft(Math.ceil(remaining / 1_000));
      } else {
        setSecondsLeft(null);
      }
    }

    tick();
    const id = setInterval(tick, TICK_MS);

    const warnAt = expiresAtMs - WARNING_BEFORE_MS - Date.now();
    let wakeId: ReturnType<typeof setTimeout> | null = null;
    if (warnAt > TICK_MS) {
      wakeId = setTimeout(tick, warnAt);
    }

    return () => {
      clearInterval(id);
      if (wakeId) clearTimeout(wakeId);
    };
  }, [accessToken, logout, navigate]);

  async function handleRefresh() {
    if (!refreshToken) {
      logout();
      navigate('/login', { replace: true });
      return;
    }
    setRefreshing(true);
    try {
      const { data } = await axios.post<{ accessToken: string }>(
        '/api/auth/api/v1/auth/refresh',
        { refreshToken },
      );
      setAccessToken(data.accessToken);
      setSecondsLeft(null);
    } catch {
      logout();
      navigate('/login', { replace: true });
    } finally {
      setRefreshing(false);
    }
  }

  if (!showModal) return null;

  return (
    <Dialog open onOpenChange={() => {}}>
      <DialogHeader>
        <DialogTitle>Session Expiring</DialogTitle>
      </DialogHeader>
      <DialogBody>
        <p className="text-sm text-muted-foreground">
          Your session will expire in{' '}
          <span className="font-semibold text-foreground">{secondsLeft}s</span>.
          Click below to stay logged in.
        </p>
      </DialogBody>
      <DialogFooter>
        <Button onClick={handleRefresh} disabled={refreshing}>
          {refreshing ? 'Refreshing...' : 'Stay Logged In'}
        </Button>
      </DialogFooter>
    </Dialog>
  );
}
