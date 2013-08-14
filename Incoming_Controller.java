import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.BitSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author jaybap
 */
public class Incoming_Controller extends Thread {

    private boolean am_alive = false;
    private ServerSocket incoming;
    private int port;
    TorrentInfo torrent;
    RUBTClient client;
    private boolean peer_is_interested = false;
    private boolean keepalive_sent = false;
    private boolean receiveBitfield = false;
    private boolean isClientchoking = true;
    private boolean handshake_confirmed=false;
    public BitSet peerbitfield = null;
    final int KEY_CHOKE = 0;
    final int KEY_UNCHOKE = 1;
    final int KEY_INTERESTED = 2;
    final int KEY_UNINTERESTED = 3;
    final int KEY_HAVE = 4;
    final int KEY_BITFIELD = 5;
    final int KEY_REQUEST = 6;
    final int KEY_PIECE = 7;
    final int KEY_CANCEL = 8;
    final int KEY_PORT = 9;
    float upload_rate;
    DataInputStream frompeer;
    DataOutputStream topeer;
    Socket peersocket;

    Incoming_Controller(Tracker tracker, TorrentInfo torr, RUBTClient c) {

        am_alive = true;
        port = tracker.listeningPort;
        torrent = torr;
        this.client = c;
    }

    public void run() {

        try {
            this.incoming = new ServerSocket(port);
          
                Thread.yield();



                /**
                 * waits for incoming connection
                 */
                peersocket = incoming.accept();
                InetAddress ip = peersocket.getInetAddress();
                int port = peersocket.getPort();
                
                frompeer = new DataInputStream(peersocket.getInputStream());
                topeer = new DataOutputStream(peersocket.getOutputStream());

                /**
                 * Reads in the handshake response from new peer
                 */
                byte[] response = new byte[68];
                frompeer.read(response);

                boolean match = true;
                for (int y = 1; y <= 20; y++) {
                    if (response[response.length - 20 - y] !return null;= torrent.info_hash.array()[torrent.info_hash.array().length - y]) {
                        System.err.print("INFO HASHES DO NOT MATCH. INCOMING CONNECTION BEING CLOSED!");
                        peersocket.close();
                        match = false;
                        break;
                    }
                }

                if (match) {
                    handshake_confirmed=true;
                    String response_string = new String(response);
                    String peer_id = response_string.substring(48);

                    Handshake(RUBTClient.peerID, RUBTClient.torrent.info_hash);
                    Messages.sendBitfield(RUBTClient.Bitfield, peersocket, topeer);

                    setpoint:
                    try {
                        if (frompeer.available() > 0) { //TODO: check if it runs

                            keepalive_sent = false;

                            int len = getPeerResponseInt(peersocket, topeer, frompeer);
                            if (len != 0) {
                                long startTime = System.currentTimeMillis();
                                byte[] message = Messages.getPeerResponse(len, peersocket, topeer, frompeer);
                                float elapsed = (System.currentTimeMillis() - startTime) / 1000;
                                upload_rate = len / elapsed;
                                byte id = message[0];


                                //if message received is not bitfield msg then sets the flag to false
                                if (id != KEY_BITFIELD) {
                                    receiveBitfield = false;
                                }

                                switch (id) {
                                    case KEY_CHOKE:

                                        break setpoint;

                                    case KEY_UNCHOKE:

                                        break setpoint;

                                    case KEY_INTERESTED:
                                        
                                        if (handshake_confirmed){
                                            
                                        
                                        for (int i = 0; i <= 2; i++) {
                                            if (RUBTClient.uploadconnections[i] == null) {
                                                RUBTClient.uploadconnections[i] = this;
                                            }
                                        }
                                        for (int i = 0; i <= 2; i++) {
                                            if (RUBTClient.uploadconnections[i] == this) {
                                                Messages.unchoke(peersocket, topeer, frompeer);
                                                isClientchoking = false;
                                                peer_is_interested = true;
                                                System.out.println("Peer " + peer_id + " sent an interested message");
                                                System.out.println("Peer " + peer_id + " has been unchoked");
                                                break setpoint;
                                            }
                                        }
                                        
                                            RUBTClient.choked_peers.add(this);
                                            if (Messages.choke(peersocket, topeer, frompeer)) {

                                                System.out.println("Peer " + peer_id + " has been choked");
                                            }
                                        }
                                        else {
                                                System.err.println("Peer " + peer_id + " sent illegal interested message. Violated Protocol so closing connection.");
                                                terminateSocketConnections(peersocket, topeer, frompeer);
                                            }
                                            break setpoint;

                                        
                                
                            
                        
                        case KEY_UNINTERESTED:
                                        peer_is_interested = false;
                                        break setpoint;

                                    case KEY_HAVE:
                                        Messages.receiveHave(peersocket, topeer, frompeer,peerbitfield , peer_id);
                                        break setpoint;

                                    case KEY_BITFIELD:
                                        //if bitfield flag is set
                                        if (receiveBitfield) {
                                            try {
                                               peerbitfield= Messages.receiveBitField(len,peer_id, peersocket,topeer,frompeer);

                                            } catch (Exception ex) {
                                                Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
                                            }
                                        } else {
                                            //else bitfield was received out of order and hence close connection
                                            System.out.println("Received bitfield out of sync... closing connection");
                                            this.suicide();
                                        }
                                        break setpoint;

                                    case KEY_REQUEST:
                                        if (isClientchoking) {
                                            System.err.println("Peer " + peer_id + " sent a request message while choked. Violated Protocol and so closing connection.");
                                            this.suicide();
                                            break setpoint;
                                        }

                                        int index = getPeerResponseInt(peersocket, topeer, frompeer);
                                        int offset = getPeerResponseInt(peersocket, topeer, frompeer);
                                        int length = getPeerResponseInt(peersocket, topeer, frompeer);
                                        System.out.println("Request received from peer " + peer_id + " for i=" + index + " o=" + offset);
                                        
                                            byte[] block = new byte[length];
                                            System.arraycopy(RUBTClient.piecesDL[index].toByteArray(), offset, block, 0, length);
                                        
                                        Messages.piece(peersocket, topeer,index, offset, block);
                                        break;

                                    case KEY_PIECE:

                                        break setpoint;

                                    default:
                                        break setpoint;
                                }
                            }
                        }
                    }  catch (IOException e) {
                        System.err.println("Caught IOException: " + e.getMessage());

                    }
                }
            }
         catch (IOException ex) {
            Logger.getLogger(Incoming_Controller.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void suicide() {
        am_alive = false;
    }

    public byte[] getPeerResponse(int length, Socket peersocket, DataOutputStream topeer, DataInputStream frompeer) {
        try {
            byte[] peerResponse = new byte[length];
            frompeer.readFully(peerResponse);

            return peerResponse;
        } catch (IOException e) {
            terminateSocketConnections(peersocket, topeer, frompeer);
            System.err.println("ERROR: Unable to receive peer response. ");
            return null;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to receive peer response. ");
            return null;
        }
    }
    
    public static void sendunchoke( Socket peerSocket, DataOutputStream client2peer, DataInputStream peer2client) {
        Messages.unchoke(peerSocket,client2peer,peer2client)
        this.isClientchoking=false;
        
    }
    
    public static void sendchoke( Socket peerSocket, DataOutputStream client2peer, DataInputStream peer2client) {
        Messages.choke(peerSocket,client2peer,peer2client)
        this.isClientchoking=true;
        
    }

    public static void terminateSocketConnections(Socket peerSocket, DataOutputStream client2peer, DataInputStream peer2client) {
        try {
            if (!peerSocket.isClosed()) {
                peerSocket.close();
                client2peer.close();
                peer2client.close();
            }
        } catch (Exception e) {
            System.err.println("ERROR: Could not terminate open socket connections. ");
        }
    }

    /**
     * Method: get int response
     */
    public synchronized int getPeerResponseInt(Socket peersocket, DataOutputStream topeer, DataInputStream frompeer) {
        try {
            return frompeer.readInt();
        } catch (IOException e) {
            terminateSocketConnections(peersocket, topeer, frompeer);
            System.out.println("first " + e.toString());
            System.err.println("ERROR: Unable to receive peer response. ");
            return -1;
        } catch (Exception e) {
            System.out.println("second " + e.toString());
            System.err.println("ERROR: Unable to receive peer response. ");
            return -1;
        }
    }
}
