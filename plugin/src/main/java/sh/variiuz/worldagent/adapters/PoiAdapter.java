package sh.variiuz.worldagent.adapters;

import java.util.List;

import sh.variiuz.worldagent.poi.Poi;

public interface PoiAdapter {
    String name();

    List<Poi> collect();
}
