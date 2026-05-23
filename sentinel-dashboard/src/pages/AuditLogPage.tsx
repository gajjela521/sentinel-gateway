import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronLeft, ChevronRight, Search, X } from 'lucide-react';
import { listAuditEvents } from '../api/client';
import { VerdictBadge } from '../components/VerdictBadge';
import type { AuditEvent, Verdict } from '../types';

const PAGE_SIZE = 20;

export function AuditLogPage() {
  const [page, setPage]         = useState(0);
  const [verdict, setVerdict]   = useState('');
  const [orgId, setOrgId]       = useState('');
  const [expanded, setExpanded] = useState<string | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ['audit', page, verdict, orgId],
    queryFn: () => listAuditEvents({
      page, size: PAGE_SIZE,
      verdict: verdict || undefined,
      organizationId: orgId || undefined,
    }),
    refetchInterval: 10_000,
    placeholderData: prev => prev,
  });

  function clearFilters() { setVerdict(''); setOrgId(''); setPage(0); }

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Audit Log</h1>
        <span className="text-dim text-sm">Auto-refreshes every 10 s</span>
      </div>

      {/* Filters */}
      <div className="card mb-16" style={{ display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: 180 }}>
          <label className="text-dim text-sm" style={{ display: 'block', marginBottom: 4 }}>Verdict</label>
          <select value={verdict} onChange={e => { setVerdict(e.target.value); setPage(0); }}>
            <option value="">All</option>
            {(['ALLOW','BLOCK','REQUIRE_APPROVAL','QUARANTINE'] as Verdict[]).map(v => (
              <option key={v} value={v}>{v}</option>
            ))}
          </select>
        </div>
        <div style={{ flex: 2, minWidth: 220 }}>
          <label className="text-dim text-sm" style={{ display: 'block', marginBottom: 4 }}>Organization ID</label>
          <div className="flex gap-8 items-center">
            <input value={orgId} onChange={e => { setOrgId(e.target.value); setPage(0); }} placeholder="Filter by org..." />
            <Search size={16} style={{ color: 'var(--text-dim)', flexShrink: 0 }} />
          </div>
        </div>
        {(verdict || orgId) && (
          <button className="btn btn-ghost" onClick={clearFilters}>
            <X size={14} /> Clear
          </button>
        )}
      </div>

      <div className="card">
        {isLoading && !data
          ? <div style={{ padding: 40, textAlign: 'center' }}><div className="spinner" /></div>
          : error || !data
          ? <div className="empty-state">Failed to load audit events.</div>
          : data.content.length === 0
          ? <div className="empty-state">No events match the current filters.</div>
          : (
            <>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Time</th>
                      <th>Org</th>
                      <th>Agent</th>
                      <th>Method</th>
                      <th>Endpoint</th>
                      <th>Verdict</th>
                      <th>Rule</th>
                      <th>Latency</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.content.map((evt: AuditEvent) => (
                      <React.Fragment key={evt.traceId}>
                        <tr style={{ cursor: 'pointer' }}
                          onClick={() => setExpanded(p => p === evt.traceId ? null : evt.traceId)}>
                          <td className="font-mono text-dim">{fmtTime(evt.timestamp)}</td>
                          <td>{evt.organizationId}</td>
                          <td className="font-mono">{evt.agentId ?? '—'}</td>
                          <td><MethodChip method={evt.method} /></td>
                          <td className="font-mono" style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{evt.endpoint}</td>
                          <td><VerdictBadge verdict={evt.verdict} /></td>
                          <td className="font-mono text-sm">{evt.ruleId ?? '—'}</td>
                          <td className="text-dim">{fmtLatency(evt.processingNanos)}</td>
                        </tr>
                        {expanded === evt.traceId && (
                          <tr>
                            <td colSpan={8}>
                              <ExpandedRow evt={evt} />
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="flex items-center gap-12 mt-16" style={{ justifyContent: 'space-between' }}>
                <span className="text-dim text-sm">
                  {data.totalElements.toLocaleString()} events
                  &nbsp;·&nbsp; Page {data.number + 1} of {data.totalPages}
                </span>
                <div className="flex gap-8">
                  <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage(p => p - 1)}>
                    <ChevronLeft size={16} />
                  </button>
                  <button className="btn btn-ghost" disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>
                    <ChevronRight size={16} />
                  </button>
                </div>
              </div>
            </>
          )
        }
      </div>
    </div>
  );
}

function ExpandedRow({ evt }: { evt: AuditEvent }) {
  return (
    <div style={{ background: 'var(--surface2)', borderRadius: 8, padding: 16, margin: '4px 0' }}>
      <div className="grid-2" style={{ gap: 16 }}>
        <div>
          <div className="text-dim text-sm mb-4">Trace ID</div>
          <div className="font-mono">{evt.traceId}</div>
          {evt.executionThreadId && (
            <>
              <div className="text-dim text-sm mt-8 mb-4">Thread ID</div>
              <div className="font-mono">{evt.executionThreadId}</div>
            </>
          )}
          {evt.reason && (
            <>
              <div className="text-dim text-sm mt-8 mb-4">Reason</div>
              <div>{evt.reason}</div>
            </>
          )}
          {evt.decidingLayer && (
            <>
              <div className="text-dim text-sm mt-8 mb-4">Layer</div>
              <div className="font-mono">{evt.decidingLayer}</div>
            </>
          )}
        </div>
        <div>
          <div className="text-dim text-sm mb-4">Scores</div>
          <ScoreBar label="Drift"     value={evt.scores.driftScore} />
          <ScoreBar label="Injection" value={evt.scores.injectionScore} />
          <ScoreBar label="Schema"    value={evt.scores.schemaAnomalyScore} />
          <ScoreBar label="Mutation Wt" value={evt.scores.mutationWeight} />
        </div>
      </div>
    </div>
  );
}

function ScoreBar({ label, value }: { label: string; value: number }) {
  const pct = Math.round(value * 100);
  const color = value >= 0.75 ? 'var(--red)' : value >= 0.5 ? 'var(--yellow)' : 'var(--accent)';
  return (
    <div style={{ marginBottom: 8 }}>
      <div className="flex" style={{ justifyContent: 'space-between', marginBottom: 3 }}>
        <span className="text-dim text-sm">{label}</span>
        <span className="text-sm">{pct}%</span>
      </div>
      <div style={{ background: 'var(--border)', borderRadius: 999, height: 4 }}>
        <div style={{ width: `${pct}%`, background: color, height: '100%', borderRadius: 999, transition: 'width 0.3s' }} />
      </div>
    </div>
  );
}

function MethodChip({ method }: { method: string }) {
  const COLOR: Record<string, string> = {
    GET: '#22c55e', POST: '#6366f1', PUT: '#eab308',
    PATCH: '#f97316', DELETE: '#ef4444', HEAD: '#8b8fa8', OPTIONS: '#8b8fa8',
  };
  return (
    <span style={{
      display: 'inline-block', padding: '1px 6px', borderRadius: 4,
      fontSize: 10, fontWeight: 700, fontFamily: 'monospace',
      background: `${COLOR[method] ?? '#6366f1'}22`,
      color: COLOR[method] ?? '#6366f1',
    }}>{method}</span>
  );
}

function fmtTime(iso: string): string {
  const d = new Date(iso);
  return `${d.toLocaleDateString()} ${d.toLocaleTimeString()}`;
}

function fmtLatency(nanos: number): string {
  if (nanos < 1_000_000) return `${(nanos / 1000).toFixed(0)} µs`;
  return `${(nanos / 1_000_000).toFixed(1)} ms`;
}
