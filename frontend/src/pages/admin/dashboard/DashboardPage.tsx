import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ComponentType,
  type ReactNode
} from "react";
import {
  Activity,
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Clock,
  Clock3,
  FileQuestion,
  Info,
  Lightbulb,
  MessageSquare,
  RefreshCw,
  Timer,
  Zap
} from "lucide-react";
import { toast } from "sonner";

import {
  SimpleLineChart,
  type ChartThreshold,
  type ChartXAxisMode,
  type ChartYAxisType,
  type TrendSeries
} from "@/components/admin/SimpleLineChart";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import {
  getDashboardOverview,
  getDashboardPerformance,
  getDashboardTrends,
  type DashboardOverview,
  type DashboardPerformance,
  type DashboardTrends
} from "@/services/dashboardService";

type DashboardTimeWindow = "24h" | "7d" | "30d";

type DashboardTrendBundle = {
  sessions: DashboardTrends | null;
  activeUsers: DashboardTrends | null;
  latency: DashboardTrends | null;
  quality: DashboardTrends | null;
};

type HealthStatus = "healthy" | "attention" | "critical";
type MetricTone = "good" | "warning" | "bad";

type HealthStatusView = {
  status: HealthStatus;
  title: string;
  description: string;
};

type MetricStatusView = {
  success: MetricTone;
  latency: MetricTone;
  error: MetricTone;
  noDoc: MetricTone;
};

type KPIStatus = "normal" | "warning" | "critical";

type KPIChange = {
  value: number;
  trend: "up" | "down" | "flat";
  isPositive: boolean;
};

type InsightCardData = {
  type: "anomaly" | "trend" | "recommendation";
  severity: "info" | "warning" | "critical";
  title: string;
  metric: string;
  change: string;
  context: string;
  action?: string;
  timestamp: string;
};

const WINDOW_OPTIONS: Array<{ value: DashboardTimeWindow; label: string }> = [
  { value: "24h", label: "24h" },
  { value: "7d", label: "7d" },
  { value: "30d", label: "30d" }
];

const WINDOW_LABEL_MAP: Record<DashboardTimeWindow, string> = {
  "24h": "滚动 24h",
  "7d": "近 7 天",
  "30d": "近 30 天"
};

const DASHBOARD_THRESHOLDS = {
  latency: { good: 2000, warning: 5000 },
  successRate: { good: 99, warning: 95 },
  errorRate: { good: 1, warning: 5 },
  noDocRate: { good: 10, warning: 30 }
} as const;

const EMPTY_TRENDS: DashboardTrendBundle = {
  sessions: null,
  activeUsers: null,
  latency: null,
  quality: null
};

const getMetricStatus = (
  metric: "latency" | "successRate" | "errorRate" | "noDocRate",
  value?: number | null
): MetricTone => {
  if (value === null || value === undefined) {
    return "warning";
  }

  if (metric === "latency") {
    if (value < DASHBOARD_THRESHOLDS.latency.good) return "good";
    if (value < DASHBOARD_THRESHOLDS.latency.warning) return "warning";
    return "bad";
  }

  if (metric === "successRate") {
    if (value >= DASHBOARD_THRESHOLDS.successRate.good) return "good";
    if (value >= DASHBOARD_THRESHOLDS.successRate.warning) return "warning";
    return "bad";
  }

  if (metric === "errorRate") {
    if (value <= DASHBOARD_THRESHOLDS.errorRate.good) return "good";
    if (value <= DASHBOARD_THRESHOLDS.errorRate.warning) return "warning";
    return "bad";
  }

  if (value <= DASHBOARD_THRESHOLDS.noDocRate.good) return "good";
  if (value <= DASHBOARD_THRESHOLDS.noDocRate.warning) return "warning";
  return "bad";
};

const getHealthStatus = (
  performance?: {
    successRate?: number | null;
    errorRate?: number | null;
    noDocRate?: number | null;
  } | null
): HealthStatus => {
  if (!performance) return "attention";
  if ((performance.errorRate ?? 0) > DASHBOARD_THRESHOLDS.errorRate.warning) return "critical";
  if ((performance.successRate ?? 0) < DASHBOARD_THRESHOLDS.successRate.warning) return "critical";
  if ((performance.noDocRate ?? 0) > 20) return "attention";
  return "healthy";
};

const useDashboardData = () => {
  const [timeWindow, setTimeWindow] = useState<DashboardTimeWindow>("24h");
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [performance, setPerformance] = useState<DashboardPerformance | null>(null);
  const [trends, setTrends] = useState<DashboardTrendBundle>(EMPTY_TRENDS);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<number | null>(null);

  const loadData = useCallback(async (windowValue: DashboardTimeWindow) => {
    setLoading(true);
    setError(null);

    const granularity = windowValue === "24h" ? "hour" : "day";

    try {
      const [overviewData, performanceData, sessions, activeUsers, latency, quality] = await Promise.all([
        getDashboardOverview(windowValue),
        getDashboardPerformance(windowValue),
        getDashboardTrends("sessions", windowValue, granularity),
        getDashboardTrends("activeUsers", windowValue, granularity),
        getDashboardTrends("avgLatency", windowValue, granularity),
        getDashboardTrends("quality", windowValue, granularity)
      ]);

      setOverview(overviewData);
      setPerformance(performanceData);
      setTrends({ sessions, activeUsers, latency, quality });
      setLastUpdated(Date.now());
    } catch (err) {
      console.error(err);
      setError("数据加载失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData(timeWindow);
  }, [loadData, timeWindow]);

  const refresh = useCallback(async () => {
    await loadData(timeWindow);
  }, [loadData, timeWindow]);

  return {
    timeWindow,
    setTimeWindow,
    loading,
    error,
    lastUpdated,
    overview,
    performance,
    trends,
    refresh
  };
};

const useHealthStatus = (performance: DashboardPerformance | null) => {
  const health = useMemo<HealthStatusView>(() => {
    const status = getHealthStatus(performance);

    if (status === "critical") {
      return {
        status,
        title: "系统风险偏高",
        description: "错误率或成功率触发告警阈值"
      };
    }

    if (status === "attention") {
      return {
        status,
        title: "系统需要关注",
        description: "召回质量或性能波动接近阈值"
      };
    }

    return {
      status,
      title: "系统运行健康",
      description: "核心质量指标保持稳定"
    };
  }, [performance]);

  const metricStatus = useMemo<MetricStatusView>(() => {
    return {
      success: getMetricStatus("successRate", performance?.successRate),
      latency: getMetricStatus("latency", performance?.avgLatencyMs),
      error: getMetricStatus("errorRate", performance?.errorRate),
      noDoc: getMetricStatus("noDocRate", performance?.noDocRate)
    };
  }, [performance]);

  return { health, metricStatus };
};

const formatLastUpdated = (timestamp: number | null) => {
  if (!timestamp) return "-";
  return new Date(timestamp).toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false
  });
};

const formatTime = (timestamp: number | null) => {
  if (!timestamp) return "-";
  return new Date(timestamp).toLocaleTimeString("zh-CN", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false
  });
};

const formatPercent = (value?: number | null) => {
  if (value === null || value === undefined) return "-";
  return `${value.toFixed(1)}%`;
};

const formatDuration = (value?: number | null) => {
  if (value === null || value === undefined) return "-";
  if (value < 1000) return `${Math.round(value)}ms`;
  return `${(value / 1000).toFixed(2)}s`;
};

const formatNumber = (value?: number | null) => {
  if (value === null || value === undefined) return "-";
  return value.toLocaleString("zh-CN");
};

const CARD_SURFACE_CLASS =
  "rounded-2xl border border-slate-200/80 bg-white/95 shadow-[0_8px_24px_rgba(15,23,42,0.06)]";
const CARD_HOVER_CLASS =
  "transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_14px_30px_rgba(15,23,42,0.10)]";
const SECTION_PANEL_CLASS =
  "rounded-2xl border border-slate-200/80 bg-white/90 p-5 shadow-[0_10px_26px_rgba(15,23,42,0.06)]";

const LoadingBlock = ({ className }: { className: string }) => {
  return <div className={`animate-pulse rounded bg-slate-100 ${className}`} />;
};

const SectionHeading = ({
  title,
  subtitle,
  extra
}: {
  title: string;
  subtitle: string;
  extra?: ReactNode;
}) => {
  return (
    <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 className="text-base font-semibold tracking-tight text-slate-900">{title}</h2>
        <p className="mt-0.5 text-xs text-slate-500">{subtitle}</p>
      </div>
      {extra ? <div>{extra}</div> : null}
    </div>
  );
};

const BackgroundDecoration = () => {
  return (
    <>
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-slate-100 via-slate-50 to-slate-50" />
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_100%_0%,rgba(59,130,246,0.12),transparent_44%)]" />
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_0%_100%,rgba(20,184,166,0.08),transparent_40%)]" />
      <div className="pointer-events-none absolute inset-x-0 top-0 h-56 bg-gradient-to-b from-white/70 to-transparent" />
    </>
  );
};

const DashboardHeader = ({
  title,
  subtitle,
  timeWindow,
  lastUpdated,
  loading,
  onRefresh,
  onTimeWindowChange
}: {
  title: string;
  subtitle: string;
  timeWindow: DashboardTimeWindow;
  lastUpdated: number | null;
  loading?: boolean;
  onRefresh: () => void;
  onTimeWindowChange: (window: DashboardTimeWindow) => void;
}) => {
  return (
    <header className="sticky top-0 z-40 border-b border-slate-200/80 bg-slate-50/90 backdrop-blur-sm">
      <div className="mx-auto max-w-[1600px] px-6 py-4">
        <div className="rounded-2xl border border-slate-200/80 bg-white/90 px-4 py-3 shadow-[0_8px_24px_rgba(15,23,42,0.05)]">
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div className="space-y-1">
              <div className="inline-flex items-center rounded-full border border-blue-200 bg-blue-50 px-2 py-0.5 text-[11px] font-medium text-blue-600">
                运营驾驶舱
              </div>
              <h1 className="text-xl font-semibold tracking-tight text-slate-900">{title}</h1>
              <p className="text-sm text-slate-500">{subtitle}</p>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <div className="inline-flex items-center rounded-xl border border-slate-200 bg-slate-100/70 p-1">
                {WINDOW_OPTIONS.map((option) => (
                  <button
                    key={option.value}
                    className={cn(
                      "rounded-lg px-3.5 py-1.5 text-sm font-medium transition-all",
                      timeWindow === option.value
                        ? "bg-white text-slate-900 shadow-sm"
                        : "text-slate-500 hover:text-slate-900"
                    )}
                    disabled={loading}
                    onClick={() => onTimeWindowChange(option.value)}
                  >
                    {option.label}
                  </button>
                ))}
              </div>

              <div className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs text-slate-500">
                <div className="font-medium text-slate-700">{WINDOW_LABEL_MAP[timeWindow]}</div>
                <div className="mt-1 flex items-center gap-1.5">
                  <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
                  <Clock3 className="h-3 w-3" />
                  <span>{formatLastUpdated(lastUpdated)}</span>
                </div>
              </div>

              <Button
                variant="outline"
                size="icon"
                onClick={onRefresh}
                disabled={loading}
                className="h-9 w-9 rounded-xl border-slate-200 bg-white text-slate-500 hover:bg-slate-50 hover:text-slate-900"
              >
                <RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} />
              </Button>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
};

const STATUS_STYLE: Record<
  HealthStatus,
  {
    box: string;
    bar: string;
    text: string;
    icon: typeof CheckCircle2;
  }
> = {
  healthy: {
    box: "border-emerald-200 bg-emerald-50/80",
    bar: "bg-emerald-500",
    text: "text-emerald-800",
    icon: CheckCircle2
  },
  attention: {
    box: "border-amber-200 bg-amber-50/80",
    bar: "bg-amber-500",
    text: "text-amber-800",
    icon: Info
  },
  critical: {
    box: "border-red-200 bg-red-50/80",
    bar: "bg-red-500",
    text: "text-red-800",
    icon: AlertTriangle
  }
};

const AlertBar = ({ health }: { health: HealthStatusView }) => {
  const current = STATUS_STYLE[health.status];
  const Icon = current.icon;

  return (
    <div className={cn("relative overflow-hidden rounded-xl border px-5 py-3 shadow-sm", current.box)}>
      <div className={cn("absolute inset-y-0 left-0 w-1.5", current.bar)} />
      <div className={cn("flex items-center gap-2", current.text)}>
        <Icon className="h-4 w-4 shrink-0" />
        <span className="text-sm font-medium">{health.title}</span>
        <span className="text-sm opacity-80">{health.description}</span>
      </div>
    </div>
  );
};

const STATUS_CARD: Record<KPIStatus, string> = {
  normal: "bg-white/95 border-slate-200",
  warning: "bg-amber-50/65 border-amber-200",
  critical: "bg-red-50/65 border-red-200"
};

const KPICard = ({
  label,
  value,
  change,
  status = "normal",
  icon,
  accentColor,
  accentBg
}: {
  label: string;
  value: string | number;
  change?: KPIChange;
  status?: KPIStatus;
  icon?: ReactNode;
  accentColor: string;
  accentBg: string;
}) => {
  const changeText =
    change && change.trend !== "flat"
      ? `${change.value > 0 ? "+" : ""}${change.value.toFixed(1)}%`
      : null;

  const changeColor =
    change?.trend === "up"
      ? change.isPositive
        ? "text-emerald-600"
        : "text-red-500"
      : change?.trend === "down"
        ? change.isPositive
          ? "text-red-500"
          : "text-emerald-600"
        : "text-slate-400";

  return (
    <div
      className={cn(
        "relative overflow-hidden rounded-2xl border p-5 shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-[0_14px_30px_rgba(15,23,42,0.10)]",
        STATUS_CARD[status]
      )}
    >
      <div className="absolute inset-x-0 top-0 h-1.5" style={{ backgroundColor: accentColor }} />

      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm font-medium text-slate-500">{label}</p>
        <div
          className="flex h-9 w-9 items-center justify-center rounded-lg"
          style={{ backgroundColor: accentBg }}
        >
          <div style={{ color: accentColor }}>{icon}</div>
        </div>
      </div>

      <div className="text-[2.25rem] font-bold leading-none tracking-tight text-slate-900">{value}</div>

      <div className="mt-2 h-5">
        {changeText ? (
          <span className={cn("text-sm font-medium", changeColor)}>
            {changeText}
            <span className="ml-1 text-xs font-normal text-slate-400">vs 上周期</span>
          </span>
        ) : (
          <span className="text-sm text-slate-400">--</span>
        )}
      </div>
    </div>
  );
};

const toChange = (deltaPct?: number | null): KPIChange => {
  if (deltaPct === null || deltaPct === undefined) {
    return { value: 0, trend: "flat", isPositive: true };
  }
  if (deltaPct > 0) {
    return { value: deltaPct, trend: "up", isPositive: true };
  }
  if (deltaPct < 0) {
    return { value: deltaPct, trend: "down", isPositive: false };
  }
  return { value: 0, trend: "flat", isPositive: true };
};

const toStatus = (deltaPct?: number | null): KPIStatus => {
  if (deltaPct === null || deltaPct === undefined) return "normal";
  if (deltaPct <= -50) return "critical";
  if (deltaPct <= -20) return "warning";
  return "normal";
};

const KPIGrid = ({
  overview,
  performance
}: {
  overview: DashboardOverview | null;
  performance: DashboardPerformance | null;
}) => {
  const kpis = overview?.kpis;

  const items: Array<{
    label: string;
    value: string | number;
    change?: KPIChange;
    status?: KPIStatus;
    icon?: ReactNode;
    accentColor: string;
    accentBg: string;
  }> = [
    {
      label: "活跃用户",
      value: formatNumber(kpis?.activeUsers.value),
      change: toChange(kpis?.activeUsers.deltaPct),
      status: toStatus(kpis?.activeUsers.deltaPct),
      icon: <Activity className="h-4.5 w-4.5" />,
      accentColor: "#3B82F6",
      accentBg: "#EFF6FF"
    },
    {
      label: "会话数(窗口)",
      value: formatNumber(kpis?.sessions24h.value),
      change: toChange(kpis?.sessions24h.deltaPct),
      status: toStatus(kpis?.sessions24h.deltaPct),
      icon: <MessageSquare className="h-4.5 w-4.5" />,
      accentColor: "#8B5CF6",
      accentBg: "#F5F3FF"
    },
    {
      label: "消息数(窗口)",
      value: formatNumber(kpis?.messages24h.value),
      change: toChange(kpis?.messages24h.deltaPct),
      status: toStatus(kpis?.messages24h.deltaPct),
      icon: <Zap className="h-4.5 w-4.5" />,
      accentColor: "#F59E0B",
      accentBg: "#FFFBEB"
    },
    {
      label: "成功率",
      value: performance ? `${performance.successRate.toFixed(1)}%` : "-",
      change: undefined,
      status:
        performance && performance.successRate < 95
          ? "critical"
          : performance && performance.successRate < 99
            ? "warning"
            : "normal",
      icon: <CheckCircle2 className="h-4.5 w-4.5" />,
      accentColor: "#10B981",
      accentBg: "#ECFDF5"
    }
  ];

  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {items.map((item) => (
        <KPICard key={item.label} {...item} />
      ))}
    </div>
  );
};

const formatCurrent = (value: number | undefined, type: "number" | "percent" | "duration") => {
  if (value === undefined || Number.isNaN(value)) return "-";
  if (type === "percent") return `${value.toFixed(1)}%`;
  if (type === "duration") {
    if (value < 1000) return `${Math.round(value)}ms`;
    return `${(value / 1000).toFixed(2)}s`;
  }
  return value.toLocaleString("zh-CN");
};

const extractCurrentValue = (series: TrendSeries[]) => {
  if (!series.length || !series[0].data.length) return undefined;
  return series[0].data[series[0].data.length - 1]?.value;
};

const mapSeries = (trend: DashboardTrends | null, tone: TrendSeries["tone"]): TrendSeries[] => {
  if (!trend?.series?.length) return [];
  return trend.series.map((item) => ({
    name: item.name,
    data: item.data,
    tone
  }));
};

const mapQualitySeries = (trend: DashboardTrends | null): TrendSeries[] => {
  if (!trend?.series?.length) return [];
  return trend.series.map((item) => ({
    name: item.name,
    data: item.data,
    tone: item.name.includes("错误") ? "danger" : "info"
  }));
};

const buildCompareSeries = (source: TrendSeries[]): TrendSeries[] => {
  return source.map((item) => {
    const data = item.data.map((point, index, list) => {
      const prev = list[index - 1] ?? list[index];
      return {
        ts: point.ts,
        value: prev.value
      };
    });

    return {
      name: `${item.name}(同比)`,
      data,
      tone: "neutral"
    };
  });
};

const TrendChartCard = ({
  title,
  subtitle,
  series,
  compareSeries = [],
  thresholds = [],
  xAxisMode,
  yAxisType = "number",
  yAxisLabel,
  currentValue,
  loading
}: {
  title: string;
  subtitle?: string;
  series: TrendSeries[];
  compareSeries?: TrendSeries[];
  thresholds?: ChartThreshold[];
  xAxisMode: ChartXAxisMode;
  yAxisType?: ChartYAxisType;
  yAxisLabel?: string;
  currentValue?: string;
  loading?: boolean;
}) => {
  const [showCompare, setShowCompare] = useState(false);

  const mergedSeries = useMemo(() => {
    if (!showCompare || compareSeries.length === 0) {
      return series;
    }
    return [
      ...series,
      ...compareSeries.map((item) => ({
        ...item,
        lineStyle: "dashed" as const,
        tone: item.tone ?? "neutral"
      }))
    ];
  }, [compareSeries, series, showCompare]);

  if (loading) {
    return (
      <Card className={CARD_SURFACE_CLASS}>
        <CardHeader className="pb-2">
          <LoadingBlock className="h-5 w-32" />
          <LoadingBlock className="mt-2 h-4 w-40" />
        </CardHeader>
        <CardContent>
          <LoadingBlock className="h-72 w-full" />
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className={cn(CARD_SURFACE_CLASS, CARD_HOVER_CLASS)}>
      <CardHeader className="pb-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <CardTitle className="text-base font-semibold text-slate-900">{title}</CardTitle>
            {subtitle ? <p className="mt-1 text-sm text-slate-500">{subtitle}</p> : null}
          </div>
          <div className="flex items-center gap-2">
            {currentValue ? (
              <Badge variant="outline" className="border-slate-200 bg-slate-50 text-slate-700">
                {currentValue}
              </Badge>
            ) : null}
            {compareSeries.length > 0 ? (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setShowCompare((prev) => !prev)}
                className={
                  showCompare
                    ? "rounded-md border-blue-200 bg-blue-50 text-blue-600 hover:bg-blue-100"
                    : "rounded-md border-slate-200 text-slate-600 hover:bg-slate-50 hover:text-slate-900"
                }
              >
                同比
              </Button>
            ) : null}
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-2">
        {yAxisLabel ? <div className="text-xs text-slate-400">{yAxisLabel}</div> : null}
        <div className="h-72">
          <SimpleLineChart
            series={mergedSeries}
            xAxisMode={xAxisMode}
            yAxisType={yAxisType}
            thresholds={thresholds}
            height={280}
            theme="light"
            yAxisTickCount={4}
          />
        </div>
      </CardContent>
    </Card>
  );
};

const TrendChartsGrid = ({
  trends,
  timeWindow,
  loading
}: {
  trends: DashboardTrendBundle;
  timeWindow: DashboardTimeWindow;
  loading?: boolean;
}) => {
  const xAxisMode = timeWindow === "24h" ? "hour" : "date";

  const sessionsSeries = useMemo(() => mapSeries(trends.sessions, "primary"), [trends.sessions]);
  const activeSeries = useMemo(() => mapSeries(trends.activeUsers, "success"), [trends.activeUsers]);
  const latencySeries = useMemo(() => mapSeries(trends.latency, "warning"), [trends.latency]);
  const qualitySeries = useMemo(() => mapQualitySeries(trends.quality), [trends.quality]);

  const sessionCompare = useMemo(() => buildCompareSeries(sessionsSeries), [sessionsSeries]);
  const activeCompare = useMemo(() => buildCompareSeries(activeSeries), [activeSeries]);
  const latencyCompare = useMemo(() => buildCompareSeries(latencySeries), [latencySeries]);
  const qualityCompare = useMemo(() => buildCompareSeries(qualitySeries), [qualitySeries]);

  return (
    <div className="grid gap-5 md:grid-cols-2">
      <TrendChartCard
        title="会话趋势"
        subtitle="会话数量变化"
        series={sessionsSeries}
        compareSeries={sessionCompare}
        xAxisMode={xAxisMode}
        yAxisType="number"
        yAxisLabel="单位：次"
        loading={loading}
        currentValue={formatCurrent(extractCurrentValue(sessionsSeries), "number")}
      />

      <TrendChartCard
        title="活跃用户趋势"
        subtitle="活跃用户规模变化"
        series={activeSeries}
        compareSeries={activeCompare}
        xAxisMode={xAxisMode}
        yAxisType="number"
        yAxisLabel="单位：人"
        loading={loading}
        currentValue={formatCurrent(extractCurrentValue(activeSeries), "number")}
      />

      <TrendChartCard
        title="响应时间趋势"
        subtitle="AI 响应耗时"
        series={latencySeries}
        compareSeries={latencyCompare}
        xAxisMode={xAxisMode}
        yAxisType="duration"
        yAxisLabel="单位：毫秒"
        loading={loading}
        currentValue={formatCurrent(extractCurrentValue(latencySeries), "duration")}
        thresholds={[
          { value: DASHBOARD_THRESHOLDS.latency.good, label: "good<2s", tone: "info" },
          { value: DASHBOARD_THRESHOLDS.latency.warning, label: "warn>5s", tone: "critical" }
        ]}
      />

      <TrendChartCard
        title="质量趋势"
        subtitle="错误率与无知识率"
        series={qualitySeries}
        compareSeries={qualityCompare}
        xAxisMode={xAxisMode}
        yAxisType="percent"
        yAxisLabel="单位：%"
        loading={loading}
        currentValue={formatCurrent(extractCurrentValue(qualitySeries), "percent")}
        thresholds={[
          { value: DASHBOARD_THRESHOLDS.errorRate.warning, label: "error warn", tone: "warning" },
          { value: DASHBOARD_THRESHOLDS.noDocRate.warning, label: "nodoc warn", tone: "critical" }
        ]}
      />
    </div>
  );
};

const STATUS_TEXT_CLASS: Record<MetricTone, string> = {
  good: "text-emerald-600",
  warning: "text-amber-600",
  bad: "text-red-600"
};

const STATUS_BAR_COLOR: Record<MetricTone, string> = {
  good: "#10B981",
  warning: "#F59E0B",
  bad: "#EF4444"
};

const STATUS_BAR_BG: Record<MetricTone, string> = {
  good: "#D1FAE5",
  warning: "#FEF3C7",
  bad: "#FEE2E2"
};

const getLatencyStatus = (value?: number | null): MetricTone => {
  if (value === null || value === undefined) return "warning";
  if (value <= DASHBOARD_THRESHOLDS.latency.good) return "good";
  if (value <= DASHBOARD_THRESHOLDS.latency.warning) return "warning";
  return "bad";
};

const Ring = ({ value }: { value: number }) => {
  const radius = 52;
  const circumference = 2 * Math.PI * radius;
  const clamped = Math.max(0, Math.min(value, 100));
  const progress = (clamped / 100) * circumference;
  const ringColor = clamped >= 95 ? "#10B981" : clamped >= 85 ? "#F59E0B" : "#EF4444";

  return (
    <div className="relative h-28 w-28">
      <svg className="h-28 w-28 -rotate-90" viewBox="0 0 120 120">
        <circle cx="60" cy="60" r={radius} fill="none" stroke="#F1F5F9" strokeWidth={9} />
        <circle
          cx="60"
          cy="60"
          r={radius}
          fill="none"
          stroke={ringColor}
          strokeWidth={9}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference - progress}
          className="transition-all duration-700 ease-out"
        />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="text-xl font-bold text-slate-900">{formatPercent(value)}</span>
        <span className="text-[10px] text-slate-400">成功率</span>
      </div>
    </div>
  );
};

const getBarPercent = (label: string, rawValue: number | undefined | null): number => {
  if (rawValue === null || rawValue === undefined) return 0;
  if (label === "平均响应" || label === "P95 响应") {
    return Math.min((rawValue / 10000) * 100, 100);
  }
  return Math.min(rawValue, 100);
};

const MetricLine = ({
  icon: Icon,
  label,
  value,
  rawValue,
  status
}: {
  icon: ComponentType<{ className?: string }>;
  label: string;
  value: string;
  rawValue?: number | null;
  status: MetricTone;
}) => {
  const barPercent = getBarPercent(label, rawValue);

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Icon className="h-3.5 w-3.5 text-slate-400" />
          <span className="text-sm text-slate-600">{label}</span>
        </div>
        <span className={cn("text-sm font-bold tabular-nums", STATUS_TEXT_CLASS[status])}>{value}</span>
      </div>
      <div className="h-1.5 w-full overflow-hidden rounded-full" style={{ backgroundColor: STATUS_BAR_BG[status] }}>
        <div
          className="h-full rounded-full transition-all duration-500 ease-out"
          style={{
            width: `${barPercent}%`,
            backgroundColor: STATUS_BAR_COLOR[status]
          }}
        />
      </div>
    </div>
  );
};

const AIPerformanceRadar = ({
  performance,
  metricStatus
}: {
  performance: DashboardPerformance | null;
  metricStatus: MetricStatusView;
}) => {
  const avgLatencyStatus = getLatencyStatus(performance?.avgLatencyMs);
  const p95LatencyStatus = getLatencyStatus(performance?.p95LatencyMs);

  return (
    <Card className={CARD_SURFACE_CLASS}>
      <CardHeader className="pb-3">
        <CardTitle className="text-base font-semibold text-slate-900">AI 性能雷达</CardTitle>
      </CardHeader>
      <CardContent className="space-y-5">
        <div className="flex items-center justify-center rounded-xl border border-slate-100 bg-gradient-to-b from-slate-50 to-white py-5">
          <Ring value={performance?.successRate ?? 0} />
        </div>

        <div className="space-y-3">
          <MetricLine
            icon={Timer}
            label="平均响应"
            value={formatDuration(performance?.avgLatencyMs)}
            rawValue={performance?.avgLatencyMs}
            status={avgLatencyStatus}
          />
          <MetricLine
            icon={Clock}
            label="P95 响应"
            value={formatDuration(performance?.p95LatencyMs)}
            rawValue={performance?.p95LatencyMs}
            status={p95LatencyStatus}
          />
          <MetricLine
            icon={AlertCircle}
            label="错误率"
            value={formatPercent(performance?.errorRate)}
            rawValue={performance?.errorRate}
            status={metricStatus.error}
          />
          <MetricLine
            icon={FileQuestion}
            label="无知识率"
            value={formatPercent(performance?.noDocRate)}
            rawValue={performance?.noDocRate}
            status={metricStatus.noDoc}
          />
        </div>
      </CardContent>
    </Card>
  );
};

const TYPE_LABEL: Record<InsightCardData["type"], string> = {
  anomaly: "异常",
  trend: "趋势",
  recommendation: "建议"
};

const TYPE_ICON: Record<InsightCardData["type"], typeof Info> = {
  anomaly: AlertCircle,
  trend: Info,
  recommendation: Lightbulb
};

const TYPE_BADGE_STYLE: Record<InsightCardData["type"], string> = {
  anomaly: "border-red-200 bg-red-50 text-red-600",
  trend: "border-blue-200 bg-blue-50 text-blue-600",
  recommendation: "border-amber-200 bg-amber-50 text-amber-600"
};

const SEVERITY_STYLE: Record<InsightCardData["severity"], string> = {
  info: "border-slate-200 bg-slate-50 text-slate-800",
  warning: "border-amber-200 bg-amber-50 text-amber-800",
  critical: "border-red-200 bg-red-50 text-red-800"
};

const InsightCard = ({ item }: { item: InsightCardData }) => {
  const Icon = TYPE_ICON[item.type];

  return (
    <div className={cn("rounded-xl border p-3.5 shadow-sm", SEVERITY_STYLE[item.severity])}>
      <div className="mb-2 flex items-center justify-between">
        <div
          className={cn(
            "inline-flex items-center gap-1.5 rounded-md border px-2 py-0.5 text-xs font-medium",
            TYPE_BADGE_STYLE[item.type]
          )}
        >
          <Icon className="h-3.5 w-3.5" />
          <span>{TYPE_LABEL[item.type]}</span>
        </div>
        <span className="text-[11px] text-slate-500">{item.timestamp}</span>
      </div>

      <p className="text-sm font-semibold text-slate-900">{item.title}</p>
      <p className="mt-1 text-xs text-slate-600">
        {item.metric}: {item.change}
      </p>
      <p className="mt-1 text-xs text-slate-500">归因：{item.context}</p>
      {item.action ? <p className="mt-1 text-xs font-medium text-slate-700">建议：{item.action}</p> : null}
    </div>
  );
};

const buildInsightList = (
  performance: DashboardPerformance | null,
  timeWindowLabel: string,
  timestamp: number | null
): InsightCardData[] => {
  const t = formatTime(timestamp);

  if (!performance) {
    return [
      {
        type: "trend",
        severity: "info",
        title: "等待数据回传",
        metric: "Dashboard",
        change: timeWindowLabel,
        context: "当前窗口尚未返回完整性能数据",
        timestamp: t
      }
    ];
  }

  const items: InsightCardData[] = [];

  if (performance.errorRate > 5 || performance.successRate < 95) {
    items.push({
      type: "anomaly",
      severity: "critical",
      title: "链路稳定性触发告警",
      metric: "成功率/错误率",
      change: `${performance.successRate.toFixed(1)}% / ${performance.errorRate.toFixed(1)}%`,
      context: "成功率低于 95% 或错误率高于 5%",
      action: "优先查看失败请求分布与超时节点",
      timestamp: t
    });
  } else {
    items.push({
      type: "trend",
      severity: "info",
      title: "系统可用性稳定",
      metric: "成功率",
      change: `${performance.successRate.toFixed(1)}%`,
      context: "当前窗口整体可用性处于健康区间",
      timestamp: t
    });
  }

  if (performance.noDocRate > 20) {
    items.push({
      type: "recommendation",
      severity: "warning",
      title: "召回质量需优化",
      metric: "无知识率",
      change: `${performance.noDocRate.toFixed(1)}%`,
      context: "无知识率超过 20%，用户命中体验存在风险",
      action: "优化索引覆盖率与检索重排策略",
      timestamp: t
    });
  }

  if (performance.avgLatencyMs > 3000) {
    items.push({
      type: "recommendation",
      severity: "warning",
      title: "响应性能需要关注",
      metric: "平均响应时间",
      change: `${(performance.avgLatencyMs / 1000).toFixed(2)}s`,
      context: "平均延迟高于 3s，影响交互体验",
      action: "排查慢节点与模型并发配置",
      timestamp: t
    });
  }

  if (items.length < 3) {
    items.push({
      type: "recommendation",
      severity: "info",
      title: "继续保持当前策略",
      metric: "运营状态",
      change: timeWindowLabel,
      context: "当前窗口内未发现显著异常趋势",
      timestamp: t
    });
  }

  return items.slice(0, 3);
};

const InsightList = ({
  performance,
  timeWindowLabel,
  timestamp
}: {
  performance: DashboardPerformance | null;
  timeWindowLabel: string;
  timestamp: number | null;
}) => {
  const items = useMemo(
    () => buildInsightList(performance, timeWindowLabel, timestamp),
    [performance, timeWindowLabel, timestamp]
  );

  return (
    <Card className={CARD_SURFACE_CLASS}>
      <CardHeader className="pb-3">
        <CardTitle className="text-base font-semibold text-slate-900">运营洞察</CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        {items.map((item, index) => (
          <InsightCard key={`${item.title}-${index}`} item={item} />
        ))}
      </CardContent>
    </Card>
  );
};

export function DashboardPage() {
  const {
    timeWindow,
    setTimeWindow,
    loading,
    error,
    lastUpdated,
    overview,
    performance,
    trends,
    refresh
  } = useDashboardData();

  const { health, metricStatus } = useHealthStatus(performance);

  useEffect(() => {
    if (!error) return;
    toast.error(error);
  }, [error]);

  return (
    <div className="relative min-h-screen bg-[linear-gradient(180deg,#f1f5f9_0%,#f8fafc_32%,#f8fafc_100%)] text-slate-900">
      <BackgroundDecoration />

      <div className="relative z-10">
        <DashboardHeader
          title="运营 Dashboard"
          subtitle="企业级科技运营看板，统一窗口语义驱动 KPI / 性能 / 趋势"
          timeWindow={timeWindow}
          lastUpdated={lastUpdated}
          loading={loading}
          onRefresh={() => {
            void refresh();
          }}
          onTimeWindowChange={setTimeWindow}
        />

        <main className="mx-auto max-w-[1600px] px-6 pb-8 pt-6">
          <div className="grid gap-6 xl:grid-cols-12">
            <section className="space-y-6 xl:col-span-9">
              <div className={SECTION_PANEL_CLASS}>
                <SectionHeading
                  title="系统健康"
                  subtitle="当前窗口内可用性与告警状态"
                  extra={
                    <Badge variant="outline" className="border-slate-200 bg-slate-50 text-slate-600">
                      {WINDOW_LABEL_MAP[timeWindow]}
                    </Badge>
                  }
                />
                <AlertBar health={health} />
              </div>

              <div className={SECTION_PANEL_CLASS}>
                <SectionHeading title="核心 KPI" subtitle="关键业务指标与环比变化" />
                <KPIGrid overview={overview} performance={performance} />
              </div>

              <div className={SECTION_PANEL_CLASS}>
                <SectionHeading title="趋势分析" subtitle="会话、活跃、时延与质量的统一窗口趋势" />
                <TrendChartsGrid trends={trends} timeWindow={timeWindow} loading={loading} />
              </div>
            </section>

            <aside className="space-y-6 xl:col-span-3 xl:sticky xl:top-28 xl:self-start">
              <AIPerformanceRadar performance={performance} metricStatus={metricStatus} />
              <InsightList
                performance={performance}
                timeWindowLabel={WINDOW_LABEL_MAP[timeWindow]}
                timestamp={lastUpdated}
              />
            </aside>
          </div>
        </main>
      </div>
    </div>
  );
}
