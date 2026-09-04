import { useEffect, useState } from 'react';
import { Map } from 'lucide-react';
import { countryApi, stateApi, zoneApi } from '../../api/mastersApi';
import { Country, StateMaster as StateEntity, Zone, ZonePayload } from '../../types/masters';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const EMPTY: ZonePayload = { name: '', code: '', countryId: '', stateId: '' };

export default function ZoneMaster() {
  const [countries, setCountries] = useState<Country[]>([]);
  const [states, setStates] = useState<StateEntity[]>([]);

  useEffect(() => {
    countryApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setCountries(res.data.content));
    stateApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setStates(res.data.content));
  }, []);

  return (
    <MasterCrudPage<Zone, ZonePayload>
      icon={Map}
      title="Zone"
      description="Manage sales/distribution zones, optionally linked to a country or state."
      itemLabel="zones"
      searchPlaceholder="Search by name"
      columns={[
        { header: 'Name', render: (z) => <span className="font-medium">{z.name}</span> },
        { header: 'Code', render: (z) => z.code || '-' },
        { header: 'Country', render: (z) => z.countryName || '-' },
        { header: 'State', render: (z) => z.stateName || '-' },
      ]}
      emptyValues={EMPTY}
      toFormValues={(z) => ({ name: z.name, code: z.code || '', countryId: z.countryId || '', stateId: z.stateId || '' })}
      renderForm={(values, setValues, fieldErrors) => (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="zoneName">Zone Name</Label>
            <Input
              id="zoneName"
              required
              value={values.name}
              onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
              invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="zoneCode">Code</Label>
            <Input
              id="zoneCode"
              value={values.code}
              onChange={(e) => setValues((p) => ({ ...p, code: e.target.value }))}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="zoneCountry">Country (optional)</Label>
              <Select
                value={values.countryId ? String(values.countryId) : undefined}
                onValueChange={(v) => v && setValues((p) => ({ ...p, countryId: Number(v) }))}
              >
                <SelectTrigger id="zoneCountry">
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
              <Label htmlFor="zoneState">State (optional)</Label>
              <Select
                value={values.stateId ? String(values.stateId) : undefined}
                onValueChange={(v) => v && setValues((p) => ({ ...p, stateId: Number(v) }))}
              >
                <SelectTrigger id="zoneState">
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
        </>
      )}
      fetchList={({ search, status, page, size }) => zoneApi.list({ search, status: status as any, page, size })}
      create={zoneApi.create}
      update={zoneApi.update}
      activate={zoneApi.activate}
      deactivate={zoneApi.deactivate}
    />
  );
}
