import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;

/**
 * Optimistic_Unchoker Class Main Functions: 
 * 1) Choke all but 3 peers based on download rate
 * 
 * @author Alex DeOliveira [126-00-8635]
 * @author Jason Baptista [126-00-4630]
 * @author Elizabeth Sanz [127-00-8628]
 * @version "project02"
 */
public class Optimistic_Unchoker extends Thread {

    /* Global variable */
    private boolean am_alive = false;

    
    public Optimistic_Unchoker() {
        this.am_alive = true;
        this.start();
    }

    public void run() {
        while (am_alive) {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException ex) {
                Logger.getLogger(Optimistic_Unchoker.class.getName()).log(Level.SEVERE, null, ex);
            }

            int index;
            if (RUBTClient.uploadconnections[0].upload_rate < RUBTClient.uploadconnections[1].upload_rate 
                    && RUBTClient.uploadconnections[1].upload_rate < RUBTClient.uploadconnections[2].upload_rate) {
                index = 0;
                RUBTClient.uploadconnections[index].sendchoke(RUBTClient.uploadconnections[index].peersocket,RUBTClient.uploadconnections[index].topeer,RUBTClient.uploadconnections[index].frompeer);
                RUBTClient.uploadconnections[index] = null;
            } 
            else if (RUBTClient.uploadconnections[0].upload_rate>RUBTClient.uploadconnections[1].upload_rate 
                    && RUBTClient.uploadconnections[1].upload_rate < RUBTClient.uploadconnections[2].upload_rate) {
                index = 1;
                RUBTClient.uploadconnections[index].sendchoke(RUBTClient.uploadconnections[index].peersocket,RUBTClient.uploadconnections[index].topeer,RUBTClient.uploadconnections[index].frompeer);
                RUBTClient.uploadconnections[index] = null;
            } 
            else {
                index = 2;
                RUBTClient.uploadconnections[index].sendchoke(RUBTClient.uploadconnections[index].peersocket,RUBTClient.uploadconnections[index].topeer,RUBTClient.uploadconnections[index].frompeer);
                RUBTClient.uploadconnections[index] = null;
            }

            Random randomGenerator = new Random();
            int peertoadd = randomGenerator.nextInt(RUBTClient.choked_peers.size());
            RUBTClient.uploadconnections[index] = RUBTClient.choked_peers.get(peertoadd);
            RUBTClient.uploadconnections[index].sendunchoke(RUBTClient.uploadconnections[index].peersocket,RUBTClient.uploadconnections[index].topeer,RUBTClient.uploadconnections[index].frompeer);
        }
    }
}
