// Central API config + a small fetch wrapper: attaches the bearer token,
// throws on non-2xx with the server's error message when present, and
// parses JSON (or returns null for 204s). Replace with axios if the rest of
// the codebase already standardizes on it — nothing else here depends on it.
import type {
  ApiErrorBody,
  ApiSuccessEnvelope,
  ApiSuccessPageEnvelope,
  Pagination,
  User
} from '../types/api';

export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || '/api';

const TOKEN_KEY = 'hl_token';
const ROLE_KEY = 'hl_role';
const USER_KEY = 'hl_user';

export type Session = {
  token: string;
  role: string;
  user?: User | null;
};

export type ApiFetchOptions = {
  method?: string;
  body?: unknown;
  params?: Record<string, string | number | boolean | null | undefined>;
  headers?: Record<string, string>;
  skipAuth?: boolean;
};

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}
export function getRole(): string | null {
  return localStorage.getItem(ROLE_KEY);
}
export function getStoredUser(): User | null {
  const raw = localStorage.getItem(USER_KEY);
  // Round-trip of a User this app itself serialized from an API response —
  // one of the two places a cast is unavoidable, since JSON.parse is untyped.
  return raw ? (JSON.parse(raw) as User) : null;
}
export function setSession({ token, role, user }: Session): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(ROLE_KEY, role);
  localStorage.setItem(USER_KEY, JSON.stringify(user || {}));
}
export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(ROLE_KEY);
  localStorage.removeItem(USER_KEY);
}

/**
 * Shown whenever the API could not be reached at all, as opposed to reaching
 * it and being told no.
 *
 * Three different failures land here, and this string is read by all three
 * audiences, so it must not name anything only a developer could act on. In
 * dev, Vite's proxy cannot open a socket to Spring Boot and answers with a
 * plain-text `500 Internal Server Error` of its own making — so the browser
 * sees a 500 the API never produced (the old fallback to `res.statusText`
 * printed exactly that on the form, which reads as a backend bug and sends
 * you looking for one). In production the SPA is served by Spring Boot itself
 * with no proxy in front, so the equivalents are a gateway answering 502/503
 * with an HTML body, and a `fetch` that rejects because the device is offline.
 *
 * A shop owner cannot restart a server or read a socket error, so the only
 * useful thing to say is that the app could not reach it and the attempt is
 * worth repeating. The diagnostic detail belongs where a developer will see
 * it: the dev proxy's own 503 body in `vite.config.js`, and the server logs.
 */
export const OFFLINE_MESSAGE =
  'Cannot reach the server right now. Check your connection and try again in a moment.';

class ApiError extends Error {
  status: number;
  body: ApiErrorBody | string | null;

  constructor(message: string, status: number, body: ApiErrorBody | string | null) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

/**
 * The shared request path: URL building, auth header, error translation and
 * body parsing. Returns the raw payload — envelope and all — so that
 * `apiFetch` can unwrap it to `data` while `apiFetchPage` can keep the
 * `pagination` block beside the rows.
 */
async function request(
  path: string,
  { method = 'GET', body, params, headers, skipAuth }: ApiFetchOptions = {}
): Promise<unknown> {
  const url = new URL(API_BASE_URL + path, window.location.origin);
  if (params) {
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
    });
  }

  const token = !skipAuth && getToken();
  let res: Response;
  try {
    res = await fetch(url.toString().replace(window.location.origin, ''), {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers
      },
      body: body !== undefined ? JSON.stringify(body) : undefined
    });
  } catch {
    // No response at all: DNS, a refused connection, or the tab going offline.
    // Status 0 marks "never reached the server" so callers can tell it apart
    // from a status the API actually chose.
    throw new ApiError(OFFLINE_MESSAGE, 0, null);
  }

  if (res.status === 401 || res.status === 403) {
    // Session expired / not permitted — let callers decide what to render
    // (agent + owner both have a dedicated "session expired" edge state).
    const err = new ApiError('Unauthorized', res.status, null);
    throw err;
  }

  if (res.status === 204) return null;

  const isJson = (res.headers.get('content-type') || '').includes('application/json');
  const payload: unknown = isJson ? await res.json().catch(() => null) : await res.text();

  if (!res.ok) {
    // Every error this API produces carries a JSON body. A 5xx without one did
    // not come from the API — it came from whatever sits in front of it (the
    // dev proxy, or a gateway), which means the backend was unreachable.
    const message =
      (payload && typeof payload === 'object' && 'message' in payload
        ? String((payload as ApiErrorBody).message)
        : null) ||
      (!isJson && res.status >= 500 ? OFFLINE_MESSAGE : null) ||
      res.statusText ||
      'Request failed';
    throw new ApiError(message, res.status, payload as ApiErrorBody | string | null);
  }

  return payload;
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const payload = await request(path, options);

  if (payload && typeof payload === 'object' && 'status' in payload && 'data' in payload) {
    // The success envelope. Note this also flattens ApiSuccessPage, dropping
    // its `pagination` — use apiFetchPage when that block matters.
    return (payload as ApiSuccessEnvelope<T>).data;
  }

  return payload as T;
}

/**
 * Same request, but for the paginated envelope: returns the rows together
 * with the `pagination` block instead of discarding it.
 *
 * Every list endpoint in this API is paged with Spring's defaults, which means
 * 20 rows unless a `size` is asked for. `fetchAllPages` below is what callers
 * actually use; this exists for anything that needs the totals.
 */
export async function apiFetchPage<T>(
  path: string,
  options: ApiFetchOptions = {}
): Promise<{ rows: T[]; pagination: Pagination | null }> {
  const payload = await request(path, options);

  if (payload && typeof payload === 'object' && 'data' in payload) {
    const envelope = payload as ApiSuccessPageEnvelope<T>;
    return {
      rows: Array.isArray(envelope.data) ? envelope.data : [],
      pagination: envelope.pagination ?? null
    };
  }

  return { rows: Array.isArray(payload) ? (payload as T[]) : [], pagination: null };
}

/** Asked for per request. Well under Spring's default 2000 ceiling. */
const PAGE_SIZE = 200;
/** Safety net against an unbounded loop: 10 x 200 = 2000 rows. */
const MAX_PAGES = 10;

/**
 * Walk a paginated endpoint to the end and return every row.
 *
 * The screens that call this render a whole list — the owner's queue and
 * register, the agent's assignments, the agents roster. Without this they
 * showed the first 20 rows and gave no indication the rest existed, because
 * the backend pages with Spring's defaults and nothing here asked for more.
 *
 * If the cap is reached the shortfall is logged rather than passed over in
 * silence; a list that long needs a pagination control in the UI, which is a
 * design decision rather than something to invent here.
 */
export async function fetchAllPages<T>(
  path: string,
  options: ApiFetchOptions = {}
): Promise<T[]> {
  const rows: T[] = [];

  for (let page = 0; page < MAX_PAGES; page += 1) {
    const { rows: batch, pagination } = await apiFetchPage<T>(path, {
      ...options,
      params: { ...options.params, page, size: PAGE_SIZE }
    });
    rows.push(...batch);

    // No pagination block means the endpoint answered with a plain list.
    if (!pagination || !pagination.hasNext) return rows;
  }

  console.warn(
    `[api] ${path}: stopped after ${MAX_PAGES} pages (${rows.length} rows); more rows exist server-side.`
  );
  return rows;
}

export { ApiError };
