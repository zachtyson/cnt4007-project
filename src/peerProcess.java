import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;


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

    public PeerLogger logger;

    public peerProcess(int currentPeerID) {
        // Reads Common.cfg and PeerInfo.cfg
        try {
            getCommon();
            this.peerConnectionVector = getPeers(currentPeerID);
        } catch (IOException e) {
            System.out.println("Error: PeerInfo.cfg not found");
        }
        try {
            logger = new PeerLogger(currentPeerID);
        } catch (IOException e) {
            System.err.println("Error: Could not create logger");
            System.exit(1);
        }
        // Start listening for connections to ServerSocket
        try {
            startListening();
            startConnection();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.err.println("All peers terminated");
        String fileName = commonCfg.fileName;
        try {
            byteMapToFile(pieceData, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Iterate over entire map to check if all pieces
        //If all pieces, try to save file

    }

    public static void byteMapToFile(ConcurrentHashMap<Integer,byte[]> pieceDataMap, String filePath) throws IOException {
        // todo: there is 100% a bug here
        // the file size is not correct and is slightly larger than the original file
        // for sake of getting other things working, I'm going to ignore this for now
        File file = new File(filePath);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            for(int i = 0; i < pieceDataMap.size(); i++) {
                byte[] temp = pieceDataMap.get(i);
                fos.write(temp);
            }
        }
    }


    public void startListening() throws InterruptedException {
        // Listens for connections to ServerSocket
        // Then after a handshake the new socket is redirected to the appropriate PeerConnection
        ServerSocketThread serverSocketThread = new ServerSocketThread(this.selfPeerPort, this);
        serverSocketThread.start();
        serverSocketThread.join();
    }

    public void startConnection() throws InterruptedException{
        // Each peer tries to connect to all peers before it
        // to each peer's ServerSocket
        for(PeerConnection peerConnection : this.peerConnectionVector) {
            if(peerConnection.client) {
                peerConnection.start();
                peerConnection.join();
            }
        }
    }

    public static class ServerSocketThread extends Thread {
        //Temporary class to listen for connections to the current peer's ServerSocket
        //Then after a handshake the new socket is redirected to the appropriate PeerConnection
        //Each socket connection to ServerSocket then is passed to PeerConnection (provided the handshake is successful and the PeerConnection.client is false)
        private final int port;
        private final peerProcess hostProcess;

        public ServerSocketThread(int port, peerProcess hostProcess) {
            this.port = port;
            this.hostProcess = hostProcess;
        }

        @Override
        public void run() {
            Vector<PeerConnection> serverWait = new Vector<>();
            for(PeerConnection peerConnection : this.hostProcess.peerConnectionVector) {
                if(!peerConnection.client) {
                    serverWait.add(peerConnection);
                }
            }
//            for(PeerConnection peerConnection : serverWait) {
//                System.err.println(peerConnection.peerId);
//            }
            if(serverWait.isEmpty()) {
                System.out.println("No peers to wait for");
                //terminate thread
                return;
            }
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while(true) {
                    //Waits for a connection to the ServerSocket
                    //Currently this waits forever but I'm thinking adding a timeout clause might be a good idea
                    System.out.println("Waiting for client on port " + serverSocket.getLocalPort() + "...");
                    Socket socket = serverSocket.accept();
                    //After a connection is made, a handshake is performed
                    System.out.println("Connected to " + socket.getRemoteSocketAddress());
                    int peerId = PeerConnection.peerHandshakeServerSocket(socket, this.hostProcess.selfPeerId);
                    boolean found = false;
                    for(PeerConnection peerConnection : serverWait) {
                        if(peerConnection.peerId == peerId) {
                            peerConnection.setSocket(socket);
                            found = true;
                            break;
                        }
                        //The handshake includes the peer's id, which is then checked amongst the list of peers in serverWait
                    }
                    if(!found) {
                        System.err.println("Error: Peer ID " + peerId);
                        for (PeerConnection peerConnection : serverWait) {
                            System.err.println(peerConnection.peerId);
                        }
                        System.exit(1);
                    }
                    System.err.println("Handshake successful");
                    //if peerId is not found, then we have a problem
                    for(PeerConnection peerConnection : serverWait) {
                        if(peerConnection.peerId == peerId) {
                            peerConnection.start();
                            peerConnection.in = new DataInputStream(socket.getInputStream());
                            peerConnection.out = new DataOutputStream(socket.getOutputStream());
                            peerConnection.join();
                            serverWait.remove(peerConnection);
                            break;
                        }
                    }
                    if(serverWait.isEmpty()) {
                        //no more peers to wait for
                        //terminate thread
                        return;
                    }
                    else {
                        System.out.println("Waiting for " + serverWait.size() + " more peers");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    ConcurrentHashMap<Integer, Boolean> peerHasWholeFile = new ConcurrentHashMap<>(); //HashMap for tracking that every peer has every piece, this is required before the program can terminate
    public Vector<PeerConnection> peerConnectionVector;
    public CommonCfg commonCfg;
    public int selfPeerId;
    public String selfPeerAddress;
    public int selfPeerPort;

    public enum pieceStatus {
        EMPTY,
        REQUESTING,
        DOWNLOADED,
        INTERESTED,
    }
    ConcurrentHashMap<Integer, pieceStatus> pieceMap;
    ConcurrentHashMap<Integer, byte[]> pieceData = new ConcurrentHashMap<>();
    AtomicBoolean hasAllPieces = new AtomicBoolean(false); //Atomic boolean added for more efficiency so that we don't have to check the entire map every time
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
                        peerHasWholeFile.put(tempPeerID, false);

                    } else {
                        // If current peer ID is the same as the current peer, act as client and attempt to connect to all peers before it
                        // 'before it' is defined as that are already in the vector
                        //currentPeer is just a peer extension of peerProcess that is used to connect to peers before it
                        //client is set to null because it shouldn't be used in any thread context
                        foundCurrentPeer = true;
                        selfPeerPort = peerPort;
                        selfPeerAddress = tokens[1];
                        selfPeerId = tempPeerID;
                        pieceMap = new ConcurrentHashMap<>();
                        if(hasFileOnStart) {
                            for(int i = 0; i < commonCfg.numPieces; i++) {
                                pieceMap.put(i, pieceStatus.DOWNLOADED);
                            }
                            // Look for presence of file
                            File file = new File(commonCfg.fileName);
                            if(!file.exists()) {
                                System.out.println("Error: File " + commonCfg.fileName + " not found");
                                System.exit(1);
                                //Likely something went wrong on our part since PeerInfo.cfg says that the peer has the file but it doesn't
                            }
                            try {
                                // Read file into byte array
                                byte[] fileContent = Files.readAllBytes(Paths.get(commonCfg.fileName));
                                //Split the file into pieces
                                int fileSize = commonCfg.fileSize;
                                int actualFileSize = fileContent.length;
                                if (fileSize != actualFileSize) {
                                    System.err.println("Error: File size does not match expected file size");
                                    System.exit(1);
                                }
                                int pieceSize = commonCfg.pieceSize;
                                int numPieces = commonCfg.numPieces;
                                for (int i = 0; i < numPieces; i++) {
                                    int start = i * pieceSize;
                                    int length = Math.min(pieceSize, fileSize - start);
                                    byte[] temp = new byte[length];
                                    System.arraycopy(fileContent, start, temp, 0, length);
                                    pieceData.put(i, temp);
                                }
                                for(int i = 0; i < commonCfg.numPieces; i++) {
                                    pieceMap.put(i, pieceStatus.DOWNLOADED);
                                    hasAllPieces.set(true);
                                }
                                for(int i = 0; i < commonCfg.numPieces; i++) {
                                    if(pieceData.get(i) == null) {
                                        System.err.println("Error: Piece " + i + " is null");
                                        System.exit(1);
                                    }
                                }
                                System.err.println("File read successfully and split into pieces");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                        } else {
                            for(int i = 0; i < commonCfg.numPieces; i++) {
                                pieceMap.put(i, pieceStatus.EMPTY);
                            }
                        }
//                        for(int i = 0; i < commonCfg.numPieces; i++) {
//                            pieceMap.put(i, pieceStatus.EMPTY);
//                        }
//                        pieceData = new ConcurrentHashMap<>();
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
