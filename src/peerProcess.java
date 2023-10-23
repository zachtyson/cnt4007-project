import java.io.*;
import java.net.*;
import java.util.BitSet;
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
        // Create a new peerProcess object
        peerProcess currentPeerProcess = new peerProcess(ID);
        //A peer process is terminated when it finds out that all the peers, not just itself, have downloaded the complete file.
        currentPeerProcess.close();
    }

    public peerProcess(int currentPeerID) {
        getCommon();
        this.peerThreadVector = getPeers(currentPeerID);
        System.out.println("Peer " + currentPeerID + " has the following peers:");
        for(PeerThread peerThread : peerThreadVector) {
            System.out.println("Peer " + peerThread.peerId + " at " + peerThread.peerAddress + ":" + peerThread.peerPort);
            peerThread.start();
        }
    }
    public PeerThread currentPeerThread;
    public Vector<PeerThread> peerThreadVector;
    public CommonCfg commonCfg;

    public void close() {
        // Close all connections
        for(PeerThread peerThread : peerThreadVector) {
            peerThread.close();
        }
        if(this.currentPeerThread != null) {
            this.currentPeerThread.close();
        }
    }

    //"PeerInfo.cfg"
    public Vector <PeerThread> getPeers(int currentPeerID){
        // Read PeerInfo.cfg
        Vector<PeerThread> peerThreadVector = new Vector<>();
        String currLine;
        this.currentPeerThread = new PeerThread();
        this.currentPeerThread.commonCfg = this.commonCfg;
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
                    int hasFile = Integer.parseInt(tokens[3]);
                    boolean hasFileOnStart = hasFile == 1;
                    if(tempPeerID != currentPeerID) {
                        // If peer ID is not the same as the current peer, add it to the vector
                        peerThreadVector.addElement(new PeerThread(tempPeerID, tokens[1], peerPort, this.currentPeerThread, !foundCurrentPeer,commonCfg));
                    } else {
                        // If current peer ID is the same as the current peer, act as client and attempt to connect to all peers before it
                        // 'before it' is defined as that are already in the vector
                        this.currentPeerThread.setPeerValues(tempPeerID, tokens[1], peerPort, this.currentPeerThread, null);
                        if(hasFileOnStart) {
                            // If currentPeerThread has the file (indicated in PeerInfo.cfg), set the bitfield to all 1s
                            File file = new File(this.commonCfg.fileName);
                            if(!file.exists()) {
                                System.err.println("Error: File " + this.commonCfg.fileName + " does not exist");
                                System.exit(1);
                            }
                            int fileSize = this.commonCfg.fileSize;
                            int pieceSize = this.commonCfg.pieceSize;
                            int numberOfPieces = (int) Math.ceil((double) fileSize / pieceSize);
                            //send bitfield message with all 1s
                            this.currentPeerThread.bitfield = new BitSet(numberOfPieces);
                        }
                        //currentPeer is just a peer extension of peerProcess that is used to connect to peers before it
                        //client is set to null because it shouldn't be used in any thread context
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
        return peerThreadVector;
    }

    private void getCommon() {
        String currLine;
        try {
            BufferedReader in = new BufferedReader(new FileReader("Common.cfg"));
            int numberOfPreferredNeighbors = -1;
            int unchokingInterval = -1;
            int optimisticUnchokingInterval = -1;
            String fileName = null;
            int fileSize = -1;
            int pieceSize = -1;

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
                    case "NumberOfPreferredNeighbors":
                        numberOfPreferredNeighbors = Integer.parseInt(value);
                        break;
                    case "UnchokingInterval":
                        unchokingInterval = Integer.parseInt(value);
                        break;
                    case "OptimisticUnchokingInterval":
                        optimisticUnchokingInterval = Integer.parseInt(value);
                        break;
                    case "FileName":
                        fileName = value;
                        break;
                    case "FileSize":
                        fileSize = Integer.parseInt(value);
                        break;
                    case "PieceSize":
                        pieceSize = Integer.parseInt(value);
                        break;
                    default:
                        System.out.println("Unknown key in config file: " + key);
                        System.exit(1);
                }
            }
            commonCfg = new CommonCfg(numberOfPreferredNeighbors, unchokingInterval, optimisticUnchokingInterval, fileName, fileSize, pieceSize);
            in.close();
        } catch (NumberFormatException e) {
            System.out.println("Error: Value must be an integer");
            System.exit(1);
        } catch (Exception ex) {
            System.out.println(ex.toString());
        }
    }
    public static class PeerThread extends Thread{
        public int peerId;
        public String peerAddress;
        public int peerPort;
        private Socket socket;
        OutputStream out;
        InputStream in;
        Boolean client;
        PeerThread currentPeerThread;
        BitSet bitfield;
        CommonCfg commonCfg;
        public PeerThread(int peerId, String peerAddress, int peerPort, PeerThread currentPeerThread, Boolean client, CommonCfg commonCfg) {
            super();
            this.peerId = peerId;
            this.peerAddress = peerAddress;
            this.peerPort = peerPort;
            this.client = client;
            this.currentPeerThread = currentPeerThread;
            this.commonCfg = commonCfg;
        }

        public PeerThread() {
            super();
        }

        public void setPeerValues(int peerId, String peerAddress, int peerPort, PeerThread currentPeerThread, Boolean client) {
            this.peerId = peerId;
            this.peerAddress = peerAddress;
            this.peerPort = peerPort;
            this.client = client;
            this.currentPeerThread = currentPeerThread;
        }

        void sendMessage(byte[] msg) {
            try {
                //stream write the message
                out.write(msg);
                out.flush();
                System.out.println("Sent message to peer " + peerId);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }

        byte[] receiveMessageLength() throws IOException {
            // Each message (given from specifications) begins with a 4 byte length header
            // This method reads the length header and returns the message
            int expectedLength = in.read();  // Assumes a 4-byte length header
            return receiveMessage(expectedLength);
        }

        byte[] receiveMessage(int expectedLength) throws IOException {
            // Read message of length expectedLength bytes
            byte[] message = new byte[expectedLength];
            int offset = 0;

            while (offset < expectedLength) {
                int bytesRead = in.read(message, offset, expectedLength - offset);
                if (bytesRead == -1) {
                    throw new IOException("Connection was terminated before message was complete.");
                }
                offset += bytesRead;
            }

            return message;
        }

        @Override
        public void run() {
            System.out.println("Starting connection to peer " + peerId);
            if(client == null) {
                //This should never happen
                System.out.println("Error: client is null, verify that the peer is in the correct order in PeerInfo.cfg and that currentPeer is set correctly");
            } else if (client) {
                client();
            } else {
                server();
            }
            //at this point the connection is established and we can start sending messages
            //check if current peer has the file
            //if it does, send a bitfield message with all 1s
            //the assignment doesn't specify if a peer can start with a partial file, so I'm assuming now for now just to make things easier

        }

        public void server() {
            try (ServerSocket serverSocket = new ServerSocket(this.currentPeerThread.peerPort)) {
                System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "...");
                socket = serverSocket.accept();
                System.out.println("Connected to " + socket.getRemoteSocketAddress());
                if(!peerHandshake()) {
                    System.exit(1);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                close();  // Close connections when finished or in case of an error
            }
        }

        private boolean peerHandshake() throws IOException {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            sendMessage(Message.createHandshakePayload(this.currentPeerThread.peerId));
            byte[] handshakeMessage = receiveMessage(32);
            //handshake is always 32 bytes

            if (!Message.checkHandshake(handshakeMessage, this.peerId)) {
                System.err.println("Handshake failed");
                return false;
            }

//            if(this.currentPeerThread.hasFileOnStart) {
//                File file = new File(this.commonCfg.fileName);
//                int fileSize = this.commonCfg.fileSize;
//                int pieceSize = this.commonCfg.pieceSize;
//                int numberOfPieces = (int) Math.ceil((double) fileSize / pieceSize);
//
//                //send bitfield message with all 1s
//                bitfield = new BitSet(numberOfPieces);
//                bitfield.set(0, numberOfPieces);
//                byte[] bitfieldMessage = Message.generateBitmapMessage(bitfield);
//                //print out each individual bit
//                for(int i = 0; i < bitfieldMessage.length; i++) {
//                    for(int j = 0; j < 8; j++) {
//                        System.out.print((bitfieldMessage[i] >> j) & 1);
//                    }
//                }
//
//            } else {
//                //if no file then no bitfield message, can just wait for other peers to send bitfield messages
//            }

            System.err.println("Handshake successful");
            return true;
        }

        public void client() {
            int numberOfRetries = 5;  // specify the maximum number of retries
            int timeBetweenRetries = 5000;  // specify the time to wait between retries in milliseconds

            for(int attempt = 0; attempt < numberOfRetries; attempt++) {
                try{
                    //create a socket to connect to the server
                    String address = this.peerAddress;
                    int port = this.peerPort;
                    System.out.println("Attempting to connect to " + address + " in port " + port);
                    socket = new Socket(address, port);
                    System.out.println("Connected to " + address + " in port " + port);
                    //initialize inputStream and outputStream
                    peerHandshake();
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


        // Prepares the peer to be closed for the program to terminate
        public void close() {
            //Close the socket
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                    System.out.println("Socket to peer " + peerId + " closed");
                } catch (IOException e) {
                    System.err.println("Error closing socket to peer " + peerId + ": " + e.getMessage());
                }
            }
        }
    }

    public static class CommonCfg {
        public int numberOfPreferredNeighbors;
        public int unchokingInterval;
        public int optimisticUnchokingInterval;
        public String fileName;
        public int fileSize;
        public int pieceSize;
        public CommonCfg(int numberOfPreferredNeighbors, int unchokingInterval, int optimisticUnchokingInterval, String fileName, int fileSize, int pieceSize) {
            this.numberOfPreferredNeighbors = numberOfPreferredNeighbors;
            this.unchokingInterval = unchokingInterval;
            this.optimisticUnchokingInterval = optimisticUnchokingInterval;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.pieceSize = pieceSize;
        }
    }

}
