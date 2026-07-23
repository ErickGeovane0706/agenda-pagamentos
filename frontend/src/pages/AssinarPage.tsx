import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import { AxiosError } from 'axios';
import api, { MOTIVO_402 } from '../api/client';
import { useAuthStore } from '../store/authStore';
import './registro/registro.css';

/**
 * Fim do funil: onde o cliente contrata.
 *
 * É o destino de todo 402 — trial vencido, inadimplência, e a 2ª loja que
 * passa do contratado. Os três são o mesmo momento comercial: ele quis usar
 * mais do que tem. Por isso uma tela só, e o motivo específico entra como
 * subtítulo em vez de virar três telas.
 *
 * Fica FORA do Layout (sem menu) de propósito: quem chega aqui está bloqueado
 * para escrita, e oferecer navegação para telas onde nada funciona é
 * frustração, não liberdade — o link de voltar ao sistema está no rodapé.
 */

interface MinhaAssinatura {
  status: 'TRIAL' | 'ATIVA' | 'INADIMPLENTE' | 'CANCELADA';
  lojasContratadas: number;
  vigenteAte: string | null;
  precoBase: number;
  precoLojaAdicional: number;
  dadosCobrancaCompletos: boolean;
  assinaturaIniciada: boolean;
}

const so_digitos = (v: string) => v.replace(/\D/g, '');

/**
 * O que dizer quando o envio falha. Erro de validação (422) traz em `detalhe`
 * qual campo está errado — "CPF ou CNPJ inválido" é acionável e precisa
 * aparecer. Falha de gateway continua genérica, que é a decisão do backend.
 */
function mensagemDoErro(erro: unknown) {
  const resp = (erro as AxiosError<{ mensagem?: string; detalhe?: string }>)?.response;
  if (resp?.status === 422 && resp.data?.detalhe) {
    // "campo: mensagem; campo: mensagem" → só as mensagens.
    return resp.data.detalhe.split('; ').map((p) => p.split(': ').pop()).join(' ');
  }
  return resp?.data?.mensagem || 'Não foi possível abrir o pagamento agora. Tente de novo em instantes.';
}

const reais = (v: number) =>
  v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

const data = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString('pt-BR');

/** Título por status: o que ele perdeu (ou está prestes a perder). */
function titulo(status: MinhaAssinatura['status'], vigenteAte: string | null) {
  switch (status) {
    case 'TRIAL':
      return vigenteAte
        ? `Seu período grátis vai até ${data(vigenteAte)}.`
        : 'Seu período grátis está terminando.';
    case 'INADIMPLENTE':
      return 'Seu pagamento não foi confirmado.';
    case 'CANCELADA':
      return 'Sua assinatura está cancelada.';
    case 'ATIVA':
      return 'Sua assinatura está ativa.';
  }
}

export default function AssinarPage() {
  const usuario = useAuthStore((s) => s.usuario);
  // Lido uma vez na montagem e removido: é o motivo do bloqueio que ACABOU de
  // acontecer, não um estado que deva sobreviver a um F5.
  const [motivo] = useState(() => {
    const m = sessionStorage.getItem(MOTIVO_402);
    sessionStorage.removeItem(MOTIVO_402);
    return m || '';
  });

  const { data: assinatura, isLoading } = useQuery<MinhaAssinatura>({
    queryKey: ['minha-assinatura'],
    queryFn: () => api.get('/assinaturas/minha').then((r) => r.data),
  });

  const [cpfCnpj, setCpfCnpj] = useState('');
  const [telefone, setTelefone] = useState('');
  const [erroCampos, setErroCampos] = useState('');

  // Quantas lojas contratar. Quem chegou aqui esbarrando no limite já vê o
  // seletor um acima do atual — foi exatamente o que ele tentou fazer, e
  // devolvê-lo à quantidade que já falhou seria repetir o bloqueio.
  const [lojas, setLojas] = useState<number | null>(null);
  const lojasAtuais = assinatura?.lojasContratadas ?? 1;
  const bateuNoLimite = motivo.includes('não cobre mais lojas');
  const lojasEscolhidas = lojas ?? (bateuNoLimite ? lojasAtuais + 1 : lojasAtuais);

  const total = assinatura
    ? assinatura.precoBase + assinatura.precoLojaAdicional * Math.max(0, lojasEscolhidas - 1)
    : 0;

  const assinar = useMutation({
    // Os dados de cobrança sobem no mesmo clique quando faltam: para o cliente
    // é uma ação só ("assinar"), e o gateway recusa criar o customer sem eles.
    mutationFn: async () => {
      if (assinatura && !assinatura.dadosCobrancaCompletos) {
        await api.put('/assinaturas/minha/cobranca', { cpfCnpj, telefone });
      }
      // Quem já tem assinatura no gateway não passa por checkout de novo: a
      // subscription em curso muda de valor e a loja libera na hora.
      if (assinatura?.assinaturaIniciada) {
        await api.put('/assinaturas/minha/lojas', { lojas: lojasEscolhidas });
        return { urlPagamento: null, apenasAmpliou: true };
      }
      const r = await api.post(`/assinaturas/${usuario?.empresaId}/assinar`,
        { lojas: lojasEscolhidas });
      return { ...(r.data as { urlPagamento: string | null }), apenasAmpliou: false };
    },
    onSuccess: (d) => {
      // Sai do app para o gateway. A ativação não acontece aqui — vem depois,
      // pelo webhook, quando o pagamento é confirmado.
      if (d.urlPagamento) window.location.href = d.urlPagamento;
    },
  });

  const iniciar = () => {
    if (assinatura && !assinatura.dadosCobrancaCompletos) {
      if (!/^\d{11}$|^\d{14}$/.test(cpfCnpj)) {
        setErroCampos('Informe CPF (11 dígitos) ou CNPJ (14 dígitos).');
        return;
      }
      if (!/^\d{10,15}$/.test(telefone)) {
        setErroCampos('Informe o telefone com DDD.');
        return;
      }
    }
    setErroCampos('');
    assinar.mutate();
  };

  if (isLoading) {
    return (
      <div className="reg-root">
        <div className="reg-body"><p className="reg-lede">Carregando...</p></div>
      </div>
    );
  }

  // A cobrança existe mas o gateway ainda não gerou a primeira fatura.
  const semUrl = assinar.isSuccess && !assinar.data?.urlPagamento;
  // Já era assinante e só ampliou: não há checkout, a loja liberou na hora.
  const ampliou = assinar.isSuccess && assinar.data?.apenasAmpliou === true;

  const rotuloBotao = assinatura?.assinaturaIniciada
    ? 'Contratar mais uma loja'
    : 'Assinar agora';

  return (
    <div className="reg-root">
      <div className="reg-topbar">
        <p className="reg-eyebrow"><b>[ $ ]</b> Assinatura</p>
      </div>
      <div className="reg-body reg-fade">
        <h1 className="reg-h1">
          {assinatura ? titulo(assinatura.status, assinatura.vigenteAte) : 'Assine para continuar.'}
        </h1>
        {motivo && <p className="reg-lede">{motivo}</p>}

        {assinatura && (
          <>
            <div className="reg-contador">
              <span>Lojas</span>
              <button
                type="button"
                aria-label="Menos uma loja"
                onClick={() => setLojas(Math.max(lojasAtuais, lojasEscolhidas - 1))}
                disabled={lojasEscolhidas <= lojasAtuais}
              >−</button>
              <b>{lojasEscolhidas}</b>
              <button
                type="button"
                aria-label="Mais uma loja"
                onClick={() => setLojas(Math.min(50, lojasEscolhidas + 1))}
                disabled={lojasEscolhidas >= 50}
              >+</button>
            </div>

            <ul className="reg-resumo">
              <li>
                <span className="reg-mark">›</span>
                <span>{reais(assinatura.precoBase)} — base, com 1 loja inclusa</span>
              </li>
              {lojasEscolhidas > 1 && (
                <li>
                  <span className="reg-mark">›</span>
                  <span>
                    {reais(assinatura.precoLojaAdicional * (lojasEscolhidas - 1))} —{' '}
                    {lojasEscolhidas - 1} {lojasEscolhidas - 1 === 1 ? 'loja adicional' : 'lojas adicionais'}
                  </span>
                </li>
              )}
              <li className="reg-total">
                <span className="reg-mark">=</span>
                <span><b>{reais(total)}</b> por mês</span>
              </li>
            </ul>
          </>
        )}

        {assinatura && !assinatura.dadosCobrancaCompletos && (
          <div className="reg-stack">
            <div className="reg-field">
              <label htmlFor="cpfCnpj">CPF ou CNPJ</label>
              <input
                id="cpfCnpj"
                inputMode="numeric"
                placeholder="Só números"
                value={cpfCnpj}
                onChange={(e) => setCpfCnpj(so_digitos(e.target.value))}
              />
            </div>
            <div className="reg-field">
              <label htmlFor="telefone">Telefone com DDD</label>
              <input
                id="telefone"
                inputMode="numeric"
                placeholder="83999999999"
                value={telefone}
                onChange={(e) => setTelefone(so_digitos(e.target.value))}
              />
            </div>
            {erroCampos && <span className="reg-erro">{erroCampos}</span>}
          </div>
        )}

        {assinar.isError && (
          <div className="reg-alert">{mensagemDoErro(assinar.error)}</div>
        )}
        {ampliou && (
          <div className="reg-ok">
            Pronto: agora você tem {lojasEscolhidas} lojas. A loja nova já pode ser
            criada, e o valor novo entra na próxima fatura.
          </div>
        )}
        {semUrl && !ampliou && (
          <div className="reg-alert">
            Sua assinatura foi registrada, mas a cobrança ainda está sendo gerada.
            Você receberá o link por email em alguns minutos.
          </div>
        )}

        <div className="reg-actions">
          {ampliou ? (
            <Link to="/lojas" className="reg-btn" style={{ textDecoration: 'none' }}>
              Criar minha loja
            </Link>
          ) : (
            <>
              <button className="reg-btn" onClick={iniciar} disabled={assinar.isPending}>
                {assinar.isPending ? 'Abrindo pagamento...' : rotuloBotao}
              </button>
              <Link to="/lojas" className="reg-link">Voltar ao sistema</Link>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
