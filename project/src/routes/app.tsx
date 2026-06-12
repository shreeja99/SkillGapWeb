import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  IconArrowLeft,
  IconCheck,
  IconCircleCheck,
  IconAlertTriangle,
  IconDownload,
  IconFileTypePdf,
  IconBrandGithub,
  IconLoader2,
  IconRefresh,
  IconX,
  IconChevronRight,
  IconActivity,
} from "@tabler/icons-react";
import { mockFiles, riskColor, type ImpactFile, type Risk } from "@/lib/mockData";
import { RippleGraph } from "@/components/RippleGraph";
import { RiskBadge } from "@/components/RiskBadge";
import logoRipple from "@/assets/logo ripple.png";

export const Route = createFileRoute("/app")({
  head: () => ({
    meta: [
      { title: "Analyzer · Ripple" },
      { name: "description", content: "Run an impact analysis on a Java repository." },
    ],
  }),
  component: AppShell,
});

type Phase = "idle" | "running" | "done";
type RepoState = "disconnected" | "connecting" | "connected";

const PIPELINE_STEPS = [
  { id: "ast", label: "Reading your code" },
  { id: "ref", label: "Mapping references" },
  { id: "bob", label: "Asking Bob to reason" },
  { id: "verify", label: "Checking line numbers" },
  { id: "render", label: "Drawing the map" },
] as const;

type Filter = "all" | "high" | "med" | "low";

const SAMPLE_REPO = "https://github.com/spring-projects/spring-petclinic";

function AppShell() {
  const [repoUrl, setRepoUrl] = useState("");
  const [repo, setRepo] = useState<RepoState>("disconnected");
  const [intent, setIntent] = useState("");
  const [phase, setPhase] = useState<Phase>("idle");
  const [stepIndex, setStepIndex] = useState(-1);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [filter, setFilter] = useState<Filter>("all");
  const [graphSeed, setGraphSeed] = useState(0);

  const connectRepo = (url?: string) => {
    const target = (url ?? repoUrl).trim();
    if (!target) return;
    setRepoUrl(target);
    setRepo("connecting");
    setTimeout(() => setRepo("connected"), 1400);
  };
  const disconnectRepo = () => {
    setRepo("disconnected");
    setRepoUrl("");
    setPhase("idle");
    setStepIndex(-1);
    setSelectedId(null);
  };
  const repoName = repoUrl
    .replace(/^https?:\/\/github\.com\//, "")
    .replace(/\.git$/, "")
    .replace(/\/$/, "") || "your-repo";
  const [hasRun, setHasRun] = useState(false);
  const timersRef = useRef<ReturnType<typeof setTimeout>[]>([]);

  const wordCount = intent.trim().split(/\s+/).filter(Boolean).length;
  const canAnalyze = repo === "connected" && wordCount >= 10 && phase !== "running";

  useEffect(() => () => timersRef.current.forEach(clearTimeout), []);

  const runAnalysis = () => {
    if (!canAnalyze) return;
    setPhase("running");
    setStepIndex(0);
    setSelectedId(null);
    setGraphSeed((seed) => seed + 1);
    timersRef.current.forEach(clearTimeout);
    timersRef.current = [];
    PIPELINE_STEPS.forEach((_, i) => {
      const t = setTimeout(
        () => {
          setStepIndex(i + 1);
          if (i === PIPELINE_STEPS.length - 1) {
            setPhase("done");
            setHasRun(true);
          }
        },
        (i + 1) * 650,
      );
      timersRef.current.push(t);
    });
  };

  const selectedFile = useMemo(
    () => mockFiles.find((f) => f.id === selectedId) ?? null,
    [selectedId],
  );

  const summary = useMemo(() => {
    const visible = mockFiles.filter((f) => f.risk !== "origin");
    return {
      files: visible.length,
      lineRefs: visible.reduce((s, f) => s + f.lineRefs.length, 0),
      highRisk: visible.filter((f) => f.risk === "high").length,
      verified: Math.round(
        (visible.flatMap((f) => f.lineRefs).filter((l) => l.verified).length /
          Math.max(1, visible.flatMap((f) => f.lineRefs).length)) *
          100,
      ),
    };
  }, []);

  return (
    <div className="flex h-screen flex-col" style={{ background: "var(--bg)" }}>
      <TopBar />
      <div className="flex min-h-0 flex-1 gap-3 px-3 pb-3">
        {/* Left panel */}
        <aside
          className="flex w-[280px] flex-shrink-0 flex-col gap-3 overflow-y-auto rounded-lg border p-3"
          style={{ background: "var(--surface)", borderColor: "var(--border)" }}
        >
          <StepHeader n={1} title="Connect a GitHub repo" done={repo === "connected"} />
          {repo === "connected" ? (
            <ConnectedRepoCard name={repoName} onDisconnect={disconnectRepo} />
          ) : (
            <div className="flex flex-col gap-2">
              <div
                className="flex items-center gap-1.5 rounded-md border px-2.5"
                style={{
                  background: "var(--bg)",
                  borderColor: "var(--border)",
                }}
              >
                <IconBrandGithub size={14} style={{ color: "var(--text-3)" }} />
                <input
                  value={repoUrl}
                  onChange={(e) => setRepoUrl(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && connectRepo()}
                  placeholder="https://github.com/owner/repo"
                  disabled={repo === "connecting"}
                  className="w-full bg-transparent py-2 font-mono text-[11.5px] outline-none placeholder:text-[var(--text-3)]"
                  style={{ color: "var(--text)" }}
                />
              </div>
              <button
                onClick={() => connectRepo()}
                disabled={!repoUrl.trim() || repo === "connecting"}
                className="inline-flex items-center justify-center gap-2 rounded-md px-3 py-2 text-[12.5px] font-medium text-white transition-opacity disabled:cursor-not-allowed disabled:opacity-40"
                style={{ background: "var(--accent)" }}
              >
                {repo === "connecting" ? (
                  <>
                    <IconLoader2 size={13} className="animate-spin" /> Connecting…
                  </>
                ) : (
                  "Connect repo"
                )}
              </button>
              <button
                onClick={() => connectRepo(SAMPLE_REPO)}
                disabled={repo === "connecting"}
                className="text-left text-[11.5px] underline-offset-2 hover:underline disabled:opacity-40"
                style={{ color: "var(--text-3)" }}
              >
                or try the Spring PetClinic sample →
              </button>
            </div>
          )}

          <StepHeader
            n={2}
            title="Describe the change"
            done={wordCount >= 10}
            disabled={repo !== "connected"}
          />
          <textarea
            value={intent}
            onChange={(e) => setIntent(e.target.value)}
            disabled={repo !== "connected"}
            placeholder={
              repo === "connected"
                ? "e.g. Rename the Owner class to Customer everywhere it’s used."
                : "Connect a repo first…"
            }
            rows={5}
            className="min-h-[140px] w-full resize-none rounded-md border px-2.5 py-2 text-[13px] leading-relaxed outline-none focus:border-[var(--accent)] disabled:opacity-50"
            style={{
              background: "var(--bg)",
              borderColor: "var(--border)",
              color: "var(--text)",
            }}
          />
          <div
            className="flex items-center justify-between font-mono text-[10.5px]"
            style={{ color: "var(--text-3)" }}
          >
            <span>{wordCount} words</span>
            <span>
              {wordCount === 0
                ? "min. 10 words"
                : wordCount < 10
                  ? `${10 - wordCount} more to go`
                  : "ready"}
            </span>
          </div>

          <StepHeader n={3} title="Run the analysis" done={hasRun} disabled={!canAnalyze && !hasRun} />
          <button
            onClick={runAnalysis}
            disabled={!canAnalyze}
            className="inline-flex items-center justify-center gap-2 rounded-md px-3 py-2.5 text-[13px] font-medium text-white transition-opacity disabled:cursor-not-allowed disabled:opacity-40"
            style={{ background: "var(--accent)" }}
          >
            {phase === "running" ? (
              <>
                Analyzing
                <span className="ml-1 inline-flex gap-1">
                  <span className="loading-dot h-1 w-1 rounded-full bg-white" />
                  <span className="loading-dot h-1 w-1 rounded-full bg-white" />
                  <span className="loading-dot h-1 w-1 rounded-full bg-white" />
                </span>
              </>
            ) : hasRun ? (
              <>
                <IconRefresh size={14} /> Re-analyze
              </>
            ) : (
              "Analyze impact"
            )}
          </button>

          {hasRun && (
            <>
              <PanelLabel>Summary</PanelLabel>
              <div className="grid grid-cols-2 gap-2">
                <Stat value={summary.files} label="files" />
                <Stat value={summary.lineRefs} label="line refs" />
                <Stat value={summary.highRisk} label="high risk" tone="high" />
                <Stat value={`${summary.verified}%`} label="verified" tone="low" />
              </div>
            </>
          )}

          {phase !== "idle" && (
            <>
              <PanelLabel>Progress</PanelLabel>
              <ul className="flex flex-col gap-1.5">
                {PIPELINE_STEPS.map((s, i) => {
                  const status =
                    i < stepIndex
                      ? "done"
                      : i === stepIndex && phase === "running"
                        ? "running"
                        : phase === "done"
                          ? "done"
                          : "waiting";
                  return <PipelineStep key={s.id} label={s.label} status={status} />;
                })}
              </ul>
            </>
          )}
        </aside>

        {/* Center panel */}
        <main
          className="flex min-w-0 flex-1 flex-col overflow-hidden rounded-lg border"
          style={{ background: "var(--surface)", borderColor: "var(--border)" }}
        >
          <div
            className="flex items-center justify-between gap-2 border-b px-4 py-2.5"
            style={{ borderColor: "var(--border)" }}
          >
            <div className="flex items-center gap-1.5">
              {(["all", "high", "med", "low"] as Filter[]).map((f) => (
                <button
                  key={f}
                  onClick={() => setFilter(f)}
                  className="rounded-md border px-2.5 py-1 text-[11.5px] font-medium uppercase tracking-wider transition-colors"
                  style={{
                    background:
                      filter === f ? "var(--accent)" : "var(--surface-2)",
                    borderColor: filter === f ? "var(--accent)" : "var(--border)",
                    color: filter === f ? "white" : "var(--text-2)",
                  }}
                >
                  {f === "med" ? "Medium" : f === "all" ? "All" : f.charAt(0).toUpperCase() + f.slice(1)}
                </button>
              ))}
            </div>
            <div className="flex items-center gap-2 font-mono text-[11px]" style={{ color: "var(--text-3)" }}>
              <IconActivity size={12} />
              {hasRun ? `${summary.files} nodes · ${summary.lineRefs} refs` : "awaiting analysis"}
            </div>
          </div>

          <div className="relative flex-1 overflow-hidden" style={{ background: "var(--bg)" }}>
            {!hasRun && phase !== "running" && (
              <EmptyGraphState />
            )}
            {phase === "running" && <RunningGraphState step={stepIndex} />}
            {hasRun && (
              <RippleGraph
                key={filter}
                selectedId={selectedId}
                onSelect={setSelectedId}
                filter={filter}
                seed={graphSeed}
              />
            )}
          </div>

          <div
            className="flex flex-wrap items-center gap-4 border-t px-4 py-2.5 text-[11.5px]"
            style={{ borderColor: "var(--border)", color: "var(--text-2)" }}
          >
            <Legend color="var(--accent)" label="Origin" square />
            <Legend color="var(--risk-high)" label="High" />
            <Legend color="var(--risk-med)" label="Medium" />
            <Legend color="var(--risk-low)" label="Low" />
            <span className="ml-auto font-mono text-[10.5px]" style={{ color: "var(--text-3)" }}>
              node size ∝ dependents
            </span>
          </div>
        </main>

        {/* Right panel */}
        <aside
          className="flex w-[320px] flex-shrink-0 flex-col overflow-hidden rounded-lg border"
          style={{ background: "var(--surface)", borderColor: "var(--border)" }}
        >
          <FileDrawer file={selectedFile} hasRun={hasRun} />
        </aside>
      </div>
    </div>
  );
}

function TopBar() {
  return (
    <div className="flex h-12 items-center justify-between px-4">
      <Link
        to="/"
        className="inline-flex items-center gap-2 text-[12.5px] font-medium"
        style={{ color: "var(--text-2)" }}
      >
        <IconArrowLeft size={14} />
        <span className="nav-brand nav-brand-pill">
          <span className="nav-dot" aria-hidden="true">
            <img src={logoRipple} alt="Ripple" className="nav-logo" />
          </span>
          <span className="nav-brand-name">Ripple</span>
        </span>
      </Link>
      <div className="flex items-center gap-3 font-mono text-[10.5px]" style={{ color: "var(--text-3)" }}>
        <span>session a3f29c · {new Date().toISOString().slice(0, 16).replace("T", " ")}</span>
      </div>
    </div>
  );
}

function PanelLabel({ children }: { children: React.ReactNode }) {
  return (
    <div
      className="mt-1 text-[10px] font-medium uppercase tracking-[0.16em]"
      style={{ color: "var(--text-3)" }}
    >
      {children}
    </div>
  );
}

function StepHeader({
  n,
  title,
  done,
  disabled,
}: {
  n: number;
  title: string;
  done?: boolean;
  disabled?: boolean;
}) {
  return (
    <div
      className="mt-1 flex items-center gap-2"
      style={{ opacity: disabled ? 0.45 : 1 }}
    >
      <span
        className="flex h-5 w-5 items-center justify-center rounded-full font-mono text-[10.5px] font-medium"
        style={{
          background: done ? "var(--risk-low)" : "var(--accent)",
          color: "white",
        }}
      >
        {done ? <IconCheck size={11} stroke={3} /> : n}
      </span>
      <span className="text-[12.5px] font-medium" style={{ color: "var(--text)" }}>
        {title}
      </span>
    </div>
  );
}

function ConnectedRepoCard({
  name,
  onDisconnect,
}: {
  name: string;
  onDisconnect: () => void;
}) {
  return (
    <div
      className="flex items-center gap-2 rounded-md border px-2.5 py-2"
      style={{ background: "var(--surface-2)", borderColor: "var(--border)" }}
    >
      <span className="h-2 w-2 rounded-full" style={{ background: "var(--risk-low)" }} />
      <IconBrandGithub size={14} style={{ color: "var(--text-2)" }} />
      <div className="flex min-w-0 flex-1 flex-col">
        <span className="truncate font-mono text-[11.5px]" style={{ color: "var(--text)" }}>
          {name}
        </span>
        <span className="font-mono text-[10px]" style={{ color: "var(--text-3)" }}>
          Connected · ready to analyze
        </span>
      </div>
      <button
        onClick={onDisconnect}
        className="rounded-sm p-1 hover:bg-[var(--bg)]"
        title="Disconnect repo"
      >
        <IconX size={12} style={{ color: "var(--text-3)" }} />
      </button>
    </div>
  );
}

function Stat({
  value,
  label,
  tone,
}: {
  value: number | string;
  label: string;
  tone?: "high" | "low";
}) {
  const color =
    tone === "high" ? "var(--risk-high)" : tone === "low" ? "var(--risk-low)" : "var(--text)";
  return (
    <div
      className="rounded-md border px-2.5 py-2"
      style={{ background: "var(--bg)", borderColor: "var(--border)" }}
    >
      <div className="font-mono text-[21px] font-medium leading-none" style={{ color }}>
        {value}
      </div>
      <div className="mt-1 text-[10.5px] uppercase tracking-wider" style={{ color: "var(--text-3)" }}>
        {label}
      </div>
    </div>
  );
}

function PipelineStep({
  label,
  status,
}: {
  label: string;
  status: "waiting" | "running" | "done" | "error";
}) {
  return (
    <li
      className="flex items-center justify-between gap-2 rounded-md px-2 py-1.5"
      style={{
        background:
          status === "done"
            ? "var(--risk-low-soft)"
            : status === "running"
              ? "var(--accent-soft)"
              : "var(--surface-2)",
      }}
    >
      <span className="flex items-center gap-2">
        <span
          className={`flex h-4 w-4 items-center justify-center rounded-full ${
            status === "running" ? "pulse-ring" : ""
          }`}
          style={{
            background:
              status === "done"
                ? "var(--risk-low)"
                : status === "running"
                  ? "var(--accent)"
                  : "transparent",
            border: status === "waiting" ? "1.5px solid var(--border-strong)" : "none",
          }}
        >
          {status === "done" && <IconCheck size={11} color="white" stroke={3} />}
        </span>
        <span className="text-[12px]" style={{ color: "var(--text)" }}>
          {label}
        </span>
      </span>
      <span className="font-mono text-[10px]" style={{ color: "var(--text-3)" }}>
        {status === "done" ? "ok" : status === "running" ? "…" : "—"}
      </span>
    </li>
  );
}

function Legend({ color, label, square }: { color: string; label: string; square?: boolean }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        style={{
          background: color,
          width: 9,
          height: 9,
          borderRadius: square ? 2 : 999,
          display: "inline-block",
        }}
      />
      {label}
    </span>
  );
}

function EmptyGraphState() {
  return (
    <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 text-center">
      <div
        className="flex h-12 w-12 items-center justify-center rounded-lg border"
        style={{ background: "var(--surface)", borderColor: "var(--border)" }}
      >
        <IconActivity size={20} style={{ color: "var(--accent)" }} />
      </div>
      <div className="text-[14px] font-medium" style={{ color: "var(--text)" }}>
        Describe a change, then click Analyze.
      </div>
      <div className="max-w-sm text-[12.5px]" style={{ color: "var(--text-2)" }}>
        The ripple map will render here. Nodes are colour-coded by risk; the origin node is the
        change target.
      </div>
    </div>
  );
}

function RunningGraphState({ step }: { step: number }) {
  return (
    <div className="absolute inset-0 flex flex-col items-center justify-center gap-3">
      <IconLoader2 size={28} className="animate-spin" style={{ color: "var(--accent)" }} />
      <div className="text-[13px]" style={{ color: "var(--text-2)" }}>
        {PIPELINE_STEPS[Math.min(step, PIPELINE_STEPS.length - 1)]?.label ?? "Working"}…
      </div>
      <div
        className="h-1 w-64 overflow-hidden rounded-full"
        style={{ background: "var(--surface-2)" }}
      >
        <div
          className="h-full transition-all duration-500"
          style={{
            width: `${(step / PIPELINE_STEPS.length) * 100}%`,
            background: "var(--accent)",
          }}
        />
      </div>
    </div>
  );
}

function FileDrawer({ file, hasRun }: { file: ImpactFile | null; hasRun: boolean }) {
  if (!hasRun) {
    return (
      <DrawerEmpty
        title="No analysis yet"
        body="Run an analysis from the left panel to populate file detail here."
      />
    );
  }
  if (!file) {
    return (
      <DrawerEmpty
        title="Select a node"
        body="Click any node in the ripple map to inspect file path, line refs, Bob reasoning, and tests."
      />
    );
  }

  const riskTone: Risk = file.risk;

  return (
    <div className="flex h-full flex-col">
      <div
        className="flex items-start justify-between gap-2 border-b px-3.5 pb-3 pt-3"
        style={{ borderColor: "var(--border)" }}
      >
        <div className="min-w-0">
          <div className="text-[14px] font-medium" style={{ color: "var(--text)" }}>
            {file.shortName}
          </div>
          <div className="mt-1 truncate font-mono text-[10.5px]" style={{ color: "var(--text-3)" }}>
            {file.path}
          </div>
        </div>
        <RiskBadge risk={riskTone} />
      </div>

      <div className="flex-1 overflow-y-auto px-3.5 py-3">
        <PanelLabel>Risk score</PanelLabel>
        <div className="mt-1.5 flex items-center gap-3">
          <span className="font-mono text-[20px] font-medium" style={{ color: riskColor(file.risk) }}>
            {file.score}
          </span>
          <div className="h-1.5 flex-1 overflow-hidden rounded-full" style={{ background: "var(--surface-2)" }}>
            <div
              className="score-fill h-full"
              style={{ width: `${file.score}%`, background: riskColor(file.risk) }}
            />
          </div>
          <span className="font-mono text-[10.5px]" style={{ color: "var(--text-3)" }}>
            /100
          </span>
        </div>

        <PanelLabel>Cascade chain</PanelLabel>
        <div className="mt-1.5 flex flex-wrap items-center gap-1 font-mono text-[11px]">
          {file.cascade.map((c, i) => (
            <span key={i} className="inline-flex items-center gap-1" style={{ color: "var(--text-2)" }}>
              {i > 0 && <IconChevronRight size={10} style={{ color: "var(--text-3)" }} />}
              <span
                className="rounded-sm px-1.5 py-0.5"
                style={{ background: "var(--surface-2)", color: "var(--text)" }}
              >
                {c}
              </span>
            </span>
          ))}
        </div>

        <PanelLabel>Line references</PanelLabel>
        <div className="mt-1.5 flex flex-col gap-2">
          {file.lineRefs.map((ref, i) => (
            <div
              key={i}
              className="rounded-md border"
              style={{ background: "var(--surface)", borderColor: "var(--border)" }}
            >
              <div
                className="flex items-center justify-between border-b px-2.5 py-1.5 font-mono text-[10.5px]"
                style={{ borderColor: "var(--border)", color: "var(--text-3)" }}
              >
                <span>
                  line <span style={{ color: "var(--text)" }}>{ref.line}</span> · {ref.kind}
                </span>
                {ref.verified ? (
                  <span
                    className="inline-flex items-center gap-1 rounded-sm px-1.5 py-0.5"
                    style={{ background: "var(--risk-low-soft)", color: "var(--risk-low)" }}
                  >
                    <IconCircleCheck size={10} /> AST
                  </span>
                ) : (
                  <span
                    className="inline-flex items-center gap-1 rounded-sm px-1.5 py-0.5"
                    style={{ background: "var(--risk-med-soft)", color: "var(--risk-med)" }}
                  >
                    <IconAlertTriangle size={10} /> Unverified
                  </span>
                )}
              </div>
              <pre
                className="overflow-x-auto px-2.5 py-2 font-mono text-[11px] leading-relaxed"
                style={{ background: "var(--surface-2)", color: "var(--text)" }}
              >
                {ref.snippet}
              </pre>
            </div>
          ))}
        </div>

        <PanelLabel>Bob reasoning</PanelLabel>
        <p className="mt-1.5 text-[12.5px] leading-relaxed" style={{ color: "var(--text-2)" }}>
          {file.reasoning}
        </p>

        {file.breakingTests.length > 0 && (
          <>
            <PanelLabel>Breaking tests</PanelLabel>
            <ul className="mt-1.5 flex flex-col gap-1">
              {file.breakingTests.map((t) => (
                <li
                  key={t}
                  className="flex items-start gap-2 font-mono text-[11px]"
                  style={{ color: "var(--text)" }}
                >
                  <IconX size={12} className="mt-0.5 flex-shrink-0" style={{ color: "var(--risk-high)" }} />
                  {t}
                </li>
              ))}
            </ul>
          </>
        )}

        {file.suggestedTests.length > 0 && (
          <>
            <PanelLabel>Suggested tests</PanelLabel>
            <ul className="mt-1.5 flex flex-col gap-1">
              {file.suggestedTests.map((t) => (
                <li
                  key={t}
                  className="flex items-start gap-2 text-[12px]"
                  style={{ color: "var(--text-2)" }}
                >
                  <IconCheck size={12} className="mt-0.5 flex-shrink-0" style={{ color: "var(--risk-low)" }} />
                  {t}
                </li>
              ))}
            </ul>
          </>
        )}
      </div>

      <div
        className="grid grid-cols-3 gap-1.5 border-t p-2.5"
        style={{ borderColor: "var(--border)", background: "var(--surface)" }}
      >
        <ExportButton icon={<IconDownload size={13} />} label="JSON" />
        <ExportButton icon={<IconFileTypePdf size={13} />} label="PDF" />
        <ExportButton icon={<IconBrandGithub size={13} />} label="Bob log" />
      </div>
    </div>
  );
}

function ExportButton({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <button
      className="inline-flex items-center justify-center gap-1.5 rounded-md border px-2 py-1.5 text-[11.5px] font-medium transition-colors hover:bg-[var(--surface-2)]"
      style={{ background: "var(--bg)", borderColor: "var(--border)", color: "var(--text-2)" }}
    >
      {icon} {label}
    </button>
  );
}

function DrawerEmpty({ title, body }: { title: string; body: string }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-2 px-6 text-center">
      <div
        className="flex h-9 w-9 items-center justify-center rounded-md border"
        style={{ background: "var(--surface-2)", borderColor: "var(--border)" }}
      >
        <IconActivity size={16} style={{ color: "var(--text-3)" }} />
      </div>
      <div className="text-[13px] font-medium" style={{ color: "var(--text)" }}>
        {title}
      </div>
      <div className="text-[12px]" style={{ color: "var(--text-2)" }}>
        {body}
      </div>
    </div>
  );
}
