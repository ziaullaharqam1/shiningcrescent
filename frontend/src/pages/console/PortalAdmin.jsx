import { useEffect, useState } from "react";
import api from "../../api/client";
import { errMsg } from "../../lib/errors";
import ErrorBanner from "../../components/ErrorBanner";
import ProducePhoto from "../../components/ProducePhoto";
import { useAuth } from "../../context/AuthContext";
import { applyBrandTheme, DEFAULT_BRAND, useBranding } from "../../context/BrandingContext";
import { Navigate } from "react-router-dom";

export default function PortalAdmin() {
  const { user, ready } = useAuth();
  const { reload: reloadBrand } = useBranding();
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [ok, setOk] = useState("");
  const [brand, setBrand] = useState(DEFAULT_BRAND);
  const [smtp, setSmtp] = useState({
    smtpHost: "",
    smtpPort: 587,
    smtpUsername: "",
    smtpPassword: "",
    smtpFrom: "",
    smtpAuth: true,
    smtpStartTls: true,
    testTo: "",
  });
  const [busy, setBusy] = useState("");

  const isAdmin = user?.roles?.includes("SUPER_ADMIN");

  async function load() {
    setError("");
    try {
      const { data: d } = await api.get("/console/portal");
      setData(d);
      setBrand({
        brandName: d.brandName || DEFAULT_BRAND.brandName,
        logoUrl: d.logoUrl || "",
        primaryColor: d.primaryColor || DEFAULT_BRAND.primaryColor,
        hoverColor: d.hoverColor || DEFAULT_BRAND.hoverColor,
        softColor: d.softColor || DEFAULT_BRAND.softColor,
        inkColor: d.inkColor || DEFAULT_BRAND.inkColor,
      });
      setSmtp((s) => ({
        ...s,
        smtpHost: d.smtpHost || "",
        smtpPort: d.smtpPort || 587,
        smtpUsername: d.smtpUsername || "",
        smtpFrom: d.smtpFrom || "",
        smtpAuth: d.smtpAuth !== false,
        smtpStartTls: d.smtpStartTls !== false,
        testTo: d.smtpFrom || user?.email || "",
      }));
    } catch (e) {
      setError(errMsg(e));
    }
  }

  useEffect(() => {
    if (ready && isAdmin) load();
  }, [ready, isAdmin]);

  if (!ready) return <p className="p-6">Loading…</p>;
  if (!isAdmin) return <Navigate to="/console" replace />;

  async function saveBrand(e) {
    e.preventDefault();
    setBusy("brand");
    setError("");
    setOk("");
    try {
      const { data: d } = await api.put("/console/portal/brand", brand);
      applyBrandTheme(d);
      await reloadBrand();
      setOk("Brand and colors saved.");
    } catch (err) {
      setError(errMsg(err));
    } finally {
      setBusy("");
    }
  }

  async function uploadLogo(file) {
    if (!file) return;
    setBusy("logo");
    setError("");
    setOk("");
    try {
      const fd = new FormData();
      fd.append("file", file);
      const { data: d } = await api.post("/console/portal/logo", fd);
      setBrand((b) => ({ ...b, logoUrl: d.logoUrl }));
      await reloadBrand();
      setOk("Logo updated.");
    } catch (err) {
      setError(errMsg(err));
    } finally {
      setBusy("");
    }
  }

  async function uploadProduct(id, file) {
    if (!file) return;
    setBusy(`p-${id}`);
    setError("");
    setOk("");
    try {
      const fd = new FormData();
      fd.append("file", file);
      await api.post(`/console/portal/products/${id}/image`, fd);
      await load();
      setOk("Product photo updated.");
    } catch (err) {
      setError(errMsg(err));
    } finally {
      setBusy("");
    }
  }

  async function saveSmtp(e) {
    e.preventDefault();
    setBusy("smtp");
    setError("");
    setOk("");
    try {
      await api.put("/console/portal/smtp", smtp);
      setOk("SMTP settings saved.");
      await load();
    } catch (err) {
      setError(errMsg(err));
    } finally {
      setBusy("");
    }
  }

  async function testSmtp(e) {
    e.preventDefault();
    setBusy("test");
    setError("");
    setOk("");
    try {
      const { data: d } = await api.post("/console/portal/smtp/test", { to: smtp.testTo });
      setOk(`Test email sent to ${d.to}.`);
    } catch (err) {
      setError(errMsg(err));
    } finally {
      setBusy("");
    }
  }

  const products = Array.isArray(data?.products) ? data.products : [];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl md:text-4xl text-grove-800">Portal admin</h1>
        <p className="text-grove-600 mt-1 text-sm">Super admin only. Theme, logo, product photos, and SMTP.</p>
      </div>
      <ErrorBanner>{error}</ErrorBanner>
      {ok && <p className="text-sm text-grove-800 bg-grove-50 border border-grove-100 rounded-xl px-3 py-2">{ok}</p>}

      <form onSubmit={saveBrand} className="card p-5 space-y-4">
        <h2 className="font-display text-2xl text-grove-800">Brand and colors</h2>
        <label className="block text-sm font-semibold text-grove-700">Brand name</label>
        <input className="input" value={brand.brandName} onChange={(e) => setBrand({ ...brand, brandName: e.target.value })} />
        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-3">
          {[
            ["primaryColor", "Accent"],
            ["hoverColor", "Accent hover"],
            ["softColor", "Soft fill"],
            ["inkColor", "Text"],
          ].map(([k, label]) => (
            <label key={k} className="text-sm font-semibold text-grove-700">
              {label}
              <input
                className="mt-1 h-11 w-full rounded-xl border border-grove-100 bg-white"
                type="color"
                value={brand[k]}
                onChange={(e) => setBrand({ ...brand, [k]: e.target.value })}
              />
            </label>
          ))}
        </div>
        <button className="btn-primary" disabled={busy === "brand"}>{busy === "brand" ? "Saving…" : "Save theme"}</button>
      </form>

      <section className="card p-5 space-y-3">
        <h2 className="font-display text-2xl text-grove-800">Logo</h2>
        <div className="flex items-center gap-4">
          {brand.logoUrl ? (
            <img src={brand.logoUrl} alt="Logo" className="h-16 w-16 rounded-full object-cover border border-grove-100" />
          ) : (
            <p className="text-sm text-grove-600">Using the default leaf mark.</p>
          )}
          <label className="btn-ghost cursor-pointer">
            {busy === "logo" ? "Uploading…" : "Upload logo"}
            <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" className="hidden" onChange={(e) => uploadLogo(e.target.files?.[0])} />
          </label>
        </div>
      </section>

      <section className="card p-5 space-y-4">
        <h2 className="font-display text-2xl text-grove-800">Product photos</h2>
        <p className="text-sm text-grove-600">Replace the photo shown on the market, cart, and product page.</p>
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {products.map((p) => (
            <div key={p.id} className="border border-grove-100 rounded-2xl overflow-hidden">
              <div className="h-32 bg-white">
                <ProducePhoto hint={p.imageHint} imageUrl={p.imageUrl} name={p.name} sku={p.sku} className="h-full w-full" />
              </div>
              <div className="p-3 space-y-2">
                <p className="font-semibold text-sm text-grove-800">{p.name}</p>
                <p className="text-xs text-grove-600">{p.sku}</p>
                <label className="btn-ghost text-xs w-full cursor-pointer">
                  {busy === `p-${p.id}` ? "Uploading…" : "Change photo"}
                  <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" className="hidden" onChange={(e) => uploadProduct(p.id, e.target.files?.[0])} />
                </label>
              </div>
            </div>
          ))}
        </div>
      </section>

      <form onSubmit={saveSmtp} className="card p-5 space-y-3">
        <h2 className="font-display text-2xl text-grove-800">SMTP</h2>
        <p className="text-sm text-grove-600">Used for test mail from this page. Leave password blank to keep the saved one.</p>
        <input className="input" placeholder="Host (smtp.example.com)" value={smtp.smtpHost} onChange={(e) => setSmtp({ ...smtp, smtpHost: e.target.value })} />
        <input className="input" placeholder="Port" value={smtp.smtpPort} onChange={(e) => setSmtp({ ...smtp, smtpPort: e.target.value })} />
        <input className="input" placeholder="Username" value={smtp.smtpUsername} onChange={(e) => setSmtp({ ...smtp, smtpUsername: e.target.value })} />
        <input className="input" type="password" placeholder={data?.smtpPasswordSet ? "Password (unchanged if empty)" : "Password"} value={smtp.smtpPassword} onChange={(e) => setSmtp({ ...smtp, smtpPassword: e.target.value })} />
        <input className="input" placeholder="From address" value={smtp.smtpFrom} onChange={(e) => setSmtp({ ...smtp, smtpFrom: e.target.value })} />
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={smtp.smtpAuth} onChange={(e) => setSmtp({ ...smtp, smtpAuth: e.target.checked })} /> SMTP auth</label>
        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={smtp.smtpStartTls} onChange={(e) => setSmtp({ ...smtp, smtpStartTls: e.target.checked })} /> STARTTLS</label>
        <button className="btn-primary" disabled={busy === "smtp"}>{busy === "smtp" ? "Saving…" : "Save SMTP"}</button>
      </form>

      <form onSubmit={testSmtp} className="card p-5 space-y-3">
        <h2 className="font-display text-2xl text-grove-800">Send a test email</h2>
        <input className="input" type="email" placeholder="To" value={smtp.testTo} onChange={(e) => setSmtp({ ...smtp, testTo: e.target.value })} required />
        <button className="btn-primary" disabled={busy === "test"}>{busy === "test" ? "Sending…" : "Send test"}</button>
      </form>
    </div>
  );
}
