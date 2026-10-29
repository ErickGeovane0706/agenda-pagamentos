import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TabelaPorLoja } from './TabelaPorLoja';

const porLoja = [
  { lojaId: 'n', nome: 'Norte', valorPago: 0, valorAPagar: 800, valorEmAtraso: 300 },
  { lojaId: 'c', nome: 'Centro', valorPago: 1000, valorAPagar: 250, valorEmAtraso: 0 },
];
const lojas = [{ id: 'c', nome: 'Centro' }, { id: 'n', nome: 'Norte' }, { id: 's', nome: 'Sul' }];

describe('TabelaPorLoja', () => {
  it('um cartão por loja na ordem que veio, com situação, quanto já pagou, a pagar e os três valores', () => {
    render(<TabelaPorLoja porLoja={porLoja} lojas={lojas} onEscolher={() => {}} />);

    const linhas = screen.getAllByRole('listitem');
    expect(linhas[0].textContent).toContain('Norte');
    expect(linhas[0].textContent).toContain('Tem vencido');
    expect(linhas[0].textContent).toContain('0%');
    expect(linhas[0].textContent).toMatch(/A pagar\s*R\$\s800,00/);
    expect(linhas[0].textContent).toMatch(/Pendente\s*R\$\s500,00/);
    expect(linhas[0].textContent).toMatch(/Vencido\s*R\$\s300,00/);
    expect(linhas[0].textContent).toMatch(/Pago\s*—/);

    expect(linhas[1].textContent).toContain('Centro');
    expect(linhas[1].textContent).toContain('Pendente');
    expect(linhas[1].textContent).toContain('80%');
    expect(linhas[1].textContent).toMatch(/Vencido\s*—/);
  });

  it('total de todas as lojas no pé e as lojas zeradas citadas', () => {
    render(<TabelaPorLoja porLoja={porLoja} lojas={lojas} onEscolher={() => {}} />);

    const total = screen.getByText('Total de todas as lojas').parentElement!;
    expect(total.textContent).toMatch(/Pago\s*R\$\s1\.000,00/);
    expect(total.textContent).toMatch(/Pendente\s*R\$\s750,00/);
    expect(total.textContent).toMatch(/Vencido\s*R\$\s300,00/);
    expect(screen.getByText(/Sem lançamento no período: Sul/)).toBeTruthy();
  });

  it('tocar na loja abre o relatório só dela', async () => {
    const onEscolher = vi.fn();
    render(<TabelaPorLoja porLoja={porLoja} lojas={lojas} onEscolher={onEscolher} />);

    await userEvent.click(screen.getByRole('button', { name: /Centro/ }));

    expect(onEscolher).toHaveBeenCalledWith('c');
  });
});
