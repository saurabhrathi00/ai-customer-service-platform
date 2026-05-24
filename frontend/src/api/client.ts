import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import { toast } from 'sonner';
import { useAuthStore } from '@/store/auth';

/** Per-service axios instance. All paths are RELATIVE to the service —
 *  the Vite dev proxy routes them in dev; in prod they hit Caddy. */
function makeClient(prefix: string): AxiosInstance {
  const c = axios.create({ baseURL: prefix, timeout: 15_000 });
  c.interceptors.request.use((cfg) => attachAuth(cfg));
  c.interceptors.response.use(
    (r) => r,
    async (err: AxiosError) => handle401(err, c),
  );
  return c;
}

function attachAuth(cfg: InternalAxiosRequestConfig) {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    cfg.headers = cfg.headers ?? {};
    cfg.headers.Authorization = `Bearer ${token}`;
  }
  return cfg;
}

let refreshing: Promise<string | null> | null = null;

async function handle401(err: AxiosError, client: AxiosInstance) {
  const original = err.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
  if (err.response?.status !== 401 || !original || original._retried) {
    surface(err);
    return Promise.reject(err);
  }
  // Skip refresh on the auth endpoints themselves
  if (original.url?.includes('/auth/')) {
    surface(err);
    return Promise.reject(err);
  }
  original._retried = true;
  refreshing = refreshing ?? doRefresh();
  const newToken = await refreshing;
  refreshing = null;
  if (!newToken) {
    useAuthStore.getState().logout();
    window.location.assign('/login');
    return Promise.reject(err);
  }
  original.headers = original.headers ?? {};
  original.headers.Authorization = `Bearer ${newToken}`;
  return client.request(original);
}

async function doRefresh(): Promise<string | null> {
  const rt = useAuthStore.getState().refreshToken;
  if (!rt) return null;
  try {
    const { data } = await axios.post<{ accessToken: string }>(
      '/api/auth/api/v1/auth/refresh',
      { refreshToken: rt },
    );
    useAuthStore.getState().setAccessToken(data.accessToken);
    return data.accessToken;
  } catch {
    return null;
  }
}

function surface(err: AxiosError) {
  const status = err.response?.status;
  // Don't toast on 401 — handled by redirect; don't toast on cancelled requests
  if (status === 401 || err.code === 'ERR_CANCELED') return;
  const body = err.response?.data as { message?: string } | undefined;
  const message = body?.message ?? err.message ?? 'Unexpected error';
  // Avoid spamming on background queries that fail repeatedly
  toast.error(message, { id: `${status}-${err.config?.url}` });
}

export const authApi      = makeClient('/api/auth/api/v1');
export const businessApi  = makeClient('/api/business/api/v1');
export const knowledgeApi = makeClient('/api/knowledge/api/v1');
export const callsApi     = makeClient('/api/calls/api/v1');
export const summaryApi   = makeClient('/api/summary/api/v1');
