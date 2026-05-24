import { Link } from 'react-router-dom';
import { Compass, Home } from 'lucide-react';
import { buttonVariants } from '@/components/ui/Button';

export default function NotFoundPage() {
  return (
    <div className="min-h-[60vh] grid place-items-center p-6">
      <div className="text-center">
        <div className="mx-auto mb-6 grid h-14 w-14 place-items-center rounded-full bg-accent text-accent-foreground">
          <Compass className="h-6 w-6" />
        </div>
        <h1 className="text-3xl font-semibold tracking-tight">Page not found</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          We couldn't find what you were looking for.
        </p>
        <Link to="/" className={`${buttonVariants()} mt-6`}>
          <Home className="h-4 w-4" /> Back to dashboard
        </Link>
      </div>
    </div>
  );
}
