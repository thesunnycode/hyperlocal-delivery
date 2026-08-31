import { apiFetch, API_BASE_URL, getToken } from '../lib/apiClient';
import type {
  AgentPerformanceRow,
  ExportKind,
  OverviewReport,
  RegisterRow,
  RegisterStatusParam,
  TrendReport
} from '../types/api';

export type RegisterParams = {
  status?: RegisterStatusParam;
  agentId?: number | string;
  from?: string;
  to?: string;
};

/**
 * ReportController#register hardcodes PageRequest.of(page, 20) and answers
 * with a bare list — no pagination block to read a `hasNext` off. So a full
 * page means "there may be more", and a short one means the end.
 */
const REGISTER_PAGE_SIZE = 20;
/** Safety net against an unbounded loop: 50 x 20 = 1000 rows. */
const MAX_REGISTER_PAGES = 50;

// Admin reporting — owner-only, entirely read-only. Every mutation stays in
// the operations surfaces; these endpoints never write.
export function getOverview({ range }: { range?: number | string } = {}) {
  return apiFetch<OverviewReport>('/reports/overview', { params: { range } });
}
export function getTrend({ days }: { days?: number | string } = {}) {
  return apiFetch<TrendReport>('/reports/trend', { params: { days } });
}
export function getAgentPerformance({ range }: { range?: number | string } = {}) {
  return apiFetch<AgentPerformanceRow[]>('/reports/agent-performance', { params: { range } });
}
/**
 * The audit register. Walks every page: the endpoint serves 20 rows at a time
 * and the page never asked for a second one, so an audit surface was quietly
 * showing the first 20 shipments and calling it the register.
 */
export async function getRegister({ status, agentId, from, to }: RegisterParams = {}) {
  const rows: RegisterRow[] = [];

  for (let page = 0; page < MAX_REGISTER_PAGES; page += 1) {
    const batch = await apiFetch<RegisterRow[]>('/reports/register', {
      params: { status, agentId, from, to, page }
    });
    const received = batch ?? [];
    rows.push(...received);
    if (received.length < REGISTER_PAGE_SIZE) return rows;
  }

  console.warn(
    `[api] /reports/register: stopped after ${MAX_REGISTER_PAGES} pages (${rows.length} rows); more rows exist server-side.`
  );
  return rows;
}
export async function exportCsv(
  kind: ExportKind,
  params: Record<string, string | number | null | undefined> = {}
): Promise<boolean> {
  // kind: 'agent-performance' | 'register'. This endpoint returns raw CSV
  // (Content-Disposition: attachment), not the JSON envelope apiFetch
  // unwraps, so it fetches directly and streams the response into a
  // browser download rather than going through apiFetch.
  const query = new URLSearchParams(
    Object.entries(params)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => [k, String(v)])
  ).toString();
  const token = getToken();
  const res = await fetch(`${API_BASE_URL}/reports/${kind}/export${query ? `?${query}` : ''}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) return false;

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${kind}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
  return true;
}
