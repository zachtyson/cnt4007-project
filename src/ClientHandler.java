import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class ClientHandler extends Thread {
    Socket clientSocket;
    ServerSocket Host; 
    public ClientHandler (Socket clientSocket, ServerSocket Host) {
        this.clientSocket = clientSocket;
        this.Host = Host;
    }

    public void run () {
       ArrayList<Boolean> bitMessage = new ArrayList<Boolean>();
       
       
    }
}
