import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { supplierApi } from '../api/supplierApi';
import { parseApiError } from '../utils/apiError';
import { Supplier } from '../types/supplier';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

export default function SupplierForm() {
  const { id } = useParams();
  const isEdit = !!id;
  const navigate = useNavigate();

  const [loading, setLoading] = useState(isEdit);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const [name, setName] = useState('');
  const [contactPerson, setContactPerson] = useState('');
  const [mobile, setMobile] = useState('');
  const [email, setEmail] = useState('');
  const [address, setAddress] = useState('');
  const [city, setCity] = useState('');
  const [state, setState] = useState('');
  const [pincode, setPincode] = useState('');
  const [gstNumber, setGstNumber] = useState('');
  const [notes, setNotes] = useState('');

  useEffect(() => {
    const loadSupplier = async () => {
      if (!isEdit) return;
      const res = await supplierApi.getById(Number(id));
      const supplier: Supplier = res.data;
      setName(supplier.name);
      setContactPerson(supplier.contactPerson || '');
      setMobile(supplier.mobile);
      setEmail(supplier.email || '');
      setAddress(supplier.address || '');
      setCity(supplier.city || '');
      setState(supplier.state || '');
      setPincode(supplier.pincode || '');
      setGstNumber(supplier.gstNumber || '');
      setNotes(supplier.notes || '');
    };

    loadSupplier().finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const validate = (): string | null => {
    if (!name.trim()) return 'Supplier name is required';
    if (!mobile.trim()) return 'Mobile number is required';
    if (!/^[0-9]{10}$/.test(mobile.trim())) return 'Mobile number must be exactly 10 digits';
    if (pincode.trim() && !/^[0-9]{6}$/.test(pincode.trim())) return 'Pincode must be exactly 6 digits';
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});

    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }

    const payload = {
      name,
      contactPerson: contactPerson || undefined,
      mobile,
      email: email || undefined,
      address: address || undefined,
      city: city || undefined,
      state: state || undefined,
      pincode: pincode || undefined,
      gstNumber: gstNumber || undefined,
      notes: notes || undefined,
    };

    setSubmitting(true);
    try {
      if (isEdit) {
        await supplierApi.update(Number(id), payload);
        toast.success('Supplier updated successfully');
      } else {
        await supplierApi.create(payload);
        toast.success('Supplier created successfully');
      }
      navigate('/suppliers');
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to save supplier');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={isEdit ? 'Edit Supplier' : 'Add Supplier'}
        description="Manage supplier contact and business details."
      />

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <form onSubmit={handleSubmit} className="space-y-6" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>Basic Information</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="name">Supplier Name</Label>
              <Input id="name" value={name} onChange={(e) => setName(e.target.value)} invalid={!!fieldErrors.name} />
              {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="contactPerson">Contact Person</Label>
              <Input
                id="contactPerson"
                value={contactPerson}
                onChange={(e) => setContactPerson(e.target.value)}
                invalid={!!fieldErrors.contactPerson}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="mobile">Mobile</Label>
              <Input
                id="mobile"
                value={mobile}
                onChange={(e) => setMobile(e.target.value)}
                invalid={!!fieldErrors.mobile}
              />
              {fieldErrors.mobile && <p className="text-xs text-destructive">{fieldErrors.mobile}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                invalid={!!fieldErrors.email}
              />
              {fieldErrors.email && <p className="text-xs text-destructive">{fieldErrors.email}</p>}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Address</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div className="space-y-1.5 sm:col-span-2 lg:col-span-3">
              <Label htmlFor="address">Address</Label>
              <Input id="address" value={address} onChange={(e) => setAddress(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="city">City</Label>
              <Input id="city" value={city} onChange={(e) => setCity(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="state">State</Label>
              <Input id="state" value={state} onChange={(e) => setState(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="pincode">Pincode</Label>
              <Input
                id="pincode"
                value={pincode}
                onChange={(e) => setPincode(e.target.value)}
                invalid={!!fieldErrors.pincode}
              />
              {fieldErrors.pincode && <p className="text-xs text-destructive">{fieldErrors.pincode}</p>}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Business</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="gstNumber">GST Number</Label>
              <Input
                id="gstNumber"
                value={gstNumber}
                onChange={(e) => setGstNumber(e.target.value)}
                invalid={!!fieldErrors.gstNumber}
                placeholder="e.g. 22AAAAA0000A1Z5"
              />
              {fieldErrors.gstNumber && <p className="text-xs text-destructive">{fieldErrors.gstNumber}</p>}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Additional</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-1.5">
              <Label htmlFor="notes">Notes</Label>
              <Textarea id="notes" rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </div>
          </CardContent>
        </Card>

        <div className="flex gap-2">
          <Button type="submit" loading={submitting}>
            {isEdit ? 'Save Changes' : 'Create Supplier'}
          </Button>
          <Button type="button" variant="outline" onClick={() => navigate('/suppliers')}>
            Cancel
          </Button>
        </div>
      </form>
    </div>
  );
}
