package poke.server.managers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class RoutingInitializer {
	protected static AtomicReference<RoutingInitializer> instance = new AtomicReference<RoutingInitializer>();

	protected static ConcurrentHashMap<Integer, RoundRobinInitilizers> loadbalancer = new ConcurrentHashMap<Integer, RoundRobinInitilizers>();

	public static RoutingInitializer initManager() {
		boolean modi = instance.compareAndSet(null, new RoutingInitializer());
		if (modi) System.out.println("settt");
		else System.out.println("not set");
		if (instance == null)
			System.out.println("nul instance");
		return instance.get();
	}

	public static RoutingInitializer getInstance() {
		return instance.get();
	}

	public RoutingInitializer() {

	}

	public ConcurrentHashMap<Integer, RoundRobinInitilizers> getBalancer() {
		return this.loadbalancer;
	}

	public void setJobMap(
			ConcurrentHashMap<Integer, RoundRobinInitilizers> loadbalancer) {
		this.loadbalancer = loadbalancer;
	}
}
