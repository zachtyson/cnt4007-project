import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.io.IOException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.*;
import java.util.logging.Formatter;
import java.io.IOException;
import java.util.Vector;
import java.util.logging.*;



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

    private CopyOnWriteArrayList<Integer> unchokedPeers;
    ArrayList<PeerConnection> interestedNeighbors=null;
    ArrayList<PeerConnection> chokedNeighbors=null;

    //Notes for choking / unchoking
    // k = number of preferred neighbors, Commoncfg.numberOfPreferredNeighbors,
    // so k preferred unchoked peers + 1 optimistically unchoked peer
    // Each peer only uploads to unchoked neighbors
    // At startup the preferred peers are selected at random

    // Every unchokingInterval seconds, a peer recalculates its preferred neighbors.
    // It calculates the downloading rate from each neighbor during the last unchoking interval.
    // It selects k neighbors that provided data at the highest rate.
    // In case of a tie, the selection is made randomly.
    // When this happens, send unchoke to all peers in the list of preferred neighbors that weren't already unchoked
    // and send choke to all peers that were unchoked but are no longer in the list of preferred neighbors. Choke can be sent again but unchoke should only be sent once.


    // Every OptimisticUnchokingInterval seconds, a peer optimistically unchokes one of its neighbors at random.
    // This is a separate operation from the regular unchoking described above.
    // The optimistically unchoked neighbor is selected at random from all the neighbors that are interested in downloading from this peer.
    // If there is no such neighbor, no optimistically unchoked neighbor is selected for this unchoking interval.
    // When this happens, send unchoke to the optimistically unchoked neighbor, and I guess just kick out the old optimistically unchoked neighbor(?)




    //Intended behavior:

    public void addUnchokedPeer(int peerId) {
        // Add a peer to the list of unchoked peers
        if (!unchokedPeers.contains(peerId)) {
            unchokedPeers.add(peerId);
        }
    }

    public void removeUnchokedPeer(Integer peerId) {
        // Remove a peer from the list of unchoked peers
        unchokedPeers.remove(peerId);
    }

    public CopyOnWriteArrayList<Integer> getUnchokedPeers() {
        // Get a snapshot of the current unchoked peers
        return new CopyOnWriteArrayList<>(unchokedPeers);
    }
    static final boolean DEBUG = false;
    Vector<Thread> childThreads = new Vector<>();

    public static void main(String[] args) {
        // Check for first argument
        if (args.length < 1) {
            printError("Missing peer ID argument");
            System.exit(1);
        }
        // Attempt to parse peer ID
        int ID = -1;
        try {
            ID = Integer.parseInt(args[0]);
            if(ID < 0) {
                printError("Peer ID must be a positive integer");
                System.exit(1);
            }
        } catch (NumberFormatException e) {
            printError("Peer ID must be an integer");
            System.exit(1);
        }
        // Create a new peerProcess object
        peerProcess currentPeerProcess = new peerProcess(ID);
        //A peer process is terminated when it finds out that all the peers, not just itself, have downloaded the complete file.
        //currentPeerProcess.close();
    }

    public PeerLogger logger;
boolean hasActiveThreads(Vector<Thread> childThreads){
    int activeThreads = 0;
    for (Thread thread: childThreads){
        if(thread.isAlive()){
            activeThreads++;
            //System.out.println(thread);
        }
    }
    //System.out.println(activeThreads);
    return !(activeThreads==0);
}
    public peerProcess(int currentPeerID) {
        // Reads Common.cfg and PeerInfo.cfg
        chokedNeighbors = new ArrayList<PeerConnection>();
        interestedNeighbors = new ArrayList<PeerConnection>();
        this.unchokedPeers = new CopyOnWriteArrayList<>();
        try {
            getCommon();
            this.peerConnectionVector = getPeers(currentPeerID);
        } catch (IOException e) {
            printError("PeerInfo.cfg not found");
        }
        try {
            logger = new PeerLogger(currentPeerID);
        } catch (IOException e) {
            printError("Could not create logger");
            System.exit(1);
        }
        // Start listening for connections to ServerSocket
        try {
            startListening();
            startConnection();
            printDebug("All peers connected");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        printDebug("All peers terminated");
        String fileName = commonCfg.fileName;
        Timer t1 = new Timer();
        Timer t2 = new Timer();
        t1.schedule(new TimerTask() {
            @Override
            public void run() {

                try {
                    Thread.sleep(10);

                    selectPreferredNeighbors(commonCfg.numberOfPreferredNeighbors);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }


            }
        },commonCfg.unchokingInterval*1000,commonCfg.unchokingInterval*1000);
        t2.schedule(new TimerTask() {
            @Override
            public void run() {
                selectOptimisticallyUnchokedNeighbor();
            }

        },commonCfg.optimisticUnchokingInterval*1000,commonCfg.optimisticUnchokingInterval*1000);
        while (hasActiveThreads(childThreads)) {
            // Optionally, you can add a sleep to avoid busy waiting

            try {
               Thread.sleep(100);



            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                printError("Interrupted while waiting for connections to close");
            }
        }
        logger.logShutdown();
        //stopping those tasks from running
        t1.cancel();
        t2.cancel();
        try {
            byteMapToFile(pieceData, fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Iterate over entire map to check if all pieces
        //If all pieces, try to save file

    }

    public void byteMapToFile(ConcurrentHashMap<Integer,byte[]> pieceDataMap, String filePath) throws IOException {
        // todo: there is 100% a bug here
        // todo: CORRECTION: it is not here, there is a big with sending/receiving pieces, not sure which
        // because peers that start with thefile don't have the bug but peers that start without the file (and receive it) do
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
                childThreads.add(peerConnection);
                //peerConnection.join();
            }
        }
//        //todo come back here and see if this fixed the concurrency issue
//        for(PeerConnection peerConnection : this.peerConnectionVector) {
//            if(peerConnection.client) {
//                //peerConnection.start();
//                //peerConnection.join();
//            }
//        }
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
                printDebug("No peers to wait for");
                //terminate thread
                return;
            }
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while(true) {
                    //Waits for a connection to the ServerSocket
                    //Currently this waits forever but I'm thinking adding a timeout clause might be a good idea
                    printDebug("Waiting for client on port " + serverSocket.getLocalPort() + "...");
                    Socket socket = serverSocket.accept();
                    //After a connection is made, a handshake is performed
                    printDebug("Connected to " + socket.getRemoteSocketAddress());
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
                        printError("Peer ID " + peerId + " not found");
                        for (PeerConnection peerConnection : serverWait) {
                            printError("Peer ID " + peerConnection.peerId + " not found");
                        }
                        System.exit(1);
                    }
                    printDebug("Handshake successful");
                    //if peerId is not found, then we have a problem
                    for(PeerConnection peerConnection : serverWait) {
                        if(peerConnection.peerId == peerId) {
                            peerConnection.start();
                            //hostProcess.activeConnections.incrementAndGet();
                            peerConnection.in = new DataInputStream(socket.getInputStream());
                            peerConnection.out = new DataOutputStream(socket.getOutputStream());
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
                        printDebug("Waiting for " + serverWait.size() + " more peers");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
//           catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
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
                                printError("File " + commonCfg.fileName + " not found");
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
                                    printError("File size does not match expected file size");
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
                                        printError("Error: Piece " + i + " is null");
                                        System.exit(1);
                                    }
                                }
                                printDebug("File read successfully and split into pieces");
                            } catch (Exception e) {
                                printError("Error reading file");
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
                    printError("Peer ID must be an integer");
                    System.exit(1);
                }
            }
            in.close();
        } catch (Exception ex) {
            printError(ex.getMessage());
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
                        printError("Unknown key in config file: " + key);
                        System.exit(1);
                }
            }
            commonCfg = new CommonCfg(numberOfPreferredNeighbors, unchokingInterval, optimisticUnchokingInterval, fileName, fileSize, pieceSize);
            in.close();
        } catch (NumberFormatException e) {
            printError("Invalid number format in config file");
            System.exit(1);
        } catch (Exception ex) {
            printError(ex.getMessage());
            System.exit(1);
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

    static void printError(String message) {
        String timestamp = java.time.LocalTime.now().toString();
        System.err.println(timestamp + " Error: " + message);
        //System.exit(1);
    }

    static void printDebug(String message) {
        if(DEBUG) {
            String timestamp = java.time.LocalTime.now().toString();
            System.out.println(timestamp + " Debug: " + message);
        }
    }


    public void selectPreferredNeighbors(int k) { //k is how many are to be selected
        // 1. Calculate downloading rates from neighbors
        //in a method in PeerConnection
        choked = 0;

        // 2. Identify interested neighbors
        ArrayList<PeerConnection> tinterestedNeighbors = (ArrayList<PeerConnection>) interestedNeighbors.clone();
        chokedNeighbors.clear();

        // 3. Select k neighbors with highest downloading rates
        //for loop through all neighbors and get the highest downloading rates
        while(tinterestedNeighbors.size()>k){
            PeerConnection min = null;
            for (PeerConnection x : tinterestedNeighbors){
                if (min != null){

                    if (min.downloadRate.get()  >x.downloadRate.get()){
                        min = x;
                    }else if(min.downloadRate.get() == x.downloadRate.get()){
                        min= x;
                    }
                }else{
                    min = x;
                }
            }
            chokedNeighbors.add(min);
            choked++;
            tinterestedNeighbors.remove(min);
        }
        Vector<String> loggedNeighbors = new Vector<>();
        for (PeerConnection selectedNeighbor : tinterestedNeighbors) {

            // Step 4: Send 'unchoke' messages to preferred neighbors
            selectedNeighbor.sendResponses.add(Message.generateUnchokeMessage());
            loggedNeighbors.add(selectedNeighbor.peerId+"");
        }
        logger.logChangePreferredNeighbors(loggedNeighbors);
        // Step 5: Send 'choke' messages to unselected neighbors
        for (PeerConnection Choking : chokedNeighbors){
            Choking.sendResponses.add(Message.generateChokeMessage());
        }

        // 4. Send 'unchoke' messages to preferred neighbors

        // 5. Send 'choke' messages to unselected neighbors. All other neighbors previously unchoked but not
        //selected as preferred neighbors at this time should be choked unless it is an optimistically
        //unchoked neighbor

    }
    int choked= 0;
    public void selectOptimisticallyUnchokedNeighbor() {
        int index = (int) ((interestedNeighbors.size()-1 - commonCfg.numberOfPreferredNeighbors)*Math.random());


        if (index < 0 ){
            index = 0;
        }
        if (index>chokedNeighbors.size()){
            index = chokedNeighbors.size()-1;
        }
        if (index!=chokedNeighbors.size()) {
            //System.out.println("Index" + index);
            PeerConnection unchoke = chokedNeighbors.get(index);
            logger.logChangeOptimisticallyUnchokedNeighbor(unchoke.peerId + "" + index);
            unchoke.sendResponses.add(Message.generateUnchokeMessage());
        }
    }

}


class PeerConnection extends Thread {
    public int peerId;
    public String peerAddress;
    public int peerPort;
    Socket socket;
    OutputStream out;
    InputStream in;
    Boolean client;
    ConcurrentHashMap<Integer, peerProcess.pieceStatus> peerPieceMap; //pieceMap of the PEER not the host
    peerProcess.CommonCfg commonCfg;
    SendHandler sendHandler;
    ReceiveHandler receiveHandler;
    peerProcess hostProcess;
    Queue<byte[]> sendResponses = new ConcurrentLinkedQueue<>();
    Queue<byte[]> chokeAndInterestedMessages = new ConcurrentLinkedQueue<>(); //queue for choke and interested messages, for some reason didn't work when I tried to use the same queue as sendResponses
    AtomicBoolean peerHasAllPieces = new AtomicBoolean(false);
    AtomicInteger currentlyRequestedPiece = new AtomicInteger(-1); // For sake of simplicity a peer can only request one piece at a time from another peer
    //Meaning you can request piece 1 from peer A and piece 2 from peer B at the same time, but you can't request piece 1 from peer A and piece 2 from peer A at the same time
    AtomicBoolean peerInterested = new AtomicBoolean(false); //If peer is interested in us
    AtomicBoolean selfInterested = new AtomicBoolean(false); //If we are interested in peer
    public AtomicBoolean selfChoked = new AtomicBoolean(false); //If we are choked
    ConcurrentMap<LocalDateTime,Integer> messageBytesReceived = new ConcurrentHashMap<>(); //Used to keep track of how many bytes we've received from this specific peer
    public final int oldestMessageToKeepInSeconds; //Used to keep track of how many seconds to keep messages in the messageBytesReceived map
    AtomicReference<Double> downloadRate = new AtomicReference<>(0.0); //Used to keep track of the download rate of this specific peer
    //Weird workaround since AtomicDouble doesn't exist
    public PeerConnection(int peerId, String peerAddress, int peerPort, peerProcess hostProcess, Boolean client, peerProcess.CommonCfg commonCfg) {
        super();
        this.peerId = peerId;
        this.peerAddress = peerAddress;
        this.peerPort = peerPort;
        this.client = client;
        this.hostProcess = hostProcess;
        this.commonCfg = commonCfg;
        //oldestMessageToKeepInSeconds is the maximum of the unchoking interval and the optimistic unchoking interval in seconds * 2
        oldestMessageToKeepInSeconds = Math.max(commonCfg.unchokingInterval,commonCfg.optimisticUnchokingInterval) * 2;
        //Set bitfield to all 0s
        //all elements are false by default
        peerPieceMap = new ConcurrentHashMap<>();
        for(int i = 0; i < commonCfg.numPieces; i++) {
            peerPieceMap.put(i, peerProcess.pieceStatus.EMPTY);
        }
    }

    void addToMessageBytesReceived(LocalDateTime time, int bytesReceived) {
        messageBytesReceived.put(time,bytesReceived);
        printAverageDownloadRate(messageBytesReceived,commonCfg.unchokingInterval,oldestMessageToKeepInSeconds);
    }
    public void printAverageDownloadRate(ConcurrentMap<LocalDateTime, Integer> downloadData, int pastSeconds, int oldestMessageToKeepInSeconds) {
        // Get the current time
        LocalDateTime currentTime = LocalDateTime.now();

        // Calculate the start time for the past p seconds
        LocalDateTime startTime = currentTime.minusSeconds(pastSeconds);

        // Initialize variables to track total bytes downloaded and time elapsed
        int totalBytesDownloaded = 0;
        long timeElapsedMillis = 0;

        // Iterate over the download data map
        for (Map.Entry<LocalDateTime, Integer> entry : downloadData.entrySet()) {
            LocalDateTime downloadTime = entry.getKey();
            int bytesDownloaded = entry.getValue();

            // Check if the download time is within the past p seconds
            if (downloadTime.isAfter(startTime)) {
                totalBytesDownloaded += bytesDownloaded;
                timeElapsedMillis += ChronoUnit.MILLIS.between(downloadTime, currentTime);
            } else if (downloadTime.isBefore(currentTime.minusSeconds(oldestMessageToKeepInSeconds))) {
                // Remove the entry if it is older than the oldest message we want to keep
                downloadData.remove(downloadTime);
            }
        }

        // Calculate the average download rate in bytes per second
        double averageDownloadRateBps = (double) totalBytesDownloaded / timeElapsedMillis;

        // Print the average download rate
        //System.out.println("Average download rate over the past " + pastSeconds + " seconds: " + averageDownloadRateBps + " bps");
        hostProcess.logger.logFiveSecondDownloadRate(String.valueOf(peerId),averageDownloadRateBps);
        downloadRate.set(averageDownloadRateBps);
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        peerProcess.printDebug("Starting connection to peer " + peerId);
        if(client == null) {
            //This should never happen
            peerProcess.printError("Client is null, verify that the peer is in the correct order in PeerInfo.cfg and that currentPeer is set correctly");
        } else if (client) {
            client();
            hostProcess.logger.logTCPConnection(String.valueOf(peerId),false);
        } else if (!client) {
            // server uses the socket found in peerProcess.java, so there is no process here it's handled by the main thread
            hostProcess.logger.logTCPConnection(String.valueOf(peerId),true);
        }
        //at this point the connection is established and handshake is done, and we can start sending/receiving messages
        //check if current peer has the file DONE AT STARTUP
//        byte[] bitfieldMessage = Message.generateBitmapMessage(this.hostProcess.pieceMap, this.commonCfg.numPieces);
//            for (byte b : bitfieldMessage) {
//                String bits = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
//                System.out.print(bits + " ");
//            }
//            System.out.println(); // New line after printing all bytes
        //if it does, send a bitfield message with all 1s
        //the assignment doesn't specify if a peer can start with a partial file, so I'm assuming now for now just to make things easier
        startHandlers(); //Starts the send and receive handlers
        int numPiecesPeerHas = 0;
        for(int i = 0; i < commonCfg.numPieces; i++) {
            if(peerPieceMap.get(i) == peerProcess.pieceStatus.DOWNLOADED) {
                numPiecesPeerHas++;
            }
        }
        //peerProcess.printError("Host: " + hostProcess.selfPeerId + ", Peer " + peerId + " has " + numPiecesPeerHas + " pieces");
        close();

    }

    public void startHandlers() {
        if(socket == null) {
            peerProcess.printError("Socket is null, verify that the peer is in the correct order in PeerInfo.cfg and that currentPeer is set correctly");
        }
        if(in == null) {
            peerProcess.printError("in is null, verify that the peer is in the correct order in PeerInfo.cfg and that currentPeer is set correctly");
        }
        if(out == null) {
            peerProcess.printError("out is null, verify that the peer is in the correct order in PeerInfo.cfg and that currentPeer is set correctly");
        }
        sendHandler = new SendHandler(this);
        receiveHandler = new ReceiveHandler(this);

        sendHandler.start();
        receiveHandler.start();
        //Starts both the send and receive handler threads
        try {
            sendHandler.join();
            receiveHandler.join();
        } catch (InterruptedException e) {
            peerProcess.printError("Thread interrupted" + e.getMessage());
            if(sendHandler.isAlive()) {
                sendHandler.interrupt();
            }
            if(receiveHandler.isAlive()) {
                receiveHandler.interrupt();
            }
            System.exit(1);
        }
        //Joining the threads will cause the program to wait until both threads are finished
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
                peerProcess.printDebug("Attempting to connect to " + address + " in port " + port);
                socket = new Socket(address, port);
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());
                peerProcess.printDebug("Connected to " + address + " in port " + port);
                //initialize inputStream and outputStream
                if(!peerHandshake(socket,this.hostProcess.selfPeerId,this.peerId)) {
                    System.exit(1);
                    close();  // Close connections when finished or in case of an error
                }
                break;
            }
            catch (ConnectException e) {
                peerProcess.printError("Connection refused. You need to initiate a server first. " + this.peerAddress+" "+this.peerPort+" "+this.peerId);
                try {
                    peerProcess.printError("Retry in " + (timeBetweenRetries / 1000) + " seconds... (" + (attempt + 1) + "/" + numberOfRetries + ")");
                    Thread.sleep(timeBetweenRetries);  // wait for a while before trying to reconnect
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            } catch(UnknownHostException unknownHost){
                peerProcess.printError("You are trying to connect to an unknown host! " + unknownHost.getMessage());

            }
            catch(IOException ioException){
                ioException.printStackTrace();
            }
        }
        if(attempt == numberOfRetries) {
            peerProcess.printError("Maximum number of retries reached. Terminating...");
            System.exit(1);
        }
        peerProcess.printDebug("Exiting client.");
    }

    public boolean peerHasAnyPiecesWeDont() {
        boolean peerHasAnyPiecesWeDont = false;
        for (int i = 0; i < commonCfg.numPieces; i++) {
            if ((hostProcess.pieceMap.get(i) == null || hostProcess.pieceMap.get(i) == peerProcess.pieceStatus.EMPTY) && peerPieceMap.get(i) == peerProcess.pieceStatus.DOWNLOADED) {
                peerHasAnyPiecesWeDont = true;
                break;
            }
        }
        return peerHasAnyPiecesWeDont;
    }

    public boolean isPieceIndexEligible(int index) {
        boolean peerHasPiece = peerPieceMap.get(index) == peerProcess.pieceStatus.DOWNLOADED;
        boolean weDontHavePiece = hostProcess.pieceMap.get(index) == null || hostProcess.pieceMap.get(index) == peerProcess.pieceStatus.EMPTY;
        return peerHasPiece && weDontHavePiece;
    }


    // Prepares the peer to be closed for the program to terminate
    public void close() {
        //Close the socket
        peerProcess.printDebug("Closing connection to peer " + peerId);
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            peerProcess.printError("Error closing resources for peer " + peerId + ": " + e.getMessage());
        }
    }

    static public int peerHandshakeServerSocket(Socket s,int peerId) throws IOException {
        sendMessage(s,Message.createHandshakePayload(peerId));
        byte[] handshakeMessage = receiveMessage(s,32);
        //handshake is always 32 bytes

        return Message.getIDFromHandshake(handshakeMessage);
    }

    static public boolean peerHandshake(Socket s,int selfPeerId, int expectedIdToReceive) throws IOException {
        sendMessage(s,Message.createHandshakePayload(selfPeerId));
        byte[] handshakeMessage = receiveMessage(s,32);
        //handshake is always 32 bytes

        if (!Message.checkHandshake(handshakeMessage, expectedIdToReceive)) {
            peerProcess.printError("Handshake failed");
            return false;
        }
        peerProcess.printDebug("Handshake successful");
        return true;
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

    public void setSelfInterested(boolean peerHasPiecesWeDont) throws IOException {
        if(selfInterested.get() == peerHasPiecesWeDont) {
            //If we're already interested and the peer has pieces we don't have, we don't need to send another interested message
            return;
        }
        if(peerHasPiecesWeDont) {
            peerProcess.printDebug("Peer has pieces we don't have");
            //byte[] message = Message.generateBitmapMessage(peerConnection.hostProcess.pieceMap, peerConnection.commonCfg.numPieces);
            //peerConnection.sendResponses.add(message);
            byte[] message = Message.generateInterestedMessage();
            chokeAndInterestedMessages.add(message);
            selfInterested.set(true);
        }
        else {
            peerProcess.printDebug("Peer does not have pieces we don't have");
            chokeAndInterestedMessages.add(Message.generateNotInterestedMessage());
            selfInterested.set(false);
        }
    }

    public void setPeerInterested(boolean status) {
        if(peerInterested.get() == status) {
            return;
        }
        peerInterested.set(status);
    }

    public void setSelfChoked(boolean status) {
        if(selfChoked.get() == status) {
            return;
        }
        selfChoked.set(status);
    }
    public boolean getSelfChoked(){
        return selfChoked.get();
    }
}
class SendHandler extends Thread {
    PeerConnection peerConnection;
    SendHandler(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
        this.lastMessageTime = Instant.now();
    }

    private volatile Instant lastMessageTime; //This does nothing currently, but I think I'm going to add a timeout feature

    @Override
    public void run() {
        peerProcess.printDebug("Starting send handler for peer");
        boolean hasAnyPiecesAtStart = checkForPieces();
        if (hasAnyPiecesAtStart) {
            // If it has any pieces, send bitfield message
            int numBytes = (int) Math.ceil(peerConnection.commonCfg.numPieces / 8.0);
            peerProcess.printDebug("Num bytes: " + numBytes);
            //byte[] headerAndType = Message.generateHeaderAndMessageType(numBytes,MsgType.bitfield);
            byte[] bitfieldMessage = Message.generateBitmapMessage(peerConnection.hostProcess.pieceMap, peerConnection.commonCfg.numPieces);

            try {
                //Print full message
                peerProcess.printDebug("Message: " + Arrays.toString(bitfieldMessage));
                sendMessage(bitfieldMessage);
                peerProcess.printDebug("Sent bitfield to peer");
            } catch (IOException e) {
                //e.printStackTrace();
            }
        }
        boolean hasAllPiecesAtStart = peerConnection.hostProcess.hasAllPieces.get();
        if(hasAllPiecesAtStart) {
            peerConnection.hostProcess.logger.logPeerCompletion(String.valueOf(peerConnection.peerId));
            peerConnection.hostProcess.hasAllPieces.set(true);
        }
        Random random = new Random();

        boolean sentHasBothNotInterested = false;
        //if both this peer and the peer it is connected to have all pieces, send not interested message

        while (!peerConnection.socket.isClosed()) {
            //check to see if peerConnection.socket is closed
            try {
                int sleepTime = random.nextInt(200) + 100;
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            // First priority should be to send a request message if there is a piece that is not currently being requested
            // Each peerConnection should be able to have 1 outstanding request at a time, meaning that the entire peerProcess
            // should be able to have 1 outstanding request per peerConnection, aka 1 outstanding request per peer
            // Second priority should be to respond to any queued responses (have message, etc.)
            // Third priority should be to send any requested pieces
            // This can be subject to change, but this is the current plan
            boolean hasOutstandingRequest = peerConnection.currentlyRequestedPiece.get() != -1;
            boolean hasQueuedResponses = !peerConnection.sendResponses.isEmpty();
            boolean hasAllPieces = peerConnection.hostProcess.hasAllPieces.get();
            boolean peerHasAllPieces = peerConnection.peerHasAllPieces.get();
            boolean peerHasAnyPiecesWeDont = peerConnection.peerHasAnyPiecesWeDont();
            boolean hasChokeAndInterestedMessages = peerConnection.chokeAndInterestedMessages.isEmpty();

            if(!hasChokeAndInterestedMessages) {
                // Send out a queued choke or unchoke message
                byte[] chokeOrUnchokeMessage = peerConnection.chokeAndInterestedMessages.remove();
                try {
                    if(peerConnection.socket.isClosed()) {
                        break;
                    }
                    sendMessage(chokeOrUnchokeMessage);
                    peerProcess.printDebug("Sent message to peer (choke or unchoke)");

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            //Check for conditions to send outstanding request
            else if(!hasOutstandingRequest && peerHasAnyPiecesWeDont) {
                //This branch means that if there are any pieces that the peer has that this peer doesn't have, request one of them
                //This branch should only be taken if there is not already an outstanding request
                List<Integer> eligiblePieces = new ArrayList<>();
                //List is used to randomly request a piece rather than just doing it in order
                for (int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                    // Check if the piece is neither downloaded nor currently being requested by this peer,
                    // and make sure that sendResponses is empty
//                    if ((peerConnection.hostProcess.pieceMap.get(i) == null || (peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED &&
//                                    peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.REQUESTING))
//                            && peerConnection.sendResponses.isEmpty()) {
                    if (peerConnection.isPieceIndexEligible(i)) {
                        eligiblePieces.add(i); // Add eligible piece index to the list
                    }
                }

                if (!eligiblePieces.isEmpty()) {
                    // Randomly select a piece from the eligible pieces
                    int randomIndex = new Random().nextInt(eligiblePieces.size());
                    int selectedPieceIndex = eligiblePieces.get(randomIndex);
                    peerConnection.currentlyRequestedPiece.set(selectedPieceIndex);
                    peerProcess.printDebug("Randomly added piece " + selectedPieceIndex + " to requested pieces");
                }
                else {
                    peerProcess.printDebug("No eligible pieces to request");
                    //This should in theory never happen, but if it does, it means that the peer has all the pieces that this peer has
                    //and this peer has all the pieces that the peer has, but logically this can't happen
                }
            }
            else if (hasQueuedResponses) {
                // Send out a queued response that ReceivedHandler has queued, this can be a have message, a piece message, or whatever
                byte[] pieceIndex = peerConnection.sendResponses.remove();
                try {
                    if(peerConnection.socket.isClosed()) {
                        break;
                    }
                    sendMessage(pieceIndex);
                    peerProcess.printDebug("Sent message to peer (have)");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else if ((!peerConnection.getSelfChoked())&&hasOutstandingRequest) {
                // Send out the outstanding request
                peerProcess.printDebug("Sending request message");
                int pieceIndex = peerConnection.currentlyRequestedPiece.get();
                byte[] message = Message.generateRequestMessage(pieceIndex);
                try {
                    //Checks to see if a piece is already requested from this peer
                    //Check if socket is closed
                    if(peerConnection.socket.isClosed()) {
                        break;
                    }
                    sendMessage(message);
                    peerConnection.hostProcess.pieceMap.put(pieceIndex, peerProcess.pieceStatus.REQUESTING);
                    peerProcess.printDebug("Sent message to peer (request)");
                } catch (IOException e) {
                    peerProcess.printError("Error sending request message to peer: " + e.getMessage());
                }
                peerConnection.currentlyRequestedPiece.set(-1);
            }
            else if (hasAllPieces && peerHasAllPieces){
                // Send not interested message if both peers have all pieces
                if(!sentHasBothNotInterested) {
                    byte[] notInterestedMessage = Message.generateNotInterestedMessage();
                    try {
                        sendMessage(notInterestedMessage);
                        peerProcess.printDebug("Sent message to peer (not interested)");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    sentHasBothNotInterested = true;
                }
                //if both peers have all pieces, check to see if all peers have all pieces
                // and if so, close the connection
                boolean allPeersHaveWholeFile = true;
                for (Map.Entry<Integer, Boolean> entry : peerConnection.hostProcess.peerHasWholeFile.entrySet()) {
                    if (!entry.getValue()) {
                        allPeersHaveWholeFile = false;
                        break;
                    }
                }
                if (allPeersHaveWholeFile) {
                    if (peerConnection.sendResponses.isEmpty()) {
                        //System.err.println("Host " + peerConnection.hostProcess.selfPeerId + " has all pieces and detected that all peers have all pieces");
                        peerConnection.hostProcess.close();
                        break; //I think continue and break here would have the same effect since the loop checks for the socket being closed at the beginning
                        // continue worked here first try so I'm not going to change it
                    }
                    try {
                        sendMessage(peerConnection.sendResponses.remove());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    // System.out.println("Both peers have all pieces, closing connection");
                    //peerConnection.close();
                }
            }
        }
    }

    boolean checkForPieces() {
        if(peerConnection.hostProcess.pieceMap == null) {
            return false;
        }
        if(peerConnection.hostProcess.hasAllPieces.get()) {
            return true;
        }
        for (int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
            if (peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.EMPTY && peerConnection.hostProcess.pieceMap.get(i) != null) {
                return true;
            }
        }
        return false;
    }
    void sendMessage(byte[] msg) throws IOException{
        //stream write the message
        lastMessageTime = Instant.now();
        if(peerConnection.socket.isClosed() || peerConnection.out == null) {
            return;
        }

        try {
            peerConnection.out.write(msg);
            peerConnection.out.flush();
        } catch (SocketException e) {
            //e.printStackTrace();
            //The socket is closed, so don't do anything
        }


    }

}

//ReceiveHandler is in charge of receiving messages from the peer, and passing them to the host process
//and this happens by modifying the PeerConnection object's ConcurrentHashMap of byte[] and status enum
class ReceiveHandler extends Thread{
    PeerConnection peerConnection;

    ReceiveHandler(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }
    @Override
    public void run() {
        while (!peerConnection.socket.isClosed()) {
            //check if socket is closed

            boolean allPeersHaveWholeFile = true;
            boolean hasAllPiecesCheckExit = peerConnection.hostProcess.hasAllPieces.get();

            for (Map.Entry<Integer, Boolean> entry : peerConnection.hostProcess.peerHasWholeFile.entrySet()) {
                if (!entry.getValue()) {
                    allPeersHaveWholeFile = false;
                    break;
                }
            }
            if (hasAllPiecesCheckExit && allPeersHaveWholeFile) {
                // peerConnection.close();
                break;
                // System.out.println("Both peers have all pieces, closing connection");

            }
            try {
                byte[] message = receiveMessageLength();
                if (message.length == 0) {
                    peerProcess.printDebug("Received keep alive message from peer");
                    // continue;
                }
                clearBuffer();
                peerProcess.printDebug("Peer+ " + peerConnection.hostProcess.selfPeerId + "Received message: " + Arrays.toString(message) + " from peer " + peerConnection.peerId);
                Message.Interpretation interpretation = Message.msgInterpret(message);
//                if(interpretation.Msg == MsgType.bitfield) {
//                    //Bitwise, set the pieces that the peer has
//                    for(int i = 0; i < peerConnection.numPieces; i++) {
//                        int nthBit = Message.getNthBit(interpretation.messagePayload, i);
//                        if(nthBit == 1) {
//                            peerConnection.pieceMap.put(i, Main.pieceStatus.DOWNLOADED);
//                        }
//                    }
//                }





                switch (interpretation.Msg) { //look at labels for choke and unchoke.
                    //do whatever it says to do when it is choked or unchoked
                    case choke:
                        //todo: implement choke
                        //choke means that you can't request pieces from the peer
                        peerProcess.printDebug("Received choke message from peer");
                        //implementation here
                        //stop sending requests to this peer
                        peerConnection.setSelfChoked(true);
                        peerConnection.hostProcess.logger.logChoking((""+peerConnection.peerId+""));
                        break;
                    case unchoke:
                        //todo: implement unchoke
                        //unchoke means that you can request pieces from the peer again
                        peerProcess.printDebug("Received unchoke message from peer");
                        //implementation here
                        //start sending requests to this peer
                        peerConnection.hostProcess.logger.logUnchoking((""+peerConnection.peerId+""));
                        peerConnection.setSelfChoked(false);

                        break;

                    case interested:
                        //todo: implement interested
                        //honestly im not even sure what to put for interested and not interested
                        //like obviously they tell us what interested and not interested means, but I'm not sure what to do with that information
                        peerProcess.printDebug("Received interested message from peer");
                        peerConnection.hostProcess.logger.logReceiveInterested(String.valueOf(peerConnection.peerId));
                        peerConnection.setPeerInterested(true);
                        peerConnection.hostProcess.interestedNeighbors.add(peerConnection);
                        break;
                    case notInterested:
                        //todo: implement not interested
                        peerProcess.printDebug("Received not interested message from peer");
                        peerConnection.hostProcess.logger.logReceiveNotInterested(String.valueOf(peerConnection.peerId));
                        peerConnection.setPeerInterested(false);
                        peerConnection.hostProcess.interestedNeighbors.remove(peerConnection);
                        break;
                    case have:
                        peerProcess.printDebug("Received have message from peer");
                        peerConnection.hostProcess.logger.logReceiveHave(String.valueOf(peerConnection.peerId), interpretation.pieceIndex);
                        //Update bitmap to reflect that the peer has the piece
                        peerConnection.peerPieceMap.put(interpretation.pieceIndex, peerProcess.pieceStatus.DOWNLOADED);
                        //If entire bitfield is Downloaded, set hasAllPieces to true
                        boolean hasAllPiecesAfterHave = true;
                        for (int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            if (peerConnection.peerPieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED) {
                                //System.err.println("Peer " + peerConnection.peerId + " does not have piece " + i);
                                hasAllPiecesAfterHave = false;
                                break;
                            }
                        }
                        if (hasAllPiecesAfterHave) {
                            //System.out.println("Peer has all pieces");
                            boolean previousValue = peerConnection.peerHasAllPieces.getAndSet(true);
                            peerConnection.hostProcess.peerHasWholeFile.put(peerConnection.peerId, true);
                            if (!previousValue) {
                                peerConnection.hostProcess.logger.logPeerCompletion(String.valueOf(peerConnection.peerId));
                                //Checking to prevent duplicate log messages
                            }
                        }
                        peerConnection.setSelfInterested(peerConnection.peerHasAnyPiecesWeDont());
                        break;
                    case request:
                        peerProcess.printDebug("Received request message from peer");
                        peerProcess.printDebug("Piece index: " + interpretation.pieceIndex);
                        byte[] pieceRequested = peerConnection.hostProcess.pieceData.get(interpretation.pieceIndex);
                        byte[] messageToPeer = Message.generatePieceMessage(pieceRequested, interpretation.pieceIndex);
                        peerConnection.sendResponses.add(messageToPeer);
                        break;
                    case piece:
                        peerProcess.printDebug("Received piece message from peer");

                        //add to bytes received, this is for measuring download speed
                        int messageLength = message.length;
                        peerConnection.addToMessageBytesReceived(LocalDateTime.now(), messageLength);

                        int pieceIndex = interpretation.pieceIndex;
                        byte[] piece = interpretation.messagePayload;
                        peerConnection.currentlyRequestedPiece.set(-1); //Set to -1 to indicate that no piece is currently being requested
                        peerProcess.printDebug("Currently requested piece set to -1");
                        peerConnection.hostProcess.pieceMap.put(pieceIndex, peerProcess.pieceStatus.DOWNLOADED);
                        peerConnection.hostProcess.pieceData.put(pieceIndex, piece);
                        int numPiecesLeft = 0;
                        for (int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            if (peerConnection.hostProcess.pieceMap.get(i) == peerProcess.pieceStatus.DOWNLOADED) {
                                numPiecesLeft++;
                            }
                        }
                        peerConnection.hostProcess.logger.logDownloadedPiece(String.valueOf(peerConnection.peerId), pieceIndex, numPiecesLeft);
                        //send message to host process that piece has been received
                        byte[] messageToHost = Message.generateHasPieceMessage(pieceIndex);
                        peerProcess.printDebug("Sending message that piece " + pieceIndex + " has been received");
                        //peerConnection.sendResponses.add(messageToHost); //dont think this is necessary
                        for(PeerConnection peerConnectionp: peerConnection.hostProcess.peerConnectionVector) {
                            peerConnectionp.sendResponses.add(messageToHost);
                        }
                        //Put messageToHost in the front of the queue

                        boolean hasAllPiecesAfterReceieve = true;
                        for(int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            if(peerConnection.hostProcess.pieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED) {
                                hasAllPiecesAfterReceieve = false;
                                break;
                            }
                        }
                        //TODO: THIS IS A HACKY WAY TO DO THIS, FIX THIS LATER
                    {
                        if (hasAllPiecesAfterReceieve) {
                            boolean previousValue = peerConnection.hostProcess.hasAllPieces.getAndSet(true);
                            if (!previousValue) {
                                peerConnection.hostProcess.logger.logCompletion();
                                byte[] bitmapMessage = Message.generateBitmapMessage(peerConnection.hostProcess.pieceMap, peerConnection.commonCfg.numPieces);
                                for (PeerConnection peerConnectionp : peerConnection.hostProcess.peerConnectionVector) {
                                    peerConnectionp.sendResponses.add(bitmapMessage);
                                }
                            }
                        }
                    }
                    break;
                    case bitfield:
                        peerProcess.printDebug("Received bitfield message from peer");
                        for (int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            int nthBit = Message.getNthBit(interpretation.messagePayload, i);
                            if (nthBit == 1) {
                                peerConnection.peerPieceMap.put(i, peerProcess.pieceStatus.DOWNLOADED);
                            }
                        }
                        //If entire bitfield is Downloaded, set hasAllPieces to true
                        //Honestly not entirely familiar with the specs so not sure if a peer would ever send a bitfield if they don't have all the pieces
                        //But I guess it's possible so it'll make this code a bit less efficient
                        boolean hasAllPieces = true;
                        for (int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            if (peerConnection.peerPieceMap.get(i) != peerProcess.pieceStatus.DOWNLOADED) {
                                hasAllPieces = false;
                                break;
                            }
                        }
                        peerConnection.peerHasAllPieces.set(hasAllPieces);
                        for (int i = 0; i < peerConnection.commonCfg.numPieces; i++) {
                            peerProcess.printDebug("Piece " + i + " is " + peerConnection.peerPieceMap.get(i));
                        }
                        if (hasAllPieces) {
                            peerProcess.printDebug("Peer has all pieces");
                            peerConnection.hostProcess.peerHasWholeFile.put(peerConnection.peerId, true);
                            peerConnection.hostProcess.logger.logPeerCompletion(String.valueOf(peerConnection.peerId));
                        }
                        peerConnection.setSelfInterested(peerConnection.peerHasAnyPiecesWeDont());

                        break;
                    default:
                        System.out.println("Received unknown message from peer");
                        break;
                }

            } catch(IOException e){
                //e.printStackTrace();
                //peerProcess.printError("Connection closed");
                //peerProcess.printError("Peer+ " + peerConnection.hostProcess.selfPeerId +" Connection closed");
                peerConnection.close();
                break;

            }

        }

        //peerConnection.hostProcess.logger.logShutdown();
    }

    byte[] receiveMessageLength() throws IOException {
        byte[] expectedLength = peerConnection.in.readNBytes(4);
        if (expectedLength.length < 4) {
            throw new IOException("Connection closed or insufficient data read for message length");
        }

        ByteBuffer wrapped = ByteBuffer.wrap(expectedLength);
        int expectedLengthInt = wrapped.getInt();
        //Type = 5th byte
        byte[] type = peerConnection.in.readNBytes(1);


        if (expectedLengthInt < 0) {
            throw new IOException("Invalid message length: " + expectedLengthInt);
        }

        byte[] overallMessage = new byte[expectedLengthInt + expectedLength.length + 1];
        System.arraycopy(expectedLength, 0, overallMessage, 0, expectedLength.length);
        System.arraycopy(type, 0, overallMessage, 4, 1);
        byte[] message = receiveMessage(expectedLengthInt);
        System.arraycopy(message, 0, overallMessage, 5, message.length);
        //parseBitmapMessage(overallMessage);
        return overallMessage;
    }

    public static void parseBitmapMessage(byte[] bitmapMessage) {
        // Extracting the message length
        int messageLength = 0;
        for (int i = 0; i < 4; i++) {
            messageLength |= (bitmapMessage[i] & 0xFF) << (24 - 8 * i);
        }

        // Extracting the message type
        int messageType = bitmapMessage[4] & 0xFF;

        // Outputting the extracted information
        System.out.println("Message Length: " + messageLength);
        System.out.println("Message Type: " + messageType);

        // Extracting and interpreting the payload
        System.out.println("Payload:");
        for (int i = 0; i < bitmapMessage.length; i++) {
            for (int j = 7; j >= 0; j--) {
                boolean isDownloaded = (bitmapMessage[i] & (1 << j)) != 0;
                System.out.print(isDownloaded ? "1" : "0");
            }
            System.out.println(); // New line for each byte
        }
    }


    byte[] receiveMessage(int expectedLength) throws IOException {
        byte[] message = new byte[expectedLength];
        int offset = 0;
        while (offset < expectedLength) {
            int bytesRead = peerConnection.in.read(message, offset, expectedLength - offset);
            if (bytesRead == -1) {
                throw new IOException("Connection terminated before message completion.");
            }
            offset += bytesRead;
        }
        return message;
    }

    void clearBuffer() throws IOException {
        int bytesAvailable = peerConnection.in.available();
        if (bytesAvailable > 0) {
            peerConnection.in.skip(bytesAvailable);
        }
    }
}
//Wrapper class for the logger
class PeerLogger {

    private final Logger logger;

    private final String peerID;

    //Each peer should write its log into the log file ‘log_peer_[peerID].log’ at the working
    //directory. For example, the peer with peer ID 1001 should write its log into the file
    //‘~/project/log_peer_1001.log’
    public PeerLogger(int peerID) throws IOException{
        logger = Logger.getLogger("peer_" + peerID);
        logger.setUseParentHandlers(false);

        Handler fileHandler = new FileHandler("log_peer_" + peerID + ".log");
        fileHandler.setFormatter(new CustomFormatter());
        logger.addHandler(fileHandler);
        this.peerID = String.valueOf(peerID);
    }

    static class CustomFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            String timestamp = getTimestamp();
            timestamp = String.format("%-29s", timestamp);
            return "[" + timestamp + "] " + record.getMessage() + "\n";
        }
    }

    private static synchronized String getTimestamp() {
        return java.time.LocalDateTime.now().toString();
    }

    public synchronized void logTCPConnection(String peerID2, boolean isServer) {
        //TCP connection
        //Whenever a peer makes a TCP connection to other peer, it generates the following log:
        //[Time]: Peer [peer_ID 1] makes a connection to Peer [peer_ID 2].
        //[peer_ID 1] is the ID of peer who generates the log, [peer_ID 2] is the peer connected
        //from [peer_ID 1]. The [Time] field represents the current time, which contains the date,
        //hour, minute, and second. The format of [Time] is up to you.
        if(isServer) {
            logger.info("Peer " + peerID + " is connected from Peer " + peerID2 + ".");
        } else {
            logger.info("Peer " + peerID + " makes a connection to Peer " + peerID2 + ".");
        }
    }

    public synchronized void logChangePreferredNeighbors(Vector<String> listNeighbors) {
        //Change of preferred neighbors
        //Whenever a peer changes its preferred neighbors, it generates the following log:
        //[Time]: Peer [peer_ID] has the preferred neighbors [preferred neighbor ID list].
        //The [Time] field represents the current time, which contains the date, hour, minute,
        //and second. The format of [Time] is up to you. [peer_ID] is the ID of peer who
        //generates the log. [preferred neighbor ID list] is the list of preferred neighbors of
        //[peer_ID]. The format of [preferred neighbor ID list] is “[peer_ID 1], [peer_ID 2], ...,
        //[peer_ID m]”, where [peer_ID 1], [peer_ID 2], ..., [peer_ID m] are the IDs of preferred
        //neighbors of [peer_ID]. Note that the list of preferred neighbors may change over
        //time. You should log the latest list of preferred neighbors of a peer.
        StringBuilder log = new StringBuilder("Peer " + peerID + " has the preferred neighbors ");
        for (int i = 0; i < listNeighbors.size(); i++) {
            log.append(listNeighbors.get(i));
            if (i != listNeighbors.size() - 1) {
                log.append(", ");
            }
        }
        logger.info(log.toString());
    }

    public synchronized void logChangeOptimisticallyUnchokedNeighbor(String neighborID) {
        //Whenever a peer changes its optimistically unchoked neighbor, it generates the following
        //log:
        //[Time]: Peer [peer_ID] has the optimistically unchoked neighbor [optimistically
        //unchoked neighbor ID].
        //[optimistically unchoked neighbor ID] is the peer ID of the optimistically unchoked
        //neighbor.
        logger.info("Peer " + peerID + " has the optimistically unchoked neighbor " + neighborID + ".");
    }

    public synchronized void logUnchoking(String neighborID) {
        //Whenever a peer is unchoked by a neighbor (which means when a peer receives an
        //unchoking message from a neighbor), it generates the following log:
        //[Time]: Peer [peer_ID 1] is unchoked by [peer_ID 2].
        //[peer_ID 1] represents the peer who is unchoked and [peer_ID 2] represents the peer
        //who unchokes [peer_ID 1].
        logger.info("Peer " + peerID + " is unchoked by " + neighborID + ".");
    }

    public synchronized void logChoking(String neighborID) {
        //Whenever a peer is choked by a neighbor (which means when a peer receives a choking
        //message from a neighbor), it generates the following log:
        //[Time]: Peer [peer_ID 1] is choked by [peer_ID 2].
        //[peer_ID 1] represents the peer who is choked and [peer_ID 2] represents the peer who
        //chokes [peer_ID 1].
        logger.info("Peer " + peerID + " is choked by " + neighborID + ".");
    }

    public synchronized void logReceiveHave(String neighborID, int pieceIndex) {
        //Whenever a peer receives a ‘have’ message, it generates the following log:
        //[Time]: Peer [peer_ID 1] received the ‘have’ message from [peer_ID 2] for the piece
        //[piece index].
        //[peer_ID 1] represents the peer who received the ‘have’ message and [peer_ID 2]
        //represents the peer who sent the message. [piece index] is the piece index contained in
        //the message
        logger.info("Peer " + peerID + " received the 'have' message from " + neighborID + " for the piece " + pieceIndex + ".");
    }

    public synchronized void logReceiveInterested(String neighborID) {
        //Whenever a peer receives an ‘interested’ message, it generates the following log:
        //[Time]: Peer [peer_ID 1] received the ‘interested’ message from [peer_ID 2].
        //[peer_ID 1] represents the peer who received the ‘interested’ message and [peer_ID 2]
        //represents the peer who sent the message.
        logger.info("Peer " + peerID + " received the 'interested' message from " + neighborID + ".");
    }

    public synchronized void logReceiveNotInterested(String neighborID) {
        //Whenever a peer receives a ‘not interested’ message, it generates the following log:
        //[Time]: Peer [peer_ID 1] received the ‘not interested’ message from [peer_ID 2].
        //[peer_ID 1] represents the peer who received the ‘not interested’ message and [peer_ID
        //2] represents the peer who sent the message.
        logger.info("Peer " + peerID + " received the 'not interested' message from " + neighborID + ".");
    }

    public synchronized void logDownloadedPiece(String neighborID, int pieceIndex, int numPieces) {
        //Whenever a peer finishes downloading a piece, it generates the following log:
        //[Time]: Peer [peer_ID 1] has downloaded the piece [piece index] from [peer_ID 2]. Now
        //the number of pieces it has is [number of pieces].
        //[peer_ID 1] represents the peer who downloaded the piece and [peer_ID 2] represents
        //the peer who sent the piece. [piece index] is the piece index the peer has downloaded.
        //[number of pieces] represents the number of pieces the peer currently has.
        logger.info("Peer " + peerID + " has downloaded the piece " + pieceIndex + " from " + neighborID + ". Now the number of pieces it has is " + numPieces + ".");
    }

    public synchronized void logCompletion() {
        //Whenever a peer downloads the complete file, it generates the following log:
        //[Time]: Peer [peer_ID] has downloaded the complete file.
        logger.info("Peer " + peerID + " has downloaded the complete file.");
    }

    public synchronized void logShutdown() {
        //Simple message that logs when all peers have the file and the program is shutting down
        logger.info("All peers have the complete file. Shutting down.");
    }

    public synchronized void logPeerCompletion(String peerID) {
        //Whenever a peer downloads the complete file, it generates the following log:
        //[Time]: Peer [peer_ID] has downloaded the complete file.
        logger.info("Peer " + peerID + " has downloaded the complete file.");
    }

    public synchronized void logFiveSecondDownloadRate(String neighborID, double downloadRate) {
        //Whenever a peer downloads the complete file, it generates the following log:
        //[Time]: Peer [peer_ID] has downloaded the complete file.
        //Not required by the assignment, but useful for debugging
        logger.info("Peer " + peerID + " has a download rate of " + downloadRate + " from " + neighborID + ".");
    }

}

