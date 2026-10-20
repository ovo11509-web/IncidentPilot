import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import {
  Activity,
  ArrowUpRight,
  CircleAlert,
  ClipboardCheck,
  RefreshCw,
  ShieldCheck,
  Radio
} from "lucide-react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis
} from "recharts";
import { incidentService } from "@/services/incidentService";
import { displayTime, stateLabel } from "@/lib/workbenchView";
import type { Workbench } from "@/types/incident";

export function IncidentWorkbenchPage() {
  const [data, setData] = useState<Workbench>();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const request = useRef<AbortController>();
  const location = useLocation();
  const refresh = useCallback(async () => {
    request.current?.abort();
    const c = new AbortController();
    request.current = c;
    setBusy(true);
    setError("");
    try {
      const next = await incidentService.workbench(c.signal);
      if (!c.signal.aborted) setData(next);
    } catch (e) {
      if (!c.signal.aborted) setError((e as Error).message);
    } finally {
      if (!c.signal.aborted) setBusy(false);
    }
  }, []);
  useEffect(() => {
    void refresh();
    return () => request.current?.abort();
  }, [refresh]);
  useEffect(() => {
    if (data && location.hash === "#pending") document.getElementById("pending")?.scrollIntoView();
  }, [data, location.hash]);
  const cards = data
    ? [
        {
          label: "活跃事件",
          value: data.counts.activeIncidents,
          unit: "个事件",
          icon: Activity,
          tone: "cyan"
        },
        {
          label: "待人工审批",
          value: data.counts.pendingApprovalActions,
          unit: "个动作",
          icon: ClipboardCheck,
          tone: "violet"
        },
        {
          label: "执行中",
          value: data.counts.executingActions,
          unit: "个动作",
          icon: Radio,
          tone: "cyan"
        },
        {
          label: "结果未知",
          value: data.counts.unknownActions,
          unit: "个动作",
          icon: CircleAlert,
          tone: "amber"
        }
      ]
    : [];
  return (
    <div className="incidentpilot-page ip-workbench">
      <header className="ip-page-heading">
        <div>
          <p className="ip-eyebrow">INCIDENT RESPONSE / ON CALL</p>
          <h1>值班工作台</h1>
          <p className="ip-muted">从告警到处置，每一步都有依据</p>
        </div>
        <div className="ip-heading-actions">
          <span className="ip-badge">
            <ShieldCheck size={14} />
            模拟处置
          </span>
          <button className="ip-button" disabled={busy} onClick={() => void refresh()}>
            <RefreshCw size={15} className={busy ? "ip-spin" : ""} />
            {busy ? "读取中…" : "刷新快照"}
          </button>
        </div>
      </header>
      <p className="ip-snapshot">
        数据库快照 {data ? displayTime(data.observedAt) : "尚未取得"} · 时间显示为{" "}
        {Intl.DateTimeFormat().resolvedOptions().timeZone}
        {data && <span title={data.observedAt}> · {data.observedAt}</span>}
      </p>
      {error && (
        <div role="alert" className="ip-error">
          {error}
          {data ? " · 当前保留的是上次快照，请刷新后再判断。" : " · 尚无可用数据，请手动刷新。"}
          <button className="ip-link" disabled={busy} onClick={() => void refresh()}>
            重新读取
          </button>
        </div>
      )}
      {busy && !data && (
        <section className="ip-panel ip-empty" role="status">
          正在读取事件、审批与告警快照…
        </section>
      )}
      {data && (
        <>
          <div className="ip-stat-grid">
            {cards.map(({ label, value, unit, icon: Icon, tone }) => (
              <section key={label} className={`ip-stat ip-tone-${tone}`}>
                <div className="ip-stat-label">
                  {label}
                  <Icon size={19} />
                </div>
                <div className="ip-stat-value">
                  {value}
                  <span>{unit}</span>
                </div>
                <div className="ip-stat-foot">
                  {label === "活跃事件"
                    ? "OPEN / ACKNOWLEDGED / MITIGATING"
                    : "按已持久化动作状态统计"}
                </div>
              </section>
            ))}
          </div>
          <div className="ip-workbench-grid">
            <div className="ip-workbench-main">
              <section className="ip-panel">
                <div className="ip-panel-heading">
                  <h2>
                    <Activity size={17} />
                    需要关注的事件
                  </h2>
                  <Link className="ip-link" to="/admin/incidents">
                    事件中心 <ArrowUpRight size={14} />
                  </Link>
                </div>
                <p className="ip-muted ip-caption">
                  优先显示结果未知、待审批及活跃事件 · 最多 8 个
                </p>
                <div className="ip-table-scroll">
                  <table className="ip-table">
                    <thead>
                      <tr>
                        <th>事件 ID</th>
                        <th>生命周期</th>
                        <th>关联告警</th>
                        <th>待审批 / 未知</th>
                        <th>最后告警</th>
                        <th>查看</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.attentionIncidents.map((i) => (
                        <tr key={i.incidentId}>
                          <td>
                            <Link className="ip-id ip-link" to={`/admin/incidents/${i.incidentId}`}>
                              {i.incidentId}
                            </Link>
                          </td>
                          <td>
                            <span className="ip-badge">{stateLabel(i.lifecycleState)}</span>
                          </td>
                          <td>{i.alertCount} 条</td>
                          <td>
                            {i.pendingApprovalActionCount} /{" "}
                            <span className={i.unknownActionCount ? "ip-amber" : ""}>
                              {i.unknownActionCount}
                            </span>{" "}
                            个动作
                          </td>
                          <td>{displayTime(i.lastAlertReceivedAt)}</td>
                          <td>
                            <Link
                              className="ip-link"
                              aria-label={`查看事件 ${i.incidentId}`}
                              to={`/admin/incidents/${i.incidentId}`}
                            >
                              <ArrowUpRight size={16} />
                            </Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {data.attentionIncidents.length === 0 && (
                  <p className="ip-empty">当前没有需要关注的事件。</p>
                )}
              </section>
              <section className="ip-panel">
                <div className="ip-panel-heading">
                  <h2>
                    <Radio size={17} />
                    最近 24 小时告警
                  </h2>
                  <span className="ip-muted">单位：条 / 60 分钟</span>
                </div>
                <p className="ip-muted ip-caption">
                  持久化接收时间 · {displayTime(data.windowStart)} — {displayTime(data.windowEnd)} ·
                  包含已接收、已处理和失败记录
                </p>
                <div
                  className="ip-chart"
                  role="img"
                  aria-label={`24 小时共接收 ${data.alertBuckets.reduce((sum, b) => sum + b.count, 0)} 条告警；详细数据见下方表格`}
                >
                  <ResponsiveContainer width="100%" height="100%">
                    <AreaChart data={data.alertBuckets}>
                      <defs>
                        <linearGradient id="ip-alert-fill" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0%" stopColor="#38bdf8" stopOpacity={0.32} />
                          <stop offset="100%" stopColor="#38bdf8" stopOpacity={0} />
                        </linearGradient>
                      </defs>
                      <CartesianGrid stroke="#223656" vertical={false} />
                      <XAxis
                        dataKey="startAt"
                        tickFormatter={(t) => displayTime(t).slice(-5)}
                        stroke="#a5b5cc"
                        minTickGap={30}
                      />
                      <YAxis allowDecimals={false} stroke="#a5b5cc" width={35} />
                      <Tooltip
                        labelFormatter={(t) => displayTime(String(t))}
                        contentStyle={{
                          background: "#0e1930",
                          border: "1px solid #385276",
                          color: "#edf4ff"
                        }}
                        formatter={(v) => [`${v} 条`, "接收告警"]}
                      />
                      <Area
                        type="linear"
                        dataKey="count"
                        stroke="#38bdf8"
                        strokeWidth={2}
                        fill="url(#ip-alert-fill)"
                        isAnimationActive={false}
                      />
                    </AreaChart>
                  </ResponsiveContainer>
                </div>
                <details className="ip-chart-data">
                  <summary>查看 24 个时段的实际计数</summary>
                  <div className="ip-table-scroll">
                    <table className="ip-table">
                      <thead>
                        <tr>
                          <th>接收窗口（左闭右开）</th>
                          <th>条数</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.alertBuckets.map((b) => (
                          <tr key={b.startAt}>
                            <td>
                              {displayTime(b.startAt)} — {displayTime(b.endAt)}
                            </td>
                            <td>{b.count}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </details>
              </section>
            </div>
            <div className="ip-workbench-side">
              <section className="ip-panel" id="pending" tabIndex={-1}>
                <div className="ip-panel-heading">
                  <h2>
                    <ClipboardCheck size={17} />
                    待人工审批
                  </h2>
                  <span className="ip-badge">{data.counts.pendingApprovalActions} 个</span>
                </div>
                <p className="ip-muted ip-caption">
                  按截止时间排序 · 最多 8 个 · 到详情审阅冻结提案
                </p>
                {data.pendingActions.map((a) => (
                  <Link
                    className="ip-pending"
                    key={a.actionId}
                    to={`/admin/incidents/${a.incidentId}?actionId=${a.actionId}#actions`}
                  >
                    <div className="ip-pending-heading">
                      <span className="ip-badge ip-violet">待审批</span>
                      <ArrowUpRight size={15} />
                    </div>
                    <strong className="ip-id">Action {a.actionId}</strong>
                    <span className="ip-muted ip-id">事件 {a.incidentId}</span>
                    <span className="ip-muted">审批截止 {displayTime(a.approvalDueAt)}</span>
                  </Link>
                ))}
                {data.pendingActions.length === 0 && <p className="ip-empty">暂无待审批动作。</p>}
                <p className="ip-muted ip-caption">
                  待审批计数不代表仍可批准；服务端会核对有效期与绑定。
                </p>
              </section>
              <section className="ip-panel">
                <div className="ip-panel-heading">
                  <h2>
                    <Activity size={17} />
                    最近活动
                  </h2>
                </div>
                <p className="ip-muted ip-caption">按事实创建时间排序 · 最多 8 条</p>
                <ol className="ip-activity">
                  {data.recentActivity.map((a) => (
                    <li key={a.eventId}>
                      <span className="ip-activity-dot" />
                      <Link className="ip-link ip-id" to={`/admin/incidents/${a.incidentId}`}>
                        {a.eventType}
                      </Link>
                      <span className="ip-muted ip-id">
                        事件 {a.incidentId} · #{a.sequenceNo}
                      </span>
                      <time className="ip-muted">{displayTime(a.createdAt)}</time>
                    </li>
                  ))}
                </ol>
                {data.recentActivity.length === 0 && <p className="ip-empty">暂无时间线事实。</p>}
              </section>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
