package cz.mts.phone.helpers

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import cz.mts.base.extensions.getProperBackgroundColor
import cz.mts.base.extensions.getProperPrimaryColor
import cz.mts.base.extensions.getProperTextColor
import cz.mts.phone.R
import cz.mts.phone.extensions.audioManager

class AudioOutputRoutingHelper(private val context: Context) {

    private val audioManager = context.audioManager


    fun getHardwareDevices(): List<AudioDeviceInfo> {
        return audioManager
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { device ->
                when (device.type) {

                    // Bluetooth určený pro hovory
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET -> true

                    // Drátová headset zařízení
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> true

                    // USB headset (ne obecné USB audio)
                    AudioDeviceInfo.TYPE_USB_HEADSET -> true

                    else -> false
                }
            }
    }

    // Ikonky
    private fun iconFor(device: AudioDeviceInfo?): String =
        when (device?.type) {

            // --- Bluetooth Classic ---
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,

                // --- Bluetooth LE (Android 14+) ---
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST ->
                "\uD83C\uDFA7(ᛒ)"   // Bluetooth 🎧

            // --- Drátová sluchátka ---
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
                "\uD83C\uDFA7"       // Wired 🎧

            // --- USB zařízení ---
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE ->
                "\uD83C\uDFA7(usb)"  // USB 🎧

            // --- Vestavěný reproduktor ---
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ->
                "\uD83D\uDD0A"       // 🔊

            else -> "🎶"
        }

    private val ICON_EARPIECE = "👂🏻"
    private val ICON_SPEAKER = "🔊"

    fun showChooserOrToggle(call : Call?, showRouteMenu : Boolean) {

        val hardware = getHardwareDevices()

        when {
            // chceme menu
            showRouteMenu -> showHardwareChooser(hardware)

            // pouze Earpiece / Speaker
            hardware.isEmpty()  -> toggleBetweenEarpieceAndSpeaker()

            // Speaker + HW → toggle
            //hardware.size == 1 -> toggleBetweenSpeakerAndHardware(hardware.first())

            // 3) Více HW → zobrazit dialog
            else -> showHardwareChooser(hardware)
        }
    }

    private fun toggleBetweenEarpieceAndSpeaker() {
        val speakerOn = audioManager.isSpeakerphoneOn

        if (speakerOn) {
            CallManager.setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE)
        } else {
            CallManager.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
        }
    }

    private fun showHardwareChooser(hardware: List<AudioDeviceInfo>) {

        val routes = mutableListOf<RouteItem>()

        // Zobrazíme ucho
        routes += RouteItem(
            label = context.getString(R.string.ear_mts),
            icon = ICON_EARPIECE,
            type = RouteType.EARPIECE
        )
        // Zobrazíme speaker
        routes += RouteItem(
            label = context.getString(R.string.speaker_mts),
            icon = ICON_SPEAKER,
            type = RouteType.SPEAKER
        )


        // Každé HW přidáme
        hardware.forEach { device ->
            routes += RouteItem(
                label = device.productName.toString(),
                icon = iconFor(device),
                type = RouteType.HARDWARE,
                device = device
            )
        }

        val labels = routes.map { "${it.icon}  ${it.label}" }.toTypedArray()

        val adapter = object : ArrayAdapter<String>(
            context,
            android.R.layout.simple_list_item_1,
            labels
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)

                textView.setTextColor(context.getProperTextColor())
                textView.setTypeface(textView.typeface, Typeface.BOLD) // volitelně

                return view
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setAdapter(adapter) { _, which ->
                applyRoute(routes[which])
            }
            .setNegativeButton(R.string.dialog_back, null)
            .show()


        val roundedBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 64f                  // ← poloměr rohů (v pixelech)
            setColor(context.getProperBackgroundColor())    // ← barva pozadí
        }

        // Nastavení pozadí dialogu
        dialog.window?.setBackgroundDrawable(roundedBackground)
        dialog.show()

        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        negativeButton?.setTextColor(context.getProperPrimaryColor())
        negativeButton?.setTypeface(negativeButton.typeface, Typeface.BOLD)



    }

    // -----------------------------
    // Aplikace routy
    // -----------------------------
    private fun applyRoute(item: RouteItem) {
        when (item.type) {

            RouteType.EARPIECE ->
                CallManager.setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE)

            RouteType.SPEAKER ->
                CallManager.setAudioRoute(CallAudioState.ROUTE_SPEAKER)

            RouteType.HARDWARE ->
                applyHardwareRoute(item.device)
        }
    }

    private fun applyHardwareRoute(device: AudioDeviceInfo?) {
        if (device == null) {
            CallManager.setAudioRoute(CallAudioState.ROUTE_WIRED_OR_EARPIECE)
            return
        }

        val route = when (device.type) {

            // Bluetooth
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST ->
                CallAudioState.ROUTE_BLUETOOTH

            // Wired/USB
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET ->
                CallAudioState.ROUTE_WIRED_OR_EARPIECE

            // Speaker
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ->
                CallAudioState.ROUTE_SPEAKER

            else ->
                CallAudioState.ROUTE_WIRED_OR_EARPIECE
        }

        CallManager.setAudioRoute(route)
    }

    data class RouteItem(
        val label: String,
        val icon: String,
        val type: RouteType,
        val device: AudioDeviceInfo? = null
    )

    enum class RouteType {
        EARPIECE,
        SPEAKER,
        HARDWARE
    }
}
