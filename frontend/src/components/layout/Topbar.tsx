import { useEffect, useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { AlertTriangle, Bell, LogOut, Menu, Moon, OctagonAlert, Sun } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/components/theme-provider';
import { alertApi } from '../../api/alertApi';
import { AlertItem } from '../../types/alert';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { getPageTitle } from './page-title';
import GlobalSearch from './GlobalSearch';

const ALERT_POLL_MS = 60000;

function formatRole(role: string) {
  return role
    .split('_')
    .map((w) => w.charAt(0) + w.slice(1).toLowerCase())
    .join(' ');
}

function initials(firstName?: string, lastName?: string) {
  return `${firstName?.[0] ?? ''}${lastName?.[0] ?? ''}`.toUpperCase() || 'U';
}

interface TopbarProps {
  onMenuClick: () => void;
}

export default function Topbar({ onMenuClick }: TopbarProps) {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const { title, parent } = getPageTitle(location.pathname);

  const [alerts, setAlerts] = useState<AlertItem[]>([]);

  useEffect(() => {
    const load = () => {
      alertApi
        .list()
        .then((res) => setAlerts(res.data))
        .catch(() => {
          // Alert Center is a convenience surface; a failed fetch should not disrupt navigation.
        });
    };
    load();
    const interval = setInterval(load, ALERT_POLL_MS);
    return () => clearInterval(interval);
  }, []);

  const criticalCount = alerts.filter((a) => a.severity === 'CRITICAL').length;

  const handleLogout = async () => {
    await logout();
    toast.success('Logged out successfully');
    navigate('/login');
  };

  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-3 border-b border-border bg-card/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-card/80 sm:px-6 print:hidden">
      <Button variant="ghost" size="icon" className="lg:hidden" onClick={onMenuClick} aria-label="Open menu">
        <Menu className="h-5 w-5" />
      </Button>

      <div className="min-w-0 flex-1 lg:hidden">
        {parent && (
          <div className="flex items-center gap-1 text-xs text-muted-foreground">
            <span>{parent}</span>
            <span>/</span>
          </div>
        )}
        <h1 className="truncate text-lg font-semibold leading-tight">{title}</h1>
      </div>

      <div className="hidden min-w-0 flex-1 lg:block">
        <GlobalSearch />
      </div>

      <div className="flex items-center gap-1.5 sm:gap-2">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon" className="relative" aria-label="Notifications">
              <Bell className="h-[18px] w-[18px]" />
              {alerts.length > 0 && (
                <span
                  className={`absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-semibold text-white ${
                    criticalCount > 0 ? 'bg-destructive' : 'bg-amber-500'
                  }`}
                >
                  {alerts.length > 9 ? '9+' : alerts.length}
                </span>
              )}
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-80">
            <DropdownMenuLabel className="flex items-center justify-between font-normal">
              <span className="text-sm font-medium">Alerts</span>
              {alerts.length > 0 && (
                <span className="text-xs text-muted-foreground">
                  {criticalCount > 0 ? `${criticalCount} critical` : `${alerts.length} warning${alerts.length === 1 ? '' : 's'}`}
                </span>
              )}
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            {alerts.length === 0 ? (
              <div className="px-2 py-6 text-center text-sm text-muted-foreground">No active alerts</div>
            ) : (
              <div className="max-h-80 overflow-y-auto">
                {alerts.slice(0, 8).map((a, idx) => (
                  <DropdownMenuItem
                    key={idx}
                    className="flex items-start gap-2 whitespace-normal py-2"
                    onClick={() => a.path && navigate(a.path)}
                  >
                    {a.severity === 'CRITICAL' ? (
                      <OctagonAlert className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
                    ) : (
                      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-500" />
                    )}
                    <div className="min-w-0">
                      <p className="text-xs font-medium text-muted-foreground">{a.category}</p>
                      <p className="text-sm leading-snug">{a.message}</p>
                    </div>
                  </DropdownMenuItem>
                ))}
              </div>
            )}
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => navigate('/alerts')} className="justify-center text-sm font-medium">
              View All Alerts
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>

        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="ghost" size="icon" onClick={toggleTheme} aria-label="Toggle theme">
              {theme === 'dark' ? <Sun className="h-[18px] w-[18px]" /> : <Moon className="h-[18px] w-[18px]" />}
            </Button>
          </TooltipTrigger>
          <TooltipContent>{theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}</TooltipContent>
        </Tooltip>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" className="flex items-center gap-2 px-2 sm:px-3">
              <Avatar className="h-8 w-8">
                <AvatarFallback className="bg-primary text-primary-foreground text-xs">
                  {initials(user?.firstName, user?.lastName)}
                </AvatarFallback>
              </Avatar>
              <div className="hidden text-left sm:block">
                <div className="text-sm font-medium leading-none">
                  {user?.firstName} {user?.lastName}
                </div>
                <div className="mt-0.5 text-xs text-muted-foreground">{user ? formatRole(user.role) : ''}</div>
              </div>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel className="font-normal">
              <div className="flex flex-col space-y-1">
                <p className="text-sm font-medium leading-none">
                  {user?.firstName} {user?.lastName}
                </p>
                <p className="text-xs text-muted-foreground">{user?.email}</p>
                {user && (
                  <Badge variant="secondary" className="mt-1 w-fit">
                    {formatRole(user.role)}
                  </Badge>
                )}
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="destructive" onClick={handleLogout}>
              <LogOut className="h-4 w-4" />
              Log out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
