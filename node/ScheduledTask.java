package socs.network.node;
import java.util.TimerTask;
import socs.network.message.SOSPFPacket;


public class ScheduledTask extends TimerTask{	
	Router router;
	Link[] ports;
	
	ScheduledTask(Router r, Link[] ports){
       	this.router = r;
        this.ports = ports;
	}
    
    
    //write the task here
	public void run() {
        //if TIMEOUT, remove neighbor from ports and lsd, then broadcast LSAUpdate for synchronization 
	    for(int i = 0; i < ports.length; i++) {
			if (ports[i] != null) {
				if (System.currentTimeMillis() - router.heartBeats[i] > Router.HEARTBEAT_TIMEOUT) {
					router.deleteFromLSD(ports[i].router2.simulatedIPAddress);
					router.LSAUpdate();
					ports[i] = null;
				}
			}
	    } 
		
	    //send heartbeat message to each connected server
	    for(int i = 0; i < ports.length; i++) {
	        if (ports[i] != null) {     	
				String serverName = ports[i].router2.processIPAddress;
	            int portN = ports[i].router2.processPortNumber;
	            //prepare heartbeat message packet
	            SOSPFPacket packet = new SOSPFPacket();
	            packet.sospfType = 3;
	            packet.neighborID = router.rd.simulatedIPAddress;
	            packet.srcProcessIP = router.rd.processIPAddress;
	            packet.srcProcessPort = router.rd.processPortNumber;
	            packet.srcIP = router.rd.simulatedIPAddress;
	            packet.dstIP = ports[i].router2.simulatedIPAddress;          
	            //start a new thread as a client, will individually send heartbeat message to each connected server   
	            new Thread(new ClientServiceThread(router, serverName, portN, packet, ports)).start();
	         }	        
	    }   
	}
}
