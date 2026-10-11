package dev.linkpulse.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import dev.linkpulse.AbstractIT;
import dev.linkpulse.click.ClickDocument;
import dev.linkpulse.link.Link;
import dev.linkpulse.link.LinkRepository;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * ITs do {@code GET /links/{code}/stats} (STAT-01 a STAT-04, D-01 a D-04).
 *
 * <p>A coleção {@code click_events} é compartilhada por todos os ITs do contexto, então cada
 * teste usa um código próprio ({@code stats-*}) e só consulta as estatísticas dele.
 *
 * <p>Os documentos inseridos à mão usam datas relativas a hoje (UTC): o índice TTL de
 * {@code click_events} (90 dias) apagaria cliques com datas fixas antigas no meio do teste.
 */
class StatsIT extends AbstractIT {

    private static final String CLIENT_IP = "198.51.100.21";
    private static final String FIREFOX =
            "Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0";
    private static final Duration WAIT = Duration.ofSeconds(30);
    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String IP_HASH = "ab".repeat(32);
    private static final long MAX_OUTAGE_MILLIS = 15_000;

    @Autowired
    private LinkRepository links;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    @Qualifier("mongoContainer")
    private MongoDBContainer mongoContainer;

    private Link saveLink(String code, Instant expiresAt) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return links.saveAndFlush(Link.create(links.nextId(), code,
                "https://example.com/" + code, expiresAt, now));
    }

    private void click(String code, String userAgent) {
        MvcTestResult result = mvc.get().uri("/" + code)
                .with(request -> {
                    request.setRemoteAddr(CLIENT_IP);
                    return request;
                })
                .header(HttpHeaders.USER_AGENT, userAgent)
                .exchange();
        assertThat(result).hasStatus(HttpStatus.FOUND);
    }

    private void insertClick(String id, String code, Instant ts, String referrer, String browser,
            String os, boolean bot) {
        mongo.insert(new ClickDocument(id, code, ts, referrer, browser, os, bot, IP_HASH),
                ClickDocument.COLLECTION);
    }

    /**
     * Insere um clique de uma hora atrás, dentro do período padrão.
     */
    private void insertRecent(String id, String code, String referrer, String browser, String os,
            boolean bot) {
        insertClick(id, code, Instant.now().minus(1, ChronoUnit.HOURS), referrer, browser, os,
                bot);
    }

    private static Map<String, Object> item(String value, int clicks) {
        return Map.of("value", value, "clicks", clicks);
    }

    private void insertHumanClick(String id, String code, Instant ts) {
        insertClick(id, code, ts, "(direct)", "Firefox", "Linux", false);
    }

    private static LocalDate todayUtc() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static Instant at(LocalDate day, String time) {
        return Instant.parse(day + "T" + time + "Z");
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String type) {
        assertThat(result).hasStatus(status);
        assertThat(result.getResponse().getContentType()).startsWith(PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(type);
    }

    private MvcTestResult stats(String uri) {
        return mvc.get().uri(uri).exchange();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void statsCountRedirectsOfTheLink() {
        saveLink("stats-e2e1", null);
        for (int i = 0; i < 3; i++) {
            click("stats-e2e1", FIREFOX);
        }

        await().atMost(WAIT).pollInterval(Duration.ofMillis(250)).untilAsserted(() -> {
            MvcTestResult result = stats("/links/stats-e2e1/stats");
            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(((Number) JsonPath.read(body(result), "$.total")).longValue())
                    .isEqualTo(3);
        });

        String raw = body(stats("/links/stats-e2e1/stats"));
        assertThat((String) JsonPath.read(raw, "$.code")).isEqualTo("stats-e2e1");
        assertThat((Boolean) JsonPath.read(raw, "$.includeBots")).isFalse();
        LocalDate to = LocalDate.parse(JsonPath.read(raw, "$.to"));
        LocalDate from = LocalDate.parse(JsonPath.read(raw, "$.from"));
        assertThat(from).isEqualTo(to.minusDays(29));
    }

    @Test
    void dailySeriesIsContinuousInUtc() {
        saveLink("stats-day1", null);
        LocalDate first = todayUtc().minusDays(10);
        insertHumanClick("stats-day1-0", "stats-day1", at(first, "23:59:59.999"));
        insertHumanClick("stats-day1-1", "stats-day1", at(first.plusDays(1), "00:00:00.000"));
        insertHumanClick("stats-day1-2", "stats-day1", at(first.plusDays(3), "12:00:00"));

        MvcTestResult result = stats("/links/stats-day1/stats?from=" + first + "&to="
                + first.plusDays(4));

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.from").isEqualTo(first.toString());
        assertThat(result).bodyJson().extractingPath("$.to")
                .isEqualTo(first.plusDays(4).toString());
        assertThat(result).bodyJson().extractingPath("$.total").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.daily").asArray().containsExactly(
                Map.of("date", first.toString(), "clicks", 1),
                Map.of("date", first.plusDays(1).toString(), "clicks", 1),
                Map.of("date", first.plusDays(2).toString(), "clicks", 0),
                Map.of("date", first.plusDays(3).toString(), "clicks", 1),
                Map.of("date", first.plusDays(4).toString(), "clicks", 0));
    }

    @Test
    void linkWithoutClicksHasZeroSeries() {
        saveLink("stats-zero1", null);

        MvcTestResult result = stats("/links/stats-zero1/stats");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.total").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.daily").asArray().hasSize(30);
        assertThat(result).bodyJson().extractingPath("$.daily[*].clicks").asArray()
                .containsOnly(0);
    }

    @Test
    void invalidPeriodIsProblem() {
        saveLink("stats-per1", null);

        MvcTestResult reversed = stats("/links/stats-per1/stats?from=2026-03-05&to=2026-03-01");
        assertProblem(reversed, HttpStatus.BAD_REQUEST, "/problems/invalid-period");
        assertThat(reversed).bodyJson().extractingPath("$.maxDays").isEqualTo(366);
        assertThat(reversed).bodyJson().extractingPath("$.from").isEqualTo("2026-03-05");
        assertThat(reversed).bodyJson().extractingPath("$.to").isEqualTo("2026-03-01");

        MvcTestResult tooLong = stats("/links/stats-per1/stats?from=2025-01-01&to=2026-01-03");
        assertProblem(tooLong, HttpStatus.BAD_REQUEST, "/problems/invalid-period");
    }

    @Test
    void malformedParameterIsProblem() {
        saveLink("stats-par1", null);

        MvcTestResult badDate = stats("/links/stats-par1/stats?from=2026-13-01");
        assertProblem(badDate, HttpStatus.BAD_REQUEST, "/problems/invalid-parameter");
        assertThat(badDate).bodyJson().extractingPath("$.parameter").isEqualTo("from");
        assertThat(body(badDate)).doesNotContain("2026-13-01");

        MvcTestResult badFlag = stats("/links/stats-par1/stats?includeBots=talvez");
        assertProblem(badFlag, HttpStatus.BAD_REQUEST, "/problems/invalid-parameter");
        assertThat(badFlag).bodyJson().extractingPath("$.parameter").isEqualTo("includeBots");
        assertThat(body(badFlag)).doesNotContain("talvez");
    }

    @Test
    void unknownCodeIsNotFound() {
        MvcTestResult result = stats("/links/nao-existe-st1/stats");

        assertProblem(result, HttpStatus.NOT_FOUND, "/problems/link-not-found");
    }

    @Test
    void expiredLinkStillHasStats() {
        Instant now = Instant.now();
        saveLink("stats-exp1", now.minus(1, ChronoUnit.DAYS));
        insertHumanClick("stats-exp1-0", "stats-exp1", now.minus(2, ChronoUnit.DAYS));

        MvcTestResult result = stats("/links/stats-exp1/stats");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.total").isEqualTo(1);
    }

    @Test
    void topsAreLimitedToTenWithStableTieBreak() {
        saveLink("stats-top1", null);
        int n = 0;
        for (String referrer : List.of("a.example", "a.example", "a.example", "b.example",
                "b.example", "c.example", "c.example", "(direct)")) {
            insertRecent("stats-top1-" + n++, "stats-top1", referrer, "Firefox", "Linux", false);
        }
        for (int i = 1; i <= 11; i++) {
            insertRecent("stats-top1-" + n++, "stats-top1", String.format("h%02d.example", i),
                    "Firefox", "Linux", false);
        }

        MvcTestResult result = stats("/links/stats-top1/stats");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.total").isEqualTo(19);
        assertThat(result).bodyJson().extractingPath("$.topReferrers").asArray().containsExactly(
                item("a.example", 3), item("b.example", 2), item("c.example", 2),
                item("(direct)", 1), item("h01.example", 1), item("h02.example", 1),
                item("h03.example", 1), item("h04.example", 1), item("h05.example", 1),
                item("h06.example", 1));
    }

    @Test
    void browserAndOsTopsFollowDocuments() {
        saveLink("stats-top2", null);
        String[][] agents = {
            {"Firefox", "Linux"}, {"Firefox", "Linux"}, {"Firefox", "Linux"},
            {"Firefox", "Windows"}, {"Firefox", "Windows"},
            {"Chrome", "Linux"}, {"Chrome", "Windows"}
        };
        for (int i = 0; i < agents.length; i++) {
            insertRecent("stats-top2-" + i, "stats-top2", "(direct)", agents[i][0], agents[i][1],
                    false);
        }

        MvcTestResult result = stats("/links/stats-top2/stats");

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.topBrowsers").asArray()
                .containsExactly(item("Firefox", 5), item("Chrome", 2));
        assertThat(result).bodyJson().extractingPath("$.topOs").asArray()
                .containsExactly(item("Linux", 4), item("Windows", 3));
    }

    @Test
    void botsAreExcludedByDefaultAndIncludedOnRequest() {
        saveLink("stats-bot1", null);
        for (int i = 0; i < 3; i++) {
            insertRecent("stats-bot1-h" + i, "stats-bot1", "news.example", "Firefox", "Linux",
                    false);
        }
        for (int i = 0; i < 2; i++) {
            insertRecent("stats-bot1-b" + i, "stats-bot1", "crawler.example", "Other", "Other",
                    true);
        }

        MvcTestResult humans = stats("/links/stats-bot1/stats");
        assertThat(humans).hasStatus(HttpStatus.OK);
        assertThat(humans).bodyJson().extractingPath("$.total").isEqualTo(3);
        assertThat(humans).bodyJson().extractingPath("$.includeBots").isEqualTo(false);
        assertThat(humans).bodyJson().extractingPath("$.topReferrers").asArray()
                .containsExactly(item("news.example", 3));
        assertThat(humans).bodyJson().extractingPath("$.topBrowsers").asArray()
                .containsExactly(item("Firefox", 3));
        assertThat(humans).bodyJson().extractingPath("$.topOs").asArray()
                .containsExactly(item("Linux", 3));

        MvcTestResult all = stats("/links/stats-bot1/stats?includeBots=true");
        assertThat(all).hasStatus(HttpStatus.OK);
        assertThat(all).bodyJson().extractingPath("$.total").isEqualTo(5);
        assertThat(all).bodyJson().extractingPath("$.includeBots").isEqualTo(true);
        assertThat(all).bodyJson().extractingPath("$.topReferrers").asArray()
                .containsExactly(item("news.example", 3), item("crawler.example", 2));
        assertThat(all).bodyJson().extractingPath("$.topBrowsers").asArray()
                .containsExactly(item("Firefox", 3), item("Other", 2));
        assertThat(all).bodyJson().extractingPath("$.topOs").asArray()
                .containsExactly(item("Linux", 3), item("Other", 2));
    }

    @Test
    void responseNeverExposesIpHash() {
        saveLink("stats-ip1", null);
        String firstHash = "0123456789abcdef".repeat(4);
        String secondHash = "fedcba9876543210".repeat(4);
        Instant ts = Instant.now().minus(1, ChronoUnit.HOURS);
        mongo.insert(new ClickDocument("stats-ip1-0", "stats-ip1", ts, "(direct)", "Firefox",
                "Linux", false, firstHash), ClickDocument.COLLECTION);
        mongo.insert(new ClickDocument("stats-ip1-1", "stats-ip1", ts, "(direct)", "Other",
                "Other", true, secondHash), ClickDocument.COLLECTION);

        for (String uri : List.of("/links/stats-ip1/stats",
                "/links/stats-ip1/stats?includeBots=true")) {
            MvcTestResult result = stats(uri);
            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(result).bodyJson().extractingPath("$.total").isNotNull();
            assertThat(body(result)).doesNotContain("ipHash", firstHash, secondHash);
        }
    }

    @Test
    void mongoOutageIsServiceUnavailable() {
        saveLink("stats-mdown1", null);
        assertThat(stats("/links/stats-mdown1/stats")).hasStatus(HttpStatus.OK);

        long elapsed;
        MvcTestResult result;
        mongoContainer.getDockerClient().pauseContainerCmd(mongoContainer.getContainerId()).exec();
        try {
            long start = System.nanoTime();
            result = stats("/links/stats-mdown1/stats");
            elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();
        } finally {
            mongoContainer.getDockerClient().unpauseContainerCmd(mongoContainer.getContainerId())
                    .exec();
        }
        System.out.println("StatsIT: tempo até o 503 com o Mongo pausado (ms) = " + elapsed);

        assertProblem(result, HttpStatus.SERVICE_UNAVAILABLE, "/problems/stats-unavailable");
        assertThat(elapsed).isLessThan(MAX_OUTAGE_MILLIS);
        await().atMost(WAIT).pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
                assertThat(stats("/links/stats-mdown1/stats")).hasStatus(HttpStatus.OK));
    }
}
