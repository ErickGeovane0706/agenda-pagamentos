import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { LinhaDocumento } from './CardBoleto';

describe('LinhaDocumento', () => {
  it('mostra o número do documento e a NF/pedido que existirem', () => {
    const { container, rerender } = render(<LinhaDocumento numeroDocumento="000123" notaFiscalPedido="NF 4521/1" />);
    expect(container.textContent).toBe('Doc 000123 · NF/Pedido NF 4521/1');

    rerender(<LinhaDocumento numeroDocumento="000123" />);
    expect(container.textContent).toBe('Doc 000123');

    rerender(<LinhaDocumento notaFiscalPedido="Pedido 88" />);
    expect(container.textContent).toBe('NF/Pedido Pedido 88');
  });

  it('boleto sem os dois não ganha linha vazia', () => {
    const { container } = render(<LinhaDocumento />);
    expect(container.innerHTML).toBe('');
  });
});
