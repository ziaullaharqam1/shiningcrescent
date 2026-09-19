import { useEffect, useMemo, useRef, useState } from "react";
import { Navigate } from "react-router-dom";
import api from "../../api/client";
import { errMsg } from "../../lib/errors";
import ErrorBanner from "../../components/ErrorBanner";
import { useAuth } from "../../context/AuthContext";

function tone(severity) {
  if (severity === "critical") return "border-red-200 bg-red-50 text-red-900";
  if (severity === "warn") return "border-amber-200 bg-amber-50 text-amber-950";
  return "border-grove-100 bg-grove-50 text-grove-800";
}

function pill(ok) {
  return ok ? "bg-grove-100 text-grove-800" : "bg-red-100 text-red-800";
}

function actionLabel(id) {
  return {
    gc: "Run GC",
    "thread-dump": "Thread dump",
    "apply-gc-profile": "Save GC profile",
    "restart-api": "Restart API",
    "reconnect-stripe": "Reconnect Stripe",
    "restart-stripe": "Reconnect Stripe",
    "retry-network": "Retry network",
    "profile-start": "Start profile",
    "profile-stop": "Stop profile",
  }[id] || id;
}

export default function Ops() {
  const { user, ready } = useAuth();
  const isAdmin = user?.roles?.includes("SUPER_ADMIN");
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [ok, setOk] = useState("");
  const [busy, setBusy] = useState("");
  const [gcProfile, setGcProfile] = useState("low-pause");
  const [termIn, setTermIn] = useState("");
  const [termOut, setTermOut] = useState("Type help, then Enter. Try: health  |  logs 80 ERROR  |  stripe  |  restart api\n");
  const [follow, setFollow] = useState(true);
  const [filter, setFilter] = useState("");
  const cursor = useRef(0);
  const termRef = useRef(null);
  const hist = useRef([]);
  const histIdx = useRef(-1);

  async function load() {
    try {
      const { data: d } = await api.get("/console/ops");
      setData(d);
      setError("");
    } catch (e) {
      setError(errMsg(e));
    }
  }

  async function pullLogs(reset) {
    try {
      const { data: d } = await api.get("/console/ops/logs", {
        params: { limit: reset ? 120 : 80, q: filter, after: reset ? undefined : cursor.current || undefined },
      });
      cursor.current = d.cursor || cursor.current;
      const rows = Array.isArray(d.lines) ? d.lines : [];
      if (!rows.length) return;
      const text = rows.map((l) => `${l.at} ${l.level} ${l.logger} ${l.message}`).join("\n");
      setTermOut((prev) => {
        const next = reset ? `${text}\n` : `${prev}${prev.endsWith("\n") ? "" : "\n"}${text}\n`;
        return next.slice(-40000);
      });
    } catch {
      /* keep last terminal text */
    }
  }

  useEffect(() => {
    if (ready && isAdmin) load();
  }, [ready, isAdmin]);

  useEffect(() => {
    if (!ready || !isAdmin) return;
    pullLogs(true);
    const t = setInterval(() => {
      load();
      if (follow) pullLogs(false);
    }, 4000);
    return () => clearInterval(t);
  }, [ready, isAdmin, follow, filter]);

  useEffect(() => {
    if (termRef.current) termRef.current.scrollTop = termRef.current.scrollHeight;
  }, [termOut]);

  async function act(action, extra = {}) {
    if (action === "restart-api" && !window.confirm("Restart the API now? The console will drop for a few seconds.")) return;
    setBusy(action);
    setError("");
    setOk("");
    try {
      const body = { action, ...extra };
      if (action === "apply-gc-profile") body.profile = extra.profile || gcProfile;
      const { data: d } = await api.post("/console/ops/actions", body);
      setOk(d.output || d.detail || "Done.");
      if (d.output && String(d.output).includes("\n")) {
        setTermOut((p) => `${p}\n$ ${action}\n${d.output}\n`);
      }
      await load();
    } catch (e) {
      setError(errMsg(e));
    } finally {
      setBusy("");
    }
  }

  async function runTerm(e) {
    e?.preventDefault();
    const command = termIn.trim();
    if (!command) return;
    hist.current.push(command);
    histIdx.current = hist.current.length;
    setTermIn("");
    setTermOut((p) => `${p}$ ${command}\n`);
    if (command === "clear") {
      setTermOut("");
      return;
    }
    setBusy("term");
    try {
      const { data: d } = await api.post("/console/ops/terminal", { command });
      setTermOut((p) => `${p}${d.output || ""}\n`);
      if (command.startsWith("restart api")) {
        setOk("API restart requested.");
      }
    } catch (err) {
      setTermOut((p) => `${p}${errMsg(err)}\n`);
    } finally {
      setBusy("");
    }
  }

  const findings = useMemo(() => (Array.isArray(data?.findings) ? data.findings : []), [data]);
  const jvm = data?.jvm || {};
  const db = data?.database || {};
  const stripe = data?.stripe || {};
  const network = data?.network || {};
  const kafka = data?.kafka || {};

  if (!ready) return <p className="p-6">Loading…</p>;
  if (!isAdmin) return <Navigate to="/console" replace />;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-3xl md:text-4xl text-grove-800">Ops</h1>
          <p className="text-grove-600 mt-1 text-sm">Super admin only. Backend health, Stripe, JVM, network, logs, and restarts.</p>
        </div>
        <button className="btn-ghost" onClick={load} disabled={!!busy}>Refresh</button>
      </div>
      <ErrorBanner>{error}</ErrorBanner>
      {ok && <p className="text-sm text-grove-800 bg-grove-50 border border-grove-100 rounded-xl px-3 py-2">{ok}</p>}

      <div className="grid md:grid-cols-2 xl:grid-cols-4 gap-3">
        <Stat title="API CPU" value={`${jvm.processCpuPct ?? "—"}%`} hint={`${jvm.threads ?? "—"} threads`} warn={jvm.processCpuPct >= 60} />
        <Stat title="Heap" value={`${jvm.heapUsedMb ?? "—"} / ${jvm.heapMaxMb ?? "—"} MB`} hint={`${jvm.heapUsedPct ?? "—"}% used`} warn={jvm.heapUsedPct >= 75} />
        <Stat title="GC overhead" value={`${jvm.gcOverheadPct ?? "—"}%`} hint={`${jvm.gcCount ?? 0} collections`} warn={jvm.gcOverheadPct >= 8} />
        <Stat title="Uptime" value={fmtUptime(data?.uptimeMs)} hint={`pid ${data?.pid ?? "—"}`} />
      </div>

      <section className="card p-5 space-y-3">
        <h2 className="font-display text-2xl text-grove-800">Findings and actions</h2>
        <div className="space-y-2">
          {findings.map((f, i) => (
            <div key={`${f.area}-${i}`} className={`rounded-xl border px-3 py-3 ${tone(f.severity)}`}>
              <p className="text-sm font-semibold capitalize">{f.area} · {f.severity}</p>
              <p className="text-sm mt-1">{f.summary}</p>
              <p className="text-xs mt-1 opacity-80">{f.advice}</p>
              {Array.isArray(f.actions) && f.actions.length > 0 && (
                <div className="flex flex-wrap gap-2 mt-2">
                  {f.actions.map((a) => (
                    <button
                      key={a}
                      className="btn-ghost text-xs min-h-9 px-3"
                      disabled={!!busy}
                      onClick={() => act(a)}
                    >
                      {busy === a ? "Working…" : actionLabel(a)}
                    </button>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      </section>

      <div className="grid lg:grid-cols-2 gap-4">
        <section className="card p-5 space-y-3">
          <h2 className="font-display text-2xl text-grove-800">Backend</h2>
          <Row label="Database" ok={db.ok} detail={`${db.detail || ""} · ${db.latencyMs ?? "—"} ms · pool ${db.active ?? "—"}/${db.maxPool ?? "—"}`} />
          <Row label="Kafka" ok={kafka.ok !== false} detail={kafka.detail || (kafka.enabled ? kafka.bootstrap : "Disabled")} />
          <p className="text-xs text-grove-600">{data?.restartHint}</p>
          <div className="flex flex-wrap gap-2">
            <button className="btn-primary" disabled={!!busy} onClick={() => act("restart-api")}>
              {busy === "restart-api" ? "Restarting…" : "Restart API"}
            </button>
            <button className="btn-ghost" disabled={!!busy} onClick={() => act("gc")}>Run GC</button>
            <button className="btn-ghost" disabled={!!busy} onClick={() => act("thread-dump")}>Thread dump</button>
          </div>
        </section>

        <section className="card p-5 space-y-3">
          <h2 className="font-display text-2xl text-grove-800">Stripe payments</h2>
          <Row label="Mode" ok={!stripe.mock && stripe.reachable} detail={stripe.mock ? "Mock checkout" : stripe.status || "unknown"} />
          <Row label="API" ok={stripe.reachable || stripe.mock} detail={stripe.detail} />
          <Row label="Webhook secret" ok={stripe.webhookConfigured} detail={stripe.lastWebhook ? `${stripe.lastWebhook.type} @ ${stripe.lastWebhook.at}` : "No webhook seen this process"} />
          {Array.isArray(stripe.recentIntents) && stripe.recentIntents.length > 0 && (
            <ul className="text-xs text-grove-700 space-y-1">
              {stripe.recentIntents.map((pi) => (
                <li key={pi.id} className="font-mono">{pi.id} · {pi.status} · {pi.amount} {pi.currency}</li>
              ))}
            </ul>
          )}
          <button className="btn-primary" disabled={!!busy} onClick={() => act("reconnect-stripe")}>
            {busy === "reconnect-stripe" ? "Checking…" : "Reconnect / recheck Stripe"}
          </button>
        </section>
      </div>

      <div className="grid lg:grid-cols-2 gap-4">
        <section className="card p-5 space-y-3">
          <h2 className="font-display text-2xl text-grove-800">GC tuning</h2>
          <p className="text-sm text-grove-600">Saving a profile writes JVM flags. Restart the API (Docker/systemd) to apply them.</p>
          {data?.pendingJvmOptions ? <p className="text-xs font-mono bg-gray-50 rounded-lg px-2 py-2">Pending: {data.pendingJvmOptions}</p> : null}
          <select className="input" value={gcProfile} onChange={(e) => setGcProfile(e.target.value)}>
            {Object.entries(data?.gcProfiles || {}).map(([k, v]) => (
              <option key={k} value={k}>{k} — {v}</option>
            ))}
          </select>
          <button className="btn-primary" disabled={!!busy} onClick={() => act("apply-gc-profile", { profile: gcProfile })}>
            Save profile
          </button>
        </section>

        <section className="card p-5 space-y-3">
          <h2 className="font-display text-2xl text-grove-800">Profiling & network</h2>
          <Row label="JFR" ok={!data?.profiling?.running || true} detail={data?.profiling?.running ? `Running ${data.profiling.name}` : "Idle"} />
          <Row label="Postgres TCP" ok={network.postgres?.ok} detail={fmtNet(network.postgres)} />
          <Row label="Kafka TCP" ok={network.kafka?.skipped || network.kafka?.ok} detail={network.kafka?.skipped ? "Skipped" : fmtNet(network.kafka)} />
          <Row label="Stripe TCP" ok={network.stripe?.ok} detail={fmtNet(network.stripe)} />
          <div className="flex flex-wrap gap-2">
            <button className="btn-ghost" disabled={!!busy} onClick={() => act(data?.profiling?.running ? "profile-stop" : "profile-start")}>
              {data?.profiling?.running ? "Stop profile" : "Start JFR profile"}
            </button>
            <button className="btn-ghost" disabled={!!busy} onClick={() => act("retry-network")}>Retry network</button>
          </div>
        </section>
      </div>

      <section className="card p-0 overflow-hidden">
        <div className="flex flex-wrap items-center gap-2 px-4 py-3 border-b border-gray-800 bg-zinc-900 text-zinc-100">
          <h2 className="font-display text-xl">Ops terminal</h2>
          <label className="ml-auto text-xs flex items-center gap-2">
            <input type="checkbox" checked={follow} onChange={(e) => setFollow(e.target.checked)} />
            Follow logs
          </label>
          <input
            className="rounded-lg bg-zinc-800 border border-zinc-700 px-2 py-1 text-xs min-h-9 w-40"
            placeholder="Filter logs"
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
          <button className="text-xs underline" onClick={() => { cursor.current = 0; pullLogs(true); }}>Reload logs</button>
        </div>
        <pre ref={termRef} className="h-[420px] overflow-auto bg-zinc-950 text-emerald-300 text-xs leading-5 p-4 font-mono whitespace-pre-wrap">{termOut}</pre>
        <form onSubmit={runTerm} className="flex gap-2 bg-zinc-900 p-3">
          <span className="text-emerald-400 font-mono pt-2">$</span>
          <input
            className="flex-1 rounded-lg bg-zinc-800 border border-zinc-700 text-zinc-100 px-3 py-2 font-mono text-sm outline-none"
            value={termIn}
            placeholder="help"
            disabled={busy === "term"}
            onChange={(e) => setTermIn(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "ArrowUp") {
                e.preventDefault();
                histIdx.current = Math.max(0, histIdx.current - 1);
                setTermIn(hist.current[histIdx.current] || "");
              }
              if (e.key === "ArrowDown") {
                e.preventDefault();
                histIdx.current = Math.min(hist.current.length, histIdx.current + 1);
                setTermIn(hist.current[histIdx.current] || "");
              }
            }}
          />
          <button className="btn-primary" disabled={busy === "term"}>Run</button>
        </form>
      </section>
    </div>
  );
}

function Stat({ title, value, hint, warn }) {
  return (
    <div className={`card p-4 ${warn ? "border-amber-200" : ""}`}>
      <p className="text-xs uppercase tracking-wide text-grove-600">{title}</p>
      <p className="font-display text-2xl text-grove-800 mt-1">{value}</p>
      <p className="text-xs text-grove-600 mt-1">{hint}</p>
    </div>
  );
}

function Row({ label, ok, detail }) {
  return (
    <div className="flex gap-3 items-start">
      <span className={`text-xs font-semibold rounded-full px-2 py-1 ${pill(ok)}`}>{ok ? "OK" : "ISSUE"}</span>
      <div>
        <p className="text-sm font-semibold text-grove-800">{label}</p>
        <p className="text-xs text-grove-600">{detail}</p>
      </div>
    </div>
  );
}

function fmtNet(p) {
  if (!p) return "—";
  return `${p.detail || ""} · ${p.latencyMs ?? "—"} ms`;
}

function fmtUptime(ms) {
  if (!ms && ms !== 0) return "—";
  const s = Math.floor(ms / 1000);
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (h > 48) return `${Math.floor(h / 24)}d ${h % 24}h`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m ${s % 60}s`;
}
