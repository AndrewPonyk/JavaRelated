import { deleted, fail, ok, readJson } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { updateTemplateSchema } from "@/lib/validation";
import { templateService } from "@/services/templateService";

interface RouteContext {
  params: Promise<{ id: string }>;
}

export async function PATCH(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    const input = updateTemplateSchema.parse(await readJson(request));
    return ok(await templateService.updateTemplate(actor, id, input));
  } catch (error) {
    return fail(error);
  }
}

export async function DELETE(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    await templateService.deleteTemplate(actor, id);
    return deleted(id);
  } catch (error) {
    return fail(error);
  }
}
