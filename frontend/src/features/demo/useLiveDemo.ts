import { useCallback, useEffect, useRef, useState } from 'react';
import { useAuthStore } from '@/store/auth';

export type DemoStatus = 'idle' | 'connecting' | 'active' | 'ended';

interface TranscriptEntry {
  speaker: 'user' | 'assistant';
  text: string;
}

interface UseLiveDemoReturn {
  status: DemoStatus;
  secondsRemaining: number;
  transcript: TranscriptEntry[];
  error: string | null;
  start: () => void;
  stop: () => void;
}

const TARGET_SAMPLE_RATE = 16000;

const BACKEND_WS_BASE = import.meta.env.VITE_BACKEND_WS_BASE ?? '';

function buildWsUrl(businessId: string, token: string): string {
  const callId = crypto.randomUUID().replace(/-/g, '').slice(0, 26);
  let base: string;
  if (BACKEND_WS_BASE) {
    base = BACKEND_WS_BASE;
  } else {
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
    base = `${proto}://${window.location.host}`;
  }
  const isDev = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
  const contextPath = isDev ? '/ws/demo' : '/call-orchestration-service/ws/demo';
  return `${base}${contextPath}/call/${callId}?token=${encodeURIComponent(token)}&businessId=${encodeURIComponent(businessId)}`;
}

export function useLiveDemo(): UseLiveDemoReturn {
  const [status, setStatus] = useState<DemoStatus>('idle');
  const [secondsRemaining, setSecondsRemaining] = useState(0);
  const [transcript, setTranscript] = useState<TranscriptEntry[]>([]);
  const [error, setError] = useState<string | null>(null);

  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (status === 'active') {
      countdownRef.current = setInterval(() => {
        setSecondsRemaining((prev) => {
          if (prev <= 1) {
            if (countdownRef.current) clearInterval(countdownRef.current);
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
    } else {
      if (countdownRef.current) {
        clearInterval(countdownRef.current);
        countdownRef.current = null;
      }
    }
    return () => {
      if (countdownRef.current) clearInterval(countdownRef.current);
    };
  }, [status]);

  const wsRef = useRef<WebSocket | null>(null);
  const audioCtxRef = useRef<AudioContext | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const workletRef = useRef<AudioWorkletNode | null>(null);
  const playbackQueueRef = useRef<Float32Array[]>([]);
  const isPlayingRef = useRef(false);
  const nextPlayTimeRef = useRef(0);

  const cleanup = useCallback(() => {
    if (workletRef.current) {
      workletRef.current.disconnect();
      workletRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
    if (wsRef.current) {
      if (wsRef.current.readyState === WebSocket.OPEN) {
        wsRef.current.send(JSON.stringify({ event: 'stop' }));
      }
      wsRef.current.close();
      wsRef.current = null;
    }
    if (audioCtxRef.current) {
      audioCtxRef.current.close().catch(() => {});
      audioCtxRef.current = null;
    }
    playbackQueueRef.current = [];
    isPlayingRef.current = false;
    nextPlayTimeRef.current = 0;
  }, []);

  useEffect(() => cleanup, [cleanup]);

  const playPcmChunk = useCallback((pcmInt16: Int16Array, sampleRate: number) => {
    const ctx = audioCtxRef.current;
    if (!ctx || ctx.state === 'closed') return;

    const float32 = new Float32Array(pcmInt16.length);
    for (let i = 0; i < pcmInt16.length; i++) {
      float32[i] = pcmInt16[i] / 32768;
    }

    const buffer = ctx.createBuffer(1, float32.length, sampleRate);
    buffer.getChannelData(0).set(float32);
    const source = ctx.createBufferSource();
    source.buffer = buffer;
    source.connect(ctx.destination);

    const now = ctx.currentTime;
    const startTime = Math.max(now, nextPlayTimeRef.current);
    source.start(startTime);
    nextPlayTimeRef.current = startTime + buffer.duration;
  }, []);

  const start = useCallback(async () => {
    const { accessToken, businessId } = useAuthStore.getState();
    if (!accessToken || !businessId) {
      setError('Not authenticated');
      return;
    }

    setError(null);
    setTranscript([]);
    setStatus('connecting');

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { sampleRate: TARGET_SAMPLE_RATE, channelCount: 1, echoCancellation: true, noiseSuppression: true },
      });
      streamRef.current = stream;

      const audioCtx = new AudioContext({ sampleRate: TARGET_SAMPLE_RATE });
      audioCtxRef.current = audioCtx;

      const url = buildWsUrl(businessId, accessToken);
      const ws = new WebSocket(url);
      wsRef.current = ws;

      ws.onopen = async () => {
        try {
          const source = audioCtx.createMediaStreamSource(stream);

          await audioCtx.audioWorklet.addModule(
            URL.createObjectURL(
              new Blob(
                [
                  `class PcmSender extends AudioWorkletProcessor {
                    process(inputs) {
                      const input = inputs[0];
                      if (input && input[0] && input[0].length > 0) {
                        this.port.postMessage(input[0]);
                      }
                      return true;
                    }
                  }
                  registerProcessor('pcm-sender', PcmSender);`,
                ],
                { type: 'application/javascript' },
              ),
            ),
          );

          const worklet = new AudioWorkletNode(audioCtx, 'pcm-sender');
          workletRef.current = worklet;

          worklet.port.onmessage = (ev: MessageEvent<Float32Array>) => {
            if (ws.readyState !== WebSocket.OPEN) return;
            const float32 = ev.data;
            const int16 = new Int16Array(float32.length);
            for (let i = 0; i < float32.length; i++) {
              const s = Math.max(-1, Math.min(1, float32[i]));
              int16[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
            }
            const b64 = uint8ToBase64(new Uint8Array(int16.buffer));
            ws.send(JSON.stringify({ event: 'media', media: { payload: b64 } }));
          };

          source.connect(worklet);
          worklet.connect(audioCtx.destination);
        } catch (err) {
          setError('Failed to start audio capture');
          setStatus('ended');
          cleanup();
        }
      };

      ws.onmessage = (ev) => {
        try {
          const msg = JSON.parse(ev.data);
          switch (msg.event) {
            case 'started':
              setSecondsRemaining(Number(msg.secondsRemaining) || 0);
              setStatus('active');
              break;
            case 'media': {
              const payload = msg.media?.payload;
              const sr = Number(msg.media?.sampleRate) || 8000;
              if (payload) {
                const bytes = base64ToUint8(payload);
                const pcm = new Int16Array(bytes.buffer, bytes.byteOffset, bytes.byteLength / 2);
                playPcmChunk(pcm, sr);
              }
              break;
            }
            case 'transcript':
              if (msg.isFinal === true || msg.isFinal === 'true') {
                setTranscript((prev) => [...prev, { speaker: 'user', text: msg.text }]);
              }
              break;
            case 'ai_reply':
            case 'ai_reply_chunk':
              setTranscript((prev) => {
                const last = prev[prev.length - 1];
                if (last && last.speaker === 'assistant' && msg.event === 'ai_reply_chunk') {
                  return [...prev.slice(0, -1), { speaker: 'assistant', text: last.text + msg.text }];
                }
                return [...prev, { speaker: 'assistant', text: msg.text }];
              });
              break;
            case 'demo_time_update':
              setSecondsRemaining(Number(msg.secondsRemaining) || 0);
              break;
            case 'demo_exhausted':
              setError('Demo time exhausted. Please subscribe to continue.');
              setStatus('ended');
              cleanup();
              break;
            case 'ended':
              setStatus('ended');
              cleanup();
              break;
            case 'error':
              setError(msg.message || 'Unknown error');
              setStatus('ended');
              cleanup();
              break;
          }
        } catch {
          // ignore parse errors
        }
      };

      ws.onerror = () => {
        setError('Connection failed');
        setStatus('ended');
        cleanup();
      };

      ws.onclose = () => {
        if (status !== 'ended') setStatus('ended');
      };
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Microphone access denied');
      setStatus('idle');
      cleanup();
    }
  }, [cleanup, playPcmChunk, status]);

  const stop = useCallback(() => {
    setStatus('ended');
    cleanup();
  }, [cleanup]);

  return { status, secondsRemaining, transcript, error, start, stop };
}

function uint8ToBase64(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function base64ToUint8(b64: string): Uint8Array {
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}
