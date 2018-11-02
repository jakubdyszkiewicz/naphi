# Not A Production HTTP Implementation

This is a partial HTTP implementation that I describe on [my blog](http://dyszkiewicz.me).
Like the title said, it should not be used on production environment.

## Usage

The library is not pushed to the Maven Central, so you have to build it and push it to Maven Local.
To do that you run
```
./gradlew clean build publishToMavenLocal
```
Then you can add dependency to your `build.gradle`
```
compile group: 'org.naphi', name: 'naphi', version: '0.1-SNAPSHOT'
```

### The Server

To start the server, create new `Server` instance and pass port and the handler - function for processing the request
```
Server(port = 8090, handler = {
    Response(Status.OK)
})
```

### The Client

To use the client, create new instance of `SocketClient`

```java
val client = SocketClient(
                keepAliveTimeout = Duration.ofMillis(1000),
                checkKeepAliveInterval = Duration.ofMillis(100),
                socketTimeout = Duration.ofSeconds(2))
```

and use it

```java
var response = client.exchange(
                url = "http://localhost:8090",
                request = Request(
                        path = "/",
                        method = RequestMethod.POST,
                        headers = HttpHeaders("content-length" to "0")))
```

There are also alternative clients:
* `ApacheHttpClient` - a client that is based on Apache HTTP Client
* `HttpUrlConnectionClient` - a client that is based on JDK's `HttpURLConnection`

## Build
To build project with tests run
```
./gradlew clean build
```
