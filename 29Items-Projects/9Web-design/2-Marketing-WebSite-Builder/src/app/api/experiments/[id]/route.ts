import { deleted, fail, ok, readJson } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { updateExperimentSchema } from "@/lib/validation";
import { experimentService } from "@/services/experimentService";

interface RouteContext {
  params: Promise<{ id: string }>;
}

export async function PATCH(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    const input = updateExperimentSchema.parse(await readJson(request));
    return ok(await experimentService.updateExperiment(actor, id, input));
  } catch (error) {
    return fail(error);
  }
}

export async function DELETE(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    await experimentService.deleteExperiment(actor, id);
    return deleted(id);
  } catch (error) {
    return fail(error);
  }
}
