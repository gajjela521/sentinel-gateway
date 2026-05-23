import React from 'react';
import { RadialBarChart, RadialBar, ResponsiveContainer, Tooltip } from 'recharts';
import type { BudgetSnapshot } from '../types';

const STATE_COLOR: Record<string, string> = {
  CLOSED:    '#22c55e',
  HALF_OPEN: '#eab308',
  OPEN:      '#ef4444',
};

function pct(used: number, limit: number): number {
  if (limit <= 0) return 0;
  return Math.min(100, Math.round((used / limit) * 100));
}

export function BudgetGauge({ snap }: { snap: BudgetSnapshot }) {
  const usdPct   = pct(snap.spentUsd,    snap.limitUsd);
  const mutPct   = pct(snap.mutationCount, snap.mutationLimit);
  const stateColor = STATE_COLOR[snap.state] ?? '#6366f1';

  const data = [
    { name: 'USD',       value: usdPct,  fill: '#6366f1' },
    { name: 'Mutations', value: mutPct,  fill: '#f97316' },
  ];

  return (
    <div className="card" style={{ textAlign: 'center' }}>
      <div style={{ fontWeight: 600, marginBottom: 4, fontSize: 13 }}>
        {snap.executionThreadId}
      </div>
      <span className="badge" style={{ background: `${stateColor}22`, color: stateColor, marginBottom: 8 }}>
        {snap.state}
      </span>
      <ResponsiveContainer width="100%" height={160}>
        <RadialBarChart innerRadius={35} outerRadius={70} data={data} startAngle={180} endAngle={0}>
          <RadialBar dataKey="value" cornerRadius={4} />
          <Tooltip
            contentStyle={{ background: '#1a1d27', border: '1px solid #2d3149', borderRadius: 8 }}
            formatter={(v, name) => [`${v}%`, name]}
          />
        </RadialBarChart>
      </ResponsiveContainer>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, marginTop: 4 }}>
        <Metric label="USD" used={snap.spentUsd.toFixed(4)} limit={snap.limitUsd.toFixed(2)} pct={usdPct} />
        <Metric label="Mutations" used={snap.mutationCount} limit={snap.mutationLimit} pct={mutPct} />
      </div>
    </div>
  );
}

function Metric({ label, used, limit, pct }: { label: string; used: string | number; limit: string | number; pct: number }) {
  return (
    <div style={{ background: 'var(--surface2)', borderRadius: 8, padding: '8px 10px' }}>
      <div className="text-dim text-sm">{label}</div>
      <div style={{ fontWeight: 600, fontSize: 13 }}>{used} / {limit}</div>
      <div style={{ marginTop: 4, background: 'var(--border)', borderRadius: 999, height: 4 }}>
        <div style={{ width: `${pct}%`, background: pct > 80 ? '#ef4444' : '#6366f1', height: '100%', borderRadius: 999, transition: 'width 0.4s' }} />
      </div>
    </div>
  );
}
