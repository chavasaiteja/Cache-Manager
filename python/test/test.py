import sys
import time

sys.path.append('..')
import cachemanager


cm = cachemanager.CacheManager()
cm.connect(serverLocator='tcp:host={},port=8001'.format(sys.argv[1]))
cm.create_table("table1")
table_id = cm.get_table_id("table1")
cm.write(table_id, 'object1', 'Hello from Python!')
value = cm.read(table_id, 'object1')
cm.delete(table_id, 'object1')
try:
    print cm.read(table_id, 'object1')
except:
    print "trying to read unavailable object"

i = 1
sample_data_mb = open('sample_data/megabyte.txt', 'r').read()
while 1:
    object_id = "object{}".format(i)
    cm.write(table_id, object_id, sample_data_mb)
    print "written {}".format(object_id)
    print cm.camp_heap
    print len(cm.csratio_ll[1])
    print cm.key_csratio
    print cm.L
    print cm.max_size
    print "\n"
    i += 1
    time.sleep(2)
