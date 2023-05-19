package nl.arthurvlug.interviews.fedex;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class AggregationController {
    private final AggregationService aggregationService = new AggregationService();

    @GetMapping(value = "/aggregation", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Aggregation> aggregation(
            @Nullable @RequestParam(value = "shipments", required = false) String shipmentIds,
            @Nullable @RequestParam(value = "track", required = false) String trackIds,
            @Nullable @RequestParam(value = "pricing", required = false) String countryCodes
    ) {
        final Aggregation aggregate = aggregationService.aggregate(
                splitString(shipmentIds),
                splitString(trackIds),
                splitString(countryCodes)
        );
        return ResponseEntity.ok(aggregate);
    }

    private static Set<String> splitString(@Nullable final String commaSeparatedStrings) {
        if(commaSeparatedStrings == null) {
            return Set.of();
        }
        return Arrays.stream(commaSeparatedStrings.split(","))
                .collect(Collectors.toSet());
    }
}
