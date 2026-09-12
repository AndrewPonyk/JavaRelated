import { deleted, fail, ok, readJson } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { updateOrganizationSchema } from "@/lib/validation";
import { organizationService } from "@/services/organizationService";

interface RouteContext {
  params: Promise<{ id: string }>;
}

export async function GET(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    return ok(await organizationService.getOrganization(actor, id));
  } catch (error) {
    return fail(error);
  }
}

export async function PATCH(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    const input = updateOrganizationSchema.parse(await readJson(request));
    return ok(await organizationService.updateOrganization(actor, id, input));
  } catch (error) {
    return fail(error);
  }
}

export async function DELETE(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    await organizationService.deleteOrganization(actor, id);
    return deleted(id);
  } catch (error) {
    return fail(error);
  }
}
