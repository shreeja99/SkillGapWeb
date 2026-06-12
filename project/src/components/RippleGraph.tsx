import { useMemo } from "react";
import { mockEdges, mockFiles, riskColor, type ImpactFile, type Risk } from "@/lib/mockData";

interface Props {
  selectedId: string | null;
  onSelect: (id: string) => void;
  filter: "all" | "high" | "med" | "low";
  preview?: boolean;
  seed?: number;
}

interface Pos {
  x: number;
  y: number;
}

const W = 900;
const H = 560;
const PADDING = 32;

function mulberry32(seed: number) {
  return () => {
    let t = (seed += 0x6d2b79f5);
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function layout(files: ImpactFile[], seed = 1): Record<string, Pos> {
  // Radial: origin in center; rings by cascade depth.
  const innerW = W - PADDING * 2;
  const innerH = H - PADDING * 2;
  const cx = PADDING + innerW / 2;
  const cy = PADDING + innerH / 2;
  const positions: Record<string, Pos> = {};
  const rand = mulberry32(seed);

  const byDepth = new Map<number, ImpactFile[]>();
  let maxDepth = 0;
  for (const f of files) {
    const d = Math.max(0, f.cascade.length - 1);
    maxDepth = Math.max(maxDepth, d);
    if (!byDepth.has(d)) byDepth.set(d, []);
    byDepth.get(d)!.push(f);
  }

  const maxRing = Math.min(innerW, innerH) / 2 - 28;
  const desiredStep = 110;
  const ringStep = Math.min(desiredStep, maxRing / Math.max(1, maxDepth + 1));

  for (const [depth, group] of byDepth) {
    if (depth === 0) {
      positions[group[0].id] = { x: cx, y: cy };
      continue;
    }
    const r = ringStep * (depth + 1);
    const step = (Math.PI * 2) / group.length;
    const offset = depth % 2 === 0 ? 0 : step / 2;
    group.forEach((f, i) => {
      const a = i * step + offset - Math.PI / 2;
      const jitter = (rand() - 0.5) * 16;
      positions[f.id] = {
        x: cx + Math.cos(a) * r + jitter,
        y: cy + Math.sin(a) * r + jitter,
      };
    });
  }
  return positions;
}

function radius(f: ImpactFile) {
  if (f.risk === "origin") return 18;
  return 10 + Math.min(14, f.dependents * 1.6);
}

export function RippleGraph({ selectedId, onSelect, filter, preview, seed = 1 }: Props) {
  const positions = useMemo(() => layout(mockFiles, seed), [seed]);

  const visibleIds = useMemo(() => {
    if (filter === "all") return new Set(mockFiles.map((f) => f.id));
    return new Set(
      mockFiles.filter((f) => f.risk === filter || f.risk === "origin").map((f) => f.id),
    );
  }, [filter]);

  return (
    <svg
      viewBox={`0 0 ${W} ${H}`}
      className="h-full w-full"
      role="img"
      aria-label="Ripple impact graph"
    >
      <defs>
        <pattern id="grid" width="32" height="32" patternUnits="userSpaceOnUse">
          <path
            d="M 32 0 L 0 0 0 32"
            fill="none"
            stroke="var(--border-strong)"
            strokeWidth="0.8"
          />
        </pattern>
      </defs>
      <rect width={W} height={H} fill="url(#grid)" opacity={preview ? 0.55 : 0.85} />

      {/* Edges */}
      {mockEdges.map((e, i) => {
        const a = positions[e.source];
        const b = positions[e.target];
        if (!a || !b) return null;
        if (!visibleIds.has(e.source) || !visibleIds.has(e.target)) return null;
        const target = mockFiles.find((f) => f.id === e.target)!;
        const edgeStroke =
          target.risk === "high"
            ? "#f5a623"
            : target.risk === "low"
              ? "#5eead4"
              : target.risk === "med"
                ? "#d97706"
                : "var(--origin)";

        return (
          <line
            key={i}
            x1={a.x}
            y1={a.y}
            x2={b.x}
            y2={b.y}
            stroke={edgeStroke}
            strokeOpacity={0.3}
            strokeWidth={1.2}
            className="graph-edge"
            style={{ animationDelay: `${i * 60}ms` }}
          />
        );
      })}

      {/* Nodes */}
      {mockFiles.map((f, i) => {
        const p = positions[f.id];
        if (!p) return null;
        if (!visibleIds.has(f.id)) return null;
        const r = radius(f);
        const isSelected = selectedId === f.id;
        const color = riskColor(f.risk);

        return (
          <g
            key={f.id}
            className="graph-node cursor-pointer"
            style={{ animationDelay: `${i * 50}ms` }}
            onClick={() => onSelect(f.id)}
          >
            {isSelected && (
              <circle cx={p.x} cy={p.y} r={r + 10} fill={color} fillOpacity={0.18} />
            )}
            {f.risk === "origin" ? (
              <rect
                x={p.x - r}
                y={p.y - r}
                width={r * 2}
                height={r * 2}
                rx={3}
                fill={color}
                stroke="#fff"
                strokeWidth={isSelected ? 2 : 1.5}
              />
            ) : (
              <circle
                cx={p.x}
                cy={p.y}
                r={r}
                fill={color}
                stroke="#fff"
                strokeWidth={isSelected ? 2 : 1.5}
              />
            )}
            {!preview && (
              <text
                x={p.x}
                y={p.y + r + 14}
                textAnchor="middle"
                className="font-mono"
                fontSize="10"
                fill="var(--text-2)"
              >
                {f.shortName}
              </text>
            )}
          </g>
        );
      })}
    </svg>
  );
}

export const riskTextColor = (r: Risk) => {
  switch (r) {
    case "high":
      return "var(--risk-high)";
    case "med":
      return "var(--risk-med)";
    case "low":
      return "var(--risk-low)";
    default:
      return "var(--origin)";
  }
};
