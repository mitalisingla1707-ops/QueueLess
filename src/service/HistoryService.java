package queueless.service;

import java.time.LocalDateTime;
import java.util.List;

import queueless.exception.QueueLessException;
import queueless.model.ServiceRecord;
import queueless.model.Token;
import queueless.repository.HistoryRepository;
import java.util.concurrent.atomic.AtomicInteger;

/** Service history tracking. */
public class HistoryService {

    private final HistoryRepository repo;
    private final AtomicInteger ids = new AtomicInteger();

    public HistoryService(HistoryRepository repo) throws QueueLessException {
        this.repo = repo;
        repo.findAll().stream().mapToInt(ServiceRecord::getRecordId).max().ifPresent(v -> ids.accumulateAndGet(v, Math::max));
    }

    public ServiceRecord record(Token token, String customerName, String counterName) throws QueueLessException {
        LocalDateTime started = token.getCalledAt() != null ? token.getCalledAt() : token.getIssuedAt();
        ServiceRecord r = new ServiceRecord(ids.incrementAndGet(), token.getTokenNumber(), token.getCustomerId(), customerName,
                token.getServiceType(), counterName, token.getIssuedAt(), started, LocalDateTime.now());
        repo.save(r);
        return r;
    }

    public List<ServiceRecord> all() throws QueueLessException { return repo.findAll(); }

    public List<ServiceRecord> forCustomer(int customerId) throws QueueLessException {
        return repo.findByCustomerId(customerId);
    }

    public int count() throws QueueLessException { return repo.count(); }
}
