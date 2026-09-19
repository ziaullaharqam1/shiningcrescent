export const ART = {
  apple: "🍎",
  mango: "🥭",
  orange: "🍊",
  dates: "🌴",
  apricot: "🍑",
  fig: "🍇",
  raisin: "🟡",
  almond: "🥜",
  cashew: "🌰",
  pistachio: "🟢",
  walnut: "🪵",
  mix: "🧺",
};

const PHOTO_KEYS = ["apple", "mango", "orange", "dates", "apricot", "fig", "raisin", "almond", "cashew", "pistachio", "walnut", "mix"];

export function art(hint) {
  return ART[hint] || "🌿";
}

export function produceSrc(hint, name = "", sku = "") {
  const key = String(hint || "").toLowerCase();
  if (PHOTO_KEYS.includes(key)) return `/produce/${key}.png`;
  const hay = `${name} ${sku} ${hint}`.toLowerCase();
  const aliases = [
    ["apple", "apl"],
    ["mango", "mng"],
    ["orange", "cit"],
    ["dates", "date", "dat"],
    ["apricot", "apr"],
    ["fig", "fig"],
    ["raisin", "rai"],
    ["almond", "alm"],
    ["cashew", "csh"],
    ["pistachio", "pst"],
    ["walnut", "wnt"],
    ["mix", "trl", "trail"],
  ];
  for (const group of aliases) {
    if (group.some((w) => hay.includes(w))) return `/produce/${group[0]}.png`;
  }
  return "/produce/apple.png";
}

export function money(v) {
  if (v == null) return "—";
  return new Intl.NumberFormat("en-AE", { style: "currency", currency: "AED" }).format(Number(v));
}

export { errMsg } from "./errors";
