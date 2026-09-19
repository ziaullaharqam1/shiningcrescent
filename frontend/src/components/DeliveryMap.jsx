import { useEffect, useRef } from "react";

const STEEL = "#4682B4";
const STEEL_DARK = "#2E5A7A";
const GREEN = "#15803d";
const GREEN_DARK = "#14532d";

function haversineKm(aLat, aLng, bLat, bLng) {
  const r = 6371;
  const dLat = ((bLat - aLat) * Math.PI) / 180;
  const dLng = ((bLng - aLng) * Math.PI) / 180;
  const x =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((aLat * Math.PI) / 180) * Math.cos((bLat * Math.PI) / 180) * Math.sin(dLng / 2) ** 2;
  return r * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
}

export function isArrived(delivery) {
  if (!delivery) return false;
  if (delivery.arrived === true) return true;
  const delivered = delivery.deliveryStatus === "DELIVERED" || delivery.status === "DELIVERED";
  return delivered && Number(delivery.etaMinutes || 0) === 0;
}

export function formatDistance(km, arrived) {
  if (arrived) return "Arrived";
  if (km == null || Number.isNaN(km)) return "—";
  if (km < 0.05) return "Arriving";
  if (km < 1) return `${Math.round(km * 1000)} m`;
  return `${km.toFixed(1)} km`;
}

function deliveryPinIcon(L) {
  const html = `
    <div class="rc-drop-pin">
      <svg width="36" height="48" viewBox="0 0 36 48" fill="none" aria-hidden="true">
        <path d="M18 46s14-16.2 14-28A14 14 0 1 0 4 18c0 11.8 14 28 14 28z" fill="${GREEN}"/>
        <circle cx="18" cy="18" r="7.5" fill="#fff"/>
        <path d="M18 13.2l4.2 8.4H13.8L18 13.2z" fill="${GREEN_DARK}"/>
      </svg>
      <span>Delivery</span>
    </div>`;
  return L.divIcon({ className: "rc-marker", html, iconSize: [72, 56], iconAnchor: [18, 48], popupAnchor: [0, -46] });
}

function vehicleIcon(L, kind) {
  const car = kind === "CAR";
  const html = car
    ? `<div class="rc-vehicle rc-vehicle-car" title="Car">
        <svg width="44" height="28" viewBox="0 0 44 28" aria-hidden="true">
          <rect x="1" y="8" width="42" height="12" rx="4" fill="${STEEL}"/>
          <path d="M10 8l4-6h16l4 6" fill="${STEEL_DARK}"/>
          <circle cx="12" cy="21" r="4.2" fill="#1E3A4C"/><circle cx="32" cy="21" r="4.2" fill="#1E3A4C"/>
          <circle cx="12" cy="21" r="1.6" fill="#EEF5FA"/><circle cx="32" cy="21" r="1.6" fill="#EEF5FA"/>
          <rect x="14" y="10" width="7" height="5" rx="1" fill="#fff"/>
          <rect x="23" y="10" width="7" height="5" rx="1" fill="#fff"/>
        </svg>
      </div>`
    : `<div class="rc-vehicle rc-vehicle-scooter" title="Scooter">
        <svg width="40" height="32" viewBox="0 0 40 32" aria-hidden="true">
          <circle cx="10" cy="24" r="5" fill="#1E3A4C"/><circle cx="30" cy="24" r="5" fill="#1E3A4C"/>
          <circle cx="10" cy="24" r="2" fill="#EEF5FA"/><circle cx="30" cy="24" r="2" fill="#EEF5FA"/>
          <path d="M12 23h14l2-8h-6l-2 4H16" stroke="${STEEL}" stroke-width="3" fill="none" stroke-linecap="round"/>
          <path d="M22 11h6v5h-8z" fill="${STEEL_DARK}"/>
          <circle cx="28" cy="9" r="2.2" fill="${GREEN}"/>
        </svg>
      </div>`;
  return L.divIcon({ className: "rc-marker", html, iconSize: [44, 32], iconAnchor: [22, 28], popupAnchor: [0, -26] });
}

function hubIcon(L) {
  const html = `<div class="rc-hub-pin">Warehouse</div>`;
  return L.divIcon({ className: "rc-marker", html, iconSize: [88, 24], iconAnchor: [44, 12] });
}

export default function DeliveryMap({ delivery }) {
  const el = useRef(null);
  const mapRef = useRef(null);
  const layers = useRef({});
  const fitted = useRef(false);

  useEffect(() => {
    if (!el.current || mapRef.current || !window.L) return;
    const map = window.L.map(el.current, { zoomControl: true, attributionControl: true });
    window.L.tileLayer("https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> &copy; <a href="https://carto.com/attributions">CARTO</a>',
      subdomains: "abcd",
      maxZoom: 20,
    }).addTo(map);
    mapRef.current = map;
    fitted.current = false;
    return () => {
      map.remove();
      mapRef.current = null;
      layers.current = {};
    };
  }, []);

  useEffect(() => {
    const L = window.L;
    const map = mapRef.current;
    if (!L || !map || delivery?.riderLat == null || delivery?.dropLat == null) return;
    const rider = [delivery.riderLat, delivery.riderLng];
    const drop = [delivery.dropLat, delivery.dropLng];
    const hub = [delivery.pickupLat, delivery.pickupLng];
    const kind = delivery.vehicleKind || delivery.rider?.vehicleKind || "SCOOTER";
    const km = haversineKm(delivery.riderLat, delivery.riderLng, delivery.dropLat, delivery.dropLng);
    const arrived = isArrived(delivery);
    const distText = delivery.distanceLabel || formatDistance(km, arrived);
    const name = delivery.rider?.name || "Rider";
    const vehicleLabel = kind === "CAR" ? "Car" : "Scooter";
    const eta = arrived ? "0 min" : delivery.etaMinutes == null ? "—" : `${delivery.etaMinutes} min`;
    const lineColor = arrived ? GREEN : STEEL;

    if (!layers.current.drop) {
      layers.current.hub = L.marker(hub, { icon: hubIcon(L), zIndexOffset: 200 }).addTo(map);
      layers.current.drop = L.marker(drop, { icon: deliveryPinIcon(L), zIndexOffset: 600 }).addTo(map);
      layers.current.rider = L.marker(rider, { icon: vehicleIcon(L, kind), zIndexOffset: 700 }).addTo(map);
      layers.current.line = L.polyline([rider, drop], { color: lineColor, weight: 4, dashArray: arrived ? null : "8 10", opacity: 0.95 }).addTo(map);
      layers.current.mid = L.tooltip({ permanent: true, direction: "center", className: arrived ? "rc-dist-tip arrived" : "rc-dist-tip" })
        .setLatLng([(rider[0] + drop[0]) / 2, (rider[1] + drop[1]) / 2])
        .setContent(distText)
        .addTo(map);
    } else {
      layers.current.rider.setLatLng(rider);
      layers.current.rider.setIcon(vehicleIcon(L, kind));
      layers.current.drop.setLatLng(drop);
      layers.current.hub.setLatLng(hub);
      layers.current.line.setLatLngs([rider, drop]);
      layers.current.line.setStyle({ color: lineColor, dashArray: arrived ? null : "8 10" });
      layers.current.mid.setLatLng([(rider[0] + drop[0]) / 2, (rider[1] + drop[1]) / 2]);
      layers.current.mid.setContent(distText);
      const tipEl = layers.current.mid.getElement?.();
      if (tipEl) tipEl.classList.toggle("arrived", arrived);
    }
    layers.current.drop.bindPopup(`<b>Delivery pin</b><br/>${delivery.shipToAddress || "Drop-off"}<br/>${distText}`);
    layers.current.rider.bindPopup(`<b>${name}</b><br/>${vehicleLabel}<br/>${distText}${arrived ? "" : ` · ETA ${eta}`}`);
    if (!fitted.current) {
      map.fitBounds(L.latLngBounds([rider, drop, hub]).pad(0.28));
      fitted.current = true;
    }
  }, [delivery]);

  const kind = delivery?.vehicleKind || delivery?.rider?.vehicleKind || "SCOOTER";
  const arrived = isArrived(delivery);
  const km =
    delivery?.riderLat != null && delivery?.dropLat != null
      ? haversineKm(delivery.riderLat, delivery.riderLng, delivery.dropLat, delivery.dropLng)
      : delivery?.distanceKm;
  const distText = delivery?.distanceLabel || formatDistance(km, arrived);

  return (
    <div className="relative" lang="en">
      <div ref={el} className="h-[380px] w-full rounded-2xl overflow-hidden border border-[#D6E6F0] z-0" />
      <div className="absolute top-3 left-3 right-3 flex flex-wrap gap-2 pointer-events-none">
        <span className={`pointer-events-auto rounded-full shadow-soft px-3 py-1.5 text-xs font-semibold ${arrived ? "rc-map-chip-arrived" : "rc-map-chip"}`}>
          {kind === "CAR" ? "Car" : "Scooter"} · {distText}
          {arrived ? "" : " to pin"}
        </span>
      </div>
    </div>
  );
}
