package com.stocksmart.rfid;

import com.stocksmart.repository.RfidTagRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Built-in RFID simulator (Day 5).
 *
 * <p>Emits clearly-marked simulated read events for development and demos:
 * every read has {@code simulated=true} and the adapter name
 * {@code SIMULATED-READER-01}. When the caller supplies an EPC the simulator
 * echoes it (the same path any physical bridge would use); otherwise it
 * picks the lowest-id active registered tag, which keeps the demo
 * deterministic. When no active tag exists it returns no reads and the
 * service reports that as a 404 — no invented tags.
 *
 * <p>This is NOT physical RFID hardware and must never be described as such.
 */
@Component
public class SimulatedRfidReader implements RfidReader {

    public static final String READER_NAME = "SIMULATED-READER-01";

    private final RfidTagRepository rfidTagRepository;

    public SimulatedRfidReader(RfidTagRepository rfidTagRepository) {
        this.rfidTagRepository = rfidTagRepository;
    }

    @Override
    public String readerName() {
        return READER_NAME;
    }

    @Override
    public boolean simulated() {
        return true;
    }

    @Override
    public List<RfidRead> scan(String epcCodeOrNull) {
        String epc;
        if (epcCodeOrNull != null && !epcCodeOrNull.isBlank()) {
            epc = epcCodeOrNull.trim().toUpperCase();
        } else {
            epc = rfidTagRepository.findFirstByActiveTrueOrderByIdAsc()
                    .map(tag -> tag.getEpcCode())
                    .orElse(null);
        }
        if (epc == null) {
            return List.of();
        }
        return List.of(new RfidRead(epc, Instant.now(), true, READER_NAME));
    }
}
