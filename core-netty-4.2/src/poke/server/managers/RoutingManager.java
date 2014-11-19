package poke.server.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.roundrobin.RoundRobinInitilizers;

/**
 * Add nodeid and roundrobinintitializer object map for server-worker node routing
 */
public class RoutingManager {
	protected static Logger logger = LoggerFactory.getLogger("server-worker routing");
	protected static AtomicReference<RoutingManager> instance = new AtomicReference<RoutingManager>();
	
	protected static ConcurrentHashMap<Integer, RoundRobinInitilizers> loadbalancer = 
			new ConcurrentHashMap<Integer, RoundRobinInitilizers>();
	protected static List<Integer> nodeList = Collections.synchronizedList(new ArrayList<Integer>());
	protected static AtomicInteger lastAssigned = new AtomicInteger(-1);
	
	public static RoutingManager initManager() {
		instance.compareAndSet(null, new RoutingManager());
		return instance.get();
	}

	public static RoutingManager getInstance() {
		if (instance.get() == null){
			throw new NullPointerException();
		}
		return instance.get();
	}

	public RoutingManager() {

	}

	public void addNodeToList(int nodeId){
		logger.info(" node added "+ nodeId);
		nodeList.add(nodeId);
	}
	
	public void removeNodeFromLsit(int nodeId){
		logger.info(" node removed "+ nodeId);
		int index = nodeList.indexOf(nodeId);
		nodeList.remove(index);
	} 
	
	public List<Integer> getActiveNodeList() {
		return nodeList;
	}
	
	public void putRobinForNode(int nodeId, RoundRobinInitilizers rri){
		logger.info(" put node into hashmap "+nodeId);
		loadbalancer.put(nodeId, rri);
	}
	
	public void removeRobinFromNode(int nodeId){
		logger.info(" remove node from hashmap "+nodeId);
		loadbalancer.remove(nodeId);
	} 
	
	public ConcurrentHashMap<Integer, RoundRobinInitilizers> getBalancer() {
		return loadbalancer;
	}
	
	//Identify which worker node will get the next job
	public int routeJobs(int nodeId){
		int roundCompleted = 0;
		
		if(lastAssigned.get() == -1){
			logger.info(" first requset by server, will be processed by itself " + lastAssigned.get());
			lastAssigned.set(nodeId);
			RoundRobinInitilizers balanceValue = loadbalancer.get(nodeId);
			balanceValue.addJobsInQueue();
			return nodeId;
		}
	
		int nodeIndex = nodeList.indexOf(lastAssigned.get());
		if((nodeIndex)+1 > nodeList.size()-1){
			nodeIndex = 0;
		}
		else{
			nodeIndex++;
		}
		boolean taskAssigned = true;
		while(taskAssigned){
			RoundRobinInitilizers balanceValue = loadbalancer.get(nodeList.get(nodeIndex));
			if(balanceValue.getJobsInQueue()<balanceValue.getProcessWeight()){
				//assign task to node and set lastAssigned node
				taskAssigned = false;
				lastAssigned.set(nodeList.get(nodeIndex));
				balanceValue.addJobsInQueue();
				logger.info("number of jobs with node "+ nodeList.get(nodeIndex) +" are "+ balanceValue.getJobsInQueue());
				logger.info("job capacity of this node "+ balanceValue.getProcessWeight());
				return nodeList.get(nodeIndex);
			}
			else if(balanceValue.getJobsInQueue()>=balanceValue.getProcessWeight()){
				if(roundCompleted<nodeList.size()){
					//assign task to node and set lastAssigned node
					taskAssigned = false;
					lastAssigned.set(nodeList.get(nodeIndex));
					balanceValue.addJobsInQueue();
					logger.info("number of jobs with node "+ nodeList.get(nodeIndex) +" are "+ balanceValue.getJobsInQueue());
					logger.info("job capacity of this node "+ balanceValue.getProcessWeight());
					return nodeList.get(nodeIndex);
				}else{
					roundCompleted++;
					if((nodeIndex)+1 > nodeList.size()-1){
						nodeIndex = 0;
					}
					else{
						nodeIndex++;
					}
				}
			}else{
				return -1;
			}
		}
		return -1;
	}
}
