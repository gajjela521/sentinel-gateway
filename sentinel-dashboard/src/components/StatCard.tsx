import React from 'react';

interface Props {
  label: string;
  value: string | number;
  sub?: string;
  accent?: string;
}

export function StatCard({ label, value, sub, accent }: Props) {
  return (
    <div className="card">
      <div className="text-dim text-sm">{label}</div>
      <div style={{ fontSize: 28, fontWeight: 700, marginTop: 4, color: accent ?? 'inherit' }}>
        {value}
      </div>
      {sub && <div className="text-dim text-sm mt-4">{sub}</div>}
    </div>
  );
}
