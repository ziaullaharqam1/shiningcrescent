import { createContext, useContext, useEffect, useMemo, useState } from "react";
import api from "../api/client";

export const DEFAULT_BRAND = {
  brandName: "Shining Crescent",
  logoUrl: "/brand-mark.svg",
  primaryColor: "#0f5132",
  hoverColor: "#0e3922",
  softColor: "#ecfdf5",
  inkColor: "#0e3922",
  hubLine: "Dubai Central Hub: Al Aweer Produce Exchange · Abu Dhabi Mina Zayed",
};

function hexToRgb(hex) {
  const h = hex.replace("#", "");
  return {
    r: parseInt(h.slice(0, 2), 16),
    g: parseInt(h.slice(2, 4), 16),
    b: parseInt(h.slice(4, 6), 16),
  };
}

function mix(a, b, t) {
  const x = hexToRgb(a);
  const y = hexToRgb(b);
  const c = (k) => Math.round(x[k] * (1 - t) + y[k] * t).toString(16).padStart(2, "0");
  return `#${c("r")}${c("g")}${c("b")}`;
}

export function applyBrandTheme(brand) {
  const b = { ...DEFAULT_BRAND, ...brand };
  const r = document.documentElement.style;
  r.setProperty("--rc-50", mix(b.softColor || DEFAULT_BRAND.softColor, "#ffffff", 0.4));
  r.setProperty("--rc-100", b.softColor || DEFAULT_BRAND.softColor);
  r.setProperty("--rc-500", b.primaryColor || DEFAULT_BRAND.primaryColor);
  r.setProperty("--rc-600", b.primaryColor || DEFAULT_BRAND.primaryColor);
  r.setProperty("--rc-700", b.hoverColor || DEFAULT_BRAND.hoverColor);
  r.setProperty("--rc-800", b.inkColor || DEFAULT_BRAND.inkColor);
  r.setProperty("--rc-900", b.inkColor || DEFAULT_BRAND.inkColor);
}

const BrandingContext = createContext(null);

export function BrandingProvider({ children }) {
  const [branding, setBranding] = useState(DEFAULT_BRAND);

  async function reload() {
    try {
      const { data } = await api.get("/auth/public-config");
      const next = {
        brandName: data.brandName || DEFAULT_BRAND.brandName,
        logoUrl: data.logoUrl || "",
        primaryColor: data.primaryColor || DEFAULT_BRAND.primaryColor,
        hoverColor: data.hoverColor || DEFAULT_BRAND.hoverColor,
        softColor: data.softColor || DEFAULT_BRAND.softColor,
        inkColor: data.inkColor || DEFAULT_BRAND.inkColor,
        hubLine: data.hubLine || DEFAULT_BRAND.hubLine,
      };
      setBranding(next);
      applyBrandTheme(next);
    } catch {
      applyBrandTheme(DEFAULT_BRAND);
    }
  }

  useEffect(() => {
    applyBrandTheme(DEFAULT_BRAND);
    reload();
  }, []);

  const value = useMemo(() => ({ branding, setBranding, reload }), [branding]);
  return <BrandingContext.Provider value={value}>{children}</BrandingContext.Provider>;
}

export function useBranding() {
  return useContext(BrandingContext) || { branding: DEFAULT_BRAND, reload: () => {}, setBranding: () => {} };
}
