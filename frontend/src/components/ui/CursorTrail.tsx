import * as React from 'react';

const TRAIL_MS = 550;   // how long a point lives before fully fading
const MAX_PTS = 80;     // ring-buffer cap

interface Point { x: number; y: number; t: number }

export function CursorTrail() {
  const canvasRef = React.useRef<HTMLCanvasElement>(null);
  const pts = React.useRef<Point[]>([]);
  const raf = React.useRef(0);

  // Collect mouse positions
  React.useEffect(() => {
    const onMove = (e: MouseEvent) => {
      pts.current.push({ x: e.clientX, y: e.clientY, t: performance.now() });
      if (pts.current.length > MAX_PTS) pts.current.shift();
    };
    window.addEventListener('mousemove', onMove);
    return () => window.removeEventListener('mousemove', onMove);
  }, []);

  // Draw loop
  React.useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const resize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    resize();
    window.addEventListener('resize', resize);

    function draw() {
      const ctx = canvas!.getContext('2d');
      if (!ctx) { raf.current = requestAnimationFrame(draw); return; }

      ctx.clearRect(0, 0, canvas!.width, canvas!.height);

      const now = performance.now();
      // Evict expired points
      pts.current = pts.current.filter(p => now - p.t < TRAIL_MS);

      const points = pts.current;

      if (points.length >= 2) {
        const len = points.length;

        // Draw each segment old→new with increasing opacity + width + glow
        for (let i = 1; i < len; i++) {
          const prev = points[i - 1];
          const curr = points[i];

          // progress: 0 = tail (oldest), 1 = head (newest)
          const progress = i / (len - 1);
          // age-based fade so the tail disappears even when cursor is still
          const ageFactor = Math.max(0, 1 - (now - curr.t) / TRAIL_MS);
          const alpha = progress * ageFactor * 0.9;
          const width = 0.8 + progress * 2.8;

          ctx.beginPath();
          ctx.moveTo(prev.x, prev.y);
          ctx.lineTo(curr.x, curr.y);
          ctx.lineCap = 'round';
          ctx.lineJoin = 'round';
          ctx.lineWidth = width;

          // Glow pass
          ctx.shadowColor = `hsla(262, 80%, 75%, ${alpha})`;
          ctx.shadowBlur = 10 + progress * 10;
          ctx.strokeStyle = `hsla(262, 80%, 80%, ${alpha})`;
          ctx.stroke();

          // Crisp core on top
          ctx.shadowBlur = 0;
          ctx.lineWidth = width * 0.45;
          ctx.strokeStyle = `hsla(262, 90%, 92%, ${alpha * 0.7})`;
          ctx.stroke();
        }
      }

      raf.current = requestAnimationFrame(draw);
    }

    raf.current = requestAnimationFrame(draw);
    return () => {
      cancelAnimationFrame(raf.current);
      window.removeEventListener('resize', resize);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="pointer-events-none fixed inset-0 z-[197] hidden lg:block"
    />
  );
}
