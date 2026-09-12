import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import { ordersApi } from "@/api/endpoints";
import { ErrorBanner, Spinner } from "@/components/common/Spinner";

const STATUS_STYLES: Record<string, string> = {
  paid: "bg-green-100 text-green-700",
  pending: "bg-yellow-100 text-yellow-700",
  fulfilled: "bg-blue-100 text-blue-700",
  cancelled: "bg-gray-200 text-gray-600",
  refunded: "bg-purple-100 text-purple-700",
};

export function OrdersPage() {
  const qc = useQueryClient();
  const [params] = useSearchParams();
  const justPlaced = params.get("placed");

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ["orders"],
    queryFn: ordersApi.list,
  });

  const cancel = useMutation({
    mutationFn: (number: string) => ordersApi.cancel(number),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["orders"] }),
  });

  if (isLoading) return <Spinner />;
  if (isError || !data) return <ErrorBanner message="Could not load orders." onRetry={refetch} />;

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-gray-900">Your orders</h1>
      {justPlaced && (
        <div className="rounded-md bg-green-50 p-3 text-green-700">
          Order {justPlaced} placed successfully — a confirmation email is on its way.
        </div>
      )}
      {data.results.length === 0 ? (
        <p className="text-gray-500">You have no orders yet.</p>
      ) : (
        <ul className="space-y-4">
          {data.results.map((order) => (
            <li key={order.id} className="rounded-lg bg-white p-4 shadow-sm">
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-semibold text-gray-900">{order.number}</p>
                  <p className="text-sm text-gray-400">
                    {new Date(order.created_at).toLocaleString()}
                  </p>
                </div>
                <span
                  className={`rounded-full px-3 py-1 text-xs font-medium ${
                    STATUS_STYLES[order.status] ?? "bg-gray-100 text-gray-600"
                  }`}
                >
                  {order.status}
                </span>
              </div>
              <ul className="mt-3 space-y-1 text-sm text-gray-600">
                {order.items.map((item, i) => (
                  <li key={i}>
                    {item.quantity} × {item.product_name} @ {item.unit_price}
                  </li>
                ))}
              </ul>
              <div className="mt-3 flex items-center justify-between">
                <span className="font-semibold">
                  {order.currency} {order.total_amount}
                </span>
                {(order.status === "paid" || order.status === "pending") && (
                  <button
                    onClick={() => cancel.mutate(order.number)}
                    disabled={cancel.isPending}
                    className="text-sm text-red-500 hover:text-red-700 disabled:opacity-50"
                  >
                    Cancel
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
