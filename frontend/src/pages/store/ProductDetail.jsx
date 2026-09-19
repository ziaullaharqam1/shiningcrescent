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
      nav("/cart");
    } catch (e) {
      setMsg(errMsg(e, "cart"));
    }
  }

  if (!p) return <div className="p-10">Loading…</div>;

  return (
    <div className="max-w-6xl mx-auto px-4 py-10 grid md:grid-cols-2 gap-8 md:gap-10">
      <div className="rounded-2xl overflow-hidden border border-gray-100 bg-white h-64 sm:h-[28rem]">
        <ProducePhoto hint={p.imageHint} imageUrl={p.imageUrl} name={p.name} sku={p.sku} className="h-full w-full" />
      </div>
      <div>
        <p className="text-xs uppercase tracking-wide text-grove-600">{p.sku} · {p.kind?.replaceAll("_", " ")}</p>
        <h1 className="font-display text-4xl mt-2">{p.name}</h1>
        <p className="mt-3 text-grove-700">{p.description}</p>
        <dl className="mt-4 grid grid-cols-2 gap-2 text-sm">
          <div>Origin <b>{p.origin}</b></div>
          <div>Grade <b>{p.grade}</b></div>
          <div>Storage <b>{p.storageHint}</b></div>
          <div>Shelf life <b>{p.shelfLifeDays} days</b></div>
        </dl>
        <p className="mt-6 text-2xl font-semibold">{money(p.retailPrice)} <span className="text-base font-normal">/ {p.uom}</span></p>
        <p className="text-sm text-grove-600">Wholesale {money(p.wholesalePrice)} from {p.minWholesaleQty} {p.uom}</p>
        <div className="mt-6 flex flex-col sm:flex-row gap-3 items-stretch sm:items-center">
          <input className="input w-full sm:w-28" type="number" min="0.5" step="0.5" value={qty} onChange={(e) => setQty(e.target.value)} />
          <button className="btn-primary w-full sm:w-auto" onClick={add}>Add to cart</button>
        </div>
        {msg === "Added to cart" ? (
          <p className="mt-3 text-sm text-grove-700">{msg}</p>
        ) : (
          <ErrorBanner>{msg}</ErrorBanner>
        )}
        <h3 className="mt-8 font-semibold">Lots</h3>
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
