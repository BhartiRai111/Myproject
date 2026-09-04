import { useEffect, useState } from 'react';
import { MapPin } from 'lucide-react';
import { countryApi, stateApi } from '../../api/mastersApi';
import { Country, StateMaster as StateEntity, StatePayload } from '../../types/masters';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const EMPTY: StatePayload = { name: '', code: '', countryId: 0 };
const ALL = '__all__';

export default function StateMaster() {
  const [countries, setCountries] = useState<Country[]>([]);
  const [countryFilter, setCountryFilter] = useState<number | ''>('');

  useEffect(() => {
    countryApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setCountries(res.data.content));
  }, []);

  return (
    <MasterCrudPage<StateEntity, StatePayload>
      icon={MapPin}
      title="State"
      description="Manage states, linked to their country."
      itemLabel="states"
      searchPlaceholder="Search by name or code"
      columns={[
        { header: 'Name', render: (s) => <span className="font-medium">{s.name}</span> },
        { header: 'Code', render: (s) => s.code || '-' },
        { header: 'Country', render: (s) => s.countryName },
      ]}
      emptyValues={EMPTY}
      toFormValues={(s) => ({ name: s.name, code: s.code || '', countryId: s.countryId })}
      extraFilterSlot={
        <select
          value={countryFilter || ALL}
          onChange={(e) => setCountryFilter(e.target.value === ALL ? '' : Number(e.target.value))}
          className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm sm:w-48"
        >
          <option value={ALL}>All Countries</option>
          {countries.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      }
      reloadToken={countryFilter}
      renderForm={(values, setValues, fieldErrors) => (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="stName">State Name</Label>
            <Input
              id="stName"
              required
              value={values.name}
              onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
              invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="stCode">Code</Label>
            <Input
              id="stCode"
              value={values.code}
              onChange={(e) => setValues((p) => ({ ...p, code: e.target.value }))}
              invalid={!!fieldErrors.code}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="stCountry">Country</Label>
            <Select
              value={values.countryId ? String(values.countryId) : undefined}
              onValueChange={(v) => v && setValues((p) => ({ ...p, countryId: Number(v) }))}
            >
              <SelectTrigger id="stCountry" invalid={!!fieldErrors.countryId}>
                <SelectValue placeholder="Select a country" />
              </SelectTrigger>
              <SelectContent>
                {countries.map((c) => (
                  <SelectItem key={c.id} value={String(c.id)}>
                    {c.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {fieldErrors.countryId && <p className="text-xs text-destructive">{fieldErrors.countryId}</p>}
          </div>
        </>
      )}
      fetchList={({ search, status, page, size }) =>
        stateApi.list({ search, status: status as any, countryId: countryFilter, page, size })
      }
      create={stateApi.create}
      update={stateApi.update}
      activate={stateApi.activate}
      deactivate={stateApi.deactivate}
    />
  );
}
