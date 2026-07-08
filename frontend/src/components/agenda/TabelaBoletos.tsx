import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Copy, Check, Pencil, Trash2,
  Plus, ExternalLink, Upload, FileX
} from 'lucide-react';
import { useState, useRef } from 'react';
import { format, isPast, parseISO } from 'date-fns';
import { ptBR } from 'date-fns/locale';
import api from '../../api/client';
import { Boleto, StatusBoleto } from '../../types';
import { BadgeStatus } from '../BadgeStatus';
import { ValorMonetario } from '../ValorMonetario';
import { useIsMobile } from '../../hooks/useIsMobile';
import { ModalBoleto } from './ModalBoleto';
import { CardBoleto } from './CardBoleto';
import { Pagination } from '../Pagination';
import { SkeletonTable } from '../Skeleton';
import { clsx } from 'clsx';
import { copiarTexto } from '../../utils/clipboard';
import { usePaginaInicialPendente } from '../../hooks/usePaginaInicialPendente';
import { BotaoPagar } from './BotaoPagar';

import type { FiltrosAgenda } from '../../types';

export function TabelaBoletos({
  lojaId,
  filtros
}: {
  lojaId: string;
  filtros: FiltrosAgenda;
}) {
  const isMobile = useIsMobile();
  const queryClient = useQueryClient();
  const [copiado, setCopiado] = useState<string | null>(null);
  const [modalAberto, setModalAberto] = useState(false);
  const [boletoEditando, setBoletoEditando] = useState<Boleto | null>(null);
  const uploadRef = useRef<HTMLInputElement>(null);
  const [boletoUpload, setBoletoUpload] = useState<string | null>(null);
  const pageSize = 15;
  const [page, setPage] = usePaginaInicialPendente('boletos', lojaId, filtros, pageSize);

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['boletos', lojaId, filtros, page, pageSize],
    queryFn: () => api.get('/boletos', {
      params: {
        lojaId,
        status: filtros.status || undefined,
        de: filtros.de || undefined,
        ate: filtros.ate || undefined,
        fornecedor: filtros.nome || undefined,
        page,
        size: pageSize,
      }
    }).then(r => r.data),
  });

  const boletos = pageData?.content ?? pageData ?? [];

  const mudarStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: StatusBoleto }) =>
      api.patch(`/boletos/${id}/status`, { status }),
    onSuccess: (_, variables) => {
      queryClient.setQueryData(
        ['boletos', lojaId, filtros, page, pageSize],
        (old: any) => {
          if (!old?.content) return old;
          return {
            ...old,
            content: old.content.map((b: Boleto) =>
              b.id === variables.id ? { ...b, status: variables.status } : b
            ),
          };
        }
      );
    },
  });

  const excluirMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/boletos/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['boletos', lojaId] }),
    onError: () => {},
  });

  const uploadMutation = useMutation({
    mutationFn: ({ id, arquivo }: { id: string; arquivo: File }) => {
      const form = new FormData();
      form.append('arquivo', arquivo);
      return api.post(`/boletos/${id}/arquivo`, form, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['boletos', lojaId] }),
    onError: () => {},
  });

  const deletarArquivoMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/boletos/${id}/arquivo`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['boletos', lojaId] }),
  });

  const abrirArquivo = async (boletoId: string) => {
    try {
      const { data } = await api.get(`/boletos/${boletoId}/arquivo`);
      window.open(data.url, '_blank');
    } catch {}
  };

  const copiarCodigo = (codigo: string, id: string) => {
    copiarTexto(codigo);
    setCopiado(id);
    setTimeout(() => setCopiado(null), 2000);
  };

  const handleUpload = (boletoId: string) => {
    setBoletoUpload(boletoId);
    uploadRef.current?.click();
  };

  const onArquivoSelecionado = (e: React.ChangeEvent<HTMLInputElement>) => {
    const arquivo = e.target.files?.[0];
    if (arquivo && boletoUpload) {
      uploadMutation.mutate({ id: boletoUpload, arquivo });
    }
    e.target.value = '';
  };

  const proximoStatus = (atual: StatusBoleto): StatusBoleto => {
    if (atual === 'PENDENTE') return 'PAGO';
    if (atual === 'PAGO') return 'PENDENTE';
    return atual;
  };

  if (isLoading) return <SkeletonTable rows={4} />;

  return (
    <div>
      <div className="flex justify-end mb-4">
        <button
          onClick={() => { setBoletoEditando(null); setModalAberto(true); }}
          className="flex items-center gap-2 bg-[#0c4a6e] hover:bg-[#0a3d5c]
                     text-white text-sm font-medium px-4 py-2 rounded-xl
                     transition-colors shadow-sm"
        >
          <Plus className="w-4 h-4" />
          Novo boleto
        </button>
      </div>

      {isMobile ? (
        <div className="space-y-3">
          {boletos.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-slate-400 text-sm">Nenhum boleto cadastrado</p>
              <button
                onClick={() => { setBoletoEditando(null); setModalAberto(true); }}
                className="mt-3 text-[#0c4a6e] text-sm hover:underline"
              >
                Adicionar o primeiro boleto
              </button>
            </div>
          ) : (
            <>
              {boletos.map((boleto: Boleto) => (
                <CardBoleto
                  key={boleto.id}
                  boleto={boleto}
                  onCopiar={copiarCodigo}
                  onEditar={() => { setBoletoEditando(boleto); setModalAberto(true); }}
                  onExcluir={() => excluirMutation.mutate(boleto.id)}
                  onUpload={() => handleUpload(boleto.id)}
                  onDeletarArquivo={() => deletarArquivoMutation.mutate(boleto.id)}
                  onMudarStatus={(status) => mudarStatusMutation.mutate({ id: boleto.id, status })}
                  copiado={copiado}
                />
              ))}
              <Pagination
                page={page}
                totalPages={pageData?.totalPages ?? 1}
                total={pageData?.totalElements ?? boletos.length}
                pageSize={pageSize}
                onChange={(p) => { setPage(p); }}
              />
            </>
          )}
        </div>
      ) : (
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
          {boletos.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-slate-400 text-sm">Nenhum boleto cadastrado</p>
              <button
                onClick={() => { setBoletoEditando(null); setModalAberto(true); }}
                className="mt-3 text-[#0c4a6e] text-sm hover:underline"
              >
                Adicionar o primeiro boleto
              </button>
            </div>
          ) : (
            <>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-slate-50 border-b border-slate-100">
                      <th className="text-left px-4 py-3 font-medium text-slate-500 text-xs uppercase tracking-wide">
                        Fornecedor
                      </th>
                      <th className="text-right px-4 py-3 font-medium text-slate-500 text-xs uppercase tracking-wide">
                        Valor
                      </th>
                      <th className="text-left px-4 py-3 font-medium text-slate-500 text-xs uppercase tracking-wide">
                        Vencimento
                      </th>
                      <th className="text-left px-4 py-3 font-medium text-slate-500 text-xs uppercase tracking-wide">
                        Status
                      </th>
                      <th className="text-left px-4 py-3 font-medium text-slate-500 text-xs uppercase tracking-wide">
                        Código de barras
                      </th>
                      <th className="px-4 py-3 w-24"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50">
                    {boletos.map((boleto: Boleto, i: number) => {
                      const vencido = boleto.status === 'PENDENTE' &&
                        isPast(parseISO(boleto.vencimento + 'T23:59:59'));
                      return (
                        <tr
                          key={boleto.id}
                          className={clsx(
                            'hover:bg-slate-50/50 transition-colors group',
                            i % 2 === 0 ? 'bg-white' : 'bg-slate-50/30'
                          )}
                        >
                          <td className="px-4 py-3">
                            <span className="font-medium text-slate-800">{boleto.fornecedor}</span>
                            {boleto.observacoes && (
                              <p className="text-xs text-slate-400 truncate max-w-[200px]">
                                {boleto.observacoes}
                              </p>
                            )}
                          </td>

                          <td className="px-4 py-3 text-right">
                            <ValorMonetario
                              valor={boleto.valor}
                              className="font-semibold text-[#0c4a6e]"
                            />
                          </td>

                          <td className="px-4 py-3">
                            <span className={clsx(
                              'font-medium',
                              vencido ? 'text-red-600' : 'text-slate-700'
                            )}>
                              {format(parseISO(boleto.vencimento), 'dd/MM/yyyy', { locale: ptBR })}
                            </span>
                            {vencido && (
                              <span className="ml-1.5 text-xs text-red-500">(vencido)</span>
                            )}
                          </td>

                          <td className="px-4 py-3">
                            <button
                              onClick={() => mudarStatusMutation.mutate({
                                id: boleto.id,
                                status: proximoStatus(boleto.status)
                              })}
                              title="Clique para mudar o status"
                              className="hover:opacity-75 transition-opacity"
                            >
                              <BadgeStatus status={boleto.status} />
                            </button>
                          </td>

                          <td className="px-4 py-3">
                            {boleto.codigoBarras ? (
                              <div className="flex items-center gap-2">
                                <span className="text-xs text-slate-500 font-mono truncate max-w-[180px]">
                                  {boleto.codigoBarras.substring(0, 20)}...
                                </span>
                                <BotaoPagar
                                  tipo="boleto"
                                  codigo={boleto.codigoBarras}
                                  fornecedor={boleto.fornecedor}
                                  valor={boleto.valor}
                                  vencimento={boleto.vencimento}
                                  className="px-2.5 py-1 text-xs"
                                />
                              </div>
                            ) : (
                              <span className="text-slate-300 text-xs">—</span>
                            )}
                          </td>

                          <td className="px-4 py-3">
                            <div className="flex items-center gap-1">
                              {boleto.arquivoKey ? (
                                <>
                                  <button
                                    onClick={() => abrirArquivo(boleto.id)}
                                    title={boleto.nomeArquivo || 'Ver arquivo'}
                                    className="p-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-500"
                                  >
                                    <ExternalLink className="w-4 h-4" />
                                  </button>
                                  <button
                                    onClick={() => { if (confirm('Remover arquivo?')) deletarArquivoMutation.mutate(boleto.id); }}
                                    title="Remover arquivo"
                                    className="p-2 rounded-lg bg-red-100 hover:bg-red-200 text-red-600"
                                  >
                                    <FileX className="w-4 h-4" />
                                  </button>
                                </>
                              ) : (
                                <button
                                  onClick={() => handleUpload(boleto.id)}
                                  title="Anexar arquivo"
                                  className="p-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-500"
                                >
                                  <Upload className="w-4 h-4" />
                                </button>
                              )}

                              {boleto.codigoBarras && (
                                <button
                                  onClick={() => copiarCodigo(boleto.codigoBarras!, boleto.id)}
                                  title="Copiar código de barras"
                                  className={clsx(
                                    'p-2 rounded-lg transition-colors',
                                    copiado === boleto.id
                                      ? 'bg-emerald-100 text-emerald-700'
                                      : 'bg-slate-100 hover:bg-slate-200 text-slate-500'
                                  )}
                                >
                                  {copiado === boleto.id ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                                </button>
                              )}

                              <button
                                onClick={() => { setBoletoEditando(boleto); setModalAberto(true); }}
                                title="Editar"
                                className="p-2 rounded-lg bg-[#e0f2fe] hover:bg-[#e0f2fe] text-[#0c4a6e]"
                              >
                                <Pencil className="w-4 h-4" />
                              </button>

                              <button
                                onClick={() => { if (confirm('Excluir este boleto?')) excluirMutation.mutate(boleto.id); }}
                                title="Excluir"
                                className="p-2 rounded-lg bg-red-100 hover:bg-red-200 text-red-600"
                              >
                                <Trash2 className="w-4 h-4" />
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
              <Pagination
                page={page}
                totalPages={pageData?.totalPages ?? 1}
                total={pageData?.totalElements ?? boletos.length}
                pageSize={pageSize}
                onChange={(p) => { setPage(p); }}
              />
            </>
          )}
        </div>
      )}

      <input
        type="file"
        ref={uploadRef}
        className="hidden"
        onChange={onArquivoSelecionado}
        accept=".pdf,.jpg,.jpeg,.png,.webp"
      />

      {modalAberto && (
        <ModalBoleto
          boleto={boletoEditando}
          lojaId={lojaId}
          onFechar={() => setModalAberto(false)} onSalvo={() => { setModalAberto(false); setBoletoEditando(null); queryClient.invalidateQueries({ queryKey: ['boletos', lojaId] }); }}
        />
      )}
    </div>
  );
}
