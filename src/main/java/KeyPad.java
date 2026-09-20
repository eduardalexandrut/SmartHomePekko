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
                .build();
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
