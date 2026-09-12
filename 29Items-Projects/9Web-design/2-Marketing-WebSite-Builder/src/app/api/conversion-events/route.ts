import { fail, ok, readJson } from "@/lib/api";
import { conversionEventSchema } from "@/lib/validation";
import { analyticsService } from "@/services/analyticsService";

export async function POST(request: Request) {
  try {
    const input = conversionEventSchema.parse(await readJson(request));
    return ok(await analyticsService.recordEvent(input), { status: 201 });
  } catch (error) {
    return fail(error);
  }
}
