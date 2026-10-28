import { describe, it, expect, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TabelaPorLoja } from './TabelaPorLoja';

const porLoja = [
  { lojaId: 'n', nome: 'Norte', valorPago: 0, valorAPagar: 800, valorEmAtraso: 300 },
  { lojaId: 'c', nome: 'Centro', valorPago: 1000, valorAPagar: 250, valorEmAtraso: 0 },
];
const lojas = [{ id: 'c', nome: 'Centro' }, { id: 'n', nome: 'Norte' }, { id: 's', nome: 'Sul' }];

describe('TabelaPorLoja', () => {
  it('uma linha por loja na ordem que veio, atraso em destaque, total no pé e as lojas zeradas citadas', () => {
    render(<TabelaPorLoja porLoja={porLoja} lojas={lojas} onEscolher={() => {}} />);

    const linhas = screen.getAllByRole('row');
    expect(linhas[1].textContent).toContain('Norte');
    expect(linhas[1].textContent).toMatch(/800,00/);
    expect(linhas[1].textContent).toMatch(/300,00 em atraso/);
    expect(linhas[2].textContent).toContain('Centro');
    expect(linhas[2].textContent).not.toContain('em atraso');

    const total = within(linhas[3]);
    expect(total.getByText(/1\.000,00/)).toBeTruthy();
    expect(total.getByText(/1\.050,00/)).toBeTruthy();

    expect(screen.getByText(/Sem lançamento no período: Sul/)).toBeTruthy();
  });

  it('tocar na loja abre o relatório só dela', async () => {
    const onEscolher = vi.fn();
    render(<TabelaPorLoja porLoja={porLoja} lojas={lojas} onEscolher={onEscolher} />);

    await userEvent.click(screen.getByRole('button', { name: 'Centro' }));

    expect(onEscolher).toHaveBeenCalledWith('c');
  });
});
