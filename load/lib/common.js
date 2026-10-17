// Helpers compartilhados pelos scripts k6 (seed.js e load.js).
// Rodam no container grafana/k6 na rede do compose: o app é http://app:8080 por dentro.
import http from 'k6/http';

// Endereço usado pelas requisições (rede interna do compose).
export const BASE_URL = __ENV.BASE_URL || 'http://app:8080';
// Endereço que o avaliador abre no navegador; só para imprimir as URLs curtas.
export const PUBLIC_BASE_URL = __ENV.PUBLIC_BASE_URL || 'http://localhost:8080';
export const GRAFANA_URL = __ENV.GRAFANA_URL || 'http://localhost:3000';

// User-Agent de navegador. O UA padrão do k6 ("Grafana k6/<versão>") contém "k6/", que o app
// classifica como bot (decisão 02-05): sem isso os cliques somem do /stats padrão.
export const BROWSER_UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) '
  + 'Chrome/141.0.0.0 Safari/537.36';

// Escolhe um item com probabilidade proporcional ao peso.
export function weightedPick(items, weights) {
  const total = weights.reduce((acc, w) => acc + w, 0);
  let r = Math.random() * total;
  for (let i = 0; i < items.length; i++) {
    r -= weights[i];
    if (r < 0) {
      return items[i];
    }
  }
  return items[items.length - 1];
}

// HEAD /{code}: 302 se o link existe, 404 se não. HEAD não gera clique nem passa pelo rate limit.
export function linkExists(code) {
  const res = http.head(`${BASE_URL}/${code}`, {
    redirects: 0,
    responseCallback: http.expectedStatuses(302, 404),
    tags: { name: 'HEAD /{code}' },
  });
  return res.status === 302;
}

// POST /links. 201 devolve o código criado; 409 (alias criado por outra execução no meio do
// caminho) devolve o próprio alias. Qualquer outro status interrompe o seed.
export function createLink(body) {
  const res = http.post(`${BASE_URL}/links`, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    // O seed liga discardResponseBodies; aqui o corpo é necessário.
    responseType: 'text',
    responseCallback: http.expectedStatuses(201, 409),
    tags: { name: 'POST /links' },
  });
  if (res.status === 201) {
    const created = res.json();
    return { code: created.code, shortUrl: created.shortUrl, created: true };
  }
  if (res.status === 409) {
    return { code: body.alias, shortUrl: `${PUBLIC_BASE_URL}/${body.alias}`, created: false };
  }
  // Sem ecoar o corpo: só o status.
  throw new Error(`POST /links respondeu ${res.status}`);
}
