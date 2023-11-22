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

}
