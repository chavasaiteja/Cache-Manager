package edu.usc.cs685;
import edu.stanford.ramcloud.RAMCloud;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

class RCWriteThread extends Thread{
    private long table_id;
    private String id;
    private RAMCloud rc;
    private String value;

    RCWriteThread(long table_id, String id, String value, RAMCloud rc) {
        this.table_id = table_id;
        this.id = id;
        this.rc = rc;
        this.value = value;
    }

    public void run() {
        rc.write(table_id, id, value);
        System.out.println("Successfully written! " + id);
    }
}

class RCRemoveThread extends Thread{
    private long table_id;
    private String id;
    private RAMCloud ob;

    RCRemoveThread(long table_id, String id, RAMCloud ob)
    {
        this.table_id = table_id;
        this.id = id;
        this.ob = ob;
    }

    public void run(){
        ob.remove(table_id,id);
        System.out.println("Successfully deleted!");
    }
}

public class TestRAMCloud{
    private static Logger log = Logger.getLogger(TestRAMCloud.class.getName());

    public static void main(String args[]){
        log.setLevel(Level.ALL);

        long table_id;
        RAMCloud rc = new RAMCloud("tcp:host=35.184.68.37,port=8001", "main");
        rc.createTable("table1");
        table_id = rc.getTableId("table1");
        int thread_timeout = 5 * 1000;
        int sleep_time = 100;
        int i = 0;
        char[] data = new char[1048576];
        String value = new String(data);

        String object_id;

        log.info("Writing...");
        while(true) {
            i += 1;
            object_id = "object" + Integer.toString(i);
            RCWriteThread w = new RCWriteThread(table_id, object_id, value, rc);
            w.start();
            try {
                w.join(thread_timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(w.isAlive()) {
                log.info("Write Thread for " + object_id + " timed out!");
                break;
            }else{
                log.info("Write Thread for " + object_id + " joined!");
            }
            try {
                Thread.sleep(sleep_time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }
        log.info("Deleting...");
        int j = 0;
        while(j < i){
            j += 1;
            try {
                Thread.sleep(sleep_time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            object_id = "object" + Integer.toString(j);
            RCRemoveThread r = new RCRemoveThread(table_id ,object_id, rc);
            r.start();
            try {
                r.join(thread_timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(r.isAlive()) {
                log.info("Delete Thread for " + object_id + " timed out!");
            }else{
                log.info("Delete Thread for " + object_id + " joined!");
            }

        }
    }
}