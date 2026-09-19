import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

export default function SignOutButton({ className = "btn-ghost text-sm" }) {
  const { logout } = useAuth();
  const nav = useNavigate();
  return (
    <button
      type="button"
      className={className}
      onClick={() => {
        logout();
        nav("/", { replace: true });
      }}
    >
      Sign out
    </button>
  );
}
