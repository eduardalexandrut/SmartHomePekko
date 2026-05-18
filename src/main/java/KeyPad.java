import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;

public class KeyPad extends AbstractActor {
    private ActorRef controlUnitActor;
    private static final String CORRECT_PIN = "1111";

    public KeyPad(ActorRef controlUnit) {
        this.controlUnitActor = controlUnit;
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
            // call ControlUnit and say pin is correct
            controlUnitActor.tell(new SmartHomeProtocol.ValidPinEntered(), this.self());
        }
    }
}
