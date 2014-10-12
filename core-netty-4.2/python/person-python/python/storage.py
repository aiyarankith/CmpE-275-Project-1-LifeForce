import data_pb2

class asfile:

  def __init__(self):
    print 'init storage'

  def ping(self):
    print 'ping'

  def store(self, person, thefile):
    print 'storing to',thefile
    f = open(thefile,'wb')
    f.write(person.SerializeToString())

  def load(self, thefile):
    print 'loading the file', thefile
    f = open(thefile,'rb')
    person = data_pb2.Person()
    person.ParseFromString(f.read())
    return person
