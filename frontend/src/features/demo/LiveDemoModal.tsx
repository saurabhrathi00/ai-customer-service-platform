import { useEffect, useRef } from 'react';
import { Phone, PhoneOff } from 'lucide-react';
import {
  Dialog,
  DialogBody,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { useLiveDemo } from './useLiveDemo';
import { cn } from '@/lib/utils';

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  demoSecondsRemaining: number;
}

export function LiveDemoModal({ open, onOpenChange, demoSecondsRemaining }: Props) {
  const { status, secondsRemaining, transcript, error, start, stop } = useLiveDemo();
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [transcript]);

  const displaySeconds = status === 'active' ? secondsRemaining : demoSecondsRemaining;
  const mins = Math.floor(displaySeconds / 60);
  const secs = displaySeconds % 60;

  const handleClose = () => {
    if (status === 'active' || status === 'connecting') stop();
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogHeader>
        <DialogTitle>Live Demo</DialogTitle>
        <DialogDescription>
          Talk to your AI assistant. {mins}:{secs.toString().padStart(2, '0')} remaining.
        </DialogDescription>
      </DialogHeader>

      <DialogBody className="space-y-4">
        {error && (
          <div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {error}
          </div>
        )}

        <div
          ref={scrollRef}
          className="h-64 overflow-y-auto rounded-lg border bg-muted/30 p-3 space-y-2"
        >
          {transcript.length === 0 && status !== 'active' && (
            <p className="text-sm text-muted-foreground text-center py-8">
              {status === 'idle'
                ? 'Press Start to begin talking to your AI assistant.'
                : status === 'connecting'
                  ? 'Connecting...'
                  : 'Demo ended.'}
            </p>
          )}
          {transcript.length === 0 && status === 'active' && (
            <p className="text-sm text-muted-foreground text-center py-8">
              Listening... Start speaking.
            </p>
          )}
          {transcript.map((entry, i) => (
            <div
              key={i}
              className={cn(
                'flex',
                entry.speaker === 'user' ? 'justify-end' : 'justify-start',
              )}
            >
              <div
                className={cn(
                  'max-w-[80%] rounded-lg px-3 py-2 text-sm',
                  entry.speaker === 'user'
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-card border',
                )}
              >
                {entry.text}
              </div>
            </div>
          ))}
        </div>

        <div className="flex items-center justify-center gap-3">
          {status === 'idle' || status === 'ended' ? (
            <Button
              onClick={start}
              disabled={demoSecondsRemaining <= 0}
              className="gap-2"
            >
              <Phone className="h-4 w-4" />
              {status === 'ended' ? 'Restart Demo' : 'Start Demo'}
            </Button>
          ) : (
            <>
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                {status === 'connecting' ? (
                  <span className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-r-transparent" />
                ) : (
                  <span className="relative flex h-3 w-3">
                    <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-green-400 opacity-75" />
                    <span className="relative inline-flex h-3 w-3 rounded-full bg-green-500" />
                  </span>
                )}
                {status === 'connecting' ? 'Connecting...' : 'Live'}
              </div>
              <Button variant="destructive" onClick={stop} className="gap-2">
                <PhoneOff className="h-4 w-4" />
                End Demo
              </Button>
            </>
          )}
        </div>

        {demoSecondsRemaining <= 0 && status === 'idle' && (
          <p className="text-center text-sm text-muted-foreground">
            Your demo time has been used up. Subscribe to get a phone number and start receiving real calls.
          </p>
        )}
      </DialogBody>
    </Dialog>
  );
}
