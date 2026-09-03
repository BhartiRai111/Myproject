import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { MoreHorizontal, Plus, Search, ShieldCheck, UserRoundX, Users as UsersIcon } from 'lucide-react';
import { userApi } from '../api/userApi';
import { parseApiError } from '../utils/apiError';
import UserFormModal, { UserFormValues } from '../components/UserFormModal';
import UserViewModal from '../components/UserViewModal';
import { Role, User, UserStatus } from '../types/user';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { TableSkeleton } from '@/components/TableSkeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Pagination } from '@/components/ui/pagination';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';

const PAGE_SIZE = 10;
const ALL = '__all__';

function formatRole(role: string) {
  return role
    .split('_')
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(' ');
}

export default function Users() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<Role | ''>('');
  const [statusFilter, setStatusFilter] = useState<UserStatus | ''>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const [formModal, setFormModal] = useState<{ show: boolean; mode: 'add' | 'edit'; user: User | null }>({
    show: false,
    mode: 'add',
    user: null,
  });
  const [viewUser, setViewUser] = useState<User | null>(null);

  const loadUsers = async () => {
    setLoading(true);
    try {
      const res = await userApi.list({ search, role: roleFilter, status: statusFilter, page, size: PAGE_SIZE });
      setUsers(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load users').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, roleFilter, statusFilter]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    loadUsers();
  };

  const openAddModal = () => setFormModal({ show: true, mode: 'add', user: null });
  const openEditModal = (user: User) => setFormModal({ show: true, mode: 'edit', user });
  const closeFormModal = () => setFormModal((prev) => ({ ...prev, show: false }));

  const handleFormSubmit = async (values: UserFormValues) => {
    if (formModal.mode === 'add') {
      await userApi.create({
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        mobile: values.mobile,
        password: values.password,
        role: values.role,
        status: values.status,
      });
      toast.success('User created successfully');
    } else if (formModal.user) {
      await userApi.update(formModal.user.id, {
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        mobile: values.mobile,
        role: values.role,
        status: values.status,
      });
      toast.success('User updated successfully');
    }
    closeFormModal();
    loadUsers();
  };

  const toggleStatus = async (user: User) => {
    const newStatus: UserStatus = user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      await userApi.updateStatus(user.id, newStatus);
      toast.success(`User ${newStatus === 'ACTIVE' ? 'activated' : 'deactivated'} successfully`);
      loadUsers();
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to update user status').message);
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="User Management"
        description="Manage who can access StoreHub and what they can do."
        actions={
          <Button onClick={openAddModal}>
            <Plus className="h-4 w-4" />
            Add User
          </Button>
        }
      />

      <Card>
        <CardContent className="p-4">
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by name or email"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9"
              />
            </div>
            <Select
              value={roleFilter || ALL}
              onValueChange={(v) => {
                setPage(0);
                setRoleFilter(v === ALL ? '' : (v as Role));
              }}
            >
              <SelectTrigger className="w-full sm:w-44">
                <SelectValue placeholder="All Roles" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Roles</SelectItem>
                <SelectItem value="ADMIN">Admin</SelectItem>
                <SelectItem value="STORE_MANAGER">Store Manager</SelectItem>
                <SelectItem value="STAFF">Staff</SelectItem>
              </SelectContent>
            </Select>
            <Select
              value={statusFilter || ALL}
              onValueChange={(v) => {
                setPage(0);
                setStatusFilter(v === ALL ? '' : (v as UserStatus));
              }}
            >
              <SelectTrigger className="w-full sm:w-36">
                <SelectValue placeholder="All Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Status</SelectItem>
                <SelectItem value="ACTIVE">Active</SelectItem>
                <SelectItem value="INACTIVE">Inactive</SelectItem>
              </SelectContent>
            </Select>
            <Button type="submit" variant="secondary" className="sm:w-auto">
              Search
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Mobile</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created At</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={7} />
            ) : (
              <TableBody>
                {users.map((u) => (
                  <TableRow key={u.id}>
                    <TableCell className="font-medium">
                      {u.firstName} {u.lastName}
                    </TableCell>
                    <TableCell className="text-muted-foreground">{u.email}</TableCell>
                    <TableCell className="text-muted-foreground">{u.mobile}</TableCell>
                    <TableCell>
                      <Badge variant="secondary">{formatRole(u.role)}</Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={u.status === 'ACTIVE' ? 'success' : 'muted'}>{u.status}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {new Date(u.createdAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell className="text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" className="h-8 w-8">
                            <MoreHorizontal className="h-4 w-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => setViewUser(u)}>View</DropdownMenuItem>
                          <DropdownMenuItem onClick={() => openEditModal(u)}>Edit</DropdownMenuItem>
                          <DropdownMenuItem
                            onClick={() => toggleStatus(u)}
                            variant={u.status === 'ACTIVE' ? 'destructive' : 'default'}
                          >
                            {u.status === 'ACTIVE' ? (
                              <>
                                <UserRoundX className="h-4 w-4" /> Deactivate
                              </>
                            ) : (
                              <>
                                <ShieldCheck className="h-4 w-4" /> Activate
                              </>
                            )}
                          </DropdownMenuItem>
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && users.length === 0 && (
            <EmptyState icon={UsersIcon} title="No users found" description="Try adjusting your search or filters." />
          )}

          {!loading && users.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>

      <UserFormModal
        show={formModal.show}
        mode={formModal.mode}
        initialUser={formModal.user}
        onClose={closeFormModal}
        onSubmit={handleFormSubmit}
      />

      <UserViewModal show={!!viewUser} user={viewUser} onClose={() => setViewUser(null)} />
    </div>
  );
}
