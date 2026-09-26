import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import api from "../../api/client";
import { errMsg, money } from "../../lib/format";
import ErrorBanner from "../../components/ErrorBanner";
import ProducePhoto from "../../components/ProducePhoto";
import StatusPill from "../../components/StatusPill";
import { useAuth } from "../../context/AuthContext";

export default function ProductDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const nav = useNavigate();
  const [p, setP] = useState(null);
  const [qty, setQty] = useState(1);
  const [msg, setMsg] = useState("");

  useEffect(() => {
    api.get(`/catalog/products/${id}`).then((r) => setP(r.data));
  }, [id]);

  async function add() {
    if (!user) return nav("/login");
    try {
      await api.post("/cart/items", { productId: Number(id), qty: Number(qty) });
      window.dispatchEvent(new Event("sc-cart-updated"));
      nav("/cart");
    } catch (e) {
      setMsg(errMsg(e, "cart"));
    }
  }

  if (!p) return <div className="p-10">Loading…</div>;

  return (
    <div className="max-w-frame mx-auto px-4 lg:px-10 py-10 grid md:grid-cols-2 gap-8 md:gap-12">
      <div className="rounded-2xl overflow-hidden border border-line bg-white aspect-[4/3] p-6">
        <ProducePhoto hint={p.imageHint} imageUrl={p.imageUrl} name={p.name} sku={p.sku} className="h-full w-full object-contain" />
      </div>
      <div>
        <p className="label-overline">{p.sku} · {p.kind?.replaceAll("_", " ")}</p>
        <h1 className="page-title mt-2">{p.name}</h1>
        <p className="mt-3 text-ink-muted font-light">{p.description}</p>
        <dl className="mt-4 grid grid-cols-2 gap-2 text-sm text-ink-muted">
          <div>Origin <b className="text-ink">{p.origin}</b></div>
          <div>Grade <b className="text-ink">{p.grade}</b></div>
          <div>Storage <b className="text-ink">{p.storageHint}</b></div>
          <div>Shelf life <b className="text-ink">{p.shelfLifeDays} days</b></div>
        </dl>
        <p className="mt-6 font-display text-2xl font-semibold text-primary">{money(p.retailPrice)} <span className="text-base font-sans font-normal text-ink-muted">/ {p.uom}</span></p>
        <p className="text-sm text-ink-muted">Wholesale {money(p.wholesalePrice)} from {p.minWholesaleQty} {p.uom}</p>
        <div className="mt-6 flex flex-col sm:flex-row gap-3 items-stretch sm:items-center">
          <input className="input w-full sm:w-28" type="number" min="0.5" step="0.5" value={qty} onChange={(e) => setQty(e.target.value)} />
          <button className="btn-primary w-full sm:w-auto" onClick={add}>Add Crate</button>
        </div>
        {msg === "Added to cart" ? (
          <p className="mt-3 text-sm text-secondary">{msg}</p>
        ) : (
          <ErrorBanner>{msg}</ErrorBanner>
        )}
        <h3 className="mt-8 font-display text-lg text-primary">Lots</h3>
        <div className="mt-2 space-y-2">
          {(p.lots || []).map((l) => (
            <div key={l.lotCode} className="flex justify-between text-sm card px-3 py-2">
              <span>{l.lotCode}</span>
              <StatusPill value={l.status} />
              <span>{l.availableQty} · exp {l.expiryOn || "—"}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
