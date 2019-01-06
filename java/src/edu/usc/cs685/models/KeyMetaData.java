package edu.usc.cs685.models;

public class KeyMetaData {
    public long table_id;
    public String id;
    public Integer cost;
    public Integer size;
    public Integer cs_ratio;
    public Integer priority;

    public KeyMetaData(long table_id, String id, Integer cost, Integer size, Integer cs_ratio, Integer priority) {
        this.table_id = table_id;
        this.id = id;
        this.cost = cost;
        this.size = size;
        this.cs_ratio = cs_ratio;
        this.priority = priority;
    }
}
