import { useEffect, useState } from 'react';
import { parseApiError } from '../utils/apiError';
import { Role, User, UserStatus } from '../types/user';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

export interface UserFormValues {
  firstName: string;
  lastName: string;
  email: string;
  mobile: string;
  password: string;
  role: Role;
  status: UserStatus;
}

interface Props {
  show: boolean;
  mode: 'add' | 'edit';
  initialUser?: User | null;
  onClose: () => void;
  onSubmit: (values: UserFormValues) => Promise<void>;
}

const EMPTY_FORM: UserFormValues = {
  firstName: '',
  lastName: '',
  email: '',
  mobile: '',
  password: '',
  role: 'STAFF',
  status: 'ACTIVE',
};

export default function UserFormModal({ show, mode, initialUser, onClose, onSubmit }: Props) {
  const [form, setForm] = useState<UserFormValues>(EMPTY_FORM);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (show) {
      setError('');
      setFieldErrors({});
      if (mode === 'edit' && initialUser) {
        setForm({
          firstName: initialUser.firstName,
          lastName: initialUser.lastName,
          email: initialUser.email,
          mobile: initialUser.mobile,
          password: '',
          role: initialUser.role,
          status: initialUser.status,
        });
      } else {
        setForm(EMPTY_FORM);
      }
    }
  }, [show, mode, initialUser]);

  const setField = (field: keyof UserFormValues) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      await onSubmit(form);
    } catch (err: any) {
      const parsed = parseApiError(err, 'Something went wrong. Please try again.');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{mode === 'add' ? 'Add User' : 'Edit User'}</DialogTitle>
          <DialogDescription>
            {mode === 'add' ? 'Create a new user account for your store.' : 'Update this user’s details.'}
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="ufFirstName">First Name</Label>
              <Input id="ufFirstName" required value={form.firstName} onChange={setField('firstName')} invalid={!!fieldErrors.firstName} />
              {fieldErrors.firstName && <p className="text-xs text-destructive">{fieldErrors.firstName}</p>}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ufLastName">Last Name</Label>
              <Input id="ufLastName" required value={form.lastName} onChange={setField('lastName')} invalid={!!fieldErrors.lastName} />
              {fieldErrors.lastName && <p className="text-xs text-destructive">{fieldErrors.lastName}</p>}
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="ufEmail">Email</Label>
            <Input id="ufEmail" type="email" required value={form.email} onChange={setField('email')} invalid={!!fieldErrors.email} />
            {fieldErrors.email && <p className="text-xs text-destructive">{fieldErrors.email}</p>}
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="ufMobile">Mobile</Label>
            <Input id="ufMobile" required value={form.mobile} onChange={setField('mobile')} invalid={!!fieldErrors.mobile} />
            {fieldErrors.mobile && <p className="text-xs text-destructive">{fieldErrors.mobile}</p>}
          </div>

          {mode === 'add' && (
            <div className="space-y-1.5">
              <Label htmlFor="ufPassword">Password</Label>
              <Input
                id="ufPassword"
                type="password"
                required
                minLength={6}
                value={form.password}
                onChange={setField('password')}
                invalid={!!fieldErrors.password}
              />
              {fieldErrors.password && <p className="text-xs text-destructive">{fieldErrors.password}</p>}
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="ufRole">Role</Label>
              <Select value={form.role} onValueChange={(v) => setForm((p) => ({ ...p, role: v as Role }))}>
                <SelectTrigger id="ufRole">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ADMIN">Admin</SelectItem>
                  <SelectItem value="STORE_MANAGER">Store Manager</SelectItem>
                  <SelectItem value="STAFF">Staff</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="ufStatus">Status</Label>
              <Select value={form.status} onValueChange={(v) => setForm((p) => ({ ...p, status: v as UserStatus }))}>
                <SelectTrigger id="ufStatus">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ACTIVE">Active</SelectItem>
                  <SelectItem value="INACTIVE">Inactive</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" loading={submitting}>
              {mode === 'add' ? 'Create User' : 'Save Changes'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
