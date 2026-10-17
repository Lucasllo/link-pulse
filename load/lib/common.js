// Helpers compartilhados pelos scripts k6 (seed.js e load.js).
// Rodam no container grafana/k6 na rede do compose: o app é http://app:8080 por dentro.
import http from 'k6/http';
import { sleep } from 'k6';

// Endereço usado pelas requisições (rede interna do compose).
export const BASE_URL = __ENV.BASE_URL || 'http://app:8080';
// Endereço que o avaliador abre no navegador; só para imprimir as URLs curtas.
export const PUBLIC_BASE_URL = __ENV.PUBLIC_BASE_URL || 'http://localhost:8080';
export const GRAFANA_URL = __ENV.GRAFANA_URL || 'http://localhost:3000';

// User-Agents reais de navegador. O UA padrão do k6 ("Grafana k6/<versão>") contém "k6/", que o
// app classifica como bot (decisão 02-05): sem isso os cliques somem do /stats padrão. Nenhum
// destes pode conter token de bot do UserAgentClassifier nem casar [a-z]bot/<dígito>.
export const BROWSER_UAS = [
  // Chrome no Windows
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36',
  // Safari no iPhone
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1',
  // Firefox no Linux
  'Mozilla/5.0 (X11; Linux x86_64; rv:143.0) Gecko/20100101 Firefox/143.0',
  // Edge no Windows
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0',
  // Chrome no Android
  'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36',
  // Safari no macOS
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Safari/605.1.15',
];

export const BROWSER_UA = BROWSER_UAS[0];

// Googlebot, usado de propósito numa fração dos cliques: aparece só com ?includeBots=true.
export const BOT_UA = 'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)';

// Origem dos cliques; null = sem Referer, que o app conta como (direct).
export const REFERRERS = [
  'https://news.ycombinator.com/',
  'https://www.google.com/',
  'https://t.co/',
  'https://www.linkedin.com/feed/',
  null,
];

export function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

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

function postLink(body) {
  return http.post(`${BASE_URL}/links`, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json' },
    // Os scripts ligam discardResponseBodies; aqui o corpo é necessário.
    responseType: 'text',
    responseCallback: http.expectedStatuses(201, 409),
    tags: { name: 'POST /links' },
  });
}

// POST /links. 201 devolve o código criado; 409 (alias criado por outra execução no meio do
// caminho) devolve o próprio alias. Em 429, espera o Retry-After e tenta uma vez de novo: é a
// rede de segurança para ordens de comando que encostam no limite de 20 criações por minuto por
// IP (por exemplo, seed e load na mesma janela); a sequência documentada nunca recebe 429.
// Qualquer outro status interrompe o script.
export function createLink(body) {
  let rateLimited = 0;
  let res = postLink(body);
  if (res.status === 429) {
    rateLimited++;
    const retryAfter = parseInt(res.headers['Retry-After'], 10);
    sleep((Number.isNaN(retryAfter) ? 60 : retryAfter) + 1);
    res = postLink(body);
    if (res.status === 429) {
      rateLimited++;
    }
  }
  if (res.status === 201) {
    const created = res.json();
    return { code: created.code, shortUrl: created.shortUrl, created: true, rateLimited };
  }
  if (res.status === 409) {
    return {
      code: body.alias,
      shortUrl: `${PUBLIC_BASE_URL}/${body.alias}`,
      created: false,
      rateLimited,
    };
  }
  // Sem ecoar o corpo: só o status.
  throw new Error(`POST /links respondeu ${res.status} (429 recebidos: ${rateLimited})`);
}
