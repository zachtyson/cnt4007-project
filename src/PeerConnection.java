import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;

public class PeerConnection extends Thread{
    public int peerId;
    public String peerAddress;
    public int peerPort;
    private Socket socket;
    OutputStream out;
    InputStream in;
    Boolean client;
    boolean[] bitfield;
    peerProcess.CommonCfg commonCfg;
    SendHandler sendHandler;
    ReceiveHandler receiveHandler;
    peerProcess hostProcess;
    public static class SendHandler extends Thread {
        PeerConnection peerConnection;
        SendHandler(PeerConnection peerConnection) {
            this.peerConnection = peerConnection;
        }
        public void run() {
        }
        void sendMessage(byte[] msg) {
            try {
                //stream write the message
                peerConnection.out.write(msg);
                peerConnection.out.flush();
                System.out.println("Sent message to peer " + peerConnection.peerId);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

    }
    public static class ReceiveHandler extends Thread{
        PeerConnection peerConnection;
        ReceiveHandler(PeerConnection peerConnection) {
            this.peerConnection = peerConnection;
        }
        public void run() {
        }
        byte[] receiveMessageLength() throws IOException {
            // Each message (given from specifications) begins with a 4 byte length header
            // This method reads the length header and returns the message
            int expectedLength = peerConnection.in.read();  // Assumes a 4-byte length header
            return receiveMessage(expectedLength);
        }

        byte[] receiveMessage(int expectedLength) throws IOException {
            // Read message of length expectedLength bytes
            byte[] message = new byte[expectedLength]; //add +1 later for message type?
            int offset = 0;

            while (offset < expectedLength) {
                int bytesRead = peerConnection.in.read(message, offset, expectedLength - offset);
                if (bytesRead == -1) {
                    throw new IOException("Connection was terminated before message was complete.");
                }
                offset += bytesRead;
            }

            return message;
        }
    }

    public PeerConnection(int peerId, String peerAddress, int peerPort, peerProcess hostProcess, Boolean client, peerProcess.CommonCfg commonCfg) {
        super();
        this.peerId = peerId;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.client = client;
        this.hostProcess = hostProcess;
        this.commonCfg = commonCfg;
        //Set bitfield to all 0s
        //all elements are false by default
        bitfield = new boolean[commonCfg.numPieces];
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        System.out.println("Starting connection to peer " + peerId);
        if(client == null) {
            //This should never happen
            System.out.println("Error: client is null, verify that the peer is in the correct order in PeerInfo.cfg and that currentPeer is set correctly");
        } else if (client) {
            client();
        }
        //at this point the connection is established and handshake is done, and we can start sending/receiving messages
        //check if current peer has the file DONE AT STARTUP
        byte[] bitfieldMessage = Message.generateBitmapMessage(this.hostProcess.selfBitfield);
//            for (byte b : bitfieldMessage) {
//                String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
//                System.out.print(bits + " ");
//            }
//            System.out.println(); // New line after printing all bytes
        //if it does, send a bitfield message with all 1s
        //the assignment doesn't specify if a peer can start with a partial file, so I'm assuming now for now just to make things easier
        close();
    }

//        public void server() {
//            try (ServerSocket serverSocket = new ServerSocket(this.currentPeerConnection.peerPort)) {
//                System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "...");
//                socket = serverSocket.accept();
//                System.out.println("Connected to " + socket.getRemoteSocketAddress());
//                in = new DataInputStream(socket.getInputStream());
//                out = new DataOutputStream(socket.getOutputStream());
//                if(!peerHandshake()) {
//                    System.exit(1);
//                    close();  // Close connections when finished or in case of an error
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            System.err.println("Exiting client.");
//        }


    public void client() {
        int numberOfRetries = 5;  // specify the maximum number of retries
        int timeBetweenRetries = 5000;  // specify the time to wait between retries in milliseconds
        int attempt = 0;
        for(attempt = 0; attempt < numberOfRetries; attempt++) {
            try{
                //create a socket to connect to the server
                String address = this.peerAddress;
                int port = this.peerPort;
                System.out.println("Attempting to connect to " + address + " in port " + port);
                socket = new Socket(address, port);
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                System.out.println("Connected to " + address + " in port " + port);
                //initialize inputStream and outputStream
                if(!peerProcess.peerHandshake(socket,this.hostProcess.selfPeerId,this.peerId)) {
                    System.exit(1);
                    close();  // Close connections when finished or in case of an error
                }
                break;
            }
            catch (ConnectException e) {
                System.err.println("Connection refused. You need to initiate a server first.");
                System.err.println(this.peerAddress+" "+this.peerPort+" "+this.peerId);
                try {
                    System.err.println("Retry in " + (timeBetweenRetries / 1000) + " seconds... (" + (attempt + 1) + "/" + numberOfRetries + ")");
                    Thread.sleep(timeBetweenRetries);  // wait for a while before trying to reconnect
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch(UnknownHostException unknownHost){
                System.err.println("You are trying to connect to an unknown host!");
            }
            catch(IOException ioException){
                ioException.printStackTrace();
            }
        }
        if(attempt == numberOfRetries) {
            System.err.println("Maximum number of attempts reached. Exiting client.");
        }
        System.err.println("Exiting client.");
    }


    // Prepares the peer to be closed for the program to terminate
    public void close() {
        //Close the socket
        System.err.println("Closing connection to peer " + peerId);
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error closing resources for peer " + peerId + ": " + e.getMessage());
        }
    }
}
