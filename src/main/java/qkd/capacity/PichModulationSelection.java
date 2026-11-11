package qkd.capacity;

import java.util.List;
import gprmcsa.modulation.Modulation;

/** Selects only modulation instances already created by network.Mesh. */
public final class PichModulationSelection {
	private PichModulationSelection() { }

	public static Modulation select(List<Modulation> configured, String name) {
		if (configured == null || configured.isEmpty())
			throw new IllegalArgumentException("No configured classical modulation available for PICh");

		if (name == null || name.trim().isEmpty())
			return configured.get(0); // Preserve network.modulations ordering.
		for (Modulation m : configured)
			if (m != null && m.getName().equalsIgnoreCase(name.trim())) return m;

		throw new IllegalArgumentException(
				"network.qkd.pichModulation must refer to network.modulations: " + name);
	}
}
