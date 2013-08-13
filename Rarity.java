import java.util.*;

public class Rarity implements Comparable<Rarity>
{
	/*The piece number of the piece that this object is tracking*/
	private final int index;
	/*The number of peers that have the piece this object is tracking*/
	private int counter;
	/*The list peers which have the piece*/
	private ArrayList<Peer> peers;

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

	public Peer getRandomPeer()
	{
		if (counter == 0)
			return null;
		int ran = (int)(Math.random() * counter);
		counter--;
		return peers.remove(ran);
	}

	public int compareTo(Rarity other)
	{
		if(this.equals(other))
			return 0;
		return other.getCount() - this.getCount();
	}
	
	public boolean equals(Object o)
	{
		if(!(o instanceof Rarity))
			return false;
		Rarity other = (Rarity)o;
		if(this.getPieceNumber() == other.getPieceNumber())
			return true;
		return false;
	}

	public int getPieceNumber()
	{
		return index;
	}  

	public int getCount()
	{
		return counter;
	}

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