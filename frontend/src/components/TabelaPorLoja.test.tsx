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
  it('uma linha por loja na ordem que veio, atraso em destaque, total no pé e as lojas zeradas citadas', () => {
    const { container } = render(<TabelaPorLoja porLoja={porLoja} lojas={lojas} onEscolher={() => {}} />);

    const linhas = screen.getAllByRole('listitem');
    expect(linhas[0].textContent).toContain('Norte');
    expect(linhas[0].textContent).toMatch(/800,00/);
    expect(linhas[0].textContent).toMatch(/300,00 em atraso/);
    expect(linhas[1].textContent).toContain('Centro');
    expect(linhas[1].textContent).not.toContain('em atraso');
    expect(linhas[1].textContent).toMatch(/pago R\$\s1\.000,00/);

    expect(container.textContent).toMatch(/Total.*1\.050,00/);
    expect(screen.getByText(/Sem lançamento no período: Sul/)).toBeTruthy();
  });

  it('tocar na loja abre o relatório só dela', async () => {
    const onEscolher = vi.fn();
    render(<TabelaPorLoja porLoja={porLoja} lojas={lojas} onEscolher={onEscolher} />);

    await userEvent.click(screen.getByRole('button', { name: /Centro/ }));

    expect(onEscolher).toHaveBeenCalledWith('c');
  });
});
