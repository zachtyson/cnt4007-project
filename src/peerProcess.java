import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Vector;

public class peerProcess {
    // From my understanding the first argument is the peerID, which we can see
    // in PeerInfo.cfg as well as page 7 of the project description pdf
    // e.g 'java peerProcess 1001'
    // from there it will see the field value of the PeerInfo.cfg and if it is 1 then it will set the
    // bitfield to all 1s, otherwise all 0s.
    // Note that the peers are intended to be started in the order they appear in PeerInfo.cfg
    // That is, that 1001 starts first followed by 1002, 1003, ... etc.
    // The peer that just started should try to make TCP connections to all peers that started before it
    // If it is the first peer, it will simply listen on port 6008.
    // When all processes have started, all peers are connected with all other peers.
    // When a peer is connected to at least one other peer,
    // it starts to exchange pieces as described in the protocol description section.
    // A peer terminates when it finds out that all the peers, not just itself,
    // have downloaded the complete file.
    // Common.cfg contains some config data that all peers should read upon starting
    // UnchokingInterval [int(?)]
    // OptimisticUnchokingInterval [int(?)]
    // FileName [string] - Specifies the name of the file peers are interested in
    // FileSize [int] - Specifies the size of the file in bytes
    // PieceSize [int] - Specifies the size of a piece in bytes
    // NumberOfPreferredNeighbors [int] - Sounds self-explanatory, but I have no idea what this means -Zach
    // TODO: Make some notes and comments on other implementations

    public static void main(String[] args) {
        // Check for first argument
        if (args.length < 1) {
            System.out.println("Error: Missing peer ID argument");
            System.exit(1);
        }
        // Attempt to parse peer ID
        int ID = -1;
        try {
            ID = Integer.parseInt(args[0]);
            if(ID < 0) {
                System.out.println("Error: Peer ID must be a positive integer");
                System.exit(1);
            }
        } catch (NumberFormatException e) {
            System.out.println("Error: Peer ID must be an integer");
            System.exit(1);
        }
        peerProcess currentPeerProcess = new peerProcess(ID);
        currentPeerProcess.close();
    }

    public peerProcess(int currentPeerID) {
        this.peerVector = getPeers(currentPeerID);
        System.out.println("Peer " + currentPeerID + " has the following peers:");
        for(Peer peer : peerVector) {
            System.out.println("Peer " + peer.peerId + " at " + peer.peerAddress + ":" + peer.peerPort);
        }
        getCommon();
    }
    public Peer currentPeer;
    public int unchokingInterval;
    public int optimisticUnchokingInterval;
    public String fileName;
    public int fileSize;
    public int pieceSize;
    public int numberOfPreferredNeighbors;
    public Vector<Peer> peerVector;

    public void close() {
        // Close all connections
        for(Peer peer : peerVector) {
            peer.close();
        }
        if(this.currentPeer != null) {
            this.currentPeer.close();
        }
    }

    //"PeerInfo.cfg"
    public Vector <Peer> getPeers(int currentPeerID){
        // Read PeerInfo.cfg
        Vector<Peer> peerVector = new Vector<>();
        String currLine;

        try {
            BufferedReader in = new BufferedReader(new FileReader("PeerInfo.cfg"));
            boolean foundCurrentPeer = false;
            while((currLine = in.readLine()) != null) {
                // Split line by whitespace
                String[] tokens = currLine.split("\\s+");
                try {
                    // Attempt to parse peer ID
                    int tempPeerID = Integer.parseInt(tokens[0]);
                    int peerPort = Integer.parseInt(tokens[2]);
                    if(tempPeerID != currentPeerID) {
                        // If peer ID is not the same as the current peer, add it to the vector
                        peerVector.addElement(new Peer(tempPeerID, tokens[1], peerPort));
                        if(foundCurrentPeer) {
                            // If current peer has already been found, connect to the peer
                            peerVector.lastElement().waitForConnection();
                        }
                    } else {
                        // If current peer ID is the same as the current peer, act as client and attempt to connect to all peers before it
                        // 'before it' is defined as that are already in the vector
                        this.currentPeer = new Peer(tempPeerID, tokens[1], peerPort);
                        for(Peer peer : peerVector) {
                            peer.connectToPeer(this.currentPeer);
                        }
                        foundCurrentPeer = true;
                    }
                    //Code above tries to connect to any peers before it, and any peers after it will connect to it
                } catch (NumberFormatException e) {
                    System.out.println("Error: Peer ID must be an integer");
                    System.exit(1);
                }
            }
            in.close();
        } catch (Exception ex) {
            System.out.println(ex.toString());
        }
        return peerVector;
    }

    private void getCommon() {
        String currLine;
        try {
            BufferedReader in = new BufferedReader(new FileReader("Common.cfg"));
            while((currLine = in.readLine()) != null) {
                // Split line by whitespace
                String[] tokens = currLine.split("\\s+",2);
                if(tokens.length != 2) {
                    // System.out.println("Error: Common.cfg must have 2 fields per line");
                    // System.exit(1);
                    // Not sure if to exit or just continue, but there is an invalid line in the file
                    continue;
                }
                String key = tokens[0];
                String value = tokens[1];
                switch (key) {
                    case "NumberOfPreferredNeighbors" -> numberOfPreferredNeighbors = Integer.parseInt(value);
                    case "UnchokingInterval" -> unchokingInterval = Integer.parseInt(value);
                    case "OptimisticUnchokingInterval" -> optimisticUnchokingInterval = Integer.parseInt(value);
                    case "FileName" -> fileName = value;
                    case "FileSize" -> fileSize = Integer.parseInt(value);
                    case "PieceSize" -> pieceSize = Integer.parseInt(value);
                    default -> {
                        System.out.println("Unknown key in config file: " + key);
                        System.exit(1);
                    }
                }
            }
            in.close();
        } catch (NumberFormatException e) {
            System.out.println("Error: Value must be an integer");
            System.exit(1);
        } catch (Exception ex) {
            System.out.println(ex.toString());
        }
    }
    public static class Peer {
        public int peerId;
        public String peerAddress;
        public int peerPort;
        private Thread clientThread = null;
        private Thread serverThread = null;


        public Peer(int peerId, String peerAddress, int peerPort) {
            this.peerId = peerId;
            this.peerAddress = peerAddress;
            this.peerPort = peerPort;
        }

        public void connectToPeer(Peer currentPeer) {
            //Creates a thread that attempts to connect to the peer
            System.out.println("Attempting to connect to peer " + peerId + " at " + peerAddress + ":" + peerPort);
            clientThread = new ClientThread(peerId, peerAddress, peerPort);
            clientThread.start();


        }

        public Thread waitForConnection() {
            //Creates a thread that waits for a connection from the peer
            System.out.println("Waiting for connection from peer " + peerId + " at " + peerAddress + ":" + peerPort);
            return null;
        }

        // Prepares the peer to be closed for the program to terminate
        public void close() {
            // Close the socket
//            if (socket != null && !socket.isClosed()) {
//                try {
//                    socket.close();
//                    System.out.println("Socket to peer " + peerId + " closed");
//                } catch (IOException e) {
//                    System.err.println("Error closing socket to peer " + peerId + ": " + e.getMessage());
//                }
//            }
//
//            // Interrupt the threads
//            if (sendingThread != null && sendingThread.isAlive()) {
//                try {
//                    sendingThread.interrupt();
//                    sendingThread.join();
//                    System.out.println("Sending thread for peer " + peerId + " interrupted and joined");
//                } catch (InterruptedException e) {
//                    System.err.println("Error interrupting/joining sending thread for peer " + peerId + ": " + e.getMessage());
//                }
//            }
//            if (receivingThread != null && receivingThread.isAlive()) {
//                try {
//                    receivingThread.interrupt();
//                    receivingThread.join();
//                    System.out.println("Receiving thread for peer " + peerId + " interrupted and joined");
//                } catch (InterruptedException e) {
//                    System.err.println("Error interrupting/joining receiving thread for peer " + peerId + ": " + e.getMessage());
//                }
//            }

        }

//        public Thread getSendingThread() {
//            return sendingThread;
//        }
//
//        public Thread getReceivingThread() {
//            return receivingThread;
//        }
    }

    static class ClientThread extends Thread {
        private Socket socket;
        ObjectOutputStream out;
        ObjectInputStream in;
        String message;
        String MESSAGE;

        public int peerId;
        public String peerAddress;
        public int peerPort;

        ClientThread(int peerId, String peerAddress, int peerPort) {
            super("ClientThread");
            this.peerId = peerId;
            this.peerAddress = peerAddress;
            this.peerPort = peerPort;

        }

        @Override
        public void run() {
            int numberOfRetries = 5;  // specify the maximum number of retries
            int timeBetweenRetries = 5000;  // specify the time to wait between retries in milliseconds

            for(int attempt = 0; attempt < numberOfRetries; attempt++) {
                try{
                    //create a socket to connect to the server
                    socket = new Socket("127.0.0.1", 6001);
                    System.out.println("Connected to localhost in port 8000");
                    //initialize inputStream and outputStream
                    out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush();
                    in = new ObjectInputStream(socket.getInputStream());
                    int messageNumber = 0;
                    while(true) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        //read a sentence from the standard input
                        message = "Message number " + messageNumber + " from client " + peerId + " to server " + peerId;
                        //Send the sentence to the server
                        sendMessage(message);
                        //Receive the upperCase sentence from the server
                        MESSAGE = (String)in.readObject();
                        //show the message to the user
                        System.out.println("Receive message: " + MESSAGE);
                        messageNumber++;
                    }

                }
                catch (ConnectException e) {
                    System.err.println("Connection refused. You need to initiate a server first.");
                    try {
                        System.err.println("Retry in " + (timeBetweenRetries / 1000) + " seconds... (" + (attempt + 1) + "/" + numberOfRetries + ")");
                        Thread.sleep(timeBetweenRetries);  // wait for a while before trying to reconnect
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
                catch ( ClassNotFoundException e ) {
                    System.err.println("Class not found");
                }
                catch(UnknownHostException unknownHost){
                    System.err.println("You are trying to connect to an unknown host!");
                }
                catch(IOException ioException){
                    ioException.printStackTrace();
                }
                finally{
                    //Close connections
                    try{
                        if (in != null) in.close();
                        if (out != null) out.close();
                        if (socket != null) socket.close();
                    }
                    catch(IOException ioException){
                        ioException.printStackTrace();
                    }
                }
            }
            System.err.println("Maximum number of attempts reached. Exiting client.");
        }

        void sendMessage(String msg) {
            try {
                //stream write the message
                out.writeObject(msg);
                out.flush();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

}
