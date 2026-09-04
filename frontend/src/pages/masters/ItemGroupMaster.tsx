import { Boxes } from 'lucide-react';
import { itemGroupApi } from '../../api/mastersApi';
import { ItemGroup, ItemGroupPayload } from '../../types/masters';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

const EMPTY: ItemGroupPayload = { name: '', description: '' };

export default function ItemGroupMaster() {
  return (
    <MasterCrudPage<ItemGroup, ItemGroupPayload>
      icon={Boxes}
      title="Item Group"
      description="Manage item groups used to classify products."
      itemLabel="item groups"
      searchPlaceholder="Search by name"
      columns={[
        { header: 'Name', render: (g) => <span className="font-medium">{g.name}</span> },
        { header: 'Description', render: (g) => g.description || '-' },
      ]}
      emptyValues={EMPTY}
      toFormValues={(g) => ({ name: g.name, description: g.description || '' })}
      renderForm={(values, setValues, fieldErrors) => (
        <>
          <div className="space-y-1.5">
            <Label htmlFor="igName">Item Group Name</Label>
            <Input
              id="igName"
              required
              value={values.name}
              onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
              invalid={!!fieldErrors.name}
            />
            {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="igDescription">Description</Label>
            <Textarea
              id="igDescription"
              value={values.description}
              onChange={(e) => setValues((p) => ({ ...p, description: e.target.value }))}
            />
          </div>
        </>
      )}
      fetchList={({ search, status, page, size }) => itemGroupApi.list({ search, status: status as any, page, size })}
      create={itemGroupApi.create}
      update={itemGroupApi.update}
      activate={itemGroupApi.activate}
      deactivate={itemGroupApi.deactivate}
    />
  );
}
