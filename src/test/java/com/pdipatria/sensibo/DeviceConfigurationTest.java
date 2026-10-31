package com.pdipatria.sensibo;
import com.pdipatria.sensibo.config.DeviceConfiguration;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class DeviceConfigurationTest {
    @Test void parsesOrderedUniqueIdsAndPrefersPlural() {
        assertEquals(List.of("a", "b"), new DeviceConfiguration(" a, b,a ", "legacy").ids());
        assertEquals(List.of("legacy"), new DeviceConfiguration("  ", " legacy ").ids());
        for (String invalid : List.of(",a", "a,", "a, ,b", ""))
            assertThrows(IllegalArgumentException.class, () -> new DeviceConfiguration(invalid, ""));
    }
}
