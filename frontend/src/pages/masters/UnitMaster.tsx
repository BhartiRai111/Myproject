import { Ruler } from 'lucide-react';
import { unitApi } from '../../api/mastersApi';
import { Unit, UnitPayload } from '../../types/masters';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

const EMPTY: UnitPayload = { name: '', symbol: '' };

export default function UnitMaster() {
  return (
    <MasterCrudPage<Unit, UnitPayload>
      icon={Ruler}
      title="Unit"
      description="Manage measurement units used for purchase and sale of items."
      itemLabel="units"
      searchPlaceholder="Search by name or symbol"
      columns={[
        { header: 'Name', render: (u) => <span className="font-medium">{u.name}</span> },
        { header: 'Symbol', render: (u) => u.symbol },
      ]}
      emptyValues={EMPTY}
      toFormValues={(u) => ({ name: u.name, symbol: u.symbol })}
      renderForm={(values, setValues, fieldErrors) => (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="unitName">Unit Name</Label>
            <Input
              id="unitName"
              required
              value={values.name}
              onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
              invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="unitSymbol">Symbol</Label>
            <Input
              id="unitSymbol"
              required
              value={values.symbol}
              onChange={(e) => setValues((p) => ({ ...p, symbol: e.target.value }))}
              invalid={!!fieldErrors.symbol}
            />
            {fieldErrors.symbol && <p className="text-xs text-destructive">{fieldErrors.symbol}</p>}
          </div>
        </>
      )}
      fetchList={({ search, status, page, size }) => unitApi.list({ search, status: status as any, page, size })}
      create={unitApi.create}
      update={unitApi.update}
      activate={unitApi.activate}
      deactivate={unitApi.deactivate}
    />
  );
}
