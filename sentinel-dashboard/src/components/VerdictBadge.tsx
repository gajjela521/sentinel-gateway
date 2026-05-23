import React from 'react';
import type { Verdict } from '../types';

const CLASS: Record<Verdict, string> = {
  ALLOW:            'badge badge-allow',
  BLOCK:            'badge badge-block',
  REQUIRE_APPROVAL: 'badge badge-approve',
  QUARANTINE:       'badge badge-quarantine',
};

const LABEL: Record<Verdict, string> = {
  ALLOW:            'Allow',
  BLOCK:            'Block',
  REQUIRE_APPROVAL: 'Review',
  QUARANTINE:       'Quarantine',
};

export function VerdictBadge({ verdict }: { verdict: Verdict }) {
  return <span className={CLASS[verdict]}>{LABEL[verdict]}</span>;
}
