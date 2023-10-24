import java.io.*;
import java.net.*;
import java.util.Arrays;
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
        //currentPeerProcess.close();
    }

    public peerProcess(int currentPeerID) {
        try {
            getCommon();
            this.peerConnectionVector = getPeers(currentPeerID);
        } catch (IOException e) {
            System.out.println("Error: PeerInfo.cfg not found");
        }
        startListening();
        startConnection();
    }

    public void startListening() {
        ServerSocketThread serverSocketThread = new ServerSocketThread(this.selfPeerPort, this);
        serverSocketThread.start();
    }

    public void startConnection() {
        for(PeerConnection peerConnection : this.peerConnectionVector) {
            if(peerConnection.client) {
                peerConnection.start();
            }
        }
    }

    public static class ServerSocketThread extends Thread {
        private final int port;
        private final peerProcess hostProcess;

        public ServerSocketThread(int port, peerProcess hostProcess) {
            this.port = port;
            this.hostProcess = hostProcess;
        }

        @Override
        public void run() {
            Vector<PeerConnection> peerServerWaitVector = this.hostProcess.peerConnectionVector;
            Vector<PeerConnection> serverWait = new Vector<PeerConnection>();
            for(PeerConnection peerConnection : peerServerWaitVector) {
                if(!peerConnection.client) {
                    serverWait.add(peerConnection);
                }
            }
            if(serverWait.isEmpty()) {
                System.out.println("No peers to wait for");
                //terminate thread
                return;
            }
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while(true) {
                    System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "...");
                    Socket socket = serverSocket.accept();
                    System.out.println("Connected to " + socket.getRemoteSocketAddress());
                    int peerId = peerHandshakeServerSocket(socket, this.hostProcess.selfPeerId);
                    boolean found = false;
                    for(PeerConnection peerConnection : serverWait) {
                        if(peerConnection.peerId == peerId) {
                            peerConnection.setSocket(socket);
                            found = true;
                            break;
                        }
                    }
                    if(!found) {
                        System.err.println("Error: Peer ID " + peerId);
                        for(int i = 0; i < serverWait.size(); i++) {
                            System.err.println(serverWait.get(i).peerId);
                        }
                        System.exit(1);
                    }
                    System.err.println("Handshake successful");
                    //if peerId is not found, then we have a problem
                    for(PeerConnection peerConnection : serverWait) {
                        if(peerConnection.peerId == peerId) {
                            peerConnection.start();
                            peerServerWaitVector.remove(peerConnection);
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public Vector<PeerConnection> peerConnectionVector;
    public CommonCfg commonCfg;
    public int selfPeerId;
    public String selfPeerAddress;
    public int selfPeerPort;
    boolean[] selfBitfield;
    public void close() {
        // Close all connections
        for(PeerConnection peerConnection : peerConnectionVector) {
            peerConnection.close();
        }
    }

    //"PeerInfo.cfg"
    public Vector <PeerConnection> getPeers(int currentPeerID) throws IOException {
        // Read PeerInfo.cfg
        String currLine;
        peerConnectionVector = new Vector<>();
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
                    // Following my line of thought, we should probably ignore this value unless it's
                    // the current peer, in which case we should set the bitfield to all 1s
                    if(tempPeerID != currentPeerID) {
                        // If peer ID is not the same as the current peer, add it to the vector
                        PeerConnection peerConnection = new PeerConnection(tempPeerID, tokens[1], peerPort, this, !foundCurrentPeer, commonCfg);
                        peerConnectionVector.addElement(peerConnection);

                    } else {
                        // If current peer ID is the same as the current peer, act as client and attempt to connect to all peers before it
                        // 'before it' is defined as that are already in the vector
                        //currentPeer is just a peer extension of peerProcess that is used to connect to peers before it
                        //client is set to null because it shouldn't be used in any thread context
                        foundCurrentPeer = true;
                        selfPeerPort = peerPort;
                        selfPeerAddress = tokens[1];
                        selfPeerId = tempPeerID;
                        selfBitfield = new boolean[commonCfg.numPieces];
                        if(hasFileOnStart) {
                            Arrays.fill(selfBitfield, true);
                        }
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
        return peerConnectionVector;
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
    public static class PeerConnection extends Thread{
        public int peerId;
        public String peerAddress;
        public int peerPort;
        private Socket socket;
        OutputStream out;
        InputStream in;
        Boolean client;
        boolean[] bitfield;
        CommonCfg commonCfg;
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

        public PeerConnection(int peerId, String peerAddress, int peerPort, peerProcess hostProcess, Boolean client, CommonCfg commonCfg) {
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
                    if(!peerHandshake(socket,this.hostProcess.selfPeerId,this.peerId)) {
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

    static void sendMessage(Socket socket, byte[] msg) {
        try {
            //stream write the message
            OutputStream out = socket.getOutputStream();
            out.write(msg);
            out.flush();
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    static byte[] receiveMessageLength(Socket socket) throws IOException {
        // Each message (given from specifications) begins with a 4 byte length header
        // This method reads the length header and returns the message
        InputStream in = socket.getInputStream();
        int expectedLength = in.read();  // Assumes a 4-byte length header
        return receiveMessage(socket, expectedLength);
    }

    static byte[] receiveMessage(Socket socket, int expectedLength) throws IOException {
        // Read message of length expectedLength bytes
        byte[] message = new byte[expectedLength]; //add +1 later for message type?
        int offset = 0;
        InputStream in = socket.getInputStream();
        while (offset < expectedLength) {
            int bytesRead = in.read(message, offset, expectedLength - offset);
            if (bytesRead == -1) {
                throw new IOException("Connection was terminated before message was complete.");
            }
            offset += bytesRead;
        }

        return message;
    }

    static private int peerHandshakeServerSocket(Socket s,int peerId) throws IOException {
        sendMessage(s,Message.createHandshakePayload(peerId));
        byte[] handshakeMessage = receiveMessage(s,32);
        //handshake is always 32 bytes

        return Message.getIDFromHandshake(handshakeMessage);
    }

    static private boolean peerHandshake(Socket s,int selfPeerId, int expectedIdToReceive) throws IOException {
        System.out.println("Waiting for handshake from peer " + expectedIdToReceive);
        sendMessage(s,Message.createHandshakePayload(selfPeerId));
        byte[] handshakeMessage = receiveMessage(s,32);
        //handshake is always 32 bytes

        if (!Message.checkHandshake(handshakeMessage, expectedIdToReceive)) {
            System.err.println("Handshake failed");
            return false;
        }

        System.err.println("Handshake successful");
        return true;
    }



    public static class CommonCfg {
        public int numberOfPreferredNeighbors;
        public int unchokingInterval;
        public int optimisticUnchokingInterval;
        public String fileName;
        public int fileSize;
        public int pieceSize;
        public int numPieces;
        public CommonCfg(int numberOfPreferredNeighbors, int unchokingInterval, int optimisticUnchokingInterval, String fileName, int fileSize, int pieceSize) {
            this.numberOfPreferredNeighbors = numberOfPreferredNeighbors;
            this.unchokingInterval = unchokingInterval;
            this.optimisticUnchokingInterval = optimisticUnchokingInterval;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.pieceSize = pieceSize;
            this.numPieces = (int) Math.ceil((double) fileSize / pieceSize);
        }
    }

}
