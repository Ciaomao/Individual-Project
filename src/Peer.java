import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.json.JSONException;
import org.json.JSONObject;

/* Author: Yasser Afifi
 * StudentID:14303154
 */
// this class implements PeerChat interface, which represents the given protocol
public class Peer implements PeerChat {
	int nodeID; // our id
	Thread socketThread;
	static long network_id; // this is a unique identifier for networks
	// this TreeMap will be like a routing table
	// where we will keep all the nodes, NodeIds is represented by Integers
	// it is ok for routingTable to be static as we will run the code only once
	// on each device
	static TreeMap<Integer, InetSocketAddress> routingTable = new TreeMap<Integer, InetSocketAddress>();
	Socket socket;
	// a hashmap to represent a Ping result, true for successful ping
	static HashMap<String, Boolean> ping = new HashMap<String, Boolean>();
	boolean[] tagACK;// an array that will hold tag receipt acknowledgement

	// this method is for initialising the network
	// the socket is for starting the tcp server, nodeID is a unique identifier
	public void init(Socket socket, int nodeID) {
		this.socket = socket;
		// the thread will be dedicated for listening to incoming json messages
		socketThread = new PeerThread(socket, nodeID);
		socketThread.start();
		// we will add the new node to the routing table
		routingTable.put(nodeID, new InetSocketAddress(
				socket.getLocalAddress(), socket.getLocalPort()));
	}

	public long joinNetwork(InetSocketAddress bootstrapNode) {
		// this method send a JOINING_NETWORK Json message to the bootstrap with
		// node details
		try {
			JSONObject message = new JSONObject();
			message.put("type", "JOINING_NETWORK");
			message.put("node_id", String.valueOf(nodeID));
			message.put("ip_address", InetAddress.getLocalHost().toString());
			// get the ip of the bootstrap node
			String ip = bootstrapNode.getAddress().toString();
			// make a socket to that bootstrap
			Socket bootstrapSocket = new Socket(ip, 8767);
			DataOutputStream toBootstrap = new DataOutputStream(
					bootstrapSocket.getOutputStream());
			// write the message to the socket
			toBootstrap.writeUTF(message.toString());
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return network_id;
	}

	// this is a predefined method for hashing tags
	public static int hashCode(String str) {
		int hash = 0;
		for (int i = 0; i < str.length(); i++) {
			hash = hash * 31 + str.charAt(i);
		}
		return Math.abs(hash);
	}

	// sending a message to a particular node using nodeID as the identifier
	static boolean send(JSONObject message, int nodeId) {
		try {
			// find address of the closes node to the given nodeID
			InetSocketAddress inet = routingTable.get(findClosestNode(nodeId));
			// get the ip of the closest node from the routing table
			String ip = inet.getAddress().toString();
			// make a socket to the closest node
			Socket targetSocket = new Socket(ip, 8767);
			DataOutputStream toPeer = new DataOutputStream(
					targetSocket.getOutputStream());// make a socket to that
													// node
			toPeer.writeUTF(message.toString());// write to that socket
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	// this method is used to find the node that is closest numerically to the
	// hashed tag it returns an integer that represents the NodeID
	static int findClosestNode(int tagKey) {
		// Returns a key-value mapping associated with the greatest key less
		// than or equals to the given key
		Entry<Integer, InetSocketAddress> low = routingTable.floorEntry(tagKey);
		// Returns a key-value mapping associated with the least key greater
		// than or equal to the given key
		Entry<Integer, InetSocketAddress> high = routingTable
				.ceilingEntry(tagKey);
		Integer result = 0;
		if (low != null && high != null) { // if both are not null
			// if key is closer to low then result= low, else, result = high
			result = Math.abs(tagKey - low.getKey()) < Math.abs(tagKey
					- high.getKey()) ? low.getKey() : high.getKey();
		}
		// if ONE of low or high is not null
		else if (low != null || high != null) {
			// result = whichever is not null
			result = low != null ? low.getKey() : high.getKey();
		}
		return result;
	}

	@Override
	public boolean leaveNetwork(int uid) {
		try {
			// making a json leave message
			JSONObject message = new JSONObject();
			message.put("type", "LEAVING_NETWORK");
			message.put("node_id", nodeID);
			// we will send a message to all nodes in the routing table when
			// leaving
			for (Entry<Integer, InetSocketAddress> entry : routingTable
					.entrySet()) {
				send(message, entry.getKey());
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return true;
	}

	@Override
	public void chat(String text, String[] tags) {
		tagACK = new boolean[tags.length];// tagAck is the same size as the tags
		try {
			// we will do that for each tag
			// we will create a message that complies with the given protocol in
			// the description
			for (int i = 0; i < tags.length; i++) {
				JSONObject toSend = new JSONObject();
				toSend.put("type", "CHAT");
				// hashCode of the tag will represent the target nodeID or the
				// closest
				toSend.put("target_id", hashCode(tags[i]));
				toSend.put("sender_id", Long.toString(nodeID));// our own id
				toSend.put("tag", tags[i]);// array of associated tags
				toSend.put("text", text);// the message itself
				// send the message to the nodes close to the hashtag to tags
				if (send(toSend, hashCode(tags[i]))) { // if sent successfully
					tagACK[i] = true;
				}
			}
			ExecutorService service = Executors.newSingleThreadExecutor();
			final String[] tagList = tags;
			try {
				Runnable runnable = new Runnable() {
					@Override
					public void run() {
					}
				};
				Future<?> future = service.submit(runnable);
				// Waits for at most 30 seconds for the computation to complete,
				// and then retrieves its result, if available
				future.get(30, TimeUnit.SECONDS); // attempt the task for 30 sec
			} catch (final InterruptedException e) {
				e.printStackTrace();
			} catch (final TimeoutException e) {
				// this catch block will be excuted if the message took took so
				// long
				// for each tage repeat that
				for (int i = 0; i < tags.length; i++) {
					if (!tagACK[i]) { // if ack is false, send a ping message
						try {
							JSONObject toSend = new JSONObject();
							toSend.put("type", "PING");
							toSend.put("target_id", hashCode(tags[i]));
							toSend.put("sender_id", Long.toString(nodeID));
							toSend.put("ip_address", InetAddress.getLocalHost()
									.getHostAddress().toString());
							send(toSend, hashCode(tags[i]));// we will retry
															// sending again
							Thread.sleep(10000);//10 seconds
							// If no ACK then target node is dead and we remove
							// it
							// from routing table.
							synchronized (ping) {
								//remove from table
								if (!ping
										.get((String) toSend.get("ip_address"))) {
									routingTable
											.remove(findClosestNode(hashCode(tags[i])));
								}
							}
						} catch (UnknownHostException | InterruptedException e1) {
							e1.printStackTrace();
						}
					}
				}
			} catch (ExecutionException e) {
				e.printStackTrace();
			} finally {
				service.shutdown();// shut down the service
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public ChatResult[] getChat(String[] words) {
		return null;
	}

	public static void send(JSONObject jsonMessage, InetSocketAddress toAddress) {

	}
}
