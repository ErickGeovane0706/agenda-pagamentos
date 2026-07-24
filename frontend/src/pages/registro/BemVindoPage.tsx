import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@tanstack/react-query';
import api from '../../api/client';
import { useAuthStore } from '../../store/authStore';
import { EtapaOnboarding, Loja, Usuario } from '../../types';
import './registro.css';

/**
 * Onboarding guiado — telas ⑤⑥⑦ do funil, logo depois do auto-login.
 *
 * Continua a linguagem visual do cadastro (mesmo CSS, mesma numeração no topo)
 * porque para o cliente é o mesmo momento: ele acabou de criar a conta e ainda
 * não viu o sistema.
 *
 * Cada passo grava a etapa NO SERVIDOR antes de avançar. É o que faz o wizard
 * retomar de onde parou se ele fechar a aba — "não trabalho com cheque" não
 * deixa rastro nenhum nos dados, então sem persistir o sistema perguntaria para
 * sempre (decisão nº 7 do PLANO-REGISTRO.md).
 *
 * Todo passo é pulável (decisão nº 6): o cliente é dono de comércio ocupado, e
 * sensação de cárcere fecha a aba.
 */

/** Os bancos que aparecem prontos para toque — os mais comuns no comércio do
 *  interior. Quem recebe de outro usa o campo livre; a lista é atalho, não
 *  restrição. Código é o da compensação, o mesmo que o cadastro manual aceita. */
const BANCOS_COMUNS = [
  { nome: 'Banco do Brasil', codigo: '001' },
  { nome: 'Bradesco', codigo: '237' },
  { nome: 'Caixa', codigo: '104' },
  { nome: 'Itaú', codigo: '341' },
  { nome: 'Santander', codigo: '033' },
  { nome: 'Sicoob', codigo: '756' },
  { nome: 'Sicredi', codigo: '748' },
];

const TELEFONE_RE = /^\+?[0-9]{10,15}$/;

export default function BemVindoPage() {
  const navigate = useNavigate();
  const usuario = useAuthStore((s) => s.usuario);
  const setAuth = useAuthStore((s) => s.setAuth);

  const [telefone, setTelefone] = useState('');
  const [horario, setHorario] = useState('08:00');
  const [erroTelefone, setErroTelefone] = useState('');
  const [selecionados, setSelecionados] = useState<string[]>([]);
  const [outroBanco, setOutroBanco] = useState('');

  // O que dizer no resumo final. Guardado no cliente porque é texto de
  // celebração, não estado de negócio — nada aqui é lido de volta.
  const [ativouLembretes, setAtivouLembretes] = useState(false);
  const [bancosCriados, setBancosCriados] = useState(0);

  const { data: lojas } = useQuery<Loja[]>({
    queryKey: ['lojas'],
    queryFn: () => api.get('/lojas').then((r) => r.data),
  });

  const avancar = useMutation({
    mutationFn: (etapa: EtapaOnboarding) =>
      api.patch<Usuario>('/usuarios/minha-conta/onboarding', { etapa }).then((r) => r.data),
    onSuccess: (u) => setAuth(u),
  });

  const salvarLembretes = useMutation({
    mutationFn: () =>
      api.put('/preferencias-notificacao/minha', {
        telefoneWhatsapp: telefone,
        whatsappAtivo: true,
        horario1: horario,
        horario2: null,
        horario3: null,
        horario4: null,
        // No trial existe uma loja só; marcar todas evita pedir uma escolha
        // que ainda não tem alternativa.
        lojaIds: (lojas ?? []).map((l) => l.id),
      }),
    onSuccess: () => {
      setAtivouLembretes(true);
      avancar.mutate('CHEQUES');
    },
  });

  const salvarBancos = useMutation({
    mutationFn: async (nomes: { nome: string; codigo: string | null }[]) => {
      // Sequencial de propósito: são no máximo alguns poucos, e um erro no meio
      // deixa os anteriores criados em vez de um estado indefinido.
      for (const b of nomes) await api.post('/bancos', b);
      return nomes.length;
    },
    onSuccess: (qtd) => {
      setBancosCriados(qtd);
      avancar.mutate('CONCLUIDO');
    },
  });

  const etapa = usuario?.onboardingEtapa;
  const primeiroNome = usuario?.nome.trim().split(' ')[0] ?? '';
  const ocupado = avancar.isPending || salvarLembretes.isPending || salvarBancos.isPending;

  const alternarBanco = (nome: string) =>
    setSelecionados((atuais) =>
      atuais.includes(nome) ? atuais.filter((n) => n !== nome) : [...atuais, nome]);

  const confirmarLembretes = () => {
    if (!TELEFONE_RE.test(telefone.trim())) {
      setErroTelefone('Confira o número: só números, com o 55 na frente e o DDD. Ex.: 5583999999999');
      return;
    }
    setErroTelefone('');
    salvarLembretes.mutate();
  };

  const confirmarBancos = () => {
    const escolhidos: { nome: string; codigo: string | null }[] = BANCOS_COMUNS
      .filter((b) => selecionados.includes(b.nome))
      .map((b) => ({ nome: b.nome, codigo: b.codigo }));
    const extra = outroBanco.trim();
    if (extra.length >= 2) escolhidos.push({ nome: extra, codigo: null });

    if (escolhidos.length === 0) {
      avancar.mutate('CONCLUIDO');
      return;
    }
    salvarBancos.mutate(escolhidos);
  };

  const erro = avancar.isError || salvarLembretes.isError || salvarBancos.isError;

  if (etapa === 'LEMBRETES') {
    return (
      <div className="reg-root">
        <div className="reg-topbar">
          <p className="reg-eyebrow"><b>[03]</b> Lembretes</p>
          <div className="reg-ticks"><i className="now" /><i /><i /></div>
        </div>
        <div className="reg-body reg-fade" key="lembretes">
          <h1 className="reg-h1">Quer receber os lembretes no WhatsApp?</h1>
          <p className="reg-lede">
            Avisamos você sobre o que vence, no horário que escolher. Dá para mudar depois.
          </p>
          <div className="reg-stack">
            <div className="reg-field">
              <label htmlFor="telefone">Seu WhatsApp</label>
              <input
                id="telefone"
                autoFocus
                inputMode="numeric"
                autoComplete="tel"
                placeholder="5583999999999"
                value={telefone}
                onChange={(e) => setTelefone(e.target.value)}
              />
              {erroTelefone
                ? <span className="reg-erro">{erroTelefone}</span>
                : <span className="reg-hint">Só números, com o 55 na frente e o DDD. Ex.: 5583999999999</span>}
            </div>
            <div className="reg-field">
              <label htmlFor="horario">Horário do aviso</label>
              <input
                id="horario"
                type="time"
                value={horario}
                onChange={(e) => setHorario(e.target.value)}
              />
            </div>
          </div>

          {erro && <div className="reg-alert">Não foi possível salvar agora. Tente de novo.</div>}

          <div className="reg-actions">
            <button className="reg-btn" onClick={confirmarLembretes} disabled={ocupado}>
              {ocupado ? 'Salvando...' : 'Ativar lembretes'}
            </button>
            <button type="button" className="reg-link" disabled={ocupado}
                    onClick={() => avancar.mutate('CHEQUES')}>
              Agora não
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (etapa === 'CHEQUES') {
    return (
      <div className="reg-root">
        <div className="reg-topbar">
          <p className="reg-eyebrow"><b>[04]</b> Cheques</p>
          <div className="reg-ticks"><i className="done" /><i className="now" /><i /></div>
        </div>
        <div className="reg-body reg-fade" key="cheques">
          <h1 className="reg-h1">Você trabalha com cheque?</h1>
          <p className="reg-lede">
            Marque os bancos que você recebe. Eles ficam prontos na hora de lançar um cheque.
          </p>

          <div className="reg-chips">
            {BANCOS_COMUNS.map((b) => (
              <button
                key={b.nome}
                type="button"
                className="reg-chip"
                aria-pressed={selecionados.includes(b.nome)}
                onClick={() => alternarBanco(b.nome)}
              >
                {b.nome}
              </button>
            ))}
          </div>

          <div className="reg-field">
            <label htmlFor="outro">Outro banco</label>
            <input
              id="outro"
              placeholder="Nome do banco"
              value={outroBanco}
              onChange={(e) => setOutroBanco(e.target.value)}
            />
          </div>

          {erro && <div className="reg-alert">Não foi possível salvar agora. Tente de novo.</div>}

          <div className="reg-actions">
            <button className="reg-btn" onClick={confirmarBancos} disabled={ocupado}>
              {ocupado ? 'Salvando...' : 'Continuar'}
            </button>
            <button type="button" className="reg-link" disabled={ocupado}
                    onClick={() => avancar.mutate('CONCLUIDO')}>
              Não trabalho com cheque
            </button>
          </div>
        </div>
      </div>
    );
  }

  // CONCLUIDO — tela ⑦. Quem chega aqui por URL direta com o onboarding já
  // encerrado vê o mesmo resumo e segue para o app; não há estado inválido.
  return (
    <div className="reg-root">
      <div className="reg-topbar">
        <p className="reg-eyebrow"><b>[ ✓ ]</b> Pronto</p>
        <div className="reg-ticks"><i className="done" /><i className="done" /><i className="done" /></div>
      </div>
      <div className="reg-body reg-fade" key="pronto">
        <h1 className="reg-h1">Tudo pronto, {primeiroNome}.</h1>
        <ul className="reg-resumo">
          <li><span className="reg-mark">✓</span><span>Sua área está no ar com <b>30 dias grátis</b></span></li>
          <li><span className="reg-mark">✓</span><span>Loja <b>{lojas?.[0]?.nome ?? 'criada'}</b> pronta para receber lançamentos</span></li>
          {ativouLembretes && (
            <li><span className="reg-mark">✓</span><span>Lembretes no WhatsApp às <b>{horario}</b></span></li>
          )}
          {bancosCriados > 0 && (
            <li><span className="reg-mark">✓</span><span><b>{bancosCriados}</b> {bancosCriados === 1 ? 'banco cadastrado' : 'bancos cadastrados'}</span></li>
          )}
        </ul>
        <div className="reg-actions">
          <button className="reg-btn" onClick={() => navigate('/lojas', { replace: true })}>
            Entrar no sistema
          </button>
        </div>
      </div>
    </div>
  );
}
