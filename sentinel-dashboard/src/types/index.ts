export type Verdict = 'ALLOW' | 'BLOCK' | 'QUARANTINE' | 'REQUIRE_APPROVAL';
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS';
export type PolicyAction = 'ALLOW' | 'BLOCK' | 'QUARANTINE' | 'REQUIRE_APPROVAL';

export interface PolicyRule {
  id: string;
  description: string;
  methods: HttpMethod[];
  endpointPattern: string;
  action: PolicyAction;
  priority: number;
  agentGuidance: string;
  safeAlternatives: string[];
}

export interface AuditEvent {
  traceId: string;
  requestId: string;
  agentId: string | null;
  organizationId: string;
  executionThreadId: string | null;
  method: HttpMethod;
  endpoint: string;
  verdict: Verdict;
  decidingLayer: string | null;
  ruleId: string | null;
  reason: string | null;
  timestamp: string;
  processingNanos: number;
  scores: {
    driftScore: number;
    injectionScore: number;
    schemaAnomalyScore: number;
    mutationWeight: number;
  };
}

export interface BudgetSnapshot {
  executionThreadId: string;
  state: 'CLOSED' | 'OPEN' | 'HALF_OPEN';
  spentUsd: number;
  spentTokens: number;
  mutationCount: number;
  limitUsd: number;
  limitTokens: number;
  mutationLimit: number;
}

export interface DashboardStats {
  totalRequests: number;
  allowedCount: number;
  blockedCount: number;
  flaggedCount: number;
  avgLatencyMs: number;
  topBlockedRules: Array<{ ruleId: string; count: number }>;
}
