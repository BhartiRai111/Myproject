import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { RefreshCw, Store as StoreIcon } from 'lucide-react';
import { storeApi } from '../../api/storeApi';
import { cityApi, countryApi, stateApi, zoneApi } from '../../api/mastersApi';
import { City, Country, StateMaster as StateEntity, Zone } from '../../types/masters';
import { Store, StorePayload, StoreType } from '../../types/store';
import { parseApiError } from '../../utils/apiError';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const STORE_TYPES: { value: StoreType; label: string }[] = [
  { value: 'STORE', label: 'Store' },
  { value: 'WAREHOUSE', label: 'Warehouse' },
  { value: 'HEAD_OFFICE', label: 'Head Office' },
];

const EMPTY: StorePayload = {
  storeCode: '',
  storeName: '',
  storeType: 'STORE',
  legalName: '',
  address: '',
  countryId: '',
  stateId: '',
  cityId: '',
  zoneId: '',
  pincode: '',
  phone: '',
  email: '',
  gstin: '',
};

export default function StoreMaster() {
  const [countries, setCountries] = useState<Country[]>([]);
  const [states, setStates] = useState<StateEntity[]>([]);
  const [cities, setCities] = useState<City[]>([]);
  const [zones, setZones] = useState<Zone[]>([]);
  const [generatingCode, setGeneratingCode] = useState(false);

  useEffect(() => {
    countryApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setCountries(res.data.content));
    stateApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setStates(res.data.content));
    cityApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setCities(res.data.content));
    zoneApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setZones(res.data.content));
  }, []);

  return (
    <MasterCrudPage<Store, StorePayload>
      icon={StoreIcon}
      title="Store"
      description="Manage stores, branches and warehouses — the operational locations Sales, Purchases and Inventory are attributed to."
      itemLabel="stores"
      searchPlaceholder="Search by code or name"
      columns={[
        { header: 'Code', render: (s) => s.storeCode },
        { header: 'Name', render: (s) => <span className="font-medium">{s.storeName}</span> },
        { header: 'Type', render: (s) => STORE_TYPES.find((t) => t.value === s.storeType)?.label || s.storeType },
        { header: 'City', render: (s) => s.cityName || '-' },
        { header: 'GSTIN', render: (s) => s.gstin || <span className="text-muted-foreground">Uses business GSTIN</span> },
      ]}
      emptyValues={EMPTY}
      toFormValues={(s) => ({
        storeCode: s.storeCode,
        storeName: s.storeName,
        storeType: s.storeType,
        legalName: s.legalName || '',
        address: s.address || '',
        countryId: s.countryId || '',
        stateId: s.stateId || '',
        cityId: s.cityId || '',
        zoneId: s.zoneId || '',
        pincode: s.pincode || '',
        phone: s.phone || '',
        email: s.email || '',
        gstin: s.gstin || '',
      })}
      renderView={(s) => (
        <div className="space-y-2 text-sm">
          <div className="grid grid-cols-2 gap-2">
            <div>
              <span className="text-muted-foreground">Code:</span> {s.storeCode}
            </div>
            <div>
              <span className="text-muted-foreground">Name:</span> {s.storeName}
            </div>
            <div>
              <span className="text-muted-foreground">Type:</span>{' '}
              {STORE_TYPES.find((t) => t.value === s.storeType)?.label || s.storeType}
            </div>
            <div>
              <span className="text-muted-foreground">Legal Name:</span> {s.legalName || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">City:</span> {s.cityName || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">State:</span> {s.stateName || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">Zone:</span> {s.zoneName || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">Pincode:</span> {s.pincode || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">Phone:</span> {s.phone || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">Email:</span> {s.email || '-'}
            </div>
          </div>
          <div>
            <span className="text-muted-foreground">Address:</span> {s.address || '-'}
          </div>
          <div className="flex items-center gap-2 pt-1">
            <span className="text-muted-foreground">GSTIN:</span>
            {s.gstin ? <Badge variant="secondary">{s.gstin}</Badge> : <span className="text-muted-foreground">Uses the business-wide GST config</span>}
          </div>
        </div>
      )}
      renderForm={(values, setValues, fieldErrors) => (
        <Tabs defaultValue="basic" className="w-full">
          <TabsList>
            <TabsTrigger value="basic">Basic Info</TabsTrigger>
            <TabsTrigger value="location">Location &amp; GST</TabsTrigger>
          </TabsList>

          <TabsContent value="basic" className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="storeCode">Store Code</Label>
                <div className="flex gap-2">
                  <Input
                    id="storeCode"
                    required
                    value={values.storeCode}
                    onChange={(e) => setValues((p) => ({ ...p, storeCode: e.target.value }))}
                    invalid={!!fieldErrors.storeCode}
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="icon"
                    loading={generatingCode}
                    onClick={async () => {
                      setGeneratingCode(true);
                      try {
                        const res = await storeApi.generateCode();
                        setValues((p) => ({ ...p, storeCode: res.data.storeCode }));
                      } catch (err) {
                        toast.error(parseApiError(err, 'Failed to generate store code').message);
                      } finally {
                        setGeneratingCode(false);
                      }
                    }}
                    title="Generate Code"
                  >
                    <RefreshCw className="h-4 w-4" />
                  </Button>
                </div>
                {fieldErrors.storeCode && <p className="text-xs text-destructive">{fieldErrors.storeCode}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="storeName">Store Name</Label>
                <Input
                  id="storeName"
                  required
                  value={values.storeName}
                  onChange={(e) => setValues((p) => ({ ...p, storeName: e.target.value }))}
                  invalid={!!fieldErrors.storeName}
                />
                {fieldErrors.storeName && <p className="text-xs text-destructive">{fieldErrors.storeName}</p>}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="storeType">Store Type</Label>
                <Select
                  value={values.storeType}
                  onValueChange={(v) => v && setValues((p) => ({ ...p, storeType: v as StoreType }))}
                >
                  <SelectTrigger id="storeType" invalid={!!fieldErrors.storeType}>
                    <SelectValue placeholder="Select a type" />
                  </SelectTrigger>
                  <SelectContent>
                    {STORE_TYPES.map((t) => (
                      <SelectItem key={t.value} value={t.value}>
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {fieldErrors.storeType && <p className="text-xs text-destructive">{fieldErrors.storeType}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="storeLegalName">Legal Name</Label>
                <Input
                  id="storeLegalName"
                  value={values.legalName}
                  onChange={(e) => setValues((p) => ({ ...p, legalName: e.target.value }))}
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="storePhone">Phone</Label>
                <Input
                  id="storePhone"
                  value={values.phone}
                  onChange={(e) => setValues((p) => ({ ...p, phone: e.target.value }))}
                  invalid={!!fieldErrors.phone}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="storeEmail">Email</Label>
                <Input
                  id="storeEmail"
                  type="email"
                  value={values.email}
                  onChange={(e) => setValues((p) => ({ ...p, email: e.target.value }))}
                  invalid={!!fieldErrors.email}
                />
                {fieldErrors.email && <p className="text-xs text-destructive">{fieldErrors.email}</p>}
              </div>
            </div>
          </TabsContent>

          <TabsContent value="location" className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="storeAddress">Address</Label>
              <Textarea
                id="storeAddress"
                value={values.address}
                onChange={(e) => setValues((p) => ({ ...p, address: e.target.value }))}
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="storeCountry">Country</Label>
                <Select
                  value={values.countryId ? String(values.countryId) : undefined}
                  onValueChange={(v) => v && setValues((p) => ({ ...p, countryId: Number(v) }))}
                >
                  <SelectTrigger id="storeCountry">
                    <SelectValue placeholder="None" />
                  </SelectTrigger>
                  <SelectContent>
                    {countries.map((c) => (
                      <SelectItem key={c.id} value={String(c.id)}>
                        {c.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="storeState">State</Label>
                <Select
                  value={values.stateId ? String(values.stateId) : undefined}
                  onValueChange={(v) => v && setValues((p) => ({ ...p, stateId: Number(v) }))}
                >
                  <SelectTrigger id="storeState">
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
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="storeCity">City</Label>
                <Select
                  value={values.cityId ? String(values.cityId) : undefined}
                  onValueChange={(v) => v && setValues((p) => ({ ...p, cityId: Number(v) }))}
                >
                  <SelectTrigger id="storeCity">
                    <SelectValue placeholder="None" />
                  </SelectTrigger>
                  <SelectContent>
                    {cities.map((c) => (
                      <SelectItem key={c.id} value={String(c.id)}>
                        {c.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="storeZone">Zone</Label>
                <Select
                  value={values.zoneId ? String(values.zoneId) : undefined}
                  onValueChange={(v) => v && setValues((p) => ({ ...p, zoneId: Number(v) }))}
                >
                  <SelectTrigger id="storeZone">
                    <SelectValue placeholder="None" />
                  </SelectTrigger>
                  <SelectContent>
                    {zones.map((z) => (
                      <SelectItem key={z.id} value={String(z.id)}>
                        {z.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="storePincode">Pincode</Label>
              <Input
                id="storePincode"
                value={values.pincode}
                onChange={(e) => setValues((p) => ({ ...p, pincode: e.target.value }))}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="storeGstin">GSTIN (optional store override)</Label>
              <Input
                id="storeGstin"
                value={values.gstin}
                onChange={(e) => setValues((p) => ({ ...p, gstin: e.target.value.toUpperCase() }))}
                invalid={!!fieldErrors.gstin}
                placeholder="Leave blank to use the business-wide GSTIN"
              />
              {fieldErrors.gstin && <p className="text-xs text-destructive">{fieldErrors.gstin}</p>}
              <p className="text-xs text-muted-foreground">
                When set, this store's own transactions use this GSTIN as their seller context instead of the
                business-wide GST configuration.
              </p>
            </div>
          </TabsContent>
        </Tabs>
      )}
      fetchList={({ search, status, page, size }) => storeApi.list({ search, status: status as any, page, size })}
      create={storeApi.create}
      update={storeApi.update}
      activate={storeApi.activate}
      deactivate={storeApi.deactivate}
    />
  );
}
