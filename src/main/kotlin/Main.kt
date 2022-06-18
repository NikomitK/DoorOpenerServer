import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.*
import mu.KotlinLogging
import org.jnativehook.GlobalScreen
import org.jnativehook.keyboard.NativeKeyEvent
import org.jnativehook.keyboard.NativeKeyListener
import org.mindrot.jbcrypt.BCrypt
import raspberrypi.OutputPin
import java.io.File
import java.net.ServerSocket
import java.net.SocketException
import java.time.LocalDate
import java.util.logging.Level
import java.util.logging.Logger
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLServerSocketFactory
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.random.nextInt

val scope = MainScope()
lateinit var storage: Storage
lateinit var serverSocket: ServerSocket

val storagePath: File = File("storage")
val storageFile: File = File(storagePath.path + File.separator + "storageFile.json")
val certificatePath: File = File("certificates")
val certCredentialsFile: File = File(certificatePath.path + File.separator + "credentialsFile.json")
val outputPin: OutputPin = OutputPin(26, false)
val logger = KotlinLogging.logger {}
val gsonPretty: Gson =
    GsonBuilder().setPrettyPrinting().registerTypeAdapter(LocalDate::class.java, LocalDateAdapter()).create()
var keyListener = object : NativeKeyListener {
    var keypadCode: String = ""
    override fun nativeKeyTyped(p0: NativeKeyEvent?) {
        // not needed
    }

    override fun nativeKeyPressed(nativeKeyEvent: NativeKeyEvent) {
        val pressedKeyText = NativeKeyEvent.getKeyText(nativeKeyEvent.keyCode)
        when (nativeKeyEvent.keyCode) {
            3658 -> {
                println("Minus")
                keypadCode = ""
            }
            3662 -> println("Plus")
            28 -> {
                println("Enter")

                with(keypadCode) {
                    scope.launch(Dispatchers.IO) {
                        verifyKeypadCode(this@with)
                    }
                }
                keypadCode = ""
            }
            else -> if (pressedKeyText.length == 1) {
                keypadCode += pressedKeyText
            }
        }
    }

    override fun nativeKeyReleased(p0: NativeKeyEvent?) {
        // not needed
    }
}

fun checkpwSafe(plainText: String, hashed: String): Boolean {
    return try {
        BCrypt.checkpw(plainText, hashed)
    } catch (illegalArgument: IllegalArgumentException) {
        logger.error { "Stored Pin is invalid, please reset" }
        false
    }
}

fun main(args: Array<String>) {

    println("Hello World!")
    println("Program arguments: ${args.joinToString()}")

    initStorage()

    // Keypad stuff
    // the next line is to prevent the console from being spammed
    Logger.getLogger(GlobalScreen::class.java.getPackage().name).level = Level.OFF

    GlobalScreen.registerNativeHook()
    switchKeypad()

    thread {
        while (true) {
            if (storage.isKeypadEnabled) {
                Thread.sleep(5000)
                continue
            }
            when (readln()) {
                "usetls=true" -> {
                    if (!storage.useTls) println("Switched TLS mode to on") else println("TLS mode is already on")
                    storage.useTls = true
                }
                "usetls=false" -> {
                    if (storage.useTls) println("Switched TLS mode to off") else println("TLS mode is already off")
                    storage.useTls = false
                }
                "reset" -> {
                    println("Reset")
                    doReset()
                }
                "showpin" -> {
                    println("Pin: ${storage.pin}")
                }
            }
            println("when done")
        }
    }

    // Network stuff
    serverSocket = createServerSocket()

    thread {
        var secureServerSocket: SSLServerSocket? = null
        while (true) {
            if (secureServerSocket == null) {
                secureServerSocket = createSecureServerSocket()
            }
            if (secureServerSocket == null || !storage.useTls) {
                Thread.sleep(5000)
                continue
            }

            val socket = secureServerSocket.accept()
            scope.launch(Dispatchers.IO) {
                try {
                    println("Secure connection from: " + socket.inetAddress)
                    socket.soTimeout = 1500
                    ConnectionHandler.handleConnection(socket)

                } catch (_: SocketException) { // useless
                } catch (_: SSLHandshakeException) { // useless
                }
            }

        }
    }

    while (true) {
        try {
            val socket = serverSocket.accept()
            scope.launch(Dispatchers.IO) {
                println("Connection from: " + socket.inetAddress)
                socket.soTimeout = 1500
                ConnectionHandler.handleConnection(socket)
            }
        } catch (_: SocketException) { // useless
        }

    }
}


fun initStorage() {
    storagePath.mkdir()
    storageFile.createNewFile()
    storage = if (storageFile.readText().isNotEmpty()) {
        try {
            gsonPretty.fromJson(storageFile.readText(), Storage::class.java)
        } catch (ignore: Exception) {
            logger.debug { "Storage file error" }
            Storage()
        }
    } else {
        logger.debug { "No storage file" }
        Storage()
    }
}

fun switchKeypad() {
    if (!storage.isKeypadEnabled) {
        unregisterKeypad()
    } else {
        registerKeypad()
    }
}

/**
 * creates a server socket running
 * on port 5687
 * @return a ServerSocket
 */
fun createServerSocket(): ServerSocket {
    return ServerSocket(5687)
}

/**
 * Tries to create an SSLServerSocket
 * with a given certificate and password
 * for it
 * @return an SSLServerSocket if the certificate is valid, else null
 */
fun createSecureServerSocket(): SSLServerSocket? {
    if (!storage.useTls) return null
    if (checkCertificateCredentials() && useCertificateCredentials()) {
        val serverSocket = SSLServerSocketFactory.getDefault().createServerSocket(5688) as SSLServerSocket
        serverSocket.enabledProtocols = arrayOf("TLSv1.3")
        serverSocket.enabledCipherSuites = arrayOf("TLS_AES_256_GCM_SHA384")
        return serverSocket
    }
    return null
}

/**
 * checks if the user supplied credentials for the certificate
 */
fun checkCertificateCredentials(): Boolean {
    certificatePath.mkdir()
    if (!certCredentialsFile.exists()) {
        certCredentialsFile.createNewFile()
        certCredentialsFile.writeText(gsonPretty.toJson(CertificateCredentials("", "")))
        return false
    }
    return true
}

/**
 * tries using the supplied keystore name and password
 * @return true if everything worked properly, else false
 */
fun useCertificateCredentials(): Boolean {
    return try {
        val certCreds = gsonPretty.fromJson(certCredentialsFile.readText(), CertificateCredentials::class.java)
        if (certCreds.keystoreName.isNotBlank() && certCreds.keystorePassword.isNotBlank()) {
            System.setProperty(
                "javax.net.ssl.keyStore", certificatePath.path + File.separator + certCreds.keystoreName
            )
            System.setProperty("javax.net.ssl.keyStorePassword", certCreds.keystorePassword)
            true
        } else false
    } catch (e: Exception) {
        false
    }
}

// Gson throws an error when serializing LocalDate/Time without this
internal class LocalDateAdapter : TypeAdapter<LocalDate?>() {

    override fun write(jsonWriter: JsonWriter, localDate: LocalDate?) {
        if (localDate == null) {
            jsonWriter.nullValue()
        } else {
            jsonWriter.value(localDate.toString())
        }
    }

    override fun read(jsonReader: JsonReader): LocalDate? {
        return if (jsonReader.peek() == JsonToken.NULL) {
            jsonReader.nextNull()
            null
        } else {
            LocalDate.parse(jsonReader.nextString())
        }
    }
}

fun generateToken(): String {
    val stringToken: String = Random.nextInt(IntRange(1000000, 9999999)).toString()
    storage.tokens[stringToken] = LocalDate.now().plusDays(30)
    return stringToken
}

fun doReset() {
    storage = Storage()
    storage.save(storageFile)
    File("LogFile.log").writeText("")
}

suspend fun doOpen(time: Int) = coroutineScope {
    launch(Dispatchers.IO) {
        outputPin.setHigh()
        delay(time.toLong() * 1000)
        outputPin.setLow()
    }
}

fun renewToken(token: String) {
    storage.tokens[token] = LocalDate.now().plusDays(30)
}

suspend fun open(time: Int) = coroutineScope {
    launch(Dispatchers.IO) {
        outputPin.setHigh()
        delay(time.toLong() * 1000)
        outputPin.setLow()
    }
}

fun registerKeypad() {
    GlobalScreen.addNativeKeyListener(keyListener)
}

fun unregisterKeypad() {
    GlobalScreen.removeNativeKeyListener(keyListener)
}

suspend fun verifyKeypadCode(keypadCode: String) = coroutineScope {
    if (storage.pin?.let {
            checkpwSafe(keypadCode, it)
        } == true) {
        launch { open(storage.keypadTime) }
        logger.info { "The door was opened by the keypad" }
        return@coroutineScope
    }
    logger.warn { "Someone used a wrong pin at the keypad" }
}

fun Storage.toJson(): String = gsonPretty.toJson(this)


fun Storage.save(file: File) = file.writeText(toJson())