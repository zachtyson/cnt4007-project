import java.io.IOException;

public class ReceiveHandler extends Thread{
    PeerConnection peerConnection;
    ReceiveHandler(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }
    public void run() {
    }
    byte[] receiveMessageLength() throws IOException {
        // Each message (given from specifications) begins with a 4 byte length header
        // This method reads the length header and returns the message
        int expectedLength = peerConnection.in.read();  // Assumes a 4-byte length header
        return receiveMessage(expectedLength);
    }

    byte[] receiveMessage(int expectedLength) throws IOException {
        // Read message of length expectedLength bytes
        byte[] message = new byte[expectedLength]; //add +1 later for message type?
        int offset = 0;

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
