package sh.variiuz.worldagent.poi;

import java.util.Map;

public record Poi(
        String id,
        String source,
        String name,
        String world,
        double x,
        double y,
        double z,
        Map<String, String> tags
) {
}
