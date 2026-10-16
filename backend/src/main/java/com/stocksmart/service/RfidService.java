package com.stocksmart.service;

import com.stocksmart.dto.RfidReadEventResponse;
import com.stocksmart.dto.RfidReadRequest;
import com.stocksmart.dto.RfidReaderInfoResponse;
import com.stocksmart.dto.RfidTagRequest;
import com.stocksmart.dto.RfidTagResponse;
import com.stocksmart.dto.RfidTagUpdateRequest;
import com.stocksmart.entity.Product;
import com.stocksmart.entity.RfidReadEvent;
import com.stocksmart.entity.RfidReadStatus;
import com.stocksmart.entity.RfidTag;
import com.stocksmart.entity.User;
import com.stocksmart.exception.ConflictException;
import com.stocksmart.exception.ResourceNotFoundException;
import com.stocksmart.mapper.RfidMapper;
import com.stocksmart.repository.ProductRepository;
import com.stocksmart.repository.RfidReadEventRepository;
import com.stocksmart.repository.RfidTagRepository;
import com.stocksmart.repository.UserRepository;
import com.stocksmart.rfid.RfidRead;
import com.stocksmart.rfid.RfidReader;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RFID workflows (Day 5): tag mapping CRUD and read processing.
 *
 * <p>Reads flow through the {@link RfidReader} abstraction (currently the
 * built-in simulator — see {@code SimulatedRfidReader}); the service applies
 * validation, duplicate-reference protection and product mapping, and records
 * a durable {@link RfidReadEvent}. A read NEVER changes stock: stock only
 * moves through the Day 3 inventory service or Day 4 order workflows after an
 * explicit, authorized user action.
 */
@Service
public class RfidService {

    private final RfidTagRepository rfidTagRepository;
    private final RfidReadEventRepository rfidReadEventRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final RfidReader rfidReader;
    private final RfidMapper rfidMapper;

    public RfidService(RfidTagRepository rfidTagRepository,
                       RfidReadEventRepository rfidReadEventRepository,
                       ProductRepository productRepository,
                       UserRepository userRepository,
                       RfidReader rfidReader,
                       RfidMapper rfidMapper) {
        this.rfidTagRepository = rfidTagRepository;
        this.rfidReadEventRepository = rfidReadEventRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.rfidReader = rfidReader;
        this.rfidMapper = rfidMapper;
    }

    // ---- Tag mapping ----

    @Transactional(readOnly = true)
    public Page<RfidTagResponse> listTags(String search, Boolean active, Long productId,
                                          Pageable pageable) {
        return rfidTagRepository.search(normalizeSearch(search), active, productId, pageable)
                .map(rfidMapper::toTagResponse);
    }

    @Transactional(readOnly = true)
    public RfidTagResponse getTag(String epcCode) {
        return rfidMapper.toTagResponse(requireTag(epcCode));
    }

    @Transactional
    public RfidTagResponse registerTag(RfidTagRequest request) {
        String epc = normalizeEpc(request.getEpcCode());
        if (rfidTagRepository.existsByEpcCodeIgnoreCase(epc)) {
            throw new ConflictException("RFID tag '" + epc + "' is already registered");
        }
        Product product = requireProduct(request.getProductId());
        RfidTag tag = new RfidTag(epc, product,
                request.getActive() != null ? request.getActive() : true, currentUser());
        return rfidMapper.toTagResponse(rfidTagRepository.save(tag));
    }

    @Transactional
    public RfidTagResponse updateTag(String epcCode, RfidTagUpdateRequest request) {
        RfidTag tag = requireTag(epcCode);
        tag.setProduct(requireProduct(request.getProductId()));
        tag.setActive(request.getActive());
        tag.touch();
        return rfidMapper.toTagResponse(rfidTagRepository.save(tag));
    }

    // ---- Reads ----

    @Transactional
    public RfidReadEventResponse processRead(RfidReadRequest request) {
        boolean simulate = request.getSimulate() == null || request.getSimulate();
        RfidRead read;
        if (simulate) {
            List<RfidRead> reads = rfidReader.scan(request.getEpcCode());
            if (reads.isEmpty()) {
                throw new ResourceNotFoundException(
                        "No active RFID tags registered — register a tag before simulating a read");
            }
            if (reads.size() > 1) {
                throw new IllegalArgumentException(
                        "This endpoint processes one read per request");
            }
            read = reads.get(0);
        } else {
            if (request.getEpcCode() == null || request.getEpcCode().isBlank()) {
                throw new IllegalArgumentException(
                        "External (non-simulated) reads require an epcCode");
            }
            String readerName = request.getReaderName() != null && !request.getReaderName().isBlank()
                    ? request.getReaderName().trim()
                    : "EXTERNAL-READER";
            read = new RfidRead(normalizeEpc(request.getEpcCode()),
                    request.getEventAt() != null ? request.getEventAt() : Instant.now(),
                    false, readerName);
        }

        String reference = request.getEventReference() != null
                && !request.getEventReference().isBlank()
                ? request.getEventReference().trim().toUpperCase()
                : nextReference();
        if (rfidReadEventRepository.existsByEventReferenceIgnoreCase(reference)) {
            throw new ConflictException(
                    "RFID read event '" + reference + "' was already processed");
        }

        Optional<RfidTag> tag = rfidTagRepository.findByEpcCodeIgnoreCase(read.epcCode());
        RfidReadStatus status = tag.isEmpty()
                ? RfidReadStatus.UNKNOWN_TAG
                : (tag.get().isActive() ? RfidReadStatus.RESOLVED : RfidReadStatus.INACTIVE_TAG);

        RfidReadEvent event = new RfidReadEvent(
                reference,
                read.epcCode(),
                tag.orElse(null),
                tag.map(RfidTag::getProduct).orElse(null),
                status,
                read.readerName(),
                read.simulated(),
                read.eventAt(),
                currentUser());
        return rfidMapper.toReadEventResponse(rfidReadEventRepository.save(event));
    }

    @Transactional(readOnly = true)
    public Page<RfidReadEventResponse> listReads(String epcCode, RfidReadStatus status,
                                                 Boolean simulated, Instant from, Instant to,
                                                 Pageable pageable) {
        return rfidReadEventRepository.search(normalizeSearch(epcCode), status, simulated, from, to,
                        pageable)
                .map(rfidMapper::toReadEventResponse);
    }

    @Transactional(readOnly = true)
    public RfidReaderInfoResponse readerInfo() {
        return new RfidReaderInfoResponse(
                rfidReader.readerName(),
                rfidReader.simulated(),
                rfidReader.simulated()
                        ? "Built-in simulator — no physical RFID hardware is integrated in this phase"
                        : "External reader bridge");
    }

    // ---- Helpers ----

    private RfidTag requireTag(String epcCode) {
        String epc = normalizeEpc(epcCode);
        return rfidTagRepository.findByEpcCodeIgnoreCase(epc)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RFID tag '" + epc + "' not found"));
    }

    private Product requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " not found"));
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException("RFID operations require an authenticated user");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    private static String normalizeEpc(String epcCode) {
        if (epcCode == null || epcCode.isBlank()) {
            throw new IllegalArgumentException("EPC code must not be blank");
        }
        return epcCode.trim().toUpperCase();
    }

    private static String nextReference() {
        return "RFD-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase().replace("-", "X");
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }
}
