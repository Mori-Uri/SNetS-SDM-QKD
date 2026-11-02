package simulator;

import network.Circuit;

import java.io.Serializable;
import java.util.Vector;

/**
 * This class represents the simulator event machine.
 * It is responsible for managing the events that are created during a simulation.
 *
 * @author Iallen
 */
@SuppressWarnings("serial")
/**
 * Represents the EventMachine component.
 */
public class EventMachine implements Serializable {

    private Vector<Event> eventList;
    private double countEvent = 0;
    /** Parte 45: save-state hook run between two events at most every checkpointIntervalNanos; null = off. */
    private transient Runnable checkpointHook;
    private transient long checkpointIntervalNanos;
    private transient long lastCheckpointNanos;

    /** Parte 45: runs {@code hook} between events, at most once every {@code intervalNanos}. */
    public void setCheckpoint(Runnable hook, long intervalNanos) {
        this.checkpointHook = hook;
        this.checkpointIntervalNanos = intervalNanos;
        this.lastCheckpointNanos = System.nanoTime();
    }

    /**
     * Creates a new instance of EventMachine
     */
    public EventMachine() {
        this.eventList = new Vector<Event>();
    }

    /**
     * Inserts an event in the event machine.
     *
     * @param e Event
     */
    public void insert(Event e) {
        e.setId(this.countEvent);
        this.countEvent++;
        // Parte 45 (desempenho): binary search of the first event with time >= e.time, the same position as the
        // former linear scan (events with equal time keep their order: the new one goes before them).
        final double t = e.getTimeHours();
        int lo = 0;
        int hi = eventList.size();
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (eventList.elementAt(mid).getTimeHours() < t) lo = mid + 1;
            else hi = mid;
        }
        eventList.insertElementAt(e, lo);
    }

    /**
     * Starts running the event machine.
     * The event machine runs until there are no more events in the eventList.
     */
    public void executeEvents() throws Exception {
        while (eventList.size() > 0) {
            Event e = eventList.firstElement();
            eventList.removeElementAt(0);
            e.listener().execute(e);
            if (checkpointHook != null && System.nanoTime() - lastCheckpointNanos >= checkpointIntervalNanos) {
                checkpointHook.run();
                lastCheckpointNanos = System.nanoTime();
            }
        }
    }

    /**
     * Returns the number of scheduled events (existing in the eventList).
     *
     * @return int
     */
    public int size() {
        return this.eventList.size();
    }

    /**
     * Ends the event machine (clear eventList).
     */
    public void stopMachine() {
        this.eventList.removeAllElements();
    }

    /**
     * Remove the event that contains the request.
     *
     * @param request Circuit
     */
    public void remove(Circuit request) {
        for (int i = 0; i < this.eventList.size(); i++) {
            Event event = this.eventList.get(i);
            if (event.getObject() instanceof Circuit) {
                if (event.getObject() == request) {
                    this.eventList.remove(event);
                    break;
                }
            }
        }
    }
}

