package de.aimtracer.android

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import de.aimtracer.android.analysis.AxisDisplayConfiguration
import de.aimtracer.android.ble.AimTracerBleManager
import de.aimtracer.android.data.SessionStore
import de.aimtracer.android.model.DeviceConfiguration
import de.aimtracer.android.model.TrainingProgram
import de.aimtracer.android.protocol.AimTracerCommand

class AimTracerViewModel(application: Application) :
    AndroidViewModel(application) {

    private val displayPreferences = application.getSharedPreferences(
        "aimtracer_display",
        Context.MODE_PRIVATE
    )

    var invertXAxis by mutableStateOf(
        displayPreferences.getBoolean("invert_x_axis", false)
    )
        private set

    var invertYAxis by mutableStateOf(
        displayPreferences.getBoolean("invert_y_axis", false)
    )
        private set

    val axisDisplayConfiguration: AxisDisplayConfiguration
        get() = AxisDisplayConfiguration(invertXAxis, invertYAxis)

    val sessions = SessionStore(application)
    val ble = AimTracerBleManager(application) { shot ->
        sessions.record(shot)
    }

    fun startSession(program: TrainingProgram) {
        sessions.start(program)
        ble.send(AimTracerCommand.START_SESSION)
    }

    fun stopSession(meytonScore: Double?) {
        ble.send(AimTracerCommand.STOP_SESSION)
        sessions.stop(meytonScore)
    }

    fun sendConfiguration(configuration: DeviceConfiguration) {
        ble.send(configuration)
    }

    fun setInvertXAxis(inverted: Boolean) {
        invertXAxis = inverted
        displayPreferences.edit().putBoolean("invert_x_axis", inverted).apply()
    }

    fun setInvertYAxis(inverted: Boolean) {
        invertYAxis = inverted
        displayPreferences.edit().putBoolean("invert_y_axis", inverted).apply()
    }

    override fun onCleared() {
        ble.close()
        super.onCleared()
    }
}
