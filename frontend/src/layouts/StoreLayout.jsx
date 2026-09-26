import { Link, Outlet, useLocation } from "react-router-dom";
import { Bell, Menu, Search, ShoppingBag, X } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { useBranding } from "../context/BrandingContext";
import { useEffect, useState } from "react";
import api from "../api/client";
import BrandLogo from "../components/BrandLogo";
import SignOutButton from "../components/SignOutButton";

const NAV = [
  { to: "/", label: "Market Floor", end: true },
  { to: "/?pricing=trade#floor", label: "Wholesale & Sacks", match: "trade" },
  { to: "/?kind=FRESH_FRUIT#floor", label: "Fresh Fruit", match: "FRESH_FRUIT" },
  { to: "/?kind=DRY_FRUIT#floor", label: "Dry Fruit & Nuts", match: "DRY_FRUIT" },
  { to: "/#protocol", label: "FEFO Protocol", hash: "protocol" },
  { to: "/orders", label: "Orders", auth: true },
];

function navActive(location, item) {
  if (item.end) {
    return location.pathname === "/" && !location.search && location.hash !== "#protocol";
  }
  if (item.match === "trade") {
    return location.pathname === "/" && new URLSearchParams(location.search).get("pricing") === "trade";
  }
  if (item.match) {
    return location.pathname === "/" && new URLSearchParams(location.search).get("kind") === item.match;
  }
  if (item.hash === "protocol") {
    return location.hash === "#protocol";
  }
  if (item.to === "/orders") {
    return location.pathname.startsWith("/orders");
  }
  return location.pathname === item.to;
}

export default function StoreLayout() {
  const { user } = useAuth();
  const { branding } = useBranding();
  const location = useLocation();
  const [unread, setUnread] = useState(0);
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);
  const [bulletin, setBulletin] = useState("");

  function refreshCart() {
    if (!user) {
      setCount(0);
      return;
    }
    api.get("/cart").then((r) => setCount(r.data.itemCount || 0)).catch(() => {});
  }

  useEffect(() => {
    if (!user) {
      setUnread(0);
      setCount(0);
      return;
    }
    refreshCart();
    api.get("/console/notifications").then((r) => setUnread(r.data.unread || 0)).catch(() => {});
  }, [user]);

  useEffect(() => {
    const onCart = () => refreshCart();
    window.addEventListener("sc-cart-updated", onCart);
    return () => window.removeEventListener("sc-cart-updated", onCart);
  }, [user]);

  useEffect(() => {
    const id = location.hash?.replace("#", "");
    if (!id) return;
    const t = window.setTimeout(() => {
      document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
    }, 80);
    return () => window.clearTimeout(t);
  }, [location.pathname, location.search, location.hash]);

  const desk = user?.console || user?.roles?.includes("SUPPLIER");
  const links = [
    ...NAV.filter((l) => !l.auth || user),
    ...(desk ? [{ to: "/console", label: user?.roles?.includes("SUPPLIER") ? "Supplier Desk" : "Trading Desk" }] : []),
  ];

  return (
    <div className="min-h-screen flex flex-col bg-surface">
      <header className="sticky top-0 z-40 border-b border-line/80 bg-white/90 backdrop-blur-xl shadow-[0_1px_0_rgba(15,81,50,0.04)]">
        <div className="max-w-frame mx-auto px-4 lg:px-10 h-16 sm:h-[4.5rem] flex items-center justify-between gap-4">
          <BrandLogo to="/" size="md" className="min-w-0" />
          <nav className="hidden xl:flex items-center gap-6 text-[11px] font-semibold uppercase tracking-[0.14em] text-ink-muted">
            {links.map((l) => {
              const active = navActive(location, l);
              return (
                <Link
                  key={`${l.to}-${l.label}`}
                  to={l.to}
                  className={`relative pb-1 transition-colors hover:text-primary ${
                    active ? "text-primary" : ""
                  }`}
                >
                  {l.label}
                  <span
                    className={`absolute left-0 right-0 -bottom-0.5 h-0.5 rounded-full bg-primary transition-opacity ${
                      active ? "opacity-100" : "opacity-0"
                    }`}
                  />
                </Link>
              );
            })}
          </nav>
          <div className="flex items-center gap-2 sm:gap-3 shrink-0">
            <Link to="/#floor" className="p-2 text-ink-muted hover:text-primary transition-colors" aria-label="Search floor">
              <Search size={18} />
            </Link>
            {user && (
              <Link to="/notifications" className="relative p-2 text-ink-muted hover:text-primary transition-colors" aria-label="Alerts">
                <Bell size={18} />
                {unread > 0 && <span className="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-secondary animate-pulse" />}
              </Link>
            )}
            <Link
              to="/cart"
              className="inline-flex items-center gap-2 bg-primary hover:bg-primary-container text-white py-2 px-3.5 rounded-full text-xs font-semibold tracking-wide transition-all shadow-soft hover:shadow-lift"
            >
              <ShoppingBag size={15} />
              <span className="hidden sm:inline">Crate ({count})</span>
              <span className="sm:hidden">{count}</span>
            </Link>
            {user ? (
              <SignOutButton className="btn-ghost hidden lg:inline-flex text-xs min-h-9 py-1.5" />
            ) : (
              <Link className="btn-ghost hidden lg:inline-flex text-xs min-h-9 py-1.5" to="/login">Sign in</Link>
            )}
            <button type="button" className="xl:hidden p-2 text-ink-muted hover:text-primary" onClick={() => setOpen((v) => !v)} aria-label="Menu">
              {open ? <X size={20} /> : <Menu size={20} />}
            </button>
          </div>
        </div>

        {open && (
          <div className="xl:hidden border-t border-line px-4 py-3 flex flex-col gap-1 bg-white/95 backdrop-blur-md">
            {links.map((l) => (
              <Link
                key={`${l.to}-${l.label}`}
                to={l.to}
                className={`nav-chip ${navActive(location, l) ? "nav-chip-active" : ""}`}
                onClick={() => setOpen(false)}
              >
                {l.label}
              </Link>
            ))}
            {user ? (
              <div onClick={() => setOpen(false)}>
                <SignOutButton className="nav-chip text-left w-full" />
              </div>
            ) : (
              <Link to="/login" className="nav-chip" onClick={() => setOpen(false)}>Sign in</Link>
            )}
          </div>
        )}
      </header>

      <main className="flex-1">
        <Outlet />
      </main>

      <footer className="relative w-full border-t border-line overflow-hidden">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,rgba(232,245,238,0.9),#ffffff_55%)]" />
        <div className="relative max-w-frame mx-auto px-4 lg:px-10 pt-16 pb-12">
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-10 pb-12 border-b border-line">
            <div className="lg:col-span-2 space-y-4">
              <BrandLogo to="/" size="sm" />
              <p className="text-ink-muted text-xs font-light leading-relaxed max-w-sm">
                Fruit and dry fruit trading without the scramble — accredited growers, cold-chain transparency, and FEFO lot allocations for wholesale and culinary buyers.
              </p>
              <p className="text-[11px] text-ink-faint uppercase tracking-widest">Cold Chain · Lot Traced · Quality Assured</p>
            </div>
            <div>
              <h4 className="text-xs font-semibold uppercase tracking-widest text-primary mb-4">Market Floor</h4>
              <ul className="space-y-3 text-xs text-ink-muted font-light">
                <li><Link className="hover:text-primary transition-colors" to="/?kind=FRESH_FRUIT#floor">Fresh Fruit</Link></li>
                <li><Link className="hover:text-primary transition-colors" to="/?kind=DRY_FRUIT#floor">Dry Fruit</Link></li>
                <li><Link className="hover:text-primary transition-colors" to="/?kind=NUT#floor">Tree Nuts</Link></li>
                <li><Link className="hover:text-primary transition-colors" to="/?pricing=trade#floor">Wholesale Sacks</Link></li>
              </ul>
            </div>
            <div>
              <h4 className="text-xs font-semibold uppercase tracking-widest text-primary mb-4">Trade Services</h4>
              <ul className="space-y-3 text-xs text-ink-muted font-light">
                <li><Link className="hover:text-primary transition-colors" to={user ? "/console" : "/login"}>Wholesale Billing</Link></li>
                <li><Link className="hover:text-primary transition-colors" to="/?pricing=trade#floor">50 KG Sack Pricing</Link></li>
                <li><span className="text-ink-faint">Al Aweer Warehouse Pickup</span></li>
                <li><Link className="hover:text-primary transition-colors" to="/#protocol">Cold Chain Manifests</Link></li>
              </ul>
            </div>
            <div>
              <h4 className="text-xs font-semibold uppercase tracking-widest text-primary mb-4">Harvest Bulletin</h4>
              <p className="text-xs text-ink-muted font-light mb-4">
                Direct notifications when incoming sovereign crates arrive at central depots.
              </p>
              <form
                className="flex items-center gap-2 border border-line rounded-lg p-1 bg-white shadow-soft"
                onSubmit={(e) => {
                  e.preventDefault();
                  setBulletin("Joined — watch for lot alerts.");
                  setTimeout(() => setBulletin(""), 3000);
                }}
              >
                <input
                  className="bg-transparent text-xs text-ink placeholder:text-ink-faint px-2 py-1.5 outline-none w-full"
                  placeholder="trade@company.ae"
                  type="email"
                  required
                />
                <button type="submit" className="bg-primary text-white text-[10px] font-semibold uppercase px-3 py-1.5 rounded-md hover:bg-primary-container transition-colors shrink-0">
                  Join
                </button>
              </form>
              {bulletin && <p className="text-[11px] text-secondary mt-2">{bulletin}</p>}
            </div>
          </div>
          <div className="pt-8 flex flex-col sm:flex-row items-center justify-between gap-4 text-[11px] text-ink-faint font-light">
            <div>{branding?.hubLine || "Dubai Central Hub: Al Aweer Produce Exchange · Abu Dhabi Mina Zayed"}</div>
            <div>© {new Date().getFullYear()} Shining Crescent Trading LLC. All rights reserved.</div>
          </div>
        </div>
      </footer>
    </div>
  );
}
