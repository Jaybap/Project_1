import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;

/**
 *
 * @author jaybap
 */
public class Optimistic_Unchoker extends Thread {

    private boolean am_alive = false;

    public Optimistic_Unchoker() {
        this.am_alive = true;
        this.start;
    }

    public void run() {
        while (am_alive) {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException ex) {
                Logger.getLogger(Optimistic_Unchoker.class.getName()).log(Level.SEVERE, null, ex);
            }


            int index;
            if (RUBTClient.uploadconnections[0].upload_rate < RUBTClient.uploadconnections[1].upload_rate && RUBTClient.uploadconnections[1].upload_rate < RUBTClient.uploadconnections[2].upload_rate) {
                index = 0;
                RUBTClient.uploadconnections[index].sendchoke(RUBTClient.uploadconnections[index].peersocket,RUBTClient.uploadconnections[index].topeer,RUBTClient.uploadconnections[index].frompeer);
                RUBTClient.uploadconnections[index] = null;
                RUBTClient.choked_peers.add(RUBTClient.uploadconnections[index]);
            } else if (RUBTClient.uploadconnections[0].upload_rate>RUBTClient.uploadconnections[1].upload_rate && RUBTClient.uploadconnections[1].upload_rate < RUBTClient.uploadconnections[2].upload_rate) {
                index = 1;
                RUBTClient.uploadconnections[index].sendchoke(RUBTClient.uploadconnections[index].peersocket,RUBTClient.uploadconnections[index].topeer,RUBTClient.uploadconnections[index].frompeer);
                RUBTClient.uploadconnections[index] = null;
                RUBTClient.choked_peers.add(RUBTClient.uploadconnections[index]);
            } else {
                index = 2;
                RUBTClient.uploadconnections[index].sendchoke(RUBTClient.uploadconnections[index].peersocket,RUBTClient.uploadconnections[index].topeer,RUBTClient.uploadconnections[index].frompeer);
                RUBTClient.uploadconnections[index] = null;
                RUBTClient.choked_peers.add(RUBTClient.uploadconnections[index]);
            }

            int peertoadd = randomGenerator.nextInt(RUBTClient.chokedpeers.size());
            RUBTClient.uploadconnections[index] = RUBTClient.chokedpeers.get(peertoadd);
            RUBTClient.uploadconnections[index].sendunchoke(RUBTClient.uploadconnections[index].peersocket,RUBTClient.uploadconnections[index].topeer,RUBTClient.uploadconnections[index].frompeer);

        }
    }
}
