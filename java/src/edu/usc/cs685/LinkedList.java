package edu.usc.cs685;
import edu.usc.cs685.models.Node;

public class LinkedList {
    public Node anchor;
    public long len;

    public LinkedList(){
        anchor = new Node();
        anchor.next = anchor.prev = anchor;
        len = 0;
    }

    public Node append(Object data){
        Node new_node = new Node();
        new_node.data = data;
        new_node.next = anchor;
        new_node.prev = anchor.prev;
        anchor.prev = new_node;
        new_node.prev.next = new_node;
        len += 1;
        return new_node;
    }

    public void unlink(Node node){
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.next = null;
        node.prev = null;
        len -= 1;
    }

}
