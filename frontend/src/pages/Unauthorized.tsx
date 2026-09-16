import { Link } from 'react-router-dom';
import { ShieldX } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';
import { Button } from '@/components/ui/button';

export default function Unauthorized() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4">
      <EmptyState
        icon={ShieldX}
        title="Access denied"
        description="You don't have permission to view this page. If you believe this is a mistake, contact an administrator."
      />
      <Button asChild>
        <Link to="/dashboard">Back to Dashboard</Link>
      </Button>
    </div>
  );
}
