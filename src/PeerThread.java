import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* Author: Yasser Afifi
 * StudentID:14303154
 */
// this class will handle incoming data and respond to them
class PeerThread extends Thread {
	Socket socket;
	JSONObject incomingJson;
	int nodeID;// our id

	// constructor
	PeerThread(Socket socket, int id) {
		this.socket = socket;
		this.nodeID = id;
	}

	// setting up inbound and outbound data streams
	public void run() {
		// reading incoming data
		try (DataInputStream fromPeer = new DataInputStream(
				socket.getInputStream());
				// outbound data
				DataOutputStream toPeer = new DataOutputStream(
						socket.getOutputStream());) {
			String peerMessage = fromPeer.readUTF();// reading the message
			JSONObject JsonMessage = new JSONObject(peerMessage);
			switch ((String) incomingJson.get("type")) {
			// in case of joining network, we create a JOINING_NETWORK
			// message and send it to the closest node to the target
			case "JOINING_NETWORK":
				JsonMessage = new JSONObject();
				JsonMessage.put("type", "JOINING_NETWORK_RELAY");
				JsonMessage.put("node_id", incomingJson.getString("node_id"));
				JsonMessage.put("gateway_id", Integer.toString(nodeID));
				Peer.send(JsonMessage,
						Integer.getInteger(incomingJson.getString("node_id")));
				// add node to routing table if we receive such a message
				Peer.routingTable.put(Integer.getInteger(incomingJson
						.getString("node_id")), new InetSocketAddress(
						incomingJson.getString("ip_address"), 8787));
				break;

			// in case of joining network relay, we create a
			// JOINING_NETWORK_RELAY
			// message and a ROUTING_INFO message
			case "JOINING_NETWORK_RELAY":
				if (Peer.findClosestNode(Integer.getInteger(incomingJson
						.getString("node_id"))) != nodeID) { // if that was not
																// us
					JsonMessage = new JSONObject();
					JsonMessage.put("type", "JOINING_NETWORK_RELAY");
					JsonMessage.put("node_id",
							incomingJson.getString("node_id"));
					JsonMessage.put("gateway_id",
							incomingJson.getString("gateway_id"));
					Peer.send(JsonMessage, Integer.getInteger(incomingJson
							.getString("node_id")));
				}
				JsonMessage = new JSONObject();
				JsonMessage.put("type", "ROUTING_INFO");
				JsonMessage.put("node_id", incomingJson.getString("node_id"));
				JsonMessage.put("gateway_id",
						incomingJson.getString("gateway_id"));
				JsonMessage.put("ip_address", InetAddress.getLocalHost()
						.toString());
				for (Entry<Integer, InetSocketAddress> entry : Peer.routingTable
						.entrySet()) {
					Peer.send(JsonMessage, Integer.getInteger(incomingJson
							.getString("node_id")));
				}
				break;

			case "ROUTING_INFO":
				if (Integer.getInteger(incomingJson.getString("gateway_id")) == nodeID) {
					Peer.send(incomingJson, Integer.getInteger(incomingJson
							.getString("node_id")));
				} else {// we will send to the gateway
					Peer.send(incomingJson, Integer.getInteger(incomingJson
							.getString("gateway_id")));
				}
				// All nodes add any routing info that passes through them
				// to their routing tables.
				JSONArray table = incomingJson.getJSONArray("route_table");
				for (int i = 0; i < table.length(); i++) {
					JSONObject entry = table.getJSONObject(i);
					Peer.routingTable.put(Integer.valueOf(entry
							.getString("node_id")),
							new InetSocketAddress(
									entry.getString("ip_address"), 8787));
				}
				break;

			case "LEAVING_NETWORK":

				for (Entry<Integer, InetSocketAddress> entry : Peer.routingTable
						.entrySet()) {
					// we will remove the node from the routing table
					Peer.routingTable.remove(incomingJson.getString("node_id"));
																		
				}

				// in case we receive a ping message
			case "PING":
				// we will create an ACK message with the following details
				InetSocketAddress target;
				JsonMessage = new JSONObject();
				JsonMessage.put("type", "ACK");
				JsonMessage.put("node_id",
						(String) incomingJson.get("target_id"));
				JsonMessage.put("ip_address", InetAddress.getLocalHost()
						.getHostAddress().toString());
				target = new InetSocketAddress(
						InetAddress.getByName((String) incomingJson
								.get("ip_address")), 8787);// creating a socket
				Peer.send(JsonMessage, target);// sending the ack back to the sender
				break;

			default:
				break;
			}

		} catch (JSONException | IOException e) {
			e.printStackTrace();
		}
	}
}