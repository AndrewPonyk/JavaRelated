import type { User, UserProfile, UserRole, Dashboard } from '@prisma/client';
type UserWithProfile = User & {
    profile: UserProfile | null;
    dashboards?: Dashboard[];
};
interface UserQuery {
    page: number;
    limit: number;
    search?: string;
    role?: UserRole;
    isActive?: boolean;
    sortBy: 'createdAt' | 'updatedAt' | 'email' | 'lastName';
    sortOrder: 'asc' | 'desc';
}
interface CreateUserData {
    email: string;
    password: string;
    firstName?: string;
    lastName?: string;
    role?: UserRole;
    isActive?: boolean;
}
interface UpdateUserData {
    email?: string;
    firstName?: string;
    lastName?: string;
    role?: UserRole;
    isActive?: boolean;
}
interface UpdateProfileData {
    theme?: 'light' | 'dark' | 'auto';
    timezone?: string;
    language?: string;
    notifications?: any;
    avatar?: string;
    bio?: string;
}
interface PaginatedResult<T> {
    data: T[];
    total: number;
    page: number;
    limit: number;
    pages: number;
    hasNext: boolean;
    hasPrev: boolean;
}
export declare class UserService {
    getAllUsers(query: UserQuery, requestingUserId: string): Promise<PaginatedResult<UserWithProfile>>;
    getUserById(userId: string, includeProfile?: boolean): Promise<UserWithProfile | null>;
    getUserByEmail(email: string): Promise<UserWithProfile | null>;
    createUser(data: CreateUserData): Promise<UserWithProfile>;
    updateUser(userId: string, data: UpdateUserData, requestingUserId: string): Promise<UserWithProfile | null>;
    updateUserProfile(userId: string, data: UpdateProfileData, requestingUserId: string): Promise<UserProfile | null>;
    deleteUser(userId: string, requestingUserId: string): Promise<boolean>;
    getUserStats(userId: string, requestingUserId: string): Promise<any>;
    searchUsers(query: string, requestingUserId: string, limit?: number): Promise<UserWithProfile[]>;
    private isAdmin;
    private isUserAdmin;
    private canEditUser;
    private createQueryHash;
}
export declare const userService: UserService;
export {};
//# sourceMappingURL=userService.d.ts.map