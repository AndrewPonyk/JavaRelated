import { Request, Response, NextFunction } from 'express';
import { z, ZodError, ZodSchema } from 'zod';
interface ValidationConfig {
    body?: ZodSchema;
    query?: ZodSchema;
    params?: ZodSchema;
    headers?: ZodSchema;
    skipOnError?: boolean;
    logValidationErrors?: boolean;
}
export declare const validate: (config: ValidationConfig) => (req: Request, res: Response, next: NextFunction) => void;
export declare const validateBody: (schema: ZodSchema, options?: {
    skipOnError?: boolean;
    logErrors?: boolean;
}) => (req: Request, res: Response, next: NextFunction) => void;
export declare const validateQuery: (schema: ZodSchema, options?: {
    skipOnError?: boolean;
    logErrors?: boolean;
}) => (req: Request, res: Response, next: NextFunction) => void;
export declare const validateParams: (schema: ZodSchema, options?: {
    skipOnError?: boolean;
    logErrors?: boolean;
}) => (req: Request, res: Response, next: NextFunction) => void;
export declare const validateHeaders: (schema: ZodSchema, options?: {
    skipOnError?: boolean;
    logErrors?: boolean;
}) => (req: Request, res: Response, next: NextFunction) => void;
export declare const commonSchemas: {
    uuidParam: z.ZodObject<{
        id: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        id: string;
    }, {
        id: string;
    }>;
    pagination: z.ZodObject<{
        page: z.ZodEffects<z.ZodOptional<z.ZodString>, number, string | undefined>;
        limit: z.ZodEffects<z.ZodOptional<z.ZodString>, number, string | undefined>;
        sortBy: z.ZodOptional<z.ZodString>;
        sortOrder: z.ZodDefault<z.ZodOptional<z.ZodEnum<["asc", "desc"]>>>;
    }, "strip", z.ZodTypeAny, {
        page: number;
        limit: number;
        sortOrder: "asc" | "desc";
        sortBy?: string | undefined;
    }, {
        page?: string | undefined;
        limit?: string | undefined;
        sortBy?: string | undefined;
        sortOrder?: "asc" | "desc" | undefined;
    }>;
    search: z.ZodObject<{
        q: z.ZodString;
        limit: z.ZodEffects<z.ZodOptional<z.ZodString>, number, string | undefined>;
    }, "strip", z.ZodTypeAny, {
        limit: number;
        q: string;
    }, {
        q: string;
        limit?: string | undefined;
    }>;
    dateRange: z.ZodEffects<z.ZodObject<{
        startDate: z.ZodOptional<z.ZodString>;
        endDate: z.ZodOptional<z.ZodString>;
    }, "strip", z.ZodTypeAny, {
        startDate?: string | undefined;
        endDate?: string | undefined;
    }, {
        startDate?: string | undefined;
        endDate?: string | undefined;
    }>, {
        startDate?: string | undefined;
        endDate?: string | undefined;
    }, {
        startDate?: string | undefined;
        endDate?: string | undefined;
    }>;
    fileUpload: z.ZodObject<{
        filename: z.ZodString;
        mimetype: z.ZodString;
        size: z.ZodNumber;
    }, "strip", z.ZodTypeAny, {
        size: number;
        filename: string;
        mimetype: string;
    }, {
        size: number;
        filename: string;
        mimetype: string;
    }>;
    email: z.ZodObject<{
        email: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        email: string;
    }, {
        email: string;
    }>;
    password: z.ZodObject<{
        password: z.ZodString;
    }, "strip", z.ZodTypeAny, {
        password: string;
    }, {
        password: string;
    }>;
    booleanQuery: z.ZodObject<{
        value: z.ZodEffects<z.ZodOptional<z.ZodString>, boolean | undefined, string | undefined>;
    }, "strip", z.ZodTypeAny, {
        value?: boolean | undefined;
    }, {
        value?: string | undefined;
    }>;
};
export declare const formatValidationError: (error: ZodError) => any;
export declare const validateEnum: <T extends string>(values: readonly T[], message?: string) => z.ZodEnum<[T, ...T[]]>;
export declare const validateOptionalEnum: <T extends string>(values: readonly T[], message?: string) => z.ZodOptional<z.ZodEnum<[T, ...T[]]>>;
export declare const conditionalValidation: <T>(condition: (data: any) => boolean, schema: ZodSchema<T>) => z.ZodEffects<z.ZodAny, any, any>;
export declare const transformAndValidate: <T>(transformer: (data: any) => any, validator: ZodSchema<T>) => (req: Request, res: Response, next: NextFunction) => void;
export declare const sanitizeHtml: z.ZodEffects<z.ZodString, string, string>;
export declare const sanitizeString: z.ZodEffects<z.ZodString, string, string>;
export declare const validateRateLimit: (maxRequests: number, windowMs: number, keyGenerator?: (req: Request) => string) => (req: Request, res: Response, next: NextFunction) => void;
declare const _default: {
    validate: (config: ValidationConfig) => (req: Request, res: Response, next: NextFunction) => void;
    validateBody: (schema: ZodSchema, options?: {
        skipOnError?: boolean;
        logErrors?: boolean;
    }) => (req: Request, res: Response, next: NextFunction) => void;
    validateQuery: (schema: ZodSchema, options?: {
        skipOnError?: boolean;
        logErrors?: boolean;
    }) => (req: Request, res: Response, next: NextFunction) => void;
    validateParams: (schema: ZodSchema, options?: {
        skipOnError?: boolean;
        logErrors?: boolean;
    }) => (req: Request, res: Response, next: NextFunction) => void;
    validateHeaders: (schema: ZodSchema, options?: {
        skipOnError?: boolean;
        logErrors?: boolean;
    }) => (req: Request, res: Response, next: NextFunction) => void;
    commonSchemas: {
        uuidParam: z.ZodObject<{
            id: z.ZodString;
        }, "strip", z.ZodTypeAny, {
            id: string;
        }, {
            id: string;
        }>;
        pagination: z.ZodObject<{
            page: z.ZodEffects<z.ZodOptional<z.ZodString>, number, string | undefined>;
            limit: z.ZodEffects<z.ZodOptional<z.ZodString>, number, string | undefined>;
            sortBy: z.ZodOptional<z.ZodString>;
            sortOrder: z.ZodDefault<z.ZodOptional<z.ZodEnum<["asc", "desc"]>>>;
        }, "strip", z.ZodTypeAny, {
            page: number;
            limit: number;
            sortOrder: "asc" | "desc";
            sortBy?: string | undefined;
        }, {
            page?: string | undefined;
            limit?: string | undefined;
            sortBy?: string | undefined;
            sortOrder?: "asc" | "desc" | undefined;
        }>;
        search: z.ZodObject<{
            q: z.ZodString;
            limit: z.ZodEffects<z.ZodOptional<z.ZodString>, number, string | undefined>;
        }, "strip", z.ZodTypeAny, {
            limit: number;
            q: string;
        }, {
            q: string;
            limit?: string | undefined;
        }>;
        dateRange: z.ZodEffects<z.ZodObject<{
            startDate: z.ZodOptional<z.ZodString>;
            endDate: z.ZodOptional<z.ZodString>;
        }, "strip", z.ZodTypeAny, {
            startDate?: string | undefined;
            endDate?: string | undefined;
        }, {
            startDate?: string | undefined;
            endDate?: string | undefined;
        }>, {
            startDate?: string | undefined;
            endDate?: string | undefined;
        }, {
            startDate?: string | undefined;
            endDate?: string | undefined;
        }>;
        fileUpload: z.ZodObject<{
            filename: z.ZodString;
            mimetype: z.ZodString;
            size: z.ZodNumber;
        }, "strip", z.ZodTypeAny, {
            size: number;
            filename: string;
            mimetype: string;
        }, {
            size: number;
            filename: string;
            mimetype: string;
        }>;
        email: z.ZodObject<{
            email: z.ZodString;
        }, "strip", z.ZodTypeAny, {
            email: string;
        }, {
            email: string;
        }>;
        password: z.ZodObject<{
            password: z.ZodString;
        }, "strip", z.ZodTypeAny, {
            password: string;
        }, {
            password: string;
        }>;
        booleanQuery: z.ZodObject<{
            value: z.ZodEffects<z.ZodOptional<z.ZodString>, boolean | undefined, string | undefined>;
        }, "strip", z.ZodTypeAny, {
            value?: boolean | undefined;
        }, {
            value?: string | undefined;
        }>;
    };
    formatValidationError: (error: ZodError) => any;
    validateEnum: <T extends string>(values: readonly T[], message?: string) => z.ZodEnum<[T, ...T[]]>;
    validateOptionalEnum: <T extends string>(values: readonly T[], message?: string) => z.ZodOptional<z.ZodEnum<[T, ...T[]]>>;
    conditionalValidation: <T>(condition: (data: any) => boolean, schema: ZodSchema<T>) => z.ZodEffects<z.ZodAny, any, any>;
    transformAndValidate: <T>(transformer: (data: any) => any, validator: ZodSchema<T>) => (req: Request, res: Response, next: NextFunction) => void;
    sanitizeHtml: z.ZodEffects<z.ZodString, string, string>;
    sanitizeString: z.ZodEffects<z.ZodString, string, string>;
    validateRateLimit: (maxRequests: number, windowMs: number, keyGenerator?: (req: Request) => string) => (req: Request, res: Response, next: NextFunction) => void;
};
export default _default;
//# sourceMappingURL=validationMiddleware.d.ts.map