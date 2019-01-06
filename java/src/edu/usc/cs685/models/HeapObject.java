package edu.usc.cs685.models;

public class HeapObject {
    public Integer priority;
    public Integer cs_ratio;

    public HeapObject(Integer priority, Integer cs_ratio) {
        this.priority = priority;
        this.cs_ratio = cs_ratio;
    }
}
