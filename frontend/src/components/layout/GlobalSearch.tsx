import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Loader2, Search, X } from 'lucide-react';
import { globalSearchApi } from '@/api/globalSearchApi';
import { GlobalSearchResultItem } from '@/types/globalSearch';
import { Input } from '@/components/ui/input';

const DEBOUNCE_MS = 300;

export default function GlobalSearch() {
  const navigate = useNavigate();
  const containerRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  const [query, setQuery] = useState('');
  const [results, setResults] = useState<GlobalSearchResultItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    const onClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current);

    const trimmed = query.trim();
    if (trimmed.length < 2) {
      setResults([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    debounceRef.current = setTimeout(async () => {
      try {
        const res = await globalSearchApi.search(trimmed);
        setResults(res.data.results);
      } catch {
        setResults([]);
      } finally {
        setLoading(false);
      }
    }, DEBOUNCE_MS);

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query]);

  const handleSelect = (item: GlobalSearchResultItem) => {
    setOpen(false);
    setQuery('');
    setResults([]);
    navigate(item.path);
  };

  const handleClear = () => {
    setQuery('');
    setResults([]);
  };

  return (
    <div ref={containerRef} className="relative hidden w-full max-w-sm sm:block">
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
      <Input
        placeholder="Search sales, purchases, products, notes…"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onFocus={() => setOpen(true)}
        className="pl-9 pr-9"
      />
      {loading ? (
        <Loader2 className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-muted-foreground" />
      ) : (
        query.length > 0 && (
          <button
            type="button"
            onClick={handleClear}
            aria-label="Clear search"
            className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
          >
            <X className="h-4 w-4" />
          </button>
        )
      )}

      {open && query.trim().length >= 2 && (
        <div className="absolute left-0 right-0 top-full z-40 mt-1 max-h-96 overflow-y-auto rounded-md border border-border bg-popover shadow-lg">
          {loading && results.length === 0 ? (
            <div className="p-4 text-center text-sm text-muted-foreground">Searching…</div>
          ) : results.length === 0 ? (
            <div className="p-4 text-center text-sm text-muted-foreground">No results for &quot;{query.trim()}&quot;</div>
          ) : (
            <ul className="py-1">
              {results.map((item) => (
                <li key={`${item.category}-${item.id}`}>
                  <button
                    type="button"
                    onClick={() => handleSelect(item)}
                    className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-accent hover:text-accent-foreground"
                  >
                    <span className="min-w-0">
                      <span className="block truncate font-medium">{item.title}</span>
                      {item.subtitle && <span className="block truncate text-xs text-muted-foreground">{item.subtitle}</span>}
                    </span>
                    <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-[10px] font-medium text-muted-foreground">
                      {item.category}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
