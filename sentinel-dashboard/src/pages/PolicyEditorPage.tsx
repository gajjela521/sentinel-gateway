import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Pencil, Trash2, ShieldCheck } from 'lucide-react';
import { listPolicies, createPolicy, updatePolicy, deletePolicy } from '../api/client';
import { PolicyModal } from '../components/PolicyModal';
import type { HttpMethod } from '../types';
import { VerdictBadge } from '../components/VerdictBadge';
import type { PolicyRule } from '../types';
import '../components/PolicyModal.css';

export function PolicyEditorPage() {
  const qc = useQueryClient();
  const { data: rules = [], isLoading } = useQuery({ queryKey: ['policies'], queryFn: listPolicies });
  const [editing, setEditing] = useState<PolicyRule | null | 'new'>(null);
  const [deleting, setDeleting] = useState<string | null>(null);

  const saveMutation = useMutation({
    mutationFn: (rule: Omit<PolicyRule, 'id'> & { id?: string }) =>
      rule.id ? updatePolicy(rule.id, rule as PolicyRule) : createPolicy(rule),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['policies'] }); setEditing(null); },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deletePolicy(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['policies'] }); setDeleting(null); },
  });

  return (
    <div>
      <div className="page-header">
        <h1 className="page-title">Policy Editor</h1>
        <button className="btn btn-primary" onClick={() => setEditing('new')}>
          <Plus size={16} /> New Rule
        </button>
      </div>

      {saveMutation.isError && (
        <div className="error-banner">Failed to save rule. Check the API connection.</div>
      )}

      <div className="card">
        {isLoading
          ? <div style={{ padding: 40, textAlign: 'center' }}><div className="spinner" /></div>
          : rules.length === 0
          ? <div className="empty-state">
              <ShieldCheck size={48} style={{ margin: '0 auto 12px', display: 'block', opacity: 0.3 }} />
              No policy rules configured. Add your first rule to start enforcing API safety.
            </div>
          : (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Priority</th>
                    <th>Rule ID</th>
                    <th>Description</th>
                    <th>Methods</th>
                    <th>Pattern</th>
                    <th>Action</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {[...rules].sort((a, b) => a.priority - b.priority).map(rule => (
                    <tr key={rule.id}>
                      <td style={{ color: 'var(--text-dim)' }}>{rule.priority}</td>
                      <td><span className="font-mono">{rule.id}</span></td>
                      <td>{rule.description}</td>
                      <td>
                        {rule.methods.length === 0
                          ? <span className="text-dim">ANY</span>
                          : rule.methods.map((m: HttpMethod) => <MethodChip key={m} method={m} />)
                        }
                      </td>
                      <td><span className="font-mono" style={{ fontSize: 11 }}>{rule.endpointPattern}</span></td>
                      <td><VerdictBadge verdict={rule.action as any} /></td>
                      <td>
                        <div className="flex gap-8" style={{ justifyContent: 'flex-end' }}>
                          <button className="btn btn-ghost" style={{ padding: '4px 8px' }}
                            onClick={() => setEditing(rule)}>
                            <Pencil size={14} />
                          </button>
                          <button className="btn btn-danger" style={{ padding: '4px 8px' }}
                            onClick={() => setDeleting(rule.id)}>
                            <Trash2 size={14} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        }
      </div>

      {editing !== null && (
        <PolicyModal
          initial={editing === 'new' ? undefined : editing}
          onSave={saveMutation.mutate}
          onClose={() => setEditing(null)}
        />
      )}

      {deleting && (
        <div className="modal-overlay" onClick={() => setDeleting(null)}>
          <div className="modal" style={{ width: 400 }} onClick={e => e.stopPropagation()}>
            <div className="modal-header"><span className="modal-title">Delete Rule</span></div>
            <div className="modal-body">
              Are you sure you want to delete rule <strong className="font-mono">{deleting}</strong>?
              This takes effect immediately on all new requests.
            </div>
            <div className="modal-footer">
              <button className="btn btn-ghost" onClick={() => setDeleting(null)}>Cancel</button>
              <button className="btn btn-danger" disabled={deleteMutation.isPending}
                onClick={() => deleteMutation.mutate(deleting)}>
                {deleteMutation.isPending ? <span className="spinner" /> : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
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
      display: 'inline-block', marginRight: 4, padding: '1px 6px',
      borderRadius: 4, fontSize: 10, fontWeight: 700, fontFamily: 'monospace',
      background: `${COLOR[method] ?? '#6366f1'}22`,
      color: COLOR[method] ?? '#6366f1',
    }}>{method}</span>
  );
}
