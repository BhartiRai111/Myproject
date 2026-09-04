import { useEffect, useState } from 'react';
import { Building2 } from 'lucide-react';
import { cityApi, stateApi } from '../../api/mastersApi';
import { City, CityPayload, StateMaster as StateEntity } from '../../types/masters';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const EMPTY: CityPayload = { name: '', stateId: 0 };
const ALL = '__all__';

export default function CityMaster() {
  const [states, setStates] = useState<StateEntity[]>([]);
  const [stateFilter, setStateFilter] = useState<number | ''>('');

  useEffect(() => {
    stateApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setStates(res.data.content));
  }, []);

  return (
    <MasterCrudPage<City, CityPayload>
      icon={Building2}
      title="City"
      description="Manage cities, linked to their state."
      itemLabel="cities"
      searchPlaceholder="Search by name"
      columns={[
        { header: 'Name', render: (c) => <span className="font-medium">{c.name}</span> },
        { header: 'State', render: (c) => c.stateName },
        { header: 'Country', render: (c) => c.countryName },
      ]}
      emptyValues={EMPTY}
      toFormValues={(c) => ({ name: c.name, stateId: c.stateId })}
      extraFilterSlot={
        <select
          value={stateFilter || ALL}
          onChange={(e) => setStateFilter(e.target.value === ALL ? '' : Number(e.target.value))}
          className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm sm:w-48"
        >
          <option value={ALL}>All States</option>
          {states.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
      }
      reloadToken={stateFilter}
      renderForm={(values, setValues, fieldErrors) => (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="cityName">City Name</Label>
            <Input
              id="cityName"
              required
              value={values.name}
              onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
              invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="cityState">State</Label>
            <Select
              value={values.stateId ? String(values.stateId) : undefined}
              onValueChange={(v) => v && setValues((p) => ({ ...p, stateId: Number(v) }))}
            >
              <SelectTrigger id="cityState" invalid={!!fieldErrors.stateId}>
                <SelectValue placeholder="Select a state" />
              </SelectTrigger>
              <SelectContent>
                {states.map((s) => (
                  <SelectItem key={s.id} value={String(s.id)}>
                    {s.name} ({s.countryName})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {fieldErrors.stateId && <p className="text-xs text-destructive">{fieldErrors.stateId}</p>}
          </div>
        </>
      )}
      fetchList={({ search, status, page, size }) =>
        cityApi.list({ search, status: status as any, stateId: stateFilter, page, size })
      }
      create={cityApi.create}
      update={cityApi.update}
      activate={cityApi.activate}
      deactivate={cityApi.deactivate}
    />
  );
}
