# CacheManager
A simple client side cachemanager library which is built on top of [RAMCloud](https://ramcloud.stanford.edu) and supports single client and single server. It uses [CAMP](https://dl.acm.org/citation.cfm?id=2663317) eviction policy to evict objects.

I chose to work on this project as a part of USC's "CS685: Advanced Topics in Database Systems" class. This is just a proof of concept project with no production value. I plan to develop the concept on server-side so as to support multiple clients and/or servers in near future as a side project.

There are two major observation which geared our design choices. 
* Most RAMCloud operations including "write" do not return until they are successfully completed. So if memory is exhausted on the server, it keeps on retrying but does not return. So we decided to run the write operation in a new thread and wait for it to join until predefined timeout. If thread join operation returns and thread is still alive we consider it as a notification of memory full on the server.

* RAMCloud deadlocks on very high memory utilization. This is because log cleaner is not able to generate any free log segments and delete operation cannot write tombstone records as there is not enough memory. To prevent this it is recommended to provide atleast 512 MB memory to the server. Because of this reason, working at near 100% utilization is not recommended and we chose to work on 90% utilization. To prevent hard-coding memory capacity on client, we chose a clever approach. We keep track of total records size successfully written (and deleted) and whenever the first write thread times out, we consider the total record size written as server memory capacity. We remove records amounting to 10% of this size right away and maintain 90% utilization there after. This approach has many benefits, one being that now onwards no write thread stalls as we always find enough memory on the server. 

This library is developed, tested and benchmarked on Debian 8 on Google Compute Engine instances. Following steps are required to run on bare-bone machine:

### Create a new user and give the newly created user the admin  permissions
    $ sudo su
    $ adduser admin
    $ usermod -aG sudo,adm admin

Let's create swap space so that we can build RAMCloud. 
### Create swap file and make it permanent
    $ sudo fallocate -l 2G /swapfile
    $ sudo chmod 600 /swapfile
    $ sudo mkswap /swapfile
    $ sudo swapon /swapfile
    $ sudo swapon -s
    $ sudo vim /etc/fstab
        append the file with "/swapfile none swap sw 0 0"
        
### Install the necessary modules as per RAMCloud's [General Information for Developers](https://ramcloud.atlassian.net/wiki/spaces/RAM/pages/6848614/General+Information+for+Developers)
    $ sudo apt-get install build-essential git-core doxygen libpcre3-dev protobuf-compiler libprotobuf-dev libcrypto++-dev libevent-dev libboost-all-dev libgtest-dev libzookeeper-mt-dev zookeeper libssl-dev

### GCC 4.9.X is recommended for RAMCloud
    $ sudo apt-get remove gcc g++
    $ sudo apt-get install gcc-4.9 g++-4.9
    $ ln -s /usr/bin/g++-4.9 /usr/bin/g++
    $ ln -s /usr/bin/gcc-4.9 /usr/bin/gcc

### Install JDK 8 for RAMCloud to successfully build
    $ echo "deb http://http.debian.net/debian jessie-backports main" |  sudo tee --append /etc/apt/sources.list.d/jessie-backports.list > /dev/null
    $ sudo apt-get install -t jessie-backports openjdk-8-jdk
    $ sudo update-alternatives --config java
        select appropriate java alternative as default
        
### Build and install RAMCloud on Server and Client machine
    $ git clone https://github.com/PlatformLab/RAMCloud.git  
    $ cd RAMCloud
    $ git submodule update --init --recursive
    $ ln -s ../../hooks/pre-commit .git/hooks/pre-commit
    $ make -j12
    $ make install

### Setup Server: Run coordinator and master
    $ sudo vim /etc/security/limits.conf
        append these lines to increase memlock limit
        *       hard    memlock unlimited
        *       soft    memlock unlimited
    $ ./obj.master/coordinator -C tcp:host=`hostname -s`,port=8001 
    $ ./obj.master/server -r 0 -L tcp:host=`hostname -s`,port=8002 -C tcp:host=`hostname -s`,port=8001 --totalMasterMemory 512 --maxCores 5 --masterOnly 
    
### Setup Client: CacheManager
    $ sudo vim /etc/hosts 
        append the file with server_hostname	XX.XXX.XX.XX
    $ git clone https://github.com/jigarkb/CacheManager.git
    
    // Build Java project and run test code (python code is little outdated and untested)
    $ cd CacheManager/java
    $ java -classpath ~/CacheManager/java/out/production/CacheManager:~/RAMCloud/install/lib/ramcloud/ramcloud.jar edu.usc.cs685.TestCacheManager
    
    // Python Bindings can be used like this 
    $ LD_LIBRARY_PATH=$LD_LIBRARY_PATH:~/RAMCloud/obj.master PYTHONPATH=~/RAMCloud/bindings/python python
    
### Visit [cachemanager-ycsb](https://github.com/jigarkb/cachemanager-ycsb) for ycsb benchmark client for this library
