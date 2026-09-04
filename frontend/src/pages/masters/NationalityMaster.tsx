import { useEffect, useState } from 'react';
import { Flag } from 'lucide-react';
import { countryApi, nationalityApi } from '../../api/mastersApi';
import { Country, Nationality, NationalityPayload } from '../../types/masters';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const EMPTY: NationalityPayload = { name: '', countryId: 0 };
const ALL = '__all__';

export default function NationalityMaster() {
  const [countries, setCountries] = useState<Country[]>([]);
  const [countryFilter, setCountryFilter] = useState<number | ''>('');

  useEffect(() => {
    countryApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setCountries(res.data.content));
  }, []);

  return (
    <MasterCrudPage<Nationality, NationalityPayload>
      icon={Flag}
      title="Nationality"
      description="Manage nationalities, linked to their country."
      itemLabel="nationalities"
      searchPlaceholder="Search by name"
      columns={[
        { header: 'Name', render: (n) => <span className="font-medium">{n.name}</span> },
        { header: 'Country', render: (n) => n.countryName },
      ]}
      emptyValues={EMPTY}
      toFormValues={(n) => ({ name: n.name, countryId: n.countryId })}
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
            <Label htmlFor="natName">Nationality Name</Label>
            <Input
              id="natName"
              required
              value={values.name}
              onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
              invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="natCountry">Country</Label>
            <Select
              value={values.countryId ? String(values.countryId) : undefined}
              onValueChange={(v) => v && setValues((p) => ({ ...p, countryId: Number(v) }))}
            >
              <SelectTrigger id="natCountry" invalid={!!fieldErrors.countryId}>
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
        nationalityApi.list({ search, status: status as any, countryId: countryFilter, page, size })
      }
      create={nationalityApi.create}
      update={nationalityApi.update}
      activate={nationalityApi.activate}
      deactivate={nationalityApi.deactivate}
    />
  );
}
