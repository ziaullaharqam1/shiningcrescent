import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ArrowRight, Headphones, ShieldCheck, Clock3 } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { useBranding } from "../context/BrandingContext";
import ProducePhoto from "../components/ProducePhoto";
import BrandLogo from "../components/BrandLogo";
import SignOutButton from "../components/SignOutButton";
import { authQueryError, errMsg } from "../lib/errors";
import ErrorBanner from "../components/ErrorBanner";
import api from "../api/client";

const SHOWCASE = [
  { hint: "apple", name: "Royal Apple", meta: "14.8° Brix" },
  { hint: "mango", name: "Golden Stone", meta: "Direct Groves" },
  { hint: "pistachio", name: "Akbari Pistachio", meta: "Grade Akbari" },
];

export default function Login() {
  const { login, sendOtp, verifyOtp, user, ready } = useAuth();
  const { branding } = useBranding();
  const nav = useNavigate();
  const [cfg, setCfg] = useState({ googleLoginUrl: "/oauth2/authorization/google", googleEnabled: true, smsMock: true });
  const [mode, setMode] = useState("sms");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [fullName, setFullName] = useState("");
  const [otpSent, setOtpSent] = useState(false);
  const [devCode, setDevCode] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get("error")) setError(authQueryError(params.get("error")));
  }, []);

  useEffect(() => {
    api.get("/auth/public-config").then((r) => setCfg(r.data)).catch(() => {});
  }, []);

  useEffect(() => {
    if (ready && user && sessionStorage.getItem("rc_oauth_pending")) {
      sessionStorage.removeItem("rc_oauth_pending");
      nav(user.console ? "/console" : "/", { replace: true });
    }
  }, [ready, user, nav]);

  function normalizePhone(raw) {
    const trimmed = String(raw || "").trim();
    if (!trimmed) return trimmed;
    if (trimmed.startsWith("+")) return trimmed;
    const digits = trimmed.replace(/\D/g, "");
    if (digits.startsWith("971")) return `+${digits}`;
    if (digits.startsWith("0")) return `+971${digits.slice(1)}`;
    return `+971${digits}`;
  }

  async function requestCode(e) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const data = await sendOtp(normalizePhone(phone));
      setOtpSent(true);
      setDevCode(data.devCode || "");
    } catch (err) {
      setError(errMsg(err, "otp"));
    } finally {
      setBusy(false);
    }
  }

  async function confirmOtp(e) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const u = await verifyOtp(normalizePhone(phone), code, fullName);
      nav(u.console ? "/console" : "/");
    } catch (err) {
      setError(errMsg(err, "otp"));
    } finally {
      setBusy(false);
    }
  }

  async function submitPassword(e) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const u = await login(username, password);
      nav(u.console ? "/console" : "/");
    } catch (err) {
      setError(errMsg(err, "login"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="min-h-screen bg-[#F8F9FA] text-ink flex flex-col justify-between selection:bg-primary selection:text-white">
      <div className="pointer-events-none fixed inset-0 bg-[radial-gradient(circle_at_10%_20%,rgba(235,244,240,0.9)_0%,rgba(253,251,247,0.7)_45%,#F8F9FA_100%)]" />

      <header className="relative z-10 w-full border-b border-line/80 bg-white/80 backdrop-blur-md sticky top-0">
        <div className="max-w-frame mx-auto px-4 sm:px-6 lg:px-8 h-16 sm:h-20 flex items-center justify-between gap-4">
          <BrandLogo to="/" size="md" />
          <div className="hidden md:flex items-center gap-6">
            <div className="flex items-center gap-2.5 text-xs text-ink-muted bg-surface-low px-3.5 py-1.5 rounded-full border border-line">
              <span className="w-2 h-2 rounded-full bg-secondary animate-pulse" />
              <span className="font-medium text-ink">Hub: Dubai Central Cold Depot (Al Aweer)</span>
            </div>
            <div className="h-4 w-px bg-line" />
            <div className="flex items-center gap-4 text-xs font-medium text-ink-muted">
              <span>English (GCC)</span>
              <span>•</span>
              <a className="hover:text-primary transition inline-flex items-center gap-1.5" href="mailto:desk@shiningcrescent.com">
                <Headphones size={14} className="text-primary" />
                Desk Support
              </a>
            </div>
          </div>
          {user && <SignOutButton className="btn-ghost text-xs min-h-9 py-1.5" />}
        </div>
      </header>

      <main className="relative z-10 flex-grow flex items-center justify-center py-10 lg:py-16 px-4 sm:px-6">
        <div className="w-full max-w-5xl bg-white rounded-3xl border border-line shadow-[0_25px_70px_-15px_rgba(19,62,43,0.08),0_10px_30px_-10px_rgba(0,0,0,0.04)] overflow-hidden grid grid-cols-1 lg:grid-cols-12 min-h-[640px]">
          <div className="lg:col-span-6 bg-gradient-to-br from-[#FAFAF8] via-[#F5F8F6] to-[#EEF5F1] p-8 lg:p-10 flex flex-col justify-between border-b lg:border-b-0 lg:border-r border-line relative overflow-hidden">
            <div>
              <div className="inline-flex items-center gap-2 bg-primary/5 text-primary px-3 py-1 rounded-full text-xs font-medium tracking-wide mb-5">
                <span className="w-1.5 h-1.5 rounded-full bg-secondary" />
                UAE Ministry of Climate Change Compliant
              </div>
              <h1 className="font-display text-4xl sm:text-5xl font-medium tracking-tight text-primary leading-[1.08] mb-4">
                Fruit and dry fruit trading without the scramble.
              </h1>
              <p className="text-ink-muted text-sm sm:text-base leading-relaxed max-w-md">
                Direct sovereign orchards, cold-chain transparency, and instant FEFO lot allocations for wholesale and culinary buyers.
              </p>
            </div>

            <div className="my-8 grid grid-cols-3 gap-3">
              {SHOWCASE.map((item) => (
                <div
                  key={item.hint}
                  className="bg-white/90 backdrop-blur rounded-2xl p-2.5 border border-line shadow-soft text-center flex flex-col items-center group hover:border-secondary/40 transition-all"
                >
                  <div className="w-16 h-16 sm:w-20 sm:h-20 rounded-xl bg-white flex items-center justify-center overflow-hidden mb-2 border border-line/70">
                    <ProducePhoto hint={item.hint} className="w-full h-full object-contain group-hover:scale-110 transition duration-300" alt={item.name} />
                  </div>
                  <span className="text-[11px] font-semibold text-ink line-clamp-1">{item.name}</span>
                  <span className="text-[9px] uppercase tracking-wider text-primary/80 font-bold mt-0.5">{item.meta}</span>
                </div>
              ))}
            </div>

            <div className="pt-4 border-t border-line/80 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-[11px] text-ink-muted font-medium">
              <span className="inline-flex items-center gap-1.5">
                <ShieldCheck size={14} className="text-primary" />
                Continuous Cold-Chain (+2.4°C)
              </span>
              <span className="inline-flex items-center gap-1.5">
                <Clock3 size={14} className="text-primary" />
                Same-day Depot Dispatch
              </span>
            </div>
          </div>

          <div className="lg:col-span-6 p-8 lg:p-12 flex flex-col justify-between bg-white">
            <div>
              <div className="mb-6">
                <h2 className="font-display text-3xl sm:text-4xl font-semibold text-primary">Buyer Sign In</h2>
                <p className="text-xs sm:text-sm text-ink-muted mt-1.5 leading-relaxed">
                  Use your registered UAE mobile for instant SMS verification or login using your merchant desk credentials.
                </p>
              </div>

              <div className="flex items-center p-1 bg-surface-low rounded-xl mb-6 text-xs font-medium">
                <button
                  type="button"
                  onClick={() => { setMode("sms"); setError(""); }}
                  className={`flex-1 py-2 rounded-lg transition-all text-center ${
                    mode === "sms" ? "bg-white text-primary font-semibold shadow-soft" : "text-ink-muted hover:text-ink"
                  }`}
                >
                  Mobile Fast Code
                </button>
                <button
                  type="button"
                  onClick={() => { setMode("desk"); setError(""); setOtpSent(false); }}
                  className={`flex-1 py-2 rounded-lg transition-all text-center ${
                    mode === "desk" ? "bg-white text-primary font-semibold shadow-soft" : "text-ink-muted hover:text-ink"
                  }`}
                >
                  Desk Password
                </button>
              </div>

              <ErrorBanner>{error}</ErrorBanner>

              {mode === "sms" && !otpSent && (
                <form className="space-y-4" onSubmit={requestCode}>
                  <div>
                    <label className="block text-xs font-semibold text-ink uppercase tracking-wider mb-2" htmlFor="mobile-number">
                      Mobile Number
                    </label>
                    <div className="relative flex items-center rounded-xl border border-line focus-within:border-primary focus-within:ring-1 focus-within:ring-primary/20 transition bg-white">
                      <div className="flex items-center pl-3.5 pr-2 py-3 border-r border-line bg-surface-low rounded-l-xl text-xs font-medium text-ink shrink-0">
                        <span className="mr-1.5">🇦🇪</span>
                        <span>+971</span>
                      </div>
                      <input
                        id="mobile-number"
                        className="w-full border-0 py-3 px-3.5 text-sm text-ink placeholder:text-ink-faint focus:ring-0 rounded-r-xl outline-none bg-transparent"
                        value={phone}
                        onChange={(e) => setPhone(e.target.value)}
                        placeholder="50 123 4567 or 05…"
                        inputMode="tel"
                        autoComplete="tel"
                        required
                      />
                    </div>
                    <p className="text-[11px] text-ink-muted mt-2 font-light">
                      A 6-digit one-time code will be dispatched via SMS. If you are new, an accredited trade account opens automatically.
                    </p>
                  </div>
                  <button className="w-full bg-primary hover:bg-primary-container text-white font-medium py-3.5 px-4 rounded-xl text-sm transition-all shadow-soft flex items-center justify-center gap-2 group mt-2" type="submit" disabled={busy}>
                    <span>{busy ? "Sending…" : "Send 6–digit code"}</span>
                    <ArrowRight size={16} className="group-hover:translate-x-0.5 transition" />
                  </button>
                </form>
              )}

              {mode === "sms" && otpSent && (
                <form className="space-y-4" onSubmit={confirmOtp}>
                  <p className="text-sm text-ink-muted">Code sent to {normalizePhone(phone)}</p>
                  {devCode && (
                    <p className="text-xs rounded-xl bg-secondary-container px-3 py-2 text-primary">
                      SMS could not be delivered to this number. Use this code: <b className="tracking-widest">{devCode}</b>
                    </p>
                  )}
                  <div>
                    <label className="block text-xs font-semibold text-ink uppercase tracking-wider mb-2">One-time code</label>
                    <input
                      className="w-full rounded-xl border border-line py-3 px-3.5 text-sm tracking-[0.4em] text-center text-lg text-ink placeholder:text-ink-faint focus:border-primary focus:ring-1 focus:ring-primary/20 outline-none"
                      value={code}
                      onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                      placeholder="000000"
                      inputMode="numeric"
                      autoComplete="one-time-code"
                      maxLength={6}
                      required
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-ink uppercase tracking-wider mb-2">Your name</label>
                    <input
                      className="w-full rounded-xl border border-line py-3 px-3.5 text-sm text-ink placeholder:text-ink-faint focus:border-primary focus:ring-1 focus:ring-primary/20 outline-none"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      placeholder="New shoppers only"
                      autoComplete="name"
                    />
                  </div>
                  <button className="w-full bg-primary hover:bg-primary-container text-white font-medium py-3.5 px-4 rounded-xl text-sm transition-all shadow-soft" type="submit" disabled={busy || code.length !== 6}>
                    {busy ? "Checking…" : "Verify and shop"}
                  </button>
                  <button type="button" className="btn-ghost w-full text-xs" disabled={busy} onClick={requestCode}>
                    Resend code
                  </button>
                  <button type="button" className="text-sm text-primary underline underline-offset-2" onClick={() => { setOtpSent(false); setCode(""); setDevCode(""); }}>
                    Use a different number
                  </button>
                </form>
              )}

              {mode === "desk" && (
                <form className="space-y-3.5" onSubmit={submitPassword}>
                  <div>
                    <label className="block text-xs font-semibold text-ink uppercase tracking-wider mb-1.5" htmlFor="username">Username or Commercial Email</label>
                    <input
                      id="username"
                      className="w-full rounded-xl border border-line py-3 px-3.5 text-sm text-ink placeholder:text-ink-faint focus:border-primary focus:ring-1 focus:ring-primary/20 outline-none"
                      value={username}
                      onChange={(e) => setUsername(e.target.value)}
                      placeholder="trade@company.ae"
                      autoComplete="username"
                      required
                    />
                  </div>
                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <label className="text-xs font-semibold text-ink uppercase tracking-wider" htmlFor="password">Password</label>
                    </div>
                    <input
                      id="password"
                      className="w-full rounded-xl border border-line py-3 px-3.5 text-sm text-ink placeholder:text-ink-faint focus:border-primary focus:ring-1 focus:ring-primary/20 outline-none"
                      type="password"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="••••••••••••"
                      autoComplete="current-password"
                      required
                    />
                  </div>
                  <button className="w-full bg-primary hover:bg-primary-container text-white font-medium py-3.5 px-4 rounded-xl text-sm transition-all shadow-soft mt-2" type="submit" disabled={busy}>
                    {busy ? "Signing in…" : "Sign In with Desk Password"}
                  </button>
                </form>
              )}

              <div className="relative my-6">
                <div className="absolute inset-0 flex items-center">
                  <div className="w-full border-t border-line" />
                </div>
                <div className="relative flex justify-center text-[11px] uppercase tracking-widest text-ink-faint font-semibold">
                  <span className="bg-white px-3">or continue with</span>
                </div>
              </div>

              <a
                className="w-full flex items-center justify-center gap-3 px-4 py-3 border border-line hover:border-ink-faint rounded-xl text-xs font-semibold text-ink bg-white hover:bg-surface-low transition shadow-soft"
                href={cfg.googleLoginUrl || "/oauth2/authorization/google"}
              >
                <GoogleMark />
                <span>Continue with Google</span>
              </a>
            </div>

            <div className="mt-8 pt-5 border-t border-line flex flex-col sm:flex-row items-center justify-between text-xs text-ink-muted gap-3">
              <p>
                New trading buyer?
                <Link className="text-primary hover:text-secondary font-semibold underline underline-offset-2 ml-1" to="/register">
                  Create an account
                </Link>
              </p>
              <span className="text-[11px] text-ink-faint">Terms & Phytosanitary Policy</span>
            </div>
          </div>
        </div>
      </main>

      <footer className="relative z-10 w-full border-t border-line/70 py-6 px-4 sm:px-8 text-center text-xs text-ink-muted">
        <div className="max-w-frame mx-auto flex flex-col sm:flex-row items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="w-1.5 h-1.5 rounded-full bg-secondary" />
            <span className="font-medium text-ink">{branding?.hubLine || "Dubai Central Hub: Al Aweer Produce Exchange · Abu Dhabi Mina Zayed"}</span>
          </div>
          <p className="text-[11px] text-ink-faint">
            © {new Date().getFullYear()} Shining Crescent Trading LLC. Accredited Sovereign Exchange.
          </p>
        </div>
      </footer>
    </div>
  );
}

function GoogleMark() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true">
      <path fill="#4285F4" d="M23.745 12.27c0-.7-.06-1.4-.19-2.07H12v4.51h6.6c-.29 1.52-1.14 2.82-2.4 3.68v3.05h3.88c2.27-2.09 3.66-5.17 3.66-9.17z" />
      <path fill="#34A853" d="M12 24c3.24 0 5.95-1.08 7.93-2.91l-3.88-3.05c-1.08.72-2.45 1.16-4.05 1.16-3.12 0-5.77-2.1-6.72-4.93H1.25v3.15C3.26 21.36 7.33 24 12 24z" />
      <path fill="#FBBC05" d="M5.28 14.27c-.25-.72-.38-1.49-.38-2.27s.13-1.55.38-2.27V6.58H1.25C.45 8.16 0 9.94 0 12s.45 3.84 1.25 5.42l4.03-3.15z" />
      <path fill="#EA4335" d="M12 4.75c1.77 0 3.35.61 4.6 1.8l3.42-3.42C17.95 1.19 15.24 0 12 0 7.33 0 3.26 2.64 1.25 6.58l4.03 3.15c.95-2.83 3.6-4.98 6.72-4.98z" />
    </svg>
  );
}
