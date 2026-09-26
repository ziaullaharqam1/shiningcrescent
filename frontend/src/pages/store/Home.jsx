import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { BadgeCheck, Package, Scale, Search, ShieldCheck, Snowflake, Truck } from "lucide-react";
import api from "../../api/client";
import ProductCard from "../../components/ProductCard";
import ProducePhoto from "../../components/ProducePhoto";

const QUICK_TAGS = [
  { label: "All SKUs", q: "" },
  { label: "Alphonso Mango", q: "mango" },
  { label: "Kashmir Apple", q: "apple" },
  { label: "Oman Fard Dates", q: "dates" },
  { label: "Mamra Almonds", q: "almond" },
  { label: "Available Today", q: "", inStock: true },
];

const BENTO = [
  {
    hint: "mango",
    name: "Alphonso mango",
    meta: "Ratnagiri · Grade A",
    badge: "Verified · 14.8° Brix",
    className: "col-span-7 row-span-4",
  },
  {
    hint: "dates",
    name: "Oman Fard",
    meta: "Sun-dried lot",
    badge: "FEFO Tier 1",
    className: "col-span-5 row-span-2",
  },
  {
    hint: "pistachio",
    name: "Akbari",
    meta: "Iran · Premium",
    badge: "Cold-chain OK",
    className: "col-span-5 row-span-2",
  },
  {
    hint: "apple",
    name: "Kashmir Royal",
    meta: "High orchard",
    badge: "+2.4°C hold",
    className: "col-span-12 row-span-2",
  },
];

export default function Home() {
  const [params, setParams] = useSearchParams();
  const [items, setItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [q, setQ] = useState(params.get("q") || "");
  const [kind, setKind] = useState(params.get("kind") || "");
  const [categoryId, setCategoryId] = useState(params.get("categoryId") || "");
  const [sort, setSort] = useState("fefo");
  const [pricingMode, setPricingMode] = useState(params.get("pricing") === "trade" ? "trade" : "retail");
  const [inStockOnly, setInStockOnly] = useState(false);
  const [activeTag, setActiveTag] = useState("All SKUs");

  async function load(attempt = 0, overrides = {}) {
    try {
      const query = {};
      const k = overrides.kind ?? kind;
      const cid = overrides.categoryId ?? categoryId;
      const queryText = overrides.q ?? q;
      if (k) query.kind = k;
      if (cid) query.categoryId = cid;
      if (queryText) query.q = queryText;
      const [{ data: products }, { data: cats }] = await Promise.all([
        api.get("/catalog/products", { params: query }),
        api.get("/catalog/categories"),
      ]);
      setItems(products);
      setCategories(cats);
    } catch {
      if (attempt < 4) setTimeout(() => load(attempt + 1, overrides), 400 * (attempt + 1));
    }
  }

  useEffect(() => {
    const k = params.get("kind") || "";
    const cid = params.get("categoryId") || "";
    const query = params.get("q") || "";
    const pricing = params.get("pricing") === "trade" ? "trade" : "retail";
    setKind(k);
    setCategoryId(cid);
    setPricingMode(pricing);
    if (query) setQ(query);
  }, [params]);

  useEffect(() => {
    load();
  }, [kind, categoryId]);

  const visible = useMemo(() => {
    let list = [...items];
    if (inStockOnly) list = list.filter((p) => Number(p.availableQty) > 0);
    if (sort === "price-asc") {
      list.sort((a, b) => Number(pricingMode === "trade" ? a.wholesalePrice : a.retailPrice)
        - Number(pricingMode === "trade" ? b.wholesalePrice : b.retailPrice));
    }
    if (sort === "price-desc") {
      list.sort((a, b) => Number(pricingMode === "trade" ? b.wholesalePrice : b.retailPrice)
        - Number(pricingMode === "trade" ? a.wholesalePrice : a.retailPrice));
    }
    if (sort === "stock-desc") list.sort((a, b) => Number(b.availableQty) - Number(a.availableQty));
    if (sort === "fefo") list.sort((a, b) => Number(a.availableQty) - Number(b.availableQty));
    return list;
  }, [items, inStockOnly, sort, pricingMode]);

  function writeParams({ nextKind = kind, nextCategory = categoryId, nextQ = q, nextPricing = pricingMode } = {}) {
    const next = new URLSearchParams();
    if (nextKind) next.set("kind", nextKind);
    if (nextCategory) next.set("categoryId", nextCategory);
    if (nextQ) next.set("q", nextQ);
    if (nextPricing === "trade") next.set("pricing", "trade");
    setParams(next, { replace: false });
  }

  function applySearch(e) {
    e?.preventDefault();
    writeParams();
    load();
    document.getElementById("floor")?.scrollIntoView({ behavior: "smooth" });
  }

  function applyTag(tag) {
    setActiveTag(tag.label);
    setInStockOnly(!!tag.inStock);
    setQ(tag.q || "");
    writeParams({ nextQ: tag.q || "" });
    load(0, { q: tag.q || "" });
    document.getElementById("floor")?.scrollIntoView({ behavior: "smooth" });
  }

  function setMode(mode) {
    setPricingMode(mode);
    writeParams({ nextPricing: mode });
    document.getElementById("floor")?.scrollIntoView({ behavior: "smooth" });
  }

  return (
    <div className="bg-surface">
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_12%_0%,rgba(232,245,238,0.98)_0%,rgba(250,250,249,0.92)_42%,#fafaf9_78%)]" />
        <div className="absolute right-[-8%] top-[-10%] w-[520px] h-[520px] rounded-full bg-secondary-container/35 blur-3xl pointer-events-none" />
        <div className="absolute left-[-6%] bottom-[-20%] w-[360px] h-[360px] rounded-full bg-harvest-soft/40 blur-3xl pointer-events-none" />

        <div className="relative max-w-frame mx-auto px-4 lg:px-10 pt-16 pb-8 lg:pt-24 lg:pb-10 grid lg:grid-cols-12 gap-12 lg:gap-16 items-center">
          <div className="lg:col-span-6 text-center lg:text-left animate-rise">
            <div className="inline-flex items-center gap-2.5 px-3.5 py-1.5 rounded-full bg-white/70 backdrop-blur-md border border-white/80 text-secondary text-[11px] font-semibold uppercase tracking-[0.18em] mb-7 shadow-soft">
              <span className="inline-block w-1.5 h-1.5 rounded-full bg-secondary animate-pulse" />
              <span>House of Harvest · Batch Traced</span>
            </div>
            <h1 className="font-display text-[2.35rem] sm:text-5xl lg:text-[3.35rem] text-primary font-semibold tracking-tight leading-[1.06] max-w-xl mx-auto lg:mx-0 mb-6">
              Fresh fruit, sun-dried harvests, and prized tree nuts.
            </h1>
            <p className="text-ink-muted text-base sm:text-lg font-light max-w-lg mx-auto lg:mx-0 leading-relaxed mb-9">
              Procured from sovereign groves, held on cold-chain specs, and dispatched FEFO — retail crates or wholesale sacks for purchase.
            </p>
            <div className="flex flex-wrap items-center justify-center lg:justify-start gap-3 mb-10">
              <a
                href="#floor"
                className="inline-flex items-center gap-2 bg-primary hover:bg-primary-container text-white px-6 py-3.5 rounded-2xl text-sm font-semibold shadow-lift transition-all"
              >
                Shop retail
              </a>
              <Link
                to="/?pricing=trade#floor"
                className="inline-flex items-center gap-2 bg-white/80 backdrop-blur-md border border-white text-primary px-6 py-3.5 rounded-2xl text-sm font-semibold shadow-soft hover:border-primary/20 transition-colors"
              >
                <Package size={16} />
                Shop wholesale sacks
              </Link>
            </div>
            <div className="flex flex-wrap items-center justify-center lg:justify-start gap-x-7 gap-y-2 text-[11px] font-medium uppercase tracking-[0.14em] text-ink-muted">
              <span className="inline-flex items-center gap-1.5"><Snowflake size={14} className="text-primary" /> +2.4°C cold chain</span>
              <span className="inline-flex items-center gap-1.5"><ShieldCheck size={14} className="text-primary" /> Lot traced</span>
              <span className="inline-flex items-center gap-1.5"><Truck size={14} className="text-primary" /> Same-day depot</span>
            </div>
          </div>

          <div className="lg:col-span-6 relative animate-rise-delay">
            <div className="absolute -inset-4 sm:-inset-6 rounded-[2rem] bg-gradient-to-br from-white/40 via-secondary-container/20 to-transparent blur-xl pointer-events-none" />
            <div className="relative grid grid-cols-12 grid-rows-6 gap-3 sm:gap-3.5 h-[340px] sm:h-[420px] lg:h-[460px]">
              {BENTO.map((tile) => (
                <div
                  key={tile.hint}
                  className={`${tile.className} group relative rounded-[1.35rem] sm:rounded-[1.6rem] overflow-hidden
                    bg-white/55 backdrop-blur-xl border border-white/70
                    shadow-[0_18px_50px_-24px_rgba(15,81,50,0.28),0_8px_20px_-12px_rgba(0,0,0,0.08)]
                    ring-1 ring-primary/[0.04] hover:ring-primary/15 transition-all duration-500`}
                >
                  <div className="absolute inset-0 bg-gradient-to-t from-primary/[0.07] via-transparent to-white/30 pointer-events-none" />
                  <div className="relative h-full p-3.5 sm:p-4 flex flex-col">
                    <div className="flex items-start justify-between gap-2 mb-1">
                      <div className="min-w-0">
                        <p className="font-display text-sm sm:text-base font-semibold text-primary truncate">{tile.name}</p>
                        <p className="text-[10px] sm:text-[11px] text-ink-muted uppercase tracking-wider truncate">{tile.meta}</p>
                      </div>
                      <span className="shrink-0 inline-flex items-center gap-1 rounded-full bg-white/85 backdrop-blur-md border border-white/90 px-2 py-1 text-[9px] sm:text-[10px] font-semibold text-secondary shadow-soft">
                        <BadgeCheck size={11} className="text-secondary" />
                        {tile.badge}
                      </span>
                    </div>
                    <div className="flex-1 flex items-center justify-center min-h-0">
                      <ProducePhoto
                        hint={tile.hint}
                        name={tile.name}
                        className="max-h-full max-w-full w-auto h-[72%] object-contain drop-shadow-[0_12px_24px_rgba(15,81,50,0.12)] group-hover:scale-[1.04] transition-transform duration-700 ease-out"
                        alt={tile.name}
                      />
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <div className="absolute -bottom-3 left-4 sm:left-6 inline-flex items-center gap-2 rounded-full bg-primary text-white px-3.5 py-1.5 text-[10px] font-semibold uppercase tracking-wider shadow-float">
              <span className="w-1.5 h-1.5 rounded-full bg-secondary-container animate-pulse" />
              Live telemetry · Al Aweer hub
            </div>
          </div>
        </div>
      </section>

      <section className="max-w-frame mx-auto px-4 lg:px-10 relative z-10 -mt-2 sm:mt-2 mb-12">
        <form
          className="bg-white/80 backdrop-blur-xl rounded-[1.75rem] shadow-[0_24px_60px_-28px_rgba(15,81,50,0.22)] border border-white/90 ring-1 ring-line/80 p-3 sm:p-4"
          onSubmit={applySearch}
        >
          <div className="flex flex-col lg:flex-row items-stretch lg:items-center gap-2.5 sm:gap-3">
            <div className="relative flex-1 w-full">
              <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-ink-faint" size={18} />
              <input
                className="w-full bg-surface-low/70 hover:bg-white focus:bg-white text-ink placeholder:text-ink-faint text-sm pl-11 pr-4 py-3.5 outline-none rounded-2xl border border-transparent focus:border-primary/20 focus:ring-2 focus:ring-secondary-container transition-all"
                placeholder="Search produce, harvest lot, origin..."
                value={q}
                onChange={(e) => setQ(e.target.value)}
              />
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-3 lg:flex lg:items-center gap-2 sm:gap-2.5">
              <select
                className="w-full lg:min-w-[148px] bg-surface-low/70 text-ink-muted text-xs font-semibold uppercase tracking-wider px-3.5 py-3.5 outline-none cursor-pointer rounded-2xl border border-transparent hover:border-line focus:border-primary/20 transition-colors"
                value={kind}
                onChange={(e) => {
                  setKind(e.target.value);
                  writeParams({ nextKind: e.target.value });
                }}
              >
                <option value="">All Varieties</option>
                <option value="FRESH_FRUIT">Fresh Produce</option>
                <option value="DRY_FRUIT">Sun-Dried Fruit</option>
                <option value="NUT">Prized Nuts</option>
                <option value="MIX">Mixes</option>
              </select>
              <select
                className="w-full lg:min-w-[148px] bg-surface-low/70 text-ink-muted text-xs font-semibold uppercase tracking-wider px-3.5 py-3.5 outline-none cursor-pointer rounded-2xl border border-transparent hover:border-line focus:border-primary/20 transition-colors"
                value={categoryId}
                onChange={(e) => {
                  setCategoryId(e.target.value);
                  writeParams({ nextCategory: e.target.value });
                }}
              >
                <option value="">All Categories</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
              <select
                className="w-full lg:min-w-[148px] bg-surface-low/70 text-ink-muted text-xs font-semibold uppercase tracking-wider px-3.5 py-3.5 outline-none cursor-pointer rounded-2xl border border-transparent hover:border-line focus:border-primary/20 transition-colors"
                value={sort}
                onChange={(e) => setSort(e.target.value)}
              >
                <option value="fefo">FEFO Dispatch</option>
                <option value="price-asc">Price: Low to High</option>
                <option value="price-desc">Price: High to Low</option>
                <option value="stock-desc">Abundant Stock</option>
              </select>
            </div>
            <button
              type="submit"
              className="w-full lg:w-auto bg-primary hover:bg-primary-container text-white px-7 py-3.5 rounded-2xl text-xs font-semibold uppercase tracking-[0.14em] transition-all shadow-soft hover:shadow-lift shrink-0"
            >
              Explore
            </button>
          </div>
        </form>

        <div className="flex items-center justify-center gap-2 mt-6 flex-wrap text-xs">
          <span className="text-ink-muted/70 text-[11px] uppercase tracking-[0.16em] mr-1">In Season</span>
          {QUICK_TAGS.map((tag) => {
            const active = activeTag === tag.label;
            return (
              <button
                key={tag.label}
                type="button"
                onClick={() => applyTag(tag)}
                className={`px-3.5 py-1.5 rounded-full border transition-all text-xs backdrop-blur-sm ${
                  active
                    ? "border-primary bg-primary text-white shadow-soft"
                    : "border-white/80 bg-white/70 text-ink-muted hover:border-ink-faint"
                }`}
              >
                {tag.label === "All SKUs" ? `All SKUs (${items.length || "—"})` : tag.label}
              </button>
            );
          })}
        </div>
      </section>

      <div id="floor" className="max-w-frame mx-auto px-4 lg:px-10 mb-8 flex flex-col sm:flex-row items-center justify-between gap-4 border-b border-line pb-6 scroll-mt-24">
        <div className="flex items-baseline gap-3">
          <h2 className="font-display text-2xl sm:text-3xl text-primary font-semibold tracking-tight">
            {pricingMode === "trade" ? "Wholesale sack lots" : "Active floor lots"}
          </h2>
          <span className="text-xs text-ink-muted font-light">{visible.length} varieties</span>
        </div>
        <div className="flex items-center gap-2 bg-white border border-line p-1 rounded-full text-xs shadow-soft">
          <button
            type="button"
            onClick={() => setMode("retail")}
            className={`px-4 py-1.5 rounded-full font-medium transition-all ${
              pricingMode === "retail" ? "bg-primary text-white shadow-soft" : "text-ink-muted hover:text-primary"
            }`}
          >
            Retail Crate
          </button>
          <button
            type="button"
            onClick={() => setMode("trade")}
            className={`px-4 py-1.5 rounded-full font-medium transition-all inline-flex items-center gap-1.5 ${
              pricingMode === "trade" ? "bg-primary text-white shadow-soft" : "text-ink-muted hover:text-primary"
            }`}
          >
            <Package size={14} />
            Wholesale Sack
          </button>
        </div>
      </div>

      {pricingMode === "trade" && (
        <div className="max-w-frame mx-auto px-4 lg:px-10 mb-8 animate-rise">
          <div className="p-4 rounded-2xl bg-gradient-to-r from-secondary-container to-primary-soft text-primary flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs border border-secondary/20 shadow-soft">
            <div className="flex items-center gap-3">
              <BadgeCheck size={18} className="text-secondary shrink-0" />
              <span>
                <strong>Commercial mode:</strong> Bulk trade rates for 50+ KG sacks. All floor lots below show wholesale pricing.
              </span>
            </div>
            <button type="button" className="underline font-semibold hover:opacity-80 shrink-0" onClick={() => setMode("retail")}>
              Switch to retail
            </button>
          </div>
        </div>
      )}

      <section className="max-w-frame mx-auto px-4 lg:px-10 pb-16">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 lg:gap-8">
          {visible.map((p, i) => (
            <div key={p.id} className="animate-rise" style={{ animationDelay: `${Math.min(i, 8) * 40}ms` }}>
              <ProductCard p={p} pricingMode={pricingMode} />
            </div>
          ))}
        </div>
        {visible.length === 0 && (
          <div className="text-center py-20 px-4 rounded-3xl border border-dashed border-line bg-white">
            <p className="font-display text-2xl text-primary mb-2">No lots on this filter</p>
            <p className="text-ink-muted text-sm mb-6">Try another variety, or open all SKUs on the floor.</p>
            <button
              type="button"
              className="btn-primary"
              onClick={() => {
                setKind("");
                setCategoryId("");
                setQ("");
                setInStockOnly(false);
                setActiveTag("All SKUs");
                writeParams({ nextKind: "", nextCategory: "", nextQ: "" });
                load(0, { kind: "", categoryId: "", q: "" });
              }}
            >
              Show all lots
            </button>
          </div>
        )}
      </section>

      <section id="protocol" className="relative border-t border-line overflow-hidden scroll-mt-24">
        <div className="absolute inset-0 bg-[linear-gradient(180deg,#ffffff_0%,#f3faf6_100%)]" />
        <div className="relative max-w-frame mx-auto px-4 lg:px-10 py-20">
          <div className="max-w-2xl mb-12">
            <span className="text-[11px] font-semibold uppercase tracking-[0.2em] text-secondary">The House Protocol</span>
            <h2 className="font-display text-3xl sm:text-4xl text-primary font-semibold mt-3 tracking-tight">An authenticated exchange</h2>
            <p className="text-ink-muted text-sm mt-3 font-light leading-relaxed">
              Every crate moves with origin papers, cold-chain discipline, and transparent dual tariffs.
            </p>
          </div>
          <div className="grid md:grid-cols-3 gap-8">
            {[
              {
                icon: ShieldCheck,
                title: "Orchard lot verification",
                body: "Sovereign origin paperwork, laboratory Brix grading, and phytosanitary clearance on every inbound lot.",
              },
              {
                icon: Scale,
                title: "FEFO dispatch priority",
                body: "Nearest-expiry algorithms clear cold storage first. Aged produce is never held for dispatch.",
              },
              {
                icon: Package,
                title: "Dual transparency tariffs",
                body: "Family kilo crates and indexed wholesale sack rates for hotels, kitchens, and traders.",
              },
            ].map(({ icon: Icon, title, body }) => (
              <div key={title} className="rounded-2xl bg-white border border-line p-6 shadow-soft hover:shadow-lift hover:-translate-y-0.5 transition-all duration-300">
                <Icon className="text-primary mb-4" size={28} />
                <h3 className="font-display text-xl text-primary font-semibold mb-2">{title}</h3>
                <p className="text-ink-muted text-sm font-light leading-relaxed">{body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
