import { createFileRoute, Link } from "@tanstack/react-router";
import {
  IconArrowRight,
  IconBolt,
  IconChartDots3,
  IconCircleCheck,
  IconCode,
  IconFileExport,
  IconGitBranch,
  IconShieldCheck,
  IconSparkles,
  IconTestPipe,
  IconUsersGroup,
  IconClock,
  IconBrain,
} from "@tabler/icons-react";
import { RippleGraph } from "@/components/RippleGraph";
import bobImg from "@/assets/bob.png";
import logoRipple from "@/assets/logo ripple.png";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Ripple — IBM Bob Hackathon 2026" },
      {
        name: "description",
        content:
          "AI-powered impact analysis that pins every affected file to the exact line number — before you make the change.",
      },
      { property: "og:title", content: "Ripple" },
      {
        property: "og:description",
        content: "Understand the ripple effects of any code change — before you make it.",
      },
    ],
  }),
  component: Landing,
});

function Navbar() {
  return (
    <header
      className="nav-front sticky top-0 z-50 w-full border-b"
      style={{ background: "var(--surface)", borderColor: "var(--border)" }}
    >
      <div className="mx-auto flex h-20 max-w-6xl items-center px-6">
        <div className="nav-pillbar">
          <div className="nav-brand">
            <span className="nav-dot" aria-hidden="true">
              <img src={logoRipple} alt="Ripple" className="nav-logo" />
            </span>
            <span className="nav-brand-name">Ripple</span>
          </div>
          <nav className="nav-links">
            <a href="#home" style={{ color: "var(--text-2)" }}>Home</a>
            <a href="#how" style={{ color: "var(--text-2)" }}>How it works</a>
            <a href="#graph" style={{ color: "var(--text-2)" }}>Ripple graph</a>
            <a href="#features" style={{ color: "var(--text-2)" }}>Capabilities</a>
            <a href="#compare" style={{ color: "var(--text-2)" }}>Compare</a>
            <a href="#proof" style={{ color: "var(--text-2)" }}>Proof</a>
          </nav>
          <Link
            to="/app"
            className="nav-cta"
          >
            Get started <IconArrowRight size={14} />
          </Link>
        </div>
      </div>
    </header>
  );
}

function WordSequence({ text, delayStart = 0 }: { text: string; delayStart?: number }) {
  const words = text.split(" ");
  return (
    <span className="word-seq">
      {words.map((word, index) => (
        <span
          key={`${word}-${index}`}
          className="word-reveal"
          style={{
            ["--word-delay" as never]: `${delayStart + index * 0.06}s`,
          }}
        >
          {word}
        </span>
      ))}
    </span>
  );
}

function Hero() {
  return (
    <section
      id="home"
      className="section-reveal px-6 pb-20 pt-20 sm:pt-28"
      style={{ ["--section-delay" as never]: "0s" }}
    >
      <div className="mx-auto grid max-w-6xl items-center gap-10 lg:grid-cols-[1.1fr_0.9fr]">
        <div className="text-center lg:text-left">
          <h1
            className="hero-italic text-balance text-[44px] font-medium leading-[1.05] tracking-tight sm:text-[56px]"
            style={{ color: "var(--text)" }}
          >
            <WordSequence text="Understand the ripple effects of any code change —" />{" "}
            <span style={{ color: "var(--text-2)" }}>
              <WordSequence text="before you make it." delayStart={0.6} />
            </span>
          </h1>

          <p className="mx-auto mt-6 max-w-2xl text-[18px] lg:mx-0" style={{ color: "#000000" }}>
            <WordSequence
              text="AI-powered impact analysis that pins every affected file to the exact line number, grounded by JavaParser AST so the line numbers are real — not hallucinated."
              delayStart={0.9}
            />
          </p>

          <div className="mt-8 flex flex-wrap items-center justify-center gap-3 lg:justify-start">
            <Link
              to="/app"
              className="btn-primary inline-flex items-center gap-2 rounded-lg px-7 py-3.5 text-[14px] font-medium text-white transition-opacity hover:opacity-90"
              style={{ background: "var(--accent)" }}
            >
              Analyze your repo <IconArrowRight size={16} />
            </Link>
            <Link
              to="/app"
              className="btn-secondary inline-flex items-center gap-2 rounded-lg border px-7 py-3.5 text-[14px] font-medium transition-colors hover:bg-[var(--surface-2)]"
              style={{ borderColor: "#000000", color: "var(--text)" }}
            >
              See a demo
            </Link>
          </div>

          <div className="mt-10 flex flex-wrap items-center justify-center gap-2 font-mono text-[12px] lg:justify-start">
            {[
              "~3h → <60s",
              "17 files traced",
              "90%+ AST-verified",
            ].map((t, i) => (
              <span
                key={t}
                className={`metric-pill metric-pill-${i} rounded-md border`}
              >
                {t}
              </span>
            ))}
          </div>
        </div>

        <div className="flex justify-center lg:justify-end">
          <AnimatedBob />
        </div>
      </div>
    </section>
  );
}

function AnimatedBob() {
  return (
    <div className="bob-float flex flex-col items-center gap-4 sm:flex-row">
      <div className="bob-wrap">
        <img src={bobImg} alt="Bob the robot" className="bob-img" />
      </div>
      <div className="bob-bubble">
        <span>Hi, I’m Bob. I track how changes in one repo affect your entire ecosystem.</span>
      </div>
    </div>
  );
}

function ProblemBar() {
  const items = [
    {
      icon: IconClock,
      title: "Manual tracing eats hours",
      body: "Senior devs reading 30+ files just to guess what one rename will break.",
    },
    {
      icon: IconTestPipe,
      title: "Tests fail too late",
      body: "Reactive: you find regressions after CI, sometimes after production.",
    },
    {
      icon: IconUsersGroup,
      title: "Knowledge bottlenecks",
      body: "Only the one engineer who wrote the module truly knows what depends on it.",
    },
  ];
  return (
    <section
      id="problems"
      className="section-reveal px-6 py-16"
      style={{ background: "var(--surface)", ["--section-delay" as never]: "0.1s" }}
    >
      <div className="mx-auto grid max-w-6xl gap-4 sm:grid-cols-3">
        {items.map(({ icon: Icon, title, body }) => (
          <div
            key={title}
            className="lift-card rounded-lg border p-5"
            style={{ borderColor: "var(--border)" }}
          >
            <Icon size={20} style={{ color: "var(--accent)" }} />
            <h3 className="mt-3 text-[15px] font-medium" style={{ color: "var(--text)" }}>
              {title}
            </h3>
            <p className="mt-1 text-[15px]" style={{ color: "var(--text-2)" }}>
              {body}
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}

function HowItWorks() {
  const steps = [
    {
      n: "01",
      t: "Connect your repository",
      d: "Paste a GitHub URL or drag a Java project. We clone and run JavaParser to build a precise map of every class, method, field and line number.",
    },
    {
      n: "02",
      t: "Describe your intended change",
      d: "Plain English. ‘Rename Owner to Customer.’ ‘Change the return type of findById to Optional<T>.’ Optional: paste the line number you’re editing.",
    },
    {
      n: "03",
      t: "Bob reasons across the full repo",
      d: "IBM Bob receives the AST graph + your intent and traces dependency ripples — call sites, type refs, field accesses, indirect chains.",
    },
    {
      n: "04",
      t: "AST verifies every line number",
      d: "Bob’s output is reconciled against the AST. Confirmed refs get a green badge. Unverified refs are surfaced honestly with an amber badge.",
    },
    {
      n: "05",
      t: "Read the ripple map",
      d: "Force-directed graph: nodes coloured by risk tier, sized by dependents, origin highlighted. Click any node for the full file breakdown.",
    },
    {
      n: "06",
      t: "Export & ship",
      d: "Download JSON, print to PDF, or attach Bob’s session log to your hackathon submission.",
    },
  ];
  return (
    <section
      id="how"
      className="section-reveal px-6 py-20"
      style={{ ["--section-delay" as never]: "0.2s" }}
    >
      <div className="mx-auto max-w-6xl">
        <SectionHeader eyebrow="How it works" title="Six steps from intent to verified impact." />
        <div className="mt-10 grid gap-x-12 gap-y-10 sm:grid-cols-2">
          {steps.map((s) => (
            <div key={s.n} className="flex gap-5">
              <div
                className="font-mono text-[28px] leading-none"
                style={{ color: "var(--accent)" }}
              >
                {s.n}
              </div>
              <div>
                <h3 className="text-[16px] font-medium" style={{ color: "var(--text)" }}>
                  {s.t}
                </h3>
                <p className="mt-1.5 text-[15px] leading-relaxed" style={{ color: "var(--text-2)" }}>
                  {s.d}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function GraphPreview() {
  return (
    <section
      id="graph"
      className="section-reveal px-6 py-20"
      style={{ background: "var(--surface-2)", ["--section-delay" as never]: "0.3s" }}
    >
      <div className="mx-auto max-w-6xl">
        <SectionHeader
          eyebrow="Ripple map"
          title="One change. Every consequence. Mapped to the line."
        />
        <div
          className="mt-10 overflow-hidden rounded-lg border"
          style={{ background: "var(--surface)", borderColor: "var(--border)" }}
        >
          <div
            className="flex items-center justify-between border-b px-5 py-3 font-mono text-[11px]"
            style={{ borderColor: "var(--border)", color: "var(--text-3)" }}
          >
            <span>petclinic / rename Owner → Customer</span>
            <span>8 files · 12 line refs · 91% verified</span>
          </div>
          <div className="aspect-[16/10]">
            <RippleGraph
              selectedId={null}
              onSelect={() => {}}
              filter="all"
              preview
              seed={1}
            />
          </div>
          <div
            className="flex flex-wrap items-center gap-4 border-t px-5 py-3 text-[12px]"
            style={{ borderColor: "var(--border)", color: "var(--text-2)" }}
          >
            <LegendDot color="var(--accent)" label="Origin" square />
            <LegendDot color="var(--risk-high)" label="High risk" />
            <LegendDot color="var(--risk-med)" label="Medium risk" />
            <LegendDot color="var(--risk-low)" label="Low risk" />
          </div>
        </div>
      </div>
    </section>
  );
}

function LegendDot({ color, label, square }: { color: string; label: string; square?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2">
      <span
        className={square ? "" : "rounded-full"}
        style={{ background: color, width: 10, height: 10, borderRadius: square ? 2 : undefined }}
      />
      {label}
    </span>
  );
}

function SectionHeader({ eyebrow, title }: { eyebrow: string; title: string }) {
  return (
    <div className="max-w-2xl">
      <div
        className="font-mono text-[11px] uppercase tracking-[0.18em]"
        style={{ color: "var(--accent)" }}
      >
        {eyebrow}
      </div>
      <h2 className="section-italic mt-2 text-[32px] font-medium leading-tight" style={{ color: "var(--text)" }}>
        {title}
      </h2>
    </div>
  );
}

function Features() {
  const features = [
    {
      icon: IconCode,
      title: "Exact line numbers",
      body: "Every affected file pinned to the line. No fuzzy ‘around line 200.’",
    },
    {
      icon: IconShieldCheck,
      title: "AST grounding",
      body: "Bob’s claims reconciled against JavaParser. Verified refs get a green badge; unverified are flagged honestly.",
    },
    {
      icon: IconChartDots3,
      title: "Risk scoring",
      body: "Each file scored 0–100 with tier (low / medium / high) and cascade depth.",
    },
    {
      icon: IconBrain,
      title: "Bob reasoning, inline",
      body: "Plain-English rationale per high-risk file. No black box, no mystery cascade.",
    },
    {
      icon: IconTestPipe,
      title: "Test gap detection",
      body: "Identifies which existing tests will break and suggests new ones for unverified paths.",
    },
    {
      icon: IconFileExport,
      title: "Exportable everywhere",
      body: "JSON for tooling, PDF for review, Bob session log for audit and submission.",
    },
  ];
  return (
    <section
      id="features"
      className="section-reveal px-6 py-20"
      style={{ ["--section-delay" as never]: "0.4s" }}
    >
      <div className="mx-auto max-w-6xl">
        <SectionHeader eyebrow="Capabilities" title="Built for compiler-grade trust." />
        <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {features.map(({ icon: Icon, title, body }) => (
            <div
              key={title}
              className="lift-card rounded-lg border p-5 transition-colors hover:bg-[var(--surface-2)]"
              style={{ background: "var(--surface)", borderColor: "var(--border)" }}
            >
              <div
                className="flex h-9 w-9 items-center justify-center rounded-md"
                style={{ background: "var(--accent-soft)", color: "var(--accent)" }}
              >
                <Icon size={18} />
              </div>
              <h3 className="mt-4 text-[15px] font-medium" style={{ color: "var(--text)" }}>
                {title}
              </h3>
              <p className="mt-1.5 text-[13px]" style={{ color: "var(--text-2)" }}>
                {body}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function Compare() {
  const cols = ["Cross-Repo", "Cursor", "SonarQube", "IDE Find Usages"];
  const rows: Array<[string, (boolean | string)[]]> = [
    ["Intent-driven ripple tracing", [true, false, false, false]],
    ["Exact line-level output", [true, false, "file", "call sites"]],
    ["Risk scoring (0–100)", [true, false, "rules", false]],
    ["Plain-English reasoning", [true, true, false, false]],
    ["Breaking-test detection", [true, false, false, false]],
    ["AST-verified, no hallucination", [true, false, true, true]],
  ];
  return (
    <section
      id="compare"
      className="section-reveal px-6 py-20"
      style={{ background: "var(--surface)", ["--section-delay" as never]: "0.5s" }}
    >
      <div className="mx-auto max-w-6xl">
        <SectionHeader eyebrow="Differentiation" title="Why nothing else does this." />
        <div
          className="mt-10 overflow-hidden rounded-lg border"
          style={{ borderColor: "var(--border)" }}
        >
          <table className="w-full border-collapse text-left text-[13px]">
            <thead>
              <tr style={{ background: "var(--surface-2)", color: "var(--text-2)" }}>
                <th className="px-4 py-3 font-medium">Capability</th>
                {cols.map((c, i) => (
                  <th
                    key={c}
                    className="px-4 py-3 font-medium"
                    style={{
                      background: i === 0 ? "var(--accent-soft)" : undefined,
                      color: i === 0 ? "var(--accent)" : undefined,
                    }}
                  >
                    {c}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map(([label, vals]) => (
                <tr key={label} className="border-t" style={{ borderColor: "var(--border)" }}>
                  <td className="px-4 py-3" style={{ color: "var(--text)" }}>
                    {label}
                  </td>
                  {vals.map((v, i) => (
                    <td
                      key={i}
                      className="px-4 py-3"
                      style={{
                        background: i === 0 ? "color-mix(in oklab, var(--accent) 4%, transparent)" : undefined,
                      }}
                    >
                      {v === true ? (
                        <IconCircleCheck size={16} style={{ color: "var(--risk-low)" }} />
                      ) : v === false ? (
                        <span style={{ color: "var(--risk-high)" }}>—</span>
                      ) : (
                        <span style={{ color: "var(--text-3)" }}>{v}</span>
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  );
}

function SocialProof() {
  const stats = [
    { v: "~3h → <60s", l: "Manual trace vs. analyzer" },
    { v: "17", l: "Files traced in PetClinic rename scenario" },
    { v: "91%", l: "AST verification rate on Bob output" },
  ];
  return (
    <section
      id="proof"
      className="section-reveal px-6 py-20"
      style={{ ["--section-delay" as never]: "0.6s" }}
    >
      <div className="mx-auto grid max-w-6xl gap-4 sm:grid-cols-3">
        {stats.map((s) => (
          <div
            key={s.l}
            className="lift-card rounded-lg border p-6"
            style={{ borderColor: "var(--border)", background: "var(--surface)" }}
          >
            <div
              className="font-mono text-[36px] font-medium leading-none"
              style={{ color: "var(--accent)" }}
            >
              {s.v}
            </div>
            <div className="mt-3 text-[13px]" style={{ color: "var(--text-2)" }}>
              {s.l}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

function CTAFooter() {
  return (
    <section
      id="cta"
      className="section-reveal px-6 py-24"
      style={{ background: "var(--surface-2)", ["--section-delay" as never]: "0.7s" }}
    >
      <div className="mx-auto max-w-2xl text-center">
        <IconSparkles size={28} style={{ color: "var(--accent)" }} className="mx-auto" />
        <h2 className="mt-4 text-[36px] font-medium leading-tight" style={{ color: "var(--text)" }}>
          Try it on your repo.
        </h2>
        <p className="mt-3 text-[15px]" style={{ color: "var(--text-2)" }}>
          Spring PetClinic ships preloaded — paste your own Java repo to see your codebase mapped.
        </p>
        <div className="mt-7 flex flex-wrap items-center justify-center gap-3">
          <Link
            to="/app"
            className="btn-primary inline-flex items-center gap-2 rounded-lg px-7 py-3.5 text-[14px] font-medium text-white"
            style={{ background: "var(--accent)" }}
          >
            Open the analyzer <IconArrowRight size={16} />
          </Link>
        </div>
      </div>
    </section>
  );
}

function Footer() {
  return (
    <footer
      className="section-reveal border-t px-6 py-6"
      style={{ background: "var(--surface)", borderColor: "var(--border)", ["--section-delay" as never]: "0.8s" }}
    >
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="text-[12px]" style={{ color: "var(--text-2)" }}>
            Ripple
          </span>
        </div>
        <div className="flex items-center gap-4 font-mono text-[11px]" style={{ color: "var(--text-3)" }}>
          <span>IBM Bob Hackathon 2026</span>
        </div>
      </div>
    </footer>
  );
}

function Landing() {
  return (
    <div className="landing-grid" style={{ background: "var(--bg)" }}>
      <Navbar />
      <Hero />
      <ProblemBar />
      <HowItWorks />
      <GraphPreview />
      <Features />
      <Compare />
      <SocialProof />
      <CTAFooter />
      <Footer />
    </div>
  );
}

// avoid lint complaints about unused imports
void IconBolt;
