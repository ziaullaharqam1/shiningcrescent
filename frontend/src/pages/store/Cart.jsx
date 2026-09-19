import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import api from "../../api/client";
import { errMsg, money } from "../../lib/format";
import ErrorBanner from "../../components/ErrorBanner";
import ProducePhoto from "../../components/ProducePhoto";
import { useAuth } from "../../context/AuthContext";

export default function Cart() {
  const { user } = useAuth();
  const nav = useNavigate();
  const [cart, setCart] = useState(null);
  const [error, setError] = useState("");

  async function load() {
    try {
      const { data } = await api.get("/cart");
      setCart(data);
    } catch (e) {
      setError(errMsg(e, "cart"));
    }
  }

  useEffect(() => {
    if (!user) nav("/login");
    else load();
  }, [user]);

  async function setQty(productId, qty) {
    try {
      await api.put("/cart/items", { productId, qty });
      setError("");
      load();
    } catch (e) {
      setError(errMsg(e, "cart"));
    }
  }

  if (!cart) {
    return (
      <div className="p-10">
        {error ? <ErrorBanner>{error}</ErrorBanner> : "Loading…"}
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto px-4 py-10">
      <h1 className="font-display text-4xl">Your crate</h1>
      <p className="text-sm text-grove-600 mt-1">Priced as {cart.channel}</p>
      {error && <div className="mt-4"><ErrorBanner>{error}</ErrorBanner></div>}
      {cart.items.length === 0 ? (
        <p className="mt-8">Empty. <Link className="underline" to="/">Browse the market</Link></p>
      ) : (
        <div className="mt-6 space-y-3">
          {cart.items.map((i) => (
            <div key={i.productId} className="bg-white border border-gray-100 rounded-2xl p-4 flex flex-wrap items-center gap-4">
              <ProducePhoto hint={i.imageHint} imageUrl={i.imageUrl} name={i.name} sku={i.sku} className="h-16 w-16 rounded-xl" />
              <div className="flex-1">
                <p className="font-semibold">{i.name}</p>
                <p className="text-sm text-grove-600">{i.sku} · {money(i.unitPrice)} / {i.uom}</p>
              </div>
              <input
                className="input w-24"
                type="number"
                min="0"
                step="0.5"
                defaultValue={i.qty}
                onBlur={(e) => setQty(i.productId, Number(e.target.value))}
              />
              <p className="w-24 text-right font-semibold">{money(i.lineTotal)}</p>
            </div>
          ))}
          <div className="card p-4 space-y-1 text-sm">
            <div className="flex justify-between"><span>Subtotal</span><b>{money(cart.subtotal)}</b></div>
            <div className="flex justify-between"><span>VAT 5%</span><b>{money(cart.tax)}</b></div>
            <div className="flex justify-between text-lg"><span>Total</span><b>{money(cart.total)}</b></div>
            <p className="text-xs text-grove-600 pt-1">Next: pay by card or cash on delivery. A Noon rider is picked from who is available.</p>
          </div>
          <Link className="btn-primary w-full sm:w-auto" to="/checkout">Pay</Link>
        </div>
      )}
    </div>
  );
}
