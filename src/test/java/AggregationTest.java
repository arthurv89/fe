import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import nl.arthurvlug.interviews.fedex.Aggregation;
import nl.arthurvlug.interviews.fedex.Application;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;

public class AggregationTest {
    final String hostname = "http://localhost:8081";
    final int schedulerPeriod = 5000; // ms

    private final WireMockServer wireMockServer = new WireMockServer(8080);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private ConfigurableApplicationContext applicationContext;

    @BeforeEach
    public void beforeEach() {
        applicationContext = Application.startApplication(new String[0]);
        wireMockServer.resetAll();
        wireMockServer.start();
    }

    @AfterEach
    public void afterEach() {
        applicationContext.close();
        wireMockServer.stop();
    }

    @Test
    public void testAllSuccess() throws Exception {
        stubShipments(TestData.smallShipmentsMap(), 200, TestData.smallShipmentIds);
        stubTrack(TestData.smallTrackMap(), 200, TestData.smallTrackIds);
        stubPricing(TestData.smallPricingMap(), 200, TestData.smallPricingIds);

        long startTime = System.currentTimeMillis();
        final Aggregation aggregation = aggregationCall(TestData.smallPricingIds, TestData.smallTrackIds, TestData.smallShipmentIds);
        long endTime = System.currentTimeMillis();

        assertThat(endTime - startTime).isGreaterThan(schedulerPeriod/2);
        assertThat(aggregation).isEqualTo(toAggregation(
                TestData.smallShipmentsMap(),
                TestData.smallTrackMap(),
                TestData.smallPricingMap()
        ));
    }

    @Test
    public void testSomeSuccess_scheduler() throws Exception {
        stubShipments(null, 503, TestData.smallShipmentIds);
        stubTrack(TestData.smallTrackMap(), 200, TestData.smallTrackIds);
        stubPricing(TestData.smallPricingMap(), 200, TestData.smallPricingIds);

        long startTime = System.currentTimeMillis();
        final Aggregation aggregation = aggregationCall(TestData.smallPricingIds, TestData.smallTrackIds, TestData.smallShipmentIds);
        long endTime = System.currentTimeMillis();

        assertThat(endTime - startTime).isGreaterThan(schedulerPeriod/2);
        assertThat(aggregation).isEqualTo(toAggregation(
                TestData.nullSmallShipmentsMap(),
                TestData.smallTrackMap(),
                TestData.smallPricingMap()
        ));
    }

    @Test
    public void testSomeSuccess_immediately() throws Exception {
        stubShipments(null, 503, TestData.bigShipmentIds);
        stubTrack(null, 200, TestData.bigTrackIds);
        stubPricing(null, 200, TestData.bigPricingIds);

        long startTime = System.currentTimeMillis();
        aggregationCall(TestData.bigPricingIds, TestData.bigTrackIds, TestData.bigShipmentIds);
        long endTime = System.currentTimeMillis();

        assertThat(endTime - startTime).isLessThan(schedulerPeriod/2);

    }

    private Aggregation toAggregation(final Map<String, List<String>> shipmentsMap,
                                      final Map<String, String> trackMap,
                                      final Map<String, Float> pricingMap) {
        return new Aggregation(shipmentsMap, trackMap, pricingMap);
    }

    private void stubShipments(final Map<String, List<String>> map, final int status, final String ids) throws JsonProcessingException {
        stubGet("/shipments?q=" + ids, map, status);
    }

    private void stubTrack(final Map<String, String> map, final int status, final String ids) throws JsonProcessingException {
        stubGet("/track?q=" + ids, map, status);
    }

    private void stubPricing(final Map<String, Float> map, final int status, final String countryCodes) throws JsonProcessingException {
        stubGet("/pricing?q=" + countryCodes, map, status);
    }

    private void stubGet(final String url, final Object responseBody, final int status) throws JsonProcessingException {
        wireMockServer.stubFor(WireMock.get(url)
                .willReturn(aResponse()
                        .withStatus(status)
                        .withBody(objectMapper.writeValueAsString(responseBody))));
    }

    private Aggregation aggregationCall(final String pricing, final String track, final String shipments) throws URISyntaxException, IOException, InterruptedException {
        return aggregationCall(String.format("pricing=%s&track=%s&shipments=%s", pricing, track, shipments));
    }

    private Aggregation aggregationCall(final String queryParams) throws URISyntaxException, IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(hostname + "/aggregation?" + queryParams))
                .GET()
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        final HttpResponse<String> send = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        final String json = send.body();
        return objectMapper.readValue(json, Aggregation.class);
    }
}
