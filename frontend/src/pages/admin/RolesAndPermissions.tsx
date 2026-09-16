import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { ShieldCheck } from 'lucide-react';
import { roleApi, PermissionsByModule } from '../../api/roleApi';
import { parseApiError } from '../../utils/apiError';
import { Role, RoleInfo } from '../../types/user';
import { PageHeader } from '@/components/PageHeader';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';

function formatRole(role: string) {
  return role
    .split('_')
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(' ');
}

export default function RolesAndPermissions() {
  const [roles, setRoles] = useState<RoleInfo[]>([]);
  const [selectedRole, setSelectedRole] = useState<Role | null>(null);
  const [permissions, setPermissions] = useState<PermissionsByModule>({});
  const [loadingRoles, setLoadingRoles] = useState(true);
  const [loadingPermissions, setLoadingPermissions] = useState(false);

  useEffect(() => {
    roleApi
      .list()
      .then((res) => {
        setRoles(res.data);
        if (res.data.length > 0) setSelectedRole(res.data[0].name);
      })
      .catch((err) => toast.error(parseApiError(err, 'Failed to load roles').message))
      .finally(() => setLoadingRoles(false));
  }, []);

  useEffect(() => {
    if (!selectedRole) return;
    setLoadingPermissions(true);
    roleApi
      .getPermissions(selectedRole)
      .then((res) => setPermissions(res.data))
      .catch((err) => toast.error(parseApiError(err, 'Failed to load permissions').message))
      .finally(() => setLoadingPermissions(false));
  }, [selectedRole]);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Roles & Permissions"
        description="Every role's access is fixed by the system and enforced on the backend for every request — this page is for review, not editing."
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardContent className="p-0">
            {loadingRoles ? (
              <div className="p-4 text-sm text-muted-foreground">Loading roles...</div>
            ) : (
              <ul className="divide-y divide-border">
                {roles.map((r) => (
                  <li key={r.name}>
                    <button
                      type="button"
                      onClick={() => setSelectedRole(r.name)}
                      className={`flex w-full flex-col items-start gap-1 px-4 py-3 text-left transition-colors hover:bg-accent ${
                        selectedRole === r.name ? 'bg-accent' : ''
                      }`}
                    >
                      <div className="flex w-full items-center justify-between">
                        <span className="font-medium">{formatRole(r.name)}</span>
                        <Badge variant="secondary">{r.permissionCount} permissions</Badge>
                      </div>
                      <span className="text-xs text-muted-foreground">{r.description}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardContent className="p-4">
            {loadingPermissions ? (
              <div className="text-sm text-muted-foreground">Loading permissions...</div>
            ) : Object.keys(permissions).length === 0 ? (
              <div className="flex flex-col items-center gap-2 py-8 text-center text-muted-foreground">
                <ShieldCheck className="h-8 w-8" />
                <p className="text-sm">This role has no permissions.</p>
              </div>
            ) : (
              <div className="space-y-5">
                {Object.entries(permissions).map(([module, perms]) => (
                  <div key={module}>
                    <h3 className="mb-2 text-sm font-semibold text-foreground">{module}</h3>
                    <div className="flex flex-wrap gap-2">
                      {perms.map((p) => (
                        <Badge key={p.name} variant="outline">
                          {p.name}
                        </Badge>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
