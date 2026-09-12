import { NextResponse } from "next/server";
import { ZodError } from "zod";
import { logger } from "@/lib/logger";
import { AppError, toAppError, ValidationError } from "@/lib/errors";

export function ok<T>(data: T, init?: ResponseInit) {
  return NextResponse.json({ ok: true, data }, init);
}

export function deleted(id: string) {
  return ok({ id, deleted: true });
}

export function fail(error: unknown, context: Record<string, unknown> = {}) {
  const appError =
    error instanceof ZodError
      ? new ValidationError("Invalid request payload", error.flatten())
      : toAppError(error);

  if (!(error instanceof AppError) || appError.status >= 500) {
    logger.error(appError.message, {
      code: appError.code,
      ...context
    });
  }

  return NextResponse.json(
    {
      ok: false,
      error: {
        code: appError.code,
        message: appError.message,
        details: appError.details
      }
    },
    { status: appError.status }
  );
}

export async function readJson(request: Request) {
  return request.json().catch(() => null);
}
