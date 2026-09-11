import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Plus, Trash2 } from 'lucide-react';
import { accountApi, journalApi } from '../../api/accountingApi';
import { parseApiError } from '../../utils/apiError';
import { Account, JournalLinePayload } from '../../types/accounting';
import { BackButton } from '@/components/BackButton';
import { PageHeader } from '@/components/PageHeader';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Alert, AlertDescription } from '@/components/ui/alert';

interface LineRow {
  accountId: string;
  debitAmount: string;
  creditAmount: string;
  narration: string;
}

const EMPTY_ROW: LineRow = { accountId: '', debitAmount: '', creditAmount: '', narration: '' };

const todayIso = () => new Date().toISOString().slice(0, 10);

export default function JournalEntryForm() {
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [journalDate, setJournalDate] = useState(todayIso());
  const [narration, setNarration] = useState('');
  const [lines, setLines] = useState<LineRow[]>([{ ...EMPTY_ROW }, { ...EMPTY_ROW }]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    accountApi.list({ active: true, size: 500 }).then((res) => setAccounts(res.data.content));
  }, []);

  const addLine = () => setLines((prev) => [...prev, { ...EMPTY_ROW }]);
  const removeLine = (index: number) => setLines((prev) => prev.filter((_, i) => i !== index));

  const updateLine = (index: number, patch: Partial<LineRow>) =>
    setLines((prev) => prev.map((row, i) => (i === index ? { ...row, ...patch } : row)));

  const totalDebit = lines.reduce((sum, l) => sum + (Number(l.debitAmount) || 0), 0);
  const totalCredit = lines.reduce((sum, l) => sum + (Number(l.creditAmount) || 0), 0);
  const balanced = lines.length > 0 && totalDebit > 0 && totalDebit === totalCredit;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    const payloadLines: JournalLinePayload[] = lines
      .filter((l) => l.accountId && (Number(l.debitAmount) > 0 || Number(l.creditAmount) > 0))
      .map((l) => ({
        accountId: Number(l.accountId),
        debitAmount: Number(l.debitAmount) || 0,
        creditAmount: Number(l.creditAmount) || 0,
        narration: l.narration || undefined,
      }));

    if (payloadLines.length < 2) {
      setError('A journal needs at least one debit line and one credit line.');
      return;
    }
    if (totalDebit !== totalCredit) {
      setError(`Journal is not balanced: total debit (₹${totalDebit.toFixed(2)}) does not equal total credit (₹${totalCredit.toFixed(2)}).`);
      return;
    }

    setSubmitting(true);
    try {
      await journalApi.create({ journalDate, narration: narration || undefined, lines: payloadLines });
      toast.success('Journal posted successfully');
      navigate('/accounting/journals');
    } catch (err) {
      setError(parseApiError(err, 'Failed to post journal').message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <BackButton label="Back to Journals" onClick={() => navigate('/accounting/journals')} />

      <PageHeader title="New Journal Entry" description="Manually post a balanced double-entry journal (voucher type: JOURNAL)." />

      <form onSubmit={handleSubmit} className="space-y-6">
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <Card>
          <CardContent className="grid grid-cols-1 gap-4 p-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="journalDate">Journal Date</Label>
              <Input id="journalDate" type="date" required value={journalDate} onChange={(e) => setJournalDate(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="narration">Narration</Label>
              <Input id="narration" value={narration} onChange={(e) => setNarration(e.target.value)} placeholder="Purpose of this journal entry" />
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardContent className="p-0">
            <div className="flex items-center justify-between p-4">
              <p className="text-sm font-medium">Journal Lines</p>
              <Button type="button" size="sm" onClick={addLine}>
                <Plus className="h-4 w-4" /> Add Line
              </Button>
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-64">Account</TableHead>
                  <TableHead>Narration</TableHead>
                  <TableHead className="w-36 text-right">Debit</TableHead>
                  <TableHead className="w-36 text-right">Credit</TableHead>
                  <TableHead className="w-10" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {lines.map((line, index) => (
                  <TableRow key={index}>
                    <TableCell>
                      <Select value={line.accountId} onValueChange={(v) => updateLine(index, { accountId: v })}>
                        <SelectTrigger>
                          <SelectValue placeholder="Select account" />
                        </SelectTrigger>
                        <SelectContent>
                          {accounts.map((a) => (
                            <SelectItem key={a.id} value={String(a.id)}>
                              {a.accountCode} — {a.accountName}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell>
                      <Input
                        value={line.narration}
                        onChange={(e) => updateLine(index, { narration: e.target.value })}
                        placeholder="Optional"
                      />
                    </TableCell>
                    <TableCell>
                      <Input
                        type="number"
                        step="0.01"
                        min={0}
                        className="text-right"
                        value={line.debitAmount}
                        onChange={(e) => updateLine(index, { debitAmount: e.target.value, creditAmount: e.target.value ? '' : line.creditAmount })}
                      />
                    </TableCell>
                    <TableCell>
                      <Input
                        type="number"
                        step="0.01"
                        min={0}
                        className="text-right"
                        value={line.creditAmount}
                        onChange={(e) => updateLine(index, { creditAmount: e.target.value, debitAmount: e.target.value ? '' : line.debitAmount })}
                      />
                    </TableCell>
                    <TableCell>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8 text-muted-foreground hover:text-destructive"
                        onClick={() => removeLine(index)}
                        disabled={lines.length <= 2}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <div className="flex items-center justify-end gap-6 border-t border-border p-4 text-sm font-semibold">
              <span>Total Debit: ₹{totalDebit.toFixed(2)}</span>
              <span>Total Credit: ₹{totalCredit.toFixed(2)}</span>
              <span className={balanced ? 'text-success' : 'text-destructive'}>{balanced ? 'Balanced' : 'Not Balanced'}</span>
            </div>
          </CardContent>
        </Card>

        <div className="flex justify-end gap-3">
          <Button type="button" variant="outline" onClick={() => navigate('/accounting/journals')} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" loading={submitting}>
            Post Journal
          </Button>
        </div>
      </form>
    </div>
  );
}
