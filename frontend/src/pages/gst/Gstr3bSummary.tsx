import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Info } from 'lucide-react';
import { gstReportApi } from '../../api/gstReportApi';
import { parseApiError } from '../../utils/apiError';
import { Gstr3bResponse } from '../../types/gstReport';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { money, currentReturnPeriod } from './gstFormat';

function AmountRow({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-center justify-between border-b border-border/60 py-2 text-sm last:border-0">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{money(value)}</span>
    </div>
  );
}

export default function Gstr3bSummary() {
  const navigate = useNavigate();
  const [returnPeriod, setReturnPeriod] = useState(currentReturnPeriod());
  const [report, setReport] = useState<Gstr3bResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (period: string) => {
    setLoading(true);
    try {
      const res = await gstReportApi.gstr3b(period);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load GSTR-3B summary').message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(returnPeriod);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="space-y-6">
      <BackButton label="Back to GST Reports" onClick={() => navigate('/gst-reports')} />

      <PageHeader
        title="GSTR-3B Summary"
        description="Outward supplies, Input Tax Credit, and net GST liability for one return period."
        actions={
          <div className="flex flex-wrap items-end gap-2">
            <div>
              <Label htmlFor="returnPeriod" className="text-xs text-muted-foreground">Return Period</Label>
              <Input
                id="returnPeriod"
                type="month"
                className="w-40"
                value={returnPeriod}
                onChange={(e) => setReturnPeriod(e.target.value)}
              />
            </div>
            <Button type="button" onClick={() => load(returnPeriod)}>
              Apply
            </Button>
          </div>
        }
      />

      <Alert>
        <Info className="h-4 w-4" />
        <AlertDescription>
          {loading ? 'Loading…' : report?.note ?? 'This is a return preparation aid — nothing here is filed with the GST portal.'}
        </AlertDescription>
      </Alert>

      {loading ? (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-56 w-full" />
          ))}
        </div>
      ) : (
        report && (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <Card>
              <CardHeader>
                <CardTitle>Outward Supplies (Output GST)</CardTitle>
              </CardHeader>
              <CardContent>
                <AmountRow label="Taxable Value" value={report.outwardSupplies.taxableAmount} />
                <AmountRow label="CGST" value={report.outwardSupplies.cgstAmount} />
                <AmountRow label="SGST" value={report.outwardSupplies.sgstAmount} />
                <AmountRow label="IGST" value={report.outwardSupplies.igstAmount} />
                <div className="mt-2 flex items-center justify-between border-t border-border pt-2 text-sm font-semibold">
                  <span>Total Tax</span>
                  <span>{money(report.outwardSupplies.totalTax)}</span>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Input Tax Credit (ITC)</CardTitle>
              </CardHeader>
              <CardContent>
                <AmountRow label="Taxable Value" value={report.inputTaxCredit.taxableAmount} />
                <AmountRow label="CGST" value={report.inputTaxCredit.cgstAmount} />
                <AmountRow label="SGST" value={report.inputTaxCredit.sgstAmount} />
                <AmountRow label="IGST" value={report.inputTaxCredit.igstAmount} />
                <div className="mt-2 flex items-center justify-between border-t border-border pt-2 text-sm font-semibold">
                  <span>Total ITC</span>
                  <span>{money(report.inputTaxCredit.totalTax)}</span>
                </div>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Net Liability</CardTitle>
              </CardHeader>
              <CardContent>
                <AmountRow label="CGST" value={report.netLiability.cgstAmount} />
                <AmountRow label="SGST" value={report.netLiability.sgstAmount} />
                <AmountRow label="IGST" value={report.netLiability.igstAmount} />
                <div className="mt-2 flex items-center justify-between border-t border-border pt-2 text-base font-bold">
                  <span>Net Payable</span>
                  <span>{money(report.netLiability.totalTax)}</span>
                </div>
              </CardContent>
            </Card>
          </div>
        )
      )}
    </div>
  );
}
