import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Pencil, Trash2, Plus,
  ExternalLink, Upload, FileX
} from 'lucide-react';
import { useState, useEffect, useRef } from 'react';
import { format, isPast, parseISO } from 'date-fns';
import { ptBR } from 'date-fns/locale';
import api from '../../api/client';
import { Cheque, StatusCheque } from '../../types';
import { BadgeStatus } from '../BadgeStatus';
import { ValorMonetario } from '../ValorMonetario';
import { useIsMobile } from '../../hooks/useIsMobile';
import { ModalCheque } from './ModalCheque';
import { CardCheque } from './CardCheque';
import { Pagination } from '../Pagination';
import { SkeletonTable } from '../Skeleton';
import { clsx } from 'clsx';

import type { FiltrosAgenda } from '../../types';

export function TabelaCheques({
  lojaId,
  filtros
}: {
  lojaId: string;
  filtros: FiltrosAgenda;
}) {
  const isMobile = useIsMobile();
  const queryClient = useQueryClient();
  const [modalAberto, setModalAberto] = useState(false);
  const [chequeEditando, setChequeEditando] = useState<Cheque | null>(null);
  const uploadRef = useRef<HTMLInputElement>(null);
  const [chequeUpload, setChequeUpload] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const pageSize = 15;

  useEffect(() => { setPage(0); }, [lojaId, filtros]);

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['cheques', lojaId, filtros, page, pageSize],
    queryFn: () => api.get('/cheques', {
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

  const cheques = pageData?.content ?? pageData ?? [];

  const mudarStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: StatusCheque }) =>
      api.patch(`/cheques/${id}/status`, { status }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cheques', lojaId] }),
  });

  const excluirMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/cheques/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cheques', lojaId] }),
  });

  const uploadMutation = useMutation({
    mutationFn: ({ id, arquivo }: { id: string; arquivo: File }) => {
      const form = new FormData();
      form.append('arquivo', arquivo);
      return api.post(`/cheques/${id}/arquivo`, form, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cheques', lojaId] }),
    onError: () => {},
  });

  const deletarArquivoMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/cheques/${id}/arquivo`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cheques', lojaId] }),
  });

  const abrirArquivo = async (chequeId: string) => {
    try {
      const { data } = await api.get(`/cheques/${chequeId}/arquivo`);
      window.open(data.url, '_blank');
    } catch {}
  };

  const handleUpload = (chequeId: string) => {
    setChequeUpload(chequeId);
    uploadRef.current?.click();
  };

  const onArquivoSelecionado = (e: React.ChangeEvent<HTMLInputElement>) => {
    const arquivo = e.target.files?.[0];
    if (arquivo && chequeUpload) {
      uploadMutation.mutate({ id: chequeUpload, arquivo });
    }
    e.target.value = '';
  };

  const proximoStatus = (atual: StatusCheque): StatusCheque => {
    if (atual === 'PENDENTE') return 'COMPENSADO';
    if (atual === 'COMPENSADO') return 'DEVOLVIDO';
    if (atual === 'DEVOLVIDO') return 'PENDENTE';
    return atual;
  };

  if (isLoading) return <SkeletonTable rows={4} />;

  return (
    <div>
      <div className="flex justify-end mb-4">
        <button
          onClick={() => { setChequeEditando(null); setModalAberto(true); }}
          className="flex items-center gap-2 bg-[#0c4a6e] hover:bg-[#0a3d5c]
                     text-white text-sm font-medium px-4 py-2 rounded-xl
                     transition-colors shadow-sm"
        >
          <Plus className="w-4 h-4" />
          Novo cheque
        </button>
      </div>

      {isMobile ? (
        <div className="space-y-3">
          {cheques.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-slate-400 text-sm">Nenhum cheque cadastrado</p>
              <button
                onClick={() => { setChequeEditando(null); setModalAberto(true); }}
                className="mt-3 text-[#0c4a6e] text-sm hover:underline"
              >
                Adicionar o primeiro cheque
              </button>
            </div>
          ) : (
            <>
              {cheques.map((cheque: Cheque) => (
                <CardCheque
                  key={cheque.id}
                  cheque={cheque}
                  onEditar={() => { setChequeEditando(cheque); setModalAberto(true); }}
                  onExcluir={() => excluirMutation.mutate(cheque.id)}
                  onUpload={() => handleUpload(cheque.id)}
                  onDeletarArquivo={() => deletarArquivoMutation.mutate(cheque.id)}
                  onMudarStatus={(status) => mudarStatusMutation.mutate({ id: cheque.id, status })}
                />
              ))}
              <Pagination
                page={page}
                totalPages={pageData?.totalPages ?? 1}
                total={pageData?.totalElements ?? cheques.length}
                pageSize={pageSize}
                onChange={(p) => { setPage(p); }}
              />
            </>
          )}
        </div>
      ) : (
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
          {cheques.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-slate-400 text-sm">Nenhum cheque cadastrado</p>
              <button
                onClick={() => { setChequeEditando(null); setModalAberto(true); }}
                className="mt-3 text-[#0c4a6e] text-sm hover:underline"
              >
                Adicionar o primeiro cheque
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
                        Banco
                      </th>
                      <th className="text-left px-4 py-3 font-medium text-slate-500 text-xs uppercase tracking-wide">
                        Nº Cheque
                      </th>
                      <th className="px-4 py-3 w-24"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50">
                    {cheques.map((cheque: Cheque, i: number) => {
                      const vencido = cheque.status === 'PENDENTE' &&
                        isPast(parseISO(cheque.vencimento + 'T23:59:59'));
                      return (
                        <tr
                          key={cheque.id}
                          className={clsx(
                            'hover:bg-slate-50/50 transition-colors group',
                            i % 2 === 0 ? 'bg-white' : 'bg-slate-50/30'
                          )}
                        >
                          <td className="px-4 py-3">
                            <span className="font-medium text-slate-800">{cheque.fornecedor}</span>
                            {cheque.observacoes && (
                              <p className="text-xs text-slate-400 truncate max-w-[200px]">
                                {cheque.observacoes}
                              </p>
                            )}
                          </td>

                          <td className="px-4 py-3 text-right">
                            <ValorMonetario
                              valor={cheque.valor}
                              className="font-semibold text-[#0c4a6e]"
                            />
                          </td>

                          <td className="px-4 py-3">
                            <span className={clsx(
                              'font-medium',
                              vencido ? 'text-red-600' : 'text-slate-700'
                            )}>
                              {format(parseISO(cheque.vencimento), 'dd/MM/yyyy', { locale: ptBR })}
                            </span>
                            {vencido && (
                              <span className="ml-1.5 text-xs text-red-500">(vencido)</span>
                            )}
                          </td>

                          <td className="px-4 py-3">
                            <button
                              onClick={() => mudarStatusMutation.mutate({
                                id: cheque.id,
                                status: proximoStatus(cheque.status)
                              })}
                              title="Clique para mudar o status"
                              className="hover:opacity-75 transition-opacity"
                            >
                              <BadgeStatus status={cheque.status} />
                            </button>
                          </td>

                          <td className="px-4 py-3">
                            <span className="text-sm text-slate-700">
                              {cheque.bancoNome || '—'}
                            </span>
                          </td>

                          <td className="px-4 py-3">
                            <span className="text-sm text-slate-700 font-mono">
                              {cheque.numeroCheque || '—'}
                            </span>
                          </td>

                          <td className="px-4 py-3">
                            <div className="flex items-center gap-1">
                              {cheque.arquivoKey ? (
                                <>
                                  <button
                                    onClick={() => abrirArquivo(cheque.id)}
                                    title="Ver arquivo"
                                    className="p-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-500"
                                  >
                                    <ExternalLink className="w-4 h-4" />
                                  </button>
                                  <button
                                    onClick={() => { if (confirm('Remover arquivo?')) deletarArquivoMutation.mutate(cheque.id); }}
                                    title="Remover arquivo"
                                    className="p-2 rounded-lg bg-red-100 hover:bg-red-200 text-red-600"
                                  >
                                    <FileX className="w-4 h-4" />
                                  </button>
                                </>
                              ) : (
                                <button
                                  onClick={() => handleUpload(cheque.id)}
                                  title="Anexar arquivo"
                                  className="p-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-500"
                                >
                                  <Upload className="w-4 h-4" />
                                </button>
                              )}

                              <button
                                onClick={() => { setChequeEditando(cheque); setModalAberto(true); }}
                                title="Editar"
                                className="p-2 rounded-lg bg-[#e0f2fe] hover:bg-[#e0f2fe] text-[#0c4a6e]"
                              >
                                <Pencil className="w-4 h-4" />
                              </button>

                              <button
                                onClick={() => { if (confirm('Excluir este cheque?')) excluirMutation.mutate(cheque.id); }}
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
                total={pageData?.totalElements ?? cheques.length}
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
        <ModalCheque
          cheque={chequeEditando}
          lojaId={lojaId}
          onFechar={() => setModalAberto(false)} onSalvo={() => { setModalAberto(false); setChequeEditando(null); queryClient.invalidateQueries({ queryKey: ['cheques', lojaId] }); }}
        />
      )}
    </div>
  );
}
