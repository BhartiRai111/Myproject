import { useEffect, useState } from 'react';
import { toast } from 'sonner';
import { Search, ShieldAlert } from 'lucide-react';
import { auditLogApi } from '../../api/auditLogApi';
import { parseApiError } from '../../utils/apiError';
import { AuditAction, AuditLog } from '../../types/auditLog';
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

const PAGE_SIZE = 20;
const ALL = '__all__';

const ACTIONS: AuditAction[] = [
  'CREATE', 'UPDATE', 'POST', 'CANCEL', 'DELETE', 'LOGIN', 'LOGOUT', 'PRINT', 'EXPORT',
  'PAYMENT', 'RECEIPT', 'APPROVE', 'REVERSE', 'FY_CLOSE', 'FY_OPEN', 'PERMISSION_CHANGE',
];

function actionVariant(action: AuditAction) {
  if (action === 'CANCEL' || action === 'DELETE' || action === 'REVERSE') return 'destructive' as const;
  if (action === 'POST' || action === 'APPROVE' || action === 'PAYMENT' || action === 'RECEIPT') return 'success' as const;
  if (action === 'FY_CLOSE' || action === 'PERMISSION_CHANGE') return 'warning' as const;
  return 'muted' as const;
}

export default function AuditTrail() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [action, setAction] = useState<AuditAction | ''>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const load = async () => {
    setLoading(true);
    try {
      const res = await auditLogApi.list({ search, action: action || undefined, page, size: PAGE_SIZE });
      setLogs(res.data.content);
      setTotalPages(res.data.totalPages);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load audit trail').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, action]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    load();
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Audit Trail"
        description="Append-only log of every important business action — who did what, when, and from where. Visible to ADMIN only."
      />

      <Card>
        <CardContent className="p-4">
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search by user, document number, or description"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="pl-9"
              />
            </div>
            <Select
              value={action || ALL}
              onValueChange={(v) => {
                setPage(0);
                setAction(v === ALL ? '' : (v as AuditAction));
              }}
            >
              <SelectTrigger className="w-full sm:w-44">
                <SelectValue placeholder="Action" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All Actions</SelectItem>
                {ACTIONS.map((a) => (
                  <SelectItem key={a} value={a}>
                    {a}
                  </SelectItem>
                ))}
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
                <TableHead>Timestamp</TableHead>
                <TableHead>User</TableHead>
                <TableHead>Action</TableHead>
                <TableHead>Module</TableHead>
                <TableHead>Entity</TableHead>
                <TableHead>Document</TableHead>
                <TableHead>Description</TableHead>
                <TableHead>IP</TableHead>
              </TableRow>
            </TableHeader>
            {loading ? (
              <TableSkeleton columns={8} />
            ) : (
              <TableBody>
                {logs.map((log) => (
                  <TableRow key={log.id}>
                    <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                      {new Date(log.timestamp).toLocaleString('en-IN')}
                    </TableCell>
                    <TableCell className="font-medium">{log.username || 'System'}</TableCell>
                    <TableCell>
                      <Badge variant={actionVariant(log.action)}>{log.action}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{log.module || '—'}</TableCell>
                    <TableCell className="text-muted-foreground">{log.entityType || '—'}</TableCell>
                    <TableCell className="font-mono text-sm">{log.documentNumber || '—'}</TableCell>
                    <TableCell className="max-w-md truncate text-sm text-muted-foreground" title={log.description}>
                      {log.description || '—'}
                    </TableCell>
                    <TableCell className="text-xs text-muted-foreground">{log.ipAddress || '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            )}
          </Table>

          {!loading && logs.length === 0 && (
            <EmptyState icon={ShieldAlert} title="No audit entries found" description="Try adjusting your search or filters." />
          )}

          {!loading && logs.length > 0 && (
            <div className="border-t border-border p-4">
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
