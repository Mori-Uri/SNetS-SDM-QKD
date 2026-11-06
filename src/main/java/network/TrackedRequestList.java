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

    private void changed() {
        for (int i = 0; i < owners.size(); i++) {
            owners.get(i).physicalChange();
        }
    }

    @Override
    public boolean add(RequestForConnection e) {
        final boolean r = super.add(e);
        changed();
        return r;
    }

    @Override
    public void add(int index, RequestForConnection e) {
        super.add(index, e);
        changed();
    }

    @Override
    public boolean addAll(Collection<? extends RequestForConnection> c) {
        final boolean r = super.addAll(c);
        changed();
        return r;
    }

    @Override
    public boolean addAll(int index, Collection<? extends RequestForConnection> c) {
        final boolean r = super.addAll(index, c);
        changed();
        return r;
    }

    @Override
    public RequestForConnection set(int index, RequestForConnection e) {
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
