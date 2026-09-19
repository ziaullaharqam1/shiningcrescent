import { Link } from "react-router-dom";
import { money } from "../lib/format";
import ProducePhoto from "./ProducePhoto";

export default function ProductCard({ p }) {
  return (
    <Link to={`/p/${p.id}`} className="bg-white overflow-hidden rounded-2xl border border-gray-100 shadow-sm hover:-translate-y-1 hover:shadow-xl transition group">
      <div className="h-52 sm:h-56 bg-white overflow-hidden">
        <ProducePhoto hint={p.imageHint} imageUrl={p.imageUrl} name={p.name} sku={p.sku} className="h-full w-full group-hover:scale-105 transition duration-300" />
      </div>
      <div className="p-4 bg-white">
        <p className="text-xs uppercase tracking-wide text-grove-600">{p.kind?.replaceAll("_", " ")}</p>
        <h3 className="font-display text-lg mt-1 group-hover:text-grove-700">{p.name}</h3>
        <p className="text-sm text-gray-500 mt-1">
          {p.origin} · Grade {p.grade}
        </p>
        <div className="mt-3 flex items-end justify-between gap-2">
          <div>
            <p className="font-semibold text-grove-800">{money(p.retailPrice)} <span className="text-xs font-normal">/ {p.uom}</span></p>
            <p className="text-xs text-gray-500">Trade {money(p.wholesalePrice)}</p>
          </div>
          <span className="text-xs bg-grove-100 text-grove-800 rounded-full px-2 py-1 whitespace-nowrap">
            {Number(p.availableQty)} {p.uom}
          </span>
        </div>
      </div>
    </Link>
  );
}
