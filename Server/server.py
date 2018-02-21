from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib import parse

hostName = '130.215.249.204'
hostPort = 8080

nextId = 1

# This is where data is stored
# it is a dict where every key is the ID of a user
# The entry for each ID is another dict that looks like this
# {'text': [], 'log': [], "file": [], "calendar": [], "contact":[], "audio": []}
# I then add each piece of received data to the corresponding array
receivedData = {}


# Return a string version of whatever result we want to display
# INPUT - ID - the id (as a string) of the requesting device
def getClassification(ID):
    global receivedData

    ## ADA HERE IS WHERE YOU MESH WITH MY CODE ##

    print("Get class for " + ID)
    
    return "YOU ARE DOOMED"

#_________ Server code ________________________________________________________________________________________

# Obtains an ID, sets up the in memory Dict for saving the data from this ID, and then sends
# the ID to the device
def prepare_sender(self):
    global nextId
    ID = nextId
    nextId += 1
    strId = str(ID)
    receivedData[strId] = {'text': [], 'log': [], "file": [], "calendar": [], "contact":[], "audio": []}
    
    self.send_response(200)
    self.end_headers()
    self.wfile.write(bytes(strId, "utf-8"))
    print("Sent ID: " + strId)

# Saves some data in memory
def saveData(self):
    global receivedData
    length = int(self.headers['Content-Length']) # <--- Gets the size of data
    data = self.rfile.read(length) # <--- Gets the data itself
    try:
        qs = parse.parse_qs(data)
        ID = qs[bytes('ID', 'utf-8')]
        Type = qs[bytes('type', 'utf-8')]
        Msg = qs[Type[0]]

        ID = ID[0].decode('utf-8')
        Type = Type[0].decode('utf-8')
        Msg = Msg[0].decode('utf-8')

        print(Type + " received")

        if(Type == 'debug'):
            print("debug")
        else:
            try:
                idData = receivedData[ID]
                idData[Type].append(Msg)
            except:
                print("Unexpected error:", sys.exc_info()[0])
                pass
    except:
        print("error")
        print(data)
        pass
    self.send_response(200)
    self.end_headers()
    self.wfile.write(bytes("", "utf-8"))

# class that handles HTTP requests
class MyServer(BaseHTTPRequestHandler):
    def _set_headers(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()

    def do_GET(self):
        
        path = self.path.split('&')[0]
        
        if(path == '/initiateclient'):
            prepare_sender(self)
            
        elif(path == '/getResults'):
            ID = self.path.split('&')[1]
            ID.split('=')[1]
            c = getClassification(ID)
            self.send_response(200)
            self.end_headers()
            self.wfile.write(bytes(c, "utf-8"))
            
    def do_HEAD(self):
        self._set_headers()
        
    def do_POST(self):
        saveData(self)

# Build server
s = HTTPServer((hostName, hostPort), MyServer)

# Listens for requests until keyboard interrupt
try:
    print("Server running on at " + str(hostName) + ":" + str(hostPort))
    s.serve_forever()
except KeyboardInterrupt:
    pass

s.server_close()

#_________________________________________________________________________________________________


