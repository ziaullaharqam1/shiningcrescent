import { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import api from "../../api/client";
import { errMsg, money } from "../../lib/format";
import { printInvoice, printPeriodReport, printPurchaseOrder } from "../../lib/printDocs";
import { useBranding } from "../../context/BrandingContext";
import ErrorBanner from "../../components/ErrorBanner";
import ProducePhoto from "../../components/ProducePhoto";
import StatusPill from "../../components/StatusPill";
import { useAuth } from "../../context/AuthContext";

function asList(value) {
  return Array.isArray(value) ? value : [];
}

function useLoad(fn) {
  const [data, setData] = useState(undefined);
  const [error, setError] = useState("");
  async function reload() {
    try {
      setError("");
      setData(await fn());
    } catch (e) {
      setError(errMsg(e, "catalog"));
    }
  }
  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return { data, error, reload, setError };
}

function PageHead({ title, hint }) {
  return (
    <div className="mb-5">
      <h1 className="font-display text-3xl md:text-4xl text-grove-800">{title}</h1>
      {hint && <p className="text-grove-600 mt-1 text-sm md:text-base">{hint}</p>}
    </div>
  );
}

function matchesQuery(row, q) {
  const n = String(q || "").trim().toLowerCase();
  if (!n) return true;
  return Object.entries(row || {}).some(([k, v]) => {
    if (k === "imageUrl" || k === "imageHint" || k === "imageFile") return false;
    if (v == null) return false;
    if (Array.isArray(v)) return v.some((x) => String(x ?? "").toLowerCase().includes(n));
    if (typeof v === "object") return JSON.stringify(v).toLowerCase().includes(n);
    return String(v).toLowerCase().includes(n);
  });
}

function RecordSearch({ value, onChange, count, total }) {
  return (
    <div className="mb-3 flex flex-wrap items-center gap-2">
      <input
        className="input max-w-md"
        value={value}
        placeholder="Search these records…"
        onChange={(e) => onChange(e.target.value)}
      />
      {value ? <p className="text-xs text-grove-600">{count} of {total}</p> : null}
    </div>
  );
}

async function removeRecord(path, reload, setError, label) {
  if (!window.confirm(`Delete this ${label || "record"}?`)) return;
  try {
    if (setError) setError("");
    await api.delete(path);
    reload();
  } catch (e) {
    const msg = errMsg(e);
    if (setError) setError(msg);
    else window.alert(msg);
  }
}

export function Dashboard() {
  const { can } = useAuth();
  const { data } = useLoad(async () => (await api.get("/console/dashboard")).data);
  if (!can("DASHBOARD:VIEW")) return <Navigate to="/console/purchase-orders" replace />;
  if (!data) return <p className="text-grove-600">Loading desk…</p>;
  const supplierDesk = Boolean(data.supplierDesk);
  const cards = supplierDesk
    ? [
        ["Assigned POs", data.assignedPos, "bg-peach"],
        ["Awaiting your ack", data.awaitingAck, "bg-gold-100"],
        ["Open with you", data.openPos, "bg-citrus/30"],
        ["Your available lots", data.availableLots, "bg-grove-100"],
        ["In quarantine", data.quarantineLots ?? data.pendingQc, "bg-sky"],
        ["On-hand kg", data.onHandQty, "bg-peach"],
      ]
    : [
        ["Orders placed", data.ordersPlaced, "bg-citrus/30"],
        ["Picking", data.ordersPicking, "bg-sky"],
        ["Shipped", data.ordersShipped, "bg-grove-100"],
        ["Open POs", data.openPos, "bg-peach"],
        ["Pending QC", data.pendingQc, "bg-gold-100"],
        ["Available lots", data.availableLots, "bg-grove-100"],
        ["On-hand kg", data.onHandQty, "bg-peach"],
        ["SKUs", data.catalogSkus, "bg-sky"],
      ];
  return (
    <div>
      <PageHead
        title={supplierDesk ? "Supplier desk" : "Trading desk"}
        hint={supplierDesk
          ? "Your assigned purchase orders, lots from those receipts, and QC results. Acknowledge when you can ship."
          : "Live harvest operations across procure, QC, warehouse and cash."}
      />
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 md:gap-4">
        {cards.map(([k, v, bg]) => (
          <div key={k} className={`card p-4 md:p-5 ${bg}`}>
            <p className="text-[11px] md:text-xs uppercase tracking-wide text-grove-600">{k}</p>
            <p className="font-display text-2xl md:text-3xl mt-2 text-grove-800">{v}</p>
          </div>
        ))}
      </div>
      {can("REPORTS:VIEW") && (
        <Link to="/console/reports" className="card p-4 md:p-5 mt-4 block hover:bg-grove-50">
          <p className="font-display text-xl text-grove-800">Reports</p>
          <p className="text-sm text-grove-600 mt-1">Daily and monthly Excel workbooks with a summary sheet and detail grids.</p>
        </Link>
      )}
    </div>
  );
}

const EMPTY_PRODUCT = {
  id: "",
  sku: "",
  name: "",
  kind: "DRY_FRUIT",
  uom: "KG",
  categoryId: "",
  retailPrice: "",
  wholesalePrice: "",
  origin: "",
  grade: "A",
  imageUrl: "",
  imageFile: null,
};

export function Products() {
  const { can } = useAuth();
  const { data: productData, reload, error, setError } = useLoad(async () => (await api.get("/console/products")).data);
  const { data: catData } = useLoad(async () => (await api.get("/catalog/categories")).data);
  const rows = asList(productData);
  const cats = asList(catData);
  const [form, setForm] = useState(EMPTY_PRODUCT);
  const [seedNote, setSeedNote] = useState("");
  const [q, setQ] = useState("");
  const filtered = rows.filter((r) => matchesQuery(r, q));
  const editing = Boolean(form.id);
  const showForm = can("PRODUCTS:CREATE") || (can("PRODUCTS:UPDATE") && editing);

  function startEdit(p) {
    setError("");
    setForm({
      id: p.id,
      sku: p.sku || "",
      name: p.name || "",
      kind: p.kind || "DRY_FRUIT",
      uom: p.uom || "KG",
      categoryId: p.categoryId ? String(p.categoryId) : "",
      retailPrice: p.retailPrice ?? "",
      wholesalePrice: p.wholesalePrice ?? "",
      origin: p.origin || "",
      grade: p.grade || "A",
      imageUrl: p.imageUrl || "",
      imageFile: null,
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function save(e) {
    e.preventDefault();
    setError("");
    try {
      const { imageFile, imageUrl, ...fields } = form;
      const payload = { ...fields, categoryId: Number(form.categoryId) };
      if (!payload.id) delete payload.id;
      const { data: saved } = await api.post("/console/products", payload);
      if (imageFile) {
        const fd = new FormData();
        fd.append("file", imageFile);
        await api.post(`/console/products/${saved.id}/image`, fd);
      }
      if (!payload.id && saved?.starterPo) {
        setSeedNote(`Opening PO ${saved.starterPo} (3 kg via ${saved.supplier}) was auto-approved for QA. Lot ${saved.lotCode} is ${saved.lotStatus} with ${saved.availableQty} available.`);
      } else {
        setSeedNote(saved?.seedNote || "");
      }
      setForm(EMPTY_PRODUCT);
      reload();
    } catch (err) {
      setError(errMsg(err));
    }
  }

  return (
    <div>
      <PageHead
        title="Product master"
        hint="Merchandisers and admins set retail and wholesale prices here. A new SKU raises an opening PO of 3 kg with any active supplier, auto-passes QA, and lands an available lot."
      />
      {seedNote && <p className="card p-3 mb-3 text-sm text-grove-800">{seedNote}</p>}
      {error && <ErrorBanner className="mb-3">{error}</ErrorBanner>}
      {showForm && (
        <form onSubmit={save} className="card p-4 mb-4 grid sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <p className="sm:col-span-2 lg:col-span-4 text-sm font-semibold text-grove-800">
            {editing ? `Edit prices for ${form.sku}` : "New SKU"}
          </p>
          {["sku", "name", "origin", "grade", "retailPrice", "wholesalePrice"].map((k) => (
            <input
              key={k}
              className="input"
              placeholder={k === "retailPrice" ? "Retail price (AED)" : k === "wholesalePrice" ? "Wholesale price (AED)" : k}
              value={form[k] || ""}
              onChange={(e) => setForm({ ...form, [k]: e.target.value })}
            />
          ))}
          <select className="input" value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}>
            <option value="">Category</option>
            {cats.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
          <select className="input" value={form.kind} onChange={(e) => setForm({ ...form, kind: e.target.value })}>
            <option>FRESH_FRUIT</option><option>DRY_FRUIT</option><option>NUT</option><option>MIX</option>
          </select>
          <div className="sm:col-span-2 lg:col-span-4 flex flex-wrap items-center gap-4">
            <div className="h-20 w-20 rounded-xl overflow-hidden border border-grove-100 bg-white shrink-0">
              {form.imageFile ? (
                <img src={URL.createObjectURL(form.imageFile)} alt="" className="h-full w-full object-cover" />
              ) : (
                <ProducePhoto hint={form.sku} imageUrl={form.imageUrl} name={form.name} sku={form.sku} className="h-full w-full" />
              )}
            </div>
            <label className="btn-ghost cursor-pointer">
              {form.imageFile ? "Photo selected" : editing ? "Change photo" : "Upload photo"}
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp,image/gif"
                className="hidden"
                onChange={(e) => setForm({ ...form, imageFile: e.target.files?.[0] || null })}
              />
            </label>
            {form.imageFile && (
              <button type="button" className="text-sm underline" onClick={() => setForm({ ...form, imageFile: null })}>
                Remove new photo
              </button>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            <button className="btn-primary">{editing ? "Save product" : "Save SKU"}</button>
            {editing && (
              <button type="button" className="btn-ghost" onClick={() => setForm(EMPTY_PRODUCT)}>Cancel</button>
            )}
          </div>
        </form>
      )}
      <RecordSearch value={q} onChange={setQ} count={filtered.length} total={rows.length} />
      <div className="table-wrap mt-4">
        <table className="data">
          <thead><tr><th></th><th>SKU</th><th>Name</th><th>Kind</th><th>Status</th><th>Available</th><th>Retail</th><th>Wholesale</th><th></th></tr></thead>
          <tbody>
            {filtered.map((p) => (
              <tr key={p.id}>
                <td>
                  <div className="h-12 w-12 rounded-lg overflow-hidden border border-grove-100">
                    <ProducePhoto hint={p.imageHint} imageUrl={p.imageUrl} name={p.name} sku={p.sku} className="h-full w-full" />
                  </div>
                </td>
                <td>{p.sku}</td><td>{p.name}</td><td>{p.kind}</td>
                <td><StatusPill value={p.status} /></td>
                <td>{p.availableQty ?? 0}</td>
                <td>{money(p.retailPrice)}</td>
                <td>{money(p.wholesalePrice)}</td>
                <td className="space-x-2 whitespace-nowrap">
                  {can("PRODUCTS:UPDATE") && (
                    <>
                      <button type="button" className="btn-ghost text-xs" onClick={() => startEdit(p)}>Edit</button>
                      <button type="button" className="btn-ghost text-xs" onClick={() => removeRecord(`/console/products/${p.id}`, reload, setError, "product")}>Delete</button>
                    </>
                  )}
                  {p.status === "DRAFT" && can("PRODUCTS:UPDATE") && <button className="btn-ghost text-xs" onClick={async () => { await api.post(`/console/products/${p.id}/submit`); reload(); }}>Submit</button>}
                  {p.status === "PENDING_APPROVAL" && can("PRODUCTS:APPROVE") && <button className="btn-ghost text-xs" onClick={async () => { await api.post(`/console/products/${p.id}/publish?approve=true`); reload(); }}>Publish</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function SimpleMaster({ title, path, fields, permCreate, permUpdate }) {
  const { can } = useAuth();
  const { data, reload, error, setError } = useLoad(async () => (await api.get(path)).data);
  const rows = asList(data);
  const init = Object.fromEntries(fields.map((f) => [f, ""]));
  const [form, setForm] = useState({ id: "", ...init });
  const [q, setQ] = useState("");
  const filtered = rows.filter((r) => matchesQuery(r, q));
  const editing = Boolean(form.id);
  const canSave = (editing && can(permUpdate)) || (!editing && can(permCreate));

  function startEdit(r) {
    setError("");
    setForm({ id: r.id, ...Object.fromEntries(fields.map((f) => [f, r[f] ?? ""])) });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function save(e) {
    e.preventDefault();
    setError("");
    try {
      const payload = { ...form };
      if (!payload.id) delete payload.id;
      await api.post(path, payload);
      setForm({ id: "", ...init });
      reload();
    } catch (err) {
      setError(errMsg(err));
    }
  }

  return (
    <div>
      <PageHead title={title} hint="Create or edit master data used across procure, warehouse and the store." />
      {error && <ErrorBanner>{error}</ErrorBanner>}
      <RecordSearch value={q} onChange={setQ} count={filtered.length} total={rows.length} />
      {canSave && (
        <form className="card p-4 mb-4 grid sm:grid-cols-2 lg:grid-cols-3 gap-3" onSubmit={save}>
          <p className="sm:col-span-2 lg:col-span-3 text-sm font-semibold text-grove-800">{editing ? "Edit record" : "New record"}</p>
          {fields.map((f) => (
            <input key={f} className="input" placeholder={f} value={form[f] ?? ""} onChange={(e) => setForm({ ...form, [f]: e.target.value })} />
          ))}
          <div className="flex flex-wrap gap-2">
            <button className="btn-primary">{editing ? "Save changes" : "Save"}</button>
            {editing && (
              <button type="button" className="btn-ghost" onClick={() => setForm({ id: "", ...init })}>Cancel</button>
            )}
          </div>
        </form>
      )}
      <div className="table-wrap mt-4">
        <table className="data">
          <thead><tr>{fields.map((f) => <th key={f}>{f}</th>)}{can(permUpdate) && <th></th>}</tr></thead>
          <tbody>
            {filtered.map((r) => (
              <tr key={r.id}>
                {fields.map((f) => <td key={f}>{String(r[f] ?? "")}</td>)}
                {can(permUpdate) && (
                  <td className="space-x-2 whitespace-nowrap">
                    <button type="button" className="btn-ghost text-xs" onClick={() => startEdit(r)}>Edit</button>
                    <button type="button" className="btn-ghost text-xs" onClick={() => removeRecord(`${path}/${r.id}`, reload, setError, "record")}>Delete</button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function SalesOrders() {
  const { can } = useAuth();
  const { data, reload, error } = useLoad(async () => (await api.get("/orders")).data);
  const rows = asList(data);
  const flow = ["CONFIRMED", "PICKING", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED"];
  return (
    <div>
      <PageHead title="Sales orders" hint="Wholesale sales confirm, pick, pack and ship. Sales staff can also shop the market for a buyer and follow the order here." />
      {error && <ErrorBanner>{error}</ErrorBanner>}
      <div className="table-wrap mt-4">
        <table className="data">
          <thead><tr><th>No</th><th>Customer</th><th>Status</th><th>Total</th><th>Advance</th></tr></thead>
          <tbody>
            {rows.map((o) => (
              <tr key={o.id}>
                <td><Link className="underline" to={`/orders/${o.id}`}>{o.orderNo}</Link></td>
                <td>{o.customer}</td>
                <td><StatusPill value={o.status} /></td>
                <td>{money(o.total)}</td>
                <td>
                  <div className="flex flex-wrap gap-1">
                  {flow.map((s) => (
                    <button key={s} className="btn-ghost text-[10px] px-2 py-1" disabled={!can("SALES_ORDERS:UPDATE") && !can("SALES_ORDERS:APPROVE")}
                      onClick={async () => { try { await api.post(`/orders/${o.id}/transition`, { status: s, comment: "Desk action" }); reload(); } catch (e) { alert(errMsg(e)); } }}>
                      {s}
                    </button>
                  ))}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function PurchaseOrders() {
  const { can, user } = useAuth();
  const { branding } = useBranding();
  const supplierPortal = asList(user?.roles).includes("SUPPLIER");
  const { data, reload, error, setError } = useLoad(async () => (await api.get("/console/purchase-orders")).data);
  const { data: supplierData } = useLoad(async () => (can("SUPPLIERS:VIEW") ? (await api.get("/console/suppliers")).data : []));
  const { data: warehouseData } = useLoad(async () => (can("WAREHOUSES:VIEW") ? (await api.get("/console/warehouses")).data : []));
  const { data: productData } = useLoad(async () => (can("PRODUCTS:VIEW") ? (await api.get("/console/products")).data : []));
  const rows = asList(data);
  const suppliers = asList(supplierData);
  const warehouses = asList(warehouseData);
  const products = asList(productData);
  const [form, setForm] = useState({ supplierId: "", warehouseId: "", productId: "", qty: "50", unitCost: "10" });
  const [q, setQ] = useState("");
  const filtered = rows.filter((r) => matchesQuery(r, q));
  const staffFlow = ["SUBMITTED", "APPROVED", "SENT_TO_SUPPLIER"];
  return (
    <div>
      <PageHead title="Purchase orders" hint={supplierPortal
        ? "Orders assigned to your house. Acknowledge when you can ship, confirm the expected date, and print the PO. You cannot approve, receive, or quarantine stock."
        : "New SKUs auto-raise a 3 kg opening PO. Preview / print opens a popup you can save as PDF. Approve or send remaining qty to quarantine from here or the agent."} />
      {error && <ErrorBanner>{error}</ErrorBanner>}
      <RecordSearch value={q} onChange={setQ} count={filtered.length} total={rows.length} />
      {can("PURCHASE_ORDERS:CREATE") && suppliers.length > 0 && (
        <form className="card p-4 mb-4 grid sm:grid-cols-2 lg:grid-cols-5 gap-3" onSubmit={async (e) => {
          e.preventDefault();
          await api.post("/console/purchase-orders", {
            supplierId: Number(form.supplierId), warehouseId: Number(form.warehouseId), notes: "Seasonal cover",
            lines: [{ productId: Number(form.productId), qty: Number(form.qty), unitCost: Number(form.unitCost) }],
          });
          reload();
        }}>
          <select className="input" value={form.supplierId} onChange={(e) => setForm({ ...form, supplierId: e.target.value })}>
            <option value="">Supplier</option>{suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
          <select className="input" value={form.warehouseId} onChange={(e) => setForm({ ...form, warehouseId: e.target.value })}>
            <option value="">Warehouse</option>{warehouses.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
          </select>
          <select className="input" value={form.productId} onChange={(e) => setForm({ ...form, productId: e.target.value })}>
            <option value="">Product</option>{products.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
          </select>
          <input className="input" value={form.qty} onChange={(e) => setForm({ ...form, qty: e.target.value })} />
          <button className="btn-primary">Create PO</button>
        </form>
      )}
      <div className="table-wrap mt-4">
        <table className="data">
          <thead><tr><th>PO</th><th>Supplier</th><th>Status</th><th>Total</th><th></th></tr></thead>
          <tbody>
            {filtered.map((po) => (
              <tr key={po.id}>
                <td>{po.poNo}</td><td>{po.supplier}</td><td><StatusPill value={po.status} /></td><td>{money(po.total)}</td>
                <td className="space-x-1">
                  {!supplierPortal && staffFlow.map((s) => (
                    <button key={s} className="btn-ghost text-[10px]" onClick={async () => { await api.post(`/console/purchase-orders/${po.id}/transition`, { status: s, comment: "Workflow" }); reload(); }}>{s}</button>
                  ))}
                  {supplierPortal && can("PURCHASE_ORDERS:UPDATE") && po.status === "SENT_TO_SUPPLIER" && (
                    <button className="btn-ghost text-[10px]" onClick={async () => { await api.post(`/console/purchase-orders/${po.id}/transition`, { status: "ACKNOWLEDGED", comment: "Supplier acknowledged" }); reload(); }}>Acknowledge ship</button>
                  )}
                  {!supplierPortal && can("PURCHASE_ORDERS:UPDATE") && po.status !== "RECEIVED" && po.status !== "CANCELLED" && (
                    <button className="btn-ghost text-[10px]" onClick={async () => {
                      const qty = window.prompt("New qty for the first line", po.lines?.[0]?.qty ?? "");
                      if (qty == null || qty === "") return;
                      await api.put(`/console/purchase-orders/${po.id}`, { qty });
                      reload();
                    }}>Edit qty</button>
                  )}
                  {!supplierPortal && can("PURCHASE_ORDERS:UPDATE") && (
                    <button className="btn-ghost text-[10px]" onClick={() => removeRecord(`/console/purchase-orders/${po.id}`, reload, setError, "purchase order")}>Delete</button>
                  )}
                  {!supplierPortal && (can("PURCHASE_ORDERS:APPROVE") || can("PURCHASE_ORDERS:UPDATE")) && po.status !== "APPROVED" && po.status !== "RECEIVED" && (
                    <button className="btn-ghost text-[10px]" onClick={async () => { await api.post(`/console/purchase-orders/${po.id}/approve-or-quarantine`, { action: "APPROVE" }); reload(); }}>Approve</button>
                  )}
                  {!supplierPortal && can("INVENTORY:UPDATE") && po.lines?.[0] && po.status !== "RECEIVED" && (
                    <button className="btn-ghost text-[10px]" onClick={async () => { await api.post(`/console/purchase-orders/${po.id}/approve-or-quarantine`, { action: "QUARANTINE" }); reload(); }}>Quarantine</button>
                  )}
                  {can("INVENTORY:UPDATE") && po.lines?.[0] && (
                    <button className="btn-ghost text-[10px]" onClick={async () => {
                      await api.post(`/console/purchase-orders/${po.id}/receive`, { productId: po.lines[0].productId, qty: po.lines[0].qty });
                      reload();
                    }}>GRN</button>
                  )}
                  <button type="button" className="btn-ghost text-[10px]" onClick={() => printPurchaseOrder(po, branding)}>Preview / print</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function Quality() {
  const { can } = useAuth();
  const { data, reload, error, setError } = useLoad(async () => (await api.get("/console/quality")).data);
  const rows = asList(data);
  const canEdit = can("QUALITY:UPDATE") || can("QUALITY:APPROVE");
  const [q, setQ] = useState("");
  const filtered = rows.filter((r) => matchesQuery(r, q));
  const [editId, setEditId] = useState(null);
  const [form, setForm] = useState({});

  function startEdit(q) {
    setEditId(q.id);
    setForm({
      result: q.result && q.result !== "PENDING" ? q.result : "PASSED",
      moisturePct: q.moisturePct ?? "",
      appearance: q.appearance || "",
      foreignMatter: q.foreignMatter || "",
      remarks: q.remarks || "",
    });
  }

  async function save(id) {
    await api.post(`/console/quality/${id}/inspect`, form);
    setEditId(null);
    reload();
  }

  return (
    <div>
      <PageHead title="Quality inspection" hint="Edit moisture, appearance, foreign matter and result, then save to release or hold the lot." />
      {error && <ErrorBanner>{error}</ErrorBanner>}
      <RecordSearch value={q} onChange={setQ} count={filtered.length} total={rows.length} />
      <div className="table-wrap">
        <table className="data">
          <thead>
            <tr>
              <th>QC</th><th>Lot</th><th>Product</th><th>Moisture</th><th>Appearance</th><th>Foreign matter</th><th>Result</th>
              {canEdit && <th></th>}
            </tr>
          </thead>
          <tbody>
            {filtered.map((insp) => (
              <tr key={insp.id}>
                <td>{insp.inspectionNo}</td>
                <td>{insp.lotCode}</td>
                <td>{insp.product}</td>
                {editId === insp.id ? (
                  <>
                    <td><input className="input w-20" value={form.moisturePct} onChange={(e) => setForm({ ...form, moisturePct: e.target.value })} /></td>
                    <td><input className="input" value={form.appearance} onChange={(e) => setForm({ ...form, appearance: e.target.value })} /></td>
                    <td><input className="input" value={form.foreignMatter} onChange={(e) => setForm({ ...form, foreignMatter: e.target.value })} /></td>
                    <td>
                      <select className="input" value={form.result} onChange={(e) => setForm({ ...form, result: e.target.value })}>
                        {["PASSED", "HOLD", "REJECTED", "PENDING"].map((r) => <option key={r} value={r}>{r}</option>)}
                      </select>
                    </td>
                    <td className="space-x-1">
                      <input className="input mb-1" placeholder="Remarks" value={form.remarks} onChange={(e) => setForm({ ...form, remarks: e.target.value })} />
                      <button type="button" className="btn-primary text-xs" onClick={() => save(insp.id)}>Save</button>
                      <button type="button" className="btn-ghost text-xs" onClick={() => setEditId(null)}>Cancel</button>
                    </td>
                  </>
                ) : (
                  <>
                    <td>{insp.moisturePct ?? "—"}</td>
                    <td>{insp.appearance || "—"}</td>
                    <td>{insp.foreignMatter || "—"}</td>
                    <td><StatusPill value={insp.result} /></td>
                    {canEdit && (
                      <td className="space-x-2 whitespace-nowrap">
                        <button type="button" className="btn-ghost text-xs" onClick={() => startEdit(insp)}>Edit</button>
                        <button type="button" className="btn-ghost text-xs" onClick={() => removeRecord(`/console/quality/${insp.id}`, reload, setError, "inspection")}>Delete</button>
                      </td>
                    )}
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function Inventory() {
  const { can } = useAuth();
  const { data, reload, error, setError } = useLoad(async () => (await api.get("/console/inventory")).data);
  const { data: lookupData } = useLoad(async () => (
    can("INVENTORY:CREATE") || can("INVENTORY:UPDATE")
      ? (await api.get("/console/inventory/lookups")).data
      : { products: [], warehouses: [], suppliers: [] }
  ));
  const rows = asList(data);
  const products = asList(lookupData?.products);
  const warehouses = asList(lookupData?.warehouses);
  const suppliers = asList(lookupData?.suppliers);
  const canEdit = can("INVENTORY:UPDATE");
  const canAdd = can("INVENTORY:CREATE") || canEdit;
  const emptyLot = { productId: "", warehouseId: "", supplierId: "", qty: "", expiryOn: "", grade: "", status: "AVAILABLE", lotCode: "" };
  const [newLot, setNewLot] = useState(emptyLot);
  const [editId, setEditId] = useState(null);
  const [form, setForm] = useState({});
  const [q, setQ] = useState("");
  const filtered = rows.filter((r) => matchesQuery(r, q));

  function startEdit(l) {
    setEditId(l.id);
    setForm({
      availableQty: l.availableQty,
      reservedQty: l.reservedQty,
      status: l.status,
      expiryOn: l.expiryOn || "",
      grade: l.grade || "",
    });
  }

  async function save(id) {
    await api.put(`/console/inventory/${id}`, form);
    setEditId(null);
    reload();
  }

  async function addLot(e) {
    e.preventDefault();
    setError("");
    try {
      await api.post("/console/inventory", newLot);
      setNewLot(emptyLot);
      reload();
    } catch (err) {
      setError(errMsg(err));
    }
  }

  return (
    <div>
      <PageHead
        title="Lot inventory"
        hint={can("INVENTORY:UPDATE")
          ? "Warehouse and procurement add lots here. New products also create an available opening lot after auto QA."
          : "Lots from goods you supplied. Available lots can sell; quarantine still waits on QC."}
      />
      {error && <ErrorBanner>{error}</ErrorBanner>}
      {canAdd && (
        <form className="card p-4 mb-4 grid sm:grid-cols-2 lg:grid-cols-4 gap-3" onSubmit={addLot}>
          <p className="sm:col-span-2 lg:col-span-4 text-sm font-semibold text-grove-800">Add lot</p>
          <select className="input" required value={newLot.productId} onChange={(e) => setNewLot({ ...newLot, productId: e.target.value })}>
            <option value="">Product</option>
            {products.map((p) => <option key={p.id} value={p.id}>{p.sku} · {p.name}</option>)}
          </select>
          <select className="input" required value={newLot.warehouseId} onChange={(e) => setNewLot({ ...newLot, warehouseId: e.target.value })}>
            <option value="">Warehouse</option>
            {warehouses.map((w) => <option key={w.id} value={w.id}>{w.name}</option>)}
          </select>
          <select className="input" value={newLot.supplierId} onChange={(e) => setNewLot({ ...newLot, supplierId: e.target.value })}>
            <option value="">Supplier (optional)</option>
            {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
          <input className="input" required placeholder="Qty (kg)" value={newLot.qty} onChange={(e) => setNewLot({ ...newLot, qty: e.target.value })} />
          <input className="input" type="date" value={newLot.expiryOn} onChange={(e) => setNewLot({ ...newLot, expiryOn: e.target.value })} />
          <input className="input" placeholder="Grade" value={newLot.grade} onChange={(e) => setNewLot({ ...newLot, grade: e.target.value })} />
          <select className="input" value={newLot.status} onChange={(e) => setNewLot({ ...newLot, status: e.target.value })}>
            <option value="AVAILABLE">AVAILABLE — sell now</option>
            <option value="QUARANTINE">QUARANTINE — send to QC</option>
            <option value="HOLD">HOLD</option>
          </select>
          <input className="input" placeholder="Lot code (optional)" value={newLot.lotCode} onChange={(e) => setNewLot({ ...newLot, lotCode: e.target.value })} />
          <button className="btn-primary">Add lot</button>
        </form>
      )}
      <RecordSearch value={q} onChange={setQ} count={filtered.length} total={rows.length} />
      <div className="table-wrap">
        <table className="data">
          <thead><tr><th>Lot</th><th>Product</th><th>WH</th><th>Avail</th><th>Reserved</th><th>Status</th><th>Expiry</th>{canEdit && <th></th>}</tr></thead>
          <tbody>
            {filtered.map((l) => (
              <tr key={l.id}>
                <td>{l.lotCode}</td>
                <td>{l.product}</td>
                <td>{l.warehouse}</td>
                {editId === l.id ? (
                  <>
                    <td><input className="input w-24" value={form.availableQty} onChange={(e) => setForm({ ...form, availableQty: e.target.value })} /></td>
                    <td><input className="input w-24" value={form.reservedQty} onChange={(e) => setForm({ ...form, reservedQty: e.target.value })} /></td>
                    <td>
                      <select className="input" value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}>
                        {["QUARANTINE", "AVAILABLE", "HOLD", "EXPIRED", "REJECTED"].map((s) => <option key={s} value={s}>{s}</option>)}
                      </select>
                    </td>
                    <td><input className="input w-32" type="date" value={String(form.expiryOn || "").slice(0, 10)} onChange={(e) => setForm({ ...form, expiryOn: e.target.value })} /></td>
                    <td className="space-x-1">
                      <button type="button" className="btn-primary text-xs" onClick={() => save(l.id)}>Save</button>
                      <button type="button" className="btn-ghost text-xs" onClick={() => setEditId(null)}>Cancel</button>
                    </td>
                  </>
                ) : (
                  <>
                    <td>{l.availableQty}</td>
                    <td>{l.reservedQty}</td>
                    <td><StatusPill value={l.status} /></td>
                    <td className="text-xs">{l.expiryOn}</td>
                    {canEdit && (
                      <td className="space-x-2 whitespace-nowrap">
                        <button type="button" className="btn-ghost text-xs" onClick={() => startEdit(l)}>Edit</button>
                        <button type="button" className="btn-ghost text-xs" onClick={() => removeRecord(`/console/inventory/${l.id}`, reload, setError, "lot")}>Delete</button>
                      </td>
                    )}
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function Reports() {
  const { can } = useAuth();
  const { branding } = useBranding();
  const showPo = can("PURCHASE_ORDERS:VIEW");
  const showInv = can("INVOICES:VIEW");
  const showPeriod = can("REPORTS:VIEW");
  const today = new Date().toISOString().slice(0, 10);
  const now = new Date();
  const [day, setDay] = useState(today);
  const [month, setMonth] = useState(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`);
  const [daily, setDaily] = useState(null);
  const [monthly, setMonthly] = useState(null);
  const [periodError, setPeriodError] = useState("");
  const { data: poData, error: poError } = useLoad(async () => (showPo ? (await api.get("/console/purchase-orders")).data : []));
  const { data: invData, error: invError } = useLoad(async () => (showInv ? (await api.get("/console/invoices")).data : []));
  const pos = asList(poData);
  const invoices = asList(invData);

  async function loadDaily() {
    try {
      setPeriodError("");
      setDaily((await api.get("/console/reports/daily", { params: { date: day } })).data);
    } catch (e) {
      setPeriodError(errMsg(e));
    }
  }

  async function loadMonthly() {
    try {
      setPeriodError("");
      const [year, mo] = month.split("-");
      setMonthly((await api.get("/console/reports/monthly", { params: { year, month: mo } })).data);
    } catch (e) {
      setPeriodError(errMsg(e));
    }
  }

  async function downloadExcel(kind) {
    try {
      setPeriodError("");
      const [year, mo] = month.split("-");
      const url = kind === "daily"
        ? `/console/reports/daily.xlsx?date=${encodeURIComponent(day)}`
        : `/console/reports/monthly.xlsx?year=${year}&month=${mo}`;
      const res = await api.get(url, { responseType: "blob" });
      const blob = new Blob([res.data], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = kind === "daily" ? `daily-${day}.xlsx` : `monthly-${month}.xlsx`;
      a.click();
      URL.revokeObjectURL(a.href);
    } catch (e) {
      setPeriodError(errMsg(e));
    }
  }

  useEffect(() => {
    if (showPeriod) {
      loadDaily();
      loadMonthly();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!showPo && !showInv && !showPeriod) return <Navigate to="/console" replace />;

  function periodCards(rep) {
    if (!rep) return null;
    const cards = [
      ["Orders", rep.orderCount],
      ["Delivered", rep.deliveredCount],
      ["Cancelled", rep.cancelledCount],
      ["Sales", money(rep.salesTotal)],
      ["Invoices", rep.invoiceCount],
      ["Invoiced", money(rep.invoiceTotal)],
      ["Collected", money(rep.invoicePaid)],
      ["POs", rep.poCount],
      ["PO value", money(rep.poTotal)],
      ["QC done", rep.qcCount],
    ];
    return (
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3 mb-4">
        {cards.map(([k, v]) => (
          <div key={k} className="card p-3">
            <p className="text-[11px] uppercase tracking-wide text-grove-600">{k}</p>
            <p className="font-display text-xl text-grove-800 mt-1">{v}</p>
          </div>
        ))}
      </div>
    );
  }

  return (
    <div>
      <PageHead
        title="Reports"
        hint="Daily and monthly trading totals (Asia/Dubai). Export Excel for a summary sheet plus sales, invoices, purchase orders and quality grids."
      />
      {(poError || invError || periodError) && <ErrorBanner>{poError || invError || periodError}</ErrorBanner>}
      {showPeriod && (
        <>
          <section className="mb-8">
            <h2 className="font-display text-2xl text-grove-800 mb-2">Daily report</h2>
            <div className="flex flex-wrap gap-2 items-end mb-3">
              <label className="text-sm text-grove-700">
                Day
                <input className="input mt-1" type="date" value={day} onChange={(e) => setDay(e.target.value)} />
              </label>
              <button type="button" className="btn-primary" onClick={loadDaily}>Load day</button>
              {daily && (
                <>
                  <button type="button" className="btn-ghost" onClick={() => printPeriodReport(daily, branding)}>Preview / print</button>
                  <button type="button" className="btn-ghost" onClick={() => downloadExcel("daily")}>Export Excel</button>
                </>
              )}
            </div>
            {periodCards(daily)}
            {daily && (
              <div className="space-y-4">
                <div className="table-wrap">
                  <table className="data">
                    <thead><tr><th>Order</th><th>Customer</th><th>Status</th><th>Total</th></tr></thead>
                    <tbody>
                      {asList(daily.orders).map((o) => (
                        <tr key={o.orderNo}><td>{o.orderNo}</td><td>{o.customer}</td><td><StatusPill value={o.status} /></td><td>{money(o.total)}</td></tr>
                      ))}
                      {asList(daily.orders).length === 0 && <tr><td colSpan={4} className="text-grove-600">No sales this day.</td></tr>}
                    </tbody>
                  </table>
                </div>
                <div className="table-wrap">
                  <table className="data">
                    <thead><tr><th>Invoice</th><th>Order</th><th>Amount</th><th>Paid</th><th>Status</th></tr></thead>
                    <tbody>
                      {asList(daily.invoices).map((i) => (
                        <tr key={i.invoiceNo}><td>{i.invoiceNo}</td><td>{i.orderNo}</td><td>{money(i.amount)}</td><td>{money(i.paidAmount)}</td><td><StatusPill value={i.status} /></td></tr>
                      ))}
                      {asList(daily.invoices).length === 0 && <tr><td colSpan={5} className="text-grove-600">No invoices this day.</td></tr>}
                    </tbody>
                  </table>
                </div>
                <div className="table-wrap">
                  <table className="data">
                    <thead><tr><th>PO</th><th>Supplier</th><th>Status</th><th>Total</th></tr></thead>
                    <tbody>
                      {asList(daily.purchaseOrders).map((p) => (
                        <tr key={p.poNo}><td>{p.poNo}</td><td>{p.supplier}</td><td><StatusPill value={p.status} /></td><td>{money(p.total)}</td></tr>
                      ))}
                      {asList(daily.purchaseOrders).length === 0 && <tr><td colSpan={4} className="text-grove-600">No purchase orders this day.</td></tr>}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </section>
          <section className="mb-8">
            <h2 className="font-display text-2xl text-grove-800 mb-2">Monthly report</h2>
            <div className="flex flex-wrap gap-2 items-end mb-3">
              <label className="text-sm text-grove-700">
                Month
                <input className="input mt-1" type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
              </label>
              <button type="button" className="btn-primary" onClick={loadMonthly}>Load month</button>
              {monthly && (
                <>
                  <button type="button" className="btn-ghost" onClick={() => printPeriodReport(monthly, branding)}>Preview / print</button>
                  <button type="button" className="btn-ghost" onClick={() => downloadExcel("monthly")}>Export Excel</button>
                </>
              )}
            </div>
            {periodCards(monthly)}
            {monthly && (
              <div className="space-y-4">
                <div className="table-wrap">
                  <table className="data">
                    <thead><tr><th>Order</th><th>Customer</th><th>Status</th><th>Total</th></tr></thead>
                    <tbody>
                      {asList(monthly.orders).map((o) => (
                        <tr key={o.orderNo}><td>{o.orderNo}</td><td>{o.customer}</td><td><StatusPill value={o.status} /></td><td>{money(o.total)}</td></tr>
                      ))}
                      {asList(monthly.orders).length === 0 && <tr><td colSpan={4} className="text-grove-600">No sales this month.</td></tr>}
                    </tbody>
                  </table>
                </div>
                <div className="table-wrap">
                  <table className="data">
                    <thead><tr><th>Invoice</th><th>Order</th><th>Amount</th><th>Paid</th><th>Status</th></tr></thead>
                    <tbody>
                      {asList(monthly.invoices).map((i) => (
                        <tr key={i.invoiceNo}><td>{i.invoiceNo}</td><td>{i.orderNo}</td><td>{money(i.amount)}</td><td>{money(i.paidAmount)}</td><td><StatusPill value={i.status} /></td></tr>
                      ))}
                      {asList(monthly.invoices).length === 0 && <tr><td colSpan={5} className="text-grove-600">No invoices this month.</td></tr>}
                    </tbody>
                  </table>
                </div>
                <div className="table-wrap">
                  <table className="data">
                    <thead><tr><th>PO</th><th>Supplier</th><th>Status</th><th>Total</th></tr></thead>
                    <tbody>
                      {asList(monthly.purchaseOrders).map((p) => (
                        <tr key={p.poNo}><td>{p.poNo}</td><td>{p.supplier}</td><td><StatusPill value={p.status} /></td><td>{money(p.total)}</td></tr>
                      ))}
                      {asList(monthly.purchaseOrders).length === 0 && <tr><td colSpan={4} className="text-grove-600">No purchase orders this month.</td></tr>}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </section>
        </>
      )}
      {showPo && (
        <section className="mb-8">
          <h2 className="font-display text-2xl text-grove-800 mb-2">Purchase orders</h2>
          <p className="text-sm text-grove-600 mb-3">Same documents as Purchase orders. Preview here without changing status.</p>
          <div className="table-wrap">
            <table className="data">
              <thead><tr><th>PO</th><th>Supplier</th><th>Status</th><th>Total</th><th></th></tr></thead>
              <tbody>
                {pos.map((po) => (
                  <tr key={po.id}>
                    <td>{po.poNo}</td><td>{po.supplier}</td><td><StatusPill value={po.status} /></td><td>{money(po.total)}</td>
                    <td>
                      <button type="button" className="btn-ghost text-xs" onClick={() => printPurchaseOrder(po, branding)}>Preview / print</button>
                    </td>
                  </tr>
                ))}
                {pos.length === 0 && <tr><td colSpan={5} className="text-grove-600">No purchase orders yet.</td></tr>}
              </tbody>
            </table>
          </div>
        </section>
      )}
      {showInv && (
        <section>
          <h2 className="font-display text-2xl text-grove-800 mb-2">Invoices</h2>
          <p className="text-sm text-grove-600 mb-3">Same documents as Invoices. Preview for the customer or accounts.</p>
          <div className="table-wrap">
            <table className="data">
              <thead><tr><th>Invoice</th><th>Order</th><th>Customer</th><th>Amount</th><th></th></tr></thead>
              <tbody>
                {invoices.map((i) => (
                  <tr key={i.id}>
                    <td>{i.invoiceNo}</td><td>{i.orderNo}</td><td>{i.customer}</td><td>{money(i.amount)}</td>
                    <td>
                      <button type="button" className="btn-ghost text-xs" onClick={() => printInvoice(i, branding)}>Preview / print</button>
                    </td>
                  </tr>
                ))}
                {invoices.length === 0 && <tr><td colSpan={5} className="text-grove-600">No invoices yet.</td></tr>}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

export function Invoices() {
  const { can } = useAuth();
  const { branding } = useBranding();
  const { data, reload } = useLoad(async () => (await api.get("/console/invoices")).data);
  const rows = asList(data);
  return (
    <div>
      <PageHead title="Invoices" hint="Bills are created as soon as a buyer places an order. Print for the customer or accounts. Who can view: finance, sales, trading manager, and admin." />
      <div className="table-wrap">
        <table className="data">
          <thead><tr><th>Invoice</th><th>Order</th><th>Customer</th><th>Amount</th><th>Paid</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {rows.map((i) => (
              <tr key={i.id}>
                <td>{i.invoiceNo}</td><td>{i.orderNo}</td><td>{i.customer}</td>
                <td>{money(i.amount)}</td><td>{money(i.paidAmount)}</td>
                <td><StatusPill value={i.status} /></td>
                <td>
                  <div className="flex flex-wrap gap-1">
                    <button type="button" className="btn-ghost text-xs" onClick={() => printInvoice(i, branding)}>Preview / print</button>
                    {i.status !== "PAID" && can("INVOICES:UPDATE") && (
                      <button className="btn-ghost text-xs" onClick={async () => { await api.post(`/console/invoices/${i.id}/pay`, { amount: i.amount, method: "BANK" }); reload(); }}>Record payment</button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

const EMPTY_USER = { id: "", username: "", fullName: "", email: "", phone: "", password: "", creditLimit: "0", roles: ["CUSTOMER"] };

export function UsersPage() {
  const { can } = useAuth();
  const { data: userData, reload, error, setError } = useLoad(async () => (await api.get("/console/users")).data);
  const { data: roleData } = useLoad(async () => (await api.get("/console/role-options")).data);
  const rows = asList(userData);
  const roles = asList(roleData);
  const [form, setForm] = useState(EMPTY_USER);
  const [q, setQ] = useState("");
  const filtered = rows.filter((r) => matchesQuery(r, q));
  const editing = Boolean(form.id);
  const canSave = editing ? can("USERS:UPDATE") : can("USERS:CREATE");
  const roleOptions = roles.length
    ? roles
    : [
        { code: "CUSTOMER", name: "Customer" },
        { code: "SALES_EXECUTIVE", name: "Wholesale sales" },
        { code: "SALES_MANAGER", name: "Sales manager" },
        { code: "SUPPLIER", name: "Grower / packer portal" },
        { code: "WAREHOUSE_KEEPER", name: "Warehouse keeper" },
        { code: "PROCUREMENT_OFFICER", name: "Procurement officer" },
        { code: "QUALITY_INSPECTOR", name: "Quality inspector" },
        { code: "FINANCE_OFFICER", name: "Finance officer" },
        { code: "CATALOG_MERCHANDISER", name: "Catalog merchandiser" },
        { code: "TRADING_MANAGER", name: "Trading manager" },
      ];

  function startEdit(u) {
    setError("");
    setForm({
      id: u.id,
      username: u.username || "",
      fullName: u.fullName || "",
      email: u.email || "",
      phone: u.phone || "",
      password: "",
      creditLimit: u.creditLimit ?? "0",
      roles: asList(u.roles).length ? asList(u.roles) : ["CUSTOMER"],
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function save(e) {
    e.preventDefault();
    setError("");
    try {
      const payload = { ...form };
      if (!payload.id) delete payload.id;
      if (!payload.password) delete payload.password;
      await api.post("/console/users", payload);
      setForm(EMPTY_USER);
      reload();
    } catch (err) {
      setError(errMsg(err));
    }
  }

  return (
    <div>
      <PageHead title="Users" hint="Create logins or edit name, email, phone, credit and role. Leave password blank when editing to keep the current one." />
      {error && <ErrorBanner>{error}</ErrorBanner>}
      {canSave && (
        <form className="card p-4 mb-4 grid sm:grid-cols-2 lg:grid-cols-3 gap-3" onSubmit={save}>
          <p className="sm:col-span-2 lg:col-span-3 text-sm font-semibold text-grove-800">{editing ? `Edit ${form.username}` : "New user"}</p>
          <input className="input" placeholder="Username" disabled={editing} value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} />
          <input className="input" placeholder="Full name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
          <input className="input" placeholder="Email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
          <input className="input" placeholder="Phone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
          <input className="input" placeholder={editing ? "New password (optional)" : "Password"} type="password" value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} />
          <input className="input" placeholder="Credit limit" value={form.creditLimit} onChange={(e) => setForm({ ...form, creditLimit: e.target.value })} />
          <select className="input" value={form.roles[0]} onChange={(e) => setForm({ ...form, roles: [e.target.value] })}>
            {roleOptions.map((r) => <option key={r.code} value={r.code}>{r.name}</option>)}
          </select>
          <div className="flex flex-wrap gap-2">
            <button className="btn-primary">{editing ? "Save user" : "Add user"}</button>
            {editing && <button type="button" className="btn-ghost" onClick={() => setForm(EMPTY_USER)}>Cancel</button>}
          </div>
        </form>
      )}
      <RecordSearch value={q} onChange={setQ} count={filtered.length} total={rows.length} />
      <div className="table-wrap">
        <table className="data">
          <thead><tr><th>User</th><th>Name</th><th>Email</th><th>Roles</th><th>Credit</th>{can("USERS:UPDATE") && <th></th>}</tr></thead>
          <tbody>
            {filtered.map((u) => (
              <tr key={u.id}>
                <td>{u.username}</td>
                <td>{u.fullName}</td>
                <td className="text-xs">{u.email}</td>
                <td>{asList(u.roles).join(", ")}</td>
                <td>{money(u.creditLimit)}</td>
                {can("USERS:UPDATE") && (
                  <td className="space-x-2 whitespace-nowrap">
                    <button type="button" className="btn-ghost text-xs" onClick={() => startEdit(u)}>Edit</button>
                    <button type="button" className="btn-ghost text-xs" onClick={() => removeRecord(`/console/users/${u.id}`, reload, setError, "user")}>Delete</button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function RolesPage() {
  const { data: roleData, reload } = useLoad(async () => (await api.get("/console/roles")).data);
  const { data: screenData } = useLoad(async () => (await api.get("/console/screens")).data);
  const roles = asList(roleData);
  const screens = asList(screenData);
  const [active, setActive] = useState(null);
  const [perms, setPerms] = useState([]);
  useEffect(() => {
    if (active) setPerms(asList(active.permissions));
  }, [active]);
  function toggle(p) {
    setPerms(perms.includes(p) ? perms.filter((x) => x !== p) : [...perms, p]);
  }
  const actions = ["VIEW", "CREATE", "UPDATE", "APPROVE", "DELETE"];
  return (
    <div>
      <PageHead title="Roles & screens" hint="Tick what each role can see and do. Grants apply on next login." />
      <div className="grid lg:grid-cols-[220px_1fr] gap-4">
        <div className="flex lg:flex-col gap-1 overflow-x-auto">
          {roles.map((r) => (
            <button key={r.id} className={`text-left px-3 py-2 rounded-xl whitespace-nowrap ${active?.id === r.id ? "bg-grove-600 text-white" : "bg-white border border-grove-100"}`} onClick={() => setActive(r)}>
              {r.name}
            </button>
          ))}
        </div>
        {active && (
          <div className="card p-4 overflow-x-auto">
            <p className="text-sm text-grove-600 mb-3">{active.description}</p>
            <table className="data">
              <thead><tr><th>Screen</th>{actions.map((a) => <th key={a}>{a}</th>)}</tr></thead>
              <tbody>
                {screens.map((s) => (
                  <tr key={s.code}>
                    <td>{s.name}</td>
                    {actions.map((a) => {
                      const code = `${s.code}:${a}`;
                      return (
                        <td key={a}>
                          <input type="checkbox" checked={perms.includes(code)} onChange={() => toggle(code)} />
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
            <button className="btn-primary mt-4" onClick={async () => { await api.put(`/console/roles/${active.id}/permissions`, perms); reload(); }}>Save grants</button>
          </div>
        )}
      </div>
    </div>
  );
}

export function AuditPage() {
  const { data } = useLoad(async () => (await api.get("/console/audit")).data);
  const rows = asList(data);
  return (
    <div>
      <PageHead title="Audit trail" hint="Who changed what — logins, cart, orders, QC, payments." />
      <div className="table-wrap">
        <table className="data">
          <thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Entity</th><th>Details</th></tr></thead>
          <tbody>
            {rows.map((a) => (
              <tr key={a.id}>
                <td className="text-xs">{a.occurredAt}</td><td>{a.actor}</td><td>{a.action}</td>
                <td>{a.entityType} {a.entityId}</td><td className="text-xs">{a.details}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export function WorkflowsPage() {
  const { can } = useAuth();
  const { data, reload, error, setError } = useLoad(async () => (await api.get("/console/workflows")).data);
  const rows = asList(data);
  const canEdit = can("WORKFLOWS:UPDATE");
  const canAdd = can("WORKFLOWS:CREATE") || canEdit;
  const empty = { entityType: "SALES_ORDER", entityRef: "", fromStatus: "", toStatus: "", comment: "" };
  const [form, setForm] = useState(empty);
  const [editId, setEditId] = useState(null);
  const [edit, setEdit] = useState({});
  const [q, setQ] = useState("");
  const filtered = rows.filter((r) => matchesQuery(r, q));

  async function add(e) {
    e.preventDefault();
    setError("");
    try {
      await api.post("/console/workflows", form);
      setForm(empty);
      reload();
    } catch (err) {
      setError(errMsg(err));
    }
  }

  async function save(id) {
    setError("");
    try {
      await api.put(`/console/workflows/${id}`, edit);
      setEditId(null);
      reload();
    } catch (err) {
      setError(errMsg(err));
    }
  }

  return (
    <div>
      <PageHead title="Workflow history" hint="Status moves for products, POs, QC and sales orders. Add a note or correct from/to and comment." />
      {error && <ErrorBanner>{error}</ErrorBanner>}
      {canAdd && (
        <form className="card p-4 mb-4 grid sm:grid-cols-2 lg:grid-cols-5 gap-3" onSubmit={add}>
          <select className="input" value={form.entityType} onChange={(e) => setForm({ ...form, entityType: e.target.value })}>
            {["PRODUCT", "SALES_ORDER", "PURCHASE_ORDER", "QUALITY_INSPECTION", "RETURN"].map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
          <input className="input" placeholder="Ref (SKU / SO / PO / QC)" value={form.entityRef} onChange={(e) => setForm({ ...form, entityRef: e.target.value })} />
          <input className="input" placeholder="From status" value={form.fromStatus} onChange={(e) => setForm({ ...form, fromStatus: e.target.value })} />
          <input className="input" placeholder="To status" value={form.toStatus} onChange={(e) => setForm({ ...form, toStatus: e.target.value })} />
          <input className="input" placeholder="Comment" value={form.comment} onChange={(e) => setForm({ ...form, comment: e.target.value })} />
          <button className="btn-primary">Add step</button>
        </form>
      )}
      <RecordSearch value={q} onChange={setQ} count={filtered.length} total={rows.length} />
      <div className="table-wrap">
        <table className="data">
          <thead><tr><th>Entity</th><th>Ref</th><th>From</th><th>To</th><th>Comment</th><th>Actor</th>{canEdit && <th></th>}</tr></thead>
          <tbody>
            {filtered.map((w) => (
              <tr key={w.id}>
                <td>{w.entityType}</td>
                <td>{w.entityRef}</td>
                {editId === w.id ? (
                  <>
                    <td><input className="input" value={edit.fromStatus} onChange={(e) => setEdit({ ...edit, fromStatus: e.target.value })} /></td>
                    <td><input className="input" value={edit.toStatus} onChange={(e) => setEdit({ ...edit, toStatus: e.target.value })} /></td>
                    <td><input className="input" value={edit.comment} onChange={(e) => setEdit({ ...edit, comment: e.target.value })} /></td>
                    <td>{w.actor}</td>
                    <td className="space-x-1">
                      <button type="button" className="btn-primary text-xs" onClick={() => save(w.id)}>Save</button>
                      <button type="button" className="btn-ghost text-xs" onClick={() => setEditId(null)}>Cancel</button>
                    </td>
                  </>
                ) : (
                  <>
                    <td>{w.fromStatus}</td>
                    <td>{w.toStatus}</td>
                    <td className="text-xs">{w.comment}</td>
                    <td>{w.actor}</td>
                    {canEdit && (
                      <td className="space-x-2 whitespace-nowrap">
                        <button type="button" className="btn-ghost text-xs" onClick={() => {
                          setEditId(w.id);
                          setEdit({ fromStatus: w.fromStatus || "", toStatus: w.toStatus || "", comment: w.comment || "" });
                        }}>Edit</button>
                        <button type="button" className="btn-ghost text-xs" onClick={() => removeRecord(`/console/workflows/${w.id}`, reload, setError, "workflow step")}>Delete</button>
                      </td>
                    )}
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
