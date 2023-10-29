import java.io.IOException;

public class SendHandler extends Thread {
    PeerConnection peerConnection;
    SendHandler(PeerConnection peerConnection) {
        this.peerConnection = peerConnection;
    }
    public void run() {
    }
    void sendMessage(byte[] msg) {
        try {
            //stream write the message
            peerConnection.out.write(msg);
            peerConnection.out.flush();
            System.out.println("Sent message to peer " + peerConnection.peerId);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

}
