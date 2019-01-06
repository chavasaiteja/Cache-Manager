package edu.usc.cs685;

import java.util.logging.Level;
import java.util.logging.Logger;

public class TestCacheManager {
    private static Logger log = Logger.getLogger(TestCacheManager.class.getName());

    public static void main(String args[]){
        log.setLevel(Level.ALL);

        long table_id;
        CacheManager cm = new CacheManager("tcp:host=server_hostname,port=8001", "main");
        cm.createTable("table1");
        table_id = cm.getTableId("table1");
        int sleep_time = 100;
        int i = 0;
        char[] data = new char[1048576];
        String value = new String(data);
        String object_id;
        Integer cost = 1;

        log.info("Writing...");
        while(true) {
            i += 1;
            object_id = "object" + Integer.toString(i);
            try {
                cm.write(table_id, object_id, value, cost);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.info(String.format("objs len: %s\n",
                    cm.csratio_ll.get(cm.camp_heap.peek().cs_ratio).len));
            try {
                Thread.sleep(sleep_time);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(i == 1650){
                break;
            }
        }
    }
}
