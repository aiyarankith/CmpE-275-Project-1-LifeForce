/*
 * copyright 2012, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.server.conf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import poke.server.storage.noop.ElectionNoOpStorage;
import poke.server.storage.noop.TenantNoOpStorage;

/**
 * Routing information for the general - internal use only
 * 
 * TODO refactor StorageEntry to be neutral for cache, file, and db
 * 
 * @author gash
 * 
 */
@XmlRootElement(name = "conf")
@XmlAccessorType(XmlAccessType.FIELD)
public class ServerConf {
	private int nodeId = -1;
	private String nodeName;

	private int numberOfElectionVotes = 1; // used to break ties in elections
	private String forwardingImplementation;
	private String electionImplementation;
	/** public communication (default is 5570) */
	private int port = 5570;
	/** internal node-to-node communication (default is 5571) */
	private int mgmtPort = 5571;
	//Added
	private GeneralConf server;
	private NodeConf description;
	
	private StorageConf storage;
	private AdjacentConf adjacent;
	private List<ResourceConf> routing;

	private volatile HashMap<Integer, ResourceConf> idToRsc;

	private HashMap<Integer, ResourceConf> asMap() {
		if (idToRsc != null)
			return idToRsc;

		if (idToRsc == null) {
			synchronized (this) {
				if (idToRsc == null) {
					idToRsc = new HashMap<Integer, ResourceConf>();
					if (routing != null) {
						for (ResourceConf entry : routing) {
							idToRsc.put(entry.id, entry);
						}
					}
				}
			}
		}

		return idToRsc;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getMgmtPort() {
		return mgmtPort;
	}

	public void setMgmtPort(int mgmtPort) {
		this.mgmtPort = mgmtPort;
	}

	public int getNodeId() {
		return nodeId;
	}

	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

	public String getNodeName() {
		return nodeName;
	}

	public String getForwardingImplementation() {
		return forwardingImplementation;
	}

	public void setForwardingImplementation(String forwardingImplementation) {
		this.forwardingImplementation = forwardingImplementation;
	}

	public String getElectionImplementation() {
		return electionImplementation;
	}

	public void setElectionImplementation(String electionImplementation) {
		this.electionImplementation = electionImplementation;
	}

	public int getNumberOfElectionVotes() {
		return numberOfElectionVotes;
	}

	public void setNumberOfElectionVotes(int numberOfElectionVotes) {
		this.numberOfElectionVotes = numberOfElectionVotes;
	}

	public void addAdjacentNode(NodeDesc node) {
		if (adjacent == null)
			adjacent = new AdjacentConf();

		adjacent.add(node);
	}

	public AdjacentConf getAdjacent() {
		return adjacent;
	}

	public void setAdjacent(AdjacentConf conf) {
		// TODO should be a deep copy
		this.adjacent = conf;
	}

	public StorageConf getStorage() {
		return storage;
	}

	public void setStorage(StorageConf conf) {
		// TODO should be a deep copy
		this.storage = conf;
	}

	public void addResource(ResourceConf entry) {
		if (entry == null)
			return;
		else if (routing == null)
			routing = new ArrayList<ResourceConf>();

		routing.add(entry);
	}

	public ResourceConf findById(int id) {
		return asMap().get(id);
	}

	public List<ResourceConf> getRouting() {
		return routing;
	}

	public void setRouting(List<ResourceConf> conf) {
		this.routing = conf;
	}
	
	//Added
	public GeneralConf getServer() {
		return server;
	}
	//Added
	public void setServer(GeneralConf server) {
		// TODO should be a deep copy
		this.server = server;
	}
	public void addGeneral(String name, String value) {
		if (server == null)
			server = new GeneralConf();

		server.add(name, value);
	}
	
	public void addNode(String name, String value) {
		if (description == null)
			description = new NodeConf();

		description.add(name, value);
	}
	@XmlRootElement(name = "storage")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static final class StorageConf {
		private String tenant = TenantNoOpStorage.class.getName();
		private String election = ElectionNoOpStorage.class.getName();

		public String getTenant() {
			return tenant;
		}

		public void setTenant(String tenant) {
			this.tenant = tenant;
		}

		public String getElection() {
			return election;
		}

		public void setElection(String election) {
			this.election = election;
		}
	}

	/**
	 * storage setup and configuration
	 * 
	 * @author gash1
	 * 
	 */
	@XmlRootElement(name = "adjacent")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static final class AdjacentConf {
		private TreeMap<Integer, NodeDesc> adjacent;

		public NodeDesc getNode(String name) {
			return adjacent.get(name);
		}

		public void add(NodeDesc node) {
			if (node == null)
				return;
			else if (adjacent == null)
				adjacent = new TreeMap<Integer, NodeDesc>();

			adjacent.put(node.getNodeId(), node);
		}

		public TreeMap<Integer, NodeDesc> getAdjacentNodes() {
			return adjacent;
		}

		public void setAdjacentNodes(TreeMap<Integer, NodeDesc> nearest) {
			this.adjacent = nearest;
		}
	}

	/**
	 * command (request) delegation
	 * 
	 * @author gash1
	 * 
	 */
	@XmlRootElement(name = "entry")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static final class ResourceConf {
		private int id;
		private String name;
		private String clazz;
		private boolean enabled;

		public ResourceConf() {
		}

		public ResourceConf(int id, String name, String clazz) {
			this.id = id;
			this.name = name;
			this.clazz = clazz;
		}

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getClazz() {
			return clazz;
		}

		public void setClazz(String clazz) {
			this.clazz = clazz;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}
	}
	
	//Added -- Ankith
	@XmlRootElement(name = "general")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static final class GeneralConf {
		private TreeMap<String, String> general;

		public String getProperty(String name) {
			return general.get(name);
		}

		public void add(String name, String value) {
			if (name == null)
				return;
			else if (general == null)
				general = new TreeMap<String, String>();

			general.put(name, value);
		}

		public TreeMap<String, String> getGeneral() {
			return general;
		}

		public void setGeneral(TreeMap<String, String> general) {
			this.general = general;
		}
	}
	
	@XmlRootElement(name = "node")
	@XmlAccessorType(XmlAccessType.FIELD)
	public static final class NodeConf {
		private TreeMap<String, String> node;

		public String getProperty(String name) {
			return node.get(name);
		}

		public void add(String name, String value) {
			if (name == null)
				return;
			else if (node == null)
				node = new TreeMap<String, String>();

			node.put(name, value);
		}

		public TreeMap<String, String> getNode() {
			return node;
		}

		public void setNode(TreeMap<String, String> node) {
			this.node = node;
		}
	}
}
