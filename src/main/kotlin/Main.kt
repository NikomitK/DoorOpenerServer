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
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.random.nextInt


var storage: Storage = Storage()

fun main(args: Array<String>) {
    // TODO handle remaining cases (numpad open, change pin, reset device, delete otp, use otp(integrate in open)), store pin hashed, store in file
    println("Hello World!")

    println("Program arguments: ${args.joinToString()}")

    val serverSocket: ServerSocket = ServerSocket(5687)
    while(true){
        val socket = serverSocket.accept()
        socket.soTimeout = 1500
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
                    if(storage.otps.contains(message.token)) { // add otps to storage and check if stored, maybe add expiration/start date
                        open()
                        removeOtp(message.token!!.toInt())
                        response.text = "Successfully used OTP! :D"
                        response.internalMessage = "success"
                    } else {
                    response.text = "Invalid token! :C"
                    response.internalMessage = "invalid token"
                    }
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
            } else if(message.type == "otpAdd") {
                if(storage.tokens.contains(message.token)) {
                    if (storage.tokens[message.token]!!.isBefore(LocalDateTime.now())) {
                        response.text = "Expired token. Login again :C"
                        response.internalMessage = "invalid token"
                        storage.tokens.remove(message.token!!)
                    } else {
                        val tempOtp: Otp = Gson().fromJson(message.content, Otp::class.java)
                        storage.otps[tempOtp.pin] = LocalDate.parse(tempOtp.expirationDate)
                        renewToken(message.token!!)
                        response.text = "Saved OTP! :D"
                        response.internalMessage = "success"
                    }
                } else {
                    response.text = "Invalid token! :C"
                    response.internalMessage = "invalid token"
                }
            } else if(message.type == "otpRemove"){
                if(storage.tokens.contains(message.token)) {
                    if (storage.tokens[message.token]!!.isBefore(LocalDateTime.now())) {
                        response.text = "Expired token. Login again :C"
                        response.internalMessage = "invalid token"
                        storage.tokens.remove(message.token!!)
                    } else {
                        storage.otps.remove(Gson().fromJson(message.content, Otp::class.java).pin)
                        renewToken(message.token!!)
                        response.text = "Removed OTP! :O"
                        response.internalMessage = "success"
                    }
                } else {
                    response.text = "Invalid token! :C"
                    response.internalMessage = "invalid token"
                }
            } else if(message.type == "keepLogs") {
                if(storage.tokens.contains(message.token)) {
                    if (storage.tokens[message.token]!!.isBefore(LocalDateTime.now())) {
                        response.text = "Expired token. Login again :C"
                        response.internalMessage = "invalid token"
                        storage.tokens.remove(message.token!!)
                    } else {
                        storage.keepLogs = message.content!!.lowercase().contains("true")
                        renewToken(message.token!!)
                        response.text = "Saved preference! :D"
                        response.internalMessage = "success"
                    }
                } else {
                    response.text = "Invalid token! :C"
                    response.internalMessage = "invalid token"
                }
            } else if(message.type == "changePin") {
                if(storage.tokens.contains(message.token)) {
                    if (storage.tokens[message.token]!!.isBefore(LocalDateTime.now())) {
                        response.text = "Expired token. Login again :C"
                        response.internalMessage = "invalid token"
                        storage.tokens.remove(message.token!!)
                    } else {
                        storage.pin = Integer.parseInt(message.content)
                        storage.tokens.clear()
                        renewToken(message.token!!)
                        response.text = "Changed pin! :D"
                        response.internalMessage = "success"
                    }
                } else {
                    response.text = "Invalid token! :C"
                    response.internalMessage = "invalid token"
                }
            } else if(message.type == "globalLogout") {
                if(storage.tokens.contains(message.token)) {
                    if (storage.tokens[message.token]!!.isBefore(LocalDateTime.now())) {
                        response.text = "Expired token. Login again :C"
                        response.internalMessage = "invalid token"
                        storage.tokens.remove(message.token!!)
                    } else {
                        storage.tokens.clear()
                        response.text = "Logged everyone out! :/"
                        response.internalMessage = "success"
                    }
                } else {
                    response.text = "Invalid token! :C"
                    response.internalMessage = "invalid token"
                }
            } else if(message.type == "reset") {
                if(storage.tokens.contains(message.token)) {
                    if (storage.tokens[message.token]!!.isBefore(LocalDateTime.now())) {
                        response.text = "Expired token. Login again :C"
                        response.internalMessage = "invalid token"
                        storage.tokens.remove(message.token!!)
                    } else {
                        storage = Storage()
                        response.text = "Ready for a new start :D"
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
    storage.tokens[token] = LocalDateTime.now().plusDays(30)
}

fun removeOtp(otp: Int) {

}

fun open() {
    println("OPEN!")
}