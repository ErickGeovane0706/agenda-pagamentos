import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Usuario } from '../types';

interface AuthState {
  usuario: Usuario | null;
  lojaAtiva: string | null;
  setAuth: (usuario: Usuario) => void;
  setLojaAtiva: (lojaId: string) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      usuario: null,
      lojaAtiva: null,

      setAuth: (usuario) => set({ usuario }),

      setLojaAtiva: (lojaId) => set({ lojaAtiva: lojaId }),

      logout: () => set({ usuario: null, lojaAtiva: null }),
    }),
    { name: 'agenda-auth' }
  )
);
