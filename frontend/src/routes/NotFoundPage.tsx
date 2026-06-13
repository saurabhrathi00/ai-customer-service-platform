import { Link } from 'react-router-dom';
import { Compass, Home } from 'lucide-react';
import { motion } from 'framer-motion';
import { buttonVariants } from '@/components/ui/Button';

export default function NotFoundPage() {
  return (
    <div className="min-h-[60vh] grid place-items-center p-6">
      <motion.div
        className="text-center"
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
      >
        <motion.div className="mx-auto mb-6 grid h-14 w-14 place-items-center rounded-full bg-primary/10 text-primary animate-float">
          <Compass className="h-6 w-6" />
        </motion.div>
        <p className="gradient-text text-8xl font-black tracking-tighter mb-2">404</p>
        <h1 className="text-3xl font-semibold tracking-tight">Page not found</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          We couldn't find what you were looking for.
        </p>
        <Link to="/" className={`${buttonVariants()} mt-6`}>
          <Home className="h-4 w-4" /> Back to dashboard
        </Link>
      </motion.div>
    </div>
  );
}
