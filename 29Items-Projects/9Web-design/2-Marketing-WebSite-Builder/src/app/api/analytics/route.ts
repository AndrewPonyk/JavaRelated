import { fail, ok } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { analyticsService } from "@/services/analyticsService";
import { ValidationError } from "@/lib/errors";

export async function GET(request: Request) {
  try {
    const actor = await getActor(request);
    const params = new URL(request.url).searchParams;
    const organizationId = params.get("organizationId");

    if (!organizationId) {
      throw new ValidationError("organizationId is required");
    }

    return ok(
      await analyticsService.getSummary(
        actor,
        organizationId,
        params.get("siteId") ?? undefined
      )
    );
  } catch (error) {
    return fail(error);
  }
}
