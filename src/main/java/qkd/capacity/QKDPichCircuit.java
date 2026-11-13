package qkd.capacity;

import java.util.concurrent.atomic.AtomicInteger;

import network.Node;
import network.SectionedCircuit;

/**
 * Parte 65: the PICh of a QKD link as a classical channel (pedido do coorientador): it has modulation and launch
 * power, its QoT is verified, it interferes with the other channels and suffers interference from them, and it has
 * one section per section of the QCh (from one trusted node to the next). See network.SectionedCircuit.
 *
 * <p>Ids are negative and come from their own counter, so creating a PICh does not change the ids of the classical
 * circuits (the circuits of a core are visited in id order).</p>
 */
public final class QKDPichCircuit extends SectionedCircuit {

    private static final long serialVersionUID = 1L;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(-1);

    public QKDPichCircuit(Node from, Node to, double[] sectionsKm, double bitRate) {
        super(Integer.valueOf(NEXT_ID.getAndDecrement()), from, to, sectionsKm, bitRate);
    }
}
