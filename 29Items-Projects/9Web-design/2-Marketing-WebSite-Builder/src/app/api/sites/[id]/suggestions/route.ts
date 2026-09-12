import { fail, ok } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { suggestionService } from "@/services/suggestionService";

interface RouteContext {
  params: Promise<{ id: string }>;
}

export async function GET(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    return ok(await suggestionService.listSuggestions(actor, id));
  } catch (error) {
    return fail(error);
  }
}

export async function POST(request: Request, context: RouteContext) {
  try {
    const actor = await getActor(request);
    const { id } = await context.params;
    return ok(await suggestionService.generateSuggestion(actor, id), { status: 201 });
  } catch (error) {
    return fail(error);
  }
}
