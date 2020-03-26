import socket
import sys
import picamera
import time

c = picamera.PiCamera()
time.sleep(2)
host = "localhost" 
port = 50500
if len(sys.argv) > 1:
    host = sys.argv[1]
if len(sys.argv) > 2:
    port = int(sys.argv[2])

print(host)
print(port)

while True:
    s = socket.socket()
    s.connect((host, port))
    c.capture('frame.png', format='png')
    filename = 'frame.png'
    f = open(filename,'rb')
    print 'Sending...'
    l = f.read(1024)
    while (l):
        #print 'Sending...'
        s.send(l)
        l = f.read(1024)
    f.close()
    print "Done Sending"
    s.close()
    time.sleep(1)


