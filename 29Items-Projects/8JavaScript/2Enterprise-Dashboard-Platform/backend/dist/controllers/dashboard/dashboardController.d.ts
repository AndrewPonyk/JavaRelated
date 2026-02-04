import { Request, Response, NextFunction } from 'express';
import { z } from 'zod';
import { DashboardService } from '../../services/dashboard/dashboardService';
declare const createDashboardSchema: z.ZodObject<{
    title: z.ZodString;
    description: z.ZodOptional<z.ZodString>;
    isPublic: z.ZodDefault<z.ZodBoolean>;
    layout: z.ZodArray<z.ZodObject<{
        id: z.ZodString;
        x: z.ZodNumber;
        y: z.ZodNumber;
        width: z.ZodNumber;
        height: z.ZodNumber;
    }, "strip", z.ZodTypeAny, {
        id: string;
        y: number;
        x: number;
        width: number;
        height: number;
    }, {
        id: string;
        y: number;
        x: number;
        width: number;
        height: number;
    }>, "many">;
}, "strip", z.ZodTypeAny, {
    title: string;
    isPublic: boolean;
    layout: {
        id: string;
        y: number;
        x: number;
        width: number;
        height: number;
    }[];
    description?: string | undefined;
}, {
    title: string;
    layout: {
        id: string;
        y: number;
        x: number;
        width: number;
        height: number;
    }[];
    isPublic?: boolean | undefined;
    description?: string | undefined;
}>;
declare const updateDashboardSchema: z.ZodObject<{
    title: z.ZodOptional<z.ZodString>;
    description: z.ZodOptional<z.ZodString>;
    isPublic: z.ZodOptional<z.ZodBoolean>;
    layout: z.ZodOptional<z.ZodArray<z.ZodObject<{
        id: z.ZodString;
        x: z.ZodNumber;
        y: z.ZodNumber;
        width: z.ZodNumber;
        height: z.ZodNumber;
    }, "strip", z.ZodTypeAny, {
        id: string;
        y: number;
        x: number;
        width: number;
        height: number;
    }, {
        id: string;
        y: number;
        x: number;
        width: number;
        height: number;
    }>, "many">>;
}, "strip", z.ZodTypeAny, {
    title?: string | undefined;
    isPublic?: boolean | undefined;
    description?: string | undefined;
    layout?: {
        id: string;
        y: number;
        x: number;
        width: number;
        height: number;
    }[] | undefined;
}, {
    title?: string | undefined;
    isPublic?: boolean | undefined;
    description?: string | undefined;
    layout?: {
        id: string;
        y: number;
        x: number;
        width: number;
        height: number;
    }[] | undefined;
}>;
declare const dashboardQuerySchema: z.ZodObject<{
    page: z.ZodEffects<z.ZodOptional<z.ZodString>, number, string | undefined>;
    limit: z.ZodEffects<z.ZodOptional<z.ZodString>, number, string | undefined>;
    search: z.ZodOptional<z.ZodString>;
    sortBy: z.ZodDefault<z.ZodOptional<z.ZodEnum<["createdAt", "updatedAt", "title"]>>>;
    sortOrder: z.ZodDefault<z.ZodOptional<z.ZodEnum<["asc", "desc"]>>>;
}, "strip", z.ZodTypeAny, {
    page: number;
    limit: number;
    sortBy: "title" | "createdAt" | "updatedAt";
    sortOrder: "asc" | "desc";
    search?: string | undefined;
}, {
    search?: string | undefined;
    page?: string | undefined;
    limit?: string | undefined;
    sortBy?: "title" | "createdAt" | "updatedAt" | undefined;
    sortOrder?: "asc" | "desc" | undefined;
}>;
interface AuthenticatedRequest extends Request {
    user?: {
        id: string;
        email: string;
        role: string;
    };
}
export declare class DashboardController {
    private dashboardService;
    private cacheService;
    constructor(dashboardService: DashboardService, cacheService: CacheService);
    getDashboards: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    getDashboard: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    createDashboard: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    updateDashboard: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    deleteDashboard: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    shareDashboard: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    getDashboardAnalytics: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
}
export declare const createDashboardController: (dashboardService: DashboardService, cacheService: CacheService) => DashboardController;
export { createDashboardSchema, updateDashboardSchema, dashboardQuerySchema, };
//# sourceMappingURL=dashboardController.d.ts.map