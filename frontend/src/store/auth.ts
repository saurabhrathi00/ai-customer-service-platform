import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { decodeJwt } from '@/lib/utils';

export interface JwtClaims {
  sub: string;       // email
  uid: string;       // businessId
  businessId: string;
  roles?: string[];
  scopes?: string[];
  iat: number;
  exp: number;
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  email: string | null;
  businessId: string | null;
  setTokens: (accessToken: string, refreshToken?: string | null) => void;
  setAccessToken: (accessToken: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      email: null,
      businessId: null,
      setTokens: (accessToken, refreshToken) => {
        const claims = decodeJwt<JwtClaims>(accessToken);
        set({
          accessToken,
          refreshToken: refreshToken ?? get().refreshToken,
          email: claims?.sub ?? null,
          businessId: claims?.businessId ?? claims?.uid ?? null,
        });
      },
      setAccessToken: (accessToken) => {
        const claims = decodeJwt<JwtClaims>(accessToken);
        set({
          accessToken,
          email: claims?.sub ?? get().email,
          businessId: claims?.businessId ?? claims?.uid ?? get().businessId,
        });
      },
      logout: () =>
        set({ accessToken: null, refreshToken: null, email: null, businessId: null }),
    }),
    { name: 'aics-auth' },
  ),
);

export function isAuthenticated() {
  const token = useAuthStore.getState().accessToken;
  if (!token) return false;
  const claims = decodeJwt<JwtClaims>(token);
  if (!claims?.exp) return false;
  return claims.exp * 1000 > Date.now();
}

export function isAdmin() {
  const token = useAuthStore.getState().accessToken;
  if (!token) return false;
  const claims = decodeJwt<JwtClaims>(token);
  return claims?.roles?.includes('ROLE_BUSINESS_ADMIN') ?? false;
}
