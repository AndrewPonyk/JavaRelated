import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '@/middleware/auth/authMiddleware.js';
export declare class UserController {
    getAllUsers: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    getCurrentUser: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    getUserById: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    createUser: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    updateUser: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    updateUserProfile: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    deleteUser: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    getUserStats: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    searchUsers: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    changePassword: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    uploadAvatar: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
    getUserActivity: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
}
export declare const userController: UserController;
export declare const getAllUsers: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void, getCurrentUser: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void, getUserById: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void, createUser: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void, updateUser: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void, updateUserProfile: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void, deleteUser: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void, getUserStats: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void, searchUsers: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void, changePassword: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void, uploadAvatar: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void, getUserActivity: (req: AuthenticatedRequest, res: Response, next: NextFunction) => void;
//# sourceMappingURL=userController.d.ts.map