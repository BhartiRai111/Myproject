import { useNavigate } from 'react-router-dom';
import {
  FileSpreadsheet,
  ShoppingBag,
  FileBarChart,
  ArrowUpRight,
  ArrowDownRight,
  Hash,
  Percent,
  Scale,
  GitCompareArrows,
  ArrowRight,
} from 'lucide-react';
import { PageHeader } from '@/components/PageHeader';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';

const SECTIONS = [
  {
    key: 'gstr1',
    title: 'GSTR-1',
    description: 'Outward supplies: B2B, B2C, and HSN-wise summary for a return period.',
    icon: FileSpreadsheet,
    path: '/gst-reports/gstr1',
  },
  {
    key: 'purchase',
    title: 'Purchase GST Report',
    description: 'Every GST-reportable purchase with its Input Tax Credit (ITC) eligibility.',
    icon: ShoppingBag,
    path: '/gst-reports/purchase',
  },
  {
    key: 'gstr3b',
    title: 'GSTR-3B Summary',
    description: 'Outward supplies, ITC, and net liability for a return period — a preparation aid.',
    icon: FileBarChart,
    path: '/gst-reports/gstr3b',
  },
  {
    key: 'output',
    title: 'Output GST Report',
    description: 'GST collected on every reportable sale, with CGST/SGST/IGST breakup.',
    icon: ArrowUpRight,
    path: '/gst-reports/output',
  },
  {
    key: 'input',
    title: 'Input GST Report',
    description: 'GST paid on every reportable purchase, with ITC eligibility per row.',
    icon: ArrowDownRight,
    path: '/gst-reports/input',
  },
  {
    key: 'hsn',
    title: 'HSN Summary',
    description: 'Outward and inward supplies aggregated by HSN code.',
    icon: Hash,
    path: '/gst-reports/hsn',
  },
  {
    key: 'tax-rate',
    title: 'Tax Rate Summary',
    description: 'Outward and inward supplies aggregated by GST rate.',
    icon: Percent,
    path: '/gst-reports/tax-rate',
  },
  {
    key: 'liability',
    title: 'GST Liability',
    description: 'Output tax vs. Input Tax Credit, and the resulting net GST liability.',
    icon: Scale,
    path: '/gst-reports/liability',
  },
  {
    key: 'reconciliation',
    title: 'GST Reconciliation',
    description: 'Compares source transactions against the GST reporting dataset: matched, mismatched, or missing.',
    icon: GitCompareArrows,
    path: '/gst-reports/reconciliation',
  },
];

export default function GstReportsHub() {
  const navigate = useNavigate();

  return (
    <div className="space-y-6">
      <PageHeader
        title="GST Reports"
        description="GST tax calculation is not GST return reporting: these reports include only POSTED transactions explicitly flagged for GST reporting. Kacchi Sale/Purchase Challans and draft transactions never appear here, even though GST was calculated on them in full."
      />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {SECTIONS.map((section) => (
          <Card key={section.key} className="flex flex-col">
            <CardContent className="flex flex-1 flex-col gap-3 p-5">
              <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <section.icon className="h-5 w-5" />
              </div>
              <p className="font-semibold">{section.title}</p>
              <p className="flex-1 text-sm text-muted-foreground">{section.description}</p>
              <Button variant="outline" className="w-full" onClick={() => navigate(section.path)}>
                Open <ArrowRight className="h-4 w-4" />
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
