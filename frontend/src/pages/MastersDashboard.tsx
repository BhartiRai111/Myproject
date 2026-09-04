import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Coins,
  Globe,
  MapPin,
  Building2,
  Map,
  Flag,
  Ruler,
  Boxes,
  FileText,
  UserSquare2,
  Handshake,
  Tags,
  Package,
  type LucideIcon,
} from 'lucide-react';
import {
  currencyApi,
  countryApi,
  stateApi,
  cityApi,
  zoneApi,
  nationalityApi,
  unitApi,
  itemGroupApi,
  hsnApi,
  employeeApi,
  partyApi,
} from '../api/mastersApi';
import { categoryApi } from '../api/categoryApi';
import { PageHeader } from '@/components/PageHeader';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';

interface MasterCard {
  key: string;
  name: string;
  description: string;
  icon: LucideIcon;
  path: string;
  loadCount: () => Promise<number>;
}

const CARDS: MasterCard[] = [
  {
    key: 'currency',
    name: 'Currency',
    description: 'Currencies used across StoreHub',
    icon: Coins,
    path: '/masters/currencies',
    loadCount: () => currencyApi.list({ size: 1 }).then((r) => r.data.totalElements),
  },
  {
    key: 'country',
    name: 'Country',
    description: 'Countries for addresses and tax reference',
    icon: Globe,
    path: '/masters/countries',
    loadCount: () => countryApi.list({ size: 1 }).then((r) => r.data.totalElements),
  },
  {
    key: 'state',
    name: 'State',
    description: 'States linked to a country',
    icon: MapPin,
    path: '/masters/states',
    loadCount: () => stateApi.list({ size: 1 }).then((r) => r.data.totalElements),
  },
  {
    key: 'city',
    name: 'City',
    description: 'Cities linked to a state',
    icon: Building2,
    path: '/masters/cities',
    loadCount: () => cityApi.list({ size: 1 }).then((r) => r.data.totalElements),
  },
  {
    key: 'zone',
    name: 'Zone',
    description: 'Distribution / sales zones',
    icon: Map,
    path: '/masters/zones',
    loadCount: () => zoneApi.list({ size: 1 }).then((r) => r.data.totalElements),
  },
  {
    key: 'nationality',
    name: 'Nationality',
    description: 'Nationalities linked to a country',
    icon: Flag,
    path: '/masters/nationalities',
    loadCount: () => nationalityApi.list({ size: 1 }).then((r) => r.data.totalElements),
  },
  {
    key: 'unit',
    name: 'Unit',
    description: 'Measurement units for purchase & sale',
    icon: Ruler,
    path: '/masters/units',
    loadCount: () => unitApi.list({ size: 1 }).then((r) => r.data.totalElements),
  },
  {
    key: 'item-category',
    name: 'Item Category',
    description: 'Product categories (shared with Products module)',
    icon: Tags,
    path: '/products',
    loadCount: () => categoryApi.list().then((r) => r.data.length),
  },
  {
    key: 'item-group',
    name: 'Item Group',
    description: 'Groups used to classify items',
    icon: Boxes,
    path: '/masters/item-groups',
    loadCount: () => itemGroupApi.list({ size: 1 }).then((r) => r.data.totalElements),
  },
  {
    key: 'hsn',
    name: 'HSN',
    description: 'HSN codes and applicable tax rates',
    icon: FileText,
    path: '/masters/hsn',
    loadCount: () => hsnApi.list({ size: 1 }).then((r) => r.data.totalElements),
  },
  {
    key: 'employee',
    name: 'Employee',
    description: 'Employees for salesman / relationship manager assignment',
    icon: UserSquare2,
    path: '/masters/employees',
    loadCount: () => employeeApi.list({ size: 1 }).then((r) => r.data.totalElements),
  },
  {
    key: 'item',
    name: 'Item Master',
    description: 'Products with extended item-master details',
    icon: Package,
    path: '/products',
    loadCount: () => Promise.resolve(-1),
  },
  {
    key: 'party',
    name: 'Party',
    description: 'Business parties (suppliers / customers)',
    icon: Handshake,
    path: '/masters/parties',
    loadCount: () => partyApi.list({ size: 1 }).then((r) => r.data.totalElements),
  },
];

export default function MastersDashboard() {
  const navigate = useNavigate();
  const [counts, setCounts] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all(
      CARDS.map(async (card) => {
        try {
          const count = await card.loadCount();
          return [card.key, count] as const;
        } catch {
          return [card.key, -1] as const;
        }
      })
    ).then((entries) => {
      setCounts(Object.fromEntries(entries));
      setLoading(false);
    });
  }, []);

  return (
    <div className="space-y-6">
      <PageHeader title="Masters" description="Manage reference and master data used across StoreHub." />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {CARDS.map((card) => {
          const Icon = card.icon;
          const count = counts[card.key];
          return (
            <Card key={card.key}>
              <CardContent className="flex flex-col gap-3 p-5">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <Icon className="h-5 w-5" />
                  </div>
                  <div>
                    <p className="font-semibold">{card.name}</p>
                    {loading ? (
                      <Skeleton className="mt-1 h-4 w-16" />
                    ) : (
                      count >= 0 && <p className="text-xs text-muted-foreground">{count} records</p>
                    )}
                  </div>
                </div>
                <p className="text-sm text-muted-foreground">{card.description}</p>
                <Button variant="outline" size="sm" className="w-full" onClick={() => navigate(card.path)}>
                  Open
                </Button>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
