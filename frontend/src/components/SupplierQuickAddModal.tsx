import { useEffect, useState } from 'react';
import { supplierApi } from '../api/supplierApi';
import { parseApiError } from '../utils/apiError';
import { Supplier } from '../types/supplier';
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
  onCreated: (supplier: Supplier) => void;
}

export default function SupplierQuickAddModal({ show, onClose, onCreated }: Props) {
  const [name, setName] = useState('');
  const [mobile, setMobile] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (show) {
      setName('');
      setMobile('');
      setEmail('');
      setAddress('');
      setError('');
      setFieldErrors({});
    }
  }, [show]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    setFieldErrors({});
    try {
      const res = await supplierApi.create({
        name,
        mobile,
        email: email || undefined,
        address: address || undefined,
      });
      onCreated(res.data);
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to add supplier');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={show} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle>Add Supplier</DialogTitle>
          <DialogDescription>Add a new supplier for this purchase.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <div className="space-y-1.5">
            <Label htmlFor="sqName">Name</Label>
            <Input
              id="sqName"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="sqMobile">Mobile</Label>
            <Input
              id="sqMobile"
              required
              value={mobile}
              onChange={(e) => setMobile(e.target.value)}
              invalid={!!fieldErrors.mobile}
            />
            {fieldErrors.mobile && <p className="text-xs text-destructive">{fieldErrors.mobile}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="sqEmail">Email</Label>
            <Input
              id="sqEmail"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              invalid={!!fieldErrors.email}
            />
            {fieldErrors.email && <p className="text-xs text-destructive">{fieldErrors.email}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="sqAddress">Address</Label>
            <Input id="sqAddress" value={address} onChange={(e) => setAddress(e.target.value)} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" loading={submitting}>
              Add Supplier
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
