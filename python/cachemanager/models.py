import ramcloud
import heapq
import linkedlist
import logging

logging.basicConfig(filename="/logs/cachemanager.log", level=logging.DEBUG)


class KeyMetaData(object):
    def __init__(self, table_id, id, cost, size, cs_ratio, priority):
        self.table_id = table_id
        self.id = id
        self.cost = cost
        self.size = size
        self.cs_ratio = cs_ratio
        self.priority = priority

    def __str__(self):
        print "table_id: {}\n" \
              "id: {}\n" \
              "cost: {}\n" \
              "size: {}\n" \
              "cs_ratio: {}\n" \
              "priority: {}".format(self.table_id, self.id, self.cost, self.size, self.cs_ratio, self.priority)


class CacheManager(ramcloud.RAMCloud):
    def __init__(self):
        super(CacheManager, self).__init__()
        self.camp_heap = []
        self.key_csratio = {}  # key -> cs_ratio
        self.csratio_ll = {}
        self.L = 0
        self.max_size = 0
        self.capacity = 0.8 * (35*1024*1024)

    def write(self, table_id, id, data, want_version=None, cost=1):
        data_size = len(data)
        if self.max_size < data_size:
            self.max_size = data_size
        logging.debug("CacheManager.write called with table_id: {}, id: {}, data size: {}, cost: {}, want_version: {}".
                      format(table_id, id, data_size, cost, want_version))
        try:
            self.delete(table_id, id, want_version)
        except ramcloud.ObjectExistsError:
            logging.debug("CacheManager.write deleting object table_id: {}, id: {} which does not exists!".format(table_id, id))
            pass

        try:
            if self.capacity > data_size:
                super(CacheManager, self).write(table_id, id, data, want_version)
            else:
                raise ramcloud.RetryExceptionError
        except ramcloud.RetryExceptionError:
            logging.debug("CacheManager.write: No free memory, will delete some data...")
            removed = 0
            while removed <= data_size:
                min_priority, cs_ratio = self.camp_heap[0]
                removed += self.delete(
                    self.csratio_ll[cs_ratio].anchor.next.data.table_id,
                    self.csratio_ll[cs_ratio].anchor.next.data.id
                )
                logging.debug("CacheManager.write: data removed size: {}".format(removed))

            super(CacheManager, self).write(table_id, id, data, want_version)
        logging.debug("CacheManager.write: successfully cached new data")
        cs_ratio = self.get_cs_ratio(cost, data_size)
        priority = self.L + cs_ratio
        key_meta_data = KeyMetaData(
            table_id=table_id,
            id=id,
            cost=cost,
            size=data_size,
            cs_ratio=cs_ratio,
            priority=priority)

        if cs_ratio not in self.csratio_ll:
            logging.debug("CacheManager.write: new cs_ratio {} seen, creating linkedlist".format(cs_ratio))
            self.csratio_ll[cs_ratio] = linkedlist.LinkedList()
            self.csratio_ll[cs_ratio].append(key_meta_data)
            logging.debug("CacheManager.write: heappush ({}, {})".format(priority, cs_ratio))
            heapq.heappush(self.camp_heap, (priority, cs_ratio))
        else:
            self.csratio_ll[cs_ratio].append(key_meta_data)
        key_ = "{}/{}".format(table_id, id)
        logging.debug("CacheManager.write: associating key {} to cs_ratio {}".format(key_, cs_ratio))
        self.key_csratio[key_] = cs_ratio

    def get_cs_ratio(self, cost, size):
        logging.debug("CacheManager.get_cs_ratio called for cost: {}, size: {}".format(cost, size))
        integer = int(cost*self.max_size/float(size))

        rounded_value = self.do_rounding(integer, 4)  # ToDo perform rounding scheme

        logging.debug("CacheManager.get_cs_ratio: rounded value: {}".format(rounded_value))
        return rounded_value

    def do_rounding(self, number, precision):
        msb = 0
        for i in range(1,33):
            if self.is_kth_bit_set(number, i) == 1:
                msb = i
                break

        if msb - precision > 0:
            mask = 2**precision - 1
            mask = mask << (msb-precision)
            number = mask & number
            return number
        else:
            return number

    @staticmethod
    def is_kth_bit_set(n, k):
        if n & (1 << (k - 1)):
            return 1
        else:
            return 0

    def read(self, table_id, id, want_version=None):
        logging.debug("CacheManager.read called with table_id: {}, id: {}, want_version: {}".
                      format(table_id, id, want_version))
        data = super(CacheManager, self).read(table_id, id, want_version)
        key_ = "{}/{}".format(table_id, id)
        cs_ratio = self.key_csratio.get(key_, None)
        if cs_ratio:
            ll = self.csratio_ll.get(cs_ratio, None)
            if ll and ll.len > 0:
                node = ll.anchor.next
                if node.data.table_id == table_id and node.data.id == id:
                    logging.debug("CacheManager.read: reading head node")
                    i = self.camp_heap.index((node.data.priority, cs_ratio))
                    self.camp_heap[i] = self.camp_heap[-1]
                    self.camp_heap.pop()
                    if i < len(self.camp_heap):
                        heapq._siftup(self.camp_heap, i)
                        heapq._siftdown(self.camp_heap, 0, i)
                    node_data = node.data
                    ll.unlink(node)

                    if ll.len == 0:
                        node_data.priority = self.L + cs_ratio
                        self.csratio_ll[cs_ratio].append(node_data)
                        logging.debug("CacheManager.read: heappush ({}, {})".format(ll.anchor.next.data.priority, cs_ratio))
                        heapq.heappush(self.camp_heap, (ll.anchor.next.data.priority, cs_ratio))
                    else:
                        logging.debug("CacheManager.read: heappush ({}, {})".format(ll.anchor.next.data.priority, cs_ratio))
                        heapq.heappush(self.camp_heap, (ll.anchor.next.data.priority, cs_ratio))
                        self.L, cs_ratio_min_heap = self.camp_heap[0]
                        logging.debug("CacheManager.read: updated L to: {}".format(self.L))
                        node_data.priority = self.L + cs_ratio
                else:
                    node = node.next
                    while node is not None:
                        if node.data.table_id == table_id and node.data.id == id:
                            node_data = node.data
                            ll.unlink(node)
                            node_data.priority = self.L + cs_ratio
                            logging.debug("CacheManager.read: new priority: {}".format(node_data.priority))
                            self.csratio_ll[cs_ratio].append(node_data)
        return data

    def delete(self, table_id, id, want_version=None):
        logging.debug("CacheManager.delete called with table_id: {}, id: {}, want_version: {}".
                      format(table_id, id, want_version))
        super(CacheManager, self).delete(table_id, id, want_version)

        size = 0
        key_ = "{}/{}".format(table_id, id)
        cs_ratio = self.key_csratio.get(key_, None)
        if cs_ratio:
            ll = self.csratio_ll.get(cs_ratio, None)
            if ll and ll.len > 0:
                node = ll.anchor.next
                if node.data.table_id == table_id and node.data.id == id:
                    logging.debug("CacheManager.delete: deleting head node")
                    size = node.data.size
                    i = self.camp_heap.index((node.data.priority, cs_ratio))
                    self.camp_heap[i] = self.camp_heap[-1]
                    self.camp_heap.pop()
                    if i < len(self.camp_heap):
                        heapq._siftup(self.camp_heap, i)
                        heapq._siftdown(self.camp_heap, 0, i)
                    ll.unlink(node)
                    del self.key_csratio[key_]
                    if ll.len > 0:
                        logging.debug("CacheManager.delete: heappush ({}, {})".format(ll.anchor.next.data.priority, cs_ratio))
                        heapq.heappush(self.camp_heap, (ll.anchor.next.data.priority, cs_ratio))
                        self.L, cs_ratio = self.camp_heap[0]
                        logging.debug("CacheManager.delete: updated L to: {}".format(self.L))
                    else:
                        del self.csratio_ll[cs_ratio]
                    logging.debug("CacheManager.delete: deleted {} bytes".format(size))
                    return size
                else:
                    node = node.next
                    while node is not None:
                        if node.data.table_id == table_id and node.data.id == id:
                            size = node.data.size
                            ll.unlink(node)
                            del self.key_csratio[key_]
                            logging.debug("CacheManager.delete: deleted {} bytes".format(size))
                            return size
                        node = node.next
        logging.debug("CacheManager.delete: deleted {} bytes".format(size))
        return size
