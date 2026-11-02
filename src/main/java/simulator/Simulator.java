package simulator;

import measurement.Measurements;
import network.Pair;
import network.RequestGenerator;
import simulator.eventListeners.ArriveRequestForConexionListener;

/**
 * This class is responsible for instantiating the event machine.
 *
 * @author Iallen
 */
public class Simulator {

    private EventMachine eMachine;
    private ArriveRequestForConexionListener arriveRequest;
    private Simulation simulation;

    /**
     * Creates a new instance of Simulator.
     *
     * @param simulation Simulation
     */
    public Simulator(Simulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Starts the Simulation. For this you need:
	 * (1) loading the mesh;
	 * (2) create the instances of ArriveRequest and FinalizeRequest;
     * (3) scheduling the first requisition arrival events.
     *
     * @return Measurements
     */
    public Measurements start() throws Exception {
        eMachine = new EventMachine();
     // Creating the ArriveRequest event listener
        arriveRequest = new ArriveRequestForConexionListener(this.getEventMachine(), simulation);
        this.scheduleFirstEvents();
        installCheckpoint();
        this.eMachine.executeEvents();
        saveFinished();
        return this.simulation.getMeasurements();
    }

    /** Parte 45: save states of this simulation (null = off). */
    private transient SaveStateStore saveStates;

    /** Parte 45: enables the save states of this simulation. */
    public void setSaveStates(SaveStateStore store) {
        this.saveStates = store;
    }

    /**
     * Parte 45: continues a simulation from an unfinished save state (the Simulation given to the constructor must be
     * the one of the state).
     */
    public Measurements resume(SaveStateStore.State state) throws Exception {
        eMachine = state.eventMachine;
        arriveRequest = state.arriveListener;
        installCheckpoint();
        this.eMachine.executeEvents();
        saveFinished();
        return this.simulation.getMeasurements();
    }

    private void installCheckpoint() {
        if (saveStates == null) return;
        final SaveStateStore store = saveStates;
        eMachine.setCheckpoint(new Runnable() {
            @Override
            public void run() {
                store.save(simulation, eMachine, arriveRequest, false);
            }
        }, store.getIntervalNanos());
    }

    private void saveFinished() {
        if (saveStates != null) saveStates.save(simulation, eMachine, arriveRequest, true);
    }

    /**
     * Schedule the first events of arrive request.
	 * This is done for each network request generator.
     */
    private void scheduleFirstEvents() {
        for (Pair pair : simulation.getMesh().getPairList()) {
            for (RequestGenerator rg : pair.getRequestGenerators()) {
                rg.scheduleNextRequest(eMachine, arriveRequest);
            }
        }
    }

    /**
     * Returns the event machine
     *
     * @return EventMachine
     */
    public EventMachine getEventMachine() {
        return this.eMachine;
    }

    /**
     * Returns the simulation
     *
     * @return Simulation
     */
    public Simulation getSimulation() {
        return this.simulation;
    }
}
