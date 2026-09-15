import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { CheckCircle2, AlertTriangle, XCircle, RefreshCcw } from 'lucide-react';
import { accountingReportApi } from '../../../api/accountingApi';
import { parseApiError } from '../../../utils/apiError';
import { AccountingHealthCheckResponse } from '../../../types/accounting';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';

function statusVariant(status: string) {
  if (status === 'PASS') return 'success' as const;
  if (status === 'WARNING') return 'warning' as const;
  return 'destructive' as const;
}

function StatusIcon({ status }: { status: string }) {
  if (status === 'PASS') return <CheckCircle2 className="h-5 w-5 text-success" />;
  if (status === 'WARNING') return <AlertTriangle className="h-5 w-5 text-warning" />;
  return <XCircle className="h-5 w-5 text-destructive" />;
}

export default function HealthCheck() {
  const navigate = useNavigate();
  const [report, setReport] = useState<AccountingHealthCheckResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await accountingReportApi.healthCheck();
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to run Accounting Health Check').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="space-y-6">
      <BackButton label="Back to Accounting" onClick={() => navigate('/accounting')} />

      <PageHeader
        title="Accounting Health Check"
        description="Journal balance, duplicate posting, source posting, orphan journals, and party ledger reconciliation — every mismatch is reported, never hidden."
        actions={
          <Button type="button" variant="outline" onClick={load}>
            <RefreshCcw className="h-4 w-4" /> Re-run
          </Button>
        }
      />

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3, 4, 5].map((i) => <Skeleton key={i} className="h-20 w-full" />)}
        </div>
      ) : (
        report && (
          <>
            <Card>
              <CardContent className="flex items-center justify-between p-5">
                <div className="flex items-center gap-2">
                  <StatusIcon status={report.overallStatus} />
                  <span className="text-base font-semibold">Overall Status</span>
                </div>
                <Badge variant={statusVariant(report.overallStatus)}>{report.overallStatus}</Badge>
              </CardContent>
            </Card>

            <div className="space-y-3">
              {report.findings.map((f) => (
                <Card key={f.checkName}>
                  <CardContent className="p-5">
                    <div className="flex items-center justify-between gap-3">
                      <div className="flex items-center gap-2">
                        <StatusIcon status={f.status} />
                        <span className="font-semibold">{f.checkName}</span>
                      </div>
                      <Badge variant={statusVariant(f.status)}>{f.status}</Badge>
                    </div>
                    <p className="mt-2 text-sm text-muted-foreground">{f.message}</p>
                    {f.details.length > 0 && (
                      <ul className="mt-3 max-h-48 space-y-1 overflow-y-auto rounded-md bg-muted/40 p-3 text-xs text-muted-foreground">
                        {f.details.map((d, idx) => (
                          <li key={idx}>{d}</li>
                        ))}
                      </ul>
                    )}
                  </CardContent>
                </Card>
              ))}
            </div>
          </>
        )
      )}
    </div>
  );
}
