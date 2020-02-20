package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import java.util.*;


public class LinkStateDatabase {
	//linkID => LSAInstance
	HashMap<String, LSA> _store = new HashMap<String, LSA>();
	private RouterDescription rd = null;
	
	public LinkStateDatabase(RouterDescription routerDescription) {
		rd = routerDescription;
		LSA l = initLinkStateDatabase();
		_store.put(l.linkStateID, l);
	}


        /** output the shortest path from this router to the destination with the given IP address*/
	String getShortestPath(String destinationIP) {
		String source = rd.simulatedIPAddress;
		if (source.equals(destinationIP)) {
			return "Path to itself is 0!";
		}else if (_store.get(source).links.size() == 1) {
			return "Disconnected from other nodes, couldn't reach destination!";
		}else if (_store.get(destinationIP).links.size() == 1) {
			return "Destination is disconnected and unreachable!";
		}else{			  
			Map<String, Integer> distance = new HashMap<String, Integer>();  //store Dijkstra's scores
			Map<String, String> path = new HashMap<String, String>();  //store visited edges
			Set<String> X = new HashSet<String>();  //store visited nodes
			Set<String> keys = new HashSet<String>();  //all connected nodes
			X.add(source);
			for (String s : _store.keySet()) {
				path.put(s, null);
				distance.put(s,  Integer.MAX_VALUE);
				if (_store.get(s).links.size() > 1) {
					keys.add(s);		  
				}		  
			}
			distance.put(source,  0);
			
			outer:  //while loop label for disconnected path case break
			while (!X.equals(keys)) {
				int minScore = Integer.MAX_VALUE;
				String minVertex = null;
				String tail = null;
				int count = 0;
				//among all edges u-v with u in X, v not in X, pick the one minimizes distance[u]+u-v length, let tail=u, minVertex=v
				for (String u : X) {  				       
					for (LinkDescription ld: _store.get(u).links) {
						if (!X.contains(ld.linkID)) {
							count++;
							int score = distance.get(u) + ld.tosMetrics;
							if (score < minScore) {
								minScore = score;
								minVertex = ld.linkID;
								tail = u;
							}
						}		    		  
					}		    	  
				}
				//if path is disconnected, break out of while loop
				if (count == 0) {
					break outer;  
				}
				X.add(minVertex);		      
				distance.put(minVertex, minScore);
				path.put(minVertex, tail);		  		  	  
			}
			
			StringBuilder sb = new StringBuilder();
			sb.append(destinationIP);
			String u = destinationIP;
			
			//infinity distance implies that destination is unreachable
			if (distance.get(u) == Integer.MAX_VALUE) {  
				return "Path is disconnected!";
			}
			
			while (path.get(u) != null) {
				String v = path.get(u);
				int length = 0;
				for (LinkDescription ld : _store.get(v).links) {
					if (ld.linkID.equals(u)) {
						length = ld.tosMetrics;
					}
				}
				sb.insert(0, " ->(" + length + ") ");
				sb.insert(0, v);
				u = v;		  
			}	
			return sb.toString();
		}
	}
  
  
        /** initialize the linkstate database by adding an entry about the router itself */
	private LSA initLinkStateDatabase() {
		LSA lsa = new LSA();
		lsa.linkStateID = rd.simulatedIPAddress;
		lsa.lsaSeqNumber = Integer.MIN_VALUE;
		LinkDescription ld = new LinkDescription();
		ld.linkID = rd.simulatedIPAddress;
		ld.portNum = -1;
		ld.tosMetrics = 0;
		lsa.links.add(ld);
		return lsa;
	}
	
	
	
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (LSA lsa: _store.values()) {
			sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
			for (LinkDescription ld : lsa.links) {
				sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
			        append(ld.tosMetrics).append("\t");
			}
			sb.append("\n");
		}
		return sb.toString();
	}
}
