import { money } from "./format";

function esc(v) {
  return String(v ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function absUrl(src) {
  if (!src) return "";
  try {
    return new URL(src, window.location.origin).href;
  } catch {
    return src;
  }
}

function linesTable(lines, costKey, totalKey) {
  const rows = (lines || []).map((l) => `
    <tr>
      <td>${esc(l.sku)}</td>
      <td>${esc(l.name)}</td>
      <td>${esc(l.qty)} ${esc(l.uom || "")}</td>
      <td>${esc(money(l[costKey]))}</td>
      <td>${esc(money(l[totalKey]))}</td>
    </tr>`).join("");
  return `
    <table>
      <thead><tr><th>SKU</th><th>Item</th><th>Qty</th><th>Unit</th><th>Amount</th></tr></thead>
      <tbody>${rows || "<tr><td colspan='5'>No lines</td></tr>"}</tbody>
    </table>`;
}

function brandBits(brand) {
  const name = brand?.brandName || "Rising Crescent";
  const logo = brand?.logoUrl
    ? `<img src="${esc(absUrl(brand.logoUrl))}" alt="" style="height:48px;width:48px;border-radius:999px;object-cover" />`
    : "";
  return { name, logo };
}

export function printPurchaseOrder(po, brand) {
  const { name, logo } = brandBits(brand);
  openPrintPreview(`${name} ${po.poNo}`, `
    <header>
      ${logo}
      <div>
        <h1>${esc(name)}</h1>
        <p>Purchase order</p>
      </div>
      <div class="meta">
        <strong>${esc(po.poNo)}</strong><br/>
        Status: ${esc(String(po.status || "").replaceAll("_", " "))}<br/>
        Expected: ${esc(po.expectedDate || "—")}
      </div>
    </header>
    <section class="grid">
      <div>
        <h2>Supplier</h2>
        <p>${esc(po.supplier)}</p>
        <p>${esc(po.supplierContact || "")}</p>
        <p>${esc(po.supplierEmail || "")}</p>
        <p>${esc(po.supplierPhone || "")}</p>
      </div>
      <div>
        <h2>Deliver to</h2>
        <p>${esc(po.warehouse)}</p>
        <p>${esc(po.warehouseCity || "")}</p>
      </div>
    </section>
    ${linesTable(po.lines, "unitCost", "lineTotal")}
    <p class="total">Total ${esc(money(po.total))}</p>
    ${po.notes ? `<p class="notes">Notes: ${esc(po.notes)}</p>` : ""}
  `);
}

function displayContact(v) {
  const s = String(v ?? "").trim();
  if (!s || s.toLowerCase().includes("@phone.risingcrescent.local")) return "";
  return s;
}

export function printInvoice(inv, brand) {
  const { name, logo } = brandBits(brand);
  const email = displayContact(inv.customerEmail);
  const phone = displayContact(inv.customerPhone);
  const billName = displayContact(inv.companyName) || displayContact(inv.customer);
  openPrintPreview(`${name} ${inv.invoiceNo}`, `
    <header>
      ${logo}
      <div>
        <h1>${esc(name)}</h1>
        <p>Tax invoice</p>
      </div>
      <div class="meta">
        <strong>${esc(inv.invoiceNo)}</strong><br/>
        Order ${esc(inv.orderNo)}<br/>
        Status: ${esc(String(inv.status || "").replaceAll("_", " "))}<br/>
        Due: ${esc(inv.dueDate || "—")}
      </div>
    </header>
    <section class="grid">
      <div>
        <h2>Bill to</h2>
        ${billName ? `<p>${esc(billName)}</p>` : ""}
        ${displayContact(inv.customer) && displayContact(inv.customer) !== billName ? `<p>${esc(displayContact(inv.customer))}</p>` : ""}
        ${email ? `<p>${esc(email)}</p>` : ""}
        ${phone ? `<p>${esc(phone)}</p>` : ""}
      </div>
      <div>
        <h2>Ship to</h2>
        <p>${esc(displayContact(inv.shipToName) || "")}</p>
        <p>${esc(displayContact(inv.shipToPhone) || "")}</p>
        <p>${esc(inv.shipToAddress || "")}</p>
      </div>
    </section>
    ${linesTable(inv.lines, "unitPrice", "lineTotal")}
    <p>Subtotal ${esc(money(inv.subtotal))}</p>
    <p>VAT ${esc(money(inv.tax))}</p>
    <p class="total">Amount due ${esc(money(inv.amount))}</p>
    <p>Paid ${esc(money(inv.paidAmount))}</p>
  `);
}

export function printSalesBill(order, brand) {
  printInvoice({
    invoiceNo: order.invoiceNo || order.orderNo,
    orderNo: order.orderNo,
    customer: order.customer,
    customerEmail: order.customerEmail,
    companyName: order.customer,
    shipToName: order.shipToName,
    shipToPhone: order.shipToPhone,
    shipToAddress: order.shipToAddress,
    lines: order.lines,
    subtotal: order.subtotal,
    tax: order.tax,
    amount: order.total,
    paidAmount: order.paymentStatus === "PAID" ? order.total : 0,
    status: order.paymentMethod === "CASH_ON_DELIVERY" ? "CASH ON DELIVERY" : order.paymentStatus,
    dueDate: "",
  }, brand);
}

export function printPeriodReport(rep, brand) {
  const { name, logo } = brandBits(brand);
  const title = `${rep.kind === "MONTHLY" ? "Monthly" : "Daily"} report ${rep.label}`;
  const orderRows = (rep.orders || []).map((o) => `
    <tr>
      <td>${esc(o.orderNo)}</td>
      <td>${esc(o.customer)}</td>
      <td>${esc(o.status)}</td>
      <td>${esc(money(o.total))}</td>
    </tr>`).join("");
  openPrintPreview(`${name} ${title}`, `
    <header>
      ${logo}
      <div>
        <h1>${esc(name)}</h1>
        <p>${esc(title)}</p>
        <p>Timezone ${esc(rep.timezone || "Asia/Dubai")}</p>
      </div>
    </header>
    <section class="grid">
      <div>
        <h2>Sales</h2>
        <p>Orders ${esc(rep.orderCount)}</p>
        <p>Delivered ${esc(rep.deliveredCount)}</p>
        <p>Cancelled ${esc(rep.cancelledCount)}</p>
        <p>Sales ${esc(money(rep.salesTotal))}</p>
      </div>
      <div>
        <h2>Accounts &amp; supply</h2>
        <p>Invoices ${esc(rep.invoiceCount)} · ${esc(money(rep.invoiceTotal))}</p>
        <p>Collected ${esc(money(rep.invoicePaid))}</p>
        <p>Purchase orders ${esc(rep.poCount)} · ${esc(money(rep.poTotal))}</p>
        <p>QC completed ${esc(rep.qcCount)}</p>
      </div>
    </section>
    <table>
      <thead><tr><th>Order</th><th>Customer</th><th>Status</th><th>Total</th></tr></thead>
      <tbody>${orderRows || "<tr><td colspan='4'>No sales in this period</td></tr>"}</tbody>
    </table>
  `);
}

export function documentPreviewHtml(inner) {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="color-scheme" content="light only" />
  <style>
    :root { color-scheme: light only; background: #ffffff; }
    html, body {
      background: #ffffff !important;
      color: #14532d !important;
      color-scheme: light only;
      font-family: Georgia, "Times New Roman", serif;
      margin: 0;
    }
    .sheet { padding: 32px; background: #ffffff; }
    header { display: flex; gap: 16px; align-items: center; justify-content: space-between; border-bottom: 2px solid #16a34a; padding-bottom: 16px; }
    h1 { margin: 0; font-size: 28px; }
    h2 { font-size: 13px; letter-spacing: .08em; text-transform: uppercase; margin: 0 0 8px; }
    p { margin: 0 0 4px; }
    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin: 24px 0; }
    table { width: 100%; border-collapse: collapse; margin-top: 12px; }
    th, td { text-align: left; padding: 8px; border-bottom: 1px solid #dcfce7; font-size: 14px; }
    .total { font-size: 20px; font-weight: 700; margin-top: 16px; }
    .notes { margin-top: 24px; }
  </style>
</head>
<body><div class="sheet">${inner}</div></body>
</html>`;
}

function openPrintPreview(title, inner) {
  window.dispatchEvent(new CustomEvent("rc-print-preview", {
    detail: { title, html: documentPreviewHtml(inner) },
  }));
}
