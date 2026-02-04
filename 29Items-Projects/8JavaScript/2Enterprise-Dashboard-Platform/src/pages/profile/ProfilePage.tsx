import React, { useState } from 'react';
import { User, Mail, Shield, Camera, Lock, Bell, Globe, Palette } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import { Avatar } from '@/components/ui/Avatar';
import { useAuth } from '@/components/auth/AuthProvider';
import { useToastHelpers } from '@/components/ui/Toaster';
import { cn } from '@/utils/cn';

export const ProfilePage: React.FC = () => {
  const { user } = useAuth();
  const { success: showSuccess, error: showError } = useToastHelpers();

  const [activeTab, setActiveTab] = useState<'profile' | 'security' | 'notifications' | 'preferences'>('profile');
  const [isEditing, setIsEditing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);

  const [profileData, setProfileData] = useState({
    firstName: user?.firstName || '',
    lastName: user?.lastName || '',
    email: user?.email || '',
    bio: '',
    timezone: 'UTC',
    language: 'en',
  });

  const [passwordData, setPasswordData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  const [notifications, setNotifications] = useState({
    email: true,
    push: true,
    dashboard: true,
    security: true,
    updates: false,
  });

  const handleSaveProfile = async () => {
    setIsSaving(true);
    try {
      await new Promise(resolve => setTimeout(resolve, 1000));
      showSuccess('Profile updated', 'Your profile has been saved successfully');
      setIsEditing(false);
    } catch (error) {
      showError('Error', 'Failed to update profile');
    } finally {
      setIsSaving(false);
    }
  };

  const handleChangePassword = async () => {
    if (passwordData.newPassword !== passwordData.confirmPassword) {
      showError('Error', 'Passwords do not match');
      return;
    }
    if (passwordData.newPassword.length < 8) {
      showError('Error', 'Password must be at least 8 characters');
      return;
    }
    setIsSaving(true);
    try {
      await new Promise(resolve => setTimeout(resolve, 1000));
      showSuccess('Password changed', 'Your password has been updated');
      setPasswordData({ currentPassword: '', newPassword: '', confirmPassword: '' });
    } catch (error) {
      showError('Error', 'Failed to change password');
    } finally {
      setIsSaving(false);
    }
  };

  const tabs = [
    { id: 'profile', label: 'Profile', icon: User },
    { id: 'security', label: 'Security', icon: Lock },
    { id: 'notifications', label: 'Notifications', icon: Bell },
    { id: 'preferences', label: 'Preferences', icon: Palette },
  ] as const;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Profile Settings</h1>
          <p className="text-gray-600">Manage your account settings and preferences</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* Sidebar */}
        <Card className="p-4 lg:col-span-1 h-fit">
          <div className="flex flex-col items-center mb-6">
            <div className="relative">
              <Avatar className="h-24 w-24 bg-blue-100 text-blue-600 text-2xl">
                {user?.firstName?.[0]}{user?.lastName?.[0]}
              </Avatar>
              <button className="absolute bottom-0 right-0 p-2 bg-blue-600 text-white rounded-full hover:bg-blue-700">
                <Camera className="h-4 w-4" />
              </button>
            </div>
            <h3 className="mt-4 font-semibold text-gray-900">
              {user?.firstName} {user?.lastName}
            </h3>
            <p className="text-sm text-gray-500">{user?.email}</p>
            <Badge className="mt-2 bg-blue-100 text-blue-800">{user?.role}</Badge>
          </div>

          <nav className="space-y-1">
            {tabs.map((tab) => {
              const Icon = tab.icon;
              return (
                <button
                  key={tab.id}
                  onClick={() => setActiveTab(tab.id)}
                  className={cn(
                    'w-full flex items-center px-3 py-2 text-sm font-medium rounded-md transition-colors',
                    activeTab === tab.id
                      ? 'bg-blue-50 text-blue-700'
                      : 'text-gray-600 hover:bg-gray-50'
                  )}
                >
                  <Icon className="mr-3 h-5 w-5" />
                  {tab.label}
                </button>
              );
            })}
          </nav>
        </Card>

        {/* Content */}
        <div className="lg:col-span-3 space-y-6">
          {activeTab === 'profile' && (
            <Card className="p-6">
              <div className="flex items-center justify-between mb-6">
                <h2 className="text-lg font-semibold text-gray-900">Personal Information</h2>
                {!isEditing ? (
                  <Button variant="outline" onClick={() => setIsEditing(true)}>Edit</Button>
                ) : (
                  <div className="space-x-2">
                    <Button variant="outline" onClick={() => setIsEditing(false)}>Cancel</Button>
                    <Button onClick={handleSaveProfile} disabled={isSaving}>
                      {isSaving ? 'Saving...' : 'Save'}
                    </Button>
                  </div>
                )}
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">First Name</label>
                  <input
                    type="text"
                    value={profileData.firstName}
                    onChange={(e) => setProfileData({ ...profileData, firstName: e.target.value })}
                    disabled={!isEditing}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md disabled:bg-gray-50"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Last Name</label>
                  <input
                    type="text"
                    value={profileData.lastName}
                    onChange={(e) => setProfileData({ ...profileData, lastName: e.target.value })}
                    disabled={!isEditing}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md disabled:bg-gray-50"
                  />
                </div>
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-2">Email</label>
                  <input
                    type="email"
                    value={profileData.email}
                    disabled
                    className="w-full px-3 py-2 border border-gray-300 rounded-md bg-gray-50"
                  />
                  <p className="mt-1 text-xs text-gray-500">Contact admin to change email</p>
                </div>
                <div className="md:col-span-2">
                  <label className="block text-sm font-medium text-gray-700 mb-2">Bio</label>
                  <textarea
                    value={profileData.bio}
                    onChange={(e) => setProfileData({ ...profileData, bio: e.target.value })}
                    disabled={!isEditing}
                    rows={3}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md disabled:bg-gray-50"
                    placeholder="Tell us about yourself..."
                  />
                </div>
              </div>
            </Card>
          )}

          {activeTab === 'security' && (
            <Card className="p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-6">Change Password</h2>
              <div className="max-w-md space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Current Password</label>
                  <input
                    type="password"
                    value={passwordData.currentPassword}
                    onChange={(e) => setPasswordData({ ...passwordData, currentPassword: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">New Password</label>
                  <input
                    type="password"
                    value={passwordData.newPassword}
                    onChange={(e) => setPasswordData({ ...passwordData, newPassword: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Confirm New Password</label>
                  <input
                    type="password"
                    value={passwordData.confirmPassword}
                    onChange={(e) => setPasswordData({ ...passwordData, confirmPassword: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  />
                </div>
                <Button onClick={handleChangePassword} disabled={isSaving}>
                  {isSaving ? 'Updating...' : 'Update Password'}
                </Button>
              </div>
            </Card>
          )}

          {activeTab === 'notifications' && (
            <Card className="p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-6">Notification Preferences</h2>
              <div className="space-y-4">
                {Object.entries({
                  email: 'Email notifications',
                  push: 'Push notifications',
                  dashboard: 'Dashboard alerts',
                  security: 'Security alerts',
                  updates: 'Product updates',
                }).map(([key, label]) => (
                  <div key={key} className="flex items-center justify-between py-2">
                    <span className="text-gray-700">{label}</span>
                    <button
                      onClick={() => setNotifications({ ...notifications, [key]: !notifications[key as keyof typeof notifications] })}
                      className={cn(
                        'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
                        notifications[key as keyof typeof notifications] ? 'bg-blue-600' : 'bg-gray-200'
                      )}
                    >
                      <span
                        className={cn(
                          'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
                          notifications[key as keyof typeof notifications] ? 'translate-x-6' : 'translate-x-1'
                        )}
                      />
                    </button>
                  </div>
                ))}
              </div>
            </Card>
          )}

          {activeTab === 'preferences' && (
            <Card className="p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-6">Preferences</h2>
              <div className="space-y-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Timezone</label>
                  <select
                    value={profileData.timezone}
                    onChange={(e) => setProfileData({ ...profileData, timezone: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  >
                    <option value="UTC">UTC</option>
                    <option value="America/New_York">Eastern Time</option>
                    <option value="America/Los_Angeles">Pacific Time</option>
                    <option value="Europe/London">London</option>
                    <option value="Asia/Tokyo">Tokyo</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">Language</label>
                  <select
                    value={profileData.language}
                    onChange={(e) => setProfileData({ ...profileData, language: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-md"
                  >
                    <option value="en">English</option>
                    <option value="es">Spanish</option>
                    <option value="fr">French</option>
                    <option value="de">German</option>
                  </select>
                </div>
              </div>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
};

export default ProfilePage;
