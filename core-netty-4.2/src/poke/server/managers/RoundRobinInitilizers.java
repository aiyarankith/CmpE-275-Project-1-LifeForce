package poke.server.managers;

import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinInitilizers {
	private int processWeight = 3;
	private int jobsInQueue = 0;
	private static RoundRobinInitilizers instance = null;
	
	AtomicInteger jobCountToThisQ = new AtomicInteger(0);
	
	
	private RoundRobinInitilizers(int processWeight, int jobsInQueue) {
		super();
		this.processWeight = processWeight;
		this.jobsInQueue = jobsInQueue;
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
		return jobsInQueue;
	}

	public void addJobsInQueue(int jobsInQueue) {
		jobCountToThisQ.incrementAndGet();
	}
	
	public void reduceJobsInQueue(int jobsInQueue) {
		jobCountToThisQ.decrementAndGet();
	}

	@Override
	public String toString() {
		return "RoundRobinInitilizers [processWeight=" + processWeight
				+ ", jobsInQueue=" + jobsInQueue + "]";
	}
}
