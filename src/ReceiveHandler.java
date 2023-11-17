import java.io.IOException;
import java.nio.ByteBuffer;

//ReceiveHandler is in charge of receiving messages from the peer, and passing them to the host process
//and this happens by modifying the PeerConnection object's ConcurrentHashMap of byte[] and status enum
public class ReceiveHandler extends Thread{
    PeerConnection peerConnection;
    ReceiveHandler(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }
    @Override
    public void run() {
        if(peerConnection.socket == null) {
            System.out.println("Error: socket is null, verify that the peer is in the correct order in PeerInfo.cfg and that currentPeer is set correctly");
        }
        if(peerConnection.in == null) {
            System.out.println("Error: in is null, verify that the peer is in the correct order in PeerInfo.cfg and that currentPeer is set correctly");
        }
        if(peerConnection.out == null) {
            System.out.println("Error: out is null, verify that the peer is in the correct order in PeerInfo.cfg and that currentPeer is set correctly");
        }
        System.out.println("Starting receive handler for peer " + peerConnection.peerId);
        while (true) {
            try {
                byte[] message = receiveMessageLength();
                if(message.length == 0) {
                    System.out.println("Received keep alive message from peer " + peerConnection.peerId);
                    continue;
                }
                System.out.println("Received message from peer " + peerConnection.peerId);
                System.out.println("Message length: " + message.length);
                int payloadLength = message.length - 5; //subtract 5 for the 4 byte length header and the message type
                Message.Interpretation interpretation = Message.msgInterpret(message);
                if(interpretation.Msg == MsgType.piece) {
                    peerConnection.sendResponses.add(interpretation.pieceIndex);
                    peerConnection.hostProcess.pieceData.put(interpretation.pieceIndex, interpretation.messagePayload);
                    peerConnection.hostProcess.pieceMap.put(interpretation.pieceIndex, peerProcess.pieceStatus.DOWNLOADED);
                }
                if(interpretation.Msg == MsgType.have) {
                    peerConnection.pieceMap.put(interpretation.pieceIndex, peerProcess.pieceStatus.DOWNLOADED);
                }
//                if (payloadLength < 0) {
//                    System.out.println("Received message from peer " + peerConnection.peerId);
//                    System.out.println("Message type: " + interpretation.Msg);
//                    System.out.println("Payload length: " + interpretation.payloadLength);
//                    System.out.println("Payload: " + interpretation.messagePayload);
//                    throw new IOException("Payload length is negative");
//
//                }
//                System.out.println("Received message from peer " + peerConnection.peerId);
//                System.out.println("Message type: " + interpretation.Msg);
//                System.out.println("Payload length: " + interpretation.payloadLength);
//                System.out.println("Payload: " + interpretation.messagePayload);
                // Process the message here
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }
    }
    byte[] receiveMessageLength() throws IOException {
        // Each message (given from specifications) begins with a 4 byte length header
        // This method reads the length header and returns the message
        System.out.println("Received message from peerS " + peerConnection.peerId);
        byte[] expectedLength = peerConnection.in.readNBytes(4);
        ByteBuffer wrapped = ByteBuffer.wrap(expectedLength);
        int expectedLengthInt = -1;
        try {
            expectedLengthInt = wrapped.getInt();

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println("Error: " + e.getStackTrace());
            System.out.println("Error: " + e.getCause());
            System.out.println("Error: " + e.getLocalizedMessage());
            System.out.println("Error: " + e.getSuppressed());
        }
                // Assumes a 4-byte length header
        if(expectedLengthInt == -1) {
            throw new IOException("Connection was terminated before message was complete.");
        }
        if(expectedLengthInt == 0) {
            return new byte[0];
        }
        System.out.println("Message length: " + expectedLengthInt);
        return receiveMessage(expectedLengthInt);
    }

    byte[] receiveMessage(int expectedLength) throws IOException {
        // Read message of length expectedLength bytes
        byte[] message = new byte[expectedLength+1]; //add +1 later for message type?
        int offset = 0;
        byte messageType = (byte) peerConnection.in.read();
        while (offset < expectedLength) {
            int bytesRead = peerConnection.in.read(message, offset, expectedLength - offset);
            if (bytesRead == -1) {
                throw new IOException("Connection was terminated before message was complete.");
            }
            offset += bytesRead;
        }

        return message;
    }
}
