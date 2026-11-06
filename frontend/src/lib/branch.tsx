import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Branch } from '../api/types';
import { useAuth } from './auth';

interface BranchState {
  branches: Branch[];
  /** Tanlangan filial (null — barcha filiallar) */
  selected: number | null;
  setSelected: (id: number | null) => void;
  /** Foydalanuvchi filialni almashtira oladimi (ALL_BRANCHES yoki filialga biriktirilmagan) */
  canSwitch: boolean;
  /** So'rovlarga qo'shiladigan branchId (backend baribir o'z filiali bilan cheklaydi) */
  param: number | undefined;
}

const BranchContext = createContext<BranchState | null>(null);
const KEY = 'branch';

function read(): number | null {
  try {
    const v = localStorage.getItem(KEY);
    return v ? Number(v) : null;
  } catch {
    return null;
  }
}

export function BranchProvider({ children }: { children: ReactNode }) {
  const { user, can } = useAuth();
  const [selected, setSelected] = useState<number | null>(read);
  const { data: branches = [] } = useQuery({
    queryKey: ['branches'],
    queryFn: () => api.get<Branch[]>('/api/branches'),
    enabled: !!user,
    staleTime: 60_000,
  });
  const canSwitch = !!user && (can('ALL_BRANCHES') || !user.branchId);

  useEffect(() => {
    try {
      if (selected == null) localStorage.removeItem(KEY);
      else localStorage.setItem(KEY, String(selected));
    } catch {
      /* ignore */
    }
  }, [selected]);

  // o'chirilgan yoki mavjud bo'lmagan filial tanlangan bo'lib qolmasin
  const valid = selected != null && branches.length > 0 && !branches.some((b) => b.id === selected) ? null : selected;

  const value = useMemo<BranchState>(() => ({
    branches, selected: canSwitch ? valid : user?.branchId ?? null, setSelected, canSwitch,
    param: canSwitch ? valid ?? undefined : undefined,
  }), [branches, valid, canSwitch, user?.branchId]);

  return <BranchContext.Provider value={value}>{children}</BranchContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useBranch(): BranchState {
  const ctx = useContext(BranchContext);
  if (!ctx) throw new Error('useBranch BranchProvider ichida ishlatilishi kerak');
  return ctx;
}
