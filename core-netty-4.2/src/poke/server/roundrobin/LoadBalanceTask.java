package poke.server.roundrobin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.managers.ElectionManager;
import poke.server.managers.RoutingManager;
import poke.server.resources.ResourceFactory;

public class LoadBalanceTask extends Thread{
	protected static Logger logger =  LoggerFactory.getLogger("loadbalancethread");
	public LoadBalanceTask(){
		
	}
	
	@Override 
	public void run(){
		logger.info("adaptive weight assignment thread statrted");
		try {
			LoadBalanceTask.sleep(60*1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		while(true){
			try {
				logger.info("load balance interval "+60);
				LoadBalanceTask.sleep(60*1000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			
			if(ElectionManager.getInstance().whoIsTheLeader()!=null && 
					ElectionManager.getInstance().whoIsTheLeader()==ResourceFactory.getCfg().getNodeId()){
				if(RoutingManager.getInstance().getActiveNodeList().size()>1){
					for(int iterateNodeId : RoutingManager.getInstance().getActiveNodeList()){
						RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(iterateNodeId);
						if(rri.getLastAverageResponseTime() != -1 && ElectionManager.getInstance().whoIsTheLeader()!=iterateNodeId){
							logger.info("average time for node"+iterateNodeId+" is "+rri.getLastAverageResponseTime());
						}
					}
				}
			}
		}
	}
}
