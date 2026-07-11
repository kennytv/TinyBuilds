package eu.kennytv.tinybuilds.common.group;

import java.util.Map;

public interface GroupStorage {

    Map<String, DisplayGroup> load();

    void save(Map<String, DisplayGroup> groups);
}
