import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import BrandLogo from "../components/BrandLogo";
import SignOutButton from "../components/SignOutButton";
import {
  LayoutDashboard, Apple, Boxes, Truck, ClipboardCheck, Warehouse,
  ShoppingCart, FileText, Users, Shield, ScrollText, GitBranch, Bell, Layers, Palette, Printer, Activity
} from "lucide-react";

const LINKS = [
  { to: "/console", label: "Desk", perm: "DASHBOARD:VIEW", icon: LayoutDashboard, end: true },
  { to: "/console/products", label: "Products", perm: "PRODUCTS:VIEW", icon: Apple },
  { to: "/console/categories", label: "Categories", perm: "CATEGORIES:VIEW", icon: Layers },
  { to: "/console/suppliers", label: "Suppliers", perm: "SUPPLIERS:VIEW", icon: Truck },
  { to: "/console/warehouses", label: "Warehouses", perm: "WAREHOUSES:VIEW", icon: Warehouse },
  { to: "/console/purchase-orders", label: "Purchase orders", perm: "PURCHASE_ORDERS:VIEW", icon: ClipboardCheck },
  { to: "/console/quality", label: "Quality", perm: "QUALITY:VIEW", icon: ClipboardCheck },
  { to: "/console/inventory", label: "Inventory", perm: "INVENTORY:VIEW", icon: Boxes },
  { to: "/console/sales-orders", label: "Sales orders", perm: "SALES_ORDERS:VIEW", icon: ShoppingCart },
  { to: "/console/invoices", label: "Invoices", perm: "INVOICES:VIEW", icon: FileText },
  { to: "/console/reports", label: "Reports", perm: "REPORTS:VIEW", icon: Printer },
  { to: "/console/users", label: "Users", perm: "USERS:VIEW", icon: Users },
  { to: "/console/portal", label: "Portal", perm: "PORTAL:VIEW", icon: Palette, superAdmin: true },
  { to: "/console/ops", label: "Ops", perm: "OPS:VIEW", icon: Activity, superAdmin: true },
  { to: "/console/roles", label: "Roles", perm: "ROLES:VIEW", icon: Shield },
  { to: "/console/audit", label: "Audit", perm: "AUDIT:VIEW", icon: ScrollText },
  { to: "/console/workflows", label: "Workflows", perm: "WORKFLOWS:VIEW", icon: GitBranch },
  { to: "/console/notifications", label: "Alerts", perm: "NOTIFICATIONS:VIEW", icon: Bell },
];

export default function ConsoleLayout() {
  const { user, can } = useAuth();
  const superAdmin = user?.roles?.includes("SUPER_ADMIN");
  return (
    <div className="min-h-screen bg-surface lg:grid lg:grid-cols-[248px_1fr]">
      <aside className="bg-white border-b lg:border-b-0 lg:border-r border-line p-4 lg:min-h-screen lg:sticky lg:top-0 lg:self-start">
        <BrandLogo to="/" size="sm" />
        <p className="text-xs text-ink-muted mt-2 mb-2 font-sans">
          <span className="font-semibold text-ink">{user?.fullName}</span>
          <span className="block text-ink-faint">{user?.roles?.join(" · ")}</span>
        </p>
        {user && (
          <div className="mb-3 sticky top-0 z-20 bg-white py-1">
            <SignOutButton className="btn-ghost btn-sm w-full" />
          </div>
        )}
        <nav className="flex lg:flex-col gap-1 overflow-x-auto pb-1 -mx-1 px-1">
          {LINKS.filter((l) => {
            if (l.superAdmin) return superAdmin;
            if (l.anyOf) return l.anyOf.some((p) => can(p));
            return can(l.perm);
          }).map((l) => {
            const Icon = l.icon;
            return (
              <NavLink
                key={l.to}
                to={l.to}
                end={l.end}
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-lg px-3 py-2.5 text-sm whitespace-nowrap min-h-11 font-medium transition-colors ${
                    isActive
                      ? "bg-secondary-container text-primary font-semibold"
                      : "text-ink-muted hover:bg-surface-low hover:text-primary"
                  }`
                }
              >
                <Icon size={16} className="shrink-0 opacity-80" /> {l.label}
              </NavLink>
            );
          })}
        </nav>
      </aside>
      <div className="p-4 md:p-8 overflow-x-hidden max-w-6xl w-full">
        <Outlet />
      </div>
    </div>
  );
}
