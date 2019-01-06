package edu.usc.cs685;

import edu.stanford.ramcloud.*;
import edu.usc.cs685.models.*;

import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheManager extends RAMCloud {
    public PriorityQueue<HeapObject> camp_heap;
    public HashMap<Integer, LinkedList> csratio_ll;
    public HashMap<String, Integer> key_csratio;
    public Integer L;
    public Integer max_size;
    public Logger log = Logger.getLogger(CacheManager.class.getName());
    public Integer thread_timeout;
    public Double server_capacity;
    public Boolean capacity_determined;
    public Integer eviction_count;
    public Integer miss_count;

    public CacheManager(String locator, String clusterName) {
        super(locator, clusterName);
        camp_heap = new PriorityQueue<HeapObject>((o1, o2) -> o1.priority - o2.priority);
        csratio_ll = new HashMap<Integer, LinkedList>();
        key_csratio = new HashMap<String, Integer>();
        L = 0;
        max_size = 0;
        thread_timeout = 10 * 1000;
        server_capacity = 0.0;
        capacity_determined = Boolean.FALSE;
        eviction_count = 0;
        miss_count = 0;
        log.setLevel(Level.OFF);
    }

    public CacheManager(String locator) {
        this(locator, "main");
    }

    public long write(long table_id, String id, String value, Integer cost) throws InterruptedException {
        long retval;
        Integer data_size = value.length();
        if(max_size < data_size){
            max_size = data_size;
        }
        log.info(String.format("CacheManager.write: called with object table_id: %s, id: %s", table_id, id));
        try{
            remove(table_id, id, Boolean.TRUE);
        }catch (ClientException.ObjectExistsException e){
            log.info(String.format("CacheManager.write: deleting object table_id: %s, id: %s which does not exists!", table_id, id));
        }

        Boolean to_remove = Boolean.FALSE;
        if(capacity_determined && server_capacity < data_size){
            to_remove = Boolean.TRUE;
        }
        CMWriteThread cmw = new CMWriteThread(table_id, id, value, this);
        cmw.start();
        cmw.join(thread_timeout);
        if(cmw.isAlive() || to_remove) {
            Double data_size_to_remove = Double.valueOf(data_size);
            if(!capacity_determined) {
                log.info(String.format("CacheManager.write: Server capacity determined to be: %s MB", -1*server_capacity/(1024*1024)));
                data_size_to_remove = -1 * server_capacity * 0.1;
                server_capacity *= 0.1;
                capacity_determined = Boolean.TRUE;
            }
            log.info(String.format("CacheManager.write: Write Thread for table_id: %s, id: %s timed out!", table_id, id));
            log.info("CacheManager.write: No free memory, will delete some data...");
            long removed = 0;
            while(removed < data_size_to_remove){
                HeapObject temp = camp_heap.peek();
                LinkedList ll = csratio_ll.get(temp.cs_ratio);
                KeyMetaData camp_root = (KeyMetaData) ll.anchor.next.data;
                removed += remove(camp_root.table_id, camp_root.id);
                eviction_count += 1;
            }
            miss_count += 1;
            log.info(String.format("CacheManager.write: data removed size: %s", removed));
            cmw.join();
            retval = cmw.retval;
        }else{
            log.info(String.format("Write Thread for table_id: %s, id: %s joined!", table_id, id));
            retval = cmw.retval;
        }
        server_capacity -= data_size;
        log.info("CacheManager.write: successfully cached new data");
        Integer cs_ratio = get_cs_ratio(cost, data_size);
        Integer priority = L + cs_ratio;
        KeyMetaData key_meta_data = new KeyMetaData(table_id, id, cost, data_size, cs_ratio, priority);

        if(csratio_ll.get(cs_ratio) == null){
            log.info(String.format("CacheManager.write: new cs_ratio %s seen, creating linkedlist", cs_ratio));
            csratio_ll.put(cs_ratio, new LinkedList());
            csratio_ll.get(cs_ratio).append(key_meta_data);
            log.info(String.format("CacheManager.write: heappush (%s, %s)", priority, cs_ratio));
            camp_heap.add(new HeapObject(priority, cs_ratio));
        } else{
            log.info(String.format("CacheManager.write: appending to linked list (%s, %s)", priority, cs_ratio));
            csratio_ll.get(cs_ratio).append(key_meta_data);
        }

        String key_ = String.format("%s/%s", table_id, id);
        log.info(String.format("CacheManager.write: associating key %s to cs_ratio %s", key_, cs_ratio));
        key_csratio.put(key_, cs_ratio);
        return retval;
    }

    private Integer get_cs_ratio(Integer cost, Integer size){
        log.info(String.format("CacheManager.get_cs_ratio called for cost: %s, size: %s", cost, size));
        Integer integer = cost*max_size/size;

        Integer rounded_value = do_rounding(integer, 4);

        log.info(String.format("CacheManager.get_cs_ratio: rounded value: %s", rounded_value));
        return rounded_value;
    }

    private static Integer do_rounding(Integer number, Integer precision)
    {
        Integer msb = 0;

        for(int i=32; i>0; i--)
        {
            if((is_Kth_bit_set(number, i)) == 1)
            {
                msb = i;
                break;
            }
        }
        if(msb - precision > 0)
        {
            int mask = (int)Math.pow(2, precision) - 1;
            mask = mask << (msb-precision);
            number = mask & number;
            return number;
        }
        else
        {
            return number;
        }

    }

    private static int is_Kth_bit_set(Integer n, int k)
    {

        if ((n & (1 << (k-1))) >= 1)
            return 1;
        else
            return 0;
    }

    @Override
    public long remove(long table_id, String id){
        return remove(table_id, id, Boolean.FALSE);
    }

    public long remove(long table_id, String id, Boolean camp_ds_only) {
        if(!camp_ds_only){
            log.info(String.format("CacheManager.delete called with table_id: %s, id: %s", table_id, id));
            super.remove(table_id, id);
        }
        log.info("let's update camp data structures");
        Integer size = 0;
        String key_ = String.format("%s/%s", table_id, id);
        Integer cs_ratio = key_csratio.get(key_);
        if(cs_ratio != null){
            LinkedList ll = csratio_ll.get(cs_ratio);
            if (ll != null && ll.len > 0){
                Node node = ll.anchor.next;
                KeyMetaData key_meta_data = (KeyMetaData) node.data;
                if(key_meta_data.table_id == table_id && key_meta_data.id.equals(id)){
                    log.info("CacheManager.delete: deleting head node");
                    size = key_meta_data.size;

                    HeapObject to_remove = null;
                    for (HeapObject iter : camp_heap) {
                        if (iter.priority.equals(key_meta_data.priority)) {
                            to_remove = iter;
                            break;
                        }
                    }
                    camp_heap.remove(to_remove);
                    ll.unlink(node);
                    key_csratio.remove(key_);
                    if(ll.len > 0){
                        Node next_node = ll.anchor.next;
                        KeyMetaData next_key_meta_data = (KeyMetaData) next_node.data;
                        log.info(String.format("CacheManager.delete: heappush (%s, %s)", next_key_meta_data.priority, cs_ratio));
                        camp_heap.add(new HeapObject(next_key_meta_data.priority, cs_ratio));
                        L = camp_heap.peek().priority;
                        log.info(String.format("CacheManager.delete: updated L to: %s",L));
                    } else{
                        csratio_ll.remove(cs_ratio);
                    }

                    log.info(String.format("CacheManager.delete: deleted %s bytes", size));
                    server_capacity += size;
                    return size;
                } else{
                    log.info("CacheManager.delete: deleting not a head node");
                    node = node.next;
                    while(node != ll.anchor){
                        key_meta_data = (KeyMetaData) node.data;
                        if (key_meta_data.table_id == table_id && key_meta_data.id.equals(id)){
                            size = key_meta_data.size;
                            ll.unlink(node);
                            key_csratio.remove(key_);
                            log.info(String.format("CacheManager.delete: deleted %s bytes", size));
                            server_capacity += size;
                            return size;
                        }
                        node = node.next;
                    }
                }
            }
        }

        log.info(String.format("CacheManager.delete: deleted %s bytes", size));
        server_capacity += size;
        return size;
    }

    @Override
    public RAMCloudObject read(long table_id, String id) {
        log.info(String.format("CacheManager.read called with table_id: %s, id: %s", table_id, id));
        RAMCloudObject data = super.read(table_id, id);
        String key_ = String.format("%s/%s", table_id, id);
        Integer cs_ratio = key_csratio.get(key_);
        if(cs_ratio != null){
            LinkedList ll = csratio_ll.get(cs_ratio);
            if (ll != null && ll.len > 0){
                Node node = ll.anchor.next;
                KeyMetaData key_meta_data = (KeyMetaData) node.data;
                if(key_meta_data.table_id == table_id && key_meta_data.id.equals(id)){
                    log.info("CacheManager.read: reading head node");

                    HeapObject to_remove = null;
                    for (HeapObject iter : camp_heap) {
                        if (iter.priority.equals(key_meta_data.priority)) {
                            to_remove = iter;
                            break;
                        }
                    }
                    camp_heap.remove(to_remove);
                    key_meta_data = (KeyMetaData) node.data;
                    ll.unlink(node);
                    if(ll.len > 0){
                        KeyMetaData next_key_meta_data = (KeyMetaData) ll.anchor.next.data;
                        log.info(String.format("CacheManager.read: heappush (%s, %s)", next_key_meta_data.priority, cs_ratio));
                        camp_heap.add(new HeapObject(next_key_meta_data.priority, cs_ratio));
                        L = camp_heap.peek().priority;
                        log.info(String.format("CacheManager.read: updated L to: %s", L));
                        key_meta_data.priority = L + cs_ratio;
                        ll.append(key_meta_data);
                    } else{
                        key_meta_data.priority = L + cs_ratio;
                        ll.append(key_meta_data);
                        log.info(String.format("CacheManager.read: heappush (%s, %s)", key_meta_data.priority, cs_ratio));
                        camp_heap.add(new HeapObject(key_meta_data.priority, cs_ratio));
                    }
                } else{
                    node = node.next;
                    while(node != ll.anchor){
                        key_meta_data = (KeyMetaData) node.data;
                        if (key_meta_data.table_id == table_id && key_meta_data.id.equals(id)){
                            ll.unlink(node);
                            key_meta_data.priority = L + cs_ratio;
                            log.info(String.format("CacheManager.read: new priority: %s", key_meta_data.priority));
                            ll.append(key_meta_data);
                            break;
                        }
                        node = node.next;
                    }
                }
            }
        }
        return data;
    }
}

class CMWriteThread extends Thread{
    private long table_id;
    private String id;
    private CacheManager cm;
    private String value;
    public long retval;

    CMWriteThread(long table_id, String id, String value, CacheManager cm) {
        this.table_id = table_id;
        this.id = id;
        this.cm = cm;
        this.value = value;
    }

    public void run() {
        retval = cm.write(table_id, id, value);
    }
}
