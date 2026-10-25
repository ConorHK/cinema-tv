package com.danielportelabyrne.cinema.desktop.input

import com.danielportelabyrne.cinema.platform.Log
import com.studiohartman.jamepad.ControllerManager
import com.studiohartman.jamepad.ControllerState

/** One frame of a pad, reduced to what the app listens for. */
data class PadFrame(
    val up: Boolean = false, val down: Boolean = false, val left: Boolean = false, val right: Boolean = false,
    val a: Boolean = false, val b: Boolean = false, val x: Boolean = false, val y: Boolean = false,
    val lb: Boolean = false, val rb: Boolean = false, val start: Boolean = false, val view: Boolean = false,
) {
    fun held(action: NavAction): Boolean = when (action) {
        NavAction.UP -> up; NavAction.DOWN -> down; NavAction.LEFT -> left; NavAction.RIGHT -> right
        NavAction.SELECT -> a; NavAction.BACK -> b; NavAction.ALT -> x; NavAction.SEARCH -> y
        NavAction.PREV -> lb; NavAction.NEXT -> rb; NavAction.MENU -> start; NavAction.INFO -> view
    }

    companion object {
        /** How far a stick has to lean before it counts as a D-pad press. */
        const val STICK = 0.55f

        /** Jamepad passes SDL's raw axes through: stick Y is negative when pushed up. */
        fun of(s: ControllerState) = PadFrame(
            up = s.dpadUp || s.leftStickY < -STICK, down = s.dpadDown || s.leftStickY > STICK,
            left = s.dpadLeft || s.leftStickX < -STICK, right = s.dpadRight || s.leftStickX > STICK,
            a = s.a, b = s.b, x = s.x, y = s.y, lb = s.lb, rb = s.rb, start = s.start, view = s.back,
        )
    }
}

/**
 * Turns successive frames of one pad into actions: a press fires once, and a held direction (or a held page button)
 * repeats after [firstRepeatMs], then every [repeatMs], the way a TV remote does. Buttons that act (A, B, Start...)
 * never repeat, so holding A cannot start a film twice.
 */
class PadRepeater(private val firstRepeatMs: Long = 380, private val repeatMs: Long = 90) {
    private val heldSince = HashMap<NavAction, Long>()
    private val lastFired = HashMap<NavAction, Long>()

    fun step(frame: PadFrame, now: Long): List<NavAction> {
        val out = ArrayList<NavAction>()
        for (action in NavAction.entries) {
            if (!frame.held(action)) { heldSince.remove(action); lastFired.remove(action); continue }
            val since = heldSince[action]
            if (since == null) {
                heldSince[action] = now; lastFired[action] = now; out += action
            } else if (repeats(action) && now - since >= firstRepeatMs && now - (lastFired[action] ?: now) >= repeatMs) {
                lastFired[action] = now; out += action
            }
        }
        return out
    }

    /** Clears held state, so a button already down when the player closes does not fire again. */
    fun reset() { heldSince.clear(); lastFired.clear() }

    private fun repeats(action: NavAction) = action.isMove || action == NavAction.PREV || action == NavAction.NEXT
}

/**
 * Reads every connected game controller through SDL (Jamepad) on a background thread, about 60 times a second, and
 * hands the resulting actions to [onAction]. SDL is given no window, so it keeps reading while mpv has the screen.
 * A missing or broken SDL just means no gamepad: the keyboard still works.
 */
class Gamepads(private val onAction: (NavAction) -> Unit) {
    @Volatile private var running = false
    private var thread: Thread? = null
    private val repeaters = HashMap<Int, PadRepeater>()

    /** Names of the pads seen on the last frame, for Settings. */
    @Volatile var connected: List<String> = emptyList()
        private set

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "gamepads").apply { isDaemon = true; start() }
    }

    fun stop() { running = false }

    /** Forgets what is held, e.g. when the player takes or gives back the screen. */
    fun resetHeld() = synchronized(repeaters) { repeaters.values.forEach { it.reset() } }

    private fun loop() {
        val manager = try {
            ControllerManager(com.studiohartman.jamepad.Configuration().apply { maxNumControllers = MAX_PADS }).apply { initSDLGamepad() }
        } catch (e: Throwable) {
            Log.w(TAG, "No gamepad support: ${e.javaClass.simpleName}: ${e.message}")
            running = false
            return
        }
        Log.i(TAG, "Gamepad support ready")
        try {
            while (running) {
                try { manager.update() } catch (_: Throwable) {}
                val names = ArrayList<String>()
                val now = System.currentTimeMillis()
                for (i in 0 until MAX_PADS) {
                    val state = try { manager.getState(i) } catch (_: Throwable) { null } ?: continue
                    if (!state.isConnected) { synchronized(repeaters) { repeaters.remove(i) }; continue }
                    names += state.controllerType ?: "Controller"
                    val actions = synchronized(repeaters) { repeaters.getOrPut(i) { PadRepeater() }.step(PadFrame.of(state), now) }
                    actions.forEach(onAction)
                }
                connected = names
                Thread.sleep(16)
            }
        } catch (_: InterruptedException) {
        } finally {
            try { manager.quitSDLGamepad() } catch (_: Throwable) {}
        }
    }

    private companion object {
        const val TAG = "Gamepad"
        const val MAX_PADS = 4
    }
}
