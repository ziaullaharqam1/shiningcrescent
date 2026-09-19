import { Link, NavLink, Outlet } from "react-router-dom";
import { Bell, ShoppingBag, Menu, X } from "lucide-react";
import { useAuth } from "../context/AuthContext";
import { useEffect, useState } from "react";
import api from "../api/client";
import BrandLogo from "../components/BrandLogo";
import SignOutButton from "../components/SignOutButton";

export default function StoreLayout() {
  const { user } = useAuth();
  const [unread, setUnread] = useState(0);
  const [count, setCount] = useState(0);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!user) {
      setUnread(0);
      setCount(0);
      return;
    }
    api.get("/cart").then((r) => setCount(r.data.itemCount || 0)).catch(() => {});
    api.get("/console/notifications").then((r) => setUnread(r.data.unread || 0)).catch(() => {});
  }, [user]);

  const links = [
    { to: "/", label: "Market", end: true },
    ...(user ? [{ to: "/orders", label: "My orders" }] : []),
    ...(user?.console || user?.roles?.includes("SUPPLIER") ? [{ to: "/console", label: user?.roles?.includes("SUPPLIER") ? "Supplier desk" : "Trading desk" }] : []),
  ];

  return (
    <div className="min-h-screen flex flex-col bg-white">
      <header className="sticky top-0 z-30 bg-white border-b border-gray-100">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center gap-3">
          <BrandLogo to="/" size="md" />
          <nav className="hidden md:flex gap-1 text-sm ml-4">
            {links.map((l) => (
              <NavLink
                key={l.to}
                to={l.to}
                end={l.end}
                className={({ isActive }) => `nav-chip ${isActive ? "nav-chip-active" : ""}`}
              >
                {l.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-1 sm:gap-2">
            {user && (
              <Link to="/notifications" className="relative p-2.5 rounded-full hover:bg-grove-100" aria-label="Notifications">
                <Bell size={18} className="text-grove-800" />
                {unread > 0 && <span className="absolute top-1.5 right-1.5 h-2.5 w-2.5 rounded-full bg-grove-600" />}
              </Link>
            )}
            <Link to="/cart" className="relative p-2.5 rounded-full hover:bg-grove-100" aria-label="Cart">
              <ShoppingBag size={18} className="text-grove-800" />
              {count > 0 && (
                <span className="absolute -top-0.5 -right-0.5 text-[10px] bg-grove-600 text-white rounded-full min-w-5 h-5 grid place-items-center px-1">
                  {count}
                </span>
              )}
            </Link>
            {user ? (
              <SignOutButton className="btn-ghost" />
            ) : (
              <Link className="btn-primary" to="/login">Sign in</Link>
            )}
            <button className="md:hidden p-2.5 rounded-full hover:bg-grove-100" onClick={() => setOpen((v) => !v)} aria-label="Menu">
              {open ? <X size={20} /> : <Menu size={20} />}
            </button>
          </div>
        </div>
        {open && (
          <div className="md:hidden border-t border-grove-100 px-4 py-3 flex flex-col gap-1 bg-white">
            {links.map((l) => (
              <NavLink key={l.to} to={l.to} end={l.end} className="nav-chip" onClick={() => setOpen(false)}>
                {l.label}
              </NavLink>
            ))}
            {user && (
              <div onClick={() => setOpen(false)}>
                <SignOutButton className="nav-chip text-left w-full" />
              </div>
            )}
          </div>
        )}
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
      <footer className="border-t border-grove-100 py-8 text-center text-sm text-grove-600 px-4">
        Orchard to warehouse to your crate · FEFO lots · graded dry fruit
      </footer>
    </div>
  );
}
