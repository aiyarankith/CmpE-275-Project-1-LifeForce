import comm_pb2
import socket               
import time
import struct


def buildPing(tag, number):

    r = comm_pb2.Request()

    r.body.ping.tag = str(tag)
    r.body.ping.number = number
    
    
    r.header.originator = 1
    r.header.tag = str(tag + number + int(round(time.time() * 1000)))
    r.header.routing_id = comm_pb2.Header.PING
    r.header.toNode = int(0)
    
    msg = r.SerializeToString()
    return msg



    
def buildPhotoJob(photoname, data):
   
    r = comm_pb2.Request()    
    b = data.encode('utf-8')
    r.body.photoPayload.name = photoname
    r.body.photoPayload.data = b
    
    r.header.originator = 1  
    r.header.routing_id = comm_pb2.Header.JOBS
    r.header.toNode = int(0)
    r.header.photoHeader.requestType = 1
    r.header.photoHeader.lastModified = 0
    r.header.photoHeader.contentLength = int(56)
    
    msg = r.SerializeToString()
    return msg




def sendMsg(msg_out, port, host):
    s = socket.socket()         
#    host = socket.gethostname()
#    host = "192.168.0.87"

    s.connect((host, port))        
    msg_len = struct.pack('>L', len(msg_out))    
    s.sendall(msg_len + msg_out)
    print "inside"
    len_buf = receiveMsg(s, 4)
    print len_buf
    msg_in_len = struct.unpack('>L', len_buf)[0]
    print msg_in_len
    msg_in = receiveMsg(s, msg_in_len)
    
    r = comm_pb2.Request()
    r.ParseFromString(msg_in)
    print msg_in
#    print r.body.job_status 
#    print r.header.reply_msg
#    print r.body.job_op.data.options
    s.close
    return r
def receiveMsg(socket, n):
    buf = ''
    while n > 0:        
        data = socket.recv(n)                  
        if data == '':
            raise RuntimeError('data not received!')
        buf += data
        n -= len(data)
    return buf  


def getBroadcastMsg(port):
    # listen for the broadcast from the leader"
          
    sock = socket.socket(socket.AF_INET,  # Internet
                        socket.SOCK_DGRAM)  # UDP
   
    sock.bind(('', port))
   
    data = sock.recv(1024)  # buffer size is 1024 bytes
    return data
        
   
if __name__ == '__main__':
    # msg = buildPing(1, 2)
    # UDP_PORT = 8080
    # serverPort = getBroadcastMsg(UDP_PORT) 
    
    host = "localhost"
    port = "6000"
    
    port = int(port)
    whoAmI = 1;
    photo = "dreema"
    for i in range (0,100):
        i=str(i)
        name = "testimg" + i
        buildPhoto = buildPhotoJob(name,photo)
        result = sendMsg(buildPhoto, port, host)
   
    
    
#    name_space = "competition"
#    ownerId = 123;
#    listcourseReq = buildListCourse(name_space, comm_pb2.JobOperation.ADDJOB, ownerId)
#    sendMsg(listcourseReq, 5573)



