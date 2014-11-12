package poke.client.comm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * 
 * @author 
 *
 */
public class MetaDataManager {

	private static Logger logger = LoggerFactory.getLogger("metaDataManager");
	private final String USER_AGENT = "Mozilla/25.0";
	
	//Current MD-Node details
	private String host = "localhost";//10.0.0.7";
	private int port = 3000;
	
	//Primary MD-Node details
	private String host1 = "localhost";//10.0.0.7";
	private int port1 = 3000;
	
	//Secondary/replica MetaDataNode details.
	private String host2 = "localhost";//10.0.0.7";
	private int port2 = 4000;
	
	private final String clientName = "leader";
	private final String passcode = "unlockItsMe";
	
	/**
	 * To get the metadata from active MD-Node.
	 * @param uuid
	 * @return
	 * @throws Exception
	 */
	public int getNodeLocation(String uuid) throws Exception {

		String url = "http://" + host + ":" + port + "/get?uuid=" + uuid;
		String response = "-1";

		URL urlObj = new URL(url);
		HttpURLConnection httpConn = (HttpURLConnection) urlObj
				.openConnection();

		httpConn.setRequestMethod("GET");

		// add request header
		httpConn.setRequestProperty("User-Agent", USER_AGENT);

		logger.info("\nSending 'GET' request to MetaDataNode, to URL : " + url);
		
		int responseCode = 200;
		try {
			responseCode = httpConn.getResponseCode();
			logger.info("Response Code : " + responseCode);
			
			BufferedReader in = new BufferedReader(new InputStreamReader(
					httpConn.getInputStream()));
			String inputLine;

			if ((inputLine = in.readLine()) != null)
				response = inputLine;
			in.close();
			logger.info("MetaData Response: "+ response.toString());

			JSONObject json = new JSONObject(response.substring(1, response.length()-1));
			logger.info("Image location - Node ID :" + json.getInt("nodeId"));
			
			return json.getInt("nodeId");
			
		} catch (java.net.ConnectException e) {
			responseCode = 500;
			logger.error("Connection refused by Current MetaDataNode.");
			shiftToAnotherMDNode();
			 return getNodeLocation(uuid);
		} catch (JSONException e) {
			response = "-1";
			logger.error("Error in JSON response obtained from MD-Node.");
		}
		return Integer.parseInt(response);
	}

	/**
	 * To put the metadata into MD-Nodes.
	 * @param uuid
	 * @param nodeId
	 * @return
	 * @throws Exception
	 */
	public boolean setNodeLocation(String uuid, int nodeId) throws Exception {
		return saveToNode(uuid, nodeId, host1, port1) | saveToNode(uuid, nodeId, host2, port2);
	}

	/**
	 * @param uuid
	 * @param nodeId
	 * @param host
	 * @param port
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws ProtocolException
	 */
	private boolean saveToNode(String uuid, int nodeId, String host, int port)
			throws MalformedURLException, IOException, ProtocolException {
		String url = "http://" + host + ":" + port + "/set?uuid=" + uuid + "&nodeId=" + nodeId;
		URL urlObj = new URL(url);
		HttpURLConnection httpConn = (HttpURLConnection) urlObj
				.openConnection();

		// add reuqest header
		httpConn.setRequestMethod("POST");
		httpConn.setRequestProperty("User-Agent", USER_AGENT);
		// con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

		logger.info("\nSending 'POST' request to MetaDataNode, URL : " + url);
		int responseCode = 200;
		try {
			responseCode = httpConn.getResponseCode();
			logger.info("Response Code : " + responseCode);
		} catch (java.net.ConnectException e) {
			responseCode = 500;
			logger.error("Connection refused by Current MetaDataNode.");
			shiftToAnotherMDNode();
		}

		return responseCode == 200;
	}
	
	/**
	 * Change active MD-Node to retrieve data (invoked in case of node failure).
	 */
	private void shiftToAnotherMDNode() {
		logger.warn("Check the status of the MD-Node, Host: " + host + " Port: " + port);
		if (this.host.equals(host2)) {
			this.host = host1;
			this.port = port1;			
		} else {
			this.host = host2;
			this.port = port2;
		}
		logger.info("Current MD-Node, Host: " + host + " Port: " + port);
	}
	
	public boolean deleteNodeLocation(String uuid) throws Exception {
		return deleteLocation(uuid, host1, port1) | deleteLocation(uuid, host2, port2);
	}
	
	public boolean deleteLocation(String uuid, String host, int port) throws Exception{
		String url = "http://" + host + ":" + port + "/delete?uuid=" + uuid;
		URL urlObj = new URL(url);
		HttpURLConnection httpConn = (HttpURLConnection) urlObj
				.openConnection();

		// add reuqest header
		httpConn.setRequestMethod("GET");
		httpConn.setRequestProperty("User-Agent", USER_AGENT);
		// con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

		logger.info("\nSending 'GET' request to MetaDataNode, URL : " + url);
		int responseCode = 200;
		try {
			responseCode = httpConn.getResponseCode();
			logger.info("Response Code : " + responseCode);
		} catch (java.net.ConnectException e) {
			responseCode = 500;
			logger.error("Connection refused by MetaDataNode, Check MetaDataNode status.");
		}

		return responseCode == 200;
	}
}
