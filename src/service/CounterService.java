package queueless.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import queueless.concurrency.TokenQueue;
import queueless.exception.QueueLessException;
import queueless.model.Customer;
import queueless.model.ServiceCounter;
import queueless.model.ServiceRecord;
import queueless.model.ServiceType;
import queueless.model.Token;
import queueless.util.AppLogger;

/** Multiple service counters: open/close, call next customer, complete service. */
public class CounterService {

    private final List<ServiceCounter> counters = new ArrayList<>();
    private final TokenService tokens;
    private final CustomerService customers;
    private final HistoryService history;
    private final TokenQueue queue;

    public CounterService(Map<String, Integer> config, TokenService tokens, CustomerService customers,
                          HistoryService history, TokenQueue queue) {
        this.tokens = tokens;
        this.customers = customers;
        this.history = history;
        this.queue = queue;
        int id = 1;
        for (ServiceType type : ServiceType.values()) {
            int n = config.getOrDefault(type.name(), 1);
            for (int i = 0; i < n; i++) {
                counters.add(new ServiceCounter(id, "Counter " + id, type));
                id++;
            }
        }
    }

    public synchronized List<ServiceCounter> getCounters() {
        return Collections.unmodifiableList(new ArrayList<>(counters));
    }

    public synchronized ServiceCounter getCounter(int id) throws QueueLessException {
        return counters.stream().filter(c -> c.getId() == id).findFirst()
                .orElseThrow(() -> QueueLessException.counterUnavailable("No counter with id " + id));
    }

    public synchronized int openCountersFor(ServiceType type) {
        return (int) counters.stream().filter(c -> c.getService() == type && c.isOpen()).count();
    }

    public synchronized void setOpen(int counterId, boolean open) throws QueueLessException {
        ServiceCounter c = getCounter(counterId);
        if (!open && c.isBusy()) {
            throw QueueLessException.counterUnavailable(c.getName() + " is serving a customer. Complete the service first.");
        }
        c.setOpen(open);
        AppLogger.info(c.getName() + (open ? " opened" : " closed"));
    }

    /** Non-blocking: next customer for this counter, or empty when nobody is waiting. */
    public synchronized Optional<Token> callNext(int counterId) throws QueueLessException {
        ServiceCounter c = getCounter(counterId);
        ensureReady(c);
        Token t = queue.pollFor(c.getService());
        if (t == null) return Optional.empty();
        begin(c, t);
        return Optional.of(t);
    }

    /** Blocking (used by worker threads): waits until a token arrives; null when the queue is closed. */
    public Token callNextBlocking(int counterId) throws QueueLessException, InterruptedException {
        ServiceCounter c;
        synchronized (this) {
            c = getCounter(counterId);
            ensureReady(c);
        }
        Token t = queue.takeFor(c.getService());     // may wait(); must not hold this lock here
        if (t == null) return null;
        synchronized (this) {
            begin(c, t);
        }
        return t;
    }

    public synchronized ServiceRecord completeService(int counterId) throws QueueLessException {
        ServiceCounter c = getCounter(counterId);
        if (!c.isBusy()) throw QueueLessException.counterUnavailable(c.getName() + " is not serving anyone.");
        Token t = tokens.getToken(c.getCurrentTokenId());
        Customer customer = customers.getById(t.getCustomerId());
        tokens.markCompleted(t);
        ServiceRecord record = history.record(t, customer.getName(), c.getName());
        c.setCurrentTokenId(0);
        c.incrementServed();
        AppLogger.info(c.getName() + " completed " + t.getTokenNumber());
        return record;
    }

    public synchronized Token markNoShow(int counterId) throws QueueLessException {
        ServiceCounter c = getCounter(counterId);
        if (!c.isBusy()) throw QueueLessException.counterUnavailable(c.getName() + " is not serving anyone.");
        Token t = tokens.getToken(c.getCurrentTokenId());
        tokens.markNoShow(t);
        c.setCurrentTokenId(0);
        AppLogger.info(c.getName() + " marked no-show " + t.getTokenNumber());
        return t;
    }

    /** Token number currently at the counter, or "-" when idle. */
    public synchronized String currentTokenNumber(ServiceCounter c) {
        if (!c.isBusy()) return "-";
        try {
            return tokens.getToken(c.getCurrentTokenId()).getTokenNumber();
        } catch (QueueLessException e) {
            return "?";
        }
    }

    private void ensureReady(ServiceCounter c) throws QueueLessException {
        if (!c.isOpen()) throw QueueLessException.counterUnavailable(c.getName() + " is closed.");
        if (c.isBusy()) throw QueueLessException.counterUnavailable(c.getName() + " is already serving a customer.");
    }

    private void begin(ServiceCounter c, Token t) throws QueueLessException {
        tokens.markServing(t, c.getId());
        c.setCurrentTokenId(t.getTokenId());
        AppLogger.info(c.getName() + " called " + t.getTokenNumber());
    }
}
