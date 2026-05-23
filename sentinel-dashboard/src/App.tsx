import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Layout } from './components/Layout';
import { OverviewPage } from './pages/OverviewPage';
import { PolicyEditorPage } from './pages/PolicyEditorPage';
import { AuditLogPage } from './pages/AuditLogPage';
import { BudgetPage } from './pages/BudgetPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 2,
      staleTime: 5_000,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Layout />}>
            <Route index element={<OverviewPage />} />
            <Route path="policies" element={<PolicyEditorPage />} />
            <Route path="audit"    element={<AuditLogPage />} />
            <Route path="budgets"  element={<BudgetPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
