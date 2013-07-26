
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * STEPS: 1) Perform handshake 2) Send interested message 3) Wait for unchoke 4)
 * Unchoke, start sequentially requesting blocks of data 5) When a piece is
 * complete, verify it sends a "have message" and writes to "destination" File
 *
 */
public class DownloadManager extends Thread {

    private Tracker tracker;
    private TorrentInfo torrent;
    private ArrayList<Peer> peerList;
    private RUBTClient client;
    private boolean stillRunning;

    public DownloadManager(RUBTClient r, Tracker t) {
        client = r;
        torrent = r.getTorrentInfo();
        tracker = t;
        peerList = t.getPeerList();
        stillRunning = true;
    }

    /**
     * METHOD: Running the program
     */
    public void run() {
        /* Extracts peer needed */
        Peer peer = peerList.get(0);

        /* Set up connection with Peer */
        peer.setPeerConnection();

        /* Establish handshake */
        peer.sendHandshake(client.getPeerId(), torrent.info_hash);
        System.out.println("Handshake sent");

        /* Receive and verify handshake */
        if (!peer.verifyHandshake(torrent.info_hash)) {
            System.err.println("ERROR: Unable to verify handshake. ");
        } else {
            int len = peer.getPeerResponseInt();
            System.out.println(len);
            byte message = peer.getPeerResponseByte();
            System.out.println(message);
            byte[] peerbits = peer.getPeerResponse(len - 1);
            peer.receiveBitfield(peerbits);
            System.out.println(peer.peerbitfield.toString());
            peer.interested();
            int numBlks=client.numBlkPieceRatio;
            System.out.println("Original # of blocks "+ numBlks);
			int total = 0;
            for (int i = 0; i < client.numPieces; i++) {
                if (client.Bitfield.get(i) != peer.peerbitfield.get(i) && !client.Bitfield.get(i) && peer.peerbitfield.get(i)) {
					System.out.println("Request Piece " + i);
                    if (i==client.numPieces-1){
                        numBlks=(int)Math.ceil((double)client.lastPieceSize/(double)client.blockLength);
                        System.out.println("Blocks for last piece "+numBlks);
                    }
                    for (int j = 0; j < numBlks; j++) {
						System.out.println("Request Block " + j);
                        if (j == numBlks-1){
                            /*!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!*/
							/* this calculation is wrong and must be redone in order to get a proper last block size of 4253*/
							if (i == client.numPieces - 1)
								peer.request(i, j*client.blockLength, client.lastBlkSize);
							else
								peer.request(i, j*client.blockLength, torrent.piece_length-(j*client.blockLength));
							/*!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!*/
                        }
                        else peer.request(i, j*client.blockLength, client.blockLength);
                        int length = peer.getPeerResponseInt();
                        //System.out.println(length - 9);
                        byte[] block = new byte[length - 9];
                        System.arraycopy(peer.getPeerResponse(length), 9, block, 0, length - 9);
                        try {
                            client.piecesDL[i].write(block);
							total += block.length;
							System.out.println(total+"/"+torrent.file_length);
                        } catch (IOException ex) {
                            Logger.getLogger(DownloadManager.class.getName()).log(Level.SEVERE, null, ex);
                        }

                    }
                }
            }
			client.writeFile();
        }
    }

    /* +++++++++++++++++++++++++++++++ GET METHODS +++++++++++++++++++++++++++++++++++ */
    ArrayList<Peer> getPeerList() {
        return peerList;
    }
}
