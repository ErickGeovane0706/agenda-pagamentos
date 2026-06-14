export function copiarTexto(texto: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    return navigator.clipboard.writeText(texto).catch(() => fallbackCopy(texto));
  }
  return fallbackCopy(texto);
}

function fallbackCopy(texto: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const textarea = document.createElement('textarea');
    textarea.value = texto;
    textarea.style.position = 'fixed';
    textarea.style.left = '-9999px';
    textarea.style.top = '-9999px';
    document.body.appendChild(textarea);
    textarea.focus();
    textarea.select();
    try {
      document.execCommand('copy');
      resolve();
    } catch {
      reject(new Error('Falha ao copiar'));
    } finally {
      document.body.removeChild(textarea);
    }
  });
}
