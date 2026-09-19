import { createContext, useContext, useEffect, useMemo, useState } from "react";
import api from "../api/client";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  async function loadMe() {
    const params = new URLSearchParams(window.location.search);
    const oauthToken = params.get("token");
    if (oauthToken && (params.get("oauth") === "1" || window.location.pathname.startsWith("/login"))) {
      sessionStorage.setItem("rc_oauth_pending", "1");
      localStorage.setItem("rc_token", oauthToken);
      params.delete("token");
      params.delete("oauth");
      const qs = params.toString();
      window.history.replaceState({}, "", window.location.pathname + (qs ? `?${qs}` : "") + window.location.hash);
    }
    const token = localStorage.getItem("rc_token");
    if (!token) {
      setUser(null);
      setReady(true);
      return;
    }
    try {
      const { data } = await api.get("/auth/me");
      setUser(data);
    } catch {
      localStorage.removeItem("rc_token");
      setUser(null);
    } finally {
      setReady(true);
    }
  }

  useEffect(() => {
    loadMe();
  }, []);

  function accept(data) {
    localStorage.setItem("rc_token", data.token);
    setUser(data);
    return data;
  }

  async function login(username, password) {
    const { data } = await api.post("/auth/login", { username, password });
    return accept(data);
  }

  async function sendOtp(phone) {
    const { data } = await api.post("/auth/otp/send", { phone });
    return data;
  }

  async function verifyOtp(phone, code, fullName) {
    const { data } = await api.post("/auth/otp/verify", { phone, code, fullName });
    return accept(data);
  }

  async function register(payload) {
    const { data } = await api.post("/auth/register", payload);
    return accept(data);
  }

  function logout() {
    localStorage.removeItem("rc_token");
    setUser(null);
  }

  const can = (perm) => !!user?.permissions?.includes(perm);
  const isSuperAdmin = () => !!user?.roles?.includes("SUPER_ADMIN");

  const value = useMemo(
    () => ({ user, ready, login, sendOtp, verifyOtp, register, logout, can, isSuperAdmin, reload: loadMe }),
    [user, ready]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
