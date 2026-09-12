import { fail, ok, readJson } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { createOrganizationSchema } from "@/lib/validation";
import { organizationService } from "@/services/organizationService";

export async function GET(request: Request) {
  try {
    const actor = await getActor(request);
    return ok(await organizationService.listOrganizations(actor));
  } catch (error) {
    return fail(error);
  }
}

export async function POST(request: Request) {
  try {
    const actor = await getActor(request);
    const input = createOrganizationSchema.parse(await readJson(request));
    return ok(await organizationService.createOrganization(actor, input), { status: 201 });
  } catch (error) {
    return fail(error);
  }
}
