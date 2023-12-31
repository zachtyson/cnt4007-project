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

public class PeerConnection extends Thread {
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
