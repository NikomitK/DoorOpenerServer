import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import messagetypes.Message
import messagetypes.Response
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.random.nextInt


var storage: Storage = Storage()

fun main(args: Array<String>) {
    // TODO remove expired tokens, renew tokens, handle remaining cases (invalid token, open, save numpad stuff, numpad open), store pin hashed, store in file
    println("Hello World!")

    println("Program arguments: ${args.joinToString()}")

    val serverSocket: ServerSocket = ServerSocket(5687)
    while(true){
        val socket = serverSocket.accept()
        GlobalScope.launch {
            println("Connection from: " + socket.inetAddress)
            val message: Message = Gson().fromJson(BufferedReader(InputStreamReader(socket.getInputStream())).readLine(), Message::class.java)
            println(message)
            val response = Response()
            if(message.type == "login") {
                if(message.isNewDevice!!) {
                    if(storage.pin == null){
                        storage.pin = message.pin
                        response.text = "Success!"
                        response.internalMessage = generateToken()
                    } else {
                        response.text = "Not a new device!"
                    }
                } else {
                    if(message.pin == storage.pin && storage.pin != null) {
                        response.text = "Success!"
                        response.internalMessage = generateToken()
                    } else {
                        response.text = "Wrong pin!"
                    }
                }
            } else if(message.type == "open") {
                if(storage.tokens.contains(message.token)) {
                    if(storage.tokens[message.token]!!.isBefore(LocalDateTime.now())) {
                        response.text = "Expired token. Login again"
                        response.internalMessage = "invalid token"
                        storage.tokens.remove(message.token!!)
                    } else {
                        open()
                        renewToken(message.token!!)
                        response.text = "Success!"
                        response.internalMessage = "success"
                    }
                } else {
                    response.text = "Invalid token! :C"
                    response.internalMessage = "invalid token"
                }
            } else if(message.type == "keypadConfig") {
                if(storage.tokens.contains(message.token)) {
                    if (storage.tokens[message.token]!!.isBefore(LocalDateTime.now())) {
                        response.text = "Expired token. Login again :C"
                        response.internalMessage = "invalid token"
                        storage.tokens.remove(message.token!!)
                    } else {
                        storage.isKeypadEnabled = (message.content!!.toInt() in 1 until 10)
                        storage.keypadTime = message.content.toInt()
                        renewToken(message.token!!)
                        response.text = "Saved config! :D"
                        response.internalMessage = "success"
                    }
                } else {
                        response.text = "Invalid token! :C"
                        response.internalMessage = "invalid token"
                    }
            }

            PrintWriter(socket.getOutputStream(), true).println(Gson().toJson(response))
            withContext(Dispatchers.IO) {
                socket.close()
            }
        }
    }
}

fun generateToken(): String {
    val stringToken: String = Random.nextInt(IntRange(1000000, 9999999)).toString()
    storage.tokens[stringToken] = LocalDateTime.now().plusDays(30)
    return stringToken
}

fun renewToken(token: String) {

}

fun open() {
    println("OPEN!")
}

fun isBetween(number: Int, start: Int, end: Int): Boolean {
    return (number in (start + 1) until end)
}