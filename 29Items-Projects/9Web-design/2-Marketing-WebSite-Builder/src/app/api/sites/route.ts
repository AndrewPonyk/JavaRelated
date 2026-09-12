import { fail, ok, readJson } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { createSiteSchema } from "@/lib/validation";
import { siteService } from "@/services/siteService";

export async function GET(request: Request) {
  try {
    const actor = await getActor(request);
    const organizationId = new URL(request.url).searchParams.get("organizationId") ?? undefined;
    return ok(await siteService.listSites(actor, organizationId));
  } catch (error) {
    return fail(error);
  }
}

export async function POST(request: Request) {
  try {
    const actor = await getActor(request);
    const input = createSiteSchema.parse(await readJson(request));
    return ok(await siteService.createSite(actor, input), { status: 201 });
  } catch (error) {
    return fail(error);
  }
}
