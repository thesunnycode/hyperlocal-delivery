import { apiFetch, fetchAllPages } from '../lib/apiClient';
import type { Agent, AgentInvite, AgentSummary, CreateAgentBody, UpdateAgentBody } from '../types/api';

// NOTE: the list and the single-agent endpoints return DIFFERENT shapes.
// GET /agents is ApiSuccessPage<AgentSummaryDto> — no deliveredCount,
// failedCount or updatedAt, and its join timestamp is `createdAt`. Everything
// else here returns the fuller AgentResponse, whose equivalent is `joinedAt`.
// Paged server-side like every list here, so it walks to the end: the roster,
// the reassign modal's radio list and the register's agent filter all mean the
// whole set, and a plain call stopped at Spring's default 20.
export function listAgents({ active }: { active?: boolean } = {}) {
  return fetchAllPages<AgentSummary>('/agents', { params: { active } });
}
export function getAgent(id: number | string) {
  return apiFetch<Agent>(`/agents/${id}`);
}
export function createAgent({ name, email, phone }: CreateAgentBody) {
  return apiFetch<Agent>('/agents', { method: 'POST', body: { name, email, phone } });
}
export function updateAgent(id: number | string, { name, phone }: UpdateAgentBody) {
  return apiFetch<Agent>(`/agents/${id}`, { method: 'PUT', body: { name, phone } });
}
// Soft delete — history and past shipments stay intact; auto-assign stops
// picking the agent up until reactivated.
export function deactivateAgent(id: number | string) {
  return apiFetch<Agent>(`/agents/${id}/deactivate`, { method: 'POST' });
}
export function reactivateAgent(id: number | string) {
  return apiFetch<Agent>(`/agents/${id}/reactivate`, { method: 'POST' });
}

/**
 * `POST /agents/:id/invite` — mint a single-use link that lets the agent set
 * their own password. Re-issuable; each new link retires the previous one.
 */
export function inviteAgent(id: number | string) {
  return apiFetch<AgentInvite>(`/agents/${id}/invite`, { method: 'POST' });
}
