import axios from 'axios';
import type { PolicyRule, AuditEvent, BudgetSnapshot, DashboardStats } from '../types';

const BASE = process.env.REACT_APP_API_BASE ?? 'http://localhost:8080';

const http = axios.create({ baseURL: BASE, timeout: 10_000 });

// ── Policy API ────────────────────────────────────────────────────────────────

export async function listPolicies(): Promise<PolicyRule[]> {
  const { data } = await http.get<PolicyRule[]>('/sentinel/policies');
  return data;
}

export async function createPolicy(rule: Omit<PolicyRule, 'id'>): Promise<PolicyRule> {
  const { data } = await http.post<PolicyRule>('/sentinel/policies', rule);
  return data;
}

export async function updatePolicy(id: string, rule: PolicyRule): Promise<PolicyRule> {
  const { data } = await http.put<PolicyRule>(`/sentinel/policies/${id}`, rule);
  return data;
}

export async function deletePolicy(id: string): Promise<void> {
  await http.delete(`/sentinel/policies/${id}`);
}

// ── Audit Log API ─────────────────────────────────────────────────────────────

export interface AuditQuery {
  page?: number;
  size?: number;
  verdict?: string;
  organizationId?: string;
  from?: string;
  to?: string;
}

export interface AuditPage {
  content: AuditEvent[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export async function listAuditEvents(q: AuditQuery = {}): Promise<AuditPage> {
  const { data } = await http.get<AuditPage>('/sentinel/audit', { params: q });
  return data;
}

// ── Budget API ────────────────────────────────────────────────────────────────

export async function listBudgets(): Promise<BudgetSnapshot[]> {
  const { data } = await http.get<BudgetSnapshot[]>('/sentinel/budgets');
  return data;
}

// ── Stats API ─────────────────────────────────────────────────────────────────

export async function getDashboardStats(): Promise<DashboardStats> {
  const { data } = await http.get<DashboardStats>('/sentinel/stats');
  return data;
}
