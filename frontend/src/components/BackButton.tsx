import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface BackButtonProps {
  label?: string;
  onClick: () => void;
  className?: string;
}

export function BackButton({ label = 'Back', onClick, className }: BackButtonProps) {
  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      onClick={onClick}
      className={cn('-ml-2 w-fit gap-1.5 text-muted-foreground hover:text-foreground', className)}
    >
      <ArrowLeft className="h-4 w-4" />
      {label}
    </Button>
  );
}
