import {
  createContext,
  useContext,
  useMemo,
  useState,
  useCallback,
  type Dispatch,
  type ReactNode,
  type SetStateAction
} from 'react';
import { getRole, getStoredUser, getToken, setSession, clearSession, type Session } from './apiClient';
import { login as loginRequest } from '../api/authApi';
import type { AuthResult, User } from '../types/api';

/**
 * Thrown when the credentials are correct but the account's role does not
 * match the door it signed in at (an agent at /owner/login, or the reverse).
 * Behaviour is identical to the plain Error it replaces — same throw, same
 * message, no session written — it simply carries the actual role so the
 * surface can say "wrong door" instead of "wrong password". A real agent's
 * first-ever login used to be reported as a credential failure.
 */
export type AuthContextValue = {
  token: string | null;
  role: string | null;
  user: User | null;
  isAuthenticated: boolean;
  isOwner: boolean;
  isAgent: boolean;
  login: (email: string, password: string) => Promise<AuthResult>;
  setAuthSession: (session: Session) => void;
  logout: () => void;
  setUser: Dispatch<SetStateAction<User | null>>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(getToken());
  const [role, setRole] = useState<string | null>(getRole());
  const [user, setUser] = useState<User | null>(getStoredUser());

  // No expectedRole guard: there is one sign-in door and the response's role
  // decides the landing page. The old two-page design passed a role here and
  // threw RoleMismatchError when an owner signed in at the agent URL — that
  // whole failure mode is gone with the second page.
  const login = useCallback(async (email: string, password: string) => {
    const data = await loginRequest(email, password);
    setSession({ token: data.token, role: data.role, user: data.user });
    setToken(data.token);
    setRole(data.role);
    setUser(data.user);
    return data;
  }, []);

  /** Store auth state directly from token data (e.g. after OTP verification). */
  const setAuthSession = useCallback(({ token: tk, role: rl, user: usr }: Session) => {
    setSession({ token: tk, role: rl, user: usr });
    setToken(tk);
    setRole(rl);
    setUser(usr ?? null);
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setToken(null);
    setRole(null);
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    token, role, user, isAuthenticated: !!token,
    isOwner: role === 'OWNER', isAgent: role === 'AGENT',
    login, setAuthSession, logout, setUser
  }), [token, role, user, login, setAuthSession, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
