export interface GlobalSearchResultItem {
  category: string;
  id: number;
  title: string;
  subtitle?: string;
  path: string;
}

export interface GlobalSearchResponse {
  query: string;
  results: GlobalSearchResultItem[];
}
