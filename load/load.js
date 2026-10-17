// Teste de carga (make load): mede o redirect e a criação da API rodando no compose.
// O k6 roda em container na rede do compose; o resumo sai no terminal e em
// results/load-summary.json (--summary-export, caminho relativo ao working_dir /work).
// Nenhum import remoto: só módulos do próprio k6 e helpers locais.
//
// Perfil (cerca de 2,5 min): warmup a 50 req/s por 30 s (absorve o JIT da JVM e fica fora dos
// thresholds), redirect a 200 req/s por 2 min e criação a 12/min por 90 s a partir de 60 s.
//
// Orçamento do rate limit (20 criações por minuto por IP, janela fixa de 60 s; 409 e 400 também
// contam):
//   - make seed: até 12 POSTs na primeira execução (janela W0);
//   - setup() deste script: 5 POSTs, na pior das hipóteses ainda em W0 -> 17 <= 20;
//   - cenário create: 12/min com startTime 60s, ou seja, sempre depois de setup + 60 s, numa
//     janela posterior à do setup e à do seed -> no máximo 12 por janela.
// 12/min e não 15/min: com constant-arrival-rate, 15/min pode dar 16 iterações numa janela
// desalinhada. O create não tem retry: um 429 ali é falha de orçamento e reprova o teste.
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
    // Aquecimento da JVM: mesma função do redirect, fora dos thresholds.
    warmup: {
      executor: 'constant-arrival-rate',
      exec: 'redirect',
      rate: 50,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
    redirect: {
      executor: 'constant-arrival-rate',
      exec: 'redirect',
      rate: 200,
      timeUnit: '1s',
      duration: '2m',
      startTime: '30s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
    // 12/min e 60 s depois do setup mantêm seed + setup + create abaixo de 20 criações por janela.
    create: {
      executor: 'constant-arrival-rate',
      exec: 'create',
      rate: 12,
      timeUnit: '1m',
      duration: '90s',
      startTime: '60s',
      preAllocatedVUs: 2,
      maxVUs: 5,
    },
  },
  // A tag de sistema scenario separa os submétricos de cada cenário; o warmup não entra.
  thresholds: {
    'http_req_duration{scenario:redirect}': ['p(95)<50', 'p(99)<100'],
    'http_req_failed{scenario:redirect}': ['rate<0.01'],
    'http_req_duration{scenario:create}': ['p(95)<200'],
    'http_req_failed{scenario:create}': ['rate<0.01'],
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

// Sem retry: um 429 aqui é erro de orçamento do rate limit e precisa reprovar o teste.
export function create() {
  const res = http.post(
    `${BASE_URL}/links`,
    JSON.stringify({ url: `https://example.com/k6/${Date.now()}-${__VU}-${__ITER}` }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /links' },
    },
  );
  check(res, { 'create 201': (r) => r.status === 201 });
}
