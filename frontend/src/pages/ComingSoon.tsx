import { Construction } from 'lucide-react';
import { EmptyState } from '@/components/EmptyState';

export default function ComingSoon() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center">
      <EmptyState
        icon={Construction}
        title="Coming soon"
        description="This module is being built and will be available in a future update."
      />
    </div>
  );
}
