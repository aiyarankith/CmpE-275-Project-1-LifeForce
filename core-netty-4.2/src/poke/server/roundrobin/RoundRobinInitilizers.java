package poke.server.roundrobin;

import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinInitilizers {
	private int processWeight = 3;
	private long lastAverageResponseTime = 0;
	/*private int jobsInQueue = 0;*/
	
	private static RoundRobinInitilizers instance = new RoundRobinInitilizers();
	
	AtomicInteger jobCountToThisQ = new AtomicInteger(0);
	
	
	private RoundRobinInitilizers(int processWeight) {
		super();
		this.processWeight = processWeight;
	}

	private RoundRobinInitilizers() {
		super();
	}
	
	public static RoundRobinInitilizers getInstance() {
		if (instance == null) {
			synchronized (instance) {
				return new RoundRobinInitilizers();
			}
		}
		return instance;
	}

	public int getProcessWeight() {
		return processWeight;
	}

	public void setProcessWeight(int processWeight) {
		this.processWeight = processWeight;
	}

	public int getJobsInQueue() {
		return jobCountToThisQ.get();
	}
	
	public void addJobsInQueue() {
		jobCountToThisQ.incrementAndGet();
	}
	
	public void reduceJobsInQueue() {
		jobCountToThisQ.decrementAndGet();
	}

	@Override
	public String toString() {
		return "RoundRobinInitilizers [processWeight=" + processWeight
				+ ", lastAverageResponseTime=" + lastAverageResponseTime
				+ ", jobCountToThisQ=" + jobCountToThisQ + "]";
	}
}
