import React, { useState } from 'react';
import { X } from 'lucide-react';
import type { PolicyRule, HttpMethod, PolicyAction } from '../types';

const ALL_METHODS: HttpMethod[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'];

interface Props {
  initial?: PolicyRule;
  onSave: (rule: Omit<PolicyRule, 'id'> & { id?: string }) => void;
  onClose: () => void;
}

export function PolicyModal({ initial, onSave, onClose }: Props) {
  const [form, setForm] = useState<Omit<PolicyRule, 'id'> & { id?: string }>({
    id:              initial?.id,
    description:     initial?.description     ?? '',
    methods:         initial?.methods         ?? [],
    endpointPattern: initial?.endpointPattern ?? '.*',
    action:          initial?.action          ?? 'BLOCK',
    priority:        initial?.priority        ?? 100,
    agentGuidance:   initial?.agentGuidance   ?? '',
    safeAlternatives: initial?.safeAlternatives ?? [],
  });

  const [altInput, setAltInput] = useState('');
  const [errors, setErrors]     = useState<Record<string, string>>({});

  function toggleMethod(m: HttpMethod) {
    setForm(f => ({
      ...f,
      methods: f.methods.includes(m) ? f.methods.filter(x => x !== m) : [...f.methods, m],
    }));
  }

  function addAlt() {
    const v = altInput.trim();
    if (!v) return;
    setForm(f => ({ ...f, safeAlternatives: [...f.safeAlternatives, v] }));
    setAltInput('');
  }

  function removeAlt(i: number) {
    setForm(f => ({ ...f, safeAlternatives: f.safeAlternatives.filter((_, idx) => idx !== i) }));
  }

  function validate(): boolean {
    const e: Record<string, string> = {};
    if (!form.description.trim()) e.description = 'Required';
    if (!form.endpointPattern.trim()) e.endpointPattern = 'Required';
    try { new RegExp(form.endpointPattern); } catch { e.endpointPattern = 'Invalid regex'; }
    if (form.priority < 0 || form.priority > 9999) e.priority = 'Must be 0–9999';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (validate()) onSave(form);
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <span className="modal-title">{initial ? 'Edit Policy Rule' : 'New Policy Rule'}</span>
          <button className="btn btn-ghost" style={{ padding: '4px 6px' }} onClick={onClose}><X size={16} /></button>
        </div>
        <form onSubmit={handleSubmit}>
          <div className="modal-body">
            <div className="form-group">
              <label>Description *</label>
              <input value={form.description} onChange={e => setForm(f => ({ ...f, description: e.target.value }))} />
              {errors.description && <span className="field-error">{errors.description}</span>}
            </div>

            <div className="form-group">
              <label>HTTP Methods (empty = any)</label>
              <div className="method-toggles">
                {ALL_METHODS.map(m => (
                  <button key={m} type="button"
                    className={`method-btn${form.methods.includes(m) ? ' active' : ''}`}
                    onClick={() => toggleMethod(m)}>{m}</button>
                ))}
              </div>
            </div>

            <div className="form-group">
              <label>Endpoint Pattern (regex) *</label>
              <input value={form.endpointPattern} onChange={e => setForm(f => ({ ...f, endpointPattern: e.target.value }))} className="font-mono" />
              {errors.endpointPattern && <span className="field-error">{errors.endpointPattern}</span>}
            </div>

            <div className="grid-2">
              <div className="form-group">
                <label>Action *</label>
                <select value={form.action} onChange={e => setForm(f => ({ ...f, action: e.target.value as PolicyAction }))}>
                  {(['ALLOW','BLOCK','QUARANTINE','REQUIRE_APPROVAL'] as PolicyAction[]).map(a => (
                    <option key={a} value={a}>{a}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Priority (lower = higher priority)</label>
                <input type="number" min={0} max={9999} value={form.priority}
                  onChange={e => setForm(f => ({ ...f, priority: Number(e.target.value) }))} />
                {errors.priority && <span className="field-error">{errors.priority}</span>}
              </div>
            </div>

            <div className="form-group">
              <label>Agent Guidance</label>
              <textarea rows={2} value={form.agentGuidance}
                onChange={e => setForm(f => ({ ...f, agentGuidance: e.target.value }))} />
            </div>

            <div className="form-group">
              <label>Safe Alternatives</label>
              <div className="flex gap-8">
                <input value={altInput} onChange={e => setAltInput(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), addAlt())}
                  placeholder="e.g. GET /users/{id}" style={{ flex: 1 }} />
                <button type="button" className="btn btn-ghost" onClick={addAlt}>Add</button>
              </div>
              {form.safeAlternatives.map((a, i) => (
                <div key={i} className="alt-chip">
                  <span className="font-mono">{a}</span>
                  <button type="button" onClick={() => removeAlt(i)}><X size={12} /></button>
                </div>
              ))}
            </div>
          </div>
          <div className="modal-footer">
            <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
            <button type="submit" className="btn btn-primary">Save Rule</button>
          </div>
        </form>
      </div>
    </div>
  );
}
