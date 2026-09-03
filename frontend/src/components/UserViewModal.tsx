import { User } from '../types/user';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';

interface Props {
  show: boolean;
  user: User | null;
  onClose: () => void;
}

function formatDate(value: string) {
  return new Date(value).toLocaleString();
}

function formatRole(role: string) {
  return role
    .split('_')
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(' ');
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between border-b border-border py-2.5 last:border-0">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="text-sm font-medium">{children}</span>
    </div>
  );
}

export default function UserViewModal({ show, user, onClose }: Props) {
  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>User Details</DialogTitle>
        </DialogHeader>

        {user && (
          <div>
            <div className="mb-4 flex items-center gap-3">
              <Avatar className="h-12 w-12">
                <AvatarFallback className="bg-primary text-primary-foreground">
                  {(user.firstName[0] ?? '') + (user.lastName[0] ?? '')}
                </AvatarFallback>
              </Avatar>
              <div>
                <p className="font-semibold">
                  {user.firstName} {user.lastName}
                </p>
                <p className="text-sm text-muted-foreground">{user.email}</p>
              </div>
            </div>

            <div>
              <Row label="Mobile">{user.mobile}</Row>
              <Row label="Role">
                <Badge variant="secondary">{formatRole(user.role)}</Badge>
              </Row>
              <Row label="Status">
                <Badge variant={user.status === 'ACTIVE' ? 'success' : 'muted'}>{user.status}</Badge>
              </Row>
              <Row label="Created At">{formatDate(user.createdAt)}</Row>
              <Row label="Updated At">{formatDate(user.updatedAt)}</Row>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
