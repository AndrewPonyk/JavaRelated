[1] What is gRPC? - gRPC is an open-source remote procedure call (RPC) framework originally developed by Google.

[2] What is the difference between gRPC and REST? - gRPC uses the HTTP/2 protocol, while REST uses the HTTP/1.1 protocol.
gRPC uses protocol buffers for serialization, while REST uses JSON or XML. gRPC is more suitable for high-performance,
 low-latency applications, while REST is more flexible and widely supported.

[3] What programming languages are supported by gRPC? - gRPC supports many programming languages, including Java, C++, Python, Ruby, JavaScript, Objective-C, and more.

[4] What is a gRPC service? - A gRPC service is defined using a .proto file, which contains the service definition and message types.

[5] What is a gRPC client? - A gRPC client is a client application that communicates with a gRPC server to send and receive messages.

[6] What is a gRPC server? - A gRPC server is a server application that listens for incoming messages and responds to them.

[7] What is protocol buffers? - Protocol Buffers is a language-agnostic serialization format developed by Google. (data FORMAT !!!!!)
 It is used to define the message types and the structure of the data exchanged between the client and the server.

[8] What are the advantages of using gRPC? - Some of the advantages of using gRPC are: high performance, support for multiple programming languages,
support for bidirectional streaming, support for flow control, and support for authentication and encryption.

[9] What is the difference between unary and streaming RPCs? - A unary RPC is a one-to-one request/response interaction,
while a streaming RPC is a one-to-many or many-to-one interaction, where the client and server can send multiple messages back and forth.

[10] What is the purpose of a stub in gRPC? - A stub is a client-side proxy that abstracts the communication with the server,
allowing the client to call the server methods as if they were local methods.

[11] What is the role of the gRPC Channel? - The gRPC Channel is used to establish a connection to the gRPC server, and to configure the transport layer settings, such as the maximum message size, the compression algorithm, and the authentication credentials.

[12] What is the role of the gRPC Interceptor? - The gRPC Interceptor is a middleware component that can intercept the requests and responses between the client and server, and add additional functionality, such as logging, authentication, and monitoring.

[13] How does gRPC support authentication and authorization? - gRPC supports authentication and authorization using various mechanisms, such as TLS/SSL, OAuth2, JWT, and custom authentication schemes.

[14] What is the role of a gRPC Load Balancer? - A gRPC Load Balancer is used to distribute the client requests across multiple servers, in order to improve the scalability, availability, and fault tolerance of the system.

[15] What is the role of the gRPC Name Resolver? - The gRPC Name Resolver is used to resolve the server address and port, based on the server name or service name.

[16] How does gRPC handle errors and status codes? - gRPC uses status codes to indicate the success or failure of a request, and to provide additional information about the error, such as the error message and the error details.

[17] What is the role of the gRPC Deadline? - The gRPC Deadline is a timeout value that specifies how long the client is willing to wait for the server to respond. If the server does not respond within the deadline, the client can cancel the request.

[21] What are the types of serialization supported by gRPC?

gRPC supports two types of serialization: Protocol Buffers and JSON.
[22] What is the difference between unary and streaming RPCs?

Unary RPCs involve a single request and response message, whereas streaming RPCs involve a stream of request or response messages, or both.
[23] Can gRPC be used with languages other than C++, Java, and Python?

Yes, gRPC supports a wide range of programming languages, including Go, Ruby, C#, and many others.
[24] How is error handling done in gRPC?

gRPC uses status codes to indicate the success or failure of a request, along with metadata that can provide additional information about the error.
[25] What is the role of protobuf in gRPC?

Protocol Buffers (protobuf) is a data serialization format used by gRPC to encode messages in a compact and efficient binary format.
[26] Can gRPC be used for serverless applications?

Yes, gRPC can be used in serverless environments like AWS Lambda or Google Cloud Functions.
[27] What are gRPC interceptors?

gRPC interceptors are middleware components that can be used to intercept and modify gRPC requests and responses.
[28] How does gRPC handle load balancing?

gRPC uses a client-side load balancing approach, where the client is responsible for selecting a server to send each request to.
[29] Can gRPC be used over the internet?

Yes, gRPC can be used over the internet, as long as the network is reliable and supports the required protocols.
[30] What is the difference between gRPC and REST?

gRPC uses a binary protocol and offers better performance and scalability, while REST uses a text-based protocol and is easier to integrate with existing web technologies.