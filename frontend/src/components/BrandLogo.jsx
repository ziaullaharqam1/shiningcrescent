import { Link } from "react-router-dom";
import { useBranding } from "../context/BrandingContext";

const SIZES = {
  sm: { mark: "h-7 w-7", text: "text-base" },
  md: { mark: "h-8 w-8", text: "text-lg" },
  lg: { mark: "h-10 w-10", text: "text-2xl" },
};

const DEFAULT_MARK = "/brand-mark.svg";

export default function BrandLogo({ to = "/", size = "md", className = "" }) {
  const { branding } = useBranding();
  const s = SIZES[size] || SIZES.md;
  const name = branding?.brandName || "Shining Crescent";
  const markSrc = branding?.logoUrl || DEFAULT_MARK;
  const inner = (
    <>
      <img
        src={markSrc}
        alt=""
        className={`${s.mark} rounded-full object-contain shrink-0`}
      />
      <span className={`font-display ${s.text} text-primary leading-none tracking-tight font-semibold`}>
        {name}
      </span>
    </>
  );
  const cls = `inline-flex items-center gap-3 shrink-0 ${className}`.trim();
  if (to) {
    return (
      <Link to={to} className={cls}>
        {inner}
      </Link>
    );
  }
  return <span className={cls}>{inner}</span>;
}
