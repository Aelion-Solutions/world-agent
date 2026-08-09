package sh.variiuz.worldagent.tx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CoordPackTest {

    @ParameterizedTest
    @CsvSource({
            "0, 64, 0",
            "-100, 0, 50",
            "1000, -64, -1000",
            "29999999, 319, -29999999",
            "-29999999, -64, 29999999"
    })
    void packRoundTrip(int x, int y, int z) {
        long key = TransactionManager.pack(x, y, z);
        assertEquals(x, TransactionManager.unpackX(key));
        assertEquals(y, TransactionManager.unpackY(key));
        assertEquals(z, TransactionManager.unpackZ(key));
    }

    @Test
    void distinctCoordsProduceDistinctKeys() {
        long a = TransactionManager.pack(1, 2, 3);
        long b = TransactionManager.pack(1, 2, 4);
        long c = TransactionManager.pack(1, 3, 3);
        assertEquals(false, a == b);
        assertEquals(false, a == c);
    }
}
