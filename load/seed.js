// Seed de demonstração (make seed): cria links pela API pública e gera cliques para os dashboards.
// Nada é escrito direto no Postgres nem no Mongo, e o instante do clique é sempre o do servidor.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, BROWSER_UA, createLink, linkExists, weightedPick } from './lib/common.js';

export const options = {
  // Nunca seguir o 302: os cliques não podem chegar aos sites de destino.
  maxRedirects: 0,
  userAgent: BROWSER_UA,
  discardResponseBodies: true,
  setupTimeout: '90s',
  scenarios: {
    // Cerca de 1.350 cliques em 45 s.
    clicks: {
      executor: 'constant-arrival-rate',
      rate: 30,
      timeUnit: '1s',
      duration: '45s',
      preAllocatedVUs: 10,
      maxVUs: 30,
      exec: 'clicks',
    },
  },
  thresholds: {
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
  },
};

const DAY_MS = 24 * 60 * 60 * 1000;

// Aliases legíveis (casam ^(?=.*[-_])[A-Za-z0-9_-]{4,32}$ e ficam fora dos reservados), com
// destinos públicos. O peso define a fatia dos cliques de cada link.
const ALIASES = [
  { alias: 'spring-docs', url: 'https://docs.spring.io/spring-boot/', weight: 30 },
  { alias: 'link-pulse-repo', url: 'https://github.com/Lucasllo/link-pulse', weight: 20 },
  { alias: 'valkey-io', url: 'https://valkey.io/', weight: 14 },
  { alias: 'k6-docs', url: 'https://grafana.com/docs/k6/latest/', weight: 10 },
  { alias: 'prometheus-docs', url: 'https://prometheus.io/docs/introduction/overview/', weight: 8 },
  { alias: 'grafana-dash', url: 'https://grafana.com/docs/grafana/latest/dashboards/', weight: 6 },
  { alias: 'mongo-agg', url: 'https://www.mongodb.com/docs/manual/aggregation/', weight: 5 },
  { alias: 'promo-30-dias', url: 'https://example.com/promo', weight: 4, expiresInDays: 30 },
];

// Links sem alias (código gerado pela sequência), criados de novo a cada execução.
const GENERATED = 4;

export function setup() {
  const codes = [];
  const weights = [];
  let created = 0;
  let existing = 0;

  for (const a of ALIASES) {
    // HEAD antes do POST: alias que já existe não gasta criação do rate limit.
    if (linkExists(a.alias)) {
      existing++;
    } else {
      const body = { url: a.url, alias: a.alias };
      if (a.expiresInDays) {
        body.expiresAt = new Date(Date.now() + a.expiresInDays * DAY_MS).toISOString();
      }
      const res = createLink(body);
      if (res.created) {
        created++;
      } else {
        existing++;
      }
    }
    codes.push(a.alias);
    weights.push(a.weight);
  }

  const stamp = Date.now();
  for (let i = 1; i <= GENERATED; i++) {
    const res = createLink({ url: `https://example.com/demo/${stamp}-${i}` });
    created++;
    codes.push(res.code);
    weights.push(1);
  }

  return { codes, weights, created, existing };
}

export function clicks(data) {
  const code = weightedPick(data.codes, data.weights);
  const res = http.get(`${BASE_URL}/${code}`, { redirects: 0, tags: { name: 'GET /{code}' } });
  check(res, { 'redirect 302': (r) => r.status === 302 });
}
