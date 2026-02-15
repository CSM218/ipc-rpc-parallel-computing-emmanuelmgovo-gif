package pdc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for the Worker class.
 * Minimal test to verify the class is functional.
 */
class WorkerTest {

    @Test
    void testWorkerInstantiation() {
        Worker worker = new Worker("test-worker", "localhost", 9999);
        assertNotNull(worker, "Worker should be instantiable");
        worker.shutdown();
    }
}
