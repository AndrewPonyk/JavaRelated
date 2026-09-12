import { deleted, fail, ok, readJson } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { updateSiteSchema } from "@/lib/validation";
import { siteService } from "@/services/siteService";

interface RouteContext {
  params: Promise<{ id: string }>;
}

export async function GET(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    return ok(await siteService.getSite(actor, id));
  } catch (error) {
    return fail(error);
  }
}

export async function PATCH(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    const input = updateSiteSchema.parse(await readJson(request));
    return ok(await siteService.updateSite(actor, id, input));
  } catch (error) {
    return fail(error);
  }
}

export async function DELETE(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    await siteService.deleteSite(actor, id);
    return deleted(id);
  } catch (error) {
    return fail(error);
  }
}
