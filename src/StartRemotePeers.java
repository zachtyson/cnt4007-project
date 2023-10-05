import java.io.*;
import java.util.*;

public class StartRemotePeers {
    public Vector<RemotePeerInfo> peerInfoVector;
    public void getConfiguration() {
        String st;
        int i1;
        peerInfoVector = new Vector<RemotePeerInfo>();
        try {
            BufferedReader in = new BufferedReader(new FileReader("PeerInfo.cfg"));
            while((st = in.readLine()) != null) {
                String[] tokens = st.split("\\s+");
                peerInfoVector.addElement(new RemotePeerInfo(tokens[0], tokens[1], tokens[2]));

            }

            in.close();
        }
        catch (Exception ex) {
            System.out.println(ex.toString());
        }
    }
    public static void main(String[] args) {
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
        // Below is some code they gave us that they said should help get started
        try {
            StartRemotePeers myStart = new StartRemotePeers();
            myStart.getConfiguration();
            // get current path
            String path = System.getProperty("user.dir");
            // start clients at remote hosts
            for (int i = 0; i < myStart.peerInfoVector.size(); i++) {
                RemotePeerInfo pInfo = (RemotePeerInfo) myStart.peerInfoVector.elementAt(i);
                System.out.println("Start remote peer " + pInfo.peerId +  " at " + pInfo.peerAddress );
                Runtime.getRuntime().exec("ssh " + pInfo.peerAddress + " cd " + path + "; java peerProcess " + pInfo.peerId);
            }
            System.out.println("Starting all remote peers has done." );

        }
        catch (Exception ex) {
            System.out.println(ex);
        }
    }
    public static class RemotePeerInfo {
        public String peerId;
        public String peerAddress;
        public String peerPort;

        public RemotePeerInfo(String peerId, String peerAddress, String peerPort) {
            this.peerId = peerId;
            this.peerAddress = peerAddress;
            this.peerPort = peerPort;
        }
    }
}
