import java.io.BufferedReader;
import java.io.FileReader;
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
        int peerID = 0;
        try {
            peerID = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Error: Peer ID must be an integer");
            System.exit(1);
        }
        // Read PeerInfo.cfg
        Vector<RemotePeerInfo> peerInfoVector = new Vector<>();
        String line;
        try {
            BufferedReader in = new BufferedReader(new FileReader("PeerInfo.cfg"));
            while((line = in.readLine()) != null) {
                // Split line by whitespace
                String[] tokens = line.split("\\s+");
                try {
                    // Attempt to parse peer ID
                    int tempPeerID = Integer.parseInt(tokens[0]);
                    if(tempPeerID != peerID) {
                        // If peer ID is not the same as the current peer, add it to the vector
                        peerInfoVector.addElement(new RemotePeerInfo(tokens[0], tokens[1], tokens[2]));
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Error: Peer ID must be an integer");
                    System.exit(1);
                }
            }
            in.close();
        } catch (Exception ex) {
            System.out.println(ex.toString());
        }

    }
}
