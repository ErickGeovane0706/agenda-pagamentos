import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import api from '../api/client';
import type { FiltrosAgenda } from '../types';

type Recurso = 'boletos' | 'pix' | 'cheques';

/**
 * Gerencia a página atual da tabela, abrindo automaticamente na primeira página
 * que contém item pendente/vencido (calculada pelo backend em /{recurso}/pagina-pendente).
 *
 * O pulo é aplicado uma única vez por combinação loja+filtros; navegação manual do
 * usuário não é sobrescrita. Sem pendência, permanece na página 0.
 */
export function usePaginaInicialPendente(
  recurso: Recurso,
  lojaId: string,
  filtros: FiltrosAgenda,
  pageSize: number,
) {
  const [page, setPage] = useState(0);
  const aplicadaRef = useRef<string | null>(null);

  const { data: paginaPendente } = useQuery({
    queryKey: [recurso, 'paginaPendente', lojaId, filtros, pageSize],
    queryFn: () => api.get(`/${recurso}/pagina-pendente`, {
      params: {
        lojaId,
        status: filtros.status || undefined,
        de: filtros.de || undefined,
        ate: filtros.ate || undefined,
        fornecedor: filtros.nome || undefined,
        size: pageSize,
      },
    }).then(r => r.data.page as number),
  });

  // Troca de loja/filtros: volta pra página 0 na hora (evita ficar numa página
  // fora do novo conjunto enquanto o backend responde).
  useEffect(() => {
    setPage(0);
  }, [lojaId, filtros]);

  // Aplica o pulo uma única vez por combinação loja+filtros. Atrelar à combinação
  // (e não ao valor) garante o pulo mesmo quando dois filtros têm a mesma página.
  useEffect(() => {
    if (paginaPendente == null) return;
    const chave = `${lojaId}|${JSON.stringify(filtros)}`;
    if (aplicadaRef.current === chave) return;
    aplicadaRef.current = chave;
    if (paginaPendente > 0) setPage(paginaPendente);
  }, [lojaId, filtros, paginaPendente]);

  return [page, setPage] as const;
}
