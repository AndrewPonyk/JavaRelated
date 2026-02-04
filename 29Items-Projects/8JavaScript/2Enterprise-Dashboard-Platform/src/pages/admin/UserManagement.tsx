import React, { useState, useEffect, useCallback } from 'react';
import {
  Search,
  Plus,
  Edit,
  Trash2,
  X,
  ChevronLeft,
  ChevronRight,
  UserCheck,
  UserX,
  Shield,
  Eye,
  MoreVertical,
  RefreshCw,
} from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Avatar } from '@/components/ui/Avatar';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
import { useToastHelpers } from '@/components/ui/Toaster';
import { userApi, User, UserRole, CreateUserData, UpdateUserData, GetUsersParams } from '@/services/api/userApi';
import { cn } from '@/utils/cn';

// Role configuration
const roleConfig: Record<UserRole, { label: string; color: string; icon: React.ReactNode }> = {
  SUPER_ADMIN: { label: 'Super Admin', color: 'bg-purple-100 text-purple-800', icon: <Shield className="h-3 w-3" /> },
  ADMIN: { label: 'Admin', color: 'bg-red-100 text-red-800', icon: <Shield className="h-3 w-3" /> },
  MANAGER: { label: 'Manager', color: 'bg-blue-100 text-blue-800', icon: <UserCheck className="h-3 w-3" /> },
  USER: { label: 'User', color: 'bg-green-100 text-green-800', icon: <UserCheck className="h-3 w-3" /> },
  VIEWER: { label: 'Viewer', color: 'bg-gray-100 text-gray-800', icon: <Eye className="h-3 w-3" /> },
};

const roles: UserRole[] = ['SUPER_ADMIN', 'ADMIN', 'MANAGER', 'USER', 'VIEWER'];

// User Form Modal Component
interface UserFormModalProps {
  open: boolean;
  onClose: () => void;
  onSubmit: (data: CreateUserData | UpdateUserData) => Promise<void>;
  user?: User | null;
  isLoading: boolean;
}

const UserFormModal: React.FC<UserFormModalProps> = ({ open, onClose, onSubmit, user, isLoading }) => {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    role: 'USER' as UserRole,
    isActive: true,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (user) {
      setFormData({
        email: user.email,
        password: '',
        firstName: user.firstName || '',
        lastName: user.lastName || '',
        role: user.role,
        isActive: user.isActive,
      });
    } else {
      setFormData({
        email: '',
        password: '',
        firstName: '',
        lastName: '',
        role: 'USER',
        isActive: true,
      });
    }
    setErrors({});
  }, [user, open]);

  const validate = (): boolean => {
    const newErrors: Record<string, string> = {};

    if (!formData.email) {
      newErrors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      newErrors.email = 'Invalid email format';
    }

    if (!user && !formData.password) {
      newErrors.password = 'Password is required';
    } else if (!user && formData.password.length < 8) {
      newErrors.password = 'Password must be at least 8 characters';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    const data: CreateUserData | UpdateUserData = user
      ? {
          email: formData.email,
          firstName: formData.firstName || undefined,
          lastName: formData.lastName || undefined,
          role: formData.role,
          isActive: formData.isActive,
        }
      : {
          email: formData.email,
          password: formData.password,
          firstName: formData.firstName || undefined,
          lastName: formData.lastName || undefined,
          role: formData.role,
          isActive: formData.isActive,
        };

    await onSubmit(data);
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center">
      <div className="bg-white rounded-lg shadow-2xl w-full max-w-md mx-4">
        <div className="flex items-center justify-between p-4 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">
            {user ? 'Edit User' : 'Create New User'}
          </h2>
          <Button variant="ghost" size="icon" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>

        <form onSubmit={handleSubmit} className="p-4 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">First Name</label>
              <input
                type="text"
                value={formData.firstName}
                onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="John"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Last Name</label>
              <input
                type="text"
                value={formData.lastName}
                onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="Doe"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Email *</label>
            <input
              type="email"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              className={cn(
                "w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500",
                errors.email ? "border-red-500" : "border-gray-300"
              )}
              placeholder="john@example.com"
            />
            {errors.email && <p className="text-red-500 text-xs mt-1">{errors.email}</p>}
          </div>

          {!user && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Password *</label>
              <input
                type="password"
                value={formData.password}
                onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                className={cn(
                  "w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500",
                  errors.password ? "border-red-500" : "border-gray-300"
                )}
                placeholder="Minimum 8 characters"
              />
              {errors.password && <p className="text-red-500 text-xs mt-1">{errors.password}</p>}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Role</label>
            <select
              value={formData.role}
              onChange={(e) => setFormData({ ...formData, role: e.target.value as UserRole })}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {roles.map((role) => (
                <option key={role} value={role}>
                  {roleConfig[role].label}
                </option>
              ))}
            </select>
          </div>

          <div className="flex items-center space-x-2">
            <input
              type="checkbox"
              id="isActive"
              checked={formData.isActive}
              onChange={(e) => setFormData({ ...formData, isActive: e.target.checked })}
              className="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
            />
            <label htmlFor="isActive" className="text-sm text-gray-700">Active user</label>
          </div>

          <div className="flex justify-end space-x-2 pt-4">
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isLoading}>
              {isLoading ? 'Saving...' : user ? 'Update User' : 'Create User'}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
};

// Delete Confirmation Modal
interface DeleteModalProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void>;
  user: User | null;
  isLoading: boolean;
}

const DeleteModal: React.FC<DeleteModalProps> = ({ open, onClose, onConfirm, user, isLoading }) => {
  if (!open || !user) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center">
      <div className="bg-white rounded-lg shadow-2xl w-full max-w-sm mx-4 p-6">
        <div className="text-center">
          <div className="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100 mb-4">
            <Trash2 className="h-6 w-6 text-red-600" />
          </div>
          <h3 className="text-lg font-medium text-gray-900 mb-2">Delete User</h3>
          <p className="text-sm text-gray-600 mb-4">
            Are you sure you want to deactivate <strong>{user.firstName} {user.lastName}</strong> ({user.email})?
            This action can be reversed.
          </p>
          <div className="flex justify-center space-x-3">
            <Button variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={onConfirm} disabled={isLoading}>
              {isLoading ? 'Deleting...' : 'Delete'}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};

// Main UserManagement Component
export const UserManagement: React.FC = () => {
  const { success: showSuccess, error: showError } = useToastHelpers();

  // State
  const [users, setUsers] = useState<User[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [pagination, setPagination] = useState({ page: 1, limit: 10, total: 0, totalPages: 0 });

  // Filters
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<UserRole | ''>('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'inactive'>('all');

  // Modals
  const [isFormModalOpen, setIsFormModalOpen] = useState(false);
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);

  // Fetch users
  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    try {
      const params: GetUsersParams = {
        page: pagination.page,
        limit: pagination.limit,
        search: search || undefined,
        role: roleFilter || undefined,
        isActive: statusFilter === 'all' ? undefined : statusFilter === 'active',
        sortBy: 'updatedAt',
        sortOrder: 'desc',
      };

      const response = await userApi.getUsers(params);
      setUsers(response.data.users);
      setPagination(prev => ({
        ...prev,
        total: response.data.pagination.total,
        totalPages: response.data.pagination.totalPages,
      }));
    } catch (error) {
      showError('Failed to load users', 'Please try again later');
      console.error('Failed to fetch users:', error);
    } finally {
      setIsLoading(false);
    }
  }, [pagination.page, pagination.limit, search, roleFilter, statusFilter, showError]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  // Debounced search
  useEffect(() => {
    const timer = setTimeout(() => {
      setPagination(prev => ({ ...prev, page: 1 }));
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  // Handlers
  const handleCreateUser = () => {
    setSelectedUser(null);
    setIsFormModalOpen(true);
  };

  const handleEditUser = (user: User) => {
    setSelectedUser(user);
    setIsFormModalOpen(true);
  };

  const handleDeleteUser = (user: User) => {
    setSelectedUser(user);
    setIsDeleteModalOpen(true);
  };

  const handleFormSubmit = async (data: CreateUserData | UpdateUserData) => {
    setIsSubmitting(true);
    try {
      if (selectedUser) {
        await userApi.updateUser(selectedUser.id, data as UpdateUserData);
        showSuccess('User updated', 'User has been updated successfully');
      } else {
        await userApi.createUser(data as CreateUserData);
        showSuccess('User created', 'New user has been created successfully');
      }
      setIsFormModalOpen(false);
      fetchUsers();
    } catch (error: any) {
      showError('Operation failed', error?.message || 'Please try again');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!selectedUser) return;
    setIsSubmitting(true);
    try {
      await userApi.deleteUser(selectedUser.id);
      showSuccess('User deleted', 'User has been deactivated');
      setIsDeleteModalOpen(false);
      fetchUsers();
    } catch (error: any) {
      showError('Delete failed', error?.message || 'Please try again');
    } finally {
      setIsSubmitting(false);
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  };

  const getUserInitials = (user: User) => {
    const first = user.firstName?.[0] || '';
    const last = user.lastName?.[0] || '';
    return (first + last).toUpperCase() || user.email[0].toUpperCase();
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">User Management</h1>
          <p className="text-gray-600">Manage users, roles, and permissions</p>
        </div>
        <Button onClick={handleCreateUser}>
          <Plus className="mr-2 h-4 w-4" />
          Add User
        </Button>
      </div>

      {/* Filters */}
      <Card className="p-4">
        <div className="flex flex-wrap gap-4">
          {/* Search */}
          <div className="flex-1 min-w-[200px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search by name or email..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>

          {/* Role Filter */}
          <select
            value={roleFilter}
            onChange={(e) => {
              setRoleFilter(e.target.value as UserRole | '');
              setPagination(prev => ({ ...prev, page: 1 }));
            }}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">All Roles</option>
            {roles.map((role) => (
              <option key={role} value={role}>{roleConfig[role].label}</option>
            ))}
          </select>

          {/* Status Filter */}
          <select
            value={statusFilter}
            onChange={(e) => {
              setStatusFilter(e.target.value as 'all' | 'active' | 'inactive');
              setPagination(prev => ({ ...prev, page: 1 }));
            }}
            className="px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="all">All Status</option>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </select>

          {/* Refresh */}
          <Button variant="outline" onClick={fetchUsers} disabled={isLoading}>
            <RefreshCw className={cn("h-4 w-4", isLoading && "animate-spin")} />
          </Button>
        </div>
      </Card>

      {/* Users Table */}
      <Card>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">User</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">Role</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">Created</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">Last Login</th>
                <th className="text-right px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {isLoading ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center">
                    <div className="flex items-center justify-center space-x-2 text-gray-500">
                      <RefreshCw className="h-5 w-5 animate-spin" />
                      <span>Loading users...</span>
                    </div>
                  </td>
                </tr>
              ) : users.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-6 py-12 text-center text-gray-500">
                    No users found
                  </td>
                </tr>
              ) : (
                users.map((user) => (
                  <tr key={user.id} className="hover:bg-gray-50">
                    <td className="px-6 py-4">
                      <div className="flex items-center space-x-3">
                        <Avatar className="h-10 w-10 bg-blue-100 text-blue-600">
                          {user.profile?.avatar ? (
                            <img src={user.profile.avatar} alt="" className="h-full w-full object-cover" />
                          ) : (
                            <span className="text-sm font-medium">{getUserInitials(user)}</span>
                          )}
                        </Avatar>
                        <div>
                          <p className="font-medium text-gray-900">
                            {user.firstName || user.lastName
                              ? `${user.firstName || ''} ${user.lastName || ''}`.trim()
                              : 'Unnamed User'}
                          </p>
                          <p className="text-sm text-gray-500">{user.email}</p>
                        </div>
                      </div>
                    </td>
                    <td className="px-6 py-4">
                      <Badge className={cn("inline-flex items-center space-x-1", roleConfig[user.role].color)}>
                        {roleConfig[user.role].icon}
                        <span>{roleConfig[user.role].label}</span>
                      </Badge>
                    </td>
                    <td className="px-6 py-4">
                      {user.isActive ? (
                        <Badge className="bg-green-100 text-green-800">
                          <UserCheck className="h-3 w-3 mr-1" />
                          Active
                        </Badge>
                      ) : (
                        <Badge className="bg-gray-100 text-gray-800">
                          <UserX className="h-3 w-3 mr-1" />
                          Inactive
                        </Badge>
                      )}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-500">
                      {formatDate(user.createdAt)}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-500">
                      {user.lastLogin ? formatDate(user.lastLogin) : 'Never'}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon">
                            <MoreVertical className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => handleEditUser(user)}>
                            <Edit className="mr-2 h-4 w-4" />
                            Edit User
                          </DropdownMenuItem>
                          <DropdownMenuSeparator />
                          <DropdownMenuItem
                            onClick={() => handleDeleteUser(user)}
                            className="text-red-600 focus:text-red-600"
                          >
                            <Trash2 className="mr-2 h-4 w-4" />
                            Delete User
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {pagination.totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-4 border-t border-gray-200">
            <p className="text-sm text-gray-500">
              Showing {(pagination.page - 1) * pagination.limit + 1} to{' '}
              {Math.min(pagination.page * pagination.limit, pagination.total)} of {pagination.total} users
            </p>
            <div className="flex items-center space-x-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPagination(prev => ({ ...prev, page: prev.page - 1 }))}
                disabled={pagination.page === 1}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="text-sm text-gray-700">
                Page {pagination.page} of {pagination.totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPagination(prev => ({ ...prev, page: prev.page + 1 }))}
                disabled={pagination.page === pagination.totalPages}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          </div>
        )}
      </Card>

      {/* Modals */}
      <UserFormModal
        open={isFormModalOpen}
        onClose={() => setIsFormModalOpen(false)}
        onSubmit={handleFormSubmit}
        user={selectedUser}
        isLoading={isSubmitting}
      />

      <DeleteModal
        open={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        onConfirm={handleDeleteConfirm}
        user={selectedUser}
        isLoading={isSubmitting}
      />
    </div>
  );
};

export default UserManagement;
