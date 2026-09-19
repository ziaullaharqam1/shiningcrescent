import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import ProducePhoto from "../components/ProducePhoto";
import BrandLogo from "../components/BrandLogo";
import SignOutButton from "../components/SignOutButton";
import { authQueryError, errMsg } from "../lib/errors";
import ErrorBanner from "../components/ErrorBanner";
import api from "../api/client";

export default function Login() {
  const { login, sendOtp, verifyOtp, user, ready } = useAuth();
  const nav = useNavigate();
  const [cfg, setCfg] = useState({ googleLoginUrl: "/oauth2/authorization/google", googleEnabled: true, smsMock: true });
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

  async function requestCode(e) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const data = await sendOtp(phone);
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
      const u = await verifyOtp(phone, code, fullName);
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
    try {
      const u = await login(username, password);
      nav(u.console ? "/console" : "/");
    } catch (err) {
      setError(errMsg(err, "login"));
    }
  }

  return (
    <div className="min-h-screen grid lg:grid-cols-2 bg-white">
      <div className="hidden lg:flex flex-col justify-between bg-white p-10 xl:p-14 border-r border-gray-100">
        <BrandLogo to="/" size="lg" />
        <div className="grid grid-cols-3 gap-3 my-8">
          {["apple", "mango", "orange", "dates", "apricot", "pistachio"].map((h) => (
            <div key={h} className="aspect-square rounded-2xl overflow-hidden border border-gray-100">
              <ProducePhoto hint={h} className="h-full w-full" alt={h} />
            </div>
          ))}
        </div>
        <div>
          <h1 className="font-display text-4xl xl:text-5xl leading-tight text-grove-800">Fruit and dry fruit trading without the scramble.</h1>
          <p className="mt-4 text-gray-600 max-w-md text-lg">
            Sign in with your mobile, Google, or a desk login. Then cart, pay with card or cash, and a rider.
          </p>
        </div>
      </div>
      <div className="p-6 sm:p-10 flex items-center justify-center">
        <div className="w-full max-w-md space-y-4">
          <BrandLogo to="/" size="md" className="lg:hidden" />
          <div className="flex items-center justify-between gap-3">
            <h2 className="font-display text-3xl text-grove-800">Sign in</h2>
            {user && <SignOutButton />}
          </div>
          <p className="text-sm text-grove-600">Use a mobile number to shop and place orders. A 6-digit SMS code is enough — we open a buyer account if you are new.</p>
          <ErrorBanner>{error}</ErrorBanner>

          {!otpSent ? (
            <form onSubmit={requestCode} className="space-y-3">
              <input
                className="input"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="Mobile (+9715… or 05…)"
                inputMode="tel"
                autoComplete="tel"
                required
              />
              <button className="btn-primary w-full" type="submit" disabled={busy}>
                {busy ? "Sending…" : "Send 6-digit code"}
              </button>
            </form>
          ) : (
            <form onSubmit={confirmOtp} className="space-y-3">
              <p className="text-sm text-grove-600">Code sent to {phone}</p>
              {devCode && (
                <p className="text-xs rounded-xl bg-grove-100 px-3 py-2">
                  SMS could not be delivered to this number (Twilio trial often blocks unverified or international numbers). Use this code: <b className="tracking-widest">{devCode}</b>
                </p>
              )}
              <input
                className="input tracking-[0.4em] text-center text-lg"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                placeholder="000000"
                inputMode="numeric"
                autoComplete="one-time-code"
                maxLength={6}
                required
              />
              <input
                className="input"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Your name (new shoppers)"
                autoComplete="name"
              />
              <button className="btn-primary w-full" type="submit" disabled={busy || code.length !== 6}>
                {busy ? "Checking…" : "Verify and shop"}
              </button>
              <button
                type="button"
                className="btn-ghost w-full text-xs"
                disabled={busy}
                onClick={requestCode}
              >
                Resend code
              </button>
              <button type="button" className="text-sm underline" onClick={() => { setOtpSent(false); setCode(""); setDevCode(""); }}>
                Use a different number
              </button>
            </form>
          )}

          <p className="text-sm">New buyer with email? <Link className="underline text-grove-700" to="/register">Create an account</Link></p>

          <form onSubmit={submitPassword} className="space-y-3 pt-4 border-t border-grove-100">
            <p className="text-xs uppercase tracking-wide text-grove-600">Username and password</p>
            <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} placeholder="Username" autoComplete="username" />
            <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password" autoComplete="current-password" />
            <button className="btn-primary w-full" type="submit">Login</button>
          </form>

          <div className="flex items-center gap-3 text-xs uppercase tracking-wide text-grove-600 pt-1">
            <span className="flex-1 h-px bg-grove-100" />
            or
            <span className="flex-1 h-px bg-grove-100" />
          </div>
          <a
            className="btn-ghost w-full"
            href={cfg.googleLoginUrl || "/oauth2/authorization/google"}
          >
            <GoogleMark />
            Continue with Google
          </a>
          {cfg.googleRedirectUri && (
            <p className="text-xs text-grove-600">
              Google Cloud must allow this redirect URI on the same OAuth client: {cfg.googleRedirectUri}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

function GoogleMark() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
      <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.92c1.7-1.57 2.68-3.88 2.68-6.62z" />
      <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.92-2.26c-.81.54-1.84.86-3.04.86-2.34 0-4.32-1.58-5.03-3.71H.96v2.33A8.99 8.99 0 0 0 9 18z" />
      <path fill="#FBBC05" d="M3.97 10.71A5.41 5.41 0 0 1 3.68 9c0-.59.1-1.17.26-1.71V4.96H.96A8.99 8.99 0 0 0 0 9c0 1.45.35 2.82.96 4.04l3.01-2.33z" />
      <path fill="#EA4335" d="M9 3.58c1.32 0 2.5.45 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A8.99 8.99 0 0 0 .96 4.96L3.97 7.3C4.68 5.16 6.66 3.58 9 3.58z" />
    </svg>
  );
}
