package queueless.service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import queueless.concurrency.TokenQueue;
import queueless.exception.QueueLessException;
import queueless.model.Customer;
import queueless.model.ServiceType;
import queueless.model.Token;
import queueless.model.TokenStatus;
import queueless.repository.TokenRepository;
import queueless.util.AppLogger;
import java.util.concurrent.atomic.AtomicInteger;

/** Automated token generation and token life-cycle. */
public class TokenService {

    private final TokenRepository repo;
    private final CustomerService customers;
    private final TokenQueue queue;
    private final int avgServiceMinutes;
    private final AtomicInteger ids = new AtomicInteger();
    private final Map<ServiceType, Integer> sequences = new EnumMap<>(ServiceType.class);

    public TokenService(TokenRepository repo, CustomerService customers, TokenQueue queue, int avgServiceMinutes)
            throws QueueLessException {
        this.repo = repo;
        this.customers = customers;
        this.queue = queue;
        this.avgServiceMinutes = avgServiceMinutes;
        for (ServiceType t : ServiceType.values()) sequences.put(t, 0);
        for (Token t : repo.findAll()) {
            ids.accumulateAndGet(t.getTokenId(), Math::max);
            String n = t.getTokenNumber();
            int seq = Integer.parseInt(n.substring(n.indexOf('-') + 1));
            sequences.merge(t.getServiceType(), seq, Math::max);
        }
    }

    /** After a restart: tokens that were waiting (or being served) go back into the queue. */
    public void restoreQueue() throws QueueLessException {
        for (Token t : repo.findByStatus(TokenStatus.SERVING)) {
            t.setStatus(TokenStatus.WAITING);
            t.setCounterId(0);
            t.setCalledAt(null);
            repo.update(t);
        }
        for (Token t : repo.findByStatus(TokenStatus.WAITING)) queue.offer(t);
    }

    /** Interactive issue: fails immediately if the queue is full. */
    public Token issueToken(int customerId, ServiceType type) throws QueueLessException {
        Token t = createToken(customerId, type);
        if (!queue.offer(t)) {
            cancelInternal(t);
            throw new QueueLessException("The queue is full (" + queue.capacity() + " tokens). Please try later.");
        }
        AppLogger.info("Issued token " + t.getTokenNumber() + " to customer " + customerId);
        return t;
    }

    /** Simulation issue: blocks (wait/notify) while the queue is full. */
    public Token issueTokenBlocking(int customerId, ServiceType type) throws QueueLessException, InterruptedException {
        Token t = createToken(customerId, type);
        try {
            queue.put(t);
        } catch (IllegalStateException e) {
            cancelInternal(t);
            throw new QueueLessException("Queue closed before the token could be queued.");
        }
        AppLogger.info("Issued token " + t.getTokenNumber() + " to customer " + customerId);
        return t;
    }

    private synchronized Token createToken(int customerId, ServiceType type) throws QueueLessException {
        Customer c = customers.getById(customerId);
        boolean alreadyWaiting = repo.findByStatus(TokenStatus.WAITING).stream()
                .anyMatch(t -> t.getCustomerId() == customerId && t.getServiceType() == type);
        if (alreadyWaiting) {
            throw QueueLessException.invalidInput(c.getName() + " already has a waiting token for " + type + ".");
        }
        int seq = sequences.merge(type, 1, Integer::sum);
        String number = type.getPrefix() + "-" + String.format("%03d", seq);
        Token t = new Token(ids.incrementAndGet(), number, customerId, type, c.getPriority(), LocalDateTime.now());
        repo.save(t);
        return t;
    }

    private void cancelInternal(Token t) throws QueueLessException {
        t.setStatus(TokenStatus.CANCELLED);
        repo.update(t);
    }

    public Token getToken(int tokenId) throws QueueLessException {
        return repo.findById(tokenId).orElseThrow(() -> QueueLessException.tokenNotFound("#" + tokenId));
    }

    public Token findByNumber(String number) throws QueueLessException {
        String wanted = number.trim();
        return repo.findAll().stream()
                .filter(t -> t.getTokenNumber().equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> QueueLessException.tokenNotFound(wanted));
    }

    public synchronized void cancelToken(int tokenId) throws QueueLessException {
        Token t = getToken(tokenId);
        if (t.getStatus() != TokenStatus.WAITING) {
            throw QueueLessException.invalidInput("Only waiting tokens can be cancelled (status: " + t.getStatus() + ").");
        }
        if (!queue.remove(t)) {
            throw QueueLessException.invalidInput("Token " + t.getTokenNumber() + " has just been called by a counter.");
        }
        cancelInternal(t);
        AppLogger.info("Cancelled token " + t.getTokenNumber());
    }

    // ---- state changes driven by counters ----
    public void markServing(Token t, int counterId) throws QueueLessException {
        t.setStatus(TokenStatus.SERVING);
        t.setCounterId(counterId);
        t.setCalledAt(LocalDateTime.now());
        repo.update(t);
    }

    public void markCompleted(Token t) throws QueueLessException {
        t.setStatus(TokenStatus.COMPLETED);
        repo.update(t);
    }

    public void markNoShow(Token t) throws QueueLessException {
        t.setStatus(TokenStatus.CANCELLED);
        repo.update(t);
    }

    // ---- queries ----
    public List<Token> waitingSnapshot() { return queue.snapshot(); }

    public List<Token> byStatus(TokenStatus status) throws QueueLessException { return repo.findByStatus(status); }

    public List<Token> all() throws QueueLessException { return repo.findAll(); }

    public int count() throws QueueLessException { return repo.count(); }

    /** 1-based position among waiting tokens of the same service. */
    public int positionOf(Token token) {
        int pos = 0;
        for (Token t : queue.snapshot()) {
            if (t.getServiceType() != token.getServiceType()) continue;
            pos++;
            if (t.equals(token)) return pos;
        }
        return 0;
    }

    public int estimatedWaitMinutes(Token token, int openCounters) {
        int ahead = Math.max(0, positionOf(token) - 1);
        return (int) Math.ceil(ahead * (double) avgServiceMinutes / Math.max(1, openCounters));
    }
}
