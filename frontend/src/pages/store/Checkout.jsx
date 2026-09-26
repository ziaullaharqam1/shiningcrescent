import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import api from "../../api/client";
import { errMsg, money } from "../../lib/format";
import ErrorBanner from "../../components/ErrorBanner";
import { useAuth } from "../../context/AuthContext";

export default function Checkout() {
  const { user, ready } = useAuth();
  const nav = useNavigate();

  useEffect(() => {
    if (ready && !user) nav("/login");
  }, [ready, user, nav]);

  useEffect(() => {
    if (!user) return;
    setForm((f) => ({
      ...f,
      shipToName: f.shipToName || user.fullName || "",
      shipToPhone: f.shipToPhone || user.phone || "",
    }));
  }, [user]);
  const [form, setForm] = useState({
    shipToName: user?.fullName || "",
    shipToPhone: user?.phone || "",
    shipToAddress: user?.city ? `${user.city}` : "Downtown Dubai",
    notes: "",
    paymentMethod: "CARD",
    leaveAtDoor: false,
  });
  const [pay, setPay] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function startPay(e) {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const body = { ...form };
      if (navigator.geolocation) {
        await new Promise((resolve) => {
          navigator.geolocation.getCurrentPosition(
            (pos) => {
              body.dropLat = pos.coords.latitude;
              body.dropLng = pos.coords.longitude;
              resolve();
            },
            () => resolve(),
            { timeout: 2500 }
          );
        });
      }
      const cashChosen = form.paymentMethod === "COD";
      const { data } = await api.post("/orders/checkout", {
        ...body,
        paymentMethod: cashChosen ? "COD" : "CARD",
      });
      if (cashChosen || data.paymentMethod === "CASH_ON_DELIVERY" || data.awaitingCard === false) {
        nav(`/orders/${data.id}`);
        return;
      }
      setPay(data);
    } catch (err) {
      setError(errMsg(err, "checkout"));
    } finally {
      setBusy(false);
    }
  }

  if (pay?.awaitingCard && pay?.stripeMock) {
    return (
      <PayShell error={error} total={pay.total} orderNo={pay.orderNo}>
        <p className="text-sm text-ink-muted">
          Test charge. Card number <span className="font-mono tracking-wide">4242424242424242</span>, any future expiry, any CVC.
        </p>
        <button
          className="btn-primary w-full"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            try {
              const { data } = await api.post(`/orders/${pay.id}/stripe/confirm`, { paymentIntentId: pay.paymentIntentId });
              nav(`/orders/${data.id}`);
            } catch (err) {
              setError(errMsg(err, "checkout"));
              setBusy(false);
            }
          }}
        >
          Pay {money(pay.total)}
        </button>
      </PayShell>
    );
  }

  if (pay?.awaitingCard && pay?.clientSecret && pay.stripePublishableKey) {
    return (
      <PayShell error={error} total={pay.total} orderNo={pay.orderNo}>
        <StripePayForm
          publishableKey={pay.stripePublishableKey}
          clientSecret={pay.clientSecret}
          orderId={pay.id}
          total={pay.total}
          onError={setError}
        />
      </PayShell>
    );
  }

  return (
    <div className="max-w-xl mx-auto px-4 py-10">
      <h1 className="page-title">Deliver the harvest</h1>
      <p className="page-hint">Address first, then pay by card or cash on delivery. A Noon rider is assigned from who is free nearby.</p>
      {error && <ErrorBanner>{error}</ErrorBanner>}
      <form onSubmit={startPay} className="card p-6 mt-6 space-y-3">
        <input className="input" placeholder="Receiver name" value={form.shipToName} onChange={(e) => setForm({ ...form, shipToName: e.target.value })} required />
        <input className="input" placeholder="Mobile" value={form.shipToPhone} onChange={(e) => setForm({ ...form, shipToPhone: e.target.value })} required />
        <textarea className="input min-h-24" placeholder="Address (try Downtown Dubai, Marina, Deira…)" value={form.shipToAddress} onChange={(e) => setForm({ ...form, shipToAddress: e.target.value })} required />
        <textarea className="input" placeholder="Notes (ripeness, packing)" value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} />
        <label className="flex items-start gap-3 text-sm text-ink cursor-pointer">
          <input
            type="checkbox"
            className="mt-1 accent-primary"
            checked={form.leaveAtDoor}
            onChange={(e) => setForm({ ...form, leaveAtDoor: e.target.checked })}
          />
          <span>
            <span className="font-semibold">Leave at the door, do not knock</span>
            <span className="block text-ink-muted">Rider drops the crate and leaves without ringing.</span>
          </span>
        </label>
        <fieldset className="space-y-2 pt-1">
          <legend className="text-sm font-semibold text-ink">Payment</legend>
          <label className="flex items-center gap-2 text-sm cursor-pointer">
            <input type="radio" name="pay" value="CARD" checked={form.paymentMethod === "CARD"} onChange={() => setForm({ ...form, paymentMethod: "CARD" })} />
            Pay
          </label>
          <label className="flex items-center gap-2 text-sm cursor-pointer">
            <input type="radio" name="pay" value="COD" checked={form.paymentMethod === "COD"} onChange={() => setForm({ ...form, paymentMethod: "COD" })} />
            Cash on delivery
          </label>
        </fieldset>
        <button className="btn-primary w-full" disabled={busy}>{busy ? "Continuing…" : "Continue"}</button>
      </form>
    </div>
  );
}

function PayShell({ children, error, total, orderNo }) {
  return (
    <div className="max-w-xl mx-auto px-4 py-10">
      <h1 className="page-title">Pay</h1>
      <p className="page-hint">{orderNo} · {money(total)} · AED</p>
      {error && <ErrorBanner>{error}</ErrorBanner>}
      <div className="card p-6 mt-6 space-y-4">{children}</div>
    </div>
  );
}

function StripePayForm({ publishableKey, clientSecret, orderId, total, onError }) {
  const mount = useRef(null);
  const stripeRef = useRef(null);
  const elementsRef = useRef(null);
  const nav = useNavigate();
  const [busy, setBusy] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!window.Stripe || !mount.current) {
      onError("Payment form could not load. Please refresh and try again.");
      return;
    }
    const stripe = window.Stripe(publishableKey);
    const elements = stripe.elements({ clientSecret });
    const payment = elements.create("payment");
    payment.mount(mount.current);
    stripeRef.current = stripe;
    elementsRef.current = elements;
    setReady(true);
    return () => {
      try { payment.unmount(); } catch { /* ignore */ }
    };
  }, [publishableKey, clientSecret, onError]);

  async function pay(e) {
    e.preventDefault();
    const stripe = stripeRef.current;
    const elements = elementsRef.current;
    if (!stripe || !elements) return;
    setBusy(true);
    onError("");
    const { error, paymentIntent } = await stripe.confirmPayment({
      elements,
      redirect: "if_required",
      confirmParams: { return_url: `${window.location.origin}/orders/${orderId}` },
    });
    if (error) {
      onError(errMsg({ message: error.message }, "checkout"));
      setBusy(false);
      return;
    }
    try {
      const { data } = await api.post(`/orders/${orderId}/stripe/confirm`, { paymentIntentId: paymentIntent?.id });
      nav(`/orders/${data.id}`);
    } catch (err) {
      onError(errMsg(err, "checkout"));
      setBusy(false);
    }
  }

  return (
    <form onSubmit={pay} className="space-y-4">
      <div ref={mount} />
      <p className="text-xs text-ink-muted">
        Test card (numbers only): <span className="font-mono">4242424242424242</span> · any future date · any CVC
      </p>
      <button className="btn-primary w-full" disabled={busy || !ready}>{busy ? "Charging…" : `Pay ${money(total)}`}</button>
    </form>
  );
}
