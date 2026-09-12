import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ProductCard } from "./ProductCard";
import type { Product } from "@/types";

const product: Product = {
  id: 1,
  sku: "SKU-1",
  name: "Test Widget",
  slug: "test-widget",
  description: "desc",
  price: "19.99",
  currency: "USD",
  status: "active",
  category: { id: 1, name: "Gadgets", slug: "gadgets", parent: null },
  vendor: { id: 1, name: "Acme", slug: "acme" },
  is_purchasable: true,
  quantity_available: 3,
  created_at: "2026-01-01T00:00:00Z",
};

describe("ProductCard", () => {
  const renderCard = (p: Product) =>
    render(
      <MemoryRouter>
        <ProductCard product={p} />
      </MemoryRouter>,
    );

  it("renders name, category and price", () => {
    renderCard(product);
    expect(screen.getByText("Test Widget")).toBeInTheDocument();
    expect(screen.getByText("Gadgets")).toBeInTheDocument();
    expect(screen.getByText(/19\.99/)).toBeInTheDocument();
    expect(screen.getByText("3 left")).toBeInTheDocument();
  });

  it("shows out-of-stock when no inventory", () => {
    renderCard({ ...product, quantity_available: 0 });
    expect(screen.getByText("Out of stock")).toBeInTheDocument();
  });

  it("links to the product detail page", () => {
    renderCard(product);
    expect(screen.getByRole("link", { name: /Test Widget/ })).toHaveAttribute(
      "href",
      "/products/test-widget",
    );
  });
});
