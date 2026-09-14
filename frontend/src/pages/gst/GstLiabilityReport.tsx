import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { gstReportApi } from '../../api/gstReportApi';
import { parseApiError } from '../../utils/apiError';
import { GstLiabilityResponse } from '../../types/gstReport';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { money, currentReturnPeriod } from './gstFormat';

export default function GstLiabilityReport() {
  const navigate = useNavigate();
  const [returnPeriod, setReturnPeriod] = useState(currentReturnPeriod());
  const [report, setReport] = useState<GstLiabilityResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const load = async (period: string) => {
    setLoading(true);
    try {
      const res = await gstReportApi.liability(period);
      setReport(res.data);
    } catch (err) {
      toast.error(parseApiError(err, 'Failed to load GST Liability').message);
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
        title="GST Liability"
        description="Output tax vs. Input Tax Credit for a return period, and the resulting net GST liability."
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

      <Card>
        <CardContent className="p-0">
          {loading ? (
            <div className="space-y-2 p-5">
              {[1, 2, 3, 4].map((i) => (
                <Skeleton key={i} className="h-8 w-full" />
              ))}
            </div>
          ) : (
            report && (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Component</TableHead>
                    <TableHead className="text-right">CGST</TableHead>
                    <TableHead className="text-right">SGST</TableHead>
                    <TableHead className="text-right">IGST</TableHead>
                    <TableHead className="text-right">Total</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  <TableRow>
                    <TableCell className="font-medium">Output Tax (on Sales)</TableCell>
                    <TableCell className="text-right">{money(report.outputCgst)}</TableCell>
                    <TableCell className="text-right">{money(report.outputSgst)}</TableCell>
                    <TableCell className="text-right">{money(report.outputIgst)}</TableCell>
                    <TableCell className="text-right font-semibold">{money(report.outputTotal)}</TableCell>
                  </TableRow>
                  <TableRow>
                    <TableCell className="font-medium">Input Tax Credit (on Purchases)</TableCell>
                    <TableCell className="text-right">{money(report.inputCgst)}</TableCell>
                    <TableCell className="text-right">{money(report.inputSgst)}</TableCell>
                    <TableCell className="text-right">{money(report.inputIgst)}</TableCell>
                    <TableCell className="text-right font-semibold">{money(report.inputTotal)}</TableCell>
                  </TableRow>
                  <TableRow className="bg-muted/40">
                    <TableCell className="font-bold">Net Liability</TableCell>
                    <TableCell className="text-right font-bold">{money(report.netCgst)}</TableCell>
                    <TableCell className="text-right font-bold">{money(report.netSgst)}</TableCell>
                    <TableCell className="text-right font-bold">{money(report.netIgst)}</TableCell>
                    <TableCell className="text-right font-bold">{money(report.netTotal)}</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            )
          )}
        </CardContent>
      </Card>
    </div>
  );
}
