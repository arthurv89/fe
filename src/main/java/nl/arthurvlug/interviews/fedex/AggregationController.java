package nl.arthurvlug.interviews.fedex;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class AggregationController {
    private final AggregationService aggregationService = new AggregationService();

    @GetMapping(value = "/aggregation", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Aggregation> aggregation(
            @RequestParam("shipments") String shipmentIds,
            @RequestParam("track") String trackIds,
            @RequestParam("pricing") String countryCodes
    ) {
        final Aggregation aggregate = aggregationService.aggregate(
                splitString(shipmentIds),
                splitString(trackIds),
                splitString(countryCodes)
        );
        return ResponseEntity.ok(aggregate);
    }

    private static Set<String> splitString(final String commaSeparatedStrings) {
        return Arrays.stream(commaSeparatedStrings.split(","))
                .collect(Collectors.toSet());
    }
}
