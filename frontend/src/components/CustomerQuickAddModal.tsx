import { useEffect, useState } from 'react';
import { customerApi } from '../api/customerApi';
import { parseApiError } from '../utils/apiError';
import { Customer } from '../types/sale';
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

interface Props {
  show: boolean;
  onClose: () => void;
  onCreated: (customer: Customer) => void;
}

export default function CustomerQuickAddModal({ show, onClose, onCreated }: Props) {
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [mobile, setMobile] = useState('');
  const [email, setEmail] = useState('');
  const [state, setState] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (show) {
      setFirstName('');
      setLastName('');
      setMobile('');
      setEmail('');
      setState('');
      setError('');
    }
  }, [show]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const res = await customerApi.create({ firstName, lastName, mobile, email, state: state || undefined });
      onCreated(res.data);
    } catch (err) {
      setError(parseApiError(err, 'Failed to add customer').message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Add Customer</DialogTitle>
          <DialogDescription>Register a new customer for this sale.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="cqFirstName">First Name</Label>
              <Input id="cqFirstName" required value={firstName} onChange={(e) => setFirstName(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="cqLastName">Last Name</Label>
              <Input id="cqLastName" value={lastName} onChange={(e) => setLastName(e.target.value)} />
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="cqMobile">Mobile</Label>
            <Input id="cqMobile" required value={mobile} onChange={(e) => setMobile(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="cqEmail">Email</Label>
            <Input id="cqEmail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="cqState">State (optional)</Label>
            <Input
              id="cqState"
              value={state}
              onChange={(e) => setState(e.target.value)}
              placeholder="For GST Intra/Inter-State suggestion"
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" loading={submitting}>
              Add Customer
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
