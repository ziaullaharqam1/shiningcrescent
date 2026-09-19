import { useEffect, useState } from "react";
import api from "../../api/client";
import ProductCard from "../../components/ProductCard";
import ProducePhoto from "../../components/ProducePhoto";

const HERO = ["mango", "apple", "dates", "pistachio", "apricot", "cashew"];

export default function Home() {
  const [items, setItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [kind, setKind] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [q, setQ] = useState("");

  async function load(attempt = 0) {
    try {
      const params = {};
      if (kind) params.kind = kind;
      if (categoryId) params.categoryId = categoryId;
      if (q) params.q = q;
      const [{ data: products }, { data: cats }] = await Promise.all([
        api.get("/catalog/products", { params }),
        api.get("/catalog/categories"),
      ]);
      setItems(products);
      setCategories(cats);
    } catch {
      if (attempt < 4) {
        setTimeout(() => load(attempt + 1), 400 * (attempt + 1));
      }
    }
  }

  useEffect(() => {
    load();
  }, [kind, categoryId]);

  return (
    <div className="bg-white">
      <section className="bg-white">
        <div className="max-w-6xl mx-auto px-4 py-8 md:py-14 grid lg:grid-cols-2 gap-8 items-center">
          <div>
            <p className="uppercase tracking-[0.2em] text-grove-600 text-xs font-semibold">House of harvest</p>
            <h1 className="font-display text-4xl md:text-6xl mt-3 leading-tight text-grove-800">
              Fresh fruit, dry fruit, and nuts — shop in a few taps.
            </h1>
            <p className="mt-4 text-gray-600 max-w-lg text-base md:text-lg">
              Retail crates or wholesale sacks. Every kilo traces to a graded lot. Warehouse picks nearest expiry first.
            </p>
            <div className="mt-6 grid grid-cols-6 gap-2">
              {HERO.map((hint) => (
                <div key={hint} className="aspect-square rounded-xl overflow-hidden border border-gray-100 bg-white">
                  <ProducePhoto hint={hint} className="h-full w-full" alt={hint} />
                </div>
              ))}
            </div>
          </div>
          <form
            className="rounded-2xl border border-gray-100 bg-white p-4 md:p-5 shadow-sm grid gap-3"
            onSubmit={(e) => {
              e.preventDefault();
              load();
            }}
          >
            <label className="text-sm font-semibold text-grove-700">Find produce</label>
            <input className="input" placeholder="Search Alphonso, Mamra, Malatya…" value={q} onChange={(e) => setQ(e.target.value)} />
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              <select className="input" value={kind} onChange={(e) => setKind(e.target.value)}>
                <option value="">All kinds</option>
                <option value="FRESH_FRUIT">Fresh fruit</option>
                <option value="DRY_FRUIT">Dry fruit</option>
                <option value="NUT">Nuts</option>
                <option value="MIX">Mixes</option>
              </select>
              <select className="input" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
                <option value="">All categories</option>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>
            <button className="btn-primary">Find harvest</button>
          </form>
        </div>
      </section>
      <section className="max-w-6xl mx-auto px-4 pb-12">
        <div className="flex flex-wrap items-end justify-between gap-2 mb-6">
          <h2 className="font-display text-3xl text-grove-800">On the floor</h2>
          <p className="text-sm text-gray-500">{items.length} listed SKUs</p>
        </div>
        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-5">
          {items.map((p) => (
            <ProductCard key={p.id} p={p} />
          ))}
        </div>
      </section>
    </div>
  );
}
