# SecureProcotol
## Intro
This project implements a chat protocol, client and server application using Java. It provides a simple and robust way for users to create, join and leave chat groups, as well as send and receive messages securely with AES encryption.
## Configuration
### Server Configuration
The server can be started by running the Server class, the port number is set within this class:
```
java Server.java localhost 12345 uesrname
```
```
public static void main(String[] args) throws IOException {
    ChatServer server = new ChatServer(12345); // Here, the port number is set to 12345
    server.start();
}
```
### Client Configuration
The client requires command-line arguments, which are:
- host: The server host to connect to.
- port: The port of the server to connect to.
- username: The user's username.
```
java Client.java localhost 12345 uesrname
```
**Note** :  For a successful connection, you need to pre-configure the username and password. Here are the necessary steps to perform the setup:
- Create or just open a text file named user.txt. This file should be created in the same directory where your ChatClient class resides.
- Open user.txt file and add your username and password. The format should be username|password. An example of this is as follows:
```
uesrname|password
```
- Save and close user.txt file.
**Improtant**
- Do not include spaces before or after the pipe (|) symbol.
- Ensure the user.txt file is always up-to-date with correct user credentials before starting the ChatClient.
- The username and password should not contain the pipe (|) symbol as it is used as a separator.
- Be cautious about the security of your user.txt file. It contains sensitive information (username and password), and it should be properly secured.


After starting, the client will ask for the password on the console.
## Project Overview
- This project is composed of the following main components:

- **Message**: This class defines the format of the messages exchanged between the client and the server. It also includes methods to encrypt and decrypt the payload of the messages using AES encryption.

- **ChatClient**: This class represents the client side of the chat protocol. It includes methods to start a connection, send messages, and handle received messages.

- **ChatServer**: This class represents the server side of the chat protocol. It can handle multiple clients concurrently, receiving and sending messages, and manage the chat groups.

- **Client and Server classes**: Entry point classes to start the client and server applications.
## Extra Credit Options

- **Robustness:** The chat protocol is designed with a state machine model to ensure correctness. The server maintains the state of each client and the transition between states is clearly defined. Additionally, the client and server implement robust error handling to deal with network failures or malformed messages.
- **Concurrent Server**: The server can handle multiple clients concurrently using a thread-per-client model.
- **Design**: The code is organized with clarity and maintainability in mind. Each class has a single responsibility and the methods are well-documented. Furthermore, the encryption of message payloads ensures that the chat is secure.
- **Using a Systems Programming Language**: This project was implemented using Java, a high-level language. Despite this, the implementation goes beyond the minimum requirements by providing features like AES encryption and multi-threading.
- **Automated testing code**: Junit test for Message.java and ChatClientHandler
- **Working with a cloud-based git-based system**: The project is managed and versioned using Git
To further understand the structure and functionality of the project, you can refer to the code comments and documentation within the project files.




