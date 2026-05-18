import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;

public class Sensor extends AbstractActor {
    private ActorRef controlUnitActor;
    private String sensorId;

    public Sensor(ActorRef controlUnitActor, String sensorId) {
        this.controlUnitActor = controlUnitActor;
        this.sensorId = sensorId;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(SmartHomeProtocol.OpenWindowMsg.class, this::onWindowOpen)
                .match(SmartHomeProtocol.OpenDoorMsg.class, this::onDoorOpen)
                .build();
    }

    private void onDoorOpen(SmartHomeProtocol.OpenDoorMsg openDoorMsg) {
        System.out.println("Sensor id: " + sensorId + " detected an open window event");
        this.controlUnitActor.tell(new SmartHomeProtocol.SensorTriggeredMsg(this.sensorId), ActorRef.noSender());
    }

    private void onWindowOpen(SmartHomeProtocol.OpenWindowMsg triggerSensorMsg) {
        System.out.println("Sensor id: " + sensorId + " detected an open window event");
        this.controlUnitActor.tell(new SmartHomeProtocol.SensorTriggeredMsg(this.sensorId), ActorRef.noSender());
    }
}
