import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Copy, Check, Pencil, Trash2, Plus,
  ExternalLink, Upload, FileX
} from 'lucide-react';
import { useState, useRef } from 'react';
import { format, isPast, parseISO } from 'date-fns';
import { ptBR } from 'date-fns/locale';
import api from '../../api/client';
import { PagamentoPix, StatusPix } from '../../types';
import { BadgeStatus } from '../BadgeStatus';
import { ValorMonetario } from '../ValorMonetario';
import { useIsMobile } from '../../hooks/useIsMobile';
import { ModalPix } from './ModalPix';
import { CardPix } from './CardPix';
import { Pagination } from '../Pagination';
import { SkeletonTable } from '../Skeleton';
import { clsx } from 'clsx';
import { copiarTexto } from '../../utils/clipboard';
import { usePaginaInicialPendente } from '../../hooks/usePaginaInicialPendente';
import { BotaoPagar } from './BotaoPagar';

import type { FiltrosAgenda } from '../../types';

export function TabelaPix({
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
  const [pixEditando, setPixEditando] = useState<PagamentoPix | null>(null);
  const uploadRef = useRef<HTMLInputElement>(null);
  const [pixUpload, setPixUpload] = useState<string | null>(null);
  const pageSize = 15;
  const [page, setPage] = usePaginaInicialPendente('pix', lojaId, filtros, pageSize);

  const { data: pageData, isLoading } = useQuery({
    queryKey: ['pix', lojaId, filtros, page, pageSize],
    queryFn: () => api.get('/pix', {
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

  const pixList = pageData?.content ?? pageData ?? [];

  const mudarStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: StatusPix }) =>
      api.patch(`/pix/${id}/status`, { status }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pix', lojaId] }),
  });

  const excluirMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/pix/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pix', lojaId] }),
  });

  const uploadMutation = useMutation({
    mutationFn: ({ id, arquivo }: { id: string; arquivo: File }) => {
      const form = new FormData();
      form.append('arquivo', arquivo);
      return api.post(`/pix/${id}/arquivo`, form, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pix', lojaId] }),
    onError: () => {},
  });

  const deletarArquivoMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/pix/${id}/arquivo`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pix', lojaId] }),
  });

  const abrirArquivo = async (pixId: string) => {
    try {
      const { data } = await api.get(`/pix/${pixId}/arquivo`);
      window.open(data.url, '_blank');
    } catch {}
  };

  const handleUpload = (pixId: string) => {
    setPixUpload(pixId);
    uploadRef.current?.click();
  };

  const onArquivoSelecionado = (e: React.ChangeEvent<HTMLInputElement>) => {
    const arquivo = e.target.files?.[0];
    if (arquivo && pixUpload) {
      uploadMutation.mutate({ id: pixUpload, arquivo });
    }
    e.target.value = '';
  };

  const copiarChave = (chave: string, id: string) => {
    copiarTexto(chave);
    setCopiado(id);
    setTimeout(() => setCopiado(null), 2000);
  };

  const proximoStatus = (atual: StatusPix): StatusPix => {
    if (atual === 'PENDENTE') return 'PAGO';
    if (atual === 'PAGO') return 'PENDENTE';
    return atual;
  };

  if (isLoading) return <SkeletonTable rows={4} />;

  return (
    <div>
      <div className="flex justify-end mb-4">
        <button
          onClick={() => { setPixEditando(null); setModalAberto(true); }}
          className="flex items-center gap-2 bg-[#0c4a6e] hover:bg-[#0a3d5c]
                     text-white text-sm font-medium px-4 py-2 rounded-xl
                     transition-colors shadow-sm"
        >
          <Plus className="w-4 h-4" />
          Novo PIX
        </button>
      </div>

      {isMobile ? (
        <div className="space-y-3">
          {pixList.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-slate-400 text-sm">Nenhum PIX cadastrado</p>
              <button
                onClick={() => { setPixEditando(null); setModalAberto(true); }}
                className="mt-3 text-[#0c4a6e] text-sm hover:underline"
              >
                Adicionar o primeiro PIX
              </button>
            </div>
          ) : (
            <>
              {pixList.map((pix: PagamentoPix) => (
                <CardPix
                  key={pix.id}
                  pix={pix}
                  onCopiar={copiarChave}
                  onEditar={() => { setPixEditando(pix); setModalAberto(true); }}
                  onExcluir={() => excluirMutation.mutate(pix.id)}
                  onUpload={() => handleUpload(pix.id)}
                  onDeletarArquivo={() => deletarArquivoMutation.mutate(pix.id)}
                  onMudarStatus={(status) => mudarStatusMutation.mutate({ id: pix.id, status })}
                  copiado={copiado}
                />
              ))}
              <Pagination
                page={page}
                totalPages={pageData?.totalPages ?? 1}
                total={pageData?.totalElements ?? pixList.length}
                pageSize={pageSize}
                onChange={(p) => { setPage(p); }}
              />
            </>
          )}
        </div>
      ) : (
        <div className="bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
          {pixList.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-slate-400 text-sm">Nenhum PIX cadastrado</p>
              <button
                onClick={() => { setPixEditando(null); setModalAberto(true); }}
                className="mt-3 text-[#0c4a6e] text-sm hover:underline"
              >
                Adicionar o primeiro PIX
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
                        Chave PIX
                      </th>
                      <th className="px-4 py-3 w-24"></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-50">
                    {pixList.map((pix: PagamentoPix, i: number) => {
                      const vencido = pix.status === 'PENDENTE' &&
                        isPast(parseISO(pix.vencimento + 'T23:59:59'));
                      return (
                        <tr
                          key={pix.id}
                          className={clsx(
                            'hover:bg-slate-50/50 transition-colors group',
                            i % 2 === 0 ? 'bg-white' : 'bg-slate-50/30'
                          )}
                        >
                          <td className="px-4 py-3">
                            <span className="font-medium text-slate-800">{pix.fornecedor}</span>
                            {pix.observacoes && (
                              <p className="text-xs text-slate-400 truncate max-w-[200px]">
                                {pix.observacoes}
                              </p>
                            )}
                          </td>

                          <td className="px-4 py-3 text-right">
                            <ValorMonetario
                              valor={pix.valor}
                              className="font-semibold text-[#0c4a6e]"
                            />
                          </td>

                          <td className="px-4 py-3">
                            <span className={clsx(
                              'font-medium',
                              vencido ? 'text-red-600' : 'text-slate-700'
                            )}>
                              {format(parseISO(pix.vencimento), 'dd/MM/yyyy', { locale: ptBR })}
                            </span>
                            {vencido && (
                              <span className="ml-1.5 text-xs text-red-500">(vencido)</span>
                            )}
                          </td>

                          <td className="px-4 py-3">
                            <button
                              onClick={() => mudarStatusMutation.mutate({
                                id: pix.id,
                                status: proximoStatus(pix.status)
                              })}
                              title="Clique para mudar o status"
                              className="hover:opacity-75 transition-opacity"
                            >
                              <BadgeStatus status={pix.status} />
                            </button>
                          </td>

                          <td className="px-4 py-3">
                            {pix.chavePix ? (
                              <div className="flex items-center gap-2">
                                <span className="text-xs text-slate-500 font-mono truncate max-w-[180px]">
                                  {pix.chavePix}
                                </span>
                                <BotaoPagar
                                  tipo="pix"
                                  codigo={pix.chavePix}
                                  fornecedor={pix.fornecedor}
                                  valor={pix.valor}
                                  vencimento={pix.vencimento}
                                  className="px-2.5 py-1 text-xs"
                                />
                              </div>
                            ) : (
                              <span className="text-slate-300 text-xs">—</span>
                            )}
                          </td>

                          <td className="px-4 py-3">
                            <div className="flex items-center gap-1">
                              {pix.arquivoKey ? (
                                <>
                                  <button
                                    onClick={() => abrirArquivo(pix.id)}
                                    title="Ver arquivo"
                                    className="p-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-500"
                                  >
                                    <ExternalLink className="w-4 h-4" />
                                  </button>
                                  <button
                                    onClick={() => { if (confirm('Remover arquivo?')) deletarArquivoMutation.mutate(pix.id); }}
                                    title="Remover arquivo"
                                    className="p-2 rounded-lg bg-red-100 hover:bg-red-200 text-red-600"
                                  >
                                    <FileX className="w-4 h-4" />
                                  </button>
                                </>
                              ) : (
                                <button
                                  onClick={() => handleUpload(pix.id)}
                                  title="Anexar arquivo"
                                  className="p-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-500"
                                >
                                  <Upload className="w-4 h-4" />
                                </button>
                              )}

                              {pix.chavePix && (
                                <button
                                  onClick={() => copiarChave(pix.chavePix, pix.id)}
                                  title="Copiar chave PIX"
                                  className={clsx(
                                    'p-2 rounded-lg transition-colors',
                                    copiado === pix.id
                                      ? 'bg-emerald-100 text-emerald-700'
                                      : 'bg-slate-100 hover:bg-slate-200 text-slate-500'
                                  )}
                                >
                                  {copiado === pix.id ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                                </button>
                              )}

                              <button
                                onClick={() => { setPixEditando(pix); setModalAberto(true); }}
                                title="Editar"
                                className="p-2 rounded-lg bg-[#e0f2fe] hover:bg-[#e0f2fe] text-[#0c4a6e]"
                              >
                                <Pencil className="w-4 h-4" />
                              </button>

                              <button
                                onClick={() => { if (confirm('Excluir este PIX?')) excluirMutation.mutate(pix.id); }}
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
                total={pageData?.totalElements ?? pixList.length}
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
        <ModalPix
          pix={pixEditando}
          lojaId={lojaId}
          onFechar={() => setModalAberto(false)} onSalvo={() => { setModalAberto(false); setPixEditando(null); queryClient.invalidateQueries({ queryKey: ['pix', lojaId] }); }}
        />
      )}
    </div>
  );
}
