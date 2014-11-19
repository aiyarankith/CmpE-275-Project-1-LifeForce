import comm_pb2
import socket
import time
import struct
import base64
import pdb
from PIL import Image
from io import BytesIO
from base64 import decodestring
import cStringIO
from StringIO import *
from PIL import Image
from PIL import ImageDraw
from PIL import ImageFont
import io
import binascii

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

def buildJob(name_space, jobAction, ownerId):
    
    jobId = str(int(round(time.time() * 1000)))

    r = comm_pb2.Request()    

    r.body.job_op.job_id = jobId
    r.body.job_op.action = jobAction
    
    r.body.job_op.data.name_space = name_space
    r.body.job_op.data.owner_id = ownerId
    r.body.job_op.data.job_id = jobId
    r.body.job_op.data.status = comm_pb2.JobDesc.JOBQUEUED
    
    r.body.job_op.data.options.node_type = comm_pb2.NameValueSet.NODE
    r.body.job_op.data.options.name = "email"
    r.body.job_op.data.options.value = "shajit4"
    
    nvs = comm_pb2.NameValueSet()
    nvs.node_type = comm_pb2.NameValueSet.NODE
    nvs.name = "test"
    nvs.value = "success"
    r.body.job_op.data.options.node.extend([nvs])
    
    r.header.originator = 1  
    r.header.routing_id = comm_pb2.Header.JOBS
    r.header.toNode = int(0)
    
    msg = r.SerializeToString()
    return msg


def buildimageuploadjob(filepath):
    jobId = str(int(round(time.time() * 1000)))

    with open("Error.png", "rb") as image_file:
            imagebytes = base64.b64encode(image_file.read())
    #imagebytes = base64.standard_b64encode(open("Error.png", "rb").read())
    #print imagebytes
    r = comm_pb2.Request()


        #b = bytearray(imagebytes)


    r.header.photoHeader.requestType = 1
    #r.body.photoPayload.uuid = "1"
    r.body.photoPayload.name = "nidhi"
    r.body.photoPayload.data = imagebytes
    r.header.originator = 1000
    r.header.routing_id = comm_pb2.Header.JOBS
    r.header.toNode = 0L

    msg = r.SerializeToString()

    return msg


def buildimageretrievejob(uuid):
    jobId = str(int(round(time.time() * 1000)))

    r = comm_pb2.Request()
    r.header.photoHeader.requestType = 0
    r.body.photoPayload.uuid = uuid
    r.header.originator = 1000
    r.header.routing_id = comm_pb2.Header.JOBS
    r.header.toNode = 0L

    msg = r.SerializeToString()

    return msg
    
def buildPhotoDeleteJob(uuid):
    
    r = comm_pb2.Request()    
   
    r.body.photoPayload.uuid = uuid
    
    r.header.originator = 1  
    r.header.routing_id = comm_pb2.Header.JOBS
    r.header.toNode = int(0)
    r.header.photoHeader.requestType = 2
    
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
    print "Length"+msg_in_len.__str__()
    print msg_in_len
    print "!234"
    msg_in = receiveMsg(s, msg_in_len)
    
    r = comm_pb2.Request()
    r.ParseFromString(msg_in)
    
    #print msg_in
#    print r.body.job_status.unique_job_id 
    print r.header.reply_msg
    print r.body.job_op.data.job_id
    
 #   print r.body.job_op.data.unique_job_id
    s.close
    return r
def receiveMsg(socket, n):
    buf = ''
    while n > 0:        
        data = socket.recv(n)  
        #print data.__str__()                
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
    
    host = raw_input("IP:")
    port = raw_input("Port:")

    port = int(port)
    whoAmI = 1;
    input = raw_input("Welcome to our LifeStream client! Kindly select your desirable action:\n1.Ping Response\n2.Write\n3.Read\n4.Delete\n")
    if input == "1":        
        print("Ping Resource") 
        ping = buildPing(2,2)
        result = sendMsg(ping, port, host)
    elif input == "2":        
        print("Photo Write") 
        file_path = raw_input("Enter the name of the picture:")
        imguploadJob = buildimageuploadjob(file_path)
        result = sendMsg(imguploadJob, port, host)
    elif input == "3":        
        print("Photo Read") 
        uuid = raw_input("Photo UUID:")
        imgretriveJob = buildimageretrievejob(uuid)
        result2 = sendMsg(imgretriveJob, port, host)
        #r_data =base64.standard_b64decode(result2.body.photoPayload.data)
        #stream = io.BytesIO(r_data)
        img = Image.open("a_test.png")
        #draw = ImageDraw.Draw(img)
        #draw = ImageDraw.Draw(img)
        img.save("a_test.png")
    elif input == "4":        
        print("Photo Delete") 
        uuid = raw_input("Photo UUID:")
        buildPhotoDeleteJob = buildPhotoDeleteJob(uuid)
        result = sendMsg(buildPhotoDeleteJob, port, host)
    while True:
        input = raw_input("\nPlease enter 0 to quit:\n0.Quit\n")
        if input == "0":
            print("Thanks for using our LifeStream! See you soon ...")
            break
    
#    name_space = "competition"
#    ownerId = 123;
#    listcourseReq = buildListCourse(name_space, comm_pb2.JobOperation.ADDJOB, ownerId)
#    sendMsg(listcourseReq, 5573)




