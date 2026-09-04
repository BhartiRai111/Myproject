import { useEffect, useState } from 'react';
import { Handshake, Plus, Trash2 } from 'lucide-react';
import { cityApi, countryApi, partyApi, stateApi } from '../../api/mastersApi';
import { categoryApi } from '../../api/categoryApi';
import { Category } from '../../types/product';
import { City, Country, Party, PartyAddress, PartyPayload, StateMaster as StateEntity } from '../../types/masters';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const EMPTY: PartyPayload = {
  partyCode: '',
  partyName: '',
  contactPerson: '',
  mobile: '',
  email: '',
  partyType: 'SUPPLIER',
  gstNumber: '',
  panNumber: '',
  address: '',
  cityId: '',
  stateId: '',
  countryId: '',
  pincode: '',
  notes: '',
  dealsInCategoryIds: [],
  addresses: [],
};

const emptyAddress = (): PartyAddress => ({ label: '', addressLine: '', cityId: undefined, stateId: undefined, pincode: '' });

export default function PartyMaster() {
  const [countries, setCountries] = useState<Country[]>([]);
  const [states, setStates] = useState<StateEntity[]>([]);
  const [cities, setCities] = useState<City[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);

  useEffect(() => {
    countryApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setCountries(res.data.content));
    stateApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setStates(res.data.content));
    cityApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setCities(res.data.content));
    categoryApi.list().then((res) => setCategories(res.data));
  }, []);

  return (
    <MasterCrudPage<Party, PartyPayload>
      icon={Handshake}
      title="Party"
      description="Manage business parties that can act as suppliers, customers, or both."
      itemLabel="parties"
      searchPlaceholder="Search by name, code or mobile"
      columns={[
        { header: 'Code', render: (p) => p.partyCode },
        { header: 'Name', render: (p) => <span className="font-medium">{p.partyName}</span> },
        { header: 'Mobile', render: (p) => p.mobile },
        { header: 'Type', render: (p) => <Badge variant="secondary">{p.partyType}</Badge> },
      ]}
      emptyValues={EMPTY}
      toFormValues={(p) => ({
        partyCode: p.partyCode,
        partyName: p.partyName,
        contactPerson: p.contactPerson || '',
        mobile: p.mobile,
        email: p.email || '',
        partyType: p.partyType,
        gstNumber: p.gstNumber || '',
        panNumber: p.panNumber || '',
        address: p.address || '',
        cityId: p.cityId || '',
        stateId: p.stateId || '',
        countryId: p.countryId || '',
        pincode: p.pincode || '',
        notes: p.notes || '',
        dealsInCategoryIds: [...p.dealsInCategoryIds],
        addresses: p.addresses.map((a) => ({ ...a })),
      })}
      renderView={(p) => (
        <div className="space-y-2 text-sm">
          <div className="grid grid-cols-2 gap-2">
            <div>
              <span className="text-muted-foreground">Code:</span> {p.partyCode}
            </div>
            <div>
              <span className="text-muted-foreground">Name:</span> {p.partyName}
            </div>
            <div>
              <span className="text-muted-foreground">Contact Person:</span> {p.contactPerson || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">Mobile:</span> {p.mobile}
            </div>
            <div>
              <span className="text-muted-foreground">Email:</span> {p.email || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">Type:</span> {p.partyType}
            </div>
            <div>
              <span className="text-muted-foreground">GST:</span> {p.gstNumber || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">PAN:</span> {p.panNumber || '-'}
            </div>
          </div>
          <div>
            <span className="text-muted-foreground">Address:</span> {p.address || '-'} {p.cityName} {p.stateName} {p.pincode}
          </div>
          <div>
            <span className="text-muted-foreground">Deals in:</span>{' '}
            {p.dealsInCategoryNames.length > 0 ? p.dealsInCategoryNames.join(', ') : '-'}
          </div>
          {p.addresses.length > 0 && (
            <div>
              <span className="text-muted-foreground">Additional Addresses:</span>
              <ul className="ml-4 list-disc">
                {p.addresses.map((a, i) => (
                  <li key={a.id ?? i}>
                    {a.label}: {a.addressLine} {a.cityName} {a.stateName} {a.pincode}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
      renderForm={(values, setValues, fieldErrors) => (
        <Tabs defaultValue="basic" className="w-full">
          <TabsList>
            <TabsTrigger value="basic">Basic Info</TabsTrigger>
            <TabsTrigger value="location">Location &amp; Tax</TabsTrigger>
            <TabsTrigger value="more">Categories &amp; Addresses</TabsTrigger>
          </TabsList>

          <TabsContent value="basic" className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="pCode">Party Code</Label>
                <Input
                  id="pCode"
                  required
                  value={values.partyCode}
                  onChange={(e) => setValues((p) => ({ ...p, partyCode: e.target.value }))}
                  invalid={!!fieldErrors.partyCode}
                />
                {fieldErrors.partyCode && <p className="text-xs text-destructive">{fieldErrors.partyCode}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="pName">Party Name</Label>
                <Input
                  id="pName"
                  required
                  value={values.partyName}
                  onChange={(e) => setValues((p) => ({ ...p, partyName: e.target.value }))}
                  invalid={!!fieldErrors.partyName}
                />
                {fieldErrors.partyName && <p className="text-xs text-destructive">{fieldErrors.partyName}</p>}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="pContact">Contact Person</Label>
                <Input
                  id="pContact"
                  value={values.contactPerson}
                  onChange={(e) => setValues((p) => ({ ...p, contactPerson: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="pType">Party Type</Label>
                <Select
                  value={values.partyType}
                  onValueChange={(v) => v && setValues((p) => ({ ...p, partyType: v as PartyPayload['partyType'] }))}
                >
                  <SelectTrigger id="pType">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="SUPPLIER">Supplier</SelectItem>
                    <SelectItem value="CUSTOMER">Customer</SelectItem>
                    <SelectItem value="BOTH">Both</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="pMobile">Mobile</Label>
                <Input
                  id="pMobile"
                  required
                  value={values.mobile}
                  onChange={(e) => setValues((p) => ({ ...p, mobile: e.target.value }))}
                  invalid={!!fieldErrors.mobile}
                />
                {fieldErrors.mobile && <p className="text-xs text-destructive">{fieldErrors.mobile}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="pEmail">Email</Label>
                <Input
                  id="pEmail"
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
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="pGst">GST Number</Label>
                <Input
                  id="pGst"
                  value={values.gstNumber}
                  onChange={(e) => setValues((p) => ({ ...p, gstNumber: e.target.value }))}
                  invalid={!!fieldErrors.gstNumber}
                />
                {fieldErrors.gstNumber && <p className="text-xs text-destructive">{fieldErrors.gstNumber}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="pPan">PAN Number</Label>
                <Input
                  id="pPan"
                  value={values.panNumber}
                  onChange={(e) => setValues((p) => ({ ...p, panNumber: e.target.value }))}
                />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="pAddress">Address</Label>
              <Textarea
                id="pAddress"
                value={values.address}
                onChange={(e) => setValues((p) => ({ ...p, address: e.target.value }))}
              />
            </div>
            <div className="grid grid-cols-3 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="pCountry">Country</Label>
                <Select
                  value={values.countryId ? String(values.countryId) : undefined}
                  onValueChange={(v) => v && setValues((p) => ({ ...p, countryId: Number(v) }))}
                >
                  <SelectTrigger id="pCountry">
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
                <Label htmlFor="pState">State</Label>
                <Select
                  value={values.stateId ? String(values.stateId) : undefined}
                  onValueChange={(v) => v && setValues((p) => ({ ...p, stateId: Number(v) }))}
                >
                  <SelectTrigger id="pState">
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
                <Label htmlFor="pCity">City</Label>
                <Select
                  value={values.cityId ? String(values.cityId) : undefined}
                  onValueChange={(v) => v && setValues((p) => ({ ...p, cityId: Number(v) }))}
                >
                  <SelectTrigger id="pCity">
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
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="pPincode">Pincode</Label>
              <Input
                id="pPincode"
                value={values.pincode}
                onChange={(e) => setValues((p) => ({ ...p, pincode: e.target.value }))}
              />
            </div>
          </TabsContent>

          <TabsContent value="more" className="space-y-4">
            <div className="space-y-1.5">
              <Label>Deals in Categories</Label>
              <div className="grid max-h-40 grid-cols-2 gap-1.5 overflow-y-auto rounded-md border border-border p-2">
                {categories.map((c) => (
                  <label key={c.id} className="flex items-center gap-2 text-sm">
                    <input
                      type="checkbox"
                      className="h-4 w-4 rounded border-input accent-primary"
                      checked={values.dealsInCategoryIds.includes(c.id)}
                      onChange={(e) =>
                        setValues((p) => ({
                          ...p,
                          dealsInCategoryIds: e.target.checked
                            ? [...p.dealsInCategoryIds, c.id]
                            : p.dealsInCategoryIds.filter((id) => id !== c.id),
                        }))
                      }
                    />
                    {c.name}
                  </label>
                ))}
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>Additional Addresses</Label>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setValues((p) => ({ ...p, addresses: [...p.addresses, emptyAddress()] }))}
                >
                  <Plus className="h-3.5 w-3.5" /> Add Address
                </Button>
              </div>
              {values.addresses.length > 0 && (
                <div className="space-y-2 rounded-md border border-border p-3">
                  {values.addresses.map((addr, idx) => (
                    <div key={idx} className="grid grid-cols-5 items-end gap-2 border-b border-border pb-2 last:border-0 last:pb-0">
                      <div className="space-y-1">
                        <Label className="text-xs">Label</Label>
                        <Input
                          value={addr.label}
                          onChange={(e) =>
                            setValues((p) => ({
                              ...p,
                              addresses: p.addresses.map((a, i) => (i === idx ? { ...a, label: e.target.value } : a)),
                            }))
                          }
                        />
                      </div>
                      <div className="col-span-2 space-y-1">
                        <Label className="text-xs">Address Line</Label>
                        <Input
                          value={addr.addressLine}
                          onChange={(e) =>
                            setValues((p) => ({
                              ...p,
                              addresses: p.addresses.map((a, i) => (i === idx ? { ...a, addressLine: e.target.value } : a)),
                            }))
                          }
                        />
                      </div>
                      <div className="space-y-1">
                        <Label className="text-xs">Pincode</Label>
                        <Input
                          value={addr.pincode || ''}
                          onChange={(e) =>
                            setValues((p) => ({
                              ...p,
                              addresses: p.addresses.map((a, i) => (i === idx ? { ...a, pincode: e.target.value } : a)),
                            }))
                          }
                        />
                      </div>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="h-9 w-9 text-destructive"
                        onClick={() => setValues((p) => ({ ...p, addresses: p.addresses.filter((_, i) => i !== idx) }))}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </TabsContent>
        </Tabs>
      )}
      fetchList={({ search, status, page, size }) => partyApi.list({ search, status: status as any, page, size })}
      create={partyApi.create}
      update={partyApi.update}
      activate={partyApi.activate}
      deactivate={partyApi.deactivate}
    />
  );
}
