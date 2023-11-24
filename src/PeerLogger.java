import java.io.IOException;
import java.util.Vector;
import java.util.logging.*;

//Wrapper class for the logger
public class PeerLogger {

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
            return "[" + getTimestamp() + "] " + record.getMessage() + "\n";
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

}
