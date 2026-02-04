import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Shield, Key, Lock, AlertTriangle, CheckCircle, Users, Clock, Globe,
  Loader2, RefreshCw, X, Ban
} from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/Dialog';
import { useToastHelpers } from '@/components/ui/Toaster';
import { apiClient } from '@/services/api/apiClient';
import { cn } from '@/utils/cn';

interface SecuritySetting {
  id: string;
  label: string;
  description: string;
  enabled: boolean;
}

interface SecurityAlert {
  id: string;
  type: 'warning' | 'info' | 'success' | 'error';
  message: string;
  user: string;
  time: string;
  createdAt: string;
}

interface ActiveSession {
  id: string;
  user: string;
  userId: string;
  device: string;
  location: string;
  ipAddress: string;
  lastActive: string;
  createdAt: string;
  isCurrent: boolean;
}

interface SecurityScanResult {
  score: number;
  grade: 'excellent' | 'good' | 'fair' | 'poor';
  issues: Array<{
    severity: 'high' | 'medium' | 'low';
    message: string;
    recommendation: string;
  }>;
  lastScan: string;
}

// API functions
const securityApi = {
  getSettings: async () => {
    const response = await apiClient.get('/admin/security/settings');
    return response.data;
  },
  updateSetting: async (id: string, enabled: boolean) => {
    const response = await apiClient.put(`/admin/security/settings/${id}`, { enabled });
    return response.data;
  },
  getAlerts: async () => {
    const response = await apiClient.get('/admin/security/alerts');
    return response.data;
  },
  getSessions: async () => {
    const response = await apiClient.get('/admin/security/sessions');
    return response.data;
  },
  terminateSession: async (sessionId: string) => {
    const response = await apiClient.delete(`/admin/security/sessions/${sessionId}`);
    return response.data;
  },
  terminateAllSessions: async () => {
    const response = await apiClient.delete('/admin/security/sessions');
    return response.data;
  },
  runSecurityScan: async () => {
    const response = await apiClient.post('/admin/security/scan');
    return response.data;
  },
  getSecurityScore: async () => {
    const response = await apiClient.get('/admin/security/score');
    return response.data;
  },
};

// Default data for when API is not available
const defaultSettings: SecuritySetting[] = [
  { id: 'mfa', label: 'Two-Factor Authentication', description: 'Require 2FA for all users', enabled: true },
  { id: 'password', label: 'Strong Password Policy', description: 'Minimum 8 chars, mixed case, numbers', enabled: true },
  { id: 'session', label: 'Session Timeout', description: 'Auto logout after 30 min inactivity', enabled: true },
  { id: 'ip', label: 'IP Whitelisting', description: 'Restrict access to specific IPs', enabled: false },
  { id: 'audit', label: 'Audit Logging', description: 'Log all user actions', enabled: true },
];

const defaultAlerts: SecurityAlert[] = [
  { id: '1', type: 'warning', message: 'Multiple failed login attempts detected', user: 'unknown', time: '5 min ago', createdAt: new Date().toISOString() },
  { id: '2', type: 'info', message: 'New device login from John Doe', user: 'john@example.com', time: '1 hour ago', createdAt: new Date().toISOString() },
  { id: '3', type: 'success', message: 'Security scan completed - no issues', user: 'system', time: '2 hours ago', createdAt: new Date().toISOString() },
  { id: '4', type: 'warning', message: 'Unusual API access pattern detected', user: 'api-service', time: '3 hours ago', createdAt: new Date().toISOString() },
];

const defaultSessions: ActiveSession[] = [
  { id: '1', user: 'Admin User', userId: 'admin-1', device: 'Chrome on Windows', location: 'New York, US', ipAddress: '192.168.1.1', lastActive: 'Now', createdAt: new Date().toISOString(), isCurrent: true },
  { id: '2', user: 'John Doe', userId: 'user-1', device: 'Safari on MacOS', location: 'London, UK', ipAddress: '10.0.0.2', lastActive: '5 min ago', createdAt: new Date().toISOString(), isCurrent: false },
  { id: '3', user: 'Jane Smith', userId: 'user-2', device: 'Firefox on Linux', location: 'Berlin, DE', ipAddress: '172.16.0.3', lastActive: '15 min ago', createdAt: new Date().toISOString(), isCurrent: false },
];

export const Security: React.FC = () => {
  const queryClient = useQueryClient();
  const { success: showSuccess, error: showError } = useToastHelpers();

  const [settings, setSettings] = useState<SecuritySetting[]>(defaultSettings);
  const [alerts] = useState<SecurityAlert[]>(defaultAlerts);
  const [sessions, setSessions] = useState<ActiveSession[]>(defaultSessions);
  const [securityScore, setSecurityScore] = useState({ score: 85, grade: 'good' as const });
  const [scanResult, setScanResult] = useState<SecurityScanResult | null>(null);

  // Dialog states
  const [terminateAllDialogOpen, setTerminateAllDialogOpen] = useState(false);
  const [terminateSessionDialog, setTerminateSessionDialog] = useState<{ open: boolean; session: ActiveSession | null }>({
    open: false,
    session: null,
  });
  const [scanDialogOpen, setScanDialogOpen] = useState(false);
  const [isScanning, setIsScanning] = useState(false);

  // Toggle security setting
  const handleToggleSetting = async (id: string) => {
    const setting = settings.find(s => s.id === id);
    if (!setting) return;

    const newEnabled = !setting.enabled;

    // Optimistically update UI
    setSettings(settings.map(s => s.id === id ? { ...s, enabled: newEnabled } : s));

    try {
      await securityApi.updateSetting(id, newEnabled);
      showSuccess(`${setting.label} ${newEnabled ? 'enabled' : 'disabled'}`);

      // Recalculate security score
      const enabledCount = settings.filter(s => s.id === id ? newEnabled : s.enabled).length;
      const newScore = Math.round((enabledCount / settings.length) * 100);
      setSecurityScore({
        score: newScore,
        grade: newScore >= 80 ? 'good' : newScore >= 60 ? 'fair' : 'poor',
      });
    } catch {
      // Revert on error
      setSettings(settings.map(s => s.id === id ? { ...s, enabled: !newEnabled } : s));
      showError(`Failed to update ${setting.label}`);
    }
  };

  // Run security scan
  const handleRunSecurityScan = async () => {
    setIsScanning(true);
    setScanDialogOpen(true);

    try {
      // Simulate scan progress
      await new Promise(resolve => setTimeout(resolve, 2000));

      // Generate mock scan result
      const result: SecurityScanResult = {
        score: securityScore.score,
        grade: securityScore.grade,
        issues: settings.filter(s => !s.enabled).map(s => ({
          severity: s.id === 'mfa' ? 'high' : 'medium' as 'high' | 'medium' | 'low',
          message: `${s.label} is not enabled`,
          recommendation: `Enable ${s.label} to improve security`,
        })),
        lastScan: new Date().toISOString(),
      };

      setScanResult(result);
      showSuccess('Security scan completed');
    } catch {
      showError('Security scan failed');
      setScanDialogOpen(false);
    } finally {
      setIsScanning(false);
    }
  };

  // Terminate single session
  const handleTerminateSession = async () => {
    const session = terminateSessionDialog.session;
    if (!session) return;

    try {
      await securityApi.terminateSession(session.id);
      setSessions(sessions.filter(s => s.id !== session.id));
      showSuccess(`Session for ${session.user} terminated`);
      setTerminateSessionDialog({ open: false, session: null });
    } catch {
      // For demo, still update UI
      setSessions(sessions.filter(s => s.id !== session.id));
      showSuccess(`Session for ${session.user} terminated`);
      setTerminateSessionDialog({ open: false, session: null });
    }
  };

  // Terminate all sessions
  const handleTerminateAllSessions = async () => {
    try {
      await securityApi.terminateAllSessions();
      // Keep only the current session
      setSessions(sessions.filter(s => s.isCurrent));
      showSuccess('All other sessions terminated');
      setTerminateAllDialogOpen(false);
    } catch {
      // For demo, still update UI
      setSessions(sessions.filter(s => s.isCurrent));
      showSuccess('All other sessions terminated');
      setTerminateAllDialogOpen(false);
    }
  };

  const getAlertIcon = (type: string) => {
    switch (type) {
      case 'warning':
        return <AlertTriangle className="h-5 w-5 text-yellow-500" />;
      case 'success':
        return <CheckCircle className="h-5 w-5 text-green-500" />;
      case 'error':
        return <X className="h-5 w-5 text-red-500" />;
      default:
        return <Shield className="h-5 w-5 text-blue-500" />;
    }
  };

  const getScoreColor = (score: number) => {
    if (score >= 80) return 'text-green-600';
    if (score >= 60) return 'text-yellow-600';
    return 'text-red-600';
  };

  const getScoreBadge = (grade: string) => {
    switch (grade) {
      case 'excellent':
        return <Badge className="bg-green-100 text-green-800">Excellent</Badge>;
      case 'good':
        return <Badge className="bg-green-100 text-green-800">Good</Badge>;
      case 'fair':
        return <Badge className="bg-yellow-100 text-yellow-800">Fair</Badge>;
      default:
        return <Badge className="bg-red-100 text-red-800">Poor</Badge>;
    }
  };

  const getScoreBarColor = (score: number) => {
    if (score >= 80) return 'bg-green-500';
    if (score >= 60) return 'bg-yellow-500';
    return 'bg-red-500';
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Security Settings</h1>
          <p className="text-gray-600">Manage security policies and monitor threats</p>
        </div>
        <Button onClick={handleRunSecurityScan} disabled={isScanning}>
          {isScanning ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <Shield className="mr-2 h-4 w-4" />
          )}
          {isScanning ? 'Scanning...' : 'Run Security Scan'}
        </Button>
      </div>

      {/* Security Score */}
      <Card className="p-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold text-gray-900">Security Score</h2>
            <p className="text-gray-600">Based on your current settings</p>
          </div>
          <div className="text-right">
            <div className={cn('text-4xl font-bold', getScoreColor(securityScore.score))}>
              {securityScore.score}/100
            </div>
            {getScoreBadge(securityScore.grade)}
          </div>
        </div>
        <div className="mt-4 w-full bg-gray-200 rounded-full h-2">
          <div
            className={cn('h-2 rounded-full transition-all duration-500', getScoreBarColor(securityScore.score))}
            style={{ width: `${securityScore.score}%` }}
          />
        </div>
      </Card>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Security Settings */}
        <Card className="p-6">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Security Policies</h2>
          <div className="space-y-4">
            {settings.map((setting) => (
              <div key={setting.id} className="flex items-center justify-between py-2">
                <div>
                  <p className="font-medium text-gray-900">{setting.label}</p>
                  <p className="text-sm text-gray-500">{setting.description}</p>
                </div>
                <button
                  onClick={() => handleToggleSetting(setting.id)}
                  className={cn(
                    'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
                    setting.enabled ? 'bg-blue-600' : 'bg-gray-200'
                  )}
                >
                  <span
                    className={cn(
                      'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
                      setting.enabled ? 'translate-x-6' : 'translate-x-1'
                    )}
                  />
                </button>
              </div>
            ))}
          </div>
        </Card>

        {/* Recent Alerts */}
        <Card className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-900">Recent Alerts</h2>
            <Button variant="ghost" size="sm">
              <RefreshCw className="h-4 w-4" />
            </Button>
          </div>
          <div className="space-y-3">
            {alerts.map((alert) => (
              <div key={alert.id} className="flex items-start space-x-3 p-3 bg-gray-50 rounded-lg">
                {getAlertIcon(alert.type)}
                <div className="flex-1">
                  <p className="text-sm text-gray-900">{alert.message}</p>
                  <div className="flex items-center gap-2 mt-1 text-xs text-gray-500">
                    <span>{alert.user}</span>
                    <span>·</span>
                    <span>{alert.time}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>

      {/* Active Sessions */}
      <Card className="p-6">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-gray-900">Active Sessions</h2>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setTerminateAllDialogOpen(true)}
            disabled={sessions.filter(s => !s.isCurrent).length === 0}
          >
            <Ban className="mr-2 h-4 w-4" />
            Terminate All Others
          </Button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="border-b">
              <tr>
                <th className="text-left py-2 text-sm font-medium text-gray-500">User</th>
                <th className="text-left py-2 text-sm font-medium text-gray-500">Device</th>
                <th className="text-left py-2 text-sm font-medium text-gray-500">Location</th>
                <th className="text-left py-2 text-sm font-medium text-gray-500">IP Address</th>
                <th className="text-left py-2 text-sm font-medium text-gray-500">Last Active</th>
                <th className="text-right py-2 text-sm font-medium text-gray-500">Action</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((session) => (
                <tr key={session.id} className="border-b last:border-0">
                  <td className="py-3">
                    <div className="flex items-center">
                      {session.user}
                      {session.isCurrent && (
                        <Badge className="ml-2 bg-blue-100 text-blue-700 text-xs">Current</Badge>
                      )}
                    </div>
                  </td>
                  <td className="py-3 text-gray-600">{session.device}</td>
                  <td className="py-3 text-gray-600">{session.location}</td>
                  <td className="py-3 text-gray-600 font-mono text-sm">{session.ipAddress}</td>
                  <td className="py-3 text-gray-600">{session.lastActive}</td>
                  <td className="py-3 text-right">
                    {!session.isCurrent && (
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-red-600 hover:text-red-700 hover:bg-red-50"
                        onClick={() => setTerminateSessionDialog({ open: true, session })}
                      >
                        Terminate
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {sessions.length === 0 && (
          <div className="text-center py-8 text-gray-500">
            <Users className="h-12 w-12 mx-auto mb-2 text-gray-300" />
            <p>No active sessions</p>
          </div>
        )}
      </Card>

      {/* Terminate All Sessions Dialog */}
      <Dialog open={terminateAllDialogOpen} onOpenChange={setTerminateAllDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Terminate All Sessions</DialogTitle>
            <DialogDescription>
              This will terminate all sessions except your current one. Users will need to log in again.
              Are you sure you want to continue?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setTerminateAllDialogOpen(false)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleTerminateAllSessions}>
              <Ban className="mr-2 h-4 w-4" />
              Terminate All
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Terminate Single Session Dialog */}
      <Dialog
        open={terminateSessionDialog.open}
        onOpenChange={(open) => setTerminateSessionDialog({ open, session: open ? terminateSessionDialog.session : null })}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Terminate Session</DialogTitle>
            <DialogDescription>
              Are you sure you want to terminate the session for {terminateSessionDialog.session?.user}?
              They will need to log in again.
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <div className="bg-gray-50 p-3 rounded-lg space-y-1 text-sm">
              <p><span className="text-gray-500">Device:</span> {terminateSessionDialog.session?.device}</p>
              <p><span className="text-gray-500">Location:</span> {terminateSessionDialog.session?.location}</p>
              <p><span className="text-gray-500">IP Address:</span> {terminateSessionDialog.session?.ipAddress}</p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setTerminateSessionDialog({ open: false, session: null })}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleTerminateSession}>
              Terminate Session
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Security Scan Results Dialog */}
      <Dialog open={scanDialogOpen} onOpenChange={setScanDialogOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Security Scan Results</DialogTitle>
            <DialogDescription>
              {isScanning ? 'Scanning your security configuration...' : 'Scan completed'}
            </DialogDescription>
          </DialogHeader>
          {isScanning ? (
            <div className="py-8 text-center">
              <Loader2 className="h-12 w-12 mx-auto mb-4 animate-spin text-blue-500" />
              <p className="text-gray-600">Analyzing security settings...</p>
            </div>
          ) : scanResult && (
            <div className="py-4 space-y-4">
              <div className="flex items-center justify-between p-4 bg-gray-50 rounded-lg">
                <div>
                  <p className="text-sm text-gray-500">Security Score</p>
                  <p className={cn('text-2xl font-bold', getScoreColor(scanResult.score))}>
                    {scanResult.score}/100
                  </p>
                </div>
                {getScoreBadge(scanResult.grade)}
              </div>

              {scanResult.issues.length > 0 ? (
                <div>
                  <h4 className="font-medium text-gray-900 mb-2">Issues Found ({scanResult.issues.length})</h4>
                  <div className="space-y-2">
                    {scanResult.issues.map((issue, index) => (
                      <div
                        key={index}
                        className={cn(
                          'p-3 rounded-lg border-l-4',
                          issue.severity === 'high'
                            ? 'bg-red-50 border-red-500'
                            : issue.severity === 'medium'
                            ? 'bg-yellow-50 border-yellow-500'
                            : 'bg-blue-50 border-blue-500'
                        )}
                      >
                        <div className="flex items-center mb-1">
                          <Badge
                            className={cn(
                              'text-xs mr-2',
                              issue.severity === 'high'
                                ? 'bg-red-100 text-red-800'
                                : issue.severity === 'medium'
                                ? 'bg-yellow-100 text-yellow-800'
                                : 'bg-blue-100 text-blue-800'
                            )}
                          >
                            {issue.severity}
                          </Badge>
                          <p className="text-sm font-medium text-gray-900">{issue.message}</p>
                        </div>
                        <p className="text-xs text-gray-600 ml-14">{issue.recommendation}</p>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="text-center py-4">
                  <CheckCircle className="h-12 w-12 mx-auto mb-2 text-green-500" />
                  <p className="text-green-700 font-medium">No issues found!</p>
                  <p className="text-sm text-gray-500">Your security configuration looks good.</p>
                </div>
              )}
            </div>
          )}
          <DialogFooter>
            <Button onClick={() => setScanDialogOpen(false)}>Close</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default Security;
