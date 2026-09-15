import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { AlertTriangle, BellRing, OctagonAlert, RefreshCw } from 'lucide-react';
import { alertApi } from '../api/alertApi';
import { parseApiError } from '../utils/apiError';
import { AlertItem } from '../types/alert';
import { PageHeader } from '@/components/PageHeader';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';

export default function AlertsCenter() {
  const navigate = useNavigate();
  const [alerts, setAlerts] = useState<AlertItem[]>([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await alertApi.list();
      setAlerts(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load alerts').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const critical = alerts.filter((a) => a.severity === 'CRITICAL');
  const warning = alerts.filter((a) => a.severity === 'WARNING');

  return (
    <div className="space-y-6">
      <PageHeader
        title="Alert Center"
        description="Business alerts computed live from inventory, financial year, receivables/payables, and the accounting health check — nothing is stored or dismissible, so it always reflects current state."
        actions={
          <Button variant="outline" onClick={load} loading={loading}>
            <RefreshCw className="h-4 w-4" /> Refresh
          </Button>
        }
      />

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <Card>
          <CardContent className="flex flex-col gap-2 p-4">
            <div className="flex items-center gap-2 text-muted-foreground">
              <OctagonAlert className="h-4 w-4" />
              <span className="text-xs font-medium">Critical</span>
            </div>
            {loading ? <Skeleton className="h-6 w-10" /> : <span className="text-xl font-bold">{critical.length}</span>}
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex flex-col gap-2 p-4">
            <div className="flex items-center gap-2 text-muted-foreground">
              <AlertTriangle className="h-4 w-4" />
              <span className="text-xs font-medium">Warning</span>
            </div>
            {loading ? <Skeleton className="h-6 w-10" /> : <span className="text-xl font-bold">{warning.length}</span>}
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex flex-col gap-2 p-4">
            <div className="flex items-center gap-2 text-muted-foreground">
              <BellRing className="h-4 w-4" />
              <span className="text-xs font-medium">Total Alerts</span>
            </div>
            {loading ? <Skeleton className="h-6 w-10" /> : <span className="text-xl font-bold">{alerts.length}</span>}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardContent className="p-0">
          {loading ? (
            <div className="space-y-2 p-4">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-14 w-full" />
              ))}
            </div>
          ) : alerts.length === 0 ? (
            <EmptyState icon={BellRing} title="No active alerts" description="Everything looks healthy right now." />
          ) : (
            <div className="divide-y divide-border">
              {alerts.map((a, idx) => (
                <div
                  key={idx}
                  className={`flex items-start gap-3 p-4 ${a.path ? 'cursor-pointer hover:bg-muted/50' : ''}`}
                  onClick={() => a.path && navigate(a.path)}
                >
                  {a.severity === 'CRITICAL' ? (
                    <OctagonAlert className="mt-0.5 h-5 w-5 shrink-0 text-destructive" />
                  ) : (
                    <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-amber-500" />
                  )}
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <Badge variant={a.severity === 'CRITICAL' ? 'destructive' : 'warning'}>{a.severity}</Badge>
                      <span className="text-xs font-medium text-muted-foreground">{a.category}</span>
                    </div>
                    <p className="mt-1 text-sm">{a.message}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
