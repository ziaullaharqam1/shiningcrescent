import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { errMsg } from "../lib/errors";
import ErrorBanner from "../components/ErrorBanner";
import BrandLogo from "../components/BrandLogo";

export default function Register() {
  const { register } = useAuth();
  const nav = useNavigate();
  const [form, setForm] = useState({ username: "", password: "", fullName: "", email: "", phone: "" });
  const [error, setError] = useState("");

  async function submit(e) {
    e.preventDefault();
    try {
      await register(form);
      nav("/");
    } catch (err) {
      setError(errMsg(err, "register"));
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-white">
      <form onSubmit={submit} className="card p-8 w-full max-w-md space-y-3">
        <BrandLogo to="/" size="md" />
        <h1 className="font-display text-3xl">Open a buyer account</h1>
        <ErrorBanner>{error}</ErrorBanner>
        {["fullName", "email", "phone", "username", "password"].map((k) => (
          <input
            key={k}
            className="input"
            type={k === "password" ? "password" : k === "phone" ? "tel" : "text"}
            placeholder={k === "phone" ? "Mobile (+9715…)" : k}
            value={form[k]}
            onChange={(e) => setForm({ ...form, [k]: e.target.value })}
          />
        ))}
        <button className="btn-primary w-full">Join</button>
        <Link to="/login" className="text-sm underline">Back to sign in</Link>
      </form>
    </div>
  );
}
