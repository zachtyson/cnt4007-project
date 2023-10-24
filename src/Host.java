import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;



public class Host {
    int PortLimit = 0;
    // each message sent 
    ArrayList<Boolean> bitfield = new ArrayList<Boolean>();
    // the different clients that are connected to the host 
    ArrayList<Socket> Clients = new ArrayList<Socket>();
    ServerSocket serverSocket; 
    public Host(int port, int PortLimit)throws UnknownHostException, IOException
    {
        serverSocket = new ServerSocket(port);
        this.PortLimit = PortLimit;
        listen();
    }

    public void listen() throws UnknownHostException, IOException{
        while(PortLimit>Clients.size())
        {
            Socket Client = serverSocket.accept();
            Clients.add(Client);
           // create thread to handle client 
            Thread t = new Thread(new ClientHandler(Client,serverSocket));
            t.start();
        }
       
    } 



}
