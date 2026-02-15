package pdc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for the Master class.
 * Minimal test to verify the class is functional.
 */
class MasterTest {

    @Test
    void testMasterInstantiation() throws Exception {
        Master master = new Master(9999);
        assertNotNull(master, "Master should be instantiable");
        master.shutdown();
    }
}

