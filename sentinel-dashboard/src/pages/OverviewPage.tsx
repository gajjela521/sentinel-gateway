import React from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend,
} from 'recharts';
import { getDashboardStats } from '../api/client';
import { StatCard } from '../components/StatCard';

const VERDICT_COLORS: Record<string, string> = {
  ALLOW:            '#22c55e',
  BLOCK:            '#ef4444',
  REQUIRE_APPROVAL: '#eab308',
  QUARANTINE:       '#f97316',
};

export function OverviewPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['stats'],
    queryFn:  getDashboardStats,
    refetchInterval: 15_000,
  });

  if (isLoading) return <div style={{ padding: 40, textAlign: 'center' }}><div className="spinner" /></div>;
  if (error || !data) return <div className="empty-state">Failed to load stats. Is the Sentinel API reachable?</div>;

  const allowRate = data.totalRequests > 0
    ? Math.round((data.allowedCount / data.totalRequests) * 100)
    : 0;

  const pieData = [
    { name: 'Allowed',  value: data.allowedCount,  fill: VERDICT_COLORS.ALLOW },
    { name: 'Blocked',  value: data.blockedCount,   fill: VERDICT_COLORS.BLOCK },
    { name: 'Flagged',  value: data.flaggedCount,   fill: VERDICT_COLORS.REQUIRE_APPROVAL },
  ].filter(d => d.value > 0);

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Overview</h1>
        <span className="text-dim text-sm">Auto-refreshes every 15 s</span>
      </div>

      <div className="grid-4">
        <StatCard label="Total Requests"   value={data.totalRequests.toLocaleString()} />
        <StatCard label="Allow Rate"       value={`${allowRate}%`}   accent="var(--green)" />
        <StatCard label="Blocked"          value={data.blockedCount.toLocaleString()} accent="var(--red)" />
        <StatCard label="Avg Latency"      value={`${data.avgLatencyMs.toFixed(1)} ms`} />
      </div>

      <div className="grid-2 mt-24">
        <div className="card">
          <div style={{ fontWeight: 600, marginBottom: 16 }}>Decision Distribution</div>
          <ResponsiveContainer width="100%" height={220}>
            <PieChart>
              <Pie data={pieData} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={80} label={({ name, percent }: { name?: string; percent?: number }) => `${name ?? ''} ${((percent ?? 0) * 100).toFixed(0)}%`}>
                {pieData.map((entry, i) => <Cell key={i} fill={entry.fill} />)}
              </Pie>
              <Tooltip contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 8 }} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>

        <div className="card">
          <div style={{ fontWeight: 600, marginBottom: 16 }}>Top Blocked Rules</div>
          {data.topBlockedRules.length === 0
            ? <div className="empty-state" style={{ padding: 40 }}>No blocked rules yet</div>
            : (
              <ResponsiveContainer width="100%" height={220}>
                <BarChart data={data.topBlockedRules} layout="vertical" barSize={14}>
                  <XAxis type="number" tick={{ fill: 'var(--text-dim)', fontSize: 11 }} />
                  <YAxis type="category" dataKey="ruleId" width={140} tick={{ fill: 'var(--text-dim)', fontSize: 11 }} />
                  <Tooltip contentStyle={{ background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 8 }} />
                  <Bar dataKey="count" fill="var(--accent)" radius={[0, 4, 4, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )
          }
        </div>
      </div>
    </div>
  );
}
