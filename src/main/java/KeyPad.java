import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;

public class KeyPad extends AbstractActor {
    private ActorRef controlUnitActor;
    private static final String CORRECT_PIN = "1111";

    public KeyPad(ActorRef controlUnit) {
        this.controlUnitActor = controlUnit;
    }

    public static Props props(ActorRef controlUnit) {
        return Props.create(KeyPad.class, () -> new KeyPad(controlUnit));
    }


    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(SmartHomeProtocol.InsertPinMsg.class, this::onInsertPinMsg)
                .match(SmartHomeProtocol.ArmWithPinMsg.class, this::onArmWithPinMsg)
                .build();
    }

    // The system is armed only if the PIN is correct
    private void onArmWithPinMsg(SmartHomeProtocol.ArmWithPinMsg armWithPinMsg) {
        if (armWithPinMsg.pin().equals(CORRECT_PIN)) {
            controlUnitActor.tell(new SmartHomeProtocol.ArmSystemRequest(armWithPinMsg.zonesToArm()), this.self());
        } else {
            controlUnitActor.tell(new SmartHomeProtocol.InvalidPinEntered(), this.self());
        }
    }

    private void onInsertPinMsg(SmartHomeProtocol.InsertPinMsg insertPinMsg) {
        final boolean isPinCorrect = insertPinMsg.pin().equals(CORRECT_PIN);
        if (isPinCorrect) {
            controlUnitActor.tell(new SmartHomeProtocol.ValidPinEntered(), this.self());
        } else {
            controlUnitActor.tell(new SmartHomeProtocol.InvalidPinEntered(), this.self());
        }
    }
}
