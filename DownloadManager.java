package RUBTClient;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.io.*;
import java.io.ByteArrayOutputStream;
import java.util.Queue;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/*
 * This class performs the following steps:
 * 1) Reads in peers 
 * 2) Stores peer list
 * 
 * @author Alex DeOliveira  [126-00-8635]
 * @author Jason Baptista   [126-00-4630]
 * @author Elizabeth Sanz   [127-00-8628]
 * @version "project01"
 */

public class DownloadManager extends Thread {

    private Tracker tracker;
    private TorrentInfo torrent;
    private static ArrayList<Peer> peers = new ArrayList<Peer>();
    private RUBTClient client;
    private boolean stillRunning;
	private static Object lock = new Object();

    public DownloadManager(RUBTClient r, Tracker t) {
        client = r;
        torrent = r.getTorrentInfo();
        tracker = t;
        stillRunning = true;
    }

    /**
     * METHOD: Spins thread for Download Manager. 
     */
    public void run() {
		String delims = "[:]";

        for(String peerFullIP : tracker.getpeerIPList())
		{
			String[] ipParts = peerFullIP.split(delims);
			try
			{
				String newIP = ipParts[0];
				System.out.println("PeerIP: " + newIP);
				
				int newPort = Integer.parseInt(ipParts[1]);
				System.out.println("PeerPort: " + newPort);

				peers.add(new Peer(newIP, newPort, false, null));
				System.out.println("Peer has been added to List. ");
			}
			catch(Exception e)
			{
				System.err.println("ERROR: Could not create peer. ");
			}
		}
    }

    /* +++++++++++++++++++++++++++++++ GET METHODS +++++++++++++++++++++++++++++++++++ */
    /**
     * Method: Used to get the list of peers 
     * @return ArrayList of Peer objects 
     */
    public static ArrayList<Peer> getPeerList() {
        return peers;
    }

	   /**
     * METHOD: Take a piece of the file and save it into the clients pieces
     * array at the given index.
     *
     * @param piece A ByteArrayOutputStream containing the bytes of the piece.
     * @param index The index of the piece.
     */
    public static void savePiece(ByteArrayOutputStream piece, int index, Peer hasLock) {
        synchronized (hasLock) {
            RUBTClient.piecesDL[index] = piece;
            RUBTClient.intBitField[index] = 2; // has piece
            RUBTClient.Bitfield.set(index);
            saveDownloadState();
            //broadCastHas(index);
        }
        if (RUBTClient.Bitfield.cardinality() == RUBTClient.numPieces) {
            RUBTClient.writeFile();
            closePeers();
        }
    }

    /**
     * METHOD: Determine whether or not the piece of the file specified by index
     * has been downloaded or not
     *
     * @param index The index number of the piece.
     * @return True if the piece has been downloaded false if not.
     */
    public static synchronized boolean hasPiece(int index, Peer hasLock) {
        synchronized (hasLock) {
            boolean returnThis = RUBTClient.intBitField[index] == 0;
            if (returnThis) {
                RUBTClient.intBitField[index] = 1; // downloading piece
            }
            return returnThis;
        }
    }

    /**
     * METHOD: Writes the current state of the download to disk.
     */
    public synchronized static void saveDownloadState() {
        File f = new File(RUBTClient.torrentName + ".save");
        try {
            FileOutputStream fileOut = new FileOutputStream(f);
            ObjectOutputStream out = new ObjectOutputStream(fileOut);
            for (ByteArrayOutputStream b : RUBTClient.piecesDL) {
                if (b == null) {
                    out.writeObject(new byte[0]);
                } else {
                    out.writeObject(b.toByteArray());
                }
            }
            out.close();
            fileOut.close();
        } catch (IOException e) {
            System.err.println("Error: Problem occured saving the current state of the file.");
        }
    }

    /**
     * METHOD: Closes all peer connections then destroys the threads.
     */
    public static void closePeers() {
        ArrayList<Peer> list = getPeerList();
        for (int i = 0; i < list.size(); i++) {
            list.get(i).am_alive = false;
            list.get(i).terminateSocketConnections();
        }
    }

    /**
     * METHOD: Broadcasts to all peers that they have the piece once they have 
     *         completed downloading the piece
     * @param index is the index of the piece that has been downloaded
     */
    public static void broadCastHas(int index) {
        for (int i = 0; i < peers.size(); i++) {
            Messages.have(peers.get(i).peerSocket, peers.get(i).client2peer, peers.get(i).peer2client, index);
        }
    }
}
