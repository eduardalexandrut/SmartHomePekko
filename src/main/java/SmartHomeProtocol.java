public interface SmartHomeProtocol {

    // Messages sent to the KeyPad Actor
    interface KeyPadCommand {}
    record InsertPinMsg(String pin) implements KeyPadCommand {}

    // Messages sent to the ControlUnit Actor
    interface ControlUnitCommand {}
    record ValidPinEntered() implements ControlUnitCommand {}
    record SensorTriggeredMsg(String sensorId) implements ControlUnitCommand {}
    record ArmSystemRequest() implements ControlUnitCommand {}
    record DelayTimeout() implements ControlUnitCommand {}

    // Message sent to the Sensor actor
    interface SensorUnitCommand {}
    record OpenWindowMsg() implements SensorUnitCommand {}
    record OpenDoorMsg() implements SensorUnitCommand {}

    // Messages sent to the siren
    interface SirenCommand {}
    record ActivateSiren() implements SirenCommand {}
    record DeactivateSiren() implements SirenCommand {}
}
