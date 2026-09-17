import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { authApi } from '../api/authApi';
import { userApi } from '../api/userApi';
import { LoginPayload, RegisterPayload, User } from '../types/user';
import { Store } from '../types/store';

interface AuthContextValue {
  user: User | null;
  loading: boolean;
  permissions: Set<string>;
  hasPermission: (permission: string) => boolean;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  /** Every store this user may act on — see StoreSwitcher (Multi-Store spec section 12/45). */
  myStores: Store[];
  switchStore: (storeId: number) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const TOKEN_KEY = 'storehub_token';
const USER_KEY = 'storehub_user';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [permissions, setPermissions] = useState<Set<string>>(new Set());
  const [myStores, setMyStores] = useState<Store[]>([]);

  const loadPermissions = (u: User) => {
    userApi
      .getEffectivePermissions(u.id)
      .then((res) => setPermissions(new Set(res.data)))
      .catch(() => setPermissions(new Set()));
  };

  /**
   * Loads the caller's accessible stores and, when they have exactly one and no current-store
   * selection yet, silently picks it for them — a single-store business should never have to
   * manually select "the only store that exists" before their first Sale/Purchase (Multi-Store
   * spec section 76's backward-compatibility intent). A multi-store user with none selected is
   * left alone: the StoreSwitcher is right there in the Topbar, and the backend's own clear
   * "a store must be selected" error is a reasonable prompt until they pick one.
   */
  const loadMyStores = (forUser: User) => {
    authApi
      .getMyStores()
      .then(async (res) => {
        setMyStores(res.data);
        if (res.data.length === 1 && !forUser.currentStoreId) {
          try {
            const updated = await authApi.setCurrentStore(res.data[0].id);
            setUser(updated.data);
            localStorage.setItem(USER_KEY, JSON.stringify(updated.data));
          } catch {
            // Best-effort convenience only — a failed auto-select just leaves manual selection required.
          }
        }
      })
      .catch(() => setMyStores([]));
  };

  useEffect(() => {
    const token = localStorage.getItem(TOKEN_KEY);
    const storedUser = localStorage.getItem(USER_KEY);

    if (token && storedUser) {
      const parsed = JSON.parse(storedUser);
      setUser(parsed);
      loadPermissions(parsed);
      loadMyStores(parsed);
      authApi
        .me()
        .then((res) => {
          setUser(res.data);
          localStorage.setItem(USER_KEY, JSON.stringify(res.data));
          loadPermissions(res.data);
        })
        .catch(() => {
          localStorage.removeItem(TOKEN_KEY);
          localStorage.removeItem(USER_KEY);
          setUser(null);
          setPermissions(new Set());
          setMyStores([]);
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = async (payload: LoginPayload) => {
    const res = await authApi.login(payload);
    localStorage.setItem(TOKEN_KEY, res.data.token);
    localStorage.setItem(USER_KEY, JSON.stringify(res.data.user));
    setUser(res.data.user);
    loadPermissions(res.data.user);
    loadMyStores(res.data.user);
  };

  const register = async (payload: RegisterPayload) => {
    await authApi.register(payload);
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } finally {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      setUser(null);
      setPermissions(new Set());
      setMyStores([]);
    }
  };

  const hasPermission = (permission: string) => permissions.has(permission);

  const switchStore = async (storeId: number) => {
    const res = await authApi.setCurrentStore(storeId);
    setUser(res.data);
    localStorage.setItem(USER_KEY, JSON.stringify(res.data));
  };

  return (
    <AuthContext.Provider
      value={{ user, loading, permissions, hasPermission, login, register, logout, myStores, switchStore }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
