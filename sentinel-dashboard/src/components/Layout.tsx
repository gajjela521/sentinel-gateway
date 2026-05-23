import React from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { ShieldCheck, ScrollText, Gauge, Activity } from 'lucide-react';
import './Layout.css';

const nav = [
  { to: '/',        icon: <Activity size={18} />,    label: 'Overview'       },
  { to: '/policies', icon: <ShieldCheck size={18} />, label: 'Policy Editor'  },
  { to: '/audit',   icon: <ScrollText size={18} />,  label: 'Audit Log'      },
  { to: '/budgets', icon: <Gauge size={18} />,        label: 'Budget Gauges'  },
];

export function Layout() {
  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <ShieldCheck size={24} className="brand-icon" />
          <span>Sentinel Gateway</span>
        </div>
        <nav className="sidebar-nav">
          {nav.map(({ to, icon, label }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
            >
              {icon}
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">v0.1.0-SNAPSHOT</div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
