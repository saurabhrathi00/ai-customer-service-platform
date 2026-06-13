import * as React from 'react';

const TRAIL_MS        = 650;   // how long a point lives
const MAX_PTS         = 120;   // more points → smoother interpolation
const SPARK_LIFE      = 500;
const SPARKS_PER_MOVE = 2;     // fewer sparks feel cleaner

interface Point { x: number; y: number; t: number }
interface Spark {
  x: number; y: number;
  vx: number; vy: number;
  size: number;
  life: number;
  maxLife: number;
  star: boolean;
}

export function CursorTrail() {
  const canvasRef = React.useRef<HTMLCanvasElement>(null);
  const pts       = React.useRef<Point[]>([]);
  const sparks    = React.useRef<Spark[]>([]);
  const raf       = React.useRef(0);

  // Collect mouse positions + spawn sparks
  React.useEffect(() => {
    const onMove = (e: MouseEvent) => {
      const now = performance.now();

      // Minimum distance gate — don't add points when barely moving
      const last = pts.current[pts.current.length - 1];
      if (last) {
        const dx = e.clientX - last.x;
        const dy = e.clientY - last.y;
        if (dx * dx + dy * dy < 4) return; // < 2px, skip
      }

      pts.current.push({ x: e.clientX, y: e.clientY, t: now });
      if (pts.current.length > MAX_PTS) pts.current.shift();

      // Spawn sparks — slower, lighter feel
      for (let i = 0; i < SPARKS_PER_MOVE; i++) {
        const angle = Math.random() * Math.PI * 2;
        const speed = 0.3 + Math.random() * 1.2;
        sparks.current.push({
          x: e.clientX + (Math.random() - 0.5) * 6,
          y: e.clientY + (Math.random() - 0.5) * 6,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          size: 1 + Math.random() * 1.8,
          life: SPARK_LIFE,
          maxLife: SPARK_LIFE,
          star: Math.random() > 0.5,
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

      // ── Smooth trail via quadratic bezier through midpoints ──
      pts.current = pts.current.filter(p => now - p.t < TRAIL_MS);
      const points = pts.current;

      if (points.length >= 3) {
        const len = points.length;

        // Draw in segments using midpoint-bezier technique.
        // Each segment goes from mid(i-1,i) → mid(i,i+1) with points[i] as control point.
        // This creates a continuous smooth C1 spline.
        for (let i = 1; i < len - 1; i++) {
          const p0 = points[i - 1];
          const p1 = points[i];
          const p2 = points[i + 1];

          // Midpoints
          const m0x = (p0.x + p1.x) / 2;
          const m0y = (p0.y + p1.y) / 2;
          const m1x = (p1.x + p2.x) / 2;
          const m1y = (p1.y + p2.y) / 2;

          const progress  = i / (len - 2);          // 0 (tail) → 1 (head)
          const ageFactor = Math.max(0, 1 - (now - p1.t) / TRAIL_MS);
          const alpha     = progress * ageFactor * 0.88;
          const width     = 0.6 + progress * 3.2;

          // ── Glow pass ──
          ctx.beginPath();
          ctx.moveTo(m0x, m0y);
          ctx.quadraticCurveTo(p1.x, p1.y, m1x, m1y);
          ctx.lineCap    = 'round';
          ctx.lineJoin   = 'round';
          ctx.lineWidth  = width;
          ctx.shadowColor = `hsla(252, 90%, 68%, ${alpha})`;
          ctx.shadowBlur  = 12 + progress * 16;
          ctx.strokeStyle = `hsla(252, 88%, 72%, ${alpha})`;
          ctx.stroke();

          // ── Bright core pass (no shadow so it stays sharp) ──
          ctx.shadowBlur  = 0;
          ctx.lineWidth   = width * 0.35;
          ctx.strokeStyle = `hsla(220, 95%, 94%, ${alpha * 0.75})`;
          ctx.stroke();
        }
      }

      // ── Sparks ──────────────────────────────────────────────
      sparks.current = sparks.current.filter(s => s.life > 0);

      for (const s of sparks.current) {
        s.life -= dt;
        s.x    += s.vx;
        s.y    += s.vy;
        s.vy   += 0.018; // very gentle gravity
        s.vx   *= 0.99;  // slight air drag

        const t     = Math.max(0, s.life / s.maxLife);
        const alpha = t * t * t;              // cubic fade — lingers then snaps out
        const r     = s.size * (0.3 + t * 0.7);

        ctx.globalAlpha = alpha;
        ctx.shadowColor = 'hsl(252, 90%, 72%)';
        ctx.shadowBlur  = 10;
        ctx.fillStyle   = `hsl(${242 + Math.random() * 28}, 88%, ${70 + t * 16}%)`;

        if (s.star) {
          drawStar(ctx, s.x, s.y, r * 2.2);
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

function drawStar(ctx: CanvasRenderingContext2D, cx: number, cy: number, r: number) {
  const thin = r * 0.16;
  ctx.beginPath();
  ctx.moveTo(cx - r,    cy - thin);
  ctx.lineTo(cx - thin, cy - thin);
  ctx.lineTo(cx - thin, cy - r);
  ctx.lineTo(cx + thin, cy - r);
  ctx.lineTo(cx + thin, cy - thin);
  ctx.lineTo(cx + r,    cy - thin);
  ctx.lineTo(cx + r,    cy + thin);
  ctx.lineTo(cx + thin, cy + thin);
  ctx.lineTo(cx + thin, cy + r);
  ctx.lineTo(cx - thin, cy + r);
  ctx.lineTo(cx - thin, cy + thin);
  ctx.lineTo(cx - r,    cy + thin);
  ctx.closePath();
  ctx.fill();
}
