package de.aimtracer.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.aimtracer.android.ble.AimTracerBleManager
import de.aimtracer.android.data.SessionStore
import de.aimtracer.android.model.DeviceConfiguration
import de.aimtracer.android.model.TrainingProgram
import de.aimtracer.android.protocol.AimTracerCommand

class AimTracerViewModel(application: Application) :
    AndroidViewModel(application) {

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

    override fun onCleared() {
        ble.close()
        super.onCleared()
    }
}
