package poke.client.comm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetaDataManager {

	private static Logger logger = LoggerFactory.getLogger("metaDataManager");
	private final String USER_AGENT = "Mozilla/25.0";
	private final String host = "10.0.0.7";
	private final int port = 3000;
	private final String clientName = "leader";
	private final String passcode = "unlockItsMe";

	// HTTP GET request
	public int getNodeLocation(String uuid) throws Exception {

		String url = "http://" + host + ":" + port + "/get?uuid=" + uuid;
		// String url = "http://"+ host +"/search?q=mkyong";
		String response = "-1";

		URL urlObj = new URL(url);
		HttpURLConnection httpConn = (HttpURLConnection) urlObj
				.openConnection();

		httpConn.setRequestMethod("GET");

		// add request header
		httpConn.setRequestProperty("User-Agent", USER_AGENT);

		logger.info("\nSending 'GET' request to MetaDataNode, to URL : " + url);
		int responseCode = httpConn.getResponseCode();
		logger.info("Response Code : " + responseCode);

		BufferedReader in = new BufferedReader(new InputStreamReader(
				httpConn.getInputStream()));
		String inputLine;

		if ((inputLine = in.readLine()) != null)
			response = inputLine;

		in.close();

		// print result
		System.out.println(response.toString());

		JSONObject json = new JSONObject(response.substring(1, response.length()-1));
		logger.info("Image location - Node ID :" + json.getInt("nodeId"));
		
		return json.getInt("nodeId");
	}

	// HTTP POST request
	public boolean setNodeLocation(String uuid, int nodeId) throws Exception {
		String url = "http://" + host + ":" + port + "/set?uuid=" + uuid + "&nodeId=" + nodeId;
		URL urlObj = new URL(url);
		HttpURLConnection httpConn = (HttpURLConnection) urlObj
				.openConnection();

		// add reuqest header
		httpConn.setRequestMethod("POST");
		httpConn.setRequestProperty("User-Agent", USER_AGENT);
		// con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

		logger.info("\nSending 'POST' request to MetaDataNode, URL : " + url);
		int responseCode = httpConn.getResponseCode();
		logger.info("Response Code : " + responseCode);

		return responseCode == 200;
	}

	public static void main(String[] args) throws Exception {

		MetaDataManager http = new MetaDataManager();

		System.out.println("Testing 1 - Send Http GET request");
		http.getNodeLocation("2bb396fc-9cd6-4d3b-b1c4-94eb6abab2cd");

		System.out.println("\nTesting 2 - Send Http POST request");
//		 http.setNodeLocation("1111111", 1);

	}

}