import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * This class takes care of incoming connections from peers,
 * checks peerlist, and reads the handshake from new peers.
 * 
 * @author Alex DeOliveira  [126-00-8635]
 * @author Jason Baptista   [126-00-4630]
 * @author Elizabeth Sanz   [127-00-8628]
 * @version "project01"
 */

public class Incoming_Controller {

    private boolean am_alive = false;
    private ServerSocket incoming;
    private int port;
    TorrentInfo torrent;
    RUBTClient client;

    Incoming_Controller(Tracker tracker, TorrentInfo torr, RUBTClient c) {

        am_alive = true;
        port = tracker.listeningPort;
        torrent = torr;
        this.client = c;
    }

    /* Spins the Incoming Controller Thread */
    public void run() {

        try {
            this.incoming = new ServerSocket(port);
            while (am_alive) {
                Thread.yield();



                /**
                 * waits for incoming connection
                 */
                Socket peersocket = incoming.accept();
                InetAddress ip = peersocket.getInetAddress();
                int port = peersocket.getPort();
                /**
                 * Checks if the peer is in our peer list
                 */
                if (!isValid(ip.getHostAddress())) {
                    System.err.print("Peer from invalid IP address " + ip.getHostAddress() + "trying to connect");
                } else {
                    DataInputStream frompeer = new DataInputStream(peersocket.getInputStream());

                    /**
                     * Reads in the handshake response from new peer
                     */
                    byte[] response = new byte[68];
                    frompeer.read(response);

                    boolean match = true;
                    for (int y = 1; y <= 20; y++) {
                        if (response[response.length - 20 - y] != torrent.info_hash.array()[torrent.info_hash.array().length - y]) {
                            System.err.print("INFO HASHES DO NOT MATCH. INCOMING CONNECTION BEING CLOSED!");
                            peersocket.close();
                            match = false;
                            break;
                        }
                    }

                    if (match) {
                        String response_string = new String(response);
                        String peer_id = response_string.substring(48);
                        Peer peer = new Peer(ip.toString(), port, true, peer_id);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Caught IOException: " + e.getMessage());
            /*
             * Restarts incoming controller to listen to more conections from peers
             */
            run();

        }

    }

    /* Method: Suicide, used to set am_alive false */
    public void suicide() {
        am_alive = false;
    }

    private boolean isValid(String ip) {
        return ip.equals("128.6.171.3") || ip.equals("128.6.171.4");
    }
}
