package network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import request.RequestForConnection;

/**
 * Parte 54: the list of requests of a circuit. It behaves like the ArrayList it replaces, and also tells its circuits
 * (the owner and any circuit that received the same list through {@link Circuit#setRequests}) that the bit rate may
 * have changed, so the QoT cache of the links they use is invalidated. Changes made through an iterator are not seen;
 * no SNetS code changes the requests that way.
 */
public class TrackedRequestList extends ArrayList<RequestForConnection> {

    private static final long serialVersionUID = 1L;

    private final List<Circuit> owners = new ArrayList<Circuit>(1);

    public TrackedRequestList(Circuit owner) {
        super();
        owners.add(owner);
    }

    /** A circuit that shares this list. */
    void addOwner(Circuit circuit) {
        for (Circuit c : owners) {
            if (c == circuit) return;
        }
        owners.add(circuit);
    }

    /** Parte 57: changes made through the methods below (ArrayList.set does not move modCount). */
    private int changes;

    /**
     * Parte 57: a number that changes whenever the content or the order of the list may have changed: the changes
     * made through this class, plus ArrayList's modCount (which also covers sort and the iterator's remove).
     */
    long stamp() {
        return ((long) changes << 32) ^ (modCount & 0xffffffffL);
    }

    private void changed() {
        changes++;
        for (int i = 0; i < owners.size(); i++) {
            owners.get(i).physicalChange();
        }
    }

    /** Parte 59: marks the requests that join this list (see RequestForConnection.markInTrackedList). */
    private static void mark(RequestForConnection e) {
        if (e != null) e.markInTrackedList();
    }

    private static void markAll(Collection<? extends RequestForConnection> c) {
        for (RequestForConnection e : c) mark(e);
    }

    @Override
    public boolean add(RequestForConnection e) {
        mark(e);
        final boolean r = super.add(e);
        changed();
        return r;
    }

    @Override
    public void add(int index, RequestForConnection e) {
        mark(e);
        super.add(index, e);
        changed();
    }

    @Override
    public boolean addAll(Collection<? extends RequestForConnection> c) {
        markAll(c);
        final boolean r = super.addAll(c);
        changed();
        return r;
    }

    @Override
    public boolean addAll(int index, Collection<? extends RequestForConnection> c) {
        markAll(c);
        final boolean r = super.addAll(index, c);
        changed();
        return r;
    }

    @Override
    public RequestForConnection set(int index, RequestForConnection e) {
        mark(e);
        final RequestForConnection r = super.set(index, e);
        changed();
        return r;
    }

    @Override
    public RequestForConnection remove(int index) {
        final RequestForConnection r = super.remove(index);
        changed();
        return r;
    }

    @Override
    public boolean remove(Object o) {
        final boolean r = super.remove(o);
        changed();
        return r;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        final boolean r = super.removeAll(c);
        changed();
        return r;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        final boolean r = super.retainAll(c);
        changed();
        return r;
    }

    @Override
    public boolean removeIf(Predicate<? super RequestForConnection> filter) {
        final boolean r = super.removeIf(filter);
        changed();
        return r;
    }

    @Override
    public void replaceAll(UnaryOperator<RequestForConnection> operator) {
        super.replaceAll(operator);
        markAll(this);
        changed();
    }

    @Override
    public void clear() {
        super.clear();
        changed();
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        super.removeRange(fromIndex, toIndex);
        changed();
    }
}
