import { fail, ok, readJson } from "@/lib/api";
import { getActor } from "@/lib/auth";
import { figmaImportSchema } from "@/lib/validation";
import { figmaImportService } from "@/services/figmaImportService";

export async function POST(request: Request) {
  try {
    const actor = await getActor(request);
    const input = figmaImportSchema.parse(await readJson(request));
    return ok(await figmaImportService.importSelection(actor, input), { status: 201 });
  } catch (error) {
    return fail(error);
  }
}
