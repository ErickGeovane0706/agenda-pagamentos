import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Usuario } from '../types';

interface AuthState {
    usuario: Usuario | null;
    lojaAtiva: string | null;
    hasHydrated: boolean;
    setAuth: (usuario: Usuario) => void;
    setLojaAtiva: (lojaId: string) => void;
    logout: () => void;
    setHasHydrated: (value: boolean) => void;
}

export const useAuthStore = create<AuthState>()(
    persist(
        (set) => ({
            usuario: null,
            lojaAtiva: null,
            hasHydrated: false,

            setAuth: (usuario) => set({ usuario }),

            setLojaAtiva: (lojaId) => set({ lojaAtiva: lojaId }),

            logout: () => set({ usuario: null, lojaAtiva: null }),

            setHasHydrated: (value) => set({ hasHydrated: value }),
        }),
        { name: 'agenda-auth' }
    )
);

/**
 * `hasHydrated` precisa virar true SEMPRE — inclusive quando a re-hidratação
 * falha. Enquanto ele for false, o ProtectedRoute renderiza LoadingScreen para
 * sempre: tela azul com o spinner, em toda rota protegida, sem saída.
 *
 * Isto fica FORA do `create` de propósito, e é a parte que já quebrou a
 * produção uma vez. Dentro do bloco `persist`, um `useAuthStore.setState(...)`
 * referencia a constante que ainda está sendo inicializada — e como o
 * localStorage é síncrono, o zustand hidrata durante o próprio `create()`, ou
 * seja, ANTES de a constante existir. O resultado é um ReferenceError que o
 * zustand engole em silêncio: nada no console, e o app inteiro preso na tela
 * de carregando.
 *
 * A API `persist` abaixo existe justamente para isso e não tem esse problema.
 */
// Incondicional: com localStorage a hidratação é SÍNCRONA, então ao chegar
// nesta linha ela já terminou — tendo dado certo ou não. É o que cobre o caso
// que derrubou o app: `agenda-auth` corrompido faz a hidratação lançar, e aí
// nem `onFinishHydration` dispara. O usuário perde a sessão salva (inevitável,
// o dado está ilegível), mas entra no app e faz login de novo — em vez de ficar
// preso numa tela azul sem saída.
useAuthStore.setState({ hasHydrated: true });

// Rede de segurança para storage assíncrono, caso um dia deixe de ser o
// localStorage: aí a linha acima roda antes da hidratação terminar.
useAuthStore.persist.onFinishHydration(() => {
    useAuthStore.setState({ hasHydrated: true });
});