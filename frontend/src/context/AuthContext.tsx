import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiError, fetchCurrentUser, signIn as apiSignIn, signUp as apiSignUp } from '../api/logClient.ts';
import type { AuthSession, User } from '../types.ts';

const TOKEN_KEY = 'log-console.token';

type AuthStatus = 'checking' | 'signed-out' | 'signed-in';

interface AuthContextValue {
  status: AuthStatus;
  user: User | null;
  token: string | null;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string) => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function readStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function storeToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Storage can be unavailable (e.g. blocked site data); the session then lasts until reload.
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(readStoredToken);
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<AuthStatus>(() => (readStoredToken() ? 'checking' : 'signed-out'));

  const signOut = useCallback(() => {
    storeToken(null);
    setToken(null);
    setUser(null);
    setStatus('signed-out');
  }, []);

  // A stored token may have expired or been signed with a rotated secret, so confirm it once on load.
  useEffect(() => {
    if (status !== 'checking' || !token) return;
    const controller = new AbortController();
    fetchCurrentUser(token, controller.signal)
      .then((current) => {
        setUser(current);
        setStatus('signed-in');
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        if (err instanceof ApiError && err.status === 401) {
          signOut();
        } else {
          // Server unreachable: keep the session and let the dashboard surface the error.
          setStatus('signed-in');
        }
      });
    return () => controller.abort();
  }, [status, token, signOut]);

  const startSession = useCallback((session: AuthSession) => {
    storeToken(session.token);
    setToken(session.token);
    setUser(session.user);
    setStatus('signed-in');
  }, []);

  const signIn = useCallback(
    async (email: string, password: string) => startSession(await apiSignIn(email, password)),
    [startSession],
  );

  const signUp = useCallback(
    async (email: string, password: string) => startSession(await apiSignUp(email, password)),
    [startSession],
  );

  const value = useMemo(
    () => ({ status, user, token, signIn, signUp, signOut }),
    [status, user, token, signIn, signUp, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider');
  return context;
}
