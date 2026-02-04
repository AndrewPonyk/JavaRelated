import React, { useState } from 'react';
import { Copy, Check, Link, Mail, Users } from 'lucide-react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Widget } from '@/types/dashboard';
import { useToastHelpers } from '@/components/ui/Toaster';

interface ShareWidgetDialogProps {
  widget: Widget;
  dashboardId?: string;
  isOpen: boolean;
  onClose: () => void;
}

export const ShareWidgetDialog: React.FC<ShareWidgetDialogProps> = ({
  widget,
  dashboardId,
  isOpen,
  onClose,
}) => {
  const [copied, setCopied] = useState(false);
  const [email, setEmail] = useState('');
  const { success: showSuccess } = useToastHelpers();

  const shareUrl = `${window.location.origin}/dashboard/${dashboardId || 'shared'}?widget=${widget.id}`;

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(shareUrl);
      setCopied(true);
      showSuccess('Copied', 'Link copied to clipboard');
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  const handleShareEmail = () => {
    const subject = encodeURIComponent(`Check out this widget: ${widget.title}`);
    const body = encodeURIComponent(`I wanted to share this widget with you:\n\n${widget.title}\n${widget.description || ''}\n\nView it here: ${shareUrl}`);
    window.open(`mailto:${email}?subject=${subject}&body=${body}`);
    showSuccess('Email opened', 'Share email opened in your mail client');
    setEmail('');
  };

  return (
    <Dialog open={isOpen} onOpenChange={onClose}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Users className="h-5 w-5" />
            Share "{widget.title}"
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-6 py-4">
          {/* Copy Link Section */}
          <div className="space-y-2">
            <Label>Share Link</Label>
            <div className="flex gap-2">
              <Input
                value={shareUrl}
                readOnly
                className="flex-1 bg-gray-50 text-sm"
              />
              <Button onClick={handleCopyLink} variant="outline">
                {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
              </Button>
            </div>
          </div>

          {/* Share via Email */}
          <div className="space-y-2">
            <Label>Share via Email</Label>
            <div className="flex gap-2">
              <Input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="colleague@company.com"
                className="flex-1"
              />
              <Button onClick={handleShareEmail} variant="outline" disabled={!email}>
                <Mail className="h-4 w-4" />
              </Button>
            </div>
          </div>

          {/* Quick Actions */}
          <div className="space-y-2">
            <Label>Quick Share</Label>
            <div className="flex gap-2">
              <Button
                variant="outline"
                className="flex-1"
                onClick={() => {
                  const url = `https://twitter.com/intent/tweet?text=${encodeURIComponent(`Check out this widget: ${widget.title}`)}&url=${encodeURIComponent(shareUrl)}`;
                  window.open(url, '_blank');
                }}
              >
                Twitter
              </Button>
              <Button
                variant="outline"
                className="flex-1"
                onClick={() => {
                  const url = `https://www.linkedin.com/sharing/share-offsite/?url=${encodeURIComponent(shareUrl)}`;
                  window.open(url, '_blank');
                }}
              >
                LinkedIn
              </Button>
            </div>
          </div>
        </div>

        <div className="flex justify-end">
          <Button variant="outline" onClick={onClose}>Close</Button>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default ShareWidgetDialog;
