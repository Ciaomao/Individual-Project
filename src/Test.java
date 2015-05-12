import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/* Author: Yasser Afifi
 * StudentID:14303154
 */
// this class is only for testing purposes
// it takes command line argument that represents the nodeID and the IP address
public class Test {

	// there are 3 arguments required, args [0] is weahter it is a bootstrap or not
	//args [1] is the nodeID
	//args[2] is the ip
	public static void main(String[] args) {
		int nodeID;
		InetAddress ip;

		try {
			// starting a new instance of ServerThread and listening on port 8767
			ServerSocket server = new ServerSocket(8767);
			Socket socket = server.accept();
			Peer test = new Peer(); // Peer is the class that implements the chat protocol ( PeerChat Interface)
			if (args.length > 0) {
				// this means the bootstrap case, there is no network to join yet
				// so we starting a new network
				if (!args[0].equals("--boot")) {
					nodeID = (Integer.valueOf(args[1]));
					test.init(socket, nodeID);
					// here just a normal node, we need to connect to that bootstrap
					// we need to fetch the network ip from the command line args and join it
				} else if (!args[0].equals("--bootstrap")) {
					ip = InetAddress.getByName(args[1]);
					nodeID = (Integer.valueOf(args[2]));
					test.init(socket, nodeID);// initiate the test with the new IP and the new NodeID
					test.joinNetwork(new InetSocketAddress(ip, 8767));
				}
			} else {
				System.out.println("Please enter Arguments");
			}
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
