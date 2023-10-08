import java.io.BufferedReader;
import java.io.FileReader;
import java.net.Socket;
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
        peerProcess currentPeer = new peerProcess(ID);
    }

    public peerProcess(int currentPeerID) {
        this.peerVector = getPeers(currentPeerID);
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

    public static class Peer {
        public int peerId;
        public String peerAddress;
        public int peerPort;
        private Socket socket;
        private Thread connectionThread;

        public Peer(int peerId, String peerAddress, int peerPort) {
            this.peerId = peerId;
            this.peerAddress = peerAddress;
            this.peerPort = peerPort;
        }

        public void connectToPeer(Peer otherPeer) {

        }

        public Thread waitForConnection() {
            return null;
        }

        public Thread getConnectionThread() {
            return connectionThread;
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
}
