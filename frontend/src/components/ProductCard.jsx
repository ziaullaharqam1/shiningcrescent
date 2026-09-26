import { Link, useNavigate } from "react-router-dom";
import { Plus } from "lucide-react";
import { useState } from "react";
import { money } from "../lib/format";
import ProducePhoto from "./ProducePhoto";
import api from "../api/client";
import { useAuth } from "../context/AuthContext";
import { errMsg } from "../lib/errors";

export default function ProductCard({ p, pricingMode = "retail" }) {
  const { user } = useAuth();
  const nav = useNavigate();
  const [qty, setQty] = useState(1);
  const [busy, setBusy] = useState(false);
  const [note, setNote] = useState("");
  const stock = Number(p.availableQty || 0);
  const soldOut = stock <= 0;
  const price = pricingMode === "trade" ? p.wholesalePrice : p.retailPrice;
  const uom = p.uom || "KG";

  async function addCrate(e) {
    e.preventDefault();
    e.stopPropagation();
    if (soldOut) return;
    if (!user) {
      nav("/login");
      return;
    }
    setBusy(true);
    setNote("");
    try {
      await api.post("/cart/items", { productId: p.id, qty: Number(qty) });
      window.dispatchEvent(new Event("sc-cart-updated"));
      setNote("Added to crate");
      setTimeout(() => setNote(""), 2000);
    } catch (err) {
      setNote(errMsg(err, "cart"));
    } finally {
      setBusy(false);
    }
  }

  return (
    <article className="group h-full bg-white rounded-2xl p-5 sm:p-6 border border-line/80 hover:border-primary/25 hover:shadow-lift hover:-translate-y-1 transition-all duration-300 flex flex-col justify-between">
      <div>
        <Link to={`/p/${p.id}`} className="block">
          <div className="w-full aspect-[4/3] rounded-xl bg-white flex items-center justify-center overflow-hidden p-5 mb-5 border border-line/60">
            <ProducePhoto
              hint={p.imageHint}
              imageUrl={p.imageUrl}
              name={p.name}
              sku={p.sku}
              className="w-full h-full object-contain group-hover:scale-105 transition-transform duration-500 ease-out"
            />
          </div>
          <div className="flex items-center justify-between text-[11px] uppercase tracking-wider text-ink-muted/80 mb-2 gap-2">
            <span className="truncate">{p.origin || "Origin TBD"} · Grade {p.grade || "A"}</span>
            <span className="font-mono text-ink-faint shrink-0">{p.sku}</span>
          </div>
          <h3 className="font-display text-xl font-semibold text-primary mb-2 group-hover:text-secondary transition-colors">
            {p.name}
          </h3>
          <p className="text-ink-muted text-xs leading-relaxed font-light line-clamp-2 min-h-[2.5rem]">
            {p.description || `${(p.kind || "").replaceAll("_", " ").toLowerCase()} · cold-chain graded lot ready for FEFO dispatch.`}
          </p>
        </Link>
      </div>

      <div className="mt-6 pt-5 border-t border-line flex flex-col gap-4">
        <div className="flex items-baseline justify-between gap-3">
          <div>
            <div className="font-display text-xl font-bold text-primary">
              {money(price)}{" "}
              <span className="text-xs font-normal text-ink-muted">/ {uom}</span>
            </div>
            <div className="text-[11px] text-ink-muted mt-0.5">
              {pricingMode === "trade"
                ? `Retail: ${money(p.retailPrice)} / ${uom}`
                : `Trade: ${money(p.wholesalePrice)} / ${uom}`}
            </div>
          </div>
          <span
            className={`text-[11px] font-medium tracking-wide whitespace-nowrap rounded-full px-2.5 py-1 ${
              soldOut
                ? "bg-red-50 text-red-700"
                : stock < 20
                  ? "bg-harvest-soft text-harvest-ink"
                  : "bg-secondary-container text-secondary"
            }`}
          >
            {soldOut ? "0 KG" : `${stock} ${uom} in stock`}
          </span>
        </div>

        <div className="flex items-center gap-3">
          <div className="flex items-center border border-line rounded-lg p-0.5 shrink-0">
            <button
              type="button"
              className="w-7 h-7 flex items-center justify-center text-ink-muted hover:text-primary transition-colors text-xs"
              onClick={() => setQty((q) => Math.max(1, Number(q) - 1))}
              aria-label="Decrease quantity"
            >
              −
            </button>
            <span className="w-7 text-center text-xs font-semibold">{qty}</span>
            <button
              type="button"
              className="w-7 h-7 flex items-center justify-center text-ink-muted hover:text-primary transition-colors text-xs"
              onClick={() => setQty((q) => Math.min(50, Number(q) + 1))}
              aria-label="Increase quantity"
            >
              +
            </button>
          </div>
          <button
            type="button"
            disabled={busy || soldOut}
            onClick={addCrate}
            className="flex-1 bg-primary hover:bg-primary-container disabled:opacity-50 text-white py-2.5 px-3 rounded-lg text-xs font-semibold tracking-wider uppercase transition-all flex items-center justify-center gap-2 shadow-soft"
          >
            <Plus size={14} />
            <span>{soldOut ? "Notify" : pricingMode === "trade" ? "Add Sack" : "Add Crate"}</span>
          </button>
        </div>
        {note && <p className="text-[11px] text-secondary">{note}</p>}
      </div>
    </article>
  );
}
