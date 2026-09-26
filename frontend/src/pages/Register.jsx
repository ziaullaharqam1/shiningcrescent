import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowRight } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { errMsg } from "../lib/errors";
import ErrorBanner from "../components/ErrorBanner";
import BrandLogo from "../components/BrandLogo";

const FIELDS = [
  { key: "fullName", label: "Full name", placeholder: "As on trade papers", type: "text", autoComplete: "name" },
  { key: "email", label: "Commercial email", placeholder: "trade@company.ae", type: "email", autoComplete: "email" },
  { key: "phone", label: "UAE mobile", placeholder: "+971 50 123 4567", type: "tel", autoComplete: "tel" },
  { key: "username", label: "Username", placeholder: "buyer desk login", type: "text", autoComplete: "username" },
  { key: "password", label: "Password", placeholder: "••••••••••••", type: "password", autoComplete: "new-password" },
];

export default function Register() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [form, setForm] = useState({ username: "", password: "", fullName: "", email: "", phone: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      await register(form);
      nav("/");
    } catch (err) {
      setError(errMsg(err, "register"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="min-h-screen bg-surface text-ink flex flex-col">
      <div className="pointer-events-none fixed inset-0 bg-[radial-gradient(circle_at_10%_20%,rgba(235,244,240,0.9)_0%,rgba(253,251,247,0.7)_45%,#fafaf9_100%)]" />

      <header className="relative z-10 w-full border-b border-line/80 bg-white/80 backdrop-blur-md">
        <div className="max-w-frame mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center">
          <BrandLogo to="/" size="md" />
        </div>
      </header>

      <main className="relative z-10 flex-1 flex items-center justify-center py-10 px-4">
        <form
          onSubmit={submit}
          className="w-full max-w-md bg-white rounded-3xl border border-line shadow-[0_25px_70px_-15px_rgba(19,62,43,0.08)] p-8 sm:p-10 space-y-4"
        >
          <div>
            <p className="label-overline mb-2">Accredited trade account</p>
            <h1 className="page-title text-3xl">Create buyer account</h1>
            <p className="page-hint">Open a merchant desk for FEFO lots, wholesale sacks, and same-day depot dispatch.</p>
          </div>
          <ErrorBanner>{error}</ErrorBanner>
          {FIELDS.map((f) => (
            <div key={f.key}>
              <label className="label-field" htmlFor={f.key}>{f.label}</label>
              <input
                id={f.key}
                className="input"
                type={f.type}
                placeholder={f.placeholder}
                autoComplete={f.autoComplete}
                value={form[f.key]}
                onChange={(e) => setForm({ ...form, [f.key]: e.target.value })}
                required={f.key !== "email"}
              />
            </div>
          ))}
          <button className="btn-primary w-full group" type="submit" disabled={busy}>
            <span>{busy ? "Opening…" : "Join the market floor"}</span>
            <ArrowRight size={16} className="group-hover:translate-x-0.5 transition" />
          </button>
          <p className="text-xs text-ink-muted text-center pt-2">
            Already trading?
            <Link className="text-primary font-semibold underline underline-offset-2 ml-1" to="/login">
              Sign in
            </Link>
          </p>
        </form>
      </main>
    </div>
  );
}
