import data_pb2
import storage

class demo:
   def run(self):
     print '\nA demonstration of storing protobuf data\n'

     person = data_pb2.Person()
     person.id = 'doe1'
     person.firstName = 'Jane'
     person.lastName = 'Doe'

     contact = person.contacts.add()
     contact.id = 'doe1.email'
     contact.type = 'email'
     contact.value = 'jane.doe@email.com'

     # save the message to a file
     s = storage.asfile()
     s.store(person,'./person.dat')

     # not to load it and check if they are the same
     person2 = s.load('./person.dat')

     if person == person2:
       print 'they are the same!'
     else:
       print 'they are NOT the same!'

if __name__=="__main__":
   d = demo()
   d.run();
