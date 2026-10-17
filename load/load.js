// Teste de carga (make load): mede o redirect da API rodando no compose.
// O k6 roda em container na rede do compose; o resumo sai no terminal e em
// results/load-summary.json (--summary-export, caminho relativo ao working_dir /work).
// Nenhum import remoto: só módulos do próprio k6 e helpers locais.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, BROWSER_UA, createLink } from './lib/common.js';

export const options = {
  // Nunca seguir o 302: mede a API, não o site de destino, e não gera tráfego para terceiros.
  // maxRedirects é a opção global; cada GET também passa redirects: 0.
  maxRedirects: 0,
  // O UA padrão do k6 ("Grafana k6/<versão>") conta como bot no app e sumiria do /stats padrão.
  userAgent: BROWSER_UA,
  discardResponseBodies: true,
  // O setup pode esperar um Retry-After (até 60 s) se encostar no rate limit.
  setupTimeout: '90s',
  // O padrão do k6 não tem p(99).
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    redirect: {
      executor: 'constant-arrival-rate',
      exec: 'redirect',
      rate: 50,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
  // A tag de sistema scenario separa os submétricos de cada cenário.
  thresholds: {
    'http_req_duration{scenario:redirect}': ['p(95)<50', 'p(99)<100'],
    'http_req_failed{scenario:redirect}': ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

// 5 links de destino público. createLink espera o Retry-After e tenta de novo uma vez se
// receber 429 (por exemplo, make seed duas vezes seguidas e logo make load).
export function setup() {
  const codes = [];
  for (let i = 1; i <= 5; i++) {
    codes.push(createLink({ url: `https://example.com/load/${i}` }).code);
  }
  return { codes };
}

export function redirect(data) {
  const code = data.codes[Math.floor(Math.random() * data.codes.length)];
  const res = http.get(`${BASE_URL}/${code}`, {
    redirects: 0,
    tags: { name: 'GET /{code}' },
  });
  check(res, { 'redirect 302': (r) => r.status === 302 });
}
