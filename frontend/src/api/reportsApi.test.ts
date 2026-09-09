import { describe, it, expect, vi, beforeEach } from 'vitest';
import { exportCsv } from './reportsApi';

// Typed as the mock rather than the global, so the partial Response
// objects below stay valid arguments.
let fetchMock: ReturnType<typeof vi.fn>;

describe('exportCsv', () => {
  beforeEach(() => {
    fetchMock = vi.fn();
    global.fetch = fetchMock as unknown as typeof fetch;
    global.URL.createObjectURL = vi.fn(() => 'blob:mock');
    global.URL.revokeObjectURL = vi.fn();
  });

  it('fetches raw CSV and triggers a download, resolving true on success', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      blob: async () => new Blob(['a,b\n1,2'], { type: 'text/csv' }),
    });
    const clickSpy = vi.fn();
    const origCreateElement = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      const el = origCreateElement(tag);
      if (tag === 'a') el.click = clickSpy;
      return el;
    });

    const result = await exportCsv('register', { status: 'delivered' });

    expect(result).toBe(true);
    expect(clickSpy).toHaveBeenCalled();
  });

  it('resolves false without throwing on a failed export', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 500 });
    const result = await exportCsv('agent-performance', {});
    expect(result).toBe(false);
  });
});
