import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ApiRequestError } from "@/api/client";
import { useAuthStore } from "@/store/auth";

export function RegisterPage() {
  const register = useAuthStore((s) => s.register);
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"customer" | "vendor">("customer");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const validate = (): boolean => {
    const next: Record<string, string> = {};
    if (!/^[^@]+@[^@]+\.[^@]+$/.test(email)) next.email = "Enter a valid email.";
    if (password.length < 8) next.password = "Password must be at least 8 characters.";
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setSubmitting(true);
    try {
      await register(email, password, role);
      navigate("/", { replace: true });
    } catch (err) {
      if (err instanceof ApiRequestError) {
        const details = err.details as Record<string, string[]>;
        const fieldErrors: Record<string, string> = {};
        for (const [k, v] of Object.entries(details)) {
          fieldErrors[k] = Array.isArray(v) ? v[0] : String(v);
        }
        setErrors(
          Object.keys(fieldErrors).length ? fieldErrors : { email: err.message },
        );
      } else {
        setErrors({ email: "Registration failed." });
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto mt-10 max-w-sm rounded-lg bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-bold text-gray-900">Create account</h1>
      <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
        <label className="block">
          <span className="text-sm font-medium text-gray-700">Email</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2"
            required
          />
          {errors.email && <p className="mt-1 text-sm text-red-600">{errors.email}</p>}
        </label>

        <label className="block">
          <span className="text-sm font-medium text-gray-700">Password</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2"
            required
          />
          {errors.password && (
            <p className="mt-1 text-sm text-red-600">{errors.password}</p>
          )}
        </label>

        <label className="block">
          <span className="text-sm font-medium text-gray-700">Account type</span>
          <select
            value={role}
            onChange={(e) => setRole(e.target.value as "customer" | "vendor")}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2"
          >
            <option value="customer">Customer</option>
            <option value="vendor">Vendor</option>
          </select>
        </label>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-brand py-2.5 text-white hover:bg-brand-dark disabled:opacity-50"
        >
          {submitting ? "Creating…" : "Sign up"}
        </button>
      </form>
      <p className="mt-4 text-center text-sm text-gray-500">
        Already have an account?{" "}
        <Link to="/login" className="text-brand hover:underline">
          Log in
        </Link>
      </p>
    </div>
  );
}
