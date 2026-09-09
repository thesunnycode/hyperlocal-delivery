import { describe, it, expect, vi, beforeEach } from 'vitest';
import { apiFetch, ApiError, OFFLINE_MESSAGE } from './apiClient';

// Typed as the mock rather than the global, so the partial Response
// objects below stay valid arguments.
let fetchMock: ReturnType<typeof vi.fn>;

describe('apiFetch envelope unwrap', () => {
  beforeEach(() => {
    fetchMock = vi.fn();
    global.fetch = fetchMock as unknown as typeof fetch;
  });

  it('unwraps {status:"success", data} to just data', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      headers: { get: () => 'application/json' },
      json: async () => ({ status: 'success', data: { id: 1, name: 'Ada' } }),
    });
    const result = await apiFetch('/agents/1');
    expect(result).toEqual({ id: 1, name: 'Ada' });
  });

  it('throws ApiError from {status:"error", code, message} on non-2xx', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 422,
      headers: { get: () => 'application/json' },
      json: async () => ({ status: 'error', code: 'INVALID_STATE', message: 'Cannot advance from DELIVERED' }),
    });
    await expect(apiFetch('/shipments/1/deliver', { method: 'POST' })).rejects.toMatchObject({
      status: 422,
      message: 'Cannot advance from DELIVERED',
    });
    await expect(apiFetch('/shipments/1/deliver', { method: 'POST' })).rejects.toBeInstanceOf(ApiError);
  });

  it('still throws a bodyless ApiError on 401/403 (session-expired path)', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 401, json: async () => { throw new Error('no body'); } });
    await expect(apiFetch('/shipments')).rejects.toMatchObject({ status: 401, body: null });
  });
});

describe('apiFetch unreachable-backend path', () => {
  beforeEach(() => {
    fetchMock = vi.fn();
    global.fetch = fetchMock as unknown as typeof fetch;
  });

  // The dev proxy answers with a plain-text 500 when it cannot open a socket
  // to Spring Boot. Reporting that as "Internal Server Error" sent a real
  // debugging session hunting a server bug that did not exist.
  it('reports a non-JSON 5xx as an unreachable backend, not "Internal Server Error"', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
      headers: { get: () => 'text/plain' },
      text: async () => 'Internal Server Error',
    });
    await expect(apiFetch('/auth/register', { method: 'POST' })).rejects.toMatchObject({
      status: 500,
      message: OFFLINE_MESSAGE,
    });
  });

  // The same condition without a proxy in front (built SPA, or a proxy that
  // drops the connection outright): fetch itself rejects.
  it('translates a fetch-level network failure into the same message', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(apiFetch('/auth/register', { method: 'POST' })).rejects.toMatchObject({
      status: 0,
      message: OFFLINE_MESSAGE,
    });
  });

  // A genuine 500 from Spring Boot still speaks for itself.
  it('leaves a JSON 500 from the API untouched', async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
      headers: { get: () => 'application/json' },
      json: async () => ({ status: 'error', code: 'EMAIL_DELIVERY_FAILED', message: 'Email delivery failed. Please try again later.' }),
    });
    await expect(apiFetch('/auth/register', { method: 'POST' })).rejects.toMatchObject({
      status: 500,
      message: 'Email delivery failed. Please try again later.',
    });
  });
});

// The same constant is shown to a deployed shop owner as to a developer with
// a stopped Spring Boot, because production serves this SPA from Spring Boot
// itself and a gateway 502 lands on the identical branch. It must not tell
// that owner to check a port or restart a process.
describe('OFFLINE_MESSAGE is safe to show a deployed user', () => {
  it('names nothing only a developer could act on', () => {
    for (const leak of ['localhost', '8080', 'backend', 'Spring', 'ECONNREFUSED', 'proxy']) {
      expect(OFFLINE_MESSAGE.toLowerCase()).not.toContain(leak.toLowerCase());
    }
  });

  it('still says what happened and that retrying is worth it', () => {
    expect(OFFLINE_MESSAGE).toMatch(/server/i);
    expect(OFFLINE_MESSAGE).toMatch(/try again/i);
  });
});
