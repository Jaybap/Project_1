
import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.util.BitSet;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Peer extends Thread {

    /**
     * It has methods to perform handshake, alive, interested, and other
     * messages.
     */
    /**
     * Peer Information
     */
    public byte[] peerID;
    public String peerIP = null;
    public int peerPort = 0;
    public Socket peerSocket = null;
    public BitSet peerbitfield = null;
    /**
     * Connection Information
     */
    public DataOutputStream client2peer = null;
    public DataInputStream peer2client = null;
    /**
     * BOOLEAN Connection status
     */
    public boolean connected = false;
    public boolean handshakeConfirmed = false;
    public boolean[] booleanBitField = null;
    public boolean incoming = false;
    private boolean receiveBitfield;
    private boolean is_interested = false;
    private boolean keepalive_sent = false;
    /**
     * BOOLEAN Peer Status
     */
    public boolean isClientchoking = false;
    public boolean client_interested = false;
    public boolean am_alive = false;
    public boolean peerChoking;
    final static int KEY_CHOKE = 0;
    final static int KEY_UNCHOKE = 1;
    final static int KEY_INTERESTED = 2;
    final static int KEY_UNINTERESTED = 3;
    final static int KEY_HAVE = 4;
    final static int KEY_BITFIELD = 5;
    final static int KEY_REQUEST = 6;
    final static int KEY_PIECE = 7;
    final static int KEY_CANCEL = 8;
    final static int KEY_PORT = 9;
    /**
     * Set the byte arrays for the static peer messages
     */
    final static byte[] keep_alive = {0, 0, 0, 0};
    final static byte[] choke = {0, 0, 0, 1, 0};
    final static byte[] unchoke = {0, 0, 0, 1, 1};
    final static byte[] interested = {0, 0, 0, 1, 2};
    final static byte[] uninterested = {0, 0, 0, 1, 3};
    final static byte[] have = {0, 0, 0, 5, 4};
    final static byte[] request = {0, 0, 1, 3, 6};
    final static byte[] piece = {0, 0, 0, 9, 7};
    final static byte[] empty_bitfield = {0, 0, 0, 2, 5, 0};

    /* ================================================================================ */
    /* 									Peer Constructor								*/
    /* ================================================================================ */
    public Peer(String IpNum, int peerPortNum, boolean incoming, String id) {
        if (!(id == null) && !(id.equals(""))) {
            this.peerID = id.getBytes();
        }
        this.peerIP = IpNum;
        this.peerPort = peerPortNum;
        this.booleanBitField = new boolean[RUBTClient.numPieces];
        this.incoming = incoming;
        this.am_alive = true;
        start();
    }

    /* ================================================================================ */
    /* 										Methods										*/
    /* ================================================================================ */
    /**
     * METHOD: Create handshake
     */
    public static byte[] buildHandshake(String localPeerID, ByteBuffer infoHash) {
        /* Variables */
        int i = 0;
        byte[] handshakeBytes;

        /**
         * Create handshake byte array
         */
        handshakeBytes = new byte[68];

        /**
         * Begin byte array with byte "nineteen"
         */
        handshakeBytes[i] = 0x13; //decimal: 19
        i++;

        try {
            /**
             * Put "BitTorrent protocol"-byte array
             */
            byte[] btBytes = {'B', 'i', 't', 'T', 'o', 'r', 'r', 'e', 'n', 't',
                ' ', 'p', 'r', 'o', 't', 'o', 'c', 'o', 'l'};
            System.arraycopy(btBytes, 0, handshakeBytes, i, btBytes.length);
            i = i + btBytes.length;

            /**
             * Put zero-byte array
             */
            byte[] zeroBytes = new byte[8];
            System.arraycopy(zeroBytes, 0, handshakeBytes, i, zeroBytes.length);
            i = i + zeroBytes.length;

            /**
             * Put 20-byte SHA-1 hash
             */
            System.arraycopy(infoHash.array(), 0, handshakeBytes, i, infoHash.array().length);
            i = i + infoHash.array().length;

            /**
             * Put peer id (generated by client)
             */
            System.arraycopy(localPeerID.getBytes("ASCII"), 0, handshakeBytes, i, localPeerID.getBytes("ASCII").length);
        } catch (UnsupportedEncodingException e) {
            System.err.println("ERROR: Could not complete handshake. ");
            e.printStackTrace();
        }
        return handshakeBytes;
    }

    /**
     * METHOD: Send handshake
     */
    public void sendHandshake(String localPeerID, ByteBuffer info_hash) {
        try {
            byte[] handshake = buildHandshake(localPeerID, info_hash);
            if (!connected) {
                this.setPeerConnection();
            }
            client2peer.write(handshake);
            client2peer.flush();
        } catch (IOException e) {
            System.err.println("Problem sending handshake to " + peerID);
            e.printStackTrace();
        }
    }

    /**
     * METHOD: Verify handshake
     */
    public boolean verifyHandshake(ByteBuffer torrentInfoHash) {
        /* Variables */
        int index = 0;
        byte[] trueInfoHash = torrentInfoHash.array();
        byte[] handshakeInfoHash = new byte[20];
        byte[] handshakeResponse = new byte[68];

        /**
         * Read response
         */
        try {
            peer2client.read(handshakeResponse);

            byte[] buffer = new byte[20];
            //System.arraycopy(handshakeResponse, );
            //not yet
            /**
             * Extract info hash from handshake response
             */
            System.arraycopy(handshakeResponse, 28, handshakeInfoHash, 0, 20);

            /**
             * Verify if torrent info hash and handshake info hash are the
             * identical
             */
            while (index < 20) {
                if (handshakeInfoHash[index] != trueInfoHash[index]) {
                    peerSocket.close();
                    return false;
                } else {
                    ++index;
                }
            }
        } catch (Exception e) {
            System.err.println("Could not read handshakeResponse. ");

        }
        handshakeConfirmed = true;
        return true;
    }

    /**
     * Function will compute the SHA-1 hash of a piece and compare it to the
     * SHA-1 from the meta-info torrent file
     *
     * @param piece The byte array of the piece which was downloaded from the
     * peer
     * @param index The index number of the piece.
     * @return True if the piece matches the SHA-1 hash from the meta-info
     * torrent file otherwise false.
     */
    public boolean verifySHA(byte[] piece, int index) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA");
        } catch (Exception e) {
            System.out.println("Bad SHA algorithm");
            return false;
        }
        byte[] hash = digest.digest(piece);
        byte[] info_hash = RUBTClient.torrent.piece_hashes[index].array();
        for (int i = 0; i < hash.length; i++) {
            if (hash[i] != info_hash[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * METHOD: Close peer socket
     */
    public void closePeerSocket() {
        try {
            if (peerSocket != null) {
                peerSocket.close();
            }
        } catch (Exception e) {
            System.err.println("ERROR: Could not close peer socket. ");
        }
    }

    public void terminateSocketConnections() {
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
     * Method: Running the program
     */
    public void run() {
        setpoint:
        while (am_alive) {
            if (this.incoming) {

                setPeerConnection();
                sendHandshake(RUBTClient.peerID, RUBTClient.torrent.info_hash);
                sendBitfield(RUBTClient.Bitfield);
                try {
                    if (peer2client.available() > 0) { //TODO: check if it runs

                        keepalive_sent = false;

                        int len = getPeerResponseInt();
                        if (len != 0) {


                            byte id = getPeerResponseByte();

                            //if message received is not bitfield msg then sets the flag to false
                            if (id != Peer.KEY_BITFIELD) {
                                this.receiveBitfield = false;
                            }

                            switch (id) {
                                case Peer.KEY_CHOKE:
                                    
                                    break setpoint;

                                case Peer.KEY_UNCHOKE:

                                    break setpoint;

                                case Peer.KEY_INTERESTED:
                                    if (unchoke()) {
                                        is_interested = true;
                                        System.out.println("Peer " + peerID + " sent an interested message");
                                    } else {
                                        System.err.println("Peer " + peerID + " sent illegal interested message. Violated Protocol so closing connection.");
                                        this.closeConnection();
                                    }
                                    break;

                                case Peer.KEY_UNINTERESTED:
                                    is_interested = false;
                                    break;

                                case Peer.KEY_HAVE:
                                    receiveHave();
                                    break setpoint;

                                case Peer.KEY_BITFIELD:
                                    //if bitfield flag is set
                                    if (receiveBitfield) {
                                        try {
                                            receiveBitField(len);
                                            
                                        } catch (Exception ex) {
                                            Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                    } else {
                                        //else bitfield was received out of order and hence close connection
                                        System.out.println("Received bitfield out of sync... closing connection");
                                        this.suicide();
                                    }
                                    break;

                                case Peer.KEY_REQUEST:
                                    if (isClientchoking) {
                                        System.err.println("Peer " + peerID + " sent a request message while choked. Violated Protocol and so closing connection.");
                                        this.suicide();
                                        break;
                                    }

                                    int index = getPeerResponseInt();
                                    int offset = getPeerResponseInt();
                                    int length = getPeerResponseInt();
                                    System.out.println("Request received from peer " + peerID + " for i=" + index + " o=" + offset);
                                    sendPiece(index, offset, length);
                                    break;

                                case Peer.KEY_PIECE:
                                     
                                        break setpoint;
                                   
                                default:
                                    break;
                            }
                        }
                    } else {

                        /* Set up connection with Peer */
                        setPeerConnection();

                        /* Establish handshake */
                        sendHandshake(RUBTClient.peerID, RUBTClient.torrent.info_hash);
                        System.out.println("Handshake sent");

                        /* Receive and verify handshake */
                        if (!verifyHandshake(RUBTClient.torrent.info_hash)) {
                            System.err.println("ERROR: Unable to verify handshake. ");
                        } else {
                            int len = getPeerResponseInt();
                            System.out.println(len);
                            byte message = getPeerResponseByte();
                            System.out.println(message);
                            byte[] peerbits = getPeerResponse(len - 1);
                            receiveBitfield(peerbits);
                            System.out.println(peerbitfield.toString());
                            interested();
                            int numBlks = RUBTClient.numBlkPieceRatio;
                            System.out.println("Original # of blocks " + numBlks);
                            int total = 0;
                            while (am_alive) {
                                for (int i = 0; i < RUBTClient.numPieces; i++) {
                                    if (!DownloadManager.hasPiece(i, this) && peerbitfield.get(i)) {
                                        ByteArrayOutputStream currentPiece = new ByteArrayOutputStream();
                                        System.out.println("Request Piece " + i);
                                        if (i == RUBTClient.numPieces - 1) {
                                            numBlks = (int) Math.ceil((double) RUBTClient.lastPieceSize / (double) RUBTClient.blockLength);
                                            System.out.println("Blocks for last piece " + numBlks);
                                        }
                                        for (int j = 0; j < numBlks; j++) {
                                            System.out.println("Request Block " + j);
                                            if (j == numBlks - 1) {
                                                if (i == RUBTClient.numPieces - 1) {
                                                    request(i, j * RUBTClient.blockLength, RUBTClient.lastBlkSize);
                                                } else {
                                                    request(i, j * RUBTClient.blockLength, RUBTClient.torrent.piece_length - (j * RUBTClient.blockLength));
                                                }
                                            } else {
                                                request(i, j * RUBTClient.blockLength, RUBTClient.blockLength);
                                            }
                                            int length = getPeerResponseInt();
                                            byte[] block = new byte[length - 9];
                                            System.arraycopy(getPeerResponse(length), 9, block, 0, length - 9);
                                            total += block.length;

                                            try {
                                                currentPiece.write(block);
                                            } catch (IOException e) {
                                                System.err.println("Error: Problem saving block " + j + " of piece " + i);
                                            }
                                            System.out.println(total + "/" + RUBTClient.torrent.file_length);
                                        }
                                        if (verifySHA(currentPiece.toByteArray(), i)) {
                                            DownloadManager.savePiece(currentPiece, i, this);
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    /* ================================================================================ */
    /* 										Messages 									*/
    /* ================================================================================ */

    private void sendPiece(int index, int offset, int length) {
        try {

            //peer already has the piece but still requesting it
            if (peerbitfield.get(index)) {
                System.err.println("Peer " + peerID + " violated protocol. Requested piece that it already has");
                this.suicide();
                return;
            }
            //if we have the piece
            if (RUBTClient.Bitfield.get(index)) {
                byte[] block = new byte[length];
                System.arraycopy(RUBTClient.piecesDL[index].toByteArray(), offset, block, 0, length);
                //send the piece
                if (!piece(index, offset, block)) {
                }
                System.out.println("Uploaded bytes to peer " + peerID + " for i=" + index + " o=" + offset);

            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.toString());
            System.err.println("Could not send i=" + index + " o=" + offset + " to peer " + peerID);
        }
    }

    private void receiveBitField(int length) throws Exception {
        int l = 0;
        BitSet bs = new BitSet(RUBTClient.numPieces);
        byte bitfield_array[] = getPeerResponse(length - 1);

        byte bit_mask = (byte) 0x80;
        //reading in bitfield bit by bit
        for (int k = 0; k < bitfield_array.length; k++) {
            byte bitfield = bitfield_array[k];

            for (int i = 0; i < 8; i++) {
                if (l < RUBTClient.numPieces) {
                    bs.set(k * 8 + i, ((bitfield & bit_mask) == bit_mask) ? true : false);
                    bitfield = (byte) (bitfield >>> 2);
                    l++;
                }
            }
        }

        if (l == RUBTClient.numPieces) {
            //update the global bitset
            System.out.println("BitField successfully received");
            peerbitfield = bs;
            System.out.println("Bitfield received from " + this.peerID);
        } else {
            throw new Exception("BitField Error: Size does not match");
        }
    }

    private void receiveHave() {
        int piece_index = getPeerResponseInt();
        System.out.println("Have message for piece " + piece_index + " received from peer " + peerID);

        if (piece_index < 0 || piece_index >= RUBTClient.numPieces) {
            System.out.println("Invalid have message received hence closing connection.");
            this.suicide();
        }


        //if peer has piece that not in global bit set and we are not interested then
        //send interested message
        if (!RUBTClient.Bitfield.get(piece_index) && !client_interested) {
            interested();
        }

        if ((peerbitfield.get(piece_index))) {
            System.err.println("Peer " + peerID + " sent a have message for piece it already had before\n Violated protocol so closing connection");
            this.suicide();
        }

        peerbitfield.set(piece_index, true);

    }

    public synchronized boolean sendBitfield(BitSet bitfield) {
        try {
            byte[] bitfield_bytes = new byte[(bitfield.length() - 1) / 8 + 1];

            //Initialize to zero
            for (int x = 0; x < bitfield_bytes.length; x++) {
                bitfield_bytes[x] = (byte) 0;
            }

            //Converts Bitset to byte array
            for (int i = 0; i < bitfield.length(); i++) {
                if (bitfield.get(i)) {
                    bitfield_bytes[i / 8] |= 1 << (7 - (i % 8));
                }
            }

            ByteBuffer bitfield_buffer = ByteBuffer.allocate(5 + bitfield_bytes.length);
            bitfield_buffer.putInt(1 + bitfield_bytes.length);
            bitfield_buffer.put((byte) 5);
            bitfield_buffer.put(bitfield_bytes);
            client2peer.write(bitfield_buffer.array());
            client2peer.flush();
            System.out.println("Bitfield message sent");
            return true;
        } catch (Exception ioe) {
            System.err.println(ioe.getMessage());
            System.out.println("COULD NOT SEND BITFIELD MESSAGE");
            return false;
        }
    }

    /**
     * Message: KEEP ALIVE
     */
    public boolean keepAlive() {
        try {
            client2peer.write(keep_alive);
            client2peer.flush();

            /* ================ */
            /* Print Statements */
            /* ================ */
            System.out.println("Sent keepAlive message to " + peerIP + ":" + peerPort);

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Message: INTERESTED
     */
    public boolean interested() {
        try {
            client2peer.write(interested);
            client2peer.flush();
            System.out.println("Sent interested message to " + peerIP + ":" + peerPort);
            int length = peer2client.readInt();
            if ((int) peer2client.readByte() == 1) {
                System.out.println("Unchoke received");
            } else {
                return false;
            }


            /* ================ */
            /* Print Statements */
            /* ================ */
            // System.out.println("Sent interested message to " + peerID);

            is_interested = true;
            return true;
        } catch (IOException e) {
            terminateSocketConnections();
            System.err.println("ERROR: Unable to send interested message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send interested message. ");
            return false;
        }
    }

    /**
     * Message: Uninterested
     */
    public boolean uninterested() {
        try {
            client2peer.write(uninterested);
            client2peer.flush();

            /* ================ */
            /* Print Statements */
            /* ================ */
            System.out.println("Sent uninterested message to " + peerID);

            is_interested = false;
            return true;
        } catch (IOException e) {
            terminateSocketConnections();
            System.err.println("ERROR: Unable to send uninterested message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send uninterested message. ");
            return false;
        }
    }

    /**
     * Message: CHOKE
     */
    public boolean choke() {
        try {
            client2peer.write(choke);
            client2peer.flush();

            /* ================ */
            /* Print Statements */
            /* ================ */
            System.out.println("Sent choke message to " + peerID);

            peerChoking = true;
            return true;
        } catch (IOException e) {
            terminateSocketConnections();
            System.err.println("ERROR: Unable to send choke message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send choke message. ");
            return false;
        }
    }

    /**
     * Message: UNCHOKE
     */
    public boolean unchoke() {
        try {
            client2peer.write(unchoke);
            client2peer.flush();

            /* ================ */
            /* Print Statements */
            /* ================ */
            System.out.println("Sent unchoke message to " + peerID);

            peerChoking = false;
            return true;
        } catch (IOException e) {
            terminateSocketConnections();
            System.err.println("ERROR: Unable to send unchoke message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send choke message. ");
            return false;
        }
    }

    /**
     * Message: Have
     */
    public boolean have(int piece_index) {
        try {
            if (!peerSocket.isClosed()) {
                ByteBuffer hByteBuffer = ByteBuffer.allocate(9);
                hByteBuffer.put(new byte[]{0, 0, 0, 5, 4});
                hByteBuffer.putInt(piece_index);
                client2peer.write(hByteBuffer.array());
                client2peer.flush();

                /* ================ */
                /* Print Statements */
                /* ================ */
                System.out.println("Have message for piece " + piece_index + " sent to peer " + peerID);
                return true;
            } else {
                System.err.println("Socket closed at " + peerID);
                return false;
            }
        } catch (IOException e) {
            terminateSocketConnections();
            System.err.println("ERROR: Unable to send have message to " + peerID);
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to send have message to " + peerID);
            return false;
        }
    }

    //Note: working on this
    public boolean request(int position, int start, int size) {
        try {
            ByteBuffer rByteBuffer = ByteBuffer.allocate(17);
            rByteBuffer.put(new byte[]{0, 0, 0, 13, 6});
            rByteBuffer.putInt(position);
            rByteBuffer.putInt(start);
            rByteBuffer.putInt(size);
            client2peer.write(rByteBuffer.array());
            client2peer.flush();


            System.out.println("Sent request message. ");
            return true;
        } catch (IOException e) {
            terminateSocketConnections();
            System.err.println("ERROR: Unable to sent request message. ");
            return false;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to sent request message. ");
            return false;
        }
    }

    public void receiveBitfield(byte[] response) {
        peerbitfield = new BitSet();
        int index = 0;
        for (byte b : response) {
            int h = (int) b;
            for (int j = 7; j >= 0; j--, index++) {
                int shifted = h >> j;
                peerbitfield.set(index, ((shifted & 1) == 1));
            }
        }
        receiveBitfield = true;
    }

    public synchronized boolean piece(int index, int begin, byte[] block) {
        try {
            ByteBuffer piece_buffer = ByteBuffer.allocate(13 + block.length);
            piece_buffer.putInt(9 + block.length);
            piece_buffer.put((byte) 7);
            piece_buffer.putInt(index);
            piece_buffer.putInt(begin);
            piece_buffer.put(block);
            client2peer.write(piece_buffer.array());
            client2peer.flush();
            System.out.println("Piece message sent");
            return true;
        } catch (IOException ioe) {
            this.closeConnection();
            System.err.println(ioe.getMessage());
            System.err.println("COULD NOT SEND PIECE MESSAGE TO PEER!");
            return false;
        } catch (Exception ioe) {
            System.err.println(ioe.getMessage());
            System.err.println("COULD NOT SEND PIECE MESSAGE TO PEER!");
            return false;
        }
    }

    /* ================================================================================ */
    /* 									Set Methods										*/
    /* ================================================================================ */
    /**
     * METHOD: Create socket for peer with given IP and Port
     */
    public void setPeerConnection() {
        try {
            /**
             * Create socket
             */
            this.peerSocket = new Socket(this.peerIP, this.peerPort);
            if (peerSocket != null) {
                /**
                 * store from peer to client
                 */
                this.peer2client = new DataInputStream(this.peerSocket.getInputStream());

                /**
                 * store from client to peer
                 */
                this.client2peer = new DataOutputStream(this.peerSocket.getOutputStream());
                connected = true;
            } else {
                connected = false;
            }
        } catch (Exception e) {
            System.err.println("ERROR: Could not create socket connection with IP: " + peerIP + " and Port: " + peerPort);
        }
    }

    /**
     * METHOD: Sets this peers ID to the given value
     *
     * @param
     */
    public void setPeerID(byte[] newID) {
        peerID = newID;
    }

    /* ================================================================================ */
    /* 									Get Methods										*/
    /* ================================================================================ */
    public byte[] getPeerID() {
        return peerID;
    }

    public String getPeerIP() {
        return peerIP;
    }

    public int getPeerPort() {
        return peerPort;
    }

    /**
     * Method: get complete peer response
     */
    public byte[] getPeerResponse(int length) {
        try {
            byte[] peerResponse = new byte[length];
            peer2client.readFully(peerResponse);

            return peerResponse;
        } catch (IOException e) {
            terminateSocketConnections();
            System.err.println("ERROR: Unable to receive peer response. ");
            return null;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to receive peer response. ");
            return null;
        }
    }

    /**
     * Method: get byte response
     */
    public synchronized byte getPeerResponseByte() {
        try {
            return peer2client.readByte();
        } catch (IOException e) {
            terminateSocketConnections();
            System.err.println("ERROR: Unable to receive peer response. ");
            return -1;
        } catch (Exception e) {
            System.err.println("ERROR: Unable to receive peer response. ");
            return -1;
        }
    }

    /**
     * Method: get int response
     */
    public synchronized int getPeerResponseInt() {
        try {
            return peer2client.readInt();
        } catch (IOException e) {
            terminateSocketConnections();
            System.out.println("first " + e.toString());
            System.err.println("ERROR: Unable to receive peer response. ");
            return -1;
        } catch (Exception e) {
            System.out.println("second " + e.toString());
            System.err.println("ERROR: Unable to receive peer response. ");
            return -1;
        }
    }

    private void closeConnection() {
        try {
            peerSocket.close();
        } catch (IOException ex) {
            Logger.getLogger(Peer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /* ================================================================================ */
    /* 									Is Methods										*/
    /* ================================================================================ */
    public boolean isPeerConnected() {
        return connected;
    }

    /**
     * Safely closes this PeerController
     */
    public void suicide() {
        System.out.println("Peer " + peerID + " is committing suicide");
        this.closeConnection();
        am_alive = false;
    }
}
