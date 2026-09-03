import { Skeleton } from '@/components/ui/skeleton';
import { TableBody, TableCell, TableRow } from '@/components/ui/table';

interface TableSkeletonProps {
  rows?: number;
  columns: number;
}

export function TableSkeleton({ rows = 5, columns }: TableSkeletonProps) {
  return (
    <TableBody>
      {Array.from({ length: rows }).map((_, r) => (
        <TableRow key={r}>
          {Array.from({ length: columns }).map((_, c) => (
            <TableCell key={c}>
              <Skeleton className="h-4 w-full max-w-[10rem]" />
            </TableCell>
          ))}
        </TableRow>
      ))}
    </TableBody>
  );
}
