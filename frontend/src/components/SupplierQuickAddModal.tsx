import { useEffect, useState } from 'react';
import { supplierApi } from '../api/supplierApi';
import { parseApiError } from '../utils/apiError';
import { Supplier } from '../types/purchase';
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
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (show) {
      setName('');
      setPhone('');
      setEmail('');
      setAddress('');
      setError('');
    }
  }, [show]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      const res = await supplierApi.create({ name, phone, email, address });
      onCreated(res.data);
    } catch (err) {
      setError(parseApiError(err, 'Failed to add supplier').message);
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
            <Input id="sqName" required value={name} onChange={(e) => setName(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="sqPhone">Phone</Label>
            <Input id="sqPhone" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="sqEmail">Email</Label>
            <Input id="sqEmail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
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
