package socs.network.node;

import socs.network.util.Configuration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Timer;
import java.util.Vector;
import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;


public class Router {
    protected LinkStateDatabase lsd;
    RouterDescription rd = new RouterDescription();
    
    //assuming that all routers are with 4 ports
    Link[] ports = new Link[4];
    long[] heartBeats = new long[4];
    public static final int HEARTBEAT_TIME = 10000;
    public static final int HEARTBEAT_TIMEOUT = 10000; 
    
    public Router(Configuration config) {
    //get info from conf file
        rd.simulatedIPAddress = config.getString("socs.network.router.ip");
        System.out.println("My Ip address : " + rd.simulatedIPAddress);
        lsd = new LinkStateDatabase(rd);
        rd.processIPAddress = config.getString("socs.network.router.processIPAddress"); 
        System.out.println("My process Ip address :" + rd.processIPAddress);
        rd.processPortNumber = config.getShort("socs.network.router.processPortNumber");
        System.out.println("My process port number:" + rd.processPortNumber);
        
        Timer time = new Timer(); //Instantiate Timer object
        ScheduledTask st = new ScheduledTask(this, this.ports); //Instantiate ScheduledTask object    
        time.schedule(st, 0, HEARTBEAT_TIME); //Schedule task repetitively every HEARTBEAT_TIME
        
        # thread
        try {
            new Thread(new Serverthread(rd.processPortNumber, this, this.ports, this.heartBeats)).start(); //Start a server socket on the specified port
        }catch(Exception e) {
            e.printStackTrace();
        }
    }



  /** output the shortest path to the given destination ip
  format: source ip address  -> ip address -> ... -> destination ip
  @param destinationIP the ip adderss of the destination simulated router */
  private void processDetect(String destinationIP) {
      //System.out.println(lsd.getShortestPath(destinationIP));
      String path = lsd.getShortestPath(destinationIP);
      if (path != null) {
          System.out.println(path);
      }else {
          System.out.println("Shortest Path not exists!");
      }
  }

  

  /**delete neighbor from LSD*/
  public void deleteFromLSD(String deletedIP) {
      LSA lsa = this.lsd._store.get(this.rd.simulatedIPAddress);
      for (LinkDescription l : lsa.links) {
          if (l.linkID.equals(deletedIP)) {                
              lsa.links.remove(l);
              lsa.lsaSeqNumber++;
              break;
          }
      }
  }
  
  
  
  /** disconnect with the router identified by the given destination ip address
  Notice: this command should trigger the synchronization of database
  @param portNumber the port number which the link attaches at */
  private void processDisconnect(short portNumber) {
      if (ports[portNumber] != null) {
          if (ports[portNumber].router2.status != null) {
              String disconnectedIP = ports[portNumber].router2.simulatedIPAddress;
              //send disconnect message to server
              SOSPFPacket packet = new SOSPFPacket();
              packet.sospfType = 2; 
              packet.neighborID = rd.simulatedIPAddress;
              packet.srcProcessIP = rd.processIPAddress;
              packet.srcProcessPort = rd.processPortNumber;
              packet.srcIP = rd.simulatedIPAddress;
              packet.dstIP = disconnectedIP;    
              
              //start a new thread as a client, will LSAUpdate to server that will be connected 
              new Thread(new ClientServiceThread(this, serverName, portN, packet, ports)).start();
              
              //delete targeted neighbor from lsd
              deleteFromLSD(disconnectedIP);	   
              
              //broadcast LSAUpdate to all neighbors for synchronization
              LSAUpdate();
              
              //delete targeted neighbor from port
              ports[portNumber] = null
          }
      }
  }



  /** attach the link to the remote router, which is identified by the given simulated ip;
   to establish the connection via socket, you need to identify the process IP and process Port;
   additionally, weight is the cost to transmitting data through the link
   NOTE: this command should not trigger link database synchronization */
  private void processAttach(String processIP, short processPort,
                             String simulatedIP, short weight) {
      //check if all ports are occupied
      int count =0;
      
      //check if current router is trying to connect with itself
      if(this.rd.simulatedIPAddress.equals(simulatedIP)){
          System.out.println("Cannot self link, please try another address!");
          return;
      }
      
      for(int i = 0; i < 4; i++){
          if(ports[i] != null){
          count++;
          }
      }
      
      if(count == 4){
          System.out.println("All ports are occupied,please try again later!");
          return;
      }
      else{
          //check if this address is already attached
          for(int i = 0; i < 4; i++){
              if(ports[i]!=null){
                  if(ports[i].router2.simulatedIPAddress.equals(simulatedIP)){
                      System.out.println(ports[i].router2.simulatedIPAddress + " has already been attached!"
                      + " Please try another adress!");
                      return;
                  }
              }
          }
          
          //make attachment
          for(int i = 0; i < 4;i++){
              if(ports[i] == null){
                  //add new router description	
                  RouterDescription r2 = new RouterDescription();
                  r2.simulatedIPAddress = simulatedIP;
                  r2.processIPAddress = processIP;
                  r2.processPortNumber = processPort;
                  r2.status = null;
                  ports[i] = new Link(this.rd, r2, 0);
                  System.out.println("Attach completed with:" + r2.simulatedIPAddress);
                  count++;
                  
                  //assign heartbeat message send out time
                  heartBeats[i] = System.currentTimeMillis();
                  
                  //modify source router(client)'s lsd by adding new link
                  LSA lsa = lsd._store.get(rd.simulatedIPAddress);  //client's lsd contains links r1-r1
                  lsa.lsaSeqNumber++;
                  LinkDescription ld = new LinkDescription();
                  ld.linkID = simulatedIP;  //server's simulated IP
                  ld.portNum = i;  //client's port number
                  ld.tosMetrics = weight;  //client-server router edge weight
                  lsa.links.add(ld);  //add new link(r1-r2) to client's lsd         
                  break;
              }
          }
      }
  }
  
  
  
  /** broadcast Hello to neighbors */
  private void processStart() {
      //for each attached sever prepare to send hello and LSAUpdate packets
      for(int i = 0; i < ports.length; i++) {
          if (ports[i] != null) {
              if (ports[i].router2.status == null) {
                  String serverName = ports[i].router2.processIPAddress;
                  int portN = ports[i].router2.processPortNumber;
                  //prepare message packet
                  SOSPFPacket packet = new SOSPFPacket();
                  packet.sospfType = 0;
                  packet.neighborID = rd.simulatedIPAddress;
                  packet.srcProcessIP = rd.processIPAddress;
                  packet.srcProcessPort = rd.processPortNumber;
                  packet.srcIP = rd.simulatedIPAddress;
                  packet.dstIP = ports[i].router2.simulatedIPAddress;          
                  //start a new thread as a client, will individually LSAUpdate to each connected server   
                  new Thread(new ClientServiceThread(this, serverName, portN, packet, ports)).start();
              }
          }
      }
      
      //broadcast LSAUpdate to all neighbors for synchronization
      LSAUpdate();
  }
  
  
  
  
  /** broadcast LSAUpdate to all neighbors */  
  public void LSAUpdate() {	  
      for(int i = 0; i < ports.length; i++) {
          if (ports[i] != null) {
              String serverName = ports[i].router2.processIPAddress;
              int portN = ports[i].router2.processPortNumber;
              //prepare message packet
              SOSPFPacket packet = new SOSPFPacket();
              packet.sospfType = 1;
              packet.neighborID = rd.simulatedIPAddress;
              packet.srcProcessIP = rd.processIPAddress;
              packet.srcProcessPort = rd.processPortNumber;
              packet.srcIP = rd.simulatedIPAddress;
              packet.dstIP = ports[i].router2.simulatedIPAddress;
              Vector<LSA> lsaVector = new Vector<LSA>();  //create new vector for packing client's LSA
              for (LSA lsa : lsd._store.values()) {
                  lsaVector.add(lsa);  //load all LSA to vector   
              }
              packet.lsaArray = lsaVector;  //now the packet carries client's all LSA
              //start a new thread as a client
              new Thread(new ClientServiceThread(this, serverName, portN, packet, ports)).start();
          }
      }
  } 
  
  
  
  /** broadcast LSAUpdate to all neighbors except the one passed in as argument */
  public void LSAUpdate(String neighbor) {	  
      for(int i = 0; i < ports.length; i++) {
          if (ports[i] != null && !(ports[i].router2.simulatedIPAddress.equals(neighbor))) {
              String serverName = ports[i].router2.processIPAddress;
              int portN = ports[i].router2.processPortNumber;
              //prepare message packet
              SOSPFPacket packet = new SOSPFPacket();
              packet.sospfType = 1;
              packet.neighborID = rd.simulatedIPAddress;
              packet.srcProcessIP = rd.processIPAddress;
              packet.srcProcessPort = rd.processPortNumber;
              packet.srcIP = rd.simulatedIPAddress;
              packet.dstIP = ports[i].router2.simulatedIPAddress;	          
              Vector<LSA> lsaVector = new Vector<LSA>();  //create new vector for packing client's LSA
              for (LSA lsa : lsd._store.values()) {
                  lsaVector.add(lsa);  //load all LSA to vector   
              }
              //now the packet carries info of client's all LSA
              packet.lsaArray = lsaVector;   
              //start a new thread as a client
              new Thread(new ClientServiceThread(this, serverName, portN, packet, ports)).start();
          }
      }
  } 
  
  
  
  
  /** attach the link to the remote router, which is identified by the given simulated ip;
   to establish the connection via socket, you need to indentify the process IP and process Port;
   additionally, weight is the cost to transmitting data through the link.
   This command does trigger the link database synchronization */
   private void processConnect(String processIP, short processPort,
                              String simulatedIP, short weight) {
       processAttach(processIP, processPort, simulatedIP, weight);
       processStart();
   }
   
   
   
   /** output the neighbors of the router */
   private void processNeighbors() {
       System.out.println("Neighbors of " + this.rd.simulatedIPAddress + ":");
       //For current router print neighbours which are r2 in all links
       for (Link l : this.ports) {
           if (l == null) continue;
           if (l.router2.status == RouterStatus.TWO_WAY) {
               //print ip address and port number of r2
               System.out.println("IP Address: " + l.router2.simulatedIPAddress + " PortNum: " + l.router2.processPortNumber);
           }
       }
   }
   
   
   
   /** disconnect with all neighbors and quit the program */
   private void processQuit() {
       System.out.println("Process Quit!");
       System.exit(0);
   }
   
   
   
   public void terminal() {
       try {
           InputStreamReader isReader = new InputStreamReader(System.in);
           BufferedReader br = new BufferedReader(isReader);
           System.out.print(">> ");
           String command = br.readLine();
           while (true) {
               if (command.startsWith("detect ")) {
                   String[] cmdLine = command.split(" ");
                   processDetect(cmdLine[1]);
               } else if (command.startsWith("disconnect ")) {
                   String[] cmdLine = command.split(" ");
                   processDisconnect(Short.parseShort(cmdLine[1]));
               } else if (command.startsWith("quit")) {
                   processQuit();
               } else if (command.startsWith("attach ")) {
                   String[] cmdLine = command.split(" ");
                   processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                   cmdLine[3], Short.parseShort(cmdLine[4]));
               } else if (command.equals("start")) {
                   processStart();
               } else if (command.startsWith("connect ")) {
                   String[] cmdLine = command.split(" ");
                   processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                   cmdLine[3], Short.parseShort(cmdLine[4]));
               } else if (command.equals("neighbors")) {
                   //output neighbors
                   processNeighbors();
               } else {
                   //invalid command
                   System.out.print("invalid command \n");
                   //System.out.print(">> ");
                   break;
               }
               System.out.print(">> ");
               command = br.readLine();
           }
           
           isReader.close();
           br.close();
       } catch (Exception e) {
           e.printStackTrace();
       }
   }
}
