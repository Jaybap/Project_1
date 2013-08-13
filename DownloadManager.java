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

	private PriorityQueue<Rarity> rarelist;
    private Tracker tracker;
    private TorrentInfo torrent;
    private static ArrayList<Peer> peers = new ArrayList<Peer>();
    private RUBTClient client;
	private static String output = "";
    private boolean stillRunning;

    public DownloadManager(RUBTClient r, Tracker t) {
		rarelist = new PriorityQueue<Rarity>();
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
		int id = 1;

        for(String peerFullIP : tracker.getpeerIPList())
		{
			String[] ipParts = peerFullIP.split(delims);
			String newIP = ipParts[0];
			// make sure it is one of the setup peers
			if (newIP.matches("128\\.6\\.171\\.[4568]"))//add back in 3 and 7 when the hosts have been brought back up
			{
				try
				{
					output += "\n"+("PeerIP: " + newIP);
					
					int newPort = Integer.parseInt(ipParts[1]);
					output += "\n"+("PeerPort: " + newPort);

					peers.add(new Peer(newIP, newPort, false, null, id++));
					output += "\n"+("Peer has been added to List. ");
				}
				catch(Exception e)
				{
					System.err.println("ERROR: Could not create peer. ");
				}
			}
		}

		ArrayList<Integer> temp = new ArrayList<Integer>();
		for(int i = 0; i < peers.size(); i++)
			temp.add(new Integer(i));

		// make sure all peer threads have reached the point of having a bitfield message before allocating them pieces to request
		while (temp.size() != 0)
		{
			try
			{
				Thread.sleep(2000);
			}
			catch(InterruptedException e)
			{
				System.err.println("The DownloadMananger is having trouble sleeping");
			}
			for (int i = 0; i < temp.size(); i++)
			{
				Peer p = peers.get(temp.get(i));
				if (p.getState() == Thread.State.TIMED_WAITING)
				{
					temp.remove(i);
					i--;
				}
			}
		}

		for(int i = 0; i < RUBTClient.numPieces; i++)
		{
			Rarity newRare = new Rarity(i, peers);
			rarelist.add(newRare);
			//output += "\n"+(newRare.toString());
		}
	
		while(rarelist.size() != 0)
		{
			output += "\n"+("Size of rarelist : " + rarelist.size());
			for(int i = 0; i < rarelist.size(); i++)
			{
				Rarity currentRarity = rarelist.poll();
				Peer requestFrom = currentRarity.getRandomPeer();
				output += "\nPoped off rare tracking " + ((currentRarity == null) ? "null":currentRarity.getPieceNumber()) + " and removed peer " + ((requestFrom == null) ? "null":requestFrom.ThreadID) + "\n";
				if (requestFrom == null) // list is empty in the rarity need to double check if we have already downloaded the piece
				{
					output += "\n"+("There are no peers left in rare object tracking piece : " + currentRarity.getPieceNumber());
					//rarelist.add(currentRarity);
					continue;
				}
				if (hasPiece(currentRarity.getPieceNumber()) == 2 || client.Bitfield.get(currentRarity.getPieceNumber())) // if the piece is already downloaded do not readd the rarity just skip to next iteration
				{
					output += "\n"+("Skipping piece " + currentRarity.getPieceNumber() + " we already have it");
					rarelist.remove();
					continue;
				}
				else if (hasPiece(currentRarity.getPieceNumber()) == 1)
				{
					output += "\n"+("Re adding current peer to current rarity because the piece may be downloading by another peer");
					currentRarity.add(requestFrom);
					rarelist.add(currentRarity);
					continue;
				}
				else
					client.intBitField[currentRarity.getPieceNumber()] = 1;
				requestFrom.addPieceToDownloadList(currentRarity.getPieceNumber());
				rarelist.add(currentRarity);
				
				requestFrom.setStart(true);
				if (requestFrom.getState() == Thread.State.TIMED_WAITING)
				{
					requestFrom.interrupt();
				}
			}
			try
			{
				output += "\n"+("Second sleep\n\ncurrent state of the intbitfield : ");
				Thread.sleep(1000);
				for(int i : RUBTClient.intBitField)
					output += i+", ";
				output += "\n\n";
			}
			catch(InterruptedException e)
			{
				System.err.println("There was a problem sleeping the download manager");
			}
		}
		output += "\n"+("Download Manager has ended its run sequence");
    }

    /* +++++++++++++++++++++++++++++++ GET METHODS +++++++++++++++++++++++++++++++++++ */
    /**
     * Method: Used to get the list of peers 
     * @return ArrayList of Peer objects 
     */
    public static ArrayList<Peer> getPeerList()
	{
        return peers;
    }

	   /**
     * METHOD: Take a piece of the file and save it into the clients pieces
     * array at the given index.
     *
     * @param piece A ByteArrayOutputStream containing the bytes of the piece.
     * @param index The index of the piece.
     */
    public synchronized static void savePiece(ByteArrayOutputStream piece, int index, Peer hasLock) // remove hasLock its not needed used atm for debugging
	{
		RUBTClient.piecesDL[index] = piece;
		RUBTClient.intBitField[index] = 2; // has piece
		RUBTClient.Bitfield.set(index);
		RUBTClient.updateBytesDownloaded(piece.size());
		output += "\n"+(RUBTClient.bytesDownloaded + "/" + RUBTClient.torrent.file_length);
		saveDownloadState();
		outputtofile(true);
		//broadCastHas(index);
        if (RUBTClient.Bitfield.cardinality() == RUBTClient.numPieces)
		{
            RUBTClient.writeFile();
            closePeers();
        }
    }

    /**
     * METHOD: Determine whether or not the piece of the file specified by index
     * has been downloaded or not
     *
     * @param index The index number of the piece.
     * @return 2 if the piece has been downloaded 1 if its being downloaded and 0 if its not been touched.
     */
    public static synchronized int hasPiece(int index) // remove hasLock its not needed used atm for debugging
	{
		return RUBTClient.intBitField[index];
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

	// used for debugging
	public synchronized static void outputtofile(boolean fromPeer)
	{
		if (fromPeer)
			for(int i = 0; i < peers.size(); i++)
			{
				File f = new File("peer" + i + ".log");
				try {
					FileOutputStream fileOut = new FileOutputStream(f);
					ObjectOutputStream out = new ObjectOutputStream(fileOut);
					if (peers.get(i).output.equals(""))
						continue;
					else
						out.writeObject(peers.get(i).output);
					out.close();
					fileOut.close();
				} catch (IOException e) {
					System.err.println("Error: Problem occured saving the current state of the file.");
				}
			}
		else
		{
			File f = new File("downloadManager.log");
			try {
				FileOutputStream fileOut = new FileOutputStream(f);
				ObjectOutputStream out = new ObjectOutputStream(fileOut);
				out.writeObject(output);
				out.close();
				fileOut.close();
			} catch (IOException e) {
				System.err.println("Error: Problem occured saving the current state of the file.");
			}
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
