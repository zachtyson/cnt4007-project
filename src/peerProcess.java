import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
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
