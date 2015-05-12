import java.net.InetSocketAddress;
import java.net.Socket;
/* Author: Yasser Afifi
 * StudentID:14303154
 */
public interface PeerChat {

	// this interface represents the protocol that is given for the project
	public void init(Socket socket, int uid);
	public long joinNetwork(InetSocketAddress bootstrap_node);
	public boolean leaveNetwork(int uid);
	public void chat(String text, String[] tags);
	public ChatResult[] getChat(String[] words);
	
}
