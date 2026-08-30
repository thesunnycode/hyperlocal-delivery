import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './lib/AuthContext.tsx';
import { ToastProvider } from './lib/ToastContext.tsx';
import { FatalErrorProvider } from './lib/FatalErrorContext.tsx';
import ProtectedRoute from './components/ProtectedRoute.tsx';
import RedirectIfAuthenticated from './components/RedirectIfAuthenticated.tsx';

import CustomerTrackingPage from './pages/customer/CustomerTrackingPage.tsx';

import LoginPage from './pages/LoginPage.tsx';
import RegisterPage from './pages/RegisterPage.tsx';
import ForgotPasswordPage from './pages/ForgotPasswordPage.tsx';

import AgentAssignmentsPage from './pages/agent/AgentAssignmentsPage.tsx';
import AgentShipmentDetailPage from './pages/agent/AgentShipmentDetailPage.tsx';
import AgentAccountPage from './pages/agent/AgentAccountPage.tsx';

import OwnerLayout from './layouts/OwnerLayout.tsx';
import OwnerShipmentsPage from './pages/owner/OwnerShipmentsPage.tsx';
import OwnerRegisterPage from './pages/owner/OwnerRegisterPage.tsx';
import OwnerAgentsPage from './pages/owner/OwnerAgentsPage.tsx';
import OwnerAccountPage from './pages/owner/OwnerAccountPage.tsx';

import ReportingFrame from './layouts/ReportingFrame.tsx';
import AdminOverviewPage from './pages/admin/AdminOverviewPage.tsx';
import AdminTrendPage from './pages/admin/AdminTrendPage.tsx';
import AdminAgentPerformancePage from './pages/admin/AdminAgentPerformancePage.tsx';
import AdminNotFoundPage from './pages/admin/AdminNotFoundPage.tsx';

import NotFoundPage from './pages/NotFoundPage.tsx';

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
      <FatalErrorProvider>
        <Routes>
          <Route path="/" element={
            <RedirectIfAuthenticated fallback={<Navigate to="/login" replace />} />
          } />

          {/* One sign-in for both roles; the auth response decides the landing
              page. /agent/login and /owner/login are kept as redirects so links
              already in the wild (and emails) still resolve. */}
          <Route path="/login" element={
            <RedirectIfAuthenticated><LoginPage /></RedirectIfAuthenticated>
          } />
          <Route path="/register" element={
            <RedirectIfAuthenticated><RegisterPage /></RedirectIfAuthenticated>
          } />
          {/* One reset flow for both roles — a reset does not care who you are,
              and the old split forced the user to know which URL applied. */}
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          {/* Same three steps, framed as a first sign-in. An agent's account is
              created for them with a password nobody ever sees, so setting one
              IS the reset flow. */}
          <Route path="/agent-setup" element={<ForgotPasswordPage mode="setup" />} />

          {/* Customer — public, no auth */}
          <Route path="/track/:token" element={<CustomerTrackingPage />} />

          {/* Agent */}
          <Route path="/agent/login" element={<Navigate to="/login" replace />} />
          <Route path="/agent/forgot-password" element={<Navigate to="/forgot-password" replace />} />
          <Route path="/agent/reset-password" element={<Navigate to="/forgot-password" replace />} />
          <Route path="/agent/assignments" element={<ProtectedRoute role="AGENT"><AgentAssignmentsPage /></ProtectedRoute>} />
          <Route path="/agent/shipments/:id" element={<ProtectedRoute role="AGENT"><AgentShipmentDetailPage /></ProtectedRoute>} />
          <Route path="/agent/account" element={<ProtectedRoute role="AGENT"><AgentAccountPage /></ProtectedRoute>} />

          {/* Owner — one console. Operations and reporting used to be two
              shells with two sidebars, two user footers and two pages called
              "Register", for the same signed-in person. They are one app now:
              reporting lives under /owner/reports/* inside the same layout,
              and the old /admin/* URLs redirect so existing links resolve. */}
          <Route path="/owner/login" element={<Navigate to="/login" replace />} />
          <Route path="/owner/forgot-password" element={<Navigate to="/forgot-password" replace />} />
          <Route path="/owner/reset-password" element={<Navigate to="/forgot-password" replace />} />
          <Route element={<ProtectedRoute role="OWNER"><OwnerLayout /></ProtectedRoute>}>
            <Route path="/owner/shipments" element={<OwnerShipmentsPage />} />
            <Route path="/owner/shipments/:id" element={<OwnerShipmentsPage />} />
            <Route path="/owner/register" element={<OwnerRegisterPage />} />
            <Route path="/owner/agents" element={<OwnerAgentsPage />} />
            <Route path="/owner/agents/:id" element={<OwnerAgentsPage />} />
            <Route path="/owner/account" element={<OwnerAccountPage />} />

            {/* Reporting — read-only, same shell, same menu. */}
            <Route path="/owner/reports" element={<ReportingFrame />}>
              <Route index element={<Navigate to="overview" replace />} />
              <Route path="overview" element={<AdminOverviewPage />} />
              <Route path="trend" element={<AdminTrendPage />} />
              <Route path="agents" element={<AdminAgentPerformancePage />} />
              <Route path="*" element={<AdminNotFoundPage />} />
            </Route>
          </Route>

          {/* The reporting section used to live here behind its own shell. */}
          <Route path="/admin" element={<Navigate to="/owner/reports/overview" replace />} />
          <Route path="/admin/overview" element={<Navigate to="/owner/reports/overview" replace />} />
          <Route path="/admin/trend" element={<Navigate to="/owner/reports/trend" replace />} />
          <Route path="/admin/agents" element={<Navigate to="/owner/reports/agents" replace />} />
          {/* The audit register was a second, thinner copy of /owner/register.
              That page now carries the CSV export the audit view had. */}
          <Route path="/admin/register" element={<Navigate to="/owner/register" replace />} />
          <Route path="/admin/*" element={<Navigate to="/owner/reports/overview" replace />} />

          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </FatalErrorProvider>
      </ToastProvider>
    </AuthProvider>
  );
}
