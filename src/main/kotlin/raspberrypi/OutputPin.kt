package raspberrypi


class OutputPin : Pin {
    private var high = false

    constructor(pinNumber: Int) : super(pinNumber) {
        defineOutput(isHigh())
    }

    constructor(pinNumber: Int, high: Boolean) : super(pinNumber) {
        defineOutput(high)
    }

    private fun defineOutput(high: Boolean) {
        execute(SET + pinNumber + DEFINE)
        if (high) setHigh() else setLow()
    }

    fun setHigh() {
        execute(SET + pinNumber.toString() + SET_HIGH)
        high = true
    }

    fun setLow() {
        execute(SET + pinNumber.toString() + SET_LOW)
        high = false
    }

    fun toggle() {
        if (isHigh()) setLow() else setHigh()
    }

    fun isHigh(): Boolean {
        val input = execute(GET + pinNumber)
        val level = input[input.indexOf("=") + 1]
        return level != '0'
    }
}