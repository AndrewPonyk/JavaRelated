import { fail, ok, readJson } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { createPageSchema } from "@/lib/validation";
import { pageService } from "@/services/pageService";

interface RouteContext {
  params: Promise<{ id: string }>;
}

export async function GET(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    return ok(await pageService.listPages(actor, id));
  } catch (error) {
    return fail(error);
  }
}

export async function POST(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    const input = createPageSchema.parse(await readJson(request));
    return ok(await pageService.createPage(actor, id, input), { status: 201 });
  } catch (error) {
    return fail(error);
  }
}
