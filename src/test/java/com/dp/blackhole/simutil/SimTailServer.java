package com.dp.blackhole.simutil;

import static org.junit.Assert.*;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.dp.blackhole.common.AgentProtocol;
import com.dp.blackhole.common.AgentProtocol.AgentHead;

public class SimTailServer implements Runnable {
    private static final Log LOG = LogFactory.getLog(SimTailServer.class);
    
    private static final int MAX_SIZE = 10;
    private ServerSocket ss;
    private List<String> receives;
    private volatile boolean shouldStop;
    public SimTailServer(int port, List<String> receives) {
        this.shouldStop = false;
        this.receives = receives;
        try {
            ss = new ServerSocket(port);
            System.out.println("server begin at " + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public boolean shouldStopOrNot() {
        return shouldStop;
    }
    public void stopIt() {
        shouldStop = true;
    }
    
    public void run() {
        Socket socket = null;
        DataInputStream din = null;
        System.out.println(Thread.currentThread());
        try {
            String line = null;
            socket = ss.accept();
            din = new DataInputStream(socket.getInputStream());
            //check header
            AgentProtocol protocol = new AgentProtocol();
            AgentHead head = protocol.new AgentHead();
            protocol.recieveHead(din, head);
            assertEquals(1, head.type);
            String appname = head.app;
            LOG.info("Receive... " + appname);
            String periodStr = String.valueOf(head.peroid);
            LOG.info("Receive... " + periodStr);
            BufferedReader reader = new BufferedReader(new InputStreamReader(din));
            while (!shouldStop && (line = reader.readLine()) != null) {
                LOG.info("server>" + line);
                receives.add(line);
                if (receives.size() == MAX_SIZE) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (din != null) {
                    din.close();
                }
                ss.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
