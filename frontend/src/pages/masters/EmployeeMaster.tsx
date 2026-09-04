import { useEffect, useState } from 'react';
import { UserSquare2 } from 'lucide-react';
import { cityApi, employeeApi, stateApi } from '../../api/mastersApi';
import { City, Employee, EmployeePayload, StateMaster as StateEntity } from '../../types/masters';
import { MasterCrudPage } from '@/components/masters/MasterCrudPage';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';

const EMPTY: EmployeePayload = {
  employeeCode: '',
  name: '',
  mobile: '',
  email: '',
  designation: '',
  department: '',
  address: '',
  cityId: '',
  stateId: '',
  joiningDate: '',
  notes: '',
};

export default function EmployeeMaster() {
  const [states, setStates] = useState<StateEntity[]>([]);
  const [cities, setCities] = useState<City[]>([]);

  useEffect(() => {
    stateApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setStates(res.data.content));
    cityApi.list({ status: 'ACTIVE', size: 1000 }).then((res) => setCities(res.data.content));
  }, []);

  return (
    <MasterCrudPage<Employee, EmployeePayload>
      icon={UserSquare2}
      title="Employee"
      description="Manage employees who can be assigned as salesmen or relationship managers."
      itemLabel="employees"
      searchPlaceholder="Search by name, code or mobile"
      columns={[
        { header: 'Code', render: (e) => e.employeeCode },
        { header: 'Name', render: (e) => <span className="font-medium">{e.name}</span> },
        { header: 'Mobile', render: (e) => e.mobile },
        { header: 'Designation', render: (e) => e.designation || '-' },
      ]}
      emptyValues={EMPTY}
      toFormValues={(e) => ({
        employeeCode: e.employeeCode,
        name: e.name,
        mobile: e.mobile,
        email: e.email || '',
        designation: e.designation || '',
        department: e.department || '',
        address: e.address || '',
        cityId: e.cityId || '',
        stateId: e.stateId || '',
        joiningDate: e.joiningDate || '',
        notes: e.notes || '',
      })}
      renderView={(e) => (
        <div className="space-y-2 text-sm">
          <div className="grid grid-cols-2 gap-2">
            <div>
              <span className="text-muted-foreground">Code:</span> {e.employeeCode}
            </div>
            <div>
              <span className="text-muted-foreground">Name:</span> {e.name}
            </div>
            <div>
              <span className="text-muted-foreground">Mobile:</span> {e.mobile}
            </div>
            <div>
              <span className="text-muted-foreground">Email:</span> {e.email || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">Designation:</span> {e.designation || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">Department:</span> {e.department || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">City:</span> {e.cityName || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">State:</span> {e.stateName || '-'}
            </div>
            <div>
              <span className="text-muted-foreground">Joining Date:</span> {e.joiningDate || '-'}
            </div>
          </div>
          <div>
            <span className="text-muted-foreground">Address:</span> {e.address || '-'}
          </div>
          <div>
            <span className="text-muted-foreground">Notes:</span> {e.notes || '-'}
          </div>
        </div>
      )}
      renderForm={(values, setValues, fieldErrors) => (
        <Tabs defaultValue="basic" className="w-full">
          <TabsList>
            <TabsTrigger value="basic">Basic Info</TabsTrigger>
            <TabsTrigger value="address">Address &amp; Notes</TabsTrigger>
          </TabsList>

          <TabsContent value="basic" className="space-y-3">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="empCode">Employee Code</Label>
                <Input
                  id="empCode"
                  required
                  value={values.employeeCode}
                  onChange={(e) => setValues((p) => ({ ...p, employeeCode: e.target.value }))}
                  invalid={!!fieldErrors.employeeCode}
                />
                {fieldErrors.employeeCode && <p className="text-xs text-destructive">{fieldErrors.employeeCode}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="empName">Name</Label>
                <Input
                  id="empName"
                  required
                  value={values.name}
                  onChange={(e) => setValues((p) => ({ ...p, name: e.target.value }))}
                  invalid={!!fieldErrors.name}
                />
                {fieldErrors.name && <p className="text-xs text-destructive">{fieldErrors.name}</p>}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="empMobile">Mobile</Label>
                <Input
                  id="empMobile"
                  required
                  value={values.mobile}
                  onChange={(e) => setValues((p) => ({ ...p, mobile: e.target.value }))}
                  invalid={!!fieldErrors.mobile}
                />
                {fieldErrors.mobile && <p className="text-xs text-destructive">{fieldErrors.mobile}</p>}
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="empEmail">Email</Label>
                <Input
                  id="empEmail"
                  type="email"
                  value={values.email}
                  onChange={(e) => setValues((p) => ({ ...p, email: e.target.value }))}
                  invalid={!!fieldErrors.email}
                />
                {fieldErrors.email && <p className="text-xs text-destructive">{fieldErrors.email}</p>}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="empDesignation">Designation</Label>
                <Input
                  id="empDesignation"
                  value={values.designation}
                  onChange={(e) => setValues((p) => ({ ...p, designation: e.target.value }))}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="empDepartment">Department</Label>
                <Input
                  id="empDepartment"
                  value={values.department}
                  onChange={(e) => setValues((p) => ({ ...p, department: e.target.value }))}
                />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="empJoiningDate">Joining Date</Label>
              <Input
                id="empJoiningDate"
                type="date"
                value={values.joiningDate}
                onChange={(e) => setValues((p) => ({ ...p, joiningDate: e.target.value }))}
              />
            </div>
          </TabsContent>

          <TabsContent value="address" className="space-y-3">
            <div className="space-y-1.5">
              <Label htmlFor="empAddress">Address</Label>
              <Textarea
                id="empAddress"
                value={values.address}
                onChange={(e) => setValues((p) => ({ ...p, address: e.target.value }))}
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="empState">State</Label>
                <Select
                  value={values.stateId ? String(values.stateId) : undefined}
                  onValueChange={(v) => v && setValues((p) => ({ ...p, stateId: Number(v) }))}
                >
                  <SelectTrigger id="empState">
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
                <Label htmlFor="empCity">City</Label>
                <Select
                  value={values.cityId ? String(values.cityId) : undefined}
                  onValueChange={(v) => v && setValues((p) => ({ ...p, cityId: Number(v) }))}
                >
                  <SelectTrigger id="empCity">
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
              <Label htmlFor="empNotes">Notes</Label>
              <Textarea
                id="empNotes"
                value={values.notes}
                onChange={(e) => setValues((p) => ({ ...p, notes: e.target.value }))}
              />
            </div>
          </TabsContent>
        </Tabs>
      )}
      fetchList={({ search, status, page, size }) => employeeApi.list({ search, status: status as any, page, size })}
      create={employeeApi.create}
      update={employeeApi.update}
      activate={employeeApi.activate}
      deactivate={employeeApi.deactivate}
    />
  );
}
