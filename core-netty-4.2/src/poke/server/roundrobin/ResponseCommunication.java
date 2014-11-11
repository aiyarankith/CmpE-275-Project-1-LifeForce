package poke.server.roundrobin;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.managers.ElectionManager;
import poke.server.managers.HeartbeatData;
import poke.server.managers.HeartbeatManager;
import poke.server.managers.RoutingManager;
import poke.server.resources.ResourceFactory;
import eye.Comm.Header;
import eye.Comm.Payload;
import eye.Comm.Ping;
import eye.Comm.Request;

public class ResponseCommunication extends Thread{
	protected static Logger logger = LoggerFactory.getLogger("response time pusher");

	private ResponseTimePusher comm;

	public ResponseCommunication() {
	}
	
	@Override
	public void run(){
		logger.info(" start response pusher");
		boolean flag = true;
		while(true){
			try {
				logger.info("poke interval in seconds "+60);
				ResponseCommunication.sleep(60*1000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			if(ElectionManager.getInstance().whoIsTheLeader()!=null && ElectionManager.getInstance().whoIsTheLeader()==ResourceFactory.getCfg().getNodeId()){
				if(RoutingManager.getInstance().getActiveNodeList().size()>1){
					for(int iterateNodeId : RoutingManager.getInstance().getActiveNodeList()){
						ConcurrentHashMap<Integer, HeartbeatData> incomingHB = HeartbeatManager.getInstance().getIncomingHB();
						HeartbeatData hbd = incomingHB.get(iterateNodeId);
						if(ResourceFactory.getCfg().getNodeId()!=iterateNodeId){
							logger.info("--------------"+iterateNodeId);
							comm = new ResponseTimePusher(hbd.getHost(), hbd.getPort());
							Ping.Builder f = eye.Comm.Ping.newBuilder();
							
							f.setTag("node"+iterateNodeId);
							f.setNumber(iterateNodeId);
	
							// payload containing data
							Request.Builder r = Request.newBuilder();
							eye.Comm.Payload.Builder p = Payload.newBuilder();
							p.setPing(f.build());
							r.setBody(p.build());
	
							// header with routing info
							eye.Comm.Header.Builder h = Header.newBuilder();
							h.setOriginator(ResourceFactory.getCfg().getNodeId());
							h.setTag("response time");
							h.setTime(System.nanoTime());
							h.setRoutingId(eye.Comm.Header.Routing.PING);
							r.setHeader(h.build());
	
							eye.Comm.Request req = r.build();
							try {
								comm.sendMessage(req);
							} catch (Exception e) {
								logger.warn("Unable to deliver message, queuing");
							}
							comm = null;
						}
					}
				}else{
					logger.info("response time pusher not started");
				}
			}
			/*if(ElectionManager.getInstance().whoIsTheLeader()!=null && 
					ElectionManager.getInstance().whoIsTheLeader()==ResourceFactory.getCfg().getNodeId()){
				if(RoutingManager.getInstance().getActiveNodeList().size()>1){
					for(int iterateNodeId : RoutingManager.getInstance().getActiveNodeList()){
						RoundRobinInitilizers rri = RoutingManager.getInstance().getBalancer().get(iterateNodeId);
						if(rri.getLastAverageResponseTime() != -1 && ElectionManager.getInstance().whoIsTheLeader()!=iterateNodeId){
							logger.info("average time for node"+iterateNodeId+" is "+rri.getLastAverageResponseTime());
						}
					}
				}
			}*/
		}
		
	}
}
