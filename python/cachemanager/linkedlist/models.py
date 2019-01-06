class Node(object):
    def __init__(self):
        self.next = None
        self.data = None
        self.prev = None

    def __str__(self):
        return str(self.data)


class LinkedList(object):
    def __init__(self):
        self.anchor = Node()
        self.anchor.next = self.anchor.prev = self.anchor
        self.len = 0

    def append(self, data):
        new_node = Node()
        new_node.data = data
        new_node.next = self.anchor
        new_node.prev = self.anchor.prev
        self.anchor.prev = new_node
        new_node.prev.next = new_node
        self.len += 1
        return new_node

    def prepend(self, data):
        new_node = Node()
        new_node.data = data
        new_node.next = self.anchor.next
        new_node.prev = self.anchor
        self.anchor.next = new_node
        new_node.next.prev = new_node
        self.len += 1
        return new_node

    def unlink(self, node):
        node.prev.next = node.next
        node.next.prev = node.prev
        node.next = node.prev = node.data = None
        del node
        self.len -= 1

    def __str__(self):
        data_list = []
        if self.len > 0:
            node = self.anchor.next
            while node != self.anchor:
                data_list.append(str(node))
                node = node.next
        return "LinkedList [ " + " -> ".join(data_list) + " ]"

    def __len__(self):
        return self.len


if __name__ == "__main__":
    ll = LinkedList()
    abc = ll.append("abc")
    pqr = ll.append("pqr")
    def_ = ll.append("def")

    print ll
    s = abc.next
    print s, abc
    ll.unlink(abc)
    print s, abc.next, abc.prev, abc.data
    print ll