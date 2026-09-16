import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { businessGstConfigApi, stateApi } from '../../api/mastersApi';
import { parseApiError } from '../../utils/apiError';
import { BusinessGstConfigPayload, StateMaster } from '../../types/masters';
import { useAuth } from '@/context/AuthContext';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const EMPTY: BusinessGstConfigPayload = {
  legalName: '',
  tradeName: '',
  gstin: '',
  pan: '',
  address: '',
  stateId: '',
  pincode: '',
};

export default function BusinessGstConfigMaster() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const [states, setStates] = useState<StateMaster[]>([]);
  const [values, setValues] = useState<BusinessGstConfigPayload>(EMPTY);
  const [stateCode, setStateCode] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    stateApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setStates(res.data.content));
    businessGstConfigApi
      .get()
      .then((res) => {
        const c = res.data;
        setValues({
          legalName: c.legalName || '',
          tradeName: c.tradeName || '',
          gstin: c.gstin || '',
          pan: c.pan || '',
          address: c.address || '',
          stateId: c.stateId ?? '',
          pincode: c.pincode || '',
        });
        setStateCode(c.stateCode);
      })
      .finally(() => setLoading(false));
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setFieldErrors({});

    if (!values.legalName.trim()) {
      setError('Legal name is required');
      return;
    }

    setSaving(true);
    try {
      const res = await businessGstConfigApi.update({
        ...values,
        tradeName: values.tradeName || undefined,
        gstin: values.gstin || undefined,
        pan: values.pan || undefined,
        address: values.address || undefined,
        stateId: values.stateId || undefined,
        pincode: values.pincode || undefined,
      });
      setStateCode(res.data.stateCode);
      toast.success('Business GST configuration saved');
    } catch (err) {
      const parsed = parseApiError(err, 'Failed to save business GST configuration');
      setError(parsed.message);
      setFieldErrors(parsed.fieldErrors);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-96 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Business GST Configuration"
        description="The seller's own GST registration details, used as the Place-of-Supply anchor for Sales and Purchase tax-mode suggestions."
      />

      {!isAdmin && (
        <Alert>
          <AlertDescription>Only an administrator can edit this configuration. You can view it below.</AlertDescription>
        </Alert>
      )}

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <form onSubmit={handleSubmit} className="space-y-6" noValidate>
        <Card>
          <CardHeader>
            <CardTitle>Registration Details</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="legalName">Legal Name</Label>
              <Input
                id="legalName"
                value={values.legalName}
                onChange={(e) => setValues((p) => ({ ...p, legalName: e.target.value }))}
                invalid={!!fieldErrors.legalName}
                disabled={!isAdmin}
              />
              {fieldErrors.legalName && <p className="text-xs text-destructive">{fieldErrors.legalName}</p>}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="tradeName">Trade Name</Label>
              <Input
                id="tradeName"
                value={values.tradeName}
                onChange={(e) => setValues((p) => ({ ...p, tradeName: e.target.value }))}
                disabled={!isAdmin}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="gstin">GSTIN</Label>
              <Input
                id="gstin"
                value={values.gstin}
                onChange={(e) => setValues((p) => ({ ...p, gstin: e.target.value.toUpperCase() }))}
                invalid={!!fieldErrors.gstin}
                disabled={!isAdmin}
                placeholder="e.g. 27AAAAA0000A1Z5"
                maxLength={15}
              />
              {fieldErrors.gstin && <p className="text-xs text-destructive">{fieldErrors.gstin}</p>}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="pan">PAN</Label>
              <Input
                id="pan"
                value={values.pan}
                onChange={(e) => setValues((p) => ({ ...p, pan: e.target.value.toUpperCase() }))}
                disabled={!isAdmin}
                maxLength={10}
              />
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
              <Textarea
                id="address"
                value={values.address}
                onChange={(e) => setValues((p) => ({ ...p, address: e.target.value }))}
                disabled={!isAdmin}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="state">State</Label>
              <Select
                value={values.stateId ? String(values.stateId) : undefined}
                onValueChange={(v) => v && setValues((p) => ({ ...p, stateId: Number(v) }))}
                disabled={!isAdmin}
              >
                <SelectTrigger id="state">
                  <SelectValue placeholder="None" />
                </SelectTrigger>
                <SelectContent>
                  {states.map((s) => (
                    <SelectItem key={s.id} value={String(s.id)}>
                      {s.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="stateCode">State Code</Label>
              <Input id="stateCode" value={stateCode || '—'} disabled />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="pincode">Pincode</Label>
              <Input
                id="pincode"
                value={values.pincode}
                onChange={(e) => setValues((p) => ({ ...p, pincode: e.target.value }))}
                disabled={!isAdmin}
              />
            </div>
          </CardContent>
        </Card>

        {isAdmin && (
          <div className="flex justify-end">
            <Button type="submit" loading={saving}>
              Save Configuration
            </Button>
          </div>
        )}
      </form>
    </div>
  );
}
