package socs.network.node;
import java.net.*;
import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Vector;
import java.io.IOException;



public class ServerServiceThread implements Runnable {
    Socket server;
    Router router;
    Link[] ports;
    long[] heartBeats;
    
    ServerServiceThread(Socket s, Router r, Link[] ports, long[] hb) {
        this.server = s;
        this.router = r;
        this.ports = ports;
        this.heartBeats = hb;
    }
    
    
    //check if already neighbor
    public boolean isNeighbor(String simulatedIPAddress) { 
        for (int i = 0; i < ports.length; i++) {
            if (ports[i] != null) {
                if (ports[i].router2.simulatedIPAddress.equals(simulatedIPAddress)) return true;
            }
        }
        return false;
    }
    
    
    
    //get neighbor status
    public RouterStatus getStatus(String simulatedIPAddress) { 
        for (int i = 0; i < ports.length; i++) {
            if (ports[i] != null) {
                if (ports[i].router2.simulatedIPAddress.equals(simulatedIPAddress)) {
                	return ports[i].router2.status;
                }
            }
        }
        return null;
    }
    
    
    //set neighbor status to two-way
    public void setStatus(String simulatedIPAddress) {
        for (int i = 0; i < ports.length; i++) {
            if (ports[i] != null) {
                if (ports[i].router2.simulatedIPAddress.equals(simulatedIPAddress)) {
                	ports[i].router2.status = RouterStatus.TWO_WAY;
                	break;
                }
            }
        }
    }
   
    
    
    //get port number that connected by neighbor
    public int getPort(String neighbor) {
    	int portN = -1;
    	for (int i = 0; i < ports.length; i++) {
    		if (ports[i] != null) {
    			if (ports[i].router2.simulatedIPAddress.equals(neighbor)) {
    				portN = i;
    				break;
    			}
    		}
    	}
    	return portN;
    }
    
    
    
    //delete neighbors from ports
    public void deleteNeighbors(String simulatedIPAddress) {
        for (int i = 0; i < ports.length; i++) {
            if (ports[i] != null) {
                if (ports[i].router2.simulatedIPAddress.equals(simulatedIPAddress)) {
                	ports[i] = null;
                	break;
                }
            }
        }
    }    
    
    
    
    //delete neighbor from LSD
    public void deleteFromLSD(String deletedIP) {
        LSA lsa = router.lsd._store.get(router.rd.simulatedIPAddress);
        for (LinkDescription l : lsa.links) {
            if (l.linkID.equals(deletedIP)) {                
                lsa.links.remove(l);
                lsa.lsaSeqNumber++;
                break;
            }
        }
    }
    
    
    
   //assign heartbeat message received time
    public void receivedHeartbeatTime(String simulatedIPAddress) {
    	for (int i = 0; i < ports.length; i++) {
    		if (ports[i] != null) {
    			if (ports[i].router2.simulatedIPAddress.equals(simulatedIPAddress)) {
    				heartBeats[i] = System.currentTimeMillis();
    				break;
    			}
    		}
    	}    	
    }

    
    
    
    public void run() {
        try {
            //output message to client
            ObjectOutputStream output = new ObjectOutputStream(server.getOutputStream());
            //Received message from client
            ObjectInputStream input = new ObjectInputStream(server.getInputStream());
            
            //message store the message passed from client
            SOSPFPacket message = (SOSPFPacket) input.readObject();
            //if router receive an HELLO message
            if(message.sospfType == 0){
                //print out IP address of the neighbor who send Hello
                System.out.println("Received HELLO from " + message.neighborID);
                //if all ports are not available then don't connect
                int count = 0;
                for (int i = 0; i < 4; i++) {
                    if (ports[i] != null) {
                        count++;
                    }
                }
                
                if(count == 4) {
                    System.out.println(router.rd.simulatedIPAddress + ": is at max capacity!");
                    System.out.print(">> ");
                    //store response in a packet
                    SOSPFPacket response = new SOSPFPacket();
                    // respond back a "Hello" message
                    response.sospfType = -1; 
                    // give self simulatedIPAddress as other's neighbor
                    response.neighborID = router.rd.simulatedIPAddress;
                    response.srcProcessIP = router.rd.processIPAddress;
                    // give the port number for connection
                    response.srcProcessPort = router.rd.processPortNumber; 
                    //write output
                    output.writeObject(response); 
                    return;
                }                    
                // message came from non-neighbor router then update
                if(!isNeighbor(message.neighborID)){
                    for (int i = 0; i < 4; i++) {
                        if (ports[i] == null) {
                            RouterDescription remoterd=new RouterDescription(); 
                            //Create a new router description for the destination
                            remoterd.simulatedIPAddress= message.neighborID;
                            remoterd.processIPAddress=message.srcProcessIP;
                            remoterd.processPortNumber=message.srcProcessPort;
                            //Initialize the values for the router description
                            remoterd.status=RouterStatus.INIT;
                            System.out.println("Set " + message.neighborID + " state to INIT");
                            short w =0;
                            ports[i]=new Link(router.rd,remoterd,w);
                            //assign heartbeat message received time
                            int portN = getPort(message.neighborID);
                            heartBeats[portN] = System.currentTimeMillis();
                            break;
                        }
                    }
                }
                //store response in a packet
                SOSPFPacket response = new SOSPFPacket();
                // respond back a "Hello" message
                response.sospfType = 0; 
                // give self simulatedIPAddress as other's neighbor
                response.neighborID = router.rd.simulatedIPAddress;
                response.srcProcessIP = router.rd.processIPAddress;
                // give the port number for connection
                response.srcProcessPort = router.rd.processPortNumber; 
                //write output
                output.writeObject(response);     
                
                //get another message
                message = (SOSPFPacket) input.readObject(); 
                if (message.sospfType == 0) {
                    System.out.println("Received HELLO from " + message.neighborID);
                    //received second message set neighbor to two-way if already initialized
                    if (getStatus(message.neighborID) == RouterStatus.INIT) { 
                        setStatus(message.neighborID);
                        System.out.println("Set " + message.neighborID + " state to TWO_WAY");
                    }                        
                    
                    //received LSAPdate message from client to update server's lsd
                    message = (SOSPFPacket) input.readObject();
                    if (message.sospfType == 1) {  //connection setup changed only server's lsa by adding client-server link
                        //only need to get server's lsa and update it, no change in other lsa
                        LSA lsa = router.lsd._store.get(router.rd.simulatedIPAddress);   
                        lsa.lsaSeqNumber++;
                        LinkDescription ld = new LinkDescription();
                        ld.linkID = message.neighborID;
                        ld.portNum = getPort(message.neighborID);
                        int weight = -1;
                        //get the message sent by client
                        LSA receivedLSA = message.lsaArray.elementAt(0);  
                        //retrieve corresponding link weight from received packet
                        for (LinkDescription l : receivedLSA.links) {  
                            if (l.linkID.equals(router.rd.simulatedIPAddress)) {
                                weight = l.tosMetrics;
                                break;
                            }
                        }
                        ld.tosMetrics = weight;
                        lsa.links.add(ld);  //add new link 
                        router.lsd._store.put(router.rd.simulatedIPAddress, lsa);  //update server's lsa                         	                        
                        router.LSAUpdate();  //broadcast update to all neighbors                        	
                    }
                    System.out.print(">> ");
                }
                //if it is a LSAPdate message
            }else if (message.sospfType == 1) {
                Vector<LSA> lsaArray = message.lsaArray;
                for (LSA lsa : lsaArray) {
                    //if client hasn't connected to this server before then add this lsa to server's lsd
                    if (!router.lsd._store.containsKey(lsa.linkStateID)) {  
                        router.lsd._store.put(lsa.linkStateID, lsa); 
                        //server broadcasts lsd update to all neighbors except the newly connected client  
                        router.LSAUpdate(message.srcIP);  
                    }else { //if client already connected to this server
                        //if the lsa is the latest one update the corresponding lsa
                        if (router.lsd._store.get(lsa.linkStateID).lsaSeqNumber < lsa.lsaSeqNumber) {   
                            router.lsd._store.put(lsa.linkStateID, lsa);  
                            //server broadcasts lsd update to all neighbors except the newly connected client
                            router.LSAUpdate(message.srcIP);  
                        }
                    }
                }
                //if it is a Disconnect message, delete neighbor form lsd and ports, then LSAPdate message
            }else if (message.sospfType == 2) {
                //delete neighbor from lsd
                deleteFromLSD(message.neighborID);  
                //broadcast LSAUpdate
                router.LSAUpdate();
                //delete neighbor from ports
                deleteNeighbors(message.neighborID);
            }else if (message.sospfType == 3) {
                //System.out.println("I got a heartbeat message");
                receivedHeartbeatTime(message.neighborID);
            }                          
        }catch(IOException ex) {
            System.out.println(ex);
        } catch (ClassNotFoundException ex) {
            System.out.println(ex);
        } catch (Exception ex) {
            return;
        }

    }
}
