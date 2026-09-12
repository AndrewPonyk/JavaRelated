import { Link } from "react-router-dom";
import type { Product } from "@/types";

export function ProductCard({ product }: { product: Product }) {
  const outOfStock = product.quantity_available <= 0;
  return (
    <li className="flex flex-col rounded-lg border border-gray-200 bg-white p-4 transition hover:shadow-md">
      <Link to={`/products/${product.slug}`} className="flex-1">
        <h3 className="font-medium text-gray-900">{product.name}</h3>
        <p className="mt-1 text-sm text-gray-500">{product.category.name}</p>
      </Link>
      <div className="mt-3 flex items-center justify-between">
        <span className="font-semibold text-brand">
          {product.currency} {product.price}
        </span>
        {outOfStock ? (
          <span className="text-xs font-medium text-red-500">Out of stock</span>
        ) : (
          <span className="text-xs text-gray-400">{product.quantity_available} left</span>
        )}
      </div>
    </li>
  );
}
