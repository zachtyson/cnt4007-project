import java.io.IOException;
import java.util.logging.*;

//Wrapper class for the logger
public class PeerLogger {

    private final Logger logger;

    //Each peer should write its log into the log file ‘log_peer_[peerID].log’ at the working
    //directory. For example, the peer with peer ID 1001 should write its log into the file
    //‘~/project/log_peer_1001.log’
    public PeerLogger(int peerID) throws IOException{
        logger = Logger.getLogger("peer_" + peerID);
        logger.setUseParentHandlers(false);

        Handler fileHandler = new FileHandler("log_peer_" + peerID + ".log");
        fileHandler.setFormatter(new SimpleFormatter());
        logger.addHandler(fileHandler);
    }

    private String getTimestamp() {
        return java.time.LocalDateTime.now().toString();
    }

    public void logTCPConnection(String peerID, String peerID2) {
        //TCP connection
        //Whenever a peer makes a TCP connection to other peer, it generates the following log:
        //[Time]: Peer [peer_ID 1] makes a connection to Peer [peer_ID 2].
        //[peer_ID 1] is the ID of peer who generates the log, [peer_ID 2] is the peer connected
        //from [peer_ID 1]. The [Time] field represents the current time, which contains the date,
        //hour, minute, and second. The format of [Time] is up to you.
        logger.info("[" + getTimestamp() + "] Peer " + peerID + " makes a connection to Peer " + peerID2 + ".");
    }

}
