// Proxy for deleting a single document.
import { deleteDocument } from "@/lib/api";
import { proxy } from "@/lib/proxy";

export async function DELETE(_request: Request, { params }: { params: { id: string } }) {
  return proxy(() => deleteDocument(params.id));
}
