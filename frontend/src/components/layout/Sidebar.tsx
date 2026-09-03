import { NavLink } from 'react-router-dom';
import { ChevronsLeft, ChevronsRight, Sparkles, Store } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuth } from '@/context/AuthContext';
import { NAV_ITEMS } from './nav-items';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent } from '@/components/ui/sheet';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';

interface SidebarNavProps {
  collapsed: boolean;
  onNavigate?: () => void;
}

function SidebarNav({ collapsed, onNavigate }: SidebarNavProps) {
  const { user } = useAuth();
  const items = NAV_ITEMS.filter((item) => !item.allowedRoles || (user && item.allowedRoles.includes(user.role)));

  return (
    <nav className="flex flex-col gap-1 px-3">
      {items.map((item) => {
        const Icon = item.icon;

        if (!item.enabled) {
          const disabledLink = (
            <div
              key={item.path}
              className={cn(
                'flex cursor-not-allowed items-center gap-3 rounded-lg px-3 py-2 text-sm text-muted-foreground/60',
                collapsed && 'justify-center px-0'
              )}
            >
              <Icon className="h-[18px] w-[18px] shrink-0" />
              {!collapsed && <span className="truncate">{item.label}</span>}
              {!collapsed && (
                <span className="ml-auto rounded-full bg-muted px-1.5 py-0.5 text-[10px] font-medium">Soon</span>
              )}
            </div>
          );

          return collapsed ? (
            <Tooltip key={item.path}>
              <TooltipTrigger asChild>{disabledLink}</TooltipTrigger>
              <TooltipContent side="right">{item.label} (Coming soon)</TooltipContent>
            </Tooltip>
          ) : (
            disabledLink
          );
        }

        const link = (
          <NavLink
            key={item.path}
            to={item.path}
            end
            onClick={onNavigate}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                'hover:bg-accent hover:text-accent-foreground',
                isActive ? 'bg-accent text-accent-foreground' : 'text-foreground/80',
                collapsed && 'justify-center px-0'
              )
            }
          >
            <Icon className="h-[18px] w-[18px] shrink-0" />
            {!collapsed && <span className="truncate">{item.label}</span>}
          </NavLink>
        );

        return collapsed ? (
          <Tooltip key={item.path}>
            <TooltipTrigger asChild>{link}</TooltipTrigger>
            <TooltipContent side="right">{item.label}</TooltipContent>
          </Tooltip>
        ) : (
          link
        );
      })}
    </nav>
  );
}

function Brand({ collapsed }: { collapsed: boolean }) {
  return (
    <div className={cn('flex h-16 items-center gap-2 px-4', collapsed && 'justify-center px-0')}>
      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary text-primary-foreground shadow-sm">
        <Store className="h-5 w-5" />
      </div>
      {!collapsed && (
        <div className="flex flex-col leading-none">
          <span className="text-base font-bold tracking-tight">StoreHub</span>
          <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
            <Sparkles className="h-3 w-3" /> Store management
          </span>
        </div>
      )}
    </div>
  );
}

interface SidebarProps {
  collapsed: boolean;
  onToggleCollapse: () => void;
  mobileOpen: boolean;
  onMobileOpenChange: (open: boolean) => void;
}

export default function Sidebar({ collapsed, onToggleCollapse, mobileOpen, onMobileOpenChange }: SidebarProps) {
  return (
    <>
      {/* Desktop sidebar */}
      <aside
        className={cn(
          'sticky top-0 hidden h-svh shrink-0 flex-col border-r border-border bg-card transition-[width] duration-200 ease-in-out lg:flex',
          collapsed ? 'w-[4.5rem]' : 'w-64'
        )}
      >
        <Brand collapsed={collapsed} />
        <div className="flex-1 overflow-y-auto py-2 scrollbar-thin">
          <SidebarNav collapsed={collapsed} />
        </div>
        <div className="border-t border-border p-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={onToggleCollapse}
            className={cn('w-full justify-start gap-2 text-muted-foreground', collapsed && 'justify-center')}
          >
            {collapsed ? <ChevronsRight className="h-4 w-4" /> : <ChevronsLeft className="h-4 w-4" />}
            {!collapsed && <span>Collapse</span>}
          </Button>
        </div>
      </aside>

      {/* Mobile drawer */}
      <Sheet open={mobileOpen} onOpenChange={onMobileOpenChange}>
        <SheetContent side="left" className="w-72 p-0">
          <Brand collapsed={false} />
          <div className="flex-1 overflow-y-auto py-2">
            <SidebarNav collapsed={false} onNavigate={() => onMobileOpenChange(false)} />
          </div>
        </SheetContent>
      </Sheet>
    </>
  );
}
