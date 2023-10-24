import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

public class Host {
    ArrayList<Boolean> Message = new ArrayList<Boolean>();
    public void listen(int port) throws UnknownHostException, IOException{
        //listen to port
        ServerSocket serverSocket = new ServerSocket(port);

        // can use threads and while loop to represent all connections to the host 
        // array of clients each requesting something different from the host 
        // write to these with our different mesages that are written in message.java 
        Socket Client = serverSocket.accept();
        PrintWriter in  = new PrintWriter(Client.getOutputStream(), true);
        BufferedReader out = new BufferedReader( new InputStreamReader(Client.getInputStream()));
           
        

    } 



}
