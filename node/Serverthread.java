package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.SOSPFPacket;
import java.net.*;
import java.io.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLSyntaxErrorException;
import java.util.Vector;
public class Serverthread implements Runnable{
    private ServerSocket serverSocket;
    Router router;
    Link[] ports;
    long[] heartBeats;
    
    public Serverthread(int port, Router r, Link[] links, long[] hb) throws IOException{
        serverSocket = new ServerSocket(port);
        this.router = r;
        this.ports = links;
        this.heartBeats = hb;
        System.out.println("Opened a server socket with IP: " + serverSocket.getInetAddress() + ":" + serverSocket.getLocalPort());
    }

    public void run() {
        while(true) {
            try {
                Socket server = serverSocket.accept(); //Wait for a client to connect
                //Defer the handling of the connected client to another thread
                new Thread(new ServerServiceThread(server, router, ports, heartBeats)).start(); 
            } catch (SocketTimeoutException s) {
                System.out.println("Socket timed out");
                break;
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }

    }

}
