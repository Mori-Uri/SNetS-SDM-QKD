package network;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import request.RequestForConnection;

/**
 * Mutable request list with a change stamp for bit-rate caching. Unlike the
 * experimental physical-route invalidator, this class does not allow global
 * cache shortcuts: the Stage-5 QoT cache still fingerprints every physical
 * source per link and core, including QKD/PICh sources.
 *
 * Handles direct list mutations, set, iterator.set, and subList.set.
 */
public final class TrackedRequestList extends ArrayList<RequestForConnection> implements Serializable {
    private static final long serialVersionUID = 1L;
    private long changes;

    public TrackedRequestList() { super(); }

    private static void mark(RequestForConnection r) {
        if (r != null) r.markInTrackedList();
    }
    private static void markAll(Collection<? extends RequestForConnection> c) {
        for (RequestForConnection r : c) mark(r);
    }
    private void changed() {
        changes++;
        PhysicalStateChanges.mutated(); // telemetry only, never cache-skip authorization
    }
    long stamp() { return changes ^ (((long)modCount) << 32); }

    @Override public boolean add(RequestForConnection r) {
        boolean b=super.add(r); mark(r); changed(); return b;
    }
    @Override public void add(int i, RequestForConnection r) {
        super.add(i,r); mark(r); changed();
    }
    @Override public boolean addAll(Collection<? extends RequestForConnection> c) {
        boolean b=super.addAll(c); if(b){ markAll(c); changed(); } return b;
    }
    @Override public boolean addAll(int i, Collection<? extends RequestForConnection> c) {
        boolean b=super.addAll(i,c); if(b){ markAll(c); changed(); } return b;
    }
    @Override public RequestForConnection set(int i,RequestForConnection r) {
        RequestForConnection old=super.set(i,r); mark(r); changed(); return old;
    }
    @Override public RequestForConnection remove(int i) {
        RequestForConnection r=super.remove(i); changed(); return r;
    }
    @Override public boolean remove(Object r) {
        boolean b=super.remove(r); if(b)changed(); return b;
    }
    @Override public boolean removeAll(Collection<?> c) {
        boolean b=super.removeAll(c); if(b)changed(); return b;
    }
    @Override public boolean retainAll(Collection<?> c) {
        boolean b=super.retainAll(c); if(b)changed(); return b;
    }
    @Override public boolean removeIf(Predicate<? super RequestForConnection> p) {
        boolean b=super.removeIf(p); if(b)changed(); return b;
    }
    @Override public void replaceAll(UnaryOperator<RequestForConnection> f) {
        super.replaceAll(f); markAll(this); changed();
    }
    @Override public void sort(java.util.Comparator<? super RequestForConnection> c) {
        super.sort(c); changed();
    }
    @Override public void clear() {
        if(!isEmpty()){ super.clear(); changed(); } else super.clear();
    }
    @Override protected void removeRange(int start,int end) {
        super.removeRange(start,end); if(start!=end) changed();
    }
    @Override public ListIterator<RequestForConnection> listIterator() { return listIterator(0); }
    @Override public ListIterator<RequestForConnection> listIterator(int start) {
        final ListIterator<RequestForConnection> it=super.listIterator(start);
        return new ListIterator<RequestForConnection>() {
            public boolean hasNext(){ return it.hasNext(); }
            public boolean hasPrevious(){ return it.hasPrevious(); }
            public RequestForConnection next(){ return it.next(); }
            public RequestForConnection previous(){ return it.previous(); }
            public int nextIndex(){ return it.nextIndex(); }
            public int previousIndex(){ return it.previousIndex(); }
            public void remove(){ it.remove(); changed(); }
            public void set(RequestForConnection r){ it.set(r); mark(r); changed(); }
            public void add(RequestForConnection r){ it.add(r); mark(r); changed(); }
        };
    }
    @Override public List<RequestForConnection> subList(int from,int to) {
        return new ObservedSubList(super.subList(from,to));
    }
    private final class ObservedSubList extends AbstractList<RequestForConnection> implements RandomAccess {
        private final List<RequestForConnection> delegate;
        ObservedSubList(List<RequestForConnection> list){ delegate=list; }
        public int size(){ return delegate.size(); }
        public RequestForConnection get(int i){ return delegate.get(i); }
        public RequestForConnection set(int i,RequestForConnection r){
            RequestForConnection prev=delegate.set(i,r); mark(r); changed(); return prev;
        }
        public void add(int i,RequestForConnection r){ delegate.add(i,r); mark(r); changed(); }
        public RequestForConnection remove(int i){ RequestForConnection r=delegate.remove(i); changed(); return r; }
        @Override public List<RequestForConnection> subList(int from,int to){
            return new ObservedSubList(delegate.subList(from,to));
        }
    }
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException,ClassNotFoundException {
        in.defaultReadObject(); markAll(this);
    }
}
