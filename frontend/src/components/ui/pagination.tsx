import { ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';

interface PaginationProps {
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  className?: string;
}

function getPageWindow(page: number, totalPages: number): (number | 'ellipsis')[] {
  const pages: (number | 'ellipsis')[] = [];
  const windowSize = 1;
  const start = Math.max(0, page - windowSize);
  const end = Math.min(totalPages - 1, page + windowSize);

  if (start > 0) {
    pages.push(0);
    if (start > 1) pages.push('ellipsis');
  }
  for (let i = start; i <= end; i++) pages.push(i);
  if (end < totalPages - 1) {
    if (end < totalPages - 2) pages.push('ellipsis');
    pages.push(totalPages - 1);
  }
  return pages;
}

export function Pagination({ page, totalPages, onPageChange, className }: PaginationProps) {
  if (totalPages <= 1) return null;
  const pages = getPageWindow(page, totalPages);

  return (
    <nav className={cn('flex items-center justify-end gap-1', className)} aria-label="Pagination">
      <Button
        variant="outline"
        size="icon"
        className="h-8 w-8"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
        aria-label="Previous page"
      >
        <ChevronLeft className="h-4 w-4" />
      </Button>

      {pages.map((p, idx) =>
        p === 'ellipsis' ? (
          <span key={`e-${idx}`} className="px-2 text-sm text-muted-foreground">
            …
          </span>
        ) : (
          <Button
            key={p}
            variant={p === page ? 'default' : 'outline'}
            size="icon"
            className="h-8 w-8"
            onClick={() => onPageChange(p)}
          >
            {p + 1}
          </Button>
        )
      )}

      <Button
        variant="outline"
        size="icon"
        className="h-8 w-8"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
        aria-label="Next page"
      >
        <ChevronRight className="h-4 w-4" />
      </Button>
    </nav>
  );
}
