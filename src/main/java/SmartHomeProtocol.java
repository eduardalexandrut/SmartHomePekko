import org.apache.pekko.actor.ActorRef;

public interface SmartHomeProtocol {

    // Messages sent to the KeyPad Actor
    interface KeyPadCommand {}
    record InsertPinMsg(String pin) implements KeyPadCommand {}

    // Messages sent to the ControlUnit Actor
    interface ControlUnitCommand {}
    record ValidPinEntered() implements ControlUnitCommand {}
    record SensorTriggered(String sensorId) implements ControlUnitCommand {}
    record ArmSystemRequest() implements ControlUnitCommand {}
    record DelayTimeout() implements ControlUnitCommand {}

    // Messages sent to the siren
    interface SirenCommand {}
    record ActivateSiren() implements SirenCommand {}
    record DeactivateSiren() implements SirenCommand {}
}
