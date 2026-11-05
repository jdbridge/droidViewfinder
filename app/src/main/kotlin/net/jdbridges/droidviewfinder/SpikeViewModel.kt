package net.jdbridges.droidviewfinder

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.util.Log
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "droidViewfinder"

/** What the user picked for one input: UVC format type, resolution and frame rate. */
private data class Mode(val type: Int, val width: Int, val height: Int, val fps: Int)

/**
 * Opens a UVC device through UVCAndroid and exposes what the camera reports as three linked choices:
 * type (format), resolution and frame rate. The last choice for each input is remembered and restored
 * when the input is opened again. Not the final architecture (see docs/DESIGN.md).
 */
class SpikeViewModel(private val app: Application) : AndroidViewModel(app) {
    var devices by mutableStateOf<List<UsbDevice>>(emptyList())
        private set
    var opened by mutableStateOf<UsbDevice?>(null)
        private set
    var modes by mutableStateOf<List<Size>>(emptyList())
        private set
    var current by mutableStateOf<Size?>(null)
        private set

    /** True while the USB device is being reset and reopened; the viewer stays up and shows a notice. */
    var resetting by mutableStateOf(false)
        private set

    private var surface: Surface? = null
    private var previewing = false

    // Bumped whenever a helper is replaced, so callbacks from a released helper are ignored.
    private var generation = 0
    private var helper: ICameraHelper = newHelper()

    /** The mode to restore when the device opens: set during a reset, otherwise read from the saved choice. */
    private var pendingMode: Mode? = null
    private var resetJob: Job? = null

    private val prefs = app.getSharedPreferences("last_modes", Context.MODE_PRIVATE)

    init {
        refreshDevices()
    }

    private fun newHelper(): ICameraHelper {
        val gen = ++generation
        return CameraHelper().also { it.setStateCallback(StateCallback(gen)) }
    }

    fun refreshDevices() {
        devices = helper.deviceList.orEmpty()
    }

    /** Asks for the USB permission (the library shows the system dialog) and, once granted, opens the device. */
    fun select(device: UsbDevice) = helper.selectDevice(device)

    /** Types the camera offers, in the order it reports them. */
    val types: List<Int>
        get() = modes.map { it.type }.distinct()

    /** Resolutions available for [type], largest first. */
    fun resolutions(type: Int): List<Pair<Int, Int>> =
        modes.filter { it.type == type }.map { it.width to it.height }.distinct()
            .sortedByDescending { (w, h) -> w.toLong() * h }

    /** Frame rates available for [type] at [width] x [height], fastest first. */
    fun frameRates(type: Int, width: Int, height: Int): List<Int> {
        val m = modes.firstOrNull { it.type == type && it.width == width && it.height == height } ?: return emptyList()
        return rates(m)
    }

    private fun rates(m: Size) = (m.fpsList.orEmpty() + m.fps).filter { it > 0 }.distinct().sortedDescending()

    fun selectType(type: Int) {
        val cur = current ?: return
        val res = resolutions(type)
        val (w, h) = if (cur.width to cur.height in res) cur.width to cur.height else res.firstOrNull() ?: return
        apply(type, w, h, cur.fps)
    }

    fun selectResolution(width: Int, height: Int) {
        val cur = current ?: return
        apply(cur.type, width, height, cur.fps)
    }

    fun selectFps(fps: Int) {
        val cur = current ?: return
        apply(cur.type, cur.width, cur.height, fps)
    }

    /** Switches to the mode for these values, keeping [wantedFps] if the mode supports it, else its fastest rate. */
    private fun apply(type: Int, width: Int, height: Int, wantedFps: Int) {
        if (resetting) return
        val size = resolve(modes, Mode(type, width, height, wantedFps)) ?: return
        setMode(size)
        opened?.let { save(it, size) }
    }

    /** The camera's own mode entry for [want] (same type and resolution) with the frame rate fixed up, or null. */
    private fun resolve(list: List<Size>, want: Mode): Size? {
        val base = list.firstOrNull { it.type == want.type && it.width == want.width && it.height == want.height } ?: return null
        val available = rates(base)
        val fps = if (want.fps in available) want.fps else available.firstOrNull() ?: base.fps
        return base.clone().also { it.fps = fps }
    }

    private fun setMode(size: Size) {
        helper.stopPreview()
        helper.setPreviewSize(size)
        helper.startPreview()
        previewing = true
        current = size
        surface?.let { helper.addSurface(it, false) }
    }

    // --- remembered choice per input -------------------------------------------------------------

    // Vendor:product plus serial number when the device has one (readable once USB permission is granted),
    // so two identical cards with different serials keep separate choices.
    private fun key(d: UsbDevice): String {
        val serial = runCatching { d.serialNumber }.getOrNull().orEmpty()
        return "%04x:%04x:%s".format(d.vendorId, d.productId, serial)
    }

    private fun save(d: UsbDevice, s: Size) {
        prefs.edit().putString(key(d), "${s.type},${s.width},${s.height},${s.fps}").apply()
    }

    private fun saved(d: UsbDevice): Mode? {
        val p = prefs.getString(key(d), null)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return null
        return if (p.size == 4) Mode(p[0], p[1], p[2], p[3]) else null
    }

    // --- reset -----------------------------------------------------------------------------------

    /**
     * Resets the open USB device and reopens it in the same mode. Releases the camera, tries a port-level
     * reset (the kernel's USB device reset, through the hidden `UsbDeviceConnection.resetDevice()`), and
     * then reopens with a fresh helper. If the port-level reset is not available the release-and-reopen
     * alone is the soft reset: it stops streaming, closes the UVC interfaces and re-runs the open sequence.
     */
    fun resetDevice() {
        val device = opened ?: return
        if (resetting) return
        resetting = true
        pendingMode = current?.let { Mode(it.type, it.width, it.height, it.fps) }
        resetJob = viewModelScope.launch {
            surface?.let { helper.removeSurface(it) }
            helper.stopPreview()
            helper.closeCamera()
            previewing = false
            delay(400)
            helper.release()
            delay(600)

            val port = portReset(device)
            Log.i(TAG, "reset: port-level reset ${if (port) "succeeded" else "unavailable, soft reset only"}")
            delay(1500) // let the device settle and the kernel rebind its drivers

            helper = newHelper()
            val again = helper.deviceList.orEmpty().firstOrNull { it.vendorId == device.vendorId && it.productId == device.productId } ?: device
            helper.selectDevice(again)

            delay(8000)
            if (resetting) {
                Log.w(TAG, "reset: device did not reopen")
                resetting = false
                pendingMode = null
                opened = null
                modes = emptyList()
                current = null
                refreshDevices()
            }
        }
    }

    /** Resets the USB device with the kernel's device reset on a second connection; false if not possible. */
    private fun portReset(device: UsbDevice): Boolean {
        val usb = app.getSystemService(UsbManager::class.java)
        val connection = runCatching { usb.openDevice(device) }.getOrNull() ?: return false
        return try {
            UsbDeviceConnection::class.java.getMethod("resetDevice").invoke(connection) as? Boolean ?: false
        } catch (e: Throwable) {
            Log.i(TAG, "reset: resetDevice() not usable: $e")
            false
        } finally {
            connection.close()
        }
    }

    // --- lifecycle -------------------------------------------------------------------------------

    fun close() {
        resetJob?.cancel()
        resetting = false
        pendingMode = null
        surface?.let { helper.removeSurface(it) }
        helper.stopPreview()
        helper.closeCamera()
        previewing = false
        opened = null
        modes = emptyList()
        current = null
    }

    fun surfaceReady(s: Surface) {
        surface = s
        if (previewing) helper.addSurface(s, false)
    }

    fun surfaceGone(s: Surface) {
        if (previewing) helper.removeSurface(s)
        if (surface === s) surface = null
    }

    override fun onCleared() {
        helper.release()
    }

    private inner class StateCallback(private val gen: Int) : ICameraHelper.StateCallback {
        private fun stale() = gen != generation

        override fun onAttach(device: UsbDevice) {
            if (!stale()) refreshDevices()
        }

        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            if (stale()) return
            // Open straight into the remembered mode when the camera already reports its mode list.
            val want = pendingMode ?: saved(device)
            val size = want?.let { w -> resolve(runCatching { helper.supportedSizeList }.getOrNull().orEmpty(), w) }
            if (size != null) helper.openCamera(size) else helper.openCamera()
        }

        override fun onCameraOpen(device: UsbDevice) {
            if (stale()) return
            opened = device
            modes = helper.supportedSizeList.orEmpty()
            val want = pendingMode ?: saved(device)
            val target = want?.let { resolve(modes, it) } ?: helper.previewSize
            target?.let { setMode(it) }
            pendingMode = null
            resetting = false
        }

        override fun onCameraClose(device: UsbDevice) {
            if (!stale() && !resetting) previewing = false
        }

        override fun onDeviceClose(device: UsbDevice) = Unit

        override fun onDetach(device: UsbDevice) {
            if (stale() || resetting) return
            if (device == opened) {
                opened = null
                modes = emptyList()
                current = null
                previewing = false
            }
            refreshDevices()
        }

        override fun onCancel(device: UsbDevice) = Unit

        override fun onError(device: UsbDevice, e: CameraException) {
            if (!stale()) Log.w(TAG, "camera error: ${e.message}")
        }
    }
}
