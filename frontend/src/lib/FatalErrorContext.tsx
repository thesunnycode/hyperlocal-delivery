import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';

export type FatalError = {
  kind: 'session' | 'fatal';
  reference?: string | null;
};

export type FatalErrorContextValue = {
  error: FatalError | null;
  reportError: (err: unknown) => void;
  clearError: () => void;
};

/**
 * The shape reportError reads off whatever it is handed. It is called with
 * ApiError from apiClient in the normal path, but also with plain Errors, so
 * it stays structural rather than using `instanceof`.
 */
type ErrorLike = {
  status?: number;
  body?: { reference?: string | null } | string | null;
};

const FatalErrorContext = createContext<FatalErrorContextValue | null>(null);

/**
 * Shared by OwnerLayout and AdminLayout (same account, same session) so any
 * page's data-load failure surfaces the right edge state instead of a blank
 * screen: a 401/403 renders the neutral "session expired" pause, anything
 * else renders the accent "something went wrong" screen with a copyable
 * REQ- reference code support can search logs for.
 */
export function FatalErrorProvider({ children }: { children: ReactNode }) {
  const [error, setError] = useState<FatalError | null>(null);

  const reportError = useCallback((err: unknown) => {
    const e = (err ?? {}) as ErrorLike;
    if (e.status === 401 || e.status === 403) {
      setError({ kind: 'session' });
    } else {
      // The server generates and logs this id (see ApiError.reference) so it's
      // genuinely searchable in server logs — never invent one client-side.
      const reference =
        (e.body && typeof e.body === 'object' ? e.body.reference : null) || null;
      setError({ kind: 'fatal', reference });
    }
  }, []);

  const clearError = useCallback(() => setError(null), []);

  return (
    <FatalErrorContext.Provider value={{ error, reportError, clearError }}>
      {children}
    </FatalErrorContext.Provider>
  );
}

export function useFatalError(): FatalErrorContextValue {
  const ctx = useContext(FatalErrorContext);
  if (!ctx) throw new Error('useFatalError must be used within FatalErrorProvider');
  return ctx;
}
