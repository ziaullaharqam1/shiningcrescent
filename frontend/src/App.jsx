import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./context/AuthContext";
import StoreLayout from "./layouts/StoreLayout";
import ConsoleLayout from "./layouts/ConsoleLayout";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Home from "./pages/store/Home";
import ProductDetail from "./pages/store/ProductDetail";
import Cart from "./pages/store/Cart";
import Checkout from "./pages/store/Checkout";
import { MyOrders, OrderDetail } from "./pages/store/Orders";
import Notifications from "./pages/Notifications";
import PrintPreviewDialog from "./components/PrintPreviewDialog";
import AgentChat from "./components/AgentChat";
import PortalAdmin from "./pages/console/PortalAdmin";
import Ops from "./pages/console/Ops";
import {
  AuditPage, Dashboard, Inventory, Invoices, Products, PurchaseOrders,
  Quality, Reports, RolesPage, SalesOrders, SimpleMaster, UsersPage, WorkflowsPage,
} from "./pages/console/Pages";

function Guard({ children, perm, consoleOnly }) {
  const { user, ready, can } = useAuth();
  if (!ready) return <div className="p-10">Loading…</div>;
  if (!user) return <Navigate to="/login" replace />;
  if (consoleOnly && !user.console && !can("PURCHASE_ORDERS:VIEW") && !can("SALES_ORDERS:VIEW")) return <Navigate to="/" replace />;
  if (perm && !can(perm) && !can("DASHBOARD:VIEW")) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <>
      <PrintPreviewDialog />
      <AgentChat />
      <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<StoreLayout />}>
        <Route path="/" element={<Home />} />
        <Route path="/p/:id" element={<ProductDetail />} />
        <Route path="/cart" element={<Cart />} />
        <Route path="/checkout" element={<Guard><Checkout /></Guard>} />
        <Route path="/orders" element={<Guard><MyOrders /></Guard>} />
        <Route path="/orders/:id" element={<Guard><OrderDetail /></Guard>} />
        <Route path="/notifications" element={<Guard><Notifications /></Guard>} />
      </Route>
      <Route
        path="/console"
        element={
          <Guard consoleOnly>
            <ConsoleLayout />
          </Guard>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="products" element={<Products />} />
        <Route path="categories" element={<SimpleMaster title="Categories" path="/console/categories" fields={["name", "description"]} permCreate="CATEGORIES:CREATE" permUpdate="CATEGORIES:UPDATE" />} />
        <Route path="suppliers" element={<SimpleMaster title="Suppliers" path="/console/suppliers" fields={["name", "contactName", "email", "phone", "originCountry", "specialties", "portalUsername"]} permCreate="SUPPLIERS:CREATE" permUpdate="SUPPLIERS:UPDATE" />} />
        <Route path="warehouses" element={<SimpleMaster title="Warehouses" path="/console/warehouses" fields={["code", "name", "city", "zoneType"]} permCreate="WAREHOUSES:CREATE" permUpdate="WAREHOUSES:UPDATE" />} />
        <Route path="purchase-orders" element={<PurchaseOrders />} />
        <Route path="quality" element={<Quality />} />
        <Route path="inventory" element={<Inventory />} />
        <Route path="sales-orders" element={<SalesOrders />} />
        <Route path="invoices" element={<Invoices />} />
        <Route path="reports" element={<Reports />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="portal" element={<PortalAdmin />} />
        <Route path="ops" element={<Ops />} />
        <Route path="roles" element={<RolesPage />} />
        <Route path="audit" element={<AuditPage />} />
        <Route path="workflows" element={<WorkflowsPage />} />
        <Route path="notifications" element={<Notifications consoleView />} />
      </Route>
    </Routes>
    </>
  );
}
