import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Star } from "lucide-react";
import api from "../../api/client";
import { money } from "../../lib/format";
import { printSalesBill } from "../../lib/printDocs";
import { useBranding } from "../../context/BrandingContext";
import StatusPill from "../../components/StatusPill";
import DeliveryMap, { formatDistance, isArrived } from "../../components/DeliveryMap";

export function MyOrders() {
  const [rows, setRows] = useState([]);
  useEffect(() => {
    api.get("/orders").then((r) => setRows(r.data));
  }, []);
  return (
    <div className="max-w-5xl mx-auto px-4 lg:px-10 py-10">
      <h1 className="page-title">Orders</h1>
      <p className="page-hint">Track harvest crates from payment through Noon delivery.</p>
      <div className="table-wrap mt-6">
        <table className="data">
          <thead><tr><th>No</th><th>Status</th><th>Payment</th><th>Total</th><th>When</th></tr></thead>
          <tbody>
            {rows.map((o) => (
              <tr key={o.id}>
                <td><Link className="text-primary font-semibold underline underline-offset-2" to={`/orders/${o.id}`}>{o.orderNo}</Link></td>
                <td><StatusPill value={o.status} /></td>
                <td><StatusPill value={o.paymentStatus} /></td>
                <td className="font-semibold">{money(o.total)}</td>
                <td className="text-xs text-ink-muted">{o.placedAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function OrderDetail() {
  const { id } = useParams();
  const { branding } = useBranding();
  const [o, setO] = useState(null);
  const [tip, setTip] = useState(10);
  const [stars, setStars] = useState(5);
  const [comment, setComment] = useState("");
  const [msg, setMsg] = useState("");

  async function load() {
    const { data } = await api.get(`/orders/${id}`);
    setO(data);
  }

  useEffect(() => {
    load();
    const t = setInterval(load, 2000);
    return () => clearInterval(t);
  }, [id]);

  if (!o) return <div className="p-10">Loading…</div>;

  const rider = o.rider;
  const vehicleKind = o.vehicleKind || rider?.vehicleKind || "SCOOTER";
  const vehicleLabel = vehicleKind === "CAR" ? "Car" : "Scooter";
  const arrived = isArrived(o);
  const phase = o.deliveryPhase || (arrived ? "Arrived" : o.deliveryStatus);
  const dist = arrived ? "Arrived" : o.distanceLabel || formatDistance(o.distanceKm, arrived);
  const etaText = arrived ? "0 min" : `${o.etaMinutes ?? "—"} min`;

  async function sendTip() {
    const { data } = await api.post(`/orders/${id}/delivery/tip`, { amount: Number(tip) });
    if (data.clientSecret) {
      setMsg("Tip PaymentIntent created — confirm in Stripe, then the rider receives it.");
      await api.post(`/orders/${id}/delivery/tip/confirm`, { amount: Number(tip) });
    }
    setMsg(`Tip ${money(Number(tip))} sent to ${rider?.name || "the rider"}`);
    load();
  }

  async function sendFeedback() {
    await api.post(`/orders/${id}/delivery/feedback`, { rating: stars, comment });
    setMsg("Thanks — your rating updates the rider’s Noon score.");
    load();
  }

  return (
    <div className="max-w-3xl mx-auto px-4 lg:px-10 py-10">
      <p className="label-overline">{o.orderNo}</p>
      <h1 className="page-title mt-1">Live delivery</h1>
      <div className="mt-3 flex flex-wrap gap-2">
        <StatusPill value={o.status} />
        <StatusPill value={o.paymentStatus} />
        {o.deliveryStatus && <StatusPill value={phase} />}
        {o.noonAwb && <span className="text-xs rounded-full bg-secondary-container px-3 py-1">Noon AWB {o.noonAwb}</span>}
      </div>
      <p className="mt-4 text-sm">{o.shipToName} · {o.shipToAddress}</p>
      {o.leaveAtDoor && (
        <p className="mt-2 text-sm font-semibold text-primary">Leave at the door — do not knock.</p>
      )}
      {o.paymentMethod === "CASH_ON_DELIVERY" && (
        <p className="mt-1 text-sm text-ink">Pay cash to the Noon rider on delivery. Amount {money(o.total)}.</p>
      )}
      {o.collectCash && o.noonAwb && (
        <p className="mt-1 text-sm text-ink">Noon will collect cash with AWB {o.noonAwb}.</p>
      )}
      <button type="button" className="btn-ghost text-sm mt-4" onClick={() => printSalesBill(o, branding)}>Print bill</button>

      {o.deliveryAssigned && rider && (
        <div className="mt-6 space-y-4">
          <DeliveryMap delivery={o} />
          <div className="card p-4 flex flex-wrap gap-4 items-start">
            <div className="flex-1 min-w-[180px]">
              <p className="text-xs uppercase tracking-wide text-ink-muted">Noon rider</p>
              <p className="font-display text-2xl mt-1">{rider.name}</p>
              <p className="text-sm text-ink-muted">{vehicleLabel} · {rider.vehicle} · {rider.phone}</p>
              <p className="text-sm mt-2 flex items-center gap-1">
                <Star size={14} className="text-[#4682B4] fill-[#4682B4]" />
                {rider.rating} · {rider.jobsCompleted} deliveries
              </p>
              <p className="text-sm font-semibold mt-2 text-[#2E5A7A]">
                {arrived ? "Arrived at delivery pin" : `${dist} to delivery pin`}
              </p>
            </div>
            <div className="text-right">
              <p className="text-xs uppercase tracking-wide text-[#2E5A7A]">Distance · ETA</p>
              <p className="font-display text-3xl text-primary">{dist}</p>
              <p className="text-sm text-[#2E5A7A]">{etaText} · {phase}</p>
            </div>
          </div>

          {(o.status === "DELIVERED" || o.status === "ARRIVING") && (
            <div className="card p-4 space-y-4">
              <div>
                <p className="font-semibold">Tip {rider.name}</p>
                <div className="flex flex-wrap gap-2 mt-2">
                  {[5, 10, 15, 20].map((n) => (
                    <button key={n} className={`btn-ghost ${Number(tip) === n ? "nav-chip-active" : ""}`} onClick={() => setTip(n)}>{money(n)}</button>
                  ))}
                  <input className="input w-28" type="number" min="1" value={tip} onChange={(e) => setTip(e.target.value)} />
                  <button className="btn-primary" onClick={sendTip} disabled={!!o.tipAmount && Number(o.tipAmount) > 0}>
                    {o.tipAmount && Number(o.tipAmount) > 0 ? `Tipped ${money(o.tipAmount)}` : "Send tip"}
                  </button>
                </div>
              </div>
              <div>
                <p className="font-semibold">Feedback</p>
                <div className="flex gap-1 mt-2">
                  {[1, 2, 3, 4, 5].map((n) => (
                    <button key={n} onClick={() => setStars(n)} aria-label={`${n} stars`}>
                      <Star size={22} className={n <= stars ? "text-[#4682B4] fill-[#4682B4]" : "text-[#D6E6F0]"} />
                    </button>
                  ))}
                </div>
                <textarea className="input mt-2" placeholder="How was the drop-off?" value={comment} onChange={(e) => setComment(e.target.value)} />
                <button className="btn-primary mt-2" onClick={sendFeedback} disabled={!!o.customerRating}>
                  {o.customerRating ? `Rated ${o.customerRating}/5` : "Submit rating"}
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {msg && <p className="text-sm text-ink mt-3">{msg}</p>}

      <div className="card p-4 mt-6 space-y-2">
        {(o.lines || []).map((l) => (
          <div key={l.sku} className="flex justify-between text-sm">
            <span>{l.name} × {l.qty}</span>
            <span>{money(l.lineTotal)}</span>
          </div>
        ))}
        <div className="flex justify-between font-semibold pt-2 border-t"><span>Total</span><span>{money(o.total)}</span></div>
      </div>
      <ol className="mt-8 space-y-2 text-sm">
        {(o.workflow || []).map((w) => (
          <li key={w.id} className="card px-3 py-2">
            {w.fromStatus || "start"} → <b>{w.toStatus}</b> · {w.actor} · {w.comment}
          </li>
        ))}
      </ol>
    </div>
  );
}
