import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.AbstractActorWithTimers;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ControlUnit extends AbstractActorWithTimers {
    private final ActorRef siren;
    private static final Object DELAY_TIMER_KEY = "DelayTimerKey";
    private final Map<String, String> sensorZoneMap;
    private final Set<String> activeZones = new HashSet<>();

    // Internal tick tokens for timeouts
    private static class ExitDelayTimeout {}
    private static class EntryDelayTimeout {}

    private final java.time.Duration exitDelay;
    private final java.time.Duration entryDelay;

    public static Props props(ActorRef siren, Duration exitDelay, Duration entryDelay, Map<String, String> sensorZoneMap) {
        return Props.create(ControlUnit.class, () -> new ControlUnit(siren,  exitDelay, entryDelay, sensorZoneMap));
    }

    public ControlUnit(ActorRef siren, Duration exitDelay, Duration entryDelay,  Map<String, String> sensorZoneMap) {
        this.siren = siren;
        this.exitDelay = exitDelay;
        this.entryDelay = entryDelay;
        this.sensorZoneMap = new HashMap<>(sensorZoneMap);;
    }

    public void addSensorZone(String sensorId, String zoneId) {
        sensorZoneMap.put(sensorId, zoneId);
    }

    @Override
    public Receive createReceive() {
        return disarmedState();
    }

    // Disarmed state:
    // - Exit
    // - SensorTriggered
    private Receive disarmedState() {
        return receiveBuilder()
                .match(SmartHomeProtocol.ArmSystemRequest.class, this::onArmSystemRequest)
                .match(SmartHomeProtocol.SensorTriggeredMsg.class, this::onSensorTriggeredDisarmed)
                .build();
    }

    // Exit delay state:
    // - SensorTriggered
    // - ExitDelayTimeout
    private Receive exitDelayState() {
        return receiveBuilder()
                .match(ExitDelayTimeout.class, this::onExitDelayTimeout)
                .match(SmartHomeProtocol.SensorTriggeredMsg.class, this::onSensorTriggeredDisarmed)
                .build();
    }

    // Armed state:
    // - SensorTriggered
    private Receive armedState() {
        return receiveBuilder()
                .match(SmartHomeProtocol.SensorTriggeredMsg.class, this::onSensorTriggeredArmed)
                .build();
    }

    // Entry delay state:
    // - ValidPinEntered
    // - EntryDelayTimeout
    private Receive entryDelayState() {
        return receiveBuilder()
                .match(SmartHomeProtocol.ValidPinEntered.class, this::onValidPinEnteredEntryState)
                .match(EntryDelayTimeout.class, this::onEntryDelayTimeout)
                .build();
    }

    // Allarm state:
    // - ValidPinEntered
    private Receive allarmState() {
        return receiveBuilder()
                .match(SmartHomeProtocol.ValidPinEntered.class, this::onValidPinEnteredAllarmState)
                .match(SmartHomeProtocol.InvalidPinEntered.class, this::onInvalidPinEnteredAllarmState)
                .build();
    }

    private void onInvalidPinEnteredAllarmState(SmartHomeProtocol.InvalidPinEntered invalidPinEntered) {
        System.out.println("[ControlUnit] WARNING! Invalid pin entered: ");
    }

    private void onValidPinEnteredAllarmState(SmartHomeProtocol.ValidPinEntered validPinEntered) {
        System.out.println("[ControlUnit] Valid pin entered! Disarming allarm");
        siren.tell(new SmartHomeProtocol.DeactivateSiren(), self());
        getContext().become(disarmedState());
    }

    private void onEntryDelayTimeout(EntryDelayTimeout entryDelayTimeout) {
        System.out.println("[ControlUnit] Entry delay timeout received. Setting up alarm!");
        siren.tell(new SmartHomeProtocol.ActivateSiren(), this.self());
        getContext().become(allarmState());
    }

    private void onSensorTriggeredArmed(SmartHomeProtocol.SensorTriggeredMsg sensorTriggeredMsg) {
        // Look up which zone this sensor belongs to
        String sensorZone = sensorZoneMap.get(sensorTriggeredMsg.sensorId());

        // check if the zone is currently active
        if (sensorZone != null && activeZones.contains(sensorZone)) {
            System.out.println("[ControlUnit] WARNING! Intrusion detected Active zone breached: " + sensorZone);
            // Trigger entry delay and swap state
            getTimers().startSingleTimer(DELAY_TIMER_KEY, new EntryDelayTimeout(), entryDelay);
            getContext().become(entryDelayState());
        } else {
            System.out.println("[ControlUnit] Ignored sensor " + sensorTriggeredMsg.sensorId() + " (Zone " + sensorZone + " is inactive).");
        }
    }

    private void onValidPinEnteredEntryState(SmartHomeProtocol.ValidPinEntered validPinEntered) {
        System.out.println("[ControlUnit] Valid pin entered! Disarming system");
        getTimers().cancel(DELAY_TIMER_KEY);
        getContext().become(disarmedState());
    }

    private void onExitDelayTimeout(ExitDelayTimeout exitDelayTimeout) {
        System.out.println("[ControlUnit] Exit delay expired. System is now armed.");
        getContext().become(armedState());
    }


    private void onSensorTriggeredDisarmed(SmartHomeProtocol.SensorTriggeredMsg sensorTriggeredMsg) {
        System.out.println("[ControlUnit] Disarmed. Logging sensor trigger: " + sensorTriggeredMsg.sensorId());
    }

    private void onArmSystemRequest(SmartHomeProtocol.ArmSystemRequest armSystemRequest) {
        System.out.println("[ControlUnit] Arming requested. Starting exit delay...");
        this.activeZones.clear();
        this.activeZones.addAll(armSystemRequest.zonesToArm());
        getTimers().startSingleTimer(DELAY_TIMER_KEY, new ExitDelayTimeout(), exitDelay);
        getContext().become(exitDelayState());
    }

}
