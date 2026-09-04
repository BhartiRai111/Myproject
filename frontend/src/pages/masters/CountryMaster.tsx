import { Globe } from 'lucide-react';
import { countryApi } from '../../api/mastersApi';
import { Country, CountryPayload } from '../../types/masters';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

const EMPTY: CountryPayload = { name: '', code: '' };

export default function CountryMaster() {
  return (
    <MasterCrudPage<Country, CountryPayload>
      icon={Globe}
      title="Country"
      description="Manage countries used across StoreHub."
      itemLabel="countries"
      searchPlaceholder="Search by name or code"
      columns={[
        { header: 'Name', render: (c) => <span className="font-medium">{c.name}</span> },
        { header: 'Code', render: (c) => c.code },
      ]}
      emptyValues={EMPTY}
      toFormValues={(c) => ({ name: c.name, code: c.code })}
      renderForm={(values, setValues, fieldErrors) => (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="ctyName">Country Name</Label>
            <Input
              id="ctyName"
              required
              value={values.name}
              onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
              invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="ctyCode">Code</Label>
            <Input
              id="ctyCode"
              required
              value={values.code}
              onChange={(e) => setValues((p) => ({ ...p, code: e.target.value }))}
              invalid={!!fieldErrors.code}
            />
            {fieldErrors.code && <p className="text-xs text-destructive">{fieldErrors.code}</p>}
          </div>
        </>
      )}
      fetchList={({ search, status, page, size }) => countryApi.list({ search, status: status as any, page, size })}
      create={countryApi.create}
      update={countryApi.update}
      activate={countryApi.activate}
      deactivate={countryApi.deactivate}
    />
  );
}
