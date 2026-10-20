import { lazy, Suspense } from "react";
import { Navigate, createBrowserRouter } from "react-router-dom";

import { LoginPage } from "@/pages/LoginPage";
const EngineGate = lazy(() =>
  import("@/components/common/EngineGate").then((m) => ({ default: m.EngineGate }))
);
const ChangeLogsPage = lazy(() =>
  import("@/pages/ChangeLogsPage").then((m) => ({ default: m.ChangeLogsPage }))
);
const DocPreviewPage = lazy(() =>
  import("@/pages/DocPreviewPage").then((m) => ({ default: m.DocPreviewPage }))
);
const NotFoundPage = lazy(() =>
  import("@/pages/NotFoundPage").then((m) => ({ default: m.NotFoundPage }))
);
const AdminLayout = lazy(() =>
  import("@/pages/admin/AdminLayout").then((m) => ({ default: m.AdminLayout }))
);
const IncidentWorkbenchPage = lazy(() =>
  import("@/pages/admin/workbench/IncidentWorkbenchPage").then((m) => ({
    default: m.IncidentWorkbenchPage
  }))
);
const IncidentReviewPage = lazy(() =>
  import("@/pages/admin/incidents/IncidentReviewPage").then((m) => ({
    default: m.IncidentReviewPage
  }))
);
const RunbookCenterPage = lazy(() =>
  import("@/pages/admin/runbooks/RunbookCenterPage").then((m) => ({ default: m.RunbookCenterPage }))
);
const IncidentListPage = lazy(() =>
  import("@/pages/admin/incidents/IncidentListPage").then((m) => ({ default: m.IncidentListPage }))
);
const IncidentDetailPage = lazy(() =>
  import("@/pages/admin/incidents/IncidentDetailPage").then((m) => ({
    default: m.IncidentDetailPage
  }))
);
const DashboardPage = lazy(() =>
  import("@/pages/admin/dashboard/DashboardPage").then((m) => ({ default: m.DashboardPage }))
);
const KnowledgeListPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeListPage").then((m) => ({
    default: m.KnowledgeListPage
  }))
);
const KnowledgeDocumentsPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeDocumentsPage").then((m) => ({
    default: m.KnowledgeDocumentsPage
  }))
);
const KnowledgeChunksPage = lazy(() =>
  import("@/pages/admin/knowledge/KnowledgeChunksPage").then((m) => ({
    default: m.KnowledgeChunksPage
  }))
);
const KnowledgeGraphPage = lazy(() =>
  import("@/pages/admin/knowledge-graph/KnowledgeGraphPage").then((m) => ({
    default: m.KnowledgeGraphPage
  }))
);
const BizChangeLogPage = lazy(() =>
  import("@/pages/admin/change-logs/BizChangeLogPage").then((m) => ({
    default: m.BizChangeLogPage
  }))
);
const IntentTreePage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentTreePage").then((m) => ({ default: m.IntentTreePage }))
);
const IntentListPage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentListPage").then((m) => ({ default: m.IntentListPage }))
);
const IntentEditPage = lazy(() =>
  import("@/pages/admin/intent-tree/IntentEditPage").then((m) => ({ default: m.IntentEditPage }))
);
const IngestionPage = lazy(() =>
  import("@/pages/admin/ingestion/IngestionPage").then((m) => ({ default: m.IngestionPage }))
);
const RagTracePage = lazy(() =>
  import("@/pages/admin/traces/RagTracePage").then((m) => ({ default: m.RagTracePage }))
);
const RagTraceDetailPage = lazy(() =>
  import("@/pages/admin/traces/RagTraceDetailPage").then((m) => ({ default: m.RagTraceDetailPage }))
);
const SystemSettingsPage = lazy(() =>
  import("@/pages/admin/settings/SystemSettingsPage").then((m) => ({
    default: m.SystemSettingsPage
  }))
);
const SampleQuestionPage = lazy(() =>
  import("@/pages/admin/sample-questions/SampleQuestionPage").then((m) => ({
    default: m.SampleQuestionPage
  }))
);
const QueryTermMappingPage = lazy(() =>
  import("@/pages/admin/query-term-mapping/QueryTermMappingPage").then((m) => ({
    default: m.QueryTermMappingPage
  }))
);
const AgentProfilePage = lazy(() =>
  import("@/pages/admin/agents/AgentProfilePage").then((m) => ({ default: m.AgentProfilePage }))
);
const AgentPromptPage = lazy(() =>
  import("@/pages/admin/agents/AgentPromptPage").then((m) => ({ default: m.AgentPromptPage }))
);
const AgentSkillPage = lazy(() =>
  import("@/pages/admin/agent-skills/AgentSkillPage").then((m) => ({ default: m.AgentSkillPage }))
);
const AgentSkillEditPage = lazy(() =>
  import("@/pages/admin/agent-skills/AgentSkillEditPage").then((m) => ({
    default: m.AgentSkillEditPage
  }))
);
const UserListPage = lazy(() =>
  import("@/pages/admin/users/UserListPage").then((m) => ({ default: m.UserListPage }))
);
import { useAuthStore } from "@/stores/authStore";

function RequireAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return (
    <Suspense
      fallback={
        <p role="status" className="p-6 text-center">
          正在加载页面…
        </p>
      }
    >
      {children}
    </Suspense>
  );
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role !== "admin") {
    return <Navigate to="/chat" replace />;
  }

  return (
    <Suspense
      fallback={
        <p role="status" className="p-6 text-center">
          正在加载页面…
        </p>
      }
    >
      {children}
    </Suspense>
  );
}

function RedirectIfAuth({ children }: { children: JSX.Element }) {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (isAuthenticated) {
    return <Navigate to={user?.role === "admin" ? "/admin/workbench" : "/chat"} replace />;
  }
  return (
    <Suspense
      fallback={
        <p role="status" className="p-6 text-center">
          正在加载页面…
        </p>
      }
    >
      {children}
    </Suspense>
  );
}

function HomeRedirect() {
  const user = useAuthStore((state) => state.user);
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return (
    <Navigate
      to={isAuthenticated ? (user?.role === "admin" ? "/admin/workbench" : "/chat") : "/login"}
      replace
    />
  );
}

function RouteFailure() {
  return (
    <div role="alert" className="p-8 text-center">
      <p>页面加载失败，请检查连接后手动刷新。不会重新发送处置操作。</p>
      <button className="mt-4 rounded border p-3" onClick={() => window.location.reload()}>
        重新加载页面
      </button>
    </div>
  );
}
export const router = createBrowserRouter(
  [
    {
      path: "/",
      element: <HomeRedirect />
    },
    {
      path: "/login",
      element: (
        <RedirectIfAuth>
          <LoginPage />
        </RedirectIfAuth>
      )
    },
    {
      path: "/chat",
      element: (
        <RequireAuth>
          <EngineGate />
        </RequireAuth>
      )
    },
    {
      path: "/chat/:sessionId",
      element: (
        <RequireAuth>
          <EngineGate />
        </RequireAuth>
      )
    },
    {
      path: "/change-logs",
      element: (
        <RequireAuth>
          <ChangeLogsPage />
        </RequireAuth>
      )
    },
    {
      path: "/preview/doc/:docId",
      element: (
        <RequireAuth>
          <DocPreviewPage />
        </RequireAuth>
      )
    },
    {
      path: "/admin",
      element: (
        <RequireAdmin>
          <AdminLayout />
        </RequireAdmin>
      ),
      children: [
        { path: "workbench", element: <IncidentWorkbenchPage /> },
        { path: "incidents", element: <IncidentListPage /> },
        { path: "incidents/:incidentId", element: <IncidentDetailPage /> },
        { path: "incidents/:incidentId/review", element: <IncidentReviewPage /> },
        { path: "runbooks", element: <RunbookCenterPage /> },
        {
          index: true,
          element: <Navigate to="/admin/workbench" replace />
        },
        {
          path: "dashboard",
          element: <DashboardPage />
        },
        {
          path: "knowledge",
          element: <KnowledgeListPage />
        },
        {
          path: "knowledge/:kbId",
          element: <KnowledgeDocumentsPage />
        },
        {
          path: "knowledge/:kbId/docs/:docId",
          element: <KnowledgeChunksPage />
        },
        {
          path: "knowledge-graph",
          element: <KnowledgeGraphPage />
        },
        {
          path: "intent-tree",
          element: <IntentTreePage />
        },
        {
          path: "intent-list",
          element: <IntentListPage />
        },
        {
          path: "intent-list/:id/edit",
          element: <IntentEditPage />
        },
        {
          path: "ingestion",
          element: <IngestionPage />
        },
        {
          path: "traces",
          element: <RagTracePage />
        },
        {
          path: "traces/:traceId",
          element: <RagTraceDetailPage />
        },
        {
          path: "change-logs",
          element: <BizChangeLogPage />
        },
        {
          path: "settings",
          element: <SystemSettingsPage />
        },
        {
          path: "sample-questions",
          element: <SampleQuestionPage />
        },
        {
          path: "mappings",
          element: <QueryTermMappingPage />
        },
        {
          path: "agents",
          element: <AgentProfilePage />
        },
        {
          path: "agents/:agentId",
          element: <AgentPromptPage />
        },
        {
          path: "agent-skills",
          element: <AgentSkillPage />
        },
        {
          path: "agent-skills/:skillId",
          element: <AgentSkillEditPage />
        },
        {
          path: "users",
          element: <UserListPage />
        }
      ]
    },
    {
      path: "*",
      element: (
        <Suspense
          fallback={
            <p role="status" className="p-6 text-center">
              正在加载页面…
            </p>
          }
        >
          <NotFoundPage />
        </Suspense>
      )
    }
  ].map((route) => ({ ...route, errorElement: <RouteFailure /> }))
);
