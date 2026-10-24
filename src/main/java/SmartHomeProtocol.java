import java.util.Set;

public interface SmartHomeProtocol {

    // Messages sent to the KeyPad Actor
    interface KeyPadCommand {}
    record InsertPinMsg(String pin) implements KeyPadCommand {}
    // Arming request typed on the keypad: the PIN is checked before the system is armed
    record ArmWithPinMsg(String pin, Set<String> zonesToArm) implements KeyPadCommand {
        public ArmWithPinMsg {
            zonesToArm = Set.copyOf(zonesToArm); // messages must be immutable
        }
    }

    // Messages sent to the ControlUnit Actor
    interface ControlUnitCommand {}
    record ValidPinEntered() implements ControlUnitCommand {}
    record InvalidPinEntered() implements ControlUnitCommand {}
    record SensorTriggeredMsg(String sensorId) implements ControlUnitCommand {}
    record ArmSystemRequest(Set<String> zonesToArm) implements ControlUnitCommand {}
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
