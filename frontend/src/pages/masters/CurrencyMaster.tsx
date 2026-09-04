import { Coins } from 'lucide-react';
import { currencyApi } from '../../api/mastersApi';
import { Currency, CurrencyPayload } from '../../types/masters';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

const EMPTY: CurrencyPayload = { name: '', code: '', symbol: '', decimalPlaces: 2 };

export default function CurrencyMaster() {
  return (
    <MasterCrudPage<Currency, CurrencyPayload>
      icon={Coins}
      title="Currency"
      description="Manage currencies used across StoreHub."
      itemLabel="currencies"
      searchPlaceholder="Search by name or code"
      columns={[
        { header: 'Name', render: (c) => <span className="font-medium">{c.name}</span> },
        { header: 'Code', render: (c) => c.code },
        { header: 'Symbol', render: (c) => c.symbol },
        { header: 'Decimal Places', render: (c) => c.decimalPlaces },
      ]}
      emptyValues={EMPTY}
      toFormValues={(c) => ({ name: c.name, code: c.code, symbol: c.symbol, decimalPlaces: c.decimalPlaces })}
      renderForm={(values, setValues, fieldErrors) => (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="curName">Currency Name</Label>
            <Input
              id="curName"
              required
              value={values.name}
              onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
              invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="curCode">Code</Label>
              <Input
                id="curCode"
                required
                value={values.code}
                onChange={(e) => setValues((p) => ({ ...p, code: e.target.value }))}
                invalid={!!fieldErrors.code}
              />
              {fieldErrors.code && <p className="text-xs text-destructive">{fieldErrors.code}</p>}
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="curSymbol">Symbol</Label>
              <Input
                id="curSymbol"
                required
                value={values.symbol}
                onChange={(e) => setValues((p) => ({ ...p, symbol: e.target.value }))}
                invalid={!!fieldErrors.symbol}
              />
              {fieldErrors.symbol && <p className="text-xs text-destructive">{fieldErrors.symbol}</p>}
            </div>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="curDecimals">Decimal Places</Label>
            <Input
              id="curDecimals"
              type="number"
              min={0}
              max={4}
              required
              value={values.decimalPlaces}
              onChange={(e) => setValues((p) => ({ ...p, decimalPlaces: Number(e.target.value) }))}
              invalid={!!fieldErrors.decimalPlaces}
            />
            {fieldErrors.decimalPlaces && <p className="text-xs text-destructive">{fieldErrors.decimalPlaces}</p>}
          </div>
        </>
      )}
      fetchList={({ search, status, page, size }) => currencyApi.list({ search, status: status as any, page, size })}
      create={currencyApi.create}
      update={currencyApi.update}
      activate={currencyApi.activate}
      deactivate={currencyApi.deactivate}
    />
  );
}
