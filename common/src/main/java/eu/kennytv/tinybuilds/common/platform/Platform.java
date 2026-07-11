package eu.kennytv.tinybuilds.common.platform;

import eu.kennytv.tinybuilds.common.group.GroupStorage;
import org.jspecify.annotations.Nullable;

public interface Platform {

    @Nullable PlatformWorld world(Object worldKey); // slightly awkward to pass a raw Object, but better than parsing the keys every time

    GroupStorage storage();

    int rotationUpdateInterval();

    long maxSelectionVolume();

    int maxDisplayEntities();
}
