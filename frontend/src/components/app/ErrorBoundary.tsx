import { useRouteError, isRouteErrorResponse, useNavigate } from 'react-router-dom';
import { AlertTriangle, Home } from 'lucide-react';
import { Button } from '@/components/ui/Button';

export function ErrorBoundary() {
  const err = useRouteError();
  const navigate = useNavigate();
  const isRouteErr = isRouteErrorResponse(err);
  const status = isRouteErr ? err.status : 500;
  const message = isRouteErr
    ? err.statusText || err.data?.message
    : err instanceof Error
      ? err.message
      : 'Something went wrong';

  return (
    <div className="min-h-screen grid place-items-center p-6">
      <div className="max-w-md text-center">
        <div className="mx-auto mb-6 grid h-14 w-14 place-items-center rounded-full bg-destructive/10 text-destructive">
          <AlertTriangle className="h-6 w-6" />
        </div>
        <h1 className="text-2xl font-semibold tracking-tight">{status} — Something broke</h1>
        <p className="mt-2 text-sm text-muted-foreground">{message}</p>
        <Button className="mt-6" onClick={() => navigate('/')}>
          <Home className="h-4 w-4" /> Back to dashboard
        </Button>
      </div>
    </div>
  );
}
