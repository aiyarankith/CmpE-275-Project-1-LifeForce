package poke.server.managers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class RoutingInitializer {
protected static AtomicReference<RoutingInitializer> instance = new AtomicReference<RoutingInitializer>();
	
	ConcurrentHashMap<Integer, RoundRobinInitilizers> loadbalancer = new ConcurrentHashMap<Integer, RoundRobinInitilizers>();
	
	public static RoutingInitializer initManager() {
		instance.compareAndSet(null, new RoutingInitializer());
		return instance.get();
	}
	
	public static RoutingInitializer getInstance() {
		// TODO throw exception if not initialized!
		if(instance.get()==null){
			new RoutingInitializer();
		}
		return instance.get();
	}
	
	public RoutingInitializer(){
		
	}
	
	public ConcurrentHashMap<Integer, RoundRobinInitilizers> getBalancer(){
		return this.loadbalancer;
	}
	
	public void setJobMap(ConcurrentHashMap<Integer, RoundRobinInitilizers> loadbalancer){
		this.loadbalancer = loadbalancer;
	}	
}


