import { deleted, fail, ok, readJson } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { updatePageSchema } from "@/lib/validation";
import { pageService } from "@/services/pageService";

interface RouteContext {
  params: Promise<{ id: string; pageId: string }>;
}

export async function PATCH(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id, pageId } = await context.params;
    const input = updatePageSchema.parse(await readJson(request));
    return ok(await pageService.updatePage(actor, id, pageId, input));
  } catch (error) {
    return fail(error);
  }
}

export async function DELETE(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id, pageId } = await context.params;
    await pageService.deletePage(actor, id, pageId);
    return deleted(pageId);
  } catch (error) {
    return fail(error);
  }
}
