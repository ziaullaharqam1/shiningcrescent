import { produceSrc } from "../lib/format";

export default function ProducePhoto({ hint, name, sku, imageUrl, className = "", alt }) {
  const custom = imageUrl || (hint && (String(hint).startsWith("/media/") || String(hint).startsWith("http")));
  const src = custom ? (imageUrl || hint) : produceSrc(hint, name, sku);
  return (
    <img
      src={src}
      alt={alt || name || "Produce"}
      className={`object-cover object-center brightness-110 contrast-105 saturate-125 ${className}`}
    />
  );
}
