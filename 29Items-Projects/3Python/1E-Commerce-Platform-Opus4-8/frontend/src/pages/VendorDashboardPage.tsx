import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { catalogApi, vendorsApi } from "@/api/endpoints";
import { ApiRequestError } from "@/api/client";
import { ErrorBanner, Spinner } from "@/components/common/Spinner";

export function VendorDashboardPage() {
  const qc = useQueryClient();
  const vendor = useQuery({
    queryKey: ["vendor-me"],
    queryFn: vendorsApi.me,
    retry: false,
  });

  if (vendor.isLoading) return <Spinner />;

  // No vendor profile yet — offer onboarding.
  if (vendor.isError) {
    return <VendorOnboarding onCreated={() => qc.invalidateQueries({ queryKey: ["vendor-me"] })} />;
  }

  return (
    <div className="space-y-6">
      <header className="rounded-lg bg-white p-5 shadow-sm">
        <h1 className="text-2xl font-bold text-gray-900">{vendor.data?.name}</h1>
        <p className="mt-1 text-sm text-gray-500">
          Status:{" "}
          <span className="font-medium">{vendor.data?.status}</span>
          {vendor.data?.status !== "approved" &&
            " — products go live once your account is approved."}
        </p>
      </header>
      <NewProductForm />
    </div>
  );
}

function VendorOnboarding({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const create = useMutation({
    mutationFn: () => vendorsApi.create(name),
    onSuccess: onCreated,
    onError: (err) =>
      setError(err instanceof ApiRequestError ? err.message : "Failed."),
  });

  return (
    <div className="mx-auto max-w-md rounded-lg bg-white p-6 shadow-sm">
      <h1 className="text-xl font-bold text-gray-900">Become a vendor</h1>
      <p className="mt-1 text-sm text-gray-500">
        Create a vendor profile to start listing products.
      </p>
      <form
        className="mt-4 space-y-3"
        onSubmit={(e) => {
          e.preventDefault();
          create.mutate();
        }}
      >
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Store name"
          required
          className="w-full rounded-md border border-gray-300 px-3 py-2"
        />
        {error && <ErrorBanner message={error} />}
        <button
          type="submit"
          disabled={create.isPending || !name}
          className="w-full rounded-md bg-brand py-2.5 text-white hover:bg-brand-dark disabled:opacity-50"
        >
          {create.isPending ? "Creating…" : "Create vendor profile"}
        </button>
      </form>
    </div>
  );
}

function NewProductForm() {
  const qc = useQueryClient();
  const [form, setForm] = useState({ sku: "", name: "", price: "", category_id: "" });
  const [message, setMessage] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const create = useMutation({
    mutationFn: () =>
      catalogApi.create({
        sku: form.sku,
        name: form.name,
        price: form.price,
        category_id: Number(form.category_id),
        status: "active",
      }),
    onSuccess: () => {
      setMessage("Product created ✓");
      setErrors({});
      setForm({ sku: "", name: "", price: "", category_id: "" });
      qc.invalidateQueries({ queryKey: ["products"] });
    },
    onError: (err) => {
      if (err instanceof ApiRequestError) {
        const details = err.details as Record<string, string[]>;
        const fieldErrors: Record<string, string> = {};
        for (const [k, v] of Object.entries(details)) {
          fieldErrors[k] = Array.isArray(v) ? v[0] : String(v);
        }
        setErrors(Object.keys(fieldErrors).length ? fieldErrors : { sku: err.message });
      }
    },
  });

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm({ ...form, [k]: e.target.value });

  return (
    <section className="rounded-lg bg-white p-5 shadow-sm">
      <h2 className="text-lg font-semibold text-gray-900">List a new product</h2>
      <form
        className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-2"
        onSubmit={(e) => {
          e.preventDefault();
          create.mutate();
        }}
      >
        <Input label="SKU" value={form.sku} onChange={set("sku")} error={errors.sku} />
        <Input label="Name" value={form.name} onChange={set("name")} error={errors.name} />
        <Input
          label="Price"
          type="number"
          value={form.price}
          onChange={set("price")}
          error={errors.price}
        />
        <Input
          label="Category ID"
          type="number"
          value={form.category_id}
          onChange={set("category_id")}
          error={errors.category_id}
        />
        <div className="md:col-span-2">
          <button
            type="submit"
            disabled={create.isPending}
            className="rounded-md bg-brand px-5 py-2 text-white hover:bg-brand-dark disabled:opacity-50"
          >
            {create.isPending ? "Saving…" : "Create product"}
          </button>
          {message && <span className="ml-3 text-sm text-green-600">{message}</span>}
        </div>
      </form>
    </section>
  );
}

function Input({
  label,
  value,
  onChange,
  type = "text",
  error,
}: {
  label: string;
  value: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  type?: string;
  error?: string;
}) {
  return (
    <label className="block">
      <span className="text-sm font-medium text-gray-700">{label}</span>
      <input
        type={type}
        value={value}
        onChange={onChange}
        required
        className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2"
      />
      {error && <p className="mt-1 text-sm text-red-600">{error}</p>}
    </label>
  );
}
