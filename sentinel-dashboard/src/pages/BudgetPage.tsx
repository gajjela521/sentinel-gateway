import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Gauge, RefreshCw } from 'lucide-react';
import { listBudgets } from '../api/client';
import { BudgetGauge } from '../components/BudgetGauge';
import type { BudgetSnapshot } from '../types';

export function BudgetPage() {
  const { data: snaps = [], isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ['budgets'],
    queryFn: listBudgets,
    refetchInterval: 5_000,
  });

  const open     = snaps.filter((s: BudgetSnapshot) => s.state === 'OPEN').length;
  const halfOpen = snaps.filter((s: BudgetSnapshot) => s.state === 'HALF_OPEN').length;

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Budget Gauges</h1>
        <button className="btn btn-ghost" onClick={() => refetch()} disabled={isFetching}>
          <RefreshCw size={14} className={isFetching ? 'spin' : ''} /> Refresh
        </button>
      </div>

      {(open > 0 || halfOpen > 0) && (
        <div className="alert-banner">
          {open > 0 && <span>⚠ {open} breaker{open > 1 ? 's' : ''} OPEN — all requests blocked for those threads.</span>}
          {halfOpen > 0 && <span>🔄 {halfOpen} breaker{halfOpen > 1 ? 's' : ''} in HALF-OPEN — probing recovery.</span>}
        </div>
      )}

      {isLoading
        ? <div style={{ padding: 60, textAlign: 'center' }}><div className="spinner" /></div>
        : error
        ? <div className="empty-state">Failed to load budget data.</div>
        : snaps.length === 0
        ? <div className="empty-state">
            <Gauge size={48} style={{ margin: '0 auto 12px', display: 'block', opacity: 0.3 }} />
            No execution threads are being tracked yet.
            <br /><span className="text-sm text-dim">Budgets appear once an agent makes its first request with an executionThreadId.</span>
          </div>
        : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 16 }}>
            {[...snaps]
              .sort((a: BudgetSnapshot, b: BudgetSnapshot) => {
                const order: Record<string, number> = { OPEN: 0, HALF_OPEN: 1, CLOSED: 2 };
                return (order[a.state] ?? 3) - (order[b.state] ?? 3);
              })
              .map(snap => <BudgetGauge key={snap.executionThreadId} snap={snap} />)
            }
          </div>
        )
      }
    </div>
  );
}
