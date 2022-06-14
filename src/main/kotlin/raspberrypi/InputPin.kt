package raspberrypi

class InputPin(pinNumber: Int) : Pin(pinNumber) {

    val isHigh: Boolean
        get() {
            val input = execute(GET + pinNumber)
            println(input)
            val level = input[input.indexOf("=") + 1]
            println(level)
            return level != '0'
        }
}