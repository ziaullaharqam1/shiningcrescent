import { Link } from "react-router-dom";
import { Leaf } from "lucide-react";
import { useBranding } from "../context/BrandingContext";

const SIZES = {
  sm: { wrap: "h-8 w-8", icon: 16, text: "text-lg" },
  md: { wrap: "h-9 w-9", icon: 18, text: "text-xl" },
  lg: { wrap: "h-11 w-11", icon: 22, text: "text-3xl" },
};

export default function BrandLogo({ to = "/", size = "md", className = "" }) {
  const { branding } = useBranding();
  const s = SIZES[size] || SIZES.md;
  const name = branding?.brandName || "Rising Crescent";
  const mark = branding?.logoUrl ? (
    <img src={branding.logoUrl} alt="" className={`${s.wrap} rounded-full object-cover border border-grove-100 shrink-0`} />
  ) : (
    <span className={`${s.wrap} rounded-full bg-grove-100 grid place-items-center shrink-0`}>
      <Leaf className="text-grove-700" size={s.icon} />
    </span>
  );
  const inner = (
    <>
      {mark}
      <span className={`font-display ${s.text} text-grove-800 leading-none`}>{name}</span>
    </>
  );
  const cls = `inline-flex items-center gap-2 shrink-0 ${className}`.trim();
  if (to) {
    return (
      <Link to={to} className={cls}>
        {inner}
      </Link>
    );
  }
  return <span className={cls}>{inner}</span>;
}
