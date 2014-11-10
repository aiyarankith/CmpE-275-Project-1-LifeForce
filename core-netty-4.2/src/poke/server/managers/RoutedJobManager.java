package poke.server.managers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import poke.server.conf.ServerConf;
import poke.server.queue.PerChannelQueue;

public class RoutedJobManager {
	protected static AtomicReference<RoutedJobManager> instance = new AtomicReference<RoutedJobManager>();
	
	ConcurrentHashMap<String, PerChannelQueue> outgoingJOBs = new ConcurrentHashMap<String, PerChannelQueue>();
	
	public static RoutedJobManager initManager() {
		instance.compareAndSet(null, new RoutedJobManager());
		return instance.get();
	}
	
	public static RoutedJobManager getInstance() {
		// TODO throw exception if not initialized!
		if(instance.get()==null){
			new RoutedJobManager();
		}
		return instance.get();
	}
	
	public RoutedJobManager(){
		
	}
	
	public void putJob (String uuid, PerChannelQueue pcq) {
		outgoingJOBs.put(uuid, pcq);
	}
	
	public void removeJob (String uuid) {
		outgoingJOBs.remove(uuid);
	}
	
	public ConcurrentHashMap<String, PerChannelQueue> getJobMap(){
		return this.outgoingJOBs;
	}
	
	public void setJobMap(ConcurrentHashMap<String, PerChannelQueue> outgoingJOBs){
		this.outgoingJOBs = outgoingJOBs;
	}
}
