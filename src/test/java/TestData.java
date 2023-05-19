import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

public class TestData {
    final static String bigShipmentIds = "0,666,777,888,999";
    final static String bigTrackIds = "111,222,333,444,555";
    final static String bigPricingIds = "BE,CN,NL,UK,US";
    final static String smallShipmentIds = "555,666";
    final static String smallTrackIds = "111,222";
    final static String smallPricingIds = "CN,NL";

    static Map<String, List<String>> smallShipmentsMap = ImmutableMap.of(
            "555", ImmutableList.of("box"),
            "666", ImmutableList.of("envelope")
    );

    static Map<String, List<String>> nullSmallShipmentsMap() {
        final Map<String, List<String>> map = Maps.newHashMap();
        map.put("555", null);
        map.put("666", null);
        return map;
    }

    static Map<String, String> smallTrackMap = ImmutableMap.of(
            "111", "NEW",
            "222", "COLLECTING"
    );

    static Map<String, Float> smallPricingMap = ImmutableMap.of(
            "NL", 2222.5555F,
            "CN", 1111.444F
    );
}
