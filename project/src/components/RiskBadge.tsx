import type { Risk } from "@/lib/mockData";

const styles: Record<Risk | "unverified", { bg: string; fg: string; label: string }> = {
  high: { bg: "var(--risk-high-soft)", fg: "#991b1b", label: "High risk" },
  med: { bg: "var(--risk-med-soft)", fg: "#a16207", label: "Medium risk" },
  low: { bg: "var(--risk-low-soft)", fg: "#15803d", label: "Low risk" },
  origin: { bg: "var(--accent-soft)", fg: "var(--accent)", label: "Origin" },
  unverified: { bg: "#fef9c3", fg: "#a16207", label: "Unverified" },
};

export function RiskBadge({
  risk,
  children,
}: {
  risk: Risk | "unverified";
  children?: React.ReactNode;
}) {
  const s = styles[risk];
  return (
    <span
      className="inline-flex items-center gap-1 rounded-sm px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wider"
      style={{ background: s.bg, color: s.fg }}
    >
      {children ?? s.label}
    </span>
  );
}
