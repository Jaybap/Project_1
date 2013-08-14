
import java.util.*;


/**
 * Rarity Class
 * 
 * @author Alex DeOliveira [126-00-8635]
 * @author Jason Baptista [126-00-4630]
 * @author Elizabeth Sanz [127-00-8628]
 * @version "project02"
 */

public class Rarity implements Comparable<Rarity>
{
	/*The piece number of the piece that this object is tracking*/
	private final int index;
	/*The number of peers that have the piece this object is tracking*/
	private int counter;
	/*The list peers which have the piece*/
	private ArrayList<Peer> peers;

        
    /* ================================================================================ */
    /* Rarity Constructor								*/
    /* ================================================================================ */
	public Rarity(int index)
	{
		this.index = index;
		peers = new ArrayList<Peer>();
	}
       
	public Rarity(int index, Peer peer)
	{
		this.index = index;
		peers = new ArrayList<Peer>();
		add(peer);
	}

	public Rarity(int index, ArrayList<Peer> addPeers)
	{
		this.index = index;
		peers = new ArrayList<Peer>();
		add(addPeers);
	}

        /* ================================================================================ */
        /* Methods              							    */
        /* ================================================================================ */

       /**
        * METHOD: gets a random peer
        */
        public Peer getRandomPeer()
	{
		if (counter == 0)
			return null;
		int ran = (int)(Math.random() * counter);
		counter--;
		return peers.remove(ran);
	}

        /**
        * METHOD: compares Rarity object
        *
        * @param other rarity object 
        * @return integer
        */
	public int compareTo(Rarity other)
	{
		if(this.equals(other))
			return 0;
		return other.getCount() - this.getCount();
	}
	
        /**
        * METHOD: compares piece number 
        *
        * @param index The index number of the piece.
        * @return boolean, true if piece number are the same, false otherwise
        */
	public boolean equals(Object o)
	{
		if(!(o instanceof Rarity))
			return false;
		Rarity other = (Rarity)o;
		if(this.getPieceNumber() == other.getPieceNumber())
			return true;
		return false;
	}

        /**
         * Method: returns the piece index number  
         */
	public int getPieceNumber()
	{
		return index;
	}  

        /**
         * Method: returns the counter
         */
	public int getCount()
	{
		return counter;
	}

        /**
         * Method: adds a peer to the peer list based on conditions
         * @param peer a peer object
         * @return boolean, true if peer is not null, is connected, and has piece
         *         false otherwise
         */
	public boolean add(Peer peer)
	{
		if(peer != null && peer.am_alive && peer.getBitField().get(index))
		{
			peers.add(peer);
			counter++;
			return true;
		}
		return false;
	}

        /**
         * Method: rebuilds the peer list
         * @param peers an array list of Peer
         * @return boolean, true if any peers are added, false otherwise
         */
	public boolean add(ArrayList<Peer> peers)
	{
		boolean added = false;
		for(Peer p: peers)
			if(add(p))
				added = true;
		return added;
	}

	// public boolean remove(Peer peer)
	// {
		// if(peer != null && peer.getBitField().get(index))
		// {
			// counter--;
			// return true;
		// }
		// return false;
	// }

	// public boolean remove(ArrayList<Peer> peers)
	// {
		// boolean removed = false;
		// for(Peer p: peers)
			// if(remove(p))
				// removed = true;
		// return removed;
	// }

	public String toString()
	{
		return "Tracking piece " + index + " which is in " + peers.size() + "peers";
	}
}
