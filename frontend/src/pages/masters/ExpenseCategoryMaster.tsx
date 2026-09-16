import { useEffect, useState } from 'react';
import { Receipt } from 'lucide-react';
import { expenseCategoryApi } from '../../api/mastersApi';
import { accountApi } from '../../api/accountingApi';
import { Account } from '../../types/accounting';
import { ExpenseCategory, ExpenseCategoryPayload } from '../../types/masters';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const EMPTY: ExpenseCategoryPayload = { code: '', name: '', linkedAccountId: undefined, description: '' };
const NONE = '__none__';

export default function ExpenseCategoryMaster() {
  const [accounts, setAccounts] = useState<Account[]>([]);

  useEffect(() => {
    accountApi.list({ active: true, size: 200 }).then((res) => setAccounts(res.data.content));
  }, []);

  return (
    <MasterCrudPage<ExpenseCategory, ExpenseCategoryPayload>
      icon={Receipt}
      title="Expense Category"
      description="Business classification for expenses (Rent, Electricity, Transport, ...). Optionally map a category to a Chart-of-Accounts account so its expenses post there instead of the generic Expenses account."
      itemLabel="expense categories"
      searchPlaceholder="Search by code or name"
      columns={[
        { header: 'Code', render: (c) => <span className="font-mono text-sm">{c.code}</span> },
        { header: 'Name', render: (c) => <span className="font-medium">{c.name}</span> },
        { header: 'Linked Account', render: (c) => c.linkedAccountName || <span className="text-muted-foreground">Generic Expenses account</span> },
      ]}
      emptyValues={EMPTY}
      toFormValues={(c) => ({ code: c.code, name: c.name, linkedAccountId: c.linkedAccountId ?? undefined, description: c.description || '' })}
      renderForm={(values, setValues, fieldErrors) => (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="categoryCode">Code</Label>
            <Input
              id="categoryCode"
              required
              value={values.code}
              onChange={(e) => setValues((p) => ({ ...p, code: e.target.value.toUpperCase() }))}
              invalid={!!fieldErrors.code}
              placeholder="e.g. RENT, ELECTRICITY, TRANSPORT"
            />
            {fieldErrors.code && <p className="text-xs text-destructive">{fieldErrors.code}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="categoryName">Name</Label>
            <Input
              id="categoryName"
              required
              value={values.name}
              onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
              invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
          </div>
          <div className="space-y-1.5">
            <Label>Linked Account (optional)</Label>
            <Select
              value={values.linkedAccountId ? String(values.linkedAccountId) : NONE}
              onValueChange={(v) => setValues((p) => ({ ...p, linkedAccountId: v === NONE ? undefined : Number(v) }))}
            >
              <SelectTrigger>
                <SelectValue placeholder="Generic Expenses account" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>Generic Expenses account (default)</SelectItem>
                {accounts.map((a) => (
                  <SelectItem key={a.id} value={String(a.id)}>
                    {a.accountCode} — {a.accountName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="categoryDescription">Description (optional)</Label>
            <Textarea
              id="categoryDescription"
              rows={2}
              value={values.description || ''}
              onChange={(e) => setValues((p) => ({ ...p, description: e.target.value }))}
            />
          </div>
        </>
      )}
      fetchList={({ search, status, page, size }) => expenseCategoryApi.list({ search, status: status as any, page, size })}
      create={expenseCategoryApi.create}
      update={expenseCategoryApi.update}
      activate={expenseCategoryApi.activate}
      deactivate={expenseCategoryApi.deactivate}
    />
  );
}
