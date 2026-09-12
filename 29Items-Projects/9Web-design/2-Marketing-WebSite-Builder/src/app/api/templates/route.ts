import { fail, ok, readJson } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { createTemplateSchema } from "@/lib/validation";
import { templateService } from "@/services/templateService";

export async function GET(request: Request) {
  try {
    const actor = await getActor(request);
    const organizationId = new URL(request.url).searchParams.get("organizationId");

    if (!organizationId) {
      return ok([]);
    }

    return ok(await templateService.listTemplates(actor, organizationId));
  } catch (error) {
    return fail(error);
  }
}

export async function POST(request: Request) {
  try {
    const actor = await getActor(request);
    const input = createTemplateSchema.parse(await readJson(request));
    return ok(await templateService.createTemplate(actor, input), { status: 201 });
  } catch (error) {
    return fail(error);
  }
}
