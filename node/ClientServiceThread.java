package socs.network.node;
import java.net.*;
import socs.network.message.SOSPFPacket;
import socs.network.message.LSA;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Vector;
import java.io.IOException;


public class ClientServiceThread implements Runnable {
	Router router;
	String serverName;
	int port;
	SOSPFPacket packet;
	Link[] ports;
	
	ClientServiceThread(Router r, String serverName,int port,SOSPFPacket packet, Link[] ports) {
		this.router = r;
		this.serverName = serverName;
		this.port = port;
		this.packet = packet;
		this.ports = ports;
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
	
	
	
	
	@SuppressWarnings("resource")
	public void run() {
		try {
			ObjectOutputStream output = null;
			ObjectInputStream input = null;
			Socket client = null;
			//intended to connect to others first by sending "HELLO" message
			if(packet.sospfType == 0){
				//create a new client socket
				client = new Socket(serverName, port);
				output = new ObjectOutputStream(client.getOutputStream());
				//send "Hello" to server
				output.writeObject(packet);
				//waiting response from server
				input = new ObjectInputStream(client.getInputStream());
				//getting the response message from server
				SOSPFPacket response = (SOSPFPacket) input.readObject(); 
				//if server is at max capacity then delete server from ports
				if(response.sospfType == -1) {
					deleteNeighbors(response.neighborID);
					System.out.println("Server " + response.neighborID +" is at max capacity, Please try again later!");
					return;
				}
				
				
				//case1: receive a "Hello" message
				if(response.sospfType == 0) {
					//print to show receive respond "Hello" from server
					System.out.println("Received HELLO from " + response.neighborID);
					//set server status to "Two Way"
					setStatus(response.neighborID);
					//print to show set server status to Two Way
					System.out.println("Set " + response.neighborID + " state to TWO_WAY");
					//send another packet to server to show message "Hello" received 
					output.writeObject(packet);
					//send LSAUpdate packet to server after setting up two_way connection
					//the update is adding new link r1-r2 to client's lsa, so only need to send this change to server 
					SOSPFPacket newPacket = new SOSPFPacket();
					newPacket.sospfType = 1;
					newPacket.neighborID = router.rd.simulatedIPAddress;
					newPacket.srcIP = router.rd.simulatedIPAddress;
					newPacket.dstIP = response.neighborID;
					Vector<LSA> lsaVector = new Vector<LSA>();
					//add client's lsa(contains links r1-r1 and r1-r2 only) to packet
					lsaVector.add(router.lsd._store.get(router.rd.simulatedIPAddress));  
					newPacket.lsaArray = lsaVector;  //newPacket received by server will contain only one lsa element 
					output.writeObject(newPacket);    
					System.out.print(">> ");
				}
				//case 2 - type 1: LinkState Update, broadcasting LSAUpdate(contains all lsa inside client's lsd) to server
				//case 3 - type 2: send disconnect message to server
				//case 4 - type 3: send heartbeat message to server
			}else if (packet.sospfType == 1 || packet.sospfType == 2 || packet.sospfType == 3) { 
				client = new Socket(serverName, port);
				output = new ObjectOutputStream(client.getOutputStream());
				output.writeObject(packet);
			}
		}catch(IOException ex) {
			//System.out.println(ex);
			System.out.print(">> ");
		} catch (ClassNotFoundException ex) {
			System.out.println(ex);
		} catch (Exception ex) {
			return;
		}
	}
}
