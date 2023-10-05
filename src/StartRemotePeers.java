public class StartRemotePeers {
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
    }
}
