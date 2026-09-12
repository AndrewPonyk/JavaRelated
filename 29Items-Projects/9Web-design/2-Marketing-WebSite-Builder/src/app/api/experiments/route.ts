import { fail, ok, readJson } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { createExperimentSchema } from "@/lib/validation";
import { experimentService } from "@/services/experimentService";

export async function GET(request: Request) {
  try {
    const actor = await getActor(request);
    const organizationId = new URL(request.url).searchParams.get("organizationId");

    if (!organizationId) {
      return ok([]);
    }

    return ok(await experimentService.listExperiments(actor, organizationId));
  } catch (error) {
    return fail(error);
  }
}

export async function POST(request: Request) {
  try {
    const actor = await getActor(request);
    const input = createExperimentSchema.parse(await readJson(request));
    return ok(await experimentService.createExperiment(actor, input), { status: 201 });
  } catch (error) {
    return fail(error);
  }
}
