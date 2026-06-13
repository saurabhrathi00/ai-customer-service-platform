import * as React from 'react';

const TRAIL_MS = 550;
const MAX_PTS  = 80;
const SPARK_LIFE  = 420;   // ms each spark lives
const SPARKS_PER_MOVE = 3; // sparks spawned per mousemove

interface Point { x: number; y: number; t: number }
interface Spark {
  x: number; y: number;
  vx: number; vy: number;
  size: number;
  life: number;    // ms remaining
  maxLife: number;
  star: boolean;   // draw as 4-point cross instead of circle
}

export function CursorTrail() {
  const canvasRef = React.useRef<HTMLCanvasElement>(null);
  const pts    = React.useRef<Point[]>([]);
  const sparks = React.useRef<Spark[]>([]);
  const raf    = React.useRef(0);

  // Collect mouse positions + spawn sparks
  React.useEffect(() => {
    const onMove = (e: MouseEvent) => {
      const now = performance.now();
      pts.current.push({ x: e.clientX, y: e.clientY, t: now });
      if (pts.current.length > MAX_PTS) pts.current.shift();

      // Spawn sparks
      for (let i = 0; i < SPARKS_PER_MOVE; i++) {
        const angle  = Math.random() * Math.PI * 2;
        const speed  = 0.6 + Math.random() * 1.8;
        sparks.current.push({
          x: e.clientX + (Math.random() - 0.5) * 4,
          y: e.clientY + (Math.random() - 0.5) * 4,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          size: 1.2 + Math.random() * 2,
          life: SPARK_LIFE,
          maxLife: SPARK_LIFE,
          star: Math.random() > 0.55,
        });
      }
    };
    window.addEventListener('mousemove', onMove);
    return () => window.removeEventListener('mousemove', onMove);
  }, []);

  // Draw loop
  React.useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const resize = () => {
      canvas.width  = window.innerWidth;
      canvas.height = window.innerHeight;
    };
    resize();
    window.addEventListener('resize', resize);

    let lastTime = performance.now();

    function draw() {
      const ctx = canvas!.getContext('2d');
      if (!ctx) { raf.current = requestAnimationFrame(draw); return; }

      const now = performance.now();
      const dt  = now - lastTime;
      lastTime  = now;

      ctx.clearRect(0, 0, canvas!.width, canvas!.height);

      // ── Trail ────────────────────────────────────────────
      pts.current = pts.current.filter(p => now - p.t < TRAIL_MS);
      const points = pts.current;

      if (points.length >= 2) {
        const len = points.length;
        for (let i = 1; i < len; i++) {
          const prev = points[i - 1];
          const curr = points[i];
          const progress  = i / (len - 1);
          const ageFactor = Math.max(0, 1 - (now - curr.t) / TRAIL_MS);
          const alpha = progress * ageFactor * 0.9;
          const width = 0.8 + progress * 2.8;

          ctx.beginPath();
          ctx.moveTo(prev.x, prev.y);
          ctx.lineTo(curr.x, curr.y);
          ctx.lineCap    = 'round';
          ctx.lineJoin   = 'round';
          ctx.lineWidth  = width;
          ctx.shadowColor = `hsla(262, 80%, 75%, ${alpha})`;
          ctx.shadowBlur  = 10 + progress * 10;
          ctx.strokeStyle = `hsla(262, 80%, 80%, ${alpha})`;
          ctx.stroke();

          // crisp bright core
          ctx.shadowBlur  = 0;
          ctx.lineWidth   = width * 0.4;
          ctx.strokeStyle = `hsla(262, 90%, 95%, ${alpha * 0.7})`;
          ctx.stroke();
        }
      }

      // ── Sparks ───────────────────────────────────────────
      sparks.current = sparks.current.filter(s => s.life > 0);

      for (const s of sparks.current) {
        s.life -= dt;
        s.x += s.vx;
        s.y += s.vy;
        s.vy += 0.03; // tiny gravity

        const t     = Math.max(0, s.life / s.maxLife); // 1→0
        const alpha = t * t;                            // ease-out fade
        const r     = s.size * (0.4 + t * 0.6);

        ctx.globalAlpha = alpha;
        ctx.shadowColor = 'hsl(262, 90%, 85%)';
        ctx.shadowBlur  = 8;
        ctx.fillStyle   = `hsl(${260 + Math.random() * 30}, 90%, ${80 + t * 15}%)`;

        if (s.star) {
          // 4-point sparkle cross
          drawStar(ctx, s.x, s.y, r * 2.5);
        } else {
          ctx.beginPath();
          ctx.arc(s.x, s.y, r, 0, Math.PI * 2);
          ctx.fill();
        }
      }

      ctx.globalAlpha = 1;
      ctx.shadowBlur  = 0;

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

/** Draws a 4-point star/cross sparkle centered at (cx, cy) */
function drawStar(ctx: CanvasRenderingContext2D, cx: number, cy: number, r: number) {
  const thin = r * 0.18;
  ctx.beginPath();
  // horizontal bar
  ctx.moveTo(cx - r, cy - thin);
  ctx.lineTo(cx - thin, cy - thin);
  ctx.lineTo(cx - thin, cy - r);
  ctx.lineTo(cx + thin, cy - r);
  ctx.lineTo(cx + thin, cy - thin);
  ctx.lineTo(cx + r, cy - thin);
  ctx.lineTo(cx + r, cy + thin);
  ctx.lineTo(cx + thin, cy + thin);
  ctx.lineTo(cx + thin, cy + r);
  ctx.lineTo(cx - thin, cy + r);
  ctx.lineTo(cx - thin, cy + thin);
  ctx.lineTo(cx - r, cy + thin);
  ctx.closePath();
  ctx.fill();
}
